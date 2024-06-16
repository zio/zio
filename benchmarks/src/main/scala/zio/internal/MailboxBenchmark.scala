package zio.internal

import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

/**
 * Defines asymetric benchmarks to measure the relative performance of MPSC
 * queue transit.
 *
 * The benchmarks are organized into groups where each group consists of one or
 * more producer threads that enqueue an element and a consumer thread that
 * dequeues _all_ elements and checks if the queue is empty.
 *
 * The total number of elements transiting the queue in an iteration is
 * `number_of_producers * batch_size * operations_per_invocation` and can be
 * configured with
 * {{{
 * -bs 100000 -wbs 100000 -opi 1 -jvmArgsAppend -Dopi=1
 * }}}
 *
 * In an ideal system, one that scales linearly with the number of threads, the
 * measurement for all groups is expected to be the same.
 *
 * A `SingleShotTime` mode is used because
 *   - a precise number of elements need to be dequeued
 *   - the operation does not have a steady state
 *   - the operation measurement is too small *
 */
@BenchmarkMode(Array(Mode.SingleShotTime))
@Fork(1)
@OperationsPerInvocation(1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Group)
@Measurement(batchSize = 100000, iterations = 20)
@Warmup(batchSize = 100000, iterations = 20)
class MailboxBenchmark {

  // general recommendation is to avoid loops in benchmarks
  val opi = Option(Integer.getInteger("opi")).fold(1)(_.intValue())

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
    var i = 0
    val n = opi
    while (i < n) {
      val a = new Object()
      q.add(a)
      i += 1
    }
    i
  }

  def poll(producers: Int) = {
    var i = 0
    val n = producers * opi
    while (i < n) {
      while (null == q.poll()) {}
      i += 1
    }
    q.isEmpty()
  }

  @Benchmark
  @Group("g01PSC")
  @GroupThreads(1)
  def add_01() =
    add()

  @Benchmark
  @Group("g01PSC")
  @GroupThreads(1)
  def poll_01() =
    poll(1)

  @Benchmark
  @Group("g02PSC")
  @GroupThreads(2)
  def add_02() =
    add()

  @Benchmark
  @Group("g02PSC")
  @GroupThreads(1)
  def poll_02() =
    poll(2)

  @Benchmark
  @Group("g04PSC")
  @GroupThreads(4)
  def add_04() =
    add()

  @Benchmark
  @Group("g04PSC")
  @GroupThreads(1)
  def poll_04() =
    poll(4)

  @Benchmark
  @Group("g08PSC")
  @GroupThreads(8)
  def add_08() =
    add()

  @Benchmark
  @Group("g08PSC")
  @GroupThreads(1)
  def poll_08() =
    poll(8)

  @Benchmark
  @Group("g16PSC")
  @GroupThreads(16)
  def add_16() =
    add()

  @Benchmark
  @Group("g16PSC")
  @GroupThreads(1)
  def poll_16() =
    poll(16)
}
