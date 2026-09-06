package zio

import zio.internal._
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] trait ZIOAppPlatformSpecific { self: ZIOApp =>

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    implicit val trace: Trace   = Trace.empty
    implicit val unsafe: Unsafe = Unsafe

    val app = workflow(args0)

    val shutdownLatch = internal.OneShot.make[Unit]

    def shutdownHook(fiberId: FiberId, fiber: Fiber.Runtime[Nothing, ExitCode]): Unit =
      Platform.addShutdownHook { () =>
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
            try {
              fiber.tellInterrupt(Cause.interrupt(fiberId))
              gracefulShutdownTimeout match {
                case Duration.Infinity       => shutdownLatch.get()
                case d if d <= Duration.Zero => ()
                case d                       => shutdownLatch.get(d.toMillis)
              }
            } catch {
              case _: OneShot.TimeoutException =>
                println(
                  "**** WARNING ****\n" +
                    s"Timed out waiting for ZIO application to shut down after ${gracefulShutdownTimeout.render}. " +
                    "You can adjust your application's shutdown timeout by overriding the `shutdownTimeout` method"
                )
              case _: Throwable =>
            }
          }
        }
      }

    val exit0 =
      runtime.unsafe.run {
        ZIO.uninterruptibleMask { restore =>
          for {
            fiberId <- ZIO.fiberId
            fiber <- restore(app).exitWith { exit0 =>
                       val exitCode = if (exit0.isSuccess) ExitCode.success else ExitCode.failure
                       interruptRootFibers(fiberId).as(exitCode)
                     }.fork
            result <- {
              shutdownHook(fiberId, fiber)
              fiber.join
            }
          } yield result
        }
      }

    shutdownLatch.set(())
    exitUnsafe(exit0)
  }
}
