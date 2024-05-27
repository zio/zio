package zio.internal

import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@State(Scope.Group)
@OperationsPerInvocation(1)
@Fork(2)
@Measurement(iterations = 15, time = 1, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 15, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class MailboxBenchmark {

  // CLI usage:
  // -opi 1024 -jvmArgsAppend -Dopi=1024
  val opi = Option(Integer.getInteger("opi")).fold(1)(_.intValue())

  @Param(
    Array(
      "ConcurrentLinkedQueue",
      "Mailbox",
      "MpscLinkedQueue"
    )
  )
  var name: String = _

  var q: MailboxQueue[AnyRef] = _

  def element(): AnyRef =
    new Object()

  @Setup(Level.Iteration)
  def create(): Unit =
    q = MailboxQueue(name)

  def dequeue(): Int = {
    var i = 0
    val n = opi
    while (i < n) {
      q.add(element())
      while (null == q.poll()) {}
      i += 1
    }
    i
  }

  def enqueue(): Int = {
    var i = 0
    val n = opi
    while (i < n) {
      val a = element()
      q.add(a)
      i += 1
    }
    i
  }

  @Benchmark
  @Group("p01")
  @GroupThreads(1)
  def dequeue_01() = dequeue()

  @Benchmark
  @Group("p01")
  @GroupThreads(1)
  def enqueue_01() = enqueue()

  @Benchmark
  @Group("p02")
  @GroupThreads(1)
  def dequeue_02() = dequeue()

  @Benchmark
  @Group("p02")
  @GroupThreads(2)
  def enqueue_02() = enqueue()

  @Benchmark
  @Group("p03")
  @GroupThreads(1)
  def dequeue_03() = dequeue()

  @Benchmark
  @Group("p03")
  @GroupThreads(2)
  def enqueue_03() = enqueue()

  @Benchmark
  @Group("dequeue")
  @GroupThreads(1)
  def empty() = {
    var i = 0
    val n = opi
    while (i < n) {
      q.poll()
      i += 1
    }
    i
  }
}
