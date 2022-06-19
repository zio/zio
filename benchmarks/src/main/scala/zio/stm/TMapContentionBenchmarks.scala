package zio.stm

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(1)
class TMapContentionBenchmarks {
  import BenchmarkUtil.unsafeRun

  @Param(Array("100", "1000", "10000"))
  var repeatedUpdates: Int = _

  private var mapUpdates: UIO[Unit] = _
  private var refUpdates: UIO[Unit] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val keysToUpdate = (1 to 100).toList
    val data         = (1 to 1000).toList.zipWithIndex
    val map          = Unsafe.unsafeCompat(implicit u => unsafeRun(TMap.fromIterable(data).commit))
    val ref          = TRef.unsafeMake(data.toMap)

    mapUpdates = ZIO.foreachParDiscard(keysToUpdate)(i => map.put(i, i).commit.repeatN(repeatedUpdates))
    refUpdates = ZIO.foreachParDiscard(keysToUpdate)(i => ref.update(_.updated(i, i)).commit.repeatN(repeatedUpdates))
  }

  @Benchmark
  def contentionMap(): Unit =
    unsafeRun(mapUpdates)

  @Benchmark
  def contentionRef(): Unit =
    unsafeRun(refUpdates)
}
