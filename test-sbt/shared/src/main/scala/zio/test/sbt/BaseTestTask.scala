package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Task, TaskDef}
import zio._
import zio.clock.Clock
import zio.test._

abstract class BaseTestTask(
  val taskDef: TaskDef,
  val sendSummary: SendSummary,
  val args: TestArgs,
  val specInstance: AbstractRunnableSpec
) extends Task {

  protected def run(eventHandler: EventHandler): ZIO[
    specInstance.SharedEnvironment with TestLogger with Clock,
    Throwable,
    Unit
  ] =
    for {
      spec   <- specInstance.runSpec(FilteredSpec(specInstance.spec, args))
      summary = SummaryBuilder.buildSummary(spec)
      _      <- sendSummary.provide(summary)
      events  = ZTestEvent.from(spec, taskDef.fullyQualifiedName(), taskDef.fingerprint())
      _      <- ZIO.foreach(events)(e => ZIO.effect(eventHandler.handle(e)))
    } yield ()

  protected def sbtTestLayer(loggers: Array[Logger]): Layer[Nothing, TestLogger with Clock] =
    ZLayer.succeed[TestLogger.Service](new TestLogger.Service {
      def logLine(line: String): UIO[Unit] =
        ZIO.effect(loggers.foreach(_.info(colored(line)))).ignore
    }) ++ Clock.live

  override def tags(): Array[String] = Array.empty
}
