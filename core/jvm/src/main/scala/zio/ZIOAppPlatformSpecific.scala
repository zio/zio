package zio

import zio.internal._
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] trait ZIOAppPlatformSpecific { self: ZIOApp =>

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    implicit val trace: Trace   = Trace.empty
    implicit val unsafe: Unsafe = Unsafe.unsafe

    val newLayer =
      ZLayer.succeed(ZIOAppArgs(Chunk.fromIterable(args0))) >>>
        bootstrap +!+ ZLayer.environment[ZIOAppArgs]

    runtime.unsafe.run {
      (for {
        runtime <- ZIO.runtime[Environment with ZIOAppArgs]
        _       <- installSignalHandlers(runtime)
        fiber   <- runtime.run(ZIO.scoped[Environment with ZIOAppArgs](run)).fork
        _ <-
          ZIO.succeed(Platform.addShutdownHook { () =>
            if (!shuttingDown.getAndSet(true)) {

              if (FiberRuntime.catastrophicFailure.get) {
                println(
                  "**** WARNING ****\n" +
                    "Catastrophic error encountered. " +
                    "Application not safely interrupted. " +
                    "Resources may be leaked. " +
                    "Check the logs for more details and consider overriding `Runtime.reportFatal` to capture context."
                )
              } else {
                try runtime.unsafe.run(Fiber.roots.flatMap(roots => interruptAll(fiber +: roots)))
                catch {
                  case _: Throwable =>
                }
              }

              ()
            }
          })
        result <- fiber.join.tapErrorCause(ZIO.logErrorCause(_))
      } yield result).provideLayer(newLayer.tapErrorCause(ZIO.logErrorCause(_))).exitCode.tap(exit)
    }.getOrThrowFiberFailure()
  }

}
