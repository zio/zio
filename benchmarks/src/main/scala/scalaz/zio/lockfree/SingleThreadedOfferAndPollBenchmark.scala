package scalaz.zio.lockfree

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class SingleThreadedOfferAndPollBenchmark {

  //  @Param(Array("RingBuffer"))
  @Param(Array("NotSafe", "RingBuffer", "JUC", "JCTools"))
  var queueTypeParam: String = _

  @Param(Array("132000"))
  var queueCapacity: Int = _

  @Param(Array("1"))
  var batchSize: Int = _

  var queue: LockFreeQueue[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    queue = impls.queueByType(queueTypeParam, queueCapacity)
  }

  @Benchmark
  def offerAndPoll(): Int = {
    val bSize = batchSize

    var i = 0
    while (i < bSize) {
      queue.offer(SingleThreadedOfferAndPollBenchmark.Message)
      i += 1
    }

    i = 0
    var result: Int = 0
    while (i < batchSize) {
      result = queue.poll().get
      i += 1
    }
    result
  }
}

object SingleThreadedOfferAndPollBenchmark {
  final val Message: Int = 1
}
