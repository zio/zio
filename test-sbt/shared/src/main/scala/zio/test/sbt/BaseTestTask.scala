package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Status, Task, TaskDef, TestSelector}
import zio.test.render.ConsoleRenderer
import zio.test.{
  AbstractRunnableSpec,
  ExecutionEventSink,
  FilteredSpec,
  StreamingTestOutput,
  TestArgs,
  TestEnvironment,
  TestLogger,
  ZIOSpecAbstract
}
import zio.{
  Chunk,
  Clock,
  Console,
  Layer,
  Random,
  Runtime,
  Scope,
  System,
  ULayer,
  ZEnvironment,
  ZIO,
  ZIOAppArgs,
  ZLayer,
  ZTraceElement
}

abstract class BaseTestTask(
  val taskDef: TaskDef,
  val testClassLoader: ClassLoader,
  val sendSummary: SendSummary,
  val args: TestArgs,
  val spec: NewOrLegacySpec
) extends Task {

  protected def run(
    eventHandler: EventHandler,
    spec: AbstractRunnableSpec
  ): ZIO[TestLogger with Clock with StreamingTestOutput with ExecutionEventSink with Random, Throwable, Unit] = {
    assert(eventHandler != null)
    for {
      summary <- spec.runSpec(FilteredSpec(spec.spec, args))
      _       <- sendSummary.provideEnvironment(ZEnvironment(summary))
    } yield ()
  }

  protected def run(
    eventHandler: EventHandler, // TODO delete?
    spec: ZIOSpecAbstract
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] = {
    assert(eventHandler != null)
    val argslayer: ULayer[ZIOAppArgs] =
      ZLayer.succeed(
        ZIOAppArgs(Chunk.empty)
      )

    val testLoggers: Layer[Nothing, TestLogger] = sbtTestLayer

    val filledTestlayer: ZLayer[Scope, Nothing, TestEnvironment] = {
      (zio.ZEnv.live ++ ZLayer.environment[Scope]) >>> TestEnvironment.live
    }

    val layer: ZLayer[Scope, Error, spec.Environment] =
      (argslayer +!+ filledTestlayer) >>> spec.layer.mapError(e => new Error(e.toString))

    val fullLayer: ZLayer[
      Any,
      Error,
      spec.Environment with ZIOAppArgs with TestEnvironment with Console with System with Random with Clock with Scope with TestLogger
    ] =
      Scope.default >>> (layer +!+ argslayer +!+ filledTestlayer +!+ ZLayer.environment[Scope]) +!+ consoleTestLogger

    for {
      summary <- spec
                   .runSpec(FilteredSpec(spec.spec, args), args)
                   .provideLayer(
                     fullLayer
                   )
      _ <- ZIO.attempt {
             eventHandler.handle(
               ZTestEvent(
                 fullyQualifiedName = "zio.test.trickysituations.AMinimalSpec",
                 new TestSelector("test name"),
                 Status.Success,
                 maybeThrowable = None,
                 duration = 0L,
                 ZioSpecFingerprint
               )
             )
             println("ZZZ handled event")
           }
      _ <- sendSummary.provideEnvironment(ZEnvironment(summary))
      _ <- TestLogger.logLine(ConsoleRenderer.render(summary)).provideLayer(testLoggers)
      _ <- (if (summary.fail > 0)
              ZIO.fail(new Exception("Failed tests"))
            else ZIO.unit)
    } yield ()
  }

  private val consoleTestLogger: Layer[Nothing, TestLogger] = Console.live >>> TestLogger.fromConsole

  protected val sbtTestLayer
    : Layer[Nothing, TestLogger with Clock with StreamingTestOutput with ExecutionEventSink with Random] = {
    Clock.live ++ (StreamingTestOutput.live >>> ExecutionEventSink.live) ++ Random.live ++ StreamingTestOutput.live ++ consoleTestLogger
  }

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] =
    try {
      spec match {
        case NewSpecWrapper(zioSpec) =>
          Runtime(ZEnvironment.empty, zioSpec.hook(zioSpec.runtime.runtimeConfig)).unsafeRun {
            run(eventHandler, zioSpec)
              .catchAll(e => ZIO.debug("Error while executing tests: " + e.getMessage))
//              .catchAll(e => ZIO.debug("Error while executing tests: " + e.prettyPrint))
          }
          Array()
        case LegacySpecWrapper(abstractRunnableSpec) =>
          Runtime(ZEnvironment.empty, abstractRunnableSpec.runtimeConfig).unsafeRun {
            run(eventHandler, abstractRunnableSpec)
              .provideLayer(sbtTestLayer)
              .onError(e => ZIO.succeed(println(e.prettyPrint)))
          }
          Array()
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }

  override def tags(): Array[String] = Array.empty
}
