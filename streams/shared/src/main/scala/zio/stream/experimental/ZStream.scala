package zio.stream.experimental

import zio._

/**
 * A `ZStream` is a process that produces values of type `I`.
 */
abstract class ZStream[-R, +E, +I] private (val process: URManaged[R, Pull[R, E, I]]) {

  /**
   * Alias for [[pipe]].
   */
  def >>>[R1 <: R, E1 >: E, I1 >: I, A](transducer: ZTransducer[R1, E1, I1, A]): ZStream[R1, E1, A] =
    pipe(transducer)

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f`.
   */
  def flatMap[R1 <: R, E1 >: E, A](f: I => ZStream[R1, E1, A]): ZStream[R1, E1, A] = {
    def go(
      outer: Pull[R1, E1, I],
      inner: Pull[R1, E1, A],
      finalizer: Ref[ZManaged.Finalizer]
    ): Pull[R1, E1, A] = {

      def next: Pull[R1, E1, Pull[R1, E1, A]] =
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

      inner.catchAllCause(Pull.recover(next.flatMap(go(outer, _, finalizer))))
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
  def head: ZIO[R, Option[E], I] =
    process.use(identity)

  /**
   * Transforms the elements of this stream using the supplied function.
   */
  def map[A](f: I => A): ZStream[R, E, A] =
    ZStream(process.map(_.map(f)))

  /**
   * Applies a transducer to the stream, which converts one or more elements of type `A` into elements of type `B`.
   */
  def pipe[R1 <: R, E1 >: E, I1 >: I, O](transducer: ZTransducer[R1, E1, I1, O]): ZStream[R1, E1, O] =
    ZStream(process.zipWith(transducer.process) {
      case (pull, (step, last)) =>
        (pull >>= step).catchAllCause(Pull.recover(last))
    })

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  def run[R1 <: R, E1 >: E, O1 >: I, O](sink: ZSink[R1, E1, O1, O]): ZIO[R1, E1, O] =
    (process <*> sink.process).use {
      case (pull, (step, done)) =>
        (pull >>= step).forever.catchAllCause(Cause.sequenceCauseOption(_).fold(done)(ZIO.halt(_)))
    }

  /**
   * Runs the stream and collects all of its elements in to a chunk.
   */
  def runCollect: ZIO[R, E, Chunk[I]] =
    run(ZSink.collect[I])
}

object ZStream {

  def apply[R, E, I](process: URManaged[R, Pull[R, E, I]]): ZStream[R, E, I] =
    new ZStream(process) {}

  def apply[I](i: I*): ZStream[Any, Nothing, I] =
    fromChunk(Chunk.fromIterable(i))

  def fromChunk[I](chunk: Chunk[I]): ZStream[Any, Nothing, I] =
    ZStream(
      ZRef
        .makeManaged(chunk)
        .map(ref => ref.modify(rem => if (rem.isEmpty) (Pull.end, rem) else (Pull.emit(rem.head), rem.tail)).flatten)
    )

  def fromEffect[R, E, I](z: ZIO[R, E, I]): ZStream[R, E, I] =
    apply(ZRef.makeManaged(false).map(_.getAndSet(true).flatMap(if (_) Pull.end else Pull(z))))

  def fromProcess[R, E, I](process: Pull[R, E, I]): ZStream[R, E, I] =
    apply(ZManaged.succeedNow(process))

  def fromPull[R, E, I](p: Pull[R, E, I]): ZStream[R, E, I] =
    fromProcess(p)

  def repeatEffect[R, E, I](z: ZIO[R, E, I]): ZStream[R, E, I] =
    fromPull(Pull(z))
}
