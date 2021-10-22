/*
 * Copyright 2018-2021 John A. De Goes and the ZIO Contributors
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
import zio.internal.UniqueKey
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.TQueue
import zio.stream.internal.Utils.zipChunks
import zio.stream.internal.{ZInputStream, ZReader}

import java.{util => ju}
import scala.reflect.ClassTag

/**
 * A `ZStream[R, E, O]` is a description of a program that, when evaluated,
 * may emit 0 or more values of type `O`, may fail with errors of type `E`
 * and uses an environment of type `R`. One way to think of `ZStream` is as a
 * `ZIO` program that could emit multiple values.
 *
 * Another analogue to `ZStream` is an imperative iterator:
 * {{{
 * trait Iterator[A] {
 *   def next: A
 * }
 * }}}
 *
 * This data type can emit multiple `A` values through multiple calls to `next`.
 * Similarly, embedded inside every `ZStream` is a ZIO program: `ZIO[R, Option[E], Chunk[O]]`.
 * This program will be repeatedly evaluated as part of the stream execution. For
 * every evaluation, it will emit a chunk of values or end with an optional failure.
 * A failure of type `None` signals the end of the stream.
 *
 * `ZStream` is a purely functional *pull* based stream. Pull based streams offer
 * inherent laziness and backpressure, relieving users of the need to manage buffers
 * between operators. As an optimization, `ZStream` does not emit single values, but
 * rather [[zio.Chunk]] values. This allows the cost of effect evaluation to be
 * amortized and most importantly, keeps primitives unboxed. This allows `ZStream`
 * to model network and file-based stream processing extremely efficiently.
 *
 * The last important attribute of `ZStream` is resource management: it makes
 * heavy use of [[ZManaged]] to manage resources that are acquired
 * and released during the stream's lifetime.
 *
 * `ZStream` forms a monad on its `O` type parameter, and has error management
 * facilities for its `E` type parameter, modeled similarly to [[ZIO]] (with some
 * adjustments for the multiple-valued nature of `ZStream`). These aspects allow
 * for rich and expressive composition of streams.
 *
 * The current encoding of `ZStream` is *not* safe for recursion. `ZStream` programs
 * that are defined in terms of themselves will leak memory. For example, the following
 * implementation of [[ZStream#forever]] is not heap-safe:
 * {{{
 * def forever = self ++ forever
 * }}}
 *
 * Instead, recursive operators must be defined explicitly. See the definition of
 * [[ZStream#forever]] for an example. This limitation will be lifted in the future.
 */
abstract class ZStream[-R, +E, +O](val process: ZManaged[R, Nothing, ZIO[R, Option[E], Chunk[O]]])
    extends ZStreamVersionSpecific[R, E, O] { self =>

  import ZStream.{BufferedPull, Pull, TerminationStrategy}

  /**
   * Symbolic alias for [[ZStream#cross]].
   */
  final def <*>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit
    zippable: Zippable[O, O2],
    trace: ZTraceElement
  ): ZStream[R1, E1, zippable.Out] =
    self cross that

  /**
   * Symbolic alias for [[ZStream#crossLeft]].
   */
  final def <*[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    self crossLeft that

  /**
   * Symbolic alias for [[ZStream#crossRight]].
   */
  final def *>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    self crossRight that

  /**
   * Symbolic alias for [[ZStream#zip]].
   */
  final def <&>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit
    zippable: Zippable[O, O2],
    trace: ZTraceElement
  ): ZStream[R1, E1, zippable.Out] =
    self zip that

  /**
   * Symbolic alias for [[ZStream#zipLeft]].
   */
  final def <&[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    self zipLeft that

  /**
   * Symbolic alias for [[ZStream#zipRight]].
   */
  final def &>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    self zipRight that

  /**
   * Symbolic alias for [[ZStream#flatMap]].
   */
  @deprecated("use flatMap", "2.0.0")
  def >>=[R1 <: R, E1 >: E, O2](f0: O => ZStream[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    flatMap(f0)

  /**
   * Symbolic alias for [[ZStream#transduce]].
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, O3](transducer: ZTransducer[R1, E1, O2, O3])(implicit trace: ZTraceElement) =
    transduce(transducer)

  /**
   * Symbolic alias for [[[zio.stream.ZStream!.run[R1<:R,E1>:E,B]*]]].
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, Z](sink: ZSink[R1, E1, O2, Any, Z])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E1, Z] =
    self.run(sink)

  /**
   * Symbolic alias for [[ZStream#concat]].
   */
  def ++[R1 <: R, E1 >: E, O1 >: O](that: => ZStream[R1, E1, O1])(implicit trace: ZTraceElement): ZStream[R1, E1, O1] =
    self concat that

  /**
   * Symbolic alias for [[ZStream#orElse]].
   */
  final def <>[R1 <: R, E2, O1 >: O](
    that: => ZStream[R1, E2, O1]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R1, E2, O1] =
    self orElse that

  /**
   * Returns a stream that submerges the error case of an `Either` into the `ZStream`.
   */
  final def absolve[R1 <: R, E1, O1](implicit
    ev: ZStream[R, E, O] <:< ZStream[R1, E1, Either[E1, O1]],
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    ZStream.absolve(ev(self))

  /**
   * Applies an aggregator to the stream, which converts one or more elements
   * of type `A` into elements of type `B`.
   */
  def aggregate[R1 <: R, E1 >: E, P](
    transducer: ZTransducer[R1, E1, O, P]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, P] =
    ZStream {
      for {
        pull <- self.process
        push <- transducer.push
        done <- ZRef.makeManaged(false)
        run = {
          def go: ZIO[R1, Option[E1], Chunk[P]] = done.get.flatMap {
            if (_)
              Pull.end
            else
              pull
                .foldZIO(
                  _.fold(done.set(true) *> push(None).asSomeError)(Pull.fail(_)),
                  os => push(Some(os)).asSomeError
                )
                .flatMap(ps => if (ps.isEmpty) go else IO.succeedNow(ps))
          }

          go
        }
      } yield run
    }

  /**
   * Aggregates elements of this stream using the provided sink for as long
   * as the downstream operators on the stream are busy.
   *
   * This operator divides the stream into two asynchronous "islands". Operators upstream
   * of this operator run on one fiber, while downstream operators run on another. Whenever
   * the downstream fiber is busy processing elements, the upstream fiber will feed elements
   * into the sink until it signals completion.
   *
   * Any transducer can be used here, but see [[ZTransducer.foldWeightedM]] and [[ZTransducer.foldUntilM]] for
   * transducers that cover the common usecases.
   */
  final def aggregateAsync[R1 <: R, E1 >: E, P](
    transducer: ZTransducer[R1, E1, O, P]
  )(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, P] =
    aggregateAsyncWithin(transducer, Schedule.forever)

  /**
   * Uses `aggregateAsyncWithinEither` but only returns the `Right` results.
   *
   * @param transducer used for the aggregation
   * @param schedule signalling for when to stop the aggregation
   * @tparam R1 environment type
   * @tparam E1 error type
   * @tparam P type of the value produced by the given transducer and consumed by the given schedule
   * @return `ZStream[R1, E1, P]`
   */
  final def aggregateAsyncWithin[R1 <: R, E1 >: E, P](
    transducer: ZTransducer[R1, E1, O, P],
    schedule: Schedule[R1, Chunk[P], Any]
  )(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, P] =
    aggregateAsyncWithinEither(transducer, schedule).collect { case Right(v) =>
      v
    }

  /**
   * Aggregates elements using the provided transducer until it signals completion, or the
   * delay signalled by the schedule has passed.
   *
   * This operator divides the stream into two asynchronous islands. Operators upstream
   * of this operator run on one fiber, while downstream operators run on another. Elements
   * will be aggregated by the transducer until the downstream fiber pulls the aggregated value,
   * or until the schedule's delay has passed.
   *
   * Aggregated elements will be fed into the schedule to determine the delays between
   * pulls.
   *
   * @param transducer used for the aggregation
   * @param schedule signalling for when to stop the aggregation
   * @tparam R1 environment type
   * @tparam E1 error type
   * @tparam P type of the value produced by the given transducer and consumed by the given schedule
   * @tparam Q type of the value produced by the given schedule
   * @return `ZStream[R1, E1, Either[Q, P]]`
   */
  final def aggregateAsyncWithinEither[R1 <: R, E1 >: E, P, Q](
    transducer: ZTransducer[R1, E1, O, P],
    schedule: Schedule[R1, Chunk[P], Q]
  )(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, Either[Q, P]] =
    ZStream {
      for {
        pull          <- self.process
        push          <- transducer.push
        handoff       <- ZStream.Handoff.make[Take[E1, O]].toManaged
        raceNextTime  <- ZRef.makeManaged(false)
        waitingFiber  <- ZRef.makeManaged[Option[Fiber[Nothing, Take[E1, O]]]](None)
        scheduleFiber <- ZRef.makeManaged[Option[Fiber[Nothing, Option[Q]]]](None)
        sdriver       <- schedule.driver.toManaged
        lastChunk     <- ZRef.makeManaged[Chunk[P]](Chunk.empty)
        producer       = Take.fromPull(pull).repeatWhileZIO(take => handoff.offer(take).as(take.isSuccess))
        consumer = {
          // Advances the state of the schedule, which may or may not terminate
          val updateSchedule: URIO[R1 with Has[Clock], Option[Q]] =
            lastChunk.get.flatMap(sdriver.next).fold(_ => None, Some(_))

          // Waiting for the normal output of the producer
          val waitForProducer: ZIO[R1, Nothing, Take[E1, O]] =
            waitingFiber.getAndSet(None).flatMap {
              case None      => handoff.take
              case Some(fib) => fib.join
            }

          // Waiting for the scheduler
          val waitForSchedule: URIO[R1 with Has[Clock], Option[Q]] =
            scheduleFiber.getAndSet(None).flatMap {
              case None      => updateSchedule
              case Some(fib) => fib.join
            }

          def updateLastChunk(take: Take[_, P]): UIO[Unit] =
            take.tap(lastChunk.set(_))

          def setScheduleOnEmptyChunk(
            take: Take[E1, P],
            optFiber: Option[Fiber[Nothing, Option[Q]]]
          ): UIO[Unit] =
            optFiber.fold(ZIO.unit) { scheduleWaiting =>
              if (take.exit.fold(_ => true, _.nonEmpty))
                scheduleWaiting.interrupt.unit
              else
                scheduleFiber.set(Some(scheduleWaiting))
            }

          def handleTake(
            take: Take[E1, O],
            scheduleWaiting: Option[Fiber[Nothing, Option[Q]]]
          ): Pull[R1, E1, Take[E1, Either[Nothing, P]]] =
            take
              .foldZIO(
                scheduleWaiting.fold(ZIO.unit)(_.interrupt.unit) *> push(None).map(ps =>
                  Chunk(Take.chunk(ps.map(Right(_))), Take.end)
                ),
                cause => ZIO.failCause(cause),
                os =>
                  Take
                    .fromPull(push(Some(os)).asSomeError)
                    .flatMap { take =>
                      setScheduleOnEmptyChunk(take, scheduleWaiting) *>
                        updateLastChunk(take).as(Chunk.single(take.map(Right(_))))
                    }
              )
              .mapError(Some(_))

          def go(race: Boolean): ZIO[R1 with Has[Clock], Option[E1], Chunk[Take[E1, Either[Q, P]]]] =
            if (!race)
              waitForProducer.flatMap(handleTake(_, None)) <* raceNextTime.set(true)
            else
              waitForSchedule
                .raceWith[R1 with Has[Clock], Nothing, Option[E1], Take[E1, O], Chunk[Take[E1, Either[Q, P]]]](
                  waitForProducer
                )(
                  (scheduleDone, producerWaiting) =>
                    ZIO.done(scheduleDone).flatMap {
                      case None =>
                        for {
                          lastQ         <- lastChunk.set(Chunk.empty) *> sdriver.last.orDie <* sdriver.reset
                          scheduleResult = Take.single(Left(lastQ))
                          take          <- Take.fromPull(push(None).asSomeError).tap(updateLastChunk)
                          _             <- raceNextTime.set(false)
                          _             <- waitingFiber.set(Some(producerWaiting))
                        } yield Chunk(scheduleResult, take.map(Right(_)))

                      case Some(_) =>
                        for {
                          ps <- Take.fromPull(push(None).asSomeError).tap(updateLastChunk)
                          _  <- raceNextTime.set(false)
                          _  <- waitingFiber.set(Some(producerWaiting))
                        } yield Chunk.single(ps.map(Right(_)))
                    },
                  (
                    producerDone,
                    scheduleWaiting
                  ) => handleTake(Take(producerDone.flatMap(_.exit)), Some(scheduleWaiting)),
                  Some(ZScope.global)
                )

          raceNextTime.get
            .flatMap(go)
            .onInterrupt {
              waitingFiber.get.flatMap(_.map(_.interrupt).getOrElse(ZIO.unit)) *>
                scheduleFiber.get.flatMap(_.map(_.interrupt).getOrElse(ZIO.unit))
            }
        }

        _ <- producer.forkManaged
      } yield consumer
    }.flattenTake

  /**
   * Maps the success values of this stream to the specified constant value.
   */
  def as[O2](o2: => O2)(implicit trace: ZTraceElement): ZStream[R, E, O2] =
    map(_ => o2)

  /**
   * Returns a stream whose failure and success channels have been mapped by
   * the specified pair of functions, `f` and `g`.
   */
  @deprecated("use mapBoth", "2.0.0")
  def bimap[E1, O1](f: E => E1, g: O => O1)(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R, E1, O1] =
    mapBoth(f, g)

  /**
   * Fan out the stream, producing a list of streams that have the same
   * elements as this stream. The driver stream will only ever advance the
   * `maximumLag` chunks before the slowest downstream stream.
   */
  final def broadcast(n: Int, maximumLag: Int)(implicit
    trace: ZTraceElement
  ): ZManaged[R, Nothing, List[ZStream[Any, E, O]]] =
    self
      .broadcastedQueues(n, maximumLag)
      .map(_.map(ZStream.fromQueueWithShutdown(_).flattenTake))

  /**
   * Fan out the stream, producing a dynamic number of streams that have the
   * same elements as this stream. The driver stream will only ever advance the
   * `maximumLag` chunks before the slowest downstream stream.
   */
  final def broadcastDynamic(
    maximumLag: Int
  )(implicit trace: ZTraceElement): ZManaged[R, Nothing, ZStream[Any, E, O]] =
    self
      .broadcastedQueuesDynamic(maximumLag)
      .map(ZStream.managed(_).flatMap(ZStream.fromQueue(_)).flattenTake)

  /**
   * Converts the stream to a managed list of queues. Every value will be
   * replicated to every queue with the slowest queue being allowed to buffer
   * `maximumLag` chunks before the driver is back pressured.
   *
   * Queues can unsubscribe from upstream by shutting down.
   */
  final def broadcastedQueues(
    n: Int,
    maximumLag: Int
  )(implicit trace: ZTraceElement): ZManaged[R, Nothing, List[Dequeue[Take[E, O]]]] =
    for {
      hub    <- Hub.bounded[Take[E, O]](maximumLag).toManaged
      queues <- ZManaged.collectAll(List.fill(n)(hub.subscribe))
      _      <- self.intoHubManaged(hub).fork
    } yield queues

  /**
   * Converts the stream to a managed dynamic amount of queues. Every chunk
   * will be replicated to every queue with the slowest queue being allowed to
   * buffer `maximumLag` chunks before the driver is back pressured.
   *
   * Queues can unsubscribe from upstream by shutting down.
   */
  final def broadcastedQueuesDynamic(
    maximumLag: Int
  )(implicit trace: ZTraceElement): ZManaged[R, Nothing, ZManaged[Any, Nothing, Dequeue[Take[E, O]]]] =
    toHub(maximumLag).map(_.subscribe)

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * up to `capacity` chunks in a queue.
   *
   * @note Prefer capacities that are powers of 2 for better performance.
   */
  final def buffer(capacity: Int)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        done  <- Ref.make(false).toManaged
        queue <- self.toQueue(capacity)
        pull = done.get.flatMap {
                 if (_) Pull.end
                 else
                   queue.take.flatMap(_.done).catchSome { case None =>
                     done.set(true) *> Pull.end
                   }
               }
      } yield pull
    }

  private final def bufferSignal[E1 >: E, O1 >: O](
    queue: Queue[(Take[E1, O1], Promise[Nothing, Unit])]
  )(implicit trace: ZTraceElement): ZManaged[R, Nothing, ZIO[R, Option[E1], Chunk[O1]]] =
    for {
      as    <- self.process
      start <- Promise.make[Nothing, Unit].toManaged
      _     <- start.succeed(()).toManaged
      ref   <- Ref.make(start).toManaged
      done  <- Ref.make(false).toManaged
      upstream = {
        def offer(take: Take[E1, O1]): UIO[Unit] =
          take.exit.fold(
            _ =>
              for {
                latch <- ref.get
                _     <- latch.await
                p     <- Promise.make[Nothing, Unit]
                _     <- queue.offer((take, p))
                _     <- ref.set(p)
                _     <- p.await
              } yield (),
            _ =>
              for {
                p     <- Promise.make[Nothing, Unit]
                added <- queue.offer((take, p))
                _     <- ref.set(p).when(added)
              } yield ()
          )

        Take.fromPull(as).tap(take => offer(take)).repeatWhile(_ != Take.end).unit
      }
      _ <- upstream.toManaged.fork
      pull = done.get.flatMap {
               if (_) Pull.end
               else
                 queue.take.flatMap { case (take, p) =>
                   p.succeed(()) *> done.set(true).when(take == Take.end) *> take.done
                 }
             }
    } yield pull

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * up to `capacity` elements in a dropping queue.
   *
   * @note Prefer capacities that are powers of 2 for better performance.
   */
  final def bufferDropping(capacity: Int)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        queue <- Queue.dropping[(Take[E, O], Promise[Nothing, Unit])](capacity).toManagedWith(_.shutdown)
        pull  <- bufferSignal(queue)
      } yield pull
    }

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * up to `capacity` elements in a sliding queue.
   *
   * @note Prefer capacities that are powers of 2 for better performance.
   */
  final def bufferSliding(capacity: Int)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        queue <- Queue.sliding[(Take[E, O], Promise[Nothing, Unit])](capacity).toManagedWith(_.shutdown)
        pull  <- bufferSignal(queue)
      } yield pull
    }

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * elements into an unbounded queue.
   */
  final def bufferUnbounded(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        done  <- ZRef.make(false).toManaged
        queue <- self.toQueueUnbounded
        pull = done.get.flatMap {
                 if (_) Pull.end
                 else
                   queue.take.flatMap(_.foldZIO(done.set(true) *> Pull.end, Pull.failCause, Pull.emit(_)))
               }
      } yield pull
    }

  /**
   * Switches over to the stream produced by the provided function in case this one
   * fails with a typed error.
   */
  final def catchAll[R1 <: R, E2, O1 >: O](
    f: E => ZStream[R1, E2, O1]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R1, E2, O1] =
    catchAllCause(_.failureOrCause.fold(f, ZStream.failCause(_)))

  /**
   * Switches over to the stream produced by the provided function in case this one
   * fails. Allows recovery from all causes of failure, including interruption if the
   * stream is uninterruptible.
   */
  final def catchAllCause[R1 <: R, E2, O1 >: O](
    f: Cause[E] => ZStream[R1, E2, O1]
  )(implicit trace: ZTraceElement): ZStream[R1, E2, O1] = {
    sealed abstract class State[+E0]
    case object NotStarted                      extends State[Nothing]
    case class Self[E0](pull: Pull[R1, E0, O1]) extends State[E0]
    case class Other(pull: Pull[R1, E2, O1])    extends State[Nothing]

    ZStream {
      for {
        finalizerRef <- ZManaged.finalizerRef(ZManaged.Finalizer.noop)
        ref          <- Ref.make[State[E]](NotStarted).toManaged
        pull = {
          def closeCurrent(cause: Cause[Any]) =
            finalizerRef.getAndSet(ZManaged.Finalizer.noop).flatMap(_.apply(Exit.failCause(cause))).uninterruptible

          def open[R, E0, O](stream: ZStream[R, E0, O])(asState: Pull[R, E0, O] => State[E]) =
            ZIO.uninterruptibleMask { restore =>
              ZManaged.ReleaseMap.make.flatMap { releaseMap =>
                finalizerRef.set(releaseMap.releaseAll(_, ExecutionStrategy.Sequential)) *>
                  restore(stream.process.zio)
                    .provideSome[R]((_, releaseMap))
                    .map(_._2)
                    .tap(pull => ref.set(asState(pull)))
              }
            }

          def failover(cause: Cause[Option[E]]) =
            Cause.flipCauseOption(cause) match {
              case None => ZIO.fail(None)
              case Some(cause) =>
                closeCurrent(cause) *> open(f(cause))(Other(_)).flatten
            }

          ref.get.flatMap {
            case NotStarted  => open(self)(Self(_)).flatten.catchAllCause(failover)
            case Self(pull)  => pull.catchAllCause(failover)
            case Other(pull) => pull
          }
        }
      } yield pull
    }
  }

  /**
   * Switches over to the stream produced by the provided function in case this one
   * fails with some typed error.
   */
  final def catchSome[R1 <: R, E1 >: E, O1 >: O](pf: PartialFunction[E, ZStream[R1, E1, O1]])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    catchAll(pf.applyOrElse[E, ZStream[R1, E1, O1]](_, ZStream.fail(_)))

  /**
   * Switches over to the stream produced by the provided function in case this one
   * fails with some errors. Allows recovery from all causes of failure, including interruption if the
   * stream is uninterruptible.
   */
  final def catchSomeCause[R1 <: R, E1 >: E, O1 >: O](
    pf: PartialFunction[Cause[E], ZStream[R1, E1, O1]]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O1] =
    catchAllCause(pf.applyOrElse[Cause[E], ZStream[R1, E1, O1]](_, ZStream.failCause(_)))

  /**
   * Returns a new stream that only emits elements that are not equal to the
   * previous element emitted, using natural equality to determine whether two
   * elements are equal.
   */
  def changes(implicit trace: ZTraceElement): ZStream[R, E, O] =
    changesWith(_ == _)

  /**
   * Returns a new stream that only emits elements that are not equal to the
   * previous element emitted, using the specified function to determine
   * whether two elements are equal.
   */
  def changesWith(f: (O, O) => Boolean)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        ref <- Ref.makeManaged[Option[O]](None)
        p   <- self.process
        pull = for {
                 z0 <- ref.get
                 c  <- p
                 (z1, chunk) = c.foldLeft[(Option[O], Chunk[O])]((z0, Chunk.empty)) {
                                 case ((Some(o), os), o1) if (f(o, o1)) => (Some(o1), os)
                                 case ((_, os), o1)                     => (Some(o1), os :+ o1)
                               }
                 _ <- ref.set(z1)
               } yield chunk
      } yield pull
    }

  /**
   * Returns a new stream that only emits elements that are not equal to the
   * previous element emitted, using the specified effectual function to
   * determine whether two elements are equal.
   */
  def changesWithZIO[R1 <: R, E1 >: E](
    f: (O, O) => ZIO[R1, E1, Boolean]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    ZStream {
      for {
        ref <- Ref.makeManaged[Option[O]](None)
        p   <- self.process
        pull = for {
                 z0 <- ref.get
                 c  <- p
                 tuple <- c.foldZIO[R1, E1, (Option[O], Chunk[O])]((z0, Chunk.empty)) {
                            case ((Some(o), os), o1) =>
                              f(o, o1).map(b => if (b) (Some(o1), os) else (Some(o1), os :+ o1))
                            case ((_, os), o1) =>
                              ZIO.succeedNow((Some(o1), os :+ o1))
                          }.asSomeError
                 (z1, chunk) = tuple
                 _          <- ref.set(z1)
               } yield chunk
      } yield pull
    }

  /**
   * Re-chunks the elements of the stream into chunks of
   * `n` elements each.
   * The last chunk might contain less than `n` elements
   */
  @deprecated("use rechunk", "2.0.0")
  def chunkN(n: Int)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    rechunk(n)

  /**
   * Exposes the underlying chunks of the stream as a stream of chunks of
   * elements.
   */
  def chunks(implicit trace: ZTraceElement): ZStream[R, E, Chunk[O]] =
    mapChunks(Chunk.single)

  /**
   * Performs a filter and map in a single step.
   */
  def collect[O1](pf: PartialFunction[O, O1])(implicit trace: ZTraceElement): ZStream[R, E, O1] =
    mapChunks(_.collect(pf))

  /**
   * Filters any `Right` values.
   */
  final def collectLeft[L1, O1](implicit ev: O <:< Either[L1, O1], trace: ZTraceElement): ZStream[R, E, L1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Either[L1, O1]]].collect { case Left(a) => a }
  }

  /**
   * Filters any 'None' values.
   */
  final def collectSome[O1](implicit ev: O <:< Option[O1], trace: ZTraceElement): ZStream[R, E, O1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Option[O1]]].collect { case Some(a) => a }
  }

  /**
   * Filters any `Exit.Failure` values.
   */
  final def collectSuccess[L1, O1](implicit ev: O <:< Exit[L1, O1], trace: ZTraceElement): ZStream[R, E, O1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Exit[L1, O1]]].collect { case Exit.Success(a) => a }
  }

  /**
   * Filters any `Left` values.
   */
  final def collectRight[L1, O1](implicit ev: O <:< Either[L1, O1], trace: ZTraceElement): ZStream[R, E, O1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Either[L1, O1]]].collect { case Right(a) => a }
  }

  /**
   * Performs an effectful filter and map in a single step.
   */
  @deprecated("use collectZIO", "2.0.0")
  final def collectM[R1 <: R, E1 >: E, O1](pf: PartialFunction[O, ZIO[R1, E1, O1]])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    collectZIO(pf)

  /**
   * Performs an effectful filter and map in a single step.
   */
  final def collectZIO[R1 <: R, E1 >: E, O1](
    pf: PartialFunction[O, ZIO[R1, E1, O1]]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O1] =
    ZStream {
      for {
        os    <- self.process.mapZIO(BufferedPull.make(_))
        pfSome = pf.andThen(_.mapBoth(Some(_), Chunk.single(_)))
        pull = {
          def go: ZIO[R1, Option[E1], Chunk[O1]] =
            os.pullElement.flatMap(o => pfSome.applyOrElse(o, (_: O) => go))

          go
        }

      } yield pull
    }

  /**
   * Transforms all elements of the stream for as long as the specified partial function is defined.
   */
  def collectWhile[O2](p: PartialFunction[O, O2])(implicit trace: ZTraceElement): ZStream[R, E, O2] =
    ZStream {
      for {
        chunks  <- self.process
        doneRef <- Ref.make(false).toManaged
        pull = doneRef.get.flatMap { done =>
                 if (done) Pull.end
                 else
                   for {
                     chunk    <- chunks
                     remaining = chunk.collectWhile(p)
                     _        <- doneRef.set(true).when(remaining.length < chunk.length)
                   } yield remaining
               }
      } yield pull
    }

  /**
   * Terminates the stream when encountering the first `Right`.
   */
  final def collectWhileLeft[L1, O1](implicit ev: O <:< Either[L1, O1], trace: ZTraceElement): ZStream[R, E, L1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Either[L1, O1]]].collectWhile { case Left(a) => a }
  }

  /**
   * Effectfully transforms all elements of the stream for as long as the specified partial function is defined.
   */
  @deprecated("use collectWhileZIO", "2.0.0")
  final def collectWhileM[R1 <: R, E1 >: E, O2](pf: PartialFunction[O, ZIO[R1, E1, O2]])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    collectWhileZIO(pf)

  /**
   * Effectfully transforms all elements of the stream for as long as the specified partial function is defined.
   */
  final def collectWhileZIO[R1 <: R, E1 >: E, O2](
    pf: PartialFunction[O, ZIO[R1, E1, O2]]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    ZStream {
      for {
        os   <- self.process.mapZIO(BufferedPull.make(_))
        done <- Ref.make(false).toManaged
        pfIO  = pf.andThen(_.mapBoth(Some(_), Chunk.single(_)))
        pull = done.get.flatMap {
                 if (_) Pull.end
                 else
                   os.pullElement.flatMap(a => pfIO.applyOrElse(a, (_: O) => done.set(true) *> Pull.end))
               }
      } yield pull
    }

  /**
   * Terminates the stream when encountering the first `None`.
   */
  final def collectWhileSome[O1](implicit ev: O <:< Option[O1], trace: ZTraceElement): ZStream[R, E, O1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Option[O1]]].collectWhile { case Some(a) => a }
  }

  /**
   * Terminates the stream when encountering the first `Left`.
   */
  final def collectWhileRight[L1, O1](implicit ev: O <:< Either[L1, O1], trace: ZTraceElement): ZStream[R, E, O1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Either[L1, O1]]].collectWhile { case Right(a) => a }
  }

  /**
   * Terminates the stream when encountering the first `Exit.Failure`.
   */
  final def collectWhileSuccess[L1, O1](implicit ev: O <:< Exit[L1, O1], trace: ZTraceElement): ZStream[R, E, O1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Exit[L1, O1]]].collectWhile { case Exit.Success(a) => a }
  }

  /**
   * Combines the elements from this stream and the specified stream by repeatedly applying the
   * function `f` to extract an element using both sides and conceptually "offer"
   * it to the destination stream. `f` can maintain some internal state to control
   * the combining process, with the initial state being specified by `s`.
   *
   * Where possible, prefer [[ZStream#combineChunks]] for a more efficient implementation.
   */
  final def combine[R1 <: R, E1 >: E, S, O2, O3](that: ZStream[R1, E1, O2])(s: S)(
    f: (S, ZIO[R, Option[E], O], ZIO[R1, Option[E1], O2]) => ZIO[R1, Nothing, Exit[Option[E1], (O3, S)]]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O3] =
    ZStream[R1, E1, O3] {
      for {
        left <- self.process.mapZIO(BufferedPull.make[R, E, O](_)) // type annotation required for Dotty
        right <- that.process.mapZIO(BufferedPull.make[R1, E1, O2](_))
        pull <- ZStream
                  .unfoldZIO(s)(s => f(s, left.pullElement, right.pullElement).flatMap(ZIO.done(_).unsome))
                  .process
      } yield pull
    }

  /**
   * Combines the chunks from this stream and the specified stream by repeatedly applying the
   * function `f` to extract a chunk using both sides and conceptually "offer"
   * it to the destination stream. `f` can maintain some internal state to control
   * the combining process, with the initial state being specified by `s`.
   */
  final def combineChunks[R1 <: R, E1 >: E, S, O2, O3](that: ZStream[R1, E1, O2])(s: S)(
    f: (
      S,
      ZIO[R, Option[E], Chunk[O]],
      ZIO[R1, Option[E1], Chunk[O2]]
    ) => ZIO[R1, Nothing, Exit[Option[E1], (Chunk[O3], S)]]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O3] =
    ZStream[R1, E1, O3] {
      for {
        left  <- self.process
        right <- that.process
        pull <- ZStream
                  .unfoldChunkZIO(s)(s => f(s, left, right).flatMap(ZIO.done(_).unsome))
                  .process
      } yield pull
    }

  /**
   * Concatenates the specified stream with this stream, resulting in a stream
   * that emits the elements from this stream and then the elements from the specified stream.
   */
  def concat[R1 <: R, E1 >: E, O1 >: O](
    that: => ZStream[R1, E1, O1]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O1] =
    ZStream {
      // This implementation is identical to ZStream.concatAll, but specialized so we can
      // maintain laziness on `that`. Laziness on concatenation is important for combinators
      // such as `forever`.
      for {
        currStream   <- Ref.make[ZIO[R1, Option[E1], Chunk[O1]]](Pull.end).toManaged
        switchStream <- ZManaged.switchable[R1, Nothing, ZIO[R1, Option[E1], Chunk[O1]]]
        switched     <- Ref.make(false).toManaged
        _            <- switchStream(self.process).flatMap(currStream.set).toManaged
        pull = {
          def go: ZIO[R1, Option[E1], Chunk[O1]] =
            currStream.get.flatten.catchAllCause {
              Cause.flipCauseOption(_) match {
                case Some(e) => Pull.failCause(e)
                case None =>
                  switched.getAndSet(true).flatMap {
                    if (_) Pull.end
                    else switchStream(that.process).flatMap(currStream.set) *> go
                  }
              }
            }

          go
        }
      } yield pull
    }

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements
   * with a specified function.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def crossWith[R1 <: R, E1 >: E, O2, C](that: ZStream[R1, E1, O2])(f: (O, O2) => C)(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, C] =
    self.flatMap(l => that.map(r => f(l, r)))

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def cross[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit
    zippable: Zippable[O, O2],
    trace: ZTraceElement
  ): ZStream[R1, E1, zippable.Out] =
    (self crossWith that)(zippable.zip(_, _))

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements,
   * but keeps only elements from this stream.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def crossLeft[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O] =
    (self crossWith that)((o, _) => o)

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements,
   * but keeps only elements from the other stream.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def crossRight[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    (self crossWith that)((_, o2) => o2)

  /**
   * More powerful version of `ZStream#broadcast`. Allows to provide a function that determines what
   * queues should receive which elements. The decide function will receive the indices of the queues
   * in the resulting list.
   */
  final def distributedWith[E1 >: E](
    n: Int,
    maximumLag: Int,
    decide: O => UIO[Int => Boolean]
  )(implicit trace: ZTraceElement): ZManaged[R, Nothing, List[Dequeue[Exit[Option[E1], O]]]] =
    Promise.make[Nothing, O => UIO[UniqueKey => Boolean]].toManaged.flatMap { prom =>
      distributedWithDynamic(maximumLag, (o: O) => prom.await.flatMap(_(o)), _ => ZIO.unit).flatMap { next =>
        ZIO.collectAll {
          Range(0, n).map(id => next.map { case (key, queue) => ((key -> id), queue) })
        }.flatMap { entries =>
          val (mappings, queues) =
            entries.foldRight((Map.empty[UniqueKey, Int], List.empty[Dequeue[Exit[Option[E1], O]]])) {
              case ((mapping, queue), (mappings, queues)) =>
                (mappings + mapping, queue :: queues)
            }
          prom.succeed((o: O) => decide(o).map(f => (key: UniqueKey) => f(mappings(key)))).as(queues)
        }.toManaged
      }
    }

  /**
   * More powerful version of `ZStream#distributedWith`. This returns a function that will produce
   * new queues and corresponding indices.
   * You can also provide a function that will be executed after the final events are enqueued in all queues.
   * Shutdown of the queues is handled by the driver.
   * Downstream users can also shutdown queues manually. In this case the driver will
   * continue but no longer backpressure on them.
   */
  final def distributedWithDynamic(
    maximumLag: Int,
    decide: O => UIO[UniqueKey => Boolean],
    done: Exit[Option[E], Nothing] => UIO[Any] = (_: Any) => UIO.unit
  )(implicit trace: ZTraceElement): ZManaged[R, Nothing, UIO[(UniqueKey, Dequeue[Exit[Option[E], O]])]] =
    for {
      queuesRef <- Ref
                     .make[Map[UniqueKey, Queue[Exit[Option[E], O]]]](Map())
                     .toManagedWith(_.get.flatMap(qs => ZIO.foreach(qs.values)(_.shutdown)))
      add <- {
        val offer = (o: O) =>
          for {
            shouldProcess <- decide(o)
            queues        <- queuesRef.get
            _ <- ZIO
                   .foldLeft(queues)(List[UniqueKey]()) { case (acc, (id, queue)) =>
                     if (shouldProcess(id)) {
                       queue
                         .offer(Exit.succeed(o))
                         .foldCauseZIO(
                           {
                             // we ignore all downstream queues that were shut down and remove them later
                             case c if c.isInterrupted => ZIO.succeedNow(id :: acc)
                             case c                    => ZIO.failCause(c)
                           },
                           _ => ZIO.succeedNow(acc)
                         )
                     } else ZIO.succeedNow(acc)
                   }
                   .flatMap(ids => if (ids.nonEmpty) queuesRef.update(_ -- ids) else ZIO.unit)
          } yield ()

        for {
          queuesLock <- Semaphore.make(1).toManaged
          newQueue <- Ref
                        .make[UIO[(UniqueKey, Queue[Exit[Option[E], O]])]] {
                          for {
                            queue <- Queue.bounded[Exit[Option[E], O]](maximumLag)
                            id     = UniqueKey()
                            _     <- queuesRef.update(_ + (id -> queue))
                          } yield (id, queue)
                        }
                        .toManaged
          finalize = (endTake: Exit[Option[E], Nothing]) =>
                       // we need to make sure that no queues are currently being added
                       queuesLock.withPermit {
                         for {
                           // all newly created queues should end immediately
                           _ <- newQueue.set {
                                  for {
                                    queue <- Queue.bounded[Exit[Option[E], O]](1)
                                    _     <- queue.offer(endTake)
                                    id     = UniqueKey()
                                    _     <- queuesRef.update(_ + (id -> queue))
                                  } yield (id, queue)
                                }
                           queues <- queuesRef.get.map(_.values)
                           _ <- ZIO.foreach(queues) { queue =>
                                  queue.offer(endTake).catchSomeCause {
                                    case c if c.isInterrupted => ZIO.unit
                                  }
                                }
                           _ <- done(endTake)
                         } yield ()
                       }
          _ <- self
                 .foreachManaged(offer)
                 .foldCauseManaged(
                   cause => finalize(Exit.failCause(cause.map(Some(_)))).toManaged,
                   _ => finalize(Exit.fail(None)).toManaged
                 )
                 .fork
        } yield queuesLock.withPermit(newQueue.get.flatten)
      }
    } yield add

  /**
   * Converts this stream to a stream that executes its effects but emits no
   * elements. Useful for sequencing effects using streams:
   *
   * {{{
   * (Stream(1, 2, 3).tap(i => ZIO(println(i))) ++
   *   Stream.fromZIO(ZIO(println("Done!"))).drain ++
   *   Stream(4, 5, 6).tap(i => ZIO(println(i)))).run(Sink.drain)
   * }}}
   */
  final def drain(implicit trace: ZTraceElement): ZStream[R, E, Nothing] =
    mapChunks(_ => Chunk.empty)

  /**
   * Drains the provided stream in the background for as long as this stream is running.
   * If this stream ends before `other`, `other` will be interrupted. If `other` fails,
   * this stream will fail with that error.
   */
  final def drainFork[R1 <: R, E1 >: E](
    other: ZStream[R1, E1, Any]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    ZStream.fromZIO(Promise.make[E1, Nothing]).flatMap { bgDied =>
      ZStream
        .managed(other.foreachManaged(_ => ZIO.unit).catchAllCause(bgDied.failCause(_).toManaged).fork) *>
        self.interruptWhen(bgDied)
    }

  /**
   * Drops the specified number of elements from this stream.
   */
  def drop(n: Long)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        chunks     <- self.process
        counterRef <- Ref.make(0L).toManaged
        pull = {
          def go: ZIO[R, Option[E], Chunk[O]] =
            chunks.flatMap { chunk =>
              counterRef.get.flatMap { cnt =>
                if (cnt >= n) ZIO.succeedNow(chunk)
                else if (chunk.size <= (n - cnt)) counterRef.set(cnt + chunk.size) *> go
                else counterRef.set(cnt + (n - cnt)).as(chunk.drop((n - cnt).toInt))
              }
            }

          go
        }
      } yield pull
    }

  /**
   * Drops all elements of the stream until the specified predicate evaluates
   * to `true`.
   */
  final def dropUntil(pred: O => Boolean)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    dropWhile(!pred(_)).drop(1)

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def dropWhile(pred: O => Boolean)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        chunks          <- self.process
        keepDroppingRef <- Ref.make(true).toManaged
        pull = {
          def go: ZIO[R, Option[E], Chunk[O]] =
            chunks.flatMap { chunk =>
              keepDroppingRef.get.flatMap { keepDropping =>
                if (!keepDropping) ZIO.succeedNow(chunk)
                else {
                  val remaining = chunk.dropWhile(pred)
                  val empty     = remaining.length <= 0

                  if (empty) go
                  else keepDroppingRef.set(false).as(remaining)
                }
              }
            }

          go
        }
      } yield pull
    }

  /**
   * Returns a stream whose failures and successes have been lifted into an
   * `Either`. The resulting stream cannot fail, because the failures have
   * been exposed as part of the `Either` success case.
   *
   * @note the stream will end as soon as the first error occurs.
   */
  final def either(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R, Nothing, Either[E, O]] =
    self.map(Right(_)).catchAll(e => ZStream(Left(e)))

  /**
   * Executes the provided finalizer after this stream's finalizers run.
   */
  final def ensuring[R1 <: R](fin: ZIO[R1, Nothing, Any])(implicit trace: ZTraceElement): ZStream[R1, E, O] =
    ZStream(self.process.ensuring(fin))

  /**
   * Executes the provided finalizer before this stream's finalizers run.
   */
  final def ensuringFirst[R1 <: R](fin: ZIO[R1, Nothing, Any])(implicit trace: ZTraceElement): ZStream[R1, E, O] =
    ZStream(self.process.ensuringFirst(fin))

  /**
   * Executes a pure fold over the stream of values - reduces all elements in the stream to a value of type `S`.
   */
  final def fold[S](s: S)(f: (S, O) => S)(implicit trace: ZTraceElement): ZIO[R, E, S] =
    foldWhileManagedZIO(s)(_ => true)((s, a) => ZIO.succeedNow(f(s, a))).use(ZIO.succeedNow)

  /**
   * Executes an effectful fold over the stream of values.
   */
  @deprecated("use foldZIO", "2.0.0")
  final def foldM[R1 <: R, E1 >: E, S](s: S)(f: (S, O) => ZIO[R1, E1, S])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E1, S] =
    foldZIO[R1, E1, S](s)(f)

  /**
   * Executes a pure fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   */
  final def foldManaged[S](s: S)(f: (S, O) => S)(implicit trace: ZTraceElement): ZManaged[R, E, S] =
    foldWhileManagedZIO(s)(_ => true)((s, a) => ZIO.succeedNow(f(s, a)))

  /**
   * Executes an effectful fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   */
  @deprecated("use foldManagedZIO", "2.0.0")
  final def foldManagedM[R1 <: R, E1 >: E, S](s: S)(f: (S, O) => ZIO[R1, E1, S])(implicit
    trace: ZTraceElement
  ): ZManaged[R1, E1, S] =
    foldManagedZIO[R1, E1, S](s)(f)

  /**
   * Executes an effectful fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   */
  final def foldManagedZIO[R1 <: R, E1 >: E, S](s: S)(f: (S, O) => ZIO[R1, E1, S])(implicit
    trace: ZTraceElement
  ): ZManaged[R1, E1, S] =
    foldWhileManagedZIO[R1, E1, S](s)(_ => true)(f)

  /**
   * Reduces the elements in the stream to a value of type `S`.
   * Stops the fold early when the condition is not fulfilled.
   * Example:
   * {{{
   *  Stream(1).forever.foldWhile(0)(_ <= 4)(_ + _) // UIO[Int] == 5
   * }}}
   */
  final def foldWhile[S](s: S)(cont: S => Boolean)(f: (S, O) => S)(implicit trace: ZTraceElement): ZIO[R, E, S] =
    foldWhileManagedZIO(s)(cont)((s, a) => ZIO.succeedNow(f(s, a))).use(ZIO.succeedNow)

  /**
   * Executes an effectful fold over the stream of values.
   * Stops the fold early when the condition is not fulfilled.
   * Example:
   * {{{
   *   Stream(1)
   *     .forever                                // an infinite Stream of 1's
   *     .fold(0)(_ <= 4)((s, a) => UIO(s + a))  // UIO[Int] == 5
   * }}}
   *
   * @param cont function which defines the early termination condition
   */
  @deprecated("use foldWhileZIO", "2.0.0")
  final def foldWhileM[R1 <: R, E1 >: E, S](s: S)(cont: S => Boolean)(f: (S, O) => ZIO[R1, E1, S])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E1, S] =
    foldWhileZIO[R1, E1, S](s)(cont)(f)

  /**
   * Executes a pure fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   * Stops the fold early when the condition is not fulfilled.
   */
  def foldWhileManaged[S](s: S)(cont: S => Boolean)(f: (S, O) => S)(implicit trace: ZTraceElement): ZManaged[R, E, S] =
    foldWhileManagedZIO(s)(cont)((s, a) => ZIO.succeedNow(f(s, a)))

  /**
   * Executes an effectful fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   * Stops the fold early when the condition is not fulfilled.
   * Example:
   * {{{
   *   Stream(1)
   *     .forever                                // an infinite Stream of 1's
   *     .fold(0)(_ <= 4)((s, a) => UIO(s + a))  // Managed[Nothing, Int]
   *     .use(ZIO.succeed)                       // UIO[Int] == 5
   * }}}
   *
   * @param cont function which defines the early termination condition
   */
  @deprecated("use foldWhileManagedZIO", "2.0.0")
  final def foldWhileManagedM[R1 <: R, E1 >: E, S](
    s: S
  )(cont: S => Boolean)(f: (S, O) => ZIO[R1, E1, S])(implicit trace: ZTraceElement): ZManaged[R1, E1, S] =
    foldWhileManagedZIO[R1, E1, S](s)(cont)(f)

  /**
   * Executes an effectful fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   * Stops the fold early when the condition is not fulfilled.
   * Example:
   * {{{
   *   Stream(1)
   *     .forever                                // an infinite Stream of 1's
   *     .fold(0)(_ <= 4)((s, a) => UIO(s + a))  // Managed[Nothing, Int]
   *     .use(ZIO.succeed)                       // UIO[Int] == 5
   * }}}
   *
   * @param cont function which defines the early termination condition
   */
  final def foldWhileManagedZIO[R1 <: R, E1 >: E, S](
    s: S
  )(cont: S => Boolean)(f: (S, O) => ZIO[R1, E1, S])(implicit trace: ZTraceElement): ZManaged[R1, E1, S] =
    process.flatMap { (is: ZIO[R, Option[E], Chunk[O]]) =>
      def loop(s1: S): ZIO[R1, E1, S] =
        if (!cont(s1)) UIO.succeedNow(s1)
        else
          is.foldZIO(
            {
              case Some(e) =>
                IO.fail(e)
              case None =>
                IO.succeedNow(s1)
            },
            (ch: Chunk[O]) => ch.foldZIO(s1)(f).flatMap(loop)
          )

      ZManaged.fromZIO(loop(s))
    }

  /**
   * Executes an effectful fold over the stream of values.
   * Stops the fold early when the condition is not fulfilled.
   * Example:
   * {{{
   *   Stream(1)
   *     .forever                                // an infinite Stream of 1's
   *     .fold(0)(_ <= 4)((s, a) => UIO(s + a))  // UIO[Int] == 5
   * }}}
   *
   * @param cont function which defines the early termination condition
   */
  final def foldWhileZIO[R1 <: R, E1 >: E, S](s: S)(cont: S => Boolean)(f: (S, O) => ZIO[R1, E1, S])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E1, S] =
    foldWhileManagedZIO[R1, E1, S](s)(cont)(f).use(ZIO.succeedNow)

  /**
   * Executes an effectful fold over the stream of values.
   */
  final def foldZIO[R1 <: R, E1 >: E, S](s: S)(f: (S, O) => ZIO[R1, E1, S])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E1, S] =
    foldWhileManagedZIO[R1, E1, S](s)(_ => true)(f).use(ZIO.succeedNow)

  /**
   * Consumes all elements of the stream, passing them to the specified callback.
   */
  final def foreach[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Any])(implicit trace: ZTraceElement): ZIO[R1, E1, Unit] =
    run(ZSink.foreach(f))

  /**
   * Consumes all elements of the stream, passing them to the specified callback.
   */
  final def foreachChunk[R1 <: R, E1 >: E](f: Chunk[O] => ZIO[R1, E1, Any])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E1, Unit] =
    run(ZSink.foreachChunk(f))

  /**
   * Like [[ZStream#foreachChunk]], but returns a `ZManaged` so the finalization order
   * can be controlled.
   */
  final def foreachChunkManaged[R1 <: R, E1 >: E](f: Chunk[O] => ZIO[R1, E1, Any])(implicit
    trace: ZTraceElement
  ): ZManaged[R1, E1, Unit] =
    runManaged(ZSink.foreachChunk(f))

  /**
   * Like [[ZStream#foreach]], but returns a `ZManaged` so the finalization order
   * can be controlled.
   */
  final def foreachManaged[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Any])(implicit
    trace: ZTraceElement
  ): ZManaged[R1, E1, Unit] =
    runManaged(ZSink.foreach(f))

  /**
   * Consumes elements of the stream, passing them to the specified callback,
   * and terminating consumption when the callback returns `false`.
   */
  final def foreachWhile[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Boolean])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E1, Unit] =
    run(ZSink.foreachWhile(f))

  /**
   * Like [[ZStream#foreachWhile]], but returns a `ZManaged` so the finalization order
   * can be controlled.
   */
  final def foreachWhileManaged[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Boolean])(implicit
    trace: ZTraceElement
  ): ZManaged[R1, E1, Unit] =
    runManaged(ZSink.foreachWhile(f))

  /**
   * Repeats this stream forever.
   */
  def forever(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        currStream   <- Ref.make[ZIO[R, Option[E], Chunk[O]]](Pull.end).toManaged
        switchStream <- ZManaged.switchable[R, Nothing, ZIO[R, Option[E], Chunk[O]]]
        _            <- switchStream(self.process).flatMap(currStream.set).toManaged
        pull = {
          def go: ZIO[R, Option[E], Chunk[O]] =
            currStream.get.flatten.catchAllCause {
              Cause.flipCauseOption(_) match {
                case Some(e) => Pull.failCause(e)
                case None =>
                  switchStream(self.process).flatMap(currStream.set) *> ZIO.yieldNow *> go
              }
            }

          go
        }
      } yield pull
    }

  /**
   * Filters the elements emitted by this stream using the provided function.
   */
  def filter(f: O => Boolean)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    mapChunks(_.filter(f))

  /**
   * Effectfully filters the elements emitted by this stream.
   */
  @deprecated("use filterZIO", "2.0.0")
  def filterM[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Boolean])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    filterZIO(f)

  /**
   * Effectfully filters the elements emitted by this stream.
   */
  def filterZIO[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Boolean])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    ZStream {
      self.process.mapZIO(BufferedPull.make(_)).map { os =>
        def pull: Pull[R1, E1, O] =
          os.pullElement.flatMap { o =>
            f(o).mapError(Some(_)).flatMap {
              if (_) UIO.succeed(Chunk.single(o))
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
  final def filterNot(pred: O => Boolean)(implicit trace: ZTraceElement): ZStream[R, E, O] = filter(a => !pred(a))

  /**
   * Emits elements of this stream with a fixed delay in between, regardless of how long it
   * takes to produce a value.
   */
  final def fixed(duration: Duration)(implicit trace: ZTraceElement): ZStream[R with Has[Clock], E, O] =
    schedule(Schedule.fixed(duration))

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f0`
   */
  def flatMap[R1 <: R, E1 >: E, O2](
    f0: O => ZStream[R1, E1, O2]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O2] = {
    def go(
      outerStream: ZIO[R1, Option[E1], Chunk[O]],
      currOuterChunk: Ref[(Chunk[O], Int)],
      currInnerStream: Ref[ZIO[R1, Option[E1], Chunk[O2]]],
      innerFinalizer: Ref[ZManaged.Finalizer]
    ): ZIO[R1, Option[E1], Chunk[O2]] = {
      def pullNonEmpty[R, E, O](pull: ZIO[R, Option[E], Chunk[O]]): ZIO[R, Option[E], Chunk[O]] =
        pull.flatMap(os => if (os.nonEmpty) UIO.succeed(os) else pullNonEmpty(pull))

      def closeInner =
        innerFinalizer.getAndSet(ZManaged.Finalizer.noop).flatMap(_.apply(Exit.unit))

      def pullOuter: ZIO[R1, Option[E1], Unit] =
        currOuterChunk.modify { case (chunk, nextIdx) =>
          if (nextIdx < chunk.size) (UIO.succeed(chunk(nextIdx)), (chunk, nextIdx + 1))
          else
            (
              pullNonEmpty(outerStream)
                .tap(os => currOuterChunk.set((os, 1)))
                .map(_.apply(0)),
              (chunk, nextIdx)
            )
        }.flatten.flatMap { o =>
          ZIO.uninterruptibleMask { restore =>
            for {
              releaseMap <- ZManaged.ReleaseMap.make
              pull       <- restore(f0(o).process.zio.provideSome[R1]((_, releaseMap)).map(_._2))
              _          <- currInnerStream.set(pull)
              _          <- innerFinalizer.set(releaseMap.releaseAll(_, ExecutionStrategy.Sequential))
            } yield ()
          }
        }

      currInnerStream.get.flatten.catchAllCause { c =>
        Cause.flipCauseOption(c) match {
          case Some(e) => Pull.failCause(e)
          case None    =>
            // The additional switch is needed to eagerly run the finalizer
            // *before* pulling another element from the outer stream.
            closeInner *>
              pullOuter *>
              go(outerStream, currOuterChunk, currInnerStream, innerFinalizer)
        }
      }
    }

    ZStream {
      for {
        outerStream     <- self.process
        currOuterChunk  <- Ref.make[(Chunk[O], Int)](Chunk.empty -> 0).toManaged
        currInnerStream <- Ref.make[ZIO[R1, Option[E1], Chunk[O2]]](Pull.end).toManaged
        innerFinalizer  <- ZManaged.finalizerRef(ZManaged.Finalizer.noop)
      } yield go(outerStream, currOuterChunk, currInnerStream, innerFinalizer)
    }
  }

  /**
   * Maps each element of this stream to another stream and returns the
   * non-deterministic merge of those streams, executing up to `n` inner streams
   * concurrently. Up to `outputBuffer` elements of the produced streams may be
   * buffered in memory by this operator.
   */
  final def flatMapPar[R1 <: R, E1 >: E, O2](n: Int, outputBuffer: Int = 16)(
    f: O => ZStream[R1, E1, O2]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    ZStream[R1, E1, O2] {
      ZManaged.withChildren { getChildren =>
        for {
          out          <- Queue.bounded[ZIO[R1, Option[E1], Chunk[O2]]](outputBuffer).toManagedWith(_.shutdown)
          permits      <- Semaphore.make(n.toLong).toManaged
          innerFailure <- Promise.make[Cause[E1], Nothing].toManaged

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
                   // If the inner stream completed successfully, release the permit so another
                   // stream can be executed. Otherwise, signal failure to the outer stream.
                   withPermitManaged = ZManaged.acquireReleaseExit(permits.acquire.commit)(
                                         _.fold(
                                           cause => innerFailure.fail(cause.stripFailures),
                                           _ => permits.release.commit
                                         )
                                       )
                   innerStream = withPermitManaged
                                   .tap(_ => latch.succeed(()).toManaged)
                                   .useDiscard(
                                     f(a)
                                       .foreachChunk(b => out.offer(UIO.succeedNow(b)))
                                       .foldCauseZIO(
                                         cause => out.offer(Pull.failCause(cause)) *> innerFailure.fail(cause),
                                         _ => ZIO.unit
                                       )
                                   )
                   _ <- innerStream.fork
                   // Make sure that the current inner stream has actually succeeded in acquiring
                   // a permit before continuing. Otherwise we could reach the end of the stream and
                   // acquire the permits ourselves before the inners had a chance to start.
                   _ <- latch.await
                 } yield ()
               }.foldCauseManaged(
                 cause => (getChildren.flatMap(Fiber.interruptAll(_)) *> out.offer(Pull.failCause(cause))).toManaged,
                 _ =>
                   innerFailure.await.interruptible
                     // Important to use `withPermits` here because the ZManaged#fork below may interrupt
                     // the driver, and we want the permits to be released in that case
                     .raceWith(permits.withPermits(n.toLong)(ZIO.unit).interruptible)(
                       // One of the inner fibers failed. It already enqueued its failure, so we
                       // interrupt the inner fibers. The finalizer below will make sure
                       // that they actually end.
                       leftDone = (_, permitAcquisition) =>
                         getChildren.flatMap(Fiber.interruptAll(_)) *> permitAcquisition.interrupt,
                       // All fibers completed successfully, so we signal that we're done.
                       rightDone = (_, failureAwait) => out.offer(Pull.end) *> failureAwait.interrupt
                     )
                     .toManaged
               ).fork
        } yield out.take.flatten
      }
    }

  /**
   * Maps each element of this stream to another stream and returns the non-deterministic merge
   * of those streams, executing up to `n` inner streams concurrently. When a new stream is created
   * from an element of the source stream, the oldest executing stream is cancelled. Up to `bufferSize`
   * elements of the produced streams may be buffered in memory by this operator.
   */
  final def flatMapParSwitch[R1 <: R, E1 >: E, O2](n: Int, bufferSize: Int = 16)(
    f: O => ZStream[R1, E1, O2]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    ZStream[R1, E1, O2] {
      ZManaged.withChildren { getChildren =>
        for {
          // Modeled after flatMapPar.
          out          <- Queue.bounded[ZIO[R1, Option[E1], Chunk[O2]]](bufferSize).toManagedWith(_.shutdown)
          permits      <- Semaphore.make(n.toLong).toManaged
          innerFailure <- Promise.make[Cause[E1], Nothing].toManaged
          cancelers    <- Queue.bounded[Promise[Nothing, Unit]](n).toManagedWith(_.shutdown)
          _ <- self.foreachManaged { a =>
                 for {
                   canceler <- Promise.make[Nothing, Unit]
                   latch    <- Promise.make[Nothing, Unit]
                   size     <- cancelers.size
                   _ <- if (size < n) UIO.unit
                        else cancelers.take.flatMap(_.succeed(()))
                   _ <- cancelers.offer(canceler)
                   innerStream = permits.withPermitManaged
                                   .tap(_ => latch.succeed(()).toManaged)
                                   .useDiscard(
                                     f(a)
                                       .foreachChunk(o2s => out.offer(UIO.succeedNow(o2s)))
                                       .foldCauseZIO(
                                         cause => out.offer(Pull.failCause(cause)) *> innerFailure.fail(cause),
                                         _ => UIO.unit
                                       )
                                   )
                   _ <- (innerStream race canceler.await).fork
                   _ <- latch.await
                 } yield ()
               }.foldCauseManaged(
                 cause => (getChildren.flatMap(Fiber.interruptAll(_)) *> out.offer(Pull.failCause(cause))).toManaged,
                 _ =>
                   innerFailure.await
                     .raceWith(permits.withPermits(n.toLong)(UIO.unit))(
                       leftDone = (_, permitAcquisition) =>
                         getChildren.flatMap(Fiber.interruptAll(_)) *> permitAcquisition.interrupt,
                       rightDone = (_, failureAwait) => out.offer(Pull.end) *> failureAwait.interrupt
                     )
                     .toManaged
               ).fork
        } yield out.take.flatten
      }
    }

  /**
   * Flattens this stream-of-streams into a stream made of the concatenation in
   * strict order of all the streams.
   */
  def flatten[R1 <: R, E1 >: E, O1](implicit ev: O <:< ZStream[R1, E1, O1], trace: ZTraceElement): ZStream[R1, E1, O1] =
    flatMap(ev(_))

  /**
   * Submerges the chunks carried by this stream into the stream's structure, while
   * still preserving them.
   */
  def flattenChunks[O1](implicit ev: O <:< Chunk[O1], trace: ZTraceElement): ZStream[R, E, O1] =
    ZStream {
      self.process
        .mapZIO(BufferedPull.make(_))
        .map(_.pullElement.map(ev))
    }

  /**
   * Flattens [[Exit]] values. `Exit.Failure` values translate to stream failures
   * while `Exit.Success` values translate to stream elements.
   */
  def flattenExit[E1 >: E, O1](implicit ev: O <:< Exit[E1, O1], trace: ZTraceElement): ZStream[R, E1, O1] =
    mapZIO(o => ZIO.done(ev(o)))

  /**
   * Unwraps [[Exit]] values that also signify end-of-stream by failing with `None`.
   *
   * For `Exit[E, O]` values that do not signal end-of-stream, prefer:
   * {{{
   * stream.mapZIO(ZIO.done(_))
   * }}}
   */
  def flattenExitOption[E1 >: E, O1](implicit
    ev: O <:< Exit[Option[E1], O1],
    trace: ZTraceElement
  ): ZStream[R, E1, O1] =
    ZStream {
      for {
        upstream <- self.process.mapZIO(BufferedPull.make(_))
        done     <- Ref.make(false).toManaged
        pull = done.get.flatMap {
                 if (_) Pull.end
                 else
                   upstream.pullElement
                     .foldZIO(
                       {
                         case None    => done.set(true) *> Pull.end
                         case Some(e) => Pull.fail(e)
                       },
                       os =>
                         ZIO
                           .done(ev(os))
                           .foldZIO(
                             {
                               case None    => done.set(true) *> Pull.end
                               case Some(e) => Pull.fail(e)
                             },
                             Pull.emit(_)
                           )
                     )
               }
      } yield pull
    }

  /**
   * Submerges the iterables carried by this stream into the stream's structure, while
   * still preserving them.
   */
  def flattenIterables[O1](implicit ev: O <:< Iterable[O1], trace: ZTraceElement): ZStream[R, E, O1] =
    map(o => Chunk.fromIterable(ev(o))).flattenChunks

  /**
   * Flattens a stream of streams into a stream by executing a non-deterministic
   * concurrent merge. Up to `n` streams may be consumed in parallel and up to
   * `outputBuffer` elements may be buffered by this operator.
   */
  def flattenPar[R1 <: R, E1 >: E, O1](n: Int, outputBuffer: Int = 16)(implicit
    ev: O <:< ZStream[R1, E1, O1],
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    flatMapPar[R1, E1, O1](n, outputBuffer)(ev(_))

  /**
   * Like [[flattenPar]], but executes all streams concurrently.
   */
  def flattenParUnbounded[R1 <: R, E1 >: E, O1](
    outputBuffer: Int = 16
  )(implicit ev: O <:< ZStream[R1, E1, O1], trace: ZTraceElement): ZStream[R1, E1, O1] =
    flattenPar[R1, E1, O1](Int.MaxValue, outputBuffer)

  /**
   * Unwraps [[Exit]] values and flatten chunks that also signify end-of-stream by failing with `None`.
   */
  final def flattenTake[E1 >: E, O1](implicit ev: O <:< Take[E1, O1], trace: ZTraceElement): ZStream[R, E1, O1] =
    map(_.exit).flattenExitOption[E1, Chunk[O1]].flattenChunks

  /**
   * More powerful version of [[ZStream.groupByKey]]
   */
  final def groupBy[R1 <: R, E1 >: E, K, V](
    f: O => ZIO[R1, E1, (K, V)],
    buffer0: Int = 16
  ): ZStream.GroupBy[R1, E1, K, V] = {
    type O1 = O
    new ZStream.GroupBy[R1, E1, K, V] {
      type O = O1
      def stream = self
      def key    = f
      def buffer = buffer0
    }
  }

  /**
   * Partition a stream using a function and process each stream individually.
   * This returns a data structure that can be used
   * to further filter down which groups shall be processed.
   *
   * After calling apply on the GroupBy object, the remaining groups will be processed
   * in parallel and the resulting streams merged in a nondeterministic fashion.
   *
   * Up to `buffer` elements may be buffered in any group stream before the producer
   * is backpressured. Take care to consume from all streams in order
   * to prevent deadlocks.
   *
   * Example:
   * Collect the first 2 words for every starting letter
   * from a stream of words.
   * {{{
   * ZStream.fromIterable(List("hello", "world", "hi", "holla"))
   *  .groupByKey(_.head) { case (k, s) => s.take(2).map((k, _)) }
   *  .runCollect
   *  .map(_ == List(('h', "hello"), ('h', "hi"), ('w', "world"))
   * }}}
   */
  final def groupByKey[K](
    f: O => K,
    buffer: Int = 16
  ): ZStream.GroupBy[R, E, K, O] =
    self.groupBy(a => ZIO.succeedNow((f(a), a)), buffer)

  /**
   * Halts the evaluation of this stream when the provided IO completes. The given IO
   * will be forked as part of the returned stream, and its success will be discarded.
   *
   * An element in the process of being pulled will not be interrupted when the IO
   * completes. See `interruptWhen` for this behavior.
   *
   * If the IO completes with a failure, the stream will emit that failure.
   */
  final def haltWhen[R1 <: R, E1 >: E](io: ZIO[R1, E1, Any])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    ZStream {
      for {
        as    <- self.process
        runIO <- io.forkManaged
      } yield runIO.poll.flatMap {
        case None       => as
        case Some(exit) => exit.fold(cause => Pull.failCause(cause), _ => Pull.end)
      }
    }

  /**
   * Specialized version of haltWhen which halts the evaluation of this stream
   * after the given duration.
   *
   * An element in the process of being pulled will not be interrupted when the
   * given duration completes. See `interruptAfter` for this behavior.
   */
  final def haltAfter(duration: Duration)(implicit trace: ZTraceElement): ZStream[R with Has[Clock], E, O] =
    haltWhen(Clock.sleep(duration))

  /**
   * Partitions the stream with specified chunkSize
   * @param chunkSize size of the chunk
   */
  def grouped(chunkSize: Int)(implicit trace: ZTraceElement): ZStream[R, E, Chunk[O]] =
    aggregate(ZTransducer.collectAllN(chunkSize))

  /**
   * Partitions the stream with the specified chunkSize or until the specified
   * duration has passed, whichever is satisfied first.
   */
  def groupedWithin(chunkSize: Int, within: Duration)(implicit
    trace: ZTraceElement
  ): ZStream[R with Has[Clock], E, Chunk[O]] =
    aggregateAsyncWithin(ZTransducer.collectAllN(chunkSize), Schedule.spaced(within))

  /**
   * Halts the evaluation of this stream when the provided promise resolves.
   *
   * If the promise completes with a failure, the stream will emit that failure.
   */
  final def haltWhen[E1 >: E](p: Promise[E1, _])(implicit trace: ZTraceElement): ZStream[R, E1, O] =
    ZStream {
      for {
        as   <- self.process
        done <- Ref.make(false).toManaged
        pull = done.get flatMap {
                 if (_) Pull.end
                 else
                   p.poll.flatMap {
                     case None    => as
                     case Some(v) => done.set(true) *> v.mapError(Some(_)) *> Pull.end
                   }
               }
      } yield pull
    }

  /**
   * Interleaves this stream and the specified stream deterministically by
   * alternating pulling values from this stream and the specified stream.
   * When one stream is exhausted all remaining values in the other stream
   * will be pulled.
   */
  final def interleave[R1 <: R, E1 >: E, O1 >: O](that: ZStream[R1, E1, O1])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    self.interleaveWith(that)(ZStream(true, false).forever)

  /**
   * Combines this stream and the specified stream deterministically using the
   * stream of boolean values `b` to control which stream to pull from next.
   * `true` indicates to pull from this stream and `false` indicates to pull
   * from the specified stream. Only consumes as many elements as requested by
   * `b`. If either this stream or the specified stream are exhausted further
   * requests for values from that stream will be ignored.
   */
  final def interleaveWith[R1 <: R, E1 >: E, O1 >: O](
    that: ZStream[R1, E1, O1]
  )(b: ZStream[R1, E1, Boolean])(implicit trace: ZTraceElement): ZStream[R1, E1, O1] = {

    def loop(
      leftDone: Boolean,
      rightDone: Boolean,
      s: ZIO[R1, Option[E1], Boolean],
      left: ZIO[R, Option[E], O],
      right: ZIO[R1, Option[E1], O1]
    ): ZIO[R1, Nothing, Exit[Option[E1], (O1, (Boolean, Boolean, ZIO[R1, Option[E1], Boolean]))]] =
      s.foldCauseZIO(
        Cause.flipCauseOption(_) match {
          case None    => ZIO.succeedNow(Exit.fail(None))
          case Some(e) => ZIO.succeedNow(Exit.failCause(e.map(Some(_))))
        },
        b =>
          if (b && !leftDone) {
            left.foldCauseZIO(
              Cause.flipCauseOption(_) match {
                case None =>
                  if (rightDone) ZIO.succeedNow(Exit.fail(None))
                  else loop(true, rightDone, s, left, right)
                case Some(e) => ZIO.succeedNow(Exit.failCause(e.map(Some(_))))
              },
              a => ZIO.succeedNow(Exit.succeed((a, (leftDone, rightDone, s))))
            )
          } else if (!b && !rightDone)
            right.foldCauseZIO(
              Cause.flipCauseOption(_) match {
                case Some(e) => ZIO.succeedNow(Exit.failCause(e.map(Some(_))))
                case None =>
                  if (leftDone) ZIO.succeedNow(Exit.fail(None))
                  else loop(leftDone, true, s, left, right)
              },
              a => ZIO.succeedNow(Exit.succeed((a, (leftDone, rightDone, s))))
            )
          else loop(leftDone, rightDone, s, left, right)
      )

    ZStream {
      for {
        sides <- b.process.mapZIO(BufferedPull.make(_))
        result <-
          self
            .combine(that)((false, false, sides.pullElement)) { case ((leftDone, rightDone, sides), left, right) =>
              loop(leftDone, rightDone, sides, left, right)
            }
            .process
      } yield result
    }
  }

  /**
   * Intersperse stream with provided element similar to <code>List.mkString</code>.
   */
  final def intersperse[O1 >: O](middle: O1)(implicit trace: ZTraceElement): ZStream[R, E, O1] =
    ZStream {
      for {
        state  <- ZRef.makeManaged(true)
        chunks <- self.process
        pull = chunks.flatMap { os =>
                 state.modify { first =>
                   val builder    = ChunkBuilder.make[O1]()
                   var flagResult = first

                   os.foreach { o =>
                     if (flagResult) {
                       flagResult = false
                       builder += o
                     } else {
                       builder += middle
                       builder += o
                     }
                   }

                   (builder.result(), flagResult)
                 }
               }
      } yield pull
    }

  /**
   * Intersperse and also add a prefix and a suffix
   */
  final def intersperse[O1 >: O](start: O1, middle: O1, end: O1)(implicit trace: ZTraceElement): ZStream[R, E, O1] =
    ZStream(start) ++ intersperse(middle) ++ ZStream(end)

  /**
   * Interrupts the evaluation of this stream when the provided IO completes. The given
   * IO will be forked as part of this stream, and its success will be discarded. This
   * combinator will also interrupt any in-progress element being pulled from upstream.
   *
   * If the IO completes with a failure before the stream completes, the returned stream
   * will emit that failure.
   */
  final def interruptWhen[R1 <: R, E1 >: E](io: ZIO[R1, E1, Any])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    ZStream {
      for {
        as    <- self.process
        runIO <- (io.asSomeError *> Pull.end).forkManaged
      } yield ZIO.transplant(graft => runIO.join.disconnect.raceFirst(graft(as)))
    }

  /**
   * Interrupts the evaluation of this stream when the provided promise resolves. This
   * combinator will also interrupt any in-progress element being pulled from upstream.
   *
   * If the promise completes with a failure, the stream will emit that failure.
   */
  final def interruptWhen[E1 >: E](p: Promise[E1, _])(implicit trace: ZTraceElement): ZStream[R, E1, O] =
    ZStream {
      for {
        as    <- self.process
        done  <- Ref.makeManaged(false)
        asPull = p.await.asSomeError *> done.set(true) *> Pull.end
        pull = (done.get <*> p.isDone) flatMap {
                 case (true, _) => Pull.end
                 case (_, true) => asPull
                 case _         => ZIO.transplant(graft => graft(as).raceFirst(asPull))
               }
      } yield pull
    }

  /**
   * Specialized version of interruptWhen which interrupts the evaluation of this stream
   * after the given duration.
   */
  final def interruptAfter(duration: Duration)(implicit trace: ZTraceElement): ZStream[R with Has[Clock], E, O] =
    interruptWhen(Clock.sleep(duration))

  /**
   * Enqueues elements of this stream into a queue. Stream failure and ending
   * will also be signalled.
   */
  @deprecated("use intoQueue", "2.0.0")
  final def into[R1 <: R, E1 >: E](
    queue: ZQueue[R1, Nothing, Nothing, Any, Take[E1, O], Any]
  )(implicit trace: ZTraceElement): ZIO[R1, E1, Unit] =
    intoQueue(queue)

  /**
   * Publishes elements of this stream to a hub. Stream failure and ending will
   * also be signalled.
   */
  final def intoHub[R1 <: R, E1 >: E](
    hub: ZHub[R1, Nothing, Nothing, Any, Take[E1, O], Any]
  )(implicit trace: ZTraceElement): ZIO[R1, E1, Unit] =
    intoQueue(hub.toQueue)

  /**
   * Like [[ZStream#intoHub]], but provides the result as a [[ZManaged]] to
   * allow for scope composition.
   */
  final def intoHubManaged[R1 <: R, E1 >: E](
    hub: ZHub[R1, Nothing, Nothing, Any, Take[E1, O], Any]
  )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
    intoQueueManaged(hub.toQueue)

  /**
   * Like [[ZStream#into]], but provides the result as a [[ZManaged]] to allow
   * for scope composition.
   */
  @deprecated("use intoQueueManaged", "2.0.0")
  final def intoManaged[R1 <: R, E1 >: E](
    queue: ZQueue[R1, Nothing, Nothing, Any, Take[E1, O], Any]
  )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
    intoQueueManaged(queue)

  /**
   * Enqueues elements of this stream into a queue. Stream failure and ending
   * will also be signalled.
   */
  final def intoQueue[R1 <: R, E1 >: E](
    queue: ZQueue[R1, Nothing, Nothing, Any, Take[E1, O], Any]
  )(implicit trace: ZTraceElement): ZIO[R1, E1, Unit] =
    intoQueueManaged(queue).useDiscard(UIO.unit)

  /**
   * Like [[ZStream#intoQueue]], but provides the result as a [[ZManaged]] to
   * allow for scope composition.
   */
  final def intoQueueManaged[R1 <: R, E1 >: E](
    queue: ZQueue[R1, Nothing, Nothing, Any, Take[E1, O], Any]
  )(implicit trace: ZTraceElement): ZManaged[R1, E1, Unit] =
    for {
      as <- self.process
      pull = {
        def go: ZIO[R1, Nothing, Unit] =
          as.foldCauseZIO(
            Cause
              .flipCauseOption(_)
              .fold[ZIO[R1, Nothing, Unit]](queue.offer(Take.end).unit)(c => queue.offer(Take.failCause(c)) *> go),
            a => queue.offer(Take.chunk(a)) *> go
          )

        go
      }
      _ <- pull.toManaged
    } yield ()

  /**
   * Locks the execution of this stream to the specified executor. Any streams
   * that are composed after this one will automatically be shifted back to the
   * previous executor.
   */
  @deprecated("use onExecutor", "2.0.0")
  def lock(executor: Executor)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    onExecutor(executor)

  /**
   * Transforms the elements of this stream using the supplied function.
   */
  def map[O2](f: O => O2)(implicit trace: ZTraceElement): ZStream[R, E, O2] =
    mapChunks(_.map(f))

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  def mapAccum[S, O1](s: S)(f: (S, O) => (S, O1))(implicit trace: ZTraceElement): ZStream[R, E, O1] =
    mapAccumZIO(s)((s, a) => UIO.succeedNow(f(s, a)))

  /**
   * Statefully and effectfully maps over the elements of this stream to produce
   * new elements.
   */
  @deprecated("use mapAccumZIO", "2.0.0")
  final def mapAccumM[R1 <: R, E1 >: E, S, O1](s: S)(f: (S, O) => ZIO[R1, E1, (S, O1)])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    mapAccumZIO[R1, E1, S, O1](s)(f)

  /**
   * Statefully and effectfully maps over the elements of this stream to produce
   * new elements.
   */
  final def mapAccumZIO[R1 <: R, E1 >: E, S, O1](
    s: S
  )(f: (S, O) => ZIO[R1, E1, (S, O1)])(implicit trace: ZTraceElement): ZStream[R1, E1, O1] =
    ZStream {
      for {
        state <- Ref.make(s).toManaged
        pull  <- self.process.mapZIO(BufferedPull.make(_))
      } yield pull.pullElement.flatMap { o =>
        (for {
          s <- state.get
          t <- f(s, o)
          _ <- state.set(t._1)
        } yield Chunk.single(t._2)).asSomeError
      }
    }

  /**
   * Transforms the chunks emitted by this stream.
   */
  def mapChunks[O2](f: Chunk[O] => Chunk[O2])(implicit trace: ZTraceElement): ZStream[R, E, O2] =
    mapChunksZIO(c => UIO.succeed(f(c)))

  /**
   * Effectfully transforms the chunks emitted by this stream.
   */
  @deprecated("use mapChunksZIO", "2.0.0")
  def mapChunksM[R1 <: R, E1 >: E, O2](f: Chunk[O] => ZIO[R1, E1, Chunk[O2]])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    mapChunksZIO(f)

  /**
   * Effectfully transforms the chunks emitted by this stream.
   */
  def mapChunksZIO[R1 <: R, E1 >: E, O2](f: Chunk[O] => ZIO[R1, E1, Chunk[O2]])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    ZStream(self.process.map(_.flatMap(f(_).mapError(Some(_)))))

  /**
   * Maps each element to an iterable, and flattens the iterables into the
   * output of this stream.
   */
  def mapConcat[O2](f: O => Iterable[O2])(implicit trace: ZTraceElement): ZStream[R, E, O2] =
    mapConcatChunk(o => Chunk.fromIterable(f(o)))

  /**
   * Maps each element to a chunk, and flattens the chunks into the output of
   * this stream.
   */
  def mapConcatChunk[O2](f: O => Chunk[O2])(implicit trace: ZTraceElement): ZStream[R, E, O2] =
    mapChunks(_.flatMap(f))

  /**
   * Effectfully maps each element to a chunk, and flattens the chunks into
   * the output of this stream.
   */
  @deprecated("use mapConcatChunkZIO", "2.0.0")
  final def mapConcatChunkM[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, Chunk[O2]])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    mapConcatChunkZIO(f)

  /**
   * Effectfully maps each element to a chunk, and flattens the chunks into
   * the output of this stream.
   */
  final def mapConcatChunkZIO[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, Chunk[O2]])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    mapZIO(f).mapConcatChunk(identity)

  /**
   * Effectfully maps each element to an iterable, and flattens the iterables into
   * the output of this stream.
   */
  @deprecated("use mapConcatZIO", "2.0.0")
  final def mapConcatM[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, Iterable[O2]])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    mapConcatZIO(f)

  /**
   * Effectfully maps each element to an iterable, and flattens the iterables into
   * the output of this stream.
   */
  final def mapConcatZIO[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, Iterable[O2]])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    mapZIO(a => f(a).map(Chunk.fromIterable(_))).mapConcatChunk(identity)

  /**
   * Returns a stream whose failure and success channels have been mapped by
   * the specified pair of functions, `f` and `g`.
   */
  def mapBoth[E1, O1](f: E => E1, g: O => O1)(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R, E1, O1] =
    mapError(f).map(g)

  /**
   * Transforms the errors emitted by this stream using `f`.
   */
  def mapError[E2](f: E => E2)(implicit trace: ZTraceElement): ZStream[R, E2, O] =
    ZStream(self.process.map(_.mapError(_.map(f))))

  /**
   * Transforms the full causes of failures emitted by this stream.
   */
  def mapErrorCause[E2](f: Cause[E] => Cause[E2])(implicit trace: ZTraceElement): ZStream[R, E2, O] =
    ZStream(
      self.process.map(
        _.mapErrorCause(
          Cause.flipCauseOption(_) match {
            case None    => Cause.fail(None)
            case Some(c) => f(c).map(Some(_))
          }
        )
      )
    )

  /**
   * Maps over elements of the stream with the specified effectful function.
   */
  @deprecated("use mapZIO", "2.0.0")
  def mapM[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    mapZIO(f)

  /**
   * Maps over elements of the stream with the specified effectful function,
   * executing up to `n` invocations of `f` concurrently. Transformed elements
   * will be emitted in the original order.
   */
  @deprecated("use mapZIOPar", "2.0.0")
  final def mapMPar[R1 <: R, E1 >: E, O2](n: Int)(f: O => ZIO[R1, E1, O2])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    mapZIOPar[R1, E1, O2](n)(f)

  /**
   * Maps over elements of the stream with the specified effectful function,
   * executing up to `n` invocations of `f` concurrently. The element order
   * is not enforced by this combinator, and elements may be reordered.
   */
  @deprecated("use mapZIOParUnordered", "2.0.0")
  final def mapMParUnordered[R1 <: R, E1 >: E, O2](n: Int)(f: O => ZIO[R1, E1, O2])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    mapZIOParUnordered[R1, E1, O2](n)(f)

  /**
   * Maps over elements of the stream with the specified effectful function,
   * partitioned by `p` executing invocations of `f` concurrently. The number
   * of concurrent invocations of `f` is determined by the number of different
   * outputs of type `K`. Up to `buffer` elements may be buffered per partition.
   * Transformed elements may be reordered but the order within a partition is maintained.
   */
  @deprecated("use mapZIOPartitioned", "2.0.0")
  final def mapMPartitioned[R1 <: R, E1 >: E, O2, K](
    keyBy: O => K,
    buffer: Int = 16
  )(f: O => ZIO[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    mapZIOPartitioned[R1, E1, O2, K](keyBy, buffer)(f)

  /**
   * Maps over elements of the stream with the specified effectful function.
   */
  def mapZIO[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    ZStream {
      self.process.mapZIO(BufferedPull.make(_)).map { pull =>
        pull.pullElement.flatMap(f(_).mapBoth(Some(_), Chunk.single(_)))
      }
    }

  /**
   * Maps over elements of the stream with the specified effectful function,
   * executing up to `n` invocations of `f` concurrently. Transformed elements
   * will be emitted in the original order.
   */
  final def mapZIOPar[R1 <: R, E1 >: E, O2](
    n: Int
  )(f: O => ZIO[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    ZStream[R1, E1, O2] {
      for {
        out         <- Queue.bounded[ZIO[R1, Option[E1], O2]](n).toManagedWith(_.shutdown)
        errorSignal <- Promise.make[E1, Nothing].toManaged
        permits     <- Semaphore.make(n.toLong).toManaged
        _ <- self.foreachManaged { a =>
               for {
                 p     <- Promise.make[E1, O2]
                 latch <- Promise.make[Nothing, Unit]
                 _     <- out.offer(p.await.mapError(Some(_)))
                 _ <- permits.withPermit {
                        latch.succeed(()) *>                      // Make sure we start evaluation before moving on to the next element
                          (errorSignal.await raceFirst f(a))      // Interrupt evaluation if another task fails
                            .tapErrorCause(errorSignal.failCause) // Notify other tasks of a failure
                            .intoPromise(p)                       // Transfer the result to the consuming stream
                      }.fork
                 _ <- latch.await
               } yield ()
             }.foldCauseManaged(
               c => out.offer(Pull.failCause(c)).toManaged,
               _ => (permits.withPermits(n.toLong)(ZIO.unit).interruptible *> out.offer(Pull.end)).toManaged
             ).fork
        consumer = out.take.flatten.map(Chunk.single(_))
      } yield consumer
    }

  /**
   * Maps over elements of the stream with the specified effectful function,
   * executing up to `n` invocations of `f` concurrently. The element order
   * is not enforced by this combinator, and elements may be reordered.
   */
  final def mapZIOParUnordered[R1 <: R, E1 >: E, O2](n: Int)(f: O => ZIO[R1, E1, O2])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    flatMapPar[R1, E1, O2](n)(a => ZStream.fromZIO(f(a)))

  /**
   * Maps over elements of the stream with the specified effectful function,
   * partitioned by `p` executing invocations of `f` concurrently. The number
   * of concurrent invocations of `f` is determined by the number of different
   * outputs of type `K`. Up to `buffer` elements may be buffered per partition.
   * Transformed elements may be reordered but the order within a partition is maintained.
   */
  final def mapZIOPartitioned[R1 <: R, E1 >: E, O2, K](
    keyBy: O => K,
    buffer: Int = 16
  )(f: O => ZIO[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    groupByKey(keyBy, buffer).apply { case (_, s) => s.mapZIO(f) }

  /**
   * Merges this stream and the specified stream together.
   *
   * New produced stream will terminate when both specified stream terminate if no termination
   * strategy is specified.
   */
  final def merge[R1 <: R, E1 >: E, O1 >: O](
    that: ZStream[R1, E1, O1],
    strategy: TerminationStrategy = TerminationStrategy.Both
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O1] =
    self.mergeWith[R1, E1, O1, O1](that, strategy)(identity, identity) // TODO: Dotty doesn't infer this properly

  /**
   * Merges this stream and the specified stream together. New produced stream will
   * terminate when either stream terminates.
   */
  final def mergeTerminateEither[R1 <: R, E1 >: E, O1 >: O](that: ZStream[R1, E1, O1])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    self.merge[R1, E1, O1](that, TerminationStrategy.Either)

  /**
   * Merges this stream and the specified stream together. New produced stream will
   * terminate when this stream terminates.
   */
  final def mergeTerminateLeft[R1 <: R, E1 >: E, O1 >: O](that: ZStream[R1, E1, O1])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    self.merge[R1, E1, O1](that, TerminationStrategy.Left)

  /**
   * Merges this stream and the specified stream together. New produced stream will
   * terminate when the specified stream terminates.
   */
  final def mergeTerminateRight[R1 <: R, E1 >: E, O1 >: O](that: ZStream[R1, E1, O1])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    self.merge[R1, E1, O1](that, TerminationStrategy.Right)

  /**
   * Merges this stream and the specified stream together to produce a stream of
   * eithers.
   */
  final def mergeEither[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, Either[O, O2]] =
    self.mergeWith(that)(Left(_), Right(_))

  /**
   * Merges this stream and the specified stream together to a common element
   * type with the specified mapping functions.
   *
   * New produced stream will terminate when both specified stream terminate if
   * no termination strategy is specified.
   */
  final def mergeWith[R1 <: R, E1 >: E, O2, O3](
    that: ZStream[R1, E1, O2],
    strategy: TerminationStrategy = TerminationStrategy.Both
  )(l: O => O3, r: O2 => O3)(implicit trace: ZTraceElement): ZStream[R1, E1, O3] =
    ZStream {
      import TerminationStrategy.{Left => L, Right => R, Either => E}

      for {
        handoff <- ZStream.Handoff.make[Take[E1, O3]].toManaged
        done    <- Ref.Synchronized.makeManaged[Option[Boolean]](None)
        chunksL <- self.process
        chunksR <- that.process
        handler = (pull: Pull[R1, E1, O3], terminate: Boolean) =>
                    done.get.flatMap {
                      case Some(true) =>
                        ZIO.succeedNow(false)
                      case _ =>
                        pull.exit.flatMap { exit =>
                          done.modifyZIO { done =>
                            ((done, exit.fold(c => Left(Cause.flipCauseOption(c)), Right(_))): @unchecked) match {
                              case (state @ Some(true), _) =>
                                ZIO.succeedNow((false, state))
                              case (state, Right(chunk)) =>
                                handoff.offer(Take.chunk(chunk)).as((true, state))
                              case (_, Left(Some(cause))) =>
                                handoff.offer(Take.failCause(cause)).as((false, Some(true)))
                              case (option, Left(None)) if terminate || option.isDefined =>
                                handoff.offer(Take.end).as((false, Some(true)))
                              case (None, Left(None)) =>
                                ZIO.succeedNow((false, Some(false)))
                            }
                          }
                        }
                    }.repeatWhileEquals(true).fork.interruptible.toManagedWith(_.interrupt)
        _ <- handler(chunksL.map(_.map(l)), List(L, E).contains(strategy))
        _ <- handler(chunksR.map(_.map(r)), List(R, E).contains(strategy))
      } yield {
        for {
          done   <- done.get
          take   <- if (done.contains(true)) handoff.poll.some else handoff.take
          result <- take.done
        } yield result
      }
    }

  /**
   * Runs the specified effect if this stream fails, providing the error to the effect if it exists.
   *
   * Note: Unlike [[ZIO.onError]], there is no guarantee that the provided effect will not be interrupted.
   */
  final def onError[R1 <: R](cleanup: Cause[E] => URIO[R1, Any])(implicit trace: ZTraceElement): ZStream[R1, E, O] =
    catchAllCause(cause => ZStream.fromZIO(cleanup(cause) *> ZIO.failCause(cause)))

  /**
   * Locks the execution of this stream to the specified executor. Any streams
   * that are composed after this one will automatically be shifted back to the
   * previous executor.
   */
  def onExecutor(executor: Executor)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream.fromZIO(ZIO.descriptor).flatMap { descriptor =>
      ZStream.managed(ZManaged.onExecutor(executor)) *>
        self <*
        ZStream.fromZIO {
          if (descriptor.isLocked) ZIO.shift(descriptor.executor)
          else ZIO.unshift
        }
    }

  /**
   * Switches to the provided stream in case this one fails with a typed error.
   *
   * See also [[ZStream#catchAll]].
   */
  final def orElse[R1 <: R, E2, O1 >: O](
    that: => ZStream[R1, E2, O1]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R1, E2, O1] =
    catchAll(_ => that)

  /**
   * Switches to the provided stream in case this one fails with a typed error.
   *
   * See also [[ZStream#catchAll]].
   */
  final def orElseEither[R1 <: R, E2, O2](
    that: => ZStream[R1, E2, O2]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R1, E2, Either[O, O2]] =
    self.map(Left(_)) orElse that.map(Right(_))

  /**
   * Fails with given error in case this one fails with a typed error.
   *
   * See also [[ZStream#catchAll]].
   */
  final def orElseFail[E1](e1: => E1)(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R, E1, O] =
    orElse(ZStream.fail(e1))

  /**
   * Switches to the provided stream in case this one fails with the `None` value.
   *
   * See also [[ZStream#catchAll]].
   */
  final def orElseOptional[R1 <: R, E1, O1 >: O](
    that: => ZStream[R1, Option[E1], O1]
  )(implicit ev: E <:< Option[E1], trace: ZTraceElement): ZStream[R1, Option[E1], O1] =
    catchAll(ev(_).fold(that)(e => ZStream.fail(Some(e))))

  /**
   * Succeeds with the specified value if this one fails with a typed error.
   */
  final def orElseSucceed[O1 >: O](o1: => O1)(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R, Nothing, O1] =
    orElse(ZStream.succeed(o1))

  /**
   * Partition a stream using a predicate. The first stream will contain all element evaluated to true
   * and the second one will contain all element evaluated to false.
   * The faster stream may advance by up to buffer elements further than the slower one.
   */
  def partition(p: O => Boolean, buffer: Int = 16)(implicit
    trace: ZTraceElement
  ): ZManaged[R, E, (ZStream[Any, E, O], ZStream[Any, E, O])] =
    self.partitionEither(a => if (p(a)) ZIO.succeedNow(Left(a)) else ZIO.succeedNow(Right(a)), buffer)

  /**
   * Split a stream by a predicate. The faster stream may advance by up to buffer elements further than the slower one.
   */
  final def partitionEither[R1 <: R, E1 >: E, O2, O3](
    p: O => ZIO[R1, E1, Either[O2, O3]],
    buffer: Int = 16
  )(implicit trace: ZTraceElement): ZManaged[R1, E1, (ZStream[Any, E1, O2], ZStream[Any, E1, O3])] =
    self
      .mapZIO(p)
      .distributedWith(
        2,
        buffer,
        {
          case Left(_)  => ZIO.succeedNow(_ == 0)
          case Right(_) => ZIO.succeedNow(_ == 1)
        }
      )
      .flatMap {
        case q1 :: q2 :: Nil =>
          ZManaged.succeedNow {
            (
              ZStream.fromQueueWithShutdown(q1).flattenExitOption.collectLeft,
              ZStream.fromQueueWithShutdown(q2).flattenExitOption.collectRight
            )
          }
        case otherwise => ZManaged.dieMessage(s"partitionEither: expected two streams but got $otherwise")
      }

  /**
   * Peels off enough material from the stream to construct a `Z` using the
   * provided [[ZSink]] and then returns both the `Z` and the rest of the
   * [[ZStream]] in a managed resource. Like all [[ZManaged]] values, the provided
   * stream is valid only within the scope of [[ZManaged]].
   */
  def peel[R1 <: R, E1 >: E, O1 >: O, Z](
    sink: ZSink[R1, E1, O, O1, Z]
  )(implicit trace: ZTraceElement): ZManaged[R1, E1, (Z, ZStream[R, E, O1])] =
    self.process.flatMap { pull =>
      val stream = ZStream.repeatZIOChunkOption(pull)
      val s      = sink.exposeLeftover
      stream.run(s).toManaged.map(e => (e._1, ZStream.fromChunk(e._2) ++ stream))
    }

  /**
   * Provides the stream with its required environment, which eliminates
   * its dependency on `R`.
   */
  final def provide(r: R)(implicit ev: NeedsEnv[R], trace: ZTraceElement): ZStream[Any, E, O] =
    ZStream(self.process.provide(r).map(_.provide(r)))

  /**
   * Provides the part of the environment that is not part of the `ZEnv`,
   * leaving a stream that only depends on the `ZEnv`.
   *
   * {{{
   * val loggingDeps: ZDeps[Any, Nothing, Logging] = ???
   *
   * val stream: ZStream[ZEnv with Logging, Nothing, Unit] = ???
   *
   * val stream2 = stream.provideCustomDeps(loggingDeps)
   * }}}
   */
  def provideCustomDeps[E1 >: E, R1](
    deps: ZDeps[ZEnv, E1, R1]
  )(implicit
    ev1: ZEnv with R1 <:< R,
    ev2: Has.Union[ZEnv, R1],
    tagged: Tag[R1],
    trace: ZTraceElement
  ): ZStream[ZEnv, E1, O] =
    provideSomeDeps[ZEnv](deps)

  /**
   * Provides the part of the environment that is not part of the `ZEnv`,
   * leaving a stream that only depends on the `ZEnv`.
   *
   * {{{
   * val loggingLayer: ZDeps[Any, Nothing, Logging] = ???
   *
   * val stream: ZStream[ZEnv with Logging, Nothing, Unit] = ???
   *
   * val stream2 = stream.provideCustomLayer(loggingLayer)
   * }}}
   */
  @deprecated("use provideCustomDeps", "2.0.0")
  def provideCustomLayer[E1 >: E, R1](
    layer: ZDeps[ZEnv, E1, R1]
  )(implicit
    ev1: ZEnv with R1 <:< R,
    ev2: Has.Union[ZEnv, R1],
    tagged: Tag[R1],
    trace: ZTraceElement
  ): ZStream[ZEnv, E1, O] =
    provideCustomDeps(layer)

  /**
   * Provides a set of dependencies to the stream, which translates it to
   * another level.
   */
  final def provideDeps[E1 >: E, R0, R1](
    deps: ZDeps[R0, E1, R1]
  )(implicit ev: R1 <:< R, trace: ZTraceElement): ZStream[R0, E1, O] =
    ZStream.managed {
      for {
        r  <- deps.build.map(ev)
        as <- self.process.provide(r)
      } yield as.provide(r)
    }.flatMap(ZStream.repeatZIOChunkOption)

  /**
   * Provides a layer to the stream, which translates it to another level.
   */
  @deprecated("use provideDeps", "2.0.0")
  final def provideLayer[E1 >: E, R0, R1](
    layer: ZDeps[R0, E1, R1]
  )(implicit ev: R1 <:< R, trace: ZTraceElement): ZStream[R0, E1, O] =
    provideDeps(layer)

  /**
   * Provides some of the environment required to run this effect,
   * leaving the remainder `R0`.
   */
  final def provideSome[R0](env: R0 => R)(implicit ev: NeedsEnv[R], trace: ZTraceElement): ZStream[R0, E, O] =
    ZStream {
      for {
        r0 <- ZManaged.environment[R0]
        as <- self.process.provide(env(r0))
      } yield as.provide(env(r0))
    }

  /**
   * Splits the environment into two parts, providing one part using the
   * specified set of dependencies and leaving the remainder `R0`.
   *
   * {{{
   * val clockDeps: ZDeps[Any, Nothing, Clock] = ???
   *
   * val stream: ZStream[Clock with Has[Random], Nothing, Unit] = ???
   *
   * val stream2 = stream.provideSomeDeps[Has[Random]](clockDeps)
   * }}}
   */
  final def provideSomeDeps[R0]: ZStream.ProvideSomeDeps[R0, R, E, O] =
    new ZStream.ProvideSomeDeps[R0, R, E, O](self)

  /**
   * Splits the environment into two parts, providing one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZDeps[Any, Nothing, Clock] = ???
   *
   * val stream: ZStream[Clock with Has[Random], Nothing, Unit] = ???
   *
   * val stream2 = stream.provideSomeLayer[Has[Random]](clockLayer)
   * }}}
   */
  @deprecated("use provideSomeDeps", "2.0.0")
  final def provideSomeLayer[R0]: ZStream.ProvideSomeDeps[R0, R, E, O] =
    provideSomeDeps

  /**
   * Re-chunks the elements of the stream into chunks of
   * `n` elements each.
   * The last chunk might contain less than `n` elements
   */
  def rechunk(n: Int)(implicit trace: ZTraceElement): ZStream[R, E, O] = {
    case class State[X](buffer: Chunk[X], done: Boolean)

    def emitOrAccumulate(
      buffer: Chunk[O],
      done: Boolean,
      ref: Ref[State[O]],
      pull: ZIO[R, Option[E], Chunk[O]]
    ): ZIO[R, Option[E], Chunk[O]] =
      if (buffer.size < n) {
        if (done) {
          if (buffer.isEmpty)
            Pull.end
          else
            ref.set(State(Chunk.empty, true)) *> Pull.emit(buffer)
        } else
          pull.foldZIO(
            {
              case Some(e) => Pull.fail(e)
              case None    => emitOrAccumulate(buffer, true, ref, pull)
            },
            ch => emitOrAccumulate(buffer ++ ch, false, ref, pull)
          )
      } else {
        val (chunk, leftover) = buffer.splitAt(n)
        ref.set(State(leftover, done)) *> Pull.emit(chunk)
      }

    if (n < 1)
      ZStream.failCause(Cause.die(new IllegalArgumentException("rechunk: n must be at least 1")))
    else
      ZStream {
        for {
          ref <- ZRef.make[State[O]](State(Chunk.empty, false)).toManaged
          p   <- self.process
          pull = ref.get.flatMap(s => emitOrAccumulate(s.buffer, s.done, ref, p))
        } yield pull

      }
  }

  /**
   * Keeps some of the errors, and terminates the fiber with the rest
   */
  final def refineOrDie[E1](
    pf: PartialFunction[E, E1]
  )(implicit ev1: E <:< Throwable, ev2: CanFail[E], trace: ZTraceElement): ZStream[R, E1, O] =
    refineOrDieWith(pf)(ev1)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest, using
   * the specified function to convert the `E` into a `Throwable`.
   */
  final def refineOrDieWith[E1](
    pf: PartialFunction[E, E1]
  )(f: E => Throwable)(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R, E1, O] =
    self.catchAll(err => (pf lift err).fold[ZStream[R, E1, O]](ZStream.die(f(err)))(ZStream.fail(_)))

  /**
   * Repeats the entire stream using the specified schedule. The stream will execute normally,
   * and then repeat again according to the provided schedule.
   */
  final def repeat[R1 <: R, B](schedule: Schedule[R1, Any, B])(implicit
    trace: ZTraceElement
  ): ZStream[R1 with Has[Clock], E, O] =
    repeatEither(schedule) collect { case Right(a) => a }

  /**
   * Repeats the entire stream using the specified schedule. The stream will execute normally,
   * and then repeat again according to the provided schedule. The schedule output will be emitted at
   * the end of each repetition.
   */
  final def repeatEither[R1 <: R, B](schedule: Schedule[R1, Any, B])(implicit
    trace: ZTraceElement
  ): ZStream[R1 with Has[Clock], E, Either[B, O]] =
    repeatWith(schedule)(Right(_), Left(_))

  /**
   * Repeats each element of the stream using the provided schedule. Repetitions are done in
   * addition to the first execution, which means using `Schedule.recurs(1)` actually results in
   * the original effect, plus an additional recurrence, for a total of two repetitions of each
   * value in the stream.
   */
  final def repeatElements[R1 <: R](schedule: Schedule[R1, O, Any])(implicit
    trace: ZTraceElement
  ): ZStream[R1 with Has[Clock], E, O] =
    repeatElementsEither(schedule).collect { case Right(a) => a }

  /**
   * Repeats each element of the stream using the provided schedule. When the schedule is finished,
   * then the output of the schedule will be emitted into the stream. Repetitions are done in
   * addition to the first execution, which means using `Schedule.recurs(1)` actually results in
   * the original effect, plus an additional recurrence, for a total of two repetitions of each
   * value in the stream.
   */
  final def repeatElementsEither[R1 <: R, E1 >: E, B](
    schedule: Schedule[R1, O, B]
  )(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, Either[B, O]] =
    repeatElementsWith(schedule)(Right.apply, Left.apply)

  /**
   * Repeats each element of the stream using the provided schedule. When the schedule is finished,
   * then the output of the schedule will be emitted into the stream. Repetitions are done in
   * addition to the first execution, which means using `Schedule.recurs(1)` actually results in
   * the original effect, plus an additional recurrence, for a total of two repetitions of each
   * value in the stream.
   *
   * This function accepts two conversion functions, which allow the output of this stream and the
   * output of the provided schedule to be unified into a single type. For example, `Either` or
   * similar data type.
   */
  final def repeatElementsWith[R1 <: R, E1 >: E, B, C](
    schedule: Schedule[R1, O, B]
  )(f: O => C, g: B => C)(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, C] =
    ZStream {
      for {
        as     <- self.process.mapZIO(BufferedPull.make(_))
        driver <- schedule.driver.toManaged
        state  <- Ref.make[Option[O]](None).toManaged
        pull = {
          def go: ZIO[R1 with Has[Clock], Option[E1], Chunk[C]] =
            state.get.flatMap {
              case None =>
                as.pullElement.flatMap(o => state.set(Some(o)) as Chunk.single(f(o)))

              case Some(o) =>
                val advance = driver.next(o) as Chunk(f(o))
                val reset   = driver.last.orDie.map(b => Chunk.single(g(b))) <* driver.reset <* state.set(None)

                advance orElse reset
            }

          go
        }
      } yield pull
    }

  /**
   * Repeats the entire stream using the specified schedule. The stream will execute normally,
   * and then repeat again according to the provided schedule. The schedule output will be emitted at
   * the end of each repetition and can be unified with the stream elements using the provided functions.
   */
  final def repeatWith[R1 <: R, B, C](
    schedule: Schedule[R1, Any, B]
  )(f: O => C, g: B => C)(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E, C] =
    ZStream[R1 with Has[Clock], E, C] {
      for {
        sdriver    <- schedule.driver.toManaged
        switchPull <- ZManaged.switchable[R1, Nothing, ZIO[R1, Option[E], Chunk[C]]]
        currPull   <- switchPull(self.map(f).process).flatMap(as => Ref.make(as)).toManaged
        doneRef    <- Ref.make(false).toManaged
        pull = {
          def go: ZIO[R1 with Has[Clock], Option[E], Chunk[C]] =
            doneRef.get.flatMap { done =>
              if (done) Pull.end
              else
                currPull.get.flatten.foldZIO(
                  {
                    case e @ Some(_) => ZIO.fail(e)
                    case None =>
                      val scheduleOutput = sdriver.last.orDie.map(g)

                      sdriver
                        .next(())
                        .foldZIO(
                          _ => doneRef.set(true) *> Pull.end,
                          _ =>
                            switchPull((self.map(f) ++ ZStream.fromZIO(scheduleOutput)).process)
                              .tap(currPull.set(_)) *> go
                        )
                  },
                  ZIO.succeedNow(_)
                )
            }
          go
        }
      } yield pull
    }

  /**
   * When the stream fails, retry it according to the given schedule
   *
   * This retries the entire stream, so will re-execute all of the stream's acquire operations.
   *
   * The schedule is reset as soon as the first element passes through the stream again.
   *
   * @param schedule Schedule receiving as input the errors of the stream
   * @return Stream outputting elements of all attempts of the stream
   */
  def retry[R1 <: R](schedule: Schedule[R1, E, _])(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E, O] =
    ZStream {
      for {
        driver       <- schedule.driver.toManaged
        currStream   <- Ref.make[ZIO[R, Option[E], Chunk[O]]](Pull.end).toManaged
        switchStream <- ZManaged.switchable[R, Nothing, ZIO[R, Option[E], Chunk[O]]]
        _            <- switchStream(self.process).flatMap(currStream.set).toManaged
        pull = {
          def loop: ZIO[R1 with Has[Clock], Option[E], Chunk[O]] =
            currStream.get.flatten.catchSome { case Some(e) =>
              driver
                .next(e)
                .foldZIO(
                  // Failure of the schedule indicates it doesn't accept the input
                  _ => Pull.fail(e),
                  _ =>
                    switchStream(self.process).flatMap(currStream.set) *>
                      // Reset the schedule to its initial state when a chunk is successfully pulled
                      loop.tap(_ => driver.reset)
                )
            }

          loop
        }
      } yield pull
    }

  /**
   * Fails with the error `None` if value is `Left`.
   */
  final def right[O1, O2](implicit ev: O <:< Either[O1, O2], trace: ZTraceElement): ZStream[R, Option[E], O2] =
    self.mapError(Some(_)).rightOrFail(None)

  /**
   * Fails with given error 'e' if value is `Left`.
   */
  final def rightOrFail[O1, O2, E1 >: E](
    e: => E1
  )(implicit ev: O <:< Either[O1, O2], trace: ZTraceElement): ZStream[R, E1, O2] =
    self.mapZIO(ev(_).fold(_ => ZIO.fail(e), ZIO.succeedNow(_)))

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  def run[R1 <: R, E1 >: E, B](sink: ZSink[R1, E1, O, Any, B])(implicit trace: ZTraceElement): ZIO[R1, E1, B] =
    runManaged(sink).useNow

  def runManaged[R1 <: R, E1 >: E, B](sink: ZSink[R1, E1, O, Any, B])(implicit
    trace: ZTraceElement
  ): ZManaged[R1, E1, B] =
    (process <*> sink.push).mapZIO { case (pull, push) =>
      def go: ZIO[R1, E1, B] = pull.foldCauseZIO(
        Cause
          .flipCauseOption(_)
          .fold(
            push(None).foldCauseZIO(
              c => Cause.flipCauseEither(c.map(_._1)).fold(IO.failCause(_), ZIO.succeedNow),
              _ => IO.dieMessage("empty stream / empty sinks")
            )
          )(IO.failCause(_)),
        os =>
          push(Some(os))
            .foldCauseZIO(c => Cause.flipCauseEither(c.map(_._1)).fold(IO.failCause(_), ZIO.succeedNow), _ => go)
      )

      go
    }

  /**
   * Runs the stream and collects all of its elements to a chunk.
   */
  def runCollect(implicit trace: ZTraceElement): ZIO[R, E, Chunk[O]] = run(ZSink.collectAll[O])

  /**
   * Runs the stream and emits the number of elements processed
   *
   * Equivalent to `run(ZSink.count)`
   */
  final def runCount(implicit trace: ZTraceElement): ZIO[R, E, Long] = self.run(ZSink.count)

  /**
   * Runs the stream only for its effects. The emitted elements are discarded.
   */
  def runDrain(implicit trace: ZTraceElement): ZIO[R, E, Unit] =
    foreach(_ => ZIO.unit)

  /**
   * Runs the stream to collect the first value emitted by it without running
   * the rest of the stream.
   */
  def runHead(implicit trace: ZTraceElement): ZIO[R, E, Option[O]] =
    run(ZSink.head)

  /**
   * Runs the stream to completion and yields the last value emitted by it,
   * discarding the rest of the elements.
   */
  def runLast(implicit trace: ZTraceElement): ZIO[R, E, Option[O]] =
    run(ZSink.last)

  /**
   * Runs the stream to a sink which sums elements, provided they are Numeric.
   *
   * Equivalent to `run(Sink.sum[A])`
   */
  final def runSum[O1 >: O](implicit ev: Numeric[O1], trace: ZTraceElement): ZIO[R, E, O1] = run(ZSink.sum[O1])

  /**
   * Statefully maps over the elements of this stream to produce all intermediate results
   * of type `S` given an initial S.
   */
  def scan[S](s: S)(f: (S, O) => S)(implicit trace: ZTraceElement): ZStream[R, E, S] =
    scanZIO(s)((s, a) => ZIO.succeedNow(f(s, a)))

  /**
   * Statefully and effectfully maps over the elements of this stream to produce all
   * intermediate results of type `S` given an initial S.
   */
  @deprecated("use scanZIO", "2.0.0")
  def scanM[R1 <: R, E1 >: E, S](s: S)(f: (S, O) => ZIO[R1, E1, S])(implicit trace: ZTraceElement): ZStream[R1, E1, S] =
    scanZIO[R1, E1, S](s)(f)

  /**
   * Statefully maps over the elements of this stream to produce all intermediate results.
   *
   * See also [[ZStream#scan]].
   */
  def scanReduce[O1 >: O](f: (O1, O) => O1)(implicit trace: ZTraceElement): ZStream[R, E, O1] =
    scanReduceZIO[R, E, O1]((curr, next) => ZIO.succeedNow(f(curr, next)))

  /**
   * Statefully and effectfully maps over the elements of this stream to produce all
   * intermediate results.
   *
   * See also [[ZStream#scanM]].
   */
  @deprecated("use scanReduceZIO", "2.0.0")
  def scanReduceM[R1 <: R, E1 >: E, O1 >: O](f: (O1, O) => ZIO[R1, E1, O1])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    scanReduceZIO(f)

  /**
   * Statefully and effectfully maps over the elements of this stream to produce all
   * intermediate results.
   *
   * See also [[ZStream#scanM]].
   */
  def scanReduceZIO[R1 <: R, E1 >: E, O1 >: O](
    f: (O1, O) => ZIO[R1, E1, O1]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O1] =
    ZStream[R1, E1, O1] {
      for {
        state <- Ref.makeManaged[Option[O1]](None)
        pull  <- self.process.mapZIO(BufferedPull.make(_))
      } yield pull.pullElement.flatMap { curr =>
        state.get.flatMap {
          case Some(s) => f(s, curr).tap(o => state.set(Some(o))).map(Chunk.single).asSomeError
          case None    => state.set(Some(curr)).as(Chunk.single(curr))
        }
      }
    }

  /**
   * Statefully and effectfully maps over the elements of this stream to produce all
   * intermediate results of type `S` given an initial S.
   */
  def scanZIO[R1 <: R, E1 >: E, S](s: S)(f: (S, O) => ZIO[R1, E1, S])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, S] =
    ZStream(s) ++ mapAccumZIO[R1, E1, S, S](s)((s, a) => f(s, a).map(s => (s, s)))

  /**
   * Schedules the output of the stream using the provided `schedule`.
   */
  final def schedule[R1 <: R](schedule: Schedule[R1, O, Any])(implicit
    trace: ZTraceElement
  ): ZStream[R1 with Has[Clock], E, O] =
    scheduleEither(schedule).collect { case Right(a) => a }

  /**
   * Schedules the output of the stream using the provided `schedule` and emits its output at
   * the end (if `schedule` is finite).
   */
  final def scheduleEither[R1 <: R, E1 >: E, B](
    schedule: Schedule[R1, O, B]
  )(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, Either[B, O]] =
    scheduleWith(schedule)(Right.apply, Left.apply)

  /**
   * Schedules the output of the stream using the provided `schedule` and emits its output at
   * the end (if `schedule` is finite).
   * Uses the provided function to align the stream and schedule outputs on the same type.
   */
  final def scheduleWith[R1 <: R, E1 >: E, B, C](
    schedule: Schedule[R1, O, B]
  )(f: O => C, g: B => C)(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, C] =
    ZStream[R1 with Has[Clock], E1, C] {
      for {
        as     <- self.process.mapZIO(BufferedPull.make(_))
        driver <- schedule.driver.toManaged
        pull = as.pullElement.flatMap(o =>
                 driver.next(o).as(Chunk(f(o))) orElse (driver.last.orDie.map(b => Chunk(f(o), g(b))) <* driver.reset)
               )
      } yield pull
    }

  /**
   * Converts an option on values into an option on errors.
   */
  final def some[O2](implicit ev: O <:< Option[O2], trace: ZTraceElement): ZStream[R, Option[E], O2] =
    self.mapError(Some(_)).someOrFail(None)

  /**
   * Extracts the optional value, or returns the given 'default'.
   */
  final def someOrElse[O2](default: => O2)(implicit ev: O <:< Option[O2], trace: ZTraceElement): ZStream[R, E, O2] =
    map(_.getOrElse(default))

  /**
   * Extracts the optional value, or fails with the given error 'e'.
   */
  final def someOrFail[O2, E1 >: E](e: => E1)(implicit ev: O <:< Option[O2], trace: ZTraceElement): ZStream[R, E1, O2] =
    self.mapZIO(ev(_).fold[IO[E1, O2]](ZIO.fail(e))(ZIO.succeedNow(_)))

  /**
   * Takes the specified number of elements from this stream.
   */
  def take(n: Long)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    if (n <= 0) ZStream.empty
    else
      ZStream {
        for {
          chunks     <- self.process
          counterRef <- Ref.make(0L).toManaged
          pull = counterRef.get.flatMap { cnt =>
                   if (cnt >= n) Pull.end
                   else
                     for {
                       chunk <- chunks
                       taken = if (chunk.size <= (n - cnt)) chunk
                               // The difference (n - cnt) is smaller than chunk.size, which
                               // is an int, so this int coercion is safe.
                               else chunk.take((n - cnt).toInt)
                       _ <- counterRef.set(cnt + taken.length)
                     } yield taken
                 }
        } yield pull
      }

  /**
   * Takes the last specified number of elements from this stream.
   */
  def takeRight(n: Int)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    if (n <= 0) ZStream.empty
    else
      ZStream {
        for {
          pull  <- self.process.mapZIO(BufferedPull.make(_))
          queue <- ZQueue.sliding[O](n).toManaged
          done  <- Ref.makeManaged(false)
        } yield done.get.flatMap {
          if (_) Pull.end
          else
            pull.pullElement.tap(queue.offer).as(Chunk.empty).catchSome { case None =>
              done.set(true) *> queue.takeAll.map(Chunk.fromIterable(_))
            }
        }
      }

  /**
   * Takes all elements of the stream until the specified predicate evaluates
   * to `true`.
   */
  def takeUntil(pred: O => Boolean)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        chunks        <- self.process
        keepTakingRef <- Ref.make(true).toManaged
        pull = keepTakingRef.get.flatMap { keepTaking =>
                 if (!keepTaking) Pull.end
                 else
                   for {
                     chunk <- chunks
                     taken  = chunk.takeWhile(!pred(_))
                     last   = chunk.drop(taken.length).take(1)
                     _     <- keepTakingRef.set(false).when(last.nonEmpty)
                   } yield taken ++ last
               }
      } yield pull
    }

  /**
   * Takes all elements of the stream until the specified effectual predicate
   * evaluates to `true`.
   */
  @deprecated("use takeUntilZIO", "2.0.0")
  def takeUntilM[R1 <: R, E1 >: E](pred: O => ZIO[R1, E1, Boolean])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    takeUntilZIO(pred)

  /**
   * Takes all elements of the stream until the specified effectual predicate
   * evaluates to `true`.
   */
  def takeUntilZIO[R1 <: R, E1 >: E](
    pred: O => ZIO[R1, E1, Boolean]
  )(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    ZStream {
      for {
        chunks        <- self.process
        keepTakingRef <- Ref.make(true).toManaged
        pull = keepTakingRef.get.flatMap { keepTaking =>
                 if (!keepTaking) Pull.end
                 else
                   for {
                     chunk <- chunks
                     taken <- chunk.takeWhileZIO(pred(_).map(!_)).asSomeError
                     last   = chunk.drop(taken.length).take(1)
                     _     <- keepTakingRef.set(false).when(last.nonEmpty)
                   } yield taken ++ last
               }
      } yield pull
    }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def takeWhile(pred: O => Boolean)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      for {
        chunks  <- self.process
        doneRef <- Ref.make(false).toManaged
        pull = doneRef.get.flatMap {
                 if (_) Pull.end
                 else
                   for {
                     chunk <- chunks
                     taken  = chunk.takeWhile(pred)
                     _     <- doneRef.set(true).when(taken.length < chunk.length)
                   } yield taken
               }
      } yield pull
    }

  /**
   * Adds an effect to consumption of every element of the stream.
   */
  final def tap[R1 <: R, E1 >: E](f0: O => ZIO[R1, E1, Any])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    mapZIO(o => f0(o).as(o))

  /**
   * Returns a stream that effectfully "peeks" at the failure of the stream.
   */
  final def tapError[R1 <: R, E1 >: E](
    f: E => ZIO[R1, E1, Any]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R1, E1, O] =
    ZStream(self.process.map(_.tapError {
      case None      => ZIO.fail(None)
      case Some(err) => f(err).mapError(Some(_))
    }))

  /**
   * Throttles the chunks of this stream according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. Chunks that do not meet the bandwidth constraints are dropped.
   * The weight of each chunk is determined by the `costFn` function.
   */
  final def throttleEnforce(units: Long, duration: Duration, burst: Long = 0)(
    costFn: Chunk[O] => Long
  )(implicit trace: ZTraceElement): ZStream[R with Has[Clock], E, O] =
    throttleEnforceZIO(units, duration, burst)(os => UIO.succeedNow(costFn(os)))

  /**
   * Throttles the chunks of this stream according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. Chunks that do not meet the bandwidth constraints are dropped.
   * The weight of each chunk is determined by the `costFn` effectful function.
   */
  @deprecated("use throttleEnforceZIO", "2.0.0")
  final def throttleEnforceM[R1 <: R, E1 >: E](units: Long, duration: Duration, burst: Long = 0)(
    costFn: Chunk[O] => ZIO[R1, E1, Long]
  )(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, O] =
    throttleEnforceZIO[R1 with Has[Clock], E1](units, duration, burst)(costFn)

  /**
   * Throttles the chunks of this stream according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. Chunks that do not meet the bandwidth constraints are dropped.
   * The weight of each chunk is determined by the `costFn` effectful function.
   */
  final def throttleEnforceZIO[R1 <: R, E1 >: E](units: Long, duration: Duration, burst: Long = 0)(
    costFn: Chunk[O] => ZIO[R1, E1, Long]
  )(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, O] =
    ZStream {
      for {
        chunks      <- self.process
        currentTime <- Clock.nanoTime.toManaged
        bucket      <- Ref.make((units, currentTime)).toManaged
        pull = {
          def go: ZIO[R1 with Has[Clock], Option[E1], Chunk[O]] =
            chunks.flatMap { chunk =>
              (costFn(chunk).mapError(Some(_)) <*> Clock.nanoTime) flatMap { case (weight, current) =>
                bucket.modify { case (tokens, timestamp) =>
                  val elapsed = current - timestamp
                  val cycles  = elapsed.toDouble / duration.toNanos
                  val available = {
                    val sum = tokens + (cycles * units).toLong
                    val max =
                      if (units + burst < 0) Long.MaxValue
                      else units + burst

                    if (sum < 0) max
                    else math.min(sum, max)
                  }

                  if (weight <= available)
                    (Some(chunk), (available - weight, current))
                  else
                    (None, (available, current))
                } flatMap {
                  case Some(os) => UIO.succeedNow(os)
                  case None     => go
                }
              }
            }

          go
        }
      } yield pull
    }

  /**
   * Delays the chunks of this stream according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. The weight of each chunk is determined by the `costFn`
   * function.
   */
  final def throttleShape(units: Long, duration: Duration, burst: Long = 0)(
    costFn: Chunk[O] => Long
  )(implicit trace: ZTraceElement): ZStream[R with Has[Clock], E, O] =
    throttleShapeZIO(units, duration, burst)(os => UIO.succeedNow(costFn(os)))

  /**
   * Delays the chunks of this stream according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. The weight of each chunk is determined by the `costFn`
   * effectful function.
   */
  @deprecated("use throttleShapeZIO", "2.0.0")
  final def throttleShapeM[R1 <: R, E1 >: E](units: Long, duration: Duration, burst: Long = 0)(
    costFn: Chunk[O] => ZIO[R1, E1, Long]
  )(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, O] =
    throttleShapeZIO[R1 with Has[Clock], E1](units, duration, burst)(costFn)

  /**
   * Delays the chunks of this stream according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. The weight of each chunk is determined by the `costFn`
   * effectful function.
   */
  final def throttleShapeZIO[R1 <: R, E1 >: E](units: Long, duration: Duration, burst: Long = 0)(
    costFn: Chunk[O] => ZIO[R1, E1, Long]
  )(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, O] =
    ZStream {
      for {
        chunks      <- self.process
        currentTime <- Clock.nanoTime.toManaged
        bucket      <- Ref.make((units, currentTime)).toManaged
        pull = for {
                 chunk   <- chunks
                 weight  <- costFn(chunk).mapError(Some(_))
                 current <- Clock.nanoTime
                 delay <- bucket.modify { case (tokens, timestamp) =>
                            val elapsed = current - timestamp
                            val cycles  = elapsed.toDouble / duration.toNanos
                            val available = {
                              val sum = tokens + (cycles * units).toLong
                              val max =
                                if (units + burst < 0) Long.MaxValue
                                else units + burst

                              if (sum < 0) max
                              else math.min(sum, max)
                            }

                            val remaining = available - weight
                            val waitCycles =
                              if (remaining >= 0) 0
                              else -remaining.toDouble / units
                            val delay = Duration.Finite((waitCycles * duration.toNanos).toLong)

                            (delay, (remaining, current))

                          }
                 _ <- Clock.sleep(delay).when(delay > Duration.Zero)
               } yield chunk
      } yield pull
    }

  final def debounce[E1 >: E, O2 >: O](
    d: Duration
  )(implicit trace: ZTraceElement): ZStream[R with Has[Clock], E1, O2] = {
    sealed abstract class State
    case object NotStarted                                  extends State
    case class Previous(fiber: Fiber[Nothing, O2])          extends State
    case class Current(fiber: Fiber[Option[E1], Chunk[O2]]) extends State
    case object Done                                        extends State

    ZStream[R with Has[Clock], E1, O2] {
      for {
        chunks <- self.process
        ref <- Ref.make[State](NotStarted).toManagedWith {
                 _.get.flatMap {
                   case Previous(fiber) => fiber.interrupt
                   case Current(fiber)  => fiber.interrupt
                   case _               => ZIO.unit
                 }
               }
        pull = {
          def store(chunk: Chunk[O2]): URIO[Has[Clock], Chunk[O2]] =
            chunk.lastOption
              .map(last => Clock.sleep(d).as(last).forkDaemon.flatMap(f => ref.set(Previous(f))))
              .getOrElse(ref.set(NotStarted))
              .as(Chunk.empty)

          ref.get.flatMap {
            case Previous(fiber) =>
              fiber.join.raceWith[R with Has[Clock], Option[E1], Option[E1], Chunk[O2], Chunk[O2]](chunks)(
                {
                  case (Exit.Success(value), current) =>
                    ref.set(Current(current)).as(Chunk.single(value))
                  case (Exit.Failure(cause), current) =>
                    current.interrupt *> Pull.failCause(cause)
                },
                {
                  case (Exit.Success(chunk), _) if chunk.isEmpty =>
                    Pull.empty
                  case (Exit.Success(chunk), previous) =>
                    previous.interrupt *> store(chunk)
                  case (Exit.Failure(cause), previous) =>
                    Cause.flipCauseOption(cause) match {
                      case Some(e) =>
                        previous.interrupt *> Pull.failCause(e)
                      case None =>
                        previous.join.map(Chunk.single) <* ref.set(Done)
                    }
                },
                Some(ZScope.global)
              )
            case Current(fiber) =>
              fiber.join.flatMap(store)
            case NotStarted =>
              chunks.flatMap(store)
            case Done =>
              Pull.end
          }
        }
      } yield pull
    }
  }

  /**
   * Ends the stream if it does not produce a value after d duration.
   */
  final def timeout(d: Duration)(implicit trace: ZTraceElement): ZStream[R with Has[Clock], E, O] =
    ZStream[R with Has[Clock], E, O] {
      for {
        timeout <- Ref.make(false).toManaged
        next    <- self.process
        pull = timeout.get.flatMap {
                 if (_) Pull.end
                 else
                   next.timeout(d).flatMap {
                     case Some(a) => Pull.emit(a)
                     case None    => timeout.set(true) *> Pull.end
                   }
               }
      } yield pull
    }

  /**
   * Fails the stream with given error if it does not produce a value after d duration.
   */
  final def timeoutError[E1 >: E](e: => E1)(d: Duration)(implicit
    trace: ZTraceElement
  ): ZStream[R with Has[Clock], E1, O] =
    self.timeoutTo[R with Has[Clock], E1, O](d)(ZStream.fail(e))

  /**
   * Halts the stream with given cause if it does not produce a value after d duration.
   */
  final def timeoutErrorCause[E1 >: E](
    cause: Cause[E1]
  )(d: Duration)(implicit trace: ZTraceElement): ZStream[R with Has[Clock], E1, O] =
    ZStream[R with Has[Clock], E1, O] {
      self.process.map { next =>
        next.timeout(d).flatMap {
          case Some(a) => Pull.emit(a)
          case None    => Pull.failCause(cause)
        }
      }
    }

  /**
   * Switches the stream if it does not produce a value after d duration.
   */
  final def timeoutTo[R1 <: R, E1 >: E, O2 >: O](
    d: Duration
  )(that: ZStream[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1 with Has[Clock], E1, O2] = {
    object StreamTimeout extends Throwable
    self.timeoutErrorCause(Cause.die(StreamTimeout))(d).catchSomeCause { case Cause.Die(StreamTimeout) => that }
  }

  /**
   * Converts the stream to a managed hub of chunks. After the managed hub is
   * used, the hub will never again produce values and should be discarded.
   */
  def toHub(
    capacity: Int
  )(implicit trace: ZTraceElement): ZManaged[R, Nothing, ZHub[Nothing, Any, Any, Nothing, Nothing, Take[E, O]]] =
    for {
      hub <- Hub.bounded[Take[E, O]](capacity).toManagedWith(_.shutdown)
      _   <- self.intoHubManaged(hub).fork
    } yield hub

  /**
   * Converts this stream of bytes into a `java.io.InputStream` wrapped in a [[ZManaged]].
   * The returned input stream will only be valid within the scope of the ZManaged.
   */
  def toInputStream(implicit
    ev0: E <:< Throwable,
    ev1: O <:< Byte,
    trace: ZTraceElement
  ): ZManaged[R, E, java.io.InputStream] =
    for {
      runtime <- ZIO.runtime[R].toManaged
      pull    <- process.asInstanceOf[ZManaged[R, Nothing, ZIO[R, Option[Throwable], Chunk[Byte]]]]
    } yield ZInputStream.fromPull(runtime, pull)

  /**
   * Converts this stream into a `scala.collection.Iterator` wrapped in a [[ZManaged]].
   * The returned iterator will only be valid within the scope of the ZManaged.
   */
  def toIterator(implicit trace: ZTraceElement): ZManaged[R, Nothing, Iterator[Either[E, O]]] =
    for {
      runtime <- ZIO.runtime[R].toManaged
      pull    <- process
    } yield {
      def unfoldPull: Iterator[Either[E, O]] =
        runtime.unsafeRunSync(pull) match {
          case Exit.Success(chunk) => chunk.iterator.map(Right(_)) ++ unfoldPull
          case Exit.Failure(cause) =>
            cause.failureOrCause match {
              case Left(None)    => Iterator.empty
              case Left(Some(e)) => Iterator.single(Left(e))
              case Right(c)      => throw FiberFailure(c)
            }
        }

      unfoldPull
    }

  /**
   * Converts this stream of chars into a `java.io.Reader` wrapped in a [[ZManaged]].
   * The returned reader will only be valid within the scope of the ZManaged.
   */
  def toReader(implicit ev0: E <:< Throwable, ev1: O <:< Char, trace: ZTraceElement): ZManaged[R, E, java.io.Reader] =
    for {
      runtime <- ZIO.runtime[R].toManaged
      pull    <- process.asInstanceOf[ZManaged[R, Nothing, ZIO[R, Option[Throwable], Chunk[Char]]]]
    } yield ZReader.fromPull(runtime, pull)

  /**
   * Converts the stream to a managed queue of chunks. After the managed queue is used,
   * the queue will never again produce values and should be discarded.
   */
  final def toQueue(capacity: Int = 2)(implicit trace: ZTraceElement): ZManaged[R, Nothing, Dequeue[Take[E, O]]] =
    for {
      queue <- Queue.bounded[Take[E, O]](capacity).toManagedWith(_.shutdown)
      _     <- self.intoQueueManaged(queue).fork
    } yield queue

  /**
   * Converts the stream into an unbounded managed queue. After the managed queue
   * is used, the queue will never again produce values and should be discarded.
   */
  final def toQueueUnbounded(implicit trace: ZTraceElement): ZManaged[R, Nothing, Dequeue[Take[E, O]]] =
    for {
      queue <- Queue.unbounded[Take[E, O]].toManagedWith(_.shutdown)
      _     <- self.intoQueueManaged(queue).fork
    } yield queue

  /**
   * Applies the transducer to the stream and emits its outputs.
   */
  def transduce[R1 <: R, E1 >: E, O3](transducer: ZTransducer[R1, E1, O, O3])(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O3] =
    aggregate(transducer)

  /**
   * Updates a service in the environment of this effect.
   */
  final def updateService[M] =
    new ZStream.UpdateService[R, E, O, M](self)

  /**
   * Updates a service at the specified key in the environment of this effect.
   */
  final def updateServiceAt[Service]: ZStream.UpdateServiceAt[R, E, O, Service] =
    new ZStream.UpdateServiceAt[R, E, O, Service](self)

  /**
   * Threads the stream through the transformation function `f`.
   */
  final def via[R2, E2, O2](f: ZStream[R, E, O] => ZStream[R2, E2, O2])(implicit
    trace: ZTraceElement
  ): ZStream[R2, E2, O2] = f(self)

  /**
   * Returns this stream if the specified condition is satisfied, otherwise returns an empty stream.
   */
  def when(b: => Boolean)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream.when(b)(self)

  /**
   * Returns this stream if the specified effectful condition is satisfied, otherwise returns an empty stream.
   */
  @deprecated("use whenZIO", "2.0.0")
  def whenM[R1 <: R, E1 >: E](b: ZIO[R1, E1, Boolean])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    whenZIO(b)

  /**
   * Returns this stream if the specified effectful condition is satisfied, otherwise returns an empty stream.
   */
  def whenZIO[R1 <: R, E1 >: E](b: ZIO[R1, E1, Boolean])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    ZStream.whenZIO(b)(self)

  /**
   * Equivalent to [[filter]] but enables the use of filter clauses in for-comprehensions
   */
  def withFilter(predicate: O => Boolean)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    filter(predicate)

  /**
   * Runs this stream on the specified runtime configuration. Any streams that
   * are composed after this one will be run on the previous executor.
   */
  def withRuntimeConfig(runtimeConfig: => RuntimeConfig)(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream.fromZIO(ZIO.runtimeConfig).flatMap { currentRuntimeConfig =>
      ZStream.managed(ZManaged.withRuntimeConfig(runtimeConfig)) *>
        self <*
        ZStream.fromZIO(ZIO.setRuntimeConfig(currentRuntimeConfig))
    }

  /**
   * Zips this stream with another point-wise, but keeps only the outputs of this stream.
   *
   * The new stream will end when one of the sides ends.
   */
  def zipLeft[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
    zipWith(that)((o, _) => o)

  /**
   * Zips this stream with another point-wise, but keeps only the outputs of the other stream.
   *
   * The new stream will end when one of the sides ends.
   */
  def zipRight[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit trace: ZTraceElement): ZStream[R1, E1, O2] =
    zipWith(that)((_, o2) => o2)

  /**
   * Zips this stream with another point-wise and emits tuples of elements from both streams.
   *
   * The new stream will end when one of the sides ends.
   */
  def zip[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(implicit
    zippable: Zippable[O, O2],
    trace: ZTraceElement
  ): ZStream[R1, E1, zippable.Out] =
    zipWith(that)(zippable.zip(_, _))

  /**
   * Zips this stream with another point-wise, creating a new stream of pairs of elements
   * from both sides.
   *
   * The defaults `defaultLeft` and `defaultRight` will be used if the streams have different lengths
   * and one of the streams has ended before the other.
   */
  def zipAll[R1 <: R, E1 >: E, O1 >: O, O2](
    that: ZStream[R1, E1, O2]
  )(defaultLeft: O1, defaultRight: O2)(implicit trace: ZTraceElement): ZStream[R1, E1, (O1, O2)] =
    zipAllWith(that)((_, defaultRight), (defaultLeft, _))((_, _))

  /**
   * Zips this stream with another point-wise, and keeps only elements from this stream.
   *
   * The provided default value will be used if the other stream ends before this one.
   */
  def zipAllLeft[R1 <: R, E1 >: E, O1 >: O, O2](that: ZStream[R1, E1, O2])(default: O1)(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O1] =
    zipAllWith(that)(identity, _ => default)((o, _) => o)

  /**
   * Zips this stream with another point-wise, and keeps only elements from the other stream.
   *
   * The provided default value will be used if this stream ends before the other one.
   */
  def zipAllRight[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(default: O2)(implicit
    trace: ZTraceElement
  ): ZStream[R1, E1, O2] =
    zipAllWith(that)(_ => default, identity)((_, o2) => o2)

  /**
   * Zips this stream with another point-wise. The provided functions will be used to create elements
   * for the composed stream.
   *
   * The functions `left` and `right` will be used if the streams have different lengths
   * and one of the streams has ended before the other.
   */
  def zipAllWith[R1 <: R, E1 >: E, O2, O3](
    that: ZStream[R1, E1, O2]
  )(left: O => O3, right: O2 => O3)(both: (O, O2) => O3)(implicit trace: ZTraceElement): ZStream[R1, E1, O3] =
    zipAllWithExec(that)(ExecutionStrategy.Parallel)(left, right)(both)

  /**
   * Zips this stream with another point-wise. The provided functions will be used to create elements
   * for the composed stream.
   *
   * The functions `left` and `right` will be used if the streams have different lengths
   * and one of the streams has ended before the other.
   *
   * The execution strategy `exec` will be used to determine whether to pull
   * from the streams sequentially or in parallel.
   */
  def zipAllWithExec[R1 <: R, E1 >: E, O2, O3](
    that: ZStream[R1, E1, O2]
  )(
    exec: ExecutionStrategy
  )(left: O => O3, right: O2 => O3)(both: (O, O2) => O3)(implicit trace: ZTraceElement): ZStream[R1, E1, O3] = {
    sealed trait Status
    case object Running   extends Status
    case object LeftDone  extends Status
    case object RightDone extends Status
    case object End       extends Status
    type State = (Status, Either[Chunk[O], Chunk[O2]])

    def handleSuccess(
      maybeO: Option[Chunk[O]],
      maybeO2: Option[Chunk[O2]],
      excess: Either[Chunk[O], Chunk[O2]]
    ): Exit[Nothing, (Chunk[O3], State)] = {
      val (excessL, excessR) = excess.fold(l => (l, Chunk.empty), r => (Chunk.empty, r))
      val chunkL             = maybeO.fold(excessL)(upd => excessL ++ upd)
      val chunkR             = maybeO2.fold(excessR)(upd => excessR ++ upd)
      val (emit, newExcess)  = zipChunks(chunkL, chunkR, both)
      val (fullEmit, status) = (maybeO.isDefined, maybeO2.isDefined) match {
        case (true, true) => (emit, Running)
        case (false, false) =>
          val leftover: Chunk[O3] = newExcess.fold[Chunk[O3]](_.map(left), _.map(right))
          (emit ++ leftover, End)
        case (false, true) => (emit, LeftDone)
        case (true, false) => (emit, RightDone)
      }
      Exit.succeed((fullEmit, (status, newExcess)))
    }

    combineChunks(that)((Running, Left(Chunk())): State) {
      case ((Running, excess), pullL, pullR) =>
        exec match {
          case ExecutionStrategy.Sequential =>
            pullL.unsome
              .zipWith(pullR.unsome)(handleSuccess(_, _, excess))
              .catchAllCause(e => UIO.succeedNow(Exit.failCause(e.map(Some(_)))))
          case _ =>
            pullL.unsome
              .zipWithPar(pullR.unsome)(handleSuccess(_, _, excess))
              .catchAllCause(e => UIO.succeedNow(Exit.failCause(e.map(Some(_)))))
        }
      case ((LeftDone, excess), _, pullR) =>
        pullR.unsome
          .map(handleSuccess(None, _, excess))
          .catchAllCause(e => UIO.succeedNow(Exit.failCause(e.map(Some(_)))))
      case ((RightDone, excess), pullL, _) =>
        pullL.unsome
          .map(handleSuccess(_, None, excess))
          .catchAllCause(e => UIO.succeedNow(Exit.failCause(e.map(Some(_)))))
      case ((End, _), _, _) => UIO.succeedNow(Exit.fail(None))
    }
  }

  /**
   * Zips this stream with another point-wise and applies the function to the paired elements.
   *
   * The new stream will end when one of the sides ends.
   */
  def zipWith[R1 <: R, E1 >: E, O2, O3](
    that: ZStream[R1, E1, O2]
  )(f: (O, O2) => O3)(implicit trace: ZTraceElement): ZStream[R1, E1, O3] = {
    sealed trait State[+W1, +W2]
    case class Running[W1, W2](excess: Either[Chunk[W1], Chunk[W2]]) extends State[W1, W2]
    case class LeftDone[W1](excessL: NonEmptyChunk[W1])              extends State[W1, Nothing]
    case class RightDone[W2](excessR: NonEmptyChunk[W2])             extends State[Nothing, W2]
    case object End                                                  extends State[Nothing, Nothing]

    def handleSuccess(
      leftUpd: Option[Chunk[O]],
      rightUpd: Option[Chunk[O2]],
      excess: Either[Chunk[O], Chunk[O2]]
    ): Exit[Option[Nothing], (Chunk[O3], State[O, O2])] = {
      val (left, right) = {
        val (leftExcess, rightExcess) = excess.fold(l => (l, Chunk.empty), r => (Chunk.empty, r))
        val l                         = leftUpd.fold(leftExcess)(upd => leftExcess ++ upd)
        val r                         = rightUpd.fold(rightExcess)(upd => rightExcess ++ upd)
        (l, r)
      }
      val (emit, newExcess): (Chunk[O3], Either[Chunk[O], Chunk[O2]]) = zipChunks(left, right, f)
      (leftUpd.isDefined, rightUpd.isDefined) match {
        case (true, true)   => Exit.succeed((emit, Running(newExcess)))
        case (false, false) => Exit.fail(None)
        case _ => {
          val newState = newExcess match {
            case Left(l)  => l.nonEmptyOrElse[State[O, O2]](End)(LeftDone(_))
            case Right(r) => r.nonEmptyOrElse[State[O, O2]](End)(RightDone(_))
          }
          Exit.succeed((emit, newState))
        }
      }
    }

    combineChunks(that)(Running(Left(Chunk.empty)): State[O, O2]) { (st, p1, p2) =>
      st match {
        case Running(excess) =>
          {
            p1.unsome.zipWithPar(p2.unsome) { case (l, r) =>
              handleSuccess(l, r, excess)
            }
          }.catchAllCause(e => UIO.succeedNow(Exit.failCause(e.map(Some(_)))))
        case LeftDone(excessL) =>
          {
            p2.unsome.map(handleSuccess(None, _, Left(excessL)))
          }.catchAllCause(e => UIO.succeedNow(Exit.failCause(e.map(Some(_)))))
        case RightDone(excessR) => {
          p1.unsome
            .map(handleSuccess(_, None, Right(excessR)))
            .catchAllCause(e => UIO.succeedNow(Exit.failCause(e.map(Some(_)))))
        }
        case End => {
          UIO.succeedNow(Exit.fail(None))
        }
      }
    }
  }

  /**
   * Zips this stream together with the index of elements.
   */
  final def zipWithIndex(implicit trace: ZTraceElement): ZStream[R, E, (O, Long)] =
    mapAccum(0L)((index, a) => (index + 1, (a, index)))

  /**
   * Zips the two streams so that when a value is emitted by either of the two streams,
   * it is combined with the latest value from the other stream to produce a result.
   *
   * Note: tracking the latest value is done on a per-chunk basis. That means that
   * emitted elements that are not the last value in chunks will never be used for zipping.
   */
  final def zipWithLatest[R1 <: R, E1 >: E, O2, O3](
    that: ZStream[R1, E1, O2]
  )(f: (O, O2) => O3)(implicit trace: ZTraceElement): ZStream[R1, E1, O3] = {
    def pullNonEmpty[R, E, O](pull: ZIO[R, Option[E], Chunk[O]]): ZIO[R, Option[E], Chunk[O]] =
      pull.flatMap(chunk => if (chunk.isEmpty) pullNonEmpty(pull) else UIO.succeedNow(chunk))

    ZStream {
      for {
        left  <- self.process.map(pullNonEmpty(_))
        right <- that.process.map(pullNonEmpty(_))
        pull <- (ZStream.fromZIOOption {
                  left.raceWith(right)(
                    (leftDone, rightFiber) => ZIO.done(leftDone).zipWith(rightFiber.join)((_, _, true)),
                    (rightDone, leftFiber) => ZIO.done(rightDone).zipWith(leftFiber.join)((r, l) => (l, r, false))
                  )
                }.flatMap { case (l, r, leftFirst) =>
                  ZStream.fromZIO(Ref.make(l(l.size - 1) -> r(r.size - 1))).flatMap { latest =>
                    ZStream.fromChunk(
                      if (leftFirst) r.map(f(l(l.size - 1), _))
                      else l.map(f(_, r(r.size - 1)))
                    ) ++
                      ZStream
                        .repeatZIOOption(left)
                        .mergeEither(ZStream.repeatZIOOption(right))
                        .mapZIO {
                          case Left(leftChunk) =>
                            latest.modify { case (_, rightLatest) =>
                              (leftChunk.map(f(_, rightLatest)), (leftChunk(leftChunk.size - 1), rightLatest))
                            }
                          case Right(rightChunk) =>
                            latest.modify { case (leftLatest, _) =>
                              (rightChunk.map(f(leftLatest, _)), (leftLatest, rightChunk(rightChunk.size - 1)))
                            }
                        }
                        .flatMap(ZStream.fromChunk(_))
                  }
                }).process

      } yield pull
    }
  }

  /**
   * Zips each element with the next element if present.
   */
  final def zipWithNext(implicit trace: ZTraceElement): ZStream[R, E, (O, Option[O])] =
    ZStream {
      for {
        chunks <- self.process
        ref    <- Ref.make[Option[O]](None).toManaged
        last    = ref.getAndSet(None).some.map((_, None)).map(Chunk.single)
        pull = for {
                 prev   <- ref.get
                 chunk  <- chunks
                 (s, c)  = chunk.mapAccum(prev)((prev, curr) => (Some(curr), prev.map((_, curr))))
                 _      <- ref.set(s)
                 result <- Pull.emit(c.collect { case Some((prev, curr)) => (prev, Some(curr)) })
               } yield result
      } yield pull.orElseOptional(last)
    }

  /**
   * Zips each element with the previous element. Initially accompanied by `None`.
   */
  final def zipWithPrevious(implicit trace: ZTraceElement): ZStream[R, E, (Option[O], O)] =
    mapAccum[Option[O], (Option[O], O)](None)((prev, next) => (Some(next), (prev, next)))

  /**
   * Zips each element with both the previous and next element.
   */
  final def zipWithPreviousAndNext(implicit trace: ZTraceElement): ZStream[R, E, (Option[O], O, Option[O])] =
    zipWithPrevious.zipWithNext.map { case ((prev, curr), next) => (prev, curr, next.map(_._2)) }
}

object ZStream extends ZStreamPlatformSpecificConstructors {

  /**
   * The default chunk size used by the various combinators and constructors of [[ZStream]].
   */
  final val DefaultChunkSize = 4096

  /**
   * Submerges the error case of an `Either` into the `ZStream`.
   */
  def absolve[R, E, O](xs: ZStream[R, E, Either[E, O]])(implicit trace: ZTraceElement): ZStream[R, E, O] =
    xs.mapZIO(ZIO.fromEither(_))

  /**
   * Accesses the environment of the stream.
   */
  def access[R]: AccessPartiallyApplied[R] =
    new AccessPartiallyApplied[R]

  /**
   * Accesses the environment of the stream in the context of an effect.
   */
  @deprecated("use accessZIO", "2.0.0")
  def accessM[R]: AccessZIOPartiallyApplied[R] =
    accessZIO

  /**
   * Accesses the environment of the stream in the context of an effect.
   */
  def accessZIO[R]: AccessZIOPartiallyApplied[R] =
    new AccessZIOPartiallyApplied[R]

  /**
   * Accesses the environment of the stream in the context of a stream.
   */
  def accessStream[R]: AccessStreamPartiallyApplied[R] =
    new AccessStreamPartiallyApplied[R]

  /**
   * Creates a stream from a single value that will get cleaned up after the
   * stream is consumed
   */
  def acquireReleaseWith[R, E, A](acquire: ZIO[R, E, A])(release: A => URIO[R, Any])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, A] =
    managed(ZManaged.acquireReleaseWith(acquire)(release))

  /**
   * Creates a stream from a single value that will get cleaned up after the
   * stream is consumed
   */
  def acquireReleaseExitWith[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => URIO[R, Any])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    managed(ZManaged.acquireReleaseExitWith(acquire)(release))

  /**
   * Creates a new [[ZStream]] from a managed effect that yields chunks.
   * The effect will be evaluated repeatedly until it fails with a `None`
   * (to signify stream end) or a `Some(E)` (to signify stream failure).
   *
   * The stream evaluation guarantees proper acquisition and release of the
   * [[ZManaged]].
   */
  def apply[R, E, O](
    process: ZManaged[R, Nothing, ZIO[R, Option[E], Chunk[O]]]
  ): ZStream[R, E, O] =
    new ZStream(process) {}

  /**
   * Creates a pure stream from a variable list of values
   */
  def apply[A](as: A*)(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] = fromIterable(as)

  /**
   * Locks the execution of the specified stream to the blocking executor. Any
   * streams that are composed after this one will automatically be shifted
   * back to the previous executor.
   */
  def blocking[R, E, A](stream: ZStream[R, E, A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    ZStream.fromZIO(ZIO.blockingExecutor).flatMap(stream.onExecutor)

  /**
   * Creates a stream from a single value that will get cleaned up after the
   * stream is consumed
   */
  @deprecated("use acquireReleaseWith", "2.0.0")
  def bracket[R, E, A](acquire: ZIO[R, E, A])(release: A => URIO[R, Any])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, A] =
    acquireReleaseWith(acquire)(release)

  /**
   * Creates a stream from a single value that will get cleaned up after the
   * stream is consumed
   */
  @deprecated("use acquireReleaseExitWith", "2.0.0")
  def bracketExit[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => URIO[R, Any])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    acquireReleaseExitWith(acquire)(release)

  /**
   * Composes the specified streams to create a cartesian product of elements
   * with a specified function. Subsequent streams would be run multiple times,
   * for every combination of elements in the prior streams.
   *
   * See also [[ZStream#zipN[R,E,A,B,C]*]] for the more common point-wise variant.
   */
  @deprecated("use cross", "2.0.0")
  def crossN[R, E, A, B, C](zStream1: ZStream[R, E, A], zStream2: ZStream[R, E, B])(
    f: (A, B) => C
  )(implicit trace: ZTraceElement): ZStream[R, E, C] =
    zStream1.crossWith(zStream2)(f)

  /**
   * Composes the specified streams to create a cartesian product of elements
   * with a specified function. Subsequent stream would be run multiple times,
   * for every combination of elements in the prior streams.
   *
   * See also [[ZStream#zipN[R,E,A,B,C,D]*]] for the more common point-wise variant.
   */
  @deprecated("use cross", "2.0.0")
  def crossN[R, E, A, B, C, D](
    zStream1: ZStream[R, E, A],
    zStream2: ZStream[R, E, B],
    zStream3: ZStream[R, E, C]
  )(
    f: (A, B, C) => D
  )(implicit trace: ZTraceElement): ZStream[R, E, D] =
    for {
      a <- zStream1
      b <- zStream2
      c <- zStream3
    } yield f(a, b, c)

  /**
   * Composes the specified streams to create a cartesian product of elements
   * with a specified function. Subsequent stream would be run multiple times,
   * for every combination of elements in the prior streams.
   *
   * See also [[ZStream#zipN[R,E,A,B,C,D,F]*]] for the more common point-wise variant.
   */
  @deprecated("use cross", "2.0.0")
  def crossN[R, E, A, B, C, D, F](
    zStream1: ZStream[R, E, A],
    zStream2: ZStream[R, E, B],
    zStream3: ZStream[R, E, C],
    zStream4: ZStream[R, E, D]
  )(
    f: (A, B, C, D) => F
  )(implicit trace: ZTraceElement): ZStream[R, E, F] =
    for {
      a <- zStream1
      b <- zStream2
      c <- zStream3
      d <- zStream4
    } yield f(a, b, c, d)

  /**
   * Concatenates all of the streams in the chunk to one stream.
   */
  def concatAll[R, E, O](streams: Chunk[ZStream[R, E, O]])(implicit trace: ZTraceElement): ZStream[R, E, O] =
    ZStream {
      val chunkSize = streams.size

      for {
        currIndex    <- Ref.make(0).toManaged
        currStream   <- Ref.make[ZIO[R, Option[E], Chunk[O]]](Pull.end).toManaged
        switchStream <- ZManaged.switchable[R, Nothing, ZIO[R, Option[E], Chunk[O]]]
        pull = {
          def go: ZIO[R, Option[E], Chunk[O]] =
            currStream.get.flatten.catchAllCause {
              Cause.flipCauseOption(_) match {
                case Some(e) => Pull.failCause(e)
                case None =>
                  currIndex.getAndUpdate(_ + 1).flatMap { i =>
                    if (i >= chunkSize) Pull.end
                    else switchStream(streams(i).process).flatMap(currStream.set) *> go
                  }
              }
            }

          go
        }
      } yield pull
    }

  /**
   * The stream that dies with the `ex`.
   */
  def die(ex: => Throwable)(implicit trace: ZTraceElement): ZStream[Any, Nothing, Nothing] =
    fromZIO(ZIO.die(ex))

  /**
   * The stream that dies with an exception described by `msg`.
   */
  def dieMessage(msg: => String)(implicit trace: ZTraceElement): ZStream[Any, Nothing, Nothing] =
    fromZIO(ZIO.dieMessage(msg))

  /**
   * The stream that ends with the [[zio.Exit]] value `exit`.
   */
  def done[E, A](exit: Exit[E, A])(implicit trace: ZTraceElement): ZStream[Any, E, A] =
    fromZIO(ZIO.done(exit))

  /**
   * The empty stream
   */
  def empty(implicit trace: ZTraceElement): ZStream[Any, Nothing, Nothing] =
    ZStream(ZManaged.succeedNow(Pull.end))

  /**
   * Accesses the whole environment of the stream.
   */
  def environment[R](implicit trace: ZTraceElement): ZStream[R, Nothing, R] =
    fromZIO(ZIO.environment[R])

  /**
   * Creates a stream that executes the specified effect but emits no elements.
   */
  def execute[R, E](zio: ZIO[R, E, Any])(implicit trace: ZTraceElement): ZStream[R, E, Nothing] =
    ZStream.fromZIO(zio).drain

  /**
   * The stream that always fails with the `error`
   */
  def fail[E](error: => E)(implicit trace: ZTraceElement): ZStream[Any, E, Nothing] =
    fromZIO(ZIO.fail(error))

  /**
   * The stream that always fails with `cause`.
   */
  def failCause[E](cause: => Cause[E])(implicit trace: ZTraceElement): ZStream[Any, E, Nothing] =
    fromZIO(ZIO.failCause(cause))

  /**
   * Creates a one-element stream that never fails and executes the finalizer when it ends.
   */
  def finalizer[R](finalizer: URIO[R, Any])(implicit trace: ZTraceElement): ZStream[R, Nothing, Any] =
    acquireReleaseWith[R, Nothing, Unit](UIO.unit)(_ => finalizer)

  /**
   * Constructs a  `ZStream` value of the appropriate type for the specified
   * input.
   */
  def from[Input](
    input: => Input
  )(implicit constructor: ZStreamConstructor[Input], trace: ZTraceElement): constructor.Out =
    constructor.make(input)

  /**
   * Creates a stream from a [[zio.Chunk]] of values
   *
   * @param c a chunk of values
   * @return a finite stream of values
   */
  def fromChunk[O](c: => Chunk[O])(implicit trace: ZTraceElement): ZStream[Any, Nothing, O] =
    ZStream {
      for {
        doneRef <- Ref.make(false).toManaged
        pull = doneRef.modify { done =>
                 if (done || c.isEmpty) Pull.end -> true
                 else ZIO.succeedNow(c)          -> true
               }.flatten
      } yield pull
    }

  /**
   * Creates a stream from a subscription to a hub.
   */
  def fromChunkHub[R, E, O](hub: ZHub[Nothing, R, Any, E, Nothing, Chunk[O]])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, O] =
    managed(hub.subscribe).flatMap(queue => fromChunkQueue(queue))

  /**
   * Creates a stream from a subscription to a hub in the context of a managed
   * effect. The managed effect describes subscribing to receive messages from
   * the hub while the stream describes taking messages from the hub.
   */
  def fromChunkHubManaged[R, E, O](
    hub: ZHub[Nothing, R, Any, E, Nothing, Chunk[O]]
  )(implicit trace: ZTraceElement): ZManaged[Any, Nothing, ZStream[R, E, O]] =
    hub.subscribe.map(queue => fromChunkQueue(queue))

  /**
   * Creates a stream from a subscription to a hub.
   *
   * The hub will be shut down once the stream is closed.
   */
  def fromChunkHubWithShutdown[R, E, O](hub: ZHub[Nothing, R, Any, E, Nothing, Chunk[O]])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, O] =
    fromChunkHub(hub).ensuringFirst(hub.shutdown)

  /**
   * Creates a stream from a subscription to a hub in the context of a managed
   * effect. The managed effect describes subscribing to receive messages from
   * the hub while the stream describes taking messages from the hub.
   *
   * The hub will be shut down once the stream is closed.
   */
  def fromChunkHubManagedWithShutdown[R, E, O](
    hub: ZHub[Nothing, R, Any, E, Nothing, Chunk[O]]
  )(implicit trace: ZTraceElement): ZManaged[Any, Nothing, ZStream[R, E, O]] =
    fromChunkHubManaged(hub).map(_.ensuringFirst(hub.shutdown))

  /**
   * Creates a stream from a queue of values
   */
  def fromChunkQueue[R, E, O](
    queue: ZQueue[Nothing, R, Any, E, Nothing, Chunk[O]]
  )(implicit trace: ZTraceElement): ZStream[R, E, O] =
    repeatZIOChunkOption {
      queue.take
        .catchAllCause(c =>
          queue.isShutdown.flatMap { down =>
            if (down && c.isInterrupted) Pull.end
            else Pull.failCause(c)
          }
        )
    }

  /**
   * Creates a stream from a queue of values. The queue will be shutdown once the stream is closed.
   */
  def fromChunkQueueWithShutdown[R, E, O](queue: ZQueue[Nothing, R, Any, E, Nothing, Chunk[O]])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, O] =
    fromChunkQueue(queue).ensuringFirst(queue.shutdown)

  /**
   * Creates a stream from an arbitrary number of chunks.
   */
  def fromChunks[O](cs: Chunk[O]*)(implicit trace: ZTraceElement): ZStream[Any, Nothing, O] =
    fromIterable(cs).flatMap(fromChunk(_))

  /**
   * Creates a stream from an effect producing a value of type `A`
   */
  @deprecated("use fromZIO", "2.0.0")
  def fromEffect[R, E, A](fa: ZIO[R, E, A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    fromZIO(fa)

  /**
   * Creates a stream from an effect producing a value of type `A` or an empty Stream
   */
  @deprecated("use fromZIOOption", "2.0.0")
  def fromEffectOption[R, E, A](fa: ZIO[R, Option[E], A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    fromZIOOption(fa)

  /**
   * Creates a stream from an effect producing a value of type `A`
   */
  def fromZIO[R, E, A](fa: ZIO[R, E, A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    fromZIOOption(fa.mapError(Some(_)))

  /**
   * Creates a stream from an effect producing a value of type `A` or an empty Stream
   */
  def fromZIOOption[R, E, A](fa: ZIO[R, Option[E], A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    ZStream {
      for {
        doneRef <- Ref.make(false).toManaged
        pull = doneRef.modify {
                 if (_) Pull.end              -> true
                 else fa.map(Chunk.single(_)) -> true
               }.flatten
      } yield pull
    }

  /**
   * Creates a stream from a subscription to a hub.
   */
  def fromHub[R, E, A](
    hub: ZHub[Nothing, R, Any, E, Nothing, A],
    maxChunkSize: Int = DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    managed(hub.subscribe).flatMap(queue => fromQueue(queue, maxChunkSize))

  /**
   * Creates a stream from a subscription to a hub in the context of a managed
   * effect. The managed effect describes subscribing to receive messages from
   * the hub while the stream describes taking messages from the hub.
   */
  def fromHubManaged[R, E, A](
    hub: ZHub[Nothing, R, Any, E, Nothing, A],
    maxChunkSize: Int = DefaultChunkSize
  )(implicit trace: ZTraceElement): ZManaged[Any, Nothing, ZStream[R, E, A]] =
    hub.subscribe.map(queue => fromQueueWithShutdown(queue, maxChunkSize))

  /**
   * Creates a stream from a subscription to a hub.
   *
   * The hub will be shut down once the stream is closed.
   */
  def fromHubWithShutdown[R, E, A](
    hub: ZHub[Nothing, R, Any, E, Nothing, A],
    maxChunkSize: Int = DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    fromHub(hub, maxChunkSize).ensuringFirst(hub.shutdown)

  /**
   * Creates a stream from a subscription to a hub in the context of a managed
   * effect. The managed effect describes subscribing to receive messages from
   * the hub while the stream describes taking messages from the hub.
   *
   * The hub will be shut down once the stream is closed.
   */
  def fromHubManagedWithShutdown[R, E, A](
    hub: ZHub[Nothing, R, Any, E, Nothing, A],
    maxChunkSize: Int = DefaultChunkSize
  )(implicit trace: ZTraceElement): ZManaged[Any, Nothing, ZStream[R, E, A]] =
    fromHubManaged(hub, maxChunkSize).map(_.ensuringFirst(hub.shutdown))

  /**
   * Creates a stream from an iterable collection of values
   */
  def fromIterable[O](as: => Iterable[O])(implicit trace: ZTraceElement): ZStream[Any, Nothing, O] =
    fromChunk(Chunk.fromIterable(as))

  /**
   * Creates a stream from an effect producing a value of type `Iterable[A]`
   */
  @deprecated("use fromIterableZIO", "2.0.0")
  def fromIterableM[R, E, O](iterable: ZIO[R, E, Iterable[O]])(implicit trace: ZTraceElement): ZStream[R, E, O] =
    fromIterableZIO(iterable)

  /**
   * Creates a stream from an effect producing a value of type `Iterable[A]`
   */
  def fromIterableZIO[R, E, O](iterable: ZIO[R, E, Iterable[O]])(implicit trace: ZTraceElement): ZStream[R, E, O] =
    fromZIO(iterable).mapConcat(identity)

  /**
   * Creates a stream from an iterator that may throw exceptions.
   */
  def fromIterator[A](iterator: => Iterator[A], maxChunkSize: Int = 1)(implicit
    trace: ZTraceElement
  ): ZStream[Any, Throwable, A] =
    ZStream {
      ZManaged
        .attempt(iterator)
        .fold(
          Pull.fail,
          iterator =>
            ZIO.attempt {
              if (maxChunkSize <= 1) {
                if (iterator.isEmpty) Pull.end else Pull.emit(iterator.next())
              } else {
                val builder = ChunkBuilder.make[A](maxChunkSize)
                var i       = 0
                while (i < maxChunkSize && iterator.hasNext) {
                  val a = iterator.next()
                  builder += a
                  i += 1
                }
                val chunk = builder.result()
                if (chunk.isEmpty) Pull.end else Pull.emit(chunk)
              }
            }.asSomeError.flatten
        )
    }

  /**
   * Creates a stream from an iterator that may potentially throw exceptions
   */
  @deprecated("use fromIteratorZIO", "2.0.0")
  def fromIteratorEffect[R, A](
    iterator: ZIO[R, Throwable, Iterator[A]]
  )(implicit trace: ZTraceElement): ZStream[R, Throwable, A] =
    fromIteratorZIO(iterator)

  /**
   * Creates a stream from a managed iterator
   */
  def fromIteratorManaged[R, A](iterator: ZManaged[R, Throwable, Iterator[A]])(implicit
    trace: ZTraceElement
  ): ZStream[R, Throwable, A] =
    managed(iterator).flatMap(fromIterator(_))

  /**
   * Creates a stream from an iterator that does not throw exceptions.
   */
  def fromIteratorSucceed[A](iterator: => Iterator[A], maxChunkSize: Int = 1)(implicit
    trace: ZTraceElement
  ): ZStream[Any, Nothing, A] =
    ZStream {
      Managed.succeed(iterator).map { iterator =>
        ZIO.succeed {
          if (maxChunkSize <= 1) {
            if (iterator.isEmpty) Pull.end else Pull.emit(iterator.next())
          } else {
            val builder = ChunkBuilder.make[A](maxChunkSize)
            var i       = 0
            while (i < maxChunkSize && iterator.hasNext) {
              val a = iterator.next()
              builder += a
              i += 1
            }
            val chunk = builder.result()
            if (chunk.isEmpty) Pull.end else Pull.emit(chunk)
          }
        }.flatten
      }
    }

  /**
   * Creates a stream from an iterator that does not throw exceptions.
   */
  @deprecated("use fromIteratorSucceed", "2.0.0")
  def fromIteratorTotal[A](iterator: => Iterator[A], maxChunkSize: Int = 1)(implicit
    trace: ZTraceElement
  ): ZStream[Any, Nothing, A] =
    fromIteratorSucceed(iterator)

  /**
   * Creates a stream from an iterator that may potentially throw exceptions
   */
  def fromIteratorZIO[R, A](
    iterator: ZIO[R, Throwable, Iterator[A]]
  )(implicit trace: ZTraceElement): ZStream[R, Throwable, A] =
    fromZIO(iterator).flatMap(fromIterator(_))

  /**
   * Creates a stream from a Java iterator that may throw exceptions
   */
  def fromJavaIterator[A](iterator: => ju.Iterator[A])(implicit trace: ZTraceElement): ZStream[Any, Throwable, A] =
    fromIterator {
      val it = iterator // Scala 2.13 scala.collection.Iterator has `iterator` in local scope
      new Iterator[A] {
        def next(): A        = it.next
        def hasNext: Boolean = it.hasNext
      }
    }

  /**
   * Creates a stream from a Java iterator that may potentially throw exceptions
   */
  @deprecated("use fromJavaIteratorZIO", "2.0.0")
  def fromJavaIteratorEffect[R, A](
    iterator: ZIO[R, Throwable, ju.Iterator[A]]
  )(implicit trace: ZTraceElement): ZStream[R, Throwable, A] =
    fromJavaIteratorZIO(iterator)

  /**
   * Creates a stream from a managed iterator
   */
  def fromJavaIteratorManaged[R, A](iterator: ZManaged[R, Throwable, ju.Iterator[A]])(implicit
    trace: ZTraceElement
  ): ZStream[R, Throwable, A] =
    managed(iterator).flatMap(fromJavaIterator(_))

  /**
   * Creates a stream from a Java iterator
   */
  def fromJavaIteratorSucceed[A](iterator: => ju.Iterator[A])(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
    fromIteratorSucceed {
      val it = iterator // Scala 2.13 scala.collection.Iterator has `iterator` in local scope
      new Iterator[A] {
        def next(): A        = it.next
        def hasNext: Boolean = it.hasNext
      }
    }

  /**
   * Creates a stream from a Java iterator
   */
  @deprecated("use fromJavaIteratorSucceed", "2.0.0")
  def fromJavaIteratorTotal[A](iterator: => ju.Iterator[A])(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
    fromJavaIteratorSucceed(iterator)

  /**
   * Creates a stream from a Java iterator that may potentially throw exceptions
   */
  def fromJavaIteratorZIO[R, A](
    iterator: ZIO[R, Throwable, ju.Iterator[A]]
  )(implicit trace: ZTraceElement): ZStream[R, Throwable, A] =
    fromZIO(iterator).flatMap(fromJavaIterator(_))

  /**
   * Creates a stream from a queue of values
   *
   * @param maxChunkSize Maximum number of queued elements to put in one chunk in the stream
   */
  def fromQueue[R, E, O](
    queue: ZQueue[Nothing, R, Any, E, Nothing, O],
    maxChunkSize: Int = DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[R, E, O] =
    repeatZIOChunkOption {
      queue
        .takeBetween(1, maxChunkSize)
        .map(Chunk.fromIterable)
        .catchAllCause(c =>
          queue.isShutdown.flatMap { down =>
            if (down && c.isInterrupted) Pull.end
            else Pull.failCause(c)
          }
        )
    }

  /**
   * Creates a stream from a queue of values. The queue will be shutdown once the stream is closed.
   *
   * @param maxChunkSize Maximum number of queued elements to put in one chunk in the stream
   */
  def fromQueueWithShutdown[R, E, O](
    queue: ZQueue[Nothing, R, Any, E, Nothing, O],
    maxChunkSize: Int = DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[R, E, O] =
    fromQueue(queue, maxChunkSize).ensuringFirst(queue.shutdown)

  /**
   * Creates a stream from a [[zio.Schedule]] that does not require any further
   * input. The stream will emit an element for each value output from the
   * schedule, continuing for as long as the schedule continues.
   */
  def fromSchedule[R, A](schedule: Schedule[R, Any, A])(implicit
    trace: ZTraceElement
  ): ZStream[R with Has[Clock], Nothing, A] =
    unwrap(schedule.driver.map(driver => repeatZIOOption(driver.next(()))))

  /**
   * Creates a stream from a [[zio.stm.TQueue]] of values.
   */
  def fromTQueue[A](queue: TQueue[A])(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
    repeatZIOChunk(queue.take.map(Chunk.single(_)).commit)

  /**
   * The stream that always halts with `cause`.
   */
  @deprecated("use failCause", "2.0.0")
  def halt[E](cause: => Cause[E])(implicit trace: ZTraceElement): ZStream[Any, E, Nothing] =
    failCause(cause)

  /**
   * The infinite stream of iterative function application: a, f(a), f(f(a)), f(f(f(a))), ...
   */
  def iterate[A](a: A)(f: A => A)(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
    ZStream(Ref.make(a).toManaged.map(_.getAndUpdate(f).map(Chunk.single(_))))

  /**
   * Creates a single-valued stream from a managed resource
   */
  def managed[R, E, A](managed: ZManaged[R, E, A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    ZStream {
      for {
        doneRef   <- Ref.make(false).toManaged
        finalizer <- ZManaged.ReleaseMap.makeManaged(ExecutionStrategy.Sequential)
        pull = ZIO.uninterruptibleMask { restore =>
                 doneRef.get.flatMap { done =>
                   if (done) Pull.end
                   else
                     (for {
                       a <-
                         restore(managed.zio.map(_._2).provideSome[R]((_, finalizer))).onError(_ => doneRef.set(true))
                       _ <- doneRef.set(true)
                     } yield Chunk(a)).mapError(Some(_))
                 }
               }
      } yield pull
    }

  /**
   * Merges a variable list of streams in a non-deterministic fashion.
   * Up to `n` streams may be consumed in parallel and up to
   * `outputBuffer` chunks may be buffered by this operator.
   */
  def mergeAll[R, E, O](n: Int, outputBuffer: Int = 16)(
    streams: ZStream[R, E, O]*
  )(implicit trace: ZTraceElement): ZStream[R, E, O] =
    fromIterable(streams).flattenPar(n, outputBuffer)

  /**
   * Like [[mergeAll]], but runs all streams concurrently.
   */
  def mergeAllUnbounded[R, E, O](outputBuffer: Int = 16)(
    streams: ZStream[R, E, O]*
  )(implicit trace: ZTraceElement): ZStream[R, E, O] = mergeAll(Int.MaxValue, outputBuffer)(streams: _*)

  /**
   * The stream that never produces any value or fails with any error.
   */
  def never(implicit trace: ZTraceElement): ZStream[Any, Nothing, Nothing] =
    ZStream(ZManaged.succeedNow(UIO.never))

  /**
   * Like [[unfold]], but allows the emission of values to end one step further than
   * the unfolding of the state. This is useful for embedding paginated APIs,
   * hence the name.
   */
  def paginate[R, E, A, S](s: S)(f: S => (A, Option[S]))(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
    paginateZIO(s)(s => ZIO.succeedNow(f(s)))

  /**
   * Like [[unfoldChunk]], but allows the emission of values to end one step further than
   * the unfolding of the state. This is useful for embedding paginated APIs,
   * hence the name.
   */
  def paginateChunk[A, S](s: S)(f: S => (Chunk[A], Option[S]))(implicit
    trace: ZTraceElement
  ): ZStream[Any, Nothing, A] =
    paginateChunkZIO(s)(s => ZIO.succeedNow(f(s)))

  /**
   * Like [[unfoldChunkM]], but allows the emission of values to end one step further than
   * the unfolding of the state. This is useful for embedding paginated APIs,
   * hence the name.
   */
  @deprecated("use paginateChunkZIO", "2.0.0")
  def paginateChunkM[R, E, A, S](s: S)(f: S => ZIO[R, E, (Chunk[A], Option[S])])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, A] =
    paginateChunkZIO(s)(f)

  /**
   * Like [[unfoldChunkZIO]], but allows the emission of values to end one step further than
   * the unfolding of the state. This is useful for embedding paginated APIs,
   * hence the name.
   */
  def paginateChunkZIO[R, E, A, S](
    s: S
  )(f: S => ZIO[R, E, (Chunk[A], Option[S])])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    ZStream {
      for {
        ref <- Ref.make(Option(s)).toManaged
      } yield ref.get.flatMap {
        case Some(s) => f(s).foldZIO(Pull.fail, { case (as, s) => ref.set(s).as(as) })
        case None    => Pull.end
      }
    }

  /**
   * Like [[unfoldM]], but allows the emission of values to end one step further than
   * the unfolding of the state. This is useful for embedding paginated APIs,
   * hence the name.
   */
  @deprecated("use paginateZIO", "2.0.0")
  def paginateM[R, E, A, S](s: S)(f: S => ZIO[R, E, (A, Option[S])])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    paginateZIO(s)(f)

  /**
   * Like [[unfoldZIO]], but allows the emission of values to end one step further than
   * the unfolding of the state. This is useful for embedding paginated APIs,
   * hence the name.
   */
  def paginateZIO[R, E, A, S](s: S)(f: S => ZIO[R, E, (A, Option[S])])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, A] =
    paginateChunkZIO(s)(f(_).map { case (a, s) => Chunk.single(a) -> s })

  /**
   * Constructs a stream from a range of integers (lower bound included, upper bound not included)
   */
  def range(min: Int, max: Int, chunkSize: Int = DefaultChunkSize)(implicit
    trace: ZTraceElement
  ): ZStream[Any, Nothing, Int] = {
    val pull = (ref: Ref[Int]) =>
      for {
        start <- ref.getAndUpdate(_ + chunkSize)
        _     <- ZIO.when(start >= max)(ZIO.fail(None))
      } yield Chunk.fromIterable(Range(start, (start + chunkSize).min(max)))
    ZStream(Ref.makeManaged(min).map(pull))
  }

  /**
   * Repeats the provided value infinitely.
   */
  def repeat[A](a: => A)(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
    repeatZIO(UIO.succeed(a))

  /**
   * Creates a stream from an effect producing a value of type `A` which repeats forever.
   */
  @deprecated("use repeatZIO", "2.0.0")
  def repeatEffect[R, E, A](fa: ZIO[R, E, A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    repeatZIO(fa)

  /**
   * Creates a stream from an effect producing chunks of `A` values which repeats forever.
   */
  @deprecated("use repeatZIOChunk", "2.0.0")
  def repeatEffectChunk[R, E, A](fa: ZIO[R, E, Chunk[A]])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    repeatZIOChunk(fa)

  /**
   * Creates a stream from an effect producing chunks of `A` values until it fails with None.
   */
  @deprecated("use repeatZIOChunkOption", "2.0.0")
  def repeatEffectChunkOption[R, E, A](fa: ZIO[R, Option[E], Chunk[A]])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, A] =
    repeatZIOChunkOption(fa)

  /**
   * Creates a stream from an effect producing values of type `A` until it fails with None.
   */
  @deprecated("use repeatZIOOption", "2.0.0")
  def repeatEffectOption[R, E, A](fa: ZIO[R, Option[E], A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    repeatZIOOption(fa)

  /**
   * Creates a stream from an effect producing a value of type `A`, which is repeated using the
   * specified schedule.
   */
  @deprecated("use repeatZIOWithSchedule", "2.0.0")
  def repeatEffectWith[R, E, A](effect: ZIO[R, E, A], schedule: Schedule[R, A, Any])(implicit
    trace: ZTraceElement
  ): ZStream[R with Has[Clock], E, A] =
    repeatZIOWithSchedule(effect, schedule)

  /**
   * Repeats the value using the provided schedule.
   */
  @deprecated("use repeatWithSchedule", "2.0.0")
  def repeatWith[R, A](a: => A, schedule: Schedule[R, A, _])(implicit
    trace: ZTraceElement
  ): ZStream[R with Has[Clock], Nothing, A] =
    repeatWithSchedule(a, schedule)

  /**
   * Repeats the value using the provided schedule.
   */
  def repeatWithSchedule[R, A](a: => A, schedule: Schedule[R, A, _])(implicit
    trace: ZTraceElement
  ): ZStream[R with Has[Clock], Nothing, A] =
    repeatZIOWithSchedule(UIO.succeed(a), schedule)

  /**
   * Creates a stream from an effect producing a value of type `A` which repeats forever.
   */
  def repeatZIO[R, E, A](fa: ZIO[R, E, A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    repeatZIOOption(fa.mapError(Some(_)))

  /**
   * Creates a stream from an effect producing chunks of `A` values which repeats forever.
   */
  def repeatZIOChunk[R, E, A](fa: ZIO[R, E, Chunk[A]])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    repeatZIOChunkOption(fa.mapError(Some(_)))

  /**
   * Creates a stream from an effect producing chunks of `A` values until it fails with None.
   */
  def repeatZIOChunkOption[R, E, A](fa: ZIO[R, Option[E], Chunk[A]])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    ZStream {
      for {
        done <- Ref.make(false).toManaged
        pull = done.get.flatMap {
                 if (_) Pull.end
                 else
                   fa.tapError {
                     case None    => done.set(true)
                     case Some(_) => ZIO.unit
                   }
               }
      } yield pull
    }

  /**
   * Creates a stream from an effect producing values of type `A` until it fails with None.
   */
  def repeatZIOOption[R, E, A](fa: ZIO[R, Option[E], A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    repeatZIOChunkOption(fa.map(Chunk.single(_)))

  /**
   * Creates a stream from an effect producing a value of type `A`, which is repeated using the
   * specified schedule.
   */
  def repeatZIOWithSchedule[R, E, A](
    effect: ZIO[R, E, A],
    schedule: Schedule[R, A, Any]
  )(implicit trace: ZTraceElement): ZStream[R with Has[Clock], E, A] =
    ZStream.fromZIO(effect zip schedule.driver).flatMap { case (a, driver) =>
      ZStream.succeed(a) ++
        ZStream.unfoldZIO(a)(driver.next(_).foldZIO(ZIO.succeed(_), _ => effect.map(nextA => Some(nextA -> nextA))))
    }

  /**
   * Accesses the specified service in the environment of the effect.
   */
  def service[A: Tag](implicit trace: ZTraceElement): ZStream[Has[A], Nothing, A] =
    ZStream.access(_.get[A])

  /**
   * Accesses the service corresponding to the specified key in the
   * environment.
   */
  def serviceAt[Service]: ZStream.ServiceAtPartiallyApplied[Service] =
    new ZStream.ServiceAtPartiallyApplied[Service]

  /**
   * Accesses the specified services in the environment of the effect.
   */
  @deprecated("use service", "2.0.0")
  def services[A: Tag, B: Tag](implicit trace: ZTraceElement): ZStream[Has[A] with Has[B], Nothing, (A, B)] =
    ZStream.access(r => (r.get[A], r.get[B]))

  /**
   * Accesses the specified services in the environment of the stream.
   */
  @deprecated("use service", "2.0.0")
  def services[A: Tag, B: Tag, C: Tag](implicit
    trace: ZTraceElement
  ): ZStream[Has[A] with Has[B] with Has[C], Nothing, (A, B, C)] =
    ZStream.access(r => (r.get[A], r.get[B], r.get[C]))

  /**
   * Accesses the specified services in the environment of the stream.
   */
  @deprecated("use service", "2.0.0")
  def services[A: Tag, B: Tag, C: Tag, D: Tag](implicit
    trace: ZTraceElement
  ): ZStream[Has[A] with Has[B] with Has[C] with Has[D], Nothing, (A, B, C, D)] =
    ZStream.access(r => (r.get[A], r.get[B], r.get[C], r.get[D]))

  /**
   * Accesses the specified service in the environment of the stream in the
   * context of an effect.
   */
  def serviceWith[Service]: ServiceWithPartiallyApplied[Service] =
    new ServiceWithPartiallyApplied[Service]

  /**
   * Accesses the specified service in the environment of the stream in the
   * context of a stream.
   */
  def serviceWithStream[Service]: ServiceWithStreamPartiallyApplied[Service] =
    new ServiceWithStreamPartiallyApplied[Service]

  /**
   * Creates a single-valued pure stream
   */
  def succeed[A](a: => A)(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
    fromChunk(Chunk.single(a))

  /**
   * A stream that emits Unit values spaced by the specified duration.
   */
  def tick(interval: Duration)(implicit trace: ZTraceElement): ZStream[Has[Clock], Nothing, Unit] =
    repeatWithSchedule((), Schedule.spaced(interval))

  /**
   * A stream that contains a single `Unit` value.
   */
  val unit: ZStream[Any, Nothing, Unit] =
    succeed(())(ZTraceElement.empty)

  /**
   * Creates a stream by peeling off the "layers" of a value of type `S`
   */
  def unfold[S, A](s: S)(f: S => Option[(A, S)])(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
    unfoldZIO(s)(s => ZIO.succeedNow(f(s)))

  /**
   * Creates a stream by peeling off the "layers" of a value of type `S`.
   */
  def unfoldChunk[S, A](s: S)(f: S => Option[(Chunk[A], S)])(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
    unfoldChunkZIO(s)(s => ZIO.succeedNow(f(s)))

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  @deprecated("use unfoldChunkZIO", "2.0.0")
  def unfoldChunkM[R, E, A, S](s: S)(f: S => ZIO[R, E, Option[(Chunk[A], S)]])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, A] =
    unfoldChunkZIO(s)(f)

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  def unfoldChunkZIO[R, E, A, S](
    s: S
  )(f: S => ZIO[R, E, Option[(Chunk[A], S)]])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    ZStream {
      for {
        done <- Ref.make(false).toManaged
        ref  <- Ref.make(s).toManaged
        pull = done.get.flatMap {
                 if (_) Pull.end
                 else {
                   ref.get
                     .flatMap(f)
                     .foldZIO(
                       Pull.fail,
                       opt =>
                         opt match {
                           case Some((a, s)) => ref.set(s).as(a)
                           case None         => done.set(true) *> Pull.end
                         }
                     )
                 }
               }
      } yield pull
    }

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  @deprecated("use unfoldZIO", "2.0.0")
  def unfoldM[R, E, A, S](s: S)(f: S => ZIO[R, E, Option[(A, S)]])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    unfoldZIO(s)(f)

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  def unfoldZIO[R, E, A, S](s: S)(f: S => ZIO[R, E, Option[(A, S)]])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    unfoldChunkZIO(s)(f(_).map(_.map { case (a, s) =>
      Chunk.single(a) -> s
    }))

  /**
   * Creates a stream produced from an effect
   */
  def unwrap[R, E, A](fa: ZIO[R, E, ZStream[R, E, A]])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    fromZIO(fa).flatten

  /**
   * Creates a stream produced from a [[ZManaged]]
   */
  def unwrapManaged[R, E, A](fa: ZManaged[R, E, ZStream[R, E, A]])(implicit trace: ZTraceElement): ZStream[R, E, A] =
    managed(fa).flatten

  /**
   * Returns the specified stream if the given condition is satisfied, otherwise returns an empty stream.
   */
  def when[R, E, O](b: => Boolean)(zStream: => ZStream[R, E, O])(implicit trace: ZTraceElement): ZStream[R, E, O] =
    whenZIO(ZIO.succeed(b))(zStream)

  /**
   * Returns the resulting stream when the given `PartialFunction` is defined for the given value, otherwise returns an empty stream.
   */
  def whenCase[R, E, A, O](a: => A)(pf: PartialFunction[A, ZStream[R, E, O]])(implicit
    trace: ZTraceElement
  ): ZStream[R, E, O] =
    whenCaseZIO(ZIO.succeed(a))(pf)

  /**
   * Returns the resulting stream when the given `PartialFunction` is defined for the given effectful value, otherwise returns an empty stream.
   */
  @deprecated("use whenCaseZIO", "2.0.0")
  def whenCaseM[R, E, A](a: ZIO[R, E, A]): WhenCaseZIO[R, E, A] =
    whenCaseZIO(a)

  /**
   * Returns the resulting stream when the given `PartialFunction` is defined for the given effectful value, otherwise returns an empty stream.
   */
  def whenCaseZIO[R, E, A](a: ZIO[R, E, A]): WhenCaseZIO[R, E, A] =
    new WhenCaseZIO(a)

  /**
   * Returns the specified stream if the given effectful condition is satisfied, otherwise returns an empty stream.
   */
  @deprecated("use whenZIO", "2.0.0")
  def whenM[R, E](b: ZIO[R, E, Boolean]): WhenZIO[R, E] =
    whenZIO(b)

  /**
   * Returns the specified stream if the given effectful condition is satisfied, otherwise returns an empty stream.
   */
  def whenZIO[R, E](b: ZIO[R, E, Boolean]) =
    new WhenZIO(b)

  /**
   * Zips the specified streams together with the specified function.
   */
  @deprecated("use zip", "2.0.0")
  def zipN[R, E, A, B, C](zStream1: ZStream[R, E, A], zStream2: ZStream[R, E, B])(
    f: (A, B) => C
  )(implicit trace: ZTraceElement): ZStream[R, E, C] =
    zStream1.zipWith(zStream2)(f)

  /**
   * Zips with specified streams together with the specified function.
   */
  @deprecated("use zip", "2.0.0")
  def zipN[R, E, A, B, C, D](zStream1: ZStream[R, E, A], zStream2: ZStream[R, E, B], zStream3: ZStream[R, E, C])(
    f: (A, B, C) => D
  )(implicit trace: ZTraceElement): ZStream[R, E, D] =
    (zStream1 <&> zStream2 <&> zStream3).map(f.tupled)

  /**
   * Returns an effect that executes the specified effects in parallel,
   * combining their results with the specified `f` function. If any effect
   * fails, then the other effects will be interrupted.
   */
  @deprecated("use zip", "2.0.0")
  def zipN[R, E, A, B, C, D, F](
    zStream1: ZStream[R, E, A],
    zStream2: ZStream[R, E, B],
    zStream3: ZStream[R, E, C],
    zStream4: ZStream[R, E, D]
  )(f: (A, B, C, D) => F)(implicit trace: ZTraceElement): ZStream[R, E, F] =
    (zStream1 <&> zStream2 <&> zStream3 <&> zStream4).map(f.tupled)

  final class AccessPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](f: R => A)(implicit trace: ZTraceElement): ZStream[R, Nothing, A] =
      ZStream.environment[R].map(f)
  }

  final class AccessZIOPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[R1 <: R, E, A](f: R => ZIO[R1, E, A])(implicit trace: ZTraceElement): ZStream[R with R1, E, A] =
      ZStream.environment[R].mapZIO(f)
  }

  final class AccessStreamPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[R1 <: R, E, A](f: R => ZStream[R1, E, A])(implicit trace: ZTraceElement): ZStream[R with R1, E, A] =
      ZStream.environment[R].flatMap(f)
  }

  final class ServiceAtPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[Key](
      key: => Key
    )(implicit
      tag: Tag[Map[Key, Service]],
      trace: ZTraceElement
    ): ZStream[HasMany[Key, Service], Nothing, Option[Service]] =
      ZStream.access(_.getAt(key))
  }

  final class ServiceWithPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[R <: Has[Service], E, A](f: Service => ZIO[R, E, A])(implicit
      tag: Tag[Service],
      trace: ZTraceElement
    ): ZStream[R with Has[Service], E, A] =
      ZStream.fromZIO(ZIO.serviceWith(f))
  }

  final class ServiceWithStreamPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[R <: Has[Service], E, A](f: Service => ZStream[R, E, A])(implicit
      tag: Tag[Service],
      trace: ZTraceElement
    ): ZStream[R with Has[Service], E, A] =
      ZStream.service[Service].flatMap(f)
  }

  /**
   * Representation of a grouped stream.
   * This allows to filter which groups will be processed.
   * Once this is applied all groups will be processed in parallel and the results will
   * be merged in arbitrary order.
   */
  sealed trait GroupBy[-R, +E, +K, +V] { self =>

    type O

    protected def stream: ZStream[R, E, O]
    protected def key: O => ZIO[R, E, (K, V)]
    protected def buffer: Int

    def grouped(implicit trace: ZTraceElement): ZStream[R, E, (K, Dequeue[Exit[Option[E], V]])] =
      ZStream.unwrapManaged {
        for {
          decider <- Promise.make[Nothing, (K, V) => UIO[UniqueKey => Boolean]].toManaged
          out <- Queue
                   .bounded[Exit[Option[E], (K, Dequeue[Exit[Option[E], V]])]](buffer)
                   .toManagedWith(_.shutdown)
          ref <- Ref.make[Map[K, UniqueKey]](Map()).toManaged
          add <- stream
                   .mapZIO(key)
                   .distributedWithDynamic(
                     buffer,
                     (kv: (K, V)) => decider.await.flatMap(_.tupled(kv)),
                     out.offer
                   )
          _ <- decider.succeed { case (k, _) =>
                 ref.get.map(_.get(k)).flatMap {
                   case Some(idx) => ZIO.succeedNow(_ == idx)
                   case None =>
                     add.flatMap { case (idx, q) =>
                       (ref.update(_ + (k -> idx)) *>
                         out.offer(Exit.succeed(k -> q.map(_.map(_._2))))).as(_ == idx)
                     }
                 }
               }.toManaged
        } yield ZStream.fromQueueWithShutdown(out).flattenExitOption
      }

    /**
     * Only consider the first n groups found in the stream.
     */
    def first(n: Int): GroupBy[R, E, K, V] =
      new GroupBy[R, E, K, V] {
        type O = self.O
        def stream: ZStream[R, E, O]    = self.stream
        def key: O => ZIO[R, E, (K, V)] = self.key
        def buffer: Int                 = self.buffer
        override def grouped(implicit trace: ZTraceElement): ZStream[R, E, (K, Dequeue[Exit[Option[E], V]])] =
          self.grouped.zipWithIndex.filterZIO { case elem @ ((_, q), i) =>
            if (i < n) ZIO.succeedNow(elem).as(true)
            else q.shutdown.as(false)
          }.map(_._1)
      }

    /**
     * Filter the groups to be processed.
     */
    def filter(f: K => Boolean): GroupBy[R, E, K, V] =
      new GroupBy[R, E, K, V] {
        type O = self.O
        def stream: ZStream[R, E, O]    = self.stream
        def key: O => ZIO[R, E, (K, V)] = self.key
        def buffer: Int                 = self.buffer
        override def grouped(implicit trace: ZTraceElement): ZStream[R, E, (K, Dequeue[Exit[Option[E], V]])] =
          self.grouped.filterZIO { case elem @ (k, q) =>
            if (f(k)) ZIO.succeedNow(elem).as(true)
            else q.shutdown.as(false)
          }
      }

    /**
     * Run the function across all groups, collecting the results in an arbitrary order.
     */
    def apply[R1 <: R, E1 >: E, A](f: (K, ZStream[Any, E, V]) => ZStream[R1, E1, A])(implicit
      trace: ZTraceElement
    ): ZStream[R1, E1, A] =
      grouped.flatMapPar[R1, E1, A](Int.MaxValue, buffer) { case (k, q) =>
        f(k, ZStream.fromQueueWithShutdown(q).flattenExitOption)
      }
  }

  final class ProvideSomeDeps[R0, -R, +E, +A](private val self: ZStream[R, E, A]) extends AnyVal {
    def apply[E1 >: E, R1](
      deps: ZDeps[R0, E1, R1]
    )(implicit
      ev1: R0 with R1 <:< R,
      ev2: Has.Union[R0, R1],
      tagged: Tag[R1],
      trace: ZTraceElement
    ): ZStream[R0, E1, A] =
      self.provideDeps[E1, R0, R0 with R1](ZDeps.environment[R0] ++ deps)
  }

  final class UpdateService[-R, +E, +O, M](private val self: ZStream[R, E, O]) extends AnyVal {
    def apply[R1 <: R with Has[M]](
      f: M => M
    )(implicit ev: Has.IsHas[R1], tag: Tag[M], trace: ZTraceElement): ZStream[R1, E, O] =
      self.provideSome(ev.update(_, f))
  }

  final class UpdateServiceAt[-R, +E, +A, Service](private val self: ZStream[R, E, A]) extends AnyVal {
    def apply[R1 <: R with HasMany[Key, Service], Key](key: => Key)(
      f: Service => Service
    )(implicit ev: Has.IsHas[R1], tag: Tag[Map[Key, Service]], trace: ZTraceElement): ZStream[R1, E, A] =
      self.provideSome(ev.updateAt(_, key, f))
  }

  /**
   * A `ZStreamConstructor[Input]` knows how to construct a `ZStream` value
   * from an input of type `Input`. This allows the type of the `ZStream`
   * value constructed to depend on `Input`.
   */
  trait ZStreamConstructor[Input] {

    /**
     * The type of the `ZStream` value.
     */
    type Out

    /**
     * Constructs a `ZStream` value from the specified input.
     */
    def make(input: => Input)(implicit trace: ZTraceElement): Out
  }

  object ZStreamConstructor extends ZStreamConstructorPlatformSpecific {

    /**
     * Constructs a `ZStream[RB, EB, B]` from a
     * `ZHub[RA, RB, EA, EB, A, Chunk[B]]`.
     */
    implicit def ChunkHubConstructor[RA, RB, EA, EB, A, B]
      : WithOut[ZHub[RA, RB, EA, EB, A, Chunk[B]], ZStream[RB, EB, B]] =
      new ZStreamConstructor[ZHub[RA, RB, EA, EB, A, Chunk[B]]] {
        type Out = ZStream[RB, EB, B]
        def make(input: => ZHub[RA, RB, EA, EB, A, Chunk[B]])(implicit trace: ZTraceElement): ZStream[RB, EB, B] =
          ZStream.fromChunkHub(input)
      }

    /**
     * Constructs a `ZStream[RB, EB, B]` from a
     * `ZQueue[RA, RB, EA, EB, A, Chunk[B]]`.
     */
    implicit def ChunkQueueConstructor[RA, RB, EA, EB, A, B]
      : WithOut[ZQueue[RA, RB, EA, EB, A, Chunk[B]], ZStream[RB, EB, B]] =
      new ZStreamConstructor[ZQueue[RA, RB, EA, EB, A, Chunk[B]]] {
        type Out = ZStream[RB, EB, B]
        def make(input: => ZQueue[RA, RB, EA, EB, A, Chunk[B]])(implicit trace: ZTraceElement): ZStream[RB, EB, B] =
          ZStream.fromChunkQueue(input)
      }

    /**
     * Constructs a `ZStream[Any, Nothing, A]` from an `Iterable[Chunk[A]]`.
     */
    implicit def ChunksConstructor[A, Collection[Element] <: Iterable[Element]]
      : WithOut[Collection[Chunk[A]], ZStream[Any, Nothing, A]] =
      new ZStreamConstructor[Collection[Chunk[A]]] {
        type Out = ZStream[Any, Nothing, A]
        def make(input: => Collection[Chunk[A]])(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
          ZStream.fromIterable(input).flatMap(ZStream.fromChunk(_))
      }

    /**
     * Constructs a `ZStream[R, E, A]` from a `ZIO[R, E, Iterable[A]]`.
     */
    implicit def IterableZIOConstructor[R, E, A, Collection[Element] <: Iterable[Element]]
      : WithOut[ZIO[R, E, Collection[A]], ZStream[R, E, A]] =
      new ZStreamConstructor[ZIO[R, E, Collection[A]]] {
        type Out = ZStream[R, E, A]
        def make(input: => ZIO[R, E, Collection[A]])(implicit trace: ZTraceElement): ZStream[R, E, A] =
          ZStream.fromIterableZIO(input)
      }

    /**
     * Constructs a `ZStream[Any, Throwable, A]` from an `Iterator[A]`.
     */
    implicit def IteratorConstructor[A, IteratorLike[Element] <: Iterator[Element]]
      : WithOut[IteratorLike[A], ZStream[Any, Throwable, A]] =
      new ZStreamConstructor[IteratorLike[A]] {
        type Out = ZStream[Any, Throwable, A]
        def make(input: => IteratorLike[A])(implicit trace: ZTraceElement): ZStream[Any, Throwable, A] =
          ZStream.fromIterator(input)
      }

    /**
     * Constructs a `ZStream[R, Throwable, A]` from a
     * `ZManaged[R, Throwable, Iterator[A]]`.
     */
    implicit def IteratorManagedConstructor[R, E <: Throwable, A, IteratorLike[Element] <: Iterator[Element]]
      : WithOut[ZManaged[R, E, IteratorLike[A]], ZStream[R, Throwable, A]] =
      new ZStreamConstructor[ZManaged[R, E, IteratorLike[A]]] {
        type Out = ZStream[R, Throwable, A]
        def make(input: => ZManaged[R, E, IteratorLike[A]])(implicit trace: ZTraceElement): ZStream[R, Throwable, A] =
          ZStream.fromIteratorManaged(input)
      }

    /**
     * Constructs a `ZStream[R, Throwable, A]` from a
     * `ZIO[R, Throwable, Iterator[A]]`.
     */
    implicit def IteratorZIOConstructor[R, E <: Throwable, A, IteratorLike[Element] <: Iterator[Element]]
      : WithOut[ZIO[R, E, IteratorLike[A]], ZStream[R, Throwable, A]] =
      new ZStreamConstructor[ZIO[R, E, IteratorLike[A]]] {
        type Out = ZStream[R, Throwable, A]
        def make(input: => ZIO[R, E, IteratorLike[A]])(implicit trace: ZTraceElement): ZStream[R, Throwable, A] =
          ZStream.fromIteratorZIO(input)
      }

    /**
     * Constructs a `ZStream[Any, Throwable, A]` from a
     * `java.util.Iterator[A]`.
     */
    implicit def JavaIteratorConstructor[A, JavaIteratorLike[Element] <: ju.Iterator[Element]]
      : WithOut[JavaIteratorLike[A], ZStream[Any, Throwable, A]] =
      new ZStreamConstructor[JavaIteratorLike[A]] {
        type Out = ZStream[Any, Throwable, A]
        def make(input: => JavaIteratorLike[A])(implicit trace: ZTraceElement): ZStream[Any, Throwable, A] =
          ZStream.fromJavaIterator(input)
      }

    /**
     * Constructs a `ZStream[R, Throwable, A]` from a
     * `ZManaged[R, Throwable, java.util.Iterator[A]]`.
     */
    implicit def JavaIteratorManagedConstructor[R, E <: Throwable, A, JavaIteratorLike[Element] <: ju.Iterator[Element]]
      : WithOut[ZManaged[R, E, JavaIteratorLike[A]], ZStream[R, Throwable, A]] =
      new ZStreamConstructor[ZManaged[R, E, JavaIteratorLike[A]]] {
        type Out = ZStream[R, Throwable, A]
        def make(input: => ZManaged[R, E, JavaIteratorLike[A]])(implicit
          trace: ZTraceElement
        ): ZStream[R, Throwable, A] =
          ZStream.fromJavaIteratorManaged(input)
      }

    /**
     * Constructs a `ZStream[R, Throwable, A]` from a
     * `ZIO[R, Throwable, java.util.Iterator[A]]`.
     */
    implicit def JavaIteratorZIOConstructor[R, E <: Throwable, A, JavaIteratorLike[Element] <: ju.Iterator[Element]]
      : WithOut[ZIO[R, E, JavaIteratorLike[A]], ZStream[R, Throwable, A]] =
      new ZStreamConstructor[ZIO[R, E, JavaIteratorLike[A]]] {
        type Out = ZStream[R, Throwable, A]
        def make(input: => ZIO[R, E, JavaIteratorLike[A]])(implicit trace: ZTraceElement): ZStream[R, Throwable, A] =
          ZStream.fromJavaIteratorZIO(input)
      }

    /**
     * Constructs a `ZStream[R, Nothing, A]` from a `Schedule[R, Any, A]`.
     */
    implicit def ScheduleConstructor[R, A]: WithOut[Schedule[R, Any, A], ZStream[R with Has[Clock], Nothing, A]] =
      new ZStreamConstructor[Schedule[R, Any, A]] {
        type Out = ZStream[R with Has[Clock], Nothing, A]
        def make(input: => Schedule[R, Any, A])(implicit trace: ZTraceElement): ZStream[R with Has[Clock], Nothing, A] =
          ZStream.fromSchedule(input)
      }

    /**
     * Constructs a `ZStream[Any, Nothing, A]` from a `TQueue[A]`.
     */
    implicit def TQueueConstructor[A]: WithOut[TQueue[A], ZStream[Any, Nothing, A]] =
      new ZStreamConstructor[TQueue[A]] {
        type Out = ZStream[Any, Nothing, A]
        def make(input: => TQueue[A])(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
          ZStream.fromTQueue(input)
      }
  }

  trait ZStreamConstructorLowPriority1 extends ZStreamConstructorLowPriority2 {

    /**
     * Constructs a `ZStream[Any, Nothing, A]` from a `Chunk[A]`.
     */
    implicit def ChunkConstructor[A]: WithOut[Chunk[A], ZStream[Any, Nothing, A]] =
      new ZStreamConstructor[Chunk[A]] {
        type Out = ZStream[Any, Nothing, A]
        def make(input: => Chunk[A])(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
          ZStream.fromChunk(input)
      }

    /**
     * Constructs a `ZStream[RB, EB, B]` from a `ZHub[RA, RB, EA, EB, A, B]`.
     */
    implicit def HubConstructor[RA, RB, EA, EB, A, B]: WithOut[ZHub[RA, RB, EA, EB, A, B], ZStream[RB, EB, B]] =
      new ZStreamConstructor[ZHub[RA, RB, EA, EB, A, B]] {
        type Out = ZStream[RB, EB, B]
        def make(input: => ZHub[RA, RB, EA, EB, A, B])(implicit trace: ZTraceElement): ZStream[RB, EB, B] =
          ZStream.fromHub(input)
      }

    /**
     * Constructs a `ZStream[Any, Nothing, A]` from a `Iterable[A]`.
     */
    implicit def IterableConstructor[A, Collection[Element] <: Iterable[Element]]
      : WithOut[Collection[A], ZStream[Any, Nothing, A]] =
      new ZStreamConstructor[Collection[A]] {
        type Out = ZStream[Any, Nothing, A]
        def make(input: => Collection[A])(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
          ZStream.fromIterable(input)
      }

    /**
     * Constructs a `ZStream[RB, EB, B]` from a `ZQueue[RA, RB, EA, EB, A, B]`.
     */
    implicit def QueueConstructor[RA, RB, EA, EB, A, B]: WithOut[ZQueue[RA, RB, EA, EB, A, B], ZStream[RB, EB, B]] =
      new ZStreamConstructor[ZQueue[RA, RB, EA, EB, A, B]] {
        type Out = ZStream[RB, EB, B]
        def make(input: => ZQueue[RA, RB, EA, EB, A, B])(implicit trace: ZTraceElement): ZStream[RB, EB, B] =
          ZStream.fromQueue(input)
      }

    /**
     * Construct a `ZStream[R, E, A]` from a `ZIO[R, Option[E], A]`.
     */
    implicit def ZIOOptionConstructor[R, E, A]: WithOut[ZIO[R, Option[E], A], ZStream[R, E, A]] =
      new ZStreamConstructor[ZIO[R, Option[E], A]] {
        type Out = ZStream[R, E, A]
        def make(input: => ZIO[R, Option[E], A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
          ZStream.fromZIOOption(input)
      }

    /**
     * Construct a `ZStream[R, E, A]` from a `ZIO[R, Option[E], A]`.
     */
    implicit def ZIOOptionNoneConstructor[R, A]: WithOut[ZIO[R, None.type, A], ZStream[R, Nothing, A]] =
      new ZStreamConstructor[ZIO[R, None.type, A]] {
        type Out = ZStream[R, Nothing, A]
        def make(input: => ZIO[R, None.type, A])(implicit trace: ZTraceElement): ZStream[R, Nothing, A] =
          ZStream.fromZIOOption(input)
      }

    /**
     * Construct a `ZStream[R, E, A]` from a `ZIO[R, Option[E], A]`.
     */
    implicit def ZIOOptionSomeConstructor[R, E, A]: WithOut[ZIO[R, Some[E], A], ZStream[R, E, A]] =
      new ZStreamConstructor[ZIO[R, Some[E], A]] {
        type Out = ZStream[R, E, A]
        def make(input: => ZIO[R, Some[E], A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
          ZStream.fromZIOOption(input)
      }
  }

  trait ZStreamConstructorLowPriority2 extends ZStreamConstructorLowPriority3 {

    /**
     * Construct a `ZStream[R, E, A]` from a `ZIO[R, E, A]`.
     */
    implicit def ZIOConstructor[R, E, A]: WithOut[ZIO[R, E, A], ZStream[R, E, A]] =
      new ZStreamConstructor[ZIO[R, E, A]] {
        type Out = ZStream[R, E, A]
        def make(input: => ZIO[R, E, A])(implicit trace: ZTraceElement): ZStream[R, E, A] =
          ZStream.fromZIO(input)
      }
  }

  trait ZStreamConstructorLowPriority3 {

    /**
     * The type of the `ZStreamConstructor` with the type of the `ZStream` value.
     */
    type WithOut[In, Out0] = ZStreamConstructor[In] { type Out = Out0 }

    /**
     * Construct a `ZStream[R, E, A]` from a `ZIO[R, E, A]`.
     */
    implicit def SucceedConstructor[A]: WithOut[A, ZStream[Any, Nothing, A]] =
      new ZStreamConstructor[A] {
        type Out = ZStream[Any, Nothing, A]
        def make(input: => A)(implicit trace: ZTraceElement): ZStream[Any, Nothing, A] =
          ZStream.succeed(input)
      }
  }

  type Pull[-R, +E, +O] = ZIO[R, Option[E], Chunk[O]]

  private[zio] object Pull {
    def emit[A](a: A)(implicit trace: ZTraceElement): IO[Nothing, Chunk[A]]         = UIO(Chunk.single(a))
    def emit[A](as: Chunk[A])(implicit trace: ZTraceElement): IO[Nothing, Chunk[A]] = UIO(as)
    def fromDequeue[E, A](d: Dequeue[stream.Take[E, A]])(implicit trace: ZTraceElement): IO[Option[E], Chunk[A]] =
      d.take.flatMap(_.done)
    def fail[E](e: E)(implicit trace: ZTraceElement): IO[Option[E], Nothing] = IO.fail(Some(e))
    def failCause[E](c: Cause[E])(implicit trace: ZTraceElement): IO[Option[E], Nothing] =
      IO.failCause(c).mapError(Some(_))
    @deprecated("use failCause", "2.0.0")
    def halt[E](c: Cause[E])(implicit trace: ZTraceElement): IO[Option[E], Nothing] = failCause(c)
    def empty[A](implicit trace: ZTraceElement): IO[Nothing, Chunk[A]]   = UIO(Chunk.empty)
    def end(implicit trace: ZTraceElement): IO[Option[Nothing], Nothing] = IO.fail(None)
  }

  private[zio] case class BufferedPull[R, E, A](
    upstream: ZIO[R, Option[E], Chunk[A]],
    done: Ref[Boolean],
    cursor: Ref[(Chunk[A], Int)]
  ) {
    def ifNotDone[R1, E1, A1](fa: ZIO[R1, Option[E1], A1])(implicit trace: ZTraceElement): ZIO[R1, Option[E1], A1] =
      done.get.flatMap(
        if (_) Pull.end
        else fa
      )

    def update(implicit trace: ZTraceElement): ZIO[R, Option[E], Unit] =
      ifNotDone {
        upstream.foldZIO(
          {
            case None    => done.set(true) *> Pull.end
            case Some(e) => Pull.fail(e)
          },
          chunk => cursor.set(chunk -> 0)
        )
      }

    def pullElement(implicit trace: ZTraceElement): ZIO[R, Option[E], A] =
      ifNotDone {
        cursor.modify { case (chunk, idx) =>
          if (idx >= chunk.size) (update *> pullElement, (Chunk.empty, 0))
          else (UIO.succeedNow(chunk(idx)), (chunk, idx + 1))
        }.flatten
      }

    def pullChunk(implicit trace: ZTraceElement): ZIO[R, Option[E], Chunk[A]] =
      ifNotDone {
        cursor.modify { case (chunk, idx) =>
          if (idx >= chunk.size) (update *> pullChunk, (Chunk.empty, 0))
          else (UIO.succeedNow(chunk.drop(idx)), (Chunk.empty, 0))
        }.flatten
      }

  }

  private[zio] object BufferedPull {
    def make[R, E, A](
      pull: ZIO[R, Option[E], Chunk[A]]
    )(implicit trace: ZTraceElement): ZIO[R, Nothing, BufferedPull[R, E, A]] =
      for {
        done   <- Ref.make(false)
        cursor <- Ref.make[(Chunk[A], Int)](Chunk.empty -> 0)
      } yield BufferedPull(pull, done, cursor)
  }

  /**
   * A synchronous queue-like abstraction that allows a producer to offer
   * an element and wait for it to be taken, and allows a consumer to wait
   * for an element to be available.
   */
  private[zio] class Handoff[A](ref: Ref[Handoff.State[A]]) {
    def offer(a: A)(implicit trace: ZTraceElement): UIO[Unit] =
      Promise.make[Nothing, Unit].flatMap { p =>
        ref.modify {
          case s @ Handoff.State.Full(_, notifyProducer) => (notifyProducer.await *> offer(a), s)
          case Handoff.State.Empty(notifyConsumer)       => (notifyConsumer.succeed(()) *> p.await, Handoff.State.Full(a, p))
        }.flatten
      }

    def take(implicit trace: ZTraceElement): UIO[A] =
      Promise.make[Nothing, Unit].flatMap { p =>
        ref.modify {
          case Handoff.State.Full(a, notifyProducer)   => (notifyProducer.succeed(()).as(a), Handoff.State.Empty(p))
          case s @ Handoff.State.Empty(notifyConsumer) => (notifyConsumer.await *> take, s)
        }.flatten
      }

    def poll(implicit trace: ZTraceElement): UIO[Option[A]] =
      Promise.make[Nothing, Unit].flatMap { p =>
        ref.modify {
          case Handoff.State.Full(a, notifyProducer) => (notifyProducer.succeed(()).as(Some(a)), Handoff.State.Empty(p))
          case s @ Handoff.State.Empty(_)            => (ZIO.succeedNow(None), s)
        }.flatten
      }
  }

  private[zio] object Handoff {
    def make[A](implicit trace: ZTraceElement): UIO[Handoff[A]] =
      Promise
        .make[Nothing, Unit]
        .flatMap(p => Ref.make[State[A]](State.Empty(p)))
        .map(new Handoff(_))

    sealed trait State[+A]
    object State {
      case class Empty(notifyConsumer: Promise[Nothing, Unit])          extends State[Nothing]
      case class Full[+A](a: A, notifyProducer: Promise[Nothing, Unit]) extends State[A]
    }
  }

  final class WhenZIO[R, E](private val b: ZIO[R, E, Boolean]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, O](zStream: ZStream[R1, E1, O])(implicit trace: ZTraceElement): ZStream[R1, E1, O] =
      fromZIO(b).flatMap(if (_) zStream else ZStream.empty)
  }

  final class WhenCaseZIO[R, E, A](private val a: ZIO[R, E, A]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, O](pf: PartialFunction[A, ZStream[R1, E1, O]])(implicit
      trace: ZTraceElement
    ): ZStream[R1, E1, O] =
      fromZIO(a).flatMap(pf.applyOrElse(_, (_: A) => ZStream.empty))
  }

  sealed trait TerminationStrategy
  object TerminationStrategy {
    case object Left   extends TerminationStrategy
    case object Right  extends TerminationStrategy
    case object Both   extends TerminationStrategy
    case object Either extends TerminationStrategy
  }

  implicit final class RefineToOrDieOps[R, E <: Throwable, A](private val self: ZStream[R, E, A]) extends AnyVal {

    /**
     * Keeps some of the errors, and terminates the fiber with the rest.
     */
    def refineToOrDie[E1 <: E: ClassTag](implicit ev: CanFail[E], trace: ZTraceElement): ZStream[R, E1, A] =
      self.refineOrDie { case e: E1 => e }
  }

  implicit final class SyntaxOps[-R, +E, O](self: ZStream[R, E, O]) {
    /*
     * Collect elements of the given type flowing through the stream, and filters out others.
     */
    def collectType[O1 <: O](implicit tag: ClassTag[O1], trace: ZTraceElement): ZStream[R, E, O1] =
      self.collect { case o if tag.runtimeClass.isInstance(o) => o.asInstanceOf[O1] }
  }

  /**
   * An `Emit[R, E, A, B]` represents an asynchronous callback that can be
   * called multiple times. The callback can be called with a value of type
   * `ZIO[R, Option[E], Chunk[A]]`, where succeeding with a `Chunk[A]`
   * indicates to emit those elements, failing with `Some[E]` indicates to
   * terminate with that error, and failing with `None` indicates to terminate
   * with an end of stream signal.
   */
  trait Emit[+R, -E, -A, +B] extends (ZIO[R, Option[E], Chunk[A]] => B) {

    def apply(v1: ZIO[R, Option[E], Chunk[A]]): B

    /**
     * Emits a chunk containing the specified values.
     */
    def chunk(as: Chunk[A])(implicit trace: ZTraceElement): B =
      apply(ZIO.succeedNow(as))

    /**
     * Terminates with a cause that dies with the specified `Throwable`.
     */
    def die(t: Throwable)(implicit trace: ZTraceElement): B =
      apply(ZIO.die(t))

    /**
     * Terminates with a cause that dies with a `Throwable` with the specified
     * message.
     */
    def dieMessage(message: String)(implicit trace: ZTraceElement): B =
      apply(ZIO.dieMessage(message))

    /**
     * Either emits the specified value if this `Exit` is a `Success` or else
     * terminates with the specified cause if this `Exit` is a `Failure`.
     */
    def done(exit: Exit[E, A])(implicit trace: ZTraceElement): B =
      apply(ZIO.done(exit.mapBoth(e => Some(e), a => Chunk(a))))

    /**
     * Terminates with an end of stream signal.
     */
    def end(implicit trace: ZTraceElement): B =
      apply(ZIO.fail(None))

    /**
     * Terminates with the specified error.
     */
    def fail(e: E)(implicit trace: ZTraceElement): B =
      apply(ZIO.fail(Some(e)))

    /**
     * Either emits the success value of this effect or terminates the stream
     * with the failure value of this effect.
     */
    @deprecated("use fromZIOChunk", "2.0.0")
    def fromEffect(zio: ZIO[R, E, A])(implicit trace: ZTraceElement): B =
      fromZIO(zio)

    /**
     * Either emits the success value of this effect or terminates the stream
     * with the failure value of this effect.
     */
    @deprecated("use fromZIOChunk", "2.0.0")
    def fromEffectChunk(zio: ZIO[R, E, Chunk[A]])(implicit trace: ZTraceElement): B =
      fromZIOChunk(zio)

    /**
     * Either emits the success value of this effect or terminates the stream
     * with the failure value of this effect.
     */
    def fromZIO(zio: ZIO[R, E, A])(implicit trace: ZTraceElement): B =
      apply(zio.mapBoth(e => Some(e), a => Chunk(a)))

    /**
     * Either emits the success value of this effect or terminates the stream
     * with the failure value of this effect.
     */
    def fromZIOChunk(zio: ZIO[R, E, Chunk[A]])(implicit trace: ZTraceElement): B =
      apply(zio.mapError(e => Some(e)))

    /**
     * Terminates the stream with the specified cause.
     */
    def halt(cause: Cause[E])(implicit trace: ZTraceElement): B =
      apply(ZIO.failCause(cause.map(e => Some(e))))

    /**
     * Emits a chunk containing the specified value.
     */
    def single(a: A)(implicit trace: ZTraceElement): B =
      apply(ZIO.succeedNow(Chunk(a)))
  }

  /**
   * Provides extension methods for streams that are sorted by distinct keys.
   */
  implicit final class SortedByKey[R, E, K, A](private val self: ZStream[R, E, (K, A)]) {

    /**
     * Zips this stream that is sorted by distinct keys and the specified
     * stream that is sorted by distinct keys to produce a new stream that is
     * sorted by distinct keys. Combines values associated with each key into a
     * tuple, using the specified values `defaultLeft` and `defaultRight` to
     * fill in missing values.
     *
     * This allows zipping potentially unbounded streams of data by key in
     * constant space but the caller is responsible for ensuring that the
     * streams are sorted by distinct keys.
     */
    final def zipAllSortedByKey[R1 <: R, E1 >: E, B](
      that: ZStream[R1, E1, (K, B)]
    )(defaultLeft: A, defaultRight: B)(implicit ord: Ordering[K], trace: ZTraceElement): ZStream[R1, E1, (K, (A, B))] =
      zipAllSortedByKeyWith(that)((_, defaultRight), (defaultLeft, _))((_, _))

    /**
     * Zips this stream that is sorted by distinct keys and the specified
     * stream that is sorted by distinct keys to produce a new stream that is
     * sorted by distinct keys. Keeps only values from this stream, using the
     * specified value `default` to fill in missing values.
     *
     * This allows zipping potentially unbounded streams of data by key in
     * constant space but the caller is responsible for ensuring that the
     * streams are sorted by distinct keys.
     */
    final def zipAllSortedByKeyLeft[R1 <: R, E1 >: E, B](
      that: ZStream[R1, E1, (K, B)]
    )(default: A)(implicit ord: Ordering[K], trace: ZTraceElement): ZStream[R1, E1, (K, A)] =
      zipAllSortedByKeyWith(that)(identity, _ => default)((a, _) => a)

    /**
     * Zips this stream that is sorted by distinct keys and the specified
     * stream that is sorted by distinct keys to produce a new stream that is
     * sorted by distinct keys. Keeps only values from that stream, using the
     * specified value `default` to fill in missing values.
     *
     * This allows zipping potentially unbounded streams of data by key in
     * constant space but the caller is responsible for ensuring that the
     * streams are sorted by distinct keys.
     */
    final def zipAllSortedByKeyRight[R1 <: R, E1 >: E, B](
      that: ZStream[R1, E1, (K, B)]
    )(default: B)(implicit ord: Ordering[K], trace: ZTraceElement): ZStream[R1, E1, (K, B)] =
      zipAllSortedByKeyWith(that)(_ => default, identity)((_, b) => b)

    /**
     * Zips this stream that is sorted by distinct keys and the specified
     * stream that is sorted by distinct keys to produce a new stream that is
     * sorted by distinct keys. Uses the functions `left`, `right`, and `both`
     * to handle the cases where a key and value exist in this stream, that
     * stream, or both streams.
     *
     * This allows zipping potentially unbounded streams of data by key in
     * constant space but the caller is responsible for ensuring that the
     * streams are sorted by distinct keys.
     */
    final def zipAllSortedByKeyWith[R1 <: R, E1 >: E, B, C](
      that: ZStream[R1, E1, (K, B)]
    )(left: A => C, right: B => C)(
      both: (A, B) => C
    )(implicit ord: Ordering[K], trace: ZTraceElement): ZStream[R1, E1, (K, C)] =
      zipAllSortedByKeyWithExec(that)(ExecutionStrategy.Parallel)(left, right)(both)

    /**
     * Zips this stream that is sorted by distinct keys and the specified
     * stream that is sorted by distinct keys to produce a new stream that is
     * sorted by distinct keys. Uses the functions `left`, `right`, and `both`
     * to handle the cases where a key and value exist in this stream, that
     * stream, or both streams.
     *
     * This allows zipping potentially unbounded streams of data by key in
     * constant space but the caller is responsible for ensuring that the
     * streams are sorted by distinct keys.
     *
     * The execution strategy `exec` will be used to determine whether to pull
     * from the streams sequentially or in parallel.
     */
    final def zipAllSortedByKeyWithExec[R1 <: R, E1 >: E, B, C](that: ZStream[R1, E1, (K, B)])(
      exec: ExecutionStrategy
    )(left: A => C, right: B => C)(
      both: (A, B) => C
    )(implicit ord: Ordering[K], trace: ZTraceElement): ZStream[R1, E1, (K, C)] = {

      sealed trait State
      case object DrainLeft                                extends State
      case object DrainRight                               extends State
      case object PullBoth                                 extends State
      final case class PullLeft(rightChunk: Chunk[(K, B)]) extends State
      final case class PullRight(leftChunk: Chunk[(K, A)]) extends State

      def pull(
        state: State,
        pullLeft: ZIO[R, Option[E], Chunk[(K, A)]],
        pullRight: ZIO[R1, Option[E1], Chunk[(K, B)]]
      ): ZIO[R1, Nothing, Exit[Option[E1], (Chunk[(K, C)], State)]] =
        state match {
          case DrainLeft =>
            pullLeft.fold(
              e => Exit.fail(e),
              leftChunk => Exit.succeed(leftChunk.map { case (k, a) => (k, left(a)) } -> DrainLeft)
            )
          case DrainRight =>
            pullRight.fold(
              e => Exit.fail(e),
              rightChunk => Exit.succeed(rightChunk.map { case (k, b) => (k, right(b)) } -> DrainRight)
            )
          case PullBoth =>
            exec match {
              case ExecutionStrategy.Sequential =>
                pullLeft.foldZIO(
                  {
                    case Some(e) => ZIO.succeedNow(Exit.fail(Some(e)))
                    case None    => pull(DrainRight, pullLeft, pullRight)
                  },
                  leftChunk =>
                    if (leftChunk.isEmpty) pull(PullBoth, pullLeft, pullRight)
                    else pull(PullRight(leftChunk), pullLeft, pullRight)
                )
              case _ =>
                pullLeft.unsome
                  .zipPar(pullRight.unsome)
                  .foldZIO(
                    e => ZIO.succeedNow(Exit.fail(Some(e))),
                    {
                      case (Some(leftChunk), Some(rightChunk)) =>
                        if (leftChunk.isEmpty && rightChunk.isEmpty) pull(PullBoth, pullLeft, pullRight)
                        else if (leftChunk.isEmpty) pull(PullLeft(rightChunk), pullLeft, pullRight)
                        else if (rightChunk.isEmpty) pull(PullRight(leftChunk), pullLeft, pullRight)
                        else ZIO.succeedNow(Exit.succeed(mergeSortedByKeyChunk(leftChunk, rightChunk)))
                      case (Some(leftChunk), None) =>
                        if (leftChunk.isEmpty) pull(DrainLeft, pullLeft, pullRight)
                        else ZIO.succeedNow(Exit.succeed(leftChunk.map { case (k, a) => (k, left(a)) } -> DrainLeft))
                      case (None, Some(rightChunk)) =>
                        if (rightChunk.isEmpty) pull(DrainRight, pullLeft, pullRight)
                        else ZIO.succeedNow(Exit.succeed(rightChunk.map { case (k, b) => (k, right(b)) } -> DrainRight))
                      case (None, None) => ZIO.succeedNow(Exit.fail(None))
                    }
                  )
            }
          case PullLeft(rightChunk) =>
            pullLeft.foldZIO(
              {
                case Some(e) => ZIO.succeedNow(Exit.fail(Some(e)))
                case None    => ZIO.succeedNow(Exit.succeed(rightChunk.map { case (k, b) => (k, right(b)) } -> DrainRight))
              },
              leftChunk =>
                if (leftChunk.isEmpty) pull(PullLeft(rightChunk), pullLeft, pullRight)
                else ZIO.succeedNow(Exit.succeed(mergeSortedByKeyChunk(leftChunk, rightChunk)))
            )
          case PullRight(leftChunk) =>
            pullRight.foldZIO(
              {
                case Some(e) => ZIO.succeedNow(Exit.fail(Some(e)))
                case None    => ZIO.succeedNow(Exit.succeed(leftChunk.map { case (k, a) => (k, left(a)) } -> DrainLeft))
              },
              rightChunk =>
                if (rightChunk.isEmpty) pull(PullRight(leftChunk), pullLeft, pullRight)
                else ZIO.succeedNow(Exit.succeed(mergeSortedByKeyChunk(leftChunk, rightChunk)))
            )
        }

      def mergeSortedByKeyChunk(leftChunk: Chunk[(K, A)], rightChunk: Chunk[(K, B)]): (Chunk[(K, C)], State) = {
        val builder       = ChunkBuilder.make[(K, C)]()
        var state         = null.asInstanceOf[State]
        val leftIterator  = leftChunk.iterator
        val rightIterator = rightChunk.iterator
        var leftTuple     = leftIterator.next()
        var rightTuple    = rightIterator.next()
        var k1            = leftTuple._1
        var a             = leftTuple._2
        var k2            = rightTuple._1
        var b             = rightTuple._2
        var loop          = true
        while (loop) {
          val compare = ord.compare(k1, k2)
          if (compare == 0) {
            builder += k1 -> both(a, b)
            if (leftIterator.hasNext && rightIterator.hasNext) {
              leftTuple = leftIterator.next()
              rightTuple = rightIterator.next()
              k1 = leftTuple._1
              a = leftTuple._2
              k2 = rightTuple._1
              b = rightTuple._2
            } else if (leftIterator.hasNext) {
              state = PullRight(Chunk.fromIterator(leftIterator))
              loop = false
            } else if (rightIterator.hasNext) {
              state = PullLeft(Chunk.fromIterator(rightIterator))
              loop = false
            } else {
              state = PullBoth
              loop = false
            }
          } else if (compare < 0) {
            builder += k1 -> left(a)
            if (leftIterator.hasNext) {
              leftTuple = leftIterator.next()
              k1 = leftTuple._1
              a = leftTuple._2
            } else {
              val rightBuilder = ChunkBuilder.make[(K, B)]()
              rightBuilder += rightTuple
              rightBuilder ++= rightIterator
              state = PullLeft(rightBuilder.result())
              loop = false
            }
          } else {
            builder += k2 -> right(b)
            if (rightIterator.hasNext) {
              rightTuple = rightIterator.next()
              k2 = rightTuple._1
              b = rightTuple._2
            } else {
              val leftBuilder = ChunkBuilder.make[(K, A)]()
              leftBuilder += leftTuple
              leftBuilder ++= leftIterator
              state = PullRight(leftBuilder.result())
              loop = false
            }
          }
        }
        (builder.result(), state)
      }

      self.combineChunks[R1, E1, State, (K, B), (K, C)](that)(PullBoth)(pull)
    }
  }
}
