package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio.internal.{ Executor, PlatformLive }
import zio.IOBenchmarks.unsafeRun
import zio.IOBenchmarks.repeat
import zio.test.RandomExecutor

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class RandomExecutorBenchmark {

  val totalSize   = 1000
  val parallelism = 5
  val times       = 100

  @Benchmark
  def runInDefaultRuntime(): Int =
    unsafeRun(io.run.repeat(hundredTimes))

  @Benchmark
  def RandomExecutorInDefaultRuntime(): Int =
    unsafeRun(RandomExecutor.run(io).repeat(hundredTimes))

  @Benchmark
  def runInSyncRuntime(): Int =
    SyncRuntime.unsafeRun(io.run.repeat(hundredTimes))

  @Benchmark
  def RandomExecutorInSyncRuntime(): Int =
    SyncRuntime.unsafeRun(RandomExecutor.run(io).repeat(hundredTimes))

  private def hundredTimes = Schedule.recurs(times)

  val io: ZIO[Any, Nothing, Int] = for {
    queue  <- Queue.bounded[Int](totalSize)
    offers <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(queue.offer(0).unit)))
    takes  <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(queue.take.unit)))
    _      <- offers.join
    _      <- takes.join
  } yield 0

  object SyncRuntime extends DefaultRuntime {
    override val platform = PlatformLive.Benchmark.withExecutor(new Executor {

      /**
       * The number of operations a fiber should run before yielding.
       */
      override def yieldOpCount: Int = Int.MaxValue

      /**
       * Current sampled execution metrics, if available.
       */
      override def metrics: Option[Nothing] = None

      /**
       * Submits an effect for execution.
       */
      override def submit(runnable: Runnable): Boolean = {
        runnable.run()
        true
      }

      /**
       * Whether or not the caller is being run on this executor.
       */
      override def here: Boolean = true
    })
  }
}
