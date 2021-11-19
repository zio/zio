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
import zio.{Chunk, Clock, Provider, Runtime, UIO, UProvider, ZEnvironment, ZIO, ZIOAppArgs, ZProvider, ZTraceElement}

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
    spec: ZIOSpecAbstract
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] = {
    val argsprovider: UProvider[ZIOAppArgs] =
      ZProvider.succeed(
        ZIOAppArgs(Chunk.empty)
      )

    val filledTestprovider: Provider[Nothing, TestEnvironment] =
      zio.ZEnv.live >>> TestEnvironment.live

    val provider: Provider[Error, spec.Environment] =
      (argsprovider +!+ filledTestprovider) >>> spec.provider.mapError(e => new Error(e.toString))

    val fullProvider: Provider[Error, spec.Environment with ZIOAppArgs with TestEnvironment with zio.ZEnv] =
      provider +!+ argsprovider +!+ filledTestprovider

    for {
      spec <- spec
                .runSpec(FilteredSpec(spec.spec, args), args)
                .provide(
                  fullProvider
                )
      events = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _     <- ZIO.foreach(events)(e => ZIO.attempt(eventHandler.handle(e)))
    } yield ()
  }

  protected def sbtTestProvider(
    loggers: Array[Logger]
  ): Provider[Nothing, TestLogger with Clock] =
    ZProvider.succeed[TestLogger](new TestLogger {
      def logLine(line: String)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.attempt(loggers.foreach(_.info(colored(line)))).ignore
    }) ++ Clock.live

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] =
    try {
      spec match {
        case NewSpecWrapper(zioSpec) =>
          Runtime(ZEnvironment.empty, zioSpec.runtime.runtimeConfig).unsafeRun {
            run(eventHandler, zioSpec)
              .onError(e => UIO(println(e.prettyPrint)))
          }
        case LegacySpecWrapper(abstractRunnableSpec) =>
          Runtime(ZEnvironment.empty, abstractRunnableSpec.runtimeConfig).unsafeRun {
            run(eventHandler, abstractRunnableSpec)
              .provide(sbtTestProvider(loggers))
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
