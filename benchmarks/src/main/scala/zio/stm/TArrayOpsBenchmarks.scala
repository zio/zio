package zio.stm

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
class TArrayOpsBenchmarks {
  import BenchmarkUtil.unsafeRun

  @Param(Array("10", "100", "1000", "10000", "100000"))
  var size: Int = _

  private var idx: Int           = _
  private var array: TArray[Int] = _

  // used to amortize the relative cost of unsafeRun
  // compared to benchmarked operations
  private val calls = List.fill(500)(false)

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data = (1 to size).toList
    idx = size / 2
    array = Unsafe.unsafeCompat(implicit u => unsafeRun(TArray.fromIterable(data).commit))
  }

  @Benchmark
  def lookup(): Unit =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(ZIO.foreachDiscard(calls)(_ => array(idx).commit))
    }

  @Benchmark
  def find(): Option[Int] =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.find(_ == size).commit)
    }

  @Benchmark
  def findSTM(): Option[Int] =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.findSTM(a => STM.succeedNow(a == size)).commit)
    }

  @Benchmark
  def fold(): Int =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.fold(0)(_ + _).commit)
    }

  @Benchmark
  def foldSTM(): Int =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.foldSTM(0)((acc, e) => STM.succeedNow(acc + e)).commit)
    }

  @Benchmark
  def indexWhere(): Int =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.indexWhere(_ == size).commit)
    }

  @Benchmark
  def indexWhereSTM(): Int =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.indexWhereSTM(a => STM.succeedNow(a == size)).commit)
    }

  @Benchmark
  def reduceOption(): Option[Int] =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.reduceOption(_ + _).commit)
    }

  @Benchmark
  def reduceOptionSTM(): Option[Int] =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.reduceOptionSTM((a, b) => STM.succeedNow(a + b)).commit)
    }

  @Benchmark
  def transform(): Unit =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.transform(_ + 1).commit)
    }

  @Benchmark
  def transformSTM(): Unit =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(array.transformSTM(i => STM.succeedNow(i + 1)).commit)
    }

  @Benchmark
  def update(): Unit =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(ZIO.foreachDiscard(calls)(_ => array.update(idx, _ + 1).commit))
    }

  @Benchmark
  def updateSTM(): Unit =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(ZIO.foreachDiscard(calls)(_ => array.updateSTM(idx, i => STM.succeedNow(i + 1)).commit))
    }
}
