package zio.test.sbt

import sbt.testing.{EventHandler, Status, TaskDef}
import zio.test.render.TestRenderer
import zio.{Semaphore, UIO, Unsafe, ZIO}
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
class ZTestEventHandlerSbt(eventHandler: EventHandler, taskDef: TaskDef, renderer: TestRenderer)
    extends ZTestEventHandler {
  val semaphore = Semaphore.unsafe.make(1L)(Unsafe.unsafe)
  def handle(event: ExecutionEvent): UIO[Unit] =
    event match {
      // TODO Is there a non-sbt version of this I need to add similar handling to?
      case evt @ ExecutionEvent.TestStarted(_, _, _, _, _) =>
        ZIO.unit
      case evt @ ExecutionEvent.Test(_, _, _, _, _, _, _) =>
        val zTestEvent = ZTestEvent.convertEvent(evt, taskDef, renderer)
        semaphore.withPermit(ZIO.succeed(eventHandler.handle(zTestEvent)))
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
            semaphore.withPermit(ZIO.succeed(eventHandler.handle(zTestEvent)))
        }

    }
}
