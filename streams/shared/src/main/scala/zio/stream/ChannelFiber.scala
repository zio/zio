package zio.stream

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

sealed trait ChannelFiber[-InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone]
    extends Fiber.Internal[OutErr, OutDone] {
  def readZIO(implicit trace: Trace): ZIO[Any, OutErr, Either[OutDone, OutElem]]
  def writeElem(elem: InElem)(implicit trace: Trace): ZIO[Any, Nothing, Unit]
  def writeErr(err: Cause[InErr])(implicit trace: Trace): ZIO[Any, Nothing, Unit]
  def writeDone(done: InDone)(implicit trace: Trace): ZIO[Any, Nothing, Unit]
}

object ChannelFiber {

  sealed abstract class Runtime[-InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone]
      extends Fiber.Runtime.Internal[OutErr, OutDone]
      with ChannelFiber[InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[stream] object Runtime {
    abstract class Internal[-InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone]
        extends Runtime[InErr, InElem, InDone, OutErr, OutElem, OutDone]
  }
}
