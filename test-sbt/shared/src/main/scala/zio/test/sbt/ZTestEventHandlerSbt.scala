package zio.test.sbt

import sbt.testing.{EventHandler, Status, TaskDef}
import zio.test.render.TestRenderer
import zio.{UIO, ZIO}
import zio.test.{ExecutionEvent, TestAnnotation, TestFailure, ZTestEventHandler}

/**
 * Reports test results to SBT, ensuring that the `test` task fails if any ZIO
 * test instances fail
 *
 * @param eventHandler
 *   The underlying handler provided by SBT
 * @param taskDef
 *   The test task that we are reporting for
 */
class ZTestEventHandlerSbt(eventHandler: EventHandler, taskDef: TaskDef, renderer: TestRenderer) extends ZTestEventHandler {
  def handle(event: ExecutionEvent): UIO[Unit] = {
    for {
      // TODO Delete when confirmed that TestArgs are the way to go here.
//      rendererConfigSelection <- zio.System.envOrElse("ZIO_TEST_RENDERER", "DEFAULT").debug("Renderer").orDie
      res <- event match {
        case evt@ExecutionEvent.Test(_, _, _, _, _, _) =>
          ZIO.succeed(eventHandler.handle(ZTestEvent.convertEvent(evt, taskDef, renderer)))
        case ExecutionEvent.SectionStart(_, _, _) => ZIO.unit
        case ExecutionEvent.SectionEnd(_, _, _) => ZIO.unit
        case ExecutionEvent.TopLevelFlush(_) => ZIO.unit
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
    }
    yield res
  }
}
