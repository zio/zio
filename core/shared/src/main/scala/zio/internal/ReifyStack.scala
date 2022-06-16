package zio.internal

import zio._
import zio.ZIO.EvaluationStep

import scala.util.control.NoStackTrace

private[zio] sealed abstract class ReifyStack extends Exception with NoStackTrace { self =>
  def updateRuntimeFlags(update: RuntimeFlags.Patch): Nothing =
    self.addAndThrow(EvaluationStep.UpdateRuntimeFlags(update))

  def stack: ChunkBuilder[EvaluationStep]

  final def addAndThrow(k: EvaluationStep): Nothing = {
    stack += (k)
    throw this
  }
}
object ReifyStack {
  final case class AsyncJump(
    registerCallback: (ZIO[Any, Any, Any] => Unit) => Any,
    stack: ChunkBuilder[EvaluationStep],
    trace: Trace,
    blockingOn: FiberId
  ) extends ReifyStack

  final case class Trampoline(
    effect: ZIO[Any, Any, Any],
    stack: ChunkBuilder[EvaluationStep],
    forceYield: Boolean
  ) extends ReifyStack

  final case class GenerateTrace(stack: ChunkBuilder[EvaluationStep]) extends ReifyStack
}
