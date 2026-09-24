package zio.testapps

import java.io.{BufferedReader, InputStreamReader}
import java.util.concurrent.TimeUnit

final case class ProcessResult(exitCode: Int, stdout: String, stderr: String)

object ProcessTestHelper {

  def runApp(mainClass: String, timeout: java.time.Duration): ProcessResult = {
    val classpath = System.getProperty("java.class.path")
    val javaHome  = System.getProperty("java.home")
    val javaBin   = s"$javaHome/bin/java"

    val pb      = new ProcessBuilder(javaBin, "-cp", classpath, mainClass)
    val process = pb.start()

    val stdoutBuilder = new StringBuilder
    val stderrBuilder = new StringBuilder

    val stdoutThread = readerThread(new BufferedReader(new InputStreamReader(process.getInputStream)), stdoutBuilder)
    val stderrThread = readerThread(new BufferedReader(new InputStreamReader(process.getErrorStream)), stderrBuilder)

    stdoutThread.start()
    stderrThread.start()

    val exited = process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
    if (!exited) process.destroyForcibly()

    stdoutThread.join(5000)
    stderrThread.join(5000)

    ProcessResult(
      if (exited) process.exitValue() else -1,
      stdoutBuilder.toString,
      stderrBuilder.toString
    )
  }

  def runAppAndSignal(mainClass: String, readyMarker: String, timeout: java.time.Duration): ProcessResult = {
    val classpath = System.getProperty("java.class.path")
    val javaHome  = System.getProperty("java.home")
    val javaBin   = s"$javaHome/bin/java"

    val pb      = new ProcessBuilder(javaBin, "-cp", classpath, mainClass)
    val process = pb.start()

    val stdoutBuilder = new StringBuilder
    val stderrBuilder = new StringBuilder

    val stderrThread = readerThread(new BufferedReader(new InputStreamReader(process.getErrorStream)), stderrBuilder)
    stderrThread.start()

    val stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream))
    val startTime    = System.currentTimeMillis()
    val deadlineMs   = startTime + timeout.toMillis

    var line        = stdoutReader.readLine()
    var markerFound = false
    while (line != null && !markerFound) {
      stdoutBuilder.append(line).append('\n')
      if (line.contains(readyMarker)) markerFound = true
      else line = stdoutReader.readLine()
    }

    if (!markerFound) {
      process.destroyForcibly()
      return ProcessResult(-1, stdoutBuilder.toString, stderrBuilder.toString)
    }

    val pid = process.pid()
    Runtime.getRuntime.exec(Array("kill", "-TERM", pid.toString)).waitFor()

    val remainingMs = deadlineMs - System.currentTimeMillis()

    val tailThread = readerThread(stdoutReader, stdoutBuilder)
    tailThread.start()

    val exited = process.waitFor(math.max(remainingMs, 1000L), TimeUnit.MILLISECONDS)
    if (!exited) process.destroyForcibly()

    tailThread.join(5000)
    stderrThread.join(5000)

    ProcessResult(
      if (exited) process.exitValue() else -1,
      stdoutBuilder.toString,
      stderrBuilder.toString
    )
  }

  private def readerThread(reader: BufferedReader, sb: StringBuilder): Thread = {
    val t = new Thread(() => {
      try {
        var line = reader.readLine()
        while (line != null) {
          sb.append(line).append('\n')
          line = reader.readLine()
        }
      } catch {
        case _: Exception => ()
      }
    })
    t.setDaemon(true)
    t
  }
}
