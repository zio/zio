package zio.internal

import org.openjdk.jmh.annotations._

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
private[this] class FiberMailboxBenchmark {
  private[this] val Ops = 1024

  private[this] val message = FiberMessage.resumeUnit

  private[this] var fiberMailbox = null.asInstanceOf[FiberMailbox]
  private[this] var linkedQueue  = null.asInstanceOf[ConcurrentLinkedQueue[FiberMessage]]

  @volatile
  private[this] var noUnrolling = true

  @Setup(Level.Invocation)
  def setup(): Unit = {
    fiberMailbox = new FiberMailbox()
    linkedQueue = new ConcurrentLinkedQueue[FiberMessage]()
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  def fiberMailboxEmptyPoll(): Unit = {
    val mailbox = fiberMailbox
    var i       = 0
    while (i < Ops && noUnrolling) {
      mailbox.poll()
      i += 1
    }
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  def linkedQueueEmptyPoll(): Unit = {
    val queue = linkedQueue
    var i     = 0
    while (i < Ops && noUnrolling) {
      queue.poll()
      i += 1
    }
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  def fiberMailboxSingleAddPoll(): Unit = {
    val mailbox = fiberMailbox
    var i       = 0
    while (i < Ops && noUnrolling) {
      mailbox.add(message)
      if (mailbox.poll() eq null) throw new AssertionError("missing mailbox message")
      i += 1
    }
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  def linkedQueueSingleAddPoll(): Unit = {
    val queue = linkedQueue
    var i     = 0
    while (i < Ops && noUnrolling) {
      queue.add(message)
      if (queue.poll() eq null) throw new AssertionError("missing queue message")
      i += 1
    }
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  def fiberMailboxBurst4(): Unit = {
    val mailbox = fiberMailbox
    var i       = 0
    while (i < Ops && noUnrolling) {
      mailbox.add(message)
      mailbox.add(message)
      mailbox.add(message)
      mailbox.add(message)
      mailbox.poll()
      mailbox.poll()
      mailbox.poll()
      mailbox.poll()
      i += 4
    }
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  def linkedQueueBurst4(): Unit = {
    val queue = linkedQueue
    var i     = 0
    while (i < Ops && noUnrolling) {
      queue.add(message)
      queue.add(message)
      queue.add(message)
      queue.add(message)
      queue.poll()
      queue.poll()
      queue.poll()
      queue.poll()
      i += 4
    }
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  def fiberMailboxIsEmpty(): Unit = {
    val mailbox = fiberMailbox
    var i       = 0
    while (i < Ops && noUnrolling) {
      mailbox.isEmpty
      i += 1
    }
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  def linkedQueueIsEmpty(): Unit = {
    val queue = linkedQueue
    var i     = 0
    while (i < Ops && noUnrolling) {
      queue.isEmpty
      i += 1
    }
  }
}
