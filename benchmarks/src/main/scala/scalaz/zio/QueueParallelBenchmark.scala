package scalaz.zio

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import cats.effect.{ ContextShift, IO => CIO }
import org.openjdk.jmh.annotations._
import scalaz.zio.IOBenchmarks._
import scalaz.zio.stm._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(3)
/**
 * This benchmark offers and takes a number of items in parallel, without back pressure.
 */
class QueueParallelBenchmark {

  val totalSize   = 1000
  val parallelism = 5

  implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

  var zioQ: Queue[Int]                     = _
  var fs2Q: fs2.concurrent.Queue[CIO, Int] = _
  var zioTQ: TQueue[Int]                   = _

  @Setup(Level.Trial)
  def createQueues(): Unit = {
    zioQ = unsafeRun(Queue.bounded[Int](totalSize))
    fs2Q = fs2.concurrent.Queue.bounded[CIO, Int](totalSize).unsafeRunSync()
    zioTQ = unsafeRun(TQueue.make(totalSize).commit)
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
}
