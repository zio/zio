package scalaz.zio.delay

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit

import scalaz.zio.TestRuntime
import scalaz.zio.duration.Duration
import scalaz.zio.clock._

class DelaySpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is =
    "DelaySpec".title ^ s2"""
      make a relative Delay from a given duration and check that:
        it is the same as duration ${sameAsDuration(_.relative)}
        applying a factor to it is the same as applying factor to duration ${applyingFactor(_.relative)}

      make an absolute Delay from a given duration and check that:
        if it's on the past, result is 0 $atThePast

      make 2 relative Delays from given durations and check that:
        summing them is the same as summing durations ${sumDelays((d1, d2) => (d1.relative, d2.relative))}
        min(1, 2) is 1 ${min((d1, d2) => (d1.relative, d2.relative))}
        min(2, 1) is 1 ${min((d1, d2) => (d2.relative, d1.relative))}
        max(1, 2) is 2 ${max((d1, d2) => (d1.relative, d2.relative))}
        max(2, 1) is 2 ${max((d1, d2) => (d2.relative, d1.relative))}
    """

  def sameAsDuration(c: Duration => Delay) =
    unsafeRun(
      for {
        tm <- nanoTime
        d  <- c(Duration.fromNanos(tm + 10000)).run
      } yield d must_=== Duration.fromNanos(tm + 10000)
    )

  def applyingFactor(c: Duration => Delay) =
    unsafeRun(
      for {
        d <- (c(Duration(200, TimeUnit.MILLISECONDS)) * 2).run
      } yield d must_=== Duration(200, TimeUnit.MILLISECONDS) * 2
    )

  def sumDelays(c: (Duration, Duration) => (Delay, Delay)) = {
    val (delay1, delay2) = c(Duration.fromNanos(100), Duration.fromNanos(200))
    val delaySum         = unsafeRun((delay1 + delay2).run)
    val durationSum      = Duration.fromNanos(100) + Duration.fromNanos(200)

    delaySum must_=== durationSum
  }

  def min(c: (Duration, Duration) => (Delay, Delay)) = {
    val (delay1, delay2) = c(Duration.fromNanos(1), Duration.fromNanos(2))
    unsafeRun((delay1 min delay2).run) must_=== Duration.fromNanos(1)
  }

  def max(c: (Duration, Duration) => (Delay, Delay)) = {
    val (delay1, delay2) = c(Duration.fromNanos(1), Duration.fromNanos(2))
    unsafeRun((delay1 max delay2).run) must_=== Duration.fromNanos(2)
  }

  def atThePast =
    unsafeRun(Delay.absolute(Duration.fromNanos(100)).run) must_=== Duration.fromNanos(0)

}
