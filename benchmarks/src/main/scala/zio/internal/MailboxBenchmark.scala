package zio.internal

import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

/**
 * Defines benchmarks to measure the relative performance of MPSC queues in the
 * presence of concurrent operations. The benchmarks are organized into groups
 * where each group consists of one or more producer threads that enqueue an
 * element and a consumer thread that dequeues all elements.
 *
 * @note
 *   A `SingleShotTime` mode with a `batchSize` is used because
 *   - a precise number of elements need to be dequeued
 *   - the operation does not have a steady state
 *   - the operation measurement is too small
 */
@BenchmarkMode(Array(Mode.SingleShotTime))
@Fork(1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Group)
@Measurement(batchSize = 100000, iterations = 10)
@Warmup(batchSize = 100000, iterations = 10)
class MailboxBenchmark {

  @Param(
    Array(
      "ConcurrentLinkedQueue",
      "Mailbox",
      "MpscLinkedQueue"
    )
  )
  var queue: String = _

  var q: MailboxWrapper[AnyRef] = _

  @Setup(Level.Iteration)
  def setup(): Unit =
    q = MailboxWrapper(queue)

  def add() = {
    val a = new Object()
    q.add(a)
    a
  }

  def poll(n: Int) = {
    var i = 0
    while (i < n) {
      while (null == q.poll()) {}
      i += 1
    }
    i
  }

  @Benchmark
  @Group("p01")
  @GroupThreads(1)
  def add_01() =
    add()

  @Benchmark
  @Group("p01")
  @GroupThreads(1)
  def poll_01() =
    poll(1)

  @Benchmark
  @Group("p02")
  @GroupThreads(2)
  def add_02() =
    add()

  @Benchmark
  @Group("p02")
  @GroupThreads(1)
  def poll_02() =
    poll(2)

  @Benchmark
  @Group("p03")
  @GroupThreads(3)
  def add_03() =
    add()

  @Benchmark
  @Group("p03")
  @GroupThreads(1)
  def poll_03() =
    poll(3)

  @Benchmark
  @Group("p07")
  @GroupThreads(7)
  def add_07() =
    add()

  @Benchmark
  @Group("p07")
  @GroupThreads(1)
  def poll_07() =
    poll(7)
}
