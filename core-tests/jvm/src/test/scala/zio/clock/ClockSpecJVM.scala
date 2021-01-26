package zio.clock

import java.time.Instant

import zio._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.Live
import java.util.concurrent.TimeUnit

object ClockSpecJVM extends ZIOBaseSpec {

  def spec: Spec[Annotations with TestConfig with ZTestEnv with Live with Annotations, TestFailure[Any], TestSuccess] =
    suite("ClockSpec")(
      testM("currentTime has microsecond resolution on JRE >= 9") {
        val unit = TimeUnit.MICROSECONDS
        for {
          a <- clock.currentTime(unit)
          _ <- ZIO.foreach(1 to 1000)(_ => UIO.unit) // just pass some time
          b <- clock.currentTime(unit)
        } yield assert((b - a) % 1000)(not(equalTo(0L)))
      }.provideLayer(Clock.live)
      // We might actually have measured exactly one millisecond. In that case we can simply retry.
        @@ TestAspect.flaky
        // This test should only run on JRE >= 9, which is when microsecond precision was introduced.
        // Versions of JREs < 9 started with s"1.${majorVersion}", then with JEP 223 they switched to semantic versioning.
        @@ TestAspect.ifProp("java.version", not(startsWithString("1."))),
      testM("currentTime has correct time") {
        val unit = TimeUnit.MICROSECONDS
        for {
          start  <- ZIO.effectTotal(Instant.now).map(_.toEpochMilli)
          time   <- clock.currentTime(unit).map(TimeUnit.MILLISECONDS.convert(_, unit))
          finish <- ZIO.effectTotal(Instant.now).map(_.toEpochMilli)
        } yield assert(time)(isGreaterThanEqualTo(start) && isLessThanEqualTo(finish))
      }.provideLayer(Clock.live)
        @@ TestAspect.nonFlaky
    )
}
