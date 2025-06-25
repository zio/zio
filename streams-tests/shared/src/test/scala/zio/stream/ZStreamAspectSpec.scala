package zio.stream

import zio._
import zio.metrics.Metric
import zio.internal.metrics.metricRegistry
import zio.test._
import zio.test.ZIOSpecDefault
import zio.test.ZTestLogger

/**
 * Comprehensive test suite for ZStreamAspect
 *
 * This test suite covers the available ZStreamAspect implementations including:
 *   - annotated: Adding log annotations to streams
 *   - tagged: Adding metric tags to streams
 *   - rechunk: Changing chunk sizes in streams
 *   - Composition and interaction between aspects
 *   - Error handling and resource safety
 *   - Performance characteristics
 */
object ZStreamAspectSpec extends ZIOSpecDefault {

  override def spec = suite("ZStreamAspectSpec")(
    annotatedTests,
    taggedTests,
    rechunkTests,
    compositionTests,
    errorHandlingTests,
    performanceTests,
    edgeCaseTests
  ).provideLayer(ZTestLogger.default) @@ TestAspect.sequential @@ TestAspect.withLiveClock

  private val annotatedTests = suite("annotated aspect")(
    test("preserves stream elements") {
      val data = List("apple", "banana", "cherry")
      for {
        result <- (ZStream.fromIterable(data) @@ ZStreamAspect.annotated("fruit", "fresh")).runCollect
      } yield assertTrue(result.toList == data)
    },
    test("adds single annotation to logs") {
      val aspect = ZStreamAspect.annotated("service", "user-api")
      val stream = ZStream.fromZIO(ZIO.logInfo("Processing request")) @@ aspect

      for {
        _      <- stream.runDrain
        logs   <- ZTestLogger.logOutput
        logCtx <- ZIO.logAnnotations
      } yield assertTrue(
        logs.exists(_.annotations.get("service").contains("user-api")),
        logCtx.get("service").isEmpty // annotations don't leak
      )
    },
    test("adds multiple annotations via varargs") {
      val aspect = ZStreamAspect.annotated("requestId" -> "req-123", "userId" -> "user-456")
      val stream = ZStream(1, 2, 3).tap(_ => ZIO.logInfo("Processing item")) @@ aspect

      for {
        _    <- stream.runDrain
        logs <- ZTestLogger.logOutput
      } yield assertTrue(
        logs.forall(log =>
          log.annotations.get("requestId").contains("req-123") &&
            log.annotations.get("userId").contains("user-456")
        )
      )
    },
    test("annotations are scoped to stream execution") {
      val aspect = ZStreamAspect.annotated("scope", "test")
      val stream = ZStream.fromZIO(ZIO.logInfo("inside stream")) @@ aspect

      for {
        _          <- stream.runDrain
        _          <- ZIO.logInfo("outside stream")
        logs       <- ZTestLogger.logOutput
        outsideLogs = logs.filter(_.message() == "outside stream")
        insideLogs  = logs.filter(_.message() == "inside stream")
      } yield assertTrue(
        insideLogs.forall(_.annotations.contains("scope")),
        outsideLogs.forall(!_.annotations.contains("scope"))
      )
    },
    test("works with empty streams") {
      val aspect = ZStreamAspect.annotated("empty", "stream")
      val stream = ZStream.empty.tap(_ => ZIO.logInfo("should not log")) @@ aspect

      for {
        result <- stream.runCollect
        logs   <- ZTestLogger.logOutput
      } yield assertTrue(
        result.isEmpty,
        logs.isEmpty
      )
    }
  )

  private val taggedTests = suite("tagged aspect")(
    test("adds single metric tag") {
      val aspect  = ZStreamAspect.tagged("environment", "production")
      val counter = Metric.counter("request_count")
      val stream  = ZStream.succeed(1).tap(_ => counter.increment) @@ aspect

      for {
        _       <- stream.runDrain
        metrics <- ZIO.metrics
        _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
      } yield assertTrue(
        metrics.metrics.exists(m =>
          m.metricKey.name == "request_count" &&
            m.metricKey.tags.exists(tag => tag.key == "environment" && tag.value == "production")
        )
      )
    },
    test("adds multiple tags via composition") {
      val envTag    = ZStreamAspect.tagged("env", "test")
      val regionTag = ZStreamAspect.tagged("region", "us-east-1")
      val counter   = Metric.counter("api_calls")
      val stream    = ZStream.succeed(1).tap(_ => counter.increment) @@ envTag @@ regionTag

      for {
        _       <- stream.runDrain
        metrics <- ZIO.metrics
        _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
      } yield assertTrue(
        metrics.metrics.exists(m =>
          m.metricKey.name == "api_calls" &&
            m.metricKey.tags.exists(_.key == "env") &&
            m.metricKey.tags.exists(_.key == "region")
        )
      )
    },
    test("adds multiple tags in single call") {
      val aspect  = ZStreamAspect.tagged("service" -> "auth", "version" -> "v1.2.3")
      val counter = Metric.counter("response_counter")
      val stream  = ZStream.succeed(42).tap(_ => counter.increment) @@ aspect

      for {
        _       <- stream.runDrain
        metrics <- ZIO.metrics
        _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
      } yield assertTrue(
        metrics.metrics.exists(m =>
          m.metricKey.name == "response_counter" &&
            m.metricKey.tags.exists(_.key == "service") &&
            m.metricKey.tags.exists(_.key == "version")
        )
      )
    },
    test("works with different metric types") {
      val aspect  = ZStreamAspect.tagged("type", "test")
      val counter = Metric.counter("test_counter")
      val gauge   = Metric.gauge("test_gauge")

      val stream = ZStream.range(1, 4).tap { i =>
        counter.increment *>
          gauge.set(i.toDouble)
      } @@ aspect

      for {
        _       <- stream.runDrain
        metrics <- ZIO.metrics
        _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
      } yield assertTrue(
        List("test_counter", "test_gauge").forall(name =>
          metrics.metrics.exists(m =>
            m.metricKey.name == name &&
              m.metricKey.tags.exists(tag => tag.key == "type" && tag.value == "test")
          )
        )
      )
    }
  )

  private val rechunkTests = suite("rechunk aspect")(
    test("creates single chunk when n exceeds stream size") {
      val aspect = ZStreamAspect.rechunk(100)
      val data   = List(1, 2, 3, 4, 5)

      for {
        chunks <- (ZStream.fromIterable(data) @@ aspect).chunks.runCollect
      } yield assertTrue(
        chunks.length == 1,
        chunks.head.toList == data
      )
    },
    test("creates perfectly sized chunks") {
      val aspect = ZStreamAspect.rechunk(3)
      val data   = List(1, 2, 3, 4, 5, 6, 7, 8, 9)

      for {
        chunks <- (ZStream.fromIterable(data) @@ aspect).chunks.runCollect
      } yield assertTrue(
        chunks.map(_.toList).toList == List(List(1, 2, 3), List(4, 5, 6), List(7, 8, 9))
      )
    },
    test("handles remainder chunks correctly") {
      val aspect = ZStreamAspect.rechunk(4)
      val data   = List(1, 2, 3, 4, 5, 6, 7)

      for {
        chunks <- (ZStream.fromIterable(data) @@ aspect).chunks.runCollect
      } yield assertTrue(
        chunks.map(_.toList).toList == List(List(1, 2, 3, 4), List(5, 6, 7))
      )
    },
    test("handles zero and negative chunk sizes") {
      for {
        zeroChunks     <- (ZStream.fromIterable(List(1, 2, 3)) @@ ZStreamAspect.rechunk(0)).chunks.runCollect
        negativeChunks <- (ZStream.fromIterable(List(4, 5, 6)) @@ ZStreamAspect.rechunk(-5)).chunks.runCollect
      } yield assertTrue(
        zeroChunks == Chunk(Chunk(1), Chunk(2), Chunk(3)),
        negativeChunks == Chunk(Chunk(4), Chunk(5), Chunk(6))
      )
    },
    test("preserves empty streams") {
      val aspect = ZStreamAspect.rechunk(10)
      val stream = ZStream.empty @@ aspect

      for {
        chunks   <- stream.chunks.runCollect
        elements <- stream.runCollect
      } yield assertTrue(
        chunks.isEmpty,
        elements.isEmpty
      )
    },
    test("works with large chunk sizes on infinite streams") {
      val aspect = ZStreamAspect.rechunk(1000)

      for {
        firstChunk <- (ZStream.iterate(0)(_ + 1) @@ aspect).chunks.take(1).runCollect
      } yield assertTrue(
        firstChunk.head.size == 1000
      )
    },
    test("maintains stream order") {
      val aspect = ZStreamAspect.rechunk(2)
      val data   = (1 to 10).toList

      for {
        rechunked <- (ZStream.fromIterable(data) @@ aspect).runCollect
      } yield assertTrue(rechunked.toList == data)
    }
  )

  private val compositionTests = suite("aspect composition")(
    test("composes with >>> operator") {
      val annotate = ZStreamAspect.annotated("trace", "abc123")
      val rechunk  = ZStreamAspect.rechunk(2)
      val stream = ZStream
        .fromChunks(Chunk(1), Chunk(2), Chunk(3), Chunk(4))
        .tap(_ => ZIO.logInfo("processing")) @@ (annotate >>> rechunk)

      for {
        chunks <- stream.chunks.runCollect
        logs   <- ZTestLogger.logOutput
      } yield assertTrue(
        chunks == Chunk(Chunk(1, 2), Chunk(3, 4)),
        logs.forall(_.annotations.get("trace").contains("abc123"))
      )
    },
    test("composes with multiple @@ applications") {
      val ann1    = ZStreamAspect.annotated("step", "1")
      val ann2    = ZStreamAspect.annotated("phase", "processing")
      val tag     = ZStreamAspect.tagged("component", "stream-processor")
      val counter = Metric.counter("processed_items")

      val stream = ZStream
        .range(1, 4)
        .tap(i => ZIO.logInfo(s"Item $i") *> counter.increment)
      val aspectedStream = stream @@ ann1 @@ ann2 @@ tag

      for {
        _       <- aspectedStream.runDrain
        logs    <- ZTestLogger.logOutput
        metrics <- ZIO.metrics
        _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
      } yield assertTrue(
        logs.forall(log =>
          log.annotations.get("step").contains("1") &&
            log.annotations.get("phase").contains("processing")
        ),
        metrics.metrics.exists(m =>
          m.metricKey.name == "processed_items" &&
            m.metricKey.tags.exists(t => t.key == "component" && t.value == "stream-processor")
        )
      )
    },
    test("order independence for annotations") {
      val a1   = ZStreamAspect.annotated("key1", "value1")
      val a2   = ZStreamAspect.annotated("key2", "value2")
      val base = ZStream.fromZIO(ZIO.logInfo("test"))

      for {
        logs1 <- (base @@ (a1 >>> a2)).runDrain *> ZTestLogger.logOutput
        logs2 <- (base @@ (a2 >>> a1)).runDrain *> ZTestLogger.logOutput
      } yield assertTrue(
        logs1.nonEmpty && logs2.nonEmpty,
        logs1.forall(_.annotations.contains("key1")) && logs1.forall(_.annotations.contains("key2")),
        logs2.forall(_.annotations.contains("key1")) && logs2.forall(_.annotations.contains("key2"))
      )
    },
    test("mixing different aspect types") {
      val annotate = ZStreamAspect.annotated("operation", "data-processing")
      val rechunk  = ZStreamAspect.rechunk(3)
      val tag      = ZStreamAspect.tagged("env", "test")
      val counter  = Metric.counter("mixed_test_counter")

      val stream = ZStream
        .range(1, 11)
        .tap(i => ZIO.logInfo(s"Processing $i") *> counter.increment)
      val aspectedStream = stream @@ annotate @@ rechunk @@ tag

      for {
        chunks  <- aspectedStream.chunks.runCollect
        logs    <- ZTestLogger.logOutput
        metrics <- ZIO.metrics
        _       <- ZIO.succeed(metricRegistry.snapshot()(Unsafe.unsafe))
      } yield assertTrue(
        chunks.forall(_.size <= 3),
        logs.forall(_.annotations.get("operation").contains("data-processing")),
        metrics.metrics.exists(m =>
          m.metricKey.name == "mixed_test_counter" &&
            m.metricKey.tags.exists(t => t.key == "env" && t.value == "test")
        )
      )
    }
  )

  private val errorHandlingTests = suite("error handling")(
    test("stream failures propagate through aspects") {
      val aspect = ZStreamAspect.rechunk(2) >>> ZStreamAspect.annotated("error", "test")
      val stream = (ZStream(1, 2) ++ ZStream.fail("deliberate failure")) @@ aspect

      for {
        result <- stream.runCollect.exit
      } yield assertTrue(result.isFailure)
    },
    test("aspects preserve interruption") {
      val aspect = ZStreamAspect.annotated("interrupted", "yes")
      val stream = ZStream.fromZIO(ZIO.logInfo("before interrupt")).take(10) @@ aspect

      for {
        fiber <- stream.runDrain.fork
        _     <- TestClock.adjust(100.millis)
        _     <- fiber.interrupt
        logs  <- ZTestLogger.logOutput
      } yield assertTrue(
        logs.exists(_.annotations.get("interrupted").contains("yes"))
      )
    },
    test("resource safety with managed resources") {
      for {
        released <- Ref.make(false)
        stream = ZStream
                   .acquireReleaseWith(ZIO.succeed("resource"))(_ => released.set(true))
                   .flatMap(resource => ZStream(resource))
        aspectedStream = stream @@ ZStreamAspect.annotated("resource", "managed")
        _             <- aspectedStream.runDrain
        wasReleased   <- released.get
      } yield assertTrue(wasReleased)
    },
    test("error in stream operation") {
      val stream = ZStream(1, 2, 3)
        .tap(_ => ZIO.fail("stream error"))
      val aspectedStream = stream @@ ZStreamAspect.annotated("test", "value")

      for {
        result <- aspectedStream.runCollect.exit
      } yield assertTrue(result.isFailure)
    }
  )

  private val performanceTests = suite("performance characteristics")(
    test("rechunk doesn't affect throughput significantly") {
      val dataSize = 10000
      val data     = (1 to dataSize).toList

      for {
        start1   <- Clock.nanoTime
        result1  <- ZStream.fromIterable(data).runCollect
        end1     <- Clock.nanoTime
        duration1 = end1 - start1

        start2   <- Clock.nanoTime
        result2  <- (ZStream.fromIterable(data) @@ ZStreamAspect.rechunk(100)).runCollect
        end2     <- Clock.nanoTime
        duration2 = end2 - start2
      } yield assertTrue(
        result1 == result2,
        duration2 < duration1 * 5 // Allow 5x overhead for aspects (more lenient)
      )
    },
    test("annotations don't significantly impact performance") {
      val iterations = 1000
      val stream     = ZStream.range(1, iterations + 1)

      for {
        start1   <- Clock.nanoTime
        _        <- stream.runDrain
        end1     <- Clock.nanoTime
        duration1 = end1 - start1

        start2   <- Clock.nanoTime
        _        <- (stream @@ ZStreamAspect.annotated("perf", "test")).runDrain
        end2     <- Clock.nanoTime
        duration2 = end2 - start2
      } yield assertTrue(
        duration2 < duration1 * 5 // Allow 5x overhead for annotations
      )
    }
  ) @@ TestAspect.withLiveClock

  private val edgeCaseTests = suite("edge cases")(
    test("very large chunk sizes") {
      val aspect = ZStreamAspect.rechunk(1000000) // Use reasonable size instead of Int.MaxValue
      val data   = List(1, 2, 3)

      for {
        chunks <- (ZStream.fromIterable(data) @@ aspect).chunks.runCollect
      } yield assertTrue(
        chunks.length == 1,
        chunks.head.toList == data
      )
    },
    test("many annotations don't cause stack overflow") {
      val manyAnnotations = (1 to 100).foldLeft(ZStream.succeed(1)) { (stream, i) =>
        stream @@ ZStreamAspect.annotated(s"key$i", s"value$i")
      }

      for {
        result <- manyAnnotations.runCollect
      } yield assertTrue(result == Chunk(1))
    },
    test("concurrent stream merging preserves aspects") {
      val ann1 = ZStreamAspect.annotated("stream", "first")
      val ann2 = ZStreamAspect.annotated("stream", "second")

      val s1 = ZStream.fromZIO(ZIO.logInfo("from first")) @@ ann1
      val s2 = ZStream.fromZIO(ZIO.logInfo("from second")) @@ ann2

      for {
        _    <- s1.merge(s2).runDrain
        logs <- ZTestLogger.logOutput
      } yield assertTrue(
        logs.exists(_.annotations.get("stream").contains("first")),
        logs.exists(_.annotations.get("stream").contains("second"))
      )
    },
    test("aspects work with infinite streams") {
      val aspect = ZStreamAspect.rechunk(5) >>> ZStreamAspect.annotated("infinite", "true")

      for {
        result <- (ZStream.iterate(0)(_ + 1) @@ aspect)
                    .take(20)
                    .runCollect
      } yield assertTrue(result.length == 20)
    },
    test("deeply nested aspect composition") {
      val deepComposition = (1 to 10).foldLeft(ZStream.succeed(42)) { (stream, i) =>
        stream @@ ZStreamAspect.annotated(s"level$i", i.toString)
      }

      for {
        _    <- deepComposition.tap(_ => ZIO.logInfo("deep")).runDrain
        logs <- ZTestLogger.logOutput
      } yield assertTrue(
        (1 to 10).forall(i => logs.exists(_.annotations.get(s"level$i").contains(i.toString)))
      )
    }
  )
}
