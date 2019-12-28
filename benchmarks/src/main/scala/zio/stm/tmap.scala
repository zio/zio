package zio.stm

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TMapOpsBenchmarks {
  import IOBenchmarks.unsafeRun

  @Param(Array("0", "10", "100", "1000", "10000", "100000"))
  private var size: Int = _

  private var idx: Int            = _
  private var map: TMap[Int, Int] = _

  // used to ammortize the relative cost of unsafeRun
  // compared to benchmarked operations
  private val calls = (0 to 500).toList

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data = (1 to size).toList.zipWithIndex

    idx = size / 2
    map = unsafeRun(TMap.fromIterable(data).commit)
  }

  @Benchmark
  def lookup(): Unit =
    unsafeRun(ZIO.foreach_(calls)(_ => map.get(idx).commit))

  @Benchmark
  def update(): Unit =
    unsafeRun(ZIO.foreach_(calls)(_ => map.put(idx, idx).commit))

  @Benchmark
  def transform(): Unit =
    unsafeRun(map.transform((k, v) => (k, v)).commit)

  @Benchmark
  def transformM(): Unit =
    unsafeRun(map.transformM((k, v) => STM.succeed(v).map(k -> _)).commit)

  @Benchmark
  def removal(): Unit =
    unsafeRun(ZIO.foreach_(calls)(_ => map.delete(idx).commit))
}

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TMapContentionBenchmarks {
  import IOBenchmarks.unsafeRun

  @Param(Array("0", "10", "100", "1000", "10000", "100000"))
  private var size: Int = _

  private var mapUpdates: List[UIO[Int]] = _
  private var refUpdates: List[UIO[Int]] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data     = (1 to size).toList.zipWithIndex
    val map      = unsafeRun(TMap.fromIterable(data).commit)
    val ref      = TRef.unsafeMake(data.toMap)
    val schedule = Schedule.recurs(1000)
    val updates  = (1 to 100).toList

    mapUpdates = updates.map(i => map.put(i, i).commit.repeat(schedule))
    refUpdates = updates.map(i => ref.update(_.updated(i, i)).commit.repeat(schedule))
  }

  @Benchmark
  def contentionMap(): Unit =
    unsafeRun(UIO.forkAll_(mapUpdates))

  @Benchmark
  def contentionRef(): Unit =
    unsafeRun(UIO.forkAll_(refUpdates))
}
