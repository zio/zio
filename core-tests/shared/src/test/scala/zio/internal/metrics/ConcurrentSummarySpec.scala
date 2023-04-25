package zio.internal.metrics

import zio._
import zio.test._
import zio.test.TestAspect._
import zio.metrics._

object ConcurrentSummarySpec extends ZIOBaseSpec {
  override def spec: Spec[Environment, Any] =
    suite("ConcurrentSummary")(
      test("single observe works with maxSize = 0") {
        val summary = ConcurrentMetricHooks.summary(
          MetricKey.summary(name = "test", maxSize = 0, maxAge = 10.seconds, error = 0.0, quantiles = Chunk.empty)
        )
        val observe = Clock.instant.flatMap(now => ZIO.attempt(summary.update((11.0, now))))

        for {
          _        <- observe
          snapshot <- ZIO.attempt(summary.get())
        } yield assertTrue(
          snapshot.quantiles.isEmpty,
          snapshot.count == 1L,
          snapshot.sum == 11.0
        )
      },
      test("single observe works with arbitrary maxSize") {
        check(Gen.int(0, 100000)) { maxSize =>
          val summary = ConcurrentMetricHooks.summary(
            MetricKey.summary(
              name = "test",
              maxAge = 10.seconds,
              maxSize = maxSize,
              error = 0.0,
              quantiles = Chunk.empty
            )
          )
          val observe = Clock.instant.flatMap(now => ZIO.attempt(summary.update(11.0 -> now)))

          for {
            _        <- observe
            snapshot <- ZIO.attempt(summary.get())
          } yield assertTrue(
            snapshot.quantiles.length <= 1,
            snapshot.count == 1L,
            snapshot.sum == 11.0
          )
        }
      },
      zio.test.suite("stable under load")(
        Seq(0, 1, 100, 100000).map { maxSize =>
          test(s"maxSize = $maxSize") {
            val summary = ConcurrentMetricHooks.summary(
              MetricKey.summary(
                name = "test",
                maxSize = maxSize,
                maxAge = 10.seconds,
                error = 0.0,
                quantiles = Chunk.empty
              )
            )
            val observe     = Clock.instant.flatMap(now => ZIO.attempt(summary.update(11.0 -> now)))
            val getSnapshot = ZIO.attempt(summary.get())

            val test =
              for {
                f1       <- observe.repeat(Schedule.upTo(2.seconds) *> Schedule.count).forkDaemon
                f2       <- observe.repeat(Schedule.upTo(2.seconds) *> Schedule.count).forkDaemon
                _        <- getSnapshot.repeat(Schedule.upTo(2.seconds))
                snapshot <- getSnapshot
                f1Count  <- f1.join
                f2Count  <- f2.join
                count    <- ZIO.attempt(summary.get().count)
                sum      <- ZIO.attempt(summary.get().sum)
              } yield assertTrue(
                snapshot.quantiles.length <= maxSize,
                count == (f1Count + f2Count + 2),
                sum == (f1Count + f2Count + 2) * 11.0
              )

            test
          } @@ withLiveClock
        }: _*
      )
    ) @@ TestAspect.exceptNative
}
