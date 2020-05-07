package zio.stream

import java.{ util => ju }

import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.internal.UniqueKey
import zio.stm.TQueue

abstract class ZStream[-R, +E, +O](
  val process: ZManaged[R, Nothing, ZIO[R, Option[E], Chunk[O]]]
) extends ZConduit[R, E, Any, O, Any](
      process.map(pull => _ => pull.mapError(_.fold[Either[E, Any]](Right(()))(Left(_))))
    ) { self =>
  import ZStream.{ BufferedPull, Pull, Take }

  /**
   * Symbolic alias for [[ZStream#cross]].
   */
  final def <*>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, (O, O2)] =
    self cross that

  /**
   * Symbolic alias for [[ZStream#crossLeft]].
   */
  final def <*[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O] =
    self crossLeft that

  /**
   * Symbolic alias for [[ZStream#crossRight]].
   */
  final def *>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O2] =
    self crossRight that

  /**
   * Symbolic alias for [[ZStream#zip]].
   */
  final def <&>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, (O, O2)] =
    self zip that

  /**
   * Symbolic alias for [[ZStream#zipLeft]].
   */
  final def <&[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O] =
    self zipLeft that

  /**
   * Symbolic alias for [[ZStream#zipRight]].
   */
  final def &>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O2] =
    self zipRight that

  /**
   * Symbolic alias for [[ZStream#flatMap]].
   */
  def >>=[R1 <: R, E1 >: E, O2](f0: O => ZStream[R1, E1, O2]): ZStream[R1, E1, O2] = flatMap(f0)

  /**
   * Symbolic alias for [[ZStream#transduce]].
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, O3](transducer: ZTransducer[R1, E1, O2, O3]) =
    transduce(transducer)

  /**
   * Symbolic alias for [[[zio.stream.ZStream!.run[R1<:R,E1>:E,B]*]]].
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, Z](sink: ZSink[R1, E1, O2, Z]): ZIO[R1, E1, Z] =
    self.run(sink)

  /**
   * Symbolic alias for [[ZStream#concat]].
   */
  def ++[R1 <: R, E1 >: E, O1 >: O](that: => ZStream[R1, E1, O1]): ZStream[R1, E1, O1] =
    self concat that

  /**
   * Returns a stream that submerges the error case of an `Either` into the `ZStream`.
   */
  final def absolve[R1 <: R, E1, O1](
    implicit ev: ZStream[R, E, O] <:< ZStream[R1, E1, Either[E1, O1]]
  ): ZStream[R1, E1, O1] =
    ZStream.absolve(ev(self))

  /**
   * Applies an aggregator to the stream, which converts one or more elements
   * of type `A` into elements of type `B`.
   */
  def aggregate[R1 <: R, E1 >: E, P](sink: ZTransducer[R1, E1, O, P]): ZStream[R1, E1, P] =
    ZStream {
      for {
        pull <- self.process
        push <- sink.push
        done <- ZRef.makeManaged(false)
        run = {
          def go: ZIO[R1, Option[E1], Chunk[P]] = done.get.flatMap {
            if (_)
              Pull.end
            else
              pull
                .foldM(
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
  ): ZStream[R1, E1, P] =
    aggregateAsyncWithin(transducer, Schedule.forever)

  /**
   * Uses `aggregateAsyncWithinEither` but only returns the `Right` results.
   *
   * @param transducer used for the aggregation
   * @param schedule signalling for when to stop the aggregation
   * @tparam R1 environment type
   * @tparam E1 error type
   * @tparam O1 type of the values consumed by the given transducer
   * @tparam P type of the value produced by the given transducer and consumed by the given schedule
   * @return `ZStream[R1, E1, B]`
   */
  final def aggregateAsyncWithin[R1 <: R, E1 >: E, P](
    transducer: ZTransducer[R1, E1, O, P],
    schedule: Schedule[R1, Chunk[P], Any]
  ): ZStream[R1, E1, P] = aggregateAsyncWithinEither(transducer, schedule).collect {
    case Right(v) => v
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
   * @tparam O1 type of the values consumed by the given transducer
   * @tparam P type of the value produced by the given transducer and consumed by the given schedule
   * @tparam Q type of the value produced by the given schedule
   * @return `ZStream[R1, E1, Either[Q, P]]`
   */
  final def aggregateAsyncWithinEither[R1 <: R, E1 >: E, P, Q](
    transducer: ZTransducer[R1, E1, O, P],
    schedule: Schedule[R1, Chunk[P], Q]
  ): ZStream[R1, E1, Either[Q, P]] =
    ZStream {
      for {
        pull         <- self.process
        push         <- transducer.push
        handoff      <- ZStream.Handoff.make[Take[E1, O]].toManaged_
        raceNextTime <- ZRef.makeManaged(false)
        waitingFiber <- ZRef.makeManaged[Option[Fiber[Nothing, Take[E1, O]]]](None)
        scheduleState <- schedule.initial
                          .flatMap(i => ZRef.make[(Chunk[P], schedule.State)](Chunk.empty -> i))
                          .toManaged_
        producer = pull.run
          .flatMap(take =>
            handoff
              .offer(take)
              .as(take.fold(!_.failures.contains(None), _ => true))
          )
          .doWhile(identity)
        consumer = {
          // Advances the state of the schedule, which may or may not terminate
          val updateSchedule: URIO[R1, Option[schedule.State]] =
            scheduleState.get.flatMap(state => schedule.update(state._1, state._2).option)

          // Waiting for the normal output of the producer
          val waitForProducer: ZIO[R1, Nothing, Take[E1, O]] =
            waitingFiber.getAndSet(None).flatMap {
              case None      => handoff.take
              case Some(fib) => fib.join
            }

          def updateLastChunk(take: Exit[_, Chunk[P]]): UIO[Unit] =
            take match {
              case Exit.Success(chunk) => scheduleState.update(_.copy(_1 = chunk))
              case _                   => ZIO.unit
            }

          def handleTake(take: Take[E1, O]) =
            take
              .foldM(
                Cause.sequenceCauseOption(_) match {
                  case None =>
                    push(None).map(ps => Chunk(Exit.succeed(ps.map(Right(_))), Exit.fail(None)))
                  case Some(cause) => ZIO.halt(cause)
                },
                os =>
                  push(Some(os))
                    .flatMap(ps => updateLastChunk(Exit.succeed(ps)).as(Chunk.single(Exit.succeed(ps.map(Right(_))))))
              )
              .mapError(Some(_))

          def go(race: Boolean): ZIO[R1, Option[E1], Chunk[Take[E1, Either[Q, P]]]] =
            if (!race)
              waitForProducer.flatMap(handleTake) <* raceNextTime.set(true)
            else
              updateSchedule.raceWith[R1, Nothing, Option[E1], Take[E1, O], Chunk[Take[E1, Either[Q, P]]]](
                waitForProducer
              )(
                (scheduleDone, producerWaiting) =>
                  ZIO.done(scheduleDone).flatMap {
                    case None =>
                      for {
                        init           <- schedule.initial
                        state          <- scheduleState.getAndSet(Chunk.empty -> init)
                        scheduleResult = Exit.succeed(Chunk.single(Left(schedule.extract(state._1, state._2))))
                        ps             <- push(None).run.tap(updateLastChunk)
                        _              <- raceNextTime.set(false)
                        _              <- waitingFiber.set(Some(producerWaiting))
                      } yield Chunk(scheduleResult, ps.bimap(Some(_), _.map(Right(_))))
                    case Some(nextState) =>
                      for {
                        _  <- scheduleState.update(_.copy(_2 = nextState))
                        ps <- push(None).run.tap(updateLastChunk)
                        _  <- raceNextTime.set(false)
                        _  <- waitingFiber.set(Some(producerWaiting))
                      } yield Chunk.single(ps.bimap(Some(_), _.map(Right(_))))
                  },
                (producerDone, scheduleWaiting) => scheduleWaiting.interrupt *> handleTake(producerDone.flatten)
              )

          raceNextTime.get.flatMap(go)

        }

        _ <- producer.forkManaged
      } yield consumer
    }.collectWhileSuccess.flattenChunks

  /**
   * Maps the success values of this stream to the specified constant value.
   */
  def as[O2](o2: => O2): ZStream[R, E, O2] =
    map(new ZIO.ConstFn(() => o2))

  /**
   * Returns a stream whose failure and success channels have been mapped by
   * the specified pair of functions, `f` and `g`.
   */
  def bimap[E1, O1](f: E => E1, g: O => O1)(implicit ev: CanFail[E]): ZStream[R, E1, O1] =
    mapError(f).map(g)

  /**
   * Fan out the stream, producing a list of streams that have the same elements as this stream.
   * The driver stream will only ever advance of the `maximumLag` chunks before the
   * slowest downstream stream.
   */
  final def broadcast(n: Int, maximumLag: Int): ZManaged[R, Nothing, List[ZStream[Any, E, O]]] =
    self
      .broadcastedQueues(n, maximumLag)
      .map(
        _.map(
          ZStream
            .fromQueueWithShutdown(_)
            .collectWhileSuccess
        )
      )

  /**
   * Fan out the stream, producing a dynamic number of streams that have the same elements as this stream.
   * The driver stream will only ever advance of the `maximumLag` chunks before the
   * slowest downstream stream.
   */
  final def broadcastDynamic(
    maximumLag: Int
  ): ZManaged[R, Nothing, UIO[ZStream[Any, E, O]]] =
    distributedWithDynamic(maximumLag, _ => ZIO.succeedNow(_ => true), _ => ZIO.unit)
      .map(_.map(_._2))
      .map(
        _.map(
          ZStream
            .fromQueueWithShutdown(_)
            .collectWhileSuccess
        )
      )

  /**
   * Converts the stream to a managed list of queues. Every value will be replicated to every queue with the
   * slowest queue being allowed to buffer `maximumLag` chunks before the driver is backpressured.
   * The downstream queues will be provided with chunks in the same order they are returned, so
   * the fastest queue might have seen up to (`maximumLag` + 1) chunks more than the slowest queue if it
   * has a lower index than the slowest queue.
   *
   * Queues can unsubscribe from upstream by shutting down.
   */
  final def broadcastedQueues(
    n: Int,
    maximumLag: Int
  ): ZManaged[R, Nothing, List[Dequeue[Exit[Option[E], O]]]] = {
    val decider = ZIO.succeedNow((_: Int) => true)
    distributedWith(n, maximumLag, _ => decider)
  }

  /**
   * Converts the stream to a managed dynamic amount of queues. Every chunk will be replicated to every queue with the
   * slowest queue being allowed to buffer `maximumLag` chunks before the driver is backpressured.
   * The downstream queues will be provided with chunks in the same order they are returned, so
   * the fastest queue might have seen up to (`maximumLag` + 1) chunks more than the slowest queue if it
   * has a lower index than the slowest queue.
   *
   * Queues can unsubscribe from upstream by shutting down.
   */
  final def broadcastedQueuesDynamic(
    maximumLag: Int
  ): ZManaged[R, Nothing, UIO[Dequeue[Exit[Option[E], O]]]] = {
    val decider = ZIO.succeedNow((_: UniqueKey) => true)
    distributedWithDynamic(maximumLag, _ => decider, _ => ZIO.unit).map(_.map(_._2))
  }

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * up to `capacity` chunks in a queue.
   *
   * @note Prefer capacities that are powers of 2 for better performance.
   */
  final def buffer(capacity: Int): ZStream[R, E, O] =
    ZStream {
      for {
        done  <- Ref.make(false).toManaged_
        queue <- self.toQueue(capacity)
        pull = done.get.flatMap {
          if (_) Pull.end
          else
            queue.take.flatMap(ZIO.done(_)).catchSome {
              case None => done.set(true) *> Pull.end
            }
        }
      } yield pull
    }

  private final def bufferSignal[E1 >: E, O1 >: O](
    queue: Queue[(Take[E1, O1], Promise[Nothing, Unit])]
  ): ZManaged[R, Nothing, ZIO[R, Option[E1], Chunk[O1]]] =
    for {
      as    <- self.process
      start <- Promise.make[Nothing, Unit].toManaged_
      _     <- start.succeed(()).toManaged_
      ref   <- Ref.make(start).toManaged_
      done  <- Ref.make(false).toManaged_
      upstream = {
        def offer(take: Take[E1, O1]): UIO[Unit] =
          take.fold(
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

        def go: URIO[R, Unit] =
          as.run.flatMap(take => offer(take) *> go.when(take != Take.End))

        go
      }
      _ <- upstream.toManaged_.fork
      pull = done.get.flatMap {
        if (_) Pull.end
        else
          queue.take.flatMap {
            case (take, p) =>
              p.succeed(()) *> done.set(true).when(take == Take.End) *> Pull.fromTake(take)
          }
      }
    } yield pull

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * up to `capacity` elements in a dropping queue.
   *
   * @note Prefer capacities that are powers of 2 for better performance.
   */
  final def bufferDropping(capacity: Int): ZStream[R, E, O] =
    ZStream {
      for {
        queue <- Queue.dropping[(Take[E, O], Promise[Nothing, Unit])](capacity).toManaged(_.shutdown)
        pull  <- bufferSignal(queue)
      } yield pull
    }

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * up to `capacity` elements in a sliding queue.
   *
   * @note Prefer capacities that are powers of 2 for better performance.
   */
  final def bufferSliding(capacity: Int): ZStream[R, E, O] =
    ZStream {
      for {
        queue <- Queue.sliding[(Take[E, O], Promise[Nothing, Unit])](capacity).toManaged(_.shutdown)
        pull  <- bufferSignal(queue)
      } yield pull
    }

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * elements into an unbounded queue.
   */
  final def bufferUnbounded: ZStream[R, E, O] =
    ZStream {
      for {
        done  <- ZRef.make(false).toManaged_
        queue <- self.toQueueUnbounded
        pull = done.get.flatMap {
          if (_) Pull.end
          else
            queue.take
              .flatMap(Pull.fromTake(_))
              .catchAllCause(
                Cause.sequenceCauseOption(_).fold[ZIO[R, Option[E], Chunk[O]]](done.set(true) *> Pull.end)(Pull.halt(_))
              )
        }
      } yield pull
    }

  /**
   * Switches over to the stream produced by the provided function in case this one
   * fails with a typed error.
   */
  final def catchAll[R1 <: R, E2, O1 >: O](f: E => ZStream[R1, E2, O1])(implicit ev: CanFail[E]): ZStream[R1, E2, O1] =
    catchAllCause(_.failureOrCause.fold(f, ZStream.halt(_)))

  /**
   * Switches over to the stream produced by the provided function in case this one
   * fails. Allows recovery from all causes of failure, including interruption if the
   * stream is uninterruptible.
   */
  final def catchAllCause[R1 <: R, E2, O1 >: O](f: Cause[E] => ZStream[R1, E2, O1]): ZStream[R1, E2, O1] = {
    sealed abstract class State
    object State {
      case object NotStarted extends State
      case object Self       extends State
      case object Other      extends State
    }

    ZStream {
      for {
        finalizers <- ZManaged.finalizerRef[R1](_ => UIO.unit)
        selfPull   <- Ref.make[ZIO[R, Option[E], Chunk[O]]](Pull.end).toManaged_
        otherPull  <- Ref.make[ZIO[R1, Option[E2], Chunk[O1]]](Pull.end).toManaged_
        stateRef   <- Ref.make[State](State.NotStarted).toManaged_
        pull = {
          def switch(e: Cause[Option[E]]): ZIO[R1, Option[E2], Chunk[O1]] = {
            def next(e: Cause[E]) = ZIO.uninterruptibleMask { restore =>
              for {
                _  <- finalizers.remove.flatMap(ZIO.foreach(_)(_(Exit.fail(e))))
                r  <- f(e).process.reserve
                _  <- finalizers.add(r.release)
                as <- restore(r.acquire)
                _  <- otherPull.set(as)
                _  <- stateRef.set(State.Other)
                a  <- as
              } yield a
            }

            Cause.sequenceCauseOption(e) match {
              case None    => Pull.end
              case Some(c) => next(c)
            }
          }

          stateRef.get.flatMap {
            case State.NotStarted =>
              ZIO.uninterruptibleMask { restore =>
                for {
                  r  <- self.process.reserve
                  _  <- finalizers.add(r.release)
                  as <- restore(r.acquire)
                  _  <- selfPull.set(as)
                  _  <- stateRef.set(State.Self)
                  a  <- as
                } yield a
              }.catchAllCause(switch)

            case State.Self =>
              selfPull.get.flatten.catchAllCause(switch)

            case State.Other =>
              otherPull.get.flatten
          }
        }
      } yield pull
    }
  }

  /**
   * Performs a filter and map in a single step.
   */
  def collect[O1](pf: PartialFunction[O, O1]): ZStream[R, E, O1] =
    mapChunks(_.collect(pf))

  /**
   * Filters any 'None'.
   */
  final def collectSome[O1](implicit ev: O <:< Option[O1]): ZStream[R, E, O1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Option[O1]]].collect { case Some(a) => a }
  }

  /**
   * Performs an effectful filter and map in a single step.
   */
  final def collectM[R1 <: R, E1 >: E, O1](pf: PartialFunction[O, ZIO[R1, E1, O1]]): ZStream[R1, E1, O1] =
    ZStream(self.process.map(_.flatMap(_.collectM(pf).mapError(Some(_)))))

  /**
   * Transforms all elements of the stream for as long as the specified partial function is defined.
   */
  def collectWhile[O2](p: PartialFunction[O, O2]): ZStream[R, E, O2] =
    ZStream {
      for {
        chunks  <- self.process
        doneRef <- Ref.make(false).toManaged_
        pull = doneRef.get.flatMap { done =>
          if (done) Pull.end
          else
            for {
              chunk     <- chunks
              remaining = chunk.collectWhile(p)
              _         <- doneRef.set(true).when(remaining.length < chunk.length)
            } yield remaining
        }
      } yield pull
    }

  /**
   * Effectfully transforms all elements of the stream for as long as the specified partial function is defined.
   */
  final def collectWhileM[R1 <: R, E1 >: E, O2](pf: PartialFunction[O, ZIO[R1, E1, O2]]): ZStream[R1, E1, O2] =
    ZStream {
      for {
        chunks <- self.process
        done   <- Ref.make(false).toManaged_
        pull = done.get.flatMap {
          if (_) Pull.end
          else
            for {
              chunk     <- chunks
              remaining <- chunk.collectWhileM(pf).mapError(Some(_))
              _         <- done.set(true).when(remaining.length < chunk.length)
            } yield remaining
        }
      } yield pull
    }

  /**
   * Terminates the stream when encountering the first `None`.
   */
  final def collectWhileSome[O1](implicit ev: O <:< Option[O1]): ZStream[R, E, O1] = {
    val _ = ev
    self.asInstanceOf[ZStream[R, E, Option[O1]]].collectWhile { case Some(a) => a }
  }

  /**
   * Unwraps [[Exit]] values that also signify end-of-stream by failing with `None`.
   *
   * For `Exit[E, O]` values that do not signal end-of-stream, prefer:
   * {{{
   * stream.mapM(ZIO.done(_))
   * }}}
   */
  def collectWhileSuccess[E1 >: E, O1](implicit ev: O <:< Exit[Option[E1], O1]): ZStream[R, E1, O1] =
    ZStream {
      for {
        upstream <- self.process.mapM(BufferedPull.make(_))
        done     <- Ref.make(false).toManaged_
        pull = done.get.flatMap {
          if (_) Pull.end
          else
            upstream.pullElement
              .foldM(
                {
                  case None    => done.set(true) *> Pull.end
                  case Some(e) => Pull.fail(e)
                },
                os =>
                  ZIO
                    .done(ev(os))
                    .foldM(
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
   * Combines the elements from this stream and the specified stream by repeatedly applying the
   * function `f` to extract an element using both sides and conceptually "offer"
   * it to the destination stream. `f` can maintain some internal state to control
   * the combining process, with the initial state being specified by `s`.
   *
   * Where possible, prefer [[ZStream#combineChunks]] for a more efficient implementation.
   */
  final def combine[R1 <: R, E1 >: E, S, O2, O3](that: ZStream[R1, E1, O2])(s: S)(
    f: (S, ZIO[R, Option[E], O], ZIO[R1, Option[E1], O2]) => ZIO[R1, Nothing, Exit[Option[E1], (O3, S)]]
  ): ZStream[R1, E1, O3] =
    ZStream[R1, E1, O3] {
      for {
        left  <- self.process.mapM(BufferedPull.make[R, E, O](_)) // type annotation required for Dotty
        right <- that.process.mapM(BufferedPull.make[R1, E1, O2](_))
        pull <- ZStream
                 .unfoldM(s)(s => f(s, left.pullElement, right.pullElement).flatMap(ZIO.done(_).optional))
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
  ): ZStream[R1, E1, O3] =
    ZStream[R1, E1, O3] {
      for {
        left  <- self.process
        right <- that.process
        pull <- ZStream
                 .unfoldChunkM(s)(s => f(s, left, right).flatMap(ZIO.done(_).optional))
                 .process
      } yield pull
    }

  /**
   * Concatenates the specified stream with this stream, resulting in a stream
   * that emits the elements from this stream and then the elements from the specified stream.
   */
  def concat[R1 <: R, E1 >: E, O1 >: O](that: => ZStream[R1, E1, O1]): ZStream[R1, E1, O1] =
    ZStream {
      // This implementation is identical to ZStream.concatAll, but specialized so we can
      // maintain laziness on `that`. Laziness on concatenation is important for combinators
      // such as `forever`.
      for {
        currStream   <- Ref.make[ZIO[R1, Option[E1], Chunk[O1]]](Pull.end).toManaged_
        switchStream <- ZManaged.switchable[R1, Nothing, ZIO[R1, Option[E1], Chunk[O1]]]
        switched     <- Ref.make(false).toManaged_
        _            <- switchStream(self.process).flatMap(currStream.set).toManaged_
        pull = {
          def go: ZIO[R1, Option[E1], Chunk[O1]] =
            currStream.get.flatten.catchAllCause {
              Cause.sequenceCauseOption(_) match {
                case Some(e) => Pull.halt(e)
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
  final def crossWith[R1 <: R, E1 >: E, O2, C](that: ZStream[R1, E1, O2])(f: (O, O2) => C): ZStream[R1, E1, C] =
    self.flatMap(l => that.map(r => f(l, r)))

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def cross[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, (O, O2)] =
    (self crossWith that)((_, _))

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements,
   * but keeps only elements from this stream.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def crossLeft[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O] =
    (self crossWith that)((o, _) => o)

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements,
   * but keeps only elements from the other stream.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def crossRight[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O2] =
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
  ): ZManaged[R, Nothing, List[Dequeue[Exit[Option[E1], O]]]] =
    Promise.make[Nothing, O => UIO[UniqueKey => Boolean]].toManaged_.flatMap { prom =>
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
        }.toManaged_
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
  ): ZManaged[R, Nothing, UIO[(UniqueKey, Dequeue[Exit[Option[E], O]])]] =
    for {
      queuesRef <- Ref
                    .make[Map[UniqueKey, Queue[Exit[Option[E], O]]]](Map())
                    .toManaged(_.get.flatMap(qs => ZIO.foreach(qs.values)(_.shutdown)))
      add <- {
        val offer = (o: O) =>
          for {
            shouldProcess <- decide(o)
            queues        <- queuesRef.get
            _ <- ZIO
                  .foldLeft(queues)(List[UniqueKey]()) {
                    case (acc, (id, queue)) =>
                      if (shouldProcess(id)) {
                        queue
                          .offer(Exit.succeed(o))
                          .foldCauseM(
                            {
                              // we ignore all downstream queues that were shut down and remove them later
                              case c if c.interrupted => ZIO.succeedNow(id :: acc)
                              case c                  => ZIO.halt(c)
                            },
                            _ => ZIO.succeedNow(acc)
                          )
                      } else ZIO.succeedNow(acc)
                  }
                  .flatMap(ids => if (ids.nonEmpty) queuesRef.update(_ -- ids) else ZIO.unit)
          } yield ()

        for {
          queuesLock <- Semaphore.make(1).toManaged_
          newQueue <- Ref
                       .make[UIO[(UniqueKey, Queue[Exit[Option[E], O]])]] {
                         for {
                           queue <- Queue.bounded[Exit[Option[E], O]](maximumLag)
                           id    = UniqueKey()
                           _     <- queuesRef.update(_ + (id -> queue))
                         } yield (id, queue)
                       }
                       .toManaged_
          finalize = (endTake: Exit[Option[E], Nothing]) =>
            // we need to make sure that no queues are currently being added
            queuesLock.withPermit {
              for {
                // all newly created queues should end immediately
                _ <- newQueue.set {
                      for {
                        queue <- Queue.bounded[Exit[Option[E], O]](1)
                        _     <- queue.offer(endTake)
                        id    = UniqueKey()
                        _     <- queuesRef.update(_ + (id -> queue))
                      } yield (id, queue)
                    }
                queues <- queuesRef.get.map(_.values)
                _ <- ZIO.foreach(queues) { queue =>
                      queue.offer(endTake).catchSomeCause {
                        case c if c.interrupted => ZIO.unit
                      }
                    }
                _ <- done(endTake)
              } yield ()
            }
          _ <- self
                .foreachManaged(offer)
                .foldCauseM(
                  cause => finalize(Exit.halt(cause.map(Some(_)))).toManaged_,
                  _ => finalize(Exit.fail(None)).toManaged_
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
   *   Stream.fromEffect(ZIO(println("Done!"))).drain ++
   *   Stream(4, 5, 6).tap(i => ZIO(println(i)))).run(Sink.drain)
   * }}}
   */
  final def drain: ZStream[R, E, Nothing] =
    ZStream(self.process.map(_.forever))

  /**
   * Drains the provided stream in the background for as long as this stream is running.
   * If this stream ends before `other`, `other` will be interrupted. If `other` fails,
   * this stream will fail with that error.
   */
  final def drainFork[R1 <: R, E1 >: E](other: ZStream[R1, E1, Any]): ZStream[R1, E1, O] =
    ZStream.fromEffect(Promise.make[E1, Nothing]).flatMap { bgDied =>
      ZStream
        .managed(other.foreachManaged(_ => ZIO.unit).catchAllCause(bgDied.halt(_).toManaged_).fork) *>
        self.interruptWhen(bgDied)
    }

  /**
   * Drops the specified number of elements from this stream.
   */
  def drop(n: Long): ZStream[R, E, O] =
    ZStream {
      for {
        chunks     <- self.process
        counterRef <- Ref.make(0L).toManaged_
        pull = {
          def go: ZIO[R, Option[E], Chunk[O]] =
            chunks.flatMap { chunk =>
              counterRef.get.flatMap { cnt =>
                if (cnt >= n) ZIO.succeedNow(chunk)
                else if (chunk.size <= (n - cnt)) counterRef.set(cnt + chunk.size) *> go
                else counterRef.set(cnt + chunk.size - (n - cnt)).as(chunk.drop((n - cnt).toInt))
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
  final def dropUntil(pred: O => Boolean): ZStream[R, E, O] =
    dropWhile(!pred(_)).drop(1)

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def dropWhile(pred: O => Boolean): ZStream[R, E, O] =
    ZStream {
      for {
        chunks          <- self.process
        keepDroppingRef <- Ref.make(true).toManaged_
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
  final def either(implicit ev: CanFail[E]): ZStream[R, Nothing, Either[E, O]] =
    self.map(Right(_)).catchAll(e => ZStream(Left(e)))

  /**
   * Executes the provided finalizer after this stream's finalizers run.
   */
  final def ensuring[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStream[R1, E, O] =
    ZStream(self.process.ensuring(fin))

  /**
   * Executes the provided finalizer before this stream's finalizers run.
   */
  final def ensuringFirst[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStream[R1, E, O] =
    ZStream(self.process.ensuringFirst(fin))

  /**
   * Executes a pure fold over the stream of values - reduces all elements in the stream to a value of type `S`.
   */
  final def fold[O1 >: O, S](s: S)(f: (S, O1) => S): ZIO[R, E, S] =
    foldWhileManagedM[R, E, O1, S](s)(_ => true)((s, a) => ZIO.succeedNow(f(s, a))).use(ZIO.succeedNow)

  /**
   * Executes an effectful fold over the stream of values.
   */
  final def foldM[R1 <: R, E1 >: E, O1 >: O, S](s: S)(f: (S, O1) => ZIO[R1, E1, S]): ZIO[R1, E1, S] =
    foldWhileManagedM[R1, E1, O1, S](s)(_ => true)(f).use(ZIO.succeedNow)

  /**
   * Executes a pure fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   */
  final def foldManaged[O1 >: O, S](s: S)(f: (S, O1) => S): ZManaged[R, E, S] =
    foldWhileManagedM[R, E, O1, S](s)(_ => true)((s, a) => ZIO.succeedNow(f(s, a)))

  /**
   * Executes an effectful fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   */
  final def foldManagedM[R1 <: R, E1 >: E, O1 >: O, S](s: S)(f: (S, O1) => ZIO[R1, E1, S]): ZManaged[R1, E1, S] =
    foldWhileManagedM[R1, E1, O1, S](s)(_ => true)(f)

  /**
   * Reduces the elements in the stream to a value of type `S`.
   * Stops the fold early when the condition is not fulfilled.
   * Example:
   * {{{
   *  Stream(1).forever.foldWhile(0)(_ <= 4)(_ + _) // UIO[Int] == 5
   * }}}
   */
  final def foldWhile[O1 >: O, S](s: S)(cont: S => Boolean)(f: (S, O1) => S): ZIO[R, E, S] =
    foldWhileManagedM[R, E, O1, S](s)(cont)((s, a) => ZIO.succeedNow(f(s, a))).use(ZIO.succeedNow)

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
  final def foldWhileM[R1 <: R, E1 >: E, O1 >: O, S](
    s: S
  )(cont: S => Boolean)(f: (S, O1) => ZIO[R1, E1, S]): ZIO[R1, E1, S] =
    foldWhileManagedM[R1, E1, O1, S](s)(cont)(f).use(ZIO.succeedNow)

  /**
   * Executes a pure fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   * Stops the fold early when the condition is not fulfilled.
   */
  def foldWhileManaged[O1 >: O, S](s: S)(cont: S => Boolean)(f: (S, O1) => S): ZManaged[R, E, S] =
    foldWhileManagedM[R, E, O1, S](s)(cont)((s, a) => ZIO.succeedNow(f(s, a)))

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
  final def foldWhileManagedM[R1 <: R, E1 >: E, A1 >: O, S](
    s: S
  )(cont: S => Boolean)(f: (S, A1) => ZIO[R1, E1, S]): ZManaged[R1, E1, S] =
    process.flatMap { (is: ZIO[R, Option[E], Chunk[O]]) =>
      def loop(s1: S): ZIO[R1, E1, S] =
        if (!cont(s1)) UIO.succeedNow(s1)
        else
          is.foldM({
            case Some(e) =>
              IO.fail(e)
            case None =>
              IO.succeedNow(s1)
          }, (ch: Chunk[O]) => ch.foldM(s1)(f).flatMap(loop))

      ZManaged.fromEffect(loop(s))
    }

  /**
   * Consumes all elements of the stream, passing them to the specified callback.
   */
  final def foreach[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Any]): ZIO[R1, E1, Unit] =
    foreachWhile(f.andThen(_.as(true)))

  /**
   * Consumes all elements of the stream, passing them to the specified callback.
   */
  final def foreachChunk[R1 <: R, E1 >: E](f: Chunk[O] => ZIO[R1, E1, Any]): ZIO[R1, E1, Unit] =
    foreachChunkWhile(f.andThen(_.as(true)))

  /**
   * Like [[ZStream#foreachChunk]], but returns a `ZManaged` so the finalization order
   * can be controlled.
   */
  final def foreachChunkManaged[R1 <: R, E1 >: E](f: Chunk[O] => ZIO[R1, E1, Any]): ZManaged[R1, E1, Unit] =
    foreachChunkWhileManaged(f.andThen(_.as(true)))

  /**
   * Consumes chunks of the stream, passing them to the specified callback,
   * and terminating consumption when the callback returns `false`.
   */
  final def foreachChunkWhile[R1 <: R, E1 >: E](f: Chunk[O] => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Unit] =
    foreachChunkWhileManaged(f).use_(ZIO.unit)

  /**
   * Like [[ZStream#foreachChunkWhile]], but returns a `ZManaged` so the finalization order
   * can be controlled.
   */
  final def foreachChunkWhileManaged[R1 <: R, E1 >: E](f: Chunk[O] => ZIO[R1, E1, Boolean]): ZManaged[R1, E1, Unit] =
    for {
      chunks <- self.process
      step = chunks.flatMap(f(_).mapError(Some(_))).flatMap {
        if (_) UIO.unit else IO.fail(None)
      }
      _ <- step.forever.catchAll {
            case Some(e) => IO.fail(e)
            case None    => UIO.unit
          }.toManaged_
    } yield ()

  /**
   * Like [[ZStream#foreach]], but returns a `ZManaged` so the finalization order
   * can be controlled.
   */
  final def foreachManaged[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Any]): ZManaged[R1, E1, Unit] =
    foreachWhileManaged(f.andThen(_.as(true)))

  /**
   * Consumes elements of the stream, passing them to the specified callback,
   * and terminating consumption when the callback returns `false`.
   */
  final def foreachWhile[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Unit] =
    foreachWhileManaged(f).use_(ZIO.unit)

  /**
   * Like [[ZStream#foreachWhile]], but returns a `ZManaged` so the finalization order
   * can be controlled.
   */
  final def foreachWhileManaged[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Boolean]): ZManaged[R1, E1, Unit] =
    foreachChunkWhileManaged(_.foldWhileM(true)(identity)((_, a) => f(a)))

  /**
   * Repeats this stream forever.
   */
  def forever: ZStream[R, E, O] =
    self ++ forever

  /**
   * Filters the elements emitted by this stream using the provided function.
   */
  def filter(f: O => Boolean): ZStream[R, E, O] =
    mapChunks(_.filter(f))

  /**
   * Effectfully filters the elements emitted by this stream.
   */
  def filterM[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Boolean]): ZStream[R1, E1, O] =
    ZStream(self.process.map(_.flatMap(_.filterM(f).mapError(Some(_)))))

  /**
   * Filters this stream by the specified predicate, removing all elements for
   * which the predicate evaluates to true.
   */
  final def filterNot(pred: O => Boolean): ZStream[R, E, O] = filter(a => !pred(a))

  /**
   * Emits elements of this stream with a fixed delay in between, regardless of how long it
   * takes to produce a value.
   */
  final def fixed[R1 <: R](duration: Duration): ZStream[R1 with Clock, E, O] =
    scheduleElementsEither(Schedule.spaced(duration) >>> Schedule.stop).collect {
      case Right(x) => x
    }

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f0`
   */
  def flatMap[R1 <: R, E1 >: E, O2](f0: O => ZStream[R1, E1, O2]): ZStream[R1, E1, O2] = {
    def go(
      outerStream: ZIO[R1, Option[E1], Chunk[O]],
      switchInner: ZManaged[R1, Nothing, ZIO[R1, Option[E1], Chunk[O2]]] => ZIO[
        R1,
        Nothing,
        ZIO[R1, Option[E1], Chunk[O2]]
      ],
      currInnerStream: Ref[ZIO[R1, Option[E1], Chunk[O2]]]
    ): ZIO[R1, Option[E1], Chunk[O2]] = {
      def pullOuter: ZIO[R1, Option[E1], Unit] =
        outerStream
          .flatMap(os => switchInner(ZStream.concatAll(os.map(f0)).process))
          .flatMap(currInnerStream.set)

      currInnerStream.get.flatten.catchAllCause { c =>
        Cause.sequenceCauseOption(c) match {
          case Some(e) => Pull.halt(e)
          case None    =>
            // The additional switch is needed to eagerly run the finalizer
            // *before* pulling another element from the outer stream.
            switchInner(ZManaged.succeed(Pull.end)) *>
              pullOuter *>
              go(outerStream, switchInner, currInnerStream)
        }
      }
    }

    ZStream {
      for {
        currInnerStream <- Ref.make[ZIO[R1, Option[E1], Chunk[O2]]](Pull.end).toManaged_
        switchInner     <- ZManaged.switchable[R1, Nothing, ZIO[R1, Option[E1], Chunk[O2]]]
        outerStream     <- self.process
      } yield go(outerStream, switchInner, currInnerStream)
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
  ): ZStream[R1, E1, O2] =
    ZStream[R1, E1, O2] {
      for {
        out          <- Queue.bounded[ZIO[R1, Option[E1], Chunk[O2]]](outputBuffer).toManaged(_.shutdown)
        permits      <- Semaphore.make(n.toLong).toManaged_
        innerFailure <- Promise.make[Cause[E1], Nothing].toManaged_

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
                innerStream = ZStream
                  .managed(permits.withPermitManaged)
                  .tap(_ => latch.succeed(()))
                  .flatMap(_ => f(a))
                  .foreachChunk(b => out.offer(UIO.succeedNow(b)).unit)
                  .foldCauseM(
                    cause => out.offer(Pull.halt(cause)) *> innerFailure.fail(cause).unit,
                    _ => ZIO.unit
                  )
                _ <- innerStream.fork
                // Make sure that the current inner stream has actually succeeded in acquiring
                // a permit before continuing. Otherwise we could reach the end of the stream and
                // acquire the permits ourselves before the inners had a chance to start.
                _ <- latch.await
              } yield ()
            }.foldCauseM(
                cause => (ZIO.interruptAllChildren *> out.offer(Pull.halt(cause)).unit).toManaged_,
                _ =>
                  innerFailure.await.interruptible
                  // Important to use `withPermits` here because the ZManaged#fork below may interrupt
                  // the driver, and we want the permits to be released in that case
                    .raceWith(permits.withPermits(n.toLong)(ZIO.unit).interruptible)(
                      // One of the inner fibers failed. It already enqueued its failure, so we
                      // interrupt the inner fibers. The finalizer below will make sure
                      // that they actually end.
                      leftDone = (_, permitAcquisition) => ZIO.interruptAllChildren *> permitAcquisition.interrupt.unit,
                      // All fibers completed successfully, so we signal that we're done.
                      rightDone = (_, failureAwait) => out.offer(Pull.end) *> failureAwait.interrupt.unit
                    )
                    .toManaged_
              )
              .fork
      } yield out.take.flatten
    }

  /**
   * Maps each element of this stream to another stream and returns the non-deterministic merge
   * of those streams, executing up to `n` inner streams concurrently. When a new stream is created
   * from an element of the source stream, the oldest executing stream is cancelled. Up to `bufferSize`
   * elements of the produced streams may be buffered in memory by this operator.
   */
  final def flatMapParSwitch[R1 <: R, E1 >: E, O2](n: Int, bufferSize: Int = 16)(
    f: O => ZStream[R1, E1, O2]
  ): ZStream[R1, E1, O2] =
    ZStream[R1, E1, O2] {
      for {
        // Modeled after flatMapPar.
        out          <- Queue.bounded[ZIO[R1, Option[E1], Chunk[O2]]](bufferSize).toManaged(_.shutdown)
        permits      <- Semaphore.make(n.toLong).toManaged_
        innerFailure <- Promise.make[Cause[E1], Nothing].toManaged_
        cancelers    <- Queue.bounded[Promise[Nothing, Unit]](n).toManaged(_.shutdown)
        _ <- self.foreachManaged { a =>
              for {
                canceler <- Promise.make[Nothing, Unit]
                latch    <- Promise.make[Nothing, Unit]
                size     <- cancelers.size
                _ <- if (size < n) UIO.unit
                    else cancelers.take.flatMap(_.succeed(())).unit
                _ <- cancelers.offer(canceler)
                innerStream = ZStream
                  .managed(permits.withPermitManaged)
                  .tap(_ => latch.succeed(()))
                  .flatMap(_ => f(a))
                  .foreachChunk(o2s => out.offer(UIO.succeedNow(o2s)).unit)
                  .foldCauseM(
                    cause => out.offer(Pull.halt(cause)) *> innerFailure.fail(cause).unit,
                    _ => UIO.unit
                  )
                _ <- (innerStream race canceler.await).fork
                _ <- latch.await
              } yield ()
            }.foldCauseM(
                cause => (ZIO.interruptAllChildren *> out.offer(Pull.halt(cause))).unit.toManaged_,
                _ =>
                  innerFailure.await
                    .raceWith(permits.withPermits(n.toLong)(UIO.unit))(
                      leftDone = (_, permitAcquisition) => ZIO.interruptAllChildren *> permitAcquisition.interrupt.unit,
                      rightDone = (_, failureAwait) => out.offer(Pull.end) *> failureAwait.interrupt.unit
                    )
                    .toManaged_
              )
              .fork
      } yield out.take.flatten
    }

  /**
   * Flattens this stream-of-streams into a stream made of the concatenation in
   * strict order of all the streams.
   */
  def flatten[R1 <: R, E1 >: E, O1](implicit ev: O <:< ZStream[R1, E1, O1]) = flatMap(ev(_))

  /**
   * Submerges the chunks carried by this stream into the stream's structure, while
   * still preserving them.
   */
  def flattenChunks[O1](implicit ev: O <:< Chunk[O1]): ZStream[R, E, O1] =
    mapConcatChunk(ev)

  /**
   * Flattens a stream of streams into a stream by executing a non-deterministic
   * concurrent merge. Up to `n` streams may be consumed in parallel and up to
   * `outputBuffer` elements may be buffered by this operator.
   */
  def flattenPar[R1 <: R, E1 >: E, O1](n: Int, outputBuffer: Int = 16)(
    implicit ev: O <:< ZStream[R1, E1, O1]
  ): ZStream[R1, E1, O1] =
    flatMapPar[R1, E1, O1](n, outputBuffer)(ev(_))

  /**
   * Like [[flattenPar]], but executes all streams concurrently.
   */
  def flattenParUnbounded[R1 <: R, E1 >: E, O1](
    outputBuffer: Int = 16
  )(implicit ev: O <:< ZStream[R1, E1, O1]): ZStream[R1, E1, O1] =
    flattenPar[R1, E1, O1](Int.MaxValue, outputBuffer)

  /**
   * More powerful version of [[ZStream.groupByKey]]
   */
  final def groupBy[R1 <: R, E1 >: E, K, V](
    f: O => ZIO[R1, E1, (K, V)],
    buffer: Int = 16
  ): ZStream.GroupBy[R1, E1, K, V] = {
    val qstream = ZStream.unwrapManaged {
      for {
        decider <- Promise.make[Nothing, (K, V) => UIO[UniqueKey => Boolean]].toManaged_
        out <- Queue
                .bounded[Exit[Option[E1], (K, Dequeue[Exit[Option[E1], V]])]](buffer)
                .toManaged(_.shutdown)
        ref <- Ref.make[Map[K, UniqueKey]](Map()).toManaged_
        add <- self
                .mapM(f)
                .distributedWithDynamic(
                  buffer,
                  (kv: (K, V)) => decider.await.flatMap(_.tupled(kv)),
                  out.offer
                )
        _ <- decider.succeed {
              case (k, _) =>
                ref.get.map(_.get(k)).flatMap {
                  case Some(idx) => ZIO.succeedNow(_ == idx)
                  case None =>
                    add.flatMap {
                      case (idx, q) =>
                        (ref.update(_ + (k -> idx)) *>
                          out.offer(Exit.succeed(k -> q.map(_.map(_._2))))).as(_ == idx)
                    }
                }
            }.toManaged_
      } yield ZStream.fromQueueWithShutdown(out).collectWhileSuccess
    }
    new ZStream.GroupBy(qstream, buffer)
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
  final def haltWhen[R1 <: R, E1 >: E](io: ZIO[R1, E1, Any]): ZStream[R1, E1, O] =
    ZStream {
      for {
        as    <- self.process
        runIO <- io.forkManaged
      } yield runIO.poll.flatMap {
        case None       => as
        case Some(exit) => exit.fold(cause => Pull.halt(cause), _ => Pull.end)
      }
    }

  /**
   * Partitions the stream with specified chunkSize
   * @param chunkSize size of the chunk
   */
  def grouped(chunkSize: Long): ZStream[R, E, List[O]] =
    aggregate(ZTransducer.collectAllN(chunkSize))

  /**
   * Partitions the stream with the specified chunkSize or until the specified
   * duration has passed, whichever is satisfied first.
   */
  def groupedWithin(chunkSize: Long, within: Duration): ZStream[R with Clock, E, List[O]] =
    aggregateAsyncWithin(ZTransducer.collectAllN(chunkSize), Schedule.spaced(within))

  /**
   * Halts the evaluation of this stream when the provided promise resolves.
   *
   * If the promise completes with a failure, the stream will emit that failure.
   */
  final def haltWhen[E1 >: E](p: Promise[E1, _]): ZStream[R, E1, O] =
    ZStream {
      for {
        as   <- self.process
        done <- Ref.make(false).toManaged_
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
  final def interleave[R1 <: R, E1 >: E, O1 >: O](that: ZStream[R1, E1, O1]): ZStream[R1, E1, O1] =
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
  )(b: ZStream[R1, E1, Boolean]): ZStream[R1, E1, O1] = {

    def loop(
      leftDone: Boolean,
      rightDone: Boolean,
      s: ZIO[R1, Option[E1], Boolean],
      left: ZIO[R, Option[E], O],
      right: ZIO[R1, Option[E1], O1]
    ): ZIO[R1, Nothing, Exit[Option[E1], (O1, (Boolean, Boolean, ZIO[R1, Option[E1], Boolean]))]] =
      s.foldCauseM(
        Cause.sequenceCauseOption(_) match {
          case None    => ZIO.succeedNow(Exit.fail(None))
          case Some(e) => ZIO.succeedNow(Exit.halt(e.map(Some(_))))
        },
        b =>
          if (b && !leftDone) {
            left.foldCauseM(
              Cause.sequenceCauseOption(_) match {
                case None =>
                  if (rightDone) ZIO.succeedNow(Exit.fail(None))
                  else loop(true, rightDone, s, left, right)
                case Some(e) => ZIO.succeedNow(Exit.halt(e.map(Some(_))))
              },
              a => ZIO.succeedNow(Exit.succeed((a, (leftDone, rightDone, s))))
            )
          } else if (!b && !rightDone)
            right.foldCauseM(
              Cause.sequenceCauseOption(_) match {
                case Some(e) => ZIO.succeedNow(Exit.halt(e.map(Some(_))))
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
        sides <- b.process.mapM(BufferedPull.make(_))
        result <- self
                   .combine(that)((false, false, sides.pullElement)) {
                     case ((leftDone, rightDone, sides), left, right) =>
                       loop(leftDone, rightDone, sides, left, right)
                   }
                   .process
      } yield result
    }
  }

  /**
   * Intersperse stream with provided element similar to <code>List.mkString</code>.
   */
  final def intersperse[O1 >: O](middle: O1): ZStream[R, E, O1] =
    ZStream {
      for {
        state  <- ZRef.makeManaged(false)
        chunks <- self.process
        pull = chunks.flatMap { os =>
          state.modify { flag =>
            os.foldRight(List.empty[O1] -> flag) {
              case (o, (Nil, curr)) => List(o)              -> !curr
              case (o, (out, curr)) => (o :: middle :: out) -> !curr
            }
          }.map(e => Chunk.fromIterable(e))
        }
      } yield pull
    }

  /**
   * Intersperse and also add a prefix and a suffix
   */
  final def intersperse[O1 >: O](start: O1, middle: O1, end: O1): ZStream[R, E, O1] =
    ZStream(start) ++ intersperse(middle) ++ ZStream(end)

  /**
   * Interrupts the evaluation of this stream when the provided IO completes. The given
   * IO will be forked as part of this stream, and its success will be discarded. This
   * combinator will also interrupt any in-progress element being pulled from upstream.
   *
   * If the IO completes with a failure before the stream completes, the returned stream
   * will emit that failure.
   */
  final def interruptWhen[R1 <: R, E1 >: E](io: ZIO[R1, E1, Any]): ZStream[R1, E1, O] =
    ZStream {
      for {
        as    <- self.process
        runIO <- (io.asSomeError *> Pull.end).forkManaged
      } yield runIO.join.disconnect.raceFirst(as)
    }

  /**
   * Interrupts the evaluation of this stream when the provided promise resolves. This
   * combinator will also interrupt any in-progress element being pulled from upstream.
   *
   * If the promise completes with a failure, the stream will emit that failure.
   */
  final def interruptWhen[E1 >: E](p: Promise[E1, _]): ZStream[R, E1, O] =
    ZStream {
      for {
        as   <- self.process
        done <- Ref.make(false).toManaged_
        pull = done.get flatMap {
          if (_) Pull.end
          else
            as.raceFirst(
              p.await
                .mapError(Some(_))
                .foldCauseM(
                  c => done.set(true) *> ZIO.halt(c),
                  _ => done.set(true) *> Pull.end
                )
            )
        }
      } yield pull
    }

  /**
   * Enqueues elements of this stream into a queue. Stream failure and ending will also be
   * signalled.
   */
  final def into[R1 <: R, E1 >: E, O1 >: O](
    queue: ZQueue[R1, Nothing, Nothing, Any, Exit[Option[E1], Chunk[O1]], Any]
  ): ZIO[R1, E1, Unit] =
    intoManaged(queue).use_(UIO.unit)

  /**
   * Like [[ZStream#into]], but provides the result as a [[ZManaged]] to allow for scope
   * composition.
   */
  final def intoManaged[R1 <: R, E1 >: E, O1 >: O](
    queue: ZQueue[R1, Nothing, Nothing, Any, Exit[Option[E1], Chunk[O1]], Any]
  ): ZManaged[R1, E1, Unit] =
    for {
      as <- self.process
      pull = {
        def go: ZIO[R1, Nothing, Unit] =
          as.foldCauseM(
            Cause
              .sequenceCauseOption(_)
              .fold[ZIO[R1, Nothing, Unit]](queue.offer(Exit.fail(None)).unit)(c =>
                queue.offer(Exit.halt(c.map(Some(_)))) *> go
              ),
            a => queue.offer(Exit.succeed(a)) *> go
          )

        go
      }
      _ <- pull.toManaged_
    } yield ()

  /**
   * Transforms the elements of this stream using the supplied function.
   */
  def map[O2](f: O => O2): ZStream[R, E, O2] =
    mapChunks(_.map(f))

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  def mapAccum[S, O1](s: S)(f: (S, O) => (S, O1)): ZStream[R, E, O1] =
    mapAccumM(s)((s, a) => UIO.succeedNow(f(s, a)))

  /**
   * Statefully and effectfully maps over the elements of this stream to produce
   * new elements.
   */
  final def mapAccumM[R1 <: R, E1 >: E, S, O1](s: S)(f: (S, O) => ZIO[R1, E1, (S, O1)]): ZStream[R1, E1, O1] =
    ZStream {
      for {
        state <- Ref.make(s).toManaged_
        pull  <- self.process
      } yield pull.flatMap { as =>
        (for {
          s <- state.get
          t <- as.mapAccumM(s)(f)
          _ <- state.set(t._1)
        } yield t._2).mapError(Some(_))
      }
    }

  /**
   * Transforms the chunks emitted by this stream.
   */
  def mapChunks[O2](f: Chunk[O] => Chunk[O2]): ZStream[R, E, O2] =
    ZStream(self.process.map { pull =>
      def go: ZIO[R, Option[E], Chunk[O2]] =
        pull.flatMap { os =>
          val o2s = f(os)

          if (o2s.isEmpty) go
          else UIO.succeedNow(o2s)
        }

      go
    })

  /**
   * Maps each element to an iterable, and flattens the iterables into the
   * output of this stream.
   */
  def mapConcat[O2](f: O => Iterable[O2]): ZStream[R, E, O2] =
    mapConcatChunk(o => Chunk.fromIterable(f(o)))

  /**
   * Maps each element to a chunk, and flattens the chunks into the output of
   * this stream.
   */
  def mapConcatChunk[O2](f: O => Chunk[O2]): ZStream[R, E, O2] =
    mapChunks(_.flatMap(f))

  /**
   * Effectfully maps each element to a chunk, and flattens the chunks into
   * the output of this stream.
   */
  final def mapConcatChunkM[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, Chunk[O2]]): ZStream[R1, E1, O2] =
    mapM(f).mapConcatChunk(identity)

  /**
   * Effectfully maps each element to an iterable, and flattens the iterables into
   * the output of this stream.
   */
  final def mapConcatM[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, Iterable[O2]]): ZStream[R1, E1, O2] =
    mapM(a => f(a).map(Chunk.fromIterable(_))).mapConcatChunk(identity)

  /**
   * Transforms the errors emitted by this stream using `f`.
   */
  def mapError[E2](f: E => E2): ZStream[R, E2, O] =
    ZStream(self.process.map(_.mapError(_.map(f))))

  /**
   * Transforms the full causes of failures emitted by this stream.
   */
  def mapErrorCause[E2](f: Cause[E] => Cause[E2]): ZStream[R, E2, O] =
    ZStream(
      self.process.map(
        _.mapErrorCause(
          Cause.sequenceCauseOption(_) match {
            case None    => Cause.fail(None)
            case Some(c) => f(c).map(Some(_))
          }
        )
      )
    )

  /**
   * Maps over elements of the stream with the specified effectful function.
   */
  def mapM[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, O2]): ZStream[R1, E1, O2] =
    ZStream(self.process.map(_.flatMap(_.mapM(f).mapError(Some(_)))))

  /**
   * Maps over elements of the stream with the specified effectful function,
   * executing up to `n` invocations of `f` concurrently. Transformed elements
   * will be emitted in the original order.
   */
  final def mapMPar[R1 <: R, E1 >: E, O2](n: Int)(f: O => ZIO[R1, E1, O2]): ZStream[R1, E1, O2] =
    ZStream[R1, E1, O2] {
      for {
        out     <- Queue.bounded[ZIO[R1, Option[E1], O2]](n).toManaged(_.shutdown)
        permits <- Semaphore.make(n.toLong).toManaged_
        _ <- self.foreachManaged { a =>
              for {
                p     <- Promise.make[E1, O2]
                latch <- Promise.make[Nothing, Unit]
                _     <- out.offer(p.await.mapError(Some(_)))
                _     <- permits.withPermit(latch.succeed(()) *> f(a).to(p)).fork
                _     <- latch.await
              } yield ()
            }.foldCauseM(
                c => out.offer(Pull.halt(c)).unit.toManaged_,
                _ => (out.offer(Pull.end) <* ZIO.awaitAllChildren).unit.toManaged_
              )
              .fork
      } yield out.take.flatten.map(Chunk.single(_))
    }

  /**
   * Maps over elements of the stream with the specified effectful function,
   * executing up to `n` invocations of `f` concurrently. The element order
   * is not enforced by this combinator, and elements may be reordered.
   */
  final def mapMParUnordered[R1 <: R, E1 >: E, O2](n: Int)(f: O => ZIO[R1, E1, O2]): ZStream[R1, E1, O2] =
    flatMapPar[R1, E1, O2](n)(a => ZStream.fromEffect(f(a)))

  /**
   * Maps over elements of the stream with the specified effectful function,
   * partitioned by `p` executing invocations of `f` concurrently. The number
   * of concurrent invocations of `f` is determined by the number of different
   * outputs of type `K`. Up to `buffer` elements may be buffered per partition.
   * Transformed elements may be reordered but the order within a partition is maintained.
   */
  final def mapMPartitioned[R1 <: R, E1 >: E, O2, K](
    keyBy: O => K,
    buffer: Int = 16
  )(f: O => ZIO[R1, E1, O2]): ZStream[R1, E1, O2] =
    groupByKey(keyBy, buffer).apply { case (_, s) => s.mapM(f) }

  /**
   * Merges this stream and the specified stream together.
   */
  final def merge[R1 <: R, E1 >: E, O1 >: O](that: ZStream[R1, E1, O1]): ZStream[R1, E1, O1] =
    self.mergeWith[R1, E1, O1, O1](that)(identity, identity) // TODO: Dotty doesn't infer this properly

  /**
   * Merges this stream and the specified stream together to produce a stream of
   * eithers.
   */
  final def mergeEither[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, Either[O, O2]] =
    self.mergeWith(that)(Left(_), Right(_))

  /**
   * Merges this stream and the specified stream together to a common element
   * type with the specified mapping functions.
   */
  final def mergeWith[R1 <: R, E1 >: E, O2, O3](
    that: ZStream[R1, E1, O2]
  )(l: O => O3, r: O2 => O3): ZStream[R1, E1, O3] = {
    type Loser = Either[Fiber[Nothing, Exit[Option[E], Chunk[O]]], Fiber[Nothing, Exit[Option[E1], Chunk[O2]]]]

    def race(
      left: ZIO[R, Nothing, Exit[Option[E], Chunk[O]]],
      right: ZIO[R1, Nothing, Exit[Option[E1], Chunk[O2]]]
    ): ZIO[R1, Nothing, (Exit[Option[E1], Chunk[O3]], Loser)] =
      left.raceWith[R1, Nothing, Nothing, Exit[Option[E1], Chunk[O2]], (Exit[Option[E1], Chunk[O3]], Loser)](right)(
        (exit, right) => ZIO.done(exit).map(a => (a.map(_.map(l)), Right(right))),
        (exit, left) => ZIO.done(exit).map(b => (b.map(_.map(r)), Left(left)))
      )

    self.combineChunks(that)((false, false, Option.empty[Loser])) {
      case ((leftDone, rightDone, loser), left, right) =>
        if (leftDone) {
          right.map(c => (c.map(r), (leftDone, rightDone, None))).run
        } else if (rightDone) {
          left.map(c => (c.map(l), (leftDone, rightDone, None))).run
        } else {
          val result = loser match {
            case None               => race(left.run, right.run)
            case Some(Left(loser))  => race(loser.join, right.run)
            case Some(Right(loser)) => race(left.run, loser.join)
          }
          result.flatMap {
            case (exit, loser) =>
              exit.foldM(
                Cause.sequenceCauseOption(_) match {
                  case Some(e) =>
                    loser.merge.interrupt.as(Exit.halt(e.map(Some(_))))
                  case None =>
                    loser.fold(
                      _.join.map(_.map(_.map(l))).map(_.map((_, (leftDone, true, None)))),
                      _.join.map(_.map(_.map(r))).map(_.map((_, (true, rightDone, None))))
                    )
                },
                chunk => ZIO.succeedNow(Exit.succeed((chunk, (leftDone, rightDone, Some(loser)))))
              )
          }
        }
    }
  }

  /**
   * Switches to the provided stream in case this one fails with a typed error.
   *
   * See also [[ZStream#catchAll]].
   */
  def orElse[R1 <: R, E2, O1 >: O](that: => ZStream[R1, E2, O1])(implicit ev: CanFail[E]): ZStream[R1, E2, O1] =
    catchAll(_ => that)

  /**
   * Partition a stream using a predicate. The first stream will contain all element evaluated to true
   * and the second one will contain all element evaluated to false.
   * The faster stream may advance by up to buffer elements further than the slower one.
   */
  def partition(p: O => Boolean, buffer: Int = 16): ZManaged[R, E, (ZStream[Any, E, O], ZStream[Any, E, O])] =
    self.partitionEither(a => if (p(a)) ZIO.succeedNow(Left(a)) else ZIO.succeedNow(Right(a)), buffer)

  /**
   * Split a stream by a predicate. The faster stream may advance by up to buffer elements further than the slower one.
   */
  final def partitionEither[R1 <: R, E1 >: E, O2, O3](
    p: O => ZIO[R1, E1, Either[O2, O3]],
    buffer: Int = 16
  ): ZManaged[R1, E1, (ZStream[Any, E1, O2], ZStream[Any, E1, O3])] =
    self
      .mapM(p)
      .distributedWith(2, buffer, {
        case Left(_)  => ZIO.succeedNow(_ == 0)
        case Right(_) => ZIO.succeedNow(_ == 1)
      })
      .flatMap {
        case q1 :: q2 :: Nil =>
          ZManaged.succeedNow {
            (
              ZStream.fromQueueWithShutdown(q1).collectWhileSuccess.collect { case Left(x)  => x },
              ZStream.fromQueueWithShutdown(q2).collectWhileSuccess.collect { case Right(x) => x }
            )
          }
        case otherwise => ZManaged.dieMessage(s"partitionEither: expected two streams but got ${otherwise}")
      }

  /**
   * Peels off enough material from the stream to construct a `Z` using the
   * provided [[ZSink]] and then returns both the `Z` and the rest of the
   * [[ZStream]] in a managed resource. Like all [[ZManaged]] values, the provided
   * stream is valid only within the scope of [[ZManaged]].
   */
  def peel[R1 <: R, E1 >: E, Z](sink: ZSink[R1, E1, O, Z]): ZManaged[R1, E1, (Z, ZStream[R1, E1, O])] =
    self.process.flatMap { pull =>
      val stream = ZStream.repeatEffectChunkOption(pull)

      stream.run(sink).toManaged_.map((_, stream))
    }

  /**
   * Provides the stream with its required environment, which eliminates
   * its dependency on `R`.
   */
  final def provide(r: R)(implicit ev: NeedsEnv[R]): ZStream[Any, E, O] =
    ZStream(self.process.provide(r).map(_.provide(r)))

  /**
   * Provides the part of the environment that is not part of the `ZEnv`,
   * leaving a stream that only depends on the `ZEnv`.
   *
   * {{{
   * val loggingLayer: ZLayer[Any, Nothing, Logging] = ???
   *
   * val stream: ZStream[ZEnv with Logging, Nothing, Unit] = ???
   *
   * val stream2 = stream.provideCustomLayer(loggingLayer)
   * }}}
   */
  def provideCustomLayer[E1 >: E, R1 <: Has[_]](
    layer: ZLayer[ZEnv, E1, R1]
  )(implicit ev: ZEnv with R1 <:< R, tagged: Tagged[R1]): ZStream[ZEnv, E1, O] =
    provideSomeLayer[ZEnv](layer)

  /**
   * Provides a layer to the stream, which translates it to another level.
   */
  final def provideLayer[E1 >: E, R0, R1](
    layer: ZLayer[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): ZStream[R0, E1, O] =
    ZStream.managed {
      for {
        r  <- layer.build.map(ev1)
        as <- self.process.provide(r)
      } yield as.provide(r)
    }.flatMap(ZStream.repeatEffectChunkOption)

  /**
   * Provides some of the environment required to run this effect,
   * leaving the remainder `R0`.
   */
  final def provideSome[R0](env: R0 => R)(implicit ev: NeedsEnv[R]): ZStream[R0, E, O] =
    ZStream {
      for {
        r0 <- ZManaged.environment[R0]
        as <- self.process.provide(env(r0))
      } yield as.provide(env(r0))
    }

  /**
   * Splits the environment into two parts, providing one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val stream: ZStream[Clock with Random, Nothing, Unit] = ???
   *
   * val stream2 = stream.provideSomeLayer[Random](clockLayer)
   * }}}
   */
  final def provideSomeLayer[R0 <: Has[_]]: ZStream.ProvideSomeLayer[R0, R, E, O] =
    new ZStream.ProvideSomeLayer[R0, R, E, O](self)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest
   */
  final def refineOrDie[E1](
    pf: PartialFunction[E, E1]
  )(implicit ev1: E <:< Throwable, ev2: CanFail[E]): ZStream[R, E1, O] =
    ZStream(self.process.map(_.mapError {
      case None                         => None
      case Some(e) if pf.isDefinedAt(e) => Some(pf.apply(e))
    }))

  /**
   * Keeps some of the errors, and terminates the fiber with the rest, using
   * the specified function to convert the `E` into a `Throwable`.
   */
  final def refineOrDieWith[E1](
    pf: PartialFunction[E, E1]
  )(f: E => Throwable)(implicit ev: CanFail[E]): ZStream[R, E1, O] =
    ZStream(self.process.map(_.mapError {
      case None                         => None
      case Some(e) if pf.isDefinedAt(e) => Some(pf.apply(e))
      case Some(e)                      => throw f(e)
    }))

  /**
   * Repeats the entire stream using the specified schedule. The stream will execute normally,
   * and then repeat again according to the provided schedule.
   */
  final def repeat[R1 <: R, B](schedule: Schedule[R1, Any, B]): ZStream[R1, E, O] =
    repeatEither(schedule) collect { case Right(a) => a }

  /**
   * Repeats the entire stream using the specified schedule. The stream will execute normally,
   * and then repeat again according to the provided schedule. The schedule output will be emitted at
   * the end of each repetition.
   */
  final def repeatEither[R1 <: R, B](schedule: Schedule[R1, Any, B]): ZStream[R1, E, Either[B, O]] =
    repeatWith(schedule)(Right(_), Left(_))

  /**
   * Repeats the entire stream using the specified schedule. The stream will execute normally,
   * and then repeat again according to the provided schedule. The schedule output will be emitted at
   * the end of each repetition and can be unified with the stream elements using the provided functions.
   */
  final def repeatWith[R1 <: R, B, C](
    schedule: Schedule[R1, Any, B]
  )(f: O => C, g: B => C): ZStream[R1, E, C] =
    ZStream[R1, E, C] {
      for {
        scheduleInit  <- schedule.initial.toManaged_
        schedStateRef <- Ref.make(scheduleInit).toManaged_
        switchPull    <- ZManaged.switchable[R1, Nothing, ZIO[R1, Option[E], Chunk[C]]]
        currPull      <- switchPull(self.map(f).process).flatMap(as => Ref.make(as)).toManaged_
        doneRef       <- Ref.make(false).toManaged_
        pull = {
          def go: ZIO[R1, Option[E], Chunk[C]] =
            doneRef.get.flatMap { done =>
              if (done) Pull.end
              else
                currPull.get.flatten.foldM(
                  {
                    case e @ Some(_) => ZIO.fail(e)
                    case None =>
                      schedStateRef.get
                        .flatMap(schedule.update((), _))
                        .foldM(
                          _ => doneRef.set(true) *> Pull.end,
                          state =>
                            switchPull((self.map(f) ++ ZStream.succeed(g(schedule.extract((), state)))).process)
                              .tap(currPull.set(_)) *> schedStateRef.set(state) *> go
                        )
                  },
                  ZIO.succeedNow
                )
            }
          go
        }
      } yield pull
    }

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  def run[R1 <: R, E1 >: E, B](sink: ZSink[R1, E1, O, B]): ZIO[R1, E1, B] =
    (process <*> sink.push).use {
      case (pull, push) =>
        def go: ZIO[R1, E1, B] = pull.foldCauseM(
          Cause
            .sequenceCauseOption(_)
            .fold(
              push(None).foldCauseM(
                Cause.sequenceCauseEither(_).fold(IO.halt(_), ZIO.succeedNow),
                _ => IO.dieMessage("empty stream / empty sinks")
              )
            )(IO.halt(_)),
          os => push(Some(os)).foldCauseM(Cause.sequenceCauseEither(_).fold(IO.halt(_), ZIO.succeedNow), _ => go)
        )

        go
    }

  /**
   * Runs the stream and collects all of its elements to a list.
   */
  def runCollect: ZIO[R, E, List[O]] = run(ZSink.collectAll[O])

  /**
   * Runs the stream and emits the number of elements processed
   *
   * Equivalent to `run(ZSink.count)`
   */
  final def runCount: ZIO[R, E, Long] = self.run(ZSink.count)

  /**
   * Runs the stream only for its effects. The emitted elements are discarded.
   */
  def runDrain: ZIO[R, E, Unit] =
    foreach(_ => ZIO.unit)

  /**
   * Runs the stream to completion and yields the first value emitted by it,
   * discarding the rest of the elements.
   */
  def runHead: ZIO[R, E, Option[O]] =
    // TODO: rewrite as a sink
    Ref.make[Option[O]](None).flatMap { ref =>
      foreach(a =>
        ref.update {
          case None        => Some(a)
          case s @ Some(_) => s
        }
      ) *>
        ref.get
    }

  /**
   * Runs the stream to completion and yields the last value emitted by it,
   * discarding the rest of the elements.
   */
  def runLast: ZIO[R, E, Option[O]] =
    // TODO: rewrite as a sink
    Ref.make[Option[O]](None).flatMap { ref =>
      foreach(o => ref.set(Some(o))) *>
        ref.get
    }

  /**
   * Runs the stream to a sink which sums elements, provided they are Numeric.
   *
   * Equivalent to `run(Sink.sum[A])`
   */
  final def runSum[O1 >: O](implicit ev: Numeric[O1]): ZIO[R, E, O1] = run(ZSink.sum[O1])

  /**
   * Schedules the output of the stream using the provided `schedule`.
   */
  final def schedule[R1 <: R](schedule: Schedule[R1, O, Any]): ZStream[R1, E, O] =
    scheduleEither(schedule).collect { case Right(a) => a }

  /**
   * Schedules the output of the stream using the provided `schedule` and emits its output at
   * the end (if `schedule` is finite).
   */
  final def scheduleEither[R1 <: R, E1 >: E, B](
    schedule: Schedule[R1, O, B]
  ): ZStream[R1, E1, Either[B, O]] =
    scheduleWith(schedule)(Right.apply, Left.apply)

  /**
   * Repeats each element of the stream using the provided `schedule`, additionally emitting the schedule's output
   * each time a schedule is completed.
   * Repeats are done in addition to the first execution, so that `scheduleElements(Schedule.once)` means "emit element
   * and if not short circuited, repeat element once".
   */
  final def scheduleElements[R1 <: R](schedule: Schedule[R1, O, Any]): ZStream[R1, E, O] =
    scheduleElementsEither(schedule).collect { case Right(a) => a }

  /**
   * Repeats each element of the stream using the provided `schedule`, additionally emitting the schedule's output
   * each time a schedule is completed.
   * Repeats are done in addition to the first execution, so that `scheduleElements(Schedule.once)` means "emit element
   * and if not short circuited, repeat element once".
   */
  final def scheduleElementsEither[R1 <: R, E1 >: E, B](
    schedule: Schedule[R1, O, B]
  ): ZStream[R1, E1, Either[B, O]] =
    scheduleElementsWith(schedule)(Right.apply, Left.apply)

  /**
   * Repeats each element of the stream using the provided schedule, additionally emitting the schedule's output
   * each time a schedule is completed.
   * Repeats are done in addition to the first execution, so that `scheduleElements(Schedule.once)` means "emit element
   * and if not short circuited, repeat element once".
   * Uses the provided functions to align the stream and schedule outputs on a common type.
   */
  final def scheduleElementsWith[R1 <: R, E1 >: E, B, C](
    schedule: Schedule[R1, O, B]
  )(f: O => C, g: B => C): ZStream[R1, E1, C] =
    ZStream {
      for {
        as    <- self.process.mapM(BufferedPull.make(_))
        state <- Ref.make[Option[(O, schedule.State)]](None).toManaged_
        pull = {
          def go: ZIO[R1, Option[E1], Chunk[C]] = state.get.flatMap {
            case None =>
              for {
                a    <- as.pullElement
                init <- schedule.initial
                _    <- state.set(Some(a -> init))
              } yield Chunk.single(f(a))

            case Some((a, scheduleState)) =>
              schedule
                .update(a, scheduleState)
                .foldM(
                  _ => state.set(None).as(Chunk.single(g(schedule.extract(a, scheduleState)))),
                  s => state.set(Some(a -> s)).as(Chunk.single(f(a)))
                )
          }

          go
        }
      } yield pull
    }

  /**
   * Schedules the output of the stream using the provided `schedule` and emits its output at
   * the end (if `schedule` is finite).
   * Uses the provided function to align the stream and schedule outputs on the same type.
   */
  final def scheduleWith[R1 <: R, E1 >: E, B, C](
    schedule: Schedule[R1, O, B]
  )(f: O => C, g: B => C): ZStream[R1, E1, C] =
    ZStream[R1, E1, C] {
      for {
        as    <- self.process.mapM(BufferedPull.make(_))
        init  <- schedule.initial.toManaged_
        state <- Ref.make[(schedule.State, Option[() => B])]((init, None)).toManaged_
        pull = state.get.flatMap {
          case (sched0, finish0) =>
            // Before pulling from the stream, we need to check whether the previous
            // action ended the schedule, in which case we must emit its final output
            finish0 match {
              case None =>
                for {
                  a <- as.pullElement.optional.mapError(Some(_))
                  c <- a match {
                        // There's a value emitted by the underlying stream, we emit it
                        // and check whether the schedule ends; in that case, we record
                        // its final state, to be emitted during the next pull
                        case Some(a) =>
                          schedule
                            .update(a, sched0)
                            .foldM(
                              _ =>
                                schedule.initial
                                  .flatMap(s1 => state.set((s1, Some(() => schedule.extract(a, sched0))))),
                              s => state.set((s, None))
                            )
                            .as(f(a))

                        // The stream ends when both the underlying stream ends and the final
                        // schedule value has been emitted
                        case None => Pull.end
                      }
                } yield Chunk.single(c)
              case Some(b) => state.set((sched0, None)) *> Pull.emit(g(b()))
            }
        }
      } yield pull
    }

  /**
   * Takes the specified number of elements from this stream.
   */
  def take(n: Long): ZStream[R, E, O] =
    if (n <= 0) ZStream.empty
    else
      ZStream {
        for {
          chunks     <- self.process
          counterRef <- Ref.make(0L).toManaged_
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
   * Takes all elements of the stream until the specified predicate evaluates
   * to `true`.
   */
  def takeUntil(pred: O => Boolean): ZStream[R, E, O] =
    ZStream {
      for {
        chunks        <- self.process
        keepTakingRef <- Ref.make(true).toManaged_
        pull = keepTakingRef.get.flatMap { keepTaking =>
          if (!keepTaking) Pull.end
          else
            for {
              chunk <- chunks
              taken = chunk.takeWhile(!pred(_))
              last  = chunk.drop(taken.length).take(1)
              _     <- keepTakingRef.set(false).when(last.nonEmpty)
            } yield taken ++ last
        }
      } yield pull
    }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def takeWhile(pred: O => Boolean): ZStream[R, E, O] =
    ZStream {
      for {
        chunks  <- self.process
        doneRef <- Ref.make(false).toManaged_
        pull = doneRef.get.flatMap {
          if (_) Pull.end
          else
            for {
              chunk <- chunks
              taken = chunk.takeWhile(pred)
              _     <- doneRef.set(true).when(taken.length < chunk.length || chunk.length == 0)
              _     <- Pull.end.when(taken.length == 0)
            } yield taken
        }
      } yield pull
    }

  /**
   * Adds an effect to consumption of every element of the stream.
   */
  final def tap[R1 <: R, E1 >: E](f0: O => ZIO[R1, E1, Any]): ZStream[R1, E1, O] =
    ZStream(self.process.map(_.tap(_.mapM_(f0).mapError(Some(_)))))

  /**
   * Throttles the chunks of this stream according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. Chunks that do not meet the bandwidth constraints are dropped.
   * The weight of each chunk is determined by the `costFn` function.
   */
  final def throttleEnforce(units: Long, duration: Duration, burst: Long = 0)(
    costFn: Chunk[O] => Long
  ): ZStream[R with Clock, E, O] =
    throttleEnforceM(units, duration, burst)(os => UIO.succeedNow(costFn(os)))

  /**
   * Throttles the chunks of this stream according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. Chunks that do not meet the bandwidth constraints are dropped.
   * The weight of each chunk is determined by the `costFn` effectful function.
   */
  final def throttleEnforceM[R1 <: R, E1 >: E](units: Long, duration: Duration, burst: Long = 0)(
    costFn: Chunk[O] => ZIO[R1, E1, Long]
  ): ZStream[R1 with Clock, E1, O] =
    ZStream {
      for {
        chunks      <- self.process
        currentTime <- clock.nanoTime.toManaged_
        bucket      <- Ref.make((units, currentTime)).toManaged_
        pull = {
          def go: ZIO[R1 with Clock, Option[E1], Chunk[O]] =
            chunks.flatMap { chunk =>
              (costFn(chunk).mapError(Some(_)) <*> clock.nanoTime) flatMap {
                case (weight, current) =>
                  bucket.modify {
                    case (tokens, timestamp) =>
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
  ): ZStream[R with Clock, E, O] =
    throttleShapeM(units, duration, burst)(os => UIO.succeedNow(costFn(os)))

  /**
   * Delays the chunks of this stream according to the given bandwidth parameters using the token bucket
   * algorithm. Allows for burst in the processing of elements by allowing the token bucket to accumulate
   * tokens up to a `units + burst` threshold. The weight of each chunk is determined by the `costFn`
   * effectful function.
   */
  final def throttleShapeM[R1 <: R, E1 >: E](units: Long, duration: Duration, burst: Long = 0)(
    costFn: Chunk[O] => ZIO[R1, E1, Long]
  ): ZStream[R1 with Clock, E1, O] =
    ZStream {
      for {
        chunks      <- self.process
        currentTime <- clock.nanoTime.toManaged_
        bucket      <- Ref.make((units, currentTime)).toManaged_
        pull = for {
          chunk   <- chunks
          weight  <- costFn(chunk).mapError(Some(_))
          current <- clock.nanoTime
          delay <- bucket.modify {
                    case (tokens, timestamp) =>
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
          _ <- clock.sleep(delay).when(delay > Duration.Zero)
        } yield chunk
      } yield pull
    }

  /**
   * Interrupts the stream if it does not produce a value after d duration.
   */
  final def timeout(d: Duration): ZStream[R with Clock, E, O] =
    ZStream[R with Clock, E, O] {
      self.process.map { next =>
        next.timeout(d).flatMap {
          case Some(a) => ZIO.succeedNow(a)
          case None    => ZIO.interrupt
        }
      }
    }

  /**
   * Converts this stream of bytes into a `java.io.InputStream` wrapped in a [[ZManaged]].
   * The returned input stream will only be valid within the scope of the ZManaged.
   */
  def toInputStream(implicit ev0: E <:< Throwable, ev1: O <:< Byte): ZManaged[R, E, java.io.InputStream] = {
    val (_, _) = (ev0, ev1)

    for {
      runtime <- ZIO.runtime[R].toManaged_
      pull    <- process
      javaStream = new java.io.InputStream {
        val capturedPull           = pull.asInstanceOf[ZIO[R, Option[Throwable], Chunk[Byte]]]
        var done                   = false
        var nextIndex: Int         = -1
        var currChunk: Chunk[Byte] = null

        override def read(): Int =
          if (done) -1
          else {
            if ((currChunk ne null) && nextIndex < currChunk.size) {
              val result = currChunk(nextIndex)
              nextIndex += 1
              result.toInt
            } else {
              runtime.unsafeRunSync(capturedPull) match {
                case Exit.Failure(cause) =>
                  cause.failureOrCause match {
                    case Left(None) =>
                      done = true
                      -1
                    case Left(Some(throwable)) =>
                      throw throwable
                    case Right(otherCause) =>
                      throw FiberFailure(otherCause)
                  }

                case Exit.Success(chunk) =>
                  currChunk = chunk
                  nextIndex = 0
                  read()
              }
            }
          }
      }
    } yield javaStream
  }

  /**
   * Converts this stream into a `scala.collection.Iterator` wrapped in a [[ZManaged]].
   * The returned iterator will only be valid within the scope of the ZManaged.
   */
  def toIterator: ZManaged[R, Nothing, Iterator[Either[E, O]]] =
    for {
      pull    <- this.process.mapM(BufferedPull.make(_))
      runtime <- ZIO.runtime[R].toManaged_
    } yield {
      new Iterator[Either[E, O]] {

        var nextTake: Exit[Option[E], O] = null
        def unsafeTake(): Unit =
          nextTake = runtime.unsafeRunSync(pull.pullElement)

        def hasNext: Boolean = {
          if (nextTake == null) {
            unsafeTake()
          }

          nextTake match {
            case Exit.Failure(cause) =>
              cause.failureOrCause match {
                case Left(None) => false
                case _          => true
              }
            case _ => true
          }
        }

        def next(): Either[E, O] = {
          if (nextTake == null) {
            unsafeTake()
          }

          val take: Either[E, O] = nextTake match {
            case Exit.Failure(cause) =>
              cause.failureOrCause match {
                case Left(None)    => throw new NoSuchElementException("next on empty iterator")
                case Left(Some(e)) => Left(e)
                case Right(c)      => throw FiberFailure(c)
              }
            case Exit.Success(a) => Right(a)
          }

          nextTake = null
          take
        }
      }
    }

  /**
   * Converts the stream to a managed queue of chunks. After the managed queue is used,
   * the queue will never again produce values and should be discarded.
   */
  final def toQueue(capacity: Int = 2): ZManaged[R, Nothing, Dequeue[Exit[Option[E], Chunk[O]]]] =
    for {
      queue <- Queue.bounded[Exit[Option[E], Chunk[O]]](capacity).toManaged(_.shutdown)
      _     <- self.intoManaged(queue).fork
    } yield queue

  /**
   * Converts the stream into an unbounded managed queue. After the managed queue
   * is used, the queue will never again produce values and should be discarded.
   */
  final def toQueueUnbounded: ZManaged[R, Nothing, Dequeue[Exit[Option[E], Chunk[O]]]] =
    for {
      queue <- Queue.unbounded[Exit[Option[E], Chunk[O]]].toManaged(_.shutdown)
      _     <- self.intoManaged(queue).fork
    } yield queue

  /**
   * Applies the transducer to the stream and emits its outputs.
   */
  def transduce[R1 <: R, E1 >: E, O3](transducer: ZTransducer[R1, E1, O, O3]): ZStream[R1, E1, O3] =
    aggregate(transducer)

  /**
   * Threads the stream through the transformation function `f`.
   */
  final def via[R2, E2, O2](f: ZStream[R, E, O] => ZStream[R2, E2, O2]): ZStream[R2, E2, O2] = f(self)

  /**
   * Zips this stream with another point-wise, but keeps only the outputs of this stream.
   *
   * The new stream will end when one of the sides ends.
   */
  def zipLeft[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O] = zipWith(that)((o, _) => o)

  /**
   * Zips this stream with another point-wise, but keeps only the outputs of the other stream.
   *
   * The new stream will end when one of the sides ends.
   */
  def zipRight[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O2] = zipWith(that)((_, o2) => o2)

  /**
   * Zips this stream with another point-wise and emits tuples of elements from both streams.
   *
   * The new stream will end when one of the sides ends.
   */
  def zip[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, (O, O2)] = zipWith(that)((_, _))

  /**
   * Zips this stream with another point-wise, creating a new stream of pairs of elements
   * from both sides.
   *
   * The defaults `defaultLeft` and `defaultRight` will be used if the streams have different lengths
   * and one of the streams has ended before the other.
   */
  def zipAll[R1 <: R, E1 >: E, O1 >: O, O2](
    that: ZStream[R1, E1, O2]
  )(defaultLeft: O1, defaultRight: O2): ZStream[R1, E1, (O1, O2)] =
    zipAllWith(that)((_, defaultRight), (defaultLeft, _))((_, _))

  /**
   * Zips this stream with another point-wise, and keeps only elements from this stream.
   *
   * The provided default value will be used if the other stream ends before this one.
   */
  def zipAllLeft[R1 <: R, E1 >: E, O1 >: O, O2](that: ZStream[R1, E1, O2])(default: O1): ZStream[R1, E1, O1] =
    zipAllWith(that)(identity, _ => default)((o, _) => o)

  /**
   * Zips this stream with another point-wise, and keeps only elements from the other stream.
   *
   * The provided default value will be used if this stream ends before the other one.
   */
  def zipAllRight[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(default: O2): ZStream[R1, E1, O2] =
    zipAllWith(that)(_ => default, identity)((_, o2) => o2)

  /**
   * Zips this stream with another point-wise. The provided functions will be used to create elements
   * for the composed stream.

   * The functions `left` and `right` will be used if the streams have different lengths
   * and one of the streams has ended before the other.
   */
  def zipAllWith[R1 <: R, E1 >: E, O2, O3](
    that: ZStream[R1, E1, O2]
  )(left: O => O3, right: O2 => O3)(both: (O, O2) => O3): ZStream[R1, E1, O3] = {
    sealed trait State[+O, +O2]
    case class Running[O, O2](excessL: Chunk[O], excessR: Chunk[O2])   extends State[O, O2]
    case class LeftDone[O, O2](excessL: Chunk[O], excessR: Chunk[O2])  extends State[O, O2]
    case class RightDone[O, O2](excessL: Chunk[O], excessR: Chunk[O2]) extends State[O, O2]
    case object End                                                    extends State[Nothing, Nothing]

    def zipSides(cl: Chunk[O], cr: Chunk[O2], bothDone: Boolean) =
      if (cl.size > cr.size) {
        if (bothDone) (cl.take(cr.size).zipWith(cr)(both) ++ cl.drop(cr.size).map(left), Chunk(), Chunk())
        else (cl.take(cr.size).zipWith(cr)(both), cl.drop(cr.size), Chunk())
      } else if (cl.size == cr.size) (cl.zipWith(cr)(both), Chunk(), Chunk())
      else {
        if (bothDone) (cl.zipWith(cr.take(cl.size))(both) ++ cr.drop(cl.size).map(right), Chunk(), Chunk())
        else (cl.zipWith(cr.take(cl.size))(both), Chunk(), cr.drop(cl.size))
      }

    def handleSuccess(maybeO: Option[Chunk[O]], maybeO2: Option[Chunk[O2]], excessL: Chunk[O], excessR: Chunk[O2]) =
      (maybeO, maybeO2) match {
        case (Some(o), Some(o2)) =>
          val (emit, el, er) = zipSides(excessL ++ o, excessR ++ o2, bothDone = false)
          Exit.succeed(emit -> Running(el, er))

        case (None, Some(o2)) =>
          val (emit, el, er) = zipSides(excessL, excessR ++ o2, bothDone = false)
          Exit.succeed(emit -> LeftDone(el, er))

        case (Some(o), None) =>
          val (emit, el, er) = zipSides(excessL ++ o, excessR, bothDone = false)
          Exit.succeed(emit -> RightDone(el, er))

        case (None, None) =>
          val (emit, _, _) = zipSides(excessL, excessR, bothDone = true)
          Exit.succeed(emit -> End)
      }

    combineChunks(that)(Running(Chunk(), Chunk()): State[O, O2]) {
      case (Running(excessL, excessR), pullL, pullR) =>
        pullL.optional
          .zipWithPar(pullR.optional)(handleSuccess(_, _, excessL, excessR))
          .catchAllCause(e => UIO.succeedNow(Exit.halt(e.map(Some(_)))))

      case (LeftDone(excessL, excessR), _, pullR) =>
        pullR.optional
          .map(handleSuccess(None, _, excessL, excessR))
          .catchAllCause(e => UIO.succeedNow(Exit.halt(e.map(Some(_)))))

      case (RightDone(excessL, excessR), pullL, _) =>
        pullL.optional
          .map(handleSuccess(_, None, excessL, excessR))
          .catchAllCause(e => UIO.succeedNow(Exit.halt(e.map(Some(_)))))

      case (End, _, _) => UIO.succeedNow(Exit.fail(None))
    }
  }

  /**
   * Zips this stream with another point-wise and applies the function to the paired elements.
   *
   * The new stream will end when one of the sides ends.
   */
  def zipWith[R1 <: R, E1 >: E, O2, O3](that: ZStream[R1, E1, O2])(f: (O, O2) => O3): ZStream[R1, E1, O3] = {
    sealed trait State[+O, +O2]
    case class Running[O, O2](excessL: Chunk[O], excessR: Chunk[O2]) extends State[O, O2]
    case object End                                                  extends State[Nothing, Nothing]

    def zipSides(cl: Chunk[O], cr: Chunk[O2]) =
      if (cl.size > cr.size) (cl.take(cr.size).zipWith(cr)(f), cl.drop(cr.size), Chunk())
      else if (cl.size == cr.size) (cl.zipWith(cr)(f), Chunk(), Chunk())
      else (cl.zipWith(cr.take(cl.size))(f), Chunk(), cr.drop(cl.size))

    combineChunks(that)(Running(Chunk[O](), Chunk[O2]()): State[O, O2]) {
      case (Running(excessL, excessR), pullL, pullR) =>
        pullL.optional
          .zipWithPar(pullR.optional) {
            case (Some(o), Some(o2)) =>
              val (emit, el, er) = zipSides(excessL ++ o, excessR ++ o2)
              Exit.succeed(emit -> Running(el, er))
            case (Some(o), None) =>
              val (emit, _, _) = zipSides(excessL ++ o, excessR)
              Exit.succeed(emit -> End)
            case (None, Some(o2)) =>
              val (emit, _, _) = zipSides(excessL, excessR ++ o2)
              Exit.succeed(emit -> End)
            case (None, None) =>
              Exit.fail(None)
          }
          .catchAllCause(e => UIO.succeedNow(Exit.halt(e.map(Some(_)))))
      case (End, _, _) => UIO.succeedNow(Exit.fail(None))
    }
  }

  /**
   * Zips this stream together with the index of elements.
   */
  final def zipWithIndex: ZStream[R, E, (O, Long)] =
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
  )(f: (O, O2) => O3): ZStream[R1, E1, O3] = {
    def pullNonEmpty[R, E, O](pull: ZIO[R, Option[E], Chunk[O]]): ZIO[R, Option[E], Chunk[O]] =
      pull.flatMap(chunk => if (chunk.isEmpty) pull else UIO.succeedNow(chunk))

    ZStream {
      for {
        left  <- self.process.map(pullNonEmpty(_))
        right <- that.process.map(pullNonEmpty(_))
        pull <- (ZStream.fromEffectOption {
                 left.raceWith(right)(
                   (leftDone, rightFiber) => ZIO.done(leftDone).zipWith(rightFiber.join)((_, _, true)),
                   (rightDone, leftFiber) => ZIO.done(rightDone).zipWith(leftFiber.join)((r, l) => (l, r, false))
                 )
               }.flatMap {
                 case (l, r, leftFirst) =>
                   ZStream.fromEffect(Ref.make(l(l.size - 1)) <*> Ref.make(r(r.size - 1))).flatMap {
                     case (latestLeft, latestRight) =>
                       ZStream.fromChunk(
                         if (leftFirst) r.map(f(l(l.size - 1), _))
                         else l.map(f(_, r(r.size - 1)))
                       ) ++
                         ZStream
                           .repeatEffectOption(
                             left.tap(chunk => latestLeft.set(chunk(chunk.size - 1))) <*> latestRight.get
                           )
                           .mergeWith(
                             ZStream.repeatEffectOption(
                               right.tap(chunk => latestRight.set(chunk(chunk.size - 1))) <*> latestLeft.get
                             )
                           )(
                             {
                               case (leftChunk, rightLatest) => leftChunk.map(f(_, rightLatest))
                             }, {
                               case (rightChunk, leftLatest) => rightChunk.map(f(leftLatest, _))
                             }
                           )
                           .flatMap(ZStream.fromChunk(_))
                   }
               }).process

      } yield pull
    }
  }
}

object ZStream extends ZStreamPlatformSpecificConstructors {

  /**
   * The default chunk size used by the various combinators and constructors of [[ZStream]].
   */
  final val DefaultChunkSize = 4096

  /**
   * Submerges the error case of an `Either` into the `ZStream`.
   */
  def absolve[R, E, O](xs: ZStream[R, E, Either[E, O]]): ZStream[R, E, O] =
    xs.flatMap(_.fold(fail(_), succeed(_)))

  /**
   * Accesses the environment of the stream.
   */
  def access[R]: AccessPartiallyApplied[R] =
    new AccessPartiallyApplied[R]

  /**
   * Accesses the environment of the stream in the context of an effect.
   */
  def accessM[R]: AccessMPartiallyApplied[R] =
    new AccessMPartiallyApplied[R]

  /**
   * Accesses the environment of the stream in the context of a stream.
   */
  def accessStream[R]: AccessStreamPartiallyApplied[R] =
    new AccessStreamPartiallyApplied[R]

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
  def apply[A](as: A*): ZStream[Any, Nothing, A] = fromIterable(as)

  /**
   * Creates a stream from a single value that will get cleaned up after the
   * stream is consumed
   */
  def bracket[R, E, A](acquire: ZIO[R, E, A])(release: A => ZIO[R, Nothing, Any]): ZStream[R, E, A] =
    managed(ZManaged.make(acquire)(release))

  /**
   * Creates a stream from a single value that will get cleaned up after the
   * stream is consumed
   */
  def bracketExit[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => ZIO[R, Nothing, Any]): ZStream[R, E, A] =
    managed(ZManaged.makeExit(acquire)(release))

  /**
   * Composes the specified streams to create a cartesian product of elements
   * with a specified function. Subsequent streams would be run multiple times,
   * for every combination of elements in the prior streams.
   *
   * See also [[ZStream#zipN[R,E,A,B,C]*]] for the more common point-wise variant.
   */
  def crossN[R, E, A, B, C](zStream1: ZStream[R, E, A], zStream2: ZStream[R, E, B])(
    f: (A, B) => C
  ): ZStream[R, E, C] =
    zStream1.crossWith(zStream2)(f)

  /**
   * Composes the specified streams to create a cartesian product of elements
   * with a specified function. Subsequent stream would be run multiple times,
   * for every combination of elements in the prior streams.
   *
   * See also [[ZStream#zipN[R,E,A,B,C,D]*]] for the more common point-wise variant.
   */
  def crossN[R, E, A, B, C, D](
    zStream1: ZStream[R, E, A],
    zStream2: ZStream[R, E, B],
    zStream3: ZStream[R, E, C]
  )(
    f: (A, B, C) => D
  ): ZStream[R, E, D] =
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
  def crossN[R, E, A, B, C, D, F](
    zStream1: ZStream[R, E, A],
    zStream2: ZStream[R, E, B],
    zStream3: ZStream[R, E, C],
    zStream4: ZStream[R, E, D]
  )(
    f: (A, B, C, D) => F
  ): ZStream[R, E, F] =
    for {
      a <- zStream1
      b <- zStream2
      c <- zStream3
      d <- zStream4
    } yield f(a, b, c, d)

  /**
   * Concatenates all of the streams in the chunk to one stream.
   */
  def concatAll[R, E, O](streams: Chunk[ZStream[R, E, O]]): ZStream[R, E, O] =
    ZStream {
      val chunkSize = streams.size

      for {
        currIndex    <- Ref.make(0).toManaged_
        currStream   <- Ref.make[ZIO[R, Option[E], Chunk[O]]](Pull.end).toManaged_
        switchStream <- ZManaged.switchable[R, Nothing, ZIO[R, Option[E], Chunk[O]]]
        pull = {
          def go: ZIO[R, Option[E], Chunk[O]] =
            currStream.get.flatten.catchAllCause {
              Cause.sequenceCauseOption(_) match {
                case Some(e) => Pull.halt(e)
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
  def die(ex: => Throwable): ZStream[Any, Nothing, Nothing] =
    fromEffect(ZIO.die(ex))

  /**
   * The stream that dies with an exception described by `msg`.
   */
  def dieMessage(msg: => String): ZStream[Any, Nothing, Nothing] =
    fromEffect(ZIO.dieMessage(msg))

  /**
   * The stream that ends with the [[zio.Exit]] value `exit`.
   */
  def done[E, A](exit: Exit[E, A]): ZStream[Any, E, A] =
    fromEffect(ZIO.done(exit))

  /**
   * The empty stream
   */
  val empty: ZStream[Any, Nothing, Nothing] =
    ZStream(ZManaged.succeedNow(Pull.end))

  /**
   * Accesses the whole environment of the stream.
   */
  def environment[R]: ZStream[R, Nothing, R] =
    fromEffect(ZIO.environment[R])

  /**
   * The stream that always fails with the `error`
   */
  def fail[E](error: => E): ZStream[Any, E, Nothing] =
    fromEffect(ZIO.fail(error))

  /**
   * Creates an empty stream that never fails and executes the finalizer when it ends.
   */
  def finalizer[R](finalizer: ZIO[R, Nothing, Any]): ZStream[R, Nothing, Nothing] =
    ZStream {
      for {
        finalizerRef <- ZManaged.finalizerRef[R](_ => UIO.unit)
        pull         = (finalizerRef.add(_ => finalizer) *> Pull.end).uninterruptible
      } yield pull
    }

  /**
   * Creates a stream from a [[zio.Chunk]] of values
   *
   * @tparam A the value type
   * @param c a chunk of values
   * @return a finite stream of values
   */
  def fromChunk[O](c: => Chunk[O]): ZStream[Any, Nothing, O] =
    ZStream {
      for {
        doneRef <- Ref.make(false).toManaged_
        pull = doneRef.modify { done =>
          if (done || c.isEmpty) Pull.end -> true
          else ZIO.succeedNow(c)          -> true
        }.flatten
      } yield pull
    }

  /**
   * Creates a stream from a [[zio.ZQueue]] of values
   */
  def fromChunkQueue[R, E, O](queue: ZQueue[Nothing, R, Any, E, Nothing, Chunk[O]]): ZStream[R, E, O] =
    repeatEffectChunkOption {
      queue.take
        .catchAllCause(c =>
          queue.isShutdown.flatMap { down =>
            if (down && c.interrupted) Pull.end
            else Pull.halt(c)
          }
        )
    }

  /**
   * Creates a stream from a [[zio.ZQueue]] of values. The queue will be shutdown once the stream is closed.
   */
  def fromChunkQueueWithShutdown[R, E, O](queue: ZQueue[Nothing, R, Any, E, Nothing, Chunk[O]]): ZStream[R, E, O] =
    fromChunkQueue(queue).ensuringFirst(queue.shutdown)

  /**
   * Creates a stream from an arbitrary number of chunks.
   */
  def fromChunks[O](cs: Chunk[O]*): ZStream[Any, Nothing, O] =
    fromIterable(cs).flatMap(fromChunk(_))

  /**
   * Creates a stream from an effect producing a value of type `A`
   */
  def fromEffect[R, E, A](fa: ZIO[R, E, A]): ZStream[R, E, A] =
    fromEffectOption(fa.mapError(Some(_)))

  /**
   * Creates a stream from an effect producing a value of type `A` or an empty Stream
   */
  def fromEffectOption[R, E, A](fa: ZIO[R, Option[E], A]): ZStream[R, E, A] =
    ZStream {
      for {
        doneRef <- Ref.make(false).toManaged_
        pull = doneRef.modify {
          if (_) Pull.end              -> true
          else fa.map(Chunk.single(_)) -> true
        }.flatten
      } yield pull
    }

  /**
   * Creates a stream from an iterable collection of values
   */
  def fromIterable[O](as: => Iterable[O]): ZStream[Any, Nothing, O] =
    fromChunk(Chunk.fromIterable(as))

  /**
   * Creates a stream from an effect producing a value of type `Iterable[A]`
   */
  def fromIterableM[R, E, O](iterable: ZIO[R, E, Iterable[O]]): ZStream[R, E, O] =
    fromEffect(iterable).mapConcat(identity)

  def fromIterator[A](iterator: => Iterator[A]): ZStream[Any, Throwable, A] = {
    object StreamEnd extends Throwable

    ZStream.fromEffect(Task(iterator) <*> ZIO.runtime[Any]).flatMap {
      case (it, rt) =>
        ZStream.repeatEffectOption {
          Task {
            val hasNext: Boolean =
              try it.hasNext
              catch {
                case e: Throwable if !rt.platform.fatal(e) =>
                  throw e
              }

            if (hasNext) {
              try it.next()
              catch {
                case e: Throwable if !rt.platform.fatal(e) =>
                  throw e
              }
            } else throw StreamEnd
          }.mapError {
            case StreamEnd => None
            case e         => Some(e)
          }
        }
    }
  }

  /**
   * Creates a stream from an iterator that may potentially throw exceptions
   */
  def fromIteratorEffect[R, A](
    iterator: ZIO[R, Throwable, Iterator[A]]
  ): ZStream[R, Throwable, A] =
    fromEffect(iterator).flatMap(fromIterator(_))

  /**
   * Creates a stream from a managed iterator
   */
  def fromIteratorManaged[R, A](iterator: ZManaged[R, Throwable, Iterator[A]]): ZStream[R, Throwable, A] =
    managed(iterator).flatMap(fromIterator(_))

  /**
   * Creates a stream from an iterator
   */
  def fromIteratorTotal[A](iterator: => Iterator[A]): ZStream[Any, Nothing, A] =
    ZStream {
      Managed.effectTotal(iterator).map { it =>
        IO.effectTotal {
          if (it.hasNext)
            Pull.emit(it.next)
          else
            Pull.end
        }.flatten
      }
    }

  /**
   * Creates a stream from a Java iterator that may throw exceptions
   */
  def fromJavaIterator[A](iterator: => ju.Iterator[A]): ZStream[Any, Throwable, A] =
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
  def fromJavaIteratorEffect[R, A](
    iterator: ZIO[R, Throwable, ju.Iterator[A]]
  ): ZStream[R, Throwable, A] =
    fromEffect(iterator).flatMap(fromJavaIterator(_))

  /**
   * Creates a stream from a managed iterator
   */
  def fromJavaIteratorManaged[R, A](iterator: ZManaged[R, Throwable, ju.Iterator[A]]): ZStream[R, Throwable, A] =
    managed(iterator).flatMap(fromJavaIterator(_))

  /**
   * Creates a stream from a Java iterator
   */
  def fromJavaIteratorTotal[A](iterator: => ju.Iterator[A]): ZStream[Any, Nothing, A] =
    fromIteratorTotal {
      val it = iterator // Scala 2.13 scala.collection.Iterator has `iterator` in local scope
      new Iterator[A] {
        def next(): A        = it.next
        def hasNext: Boolean = it.hasNext
      }
    }

  /**
   * Creates a stream from a [[zio.ZQueue]] of values
   */
  def fromQueue[R, E, O](queue: ZQueue[Nothing, R, Any, E, Nothing, O]): ZStream[R, E, O] =
    fromChunkQueue(queue.map(Chunk.single(_)))

  /**
   * Creates a stream from a [[zio.ZQueue]] of values. The queue will be shutdown once the stream is closed.
   */
  def fromQueueWithShutdown[R, E, O](queue: ZQueue[Nothing, R, Any, E, Nothing, O]): ZStream[R, E, O] =
    fromQueue(queue).ensuringFirst(queue.shutdown)

  /**
   * Creates a stream from a [[zio.Schedule]] that does not require any further
   * input. The stream will emit an element for each value output from the
   * schedule, continuing for as long as the schedule continues.
   */
  def fromSchedule[R, A](schedule: Schedule[R, Any, A]): ZStream[R, Nothing, A] =
    ZStream.fromEffect(schedule.initial).flatMap { s =>
      ZStream.succeed(schedule.extract((), s)) ++
        ZStream.unfoldM(s)(s => schedule.update((), s).map(s => (schedule.extract((), s), s)).option)
    }

  /**
   * Creates a stream from a [[zio.stm.TQueue]] of values.
   */
  def fromTQueue[A](queue: TQueue[A]): ZStream[Any, Nothing, A] =
    repeatEffectChunk(queue.take.map(Chunk.single(_)).commit)

  /**
   * The stream that always halts with `cause`.
   */
  def halt[E](cause: => Cause[E]): ZStream[Any, E, Nothing] =
    fromEffect(ZIO.halt(cause))

  /**
   * The infinite stream of iterative function application: a, f(a), f(f(a)), f(f(f(a))), ...
   */
  def iterate[A](a: A)(f: A => A): ZStream[Any, Nothing, A] =
    ZStream(Ref.make(a).toManaged_.map(_.getAndUpdate(f).map(Chunk.single(_))))

  /**
   * Creates a single-valued stream from a managed resource
   */
  def managed[R, E, A](managed: ZManaged[R, E, A]): ZStream[R, E, A] =
    ZStream {
      for {
        doneRef   <- Ref.make(false).toManaged_
        finalizer <- ZManaged.finalizerRef[R](_ => UIO.unit)
        pull = ZIO.uninterruptibleMask { restore =>
          doneRef.get.flatMap { done =>
            if (done) Pull.end
            else
              (for {
                reservation <- managed.reserve.onError(_ => doneRef.set(true))
                _           <- finalizer.add(reservation.release)
                _           <- doneRef.set(true)
                a           <- restore(reservation.acquire).onError(_ => doneRef.set(true))
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
  ): ZStream[R, E, O] =
    fromIterable(streams).flattenPar(n, outputBuffer)

  /**
   * Like [[mergeAll]], but runs all streams concurrently.
   */
  def mergeAllUnbounded[R, E, O](outputBuffer: Int = 16)(
    streams: ZStream[R, E, O]*
  ): ZStream[R, E, O] = mergeAll(Int.MaxValue, outputBuffer)(streams: _*)

  /**
   * The stream that never produces any value or fails with any error.
   */
  val never: ZStream[Any, Nothing, Nothing] =
    ZStream(ZManaged.succeedNow(UIO.never))

  /**
   * Like [[unfoldM]], but allows the emission of values to end one step further than
   * the unfolding of the state. This is useful for embedding paginated APIs,
   * hence the name.
   */
  def paginate[R, E, A, S](s: S)(f: S => (A, Option[S])): ZStream[Any, Nothing, A] =
    paginateM(s)(s => ZIO.succeedNow(f(s)))

  /**
   * Like [[unfoldM]], but allows the emission of values to end one step further than
   * the unfolding of the state. This is useful for embedding paginated APIs,
   * hence the name.
   */
  def paginateM[R, E, A, S](s: S)(f: S => ZIO[R, E, (A, Option[S])]): ZStream[R, E, A] =
    ZStream {
      for {
        ref <- Ref.make[Option[S]](Some(s)).toManaged_
      } yield ref.get.flatMap {
        case Some(s) => f(s).foldM(e => Pull.fail(e), { case (a, s) => ref.set(s).as(Chunk.single(a)) })
        case None    => Pull.end
      }
    }

  /**
   * Constructs a stream from a range of integers (lower bound included, upper bound not included)
   */
  def range(min: Int, max: Int): ZStream[Any, Nothing, Int] =
    iterate(min)(_ + 1).takeWhile(_ < max)

  /**
   * Creates a stream from an effect producing a value of type `A` which repeats forever.
   */
  def repeatEffect[R, E, A](fa: ZIO[R, E, A]): ZStream[R, E, A] =
    repeatEffectOption(fa.mapError(Some(_)))

  /**
   * Creates a stream from an effect producing values of type `A` until it fails with None.
   */
  def repeatEffectOption[R, E, A](fa: ZIO[R, Option[E], A]): ZStream[R, E, A] =
    repeatEffectChunkOption(fa.map(Chunk.single(_)))

  /**
   * Creates a stream from an effect producing chunks of `A` values which repeats forever.
   */
  def repeatEffectChunk[R, E, A](fa: ZIO[R, E, Chunk[A]]): ZStream[R, E, A] =
    repeatEffectChunkOption(fa.mapError(Some(_)))

  /**
   * Creates a stream from an effect producing chunks of `A` values until it fails with None.
   */
  def repeatEffectChunkOption[R, E, A](fa: ZIO[R, Option[E], Chunk[A]]): ZStream[R, E, A] =
    ZStream(ZManaged.succeedNow(fa))

  /**
   * Creates a stream from an effect producing a value of type `A` which repeats using the specified schedule
   */
  def repeatEffectWith[R, E, A](
    fa: ZIO[R, E, A],
    schedule: Schedule[R, Any, _]
  ): ZStream[R, E, A] =
    fromEffect(fa).repeat(schedule)

  /**
   * Accesses the specified service in the environment of the effect.
   */
  def service[A](implicit tagged: Tagged[A]): ZStream[Has[A], Nothing, A] =
    ZStream.access(_.get[A])

  /**
   * Accesses the specified services in the environment of the effect.
   */
  def services[A: Tagged, B: Tagged]: ZStream[Has[A] with Has[B], Nothing, (A, B)] =
    ZStream.access(r => (r.get[A], r.get[B]))

  /**
   * Accesses the specified services in the environment of the effect.
   */
  def services[A: Tagged, B: Tagged, C: Tagged]: ZStream[Has[A] with Has[B] with Has[C], Nothing, (A, B, C)] =
    ZStream.access(r => (r.get[A], r.get[B], r.get[C]))

  /**
   * Accesses the specified services in the environment of the effect.
   */
  def services[A: Tagged, B: Tagged, C: Tagged, D: Tagged]
    : ZStream[Has[A] with Has[B] with Has[C] with Has[D], Nothing, (A, B, C, D)] =
    ZStream.access(r => (r.get[A], r.get[B], r.get[C], r.get[D]))

  /**
   * Creates a single-valued pure stream
   */
  def succeed[A](a: => A): ZStream[Any, Nothing, A] =
    fromChunk(Chunk.single(a))

  /**
   * A stream that contains a single `Unit` value.
   */
  val unit: ZStream[Any, Nothing, Unit] =
    succeed(())

  /**
   * Creates a stream by peeling off the "layers" of a value of type `S`
   */
  def unfold[S, A](s: S)(f0: S => Option[(A, S)]): ZStream[Any, Nothing, A] =
    unfoldM(s)(s => ZIO.succeedNow(f0(s)))

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  def unfoldM[R, E, A, S](s: S)(f0: S => ZIO[R, E, Option[(A, S)]]): ZStream[R, E, A] =
    unfoldChunkM(s)(f0(_).map(_.map {
      case (a, s) => Chunk.single(a) -> s
    }))

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  def unfoldChunkM[R, E, A, S](s: S)(f0: S => ZIO[R, E, Option[(Chunk[A], S)]]): ZStream[R, E, A] =
    ZStream {
      for {
        done <- Ref.make(false).toManaged_
        ref  <- Ref.make(s).toManaged_
        pull = done.get.flatMap {
          if (_) Pull.end
          else {
            ref.get
              .flatMap(f0)
              .foldM(
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
   * Creates a stream produced from an effect
   */
  def unwrap[R, E, A](fa: ZIO[R, E, ZStream[R, E, A]]): ZStream[R, E, A] =
    fromEffect(fa).flatten

  /**
   * Creates a stream produced from a [[ZManaged]]
   */
  def unwrapManaged[R, E, A](fa: ZManaged[R, E, ZStream[R, E, A]]): ZStream[R, E, A] =
    managed(fa).flatten

  /**
   * Zips the specified streams together with the specified function.
   */
  def zipN[R, E, A, B, C](zStream1: ZStream[R, E, A], zStream2: ZStream[R, E, B])(
    f: (A, B) => C
  ): ZStream[R, E, C] =
    zStream1.zipWith(zStream2)(f)

  /**
   * Zips with specified streams together with the specified function.
   */
  def zipN[R, E, A, B, C, D](zStream1: ZStream[R, E, A], zStream2: ZStream[R, E, B], zStream3: ZStream[R, E, C])(
    f: (A, B, C) => D
  ): ZStream[R, E, D] =
    (zStream1 <&> zStream2 <&> zStream3).map {
      case ((a, b), c) => f(a, b, c)
    }

  /**
   * Returns an effect that executes the specified effects in parallel,
   * combining their results with the specified `f` function. If any effect
   * fails, then the other effects will be interrupted.
   */
  def zipN[R, E, A, B, C, D, F](
    zStream1: ZStream[R, E, A],
    zStream2: ZStream[R, E, B],
    zStream3: ZStream[R, E, C],
    zStream4: ZStream[R, E, D]
  )(f: (A, B, C, D) => F): ZStream[R, E, F] =
    (zStream1 <&> zStream2 <&> zStream3 <&> zStream4).map {
      case (((a, b), c), d) => f(a, b, c, d)
    }

  final class AccessPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](f: R => A): ZStream[R, Nothing, A] =
      ZStream.environment[R].map(f)
  }

  final class AccessMPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](f: R => ZIO[R, E, A]): ZStream[R, E, A] =
      ZStream.environment[R].mapM(f)
  }

  final class AccessStreamPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](f: R => ZStream[R, E, A]): ZStream[R, E, A] =
      ZStream.environment[R].flatMap(f)
  }

  /**
   * Representation of a grouped stream.
   * This allows to filter which groups will be processed.
   * Once this is applied all groups will be processed in parallel and the results will
   * be merged in arbitrary order.
   */
  final class GroupBy[-R, +E, +K, +V](
    private val grouped: ZStream[R, E, (K, Dequeue[Exit[Option[E], V]])],
    private val buffer: Int
  ) {

    /**
     * Only consider the first n groups found in the stream.
     */
    def first(n: Int): GroupBy[R, E, K, V] = {
      val g1 = grouped.zipWithIndex.filterM {
        case elem @ ((_, q), i) =>
          if (i < n) ZIO.succeedNow(elem).as(true)
          else q.shutdown.as(false)
      }.map(_._1)
      new GroupBy(g1, buffer)
    }

    /**
     * Filter the groups to be processed.
     */
    def filter(f: K => Boolean): GroupBy[R, E, K, V] = {
      val g1 = grouped.filterM {
        case elem @ (k, q) =>
          if (f(k)) ZIO.succeedNow(elem).as(true)
          else q.shutdown.as(false)
      }
      new GroupBy(g1, buffer)
    }

    /**
     * Run the function across all groups, collecting the results in an arbitrary order.
     */
    def apply[R1 <: R, E1 >: E, A](f: (K, ZStream[Any, E, V]) => ZStream[R1, E1, A]): ZStream[R1, E1, A] =
      grouped.flatMapPar[R1, E1, A](Int.MaxValue, buffer) {
        case (k, q) =>
          f(k, ZStream.fromQueueWithShutdown(q).collectWhileSuccess)
      }
  }

  final class ProvideSomeLayer[R0 <: Has[_], -R, +E, +A](private val self: ZStream[R, E, A]) extends AnyVal {
    def apply[E1 >: E, R1 <: Has[_]](
      layer: ZLayer[R0, E1, R1]
    )(implicit ev1: R0 with R1 <:< R, ev2: NeedsEnv[R], tagged: Tagged[R1]): ZStream[R0, E1, A] =
      self.provideLayer[E1, R0, R0 with R1](ZLayer.identity[R0] ++ layer)
  }

  private[zio] object Pull {
    def emit[A](a: A): IO[Nothing, Chunk[A]]                               = UIO(Chunk.single(a))
    def emit[A](as: Chunk[A]): IO[Nothing, Chunk[A]]                       = UIO(as)
    def fromDequeue[E, A](d: Dequeue[Take[E, A]]): IO[Option[E], Chunk[A]] = d.take.flatMap(IO.done(_))
    def fromTake[E, A](t: Take[E, A]): IO[Option[E], Chunk[A]]             = IO.done(t)
    def fail[E](e: E): IO[Option[E], Nothing]                              = IO.fail(Some(e))
    def halt[E](c: Cause[E]): IO[Option[E], Nothing]                       = IO.halt(c).mapError(Some(_))
    val end: IO[Option[Nothing], Nothing]                                  = IO.fail(None)
  }

  type Take[+E, +A] = Exit[Option[E], Chunk[A]]

  object Take {
    val End: Exit[Option[Nothing], Nothing] = Exit.fail(None)
  }

  private[zio] case class BufferedPull[R, E, A](
    upstream: ZIO[R, Option[E], Chunk[A]],
    done: Ref[Boolean],
    cursor: Ref[(Chunk[A], Int)]
  ) {
    def ifNotDone[R1, E1, A1](fa: ZIO[R1, Option[E1], A1]): ZIO[R1, Option[E1], A1] =
      done.get.flatMap(
        if (_) Pull.end
        else fa
      )

    def update: ZIO[R, Option[E], Unit] =
      ifNotDone {
        upstream.foldM(
          {
            case None    => done.set(true) *> Pull.end
            case Some(e) => Pull.fail(e)
          },
          chunk => cursor.set(chunk -> 0)
        )
      }

    def pullElement: ZIO[R, Option[E], A] =
      ifNotDone {
        cursor.modify {
          case (chunk, idx) =>
            if (idx >= chunk.size) (update *> pullElement, (Chunk.empty, 0))
            else (UIO.succeedNow(chunk(idx)), (chunk, idx + 1))
        }.flatten
      }

    def pullChunk: ZIO[R, Option[E], Chunk[A]] =
      ifNotDone {
        cursor.modify {
          case (chunk, idx) =>
            if (idx >= chunk.size) (update *> pullChunk, (Chunk.empty, 0))
            else (update.as(chunk.drop(idx)), (Chunk.empty, 0))
        }.flatten
      }

  }

  private[zio] object BufferedPull {
    def make[R, E, A](
      pull: ZIO[R, Option[E], Chunk[A]]
    ): ZIO[R, Nothing, BufferedPull[R, E, A]] =
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
    def offer(a: A): UIO[Unit] =
      Promise.make[Nothing, Unit].flatMap { p =>
        ref.modify {
          case s @ Handoff.State.Full(_, notifyProducer) => (notifyProducer.await *> offer(a), s)
          case Handoff.State.Empty(notifyConsumer)       => (notifyConsumer.succeed(()) *> p.await, Handoff.State.Full(a, p))
        }.flatten
      }

    def take: UIO[A] =
      Promise.make[Nothing, Unit].flatMap { p =>
        ref.modify {
          case Handoff.State.Full(a, notifyProducer)   => (notifyProducer.succeed(()).as(a), Handoff.State.Empty(p))
          case s @ Handoff.State.Empty(notifyConsumer) => (notifyConsumer.await *> take, s)
        }.flatten
      }
  }

  private[zio] object Handoff {
    def make[A]: UIO[Handoff[A]] =
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
}
