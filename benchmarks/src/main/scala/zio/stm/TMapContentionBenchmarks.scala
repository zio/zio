package zio.stm

import java.lang.{ Runtime => JRuntime }
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(1)
class TMapContentionBenchmarks {
  import IOBenchmarks.unsafeRun

  @Param(Array("0", "10", "100", "1000", "10000", "100000"))
  var size: Int = _

  private var mapUpdates: List[UIO[Int]] = _
  private var refUpdates: List[UIO[Int]] = _

  private val parallelism = JRuntime.getRuntime().availableProcessors()

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data     = (1 to size).toList.zipWithIndex
    val map      = unsafeRun(TMap.fromIterable(data).commit)
    val ref      = ZTRef.unsafeMake(data.toMap)
    val schedule = Schedule.recurs(1000)
    val updates  = (1 to 100).toList

    mapUpdates = updates.map(i => map.put(i, i).commit.repeat(schedule))
    refUpdates = updates.map(i => ref.update(_.updated(i, i)).commit.repeat(schedule))
  }

  @Benchmark
  def contentionMap(): Unit =
    unsafeRun(UIO.collectAllParN_(parallelism)(mapUpdates))

  @Benchmark
  def contentionRef(): Unit =
    unsafeRun(UIO.collectAllParN_(parallelism)(refUpdates))
}
