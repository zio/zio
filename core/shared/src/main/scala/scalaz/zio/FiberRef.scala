package scalaz.zio

import scalaz.zio

/**
 * TODO: improve
 * Fiber's counterpart for [[ThreadLocal]]. Value is automatically propagated
 * to child on fork and merged back in after joining child.
 * {{{
 * for {
 *   fiberRef <- FiberRef.make("Hello world!")
 *   child <- fiberRef.set("Hi!).fork
 *   result <- child.join
 * } yield result
 * }}}
 *
 * `result` will be equal to "Hi!" as changes done by child were merged on join.
 *
 * @param initial
 * @tparam A
 */
final class FiberRef[A](private[zio] val initial: A) extends Serializable {

  /**
   * Reads the value associated with the current fiber. Returns initial value if
   * no value was `set` or inherited from parent.
   */
  final val get: UIO[A] = new ZIO.FiberRefModify[A, A](this, v => (v, v))

  /**
   * Sets the value associated with the current fiber.
   */
  final def set(value: A): UIO[Unit] = new ZIO.FiberRefModify[A, Unit](this, _ => ((), value))

  /**
   * Returns an `IO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber data is properly restored via `bracket`.
   */
  final def locally[R, E, B](value: A)(use: ZIO[R, E, B]): ZIO[R, E, B] =
    for {
      oldValue <- get
      b        <- set(value).bracket_(set(oldValue))(use)
    } yield b

}

object FiberRef extends Serializable {

  /**
   * Creates a new `FiberRef` with given initial value.
   */
  def make[A](initialValue: A): UIO[FiberRef[A]] = new zio.ZIO.FiberRefNew(initialValue)
}
