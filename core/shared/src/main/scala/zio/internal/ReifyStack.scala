package zio.internal

import zio._
import zio.ZIO.EvaluationStep

import scala.util.control.NoStackTrace

sealed abstract class ReifyStack extends Exception with NoStackTrace { self =>
  def addContinuation(continuation: EvaluationStep.Continuation[_, _, _, _, _]): Nothing =
    self.addAndThrow(continuation)

  def updateRuntimeFlags(update: RuntimeFlags.Patch): Nothing =
    self.addAndThrow(EvaluationStep.UpdateRuntimeFlags(update))

  def runtimeFlags: RuntimeFlags

  def stack: ChunkBuilder[EvaluationStep]

  private final def addAndThrow(k: EvaluationStep): Nothing = {
    stack += (k)
    throw this
  }
}
object ReifyStack {
  final case class AsyncJump(
    registerCallback: (ZIO[Any, Any, Any] => Unit) => Any,
    stack: ChunkBuilder[EvaluationStep],
    runtimeFlags: RuntimeFlags,
    trace: Trace,
    blockingOn: FiberId
  ) extends ReifyStack

  final case class Trampoline(
    effect: ZIO[Any, Any, Any],
    stack: ChunkBuilder[EvaluationStep],
    runtimeFlags: RuntimeFlags
  ) extends ReifyStack

  final case class GenerateTrace(stack: ChunkBuilder[EvaluationStep], runtimeFlags: RuntimeFlags) extends ReifyStack
}
