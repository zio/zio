package zio.stream.experimental

import zio._

/**
 * A `ZStream` is a process that produces values of type `I`.
 */
final class ZStream[-R, +E, +O] private (val process: URManaged[R, Pull[R, E, O]]) {

  def >>>[R1 <: R, E1 >: E, I1 >: O, A](transducer: ZTransducer[R1, E1, I1, A]): ZStream[R1, E1, A] =
    aggregate(transducer)

  /**
   * Applies an aggregator to the stream, which converts one or more elements of type `A` into elements of type `B`.
   */
  def aggregate[R1 <: R, E1 >: E, O1 >: O, A](transducer: ZTransducer[R1, E1, O1, A]): ZStream[R1, E1, A] =
    ZStream(process.zip(ZRef.makeManaged(false)).zipWith(transducer.process) {
      case ((pull, done), (step, last)) =>
        ZIO.ifM(done.get)(
          Pull.end,
          (pull >>= step).catchAllCause(Pull.recover(last.ensuring(done.set(true))))
        )
    })

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f`.
   */
  def flatMap[R1 <: R, E1 >: E, A](f: O => ZStream[R1, E1, A]): ZStream[R1, E1, A] = {
    def go(
      outer: Pull[R1, E1, O],
      inner: Pull[R1, E1, A],
      finalizer: Ref[ZManaged.Finalizer]
    ): Pull[R1, E1, A] = {

      def next: ZIO[R1, Option[E1], Pull[R1, E1, A]] =
        finalizer.getAndSet(ZManaged.Finalizer.noop).flatMap(_.apply(Exit.unit)) *>
          outer.flatMap(i =>
            ZIO.uninterruptibleMask { restore =>
              for {
                releaseMap <- ZManaged.ReleaseMap.make
                pull       <- restore(f(i).process.zio.provideSome[R1]((_, releaseMap)).map(_._2))
                _          <- finalizer.set(releaseMap.releaseAll(_, ExecutionStrategy.Sequential))
              } yield pull
            }
          )

      inner.catchAllCause(Cause.sequenceCauseOption(_).fold(next.flatMap(go(outer, _, finalizer)))(Pull.halt))
    }

    ZStream {
      for {
        outer     <- process
        inner     <- ZManaged.succeedNow[Pull[R1, E1, A]](Pull.end)
        finalizer <- ZManaged.finalizerRef(ZManaged.Finalizer.noop)
      } yield go(outer, inner, finalizer)
    }
  }

  /**
   * Pulls and returns one element from this stream, without running the entire stream.
   *
   * @note This is, in some sense. equivalent to `Seq.head`.
   */
  def head: ZIO[R, Option[E], O] =
    process.use(identity)

  /**
   * Transforms the elements of this stream using the supplied function.
   */
  def map[A](f: O => A): ZStream[R, E, A] =
    ZStream(process.map(_.map(f)))

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  def run[R1 <: R, E1 >: E, O1 >: O, Z](sink: ZSink[R1, E1, O1, Z]): ZIO[R1, E1, Z] =
    (process <*> sink.process).use {
      case (pull, (push, done)) =>
        (pull >>= push).forever.catchAllCause(Cause.sequenceCauseOption(_).fold(done)(ZIO.halt(_)))
    }

  /**
   * Runs the stream and collects all of its elements in to a chunk.
   */
  def runCollect: ZIO[R, E, Chunk[O]] =
    run(ZSink.collect[O])

  /**
   * A stream that emits values from this stream until the specified predicate evaluates to `true`.
   */
  def takeUntil(p: O => Boolean): ZStream[R, E, O] =
    ZStream(
      ZRef
        .makeManaged(false)
        .zipWith(process)((done, outer) =>
          ZIO.ifM(done.get)(
            Pull.end,
            outer.foldCauseM(
              Pull.recover(Pull.end),
              i =>
                if (p(i)) done.set(true).as(i)
                else Pull.emit(i)
            )
          )
        )
    )

  /**
   * A stream that emits values from this stream while the specified predicate evaluates to `true`.
   */
  def takeWhile(p: O => Boolean): ZStream[R, E, O] =
    ZStream(
      ZRef
        .makeManaged(false)
        .zipWith(process)((done, outer) =>
          ZIO.ifM(done.get)(
            Pull.end,
            outer.foldCauseM(
              Pull.recover(Pull.end),
              i =>
                if (p(i)) Pull.emit(i)
                else done.set(true) *> Pull.end
            )
          )
        )
    )
}

object ZStream {

  def apply[R, E, O](process: URManaged[R, Pull[R, E, O]]): ZStream[R, E, O] =
    new ZStream(process)

  def apply[O](i: O*): ZStream[Any, Nothing, O] =
    fromChunk(Chunk.fromIterable(i))

  def fromChunk[O](chunk: Chunk[O]): ZStream[Any, Nothing, O] =
    ZStream(
      ZRef
        .makeManaged(chunk)
        .map(ref => ref.modify(rem => if (rem.isEmpty) (Pull.end, rem) else (Pull.emit(rem.head), rem.tail)).flatten)
    )

  def fromEffect[R, E, O](z: ZIO[R, E, O]): ZStream[R, E, O] =
    apply(ZRef.makeManaged(false).map(done => ZIO.ifM(done.get)(Pull.end, Pull(z.ensuring(done.set(true))))))

  def repeatEffect[R, E, O](z: ZIO[R, E, O]): ZStream[R, E, O] =
    succeed(z.mapError(Some(_)))

  def succeed[R, E, O](process: Pull[R, E, O]): ZStream[R, E, O] =
    apply(ZManaged.succeedNow(process))
}
