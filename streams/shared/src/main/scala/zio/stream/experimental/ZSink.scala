package zio.stream.experimental

import zio._

/**
 * A `ZSink` is a process that aggregate values of type `I` in to a value of type `O`.
 */
abstract class ZSink[-R, +E, -I, +O] {

  val process: ZSink.Process[R, E, I, O]

  /**
   * Returns a sink that applies this sink's process to a chunk of input values.
   *
   * @note If this sink applies a pure transformation, better efficiency can be achieved by overriding this method.
   */
  def chunked: ZSink[R, E, Chunk[I], O] =
    ZSink(process.map {
      case (push, read) => (ZIO.foreach_(_)(push), l => read(l.flatten))
    })

  /**
   * Runs this sink until it yields a result, then uses that result to create another
   * sink from the provided function which will continue to run until it yields a result.
   *
   * This function essentially runs sinks in sequence.
   */
  def flatMap[R1 <: R, E1 >: E, I1 <: I, A](f: O => ZSink[R1, E1, I1, A]): ZSink[R1, E1, I1, A] =
    foldCauseM(ZSink.halt[E1], f)

  def foldCauseM[R1 <: R, E1 >: E, I1 <: I, A](
    failure: Cause[E1] => ZSink[R1, E1, I1, A],
    success: O => ZSink[R1, E1, I1, A]
  ): ZSink[R1, E1, I1, A] =
    ZSink(for {
      switch <- ZManaged.switchable[R1, Nothing, (Push[R1, E1, I1, Any], Read[R1, E1, Chunk[I1], A])]
      outer  <- process
      inner  <- ZRef.makeManaged(Option.empty[(Push[R1, E1, I1, Any], Read[R1, E1, Chunk[I1], A])])
    } yield {

      def open(sink: ZSink[R1, E1, I1, A]): URIO[R1, (Push[R1, E1, I1, Any], Read[R1, E1, Chunk[I1], A])] =
        switch(sink.process).tap(p => inner.set(Some(p)))

      def push(i: I1): Pull[R1, E1, Any] =
        inner.get.flatMap(
          _.fold[Pull[R1, E1, Any]](
            outer
              ._1(i)
              .catchAllCause(
                Cause
                  .sequenceCauseOption(_)
                  .fold(Pull(outer._2(Chunk.empty)).flatMap(z => open(success(z))))(c => open(failure(c)))
              )
          )(_._1(i))
        )

      def read(l: Chunk[I1]): ZIO[R1, E1, A] =
        inner.get.flatMap(_.fold(outer._2(Chunk.empty).flatMap(z => open(success(z)).flatMap(_._2(l))))(_._2(l)))

      (push _, read _)
    })

  /**
   * Transforms this sink's result.
   */
  def map[A](f: O => A): ZSink[R, E, I, A] =
    ZSink(process.map { case (push, read) => (push, read(_).map(f)) })

  /**
   * Transforms the errors emitted by this sink using `f`.
   */
  def mapError[E1](f: E => E1): ZSink[R, E1, I, O] =
    ZSink(process.map { case (push, read) => (push(_).mapError(_.map(f)), read(_).mapError(f)) })

  /**
   * Effectfully transforms this sink's result.
   */
  def mapM[R1 <: R, E1 >: E, A](f: O => ZIO[R1, E1, A]): ZSink[R1, E1, I, A] =
    ZSink(process.map { case (push, read) => (push, l => read(l).flatMap(f)) })

  def sanitize[R1 <: R, E1 >: E, I1 <: I](f: Chunk[I1] => ZIO[R1, E1, Any]): ZSink[R1, E1, I1, O] =
    ZSink(process.map {
      case (push, read) => (push, f(_) *> read(Chunk.empty))
    })

  def sanitize_ : ZSink[R, E, I, O] =
    sanitize(_ => ZIO.unit)
}

object ZSink {

  type Process[-R, +E, -I, +O] = URManaged[R, (Push[R, E, I, Any], Read[R, E, Chunk[I], O])]

  /**
   * A sink that aggregates values using the given process.
   */
  def apply[R, E, I, O](p: Process[R, E, I, O]): ZSink[R, E, I, O] =
    new ZSink[R, E, I, O] {
      val process: Process[R, E, I, O] = p
    }

  /**
   * A continuous sink that collects all inputs into a chunk.
   */
  def collect[A]: ZSink[Any, Nothing, A, Chunk[A]] =
    fold[A, ChunkBuilder[A]](ChunkBuilder.make[A]())(_ += _)(_ ++= _).map(_.result())

  /**
   * A continuous sink that collects all inputs into a map. The keys are extracted from inputs using the keying function
   * `key`; if multiple inputs use the same key, they are merged using the `f` function.
   */
  def collectMap[A, K](key: A => K)(f: (A, A) => A): ZSink[Any, Nothing, A, Map[K, A]] = {

    def put(z: Map[K, A], a: A): Map[K, A] = {
      val k = key(a)
      z.updated(k, if (z.contains(k)) f(z(k), a) else a)
    }

    def putAll(z: Map[K, A], a: Chunk[A]): Map[K, A] =
      a.foldLeft(z)(put)

    fold(Map.empty[K, A])(put)(putAll)
  }

  /**
   * A continuous sink that collects all inputs into a set.
   */
  def collectSet[A]: ZSink[Any, Nothing, A, Set[A]] =
    fold[A, scala.collection.mutable.Builder[A, Set[A]]](Set.newBuilder[A])(_ += _)(_ ++= _).map(_.result())

  def fold[I, O](z: => O)(push: (O, I) => O)(read: (O, Chunk[I]) => O): ZSink[Any, Nothing, I, O] =
    new ZSink[Any, Nothing, I, O] {
      val process: Process[Any, Nothing, I, O] = Process.fold(z)(push, read)

      override def chunked: ZSink[Any, Nothing, Chunk[I], O] =
        ZSink(Process.fold(z)((s, i) => i.foldLeft(s)(push), (s, l) => l.foldLeft(s)(read)))
    }

  /**
   * A continuous sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldM[R, E, I, Z](z: => Z)(f: (Z, I) => ZIO[R, E, Z]): ZSink[R, E, I, Z] =
    ZSink(Process.foldM(z)((s, i) => Pull(f(s, i)), (s, l) => ZIO.foldLeft(l)(s)(f)))

  def fromEffect[R, E, A](z: ZIO[R, E, A]): ZSink[R, E, Any, A] =
    ZSink(Process.succeed(z))

  /**
   * A continuous sink that executes the provided effectful function for every element fed to it.
   */
  def foreach[R, E, A](f: A => ZIO[R, E, Any]): ZSink[R, E, A, Unit] =
    ZSink(Process.foreach(f))

  /**
   * A sink that halts with the given cause.
   */
  def halt[E](cause: Cause[E]): ZSink[Any, E, Any, Nothing] =
    ZSink(Process.halt(cause))

  /**
   * A continuous sink that consumes all input values and yields the first one.
   */
  def head[A]: ZSink[Any, Nothing, A, Option[A]] =
    fold[A, Option[A]](Option.empty)((s, a) => if (s.isEmpty) Some(a) else s)((s, l) => s.orElse(l.headOption))

  /**
   * A continuous sink that yields the last input value.
   */
  def last[A]: ZSink[Any, Nothing, A, Option[A]] =
    fold[A, Option[A]](Option.empty)((_, a) => Some(a))((s, l) => s.orElse(l.lastOption))

  /**
   * A continuous sink that prints every input string to the console (including a newline character).
   */
  val putStrLn: ZSink[console.Console, Nothing, String, Unit] =
    ZSink(Process.foreach(console.putStrLn(_)))

  def succeed[A](a: A): ZSink[Any, Nothing, Any, A] =
    ZSink(Process.succeed(ZIO.succeedNow(a)))

  object Process {

    def fold[I, O, S](init: => S)(push: (S, I) => S, read: (S, Chunk[I]) => O): Process[Any, Nothing, I, O] =
      ZRef.makeManaged(init).map(ref => (i => ref.update(push(_, i)), l => ref.get.map(read(_, l))))

    def foldM[R, E, I, O, S](
      init: => S
    )(push: (S, I) => Pull[R, E, S], read: (S, Chunk[I]) => ZIO[R, E, O]): Process[R, E, I, O] =
      ZRef
        .makeManaged(init)
        .map(ref => (i => ref.get.flatMap(push(_, i) >>= ref.set), l => ref.get.flatMap(read(_, l))))

    def foreach[R, E, A](f: A => ZIO[R, E, Any]): Process[R, E, A, Unit] =
      ZManaged.succeedNow((a => Pull(f(a)), _ => ZIO.unit))

    def halt[E](cause: Cause[E]): Process[Any, E, Any, Nothing] =
      ZManaged.succeedNow((_ => Pull.halt(cause), _ => ZIO.halt(cause)))

    def succeed[R, E, A](z: ZIO[R, E, A]): Process[R, E, Any, A] =
      ZManaged.succeedNow((_ => Pull.end, _ => z))
  }
}
