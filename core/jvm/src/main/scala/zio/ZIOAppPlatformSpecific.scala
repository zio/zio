package zio

import zio.internal._
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait ZIOAppPlatformSpecific { self: ZIOApp =>

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    implicit val trace: Trace = Trace.empty

    runtime.unsafeRun {
      (for {
        fiber <- invoke(Chunk.fromIterable(args0)).provideEnvironment(runtime.environment).fork
        _ <-
          IO.succeed(Platform.addShutdownHook { () =>
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
      } yield ())
    }
  }

}
