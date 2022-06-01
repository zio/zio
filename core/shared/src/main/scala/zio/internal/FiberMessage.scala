package zio.internal

import zio._

sealed trait FiberMessage
object FiberMessage {
  final case class InterruptSignal(cause: Cause[Nothing])                            extends FiberMessage
  final case class GenStackTrace(onTrace: StackTrace => Unit)                        extends FiberMessage
  final case class Stateful(onFiber: (FiberRuntime[Any, Any], Fiber.Status) => Unit) extends FiberMessage
  final case class Resume(zio: ZIO[Any, Any, Any], stack: Chunk[ZIO.EvaluationStep], interruptible: Boolean)
      extends FiberMessage
}
