package zio

final class PlatformSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "PlatformSpec".title ^ s2"""
        PlatformLive fatal:
          `Platform.fatal` should identify a nonFatal exception $e1
          `Platform.fatal` should identify a fatal exception $e2
    """

  def e1 = {
    val nonFatal = new Exception

    Platform.fatal(nonFatal) must_=== false
  }

  def e2 = {
    val fatal = new OutOfMemoryError

    Platform.fatal(fatal) must_=== true
  }
}
