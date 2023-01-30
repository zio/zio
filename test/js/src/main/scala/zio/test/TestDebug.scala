package zio.test

import org.scalajs.dom.{Blob, ProgressEvent, window}
import zio.{Ref, ZIO}

private[test] object TestDebug {
  val outputFile = "target/test-reports-zio/debug.txt"
  def printEmergency(executionEvent: ExecutionEvent, lock: Ref.Synchronized[Unit]) =
    executionEvent match {
      case t @ ExecutionEvent.TestStarted(
            labelsReversed,
            annotations,
            ancestors,
            id,
            fullyQualifiedName
          ) =>
        write(s"${t.labels.mkString(" - ")} STARTED\n", true, lock)

      case t @ ExecutionEvent.Test(labelsReversed, test, annotations, ancestors, duration, id, fullyQualifiedName) =>
        removeLine(t.labels.mkString(" - ") + " STARTED")

      case ExecutionEvent.SectionStart(labelsReversed, id, ancestors) => ZIO.unit
      case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors)   => ZIO.unit
      case ExecutionEvent.TopLevelFlush(id) =>
        ZIO.unit
      case ExecutionEvent.RuntimeFailure(id, labelsReversed, failure, ancestors) =>
        ZIO.unit
    }

  // TODO Implement this with appropriate JS filesystem APIs after JVM version is finalized
  def write(content: => String, append: Boolean, lock: Ref.Synchronized[Unit]): ZIO[Any, Nothing, Unit] = {
    ZIO.unit
  }

  private def removeLine(searchString: String) = ZIO.attempt{
  }.orDie
//    ZIO.unit

}
