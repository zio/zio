package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

/**
 * Exercises `Queue.Strategy.Sliding.handleSurplus`, which only runs once the
 * queue is full: until then offers land in free space and the strategy is never
 * consulted. The queue is filled once and never taken from, so it stays full
 * for the whole trial and every measured offer slides.
 *
 * The `contended` variants run several fibers offering into the same full
 * queue, so producers race for the space each slide frees. That is the case the
 * bulk reservation is meant to help; the sequential ones are the control.
 */
@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 1)
@Warmup(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(1)
class QueueSlidingBenchmark {

  val queueSize   = 64
  val batch       = 8
  val parallelism = 4

  var zioQ: Queue[Int]             = _
  var values: List[Int]            = _
  var offerOne: UIO[Unit]          = _
  var offerMany: UIO[Unit]         = _
  var offerOneParallel: UIO[Unit]  = _
  var offerManyParallel: UIO[Unit] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    zioQ = unsafeRun(Queue.sliding[Int](queueSize))
    values = List.range(0, batch)
    offerOne = zioQ.offer(0).unit
    offerMany = zioQ.offerAll(values).unit
    offerOneParallel = ZIO.forkAll(List.fill(parallelism)(zioQ.offer(0).unit)).flatMap(_.join).unit
    offerManyParallel = ZIO.forkAll(List.fill(parallelism)(zioQ.offerAll(values).unit)).flatMap(_.join).unit
    // fill to capacity so every measured offer goes through handleSurplus;
    // nothing is ever taken, so the queue stays full for the whole trial
    unsafeRun(zioQ.offerAll(List.range(0, queueSize)).unit)
  }

  /** One value into a full sliding queue: the `Chunk.single` path. */
  @Benchmark
  def slidingOffer(): Unit =
    unsafeRun(offerOne)

  /** A batch into a full sliding queue: where bulk reservation should pay. */
  @Benchmark
  def slidingOfferAll(): Unit =
    unsafeRun(offerMany)

  /** Single-value slides racing each other for the freed space. */
  @Benchmark
  def slidingOfferContended(): Unit =
    unsafeRun(offerOneParallel)

  /** Batched slides racing each other for the freed space. */
  @Benchmark
  def slidingOfferAllContended(): Unit =
    unsafeRun(offerManyParallel)
}
