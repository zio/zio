package zio.test.sbt

import sbt.testing.{ EventHandler, Logger, Task, TaskDef }
import zio.test.RenderedResult.CaseType
import zio.test.{ AbstractRunnableSpec, DefaultTestReporter, RenderedResult, TestReporter }
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
      result   <- spec.runWith(TestReporter.silent)
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
