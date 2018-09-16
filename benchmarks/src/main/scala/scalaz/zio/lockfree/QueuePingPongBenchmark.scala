package scalaz.zio.lockfree

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Control

object QueuePingPongBenchmark {
  @AuxCounters
  @State(Scope.Thread)
  case class OfferCounters(var offersMade: Long, var offersFailed: Long)

  @AuxCounters
  @State(Scope.Thread)
  case class PollCounters(var pollMade: Long, var pollFailed: Long)
}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Group)
class QueuePingPongBenchmark {
  val Message: Int = 1
  val capacity: Int = 32 * 1024

  @Param(Array("JUC", "JCTools", "RingBuffer"))
  var queueType: String = _
  var queueIn: LockFreeQueue[Int] = _
  var queueOut: LockFreeQueue[Int] = _

  @Setup
  def setup(): Unit = {
    queueIn = impls.queueByType(queueType, capacity)
    queueOut = impls.queueByType(queueType, capacity)
  }

  @Benchmark
  @Group("A")
  @GroupThreads(1)
  def roundtrip(control: Control): Int = {
    queueIn.offer(Message)

    var el = queueOut.poll()
    while(!control.stopMeasurement && el.isEmpty) el = queueOut.poll()
    el.getOrElse(Message)
  }

  @Benchmark
  @Group("A")
  @GroupThreads(1)
  def poll(control: Control): Boolean = {
    var el = queueIn.poll()
    while (!control.stopMeasurement && el.isEmpty) el = queueIn.poll()

    queueOut.offer(el.getOrElse(Message))
  }
}
