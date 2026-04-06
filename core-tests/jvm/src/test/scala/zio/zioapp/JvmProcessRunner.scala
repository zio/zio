package zio.zioapp

import java.io.{BufferedReader, InputStreamReader}
import java.util.concurrent.{CountDownLatch, TimeUnit}

/**
 * Spawns a JVM child process running a given main class, captures stdout/stderr,
 * and optionally sends SIGINT to simulate Ctrl+C. Uses CountDownLatch with hard
 * timeouts so CI never hangs.
 *
 * Each test app prints "APP_READY" to stdout once it is blocked or done. The
 * `runAndInterrupt` variant waits for that marker before sending SIGINT.
 */
object JvmProcessRunner {

  final case class ProcessResult(exitCode: Int, stdout: String, stderr: String)

  private val DefaultTimeoutMs = 30_000L

  /**
   * Run the app, wait for it to exit naturally (or time out).
   */
  def runToCompletion(
    mainClass: String,
    args: List[String] = Nil,
    timeoutMs: Long = DefaultTimeoutMs
  ): ProcessResult = {
    val process = startProcess(mainClass, args)
    val (stdout, stderr) = drainOutput(process, timeoutMs)
    val exitCode =
      if (process.isAlive) { process.destroyForcibly(); -1 }
      else process.exitValue()
    ProcessResult(exitCode, stdout, stderr)
  }

  /**
   * Run the app, wait for the "APP_READY" marker on stdout, send SIGINT,
   * then wait for exit.
   */
  def runAndInterrupt(
    mainClass: String,
    args: List[String] = Nil,
    timeoutMs: Long = DefaultTimeoutMs
  ): ProcessResult = {
    val process    = startProcess(mainClass, args)
    val readyLatch = new CountDownLatch(1)
    val stdoutBuf  = new StringBuilder
    val stderrBuf  = new StringBuilder

    val stdoutThread = readerThread("stdout", process.getInputStream, stdoutBuf, Some(readyLatch))
    val stderrThread = readerThread("stderr", process.getErrorStream, stderrBuf, None)
    stdoutThread.start()
    stderrThread.start()

    // Wait for the app to signal it is ready
    val markerSeen = readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
    if (!markerSeen) {
      process.destroyForcibly()
      stdoutThread.join(2000)
      stderrThread.join(2000)
      return ProcessResult(-1, stdoutBuf.toString, stderrBuf.toString + "\n[TIMEOUT waiting for APP_READY]")
    }

    // Brief pause so the app is fully blocked (e.g. on ZIO.never)
    Thread.sleep(250)

    // Send actual SIGINT (not SIGTERM, which is what Process.destroy() sends)
    sendSigint(process)

    // Wait for the process to terminate
    val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (!finished) {
      process.destroyForcibly()
      stdoutThread.join(2000)
      stderrThread.join(2000)
      return ProcessResult(-1, stdoutBuf.toString, stderrBuf.toString + "\n[TIMEOUT waiting for exit]")
    }

    stdoutThread.join(3000)
    stderrThread.join(3000)

    ProcessResult(process.exitValue(), stdoutBuf.toString, stderrBuf.toString)
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  private def startProcess(mainClass: String, args: List[String]): Process = {
    val classpath = System.getProperty("java.class.path")
    val javaHome  = System.getProperty("java.home")
    val javaBin   = s"$javaHome/bin/java"
    val command   = List(javaBin, "-cp", classpath, mainClass) ++ args
    new ProcessBuilder(command: _*).start()
  }

  private def readerThread(
    name: String,
    stream: java.io.InputStream,
    buf: StringBuilder,
    readyLatch: Option[CountDownLatch]
  ): Thread = {
    val t = new Thread(() => {
      val reader = new BufferedReader(new InputStreamReader(stream))
      var line   = reader.readLine()
      while (line != null) {
        buf.append(line).append('\n')
        readyLatch.foreach { latch =>
          if (line.contains("APP_READY")) latch.countDown()
        }
        line = reader.readLine()
      }
    }, s"$name-reader")
    t.setDaemon(true)
    t
  }

  /**
   * Drain stdout and stderr, waiting up to `timeoutMs` for the process to exit.
   */
  private def drainOutput(process: Process, timeoutMs: Long): (String, String) = {
    val stdoutBuf = new StringBuilder
    val stderrBuf = new StringBuilder

    val stdoutThread = readerThread("stdout", process.getInputStream, stdoutBuf, None)
    val stderrThread = readerThread("stderr", process.getErrorStream, stderrBuf, None)
    stdoutThread.start()
    stderrThread.start()

    process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (process.isAlive) process.destroyForcibly()

    stdoutThread.join(3000)
    stderrThread.join(3000)

    (stdoutBuf.toString, stderrBuf.toString)
  }

  private def sendSigint(process: Process): Unit = {
    val pid         = process.pid()
    val killProcess = new ProcessBuilder("kill", "-INT", pid.toString).start()
    val _ = killProcess.waitFor(5, TimeUnit.SECONDS)
  }
}
