package scalaz.zio.internal

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

import scalaz.zio.internal.impls._

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

  var oneElQ: MutableConcurrentQueue[QueueElement] = new OneElementConcurrentQueue
  var pow2RB: MutableConcurrentQueue[QueueElement] = RingBufferPow2(2)

  @Setup(Level.Trial)
  def createQ(): Unit = {
    oneElQ = new OneElementConcurrentQueue()
    pow2RB = RingBufferPow2(2)
  }

  @Benchmark
  def oneElOfferAndPoll(): Int = {
    doOffer(oneElQ)
    doPoll(oneElQ)
  }

  @Benchmark
  def pow2RBOfferAndPoll(): Int = {
    doOffer(pow2RB)
    doPoll(pow2RB)
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
