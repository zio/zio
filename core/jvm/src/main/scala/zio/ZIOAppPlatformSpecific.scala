package zio

import zio.internal._
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] trait ZIOAppPlatformSpecific { self: ZIOApp =>

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    implicit val trace: Trace   = Trace.tracer.newTrace
    implicit val unsafe: Unsafe = Unsafe.unsafe

    val newLayer =
      ZLayer.succeed(ZIOAppArgs(Chunk.fromIterable(args0))) >>>
        bootstrap +!+ ZLayer.environment[ZIOAppArgs]

    val workflow =
      (for {
        runtime <- ZIO.runtime[Environment with ZIOAppArgs]
        _       <- installSignalHandlers(runtime)
        result  <- runtime.run(ZIO.scoped[Environment with ZIOAppArgs](run)).tapErrorCause(ZIO.logErrorCause(_))
      } yield result).provideLayer(newLayer.tapErrorCause(ZIO.logErrorCause(_)))

    runtime.unsafe.run {
      (for {
        fiber <- workflow.fork
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
                try {
                  runtime.unsafe.run {
                    for {
                      _       <- fiber.interrupt
                      fiberId <- ZIO.fiberId
                      roots   <- Fiber.roots
                      _       <- Fiber.interruptAll(fiber +: roots.filterNot(_.id == fiberId))
                    } yield ()
                  }
                } catch {
                  case _: Throwable =>
                }
              }

              ()
            }
          })
        result <- fiber.join
      } yield result).exitCode.tap(exit)
    }.getOrThrowFiberFailure()
  }

}
