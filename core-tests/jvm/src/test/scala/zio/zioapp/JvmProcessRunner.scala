package zio.zioapp

import java.io.{BufferedReader, InputStreamReader}
import java.util.concurrent.{CountDownLatch, TimeUnit}

// Helper to fork a JVM with a given main class, grab stdout/stderr and
// optionally send SIGINT. Uses CountDownLatch + hard timeouts so CI never hangs.
object JvmProcessRunner {

  case class ProcessResult(exitCode: Int, stdout: String, stderr: String)

  private val defaultTimeoutMs = 30000L

  // Run the app, wait for "APP_READY" marker, then let it finish on its own.
  def runToCompletion(
    mainClass: String,
    args: List[String] = Nil,
    timeoutMs: Long = defaultTimeoutMs
  ): ProcessResult = {
    val pb      = buildProcess(mainClass, args)
    val process = pb.start()

    val (stdout, stderr) = captureOutput(process, timeoutMs)
    val exitCode = if (process.isAlive) { process.destroyForcibly(); -1 }
    else process.exitValue()

    ProcessResult(exitCode, stdout, stderr)
  }

  // Run the app, wait for APP_READY, send SIGINT, wait for it to shut down.
  def runAndInterrupt(
    mainClass: String,
    args: List[String] = Nil,
    timeoutMs: Long = defaultTimeoutMs
  ): ProcessResult = {
    val pb      = buildProcess(mainClass, args)
    val process = pb.start()

    val readyLatch = new CountDownLatch(1)
    val stdoutBuf  = new StringBuilder
    val stderrBuf  = new StringBuilder

    // stdout reader thread
    val stdoutThread = new Thread(
      () => {
        val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
        var line   = reader.readLine()
        while (line != null) {
          stdoutBuf.append(line).append('\n')
          if (line.contains("APP_READY")) readyLatch.countDown()
          line = reader.readLine()
        }
      },
      "stdout-reader"
    )
    stdoutThread.setDaemon(true)
    stdoutThread.start()

    // stderr reader thread
    val stderrThread = new Thread(
      () => {
        val reader = new BufferedReader(new InputStreamReader(process.getErrorStream))
        var line   = reader.readLine()
        while (line != null) {
          stderrBuf.append(line).append('\n')
          line = reader.readLine()
        }
      },
      "stderr-reader"
    )
    stderrThread.setDaemon(true)
    stderrThread.start()

    val markerSeen = readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
    if (!markerSeen) {
      process.destroyForcibly()
      return ProcessResult(-1, stdoutBuf.toString(), stderrBuf.toString() + "\n[TIMEOUT waiting for APP_READY]")
    }

    // Small delay so the app is properly blocked on ZIO.never or similar
    Thread.sleep(200)

    // Send SIGINT to the process
    sendSigint(process)

    // Wait for the process to terminate
    val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (!finished) {
      process.destroyForcibly()
      return ProcessResult(-1, stdoutBuf.toString(), stderrBuf.toString() + "\n[TIMEOUT waiting for exit]")
    }

    // Let output threads finish
    stdoutThread.join(2000)
    stderrThread.join(2000)

    ProcessResult(process.exitValue(), stdoutBuf.toString(), stderrBuf.toString())
  }

  private def buildProcess(mainClass: String, args: List[String]): ProcessBuilder = {
    val classpath = System.getProperty("java.class.path")
    val javaHome  = System.getProperty("java.home")
    val javaBin   = s"$javaHome/bin/java"
    val cmd       = List(javaBin, "-cp", classpath, mainClass) ++ args
    new ProcessBuilder(cmd: _*)
  }

  private def captureOutput(process: Process, timeoutMs: Long): (String, String) = {
    val stdoutBuf = new StringBuilder
    val stderrBuf = new StringBuilder

    val stdoutThread = new Thread(
      () => {
        val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
        var line   = reader.readLine()
        while (line != null) {
          stdoutBuf.append(line).append('\n')
          line = reader.readLine()
        }
      },
      "stdout-reader"
    )
    stdoutThread.setDaemon(true)
    stdoutThread.start()

    val stderrThread = new Thread(
      () => {
        val reader = new BufferedReader(new InputStreamReader(process.getErrorStream))
        var line   = reader.readLine()
        while (line != null) {
          stderrBuf.append(line).append('\n')
          line = reader.readLine()
        }
      },
      "stderr-reader"
    )
    stderrThread.setDaemon(true)
    stderrThread.start()

    val _ = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (process.isAlive) process.destroyForcibly()

    stdoutThread.join(2000)
    stderrThread.join(2000)

    (stdoutBuf.toString(), stderrBuf.toString())
  }

  private def sendSigint(process: Process): Unit = {
    val pid = process.pid()
    // Use kill -INT to send actual SIGINT, not SIGTERM
    val killProcess = new ProcessBuilder("kill", "-INT", pid.toString).start()
    val _           = killProcess.waitFor(5, TimeUnit.SECONDS)
  }
}
