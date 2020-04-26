/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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
   *
   * @return `UIO[A]` value from the `Ref`
   */
  def get: UIO[A] = value.get

  /**
   * Atomically modifies the `RefM` with the specified function, returning the
   * value immediately before modification.
   *
   * @param f function to atomically modify the `RefM`
   * @tparam R environment of the effect
   * @tparam E error type
   * @return `ZIO[R, E, A]` value of the `RefM` immediately before modification
   */
  def getAndUpdate[R, E](f: A => ZIO[R, E, A]): ZIO[R, E, A] =
    modify(a => f(a).map((a, _)))

  /**
   * Writes a new value to the `RefM`, returning the value immediately before
   * modification.
   *
   * @param a value to be written to the `RefM`
   * @return `UIO[A]` value of the `RefM` immediately before modification
   */
  def getAndSet(a: A): UIO[A] =
    value.getAndSet(a)

  /**
   * Atomically modifies the `RefM` with the specified partial function,
   * returning the value immediately before modification.
   * If the function is undefined on the current value it doesn't change it.
   *
   * @param pf partial function to atomically modify the `RefM`
   * @tparam R environment of the effect
   * @tparam E error type
   * @return `ZIO[R, E, A]` value of the `RefM` immediately before modification
   */
  def getAndUpdateSome[R, E](pf: PartialFunction[A, ZIO[R, E, A]]): ZIO[R, E, A] =
    modify(a => pf.applyOrElse(a, (_: A) => IO.succeedNow(a)).map((a, _)))

  /**
   * Atomically modifies the `RefM` with the specified function, which computes
   * a return value for the modification. This is a more powerful version of
   * `update`.
   *
   * @param f function which computes a return value for the modification
   * @tparam R environment of the effect
   * @tparam E error type
   * @tparam B type of the `RefM`
   * @return `ZIO[R, E, B]` modified value of the `RefM`
   */
  def modify[R, E, B](f: A => ZIO[R, E, (B, A)]): ZIO[R, E, B] =
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
   *
   * @param default default value to be returned if the partial function is not defined on the current value
   * @param pf partial function to be computed on the current value if defined on the current value
   * @tparam R environment of the effect
   * @tparam E error type
   * @tparam B type of the `RefM`
   * @return `ZIO[R, E, B]` modified value of the `RefM`
   */
  def modifySome[R, E, B](default: B)(pf: PartialFunction[A, ZIO[R, E, (B, A)]]): ZIO[R, E, B] =
    for {
      promise <- Promise.make[E, B]
      ref     <- Ref.make[Option[Cause[Nothing]]](None)
      env     <- ZIO.environment[R]
      bundle = RefM.Bundle[E, A, B](
        ref,
        pf.andThen(_.provide(env)).orElse[A, IO[E, (B, A)]] { case a => IO.succeedNow(default -> a) },
        promise
      )
      b <- (for {
            _ <- queue.offer(bundle)
            b <- promise.await
          } yield b).onTermination(cause => bundle.interrupted.set(Some(cause)))
    } yield b

  /**
   * Writes a new value to the `RefM`, with a guarantee of immediate
   * consistency (at some cost to performance).
   *
   * @param a value to be written to the `RefM`
   * @return `UIO[Unit]`
   */
  def set(a: A): UIO[Unit] = value.set(a)

  /**
   * Writes a new value to the `Ref` without providing a guarantee of
   * immediate consistency.
   *
   * @param a value to be written to the `RefM`
   * @return `UIO[Unit]`
   */
  def setAsync(a: A): UIO[Unit] = value.setAsync(a)

  /**
   * Atomically modifies the `RefM` with the specified function.
   *
   * @param f function to atomically modify the `RefM`
   * @tparam R environment of the effect
   * @tparam E error type
   * @return `ZIO[R, E, Unit]`
   */
  def update[R, E](f: A => ZIO[R, E, A]): ZIO[R, E, Unit] =
    modify(a => f(a).map(a => ((), a)))

  /**
   * Atomically modifies the `RefM` with the specified function, returning the
   * value immediately after modification.
   *
   * @param f function to atomically modify the `RefM`
   * @tparam R environment of the effect
   * @tparam E error type
   * @return `ZIO[R, E, A]` modified value of the `RefM`
   */
  def updateAndGet[R, E](f: A => ZIO[R, E, A]): ZIO[R, E, A] =
    modify(a => f(a).map(a => (a, a)))

  /**
   * Atomically modifies the `RefM` with the specified partial function.
   * If the function is undefined on the current value it doesn't change it.
   *
   * @param pf partial function to atomically modify the `RefM`
   * @tparam R environment of the effect
   * @tparam E error type
   * @return `ZIO[R, E, Unit]`
   */
  def updateSome[R, E](pf: PartialFunction[A, ZIO[R, E, A]]): ZIO[R, E, Unit] =
    modify(a => pf.applyOrElse(a, (_: A) => IO.succeedNow(a)).map(a => ((), a)))

  /**
   * Atomically modifies the `RefM` with the specified partial function.
   * If the function is undefined on the current value it returns the old value
   * without changing it.
   *
   * @param pf partial function to atomically modify the `RefM`
   * @tparam R environment of the effect
   * @tparam E error type
   * @return `ZIO[R, E, A]` modified value of the `RefM`
   */
  def updateSomeAndGet[R, E](pf: PartialFunction[A, ZIO[R, E, A]]): ZIO[R, E, A] =
    modify(a => pf.applyOrElse(a, (_: A) => IO.succeedNow(a)).map(a => (a, a)))
}

object RefM extends Serializable {
  private[RefM] final case class Bundle[E, A, B](
    interrupted: Ref[Option[Cause[Nothing]]],
    update: A => IO[E, (B, A)],
    promise: Promise[E, B]
  ) {
    def run(a: A, ref: Ref[A], onDefect: Cause[E] => UIO[Unit]): UIO[Unit] =
      interrupted.get.flatMap {
        case Some(cause) => onDefect(cause)
        case None =>
          update(a).foldCauseM(c => onDefect(c).ensuring_(promise.halt(c)), {
            case (b, a) => ref.set(a) <* promise.succeed(b)
          })
      }
  }

  /**
   * Creates a new `RefM` with the specified value.
   *
   * @param a value of the new `RefM`
   * @param n requested capacity for the bounded queue
   * @param onDefect function that handles defects
   * @tparam A type of the value
   * @return `UIO[RefM[A]]`
   */
  def make[A](
    a: A,
    n: Int = 1000,
    onDefect: Cause[Any] => UIO[Unit] = _ => IO.unit
  ): UIO[RefM[A]] =
    for {
      ref   <- Ref.make(a)
      queue <- Queue.bounded[Bundle[_, A, _]](n)
      _     <- queue.take.flatMap(b => ref.get.flatMap(a => b.run(a, ref, onDefect))).forever.forkDaemon
    } yield new RefM[A](ref, queue)

}
