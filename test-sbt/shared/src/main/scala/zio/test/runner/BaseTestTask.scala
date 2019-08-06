package zio.test.runner

import sbt.testing.{ EventHandler, Logger, Task, TaskDef }
import zio.{ Runtime, ZIO }
import zio.test.RenderedResult.CaseType
import zio.test.{ DefaultTestReporter, RenderedResult, RunnableSpec }

abstract class BaseTestTask(val taskDef: TaskDef, testClassLoader: ClassLoader) extends Task {
  protected lazy val spec = {
    import org.portablescala.reflect._
    val fqn = taskDef.fullyQualifiedName.stripSuffix("$") + "$"
    Reflect
      .lookupLoadableModuleClass(fqn, testClassLoader)
      .getOrElse(throw new ClassNotFoundException("failed to load object: " + fqn))
      .loadModule()
      .asInstanceOf[RunnableSpec]
  }

  protected def run(eventHandler: EventHandler, loggers: Array[Logger]) = {

    def logResult[R, E](result: RenderedResult): zio.Task[Unit] =
      ZIO
        .sequence[Any, Throwable, Unit](
          for {
            log  <- loggers.toSeq
            line <- result.rendered
          } yield ZIO.effect(log.info(line))
        )
        .unit

    def reportResults(results: Seq[RenderedResult]) =
      ZIO.foreach(results) { result =>
        logResult(result) *> {
          result.caseType match {
            case CaseType.Test =>
              ZIO.effect(
                eventHandler.handle(ZTestEvent.from(result, taskDef.fullyQualifiedName, taskDef.fingerprint))
              )
            case CaseType.Suite => ZIO.unit
          }
        }
      }

    for {
      result   <- spec.run
      rendered = DefaultTestReporter.render(result.mapLabel(_.toString))
      _        <- reportResults(rendered)
    } yield ()
  }

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    Runtime((), spec.platform).unsafeRun(run(eventHandler, loggers))
    Array()
  }

  override def tags(): Array[String] = Array.empty
}
