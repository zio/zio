package zio.stm

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
class TArrayOpsBenchmarks {
  import IOBenchmarks.unsafeRun

  @Param(Array("0", "10", "100", "1000", "10000", "100000"))
  var size: Int = _

  private var idx: Int           = _
  private var array: TArray[Int] = _

  // used to ammortize the relative cost of unsafeRun
  // compared to benchmarked operations
  private val calls = List.fill(500)(false)

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data = (1 to size).toList
    idx = size / 2
    array = unsafeRun(TArray.fromIterable(data).commit)
  }

  @Benchmark
  def lookup(): Unit =
    unsafeRun(ZIO.foreach_(calls)(_ => array(idx).commit))

  @Benchmark
  def find(): Option[Int] =
    unsafeRun(array.find(_ == (size - 1)).commit)

  @Benchmark
  def findM(): Option[Int] =
    unsafeRun(array.findM(i => STM.succeedNow(i == (size - 1))).commit)

  @Benchmark
  def fold(): Int =
    unsafeRun(array.fold(0)(_ + _).commit)

  @Benchmark
  def foldM(): Int =
    unsafeRun(array.foldM(0)((acc, e) => STM.succeedNow(acc + e)).commit)

  @Benchmark
  def transform(): Unit =
    unsafeRun(array.transform(_ + 1).commit)

  @Benchmark
  def transformM(): Unit =
    unsafeRun(array.transformM(i => STM.succeedNow(i + 1)).commit)

  @Benchmark
  def update(): Unit =
    unsafeRun(ZIO.foreach_(calls)(_ => array.update(idx, _ + 1).commit))

  @Benchmark
  def updateM(): Unit =
    unsafeRun(ZIO.foreach_(calls)(_ => array.updateM(idx, i => STM.succeedNow(i + 1)).commit))
}
