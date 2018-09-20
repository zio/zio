package scalaz.zio.lockfree

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class SingleThreadedPollBenchmark {
  val Ops: Int   = 1 << 16
  val Token: Int = 1

  @volatile var preventUnrolling = true

  @Param(Array("65536"))
  var qCapacity: Int = _

  @Param(Array("RingBuffer", "JucBlocking", "JucConcurrent", "JCTools", "Unsafe"))
  var qType: String = _

  var q: LockFreeQueue[Int] = _

  @Setup(Level.Trial)
  def createQ(): Unit =
    q = impls.queueByType(qType, qCapacity)

  @Setup(Level.Invocation)
  def fill(): Unit = {
    var i = 0
    while (i < Ops) { q.offer(Token); i += 1 }
  }

  @Benchmark
  @OperationsPerInvocation(1 << 16)
  def poll(): Unit = {
    val lq = q
    var i  = 0
    while (i < Ops && preventUnrolling) {
      lq.poll()
      i += 1
    }
  }
}
