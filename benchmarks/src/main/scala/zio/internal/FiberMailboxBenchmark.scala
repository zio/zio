package zio.internal

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

/**
 * Isolated mailbox costs. The run loop polls an empty mailbox before every
 * effect; fork/join offers one message to a fresh fiber and occasionally bursts
 * a parent fiber.
 *
 * Run with `CI_RELEASE_MODE=1`.
 */
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
class FiberMailboxBenchmark {
  private val message = FiberMessage.resumeUnit

  private var mailbox: FiberMailbox                      = _
  private var queue: ConcurrentLinkedQueue[FiberMessage] = _
  private var promoted: FiberMailbox                     = _

  @Setup
  def setup(): Unit = {
    mailbox = new FiberMailbox
    queue = new ConcurrentLinkedQueue[FiberMessage]
    promoted = new FiberMailbox
    // Second offer leaves the promoted queue in place for the rest of the trial.
    promoted.add(message)
    promoted.add(message)
    promoted.poll()
    promoted.poll()
  }

  @Benchmark
  def emptyPollMailbox(bh: Blackhole): Unit =
    bh.consume(mailbox.pollRelaxed())

  @Benchmark
  def emptyPollClq(bh: Blackhole): Unit =
    bh.consume(queue.poll())

  @Benchmark
  def emptyPollPromoted(bh: Blackhole): Unit =
    bh.consume(promoted.pollRelaxed())

  @Benchmark
  def addPollMailbox(bh: Blackhole): Unit = {
    mailbox.add(message)
    bh.consume(mailbox.poll())
  }

  @Benchmark
  def addPollClq(bh: Blackhole): Unit = {
    queue.add(message)
    bh.consume(queue.poll())
  }

  @Benchmark
  def burst4Mailbox(bh: Blackhole): Unit = {
    mailbox.add(message)
    mailbox.add(message)
    mailbox.add(message)
    mailbox.add(message)
    bh.consume(mailbox.poll())
    bh.consume(mailbox.poll())
    bh.consume(mailbox.poll())
    bh.consume(mailbox.poll())
  }

  @Benchmark
  def burst4Clq(bh: Blackhole): Unit = {
    queue.add(message)
    queue.add(message)
    queue.add(message)
    queue.add(message)
    bh.consume(queue.poll())
    bh.consume(queue.poll())
    bh.consume(queue.poll())
    bh.consume(queue.poll())
  }

  @Benchmark
  @OperationsPerInvocation(32)
  def runLoopShapeMailbox(bh: Blackhole): Unit = {
    var i = 0
    while (i < 31) {
      bh.consume(mailbox.pollRelaxed())
      i += 1
    }
    mailbox.add(message)
    bh.consume(mailbox.poll())
  }

  @Benchmark
  @OperationsPerInvocation(32)
  def runLoopShapeClq(bh: Blackhole): Unit = {
    var i = 0
    while (i < 31) {
      bh.consume(queue.poll())
      i += 1
    }
    queue.add(message)
    bh.consume(queue.poll())
  }
}
