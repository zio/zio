package zio.test.sbt

import sbt.testing.{EventHandler, Status, TaskDef}
import zio.{UIO, ZIO}
import zio.test.{ExecutionEvent, TestAnnotation, TestFailure, ZTestEventHandler}

import java.nio.file.StandardOpenOption

/**
 * Reports test results to SBT, ensuring that the `test` task fails if any ZIO
 * test instances fail
 *
 * @param eventHandler
 *   The underlying handler provided by SBT
 * @param taskDef
 *   The test task that we are reporting for
 */
class ZTestEventHandlerSbt(eventHandler: EventHandler, taskDef: TaskDef) extends ZTestEventHandler {
  def handle(event: ExecutionEvent): UIO[Unit] =
    event match {
      case evt @ ExecutionEvent.Test(_, _, _, _, _, _) =>
        writeTestResultsToFile(evt) *>
        ZIO.succeed(eventHandler.handle(ZTestEvent.convertEvent(evt, taskDef)))
      case ExecutionEvent.SectionStart(_, _, _) => ZIO.unit
      case ExecutionEvent.SectionEnd(_, _, _)   => ZIO.unit
      case ExecutionEvent.TopLevelFlush(_)      => ZIO.unit
      case ExecutionEvent.RuntimeFailure(_, _, failure, _) =>
        failure match {
          case TestFailure.Assertion(_, _) => ZIO.unit // Assertion failures all come through Execution.Test path above
          case TestFailure.Runtime(cause, annotations) =>
            val zTestEvent = ZTestEvent(
              taskDef.fullyQualifiedName(),
              taskDef.selectors().head,
              Status.Failure,
              cause.dieOption,
              annotations.get(TestAnnotation.timing).toMillis,
              ZioSpecFingerprint
            )
            ZIO.succeed(eventHandler.handle(zTestEvent))
        }

    }

  private def writeTestResultsToFile[E](test: ExecutionEvent.Test[E]) = ZIO.succeed {
    import java.nio.file.{Paths, Files}
    import java.nio.charset.StandardCharsets

    val path = Paths.get("file.txt")

    if (!Files.exists(path))
      Files.createFile(path)

    // TODO Write as JSON. Decide if we want everything in the event serialized.
    val testOutput = test.annotations.get(TestAnnotation.output)

    val serialized =
      test.labels.mkString(" - ") + "\n" +
        testOutput.mkString("\n") + "\n\n"

    Files.write(path, serialized.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND)

  }
}
