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
import zio.{
  Chunk,
  Clock,
  Console,
  Layer,
  Random,
  Runtime,
  System,
  UIO,
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
  ): ZIO[TestLogger with Clock, Throwable, Unit] =
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
  )(implicit trace: ZTraceElement): ZIO[TestLogger, Throwable, Unit] = {
    // TODO Is all this layer construction inappropriate here? the old style handled everything via the `runner` field, and didn't need to .provide here
    val argslayer: ULayer[ZIOAppArgs] =
      ZLayer.succeed(
        ZIOAppArgs(Chunk.empty)
      )

    val filledTestlayer: Layer[Nothing, TestEnvironment] =
      zio.ZEnv.live >>> TestEnvironment.live

    val layer: Layer[Error, spec.Environment] =
      (argslayer +!+ filledTestlayer) >>> spec.layer.mapError(e => new Error(e.toString))

    val fullLayer
    // TODO This type annotation in particular just feels like it _can't_ be part of the correct solution
      : Layer[Error, spec.Environment with ZIOAppArgs with TestEnvironment with Console with System with Random] =
      layer +!+ argslayer +!+ filledTestlayer

    val testLoggers: Layer[Nothing, TestLogger with Clock] = sbtTestLayer(loggers)

    for {
      spec <- spec
                .runSpec(FilteredSpec(spec.spec, args), args, sendSummary)
                .provide(
                  testLoggers,
                  fullLayer
                )
      events = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _     <- ZIO.foreach(events)(e => ZIO.attempt(eventHandler.handle(e)))
    } yield ()
  }

  protected def sbtTestLayer(
    loggers: Array[Logger]
  ): Layer[Nothing, TestLogger with Clock] =
    ZLayer.succeed[TestLogger](new TestLogger {
      def logLine(line: String)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.attempt(loggers.foreach(_.info(colored(line)))).ignore
    }) ++ Clock.live

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] =
    try {
      spec match {
        case NewSpecWrapper(zioSpec) =>
          Runtime(ZEnvironment.empty, zioSpec.runtime.runtimeConfig).unsafeRun {
            run(eventHandler, zioSpec, loggers)
              .provide(sbtTestLayer(loggers))
              .onError(e => UIO(println(e.prettyPrint)))
          }
        case LegacySpecWrapper(abstractRunnableSpec) =>
          Runtime(ZEnvironment.empty, abstractRunnableSpec.runtimeConfig).unsafeRun {
            run(eventHandler, abstractRunnableSpec)
              .provide(sbtTestLayer(loggers))
              .onError(e => UIO(println(e.prettyPrint)))
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
