// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

/**
 * A mutable atomic reference for the `IO` monad. This is the `IO` equivalent of
 * a volatile `var`, augmented with atomic effectful operations, which make it
 * useful as a reasonably efficient (if low-level) concurrency primitive.
 *
 * Unlike `Ref`, `RefM` allows effects in atomic operations, which makes the
 * structure slower but more powerful than `Ref`.
 *
 * {{{
 * for {
 *   ref <- RefM(2)
 *   v   <- ref.update(_ + putStrLn("Hello World!").attempt.void *> IO.now(3))
 *   _   <- putStrLn("Value = " + v) // Value = 5
 * } yield ()
 * }}}
 */
final class RefM[A] private (private val value: Ref[A], private val queue: Queue[RefM.Bundle[A, _]])
    extends Serializable {

  /**
   * Reads the value from the `Ref`.
   */
  final def get: IO[Nothing, A] = value.get

  /**
   * Writes a new value to the `Ref`, with a guarantee of immediate
   * consistency (at some cost to performance).
   */
  final def set(a: A): IO[Nothing, Unit] = value.set(a)

  /**
   * Writes a new value to the `Ref` without providing a guarantee of
   * immediate consistency.
   */
  final def setLater(a: A): IO[Nothing, Unit] = value.setLater(a)

  /**
   * Atomically modifies the `RefM` with the specified function, returning the
   * value immediately after modification.
   */
  final def update(f: A => IO[Nothing, A]): IO[Nothing, A] =
    modify(a => f(a).map(a => (a, a)))

  /**
   * Atomically modifies the `RefM` with the specified function, which computes
   * a return value for the modification. This is a more powerful version of
   * `update`.
   */
  final def modify[B](f: A => IO[Nothing, (B, A)]): IO[Nothing, B] =
    for {
      promise <- Promise.make[Nothing, B]
      bundle  = RefM.Bundle(None, f, promise)
      _       <- queue.offer(bundle)
      b       <- promise.get.onTermination(ts => IO.sync(bundle.interrupted = Some(ts)))
    } yield b
}

object RefM {
  private[RefM] final case class Bundle[A, B](
    var interrupted: Option[List[Throwable]] = None,
    update: A => IO[Nothing, (B, A)],
    promise: Promise[Nothing, B]
  ) {
    final def run(a: A): IO[List[Throwable], A] =
      interrupted match {
        case Some(ts) => IO.fail(ts)
        case None     => update(a).flatMap(t => promise.complete(t._1).map(_ => t._2))
      }
  }

  /**
   * Creates a new `RefM` with the specified value.
   */
  final def apply[A](
    a: A,
    n: Int = 1000,
    onDefect: List[Throwable] => IO[Nothing, Unit] = _ => IO.unit
  ): IO[Nothing, RefM[A]] =
    for {
      ref   <- Ref(a)
      queue <- Queue.bounded[Bundle[A, _]](n)
      _     <- queue.take.flatMap(b => ref.get.flatMap(a => b.run(a).redeem(onDefect, ref.set))).forever.fork
    } yield new RefM[A](ref, queue)
}
