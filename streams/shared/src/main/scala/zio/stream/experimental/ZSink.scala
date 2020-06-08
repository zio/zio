package zio.stream.experimental

import zio._

import scala.collection.mutable

/**
 * A `ZSink` is a process that aggregate values of type `I` in to a value of type `O`.
 */
abstract class ZSink[-R, +E, -I, +O] {

  val process: ZSink.Process[R, E, I, O]

  /**
   * Alias for [[pipe]].
   */
  def <<<[R1 <: R, E1 >: E, A](transducer: ZTransducer[R1, E1, A, I]): ZSink[R1, E1, A, O] =
    pipe(transducer)

  /**
   * Returns a sink that applies this sink's process to a chunk of input values.
   *
   * @note If this sink applies a pure transformation, better efficiency can be achieved by overriding this method.
   */
  def chunked: ZSink[R, E, Chunk[I], O] =
    ZSink(process.map { case (step, done) => (ZIO.foreach_(_)(step), done) })

  /**
   * Transforms this sink's input elements.
   */
  def contramap[A](f: A => I): ZSink[R, E, A, O] =
    ZSink(process.map { case (step, done) => (a => step(f(a)), done) })

  /**
   * Effectfully transforms this sink's input elements.
   */
  def contramapM[R1 <: R, E1 >: E, A](f: A => ZIO[R1, E1, I]): ZSink[R1, E1, A, O] =
    ZSink[R1, E1, A, O](process.map { case (step, done) => (a => Pull(f(a)).flatMap(step), done) })

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
      switch <- ZManaged.switchable[R1, Nothing, (I1 => Pull[R1, E1, Any], ZIO[R1, E1, A])]
      outer  <- process
      inner  <- Promise.make[Nothing, (I1 => Pull[R1, E1, Any], ZIO[R1, E1, A])].toManaged_
    } yield {

      def open(sink: ZSink[R1, E1, I1, A]): ZIO[R1, Nothing, Boolean] =
        switch(sink.process).to(inner)

      def push(i: I1): Pull[R1, E1, Any] =
        ZIO.ifM(inner.isDone)(
          inner.await.flatMap { case (push, _) => push(i) },
          outer
            ._1(i)
            .catchAllCause(
              Cause.sequenceCauseOption(_).fold(Pull(outer._2).flatMap(z => open(success(z))))(c => open(failure(c)))
            )
        )

      val done = ZIO.ifM(inner.isDone)(
        inner.await.flatMap(_._2),
        outer._2.flatMap(z => open(success(z)) *> inner.await.flatMap(_._2))
      )

      (i => push(i), done)
    })

  /**
   * Returns a sink that applies this sink's process to multiple input values.
   *
   * @note If this sink applies a pure transformation, better efficiency can be achieved by overriding this method.
   */
  def foreach: ZSink[R, E, Iterable[I], O] =
    ZSink(process.map { case (step, done) => (ZIO.foreach_(_)(step), done) })

  /**
   * Transforms this sink's result.
   */
  def map[A](f: O => A): ZSink[R, E, I, A] =
    ZSink(process.map { case (step, done) => (step, done.map(f)) })

  /**
   * Transforms the errors emitted by this sink using `f`.
   */
  def mapError[E1](f: E => E1): ZSink[R, E1, I, O] =
    ZSink(process.map { case (step, done) => (i => step(i).mapError(_.map(f)), done.mapError(f)) })

  /**
   * Effectfully transforms this sink's result.
   */
  def mapM[R1 <: R, E1 >: E, A](f: O => ZIO[R1, E1, A]): ZSink[R1, E1, I, A] =
    ZSink(process.map { case (step, done) => (step, done.flatMap(f)) })

  /**
   * A sink that passes input values through `transducer` and then passes the output through this sink.
   */
  def pipe[R1 <: R, E1 >: E, A](transducer: ZTransducer[R1, E1, A, I]): ZSink[R1, E1, A, O] =
    ZSink(transducer.process.zip(ZRef.makeManaged(false)).zipWith(process) {
      case (((p1, z1), ref), (p2, z2)) =>
        (
          i => (p1(i) >>= p2).catchAllCause(Pull.recover(Pull.end.ensuring(ref.set(true)))),
          ZIO.ifM(ref.get)(
            z2,
            z1.foldCauseM(
              Cause.sequenceCauseOption(_).fold[ZIO[R1, E1, O]](z2)(ZIO.halt(_)),
              p2(_).foldCauseM(Cause.sequenceCauseOption(_).fold(z2.ensuring(ref.set(true)))(ZIO.halt(_)), _ => z2)
            )
          )
        )
    })
}

object ZSink {

  type Process[-R, +E, -I, +O] = URManaged[R, (I => Pull[R, E, Any], ZIO[R, E, O])]

  /**
   * A sink that aggregates values using the given process.
   */
  def apply[R, E, I, O](p: Process[R, E, I, O]): ZSink[R, E, I, O] =
    new ZSink[R, E, I, O] {
      val process: Process[R, E, I, O] = p
    }

  /**
   * A continuous sink that folds its inputs with the provided function and initial state.
   */
  def foldLeft[I, Z](z: Z)(f: (Z, I) => Z): ZSink[Any, Nothing, I, Z] =
    ZSink(ZRef.makeManaged(z).map(ref => ((i: I) => ref.update(f(_, i)), ref.get)))

  /**
   * A continuous sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldLeftM[R, E, I, Z](z: Z)(f: (Z, I) => ZIO[R, E, Z]): ZSink[R, E, I, Z] =
    ZSink(ZRef.makeManaged(z).map(ref => ((i: I) => ref.get.flatMap(z => Pull(f(z, i)).flatMap(ref.set)), ref.get)))

  /**
   * A continuous sink that collects all of its inputs into a chunk.
   */
  def collect[A]: ZSink[Any, Nothing, A, Chunk[A]] =
    new ZSink[Any, Nothing, A, Chunk[A]] {
      val builder: UManaged[ChunkBuilder[A]] = ZManaged.succeed(ChunkBuilder.make[A]())
      val process: Process[Any, Nothing, A, Chunk[A]] =
        builder.map(b => ((a: A) => ZIO.succeedNow(b += a), ZIO.succeed(b.result())))

      override def chunked: ZSink[Any, Nothing, Chunk[A], Chunk[A]] =
        ZSink(builder.map(b => ((a: Chunk[A]) => ZIO.succeedNow(b ++= a), ZIO.succeed(b.result()))))

      override def foreach: ZSink[Any, Nothing, Iterable[A], Chunk[A]] =
        ZSink(builder.map(b => ((a: Iterable[A]) => ZIO.succeedNow(b ++= a), ZIO.succeed(b.result()))))
    }

  /**
   * A continuous sink that collects all of its inputs into a map. The keys are extracted from inputs
   * using the keying function `key`; if multiple inputs use the same key, they are merged
   * using the `f` function.
   */
  def collectMap[A, K](key: A => K)(f: (A, A) => A): ZSink[Any, Nothing, A, Map[K, A]] =
    new ZSink[Any, Nothing, A, Map[K, A]] {
      val builder: Managed[Nothing, Map[K, A]] = ZManaged.succeed(Map.empty[K, A])
      val process: Process[Any, Nothing, A, Map[K, A]] =
        builder.map(z => ((a: A) => ZIO.succeedNow(put(z, a)), ZIO.succeed(z)))

      override def chunked: ZSink[Any, Nothing, Chunk[A], Map[K, A]] =
        ZSink(builder.map(z => ((a: Chunk[A]) => ZIO.succeedNow(putAll(z, a)), ZIO.succeed(z))))

      override def foreach: ZSink[Any, Nothing, Iterable[A], Map[K, A]] =
        ZSink(builder.map(z => ((a: Iterable[A]) => ZIO.succeedNow(putAll(z, a)), ZIO.succeed(z))))

      private def put(z: Map[K, A], a: A): Map[K, A] = {
        val k = key(a)
        z.updated(k, if (z.contains(k)) f(z(k), a) else a)
      }

      private def putAll(z: Map[K, A], a: Iterable[A]): Map[K, A] =
        a.foldLeft(z)(put)
    }

  /**
   * A continuous sink that collects all of its inputs into a set.
   */
  def collectSet[A]: ZSink[Any, Nothing, A, Set[A]] =
    new ZSink[Any, Nothing, A, Set[A]] {
      val builder: Managed[Nothing, mutable.Builder[A, Set[A]]] = ZManaged.succeed(Set.newBuilder[A])
      val process: Process[Any, Nothing, A, Set[A]] =
        builder.map(b => ((a: A) => ZIO.succeedNow(b += a), ZIO.succeed(b.result())))

      override def chunked: ZSink[Any, Nothing, Chunk[A], Set[A]] =
        ZSink(builder.map(b => ((a: Chunk[A]) => ZIO.succeedNow(b ++= a), ZIO.succeed(b.result()))))

      override def foreach: ZSink[Any, Nothing, Iterable[A], Set[A]] =
        ZSink(builder.map(b => ((a: Iterable[A]) => ZIO.succeedNow(b ++= a), ZIO.succeed(b.result()))))
    }

  /**
   * A continuous sink that executes the provided effectful function for every element fed to it.
   */
  def foreach[R, E, A](f: A => ZIO[R, E, Unit]): ZSink[R, E, A, Unit] =
    push(a => Pull(f(a)), ZIO.unit)

  def fromEffect[R, E, A](z: ZIO[R, E, A]): ZSink[R, E, Any, A] =
    push(_ => Pull.end, z)

  /**
   * A sink that halts with the given cause.
   */
  def halt[E](cause: Cause[E]): ZSink[Any, E, Any, Nothing] =
    push(_ => Pull.halt(cause), ZIO.halt(cause))

  /**
   * A continuous sink that consumes all input values and yields the first one.
   */
  def head[A]: ZSink[Any, Nothing, A, Option[A]] =
    ZSink(
      ZRef
        .makeManaged(Option.empty[A])
        .map(ref => (a => ref.update(o => if (o.isEmpty) Some(a) else o), ref.get))
    )

  /**
   * A continuous sink that yields the last input value.
   */
  def last[A]: ZSink[Any, Nothing, A, Option[A]] =
    ZSink(ZRef.makeManaged(Option.empty[A]).map(ref => (a => ref.set(Some(a)), ref.get)))

  /**
   * A sink that consumes input values using `step` yields the value from `done`.
   */
  def push[R, E, I, Z](step: I => Pull[R, E, Any], done: ZIO[R, E, Z]): ZSink[R, E, I, Z] =
    ZSink(ZManaged.succeedNow(step -> done))

  /**
   * A continuous sink that prints every input string to the console (including a newline character).
   */
  val putStrLn: ZSink[console.Console, Nothing, String, Unit] =
    foreach(console.putStrLn(_))

  def succeed[A](a: A): ZSink[Any, Nothing, Any, A] =
    fromEffect(ZIO.succeedNow(a))
}
