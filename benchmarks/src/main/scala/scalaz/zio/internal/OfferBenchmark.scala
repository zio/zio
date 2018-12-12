package scalaz.zio.internal

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import BenchUtils._

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class OfferBenchmark {
  val Ops: Int = 1 << 16

  def mkEl(): AnyRef = new Object()

  @volatile var noUnrolling = true

  @Param(Array("65536"))
  var qCapacity: Int = _

  @Param(Array("RingBuffer", "JCTools", "LinkedQueue", "JucBlocking", "NotThreadSafe"))
  var qType: String = _

  var q: MutableConcurrentQueue[AnyRef] = _

  @Setup(Level.Invocation)
  def createQ(): Unit =
    q = queueByType(qType, qCapacity)

  @Benchmark
  @OperationsPerInvocation(1 << 16)
  def offer(): Unit = {
    val aQ = q
    var i  = 0

    while (i < Ops && noUnrolling) {
      val anEl = mkEl()
      aQ.offer(anEl)
      i += 1
    }
  }
}
