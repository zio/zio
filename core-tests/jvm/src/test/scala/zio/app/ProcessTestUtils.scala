package zio.app

import zio._
import zio.test._

import java.io.{BufferedReader, File, InputStreamReader}
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer

/**
 * Utilities for testing ZIOApp behavior by spawning external JVM processes.
 * This allows testing of:
 *   - Exit codes
 *   - Signal handling (SIGINT/SIGTERM)
 *   - Finalizer execution
 *   - Graceful shutdown timeout behavior
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
    def allOutput: List[String]            = stdout ++ stderr
  }

  case class RunningProcess(
    process: Process,
    pid: Long,
    startTime: java.time.Instant,
    stdoutBuffer: scala.collection.mutable.ListBuffer[String],
    stderrBuffer: scala.collection.mutable.ListBuffer[String],
    stdoutThread: Thread,
    stderrThread: Thread
  ) {
    def isAlive: Boolean = process.isAlive
    def exitValue: Int   = process.exitValue()
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
      val exe       = if (sys.props("os.name").toLowerCase.contains("win")) ".exe" else ""
      s"$javaHome${separator}bin${separator}java$exe"
    } else {
      "java" // Rely on PATH
    }
  }

  /**
   * Gets the classpath for running test apps
   */
  private def classpath: String = sys.props.getOrElse("java.class.path", "")

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
  ): ZIO[Any, Throwable, ProcessResult] =
    ZIO.attemptBlocking {
      val stdoutBuffer = ListBuffer.empty[String]
      val stderrBuffer = ListBuffer.empty[String]
      val startTime    = java.lang.System.nanoTime()

      val processBuilder = new ProcessBuilder(command: _*)
      env.foreach { case (k, v) => processBuilder.environment().put(k, v) }

      val process = processBuilder.start()

      // Read stdout in separate thread
      val stdoutReader = new Thread(() => {
        val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
        try {
          var line: String = null
          while ({ line = reader.readLine(); line != null }) {
            stdoutBuffer.synchronized(stdoutBuffer += line)
          }
        } finally reader.close()
      })

      // Read stderr in separate thread
      val stderrReader = new Thread(() => {
        val reader = new BufferedReader(new InputStreamReader(process.getErrorStream))
        try {
          var line: String = null
          while ({ line = reader.readLine(); line != null }) {
            stderrBuffer.synchronized(stderrBuffer += line)
          }
        } finally reader.close()
      })

      stdoutReader.start()
      stderrReader.start()

      val completed = process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
      val endTime   = java.lang.System.nanoTime()

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
  ): ZIO[Scope, Throwable, RunningProcess] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        val stdoutBuffer   = ListBuffer.empty[String]
        val stderrBuffer   = ListBuffer.empty[String]
        val processBuilder = new ProcessBuilder(command: _*)
        env.foreach { case (k, v) => processBuilder.environment().put(k, v) }
        val process = processBuilder.start()
        val pid     = process.pid()

        // Read stdout in separate thread
        val stdoutReader = new Thread(() => {
          val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
          try {
            var line: String = null
            while ({ line = reader.readLine(); line != null }) {
              stdoutBuffer.synchronized(stdoutBuffer += line)
            }
          } finally reader.close()
        })
        stdoutReader.setDaemon(true)
        stdoutReader.start()

        // Read stderr in separate thread
        val stderrReader = new Thread(() => {
          val reader = new BufferedReader(new InputStreamReader(process.getErrorStream))
          try {
            var line: String = null
            while ({ line = reader.readLine(); line != null }) {
              stderrBuffer.synchronized(stderrBuffer += line)
            }
          } finally reader.close()
        })
        stderrReader.setDaemon(true)
        stderrReader.start()

        RunningProcess(process, pid, java.time.Instant.now(), stdoutBuffer, stderrBuffer, stdoutReader, stderrReader)
      }
    )(rp =>
      ZIO.attemptBlocking {
        if (rp.process.isAlive) {
          rp.process.destroyForcibly()
          rp.process.waitFor(5, TimeUnit.SECONDS)
        }
        rp.stdoutThread.join(1000)
        rp.stderrThread.join(1000)
      }.orDie
    )

  /**
   * Sends a signal to a process (Unix-like systems only)
   */
  def sendSignal(pid: Long, signal: String): ZIO[Any, Throwable, Unit] =
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

  /**
   * Waits for a process to complete with timeout
   */
  def waitForProcess(
    runningProcess: RunningProcess,
    timeout: Duration
  ): ZIO[Any, Throwable, ProcessResult] =
    ZIO.attemptBlocking {
      val completed = runningProcess.process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)

      if (!completed) {
        runningProcess.process.destroyForcibly()
        runningProcess.process.waitFor(5, TimeUnit.SECONDS)
      }

      runningProcess.stdoutThread.join(2000)
      runningProcess.stderrThread.join(2000)

      val endTime = java.time.Instant.now()
      val duration = Duration.fromMillis(
        java.time.Duration.between(runningProcess.startTime, endTime).toMillis
      )

      ProcessResult(
        exitCode = if (completed) runningProcess.exitValue else -1,
        stdout = runningProcess.stdoutBuffer.synchronized(runningProcess.stdoutBuffer.toList),
        stderr = runningProcess.stderrBuffer.synchronized(runningProcess.stderrBuffer.toList),
        duration = duration
      )
    }

  /**
   * Waits for a specific output pattern to appear
   */
  def waitForOutput(
    runningProcess: RunningProcess,
    pattern: String,
    timeout: Duration
  ): ZIO[Any, Throwable, Boolean] =
    ZIO.attemptBlocking {
      val deadline = java.lang.System.currentTimeMillis() + timeout.toMillis
      var found    = false
      while (java.lang.System.currentTimeMillis() < deadline && runningProcess.process.isAlive && !found) {
        runningProcess.stdoutBuffer.synchronized {
          found = runningProcess.stdoutBuffer.exists(_.contains(pattern))
        }
        if (!found) Thread.sleep(50)
      }
      found
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
