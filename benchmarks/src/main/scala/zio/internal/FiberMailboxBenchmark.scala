package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class FiberMailboxBenchmark {
  private[this] val message = FiberMessage.resumeUnit

  private[this] val fiberMailbox = new FiberMailbox
  private[this] val linkedQueue  = new ConcurrentLinkedQueue[FiberMessage]()

  @Param(Array("1", "2", "3", "4", "16"))
  var burstSize: Int = _

  @Benchmark
  def fiberMailboxEmptyPoll(): FiberMessage =
    fiberMailbox.poll()

  @Benchmark
  def concurrentLinkedQueueEmptyPoll(): FiberMessage =
    linkedQueue.poll()

  @Benchmark
  def fiberMailboxHasLinkedMessages(): Boolean =
    fiberMailbox.hasLinkedMessages

  @Benchmark
  def fiberMailboxIsDefinitelyEmpty(): Boolean =
    fiberMailbox.isDefinitelyEmpty

  @Benchmark
  def concurrentLinkedQueueIsEmpty(): Boolean =
    linkedQueue.isEmpty

  @Benchmark
  def fiberMailboxOfferPoll(): FiberMessage = {
    fiberMailbox.add(message)
    fiberMailbox.poll()
  }

  @Benchmark
  def concurrentLinkedQueueOfferPoll(): FiberMessage = {
    linkedQueue.add(message)
    linkedQueue.poll()
  }

  @Benchmark
  def fiberMailboxBurstDrain(): Int = {
    var offered = 0
    while (offered < burstSize) {
      fiberMailbox.add(message)
      offered += 1
    }

    var drained = 0
    while (fiberMailbox.poll() ne null) {
      drained += 1
    }
    drained
  }

  @Benchmark
  def concurrentLinkedQueueBurstDrain(): Int = {
    var offered = 0
    while (offered < burstSize) {
      linkedQueue.add(message)
      offered += 1
    }

    var drained = 0
    while (linkedQueue.poll() ne null) {
      drained += 1
    }
    drained
  }
}
