package zio.internal.metrics

import zio._
import zio.test._

object ConcurrentSummarySpec extends ZIOBaseSpec {
  override def spec: ZSpec[Environment, Any] =
    suite("ConcurrentSummary")(
      test("single observe works with maxSize = 0") {
        val summary = ConcurrentSummary.manual(maxSize = 0, maxAge = 10.seconds, err = 0.0, quantiles = Chunk.empty)
        val observe = Clock.instant.flatMap(now => ZIO.attempt(summary.observe(11.0, now)))

        for {
          _        <- observe
          now      <- Clock.instant
          snapshot <- ZIO.attempt(summary.snapshot(now))
          count    <- ZIO.attempt(summary.getCount())
          sum      <- ZIO.attempt(summary.getSum())
        } yield assertTrue(
          snapshot.isEmpty,
          count == 1L,
          sum == 11.0
        )
      },
      test("single observe works with arbitrary maxSize") {
        check(Gen.int(0, 100000)) { maxSize =>
          val summary = ConcurrentSummary.manual(maxSize, maxAge = 10.seconds, err = 0.0, quantiles = Chunk.empty)
          val observe = Clock.instant.flatMap(now => ZIO.attempt(summary.observe(11.0, now)))

          for {
            _        <- observe
            now      <- Clock.instant
            snapshot <- ZIO.attempt(summary.snapshot(now))
            count    <- ZIO.attempt(summary.getCount())
            sum      <- ZIO.attempt(summary.getSum())
          } yield assertTrue(
            snapshot.length <= 1,
            count == 1L,
            sum == 11.0
          )
        }
      },
      zio.test.suite("stable under load")(
        Seq(0, 1, 100, 100000).map { maxSize =>
          test(s"maxSize = $maxSize") {
            val summary     = ConcurrentSummary.manual(maxSize, maxAge = 10.seconds, err = 0.0, quantiles = Chunk.empty)
            val observe     = Clock.instant.flatMap(now => ZIO.attempt(summary.observe(11.0, now)))
            val getSnapshot = Clock.instant.flatMap(now => ZIO.attempt(summary.snapshot(now)))

            val test =
              for {
                f1       <- observe.repeat(Schedule.upTo(2.seconds) *> Schedule.count).forkDaemon
                f2       <- observe.repeat(Schedule.upTo(2.seconds) *> Schedule.count).forkDaemon
                _        <- getSnapshot.repeat(Schedule.upTo(2.seconds))
                snapshot <- getSnapshot
                f1Count  <- f1.join
                f2Count  <- f2.join
                count    <- ZIO.attempt(summary.getCount())
                sum      <- ZIO.attempt(summary.getSum())
              } yield assertTrue(
                snapshot.length <= maxSize,
                count == (f1Count + f2Count + 2),
                sum == (f1Count + f2Count + 2) * 11.0
              )

            test.manuallyProvide(Clock.live)
          }
        }: _*
      ),
      test(s"old measurements not used for quantiles with non-full buffer") {
        val summary =
          ConcurrentSummary.manual(maxSize = 10, maxAge = 1.seconds, err = 0.0, quantiles = Chunk(0.5, 1.0))
        def observe(v: Double) = Clock.instant.flatMap(now => ZIO.attempt(summary.observe(v, now)))
        val getSnapshot        = Clock.instant.flatMap(now => ZIO.attempt(summary.snapshot(now)))

        for {
          _ <- observe(1.0) // old
          _ <- TestClock.adjust(300.millis)
          _ <- observe(2.0) // old
          _        <- TestClock.adjust(300.millis)
          _        <- observe(3.0)
          _        <- TestClock.adjust(300.millis)
          _        <- observe(4.0)
          _        <- TestClock.adjust(300.millis)
          _        <- observe(5.0)
          _        <- TestClock.adjust(300.millis)
          snapshot <- getSnapshot
          count    <- ZIO.attempt(summary.getCount())
          sum      <- ZIO.attempt(summary.getSum())
          s0        = (0.5, Some(3.0))
          s1        = (1.0, Some(5.0))
        } yield assertTrue(
          snapshot.length == 2,
          snapshot(0) == s0,
          snapshot(1) == s1,
          count == 5L,
          sum == 1.0 + 2.0 + 3.0 + 4.0 + 5.0
        )
      },
      test(s"old measurements not used for quantiles with full buffer") {
        val summary =
          ConcurrentSummary.manual(maxSize = 3, maxAge = 1.seconds, err = 0.0, quantiles = Chunk(0.5, 1.0))
        def observe(v: Double) = Clock.instant.flatMap(now => ZIO.attempt(summary.observe(v, now)))
        val getSnapshot        = Clock.instant.flatMap(now => ZIO.attempt(summary.snapshot(now)))

        for {
          _ <- observe(1.0) // old
          _ <- TestClock.adjust(300.millis)
          _ <- observe(2.0) // old
          _        <- TestClock.adjust(300.millis)
          _        <- observe(3.0)
          _        <- TestClock.adjust(300.millis)
          _        <- observe(4.0)
          _        <- TestClock.adjust(300.millis)
          _        <- observe(5.0)
          _        <- TestClock.adjust(300.millis)
          snapshot <- getSnapshot
          count    <- ZIO.attempt(summary.getCount())
          sum      <- ZIO.attempt(summary.getSum())
          s0        = (0.5, Some(3.0))
          s1        = (1.0, Some(5.0))
        } yield assertTrue(
          snapshot.length == 2,
          snapshot(0) == s0,
          snapshot(1) == s1,
          count == 5L,
          sum == 1.0 + 2.0 + 3.0 + 4.0 + 5.0
        )
      }
    )
}
