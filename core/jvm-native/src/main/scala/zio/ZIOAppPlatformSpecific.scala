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

    val workflow =
      (for {
        runtime <- ZIO.runtime[Environment with ZIOAppArgs]
        _       <- installSignalHandlers(runtime)
        result  <- runtime.run(ZIO.scoped[Environment with ZIOAppArgs](run)).tapErrorCause(ZIO.logErrorCause(_))
      } yield result).provideLayer(newLayer.tapErrorCause(ZIO.logErrorCause(_)))

    runtime.unsafe.run {
      ZIO.uninterruptible {
        for {
          fiberId <- ZIO.fiberId
          fiber <- workflow.interruptible.exitWith { exit0 =>
                     val exitCode = if (exit0.isSuccess) ExitCode.success else ExitCode.failure
                     interruptRootFibers(fiberId).as(exitCode)
                   }.fork
          _ <-
            ZIO.succeed(Platform.addShutdownHook { () =>
              if (shuttingDown.compareAndSet(false, true)) {

                if (FiberRuntime.catastrophicFailure.get) {
                  println(
                    "**** WARNING ****\n" +
                      "Catastrophic error encountered. " +
                      "Application not safely interrupted. " +
                      "Resources may be leaked. " +
                      "Check the logs for more details and consider overriding `Runtime.reportFatal` to capture context."
                  )
                } else {
                  // NOTE: try-catch likely not needed,
                  // but guarding against cases where the submission of the task fails spuriously
                  try {
                    fiber.tellInterrupt(Cause.interrupt(fiberId))
                  } catch {
                    case _: Throwable =>
                  }
                }
              }
            })
          result <- fiber.join
          _      <- exit(result)
        } yield ()
      }
    }.getOrThrowFiberFailure()
  }

  private def interruptRootFibers(mainFiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    for {
      roots <- Fiber.roots
      _     <- Fiber.interruptAll(roots.view.filter(fiber => fiber.isAlive() && (fiber.id != mainFiberId)))
    } yield ()
}
