package scalaz.zio.internal

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

// import BenchUtils._

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class RingBufferMethodDispatchBenchmark {
  type QueueElement = () => Int

  def mkEl(): QueueElement  = () => 1
  val emptyEl: QueueElement = () => -1

  @Param(Array("1"))
  var batchSize: Int = _

  @Param(Array("8"))
  var qCapacity: Int = _

  var q1: MutableConcurrentQueue[QueueElement] = _
  var q2: MutableConcurrentQueue[QueueElement] = _
  var q3: MutableConcurrentQueue[QueueElement] = _

  @Setup(Level.Trial)
  def createQ(): Unit = {
    q1 = new impls.RingBufferPow2[QueueElement](qCapacity)
    q2 = new impls.RingBufferArb[QueueElement](qCapacity)
    q3 = new impls.LinkedQueue[QueueElement]
  }

  @Benchmark
  @Group("OnlyPow2")
  @GroupThreads(4)
  def onlyPow2Pow2(): Int = {
    doOffer(q1, batchSize)
    doPoll(q1)
  }

  @Benchmark
  @Group("Pow2Unbounded")
  @GroupThreads(3)
  def puPow2(): Int = {
    doOffer(q1, batchSize)
    doPoll(q1)
  }

  @Benchmark
  @Group("Pow2Unbounded")
  @GroupThreads(1)
  def puUnbounded(): Int = {
    doOffer(q3, batchSize)
    doPoll(q3)
  }

  @Benchmark
  @Group("All")
  @GroupThreads(2)
  def allPow2(): Int = {
    doOffer(q1, batchSize)
    doPoll(q1)
  }

  @Benchmark
  @Group("All")
  @GroupThreads(1)
  def allArb(): Int = {
    doOffer(q2, batchSize)
    doPoll(q2)
  }

  @Benchmark
  @Group("All")
  @GroupThreads(1)
  def allUnbounded(): Int = {
    doOffer(q3, batchSize)
    doPoll(q3)
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doOffer(q: MutableConcurrentQueue[QueueElement], bSize: Int): Unit = {
    var i = 0
    while (i < bSize) {
      q.offer(mkEl())
      i += 1
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doPoll(q: MutableConcurrentQueue[QueueElement]): Int = {
    var i           = 0
    var result: Int = 0
    while (i < batchSize) {
      val delayed = q.poll(emptyEl)
      result += delayed()
      i += 1
    }
    result
  }
}
