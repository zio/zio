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

  private val argslayer: ULayer[ZIOAppArgs] =
    ZLayer.succeed(
      ZIOAppArgs(Chunk.empty)
    )

  private val consoleTestLogger: Layer[Nothing, TestLogger] = Console.live >>> TestLogger.fromConsole

  protected val sharedFilledTestlayer
    : ZLayer[Any, Nothing, TestEnvironment with TestLogger with ZIOAppArgs with Scope] = {
    argslayer +!+ (
      (zio.ZEnv.live ++ Scope.default) >>>
        TestEnvironment.live
    ) +!+ consoleTestLogger
  } +!+ Scope.default

  protected def run(
    eventHandler: EventHandler, // TODO delete?
    spec: ZIOSpecAbstract
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] = {

    type SpecAndGenericEnvironment =
      spec.Environment
        with ZIOAppArgs
        with TestEnvironment
        with System
        with Random
        with Clock
        with Scope
        with TestLogger

    val layer: ZLayer[Any, Error, spec.Environment] =
      sharedFilledTestlayer >>> spec.layer.mapError(e => new Error(e.toString))

    val fullLayer: ZLayer[
      Any,
      Error,
      SpecAndGenericEnvironment
    ] =
      (layer +!+ sharedFilledTestlayer)

    (for {
      summary <- spec
                   .runSpec(FilteredSpec(spec.spec, args), args)
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
      _ <- TestLogger.logLine(ConsoleRenderer.render(summary))
      _ <- (if (summary.fail > 0)
              ZIO.fail(new Exception("Failed tests"))
            else ZIO.unit)
    } yield ())
      .provideLayer(
        fullLayer
      )
  }

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] =
    try {
      spec match {
        case NewSpecWrapper(zioSpec) =>
          Runtime(ZEnvironment.empty, zioSpec.hook(zioSpec.runtime.runtimeConfig)).unsafeRun {
            run(eventHandler, zioSpec)
              .catchAll(e => ZIO.debug("Error while executing tests: " + e.getMessage))
          }
          Array()
        case LegacySpecWrapper(abstractRunnableSpec) =>
          Runtime(ZEnvironment.empty, abstractRunnableSpec.runtimeConfig).unsafeRun {
            run(eventHandler, abstractRunnableSpec)
              .provideLayer(sharedFilledTestlayer)
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
