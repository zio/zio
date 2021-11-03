package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Task, TaskDef}
import zio.test.{
  AbstractRunnableSpec,
  FilteredSpec,
  SummaryBuilder,
  TestArgs,
  TestLogger,
  TestEnvironment,
  ZIOSpecAbstract
}
import zio.{Chunk, Clock, Has, Layer, Runtime, UIO, ULayer, ZIO, ZIOAppArgs, ZLayer, ZTraceElement}

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
  ): ZIO[Has[TestLogger] with Has[Clock], Throwable, Unit] =
    for {
      spec   <- spec.runSpec(FilteredSpec(spec.spec, args))
      summary = SummaryBuilder.buildSummary(spec)
      _      <- sendSummary.provide(summary)
      events  = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _      <- ZIO.foreach(events)(e => ZIO.attempt(eventHandler.handle(e)))
    } yield ()

  protected def run(
    eventHandler: EventHandler,
    spec: ZIOSpecAbstract
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] = {
    val argsLayer: ULayer[Has[ZIOAppArgs]] =
      ZLayer.succeed(
        ZIOAppArgs(Chunk.empty)
      )

    val zEnvLayerThatIsNotBeingRecognizedByScala3 // Only here to make types more explicit
      : Layer[Nothing, zio.Has[zio.Clock] with zio.Has[zio.Console] with zio.Has[zio.System] with zio.Has[zio.Random]] =
      zio.ZEnv.live

    val filledTestLayer: Layer[Nothing, TestEnvironment] =
      zEnvLayerThatIsNotBeingRecognizedByScala3 >>> TestEnvironment.live

    val layer: Layer[Error, spec.Environment] =
      (argsLayer +!+ filledTestLayer) >>> spec.layer.mapError(e => new Error(e.toString))

    val fullLayer: Layer[Error, spec.Environment with Has[ZIOAppArgs] with TestEnvironment with zio.Has[
      zio.Clock
    ] with zio.Has[zio.Console] with zio.Has[zio.System] with zio.Has[zio.Random]] =
      layer +!+ argsLayer +!+ filledTestLayer +!+ zEnvLayerThatIsNotBeingRecognizedByScala3

    for {
      spec <- spec
                .runSpec(FilteredSpec(spec.spec, args), args)
                .provideLayer(
                  zEnvLayerThatIsNotBeingRecognizedByScala3 >>> fullLayer
                )
      events = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _     <- ZIO.foreach(events)(e => ZIO.attempt(eventHandler.handle(e)))
    } yield ()
  }

  protected def sbtTestLayer(loggers: Array[Logger]): Layer[Nothing, Has[TestLogger] with Has[Clock]] =
    ZLayer.succeed[TestLogger](new TestLogger {
      def logLine(line: String)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.attempt(loggers.foreach(_.info(colored(line)))).ignore
    }) ++ Clock.live

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] =
    try {
      spec match {
        case NewSpecWrapper(zioSpec) =>
          Runtime((), zioSpec.runtime.runtimeConfig).unsafeRun {
            run(eventHandler, zioSpec)
              .onError(e => UIO(println(e.prettyPrint)))
          }
        case LegacySpecWrapper(abstractRunnableSpec) =>
          Runtime((), abstractRunnableSpec.runtimeConfig).unsafeRun {
            run(eventHandler, abstractRunnableSpec)
              .provideLayer(sbtTestLayer(loggers))
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
