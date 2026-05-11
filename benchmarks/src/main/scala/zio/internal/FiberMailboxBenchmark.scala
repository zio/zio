package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class FiberMailboxBenchmark {

  @Benchmark
  def fiberMailboxEmptyPoll(state: FiberMailboxBatchState, blackhole: Blackhole): Unit =
    blackhole.consume(state.fiberMailbox.poll())

  @Benchmark
  def concurrentLinkedQueueEmptyPoll(state: FiberMailboxBatchState, blackhole: Blackhole): Unit =
    blackhole.consume(state.concurrentLinkedQueue.poll())

  @Benchmark
  def fiberMailboxOfferPoll(state: FiberMailboxBatchState, blackhole: Blackhole): Unit = {
    var i = 0
    while (i < state.pending) {
      state.fiberMailbox.offer(state.messages(i))
      i += 1
    }

    i = 0
    while (i < state.pending) {
      blackhole.consume(state.fiberMailbox.poll())
      i += 1
    }
  }

  @Benchmark
  def concurrentLinkedQueueOfferPoll(state: FiberMailboxBatchState, blackhole: Blackhole): Unit = {
    var i = 0
    while (i < state.pending) {
      state.concurrentLinkedQueue.offer(state.messages(i))
      i += 1
    }

    i = 0
    while (i < state.pending) {
      blackhole.consume(state.concurrentLinkedQueue.poll())
      i += 1
    }
  }

  @Benchmark
  @Group("fiberMailboxMpsc")
  @GroupThreads(2)
  def fiberMailboxMpscOffer(state: FiberMailboxMpscState): Unit =
    state.fiberMailbox.offer(state.message)

  @Benchmark
  @Group("fiberMailboxMpsc")
  @GroupThreads(1)
  def fiberMailboxMpscPoll(state: FiberMailboxMpscState, blackhole: Blackhole): Unit = {
    blackhole.consume(state.fiberMailbox.poll())
    blackhole.consume(state.fiberMailbox.poll())
    blackhole.consume(state.fiberMailbox.poll())
    blackhole.consume(state.fiberMailbox.poll())
  }

  @Benchmark
  @Group("concurrentLinkedQueueMpsc")
  @GroupThreads(2)
  def concurrentLinkedQueueMpscOffer(state: FiberMailboxMpscState): Unit =
    state.concurrentLinkedQueue.offer(state.message)

  @Benchmark
  @Group("concurrentLinkedQueueMpsc")
  @GroupThreads(1)
  def concurrentLinkedQueueMpscPoll(state: FiberMailboxMpscState, blackhole: Blackhole): Unit = {
    blackhole.consume(state.concurrentLinkedQueue.poll())
    blackhole.consume(state.concurrentLinkedQueue.poll())
    blackhole.consume(state.concurrentLinkedQueue.poll())
    blackhole.consume(state.concurrentLinkedQueue.poll())
  }
}

@State(JScope.Thread)
class FiberMailboxBatchState {
  val fiberMailbox          = new FiberMailbox[AnyRef]
  val concurrentLinkedQueue = new ConcurrentLinkedQueue[AnyRef]
  val messages              = Array.fill[AnyRef](4)(new Object)

  @Param(Array("1", "2", "4"))
  var pending: Int = _
}

@State(JScope.Group)
class FiberMailboxMpscState {
  val fiberMailbox          = new FiberMailbox[AnyRef]
  val concurrentLinkedQueue = new ConcurrentLinkedQueue[AnyRef]
  val message               = new Object
}
