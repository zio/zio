package zio

import zio.ZIOMetric.{Counter, MetricAspect}
import zio.metrics.{MetricClient, MetricKey, MetricType}
import zio.test._

object ZIOMetricSpec extends ZIOBaseSpec {

  private val labels1 = Chunk(MetricLabel("x", "a"), MetricLabel("y", "b"))

  def spec = suite("ZIOMetric")(
    suite("Counter")(
      test("custom increment as aspect") {
        val c = new Counter[Any](
          "c1",
          labels1,
          metric =>
            new MetricAspect[Any] {
              override def apply[R, E, A1 <: Any](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio.tap(_ => metric.increment)
            }
        )
        for {
          _      <- ZIO.unit @@ c
          _      <- ZIO.unit @@ c
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Counter("c1", labels1)).map(_.details)
        } yield assertTrue(r == Some(MetricType.Counter(2.0)))
      },
      test("direct increment") {
        val c = new Counter[Any](
          "c2",
          labels1,
          _ =>
            new MetricAspect[Any] {
              override def apply[R, E, A1 <: Any](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio
            }
        )
        for {
          _      <- c.increment
          _      <- c.increment
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Counter("c2", labels1)).map(_.details)
        } yield assertTrue(r == Some(MetricType.Counter(2.0)))
      },
      test("custom increment by value as aspect") {
        val c = new Counter[Double](
          "c3",
          labels1,
          metric =>
            new MetricAspect[Double] {
              override def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio.tap(metric.increment)
            }
        )
        for {
          _      <- ZIO.succeed(10.0) @@ c
          _      <- ZIO.succeed(5.0) @@ c
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Counter("c3", labels1)).map(_.details)
        } yield assertTrue(r == Some(MetricType.Counter(15.0)))
      },
      test("direct increment by value") {
        val c = new Counter[Any](
          "c4",
          labels1,
          _ =>
            new MetricAspect[Any] {
              override def apply[R, E, A1 <: Any](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio
            }
        )
        for {
          _      <- c.increment(10.0)
          _      <- c.increment(5.0)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Counter("c4", labels1)).map(_.details)
        } yield assertTrue(r == Some(MetricType.Counter(15.0)))
      },
      test("count") {
        for {
          _      <- ZIO.unit @@ ZIOMetric.count("c5", labels1: _*)
          _      <- ZIO.unit @@ ZIOMetric.count("c5", labels1: _*)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Counter("c5", labels1)).map(_.details)
          v      <- ZIOMetric.count("c5", labels1: _*).count
        } yield assertTrue(
          r == Some(MetricType.Counter(2.0)),
          v == 2.0
        )
      },
      test("countValue") {
        for {
          _      <- ZIO.succeed(10.0) @@ ZIOMetric.countValue("c6", labels1: _*)
          _      <- ZIO.succeed(5.0) @@ ZIOMetric.countValue("c6", labels1: _*)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Counter("c6", labels1)).map(_.details)
          v      <- ZIOMetric.count("c6", labels1: _*).count
        } yield assertTrue(
          r == Some(MetricType.Counter(15.0)),
          v == 15.0
        )
      },
      test("countValueWith") {
        val c = ZIOMetric.countValueWith[String]("c7", labels1: _*)(_.length.toDouble)
        for {
          _      <- ZIO.succeed("hello") @@ c
          _      <- ZIO.succeed("!") @@ c
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Counter(c.name, labels1)).map(_.details)
          v      <- c.count
        } yield assertTrue(
          r == Some(MetricType.Counter(6.0)),
          v == 6.0
        )
      },
      test("countErrors") {
        val c = ZIOMetric.countErrors("c8")
        for {
          _      <- (ZIO.unit @@ c *> ZIO.fail("error") @@ c).ignore
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Counter(c.name, Chunk.empty)).map(_.details)
          v      <- c.count
        } yield assertTrue(
          r == Some(MetricType.Counter(1.0)),
          v == 1.0
        )
      },
      test("countValueWith + copy") {
        val c = ZIOMetric
          .countValueWith[String]("c9", labels1: _*)(_.length.toDouble)
          .copy("c9c", Chunk.empty)
        for {
          _      <- ZIO.succeed("hello") @@ c
          _      <- ZIO.succeed("!") @@ c
          states <- UIO(MetricClient.unsafeStates)
          r0      = states.get(MetricKey.Counter("c9", labels1)).map(_.details)
          r       = states.get(MetricKey.Counter("c9c", Chunk.empty)).map(_.details)
          v      <- c.count
        } yield assertTrue(
          r0 == Some(MetricType.Counter(0.0)),
          r == Some(MetricType.Counter(6.0)),
          v == 6.0,
          c.name == "c9c"
        )
      },
      test("count + taggedWith") {
        val c = ZIOMetric
          .count("c10", MetricLabel("static", "0"))
          .taggedWith {
            case s: String => Chunk(MetricLabel("dyn", s))
            case _         => Chunk.empty
          }
        for {
          _      <- ZIO.succeed("hello") @@ c
          _      <- ZIO.succeed("!") @@ c
          _      <- ZIO.succeed("!") @@ c
          states <- UIO(MetricClient.unsafeStates)
          r = states
                .get(MetricKey.Counter("c10", Chunk(MetricLabel("static", "0"), MetricLabel("dyn", "!"))))
                .map(_.details)
        } yield assertTrue(
          r == Some(MetricType.Counter(2.0))
        )
      },
      test("count + taggedWith referential transparency") {
        val c1 = ZIOMetric
          .count("c11", MetricLabel("static", "0"))
        val c2 =
          c1.taggedWith {
            case s: String => Chunk(MetricLabel("dyn", s))
            case _         => Chunk.empty
          }
        for {
          _      <- ZIO.succeed("hello") @@ c1
          _      <- ZIO.succeed("!") @@ c1
          _      <- ZIO.succeed("!") @@ c2
          states <- UIO(MetricClient.unsafeStates)
          r1 = states
                 .get(MetricKey.Counter("c11", Chunk(MetricLabel("static", "0"))))
                 .map(_.details)
          r2 = states
                 .get(MetricKey.Counter("c11", Chunk(MetricLabel("static", "0"), MetricLabel("dyn", "!"))))
                 .map(_.details)
        } yield assertTrue(
          r1 == Some(MetricType.Counter(2.0)),
          r2 == Some(MetricType.Counter(1.0))
        )
      }
    )
  )
}
