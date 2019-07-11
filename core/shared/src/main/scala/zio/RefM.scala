/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

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
 *   v   <- ref.update(_ + putStrLn("Hello World!").attempt.unit *> IO.succeed(3))
 *   _   <- putStrLn("Value = " + v) // Value = 5
 * } yield ()
 * }}}
 */
final class RefM[A] private (value: Ref[A], queue: Queue[RefM.Bundle[_, A, _]]) extends Serializable {

  /**
   * Reads the value from the `Ref`.
   */
  final def get: UIO[A] = value.get

  /**
   * Atomically modifies the `RefM` with the specified function, which computes
   * a return value for the modification. This is a more powerful version of
   * `update`.
   */
  final def modify[R, E, B](f: A => ZIO[R, E, (B, A)]): ZIO[R, E, B] =
    for {
      promise <- Promise.make[E, B]
      ref     <- Ref.make[Option[Cause[Nothing]]](None)
      env     <- ZIO.environment[R]
      bundle  = RefM.Bundle(ref, f.andThen(_.provide(env)), promise)
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
  final def modifySome[R, E, B](default: B)(pf: PartialFunction[A, ZIO[R, E, (B, A)]]): ZIO[R, E, B] =
    for {
      promise <- Promise.make[E, B]
      ref     <- Ref.make[Option[Cause[Nothing]]](None)
      env     <- ZIO.environment[R]
      bundle = RefM.Bundle[E, A, B](
        ref,
        pf.andThen(_.provide(env)).orElse[A, IO[E, (B, A)]] { case a => IO.succeed(default -> a) },
        promise
      )
      b <- (for {
            _ <- queue.offer(bundle)
            b <- promise.await
          } yield b).onTermination(cause => bundle.interrupted.set(Some(cause)))
    } yield b

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
  final def update[R, E](f: A => ZIO[R, E, A]): ZIO[R, E, A] =
    modify(a => f(a).map(a => (a, a)))

  /**
   * Atomically modifies the `RefM` with the specified partial function.
   * if the function is undefined in the current value it returns the old value without changing it.
   */
  final def updateSome[R, E](pf: PartialFunction[A, ZIO[R, E, A]]): ZIO[R, E, A] =
    modify(a => pf.applyOrElse(a, (_: A) => IO.succeed(a)).map(a => (a, a)))
}

object RefM extends Serializable {
  private[RefM] final case class Bundle[E, A, B](
    interrupted: Ref[Option[Cause[Nothing]]],
    update: A => IO[E, (B, A)],
    promise: Promise[E, B]
  ) {
    final def run(a: A, ref: Ref[A], onDefect: Cause[E] => UIO[Unit]): UIO[Unit] =
      interrupted.get.flatMap {
        case Some(cause) => onDefect(cause)
        case None =>
          update(a).foldM(e => onDefect(Cause.fail(e)) <* promise.fail(e), {
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
    onDefect: Cause[_] => UIO[Unit] = _ => IO.unit
  ): UIO[RefM[A]] =
    for {
      ref   <- Ref.make(a)
      queue <- Queue.bounded[Bundle[_, A, _]](n)
      _     <- queue.take.flatMap(b => ref.get.flatMap(a => b.run(a, ref, onDefect))).forever.fork
    } yield new RefM[A](ref, queue)

}
