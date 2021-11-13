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
import zio.{
  Chunk,
  Clock,
  ServiceBuilder,
  Has,
  Runtime,
  UIO,
  UServiceBuilder,
  ZIO,
  ZIOAppArgs,
  ZServiceBuilder,
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
    val argsserviceBuilder: UServiceBuilder[Has[ZIOAppArgs]] =
      ZServiceBuilder.succeed(
        ZIOAppArgs(Chunk.empty)
      )

    val filledTestserviceBuilder: ServiceBuilder[Nothing, TestEnvironment] =
      zio.ZEnv.live >>> TestEnvironment.live

    val serviceBuilder: ServiceBuilder[Error, spec.Environment] =
      (argsserviceBuilder +!+ filledTestserviceBuilder) >>> spec.serviceBuilder.mapError(e => new Error(e.toString))

    val fullServiceBuilder
      : ServiceBuilder[Error, spec.Environment with Has[ZIOAppArgs] with TestEnvironment with zio.ZEnv] =
      serviceBuilder +!+ argsserviceBuilder +!+ filledTestserviceBuilder

    for {
      spec <- spec
                .runSpec(FilteredSpec(spec.spec, args), args)
                .provideService(
                  fullServiceBuilder
                )
      events = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _     <- ZIO.foreach(events)(e => ZIO.attempt(eventHandler.handle(e)))
    } yield ()
  }

  protected def sbtTestServiceBuilder(
    loggers: Array[Logger]
  ): ServiceBuilder[Nothing, Has[TestLogger] with Has[Clock]] =
    ZServiceBuilder.succeed[TestLogger](new TestLogger {
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
              .provideService(sbtTestServiceBuilder(loggers))
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
