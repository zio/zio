package scalaz.zio

import FiberLocal.internal._

/**
 * A container for fiber-local storage. It is the pure equivalent to Java's `ThreadLocal`
 * on a fiber architecture.
 */
final class FiberLocal[A] private (private val state: Ref[State[A]]) extends Serializable {

  /**
   * Reads the value associated with the current fiber.
   */
  final def get: IO[Nothing, Option[A]] =
    for {
      descriptor <- IO.descriptor
      value      <- state.get
    } yield value.get(descriptor.id)

  /**
   * Sets the value associated with the current fiber.
   */
  final def set(value: A): IO[Nothing, Unit] =
    for {
      descriptor <- IO.descriptor
      _          <- state.update(_ + (descriptor.id -> value))
    } yield ()

  /**
   * Empties the value associated with the current fiber.
   */
  final def empty: IO[Nothing, Unit] =
    for {
      descriptor <- IO.descriptor
      _          <- state.update(_ - descriptor.id)
    } yield ()

  /**
   * Returns an `IO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber-local data is properly freed via `bracket`.
   */
  final def locally[E, B](value: A)(use: IO[E, B]): IO[E, B] =
    set(value).bracket(_ => empty)(_ => use)

}

object FiberLocal {

  /**
   * Creates a new `FiberLocal`.
   */
  final def make[A]: IO[Nothing, FiberLocal[A]] =
    Ref
      .make[internal.State[A]](Map())
      .map(state => new FiberLocal(state))

  private[zio] object internal {
    type State[A] = Map[FiberId, A]
  }

}
