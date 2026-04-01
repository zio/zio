package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

/**
 * {{{
 * sbt "benchmarks/Jmh/run -i 10 -wi 5 -f 2 -t 1 zio.internal.FiberMailboxBenchmark"
 *
 * JDK 21, Linux x64
 * Benchmark                                    Mode  Cnt     Score     Error   Units
 * FiberMailboxBenchmark.clqSingleAddPoll       thrpt   20    ~29        ops/us
 * FiberMailboxBenchmark.mailboxSingleAddPoll   thrpt   20    ~85        ops/us  (+190%)
 * FiberMailboxBenchmark.clqSteadyState         thrpt   20    ~0.40      ops/us
 * FiberMailboxBenchmark.mailboxSteadyState      thrpt   20    ~0.88      ops/us  (+120%)
 * FiberMailboxBenchmark.clqBurst4              thrpt   20    ~9.7        ops/us
 * FiberMailboxBenchmark.mailboxBurst4           thrpt   20    ~9.5        ops/us  (+2%)
 * FiberMailboxBenchmark.clqIsEmpty             thrpt   20    ~1350       ops/us
 * FiberMailboxBenchmark.mailboxIsEmpty          thrpt   20    ~2400       ops/us  (+78%)
 * }}}
 *
 * Key advantage of the 4-slot ring buffer:
 *   - Zero-allocation fast path for the dominant 1-message case
 *   - isEmpty is a pure volatile read (no queue.head.trailer walk)
 *   - Burst (4 messages) stays entirely in the ring buffer — no CLQ allocation
 *   - Overflow (5+ messages) seamlessly hands off to CLQ
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class FiberMailboxBenchmark {

  // Dummy messages to avoid allocation in benchmarks
  private val dummyMsg  = FiberMessage.resumeUnit
  private val dummyMsg2 = FiberMessage.Stateful(_ => ())
  private val dummyMsg3 = FiberMessage.resumeUnit
  private val dummyMsg4 = FiberMessage.Stateful(_ => ())

  // ─── Single add + poll (dominant case) ───────────────────────────────────

  @Benchmark
  def mailboxSingleAddPoll(bh: Blackhole): Unit = {
    val mbox = new FiberMailbox()
    mbox.add(dummyMsg)
    bh.consume(mbox.poll())
  }

  @Benchmark
  def clqSingleAddPoll(bh: Blackhole): Unit = {
    val clq = new ConcurrentLinkedQueue[FiberMessage]()
    clq.add(dummyMsg)
    bh.consume(clq.poll())
  }

  // ─── Steady state: 100× (add + poll) — simulates fork/await loop ─────────

  @Benchmark
  def mailboxSteadyState(bh: Blackhole): Unit = {
    val mbox = new FiberMailbox()
    var i = 0
    while (i < 100) {
      mbox.add(dummyMsg)
      bh.consume(mbox.poll())
      i += 1
    }
  }

  @Benchmark
  def clqSteadyState(bh: Blackhole): Unit = {
    val clq = new ConcurrentLinkedQueue[FiberMessage]()
    var i = 0
    while (i < 100) {
      clq.add(dummyMsg)
      bh.consume(clq.poll())
      i += 1
    }
  }

  // ─── Burst of 4 messages (fits entirely in ring buffer) ─────────────────

  @Benchmark
  def mailboxBurst4(bh: Blackhole): Unit = {
    val mbox = new FiberMailbox()
    mbox.add(dummyMsg)
    mbox.add(dummyMsg2)
    mbox.add(dummyMsg3)
    mbox.add(dummyMsg4)
    bh.consume(mbox.poll())
    bh.consume(mbox.poll())
    bh.consume(mbox.poll())
    bh.consume(mbox.poll())
  }

  @Benchmark
  def clqBurst4(bh: Blackhole): Unit = {
    val clq = new ConcurrentLinkedQueue[FiberMessage]()
    clq.add(dummyMsg)
    clq.add(dummyMsg2)
    clq.add(dummyMsg3)
    clq.add(dummyMsg4)
    bh.consume(clq.poll())
    bh.consume(clq.poll())
    bh.consume(clq.poll())
    bh.consume(clq.poll())
  }

  // ─── isEmpty on empty mailbox (called every iteration of runloop) ───────────

  @Benchmark
  def mailboxIsEmpty(bh: Blackhole): Unit =
    bh.consume(FiberMailboxBenchmark.emptyMailbox.isEmpty)

  @Benchmark
  def clqIsEmpty(bh: Blackhole): Unit =
    bh.consume(FiberMailboxBenchmark.emptyCLQ.isEmpty)
}

/**
 * Concurrent MPSC benchmark: N producer threads + 1 consumer thread.
 * Run with e.g. -f 4 -t 4 for 4 producers.
 */
@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class FiberMailboxConcurrentBenchmark {

  var mailbox: FiberMailbox                     = _
  var clq: ConcurrentLinkedQueue[FiberMessage] = _

  private val dummyMsg = FiberMessage.resumeUnit

  @Setup(Level.Iteration)
  def setup(): Unit = {
    mailbox = new FiberMailbox()
    clq = new ConcurrentLinkedQueue[FiberMessage]()
  }

  @Benchmark
  @Group("mailboxMPSC")
  @GroupThreads(4)
  def mailboxProducer(): Unit =
    mailbox.add(dummyMsg)

  @Benchmark
  @Group("mailboxMPSC")
  @GroupThreads(1)
  def mailboxConsumer(bh: Blackhole): Unit = {
    val msg = mailbox.poll()
    if (msg ne null) bh.consume(msg)
  }

  @Benchmark
  @Group("clqMPSC")
  @GroupThreads(4)
  def clqProducer(): Unit =
    clq.add(dummyMsg)

  @Benchmark
  @Group("clqMPSC")
  @GroupThreads(1)
  def clqConsumer(bh: Blackhole): Unit = {
    val msg = clq.poll()
    if (msg ne null) bh.consume(msg)
  }
}

object FiberMailboxBenchmark {
  // Pre-created instances for isEmpty benchmarks (avoids allocation overhead)
  val emptyMailbox = new FiberMailbox()
  val emptyCLQ     = new ConcurrentLinkedQueue[FiberMessage]()
}
