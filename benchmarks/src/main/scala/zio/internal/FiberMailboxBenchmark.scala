package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

/**
 * {{{
 * sbt "benchmarks/Jmh/run -i 10 -wi 5 -f 2 -t 1 zio.internal.FiberMailboxBenchmark"
 *
 * JDK 21, Windows 11
 * Benchmark                                    Mode  Cnt     Score     Error   Units
 * FiberMailboxBenchmark.clqSingleAddPoll      thrpt    5    29.071 ±   0.323  ops/us
 * FiberMailboxBenchmark.mailboxSingleAddPoll  thrpt    5    83.965 ±   1.598  ops/us  (+189%)
 * FiberMailboxBenchmark.clqSteadyState        thrpt    5     0.407 ±   0.019  ops/us
 * FiberMailboxBenchmark.mailboxSteadyState    thrpt    5     0.877 ±   0.020  ops/us  (+115%)
 * FiberMailboxBenchmark.clqBurst4             thrpt    5     9.621 ±   0.536  ops/us
 * FiberMailboxBenchmark.mailboxBurst4         thrpt    5     6.170 ±   0.874  ops/us
 * FiberMailboxBenchmark.clqIsEmpty            thrpt    5  1342.038 ±  24.082  ops/us
 * FiberMailboxBenchmark.mailboxIsEmpty        thrpt    5  1416.823 ± 111.643  ops/us
 *
 * ForkJoinBenchmark.zioForkJoin (before)     thrpt   20   648.195 ±  20.406  ops/s
 * ForkJoinBenchmark.zioForkJoin (after)      thrpt   20   669.037 ±  16.345  ops/s   (+3.2%)
 * }}}
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class FiberMailboxBenchmark {

  @Benchmark
  def mailboxSingleAddPoll(bh: Blackhole): Unit = {
    val mbox = new FiberMailbox()
    mbox.add(FiberMailboxBenchmark.dummyMsg)
    bh.consume(mbox.poll())
  }

  @Benchmark
  def clqSingleAddPoll(bh: Blackhole): Unit = {
    val clq = new ConcurrentLinkedQueue[FiberMessage]()
    clq.add(FiberMailboxBenchmark.dummyMsg)
    bh.consume(clq.poll())
  }

  @Benchmark
  def mailboxBurst4(bh: Blackhole): Unit = {
    val mbox = new FiberMailbox()
    mbox.add(FiberMailboxBenchmark.dummyMsg)
    mbox.add(FiberMailboxBenchmark.dummyMsg2)
    mbox.add(FiberMailboxBenchmark.dummyMsg3)
    mbox.add(FiberMailboxBenchmark.dummyMsg4)
    bh.consume(mbox.poll())
    bh.consume(mbox.poll())
    bh.consume(mbox.poll())
    bh.consume(mbox.poll())
  }

  @Benchmark
  def clqBurst4(bh: Blackhole): Unit = {
    val clq = new ConcurrentLinkedQueue[FiberMessage]()
    clq.add(FiberMailboxBenchmark.dummyMsg)
    clq.add(FiberMailboxBenchmark.dummyMsg2)
    clq.add(FiberMailboxBenchmark.dummyMsg3)
    clq.add(FiberMailboxBenchmark.dummyMsg4)
    bh.consume(clq.poll())
    bh.consume(clq.poll())
    bh.consume(clq.poll())
    bh.consume(clq.poll())
  }

  @Benchmark
  def mailboxSteadyState(bh: Blackhole): Unit = {
    val mbox = new FiberMailbox()
    var i = 0
    while (i < 100) {
      mbox.add(FiberMailboxBenchmark.dummyMsg)
      bh.consume(mbox.poll())
      i += 1
    }
  }

  @Benchmark
  def clqSteadyState(bh: Blackhole): Unit = {
    val clq = new ConcurrentLinkedQueue[FiberMessage]()
    var i = 0
    while (i < 100) {
      clq.add(FiberMailboxBenchmark.dummyMsg)
      bh.consume(clq.poll())
      i += 1
    }
  }

  @Benchmark
  def mailboxIsEmpty(bh: Blackhole): Unit =
    bh.consume(FiberMailboxBenchmark.emptyMailbox.isEmpty)

  @Benchmark
  def clqIsEmpty(bh: Blackhole): Unit =
    bh.consume(FiberMailboxBenchmark.emptyCLQ.isEmpty)
}

@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class FiberMailboxConcurrentBenchmark {

  var mailbox: FiberMailbox                    = _
  var clq: ConcurrentLinkedQueue[FiberMessage] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    mailbox = new FiberMailbox()
    clq = new ConcurrentLinkedQueue[FiberMessage]()
  }

  @Benchmark
  @Group("mailboxMPSC")
  @GroupThreads(4)
  def mailboxProducer(): Unit =
    mailbox.add(FiberMailboxBenchmark.dummyMsg)

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
    clq.add(FiberMailboxBenchmark.dummyMsg)

  @Benchmark
  @Group("clqMPSC")
  @GroupThreads(1)
  def clqConsumer(bh: Blackhole): Unit = {
    val msg = clq.poll()
    if (msg ne null) bh.consume(msg)
  }
}

object FiberMailboxBenchmark {
  val dummyMsg: FiberMessage  = FiberMessage.resumeUnit
  val dummyMsg2: FiberMessage = FiberMessage.Stateful(_ => ())
  val dummyMsg3: FiberMessage = FiberMessage.resumeUnit
  val dummyMsg4: FiberMessage = FiberMessage.Stateful(_ => ())

  val emptyMailbox = new FiberMailbox()
  val emptyCLQ     = new ConcurrentLinkedQueue[FiberMessage]()
}
