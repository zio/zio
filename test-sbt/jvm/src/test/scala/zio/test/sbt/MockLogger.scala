package zio.test.sbt

import java.util.concurrent.atomic.AtomicReference

import sbt.testing.Logger
import zio.test.sbt.TestingSupport._

class MockLogger extends Logger {
  private val logged = new AtomicReference(Vector.empty[String])
  private def log(str: String) = {
    logged.getAndUpdate(_ :+ str)
    ()
  }
  private def logWithPrefix(s: String)(prefix: String): Unit =
    log(s.split("\n").map(reset(prefix) + _).mkString("\n"))
  def messages: Seq[String] = logged.get()

  override def ansiCodesSupported(): Boolean = false
  override def error(msg: String): Unit      = logWithPrefix(msg)("error: ")
  override def warn(msg: String): Unit       = logWithPrefix(msg)("warn: ")
  override def info(msg: String): Unit       = logWithPrefix(msg)("info: ")
  override def debug(msg: String): Unit      = logWithPrefix(msg)("debug: ")
  override def trace(t: Throwable): Unit     = logWithPrefix(t.toString)("trace: ")
}
