package zio.internal 

import zio.{ChunkBuilder, ZIO}
import zio.ZIO.EvaluationStep 

import scala.util.control.NoStackTrace

sealed abstract class ReifyStack extends Exception with NoStackTrace { self =>
  def addContinuation(continuation: EvaluationStep.Continuation[_, _, _, _, _]): Nothing =
    self.addAndThrow(continuation)

  def changeInterruptibility(interruptible: Boolean): Nothing =
    self.addAndThrow(EvaluationStep.ChangeInterruptibility(interruptible))

  def stack: ChunkBuilder[EvaluationStep]

  private final def addAndThrow(k: EvaluationStep): Nothing = {
    stack += (k)
    throw this
  }
}
object ReifyStack {
  final case class AsyncJump(
    registerCallback: (ZIO[Any, Any, Any] => Unit) => Unit,
    stack: ChunkBuilder[EvaluationStep]
  ) extends ReifyStack

  final case class Trampoline(effect: ZIO[Any, Any, Any], stack: ChunkBuilder[EvaluationStep]) extends ReifyStack

  final case class GenerateTrace(stack: ChunkBuilder[EvaluationStep]) extends ReifyStack
}