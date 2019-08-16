/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.stream

import zio._
import zio.clock.Clock
import zio.duration.Duration

/**
 * A `Stream[E, A]` represents an effectful stream that can produce values of
 * type `A`, or potentially fail with a value of type `E`.
 *
 * Streams have a very similar API to Scala collections, making them immediately
 * familiar to most developers. Unlike Scala collections, streams can be used
 * on effectful streams of data, such as HTTP connections, files, and so forth.
 *
 * Streams do not leak resources. This guarantee holds in the presence of early
 * termination (not all of a stream is consumed), failure, or even interruption.
 *
 * Thanks to only first-order types, appropriate variance annotations, and
 * specialized effect type (ZIO), streams feature extremely good type inference
 * and should almost never require specification of any type parameters.
 *
 */
trait ZStream[-R, +E, +A] extends Serializable { self =>
  import ZStream.{ Fold, InputStream }

  /**
   * Obtain a managed input stream that can be used to read from the stream until it is
   * empty (or possibly forever, if the stream is infinite). The provided `InputStream`
   * is valid only inside the scope of the managed resource.
   */
  def process: ZManaged[R, E, InputStream[R, E, A]] = processDefault

  /**
   * Executes an effectful fold over the stream of values.
   */
  def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S]

  /**
   * Concatenates with another stream in strict order
   */
  final def ++[R1 <: R, E1 >: E, A1 >: A](other: ZStream[R1, E1, A1]): ZStream[R1, E1, A1] =
    concat(other)

  /**
   * Aggregates elements of this stream using the provided sink for as long
   * as the downstream operators on the stream are busy.
   *
   * This operator divides the stream into two asynchronous "islands". Operators upstream
   * of this operator run on one fiber, while downstream operators run on another. Whenever
   * the downstream fiber is busy processing elements, the upstream fiber will feed elements
   * into the sink until it signals completion.
   *
   * Any sink can be used here, but see [[Sink.foldWeightedM]] and [[Sink.foldUntilM]] for
   * sinks that cover the common usecases.
   */
  final def aggregate[R1 <: R, E1 >: E, A1 >: A, B](sink: ZSink[R1, E1, A1, A1, B]): ZStream[R1, E1, B] = {
    /*
     * How this works:
     *
     * One fiber reads from the `self` stream, and is responsible for aggregating the elements
     * using the sink. Another fiber reads the aggregated elements as fast as possible.
     *
     * The two fibers share the sink's state in a Ref protected by a semaphore. The state machine
     * is defined by the `State` type. See the comments on `producer` and `consumer` for more details
     * on the transitions.
     */
    import ZSink.Step

    sealed abstract class State
    object State {
      case class Empty(state: sink.State, notifyConsumer: Promise[Nothing, Unit])       extends State
      case class BatchMiddle(state: sink.State, notifyProducer: Promise[Nothing, Unit]) extends State
      case class BatchEnd(state: sink.State, notifyProducer: Promise[Nothing, Unit])    extends State
      case class Error(e: Cause[E1])                                                    extends State
      case object End                                                                   extends State
    }

    def withStateVar[R, E, A](ref: Ref[State], permits: Semaphore)(f: State => ZIO[R, E, (A, State)]): ZIO[R, E, A] =
      permits.withPermit {
        for {
          s <- ref.get
          a <- f(s).flatMap {
                case (a, s2) => ref.set(s2).as(a)
              }
        } yield a
      }

    def produce(stateVar: Ref[State], permits: Semaphore, a: A1): ZIO[R1, E1, Boolean] =
      withStateVar(stateVar, permits) {
        case State.Empty(state, notifyConsumer) =>
          for {
            notifyProducer <- Promise.make[Nothing, Unit]
            step           <- sink.step(state, a)
            leftover       = Step.leftover(step)
            result <- if (Step.cont(step))
                       UIO.succeed(
                         // Notify the consumer so they won't busy wait
                         (notifyConsumer.succeed(()).as(true), State.BatchMiddle(Step.state(step), notifyProducer))
                       )
                     else
                       UIO.succeed(
                         (
                           // Notify the consumer, wait for them to take the aggregate so we know
                           // it's time to progress, and process the leftovers
                           notifyConsumer.succeed(()) *> notifyProducer.await *>
                             leftover.foldMLazy(true)(identity)((_, a) => produce(stateVar, permits, a)),
                           State.BatchEnd(Step.state(step), notifyProducer)
                         )
                       )
          } yield result

        case State.BatchMiddle(state, notifyProducer) =>
          // The logic here is the same as the Empty state, except we don't need
          // to notify the consumer on the transition
          for {
            step     <- sink.step(state, a)
            leftover = Step.leftover(step)
            result <- if (Step.cont(step))
                       UIO.succeed((UIO.succeed(true), State.BatchMiddle(Step.state(step), notifyProducer)))
                     else
                       UIO.succeed(
                         (
                           notifyProducer.await *>
                             leftover.foldMLazy(true)(identity)((_, a) => produce(stateVar, permits, a)),
                           State.BatchEnd(Step.state(step), notifyProducer)
                         )
                       )
          } yield result

        // The producer shouldn't actually see these states, but we still use sane
        // transitions here anyway.
        case s @ State.BatchEnd(_, batchTaken) => UIO.succeed((batchTaken.await.as(true), s))
        case State.Error(e)                    => ZIO.halt(e)
        case State.End                         => UIO.succeed((UIO.succeed(true), State.End))
      }.flatten

    // This function is used in an unfold, so `None` means stop consuming
    def consume(stateVar: Ref[State], permits: Semaphore): ZIO[R1, E1, Option[Chunk[B]]] =
      withStateVar(stateVar, permits) {
        // If the state is empty, wait for a notification from the producer
        case s @ State.Empty(_, notify) => UIO.succeed((notify.await.as(Some(Chunk.empty)), s))

        case State.BatchMiddle(state, notifyProducer) =>
          for {
            initial        <- sink.initial.map(Step.state(_))
            notifyConsumer <- Promise.make[Nothing, Unit]
          } yield (
            // Inform the producer that we took the batch, extract the sink and emit the data
            notifyProducer.succeed(()) *> sink.extract(state).map(b => Some(Chunk.single(b))),
            State.Empty(initial, notifyConsumer)
          )

        case State.BatchEnd(state, notifyProducer) =>
          for {
            initial        <- sink.initial.map(Step.state(_))
            notifyConsumer <- Promise.make[Nothing, Unit]
          } yield (
            notifyProducer.succeed(()) *> sink.extract(state).map(b => Some(Chunk.single(b))),
            State.Empty(initial, notifyConsumer)
          )

        case State.Error(cause) => ZIO.halt(cause)
        case State.End          => ZIO.succeed((UIO.succeed(None), State.End))
      }.flatten

    def drainAndSet(stateVar: Ref[State], permits: Semaphore, s: State): UIO[Unit] =
      withStateVar(stateVar, permits) {
        // If the state is empty, it's ok to overwrite it. We just need to notify the consumer.
        case State.Empty(_, notifyNext) => UIO.succeed((notifyNext.succeed(()).unit, s))

        // For these states (middle/end), we need to wait until the consumer notified us
        // that they took the data. Then rerun.
        case existing @ State.BatchMiddle(_, notifyProducer) =>
          UIO.succeed((notifyProducer.await *> drainAndSet(stateVar, permits, s), existing))
        case existing @ State.BatchEnd(_, notifyProducer) =>
          UIO.succeed((notifyProducer.await *> drainAndSet(stateVar, permits, s), existing))

        // For all other states, we just overwrite.
        case _ => UIO.succeed((UIO.unit, s))
      }.flatten

    new ZStream[R1, E1, B] {
      def fold[R2 <: R1, E2 >: E1, B1 >: B, S]: ZStream.Fold[R2, E2, B1, S] =
        ZManaged.succeed { (s, cont, f) =>
          for {
            initSink  <- sink.initial.map(Step.state(_)).toManaged_
            initAwait <- Promise.make[Nothing, Unit].toManaged_
            stateVar  <- Ref.make[State](State.Empty(initSink, initAwait)).toManaged_
            permits   <- Semaphore.make(1).toManaged_
            producer <- self
                         .foreachWhileManaged(produce(stateVar, permits, _))
                         .foldCauseM(
                           // At this point, we're done working but we can't just overwrite the
                           // state because the consumer might not have taken the last batch. So
                           // we need to wait for the state to be drained.
                           c => drainAndSet(stateVar, permits, State.Error(c)).toManaged_,
                           _ => drainAndSet(stateVar, permits, State.End).toManaged_
                         )
                         .fork
            s2 <- ZStream
                   .unfoldM(())(_ => consume(stateVar, permits).map(_.map((_, ()))))
                   .mapConcat(identity)
                   .fold[R2, E2, B1, S]
                   .flatMap(_.apply(s, cont, f))
                   .ensuringFirst(producer.interrupt.fork)
          } yield s2
        }
    }
  }

  /**
   * Aggregates elements using the provided sink until it signals completion, or the
   * delay signalled by the schedule has passed.
   *
   * This operator divides the stream into two asynchronous islands. Operators upstream
   * of this operator run on one fiber, while downstream operators run on another. Elements
   * will be aggregated by the sink until the downstream fiber pulls the aggregated value,
   * or until the schedule's delay has passed.
   *
   * Aggregated elements will be fed into the schedule to determine the delays between
   * pulls.
   */
  final def aggregateWithin[R1 <: R, E1 >: E, A1 >: A, B, C](
    sink: ZSink[R1, E1, A1, A1, B],
    schedule: ZSchedule[R1, Option[B], C]
  ): ZStream[R1 with Clock, E1, Either[C, B]] = {
    /*
     * How this works:
     *
     * One fiber reads from the `self` stream, and is responsible for aggregating the elements
     * using the sink. Another fiber reads the aggregated elements when the sink has signalled
     * completion or the delay from the schedule has expired. The delay for each iteartion of
     * the consumer is derived from the the last aggregate pulled. When the schedule signals
     * completion, its result is also emitted into the stream.
     *
     * The two fibers share the sink's state in a Ref protected by a semaphore. The state machine
     * is defined by the `State` type. See the comments on `producer` and `consumer` for more details
     * on the transitions.
     */
    import ZSink.Step

    sealed abstract class State
    object State {
      case class Empty(state: sink.State, notifyConsumer: Promise[Nothing, Unit]) extends State
      case class BatchMiddle(
        state: sink.State,
        notifyProducer: Promise[Nothing, Unit],
        notifyConsumer: Promise[Nothing, Unit]
      ) extends State
      case class BatchEnd(state: sink.State, notifyProducer: Promise[Nothing, Unit]) extends State
      case class Error(e: Cause[E1])                                                 extends State
      case object End                                                                extends State
    }

    def withStateVar[R, E, A](ref: Ref[State], permits: Semaphore)(f: State => ZIO[R, E, (A, State)]): ZIO[R, E, A] =
      permits.withPermit {
        for {
          s <- ref.get
          a <- f(s).flatMap {
                case (a, s2) => ref.set(s2).as(a)
              }
        } yield a
      }

    def produce(out: Ref[State], permits: Semaphore, a: A1): ZIO[R1, E1, Boolean] =
      withStateVar(out, permits) {
        case State.Empty(state, notifyConsumer) =>
          for {
            step           <- sink.step(state, a)
            notifyProducer <- Promise.make[Nothing, Unit]
            result <- if (Step.cont(step))
                       // If the sink signals to continue, we move to BatchMiddle. The existing notifyConsumer
                       // promise is copied along because the consumer is racing it against the schedule's timeout.
                       UIO.succeed(
                         (UIO.succeed(true), State.BatchMiddle(Step.state(step), notifyProducer, notifyConsumer))
                       )
                     else
                       // If the sink signals to stop, we notify the consumer that we're done and wait for it
                       // to take the data. Then we process the leftovers.
                       UIO.succeed(
                         (
                           notifyConsumer.succeed(()) *> notifyProducer.await *> Step
                             .leftover(step)
                             .foldMLazy(true)(identity)((_, a) => produce(out, permits, a)),
                           State.BatchEnd(Step.state(step), notifyProducer)
                         )
                       )
          } yield result

        case State.BatchMiddle(currentState, notifyProducer, notifyConsumer) =>
          for {
            step <- sink.step(currentState, a)
            // Same logic here as in BatchEmpty: when the sink continues, we stay in this state;
            // when the sink stops, we signal the consumer, wait for the data to be taken and
            // process leftovers.
            result <- if (Step.cont(step))
                       UIO.succeed(
                         (UIO.succeed(true), State.BatchMiddle(Step.state(step), notifyProducer, notifyConsumer))
                       )
                     else
                       UIO.succeed(
                         (
                           notifyConsumer.succeed(()) *> notifyProducer.await *> Step
                             .leftover(step)
                             .foldMLazy(true)(identity)((_, a) => produce(out, permits, a)),
                           State.BatchEnd(Step.state(step), notifyProducer)
                         )
                       )
          } yield result

        // The producer shouldn't actually see these states, but we do whatever is sensible anyway
        case s @ State.BatchEnd(_, notifyProducer) =>
          UIO.succeed(notifyProducer.await.as(true) -> s)

        case s @ State.Error(c) =>
          UIO.succeed(ZIO.halt(c) -> s)

        case State.End =>
          UIO.succeed(UIO.succeed(false) -> State.End)
      }.flatten

    case class UnfoldState(
      lastBatch: Option[B],
      scheduleState: schedule.State,
      nextBatchCompleted: Promise[Nothing, Unit]
    )

    def consume(
      unfoldState: UnfoldState,
      stateVar: Ref[State],
      permits: Semaphore
    ): ZIO[R1 with Clock, E1, Option[(Chunk[Either[C, B]], UnfoldState)]] =
      for {
        decision <- schedule.update(unfoldState.lastBatch, unfoldState.scheduleState)
        result <- if (!decision.cont)
                   // When the schedule signals completion, we emit its result into the
                   // stream and restart with the schedule's initial state
                   schedule.initial.map(
                     init => Some(Chunk.single(Left(decision.finish())) -> unfoldState.copy(scheduleState = init))
                   )
                 else
                   for {
                     _ <- unfoldState.nextBatchCompleted.await.timeout(decision.delay)
                     r <- withStateVar(stateVar, permits) {
                           case s @ State.Empty(_, notifyDone) =>
                             // Empty state means the producer hasn't done anything yet, so nothing to do other
                             // than restart with the provided promise
                             UIO.succeed(
                               UIO.succeed(Some(Chunk.empty -> UnfoldState(None, decision.state, notifyDone))) -> s
                             )

                           case State.BatchMiddle(sinkState, notifyProducer, _) =>
                             // The schedule's delay expired before the sink signalled completion. So we extract
                             // the sink anyway and empty the state.
                             for {
                               batch          <- sink.extract(sinkState)
                               sinkInitial    <- sink.initial.map(Step.state(_))
                               notifyConsumer <- Promise.make[Nothing, Unit]
                               s              = State.Empty(sinkInitial, notifyConsumer)
                               action = notifyProducer
                                 .succeed(())
                                 .as(
                                   Some(
                                     Chunk
                                       .single(Right(batch)) -> UnfoldState(Some(batch), decision.state, notifyConsumer)
                                   )
                                 )
                             } yield action -> s

                           case State.BatchEnd(sinkState, notifyProducer) =>
                             // The sink signalled completion, so we extract it and empty the state.
                             for {
                               batch          <- sink.extract(sinkState)
                               sinkInitial    <- sink.initial.map(Step.state(_))
                               notifyConsumer <- Promise.make[Nothing, Unit]
                               s              = State.Empty(sinkInitial, notifyConsumer)
                               action = notifyProducer
                                 .succeed(())
                                 .as(
                                   Some(
                                     Chunk
                                       .single(Right(batch)) -> UnfoldState(Some(batch), decision.state, notifyConsumer)
                                   )
                                 )
                             } yield action -> s

                           case s @ State.Error(cause) =>
                             UIO.succeed(ZIO.halt(cause) -> s)

                           case State.End =>
                             UIO.succeed(UIO.succeed(None) -> State.End)
                         }.flatten
                   } yield r
      } yield result

    def consumerStream[E2 >: E1](out: Ref[State], permits: Semaphore) =
      ZStream.unwrap {
        for {
          scheduleInit <- schedule.initial
          notify <- out.get.flatMap {
                     case State.Empty(_, notifyConsumer)          => UIO.succeed(notifyConsumer)
                     case State.BatchMiddle(_, _, notifyConsumer) => UIO.succeed(notifyConsumer)
                     // If we're at the end of the batch or the end of the stream, we start off with
                     // an already completed promise to skip the schedule's delay.
                     case State.BatchEnd(_, _) | State.End => Promise.make[Nothing, Unit].tap(_.succeed(()))
                     // If we see an error, we don't even start the consumer stream.
                     case State.Error(c) => ZIO.halt(c)
                   }
          stream = ZStream
            .unfoldM(UnfoldState(None, scheduleInit, notify))(consume(_, out, permits))
            .mapConcat(identity)
        } yield stream
      }

    def drainAndSet(stateVar: Ref[State], permits: Semaphore, s: State): UIO[Unit] =
      withStateVar(stateVar, permits) {
        // It's ok to overwrite an empty state - we just need to notify the consumer
        // so it'll take the data
        case State.Empty(_, notifyNext) => UIO.succeed((notifyNext.succeed(()).unit, s))

        // For these states, we wait for the consumer to take the data and retry
        case existing @ State.BatchMiddle(_, notifyProducer, notifyConsumer) =>
          UIO.succeed(
            (notifyConsumer.succeed(()) *> notifyProducer.await *> drainAndSet(stateVar, permits, s), existing)
          )
        case existing @ State.BatchEnd(_, notifyProducer) =>
          UIO.succeed((notifyProducer.await *> drainAndSet(stateVar, permits, s), existing))

        // On all other states, we can just overwrite the state
        case _ => UIO.succeed((UIO.unit, s))
      }.flatten

    new ZStream[R1 with Clock, E1, Either[C, B]] {
      def fold[R2 <: R1 with Clock, E2 >: E1, B1 >: Either[C, B], S]: ZStream.Fold[R2, E2, B1, S] =
        ZManaged.succeed { (s, cont, f) =>
          for {
            initSink  <- sink.initial.map(Step.state(_)).toManaged_
            initAwait <- Promise.make[Nothing, Unit].toManaged_
            permits   <- Semaphore.make(1).toManaged_
            stateVar  <- Ref.make[State](State.Empty(initSink, initAwait)).toManaged_
            producer <- self
                         .foreachWhileManaged(produce(stateVar, permits, _))
                         .foldCauseM(
                           cause => drainAndSet(stateVar, permits, State.Error(cause)).toManaged_,
                           _ => drainAndSet(stateVar, permits, State.End).toManaged_
                         )
                         .fork
            s2 <- consumerStream(stateVar, permits)
                   .fold[R2, E2, B1, S]
                   .flatMap(_.apply(s, cont, f))
                   .ensuringFirst(producer.interrupt.fork)
          } yield s2
        }
    }
  }

  /**
   * Allow a faster producer to progress independently of a slower consumer by buffering
   * up to `capacity` elements in a queue.
   *
   * @note when possible, prefer capacities that are powers of 2 for better performance.
   */
  final def buffer(capacity: Int): ZStream[R, E, A] =
    ZStream.managed(self.toQueue(capacity)).flatMap { queue =>
      ZStream.fromQueue(queue).unTake
    }

  /**
   * Performs a filter and map in a single step.
   */
  def collect[B](pf: PartialFunction[A, B]): ZStream[R, E, B] =
    new ZStream[R, E, B] {
      override def fold[R1 <: R, E1 >: E, B1 >: B, S]: Fold[R1, E1, B1, S] =
        foldDefault

      override def process =
        self.process.map { as =>
          val pfIO: PartialFunction[A, InputStream[R, E, B]] = pf.andThen(InputStream.emit(_))
          def pull: InputStream[R, E, B] =
            as.flatMap { a =>
              pfIO.applyOrElse(a, (_: A) => pull)
            }

          pull
        }
    }

  /**
   * Transforms all elements of the stream for as long as the specified partial function is defined.
   */
  def collectWhile[B](pred: PartialFunction[A, B]): ZStream[R, E, B] = new ZStream[R, E, B] {
    override def fold[R1 <: R, E1 >: E, B1 >: B, S]: Fold[R1, E1, B1, S] =
      foldDefault

    override def process: ZManaged[R, E, ZStream.InputStream[R, E, B]] =
      for {
        as   <- self.process
        done <- Ref.make(false).toManaged_
        pfIO = pred.andThen(InputStream.emit(_))
        pull = for {
          alreadyDone <- done.get
          result <- if (alreadyDone) InputStream.end
                   else
                     as.flatMap { a =>
                       pfIO.applyOrElse(a, (_: A) => done.set(true) *> InputStream.end)
                     }
        } yield result
      } yield pull
  }

  /**
   * Combines this stream and the specified stream by converting both streams
   * to queues and repeatedly applying the function `f0` to extract
   * an element from the queues and conceptually "offer" it to the destination
   * stream. `f0` can maintain some internal state to control the combining
   * process, with the initial state being specified by `s1`.
   */
  final def combine[R1 <: R, E1 >: E, A1 >: A, S1, B, C](that: ZStream[R1, E1, B], lc: Int = 2, rc: Int = 2)(
    s1: S1
  )(f0: (S1, Queue[Take[E1, A1]], Queue[Take[E1, B]]) => ZIO[R1, E1, (S1, Take[E1, C])]): ZStream[R1, E1, C] =
    new ZStream[R1, E1, C] {
      def fold[R2 <: R1, E2 >: E1, C1 >: C, S]: Fold[R2, E2, C1, S] = foldDefault

      override def process =
        for {
          left  <- self.toQueue[E1, A1](lc)
          right <- that.toQueue(rc)
          pull <- ZStream
                   .unfoldM((s1, left, right)) {
                     case (s1, left, right) =>
                       f0(s1, left, right).flatMap {
                         case (s1, take) =>
                           Take.option(UIO.succeed(take)).map(_.map((_, (s1, left, right))))
                       }
                   }
                   .process
        } yield pull
    }

  /**
   * Appends another stream to this stream. The concatenated stream will first emit the
   * elements of this stream, and then emit the elements of the `other` stream.
   */
  final def concat[R1 <: R, E1 >: E, A1 >: A](other: ZStream[R1, E1, A1]): ZStream[R1, E1, A1] =
    Stream(self, other).flatMap(identity)

  /**
   * Converts this stream to a stream that executes its effects but emits no
   * elements. Useful for sequencing effects using streams:
   *
   * {{{
   * (Stream(1, 2, 3).tap(i => ZIO(println(i))) ++
   *   Stream.lift(ZIO(println("Done!"))).drain ++
   *   Stream(4, 5, 6).tap(i => ZIO(println(i)))).run(Sink.drain)
   * }}}
   */
  final def drain: ZStream[R, E, Nothing] =
    new ZStream[R, E, Nothing] {
      override def fold[R1 <: R, E1 >: E, A1 >: Nothing, S]: Fold[R1, E1, A1, S] =
        foldDefault

      override def process =
        self.process.map(_.forever)
    }

  /**
   * Drops the specified number of elements from this stream.
   */
  final def drop(n: Int): ZStream[R, E, A] =
    self.zipWithIndex.filter(_._2 > n - 1).map(_._1)

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def dropWhile(pred: A => Boolean): ZStream[R, E, A] = new ZStream[R, E, A] {
    override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
      foldDefault

    override def process =
      for {
        as              <- self.process
        keepDroppingRef <- Ref.make(true).toManaged_
        pull = {
          def go: InputStream[R, E, A] =
            as.flatMap { a =>
              keepDroppingRef.get.flatMap { keepDropping =>
                if (!keepDropping) InputStream.emit(a)
                else if (!pred(a)) keepDroppingRef.set(false) *> InputStream.emit(a)
                else go
              }
            }

          go
        }
      } yield pull
  }

  /**
   * Executes the provided finalizer after this stream's finalizers run.
   */
  def ensuring[R1 <: R](fin: ZIO[R1, Nothing, _]): ZStream[R1, E, A] =
    new ZStream[R1, E, A] {
      def fold[R2 <: R1, E1 >: E, A1 >: A, S]: ZStream.Fold[R2, E1, A1, S] =
        foldDefault

      override def process = self.process.ensuring(fin)
    }

  /**
   * Executes the provided finalizer before this stream's finalizers run.
   */
  def ensuringFirst[R1 <: R](fin: ZIO[R1, Nothing, _]): ZStream[R1, E, A] =
    new ZStream[R1, E, A] {
      def fold[R2 <: R1, E1 >: E, A1 >: A, S]: ZStream.Fold[R2, E1, A1, S] =
        foldDefault

      override def process = self.process.ensuringFirst(fin)
    }

  /**
   * Filters this stream by the specified predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  def filter(pred: A => Boolean): ZStream[R, E, A] = new ZStream[R, E, A] {
    override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
      foldDefault

    override def process =
      self.process.map { as =>
        def pull: InputStream[R, E, A] = as.flatMap { a =>
          if (pred(a)) InputStream.emit(a)
          else pull
        }

        pull
      }
  }

  /**
   * Filters this stream by the specified effectful predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  final def filterM[R1 <: R, E1 >: E](pred: A => ZIO[R1, E1, Boolean]): ZStream[R1, E1, A] = new ZStream[R1, E1, A] {
    override def fold[R2 <: R1, E2 >: E1, A1 >: A, S]: Fold[R2, E2, A1, S] =
      foldDefault

    override def process =
      self.process.map { as =>
        def pull: InputStream[R1, E1, A] =
          as.flatMap { a =>
            pred(a).mapError(Some(_)).flatMap {
              if (_) InputStream.emit(a)
              else pull
            }
          }

        pull
      }
  }

  /**
   * Filters this stream by the specified predicate, removing all elements for
   * which the predicate evaluates to true.
   */
  final def filterNot(pred: A => Boolean): ZStream[R, E, A] = filter(a => !pred(a))

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f0`
   */
  final def flatMap[R1 <: R, E1 >: E, B](f0: A => ZStream[R1, E1, B]): ZStream[R1, E1, B] =
    new ZStream[R1, E1, B] {
      def fold[R2 <: R1, E2 >: E1, B1 >: B, S]: Fold[R2, E2, B1, S] =
        foldDefault

      override def process: ZManaged[R1, E1, ZStream.InputStream[R1, E1, B]] =
        for {
          ref <- Ref.make[Option[(InputStream[R1, E1, B], Exit[_, _] => ZIO[R1, Nothing, Any])]](None).toManaged_
          as  <- self.process
          _   <- ZManaged.finalizerExit(e => ref.get.flatMap(_.map(_._2).getOrElse((_: Exit[_, _]) => UIO.unit).apply(e)))
          pullOuter = ZIO.uninterruptibleMask { restore =>
            restore(as).flatMap { a =>
              (for {
                reservation <- f0(a).process.reserve
                bs          <- restore(reservation.acquire)
                _           <- ref.set(Some(bs -> reservation.release))
              } yield ()).mapError(Some(_))
            }
          }
          bs = {
            def go: InputStream[R1, E1, B] = ref.get.flatMap {
              case None => pullOuter *> go
              case Some((isB, finalizer)) =>
                isB.catchAll {
                  case e @ Some(e1) => (finalizer(Exit.fail(e1)) *> ref.set(None)).uninterruptible *> ZIO.fail(e)
                  case None         => (finalizer(Exit.succeed(())) *> ref.set(None)).uninterruptible *> pullOuter *> go
                }
            }

            go
          }
        } yield bs
    }

  /**
   * Maps each element of this stream to another stream and returns the
   * non-deterministic merge of those streams, executing up to `n` inner streams
   * concurrently. Up to `outputBuffer` elements of the produced streams may be
   * buffered in memory by this operator.
   */
  final def flatMapPar[R1 <: R, E1 >: E, B](n: Int, outputBuffer: Int = 16)(
    f: A => ZStream[R1, E1, B]
  ): ZStream[R1, E1, B] =
    new ZStream[R1, E1, B] {
      def fold[R2 <: R1, E2 >: E1, B1 >: B, S]: Fold[R2, E2, B1, S] = foldDefault

      override def process =
        for {
          out             <- Queue.bounded[InputStream[R1, E1, B]](outputBuffer).toManaged(_.shutdown)
          permits         <- Semaphore.make(n.toLong).toManaged_
          innerFailure    <- Promise.make[Cause[E1], Nothing].toManaged_
          interruptInners <- Promise.make[Nothing, Unit].toManaged_

          // - The driver stream forks an inner fiber for each stream created
          //   by f, with an upper bound of n concurrent fibers, enforced by the semaphore.
          //   - On completion, the driver stream tries to acquire all permits to verify
          //     that all inner fibers have finished.
          //     - If one of them failed (signalled by a promise), all other fibers are interrupted
          //     - If they all succeeded, Take.End is enqueued
          //   - On error, the driver stream interrupts all inner fibers and emits a
          //     Take.Fail value
          //   - Interruption is handled by running the finalizers which take care of cleanup
          // - Inner fibers enqueue Take values from their streams to the output queue
          //   - On error, an inner fiber enqueues a Take.Fail value and signals its failure
          //     with a promise. The driver will pick that up and interrupt all other fibers.
          //   - On interruption, an inner fiber does nothing
          //   - On completion, an inner fiber does nothing
          _ <- self.foreachManaged { a =>
                for {
                  latch <- Promise.make[Nothing, Unit]
                  innerStream = Stream
                    .managed(permits.withPermitManaged)
                    .flatMap(_ => Stream.bracket(latch.succeed(()))(_ => UIO.unit))
                    .flatMap(_ => f(a))
                    .foreach(b => out.offer(InputStream.emit(b)).unit)
                    .foldCauseM(
                      cause => out.offer(InputStream.halt(cause)) *> innerFailure.fail(cause).unit,
                      _ => ZIO.unit
                    )
                  _ <- (innerStream race interruptInners.await).fork
                  // Make sure that the current inner stream has actually succeeded in acquiring
                  // a permit before continuing. Otherwise we could reach the end of the stream and
                  // acquire the permits ourselves before the inners had a chance to start.
                  _ <- latch.await
                } yield ()
              }.foldCauseM(
                  cause => (interruptInners.succeed(()) *> out.offer(InputStream.halt(cause))).unit.toManaged_,
                  _ =>
                    innerFailure.await
                    // Important to use `withPermits` here because the finalizer below may interrupt
                    // the driver, and we want the permits to be released in that case
                      .raceWith(permits.withPermits(n.toLong)(ZIO.unit))(
                        // One of the inner fibers failed. It already enqueued its failure, so we
                        // signal the inner fibers to interrupt. The finalizer below will make sure
                        // that they actually end.
                        leftDone = (_, permitAcquisition) => interruptInners.succeed(()) *> permitAcquisition.interrupt,
                        // All fibers completed successfully, so we signal that we're done.
                        rightDone = (_, failureAwait) => out.offer(InputStream.end) *> failureAwait.interrupt
                      )
                      .toManaged_
                )
                // This finalizer makes sure that in all cases, the driver stops spawning new streams
                // and the inner fibers are signalled to interrupt and actually exit.
                .ensuringFirst(interruptInners.succeed(()) *> permits.withPermits(n.toLong)(ZIO.unit))
                .fork
        } yield out.take.flatten
    }

  /**
   * Maps each element of this stream to another stream and returns the non-deterministic merge
   * of those streams, executing up to `n` inner streams concurrently. When a new stream is created
   * from an element of the source stream, the oldest executing stream is cancelled. Up to `bufferSize`
   * elements of the produced streams may be buffered in memory by this operator.
   */
  final def flatMapParSwitch[R1 <: R, E1 >: E, B](n: Int, bufferSize: Int = 16)(
    f: A => ZStream[R1, E1, B]
  ): ZStream[R1, E1, B] =
    new ZStream[R1, E1, B] {
      def fold[R2 <: R1, E2 >: E1, B1 >: B, S]: Fold[R2, E2, B1, S] = foldDefault

      override def process =
        for {
          // Modeled after flatMapPar.
          out             <- Queue.bounded[InputStream[R1, E1, B]](bufferSize).toManaged(_.shutdown)
          permits         <- Semaphore.make(n.toLong).toManaged_
          innerFailure    <- Promise.make[Cause[E1], Nothing].toManaged_
          interruptInners <- Promise.make[Nothing, Unit].toManaged_
          cancelers       <- Queue.bounded[Promise[Nothing, Unit]](n).toManaged(_.shutdown)
          _ <- self.foreachManaged { a =>
                for {
                  canceler <- Promise.make[Nothing, Unit]
                  latch    <- Promise.make[Nothing, Unit]
                  size     <- cancelers.size
                  _ <- if (size < n) UIO.unit
                      else cancelers.take.flatMap(_.succeed(())).unit
                  _ <- cancelers.offer(canceler)
                  innerStream = Stream
                    .managed(permits.withPermitManaged)
                    .flatMap(_ => Stream.bracket(latch.succeed(()))(_ => UIO.unit))
                    .flatMap(_ => f(a))
                    .foreach(b => out.offer(InputStream.emit(b)).unit)
                    .foldCauseM(
                      cause => out.offer(InputStream.halt(cause)) *> innerFailure.fail(cause).unit,
                      _ => UIO.unit
                    )
                  _ <- innerStream.raceAll(List(canceler.await, interruptInners.await)).fork
                  _ <- latch.await
                } yield ()
              }.foldCauseM(
                  cause => (interruptInners.succeed(()) *> out.offer(InputStream.halt(cause))).unit.toManaged_,
                  _ =>
                    innerFailure.await
                      .raceWith(permits.withPermits(n.toLong)(UIO.unit))(
                        leftDone =
                          (_, permitAcquisition) => interruptInners.succeed(()) *> permitAcquisition.interrupt.unit,
                        rightDone = (_, failureAwait) => out.offer(InputStream.end) *> failureAwait.interrupt.unit
                      )
                      .toManaged_
                )
                .ensuringFirst(interruptInners.succeed(()) *> permits.withPermits(n.toLong)(UIO.unit))
                .fork
        } yield out.take.flatten
    }

  final def foldDefault[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
    ZManaged.effectTotal { (s, cont, f) =>
      process.flatMap { is =>
        def loop(s1: S): ZIO[R1, E1, S] =
          if (!cont(s1)) UIO.succeed(s1)
          else
            is.foldM({
              case Some(e) => IO.fail(e)
              case None    => IO.succeed(s1)
            }, a => f(s1, a).flatMap(loop))

        ZManaged.fromEffect(loop(s))
      }
    }

  /**
   * Reduces the elements in the stream to a value of type `S`
   */
  def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): ZManaged[R, E, S] =
    fold[R, E, A1, S].flatMap(fold => fold(s, _ => true, (s, a) => ZIO.succeed(f(s, a))))

  /**
   * Consumes all elements of the stream, passing them to the specified callback.
   */
  final def foreach[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Unit]): ZIO[R1, E1, Unit] =
    foreachWhile(f.andThen(_.as(true)))

  /**
   * Like [[ZStream#foreach]], but returns a `ZManaged` so the finalization order
   * can be controlled.
   */
  final def foreachManaged[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Unit]): ZManaged[R1, E1, Unit] =
    foreachWhileManaged(f.andThen(_.as(true)))

  /**
   * Consumes elements of the stream, passing them to the specified callback,
   * and terminating consumption when the callback returns `false`.
   */
  final def foreachWhile[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Unit] =
    foreachWhileManaged(f).use_(ZIO.unit)

  /**
   * Like [[ZStream#foreachWhile]], but returns a `ZManaged` so the finalization order
   * can be controlled.
   */
  final def foreachWhileManaged[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Boolean]): ZManaged[R1, E1, Unit] =
    self
      .fold[R1, E1, A, Boolean]
      .flatMap { fold =>
        fold(true, identity, (cont, a) => if (cont) f(a) else IO.succeed(cont)).unit
      }

  /**
   * Repeats this stream forever.
   */
  def forever: ZStream[R, E, A] =
    new ZStream[R, E, A] {
      override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
        ZManaged.succeed { (s, cont, f) =>
          def loop(s: S): ZManaged[R1, E1, S] =
            self.fold[R1, E1, A, S].flatMap { fold =>
              fold(s, cont, f).flatMap(s => if (cont(s)) loop(s) else ZManaged.succeed(s))
            }

          loop(s)
        }
    }

  /**
   * Interleaves this stream and the specified stream deterministically by
   * alternating pulling values from this stream and the specified stream.
   * When one stream is exhausted all remaining values in the other stream
   * will be pulled.
   */
  final def interleave[R1 <: R, E1 >: E, A1 >: A](that: ZStream[R1, E1, A1]): ZStream[R1, E1, A1] =
    self.interleaveWith(that)(Stream(true, false).forever)

  /**
   * Combines this stream and the specified stream deterministically using the
   * stream of boolean values `b` to control which stream to pull from next.
   * `true` indicates to pull from this stream and `false` indicates to pull
   * from the specified stream. Only consumes as many elements as requested by
   * `b`. If either this stream or the specified stream are exhausted further
   * requests for values from that stream will be ignored.
   */
  final def interleaveWith[R1 <: R, E1 >: E, A1 >: A](
    that: ZStream[R1, E1, A1]
  )(b: ZStream[R1, E1, Boolean]): ZStream[R1, E1, A1] = {

    def loop(
      leftDone: Boolean,
      rightDone: Boolean,
      s: Queue[Take[E1, Boolean]],
      left: Queue[Take[E1, A]],
      right: Queue[Take[E1, A1]]
    ): ZIO[Any, Nothing, ((Boolean, Boolean, Queue[Take[E1, Boolean]]), Take[E1, A1])] =
      s.take.flatMap {
        case Take.Fail(e) => ZIO.succeed(((leftDone, rightDone, s), Take.Fail(e)))
        case Take.Value(b) =>
          if (b && !leftDone) {
            left.take.flatMap {
              case Take.Fail(e)  => ZIO.succeed(((leftDone, rightDone, s), Take.Fail(e)))
              case Take.Value(a) => ZIO.succeed(((leftDone, rightDone, s), Take.Value(a)))
              case Take.End =>
                if (rightDone) ZIO.succeed(((leftDone, rightDone, s), Take.End))
                else loop(true, rightDone, s, left, right)
            }
          } else if (!b && !rightDone)
            right.take.flatMap {
              case Take.Fail(e)  => ZIO.succeed(((leftDone, rightDone, s), Take.Fail(e)))
              case Take.Value(a) => ZIO.succeed(((leftDone, rightDone, s), Take.Value(a)))
              case Take.End =>
                if (leftDone) ZIO.succeed(((leftDone, rightDone, s), Take.End))
                else loop(leftDone, true, s, left, right)
            } else loop(leftDone, rightDone, s, left, right)
        case Take.End => ZIO.succeed(((leftDone, rightDone, s), Take.End))
      }

    for {
      s <- ZStream.managed(b.toQueue())
      result <- self.combine(that)((false, false, s)) {
                 case ((leftDone, rightDone, s), left, right) =>
                   loop(leftDone, rightDone, s, left, right)
               }
    } yield result
  }

  /**
   * Enqueues elements of this stream into a queue. Stream failure and ending will also be
   * signalled.
   */
  def into[R1 <: R, E1 >: E, A1 >: A](queue: ZQueue[R1, E1, _, _, Take[E1, A1], _]): ZIO[R1, E1, Unit] =
    intoManaged(queue).use_(UIO.unit)

  /**
   * Like [[ZStream#into]], but provides the result as a [[ZManaged]] to allow for scope
   * composition.
   */
  def intoManaged[R1 <: R, E1 >: E, A1 >: A](queue: ZQueue[R1, E1, _, _, Take[E1, A1], _]): ZManaged[R1, E1, Unit] =
    self
      .foreachManaged(a => queue.offer(Take.Value(a)).unit)
      .foldCauseM(
        cause => queue.offer(Take.Fail(cause)).unit.toManaged_,
        _ => queue.offer(Take.End).unit.toManaged_
      )

  /**
   * Returns a stream made of the elements of this stream transformed with `f0`
   */
  def map[B](f0: A => B): ZStream[R, E, B] =
    new ZStream[R, E, B] {
      def fold[R1 <: R, E1 >: E, B1 >: B, S]: Fold[R1, E1, B1, S] =
        foldDefault

      override def process =
        self.process.map(_.map(f0))
    }

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): ZStream[R, E, B] =
    mapAccumM(s1)((s, a) => UIO.succeed(f1(s, a)))

  /**
   * Statefully and effectfully maps over the elements of this stream to produce
   * new elements.
   */
  final def mapAccumM[R1 <: R, E1 >: E, S1, B](s1: S1)(f1: (S1, A) => ZIO[R1, E1, (S1, B)]): ZStream[R1, E1, B] =
    new ZStream[R1, E1, B] {
      def fold[R2 <: R1, E2 >: E1, B1 >: B, S]: Fold[R2, E2, B1, S] = foldDefault

      override def process =
        for {
          state <- Ref.make(s1).toManaged_
          as    <- self.process
        } yield as.flatMap { a =>
          (for {
            s <- state.get
            t <- f1(s, a)
            _ <- state.set(t._1)
          } yield t._2).mapError(Some(_))
        }
    }

  /**
   * Maps each element to a chunk, and flattens the chunks into the output of
   * this stream.
   */
  def mapConcat[B](f: A => Chunk[B]): ZStream[R, E, B] = new ZStream[R, E, B] {
    override def fold[R1 <: R, E1 >: E, B1 >: B, S]: Fold[R1, E1, B1, S] =
      ZManaged.succeed { (s, cont, g) =>
        self.fold[R1, E1, A, S].flatMap(fold => fold(s, cont, (s, a) => f(a).foldMLazy(s)(cont)(g)))

      }
  }

  /**
   * Effectfully maps each element to a chunk, and flattens the chunks into
   * the output of this stream.
   */
  def mapConcatM[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, Chunk[B]]): ZStream[R1, E1, B] =
    mapM(f).mapConcat(identity)

  /**
   * Maps over elements of the stream with the specified effectful function.
   */
  final def mapM[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): ZStream[R1, E1, B] = new ZStream[R1, E1, B] {
    override def fold[R2 <: R1, E2 >: E1, B1 >: B, S]: Fold[R2, E2, B1, S] =
      foldDefault

    override def process = self.process.map(_.flatMap(f(_).mapError(Some(_))))
  }

  /**
   * Maps over elements of the stream with the specified effectful function,
   * executing up to `n` invocations of `f` concurrently. Transformed elements
   * will be emitted in the original order.
   */
  final def mapMPar[R1 <: R, E1 >: E, B](n: Int)(f: A => ZIO[R1, E1, B]): ZStream[R1, E1, B] =
    new ZStream[R1, E1, B] {
      def fold[R2 <: R1, E2 >: E1, B1 >: B, S]: ZStream.Fold[R2, E2, B1, S] =
        ZManaged.succeed { (s, cont, g) =>
          for {
            out              <- Queue.bounded[Take[E1, IO[E1, B]]](n).toManaged(_.shutdown)
            permits          <- Semaphore.make(n.toLong).toManaged_
            interruptWorkers <- Promise.make[Nothing, Unit].toManaged_
            _ <- self.foreachManaged { a =>
                  for {
                    p <- Promise.make[E1, B]
                    _ <- out.offer(Take.Value(p.await))
                    _ <- (permits.withPermit(f(a).to(p)) race interruptWorkers.await).fork
                  } yield ()
                }.foldCauseM(
                    c => (out.offer(Take.Fail(c)) *> ZIO.halt(c)).toManaged_,
                    _ => out.offer(Take.End).unit.toManaged_
                  )
                  .ensuring(interruptWorkers.succeed(()) *> permits.withPermits(n.toLong)(ZIO.unit))
                  .fork
            s <- Stream
                  .fromQueue(out)
                  .unTake
                  .mapM(identity)
                  .fold[R2, E2, B1, S]
                  .flatMap(fold => fold(s, cont, g))
          } yield s
        }
    }

  /**
   * Maps over elements of the stream with the specified effectful function,
   * executing up to `n` invocations of `f` concurrently. The element order
   * is not enforced by this combinator, and elements may be reordered.
   */
  final def mapMParUnordered[R1 <: R, E1 >: E, B](n: Int)(f: A => ZIO[R1, E1, B]): ZStream[R1, E1, B] =
    self.flatMapPar[R1, E1, B](n)(a => ZStream.fromEffect(f(a)))

  /**
   * Merges this stream and the specified stream together.
   */
  final def merge[R1 <: R, E1 >: E, A1 >: A](that: ZStream[R1, E1, A1]): ZStream[R1, E1, A1] =
    self.mergeWith[R1, E1, A1, A1](that)(identity, identity) // TODO: Dotty doesn't infer this properly

  /**
   * Merges this stream and the specified stream together to produce a stream of
   * eithers.
   */
  final def mergeEither[R1 <: R, E1 >: E, B](that: ZStream[R1, E1, B]): ZStream[R1, E1, Either[A, B]] =
    self.mergeWith(that)(Left(_), Right(_))

  /**
   * Merges this stream and the specified stream together to a common element
   * type with the specified mapping functions.
   */
  final def mergeWith[R1 <: R, E1 >: E, B, C](that: ZStream[R1, E1, B])(l: A => C, r: B => C): ZStream[R1, E1, C] = {
    type Loser = Either[Fiber[Nothing, Take[E1, A]], Fiber[Nothing, Take[E1, B]]]

    def race(left: UIO[Take[E1, A]], right: UIO[Take[E1, B]]): UIO[(Take[E1, C], Loser)] =
      left.raceWith(right)(
        (exit, right) => ZIO.done(exit).map(a => (a.map(l), Right(right))),
        (exit, left) => ZIO.done(exit).map(b => (b.map(r), Left(left)))
      )

    self.combine(that)((false, false, Option.empty[Loser])) {
      case ((leftDone, rightDone, loser), left, right) =>
        if (leftDone) {
          right.take.map(_.map(r)).map(take => ((leftDone, rightDone, None), take))
        } else if (rightDone) {
          left.take.map(_.map(l)).map(take => ((leftDone, rightDone, None), take))
        } else {
          val result = loser match {
            case None               => race(left.take, right.take)
            case Some(Left(loser))  => race(loser.join, right.take)
            case Some(Right(loser)) => race(left.take, loser.join)
          }
          result.flatMap {
            case (Take.End, Left(loser)) =>
              loser.join.map(_.map(l)).map(take => ((leftDone, true, None), take))
            case (Take.End, Right(loser)) =>
              loser.join.map(_.map(r)).map(take => ((true, rightDone, None), take))
            case (Take.Value(c), loser) =>
              ZIO.succeed(((leftDone, rightDone, Some(loser)), Take.Value(c)))
            case (Take.Fail(e), loser) =>
              loser.merge.interrupt *> ZIO.succeed(((leftDone, rightDone, None), Take.Fail(e)))
          }
        }
    }
  }

  /**
   * Peels off enough material from the stream to construct an `R` using the
   * provided `Sink`, and then returns both the `R` and the remainder of the
   * `Stream` in a managed resource. Like all `Managed` resources, the provided
   * remainder is valid only within the scope of `Managed`.
   */
  final def peel[R1 <: R, E1 >: E, A1 >: A, B](
    sink: ZSink[R1, E1, A1, A1, B]
  ): ZManaged[R1, E1, (B, ZStream[R1, E1, A1])] = {
    type Folder = (Any, A1) => IO[E1, Any]
    type Cont   = Any => Boolean
    type Fold   = (Any, Cont, Folder)
    type State  = Either[sink.State, Fold]
    type Result = (B, ZStream[R, E1, A1])

    def feed[R2 <: R1, S](chunk: Chunk[A1])(s: S, cont: S => Boolean, f: (S, A1) => ZIO[R2, E1, S]): ZIO[R2, E1, S] =
      chunk.foldMLazy(s)(cont)(f)

    def tail(resume: Promise[Nothing, Fold], done: Promise[E1, Any]): ZStream[R, E1, A1] =
      new ZStream[R, E1, A1] {
        override def fold[R2 <: R, E2 >: E1, A2 >: A1, S]: ZStream.Fold[R2, E2, A2, S] =
          ZManaged.succeed { (s, cont, f) =>
            if (!cont(s)) ZManaged.succeed(s)
            else
              ZManaged.fromEffect(
                resume.succeed((s, cont.asInstanceOf[Cont], f.asInstanceOf[Folder])) *>
                  done.await.asInstanceOf[IO[E2, S]]
              )
          }
      }

    def acquire(lstate: sink.State): ZManaged[R1, Nothing, (Fiber[E1, State], Promise[E1, Result])] =
      for {
        resume <- Promise.make[Nothing, Fold].toManaged_
        done   <- Promise.make[E1, Any].toManaged_
        result <- Promise.make[E1, Result].toManaged_
        fiber <- self
                  .fold[R1, E1, A1, State]
                  .flatMap { fold =>
                    fold(
                      Left(lstate), {
                        case Left(_)             => true
                        case Right((s, cont, _)) => cont(s)
                      }, {
                        case (Left(lstate), a) =>
                          sink.step(lstate, a).flatMap { step =>
                            if (ZSink.Step.cont(step)) IO.succeed(Left(ZSink.Step.state(step)))
                            else {
                              val lstate = ZSink.Step.state(step)
                              val as     = ZSink.Step.leftover(step)

                              sink.extract(lstate).flatMap { r =>
                                result.succeed(r -> tail(resume, done)) *>
                                  resume.await
                                    .flatMap(t => feed(as)(t._1, t._2, t._3).map(s => Right((s, t._2, t._3))))
                              }
                            }
                          }
                        case (Right((rstate, cont, f)), a) =>
                          f(rstate, a).flatMap { rstate =>
                            ZIO.succeed(Right((rstate, cont, f)))
                          }
                      }
                    )
                  }
                  .foldCauseM(
                    c => ZManaged.fromEffect(result.complete(IO.halt(c)).unit *> ZIO.halt(c)),
                    ZManaged.succeed(_)
                  )
                  .fork
        _ <- fiber.await.flatMap {
              case Exit.Success(Left(_)) =>
                done.complete(
                  IO.die(new Exception("Logic error: Stream.peel's inner stream ended with a Left"))
                )
              case Exit.Success(Right((rstate, _, _))) => done.succeed(rstate)
              case Exit.Failure(c)                     => done.complete(IO.halt(c))
            }.fork.unit.toManaged_
      } yield (fiber, result)

    ZManaged
      .fromEffect(sink.initial)
      .flatMap { step =>
        acquire(ZSink.Step.state(step)).flatMap { t =>
          ZManaged.fromEffect(t._2.await)
        }
      }
  }

  private final def processDefault: ZManaged[R, E, InputStream[R, E, A]] =
    toQueue(1).map(_.take.flatMap {
      case Take.Value(a) => UIO.succeed(a)
      case Take.Fail(c) =>
        c.failureOrCause match {
          case Left(e)      => IO.fail(Some(e))
          case Right(cause) => UIO.halt(cause)
        }
      case Take.End => IO.fail(None)
    })

  /**
   * Repeats the entire stream using the specified schedule. The stream will execute normally,
   * and then repeat again according to the provided schedule.
   */
  def repeat[R1 <: R](schedule: ZSchedule[R1, Unit, _]): ZStream[R1 with Clock, E, A] =
    new ZStream[R1 with Clock, E, A] {
      import clock.sleep

      override def fold[R2 <: R1 with Clock, E1 >: E, A1 >: A, S]: Fold[R2, E1, A1, S] =
        ZManaged.succeed { (s, cont, f) =>
          def loop(s: S, sched: schedule.State): ZManaged[R2, E1, S] =
            self.fold[R2, E1, A1, S].flatMap { fold =>
              fold(s, cont, f).zip(ZManaged.fromEffect(schedule.update((), sched))).flatMap {
                case (s, decision) =>
                  if (decision.cont && cont(s)) sleep(decision.delay).toManaged_ *> loop(s, decision.state)
                  else ZManaged.succeed(s)
              }
            }

          schedule.initial.toManaged_.flatMap(loop(s, _))
        }
    }

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  def run[R1 <: R, E1 >: E, A0, A1 >: A, B](sink: ZSink[R1, E1, A0, A1, B]): ZIO[R1, E1, B] =
    sink.initial.flatMap { initial =>
      self.process.use { as =>
        def pull(state: sink.State): ZIO[R1, E1, B] =
          as.foldM(
            {
              case Some(e) => ZIO.fail(e)
              case None    => sink.extract(state)
            },
            sink.step(state, _).flatMap { step =>
              if (ZSink.Step.cont(step)) pull(ZSink.Step.state(step))
              else sink.extract(ZSink.Step.state(step))
            }
          )

        pull(ZSink.Step.state(initial))
      }
    }

  /**
   * Runs the stream and collects all of its elements in a list.
   *
   * Equivalent to `run(Sink.collectAll[A])`.
   */
  def runCollect: ZIO[R, E, List[A]] = run(Sink.collectAll[A])

  /**
   * Runs the stream purely for its effects. Any elements emitted by
   * the stream are discarded.
   *
   * Equivalent to `run(Sink.drain)`.
   */
  def runDrain: ZIO[R, E, Unit] = run(Sink.drain)

  /**
   * Repeats each element of the stream using the provided schedule, additionally emitting schedule's output,
   * each time a schedule is completed.
   * Repeats are done in addition to the first execution, so that
   * `spaced(Schedule.once)` means "emit element and if not short circuited, repeat element once".
   */
  def spaced[R1 <: R, A1 >: A](schedule: ZSchedule[R1, A, A1]): ZStream[R1 with Clock, E, A1] =
    spacedEither(schedule).map(_.merge)

  /**
   * Analogical to `spaced` but with distinction of stream elements and schedule output represented by Either
   */
  def spacedEither[R1 <: R, B](schedule: ZSchedule[R1, A, B]): ZStream[R1 with Clock, E, Either[B, A]] =
    new ZStream[R1 with Clock, E, Either[B, A]] {

      override def fold[R2 <: R1 with Clock, E1 >: E, A1 >: Either[B, A], S]: Fold[R2, E1, A1, S] =
        ZManaged.succeed { (s, cont, f) =>
          def loop(s: S, schedSt: schedule.State, a: A): ZIO[R2, E1, S] =
            if (!cont(s)) ZIO.succeed(s)
            else
              f(s, Right(a)).zip(schedule.update(a, schedSt)).flatMap {
                case (su, decision) if !decision.cont && cont(su) => f(su, Left(decision.finish()))
                case (su, decision) if decision.cont && cont(su) =>
                  loop(su, decision.state, a).delay(decision.delay)
                case (su, _) => IO.succeed(su)
              }

          schedule.initial.toManaged_.flatMap { schedSt =>
            self.fold[R2, E1, A, S].flatMap { fl =>
              fl(s, cont, (s, a) => loop(s, schedSt, a))
            }
          }

        }

    }

  /**
   * Takes the specified number of elements from this stream.
   */
  final def take(n: Int): ZStream[R, E, A] =
    new ZStream[R, E, A] {
      override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
        foldDefault

      override def process =
        for {
          as      <- self.process
          counter <- Ref.make(0).toManaged_
          pull = counter.modify { c =>
            if (c >= n) (InputStream.end, c)
            else (as, c + 1)
          }.flatten
        } yield pull
    }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def takeWhile(pred: A => Boolean): ZStream[R, E, A] = new ZStream[R, E, A] {
    override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
      ZManaged.succeed { (s, cont, f) =>
        self.fold[R1, E1, A, (Boolean, S)].flatMap { fold =>
          fold(true -> s, tp => tp._1 && cont(tp._2), {
            case ((_, s), a) =>
              if (pred(a)) f(s, a).map(true -> _)
              else IO.succeed(false         -> s)
          }).map(_._2)
        }
      }
  }

  /**
   * Adds an effect to consumption of every element of the stream.
   */
  final def tap[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, _]): ZStream[R1, E1, A] =
    new ZStream[R1, E1, A] {
      override def fold[R2 <: R1, E2 >: E1, A1 >: A, S]: Fold[R2, E2, A1, S] =
        foldDefault

      override def process = self.process.map(_.tap(f(_).mapError(Some(_))))
    }

  /**
   * Throttles elements of type A according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. Elements that do not meet the bandwidth constraints are dropped.
   * The weight of each element is determined by the `costFn` function.
   */
  final def throttleEnforce(units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => Long
  ): ZStream[R with Clock, E, A] =
    throttleEnforceM(units, duration, burst)(a => UIO.succeed(costFn(a)))

  /**
   * Throttles elements of type A according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. Elements that do not meet the bandwidth constraints are dropped.
   * The weight of each element is determined by the `costFn` effectful function.
   */
  final def throttleEnforceM[R1 <: R, E1 >: E](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => ZIO[R1, E1, Long]
  ): ZStream[R1 with Clock, E1, A] =
    transduceManaged(ZSink.throttleEnforceM(units, duration, burst)(costFn)).collect { case Some(a) => a }

  /**
   * Delays elements of type A according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. The weight of each element is determined by the `costFn` function.
   */
  final def throttleShape(units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => Long
  ): ZStream[R with Clock, E, A] =
    throttleShapeM(units, duration, burst)(a => UIO.succeed(costFn(a)))

  /**
   * Delays elements of type A according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. The weight of each element is determined by the `costFn`
   * effectful function.
   */
  final def throttleShapeM[R1 <: R, E1 >: E](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => ZIO[R1, E1, Long]
  ): ZStream[R1 with Clock, E1, A] =
    transduceManaged(ZSink.throttleShapeM(units, duration, burst)(costFn))

  /**
   * Converts the stream to a managed queue. After managed queue is used, the
   * queue will never again produce values and should be discarded.
   */
  final def toQueue[E1 >: E, A1 >: A](capacity: Int = 2): ZManaged[R, E1, Queue[Take[E1, A1]]] =
    for {
      queue <- ZManaged.make(Queue.bounded[Take[E1, A1]](capacity))(_.shutdown)
      _     <- self.intoManaged(queue).fork
    } yield queue

  /**
   * Applies a transducer to the stream, converting elements of type `A` into elements of type `C`, with a
   * managed resource of type `D` available.
   */
  final def transduceManaged[R1 <: R, E1 >: E, A1 >: A, C](
    managedSink: ZManaged[R1, E1, ZSink[R1, E1, A1, A1, C]]
  ): ZStream[R1, E1, C] =
    new ZStream[R1, E1, C] {
      override def fold[R2 <: R1, E2 >: E1, C1 >: C, S]: Fold[R2, E2, C1, S] =
        ZManaged.succeed { (s: S, cont: S => Boolean, f: (S, C1) => ZIO[R2, E2, S]) =>
          def feed(
            sink: ZSink[R1, E1, A1, A1, C]
          )(s1: sink.State, s2: S, a: Chunk[A1]): ZIO[R2, E2, (sink.State, S, Boolean)] =
            sink.stepChunk(s1, a).flatMap { step =>
              if (ZSink.Step.cont(step)) {
                IO.succeed((ZSink.Step.state(step), s2, true))
              } else {
                sink.extract(ZSink.Step.state(step)).flatMap { c =>
                  f(s2, c).flatMap { s2 =>
                    val remaining = ZSink.Step.leftover(step)
                    sink.initial.flatMap { initStep =>
                      if (cont(s2) && !remaining.isEmpty) {
                        feed(sink)(ZSink.Step.state(initStep), s2, remaining)
                      } else {
                        IO.succeed((ZSink.Step.state(initStep), s2, false))
                      }
                    }
                  }
                }
              }
            }

          for {
            sink     <- managedSink
            initStep <- sink.initial.toManaged_
            f0       <- self.fold[R2, E2, A, (sink.State, S, Boolean)]
            result <- f0((ZSink.Step.state(initStep), s, false), stepState => cont(stepState._2), { (s, a) =>
                       val (s1, s2, _) = s
                       feed(sink)(s1, s2, Chunk(a))
                     }).mapM {
                       case (s1, s2, extractNeeded) =>
                         if (extractNeeded) {
                           sink.extract(s1).flatMap(f(s2, _))
                         } else {
                           IO.succeed(s2)
                         }
                     }
          } yield result
        }
    }

  /**
   * Applies a transducer to the stream, which converts one or more elements
   * of type `A` into elements of type `C`.
   */
  final def transduce[R1 <: R, E1 >: E, A1 >: A, C](sink: ZSink[R1, E1, A1, A1, C]): ZStream[R1, E1, C] =
    transduceManaged[R1, E1, A1, C](ZManaged.succeed(sink))

  /**
   * Zips this stream together with the specified stream.
   */
  final def zip[R1 <: R, E1 >: E, B](that: ZStream[R1, E1, B], lc: Int = 2, rc: Int = 2): ZStream[R1, E1, (A, B)] =
    self.zipWith(that, lc, rc)(
      (left, right) => left.flatMap(a => right.map(a -> _))
    )

  /**
   * Zips two streams together with a specified function.
   */
  final def zipWith[R1 <: R, E1 >: E, B, C](that: ZStream[R1, E1, B], lc: Int = 2, rc: Int = 2)(
    f0: (Option[A], Option[B]) => Option[C]
  ): ZStream[R1, E1, C] = {
    def loop(
      leftDone: Boolean,
      rightDone: Boolean,
      q1: Queue[Take[E1, A]],
      q2: Queue[Take[E1, B]]
    ): ZIO[R1, E1, ((Boolean, Boolean), Take[E1, C])] = {
      val takeLeft: ZIO[R1, E1, Option[A]]  = if (leftDone) IO.succeed(None) else Take.option(q1.take)
      val takeRight: ZIO[R1, E1, Option[B]] = if (rightDone) IO.succeed(None) else Take.option(q2.take)

      def handleSuccess(left: Option[A], right: Option[B]): ZIO[Any, Nothing, ((Boolean, Boolean), Take[E1, C])] =
        f0(left, right) match {
          case None    => ZIO.succeed(((leftDone, rightDone), Take.End))
          case Some(c) => ZIO.succeed(((left.isEmpty, right.isEmpty), Take.Value(c)))
        }

      takeLeft.raceWith(takeRight)(
        (leftResult, rightFiber) =>
          leftResult.fold(
            e => rightFiber.interrupt *> ZIO.succeed(((leftDone, rightDone), Take.Fail(e))),
            l => rightFiber.join.flatMap(r => handleSuccess(l, r))
          ),
        (rightResult, leftFiber) =>
          rightResult.fold(
            e => leftFiber.interrupt *> ZIO.succeed(((leftDone, rightDone), Take.Fail(e))),
            r => leftFiber.join.flatMap(l => handleSuccess(l, r))
          )
      )
    }

    self.combine(that, lc = lc, rc = rc)((false, false)) {
      case ((leftDone, rightDone), left, right) =>
        loop(leftDone, rightDone, left, right)
    }
  }

  /**
   * Zips this stream together with the index of elements of the stream.
   */
  def zipWithIndex: ZStream[R, E, (A, Int)] =
    self.mapAccum(0)((index, a) => (index + 1, (a, index)))
}

object ZStream extends ZStreamPlatformSpecific {

  /**
   * Describes an effectful read from a stream. The optionality of the error channel denotes
   * normal termination of the stream when `None` and an error when `Some(e: E)`.
   */
  type InputStream[-R, +E, +A] = ZIO[R, Option[E], A]

  object InputStream {
    val end: InputStream[Any, Nothing, Nothing]                   = IO.fail(None)
    def emit[A](a: A): InputStream[Any, Nothing, A]               = UIO.succeed(a)
    def fail[E](e: E): InputStream[Any, E, Nothing]               = IO.fail(Some(e))
    def halt[E](c: Cause[E]): InputStream[Any, E, Nothing]        = IO.halt(c).mapError(Some(_))
    def die(t: Throwable): InputStream[Any, Nothing, Nothing]     = UIO.die(t)
    def dieMessage(m: String): InputStream[Any, Nothing, Nothing] = UIO.dieMessage(m)
    def done[E, A](e: Exit[E, A]): InputStream[Any, E, A]         = IO.done(e).mapError(Some(_))
  }

  /**
   * Describes an effectful fold over the elements of the stream. Conceptually it is an effectful state
   * transformation function in the `R` environment that can fail with a checked error `E`. It consumes
   * stream elements of type `A` and keeps state of type `S`. It consists of three main components:
   *
   *   1. The current state represented by `S`.
   *   2. A continuation signal function (`S => Boolean`). Decides whether the fold should continue based
   *      on the current state.
   *   3. Effectful step function (`(S, A) => ZIO[R, E, S]`) which takes as arguments the current state,
   *      the current stream element and effectfully produces the next state.
   */
  type Fold[R, E, +A, S] = ZManaged[R, Nothing, (S, S => Boolean, (S, A) => ZIO[R, E, S]) => ZManaged[R, E, S]]

  implicit class unTake[-R, +E, +A](val s: ZStream[R, E, Take[E, A]]) extends AnyVal {
    def unTake: ZStream[R, E, A] =
      s.mapM(t => Take.option(UIO.succeed(t))).collectWhile { case Some(v) => v }
  }

  /**
   * The empty stream
   */
  final val empty: Stream[Nothing, Nothing] =
    new Stream[Nothing, Nothing] {
      def fold[R, E, A, S]: Fold[R, E, A, S] = foldDefault

      override def process: Managed[Nothing, InputStream[Any, Nothing, Nothing]] =
        ZManaged.succeed(InputStream.end)
    }

  /**
   * The stream that never produces any value or fails with any error.
   */
  final val never: Stream[Nothing, Nothing] =
    new Stream[Nothing, Nothing] {
      def fold[R, E, A, S]: Fold[R, E, A, S] = foldDefault

      override def process: Managed[Nothing, InputStream[Any, Nothing, Nothing]] =
        ZManaged.succeed(UIO.never)
    }

  /**
   * Creates a pure stream from a variable list of values
   */
  final def apply[A](as: A*): Stream[Nothing, A] = fromIterable(as)

  /**
   * Creates a stream from a single value that will get cleaned up after the
   * stream is consumed
   */
  final def bracket[R, E, A](acquire: ZIO[R, E, A])(release: A => ZIO[R, Nothing, _]): ZStream[R, E, A] =
    managed(ZManaged.make(acquire)(release))

  /**
   * The stream that always dies with `ex`.
   */
  final def die(ex: Throwable): Stream[Nothing, Nothing] =
    halt(Cause.die(ex))

  /**
   * The stream that always dies with an exception described by `msg`.
   */
  final def dieMessage(msg: String): Stream[Nothing, Nothing] =
    halt(Cause.die(new RuntimeException(msg)))

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The optionality of the error type `E` can be used to signal the end of the stream,
   * by setting it to `None`.
   */
  final def effectAsync[R, E, A](
    register: (ZIO[R, Option[E], A] => Unit) => Unit,
    outputBuffer: Int = 16
  ): ZStream[R, E, A] =
    effectAsyncMaybe(callback => {
      register(callback)
      None
    }, outputBuffer)

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The registration of the callback can possibly return the stream synchronously.
   * The optionality of the error type `E` can be used to signal the end of the stream,
   * by setting it to `None`.
   */
  final def effectAsyncMaybe[R, E, A](
    register: (ZIO[R, Option[E], A] => Unit) => Option[ZStream[R, E, A]],
    outputBuffer: Int = 16
  ): ZStream[R, E, A] =
    new ZStream[R, E, A] {
      def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] = foldDefault

      override def process: ZManaged[R, E, InputStream[R, E, A]] =
        for {
          output  <- Queue.bounded[InputStream[R, E, A]](outputBuffer).toManaged(_.shutdown)
          runtime <- ZIO.runtime[R].toManaged_
          maybeStream <- UIO(
                          register(
                            k =>
                              runtime.unsafeRunAsync_(
                                k.foldCauseM(
                                  _.failureOrCause match {
                                    case Left(None)    => output.offer(InputStream.end).unit
                                    case Left(Some(e)) => output.offer(InputStream.fail(e)).unit
                                    case Right(cause)  => output.offer(InputStream.halt(cause)).unit
                                  },
                                  a => output.offer(InputStream.emit(a)).unit
                                )
                              )
                          )
                        ).toManaged_
          is <- maybeStream match {
                 case Some(stream) => output.shutdown.toManaged_ *> stream.process
                 case None         => ZManaged.succeed(output.take.flatten)
               }
        } yield is
    }

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times
   * The registration of the callback itself returns an effect. The optionality of the
   * error type `E` can be used to signal the end of the stream, by setting it to `None`.
   */
  final def effectAsyncM[R, E, A](
    register: (ZIO[R, Option[E], A] => Unit) => ZIO[R, E, _],
    outputBuffer: Int = 16
  ): ZStream[R, E, A] =
    new ZStream[R, E, A] {
      def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] = foldDefault

      override def process: ZManaged[R, E, InputStream[R, E, A]] =
        for {
          output  <- Queue.bounded[InputStream[R, E, A]](outputBuffer).toManaged(_.shutdown)
          runtime <- ZIO.runtime[R].toManaged_
          _ <- register(
                k =>
                  runtime.unsafeRunAsync_(
                    k.foldCauseM(
                      _.failureOrCause match {
                        case Left(None)    => output.offer(InputStream.end).unit
                        case Left(Some(e)) => output.offer(InputStream.fail(e)).unit
                        case Right(cause)  => output.offer(InputStream.halt(cause)).unit
                      },
                      a => output.offer(InputStream.emit(a)).unit
                    )
                  )
              ).toManaged_
        } yield output.take.flatten
    }

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The registration of the callback returns either a canceler or synchronously returns a stream.
   * The optionality of the error type `E` can be used to signal the end of the stream, by
   * setting it to `None`.
   */
  final def effectAsyncInterrupt[R, E, A](
    register: (ZIO[R, Option[E], A] => Unit) => Either[Canceler, ZStream[R, E, A]],
    outputBuffer: Int = 16
  ): ZStream[R, E, A] =
    new ZStream[R, E, A] {
      def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] = foldDefault

      override def process: ZManaged[R, E, InputStream[R, E, A]] =
        for {
          output  <- Queue.bounded[InputStream[R, E, A]](outputBuffer).toManaged(_.shutdown)
          runtime <- ZIO.runtime[R].toManaged_
          eitherStream <- UIO(
                           register(
                             k =>
                               runtime.unsafeRunAsync_(
                                 k.foldCauseM(
                                   _.failureOrCause match {
                                     case Left(None)    => output.offer(InputStream.end).unit
                                     case Left(Some(e)) => output.offer(InputStream.fail(e)).unit
                                     case Right(cause)  => output.offer(InputStream.halt(cause)).unit
                                   },
                                   a => output.offer(InputStream.emit(a)).unit
                                 )
                               )
                           )
                         ).toManaged_
          is <- eitherStream match {
                 case Left(canceler) =>
                   ZManaged.succeed(output.take.flatten).ensuring(canceler)
                 case Right(stream) => output.shutdown.toManaged_ *> stream.process
               }
        } yield is
    }

  /**
   * The stream that always fails with `error`
   */
  final def fail[E](error: E): Stream[E, Nothing] =
    halt(Cause.fail(error))

  /**
   * Creates an empty stream that never fails and executes the finalizer when it ends.
   */
  final def finalizer[R](finalizer: ZIO[R, Nothing, _]): ZStream[R, Nothing, Nothing] =
    new ZStream[R, Nothing, Nothing] {
      def fold[R1 <: R, E, A1, S]: Fold[R1, E, A1, S] = foldDefault

      override def process: ZManaged[R, Nothing, InputStream[R, Nothing, Nothing]] =
        for {
          finalizerRef <- Ref.make[ZIO[R, Nothing, Any]](UIO.unit).toManaged_
          _            <- ZManaged.finalizer[R](finalizerRef.get.flatten)
          pull         = (finalizerRef.set(finalizer) *> InputStream.end).uninterruptible
        } yield pull
    }

  /**
   * Flattens nested streams.
   */
  final def flatten[R, E, A](fa: ZStream[R, E, ZStream[R, E, A]]): ZStream[R, E, A] =
    fa.flatMap(identity)

  /**
   * Flattens a stream of streams into a stream by executing a non-deterministic
   * concurrent merge. Up to `n` streams may be consumed in parallel and up to
   * `outputBuffer` elements may be buffered by this operator.
   */
  final def flattenPar[R, E, A](n: Int, outputBuffer: Int = 16)(
    fa: ZStream[R, E, ZStream[R, E, A]]
  ): ZStream[R, E, A] =
    fa.flatMapPar(n, outputBuffer)(identity)

  /**
   * Creates a stream from a [[zio.Chunk]] of values
   */
  final def fromChunk[@specialized A](c: Chunk[A]): Stream[Nothing, A] =
    new Stream[Nothing, A] {
      def fold[R, E, A1 >: A, S]: Fold[R, E, A1, S] = foldDefault

      override def process: Managed[Nothing, InputStream[Any, Nothing, A]] =
        for {
          index <- Ref.make(0).toManaged_
          len   = c.length
        } yield index.get.flatMap { i =>
          if (i >= len) InputStream.end
          else index.set(i + 1) *> InputStream.emit(c(i))
        }
    }

  /**
   * Creates a stream from an effect producing a value of type `A`
   */
  final def fromEffect[R, E, A](fa: ZIO[R, E, A]): ZStream[R, E, A] =
    managed(fa.toManaged_)

  /**
   * Creates a stream from an effect producing a value of type `A` which repeats forever
   */
  final def repeatEffect[R, E, A](fa: ZIO[R, E, A]): ZStream[R, E, A] =
    fromEffect(fa).forever

  /**
   * Creates a stream from an effect producing a value of type `A` which repeats using the specified schedule
   */
  final def repeatEffectWith[R, E, A](
    fa: ZIO[R, E, A],
    schedule: ZSchedule[R, Unit, _]
  ): ZStream[R with Clock, E, A] =
    fromEffect(fa).repeat(schedule)

  /**
   * Creates a stream from an iterable collection of values
   */
  final def fromIterable[A](as: Iterable[A]): Stream[Nothing, A] =
    new ZStream[Any, Nothing, A] {
      override def fold[R1, E1, A1 >: A, S]: Fold[R1, E1, A1, S] = foldDefault

      override def process =
        for {
          it <- ZManaged.effectTotal(as.iterator)
          pull = UIO {
            if (it.hasNext) InputStream.emit(it.next)
            else InputStream.end
          }.flatten
        } yield pull
    }

  /**
   * Creates a stream from a [[zio.ZQueue]] of values
   */
  final def fromQueue[R, E, A](queue: ZQueue[_, _, R, E, _, A]): ZStream[R, E, A] =
    unfoldM(()) { _ =>
      queue.take
        .map(a => Some((a, ())))
        .foldCauseM(
          cause =>
            // Dequeueing from a shutdown queue will result in interruption,
            // so use that to signal the stream's end.
            if (cause.interrupted) ZIO.succeed(None)
            else ZIO.halt(cause),
          ZIO.succeed
        )
    }

  /**
   * The stream that always halts with `cause`.
   */
  final def halt[E](cause: Cause[E]): ZStream[Any, E, Nothing] = fromEffect(ZIO.halt(cause))

  /**
   * Creates a single-valued stream from a managed resource
   */
  final def managed[R, E, A](managed: ZManaged[R, E, A]): ZStream[R, E, A] =
    new ZStream[R, E, A] {
      def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] = foldDefault

      override def process: ZManaged[R, E, InputStream[R, E, A]] =
        for {
          doneRef      <- Ref.make(false).toManaged_
          finalizerRef <- Ref.make[Exit[_, _] => ZIO[R, Nothing, Any]](_ => UIO.unit).toManaged_
          _            <- ZManaged.finalizerExit(e => finalizerRef.get.flatMap(_.apply(e)))
          pull = ZIO.uninterruptibleMask { restore =>
            doneRef.get.flatMap { done =>
              if (done) InputStream.end
              else
                (for {
                  reservation <- managed.reserve
                  _           <- finalizerRef.set(reservation.release)
                  a           <- restore(reservation.acquire)
                  _           <- doneRef.set(true)
                } yield a).mapError(Some(_))
            }
          }
        } yield pull
    }

  /**
   * Merges a variable list of streams in a non-deterministic fashion.
   * Up to `n` streams may be consumed in parallel and up to
   * `outputBuffer` elements may be buffered by this operator.
   */
  final def mergeAll[R, E, A](n: Int, outputBuffer: Int = 16)(
    streams: ZStream[R, E, A]*
  ): ZStream[R, E, A] =
    flattenPar(n, outputBuffer)(fromIterable(streams))

  /**
   * Constructs a stream from a range of integers (inclusive).
   */
  final def range(min: Int, max: Int): Stream[Nothing, Int] =
    unfold(min)(cur => if (cur > max) None else Some((cur, cur + 1)))

  /**
   * Creates a single-valued pure stream
   */
  final def succeed[A](a: A): Stream[Nothing, A] =
    new Stream[Nothing, A] {
      def fold[R, E, A1 >: A, S]: Fold[R, E, A1, S] = foldDefault

      override def process =
        for {
          done <- Ref.make(false).toManaged_
        } yield done.get.flatMap {
          if (_) InputStream.end
          // TODO: guard against interruption
          else done.set(true) *> InputStream.emit(a)
        }
    }

  @deprecated("use succeed", "1.0.0")
  final def succeedLazy[A](a: => A): Stream[Nothing, A] =
    succeed(a)

  /**
   * Creates a stream by peeling off the "layers" of a value of type `S`
   */
  final def unfold[S, A](s: S)(f0: S => Option[(A, S)]): Stream[Nothing, A] =
    unfoldM(s)(s => ZIO.succeed(f0(s)))

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  final def unfoldM[R, E, A, S](s: S)(f0: S => ZIO[R, E, Option[(A, S)]]): ZStream[R, E, A] =
    new ZStream[R, E, A] {
      def fold[R1 <: R, E1 >: E, A1 >: A, S2]: Fold[R1, E1, A1, S2] = foldDefault

      override def process: ZManaged[R, E, InputStream[R, E, A]] =
        for {
          ref <- Ref.make(s).toManaged_
        } yield ref.get
          .flatMap(f0)
          .foldM(
            e => InputStream.fail(e),
            opt =>
              opt match {
                case Some((a, s)) => ref.set(s) *> InputStream.emit(a)
                case None         => InputStream.end
              }
          )
    }

  /**
   * Creates a stream produced from an effect
   */
  final def unwrap[R, E, A](fa: ZIO[R, E, ZStream[R, E, A]]): ZStream[R, E, A] =
    flatten(fromEffect(fa))
}
