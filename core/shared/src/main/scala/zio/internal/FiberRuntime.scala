// TODO: Investigate merging FiberRuntime and Promise (#9877)
package zio.internal

import zio._

private[zio] trait FiberRuntime[E, A] extends Fiber.Runtime[E, A] {
  // FiberRuntime implementation details
}
