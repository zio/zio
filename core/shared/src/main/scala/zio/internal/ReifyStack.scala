package zio.internal

import zio._

import scala.util.control.NoStackTrace

private[zio] sealed abstract class ReifyStack extends Exception with NoStackTrace
private[zio] object ReifyStack {
  case object AsyncJump extends ReifyStack

  final case class Trampoline(
    effect: ZIO[Any, Any, Any],
    forceYield: Boolean
  ) extends ReifyStack

  case object GenerateTrace extends ReifyStack
}
