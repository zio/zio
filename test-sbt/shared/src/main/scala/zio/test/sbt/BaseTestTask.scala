package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Task, TaskDef}
import zio.test.{AbstractRunnableSpec, FilteredSpec, SummaryBuilder, TestArgs, TestLogger, ZIOSpecAbstract}
import zio.{Chunk, Clock, Has, Layer, Runtime, UIO, ZIO, ZIOAppArgs, ZLayer, ZTraceElement}
import zio.test.environment.TestEnvironment

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
    spec: ZIOSpecAbstract,
    loggers: Array[Logger]
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    (for {
      spec <- spec
                .runSpec(FilteredSpec(spec.spec, args), args)
                .provideLayer(
                  spec.layer.mapError(e => new Error(e.toString)) >+> ZLayer.succeed(
                    ZIOAppArgs(Chunk.empty)
                  ) >+> zio.ZEnv.live >+> sbtTestLayer(loggers) >+> TestEnvironment.live
                )
      events = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _     <- ZIO.foreach(events)(e => ZIO.attempt(eventHandler.handle(e)))
    } yield ()).provideLayer(ZLayer.succeed(ZIOAppArgs(Chunk.empty)))

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
            run(eventHandler, zioSpec, loggers)
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
