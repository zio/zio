package zio.internal

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{Blackhole, Control}

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

/**
 * Single-threaded steady-state microbenchmark for the fiber mailbox
 * replacement (issue https://github.com/zio/zio/issues/8807).
 *
 * Models a fiber that receives a small batch of messages, drains them, and
 * yields. This is the dominant pattern observed in
 * [[zio.internal.FiberRuntime]]'s `inbox` usage.
 *
 * Run with:
 * {{{
 *   benchmarks/Jmh/run -i 10 -wi 10 -f 2 zio.internal.FiberMailboxSeqBenchmark
 * }}}
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
class FiberMailboxSeqBenchmark {
  // Single payload reused across operations: keeps the benchmark focused on
  // the queue's own overhead and out of the allocator path.
  val payload: AnyRef = new Object

  @Param(Array("1", "2", "4"))
  var batchSize: Int = _

  @Param(Array("CLQ", "FiberMailbox"))
  var queueType: String = _

  var clq: ConcurrentLinkedQueue[AnyRef] = _
  var mbox: FiberMailbox[AnyRef]         = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    clq = new ConcurrentLinkedQueue[AnyRef]()
    mbox = new FiberMailbox[AnyRef]()
  }

  @Benchmark
  def offerAndPoll(blackhole: Blackhole): Unit =
    queueType match {
      case "CLQ" =>
        var i = 0
        while (i < batchSize) { clq.add(payload); i += 1 }
        i = 0
        while (i < batchSize) { blackhole.consume(clq.poll()); i += 1 }
      case "FiberMailbox" =>
        var i = 0
        while (i < batchSize) { mbox.add(payload); i += 1 }
        i = 0
        while (i < batchSize) { blackhole.consume(mbox.poll()); i += 1 }
      case other =>
        sys.error(s"Unknown queue type: $other")
    }
}

/**
 * Multi-producer / single-consumer microbenchmark for the fiber mailbox
 * replacement.
 *
 * Four producer threads call `add` concurrently; a single consumer thread
 * polls. Models the high-contention case (many fibers signalling a single
 * fiber).
 *
 * Run with:
 * {{{
 *   benchmarks/Jmh/run -i 10 -wi 10 -f 2 zio.internal.FiberMailboxMpscBenchmark
 * }}}
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Group)
class FiberMailboxMpscBenchmark {
  val payload: AnyRef = new Object

  @Param(Array("CLQ", "FiberMailbox"))
  var queueType: String = _

  var clq: ConcurrentLinkedQueue[AnyRef] = _
  var mbox: FiberMailbox[AnyRef]         = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    clq = new ConcurrentLinkedQueue[AnyRef]()
    mbox = new FiberMailbox[AnyRef]()
  }

  @Benchmark
  @Group("mpsc4")
  @GroupThreads(4)
  def producer(): Unit =
    queueType match {
      case "CLQ"          => clq.add(payload)
      case "FiberMailbox" => mbox.add(payload)
      case other          => sys.error(s"Unknown queue type: $other")
    }

  @Benchmark
  @Group("mpsc4")
  @GroupThreads(1)
  def consumer(control: Control, blackhole: Blackhole): Unit =
    queueType match {
      case "CLQ" =>
        var v = clq.poll()
        while ((v eq null) && !control.stopMeasurement) v = clq.poll()
        if (v ne null) blackhole.consume(v)
      case "FiberMailbox" =>
        var v = mbox.poll()
        while ((v eq null) && !control.stopMeasurement) v = mbox.poll()
        if (v ne null) blackhole.consume(v)
      case other => sys.error(s"Unknown queue type: $other")
    }
}
