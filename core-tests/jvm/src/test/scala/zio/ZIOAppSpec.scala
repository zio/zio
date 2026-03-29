package zio

import zio.test._

import java.io.File
import scala.sys.process._

object ZIOAppSpec extends ZIOSpecDefault {

  private val isWindows: Boolean =
    System.getProperty("os.name", "").toLowerCase.contains("win")

  private val javaExe: String = {
    val base =
      java.nio.file.Paths
        .get(System.getProperty("java.home"), "bin", "java")
        .toAbsolutePath
        .toString
    if (isWindows && !base.toLowerCase.endsWith(".exe")) base + ".exe"
    else base
  }

  private val classpath: String =
    System.getProperty("java.class.path")

  private def runApp(mainClass: String, timeoutSeconds: Int = 10): (Int, String) = {
    val output  = new StringBuilder
    val logger  = ProcessLogger(line => output.append(line).append("\n"), line => output.append(line).append("\n"))
    val process = Process(Seq(javaExe, "-cp", classpath, mainClass)).run(logger)

    // Wait up to timeoutSeconds; forcibly destroy if it hangs
    val finished = waitFor(process, timeoutSeconds * 1000L)
    if (!finished) {
      process.destroy()
      (-1, output.toString)
    } else {
      (process.exitValue(), output.toString)
    }
  }

  /** Polls until the process exits or the timeout elapses. Returns true if the
    * process finished before the timeout.
    */
  private def waitFor(process: Process, timeoutMs: Long): Boolean = {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      try {
        process.exitValue()
        return true
      } catch {
        case _: IllegalThreadStateException => Thread.sleep(100)
      }
    }
    false
  }

  def spec: Spec[Any, Any] = suite("ZIOAppSpec")(
    test("ZIOAppDefault exits with code 0 on success") {
      val (code, _) = runApp("zio.ZIOAppSpecHelper$SuccessApp$")
      assertTrue(code == 0)
    },
    test("ZIOAppDefault exits with code 1 on failure") {
      val (code, _) = runApp("zio.ZIOAppSpecHelper$FailureApp$")
      assertTrue(code == 1)
    },
    test("ZIOAppDefault runs finalizers on normal exit") {
      val (code, out) = runApp("zio.ZIOAppSpecHelper$FinalizerApp$")
      assertTrue(code == 0, out.contains("finalizer ran"))
    },
    test("ZIOAppDefault runs finalizers on interrupted exit") {
      val (code, out) = runApp("zio.ZIOAppSpecHelper$InterruptedFinalizerApp$")
      assertTrue(out.contains("finalizer ran"))
    },
    test("ZIOApp does not hang when main effect completes") {
      val start         = System.currentTimeMillis()
      val (code, _)     = runApp("zio.ZIOAppSpecHelper$SuccessApp$", timeoutSeconds = 10)
      val elapsed       = System.currentTimeMillis() - start
      assertTrue(code == 0, elapsed < 9000L)
    },
    test("ZIOApp slow finalizer is bounded by gracefulShutdownTimeout") {
      // The app overrides gracefulShutdownTimeout to a short value and has a
      // finalizer that sleeps longer.  ZIO should force-exit before the full
      // finalizer sleep completes, so the whole run should finish well within
      // the test timeout.
      val start     = System.currentTimeMillis()
      val (_, out)  = runApp("zio.ZIOAppSpecHelper$SlowFinalizerApp$", timeoutSeconds = 15)
      val elapsed   = System.currentTimeMillis() - start
      // The app's gracefulShutdownTimeout is 2 s; the finalizer sleeps 30 s.
      // If timeout behaviour is correct the process should exit in < 10 s.
      assertTrue(elapsed < 10000L, out.contains("finalizer started"))
    }
  )
}
