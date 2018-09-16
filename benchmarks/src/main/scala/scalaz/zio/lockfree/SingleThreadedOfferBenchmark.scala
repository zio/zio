package scalaz.zio.lockfree

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

object SingleThreadedOfferBenchmark {
  val OPS: Int = 1 << 15
  val TOKEN: Int = 1
}

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class SingleThreadedOfferBenchmark {
  @volatile var preventUnrolling = true

  @Param(Array("NotSafe", "RingBuffer", "JUC", "JCTools"))
  var queueTypeParam: String = _
  var queue: LockFreeQueue[Int] = _

  @Param(Array("32769"))
  var queueCapacity: Int = _

  @Setup(Level.Invocation)
  def createQ(): Unit = {
    queue = impls.queueByType(queueTypeParam, queueCapacity)
  }

  @Benchmark
  @OperationsPerInvocation(1 << 15)
  def offer(): Unit = {
    val lq = queue
    var i = 0
    while (i < SingleThreadedPollBenchmark.OPS && preventUnrolling) {
      assert(lq.offer(SingleThreadedPollBenchmark.TOKEN))
      i += 1
    }
  }
}

