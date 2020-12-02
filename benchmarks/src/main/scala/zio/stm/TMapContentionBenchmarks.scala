package zio.stm

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio._
import zio.clock.Clock

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(1)
class TMapContentionBenchmarks {
  import IOBenchmarks.unsafeRun

  @Param(Array("100", "1000", "10000"))
  var repeatedUpdates: Int = _

  private var mapUpdates: URIO[Clock, Unit] = _
  private var refUpdates: URIO[Clock, Unit] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val keysToUpdate = (1 to 100).toList
    val data         = (1 to 1000).toList.zipWithIndex
    val map          = unsafeRun(TMap.fromIterable(data).commit)
    val ref          = ZTRef.unsafeMake(data.toMap)

    mapUpdates = ZIO.foreachPar_(keysToUpdate)(i => map.put(i, i).commit.repeatN(repeatedUpdates))
    refUpdates = ZIO.foreachPar_(keysToUpdate)(i => ref.update(_.updated(i, i)).commit.repeatN(repeatedUpdates))
  }

  @Benchmark
  def contentionMap(): Unit =
    unsafeRun(mapUpdates)

  @Benchmark
  def contentionRef(): Unit =
    unsafeRun(refUpdates)
}
