package scalaz.zio

import java.util.concurrent.TimeUnit

case class TestClock(ref: Ref[TestClock.Data]) extends Clock {
  final def currentTime(unit: TimeUnit): IO[Nothing, Long] =
    ref.get.map(data => unit.convert(data.currentTimeMillis, TimeUnit.MILLISECONDS))

  final val nanoTime: IO[Nothing, Long] =
    ref.get.map(_.nanoTime)

  final def sleep(length: Long, unit: TimeUnit): IO[Nothing, Unit] =
    ref.update(_.adjust(length, unit).sleep(length, unit)).void
}
object TestClock {
  val Zero = Data(0, 0, Nil)

  case class Data(nanoTime: Long, currentTimeMillis: Long, sleeps0: List[(Long, TimeUnit)]) {
    lazy val sleeps = sleeps0.reverse

    final def adjust(length: Long, unit: TimeUnit): Data = {
      val nanos  = TimeUnit.NANOSECONDS.convert(length, unit)
      val millis = TimeUnit.MILLISECONDS.convert(length, unit)

      Data(nanoTime + nanos, currentTimeMillis + millis, sleeps0)
    }

    final def sleep(length: Long, unit: TimeUnit): Data =
      copy(sleeps0 = ((length, unit)) :: sleeps0)
  }
}

class ClockSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec {

  def is = "ClockSpec".title ^ s2"""
      Sleep does not sleep forever                      $e1
      Nano time is monotonically increasing             $e2
      Current time millis is monotonically increasing   $e3
     """

  val Live = Clock.Live

  def e1 =
    unsafeRun(
      for {
        _ <- Live.sleep(1, TimeUnit.MILLISECONDS)
      } yield true must beTrue
    )

  def e2 =
    unsafeRun(
      for {
        time1 <- Live.nanoTime
        _     <- Live.sleep(1, TimeUnit.MILLISECONDS)
        time2 <- Live.nanoTime
      } yield (time1 < time2) must beTrue
    )

  def e3 =
    unsafeRun(
      for {
        time1 <- Live.currentTime(TimeUnit.MILLISECONDS)
        _     <- Live.sleep(1, TimeUnit.MILLISECONDS)
        time2 <- Live.currentTime(TimeUnit.MILLISECONDS)
      } yield (time1 < time2) must beTrue
    )

  def e4 =
    unsafeRun(
      for {
        ref    <- Ref(TestClock.Zero)
        clock  <- IO.succeed(TestClock(ref))
        time1  <- clock.currentTime(TimeUnit.MILLISECONDS)
        _      <- clock.sleep(1, TimeUnit.MILLISECONDS)
        time2  <- clock.currentTime(TimeUnit.MILLISECONDS)
        sleeps <- clock.ref.get.map(_.sleeps)
      } yield
        (sleeps must_=== List((1, TimeUnit.MILLISECONDS))) and
          ((time2 - time1) must_=== 1)
    )
}
