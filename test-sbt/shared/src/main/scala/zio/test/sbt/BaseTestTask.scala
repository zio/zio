package zio.test.sbt

import java.io.IOException

import sbt.testing.{ EventHandler, Logger, Task, TaskDef }
import zio.console.Console
import zio.test.AbstractRunnableSpec
import zio.{ Ref, Runtime, UIO, ZIO }

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
      console <- SbtLoggersConsole.console(loggers)
      res     <- spec.run.provide(console)
      _       <- console.flush()
      events  = ZTestEvent.from(res, taskDef.fullyQualifiedName, taskDef.fingerprint)
      _       <- ZIO.foreach[Any, Throwable, ZTestEvent, Unit](events)(e => ZIO.effect(eventHandler.handle(e)))
    } yield ()

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    Runtime((), spec.platform).unsafeRun(run(eventHandler, loggers))
    Array()
  }

  override def tags(): Array[String] = Array.empty
}

private[sbt] object SbtLoggersConsole {
  def console(loggers: Array[Logger]): ZIO[Any, Nothing, FlushableConsole] =
    Ref.make("").map(new FlushableConsole(loggers, _))

  class FlushableConsole(loggers: Array[Logger], ref: Ref[String]) extends Console {
    override val console: Service = new Service(loggers, ref)
    def flush(): UIO[Unit]        = console.putStr("")
  }

  class Service(loggers: Array[Logger], buffer: Ref[String]) extends Console.Service[Any] {
    override def putStr(line: String): UIO[Unit] =
      buffer.modify(b => () -> (b + line))

    override def putStrLn(line: String): UIO[Unit] =
      for {
        acc <- buffer.get
        _   <- buffer.set("")
        _ <- ZIO
              .effect(loggers.foreach(_.info(acc + line)))
              .catchAll(_ => ZIO.unit)
      } yield ()

    override val getStrLn: ZIO[Any, IOException, String] =
      Console.Live.console.getStrLn

    def flush(): UIO[Unit] =
      for {
        acc <- buffer.get
        _ <- if (acc.nonEmpty) putStrLn("")
            else ZIO.unit
      } yield ()
  }
}
