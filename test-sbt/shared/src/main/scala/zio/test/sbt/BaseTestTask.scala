package zio.test.sbt

import java.util.concurrent.atomic.AtomicReference

import sbt.testing.{ EventHandler, Logger, Task, TaskDef }
import zio.clock.Clock
import zio.test.TestRunner.ExecutionResult
import zio.test.{ AbstractRunnableSpec, TestLogger }
import zio.{ Runtime, ZIO }

abstract class BaseTestTask(val taskDef: TaskDef, testClassLoader: ClassLoader) extends Task {
  val summaryRef: AtomicReference[String] = new AtomicReference[String]("")

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
      result                         <- spec.run.provide(new SbtTestLogger(loggers) with Clock.Live)
      ExecutionResult(spec, summary) = result
      events                         = ZTestEvent.from(spec, taskDef.fullyQualifiedName, taskDef.fingerprint)
      _                              <- ZIO.foreach[Any, Throwable, ZTestEvent, Unit](events)(e => ZIO.effect(eventHandler.handle(e)))
      _                              <- ZIO.effectTotal(summaryRef.set(summary))
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
