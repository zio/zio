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
class SingleThreadedRoundtrip {
  val Token: Int = 1

  @Param(Array("1", "10"))
  var batchSize: Int = _

  @Param(Array("65536"))
  var qCapacity: Int = _

  @Param(Array("RingBuffer", "JucBlocking", "JucConcurrent", "JCTools", "Unsafe"))
  var qType: String = _

  var q: MutableConcurrentQueue[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit =
    q = impls.queueByType(qType, qCapacity)

  @Benchmark
  def offerAndPoll(): Int = {
    val bSize = batchSize

    var i = 0
    while (i < bSize) {
      q.offer(Token)
      i += 1
    }

    i = 0
    var result: Int = 0
    while (i < batchSize) {
      result = q.poll().get
      i += 1
    }
    result
  }
}
