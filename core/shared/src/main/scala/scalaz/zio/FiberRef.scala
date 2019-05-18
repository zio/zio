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

  private[this] val read = new ZIO.FiberRefGet[A](this)

  private def write(value: A, fiberId: FiberId) = new ZIO.FiberRefSet[A](this, value, fiberId)

  /**
   * Reads the value associated with the current fiber. Returns initial value if
   * no value was `set` or inherited from parent.
   */
  final val get: UIO[A] = read.map(_.map(_._1).getOrElse(initial))

  /**
   * Sets the value associated with the current fiber.
   */
  final def set(value: A): UIO[Unit] =
    for {
      descriptor <- ZIO.descriptor
      _          <- write(value, descriptor.id)
    } yield ()

  /**
   * Returns an `IO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber data is properly restored via `bracket`.
   */
  final def locally[R, E, B](value: A)(use: ZIO[R, E, B]): ZIO[R, E, B] = {
    // let's write initial value to fiber's locals map if there is no record
    val readWithDefault: UIO[(A, FiberId)] = read.flatMap {
      case Some(pair) => ZIO.succeed(pair)
      case None       => ZIO.descriptor.map(descriptor => (initial, descriptor.id))
    }
    readWithDefault.bracket(pair => write(pair._1, pair._2))(_ => set(value) *> use)
  }

}

object FiberRef extends Serializable {

  /**
   * Creates a new `FiberRef` with given initial value.
   */
  def make[A](initialValue: A): UIO[FiberRef[A]] = new zio.ZIO.FiberRefNew(initialValue)
}
