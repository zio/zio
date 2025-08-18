package zio.test.sbt

import sbt.testing.{Event, EventHandler, TaskDef}
import zio.test.render.TestRenderer
import zio.ZIO
import zio.test.{ExecutionEvent, TestFailure, ZTestEventHandler}

/**
 * Reports test results to SBT, ensuring that the `test` task fails if any ZIO
 * test instances fail
 *
 * @param eventHandler
 *   The underlying handler provided by SBT
 * @param taskDef
 *   The test task that we are reporting for
 */
final class ZTestEventHandlerSbt(
  eventHandler: EventHandler,
  taskDef: TaskDef,
  renderer: TestRenderer
) extends ZTestEventHandler {
  private val semaphore: zio.Semaphore = zio.Semaphore.unsafe.make(1L)(zio.Unsafe)
  private def forward(event: Event): ZIO[Any, Nothing, Unit] =
    semaphore.withPermit(ZIO.succeed(eventHandler.handle(event)))

  override def handle(event: ExecutionEvent): zio.UIO[Unit] =
    event match {
      // TODO Is there a non-sbt version of this I need to add similar handling to?
      case ExecutionEvent.TestStarted(_, _, _, _, _) => ZIO.unit
      case test @ ExecutionEvent.Test(_, _, _, _, _, _, _) =>
        forward(ZTestEvent.convertTestEvent(test, taskDef, renderer))
      case ExecutionEvent.SectionStart(_, _, _) => ZIO.unit
      case ExecutionEvent.SectionEnd(_, _, _)   => ZIO.unit
      case ExecutionEvent.TopLevelFlush(_)      => ZIO.unit
      case ExecutionEvent.RuntimeFailure(_, _, failure, _) =>
        failure match {
          case TestFailure.Assertion(_, _) => ZIO.unit // Assertion failures all come through Execution.Test path above
          case failure @ TestFailure.Runtime(_, _) =>
            forward(ZTestEvent.convertRuntimeFailure(failure, taskDef))
        }
    }
}
