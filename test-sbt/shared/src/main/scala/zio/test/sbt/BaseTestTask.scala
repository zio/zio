package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Task, TaskDef}
import zio.test.{
  AbstractRunnableSpec,
  FilteredSpec,
  SummaryBuilder,
  TestArgs,
  TestEnvironment,
  TestLogger,
  ZIOSpecAbstract
}
import zio.{Chunk, Clock, Layer, Runtime, Scope, UIO, ULayer, ZEnvironment, ZIO, ZIOAppArgs, ZLayer, ZTraceElement}

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
  ): ZIO[TestLogger, Throwable, Unit] =
    for {
      spec   <- spec.runSpec(FilteredSpec(spec.spec, args))
      summary = SummaryBuilder.buildSummary(spec)
      _      <- sendSummary.provideEnvironment(ZEnvironment(summary))
      events  = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _      <- ZIO.foreach(events)(e => ZIO.attempt(eventHandler.handle(e)))
    } yield ()

  protected def run(
    eventHandler: EventHandler,
    spec: ZIOSpecAbstract,
    loggers: Array[Logger]
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] = {
    val argslayer: ULayer[ZIOAppArgs] =
      ZLayer.succeed(
        ZIOAppArgs(Chunk.empty)
      )

    val filledTestlayer: ZLayer[Scope, Nothing, TestEnvironment] =
      (zio.ZEnv.live ++ ZLayer.environment[Scope]) >>> TestEnvironment.live

    val layer: ZLayer[Scope, Error, spec.Environment] =
      (argslayer +!+ filledTestlayer) >>> spec.layer.mapError(e => new Error(e.toString))

    val fullLayer: ZLayer[
      Any,
      Error,
      spec.Environment with ZIOAppArgs with TestEnvironment with Scope
    ] =
      Scope.default >>> (layer +!+ argslayer +!+ filledTestlayer +!+ ZLayer.environment[Scope])

    val testLoggers: Layer[Nothing, TestLogger] = sbtTestLayer(loggers)

    for {
      spec <- spec
                .runSpec(FilteredSpec(spec.spec, args), args, sendSummary)
                .provideLayer(
                  testLoggers +!+ fullLayer
                )
      events = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _     <- ZIO.foreach(events)(e => ZIO.attempt(eventHandler.handle(e)))
    } yield ()
  }

  protected def sbtTestLayer(
    loggers: Array[Logger]
  ): Layer[Nothing, TestLogger] =
    ZLayer.succeed[TestLogger](new TestLogger {
      def logLine(line: String)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.attempt(loggers.foreach(_.info(colored(line)))).ignore
    }) ++ Clock.live

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] =
    try {
      spec match {
        case NewSpecWrapper(zioSpec) =>
          Runtime(ZEnvironment.empty, zioSpec.hook(zioSpec.runtime.runtimeConfig)).unsafeRun {
            run(eventHandler, zioSpec, loggers)
              .provideLayer(sbtTestLayer(loggers))
              .onError(e => ZIO.succeed(println(e.prettyPrint)))
          }
        case LegacySpecWrapper(abstractRunnableSpec) =>
          Runtime(ZEnvironment.empty, abstractRunnableSpec.runtimeConfig).unsafeRun {
            run(eventHandler, abstractRunnableSpec)
              .provideLayer(sbtTestLayer(loggers))
              .onError(e => ZIO.succeed(println(e.prettyPrint)))
          }
      }
      Array()
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }

  override def tags(): Array[String] = Array.empty

}
