package scalaz.zio.internal

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{ Blackhole, Control }

import BenchUtils._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(5)
@State(Scope.Group)
class PingPongBenchmark {
  def mkEl(): AnyRef = new Object()
  val emptyEl        = null.asInstanceOf[AnyRef]

  @Param(Array("1", "2"))
  var batchSize: Int = _

  @Param(Array("4"))
  var qCapacity: Int = _

  @Param(Array("RingBuffer", "JCTools", "JucCLQ", "JucBlocking"))
  var qType: String = _

  var qIn: MutableConcurrentQueue[AnyRef]  = _
  var qOut: MutableConcurrentQueue[AnyRef] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    qIn = queueByType(qType, qCapacity)
    qOut = queueByType(qType, qCapacity)
  }

  @Benchmark
  @Group("A")
  @GroupThreads(1)
  def roundtrip(control: Control, blackhole: Blackhole): Unit = {
    var i = 0
    while (i < batchSize) { qIn.offer(mkEl()); i += 1 }

    i = 0
    while (i < batchSize && !control.stopMeasurement) {
      var anEl = qOut.poll(emptyEl)
      while (!control.stopMeasurement && (anEl == emptyEl)) anEl = qOut.poll(emptyEl)
      blackhole.consume(anEl)
      i += 1
    }
  }

  @Benchmark
  @Group("A")
  @GroupThreads(1)
  def poll(control: Control): Unit = {
    var i: Int = 0

    while (i < batchSize && !control.stopMeasurement) {
      var anEl = qIn.poll(emptyEl)
      while (!control.stopMeasurement && (anEl == emptyEl)) anEl = qIn.poll(emptyEl)
      if (anEl != emptyEl) qOut.offer(anEl) else qOut.offer(mkEl())
      i += 1
    }
  }
}
