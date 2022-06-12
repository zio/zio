package zio.internal

import zio._

/**
 * Fiber messages are low-level messages processed by the fiber runtime. They
 * are processed in two modes: either while the fiber is suspended, in which
 * case one message (FiberMessage.Resume) can wake the fiber up, or while the
 * fiber is running.
 */
sealed trait FiberMessage
object FiberMessage {
  final case class InterruptSignal(cause: Cause[Nothing])                            extends FiberMessage
  final case class GenStackTrace(onTrace: StackTrace => Unit)                        extends FiberMessage
  final case class Stateful(onFiber: (FiberRuntime[Any, Any], Fiber.Status) => Unit) extends FiberMessage
  final case class Resume(zio: ZIO[Any, Any, Any], stack: Chunk[ZIO.EvaluationStep]) extends FiberMessage
  case object YieldNow extends FiberMessage 
}
