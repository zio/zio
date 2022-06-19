package zio

import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._
import zio.stm._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(3)
/**
 * This benchmark offers and takes a number of items in parallel, without back
 * pressure.
 */
class QueueParallelBenchmark {

  val totalSize   = 1000
  val parallelism = 5

  var zioQ: Queue[Int]                      = _
  var fs2Q: cats.effect.std.Queue[CIO, Int] = _
  var zioTQ: TQueue[Int]                    = _

  @Setup(Level.Trial)
  def createQueues(): Unit = Unsafe.unsafeCompat { implicit u =>
    zioQ = unsafeRun(Queue.bounded[Int](totalSize))
    fs2Q = cats.effect.std.Queue.bounded[CIO, Int](totalSize).unsafeRunSync()
    zioTQ = unsafeRun(TQueue.bounded(totalSize).commit)
  }

  @Benchmark
  def zioQueue(): Int = {

    val io = for {
      offers <- ZIO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(zioQ.offer(0).unit)))
      takes  <- ZIO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(zioQ.take.unit)))
      _      <- offers.join
      _      <- takes.join
    } yield 0

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(io)
    }
  }

  @Benchmark
  def zioTQueue(): Int = {

    val io = for {
      offers <- ZIO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(zioTQ.offer(0).unit.commit)))
      takes  <- ZIO.forkAll(List.fill(parallelism)(repeat(totalSize / parallelism)(zioTQ.take.unit.commit)))
      _      <- offers.join
      _      <- takes.join
    } yield 0

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(io)
    }
  }

  @Benchmark
  def fs2Queue(): Int = {

    val io = for {
      offers <- catsForkAll(List.fill(parallelism)(catsRepeat(totalSize / parallelism)(fs2Q.offer(0))))
      takes  <- catsForkAll(List.fill(parallelism)(catsRepeat(totalSize / parallelism)(fs2Q.take.void)))
      _      <- offers.join
      _      <- takes.join
    } yield 0

    io.unsafeRunSync()
  }
}
