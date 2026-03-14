package zio

import zio.test.TestAspect.{jvmOnly, sequential}
import zio.test._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit

/**
 * Process-level lifecycle tests for ZIOApp shutdown behavior.
 *
 * Covers: exit codes, finalizer execution, signal handling,
 * gracefulShutdownTimeout, catastrophic failure, and non-hanging shutdown.
 *
 * Regression tests for #9901, #9807, #9240.
 */
object ZIOAppLifecycleSpec extends ZIOBaseSpec {

  def spec = suite("ZIOAppLifecycleSpec")(
    test("external interrupt runs finalizers (regression #9901)") {
      withTempDir { dir =>
        for {
          ready  <- makePath(dir, "ready")
          finish <- makePath(dir, "finalized")
          proc   <- startApp("zio.ZIOAppLifecycleSignalApp", ready, finish)
          _      <- waitForFile(ready, 5.seconds)
          _      <- sendInterrupt(proc)
          out    <- waitForExit(proc, 10.seconds)
          ran    <- fileExists(finish)
        } yield assertTrue(ran) && assertTrue(out.exitCode != 0)
      }
    },
    test("gracefulShutdownTimeout cuts hanging finalizer (regression #9807)") {
      withTempDir { dir =>
        for {
          ready  <- makePath(dir, "ready")
          finish <- makePath(dir, "finalized")
          proc   <- startApp("zio.ZIOAppLifecycleHangingApp", ready, finish)
          _      <- waitForFile(ready, 5.seconds)
          _      <- sendInterrupt(proc)
          out    <- waitForExit(proc, 5.seconds)
          ran    <- fileExists(finish)
        } yield assertTrue(!ran) && assertTrue(out.elapsed < 3.seconds)
      }
    },
    test("gracefulShutdownTimeout = Infinity waits for slow finalizer") {
      withTempDir { dir =>
        for {
          ready  <- makePath(dir, "ready")
          finish <- makePath(dir, "finalized")
          proc   <- startApp("zio.ZIOAppLifecycleSlowFinalizerApp", ready, finish)
          _      <- waitForFile(ready, 5.seconds)
          _      <- sendInterrupt(proc)
          out    <- waitForExit(proc, 10.seconds)
          ran    <- fileExists(finish)
        } yield assertTrue(ran) && assertTrue(out.elapsed >= 400.millis)
      }
    },
    test("catastrophic failure skips finalizers") {
      withTempDir { dir =>
        for {
          ready  <- makePath(dir, "ready")
          finish <- makePath(dir, "finalized")
          proc   <- startApp("zio.ZIOAppLifecycleCatastrophicApp", ready, finish)
          _      <- waitForFile(ready, 5.seconds)
          _      <- sendInterrupt(proc)
          _      <- waitForExit(proc, 10.seconds)
          ran    <- fileExists(finish)
        } yield assertTrue(!ran)
      }
    },
    test("shutdown does not hang (regression #9240)") {
      withTempDir { dir =>
        for {
          ready  <- makePath(dir, "ready")
          finish <- makePath(dir, "finalized")
          proc   <- startApp("zio.ZIOAppLifecycleZeroTimeoutApp", ready, finish)
          _      <- waitForFile(ready, 5.seconds)
          _      <- sendInterrupt(proc)
          out    <- waitForExit(proc, 3.seconds)
        } yield assertTrue(out.elapsed < 2.seconds)
      }
    }
  ) @@ sequential @@ jvmOnly

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private final case class ProcessResult(exitCode: Int, output: String, elapsed: Duration)

  private def withTempDir[A](f: Path => ZIO[Any, Throwable, A]): ZIO[Any, Throwable, A] =
    ZIO.acquireReleaseWith(
      ZIO.attempt(Files.createTempDirectory("zio-app-lifecycle-"))
    )(deleteRecursively)(f)

  private def deleteRecursively(root: Path): UIO[Unit] =
    ZIO.succeed {
      if (Files.exists(root)) {
        val stream = Files.walk(root)
        try stream.sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.deleteIfExists(p))
        finally stream.close()
      }
    }

  private def makePath(dir: Path, name: String): UIO[Path] =
    ZIO.succeed(dir.resolve(name))

  private def startApp(mainClass: String, ready: Path, finalized: Path): ZIO[Any, Throwable, Process] =
    ZIO.attempt {
      val javaHome = java.lang.System.getProperty("java.home")
      val javaBin  = Path.of(javaHome).resolve("bin").resolve("java").toString
      val cp       = java.lang.System.getProperty("java.class.path")
      new ProcessBuilder(javaBin, "-cp", cp, mainClass, ready.toString, finalized.toString)
        .redirectErrorStream(true)
        .start()
    }

  private def sendInterrupt(process: Process): ZIO[Any, Throwable, Unit] =
    ZIO.attemptBlocking {
      val pid = process.pid()
      val killer = new ProcessBuilder("kill", "-INT", pid.toString).start()
      val ok = killer.waitFor(3, TimeUnit.SECONDS)
      if (!ok || killer.exitValue() != 0) {
        process.destroy()
      }
    }

  private def waitForFile(path: Path, timeout: Duration): ZIO[Any, Throwable, Unit] =
    ZIO.attemptBlocking {
      val deadline = java.lang.System.nanoTime() + timeout.toNanos
      while (java.lang.System.nanoTime() < deadline && !Files.exists(path))
        Thread.sleep(50L)
      if (!Files.exists(path))
        throw new RuntimeException(s"Timed out waiting for $path")
    }

  private def fileExists(path: Path): UIO[Boolean] =
    ZIO.succeed(Files.exists(path))

  private def waitForExit(process: Process, timeout: Duration): ZIO[Any, Throwable, ProcessResult] =
    ZIO.attemptBlocking {
      val t0   = java.lang.System.nanoTime()
      val done = process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
      if (!done) {
        process.destroyForcibly()
        throw new RuntimeException(s"Process did not exit within $timeout")
      }
      val elapsed = Duration.fromNanos(java.lang.System.nanoTime() - t0)
      val output  = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
      ProcessResult(process.exitValue(), output, elapsed)
    }
}