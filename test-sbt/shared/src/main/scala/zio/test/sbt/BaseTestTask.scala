package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Task, TaskDef}
import zio.{CancelableFuture, Runtime, Scope, ZEnvironment, ZIO, ZIOAppArgs, ZLayer, ZTraceElement}
import zio.test.render.ConsoleRenderer
import zio.test.{FilteredSpec, TestArgs, TestEnvironment, TestLogger, ZIOSpecAbstract}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

abstract class BaseTestTask(
  val taskDef: TaskDef,
  val testClassLoader: ClassLoader,
  val sendSummary: SendSummary,
  val args: TestArgs,
  val spec: ZIOSpecAbstract
) extends Task {

  protected val sharedFilledTestlayer
    : ZLayer[Any, Nothing, TestEnvironment with TestLogger with ZIOAppArgs with Scope] = {
    ZIOAppArgs.empty +!+ (
      (zio.ZEnv.live ++ Scope.default) >>>
        TestEnvironment.live >+> TestLogger.fromConsole
    )
  } +!+ Scope.default

  protected def constructLayer[Environment](
    specLayer: ZLayer[ZIOAppArgs with Scope, Any, Environment]
  ): ZLayer[Any, Error, Environment with TestEnvironment with TestLogger with ZIOAppArgs with Scope] =
    (sharedFilledTestlayer >>> specLayer.mapError(e => new Error(e.toString))) +!+ sharedFilledTestlayer

  protected def run(
    eventHandler: EventHandler,
    spec: ZIOSpecAbstract
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    (for {
      _ <- ZIO.succeed("TODO pass this where needed to resolve #6481: " + eventHandler)
      summary <- spec
                   .runSpec(FilteredSpec(spec.spec, args), args)
      _ <- sendSummary.provideEnvironment(ZEnvironment(summary))
      _ <- TestLogger.logLine(ConsoleRenderer.render(summary))
      _ <- (if (summary.fail > 0)
              ZIO.fail(new Exception("Failed tests"))
            else ZIO.unit)
    } yield ())
      .provideLayer(
        constructLayer[spec.Environment](spec.layer)
      )

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    var resOutter: CancelableFuture[Unit] = null
    try {
      val res: CancelableFuture[Unit] =
        Runtime(ZEnvironment.empty, spec.hook(spec.runtime.runtimeConfig)).unsafeRunToFuture {
          executeZ(eventHandler)
        }

      resOutter = res
      Await.result(res, Duration.Inf)
      Array()
    } catch {
      case t: Throwable =>
        if (resOutter != null) resOutter.cancel()
        t.printStackTrace()
        throw t
    }
  }

  def executeZ(eventHandler: EventHandler): ZIO[Any, Throwable, Unit] = {
          run(eventHandler, spec)
            .tapError(e => ZIO.succeed(println(e.getMessage)))
  }

  override def tags(): Array[String] = Array.empty
}
