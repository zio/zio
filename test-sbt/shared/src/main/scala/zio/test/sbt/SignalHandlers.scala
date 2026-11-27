package zio.test.sbt

import zio.{Fiber, Trace, Unsafe}
import zio.internal.Platform

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs signal handlers that print a dump of the running fibers.
 */
object SignalHandlers {
  private val installed: AtomicBoolean = new AtomicBoolean(false)

  /**
   * Installs handlers that print a dump of all fibers (`Fiber.dumpAll`) to the standard error stream when the process receives a signal: `INT` on Windows, `INFO` and `USR1` on other operating systems. Only the first call installs them, and a signal that can't be handled (for example `INFO`, which Linux doesn't have) is skipped. Handlers are registered on the JVM only: on Scala Native the call registers nothing, and on Scala.js it registers no operating system signal handler.
   */
  def install()(implicit trace: Trace): Unit =
    try {
      implicit val unsafe: Unsafe = Unsafe
      if (!installed.getAndSet(true)) {
        val dumpFibers =
          () => zio.Runtime.default.unsafe.run(Fiber.dumpAll).getOrThrowFiberFailure()

        if (zio.System.os.isWindows) {
          Platform.addSignalHandler("INT", dumpFibers)
        } else {
          Platform.addSignalHandler("INFO", dumpFibers)
          Platform.addSignalHandler("USR1", dumpFibers)
        }
      }
    }
}
