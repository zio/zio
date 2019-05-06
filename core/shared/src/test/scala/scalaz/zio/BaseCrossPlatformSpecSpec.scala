package scalaz.zio

import scalaz.zio.duration._

// THIS IS TEMPORARY, TO PROVE THAT TESTS RUNNING TOO LONG WOULD ERR
class BaseCrossPlatformSpecSpec extends BaseCrossPlatformSpec {

  def is =
    "TestRuntimeJS".title ^ s2"""
    This case should error to prove timeouts when test execution take too long $e1
    """

  def e1 =
    runToFutureWithTimeout(
      for {
        _     <- ZIO.sleep(100.milliseconds)
        value <- ZIO(42)
      } yield value must_=== 42,
      10.milliseconds.asScala
    ).failed.map(e => e.getMessage must_=== "TIMEOUT: 10000000 nanoseconds")
}
