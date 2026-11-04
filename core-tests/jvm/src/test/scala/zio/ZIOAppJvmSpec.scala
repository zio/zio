package zio

import zio.test._

import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.nio.file.Paths
import java.util.concurrent.{CountDownLatch, TimeUnit}

object ZIOAppJvmSpec extends ZIOBaseSpec {

  final case class ProcessResult(exitCode: Int, stdout: Chunk[String], stderr: Chunk[String]) {
    def stdoutText: String = stdout.mkString("\n")
    def stderrText: String = stderr.mkString("\n")
  }

  private val javaExecutable: String = {
    val isWindows = java.lang.System.getProperty("os.name", "").toLowerCase.contains("win")
    val binary    = if (isWindows) "java.exe" else "java"
    Paths.get(java.lang.System.getProperty("java.home"), "bin", binary).toAbsolutePath.toString
  }

  private val classpath: String =
    java.lang.System.getProperty("java.class.path")

  private def drainStream(
    stream: InputStream,
    marker: Option[String],
    ready: CountDownLatch
  ): UIO[Chunk[String]] = {
    val read = ZIO.attemptBlocking {
      val reader  = new BufferedReader(new InputStreamReader(stream))
      val builder = ChunkBuilder.make[String]()
      try {
        var line = reader.readLine()
        while (line != null) {
          builder += line
          if (marker.exists(line.contains)) ready.countDown()
          line = reader.readLine()
        }
      } catch {
        case _: Throwable => ()
      }
      builder.result()
    }
    read.orElseSucceed(Chunk.empty)
  }

  private def sendSigInt(process: Process): Unit = {
    val pid = classOf[Process].getMethod("pid").invoke(process).toString
    val _   = new ProcessBuilder("kill", "-INT", pid).start().waitFor(5, TimeUnit.SECONDS)
  }

  private def runAppProcess(
    mainClass: String,
    args: List[String] = Nil,
    destroyOnLine: Option[String] = None,
    timeout: java.time.Duration = java.time.Duration.ofSeconds(30)
  ): ZIO[Any, Throwable, ProcessResult] = {
    val cmd        = javaExecutable :: "-cp" :: classpath :: mainClass :: args
    val readyLatch = new CountDownLatch(1)
    val startProc  = ZIO.attemptBlocking(new ProcessBuilder(cmd: _*).start())

    def cleanup(process: Process): UIO[Unit] =
      ZIO.attemptBlocking(if (process.isAlive) process.destroyForcibly()).ignore

    def maybeSignal(process: Process): Task[Option[Unit]] =
      ZIO.when(destroyOnLine.isDefined) {
        ZIO.attemptBlocking {
          if (readyLatch.await(timeout.toMillis, TimeUnit.MILLISECONDS)) sendSigInt(process)
          else {
            process.destroyForcibly()
            ()
          }
        }
      }

    ZIO.acquireReleaseWith(startProc)(cleanup) { process =>
      for {
        stdoutFiber <- drainStream(process.getInputStream, destroyOnLine, readyLatch).forkDaemon
        stderrFiber <- drainStream(process.getErrorStream, None, readyLatch).forkDaemon
        _           <- maybeSignal(process)
        exited      <- ZIO.attemptBlocking(process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS))
        _           <- ZIO.when(!exited)(cleanup(process))
        stdout      <- stdoutFiber.join
        stderr      <- stderrFiber.join
        code        <- if (exited) ZIO.attemptBlocking(process.exitValue()) else ZIO.succeed(-1)
      } yield ProcessResult(code, stdout, stderr)
    }
  }

  def spec = suite("ZIOAppJvmSpec")(
    suite("natural completion")(
      test("successful app exits with code 0 and without SignalHandler errors (#9240)") {
        for {
          result <- runAppProcess("zio.ZIOAppJvmSuccess")
        } yield assertTrue(
          result.exitCode == 0,
          result.stdoutText.contains("APP_SUCCESS"),
          !result.stderrText.contains("NoClassDefFoundError"),
          !result.stderrText.contains("sun/misc/SignalHandler")
        )
      },
      test("failed app exits with non-zero code 1") {
        for {
          result <- runAppProcess("zio.ZIOAppJvmFail")
        } yield assertTrue(
          result.exitCode == 1,
          result.stdoutText.contains("APP_FAIL")
        )
      },
      test("defect in app exits with non-zero code 1") {
        for {
          result <- runAppProcess("zio.ZIOAppJvmDefect")
        } yield assertTrue(
          result.exitCode == 1,
          result.stdoutText.contains("APP_DEFECT")
        )
      },
      test("command-line arguments are passed through main to getArgs") {
        for {
          result <- runAppProcess("zio.ZIOAppJvmArgs", List("alpha", "beta", "gamma"))
        } yield assertTrue(
          result.exitCode == 0,
          result.stdoutText.contains("ARGS=alpha,beta,gamma")
        )
      },
      test("background root fibers are interrupted when main workflow completes (#9552)") {
        for {
          result <- runAppProcess("zio.ZIOAppJvmRootFibers")
        } yield assertTrue(
          result.exitCode == 0,
          result.stdoutText.contains("MAIN_DONE"),
          result.stdoutText.contains("ROOT_FIBER_INTERRUPTED")
        )
      }
    ),
    suite("signal handling")(
      test("scoped and app finalizers run to completion on external termination signal (#9901)") {
        for {
          result <- runAppProcess("zio.ZIOAppJvmFinalizer", destroyOnLine = Some("APP_READY"))
        } yield assertTrue(
          result.exitCode != -1,
          result.stdoutText.contains("ACQUIRED_APP_SCOPE"),
          result.stdoutText.contains("RELEASED_SCOPED"),
          result.stdoutText.contains("RELEASED_APP_SCOPE")
        )
      },
      test("gracefulShutdownTimeout bounds shutdown wait when finalizer is slow") {
        for {
          result <- runAppProcess("zio.ZIOAppJvmSlowFinalizer", destroyOnLine = Some("APP_READY"))
        } yield assertTrue(
          result.exitCode != -1,
          result.stdoutText.contains("SLOW_FINALIZER_STARTED"),
          result.stdoutText.contains("Timed out waiting for ZIO application to shut down")
        )
      },
      test("concurrent shutdown hooks run without uncaught FiberFailure on stderr (#9807)") {
        for {
          result <- runAppProcess("zio.ZIOAppJvmCleanShutdown", destroyOnLine = Some("APP_READY"))
        } yield assertTrue(
          result.exitCode != -1,
          result.stderrText.contains("USER_SHUTDOWN_HOOK_RAN"),
          !result.stderrText.contains("FiberFailure"),
          !result.stderrText.contains("Exception in thread")
        )
      }
    ) @@ TestAspect.unix
  ) @@ TestAspect.sequential @@ TestAspect.timeout(60.seconds)
}

object ZIOAppJvmSuccess extends ZIOAppDefault {
  val run = Console.printLine("APP_SUCCESS")
}

object ZIOAppJvmFail extends ZIOAppDefault {
  val run = Console.printLine("APP_FAIL") *> ZIO.fail("boom")
}

object ZIOAppJvmDefect extends ZIOAppDefault {
  val run = Console.printLine("APP_DEFECT") *> ZIO.dieMessage("defect")
}

object ZIOAppJvmArgs extends ZIOAppDefault {
  val run = getArgs.flatMap(args => Console.printLine(s"ARGS=${args.mkString(",")}"))
}

object ZIOAppJvmRootFibers extends ZIOAppDefault {
  val run =
    for {
      started <- Promise.make[Nothing, Unit]
      _       <- ZIO.foreachDiscard(1 to 64)(_ => ZIO.unit.forkDaemon)
      daemon   = (started.succeed(()) *> ZIO.never).onInterrupt(Console.printLine("ROOT_FIBER_INTERRUPTED").orDie)
      _       <- daemon.forkDaemon
      _       <- started.await
      _       <- Console.printLine("MAIN_DONE")
    } yield ()
}

object ZIOAppJvmFinalizer extends ZIOAppDefault {
  private val appRelease    = ZIO.sleep(100.millis) *> Console.printLine("RELEASED_APP_SCOPE").orDie
  private val scopedRelease = ZIO.sleep(100.millis) *> Console.printLine("RELEASED_SCOPED").orDie

  val run =
    for {
      _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED_APP_SCOPE"))(_ => appRelease)
      _ <- ZIO.scoped(ZIO.acquireRelease(Console.printLine("APP_READY"))(_ => scopedRelease) *> ZIO.never)
    } yield ()
}

object ZIOAppJvmSlowFinalizer extends ZIOAppDefault {
  override def gracefulShutdownTimeout: Duration = 200.millis

  private val slowRelease =
    Console.printLine("SLOW_FINALIZER_STARTED").orDie *> ZIO.sleep(30.seconds).uninterruptible

  val run =
    ZIO.scoped(ZIO.acquireRelease(Console.printLine("APP_READY"))(_ => slowRelease) *> ZIO.never)
}

object ZIOAppJvmCleanShutdown extends ZIOAppDefault {
  private val registerHook = ZIO.succeed {
    java.lang.Runtime.getRuntime.addShutdownHook(
      new Thread {
        override def run(): Unit = {
          java.lang.System.err.println("USER_SHUTDOWN_HOOK_RAN")
          Thread.sleep(300)
        }
      }
    )
  }

  val run = registerHook *> Console.printLine("APP_READY") *> ZIO.never
}
