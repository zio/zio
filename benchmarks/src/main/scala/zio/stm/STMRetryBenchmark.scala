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
class STMRetryBenchmark {
  import IOBenchmarks.unsafeRun

  private var long: UIO[Unit]  = _
  private var short: UIO[Unit] = _

  private val Size = 10000

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data       = (1 to Size).toList
    val ref        = ZTRef.unsafeMake(data)
    val n          = JRuntime.getRuntime().availableProcessors() - 1
    val updateHead = ref.update(list => 0 :: list.tail).commit.forever

    short = UIO.collectAllParN_(n)(List.fill(n)(updateHead))
    long = ref.update(_.map(_ + 1)).commit
  }

  @Benchmark
  def mixedTransactions(): Unit =
    unsafeRun(long.race(short))
}
