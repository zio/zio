package zio.stm

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TMapBenchmarks {
  import IOBenchmarks.unsafeRun

  @Param(Array("10", "100", "1000", "10000", "100000"))
  private var size: Int = _

  private var keys: List[Int]          = _
  private var map: TMap[Int, Int]      = _
  private var ref: TRef[Map[Int, Int]] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    keys = (1 to size).toList
    map = unsafeRun(TMap.fromIterable(keys.zipWithIndex).commit)
    ref = unsafeRun(TRef.makeCommit(Map.empty[Int, Int]))
  }

  @Benchmark
  def insertion(): Unit = {
    val tx =
      for {
        map <- TMap.empty[Int, Int]
        _   <- STM.foreach(keys)(i => map.put(i, i))
      } yield ()

    unsafeRun(tx.commit)
  }

  @Benchmark
  def lookup(): List[Int] = {
    val tx = STM.foreach(keys)(map.get).map(_.flatten)
    unsafeRun(tx.commit)
  }

  @Benchmark
  def update(): List[Unit] = {
    val tx = STM.foreach(keys)(i => map.put(i, i * 2))
    unsafeRun(tx.commit)
  }

  @Benchmark
  def transform(): Unit = {
    val tx = map.transform((k, v) => (k, v * 2))
    unsafeRun(tx.commit)
  }

  @Benchmark
  def transformM(): Unit = {
    val tx = map.transformM((k, v) => STM.succeed(v * 2).map(k -> _))
    unsafeRun(tx.commit)
  }

  @Benchmark
  def removal(): List[Unit] = {
    val tx = STM.foreach(keys)(map.delete)
    unsafeRun(tx.commit)
  }

  @Benchmark
  def contentionMap(): Unit = {
    val txs = keys.map(i => map.put(i, i * 2).commit)
    unsafeRun(UIO.forkAll_(txs))
  }

  @Benchmark
  def contentionRef(): Unit = {
    val txs = keys.map(i => ref.update(_.updated(i, i * 2)).commit)
    unsafeRun(UIO.forkAll_(txs))
  }
}
