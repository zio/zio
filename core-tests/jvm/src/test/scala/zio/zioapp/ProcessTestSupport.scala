package zio.zioapp

import zio.Duration

import java.io.{BufferedReader, InputStreamReader}
import java.util.concurrent.{CountDownLatch, TimeUnit}

object ProcessTestSupport {

  final case class ProcessResult(exitCode: Int, stdout: String, stderr: String)

  val isUnix: Boolean =
    !System.getProperty("os.name", "").toLowerCase.contains("win")

  def runApp(mainClass: String, timeout: Duration): ProcessResult = {
    val processBuilder = childProcessBuilder(mainClass)
    val process        = processBuilder.start()

    val stdout = new StringBuilder
    val stderr = new StringBuilder

    val outThread = drainLines(process.getInputStream, stdout, None)
    val errThread = drainLines(process.getErrorStream, stderr, None)

    outThread.start()
    errThread.start()

    val completed =
      process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)

    if (!completed) {
      destroyForcibly(process)
      ProcessResult(-1, stdout.toString, stderr.toString)
    } else {
      ProcessResult(process.exitValue(), stdout.toString, stderr.toString)
    }
  }

  def runAppAndSendSIGINT(mainClass: String, readyMarker: String, timeout: Duration): ProcessResult = {
    val processBuilder = childProcessBuilder(mainClass)
    val process        = processBuilder.start()

    val stdout = new StringBuilder
    val stderr = new StringBuilder

    val readyLatch = new CountDownLatch(1)

    val outThread = drainLines(process.getInputStream, stdout, Some(readyMarker -> readyLatch))
    val errThread = drainLines(process.getErrorStream, stderr, None)

    outThread.start()
    errThread.start()

    val ready =
      readyLatch.await(timeout.toMillis, TimeUnit.MILLISECONDS)

    if (!ready) {
      destroyForcibly(process)
      ProcessResult(-1, stdout.toString, stderr.toString)
    } else {
      val pid = process.pid()

      val killExitCode =
        try {
          val kill = Runtime.getRuntime.exec(Array("kill", "-INT", pid.toString))
          kill.waitFor()
          kill.exitValue()
        } catch {
          case _: Throwable =>
            // Fallback: ensure the process is at least asked to terminate.
            ProcessHandle.of(pid).ifPresent(ph => { ph.destroy(); () })
            -1
        }

      stderr.synchronized {
        stderr.append("\n")
        stderr.append(s"KILL_INT_EXIT_CODE=$killExitCode")
        stderr.append("\n")
      }

      val completed =
        process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)

      if (!completed) {
        destroyForcibly(process)
        ProcessResult(-1, stdout.toString, stderr.toString)
      } else {
        ProcessResult(process.exitValue(), stdout.toString, stderr.toString)
      }
    }
  }

  private def childProcessBuilder(mainClass: String): ProcessBuilder = {
    val javaHome  = System.getProperty("java.home")
    val javaBin   = s"$javaHome/bin/java"
    val classpath = System.getProperty("java.class.path")
    val builder   = new ProcessBuilder(javaBin, "-cp", classpath, mainClass)
    val _         = builder.redirectInput(ProcessBuilder.Redirect.PIPE)
    builder
  }

  private def drainLines(
    inputStream: java.io.InputStream,
    dest: StringBuilder,
    ready: Option[(String, CountDownLatch)]
  ): Thread =
    new Thread(() => {
      val reader = new BufferedReader(new InputStreamReader(inputStream))
      try {
        var line: String = reader.readLine()
        while (line != null) {
          dest.synchronized {
            dest.append(line)
            dest.append("\n")
          }

          ready.foreach { case (marker, latch) =>
            if (line.contains(marker)) latch.countDown()
          }

          line = reader.readLine()
        }
      } catch {
        case _: Throwable =>
      } finally {
        try reader.close()
        catch {
          case _: Throwable =>
        }
      }
    }) {
      setDaemon(true)
      setName("zioapp-process-drain")
    }

  private def destroyForcibly(process: Process): Unit =
    try {
      ProcessHandle.of(process.pid()).ifPresent(ph => { ph.destroyForcibly(); () })
      process.destroyForcibly()
      ()
    } catch {
      case _: Throwable =>
    }
}
