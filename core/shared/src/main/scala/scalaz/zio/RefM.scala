// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio
import scalaz.zio.Exit.Cause

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
 *   v   <- ref.update(_ + putStrLn("Hello World!").attempt.void *> IO.succeed(3))
 *   _   <- putStrLn("Value = " + v) // Value = 5
 * } yield ()
 * }}}
 */
final class RefM[A] private (value: Ref[A], queue: Queue[RefM.Bundle[A, _]]) extends Serializable {

  /**
   * Reads the value from the `Ref`.
   */
  final def get: UIO[A] = value.get

  /**
   * Writes a new value to the `Ref`, with a guarantee of immediate
   * consistency (at some cost to performance).
   */
  final def set(a: A): UIO[Unit] = value.set(a)

  /**
   * Writes a new value to the `Ref` without providing a guarantee of
   * immediate consistency.
   */
  final def setAsync(a: A): UIO[Unit] = value.setAsync(a)

  /**
   * Atomically modifies the `RefM` with the specified function, returning the
   * value immediately after modification.
   */
  final def update(f: A => UIO[A]): UIO[A] =
    modify(a => f(a).map(a => (a, a)))

  /**
   * Atomically modifies the `RefM` with the specified partial function.
   * if the function is undefined in the current value it returns the old value without changing it.
   */
  final def updateSome(pf: PartialFunction[A, UIO[A]]): UIO[A] =
    modify(a => pf.applyOrElse(a, (_: A) => IO.succeed(a)).map(a => (a, a)))

  /**
   * Atomically modifies the `RefM` with the specified function, which computes
   * a return value for the modification. This is a more powerful version of
   * `update`.
   */
  final def modify[B](f: A => UIO[(B, A)]): UIO[B] =
    for {
      promise <- Promise.make[Nothing, B]
      ref     <- Ref.make[Option[Cause[Nothing]]](None)
      bundle  = RefM.Bundle(ref, f, promise)
      b <- (for {
            _ <- queue.offer(bundle)
            b <- promise.await
          } yield b).onTermination(cause => bundle.interrupted.set(Some(cause)))
    } yield b

  /**
   * Atomically modifies the `RefM` with the specified function, which computes
   * a return value for the modification if the function is defined in the current value
   * otherwise it returns a default value.
   * This is a more powerful version of `updateSome`.
   */
  final def modifySome[B](default: B)(pf: PartialFunction[A, UIO[(B, A)]]): UIO[B] =
    for {
      promise <- Promise.make[Nothing, B]
      ref     <- Ref.make[Option[Cause[Nothing]]](None)
      bundle  = RefM.Bundle(ref, pf.orElse[A, UIO[(B, A)]] { case a => IO.succeed(default -> a) }, promise)
      b <- (for {
            _ <- queue.offer(bundle)
            b <- promise.await
          } yield b).onTermination(cause => bundle.interrupted.set(Some(cause)))
    } yield b
}

object RefM extends Serializable {
  private[RefM] final case class Bundle[A, B](
    interrupted: Ref[Option[Cause[Nothing]]],
    update: A => UIO[(B, A)],
    promise: Promise[Nothing, B]
  ) {
    final def run(a: A, ref: Ref[A], onDefect: Cause[Nothing] => UIO[Unit]): UIO[Unit] =
      interrupted.get.flatMap {
        case Some(cause) => onDefect(cause)
        case None =>
          update(a).sandbox.redeem(onDefect, {
            case (b, a) => ref.set(a) <* promise.succeed(b)
          })
      }
  }

  /**
   * Creates a new `RefM` with the specified value.
   */
  final def make[A](
    a: A,
    n: Int = 1000,
    onDefect: Cause[Nothing] => UIO[Unit] = _ => IO.unit
  ): UIO[RefM[A]] =
    for {
      ref   <- Ref.make(a)
      queue <- Queue.bounded[Bundle[A, _]](n)
      _     <- queue.take.flatMap(b => ref.get.flatMap(a => b.run(a, ref, onDefect))).forever.fork
    } yield new RefM[A](ref, queue)
}
