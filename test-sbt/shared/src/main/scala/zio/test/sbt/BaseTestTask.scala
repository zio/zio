package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Task, TaskDef}
import zio.clock.Clock
import zio.test._
import zio._

abstract class BaseTestTask(
  val taskDef: TaskDef,
  val testClassLoader: ClassLoader,
  val sendSummary: SendSummary,
  val args: TestArgs,
  private[sbt] val specInstance: AbstractRunnableSpec,
  layerCache: CustomSpecLayerCache
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

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
//    println(
//      s"=-=-=-=-> ${System.identityHashCode(this).toHexString}: execute(${specInstance.getClass.getCanonicalName})"
//    )

    try {
      Runtime((), specInstance.platform).unsafeRun {
        layerCache.awaitAvailable *> // layerCache.debug *>
          layerCache.getEnvironment(specInstance.sharedLayer).flatMap { env =>
            run(eventHandler)
              .provideSomeLayer[specInstance.SharedEnvironment](sbtTestLayer(loggers))
              .provide(env)
              .onError(e => UIO(println(e.prettyPrint)))
          }
      }
      Array()
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }
  }

  override def tags(): Array[String] = Array.empty
}
