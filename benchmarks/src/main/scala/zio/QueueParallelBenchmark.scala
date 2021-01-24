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
 * This benchmark offers and takes a number of items in parallel, without back pressure.
 */
class QueueParallelBenchmark {

  val totalSize   = 1000
  val parallelism = 5

  implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

  var zioQ: Queue[Int]                                 = _
  var fs2Q: fs2.concurrent.Queue[CIO, Int]             = _
  var zioTQ: TQueue[Int]                               = _
  var monixQ: monix.catnap.ConcurrentQueue[MTask, Int] = _

  @Setup(Level.Trial)
  def createQueues(): Unit = {
    zioQ = unsafeRun(Queue.bounded[Int](totalSize))
    fs2Q = fs2.concurrent.Queue.bounded[CIO, Int](totalSize).unsafeRunSync()
    zioTQ = unsafeRun(TQueue.bounded(totalSize).commit)
    monixQ = monix.catnap.ConcurrentQueue.bounded[MTask, Int](totalSize).runSyncUnsafe()
  }

  @Benchmark
  def zioQueue(): Int = {

    val io = for {
      offers <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(zioQ.offer(0).unit)))
      takes  <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(zioQ.take.unit)))
      _      <- offers.join
      _      <- takes.join
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def zioTQueue(): Int = {

    val io = for {
      offers <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(zioTQ.offer(0).unit.commit)))
      takes  <- IO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(zioTQ.take.unit.commit)))
      _      <- offers.join
      _      <- takes.join
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def fs2Queue(): Int = {

    val io = for {
      offers <- catsForkAll(List.fill(parallelism)(catsRepeat(totalSize / parallelism)(fs2Q.enqueue1(0))))
      takes  <- catsForkAll(List.fill(parallelism)(catsRepeat(totalSize / parallelism)(fs2Q.dequeue1.map(_ => ()))))
      _      <- offers.join
      _      <- takes.join
    } yield 0

    io.unsafeRunSync()
  }

  @Benchmark
  def monixQueue(): Int = {
    import IOBenchmarks.monixScheduler

    val io = for {
      offers <- monixForkAll(List.fill(parallelism)(monixRepeat(totalSize / parallelism)(monixQ.offer(0))))
      takes  <- monixForkAll(List.fill(parallelism)(monixRepeat(totalSize / parallelism)(monixQ.poll.map(_ => ()))))
      _      <- offers.join
      _      <- takes.join
    } yield 0

    io.runSyncUnsafe()
  }
}
