package scalaz.zio.internal

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import BenchUtils._

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class OneElementQueueSeqBenchmark {
  type QueueElement = () => Int

  def mkEl(): QueueElement  = () => 1
  val emptyEl: QueueElement = () => -1

  @Param(Array("OneElementQueue", "RingBufferPow2"))
  var qType: String = _

  var q: MutableConcurrentQueue[QueueElement] = _

  @Setup(Level.Trial)
  def createQ(): Unit =
    q = queueByType(qType, 1)

  @Benchmark
  def offerAndPoll(): Int = {
    doOffer(q)
    doPoll(q)
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doOffer(q: MutableConcurrentQueue[QueueElement]): Unit = {
    q.offer(mkEl())
    ()
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doPoll(q: MutableConcurrentQueue[QueueElement]): Int = {
    val delayed = q.poll(emptyEl)
    delayed()
  }
}
