package scalaz.zio.testkit

import java.util.concurrent.TimeUnit

import scalaz.zio._
import scalaz.zio.duration.Duration
import scalaz.zio.clock.Clock
import scalaz.zio.scheduler.Scheduler
import scalaz.zio.testkit.TestClock.Data

case class TestClock(ref: Ref[TestClock.Data]) extends Clock.Interface[Scheduler] {

  final def currentTime(unit: TimeUnit): UIO[Long] =
    ref.get.map(data => unit.convert(data.currentTimeMillis, TimeUnit.MILLISECONDS))

  final val nanoTime: IO[Nothing, Long] =
    ref.get.map(_.nanoTime)

  final def sleep(duration: Duration): UIO[Unit] =
    adjust(duration) *> ref.update(data => data.copy(sleeps0 = duration :: data.sleeps0)).void

  val sleeps: UIO[List[Duration]] = ref.get.map(_.sleeps0.reverse)

  final def adjust(duration: Duration): UIO[Unit] =
    ref.update { data =>
      Data(data.nanoTime + duration.toNanos, data.currentTimeMillis + duration.toMillis, data.sleeps0)
    }.void

}

object TestClock {
  val Zero = Data(0, 0, Nil)

  case class Data(nanoTime: Long, currentTimeMillis: Long, sleeps0: List[Duration])
}
