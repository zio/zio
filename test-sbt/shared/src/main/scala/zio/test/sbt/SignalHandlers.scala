package zio.test.sbt

import zio.Unsafe
import zio.internal.Platform

import java.util.concurrent.atomic.AtomicBoolean

object SignalHandlers {
  private val installed: AtomicBoolean = new AtomicBoolean(false)

  def install()(implicit trace: zio.Trace): Unit =
    try {
      implicit val unsafe: Unsafe = Unsafe
      if (!installed.getAndSet(true)) {
        val dumpFibers =
          () => zio.Runtime.default.unsafe.run(zio.Fiber.dumpAll).getOrThrowFiberFailure()

        if (zio.System.os.isWindows) {
          Platform.addSignalHandler("INT", dumpFibers)
        } else {
          Platform.addSignalHandler("INFO", dumpFibers)
          Platform.addSignalHandler("USR1", dumpFibers)
        }
      }
    }
}
