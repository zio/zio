package zio.test.sbt

import sbt.testing.{ EventHandler, Logger, Task, TaskDef }

import zio.UIO
import zio.clock.Clock
import zio.test.{ AbstractRunnableSpec, FilteredSpec, SummaryBuilder, TestArgs, TestLogger }
import zio.{ Runtime, ZIO, ZLayer }

abstract class BaseTestTask(
  val taskDef: TaskDef,
  val testClassLoader: ClassLoader,
  val sendSummary: SendSummary,
  val args: TestArgs
) extends Task {

  protected lazy val specInstance: AbstractRunnableSpec = {
    import org.portablescala.reflect._
    val fqn = taskDef.fullyQualifiedName.stripSuffix("$") + "$"
    Reflect
      .lookupLoadableModuleClass(fqn, testClassLoader)
      .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
      .loadModule()
      .asInstanceOf[AbstractRunnableSpec]
  }

  protected def run(eventHandler: EventHandler) =
    for {
      spec    <- specInstance.runSpec(FilteredSpec(specInstance.spec, args))
      summary <- SummaryBuilder.buildSummary(spec)(specInstance.runner.reporter.render)
      _       <- sendSummary.provide(summary)
      events  <- ZTestEvent.from(spec, taskDef.fullyQualifiedName, taskDef.fingerprint)
      _       <- ZIO.foreach[Any, Throwable, ZTestEvent, Unit](events)(e => ZIO.effect(eventHandler.handle(e)))
    } yield ()

  protected def sbtTestLayer(loggers: Array[Logger]): ZLayer.NoDeps[Nothing, TestLogger with Clock] =
    ZLayer.succeed[TestLogger.Service](new TestLogger.Service {
      def logLine(line: String): UIO[Unit] =
        ZIO
          .effect(loggers.foreach(_.info(colored(line))))
          .catchAll(_ => ZIO.unit)
    }) ++ Clock.live

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    Runtime((), specInstance.platform).unsafeRun(
      (sbtTestLayer(loggers).build >>> run(eventHandler).toManaged_)
        .use_(ZIO.unit)
        .onError(e => UIO(println(e.prettyPrint)))
    )
    Array()
  }

  override def tags(): Array[String] = Array.empty

}
