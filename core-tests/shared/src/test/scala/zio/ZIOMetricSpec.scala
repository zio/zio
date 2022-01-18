package zio

import zio.ZIOMetric._
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
          _      <- ZIO.succeed("!") @@ c2
          _      <- ZIO.succeed("hello") @@ c1
          _      <- ZIO.succeed("!") @@ c1
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
    ),
    suite("Gauge")(
      test("custom set as aspect") {
        val g = new Gauge[Double](
          "g1",
          labels1,
          metric =>
            new MetricAspect[Double] {
              override def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio.tap(metric.set)
            }
        )
        for {
          _      <- ZIO.succeed(1.0) @@ g
          _      <- ZIO.succeed(3.0) @@ g
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Gauge("g1", labels1)).map(_.details)
        } yield assertTrue(r == Some(MetricType.Gauge(3.0)))
      },
      test("direct increment") {
        val g = new Gauge[Any](
          "g2",
          labels1,
          _ =>
            new MetricAspect[Any] {
              override def apply[R, E, A1 <: Any](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio
            }
        )
        for {
          _      <- g.set(1.0)
          _      <- g.set(3.0)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Gauge("g2", labels1)).map(_.details)
        } yield assertTrue(r == Some(MetricType.Gauge(3.0)))
      },
      test("custom adjust as aspect") {
        val g = new Gauge[Double](
          "g3",
          labels1,
          metric =>
            new MetricAspect[Double] {
              override def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio.tap(metric.adjust)
            }
        )
        for {
          _      <- ZIO.succeed(10.0) @@ g
          _      <- ZIO.succeed(5.0) @@ g
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Gauge("g3", labels1)).map(_.details)
        } yield assertTrue(r == Some(MetricType.Gauge(15.0)))
      },
      test("direct adjust") {
        val g = new Gauge[Any](
          "g4",
          labels1,
          _ =>
            new MetricAspect[Any] {
              override def apply[R, E, A1 <: Any](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio
            }
        )
        for {
          _      <- g.adjust(10.0)
          _      <- g.adjust(5.0)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Gauge("g4", labels1)).map(_.details)
        } yield assertTrue(r == Some(MetricType.Gauge(15.0)))
      },
      test("setGauge") {
        for {
          _      <- ZIO.succeed(1.0) @@ ZIOMetric.setGauge("g5", labels1: _*)
          _      <- ZIO.succeed(3.0) @@ ZIOMetric.setGauge("g5", labels1: _*)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Gauge("g5", labels1)).map(_.details)
          v      <- ZIOMetric.setGauge("g5", labels1: _*).value
        } yield assertTrue(
          r == Some(MetricType.Gauge(3.0)),
          v == 3.0
        )
      },
      test("adjustGauge") {
        for {
          _      <- ZIO.succeed(10.0) @@ ZIOMetric.adjustGauge("g6", labels1: _*)
          _      <- ZIO.succeed(5.0) @@ ZIOMetric.adjustGauge("g6", labels1: _*)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Gauge("g6", labels1)).map(_.details)
          v      <- ZIOMetric.adjustGauge("g6", labels1: _*).value
        } yield assertTrue(
          r == Some(MetricType.Gauge(15.0)),
          v == 15.0
        )
      },
      test("setGaugeWith") {
        val g = ZIOMetric.setGaugeWith("g7", labels1: _*)((n: Int) => n.toDouble)
        for {
          _      <- ZIO.succeed(1) @@ g
          _      <- ZIO.succeed(3) @@ g
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Gauge("g7", labels1)).map(_.details)
          v      <- g.value
        } yield assertTrue(
          r == Some(MetricType.Gauge(3.0)),
          v == 3.0
        )
      },
      test("adjustGaugeWith") {
        val g = ZIOMetric.adjustGaugeWith("g8", labels1: _*)((n: Int) => n.toDouble)
        for {
          _      <- ZIO.succeed(10) @@ g
          _      <- ZIO.succeed(5) @@ g
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Gauge("g8", labels1)).map(_.details)
          v      <- g.value
        } yield assertTrue(
          r == Some(MetricType.Gauge(15.0)),
          v == 15.0
        )
      },
      test("adjustGaugeWith + copy") {
        val g = ZIOMetric
          .adjustGaugeWith[String]("g9", labels1: _*)(_.length.toDouble)
          .copy("g9c", Chunk.empty)
        for {
          _      <- ZIO.succeed("hello") @@ g
          _      <- ZIO.succeed("!") @@ g
          states <- UIO(MetricClient.unsafeStates)
          r0      = states.get(MetricKey.Gauge("g9", labels1)).map(_.details)
          r       = states.get(MetricKey.Gauge("g9c", Chunk.empty)).map(_.details)
          v      <- g.value
        } yield assertTrue(
          r0 == Some(MetricType.Gauge(0.0)),
          r == Some(MetricType.Gauge(6.0)),
          v == 6.0,
          g.name == "g9c"
        )
      },
      test("adjustGaugeWith + taggedWith") {
        val g = ZIOMetric
          .adjustGaugeWith[String]("g10", MetricLabel("static", "0"))(_.length.toDouble)
          .taggedWith(s => Chunk(MetricLabel("dyn", s)))
        for {
          _      <- ZIO.succeed("hello") @@ g
          _      <- ZIO.succeed("!") @@ g
          _      <- ZIO.succeed("!") @@ g
          states <- UIO(MetricClient.unsafeStates)
          r = states
                .get(MetricKey.Gauge("g10", Chunk(MetricLabel("static", "0"), MetricLabel("dyn", "!"))))
                .map(_.details)
        } yield assertTrue(
          r == Some(MetricType.Gauge(2.0))
        )
      },
      test("adjustGaugeWith + taggedWith referential transparency") {
        val g1 = ZIOMetric
          .adjustGaugeWith[String]("g11", MetricLabel("static", "0"))(_.length.toDouble)
        val g2 =
          g1.taggedWith(s => Chunk(MetricLabel("dyn", s)))
        for {
          _      <- ZIO.succeed("!") @@ g2
          _      <- ZIO.succeed("hello") @@ g1
          _      <- ZIO.succeed("!") @@ g1
          states <- UIO(MetricClient.unsafeStates)
          r1 = states
                 .get(MetricKey.Gauge("g11", Chunk(MetricLabel("static", "0"))))
                 .map(_.details)
          r2 = states
                 .get(MetricKey.Gauge("g11", Chunk(MetricLabel("static", "0"), MetricLabel("dyn", "!"))))
                 .map(_.details)
        } yield assertTrue(
          r1 == Some(MetricType.Gauge(6.0)),
          r2 == Some(MetricType.Gauge(1.0))
        )
      }
    ),
    suite("Histogram")(
      test("custom observe as aspect") {
        val h = new Histogram[Double](
          "h1",
          Histogram.Boundaries.linear(0, 1.0, 10),
          labels1,
          metric =>
            new MetricAspect[Double] {
              override def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio.tap(metric.observe)
            }
        )
        for {
          _      <- ZIO.succeed(1.0) @@ h
          _      <- ZIO.succeed(3.0) @@ h
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Histogram(h.name, h.boundaries, h.tags)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.DoubleHistogram].count == 2L,
          r.get.asInstanceOf[MetricType.DoubleHistogram].sum == 4.0
        )
      },
      test("direct observe") {
        val h = new Histogram[Double](
          "h2",
          Histogram.Boundaries.linear(0, 1.0, 10),
          labels1,
          _ =>
            new MetricAspect[Double] {
              override def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio
            }
        )
        for {
          _      <- h.observe(1.0)
          _      <- h.observe(3.0)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Histogram(h.name, h.boundaries, h.tags)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.DoubleHistogram].count == 2L,
          r.get.asInstanceOf[MetricType.DoubleHistogram].sum == 4.0
        )
      },
      test("observeDurations") {
        val h =
          observeDurations[Any]("h3", Histogram.Boundaries.linear(0, 1.0, 10), labels1: _*)(
            _.toMillis.toDouble / 1000.0
          )
        for {
          // NOTE: observeDurations always uses real clock
          _      <- (Clock.sleep(1.second) @@ h).provide(Clock.live)
          _      <- (Clock.sleep(3.seconds) @@ h).provide(Clock.live)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Histogram(h.name, h.boundaries, h.tags)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.DoubleHistogram].count == 2L,
          r.get.asInstanceOf[MetricType.DoubleHistogram].sum > 3.5,
          r.get.asInstanceOf[MetricType.DoubleHistogram].sum < 4.5
        )
      },
      test("observeHistogram") {
        val h = observeHistogram("h4", Histogram.Boundaries.linear(0, 1.0, 10), labels1: _*)
        for {
          _      <- ZIO.succeed(1.0) @@ h
          _      <- ZIO.succeed(3.0) @@ h
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Histogram(h.name, h.boundaries, h.tags)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.DoubleHistogram].count == 2L,
          r.get.asInstanceOf[MetricType.DoubleHistogram].sum == 4.0
        )
      },
      test("observeHistogramWith") {
        val h =
          observeHistogramWith[String]("h5", Histogram.Boundaries.linear(0, 1.0, 10), labels1: _*)(_.length.toDouble)
        for {
          _      <- ZIO.succeed("x") @@ h
          _      <- ZIO.succeed("xyz") @@ h
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Histogram(h.name, h.boundaries, h.tags)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.DoubleHistogram].count == 2L,
          r.get.asInstanceOf[MetricType.DoubleHistogram].sum == 4.0
        )
      },
      test("observeHistogram + copy") {
        val h = observeHistogram("h6", Histogram.Boundaries.linear(0, 1.0, 10), labels1: _*)
          .copy(name = "h6c", tags = Chunk.empty)
        for {
          _      <- ZIO.succeed(1.0) @@ h
          _      <- ZIO.succeed(3.0) @@ h
          states <- UIO(MetricClient.unsafeStates)
          r0      = states.get(MetricKey.Histogram("h6", h.boundaries, labels1)).map(_.details)
          r       = states.get(MetricKey.Histogram("h6c", h.boundaries, Chunk.empty)).map(_.details)
        } yield assertTrue(
          r0.get.asInstanceOf[MetricType.DoubleHistogram].count == 0L,
          r.get.asInstanceOf[MetricType.DoubleHistogram].count == 2L,
          r.get.asInstanceOf[MetricType.DoubleHistogram].sum == 4.0
        )
      },
      test("observeHistogramWith + taggedWith") {
        val boundaries = Histogram.Boundaries.linear(0, 1.0, 10)
        val h =
          observeHistogramWith[String]("h7", boundaries, labels1: _*)(_.length.toDouble)
            .taggedWith(s => Chunk(MetricLabel("dyn", s)))
        for {
          _      <- ZIO.succeed("x") @@ h
          _      <- ZIO.succeed("xyz") @@ h
          states <- UIO(MetricClient.unsafeStates)
          r0      = states.get(MetricKey.Histogram("h7", boundaries, labels1)).map(_.details)
          r1      = states.get(MetricKey.Histogram("h7", boundaries, labels1 :+ MetricLabel("dyn", "x"))).map(_.details)
          r2      = states.get(MetricKey.Histogram("h7", boundaries, labels1 :+ MetricLabel("dyn", "xyz"))).map(_.details)
        } yield assertTrue(
          r0.get.asInstanceOf[MetricType.DoubleHistogram].count == 0L,
          r1.get.asInstanceOf[MetricType.DoubleHistogram].count == 1L,
          r2.get.asInstanceOf[MetricType.DoubleHistogram].count == 1L
        )
      },
      test("observeHistogramWith + taggedWith referential transparency") {
        val boundaries = Histogram.Boundaries.linear(0, 1.0, 10)
        val h1         = observeHistogramWith[String]("h8", boundaries, labels1: _*)(_.length.toDouble)
        val h2         = h1.taggedWith(s => Chunk(MetricLabel("dyn", s)))
        for {
          _      <- ZIO.succeed("x") @@ h2
          _      <- ZIO.succeed("xyz") @@ h1
          states <- UIO(MetricClient.unsafeStates)
          r0      = states.get(MetricKey.Histogram("h8", boundaries, labels1)).map(_.details)
          r1      = states.get(MetricKey.Histogram("h8", boundaries, labels1 :+ MetricLabel("dyn", "x"))).map(_.details)
          r2      = states.get(MetricKey.Histogram("h8", boundaries, labels1 :+ MetricLabel("dyn", "xyz"))).map(_.details)
        } yield assertTrue(
          r0.get.asInstanceOf[MetricType.DoubleHistogram].count == 1L,
          r1.get.asInstanceOf[MetricType.DoubleHistogram].count == 1L,
          r2.isEmpty
        )
      }
    ),
    suite("Summary")(
      test("custom observe as aspect") {
        val s = new ZIOMetric.Summary[Double](
          "s1",
          1.minute,
          10,
          0.0,
          Chunk(0.0, 1.0, 10.0),
          labels1,
          metric =>
            new MetricAspect[Double] {
              override def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio.tap(metric.observe)
            }
        )
        for {
          _      <- ZIO.succeed(1.0) @@ s
          _      <- ZIO.succeed(3.0) @@ s
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Summary(s.name, s.maxAge, s.maxSize, s.error, s.quantiles, s.tags)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.Summary].count == 2L,
          r.get.asInstanceOf[MetricType.Summary].sum == 4.0
        )
      },
      test("direct observe") {
        val s = new ZIOMetric.Summary[Double](
          "s2",
          1.minute,
          10,
          0.0,
          Chunk(0.0, 1.0, 10.0),
          labels1,
          _ =>
            new MetricAspect[Double] {
              override def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio
            }
        )
        for {
          _      <- s.observe(1.0)
          _      <- s.observe(3.0)
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Summary(s.name, s.maxAge, s.maxSize, s.error, s.quantiles, s.tags)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.Summary].count == 2L,
          r.get.asInstanceOf[MetricType.Summary].sum == 4.0
        )
      },
      test("observeSummary") {
        val s = observeSummary(
          "s3",
          1.minute,
          10,
          0.0,
          Chunk(0.0, 1.0, 10.0),
          labels1: _*
        )
        for {
          _      <- ZIO.succeed(1.0) @@ s
          _      <- ZIO.succeed(3.0) @@ s
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Summary(s.name, s.maxAge, s.maxSize, s.error, s.quantiles, s.tags)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.Summary].count == 2L,
          r.get.asInstanceOf[MetricType.Summary].sum == 4.0
        )
      },
      test("observeSummaryWith") {
        val s = observeSummaryWith[String](
          "s4",
          1.minute,
          10,
          0.0,
          Chunk(0.0, 1.0, 10.0),
          labels1: _*
        )(_.length.toDouble)
        for {
          _      <- ZIO.succeed("x") @@ s
          _      <- ZIO.succeed("xyz") @@ s
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.Summary(s.name, s.maxAge, s.maxSize, s.error, s.quantiles, s.tags)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.Summary].count == 2L,
          r.get.asInstanceOf[MetricType.Summary].sum == 4.0
        )
      },
      test("observeSummaryWith + copy") {
        val s = observeSummaryWith[String](
          "s5",
          1.minute,
          10,
          0.0,
          Chunk(0.0, 1.0, 10.0),
          labels1: _*
        )(_.length.toDouble).copy("s5c", tags = Chunk.empty)
        for {
          _      <- ZIO.succeed("x") @@ s
          _      <- ZIO.succeed("xyz") @@ s
          states <- UIO(MetricClient.unsafeStates)
          r0      = states.get(MetricKey.Summary("s5", s.maxAge, s.maxSize, s.error, s.quantiles, labels1)).map(_.details)
          r1 =
            states.get(MetricKey.Summary("s5c", s.maxAge, s.maxSize, s.error, s.quantiles, Chunk.empty)).map(_.details)
        } yield assertTrue(
          r0.get.asInstanceOf[MetricType.Summary].count == 0L,
          r1.get.asInstanceOf[MetricType.Summary].count == 2L,
          r1.get.asInstanceOf[MetricType.Summary].sum == 4.0
        )
      },
      test("observeSummaryWith + taggedWith") {
        val s0 = observeSummaryWith[String](
          "s6",
          1.minute,
          10,
          0.0,
          Chunk(0.0, 1.0, 10.0),
          labels1: _*
        )(_.length.toDouble)
        val s = s0.taggedWith(s => Chunk(MetricLabel("dyn", s)))
        for {
          _      <- ZIO.succeed("x") @@ s
          _      <- ZIO.succeed("xyz") @@ s
          states <- UIO(MetricClient.unsafeStates)
          r0 = states
                 .get(MetricKey.Summary(s0.name, s0.maxAge, s0.maxSize, s0.error, s0.quantiles, labels1))
                 .map(_.details)
          r1 =
            states
              .get(
                MetricKey
                  .Summary(s0.name, s0.maxAge, s0.maxSize, s0.error, s0.quantiles, labels1 :+ MetricLabel("dyn", "x"))
              )
              .map(_.details)
          r2 =
            states
              .get(
                MetricKey
                  .Summary(s0.name, s0.maxAge, s0.maxSize, s0.error, s0.quantiles, labels1 :+ MetricLabel("dyn", "xyz"))
              )
              .map(_.details)
        } yield assertTrue(
          r0.get.asInstanceOf[MetricType.Summary].count == 0L,
          r1.get.asInstanceOf[MetricType.Summary].count == 1L,
          r2.get.asInstanceOf[MetricType.Summary].count == 1L
        )
      },
      test("observeSummaryWith + taggedWith referential transparency") {
        val s0 = observeSummaryWith[String](
          "s7",
          1.minute,
          10,
          0.0,
          Chunk(0.0, 1.0, 10.0),
          labels1: _*
        )(_.length.toDouble)
        val s = s0.taggedWith(s => Chunk(MetricLabel("dyn", s)))
        for {
          _      <- ZIO.succeed("x") @@ s
          _      <- ZIO.succeed("xyz") @@ s0
          _      <- ZIO.succeed("xyz") @@ s
          states <- UIO(MetricClient.unsafeStates)
          r0 = states
                 .get(MetricKey.Summary(s0.name, s0.maxAge, s0.maxSize, s0.error, s0.quantiles, labels1))
                 .map(_.details)
          r1 =
            states
              .get(
                MetricKey
                  .Summary(s0.name, s0.maxAge, s0.maxSize, s0.error, s0.quantiles, labels1 :+ MetricLabel("dyn", "x"))
              )
              .map(_.details)
          r2 =
            states
              .get(
                MetricKey
                  .Summary(s0.name, s0.maxAge, s0.maxSize, s0.error, s0.quantiles, labels1 :+ MetricLabel("dyn", "xyz"))
              )
              .map(_.details)
        } yield assertTrue(
          r0.get.asInstanceOf[MetricType.Summary].count == 1L,
          r1.get.asInstanceOf[MetricType.Summary].count == 1L,
          r2.get.asInstanceOf[MetricType.Summary].count == 1L
        )
      }
    ),
    suite("SetCount")(
      test("custom observe as aspect") {
        val sc = new SetCount[String](
          "sc1",
          "tag",
          labels1,
          metric =>
            new MetricAspect[String] {
              override def apply[R, E, A1 <: String](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio.tap(metric.observe)
            }
        )
        for {
          _      <- ZIO.succeed("hello") @@ sc
          _      <- ZIO.succeed("hello") @@ sc
          _      <- ZIO.succeed("world") @@ sc
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.SetCount("sc1", "tag", labels1)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("hello" -> 2L, "world" -> 1L)
        )
      },
      test("direct observe") {
        val sc = new SetCount[String](
          "sc2",
          "tag",
          labels1,
          _ =>
            new MetricAspect[String] {
              override def apply[R, E, A1 <: Any](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
                zio
            }
        )
        for {
          _      <- sc.observe("hello")
          _      <- sc.observe("hello")
          _      <- sc.observe("world")
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.SetCount("sc2", "tag", labels1)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("hello" -> 2L, "world" -> 1L)
        )
      },
      test("occurrences") {
        val sc = occurrences(
          "sc3",
          "tag",
          labels1: _*
        )
        for {
          _      <- ZIO.succeed("hello") @@ sc
          _      <- ZIO.succeed("hello") @@ sc
          _      <- ZIO.succeed("world") @@ sc
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.SetCount("sc3", "tag", labels1)).map(_.details)
        } yield assertTrue(
          r.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("hello" -> 2L, "world" -> 1L)
        )
      },
      test("occurrencesWith") {
        val sc = occurrencesWith[Int](
          "sc4",
          "tag",
          labels1: _*
        )(_.toString)
        for {
          _      <- ZIO.succeed(1) @@ sc
          _      <- ZIO.succeed(1) @@ sc
          _      <- ZIO.succeed(100) @@ sc
          states <- UIO(MetricClient.unsafeStates)
          r       = states.get(MetricKey.SetCount("sc4", "tag", labels1)).map(_.details)
        } yield assertTrue(r.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("1" -> 2L, "100" -> 1L))
      },
      test("occurrences + copy") {
        val sc = occurrences(
          "sc5",
          "tag",
          labels1: _*
        ).copy("sc5c", "tag2", Chunk.empty)
        for {
          _      <- ZIO.succeed("hello") @@ sc
          _      <- ZIO.succeed("hello") @@ sc
          _      <- ZIO.succeed("world") @@ sc
          states <- UIO(MetricClient.unsafeStates)
          r0      = states.get(MetricKey.SetCount("sc5", "tag", labels1)).map(_.details)
          r1      = states.get(MetricKey.SetCount("sc5c", "tag2", Chunk.empty)).map(_.details)
        } yield assertTrue(
          r0.get.asInstanceOf[MetricType.SetCount].occurrences.toSet.isEmpty,
          r1.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("hello" -> 2L, "world" -> 1L)
        )
      },
      test("occurrences + taggedWith") {
        val sc = occurrences(
          "sc6",
          "tag",
          labels1: _*
        ).taggedWith(s => Chunk(MetricLabel("dyn", s)))
        for {
          _      <- ZIO.succeed("hello") @@ sc
          _      <- ZIO.succeed("hello") @@ sc
          _      <- ZIO.succeed("world") @@ sc
          states <- UIO(MetricClient.unsafeStates)
          r0      = states.get(MetricKey.SetCount("sc6", "tag", labels1)).map(_.details)
          r1      = states.get(MetricKey.SetCount("sc6", "tag", labels1 :+ MetricLabel("dyn", "hello"))).map(_.details)
          r2      = states.get(MetricKey.SetCount("sc6", "tag", labels1 :+ MetricLabel("dyn", "world"))).map(_.details)
        } yield assertTrue(
          r0.get.asInstanceOf[MetricType.SetCount].occurrences.toSet.isEmpty,
          r1.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("hello" -> 2L),
          r2.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("world" -> 1L)
        )
      },
      test("occurrences + taggedWith referential transparency") {
        val sc1 = occurrences(
          "sc7",
          "tag",
          labels1: _*
        )
        val sc2 = sc1.taggedWith(s => Chunk(MetricLabel("dyn", s)))
        for {
          _      <- ZIO.succeed("hello") @@ sc2
          _      <- ZIO.succeed("hello") @@ sc1
          _      <- ZIO.succeed("world") @@ sc2
          states <- UIO(MetricClient.unsafeStates)
          r0      = states.get(MetricKey.SetCount("sc7", "tag", labels1)).map(_.details)
          r1      = states.get(MetricKey.SetCount("sc7", "tag", labels1 :+ MetricLabel("dyn", "hello"))).map(_.details)
          r2      = states.get(MetricKey.SetCount("sc7", "tag", labels1 :+ MetricLabel("dyn", "world"))).map(_.details)
        } yield assertTrue(
          r0.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("hello" -> 1L),
          r1.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("hello" -> 1L),
          r2.get.asInstanceOf[MetricType.SetCount].occurrences.toSet == Set("world" -> 1L)
        )
      }
    )
  )
}
