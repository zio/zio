package zio.internal

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._
import zio.BenchmarkUtil.unsafeRun

/**
 * Low-yield vs high-yield scheduler throughput. Low-yield yields every 64 ops;
 * high-yield yields every op (stresses submitAndYield and cross-worker submit).
 * Run and compare baseline vs PR; use -jvmArgs -Xmx4g for large fiber counts.
 */
@Measurement(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ZSchedulerYieldBenchmark {

  @Param(Array("128", "512", "1024"))
  var fibers: Int = 0

  var lowYieldZ: ZIO[Any, Nothing, Unit]  = _
  var highYieldZ: ZIO[Any, Nothing, Unit] = _

  @Setup
  def setup(): Unit = {
    val itersPerFiber = 80
    val tasksLow = Chunk.fromIterable(
      (1 to fibers).map(_ =>
        ZIO.foreachDiscard(1 to itersPerFiber)(i => (if (i % 64 == 0) ZIO.yieldNow else ZIO.unit) *> ZIO.succeed(()))
      )
    )
    lowYieldZ = ZIO.forkAll(tasksLow).flatMap(_.join).unit

    val tasksHigh = Chunk.fromIterable(
      (1 to fibers).map(_ => ZIO.foreachDiscard(1 to itersPerFiber)(_ => ZIO.yieldNow *> ZIO.succeed(())))
    )
    highYieldZ = ZIO.forkAll(tasksHigh).flatMap(_.join).unit
  }

  @Benchmark
  def lowYield(): Unit =
    unsafeRun(lowYieldZ)

  @Benchmark
  def highYield(): Unit =
    unsafeRun(highYieldZ)
}
