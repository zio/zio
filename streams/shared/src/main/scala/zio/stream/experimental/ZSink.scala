package zio.stream.experimental

import zio._

import scala.collection.mutable

/**
 * A `ZSink` is a process that aggregate values of type `I` and to a value of type `Z`.
 */
trait ZSink[-R, +E, -I, +Z] {

  def process: URManaged[R, (I => Pull[R, E, Any], ZIO[R, E, Z])]

  /**
   * Returns a sink that applies this sink's process to a chunk of input values.
   *
   * @note If this sink applies a pure transformation, better efficiency can be achieved by overriding this method.
   */
  def chunked: ZSink[R, E, Chunk[I], Z] =
    ZSink(process.map { case (push, done) => (ZIO.foreach_(_)(push), done) })

  /**
   * Transforms this sink's input elements.
   */
  def contramap[A](f: A => I): ZSink[R, E, A, Z] =
    ZSink(process.map { case (push, done) => (push compose f, done) })

  /**
   * Effectfully transforms this sink's input elements.
   */
  def contramapM[R1 <: R, E1 >: E, A](f: A => ZIO[R1, E1, I]): ZSink[R1, E1, A, Z] =
    ZSink(process.map { case (push, done) => (a => Pull(f(a)).flatMap(push), done) })

  /**
   * Runs this sink until it yields a result, then uses that result to create another
   * sink from the provided function which will continue to run until it yields a result.
   *
   * This function essentially runs sinks in sequence.
   */
  def flatMap[R1 <: R, E1 >: E, I1 <: I, A](f: Z => ZSink[R1, E1, I1, A]): ZSink[R1, E1, I1, A] =
    foldCauseM(ZSink.halt[E1], f)

  def foldCauseM[R1 <: R, E1 >: E, I1 <: I, A](
    failure: Cause[E1] => ZSink[R1, E1, I1, A],
    success: Z => ZSink[R1, E1, I1, A]
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
   * Returns a sink that applies this sink's process to a multiple input values.
   *
   * @note If this sink applies a pure transformation, better efficiency can be achieved by overriding this method.
   */
  def forall: ZSink[R, E, Iterable[I], Z] =
    ZSink(process.map { case (push, done) => (ZIO.foreach_(_)(push), done) })

  /**
   * Transforms this sink's result.
   */
  def map[A](f: Z => A): ZSink[R, E, I, A] =
    ZSink(process.map { case (push, done) => (push, done.map(f)) })

  /**
   * Transforms the errors emitted by this sink using `f`.
   */
  def mapError[E1](f: E => E1): ZSink[R, E1, I, Z] =
    ZSink(process.map { case (push, done) => (i => push(i).mapError(_.map(f)), done.mapError(f)) })

  /**
   * Effectfully transforms this sink's result.
   */
  def mapM[R1 <: R, E1 >: E, A](f: Z => ZIO[R1, E1, A]): ZSink[R1, E1, I, A] =
    ZSink(process.map { case (push, done) => (push, done.flatMap(f)) })
}

object ZSink {

  type Process[-R, +E, -I, +Z] = URManaged[R, (I => Pull[R, E, Any], ZIO[R, E, Z])]

  /**
   * A sink that aggregates values using the given process.
   */
  def apply[R, E, I, Z](p: Process[R, E, I, Z]): ZSink[R, E, I, Z] =
    new ZSink[R, E, I, Z] {
      val process: URManaged[R, (I => Pull[R, E, Any], ZIO[R, E, Z])] = p
    }

  /**
   * A sink that folds its inputs with the provided function and initial state.
   */
  def foldLeft[I, Z](z: Z)(f: (Z, I) => Z): ZSink[Any, Nothing, I, Z] =
    ZSink(ZRef.makeManaged(z).map(ref => ((i: I) => ref.update(f(_, i)), ref.get)))

  /**
   * A sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldLeftM[R, E, I, Z](z: Z)(f: (Z, I) => ZIO[R, E, Z]): ZSink[R, E, I, Z] =
    ZSink(ZRef.makeManaged(z).map(ref => ((i: I) => ref.get.flatMap(z => Pull(f(z, i)).flatMap(ref.set)), ref.get)))

  /**
   * A sink that collects all of its inputs into a chunk.
   */
  def collect[A]: ZSink[Any, Nothing, A, Chunk[A]] =
    new ZSink[Any, Nothing, A, Chunk[A]] {
      val builder: UManaged[ChunkBuilder[A]] = ZManaged.succeed(ChunkBuilder.make[A]())
      val process: Process[Any, Nothing, A, Chunk[A]] =
        builder.map(b => ((a: A) => ZIO.succeedNow(b += a), ZIO.succeed(b.result())))

      override def chunked: ZSink[Any, Nothing, Chunk[A], Chunk[A]] =
        ZSink(builder.map(b => ((a: Chunk[A]) => ZIO.succeedNow(b ++= a), ZIO.succeed(b.result()))))

      override def forall: ZSink[Any, Nothing, Iterable[A], Chunk[A]] =
        ZSink(builder.map(b => ((a: Iterable[A]) => ZIO.succeedNow(b ++= a), ZIO.succeed(b.result()))))
    }

  /**
   * A sink that collects all of its inputs into a map. The keys are extracted from inputs
   * using the keying function `key`; if multiple inputs use the same key, they are merged
   * using the `f` function.
   */
  def collectMap[A, K](key: A => K)(f: (A, A) => A): ZSink[Any, Nothing, A, Map[K, A]] =
    new ZSink[Any, Nothing, A, Map[K, A]] {
      val builder: Managed[Nothing, Ref[Map[K, A]]] = ZRef.makeManaged(Map.empty[K, A])
      val process: Process[Any, Nothing, A, Map[K, A]] =
        builder.map(ref => ((a: A) => ref.update(put(_, a)), ref.get))

      override def chunked: ZSink[Any, Nothing, Chunk[A], Map[K, A]] =
        ZSink(builder.map(ref => ((a: Chunk[A]) => ref.update(putAll(_, a)), ref.get)))

      override def forall: ZSink[Any, Nothing, Iterable[A], Map[K, A]] =
        ZSink(builder.map(ref => ((a: Iterable[A]) => ref.update(putAll(_, a)), ref.get)))

      private def put(z: Map[K, A], a: A): Map[K, A] = {
        val k = key(a)
        z.updated(k, if (z.contains(k)) f(z(k), a) else a)
      }

      private def putAll(z: Map[K, A], a: Iterable[A]): Map[K, A] =
        a.foldLeft(z)(put)
    }

  /**
   * A sink that collects all of its inputs into a set.
   */
  def collectSet[A]: ZSink[Any, Nothing, A, Set[A]] =
    new ZSink[Any, Nothing, A, Set[A]] {
      val builder: Managed[Nothing, Ref[mutable.Builder[A, Set[A]]]] = ZRef.makeManaged(Set.newBuilder[A])
      val process: Process[Any, Nothing, A, Set[A]] =
        builder.map(ref => ((a: A) => ref.update(_ += a), ref.get.map(_.result())))

      override def chunked: ZSink[Any, Nothing, Chunk[A], Set[A]] =
        ZSink(builder.map(ref => ((a: Chunk[A]) => ref.update(_ ++= a), ref.get.map(_.result()))))

      override def forall: ZSink[Any, Nothing, Iterable[A], Set[A]] =
        ZSink(builder.map(ref => ((a: Iterable[A]) => ref.update(_ ++= a), ref.get.map(_.result()))))
    }

  /**
   * A sink that executes the provided effectful function for every element fed to it.
   */
  def foreach[R, E, A](f: A => ZIO[R, E, Unit]): ZSink[R, E, A, Unit] =
    succeed(a => Pull(f(a)), ZIO.unit)

  /**
   * A sink that halts with the given cause.
   */
  def halt[E](cause: Cause[E]): ZSink[Any, E, Any, Nothing] =
    succeed(_ => ZIO.halt(cause.map(Option(_))), ZIO.halt(cause))

  /**
   * A sink that yields the first value it receives.
   */
  def head[A]: ZSink[Any, Nothing, A, Option[A]] =
    ZSink(
      ZRef
        .makeManaged(Option.empty[A])
        .map(ref => (a => ref.update(o => if (o.isEmpty) Some(a) else o), ref.get))
    )

  /**
   * A sink that yields the last value it receives.
   */
  def last[A]: ZSink[Any, Nothing, A, Option[A]] =
    ZSink(ZRef.makeManaged(Option.empty[A]).map(ref => (a => ref.set(Some(a)), ref.get)))

  /**
   * A sink prints every input string to the console (including a newline character).
   */
  val putStrLn: ZSink[console.Console, Nothing, String, Unit] =
    foreach(console.putStrLn(_))

  /**
   * A sink that consumes values using `push` yields the value from `done`.
   */
  def succeed[R, E, I, Z](push: I => Pull[R, E, Any], done: ZIO[R, E, Z]): ZSink[R, E, I, Z] =
    ZSink(ZManaged.succeedNow(push -> done))
}
