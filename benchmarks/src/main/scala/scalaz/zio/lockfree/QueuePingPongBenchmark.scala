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
  val Token: Int = 1

  @Param(Array("65536"))
  var qCapacity: Int = _

  @Param(Array("RingBuffer", "JucBlocking", "JucConcurrent", "JCTools"))
  var qType: String = _

  var qIn: LockFreeQueue[Int]  = _
  var qOut: LockFreeQueue[Int] = _

  @Setup
  def setup(): Unit = {
    qIn = impls.queueByType(qType, qCapacity)
    qOut = impls.queueByType(qType, qCapacity)
  }

  @Benchmark
  @Group("A")
  @GroupThreads(1)
  def roundtrip(control: Control): Int = {
    qIn.offer(Token)

    var el = qOut.poll()
    while (!control.stopMeasurement && el.isEmpty) el = qOut.poll()
    el.getOrElse(Token)
  }

  @Benchmark
  @Group("A")
  @GroupThreads(1)
  def poll(control: Control): Boolean = {
    var el = qIn.poll()
    while (!control.stopMeasurement && el.isEmpty) el = qIn.poll()

    qOut.offer(el.getOrElse(Token))
  }
}
