package zio.test.sbt

import zio.{Fiber, Trace, Unsafe}
import zio.internal.Platform

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs signal handlers that print a dump of the running fibers while tests run.
 */
object SignalHandlers {
  private val installed: AtomicBoolean = new AtomicBoolean(false)

  /**
   * Installs handlers that print a dump of all fibers (`Fiber.dumpAll`) to the standard error stream when the process receives a signal: `INT` on Windows, `INFO` and `USR1` on other operating systems. Every ZIO Test sbt task calls it before it runs its spec.
   *
   * Only the first call installs the handlers; later calls do nothing. A handler that can't be registered (for example `INFO`, which Linux doesn't have) is skipped. On the JVM the handlers are registered with `sun.misc.Signal` when it is available. On Scala Native no handler is registered, and on Scala.js no operating system signal handler is registered.
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
