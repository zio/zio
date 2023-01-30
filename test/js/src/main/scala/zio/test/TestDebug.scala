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
    import scala.scalajs.js
    import js.annotation._
    import js.|

    @JSExportTopLevel("FileWriter")
    class FileWriter(file: Blob) {
      window.
      private val fileWriter = new js.Dynamic {

        def onwriteend(event: ProgressEvent): Unit = {}

        def onerror(event: ProgressEvent): Unit = {}
      }

      def write(text: String): Unit = {
        val writer = new FileWriter(fileWriter)
        writer.onwriteend = (event: ProgressEvent) => {
          console.log("Write completed.")
        }
        writer.onerror = (event: ProgressEvent) => {
          console.error("Write failed: " + writer.error)
        }
        writer.write(text)
      }
    }


  }
//    ZIO.unit

}
