package zio.stm

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TMapBenchmarks {
  import IOBenchmarks.unsafeRun

  @Param(Array("0", "10", "100", "1000", "10000", "100000"))
  private var size: Int = _

  private var idx: Int                 = _
  private var map: TMap[Int, Int]      = _
  private var ref: TRef[Map[Int, Int]] = _

  // used to ammortize the relative cost of unsafeRun
  // compared to benchmarked operations
  private val invocations = (0 to 500).toList

  // used to benchmark performace under heavy contention
  private var mapUpdates: List[UIO[Int]] = _
  private var refUpdates: List[UIO[Int]] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data = (1 to size).toList.zipWithIndex

    idx = size / 2
    map = unsafeRun(TMap.fromIterable(data).commit)
    ref = unsafeRun(TRef.makeCommit(data.toMap))

    val schedule = Schedule.recurs(1000)
    val updates  = (1 to 100).toList

    mapUpdates = updates.map(i => map.put(i, i).commit.repeat(schedule))
    refUpdates = updates.map(i => ref.update(_.updated(i, i)).commit.repeat(schedule))
  }

  @Benchmark
  def contentionMap(): Unit = {
    val forked = UIO.forkAll_(mapUpdates)
    unsafeRun(forked)
  }

  @Benchmark
  def contentionRef(): Unit = {
    val forked = UIO.forkAll_(refUpdates)
    unsafeRun(forked)
  }

  @Benchmark
  def lookup(): Unit = {
    val tx = STM.foreach_(invocations)(_ => map.get(idx))
    unsafeRun(tx.commit)
  }

  @Benchmark
  def update(): Unit = {
    val tx = STM.foreach_(invocations)(_ => map.put(idx, idx))
    unsafeRun(tx.commit)
  }

  @Benchmark
  def transform(): Unit = {
    val tx = map.transform((k, v) => (k, v))
    unsafeRun(tx.commit)
  }

  @Benchmark
  def transformM(): Unit = {
    val tx = map.transformM((k, v) => STM.succeed(v).map(k -> _))
    unsafeRun(tx.commit)
  }

  @Benchmark
  def removal(): Unit = {
    val tx = STM.foreach_(invocations)(_ => map.delete(idx))
    unsafeRun(tx.commit)
  }
}
