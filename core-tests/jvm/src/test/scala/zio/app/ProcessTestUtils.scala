package zio.app

import zio._
import zio.test._

import java.io.{BufferedReader, File, InputStreamReader}
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer

/**
 * Utilities for testing ZIOApp behavior by spawning external JVM processes.
 * This allows testing of:
 * - Exit codes
 * - Signal handling (SIGINT/SIGTERM)
 * - Finalizer execution
 * - Graceful shutdown timeout behavior
 */
object ProcessTestUtils {

  case class ProcessResult(
    exitCode: Int,
    stdout: List[String],
    stderr: List[String],
    duration: Duration
  ) {
    def stdoutContains(s: String): Boolean = stdout.exists(_.contains(s))
    def stderrContains(s: String): Boolean = stderr.exists(_.contains(s))
    def outputContains(s: String): Boolean = stdoutContains(s) || stderrContains(s)
    def allOutput: List[String] = stdout ++ stderr
  }

  case class RunningProcess(
    process: Process,
    pid: Long,
    startTime: java.time.Instant
  ) {
    def isAlive: Boolean = process.isAlive
    def exitValue: Int = process.exitValue()
    def destroyForcibly(): Unit = {
      process.destroyForcibly()
      ()
    }
  }

  /**
   * Finds the java executable path
   */
  private def javaPath: String = {
    val javaHome = sys.props.getOrElse("java.home", sys.env.getOrElse("JAVA_HOME", ""))
    if (javaHome.nonEmpty) {
      val separator = File.separator
      s"$javaHome${separator}bin${separator}java"
    } else {
      "java" // Rely on PATH
    }
  }

  /**
   * Gets the classpath for running test apps
   */
  private def classpath: String = sys.props.getOrElse("java.class.path", "")

  /**
   * Creates a temporary Scala source file for a test app
   */
  def createTestAppSource(appName: String, code: String): ZIO[Scope, Throwable, Path] = {
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        val tempDir = Files.createTempDirectory("zio-test-app")
        val sourceFile = tempDir.resolve(s"$appName.scala")
        Files.writeString(sourceFile, code)
        sourceFile
      }
    )(path => ZIO.attemptBlocking {
      Files.deleteIfExists(path)
      Files.deleteIfExists(path.getParent)
    }.orDie)
  }

  /**
   * Runs a ZIOApp class and captures output
   */
  def runApp(
    mainClass: String,
    args: List[String] = Nil,
    timeout: Duration = 30.seconds,
    env: Map[String, String] = Map.empty
  ): ZIO[Any, Throwable, ProcessResult] = {
    val command = List(javaPath, "-cp", classpath, mainClass) ++ args
    runCommand(command, timeout, env)
  }

  /**
   * Runs a command and captures output with timeout
   */
  def runCommand(
    command: List[String],
    timeout: Duration = 30.seconds,
    env: Map[String, String] = Map.empty
  ): ZIO[Any, Throwable, ProcessResult] = {
    ZIO.attemptBlocking {
      val stdoutBuffer = ListBuffer.empty[String]
      val stderrBuffer = ListBuffer.empty[String]
      val startTime = java.lang.System.nanoTime()

      val processBuilder = new ProcessBuilder(command: _*)
      env.foreach { case (k, v) => processBuilder.environment().put(k, v) }
      
      val process = processBuilder.start()

      // Read stdout in separate thread
      val stdoutReader = new Thread(() => {
        val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
        try {
          var line: String = null
          while ({ line = reader.readLine(); line != null }) {
            stdoutBuffer.synchronized { stdoutBuffer += line }
          }
        } finally reader.close()
      })

      // Read stderr in separate thread
      val stderrReader = new Thread(() => {
        val reader = new BufferedReader(new InputStreamReader(process.getErrorStream))
        try {
          var line: String = null
          while ({ line = reader.readLine(); line != null }) {
            stderrBuffer.synchronized { stderrBuffer += line }
          }
        } finally reader.close()
      })

      stdoutReader.start()
      stderrReader.start()

      val completed = process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
      val endTime = java.lang.System.nanoTime()

      if (!completed) {
        process.destroyForcibly()
        process.waitFor(5, TimeUnit.SECONDS)
      }

      stdoutReader.join(1000)
      stderrReader.join(1000)

      val exitCode = if (completed) process.exitValue() else -1
      val duration = Duration.fromNanos(endTime - startTime)

      ProcessResult(
        exitCode = exitCode,
        stdout = stdoutBuffer.toList,
        stderr = stderrBuffer.toList,
        duration = duration
      )
    }
  }

  /**
   * Starts a process without waiting for completion
   */
  def startApp(
    mainClass: String,
    args: List[String] = Nil,
    env: Map[String, String] = Map.empty
  ): ZIO[Scope, Throwable, RunningProcess] = {
    val command = List(javaPath, "-cp", classpath, mainClass) ++ args
    startCommand(command, env)
  }

  /**
   * Starts a command without waiting for completion
   */
  def startCommand(
    command: List[String],
    env: Map[String, String] = Map.empty
  ): ZIO[Scope, Throwable, RunningProcess] = {
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        val processBuilder = new ProcessBuilder(command: _*)
        env.foreach { case (k, v) => processBuilder.environment().put(k, v) }
        val process = processBuilder.start()
        val pid = process.pid()
        RunningProcess(process, pid, java.time.Instant.now())
      }
    )(rp => ZIO.attemptBlocking {
      if (rp.process.isAlive) {
        rp.process.destroyForcibly()
        rp.process.waitFor(5, TimeUnit.SECONDS)
      }
    }.orDie)
  }

  /**
   * Sends a signal to a process (Unix-like systems only)
   */
  def sendSignal(pid: Long, signal: String): ZIO[Any, Throwable, Unit] = {
    ZIO.attemptBlocking {
      val os = java.lang.System.getProperty("os.name").toLowerCase
      if (os.contains("win")) {
        throw new UnsupportedOperationException("Signal sending not supported on Windows")
      } else {
        val sigName = signal match {
          case "SIGINT"  => "INT"
          case "SIGTERM" => "TERM"
          case "SIGKILL" => "KILL"
          case other     => other.stripPrefix("SIG")
        }
        val exitCode = java.lang.Runtime.getRuntime.exec(Array("kill", s"-$sigName", pid.toString)).waitFor()
        if (exitCode != 0) {
          throw new RuntimeException(s"Failed to send signal $signal to process $pid, exit code: $exitCode")
        }
      }
    }
  }

  /**
   * Waits for a process to complete with timeout
   */
  def waitForProcess(
    runningProcess: RunningProcess,
    timeout: Duration
  ): ZIO[Any, Throwable, ProcessResult] = {
    ZIO.attemptBlocking {
      val stdoutBuffer = ListBuffer.empty[String]
      val stderrBuffer = ListBuffer.empty[String]

      // Read remaining output
      val stdoutReader = new BufferedReader(new InputStreamReader(runningProcess.process.getInputStream))
      val stderrReader = new BufferedReader(new InputStreamReader(runningProcess.process.getErrorStream))

      // Read in threads to avoid deadlock
      val stdoutThread = new Thread(() => {
        try {
          var line: String = null
          while ({ line = stdoutReader.readLine(); line != null }) {
            stdoutBuffer.synchronized { stdoutBuffer += line }
          }
        } catch { case _: Exception => }
        finally stdoutReader.close()
      })

      val stderrThread = new Thread(() => {
        try {
          var line: String = null
          while ({ line = stderrReader.readLine(); line != null }) {
            stderrBuffer.synchronized { stderrBuffer += line }
          }
        } catch { case _: Exception => }
        finally stderrReader.close()
      })

      stdoutThread.start()
      stderrThread.start()

      val completed = runningProcess.process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
      
      if (!completed) {
        runningProcess.process.destroyForcibly()
        runningProcess.process.waitFor(5, TimeUnit.SECONDS)
      }

      stdoutThread.join(2000)
      stderrThread.join(2000)

      val endTime = java.time.Instant.now()
      val duration = Duration.fromMillis(
        java.time.Duration.between(runningProcess.startTime, endTime).toMillis
      )

      ProcessResult(
        exitCode = if (completed) runningProcess.exitValue else -1,
        stdout = stdoutBuffer.toList,
        stderr = stderrBuffer.toList,
        duration = duration
      )
    }
  }

  /**
   * Waits for a specific output pattern to appear
   */
  def waitForOutput(
    runningProcess: RunningProcess,
    pattern: String,
    timeout: Duration
  ): ZIO[Any, Throwable, Boolean] = {
    ZIO.attemptBlocking {
      val deadline = java.lang.System.currentTimeMillis() + timeout.toMillis
      val reader = new BufferedReader(new InputStreamReader(runningProcess.process.getInputStream))
      
      try {
        var found = false
        while (java.lang.System.currentTimeMillis() < deadline && runningProcess.process.isAlive && !found) {
          if (reader.ready()) {
            val line = reader.readLine()
            if (line != null && line.contains(pattern)) {
              found = true
            }
          } else {
            Thread.sleep(50)
          }
        }
        found
      } finally {
        // Don't close reader - process might still be running
      }
    }
  }

  /**
   * Check if running on a Unix-like system that supports signals
   */
  def supportsSignals: Boolean = {
    val os = java.lang.System.getProperty("os.name").toLowerCase
    !os.contains("win")
  }

  /**
   * Aspect that skips tests if signals are not supported
   */
  def requiresSignals: TestAspect[Nothing, Any, Nothing, Any] =
    TestAspect.ifProp("os.name")(_.toLowerCase.contains("win") == false)
}
