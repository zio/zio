package zio

import zio.internal._
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait ZIOAppPlatformSpecific { self: ZIOApp =>

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    implicit val trace: Trace = Trace.empty

    val newLayer =
      Scope.default +!+ ZLayer.succeed(ZIOAppArgs(Chunk.fromIterable(args0))) >>>
        environmentLayer +!+ ZLayer.environment[ZIOAppArgs with Scope]

    runtime.unsafeRun {
      (for {
        runtime <- ZIO.runtime[Environment with ZIOAppArgs with Scope]
        _       <- installSignalHandlers(runtime)
        fiber   <- runtime.run(run).fork
        _ <-
          ZIO.succeed(Platform.addShutdownHook { () =>
            if (!shuttingDown.getAndSet(true)) {

              if (FiberContext.catastrophicFailure.get) {
                println(
                  "**** WARNING ****\n" +
                    "Catastrophic error encountered. " +
                    "Application not safely interrupted. " +
                    "Resources may be leaked. " +
                    "Check the logs for more details and consider overriding `RuntimeConfig.reportFatal` to capture context."
                )
              } else {
                try runtime.unsafeRunSync(fiber.interrupt)
                catch { case _: Throwable => }
              }

              ()
            }
          })
        result <- fiber.join.tapErrorCause(ZIO.logErrorCause(_)).exitCode
        _      <- exit(result)
      } yield ()).provideLayer(newLayer)
    }
  }

}
