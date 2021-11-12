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
import zio.{Chunk, Clock, Deps, Has, Runtime, UIO, UDeps, ZIO, ZIOAppArgs, ZDeps, ZTraceElement}

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
    val argsDeps: UDeps[Has[ZIOAppArgs]] =
      ZDeps.succeed(
        ZIOAppArgs(Chunk.empty)
      )

    val filledTestDeps: Deps[Nothing, TestEnvironment] =
      zio.ZEnv.live >>> TestEnvironment.live

    val deps: Deps[Error, spec.Environment] =
      (argsDeps +!+ filledTestDeps) >>> spec.deps.mapError(e => new Error(e.toString))

    val fullDeps: Deps[Error, spec.Environment with Has[ZIOAppArgs] with TestEnvironment with zio.ZEnv] =
      deps +!+ argsDeps +!+ filledTestDeps

    for {
      spec <- spec
                .runSpec(FilteredSpec(spec.spec, args), args)
                .provideDeps(
                  fullDeps
                )
      events = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _     <- ZIO.foreach(events)(e => ZIO.attempt(eventHandler.handle(e)))
    } yield ()
  }

  protected def sbtTestDeps(loggers: Array[Logger]): Deps[Nothing, Has[TestLogger] with Has[Clock]] =
    ZDeps.succeed[TestLogger](new TestLogger {
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
              .provideDeps(sbtTestDeps(loggers))
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
