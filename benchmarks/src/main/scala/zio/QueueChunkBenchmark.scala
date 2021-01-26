package zio

import org.openjdk.jmh.annotations._
import zio.IOBenchmarks._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
/**
 * This benchmark offers and takes chunks of items.
 */
class QueueChunkBenchmark {

  val totalSize   = 1000
  val chunkSize   = 10
  val parallelism = 5

  val chunk: List[Int] = List.fill(chunkSize)(0)

  var queue: Queue[Int] = _

  var offerChunks: List[UIO[Any]] = _
  var takeChunks: List[UIO[Any]]  = _

  var offers: List[UIO[Any]] = _
  var takes: List[UIO[Any]]  = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    queue = unsafeRun(Queue.bounded[Int](totalSize))
    offerChunks = List.fill(parallelism) {
      repeat(totalSize * 1 / chunkSize * 1 / parallelism)(queue.offerAll(chunk))
    }
    takeChunks = List.fill(parallelism) {
      repeat(totalSize * 1 / chunkSize * 1 / parallelism)(queue.takeBetween(chunkSize, chunkSize))
    }
    offers = List.fill(parallelism) {
      repeat(totalSize * 1 / parallelism)(queue.offer(0))
    }
    takes = List.fill(parallelism) {
      repeat(totalSize * 1 / parallelism)(queue.take)
    }
  }

  @Benchmark
  def parallel(): Int = {

    val io = for {
      offers <- ZIO.forkAll(offers)
      takes  <- ZIO.forkAll(takes)
      _      <- offers.join
      _      <- takes.join
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def parallelChunked(): Int = {

    val io = for {
      offers <- ZIO.forkAll(offerChunks)
      takes  <- ZIO.forkAll(takeChunks)
      _      <- offers.join
      _      <- takes.join
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def sequential(): Int = {

    val io = for {
      _ <- repeat(totalSize)(queue.offer(0))
      _ <- repeat(totalSize)(queue.take)
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def sequentialChunked(): Int = {

    val io = for {
      _ <- repeat(totalSize / chunkSize)(queue.offerAll(chunk))
      _ <- repeat(totalSize / chunkSize)(queue.takeBetween(chunkSize, chunkSize))
    } yield 0

    unsafeRun(io)
  }
}
