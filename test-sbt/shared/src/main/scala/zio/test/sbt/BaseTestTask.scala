package zio.test.sbt

import sbt.testing.{Event, EventHandler, Logger, Status, Task, TaskDef}
import zio.{CancelableFuture, Console, Runtime, Scope, Trace, UIO, ZEnvironment, ZIO, ZIOAppArgs, ZLayer}
import zio.test.render.ConsoleRenderer
import zio.test._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.nio.file.StandardOpenOption

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
  )(implicit trace: Trace): ZLayer[Any, Nothing, TestEnvironment with TestLogger with ZIOAppArgs with Scope] = {
    ZIOAppArgs.empty +!+ (
      (liveEnvironment ++ Scope.default) >>>
        TestEnvironment.live >+> TestLogger.fromConsole(console)
    )
  } +!+ Scope.default

  private[zio] def run(
    eventHandlerZ: ZTestEventHandler
  )(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
    ZIO.consoleWith { console =>
      (for {
        summary <- spec
                     .runSpecInfallible(FilteredSpec(spec.spec, args), args, console, runtime, eventHandlerZ)
        _ <- sendSummary.provideEnvironment(ZEnvironment(summary))
      } yield ())
        .provideLayer(
          sharedFilledTestlayer(console)
        )
    }

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    implicit val trace = Trace.empty

    println("TaskDef: " + taskDef)
    import java.nio.file.{Paths, Files}
    import java.nio.charset.StandardCharsets
    import java.io.File

    val htmlDir = Paths.get(s"target/test-reports-html")
    htmlDir.toFile.mkdirs()
    val html = htmlDir.resolve(s"${taskDef.fullyQualifiedName()}.html")
    if (!Files.exists(htmlDir)) {
      html.toFile.createNewFile()
    }

    Files.write(html, "<html>".getBytes(StandardCharsets.UTF_8))
    val zTestHandler                      = new ZTestEventHandlerSbt(eventHandler, taskDef)
    var resOutter: CancelableFuture[Unit] = null
    try {
      val res: CancelableFuture[Unit] =
        runtime.unsafeRunToFuture {
          run(zTestHandler)
        }

      resOutter = res
      Await.result(res, Duration.Inf)
      Files.write(html, "</html>".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND)
      Array()
    } catch {
      case t: Throwable =>
        if (resOutter != null) resOutter.cancel()
        throw t
    }
  }

  override def tags(): Array[String] = Array.empty
}
