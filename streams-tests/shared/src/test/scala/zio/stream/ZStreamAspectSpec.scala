package zio.stream

import zio._
import zio.metrics.Metric
import zio.internal.metrics.metricRegistry
import zio.stream.ZStream
import zio.stream.ZStreamAspect
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.ZIOSpecDefault
import zio.test.ZTestLogger

object ZStreamAspectSpec extends ZIOSpecDefault {

  def spec = (suite("ZStreamAspectSpec")(
    suite("annotated")(
      test("preserves elements when no logs") {
        val data = List("a", "b", "c")
        for {
          collected <- (ZStream.fromIterable(data) @@ ZStreamAspect.annotated("k", "v")).runCollect
        } yield assert(collected.toList)(equalTo(data))
      },
      test("single annotation") {
        val aspect = ZStreamAspect.annotated("key", "value")
        val stream = ZStream.fromZIO(ZIO.log("message")) @@ aspect
        for {
          _       <- stream.runDrain
          res     <- ZTestLogger.logOutput
          logAnns <- ZIO.logAnnotations
        } yield assertTrue(
          res.exists(_.annotations.get("key").contains("value")),
          logAnns.get("key").isEmpty
        )
      },
      test("multiple annotations") {
        val aspect = ZStreamAspect.annotated("k1" -> "v1", "k2" -> "v2")
        val stream = ZStream(1, 2).tap(_ => ZIO.log("message")) @@ aspect
        for {
          _       <- stream.runDrain
          res     <- ZTestLogger.logOutput
          logAnns <- ZIO.logAnnotations
        } yield assertTrue(
          res.exists(i => i.annotations.get("k1").contains("v1")),
          res.exists(i => i.annotations.get("k2").contains("v2")),
          logAnns.get("k1").isEmpty,
          logAnns.get("k2").isEmpty
        )
      },
      test("does not leak annotations outside stream") {
        val aspect = ZStreamAspect.annotated("foo", "bar")
        val stream = ZStream.fromZIO(ZIO.log("x")) @@ aspect
        for {
          _       <- stream.runDrain
          logAnns <- ZIO.logAnnotations
        } yield assertTrue(logAnns.isEmpty)
      }
    ),
    suite("tagged")(
      test("single metric tag") {
        val aspect  = ZStreamAspect.tagged("env", "test")
        val counter = Metric.counter("ctr")
        val stream  = ZStream.succeed(1).tap(_ => counter.increment) @@ aspect
        for {
          _       <- stream.runDrain
          metrics <- ZIO.metrics
          _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
        } yield assertTrue(
          metrics.metrics.exists(m =>
            m.metricKey.name == "ctr" &&
              m.metricKey.tags.exists(l => l.key == "env" && l.value == "test")
          )
        )
      },
      test("multiple metric tags") {
        val aspect  = ZStreamAspect.tagged("env", "test") >>> ZStreamAspect.tagged("region", "us")
        val counter = Metric.counter("ctr2")
        val stream  = ZStream.succeed(1).tap(_ => counter.increment) @@ aspect
        for {
          _       <- stream.runDrain
          metrics <- ZIO.metrics
          _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
        } yield assertTrue(
          metrics.metrics.exists(m =>
            m.metricKey.name == "ctr2" &&
              m.metricKey.tags.exists(l => l.key == "env" && l.value == "test") &&
              m.metricKey.tags.exists(l => l.key == "region" && l.value == "us")
          )
        )
      },
      test("tagged(varargs) in one shot") {
        val aspect  = ZStreamAspect.tagged("k1" -> "v1", "k2" -> "v2")
        val counter = Metric.counter("ctrMulti")
        val stream  = ZStream.succeed(0).tap(_ => counter.increment) @@ aspect
        for {
          _       <- stream.runDrain
          metrics <- ZIO.metrics
          _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
        } yield assertTrue(
          metrics.metrics.exists(m =>
            m.metricKey.name == "ctrMulti" &&
              m.metricKey.tags.exists(_.key == "k1") &&
              m.metricKey.tags.exists(_.key == "k2")
          )
        )
      }
    ),
    suite("rechunk")(
      test("one big chunk if n > size") {
        val aspect = ZStreamAspect.rechunk(100)
        val data   = List(1, 2, 3, 4)
        for {
          chunks <- (ZStream.fromIterable(data) @@ aspect).chunks.runCollect
        } yield assert(chunks.map(_.toList).toList)(equalTo(List(data)))
      },
      test("perfect splits") {
        val aspect = ZStreamAspect.rechunk(2)
        for {
          chunks <- (ZStream.fromIterable(List(1, 2, 3, 4)) @@ aspect).chunks.runCollect
        } yield assert(chunks.map(_.toList).toList)(equalTo(List(List(1, 2), List(3, 4))))
      },
      test("handles remainder chunk") {
        val aspect = ZStreamAspect.rechunk(3)
        for {
          chunks <- (ZStream.fromIterable(List(1, 2, 3, 4, 5)) @@ aspect).chunks.runCollect
        } yield assert(chunks.map(_.toList).toList)(equalTo(List(List(1, 2, 3), List(4, 5))))
      },
      test("size = 0 with chunk of size 1") {
        val stream = ZStream.fromIterable(List(9, 8, 7)) @@ ZStreamAspect.rechunk(0)
        for {
          chunks <- stream.chunks.runCollect
        } yield assert(chunks)(equalTo(Chunk(Chunk(9), Chunk(8), Chunk(7))))
      },
      test("negative size with chunk of size 1") {
        val stream = ZStream.fromIterable(List(9, 8, 7)) @@ ZStreamAspect.rechunk(-5)
        for {
          chunks <- stream.chunks.runCollect
        } yield assert(chunks)(equalTo(Chunk(Chunk(9), Chunk(8), Chunk(7))))
      },
      test("empty stream remains empty") {
        val aspect = ZStreamAspect.rechunk(5)
        val stream = ZStream.fromIterable(List.empty[Int]) @@ aspect
        for {
          chunks <- stream.chunks.runCollect
        } yield assert(chunks)(isEmpty)
      }
    ),
    suite("composition")(
      test("composes annotated with >>>") {
        val a1     = ZStreamAspect.annotated("x", "1")
        val a2     = ZStreamAspect.annotated("y", "2")
        val stream = ZStream.fromZIO(ZIO.log("msg")) @@ (a1 >>> a2)
        for {
          _   <- stream.runDrain
          res <- ZTestLogger.logOutput
        } yield assertTrue(
          res.nonEmpty,
          res.forall(i => i.annotations.get("x").contains("1") && i.annotations.get("y").contains("2"))
        )
      },
      test("composes annotated with @@ alias") {
        val a1     = ZStreamAspect.annotated("m", "v1")
        val a2     = ZStreamAspect.annotated("n", "v2")
        val stream = ZStream.fromZIO(ZIO.log("msg")) @@ a1 @@ a2
        for {
          _   <- stream.runDrain
          res <- ZTestLogger.logOutput
        } yield assertTrue(
          res.nonEmpty,
          res.forall(i => i.annotations.get("m").contains("v1") && i.annotations.get("n").contains("v2"))
        )
      },
      test("mixes annotated and rechunk") {
        val annotate = ZStreamAspect.annotated("k", "v")
        val rechunk  = ZStreamAspect.rechunk(2)
        val stream   = ZStream.fromChunks(Chunk(1), Chunk(2), Chunk(3)).tap(_ => ZIO.log("x")) @@ annotate @@ rechunk
        for {
          chunk <- stream.chunks.runCollect
          res   <- ZTestLogger.logOutput
        } yield assertTrue(
          chunk == Chunk(Chunk(1, 2), Chunk(3)),
          res.forall(i => i.annotations.get("k").contains("v"))
        )
      },
      test("mixes annotated and tagged") {
        val annotate = ZStreamAspect.annotated("user", "bob")
        val tag      = ZStreamAspect.tagged("env", "prod")
        val ctr      = Metric.counter("ctr3")
        val stream   = ZStream.succeed(1).tap(_ => ZIO.log("m") *> ctr.increment) @@ annotate @@ tag
        for {
          _       <- stream.runDrain
          res     <- ZTestLogger.logOutput
          metrics <- ZIO.metrics
          _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
        } yield assertTrue(
          res.forall(i => i.annotations.get("user").contains("bob")),
          metrics.metrics.exists(m =>
            m.metricKey.name == "ctr3" &&
              m.metricKey.tags.exists(l => l.key == "env" && l.value == "prod")
          )
        )
      },
      test("annotation order invariance") {
        val a1   = ZStreamAspect.annotated("o1", "v1")
        val a2   = ZStreamAspect.annotated("o2", "v2")
        val base = ZStream.fromZIO(ZIO.log("x"))
        for {
          r1 <- (base @@ (a1 >>> a2)).runDrain *> ZTestLogger.logOutput
          r2 <- (base @@ (a2 >>> a1)).runDrain *> ZTestLogger.logOutput
        } yield assertTrue(
          r1.nonEmpty,
          r2.nonEmpty,
          r1.forall(evt => evt.annotations.contains("o1") && evt.annotations.contains("o2")),
          r2.forall(evt => evt.annotations.contains("o1") && evt.annotations.contains("o2"))
        )
      }
    ),
    suite("other cases")(
      test("stream failure propagates") {
        val aspect = ZStreamAspect.rechunk(2)
        val stream = (ZStream(1, 2) ++ ZStream.fail("err")) @@ aspect
        for {
          res <- stream.runCollect.either
        } yield assert(res)(isLeft(equalTo("err")))
      },
      test("handles complex stream with multiple aspects") {
        val annotate = ZStreamAspect.annotated("key", "value")
        val rechunk  = ZStreamAspect.rechunk(2)
        val tag      = ZStreamAspect.tagged("env", "test")
        val counter  = Metric.counter("test_counter")

        val stream = ZStream
          .fromChunks(Chunk(1), Chunk(2), Chunk(3))
          .tap(_ => ZIO.log("message") *> counter.increment)
          .map(_ * 2)
          .filter(_ > 2)
          .take(2)
          .@@(annotate)
          .@@(rechunk)
          .@@(tag)

        for {
          chunks  <- stream.chunks.runCollect
          output  <- ZTestLogger.logOutput
          metrics <- ZIO.metrics
        } yield assertTrue(
          chunks == Chunk(Chunk(4, 6)),
          output.exists(e => e.annotations.get("key").contains("value")),
          metrics.metrics.exists(m =>
            m.metricKey.name == "test_counter" &&
              m.metricKey.tags.exists(l => l.key == "env" && l.value == "test")
          )
        )
      },
      test("infinite stream chunked lazily") {
        val aspect = ZStreamAspect.rechunk(2)
        for {
          chunk <- (ZStream.repeat(5) @@ aspect).chunks.take(2).runCollect
        } yield assert(chunk.map(_.size).toList)(equalTo(List(2, 2)))
      },
      test("interrupt still applies annotations") {
        val aspect = ZStreamAspect.annotated("i", "j")
        val stream = ZStream.fromZIO(ZIO.log("m")).take(3) @@ aspect
        for {
          fiber <- stream.runDrain.fork
          _     <- TestClock.adjust(1.second)
          _     <- fiber.interrupt
          res   <- ZTestLogger.logOutput
        } yield assertTrue(res.nonEmpty, res.forall(_.annotations.get("i").contains("j")))
      },
      test("resource safety with bracket") {
        for {
          ref <- Ref.make(false)
          s    = ZStream.acquireReleaseWith(ZIO.unit)(_ => ref.set(true)) *> ZStream(1)
          _   <- (s @@ ZStreamAspect.annotated("r", "t")).runDrain
          res <- ref.get
        } yield assert(res)(isTrue)
      },
      test("merging streams retains annotations") {
        val a1     = ZStreamAspect.annotated("m1", "v1")
        val a2     = ZStreamAspect.annotated("m2", "v2")
        val s1     = ZStream.fromZIO(ZIO.log("one")) @@ a1
        val s2     = ZStream.fromZIO(ZIO.log("two")) @@ a2
        val merged = s1.merge(s2)
        for {
          _   <- merged.runDrain
          res <- ZTestLogger.logOutput
        } yield assertTrue(
          res.exists(_.annotations.get("m1").contains("v1")),
          res.exists(_.annotations.get("m2").contains("v2"))
        )
      }
    )
  ) @@ sequential
    @@ withLiveClock).provideLayer(ZTestLogger.default)
}
