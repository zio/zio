package scalaz.zio.delay

import scalaz.zio.TestRuntime
import scalaz.zio.duration.Duration

class DelaySpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "DelaySpec".title ^ s2"""
      Make a relative Delay from a given duration and check that:
        it is the same as duration ${sameAsDuration(_.relative)}
        applying a factor to it is the same as applying factor to duration ${applyingFactor(_.relative)}

      Make 2 relative Delays from given durations and check that:
        summing them is the same as summing durations ${sumDelays((d1, d2) => (d1.relative, d2.relative))}
        min(1, 2) is 1 ${min((d1, d2) => (d1.relative, d2.relative))}
        min(2, 1) is 1 ${min((d1, d2) => (d2.relative, d1.relative))}
    """

  def sameAsDuration(c: Duration => Delay) =
    unsafeRun(c(Duration.fromNanos(100)).run) must_=== Duration.fromNanos(100)

  def applyingFactor(c: Duration => Delay) =
    unsafeRun((c(Duration.fromNanos(100)) * 5).run) must_=== Duration.fromNanos(500)

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

}
