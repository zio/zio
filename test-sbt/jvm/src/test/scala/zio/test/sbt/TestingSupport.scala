package zio.test.sbt

import scala.util.control.NonFatal
import scala.util.{Failure, Try}

object TestingSupport {
  def test[T](l: String)(body: => Unit): Try[Unit] =
    Try {
      body
      println(s"${green("+")} $l")
    }.recoverWith { case NonFatal(e) =>
      println(s"${red("-")} $l: ${e.getMessage}")
      e.printStackTrace()
      Failure(e)
    }

  def run(tests: Try[Unit]*): Unit = {
    val failed       = tests.count(_.isFailure)
    val successful   = tests.count(_.isSuccess)
    val failedCount  = if (failed > 0) red(s"failed: $failed") else s"failed: $failed"
    val successCount = if (successful > 0) green(s"successful: $successful") else s"successful: $successful"
    println(s"Summary: $failedCount, $successCount")
    if (failed > 0)
      throw new AssertionError(s"$failed tests failed")
  }

  def assertEquals(what: String, actual: => Any, expected: Any): Unit =
    assert(actual == expected, s"$what:\n  expected: `$expected`\n  actual  : `$actual`")

  def colored(code: String)(str: String): String = s"$code$str${Console.RESET}"
  lazy val red: String => String                 = colored(Console.RED) _
  lazy val green: String => String               = colored(Console.GREEN) _
  lazy val cyan: String => String                = colored(Console.CYAN) _
  lazy val blue: String => String                = colored(Console.BLUE) _
  lazy val yellow: String => String              = colored(Console.YELLOW) _

  def reset(str: String): String =
    s"${Console.RESET}$str"
}
