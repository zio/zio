package zio

import cats.effect.{ContextShift, IO => CIO}
import monix.eval.{Task => MTask}
import org.openjdk.jmh.annotations._
import zio.IOBenchmarks._
import zio.stm._

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(3)
/**
 * This benchmark offers number of items in parallel and takes item from single fiber, without back pressure.
 */
class MPSCQueueParallelBenchmark {

  val totalSize   = 1000
  val parallelism = 5

  implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

  var zioQ: Queue[Int] = _
  var mpscQ: MPSCQueue[Int] = _

  @Setup(Level.Trial)
  def createQueues(): Unit = {
    zioQ = unsafeRun(Queue.unbounded[Int])
    mpscQ = unsafeRun(MPSCQueue.make[Int])
  }

  @Benchmark
  def zioQueue(): Int = {

    val io = for {
      offers <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(zioQ.offer(0).unit)))
      takes  <- repeat(totalSize)(zioQ.take.unit).fork
      _      <- offers.join
      _      <- takes.join
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def mpscQueue(): Int = {

    val io = for {
      offers <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(mpscQ.offer(0).unit)))
      takes  <- repeat(totalSize)(mpscQ.take.unit).fork
      _      <- offers.join
      _      <- takes.join
    } yield 0

    unsafeRun(io)
  }
}
