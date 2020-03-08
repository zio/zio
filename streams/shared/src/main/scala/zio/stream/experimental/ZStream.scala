package zio.stream.experimental

import zio._

abstract class ZStream[-R, +E, +O](
  val process: ZManaged[R, Nothing, ZIO[R, Either[E, Unit], Chunk[O]]]
) extends ZConduit[R, E, Unit, O, Unit](process.map(pull => _ => pull)) { self =>

  /**
   * Symbolic alias for [[ZStream#concat]].
   */
  def ++[R1 <: R, E1 >: E, O1 >: O](that: => ZStream[R1, E1, O1]): ZStream[R1, E1, O1] =
    ZStream {
      // This implementation is identical to ZStream.concatAll, but specialized so we can
      // maintain laziness on `that`. Laziness on concatenation is important for combinators
      // such as `forever`.
      for {
        currStream   <- Ref.make[ZIO[R1, Either[E1, Unit], Chunk[O1]]](ZIO.fail(Right(()))).toManaged_
        switchStream <- ZManaged.switchable[R1, Nothing, ZIO[R1, Either[E1, Unit], Chunk[O1]]]
        switched     <- Ref.make(false).toManaged_
        _            <- switchStream(self.process).flatMap(currStream.set).toManaged_
        pull = {
          def go: ZIO[R1, Either[E1, Unit], Chunk[O1]] =
            currStream.get.flatten.catchAllCause {
              Cause.sequenceCauseEither(_) match {
                case Left(e) => ZIO.halt(e.map(Left(_)))
                case Right(_) =>
                  switched.getAndSet(true).flatMap {
                    if (_) ZIO.fail(Right(()))
                    else switchStream(that.process).flatMap(currStream.set) *> go
                  }
              }
            }

          go
        }
      } yield pull
    }

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
   * Performs a filter and map in a single step.
   */
  def collect[O1](pf: PartialFunction[O, O1]): ZStream[R, E, O1] =
    ZStream(self.process.map(_.map(_.collect(pf))))

  /**
   * Performs an effectful filter and map in a single step.
   */
  final def collectM[R1 <: R, E1 >: E, O1](pf: PartialFunction[O, ZIO[R1, E1, O1]]): ZStream[R1, E1, O1] =
    ZStream(self.process.map(_.flatMap(_.collectM(pf).mapError(Left(_)))))

  /**
   * Concatenates the specified stream with this stream, resulting in a stream
   * that emits the elements from this stream and then the elements from the specified stream.
   */
  def concat[R1 <: R, E1 >: E, O1 >: O](that: ZStream[R1, E1, O1]): ZStream[R1, E1, O1] =
    ZStream.concatAll(Chunk(self, that))

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
   * Repeats this stream forever.
   */
  def forever: ZStream[R, E, O] =
    self ++ forever

  /**
   * Filters the elements emitted by this stream using the provided function.
   */
  def filter(f: O => Boolean): ZStream[R, E, O] =
    ZStream(self.process.map(_.map(_.filter(f))))

  /**
   * Effectfully filters the elements emitted by this stream.
   */
  def filterM[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Boolean]): ZStream[R1, E1, O] =
    ZStream(self.process.map(_.flatMap(_.filterM(f).mapError(Left(_)))))

  /**
   * Filters this stream by the specified predicate, removing all elements for
   * which the predicate evaluates to true.
   */
  final def filterNot(pred: O => Boolean): ZStream[R, E, O] = filter(a => !pred(a))

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f0`
   */
  def flatMap[R1 <: R, E1 >: E, O2](f0: O => ZStream[R1, E1, O2]): ZStream[R1, E1, O2] = {
    def go(
      outerStream: ZIO[R1, Either[E1, Unit], Chunk[O]],
      switchInner: ZManaged[R1, Nothing, ZIO[R1, Either[E1, Unit], Chunk[O2]]] => ZIO[
        R1,
        Nothing,
        ZIO[R1, Either[E1, Unit], Chunk[O2]]
      ],
      currInnerStream: Ref[ZIO[R1, Either[E1, Unit], Chunk[O2]]]
    ): ZIO[R1, Either[E1, Unit], Chunk[O2]] = {
      def pullOuter: ZIO[R1, Either[E1, Unit], Unit] =
        outerStream
          .flatMap(os => switchInner(ZStream.concatAll(os.map(f0)).process))
          .flatMap(currInnerStream.set)

      currInnerStream.get.flatten.catchAllCause { c =>
        Cause.sequenceCauseEither(c) match {
          case Left(e)  => ZIO.halt(e.map(Left(_)))
          case Right(_) =>
            // The additional switch is needed to eagerly run the finalizer
            // *before* pulling another element from the outer stream.
            switchInner(ZManaged.succeed(ZIO.fail(Right(())))) *>
              pullOuter *>
              go(outerStream, switchInner, currInnerStream)
        }
      }
    }

    ZStream {
      for {
        currInnerStream <- Ref.make[ZIO[R1, Either[E1, Unit], Chunk[O2]]](ZIO.fail(Right(()))).toManaged_
        switchInner     <- ZManaged.switchable[R1, Nothing, ZIO[R1, Either[E1, Unit], Chunk[O2]]]
        outerStream     <- self.process
      } yield go(outerStream, switchInner, currInnerStream)
    }
  }

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
          if (_) ZIO.fail(Right(()))
          else
            p.poll.flatMap {
              case None    => as
              case Some(v) => done.set(true) *> v.mapError(Left(_)) *> ZIO.fail(Right(()))
            }
        }
      } yield pull
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
          if (_) ZIO.fail(Right(()))
          else
            as.raceFirst(
              p.await
                .mapError(Left(_))
                .foldCauseM(
                  c => done.set(true) *> ZIO.halt(c),
                  _ => done.set(true) *> ZIO.fail(Right(()))
                )
            )
        }
      } yield pull
    }

  /**
   * Transforms the elements of this stream using the supplied function.
   */
  def map[O2](f: O => O2): ZStream[R, E, O2] =
    ZStream(self.process.map(_.map(_.map(f))))

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
        } yield t._2).mapError(Left(_))
      }
    }

  def mapConcat[O2](f: O => Iterable[O2]): ZStream[R, E, O2] =
    mapConcatChunk(o => Chunk.fromIterable(f(o)))

  def mapConcatChunk[O2](f: O => Chunk[O2]): ZStream[R, E, O2] =
    ZStream(self.process.map(_.map(_.flatMap(f))))

  /**
   * Transforms the errors emitted by this stream using `f`.
   */
  def mapError[E2](f: E => E2): ZStream[R, E2, O] =
    ZStream(self.process.map(_.mapError(_.left.map(f))))

  /**
   * Transforms the full causes of failures emitted by this stream.
   */
  def mapErrorCause[E2](f: Cause[E] => Cause[E2]): ZStream[R, E2, O] =
    ZStream(
      self.process.map(
        _.mapErrorCause(Cause.sequenceCauseEither(_).fold(f(_).map(Left(_)), _ => Cause.fail(Right(()))))
      )
    )

  def transduce[R1 <: R, E1 >: E, O2 >: O, O3](transducer: ZTransducer[R1, E1, O2, O3]): ZStream[R1, E1, O3] =
    ZStream {
      for {
        pushTransducer <- transducer.push
        pullSelf       <- self.process
        pull = pullSelf.foldM(
          {
            case l @ Left(_) => ZIO.fail(l)
            case Right(_)    => pushTransducer(None)
          },
          os => pushTransducer(Some(os))
        )
      } yield pull
    }
}

object ZStream {
  def apply[R, E, O](
    process: ZManaged[R, Nothing, ZIO[R, Either[E, Unit], Chunk[O]]]
  ): ZStream[R, E, O] =
    new ZStream(process) {}

  /**
   * Concatenates all of the streams in the chunk to one stream.
   */
  def concatAll[R, E, O](streams: Chunk[ZStream[R, E, O]]): ZStream[R, E, O] =
    ZStream {
      val chunkSize = streams.size

      for {
        currIndex    <- Ref.make(0).toManaged_
        currStream   <- Ref.make[ZIO[R, Either[E, Unit], Chunk[O]]](ZIO.fail(Right(()))).toManaged_
        switchStream <- ZManaged.switchable[R, Nothing, ZIO[R, Either[E, Unit], Chunk[O]]]
        pull = {
          def go: ZIO[R, Either[E, Unit], Chunk[O]] =
            currStream.get.flatten.catchAllCause {
              Cause.sequenceCauseEither(_) match {
                case Left(e) => ZIO.halt(e.map(Left(_)))
                case Right(_) =>
                  currIndex.getAndUpdate(_ + 1).flatMap { i =>
                    if (i >= chunkSize) ZIO.fail(Right(()))
                    else switchStream(streams(i).process).flatMap(currStream.set) *> go
                  }
              }
            }

          go
        }
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
        pull = doneRef.modify {
          if (_) ZIO.fail(Right(())) -> true
          else ZIO.succeed(c)        -> true
        }.flatten
      } yield pull
    }

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
            if (done) ZIO.fail(Right(()))
            else
              (for {
                _           <- doneRef.set(true)
                reservation <- managed.reserve
                _           <- finalizer.add(reservation.release)
                a           <- restore(reservation.acquire)
              } yield Chunk(a)).mapError(Left(_))
          }
        }
      } yield pull
    }
}
