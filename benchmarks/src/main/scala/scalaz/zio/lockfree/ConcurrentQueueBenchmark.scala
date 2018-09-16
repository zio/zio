package scalaz.zio.lockfree

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

import scalaz.zio.lockfree.ConcurrentQueueBenchmark.{ OfferCounters, PollCounters }

object ConcurrentQueueBenchmark {
  @AuxCounters
  @State(Scope.Thread)
  case class PollCounters(var pollsFailed: Long, var pollsMade: Long)

  @AuxCounters
  @State(Scope.Thread)
  class OfferCounters(var offersFailed: Long, var offersMade: Long)
}

@State(Scope.Group)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 1)
@Fork(1)
@Measurement(iterations = 10, time = 1)
class ConcurrentQueueBenchmark {
  private val DELAY_PRODUCER: Long = java.lang.Long.getLong("delay.p", 0L)
  private val DELAY_CONSUMER: Long = java.lang.Long.getLong("delay.c", 0L)

  val TestElement: Int      = 1
  var element: Int          = 1
  var escape: Int           = _
  var q: LockFreeQueue[Int] = _

  @Param(Array("RingBuffer", "JUC", "JCTools"))
  var qType: String = _

  @Param(Array("128000"))
  var qCapacity: Int = _

  @Setup
  def createQueue(): Unit =
    q = impls.queueByType(qType, qCapacity)

  @Benchmark
  @Group("A")
  @GroupThreads(4)
  def offer(counters: OfferCounters): Unit = {
    if (!q.offer(element)) {
      counters.offersFailed += 1
      backoff()
    } else {
      counters.offersMade += 1
    }
    if (DELAY_PRODUCER != 0) {
      Blackhole.consumeCPU(DELAY_PRODUCER)
    }
  }

  @Benchmark
  @Group("A")
  @GroupThreads(4)
  def poll(counters: PollCounters): Unit = {
    val e = q.poll()

    if (e.isEmpty) {
      counters.pollsFailed += 1
      backoff()
    } else if (e.get == TestElement) {
      counters.pollsMade += 1
    } else {
      escape = e.get
    }

    if (DELAY_CONSUMER != 0) {
      Blackhole.consumeCPU(DELAY_CONSUMER)
    }
  }

  @TearDown(Level.Iteration)
  def emptyQ(): Unit =
    q.synchronized {
      while (q.poll().isDefined) {}
    }

  def backoff(): Unit = {}
}
