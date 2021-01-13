package zio.clock

import zio._
import zio.system._
import zio.test.Assertion._
import zio.test._

import java.util.concurrent.TimeUnit

object ClockSpecJVM extends ZIOBaseSpec {

  def spec: Spec[Annotations with TestConfig with ZTestEnv, TestFailure[Any], TestSuccess] = suite("ClockSpec")(
    testM("currentTime has microsecond resolution on JRE >= 9") {
      val unit = TimeUnit.MICROSECONDS
      for {
        jreVersion <- property("java.version")
        a          <- clock.currentTime(unit)
        _          <- ZIO.foreach(1 to 1000)(_ => UIO.unit) // just pass some time
        b          <- clock.currentTime(unit)
      } yield
      // This test should only run on JRE >= 9, which is when microsecond precision was introduced.
      // Versions of JREs < 9 started with s"1.${majorVersion}", then with JEP 223 they switched to semantic versioning.
      assert(jreVersion)(isSome(startsWithString("1."))) || assert((b - a) % 1000)(not(equalTo(0L)))
    }.provideLayer(Clock.live ++ System.live) @@ TestAspect.flaky
  )
}
