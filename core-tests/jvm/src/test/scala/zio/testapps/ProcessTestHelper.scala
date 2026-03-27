package zio.testapps

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CountDownLatch, TimeUnit}

final case class ProcessResult(exitCode: Int, stdout: String, stderr: String)

object ProcessTestHelper {

  def runApp(mainClass: String, timeout: java.time.Duration): ProcessResult = {
    val process = startProcess(mainClass)

    val stdoutBuffer = new StringBuffer
    val stderrBuffer = new StringBuffer

    val stdoutThread = readerThread(
      new BufferedReader(new InputStreamReader(process.getInputStream, StandardCharsets.UTF_8)),
      stdoutBuffer
    )
    val stderrThread = readerThread(
      new BufferedReader(new InputStreamReader(process.getErrorStream, StandardCharsets.UTF_8)),
      stderrBuffer
    )

    stdoutThread.start()
    stderrThread.start()

    val exited = process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
    if (!exited) {
      process.destroyForcibly()
      process.waitFor(5L, TimeUnit.SECONDS)
    }

    stdoutThread.join(5000L)
    stderrThread.join(5000L)

    ProcessResult(
      exitCode = safeExitCode(process, default = -1),
      stdout = stdoutBuffer.toString,
      stderr = stderrBuffer.toString
    )
  }

  def runAppAndSignal(
    mainClass: String,
    readyMarker: String,
    signal: String,
    timeout: java.time.Duration
  ): ProcessResult = {
    val process = startProcess(mainClass)

    val stdoutBuffer = new StringBuffer
    val stderrBuffer = new StringBuffer

    val markerLatch = new CountDownLatch(1)

    val stdoutThread = readerThread(
      new BufferedReader(new InputStreamReader(process.getInputStream, StandardCharsets.UTF_8)),
      stdoutBuffer,
      line => if (line.contains(readyMarker)) markerLatch.countDown()
    )
    val stderrThread = readerThread(
      new BufferedReader(new InputStreamReader(process.getErrorStream, StandardCharsets.UTF_8)),
      stderrBuffer
    )

    stdoutThread.start()
    stderrThread.start()

    val startedAt = System.currentTimeMillis()
    val timeoutMs = timeout.toMillis

    val markerSeen = markerLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    if (!markerSeen) {
      process.destroyForcibly()
      process.waitFor(5L, TimeUnit.SECONDS)
    } else {
      sendSignal(process, signal)

      val elapsedMs   = System.currentTimeMillis() - startedAt
      val remainingMs = math.max(timeoutMs - elapsedMs, 1000L)
      val exited      = process.waitFor(remainingMs, TimeUnit.MILLISECONDS)

      if (!exited) {
        process.destroyForcibly()
        process.waitFor(5L, TimeUnit.SECONDS)
      }
    }

    stdoutThread.join(5000L)
    stderrThread.join(5000L)

    ProcessResult(
      exitCode = safeExitCode(process, default = -1),
      stdout = stdoutBuffer.toString,
      stderr = stderrBuffer.toString
    )
  }

  private def startProcess(mainClass: String): Process = {
    val classpath = System.getProperty("java.class.path")
    val javaHome  = System.getProperty("java.home")
    val javaBin   = s"$javaHome/bin/java"

    new ProcessBuilder(javaBin, "-cp", classpath, mainClass).start()
  }

  private def sendSignal(process: Process, signal: String): Unit = {
    val pid = process.pid().toString
    val killProcess =
      new ProcessBuilder("kill", s"-$signal", pid)
        .redirectErrorStream(true)
        .start()
    val completed = killProcess.waitFor(5L, TimeUnit.SECONDS)
    if (!completed || killProcess.exitValue() != 0) {
      process.destroy()
    }
  }

  private def safeExitCode(process: Process, default: Int): Int =
    try process.exitValue()
    catch {
      case _: IllegalThreadStateException => default
    }

  private def readerThread(reader: BufferedReader, sink: StringBuffer, onLine: String => Unit = _ => ()): Thread = {
    val t = new Thread(() => {
      try {
        var line = reader.readLine()
        while (line != null) {
          sink.append(line).append('\n')
          onLine(line)
          line = reader.readLine()
        }
      } catch {
        case _: Throwable => ()
      } finally {
        try reader.close()
        catch {
          case _: Throwable => ()
        }
      }
    })
    t.setDaemon(true)
    t
  }
}