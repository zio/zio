package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Task, TaskDef}
import zio.{CancelableFuture, Console, Runtime, Scope, ZEnvironment, ZIO, ZIOAppArgs, ZLayer, ZTraceElement}
import zio.test.render.ConsoleRenderer
import zio.test.{FilteredSpec, TestArgs, TestEnvironment, TestLogger, ZIOSpecAbstract}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import zio.stacktracer.TracingImplicits.disableAutoTrace

abstract class BaseTestTask(
  val taskDef: TaskDef,
  val testClassLoader: ClassLoader,
  val sendSummary: SendSummary,
  val args: TestArgs,
  val spec: ZIOSpecAbstract
) extends Task {

  protected def sharedFilledTestlayer(
    console: Console
  )(implicit trace: ZTraceElement): ZLayer[Any, Nothing, TestEnvironment with TestLogger with ZIOAppArgs with Scope] = {
    ZIOAppArgs.empty +!+ (
      (zio.ZEnv.live ++ Scope.default) >>>
        TestEnvironment.live >+> TestLogger.fromConsole(console)
    )
  } +!+ Scope.default

  protected def constructLayer[Environment](
    specLayer: ZLayer[ZIOAppArgs with Scope, Any, Environment],
    console: Console
  )(implicit
    trace: ZTraceElement
  ): ZLayer[Any, Error, Environment with TestEnvironment with TestLogger with ZIOAppArgs with Scope] = {
    println("about to blow up when constructing spec layer")
    (sharedFilledTestlayer(console) >>> specLayer.mapError(e => new Error(e.toString))) +!+ sharedFilledTestlayer(
      console
    )
  }

  protected def run(
    eventHandler: EventHandler,
    spec: ZIOSpecAbstract
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] = {
    ZIO.consoleWith { console =>
      (for {
        _ <- ZIO.succeed("TODO pass this where needed to resolve #6481: " + eventHandler)
        _ <- ZIO.debug("BaseTestTask.run")
        summary <- spec
                     .runSpec(FilteredSpec(spec.spec, args), args, console)
//          .provideLayer(
//            constructLayer[spec.Environment](spec.layer, console)
//          )
        _ <- sendSummary.provideEnvironment(ZEnvironment(summary))
        _ <- TestLogger.logLine(ConsoleRenderer.render(summary))
        _ <- ZIO.when(summary.fail == 0 && summary.success == 0 && summary.ignore == 0) {
               TestLogger.logLine("No tests were executed.")
             }
        _ <- (if (summary.fail > 0)
                ZIO.fail(new Exception("Failed tests."))
              else ZIO.unit)
      } yield ())
        .provideLayer(
          sharedFilledTestlayer(console)
        )
    }
  }

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    println("BaseTestTask.execute (SBT command)")
    implicit val trace                    = ZTraceElement.empty
    var resOutter: CancelableFuture[Unit] = null
    try {
      val res: CancelableFuture[Unit] =
        Runtime(ZEnvironment.empty, spec.hook(spec.runtime.runtimeConfig)).unsafeRunToFuture {
          ZIO.debug("BaseTestTask.execute (ZIO command)") *>
          executeZ(eventHandler)
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

  def executeZ(eventHandler: EventHandler)(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    run(eventHandler, spec)

  override def tags(): Array[String] = Array.empty
}
