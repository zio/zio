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
class TMapOpsBenchmarks {
  import BenchmarkUtil.unsafeRun

  @Param(Array("0", "10", "100", "1000", "10000", "100000"))
  var size: Int = _

  private var idx: Int            = _
  private var map: TMap[Int, Int] = _

  // used to amortize the relative cost of unsafeRun
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
    unsafeRun(ZIO.foreachDiscard(calls)(_ => map.get(idx).commit))

  @Benchmark
  def update(): Unit =
    unsafeRun(ZIO.foreachDiscard(calls)(_ => map.put(idx, idx).commit))

  @Benchmark
  def transform(): Unit =
    unsafeRun(map.transform((k, v) => (k, v)).commit)

  @Benchmark
  def transformSTM(): Unit =
    unsafeRun(map.transformSTM((k, v) => STM.succeedNow(v).map(k -> _)).commit)

  @Benchmark
  def removal(): Unit =
    unsafeRun(ZIO.foreachDiscard(calls)(_ => map.delete(idx).commit))

  @Benchmark
  def fold(): Int =
    unsafeRun(map.fold(0)((acc, kv) => acc + kv._2).commit)

  @Benchmark
  def foldSTM(): Int =
    unsafeRun(map.foldSTM[Any, Nothing, Int](0)((acc, kv) => STM.succeedNow(acc + kv._2)).commit)

}
