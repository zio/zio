package zio.stream.experimental

import zio._

abstract class ZStream[-R, +E, +O](
  val process: ZManaged[R, Nothing, ZIO[R, Either[E, Unit], Chunk[O]]]
) extends ZConduit[R, E, Unit, O, Unit](process.map(pull => _ => pull)) { self =>

  /**
   * Maps the success values of this stream to the specified constant value.
   */
  def as[O2](o2: => O2): ZStream[R, E, O2] =
    map(new ZIO.ConstFn(() => o2))

  def map[O2](f: O => O2): ZStream[R, E, O2] =
    ZStream(self.process.map(_.map(_.map(f))))

  def mapConcat[O2](f: O => Iterable[O2]): ZStream[R, E, O2] =
    ZStream(self.process.map(_.map(_.flatMap(o => Chunk.fromIterable(f(o))))))

  def filter(f: O => Boolean): ZStream[R, E, O] =
    ZStream(self.process.map(_.map(_.filter(f))))

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f0`
   */
  def flatMap[R1 <: R, E1 >: E, O2](f0: O => ZStream[R1, E1, O2]): ZStream[R1, E1, O2] = {
    def go(
      outerStream: ZIO[R1, Either[E1, Unit], Chunk[O]],
      currOuterChunk: Ref[Chunk[O]],
      currOuterChunkIdx: Ref[Int],
      finalizer: ZManaged.FinalizerRef[R1],
      currInnerStream: Ref[ZIO[R1, Either[E1, Unit], Chunk[O2]]]
    ): ZIO[R1, Either[E1, Unit], Chunk[O2]] = {
      def pullOuter: ZIO[R1, Either[E1, Unit], Unit] = ZIO.uninterruptibleMask { restore =>
        for {
          outerChunk <- currOuterChunk.get
          outerIdx   <- currOuterChunkIdx.get
          _ <- if (outerIdx >= outerChunk.size)
                restore(outerStream).flatMap { o =>
                  if (o.isEmpty) pullOuter
                  else
                    (for {
                      _           <- currOuterChunk.set(o)
                      _           <- currOuterChunkIdx.set(1)
                      reservation <- f0(o(0)).process.reserve
                      innerStream <- restore(reservation.acquire)
                      _           <- finalizer.add(reservation.release)
                      _           <- currInnerStream.set(innerStream)
                    } yield ())
                }
              else
                (for {
                  _           <- currOuterChunkIdx.update(_ + 1)
                  reservation <- f0(outerChunk(outerIdx)).process.reserve
                  innerStream <- restore(reservation.acquire)
                  _           <- finalizer.add(reservation.release)
                  _           <- currInnerStream.set(innerStream)
                } yield ())

        } yield ()
      }

      currInnerStream.get.flatten.catchAllCause { c =>
        Cause.sequenceCauseEither(c) match {
          case Right(e) => ZIO.halt(e.map(Left(_)))
          case Left(_) =>
            finalizer.remove.flatMap(fins => ZIO.foreach(fins)(fin => fin(Exit.succeed(())))).uninterruptible *>
              pullOuter *>
              go(outerStream, currOuterChunk, currOuterChunkIdx, finalizer, currInnerStream)
        }
      }
    }

    ZStream {
      for {
        currInnerStream   <- Ref.make[ZIO[R1, Either[E1, Unit], Chunk[O2]]](ZIO.fail(Right(()))).toManaged_
        currOuterChunk    <- Ref.make[Chunk[O]](Chunk.empty).toManaged_
        currOuterChunkIdx <- Ref.make[Int](-1).toManaged_
        outerStream       <- self.process
        finalizer         <- ZManaged.finalizerRef[R1](_ => UIO.unit)
      } yield go(outerStream, currOuterChunk, currOuterChunkIdx, finalizer, currInnerStream)
    }
  }

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
