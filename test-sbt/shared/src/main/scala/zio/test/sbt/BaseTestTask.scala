package zio.test.sbt

import sbt.testing.{ EventHandler, Logger, Task, TaskDef }
import zio.clock.Clock
import zio.test.{ AbstractRunnableSpec, TestLogger }
import zio.{ Runtime, ZIO }

abstract class BaseTestTask(val taskDef: TaskDef, testClassLoader: ClassLoader) extends Task {
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
      res    <- spec.run.provide(new SbtTestLogger(loggers) with Clock.Live)
      events = ZTestEvent.from(res, taskDef.fullyQualifiedName, taskDef.fingerprint)
      _      <- ZIO.foreach[Any, Throwable, ZTestEvent, Unit](events)(e => ZIO.effect(eventHandler.handle(e)))
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
