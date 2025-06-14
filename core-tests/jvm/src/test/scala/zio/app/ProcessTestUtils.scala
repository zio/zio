package zio.app

import java.io.{BufferedReader, File, InputStreamReader, PrintWriter}
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference
import zio._

/**
 * Utilities for process-based testing of ZIOApp.
 * This allows starting a ZIO application in a separate process and controlling/monitoring it.
 */
object ProcessTestUtils {

  /**
   * Represents a running ZIO application process.
   *
   * @param process The underlying JVM process
   * @param outputCapture The captured stdout/stderr output
   * @param outputFile The file where output is being written
   */
  final case class AppProcess(
    process: java.lang.Process, 
    outputCapture: Ref[Chunk[String]],
    outputFile: File
  ) {
    /**
     * Checks if the process is still alive.
     */
    def isAlive: Boolean = process.isAlive

    /**
     * Gets the process exit code if available.
     */
    def exitCode: Task[Int] = 
      if (process.isAlive) ZIO.fail(new RuntimeException("Process still running"))
      else ZIO.succeed(process.exitValue())

    /**
     * Sends a signal to the process.
     *
     * @param signal The signal to send (e.g. "TERM", "INT", etc.)
     */
    def sendSignal(signal: String): Task[Unit] = ZIO.attempt {
      if (!process.isAlive) {
        println(s"Process is no longer alive, cannot send signal $signal")
        return ZIO.unit
      }
      
      val pidOpt = ProcessHandle.of(process.pid())
      if (pidOpt.isEmpty) {
        println(s"Cannot get process handle for PID ${process.pid()}, process may have terminated")
        return ZIO.unit
      }
      
      val pid = pidOpt.get()
      val isWindows = java.lang.System.getProperty("os.name", "").toLowerCase().contains("win")
      
      if (isWindows) {
        // Windows doesn't have the same signal mechanism as Unix
        signal match {
          case "INT" => // Simulate Ctrl+C
            process.destroy()
          case "TERM" => // Equivalent to SIGTERM
            process.destroy() 
          case "KILL" => // Equivalent to SIGKILL
            process.destroyForcibly(); ()
          case _ =>
            throw new UnsupportedOperationException(s"Signal $signal not supported on Windows")
        }
      } else {
        // Unix/Mac implementation
        import scala.sys.process._
        signal match {
          case "INT" => 
            val exitCode = s"kill -SIGINT ${pid.pid()}".!
            if (exitCode != 0) {
              throw new RuntimeException(s"Failed to send SIGINT to process ${pid.pid()}, exit code: $exitCode")
            }
          case "TERM" => 
            val exitCode = s"kill -SIGTERM ${pid.pid()}".!
            if (exitCode != 0) {
              throw new RuntimeException(s"Failed to send SIGTERM to process ${pid.pid()}, exit code: $exitCode")
            }
          case "KILL" => 
            val exitCode = s"kill -SIGKILL ${pid.pid()}".!
            if (exitCode != 0) {
              throw new RuntimeException(s"Failed to send SIGKILL to process ${pid.pid()}, exit code: $exitCode")
            }
          case other => 
            val exitCode = s"kill -$other ${pid.pid()}".!
            if (exitCode != 0) {
              throw new RuntimeException(s"Failed to send signal $other to process ${pid.pid()}, exit code: $exitCode")
            }
        }
      }
    }

    /**
     * Gets the captured output from the process.
     */
    def output: UIO[Chunk[String]] = outputCapture.get

    /**
     * Gets the captured output as a string.
     */
    def outputString: UIO[String] = output.map(_.mkString(java.lang.System.getProperty("line.separator")))

    /**
     * Waits for a specific string to appear in the output.
     *
     * @param marker The string to wait for
     * @param timeout Maximum time to wait
     */
    def waitForOutput(marker: String, timeout: Duration = 10.seconds): ZIO[Any, Throwable, Boolean] = {
      def check: ZIO[Any, Nothing, Boolean] =
        outputString.map(_.contains(marker))

      def loop: ZIO[Any, Nothing, Boolean] =
        check.flatMap {
          case true  => ZIO.succeed(true)
          case false => ZIO.sleep(100.millis) *> loop
        }

      loop.timeout(timeout).map(_.getOrElse(false))
    }

    /**
     * Waits for the process to exit.
     *
     * @param timeout Maximum time to wait
     */
    def waitForExit(timeout: Duration = 30.seconds): Task[Int] = {
      ZIO.attemptBlockingInterrupt {
        val exitCode = process.waitFor()
        if (process.isAlive) throw new RuntimeException("Process wait timed out")
        exitCode
      }.timeout(timeout).flatMap {
        case Some(exitCode) => ZIO.succeed(exitCode)
        case None => ZIO.fail(new RuntimeException("Process wait timed out"))
      }
    }

    /**
     * Forcibly terminates the process.
     */
    def destroy: Task[Unit] = ZIO.attempt {
      if (process.isAlive) {
        process.destroy()
        process.waitFor(); ()
      }
      val deleted = Files.deleteIfExists(outputFile.toPath)
      if (!deleted) {
        // Log but don't fail if file couldn't be deleted - it might be cleaned up later
        println(s"Warning: Could not delete temporary file: ${outputFile.getAbsolutePath}")
      }
    }
  }

  /**
   * Runs a ZIO application in a separate process.
   *
   * @param mainClass The fully qualified name of the ZIOApp class
   * @param gracefulShutdownTimeout Custom graceful shutdown timeout (if testing it)
   * @param jvmArgs Additional JVM arguments
   */
  def runApp(
    mainClass: String, 
    gracefulShutdownTimeout: Option[Duration] = None,
    jvmArgs: List[String] = List.empty
  ): ZIO[Any, Throwable, AppProcess] = {
    for {
      outputFile <- ZIO.attempt {
        val tempFile = File.createTempFile("zio-test-", ".log")
        tempFile.deleteOnExit()
        tempFile
      }
      
      outputRef <- Ref.make(Chunk.empty[String])
      
      process <- ZIO.attempt {
        val classPath = java.lang.System.getProperty("java.class.path")
        
        // Configure JVM arguments including custom shutdown timeout if provided
        val allJvmArgs = gracefulShutdownTimeout match {
          case Some(timeout) => 
            s"-Dzio.app.shutdown.timeout=${timeout.toMillis}" :: jvmArgs
          case None =>
            jvmArgs
        }
        
        val processBuilder = new ProcessBuilder()
        val cmdList = List("java") ++ allJvmArgs ++ List("-cp", classPath, mainClass)
        import scala.jdk.CollectionConverters._
        processBuilder.command(cmdList.asJava)
        
        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(ProcessBuilder.Redirect.to(outputFile))
        
        processBuilder.start()
      }
      
      // Start a background fiber to monitor the output
      _ <- ZIO.attemptBlockingInterrupt {
        val reader = new BufferedReader(new InputStreamReader(Files.newInputStream(outputFile.toPath)))
        var line: String = null
        val buffer = new AtomicReference[Chunk[String]](Chunk.empty)
        
        def readLoop(): Unit = {
          line = reader.readLine()
          if (line != null) {
            buffer.updateAndGet(_ :+ line)
            readLoop()
          }
        }
        
        while (process.isAlive) {
          readLoop()
          Unsafe.unsafe { implicit unsafe =>
            Runtime.default.unsafe.run(outputRef.set(buffer.get)).getOrThrowFiberFailure()
          }
          Thread.sleep(100)
        }
        
        readLoop() // One final read after process has exited
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(outputRef.set(buffer.get)).getOrThrowFiberFailure()
        }
        reader.close()
      }.fork
    } yield AppProcess(process, outputRef, outputFile)
  }

  /**
   * Creates a simple test application with configurable behavior.
   * This can be used to compile and run test applications dynamically.
   *
   * @param className The name of the class to generate
   * @param behavior The effect to run in the application
   * @param packageName Optional package name
   * @return Path to the generated source file
   */
  def createTestApp(
    className: String,
    behavior: String,
    packageName: Option[String] = None
  ): ZIO[Any, Throwable, Path] = {
    ZIO.attempt {
      val packageDecl = packageName.fold("")(pkg => s"package $pkg\n\n")
      
      val code =
        s"""$packageDecl
           |import zio._
           |
           |object $className extends ZIOAppDefault {
           |  override def run = {
           |    $behavior
           |  }
           |}
           |""".stripMargin
      
      val tmpDir = Files.createTempDirectory("zio-test-")
      val pkgDirs = packageName.map(_.split('.').toList).getOrElse(List.empty)
      
      val fileDir = pkgDirs.foldLeft(tmpDir) { (dir, pkg) =>
        val newDir = dir.resolve(pkg)
        Files.createDirectories(newDir)
        newDir
      }
      
      val srcFile = fileDir.resolve(s"$className.scala")
      val writer = new PrintWriter(srcFile.toFile)
      try {
        writer.write(code)
      } finally {
        writer.close()
      }
      
      srcFile
    }
  }
} 