package zio.stream.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream._

private sealed trait ChannelFiberMessage

private object ChannelFiberMessage {

  sealed trait Write extends ChannelFiberMessage

  final case class AddObserver(observer: Exit[Any, Any] => Unit, epoch: Long)    extends ChannelFiberMessage
  final case class RemoveObserver(observer: Exit[Any, Any] => Unit, epoch: Long) extends ChannelFiberMessage
  final case class Interrupt(cause: Cause[Nothing])                              extends ChannelFiberMessage
  final case class Read(onErr: Cause[Any] => Unit, onElem: Any => Unit, onDone: Any => Unit, epoch: Long)
      extends ChannelFiberMessage
  final case class WriteElem(elem: Any, read: () => Unit) extends Write
  final case class WriteErr(err: Cause[Any])              extends Write
  final case class WriteDone(done: Any)                   extends Write
  final case class Resume(channel: ZChannel[Any, Any, Any, Any, Any, Any, Any], fiberRefs: FiberRefs, epoch: Long)
      extends ChannelFiberMessage
  case object Start extends ChannelFiberMessage
  final case class Stateful(
    onFiber: (ChannelFiberRuntime[Nothing, Nothing, Nothing, Any, Any, Any], Fiber.Status) => Unit
  ) extends ChannelFiberMessage
  final case class YieldNow(fiberRefs: FiberRefs, epoch: Long) extends ChannelFiberMessage
}
