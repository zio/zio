package zio.test.sbt

import sbt.testing.{Event, EventHandler, TaskDef}
import zio.test.render.TestRenderer
import zio.{Semaphore, UIO, Unsafe, ZIO}
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
  private val semaphore: Semaphore = Semaphore.unsafe.make(1L)(Unsafe)

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
