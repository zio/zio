package zio.test.sbt

import sbt.testing.{Event, EventHandler, TaskDef}
import zio.test.render.TestRenderer
import zio.{Semaphore, UIO, Unsafe, ZIO}
import zio.test.{ExecutionEvent, TestFailure, ZTestEventHandler}

/**
 * Reports ZIO Test results to sbt. It converts each test result and each
 * runtime failure (`TestFailure.Runtime`) to an sbt event with [[ZTestEvent]],
 * and passes the events to sbt's `EventHandler` one at a time. Failed tests are
 * reported with `Status.Failure`, which sbt counts as failed tests. Other
 * execution events are not reported.
 *
 * @param eventHandler
 *   the event handler that sbt provides
 * @param taskDef
 *   the task definition of the spec whose results are reported
 * @param renderer
 *   renders the message of each failed test
 */
final class ZTestEventHandlerSbt(
  eventHandler: EventHandler,
  taskDef: TaskDef,
  renderer: TestRenderer
) extends ZTestEventHandler {
  private val semaphore: Semaphore = Semaphore.unsafe.make(1L)(Unsafe)

  /**
   * Converts `executionEvent` to an sbt event and passes it to sbt, or does
   * nothing when the event is not a test result or a runtime failure.
   */
  override def handle(executionEvent: ExecutionEvent): UIO[Unit] = {
    val event: Option[Event] = executionEvent match {
      case test @ ExecutionEvent.Test(_, _, _, _, _, _, _) =>
        Some(ZTestEvent.convertTestEvent(test, taskDef, renderer))
      case ExecutionEvent.RuntimeFailure(_, _, failure @ TestFailure.Runtime(_, _), _) =>
        Some(ZTestEvent.convertRuntimeFailure(failure, taskDef))
      case _ =>
        None
    }

    event
      .map(event => semaphore.withPermit(ZIO.succeed(eventHandler.handle(event))))
      .getOrElse(ZIO.unit)
  }
}
