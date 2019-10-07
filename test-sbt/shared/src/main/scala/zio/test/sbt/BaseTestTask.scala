package zio.test.sbt

import sbt.testing.{ EventHandler, Logger, Task, TaskDef }
import zio.clock.Clock
import zio.test.{ AbstractRunnableSpec, SummaryBuilder, TestArgs, TestLogger }
import zio.{ Runtime, ZIO }

abstract class BaseTestTask(
  val taskDef: TaskDef,
  val testClassLoader: ClassLoader,
  val sendSummary: SendSummary,
  val args: TestArgs
) extends Task {

  protected lazy val spec: AbstractRunnableSpec = {
    import org.portablescala.reflect._
    val fqn = taskDef.fullyQualifiedName.stripSuffix("$") + "$"
    Reflect
      .lookupLoadableModuleClass(fqn, testClassLoader)
      .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
      .loadModule()
      .asInstanceOf[AbstractRunnableSpec]
  }

  protected def run(eventHandler: EventHandler, loggers: Array[Logger]) =
    for {
      spec <- args.testSearchTerm
               .fold(spec.run)(
                 s => spec.runner.run(spec.spec.filterTestLabels(_.toString.contains(s)).getOrElse(spec.spec))
               )
               .provide(new SbtTestLogger(loggers) with Clock.Live)
      summary <- SummaryBuilder.buildSummary(spec)
      _       <- sendSummary.run(summary)
      events  <- ZTestEvent.from(spec, taskDef.fullyQualifiedName, taskDef.fingerprint)
      _       <- ZIO.foreach[Any, Throwable, ZTestEvent, Unit](events)(e => ZIO.effect(eventHandler.handle(e)))
    } yield ()

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    Runtime((), spec.platform).unsafeRun(run(eventHandler, loggers))
    Array()
  }

  override def tags(): Array[String] = Array.empty
}

class SbtTestLogger(loggers: Array[Logger]) extends TestLogger {
  override def testLogger: TestLogger.Service = (line: String) => {
    ZIO
      .effect(loggers.foreach(_.info(line)))
      .catchAll(_ => ZIO.unit)
  }
}
