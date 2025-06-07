package zio.stream

import zio._
import zio.test._
import zio.test.ZIOSpecDefault
import zio.test.ZTestLogger
import zio.metrics.Metric
import zio.stream.ZStreamAspect
import zio.internal.metrics.metricRegistry
import zio.test.Assertion._

object ZStreamAspectSpec extends ZIOSpecDefault {

  def spec =
    (suite("ZStreamAspectSpec")(
      suite("annotated")(
        test("single annotation") {
          val aspect = ZStreamAspect.annotated("key", "value")
          val stream = ZStream.fromZIO(ZIO.log("message")) @@ aspect
          for {
            _      <- stream.runDrain
            output <- ZTestLogger.logOutput
            anns   <- ZIO.logAnnotations
          } yield assertTrue(
            output.exists(_.annotations.get("key").contains("value")),
            anns.get("key").isEmpty
          )
        },
        test("multiple annotations") {
          val aspect = ZStreamAspect.annotated("key1" -> "value1", "key2" -> "value2")
          val stream = ZStream(1, 2).tap(_ => ZIO.log("message")) @@ aspect
          for {
            _      <- stream.runDrain
            output <- ZTestLogger.logOutput
            anns   <- ZIO.logAnnotations
          } yield assertTrue(
            output.exists(e => e.annotations.get("key1").contains("value1")),
            output.exists(e => e.annotations.get("key2").contains("value2")),
            anns.get("key1").isEmpty,
            anns.get("key2").isEmpty
          )
        }
      ),
      suite("rechunk")(
        test("rechunks stream to specified size") {
          val aspect = ZStreamAspect.rechunk(2)
          val stream = ZStream.fromChunks(Chunk(1), Chunk(2), Chunk(3), Chunk(4)) @@ aspect
          for {
            chunks <- stream.chunks.runCollect
          } yield assertTrue(chunks == Chunk(Chunk(1, 2), Chunk(3, 4)))
        },
        test("handles final chunk smaller than n") {
          val aspect = ZStreamAspect.rechunk(3)
          val stream = ZStream.fromChunks(Chunk(1), Chunk(2), Chunk(3), Chunk(4)) @@ aspect
          for {
            chunks <- stream.chunks.runCollect
          } yield assertTrue(chunks == Chunk(Chunk(1, 2, 3), Chunk(4)))
        },
        test("handles empty stream") {
          val aspect = ZStreamAspect.rechunk(2)
          val stream = ZStream.empty @@ aspect
          for {
            chunks <- stream.chunks.runCollect
          } yield assertTrue(chunks == Chunk())
        },
        test("handles invalid chunk size") {
          val aspect = ZStreamAspect.rechunk(0)
          val stream = ZStream.fromChunks(Chunk(1, 2)) @@ aspect
          for {
            chunks <- stream.chunks.runCollect
          } yield assertTrue(chunks == Chunk(Chunk(1), Chunk(2)))
        },
        test("handles negative chunk size") {
          val aspect = ZStreamAspect.rechunk(-1)
          val stream = ZStream.fromChunks(Chunk(1, 2)) @@ aspect
          for {
            chunks <- stream.chunks.runCollect
          } yield assertTrue(chunks == Chunk(Chunk(1), Chunk(2)))
        }
      ),
      suite("tagged")(
        test("tags metrics with specified key-value pair") {
          val aspect = ZStreamAspect.tagged("env", "test")
          val counter = Metric.counter("test_counter")
          val stream = ZStream.succeed(1).tap(_ => counter.increment) @@ aspect
          for {
            _      <- stream.runDrain
            metrics <- ZIO.metrics
            _      <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
          } yield assertTrue(
            metrics.metrics.exists(m =>
              m.metricKey.name == "test_counter" &&
              m.metricKey.tags.exists(l => l.key == "env" && l.value == "test")
            )
          )
        },
        test("handles multiple tags") {
          val aspect = ZStreamAspect.tagged("env", "test") >>> ZStreamAspect.tagged("region", "us")
          val counter = Metric.counter("test_counter")
          val stream = ZStream.succeed(1).tap(_ => counter.increment) @@ aspect
          for {
            _      <- stream.runDrain
            metrics <- ZIO.metrics
            _      <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
          } yield assertTrue(
            metrics.metrics.exists(m =>
              m.metricKey.name == "test_counter" &&
              m.metricKey.tags.exists(l => l.key == "env" && l.value == "test") &&
              m.metricKey.tags.exists(l => l.key == "region" && l.value == "us")
            )
          )
        }
      ),
      suite("composition")(
        test("composes aspects with >>>") {
          val aspect1 = ZStreamAspect.annotated("key1", "value1")
          val aspect2 = ZStreamAspect.annotated("key2", "value2")
          val stream = ZStream.fromZIO(ZIO.log("message")) @@ (aspect1 >>> aspect2)
          for {
            _      <- stream.runDrain
            output <- ZTestLogger.logOutput
          } yield assertTrue(
            output.exists(e => e.annotations.get("key1").contains("value1")),
            output.exists(e => e.annotations.get("key2").contains("value2"))
          )
        },
        test("composes aspects with @@") {
          val aspect1 = ZStreamAspect.annotated("key1", "value1")
          val aspect2 = ZStreamAspect.annotated("key2", "value2")
          val stream = ZStream.fromZIO(ZIO.log("message")) @@ aspect1 @@ aspect2
          for {
            _      <- stream.runDrain
            output <- ZTestLogger.logOutput
          } yield assertTrue(
            output.exists(e => e.annotations.get("key1").contains("value1")),
            output.exists(e => e.annotations.get("key2").contains("value2"))
          )
        },
        test("composes different aspect types") {
          val annotate = ZStreamAspect.annotated("key", "value")
          val rechunk = ZStreamAspect.rechunk(2)
          val stream = ZStream.fromChunks(Chunk(1), Chunk(2), Chunk(3)).tap(_ => ZIO.log("message")) @@ annotate @@ rechunk
          for {
            chunks <- stream.chunks.runCollect
            output <- ZTestLogger.logOutput
          } yield assertTrue(
            chunks == Chunk(Chunk(1, 2), Chunk(3)),
            output.exists(e => e.annotations.get("key").contains("value"))
          )
        },
        test("composes tagged and annotated aspects") {
          val annotate = ZStreamAspect.annotated("key", "value")
          val tag = ZStreamAspect.tagged("env", "test")
          val counter = Metric.counter("test_counter")
          val stream = ZStream.succeed(1).tap(_ => ZIO.log("message") *> counter.increment) @@ annotate @@ tag
          for {
            _      <- stream.runDrain
            output <- ZTestLogger.logOutput
            metrics <- ZIO.metrics
          } yield assertTrue(
            output.exists(e => e.annotations.get("key").contains("value")),
            metrics.metrics.exists(m =>
              m.metricKey.name == "test_counter" &&
              m.metricKey.tags.exists(l => l.key == "env" && l.value == "test")
            )
          )
        },
        test("verifies aspect application order") {
          val annotate = ZStreamAspect.annotated("key", "value")
          val rechunk = ZStreamAspect.rechunk(2)
          val stream = ZStream.fromChunks(Chunk(1), Chunk(2), Chunk(3)).tap(_ => ZIO.log("message"))
          
          // Test both orders of composition
          for {
            // First annotate then rechunk
            chunks1 <- (stream @@ annotate @@ rechunk).chunks.runCollect
            output1 <- ZTestLogger.logOutput
            // First rechunk then annotate
            chunks2 <- (stream @@ rechunk @@ annotate).chunks.runCollect
            output2 <- ZTestLogger.logOutput
          } yield assertTrue(
            chunks1 == Chunk(Chunk(1, 2), Chunk(3)),
            output1.exists(e => e.annotations.get("key").contains("value")),
            chunks2 == Chunk(Chunk(1, 2), Chunk(3)),
            output2.exists(e => e.annotations.get("key").contains("value"))
          )
        }
      ),
      suite("edge cases")(
        test("handles stream with errors") {
          val aspect = ZStreamAspect.rechunk(2)
          val stream = (ZStream(1, 2) ++ ZStream.fail("error")) @@ aspect
          for {
            result <- stream.runCollect.either
            anns <- ZIO.logAnnotations
          } yield assertTrue(
            result.isLeft,
            result.left.exists(_ == "error"),
            anns.isEmpty
          )
        },
        test("handles complex stream with multiple aspects") {
          val annotate = ZStreamAspect.annotated("key", "value")
          val rechunk = ZStreamAspect.rechunk(2)
          val tag = ZStreamAspect.tagged("env", "test")
          val counter = Metric.counter("test_counter")
          
          val stream = ZStream.fromChunks(Chunk(1), Chunk(2), Chunk(3))
            .tap(_ => ZIO.log("message") *> counter.increment)
            .map(_ * 2)
            .filter(_ > 2)
            .take(2)
            .@@(annotate)
            .@@(rechunk)
            .@@(tag)
            
          for {
            chunks <- stream.chunks.runCollect
            output <- ZTestLogger.logOutput
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
        test("handles infinite stream") {
          val aspect = ZStreamAspect.rechunk(2)
          val stream = ZStream.repeat(1) @@ aspect
          for {
            chunks <- stream.chunks.take(2).runCollect
          } yield assertTrue(
            chunks.forall(_.size == 2),
            chunks.flatten.take(4).forall(_ == 1)
          )
        },
        test("handles stream with interruptions") {
          val aspect = ZStreamAspect.annotated("key", "value")
          val stream = ZStream.fromZIO(ZIO.log("message")).take(5) @@ aspect
          for {
            fiber <- stream.runDrain.fork
            _     <- TestClock.adjust(1.second)
            _     <- fiber.interrupt
            output <- ZTestLogger.logOutput
            anns  <- ZIO.logAnnotations
          } yield assertTrue(
            output.exists(e => e.annotations.get("key").contains("value")),
            anns.get("key").isEmpty
          )
        },
        test("ensures resource safety with bracket and aspects") {
          val program = Ref.make(false).flatMap { ref =>
            val stream = ZStream.acquireReleaseWith(ZIO.unit)(_ => ref.set(true)) *> ZStream(1, 2, 3)
            val annotatedStream = stream @@ ZStreamAspect.annotated("key", "value")
            annotatedStream.runDrain *> ref.get
          }
          assertZIO(program)(isTrue)
        },
        test("handles merging streams with different aspects") {
          val aspect1 = ZStreamAspect.annotated("key1", "value1")
          val aspect2 = ZStreamAspect.annotated("key2", "value2")
          val stream1 = ZStream.fromZIO(ZIO.log("message1")) @@ aspect1
          val stream2 = ZStream.fromZIO(ZIO.log("message2")) @@ aspect2
          val merged = stream1.merge(stream2)
          
          for {
            _      <- merged.runDrain
            output <- ZTestLogger.logOutput
          } yield assertTrue(
            output.exists(e => e.annotations.get("key1").contains("value1")),
            output.exists(e => e.annotations.get("key2").contains("value2"))
          )
        }
      )
    ) @@ TestAspect.sequential @@ TestAspect.withLiveClock)
      .provideLayer(ZTestLogger.default)
}