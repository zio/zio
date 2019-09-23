package zio.test.sbt

import java.util.concurrent.atomic.AtomicReference

import sbt.testing.Logger

class MockLogger extends Logger {
  private val logged = new AtomicReference(Vector.empty[String])
  private def log(str: String) = {
    logged.getAndUpdate(_ :+ str)
    ()
  }
  def messages: Seq[String] = logged.get()

  override def ansiCodesSupported(): Boolean = false
  override def error(msg: String): Unit      = log(s"error: $msg")
  override def warn(msg: String): Unit       = log(s"warn: $msg")
  override def info(msg: String): Unit       = log(s"info: $msg")
  override def debug(msg: String): Unit      = log(s"debug: $msg")
  override def trace(t: Throwable): Unit     = log(s"trace: $t")
}
