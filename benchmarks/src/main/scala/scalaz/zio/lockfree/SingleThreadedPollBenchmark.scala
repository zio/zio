package scalaz.zio.lockfree

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

object SingleThreadedPollBenchmark {
  final val OPS: Int = 1 << 15
  final val TOKEN: Int = 1
}

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class SingleThreadedPollBenchmark {
  final val OPS: Int = 1 << 15
  final val TOKEN: Int = 1

  @volatile var preventUnrolling = true

  @Param(Array("NotSafe", "RingBuffer", "JUC", "JCTools"))
  var queueTypeParam: String = _
  var queue: LockFreeQueue[Int] = _

  @Param(Array("1024"))
  var queueCapacity: Int = _

  @Setup(Level.Trial)
  def createQ(): Unit = {
    queue = impls.queueByType(queueTypeParam, queueCapacity)
  }

  @Setup(Level.Invocation)
  def fill(): Unit = {
    1.to(SingleThreadedPollBenchmark.OPS).foreach(_ => queue.offer(SingleThreadedPollBenchmark.TOKEN))
  }

  @Benchmark
  @OperationsPerInvocation(1 << 15)
  def poll(): Unit = {
    val lq = queue
    var i = 0
    while (i < SingleThreadedPollBenchmark.OPS && preventUnrolling) {
      lq.poll()
      i += 1
    }
  }
}

