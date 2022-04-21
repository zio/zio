package zio.test.sbt

import sbt.testing.{Event, EventHandler, Logger, Status, Task, TaskDef}
import zio.{CancelableFuture, Console, Runtime, Scope, ZEnvironment, ZIO, ZIOAppArgs, ZLayer, ZTraceElement}
import zio.test.render.ConsoleRenderer
import zio.test.{ExecutionEvent, FilteredSpec, Summary, TestArgs, TestEnvironment, TestLogger, ZIOSpecAbstract}

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

  protected def sharedFilledTestlayer(
    console: Console
  )(implicit trace: ZTraceElement): ZLayer[Any, Nothing, TestEnvironment with TestLogger with ZIOAppArgs with Scope] = {
    ZIOAppArgs.empty +!+ (
      (zio.ZEnv.live ++ Scope.default) >>>
        TestEnvironment.live >+> TestLogger.fromConsole(console)
    )
  } +!+ Scope.default

  private[zio] def run(
    eventHandler: Event => zio.Task[Unit]
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] = {
    val eventHandlerZ = (executionEvent: ExecutionEvent.Test[_]) => {
      ZIO.debug("handling event: " + executionEvent) *>
      eventHandler(ZTestEvent.convertEvent(executionEvent, taskDef))
    }

    ZIO.consoleWith { console =>
      (for {
        _ <- ZIO.succeed("TODO pass this where needed to resolve #6481: " + eventHandler)
        summary <- spec
                     .runSpecInfallible(FilteredSpec(spec.spec, args), args, console, runtime, eventHandlerZ)
        _ <- sendSummary.provideEnvironment(ZEnvironment(summary))
        _ <- ZIO.when(summary.status == Summary.Failure)(
               ZIO.fail(new Exception("Failed tests."))
             )
      } yield ())
        .provideLayer(
          sharedFilledTestlayer(console)
        )
    }
  }

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    implicit val trace                    = ZTraceElement.empty
    val blah: Event => zio.Task[Unit] = (event: Event) => ZIO.attempt(eventHandler.handle(event))
    var resOutter: CancelableFuture[Unit] = null
    try {
      val res: CancelableFuture[Unit] =
        runtime.unsafeRunToFuture {
          run(blah)
        }

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
