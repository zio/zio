package scalaz.zio.testkit

import java.util.concurrent.TimeUnit

import scalaz.zio._
import scalaz.zio.clock.Clock
import scalaz.zio.testkit.TestClock.Data

case class TestClock(ref: Ref[TestClock.Data]) extends Clock.Interface[Any] {

  final def currentTime(unit: TimeUnit): IO[Nothing, Long] =
    ref.get.map(data => unit.convert(data.currentTimeMillis, TimeUnit.MILLISECONDS))

  final val nanoTime: IO[Nothing, Long] =
    ref.get.map(_.nanoTime)

  final def sleep(length: Long, unit: TimeUnit): UIO[Unit] =
    adjust(length, unit) *> ref.update(data => data.copy(sleeps0 = (length, unit) :: data.sleeps0)).void

  val sleeps: UIO[List[(Long, TimeUnit)]] = ref.get.map(_.sleeps0.reverse)

  final def adjust(length: Long, unit: TimeUnit): UIO[Unit] =
    ref.update { data =>
      val nanos  = TimeUnit.NANOSECONDS.convert(length, unit)
      val millis = TimeUnit.MILLISECONDS.convert(length, unit)

      Data(data.nanoTime + nanos, data.currentTimeMillis + millis, data.sleeps0)
    }.void

}

object TestClock {
  val Zero = Data(0, 0, Nil)

  case class Data(nanoTime: Long, currentTimeMillis: Long, sleeps0: List[(Long, TimeUnit)])
}
