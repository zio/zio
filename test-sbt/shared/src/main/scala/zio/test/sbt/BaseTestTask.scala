package zio.test.sbt

import sbt.testing.{Event, EventHandler, Logger, Status, Task, TaskDef}
import zio.{CancelableFuture, Console, Runtime, Scope, Trace, UIO, Unsafe, ZEnvironment, ZIO, ZIOAppArgs, ZLayer}
import zio.test.render.ConsoleRenderer
import zio.test._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import zio.stacktracer.TracingImplicits.disableAutoTrace

abstract class BaseTestTask[T](
  val taskDef: TaskDef,
  val testClassLoader: ClassLoader,
  val sendSummary: SendSummary,
  val args: TestArgs,
  val spec: ZIOSpecAbstract,
  val runtime: zio.Runtime[T]
) extends Task {

  protected def sharedFilledTestLayer(implicit
    trace: Trace
  ): ZLayer[Any, Nothing, TestEnvironment with ZIOAppArgs with Scope] = {
    ZIOAppArgs.empty +!+ (
      (liveEnvironment ++ Scope.default) >>> TestEnvironment.live
    )
  } +!+ Scope.default

  private[zio] def run(
    eventHandlerZ: ZTestEventHandler
  )(implicit trace: Trace): ZIO[Any, Throwable, Unit] =
    (for {
      summary <- spec.runSpecWithSharedRuntimeLayer(spec.spec, args, runtime, eventHandlerZ)
      _       <- sendSummary.provideEnvironment(ZEnvironment(summary))
    } yield ())
      .provideLayer(sharedFilledTestLayer)

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    implicit val trace = Trace.empty

    val zTestHandler                      = new ZTestEventHandlerSbt(eventHandler, taskDef)
    var resOutter: CancelableFuture[Unit] = null
    try {
      val res: CancelableFuture[Unit] =
        runtime.unsafe.runToFuture(run(zTestHandler))(trace, Unsafe.unsafe)

      resOutter = res
      Await.result(res, Duration.Inf)
      Array()
    } catch {
      case t: Throwable =>
        if (resOutter != null) resOutter.cancel()
        throw t
    }
  }

  override def tags(): Array[String] = Array.empty
}
