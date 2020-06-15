package zio.stream.experimental

import zio._

/**
 * A `ZStream` is a process that produces values of type `I`.
 */
abstract class ZStream[-R, +E, +I] private (val process: ZStream.Process[R, E, I]) {
  self =>

  /**
   * Alias for [[pipe]].
   */
  def >>>[R1 <: R, E1 >: E, I1 >: I, A](transducer: ZTransducer[R1, E1, I1, A]): ZStream[R1, E1, A] =
    pipe(transducer)

  /**
   * Symbolic alias for [[ZStream#cross]].
   */
  final def <*>[R1 <: R, E1 >: E, J](that: ZStream[R1, E1, J]): ZStream[R1, E1, (I, J)] =
    self cross that

  /**
   * Symbolic alias for [[ZStream#crossLeft]].
   */
  final def <*[R1 <: R, E1 >: E, J](that: ZStream[R1, E1, J]): ZStream[R1, E1, I] =
    self crossLeft that

  /**
   * Symbolic alias for [[ZStream#crossRight]].
   */
  final def *>[R1 <: R, E1 >: E, J](that: ZStream[R1, E1, J]): ZStream[R1, E1, J] =
    self crossRight that

  final def cross[R1 <: R, E1 >: E, J](that: ZStream[R1, E1, J]): ZStream[R1, E1, (I, J)] =
    (self crossWith that)((_, _))

  final def crossLeft[R1 <: R, E1 >: E, J](that: ZStream[R1, E1, J]): ZStream[R1, E1, I] =
    (self crossWith that)((i, _) => i)

  final def crossRight[R1 <: R, E1 >: E, J](that: ZStream[R1, E1, J]): ZStream[R1, E1, J] =
    (self crossWith that)((_, j) => j)

  def crossWith[R1 <: R, E1 >: E, J, A](that: ZStream[R1, E1, J])(f: (I, J) => A): ZStream[R1, E1, A] =
    self.flatMap(i => that.map(f(i, _)))

  /**
   * Executes the provided finalizer after this stream's finalizers run.
   */
  final def ensuring[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStream[R1, E, I] =
    ZStream(self.process.ensuring(fin))

  /**
   * Executes the provided finalizer before this stream's finalizers run.
   */
  final def ensuringFirst[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStream[R1, E, I] =
    ZStream(self.process.ensuringFirst(fin))

  def filter(p: I => Boolean): ZStream[R, E, I] =
    ZStream(process.map { pull =>
      def go: Pull[R, E, I] =
        pull.flatMap(i => if (p(i)) Pull.emit(i) else go)
      go
    })

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f`.
   */
  def flatMap[R1 <: R, E1 >: E, A](f: I => ZStream[R1, E1, A]): ZStream[R1, E1, A] = {
    def go(
      outer: Pull[R1, E1, I],
      inner: Ref[Pull[R1, E1, A]],
      finalizer: Ref[ZManaged.Finalizer]
    ): Pull[R1, E1, A] = {

      def next: Pull[R1, E1, Unit] =
        finalizer.getAndSet(ZManaged.Finalizer.noop).flatMap(_.apply(Exit.unit)) *>
          outer.flatMap(i =>
            ZIO.uninterruptibleMask { restore =>
              for {
                releaseMap <- ZManaged.ReleaseMap.make
                pull       <- restore(f(i).process.zio.provideSome[R1]((_, releaseMap)).map(_._2))
                _          <- finalizer.set(releaseMap.releaseAll(_, ExecutionStrategy.Sequential))
                _          <- inner.set(pull)
              } yield ()
            }
          )

      inner.get.flatten.catchAllCause(Pull.recover(next *> go(outer, inner, finalizer)))
    }

    ZStream {
      for {
        outer     <- process
        inner     <- ZRef.makeManaged[Pull[R1, E1, A]](Pull.end)
        finalizer <- ZManaged.finalizerRef(ZManaged.Finalizer.noop)
      } yield go(outer, inner, finalizer)
    }
  }

  def forever: ZStream[R, E, I] =
    ZStream(
      for {
        current <- ZRef.makeManaged(Pull.end: Pull[R, E, I])
        switch  <- ZManaged.switchable[R, Nothing, Pull[R, E, I]]
      } yield {
        def go: Pull[R, E, I] =
          current.get.flatten.catchAllCause(Pull.recover(switch(process).flatMap(current.set) *> go))
        go
      }
    )

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
   * Effectfully transforms the elements of this stream using the supplied function.
   */
  def mapM[R1 <: R, E1 >: E, J](f: I => ZIO[R1, E1, J]): ZStream[R1, E1, J] =
    ZStream(process.map(_.flatMap(i => Pull(f(i)))))

  /**
   * Applies a transducer to the stream, which converts one or more elements of type `A` into elements of type `B`.
   */
  def pipe[R1 <: R, E1 >: E, I1 >: I, O](
    transducer: ZTransducer[R1, E1, I1, O]
  ): ZStream[R1, E1, O] =
    ZStream(process.zipWith(transducer.process) {
      case (pull, (push, _)) => pull >>= push
    })

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  def run[R1 <: R, E1 >: E, O1 >: I, O](sink: ZSink[R1, E1, O1, O]): ZIO[R1, E1, O] =
    (process <*> sink.process).use {
      case (pull, (push, read)) =>
        (pull >>= push).forever.catchAllCause(Cause.sequenceCauseOption(_).fold(read(Chunk.empty))(ZIO.halt(_)))
    }

  /**
   * Runs the stream and collects all of its elements in to a chunk.
   */
  def runCollect: ZIO[R, E, Chunk[I]] =
    run(ZSink.collect[I])

  def runDrain: ZIO[R, E, Unit] =
    run(ZSink.drain)

  def take(n: Long): ZStream[R, E, I] =
    ZStream(
      ZRef
        .makeManaged(n)
        .zipWith(process)((ref, pull) => ref.modify(i => if (i <= 0) (Pull.end, 0) else (pull, i - 1)).flatten)
    )

  def tap[R1 <: R, E1 >: E](f: I => ZIO[R1, E1, Any]): ZStream[R1, E1, I] =
    mapM(i => f(i).as(i))
}

object ZStream {

  type Process[-R, +E, +I] = URManaged[R, Pull[R, E, I]]

  def apply[R, E, I](process: Process[R, E, I]): ZStream[R, E, I] =
    new ZStream(process) {}

  def apply[I](i: I*): ZStream[Any, Nothing, I] =
    fromChunk(Chunk.fromIterable(i))

  def bracket[R, E, A](acquire: ZIO[R, E, A])(release: A => ZIO[R, Nothing, Any]): ZStream[R, E, A] =
    managed(ZManaged.make(acquire)(release))

  def bracketExit[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => ZIO[R, Nothing, Any]): ZStream[R, E, A] =
    managed(ZManaged.makeExit(acquire)(release))

  val empty: ZStream[Any, Nothing, Nothing] =
    ZStream()

  def fail[E](e: E): ZStream[Any, E, Nothing] =
    ZStream(ZManaged.succeedNow(Pull.fail(e)))

  def finalizer[R](finalizer: ZIO[R, Nothing, Any]): ZStream[R, Nothing, Any] =
    bracket[R, Nothing, Unit](ZIO.unit)(_ => finalizer)

  def fromChunk[I](chunk: Chunk[I]): ZStream[Any, Nothing, I] =
    ZStream(
      ZRef
        .makeManaged(chunk)
        .map(ref => ref.modify(rem => if (rem.isEmpty) (Pull.end, rem) else (Pull.emit(rem.head), rem.tail)).flatten)
    )

  def fromEffect[R, E, I](z: ZIO[R, E, I]): ZStream[R, E, I] =
    apply(ZRef.makeManaged(false).map(_.getAndSet(true).flatMap(if (_) Pull.end else Pull(z))))

  def fromIterable[I](is: Iterable[I]): ZStream[Any, Nothing, I] =
    ZStream(
      ZRef
        .makeManaged(is)
        .map(ref => ref.modify(rem => if (rem.isEmpty) (Pull.end, rem) else (Pull.emit(rem.head), rem.tail)).flatten)
    )

  def fromPull[R, E, I](p: Pull[R, E, I]): ZStream[R, E, I] =
    apply(ZManaged.succeedNow(p))

  def managed[R, E, I](managed: ZManaged[R, E, I]): ZStream[R, E, I] =
    ZStream(
      for {
        ref       <- ZRef.makeManaged(false)
        finalizer <- ZManaged.ReleaseMap.makeManaged(ExecutionStrategy.Sequential)
      } yield ZIO.uninterruptibleMask { restore =>
        ref.get.flatMap { done =>
          if (done) Pull.end
          else
            (for {
              a <- restore(managed.zio.map(_._2).provideSome[R]((_, finalizer))).onError(_ => ref.set(true))
              _ <- ref.set(true)
            } yield a).mapError(Some(_))
        }
      }
    )

  def repeatEffect[R, E, I](z: ZIO[R, E, I]): ZStream[R, E, I] =
    fromPull(Pull(z))
}
