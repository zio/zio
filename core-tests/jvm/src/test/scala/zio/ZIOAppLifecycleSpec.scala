package zio

import _root_.zio.test._
import java.io.File
import java.util.concurrent.TimeUnit

object ZIOAppLifecycleSpec extends ZIOSpecDefault {

  def runApp(
    className: String,
    timeoutMs: Long = 15000L,
    sendSigIntAfterMs: Option[Long] = None
  ): Task[(Int, String, String)] = ZIO.attemptBlocking {
    val javaHome                    = java.lang.System.getProperty("java.home")
    val javaBin                     = javaHome + File.separator + "bin" + File.separator + "java"
    val classpath                   = java.lang.System.getProperty("java.class.path")
    val cmd: java.util.List[String] = java.util.Arrays.asList(javaBin, "-cp", classpath, className)
    val pb                          = new ProcessBuilder(cmd)
    pb.redirectErrorStream(false)
    val process = pb.start()
    sendSigIntAfterMs.foreach { delay =>
      val t = new Thread(new Runnable {
        def run(): Unit = {
          Thread.sleep(delay)
          val pid       = process.pid()
          val isWindows = java.lang.System.getProperty("os.name").toLowerCase.contains("win")
          if (isWindows) {
            java.lang.Runtime.getRuntime.exec(Array("taskkill", "/PID", pid.toString)).waitFor(); ()
          } else {
            java.lang.Runtime.getRuntime.exec(Array("kill", "-2", pid.toString)).waitFor(); ()
          }
        }
      })
      t.setDaemon(true)
      t.start()
    }
    val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (!finished) process.destroyForcibly()
    val stdout = new String(process.getInputStream.readAllBytes())
    val stderr = new String(process.getErrorStream.readAllBytes())
    val code   = if (finished) process.exitValue() else -1
    (code, stdout, stderr)
  }

  def spec = suite("ZIOAppLifecycleSpec")(
    test("app that succeeds exits with code 0") {
      for { result <- runApp("zio.AppSucceeds") } yield assertTrue(result._1 == 0, result._2.contains("finalizer ran"))
    },
    test("app that fails exits with non-zero code") {
      for { result <- runApp("zio.AppFails") } yield assertTrue(result._1 != 0, result._2.contains("finalizer ran"))
    },
    test("finalizers run on shutdown signal - regression #9901") {
      for { result <- runApp("zio.AppNeverWithFinalizer", sendSigIntAfterMs = Some(1000L)) } yield assertTrue(
        result._2.contains("finalizer ran")
      )
    } @@ TestAspect.unix,
    test("gracefulShutdownTimeout is respected") {
      for { result <- runApp("zio.AppSlowFinalizer", sendSigIntAfterMs = Some(500L)) } yield assertTrue(
        !result._2.contains("slow finalizer done")
      )
    } @@ TestAspect.unix
  ) @@ TestAspect.sequential @@ TestAspect.timeout(2.minutes)
}

object AppSucceeds extends ZIOAppDefault {
  def run = ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("finalizer ran").orDie).flatMap(_ => ZIO.unit)
}

object AppFails extends ZIOAppDefault {
  def run = ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("finalizer ran").orDie).flatMap(_ => ZIO.fail("boom"))
}

object AppNeverWithFinalizer extends ZIOAppDefault {
  def run = ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("finalizer ran").orDie).flatMap(_ => ZIO.never)
}

object AppSlowFinalizer extends ZIOAppDefault {
  override val gracefulShutdownTimeout: Duration = 1.second
  def run = ZIO
    .acquireRelease(ZIO.unit)(_ => ZIO.sleep(30.seconds) *> Console.printLine("slow finalizer done").orDie)
    .flatMap(_ => ZIO.never)
}
