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
  private var keys: List[Int]          = _
  private var map: TMap[Int, Int]      = _
  private var ref: TRef[Map[Int, Int]] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    idx = size / 2
    keys = (1 to size).toList
    map = unsafeRun(TMap.fromIterable(keys.zipWithIndex).commit)
    ref = unsafeRun(TRef.makeCommit(Map.empty[Int, Int]))
  }

  @Benchmark
  def lookup(): Option[Int] = {
    val tx = map.get(idx)
    unsafeRun(tx.commit)
  }

  @Benchmark
  def update(): Unit = {
    val tx = map.put(idx, idx)
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
    val tx = map.delete(idx)
    unsafeRun(tx.commit)
  }

  @Benchmark
  def contentionMap(): Unit = {
    val txs = keys.map(i => map.put(i, i).commit)
    unsafeRun(UIO.forkAll_(txs))
  }

  @Benchmark
  def contentionRef(): Unit = {
    val txs = keys.map(i => ref.update(_.updated(i, i)).commit)
    unsafeRun(UIO.forkAll_(txs))
  }
}
