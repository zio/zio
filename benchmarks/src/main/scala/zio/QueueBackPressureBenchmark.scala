package zio

import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext

import cats.effect.{ ContextShift, IO => CIO }
import monix.eval.{ Task => MTask }
import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._
import zio.stm._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(3)
/**
 * This benchmark offers and takes a number of items in parallel, with a very small queue to enforce back pressure mechanism is used.
 */
class QueueBackPressureBenchmark {
  val queueSize   = 2
  val totalSize   = 1000
  val parallelism = 5

  implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

  var zioQ: Queue[Int]                                 = _
  var fs2Q: fs2.concurrent.Queue[CIO, Int]             = _
  var zioTQ: TQueue[Int]                               = _
  var monixQ: monix.catnap.ConcurrentQueue[MTask, Int] = _

  @Setup(Level.Trial)
  def createQueues(): Unit = {
    zioQ = unsafeRun(Queue.bounded[Int](queueSize))
    fs2Q = fs2.concurrent.Queue.bounded[CIO, Int](queueSize).unsafeRunSync()
    zioTQ = unsafeRun(TQueue.bounded(queueSize).commit)
    monixQ = monix.catnap.ConcurrentQueue.bounded[MTask, Int](queueSize).runSyncUnsafe()
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
