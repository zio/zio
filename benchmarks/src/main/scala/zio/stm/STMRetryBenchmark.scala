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

  private var updates: List[UIO[Unit]] = _

  private val Size        = 10000
  private val Parallelism = JRuntime.getRuntime().availableProcessors()

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data     = (1 to Size).toList
    val ref      = TRef.unsafeMake(data)
    val schedule = Schedule.recurs(1000).unit

    val update = ref.update(_.map(_ + 1)).commit.repeat(schedule)

    updates = List.fill(Parallelism)(update)
  }

  @Benchmark
  def concurrentLongTransactions(): Unit =
    unsafeRun(UIO.collectAllParN_(Parallelism)(updates))
}
