package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.{TimeUnit, ThreadLocalRandom}
import java.util.concurrent.atomic.AtomicLong

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(16)
class FiberSetBenchmark {

  @Param(Array("1000"))
  var capacity: Int = _

  @Param(Array("1000"))
  var elements: Int = _

  var fiberSet: FiberSet = null

  @Setup(Level.Iteration)
  def createFiberSet() = {
    FiberSetBenchmark.alive.set(0L)
    FiberSetBenchmark.dead.set(0L)

    fiberSet = FiberSet(1000)
  }

  @Benchmark
  def add(): Any =
    fiberSet.add(FiberEntry())

  @TearDown(Level.Iteration)
  def printStats(): Unit = {
    val alive = FiberSetBenchmark.alive.get
    val dead  = FiberSetBenchmark.dead.get
    println(s"dead: ${dead}")
    println(s"alive: ${alive}")
    println(s"total: ${dead + alive}")
    println(s"aliveness: ${alive.toDouble / (alive.toDouble + dead.toDouble)}")
  }
}

object FiberSetBenchmark {
  val alive: AtomicLong = new AtomicLong(0L)
  val dead: AtomicLong  = new AtomicLong(0L)

  final val instrument = true
}

final class FiberEntry(expiration: Long) extends FiberSetRef {
  import FiberSetBenchmark._

  @volatile var _setEpochId: Long = -1L
  @volatile var _setIndex: Int    = -1

  def isTerminated: Boolean = {
    val result = System.nanoTime() > expiration

    if (instrument) {
      if (result) dead.incrementAndGet() else alive.incrementAndGet()
    }

    result
  }
}

object FiberEntry {
  def apply(): FiberEntry = {
    val random = ThreadLocalRandom.current()

    new FiberEntry(System.nanoTime() + random.nextInt(100000))
  }
}
