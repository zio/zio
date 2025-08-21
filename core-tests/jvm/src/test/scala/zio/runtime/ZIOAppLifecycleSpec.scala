package zio.runtime

import zio.test._
import java.lang.ProcessBuilder

object ZIOAppLifecycleSpec extends ZIOSpecDefault {

  def runApp(appClass: String): (Int, String) = {
    val javaBin = System.getProperty("java.home") + "/bin/java"
    val classpath = System.getProperty("java.class.path")
    val process = new ProcessBuilder(
      javaBin,
      "-cp",
      classpath,
      appClass
    ).redirectErrorStream(true).start()

    val output = scala.io.Source.fromInputStream(process.getInputStream).mkString
    val exitCode = process.waitFor()
    (exitCode, output)
  }

  def spec = suite("ZIOAppLifecycleSpec")(
    test("FinalizingApp should run finalizer on shutdown") {
      val (exitCode, output) = runApp("zio.runtime.fixtures.FinalizingApp")
      assertTrue(exitCode == 0 && output.contains("Finalizer ran"))
    },
    test("LoggingApp should log start and finish messages") {
      val (exitCode, output) = runApp("zio.runtime.fixtures.LoggingApp")
      assertTrue(exitCode == 0 && output.contains("App started") && output.contains("App finished"))
    },
    test("TimeoutApp should fail with timeout") {
      val (exitCode, output) = runApp("zio.runtime.fixtures.TimeoutApp")
      assertTrue(exitCode != 0 && output.contains("Timed out"))
    }
  )
}