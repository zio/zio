package zio

import java.lang.management.ManagementFactory
import BytesFormatter.BytesFmt

import zio.TestUtils._

abstract class MemoryLeakSpec extends munit.FunSuite {

  override val munitTimeout = 60.seconds.asFiniteDuration

  case class MemoryHolder(ar: Array[Byte])
  object MemoryHolder {
    def apply(bytes: Int = 1024): MemoryHolder = MemoryHolder(Array.ofDim[Byte](bytes))
  }

  private def heapUsed: UIO[Long] =
    ZIO.succeed {
      val runtime = java.lang.Runtime.getRuntime
      runtime.gc()
      runtime.totalMemory() - runtime.freeMemory()
    }

  private def currentGC: Chunk[String] = {
    val gcMxBeans = Chunk.fromJavaIterable(ManagementFactory.getGarbageCollectorMXBeans)
    gcMxBeans.map(_.getName)
  }

  sealed trait LeakResult

  case class LeakDetected(heapUsed: Long, increasePercent: Double) extends LeakResult
  case object NoLeakDetected                                       extends LeakResult

  private def percDiff(first: Long, last: Long): Double =
    100 * (last - first) / first

  case class MemoryState(history: Chunk[Long] = Chunk.empty, mins: Chunk[Long] = Chunk.empty) {
    def append(used: Long): MemoryState =
      this.copy(history = history :+ used)

    def compact: MemoryState = this.copy(
      mins = mins :+ history.min,
      history = Chunk.empty
    )
  }

  private def monitorHeap(
    samplePeriod: Duration,
    warmupPeriods: Int,
    increaseThreshold: Double
  ): ZIO[Scope, Throwable, LeakDetected] = {
    def collectUsed(state: Ref[MemoryState]): UIO[Unit] =
      heapUsed.flatMap { bytesUsed =>
        state.update { current =>
          current.append(bytesUsed)
        }
      }

    def monitor(p: Promise[Nothing, LeakDetected], state: Ref[MemoryState]): IO[LeakDetected, Unit] =
      state.updateAndGet { current =>
        current.compact
      }.flatMap { state =>
        val bytesUsed = state.mins.last

        // minimum samples is 4
        if (state.mins.size > 3) {
          val incBy = percDiff(state.mins.take(2).min, bytesUsed)
          println(f"Heap: ${bytesUsed.fmtBytes}%12.12s total diff percent = $incBy")
          ZIO
            .when(incBy > increaseThreshold) {
              p.succeed(LeakDetected(bytesUsed, incBy))
            }
            .unit
        } else {
          println(f"Heap: ${bytesUsed.fmtBytes}%12.12s total")
          ZIO.unit
        }
      }

    ZIO.scopeWith { scope =>
      heapUsed.flatMap { usedBytes =>
        ZIO.logDebug(s"usedBytes per warmup = ${usedBytes.fmtBytes}") *> ZIO.sleep(samplePeriod)
      }.repeatN(warmupPeriods) *> heapUsed.flatMap { initialBytes =>
        println(s"heap used after warmup = ${initialBytes.fmtBytes}")
        for {
          p      <- Promise.make[Nothing, LeakDetected]
          state  <- Ref.make[MemoryState](MemoryState())
          f1     <- collectUsed(state).delay(250.millis).forever.forkIn(scope)
          f2     <- monitor(p, state).delay(samplePeriod).forever.forkIn(scope)
          result <- p.await
          _      <- f1.interruptFork
          _      <- f2.interruptFork
        } yield result
      }
    }
  }

  private lazy val gc = currentGC

  def pid: Long = ProcessHandle.current.pid

  protected def leakTest(
    name: String,
    warmupPeriods: Int = 3,
    increaseThreshold: Double = 20
  )(effect: => Task[Unit]): Unit =
    test(name) {
      println(s"start test $name, pid=$pid using GC = $gc")
      val result = unsafeRunNewRuntime(
        heapUsed.flatMap { initialBytes =>
          println(s"Initial used heap = ${initialBytes.fmtBytes}")
          ZIO.scoped {
            effect.disconnect
              .timeout(14.seconds)
              .raceEither(
                monitorHeap(
                  samplePeriod = 1.seconds,
                  warmupPeriods = warmupPeriods,
                  increaseThreshold = increaseThreshold
                )
              )
          }
        }
      )

      val leakResult = result.getOrElse(NoLeakDetected)
      assertEquals(leakResult, NoLeakDetected)
    }

}
