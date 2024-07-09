package zio

import zio.test.{TestAspect, TestAspectPoly, assertTrue}

import java.lang.management.ManagementFactory
import BytesFormatter.BytesFmt

trait MemoryLeakSpec extends ZIOBaseSpec {

  case class MemoryHolder(ar: Array[Byte])
  object MemoryHolder {
    def apply(bytes: Int = 1024): MemoryHolder = MemoryHolder(Array.ofDim[Byte](bytes))
  }

  override def aspects: Chunk[TestAspectPoly] =
    Chunk(TestAspect.timeout(15.seconds), TestAspect.timed, TestAspect.sequential, TestAspect.withLiveClock)

  private def heapUsed: UIO[Long] =
    ZIO.succeed {
      val runtime = java.lang.Runtime.getRuntime
      runtime.gc()
      java.lang.System.gc()
      runtime.totalMemory() - runtime.freeMemory()
    }

  private def currentGC: Chunk[String] = {
    val gcMxBeans = Chunk.fromJavaIterable(ManagementFactory.getGarbageCollectorMXBeans)
    gcMxBeans.map(_.getName)
  }

  case class LeakDetected(heapUsed: Long, zScore: Double)

  private def mean(xs: Chunk[Long]): Double = xs.sum / xs.size.toDouble

  private def variance(xs: Chunk[Long]): Double = {
    val avg = mean(xs)
    xs.map(x => math.pow(x - avg, 2)).sum / xs.size
  }

  private def standardDeviation(xs: Chunk[Long]): Double = math.sqrt(variance(xs))

  private def zScore(xs: Chunk[Long]): Double = {
    val avg    = mean(xs)
    val stdDev = standardDeviation(xs)
    val x      = xs.last
    (x - avg) / stdDev
  }

  private def percDiff(a: Long, b: Long): Double =
    100 * math.abs(a - b) / ((a + b) / 2)

  private def monitorHeap(
    samplePeriod: Duration,
    warmupIterations: Int,
    heapUsedInitial: Long,
    zScoreThreshold: Double
  ): Task[LeakDetected] = {
    println(s"heapUsedInitial = ${heapUsedInitial.fmtBytes}")
    def go(initialBytes: Long, usedHistory: Chunk[Long] = Chunk.empty): Task[LeakDetected] =
      ZIO.sleep(samplePeriod) *> heapUsed.flatMap { bytesUsed =>
        val currentDiff = bytesUsed - initialBytes

        val updated = usedHistory :+ bytesUsed

        val zs = zScore(updated)
        println(
          f"Heap: ${bytesUsed.fmtBytes}%12.12s total, ${currentDiff.fmtDelta}%12.12s since start, zScore=${zs}"
        )

        if (zs > zScoreThreshold) {
          println(s"LEAK: zscore=${zs} hist=${updated.map(_.fmtBytes)}")
          Exit.succeed(LeakDetected(bytesUsed, zs))
        } else go(initialBytes, updated)

      }

    heapUsed.flatMap { usedBytes =>
      ZIO.logDebug(s"usedBytes per warmup = ${usedBytes.fmtBytes}") *> ZIO.sleep(samplePeriod)
    }.repeatN(warmupIterations) *> heapUsed.flatMap { initialBytes =>
      println(s"heap used after warmup = ${initialBytes.fmtBytes}")
      if (percDiff(heapUsedInitial, initialBytes) > 100.0) {
        // heap increased twice after warmup, definitely leak
        Exit.Success(LeakDetected(initialBytes, 2.0))
      } else go(initialBytes)
    }
  }

  private lazy val gc = currentGC

  protected def leakTest(
    name: String,
    zScoreThreshold: Double = 2
  )(effect: => Task[Unit]) =
    test(name) {
      println(s"start test $name, using GC = $gc")
      heapUsed.flatMap { initialBytes =>
        effect.disconnect
          .timeout(10.seconds)
          .raceEither(
            monitorHeap(
              samplePeriod = 1.seconds,
              warmupIterations = 3,
              heapUsedInitial = initialBytes,
              zScoreThreshold = zScoreThreshold
            )
          )
          .map { result =>
            assertTrue(result.isLeft)
          }
      }
    }
}
