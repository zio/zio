package zio.internal

import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

/**
 * Defines a benchmark to measure the performance of dequeue operation of MPSC
 * queues in the absence of concurrent operations.
 */
@BenchmarkMode(Array(Mode.AverageTime))
@Fork(1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
class MailboxPollBenchmark {

  @Param(Array("0", "1000000"))
  var fill: Int = _

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
  def setup() = {
    q = MailboxWrapper(queue)
    val element = new Object()
    for (_ <- 0 to fill) q.add(element)
  }

  @Benchmark
  def poll() =
    q.poll()
}
