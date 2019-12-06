package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio.IOBenchmarks._
import zio.test.RandomExecutor

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TestRuntimeBenchmark {

  val totalSize   = 1000
  val parallelism = 5

  @Benchmark
  def runInDefaultRuntime(): Int =
    unsafeRun(io.repeat(Schedule.recurs(100)))

  @Benchmark
  def analyseInDefaultRuntime(): Unit =
    unsafeRun(RandomExecutor.paths(io).forever.take(100).runDrain)

  @Benchmark
  def runInSyncRuntime(): Int =
    SyncRuntime.unsafeRun(io.repeat(Schedule.recurs(100)))

  @Benchmark
  def analyseInSyncRuntime(): Unit =
    SyncRuntime.unsafeRun(RandomExecutor.paths(io).forever.take(100).runDrain)

  val io: ZIO[Any, Nothing, Int] = for {
    queue  <- Queue.bounded[Int](totalSize)
    offers <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(queue.offer(0).unit)))
    takes  <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(queue.take.unit)))
    _      <- offers.join
    _      <- takes.join
  } yield 0
}
