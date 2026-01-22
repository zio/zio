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

  var fiberSet: FiberSet[FiberSetEntry]         = _
  var weakBag: WeakConcurrentBag[FiberSetEntry] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    FiberSetBenchmark.alive.set(0L)
    FiberSetBenchmark.dead.set(0L)

    fiberSet = FiberSet(capacity, _.isAlive())
    weakBag = WeakConcurrentBag(capacity, _.isAlive())
  }

  @Benchmark
  def fiberSetAdd(): Any =
    fiberSet.add(FiberSetEntry())

  @Benchmark
  def weakBagAdd(): Any =
    weakBag.add(FiberSetEntry())

  @TearDown(Level.Iteration)
  def printStats(): Unit = {
    val alive = FiberSetBenchmark.alive.get
    val dead  = FiberSetBenchmark.dead.get
    println(s"alive: $alive, dead: $dead, total: ${alive + dead}")
  }
}

@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(16)
class FiberSetAddRemoveBenchmark {

  var fiberSet: FiberSet[FiberSetEntry] = _
  var entries: Array[FiberSetEntry]     = _
  var index: Int                        = 0

  @Setup(Level.Iteration)
  def setup(): Unit = {
    fiberSet = FiberSet(1000, _.isAlive())
    entries = Array.fill(10000)(FiberSetEntry())
    entries.foreach(fiberSet.add)
    fiberSet.graduate()
    index = 0
  }

  @Benchmark
  def addThenRemove(): Any = {
    val entry = entries(index % entries.length)
    fiberSet.remove(entry)
    fiberSet.add(entry)
    index += 1
  }
}

/**
 * High-contention benchmark simulating Loom virtual thread workloads. Tests
 * many concurrent threads hitting the same FiberSet, which is the pattern seen
 * with Project Loom's virtual threads.
 */
@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class FiberSetHighContentionBenchmark {

  var fiberSet: FiberSet[FiberSetEntry]         = _
  var weakBag: WeakConcurrentBag[FiberSetEntry] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    fiberSet = FiberSet(256, _.isAlive())
    weakBag = WeakConcurrentBag(256, _.isAlive())
  }

  // Simulates 64 virtual threads adding concurrently
  @Benchmark
  @Threads(64)
  def fiberSetHighContention(): Any =
    fiberSet.add(FiberSetEntry())

  @Benchmark
  @Threads(64)
  def weakBagHighContention(): Any =
    weakBag.add(FiberSetEntry())

  // Simulates rapid fiber lifecycle (fork + terminate)
  @Benchmark
  @Threads(32)
  def fiberSetLifecycle(): Any = {
    val entry = FiberSetEntry()
    fiberSet.add(entry)
    fiberSet.graduate()
    fiberSet.remove(entry)
  }
}

object FiberSetBenchmark {
  val alive: AtomicLong = new AtomicLong(0L)
  val dead: AtomicLong  = new AtomicLong(0L)

  final val instrument = true
}

final case class FiberSetEntry(expiration: Long) {
  import FiberSetBenchmark._

  def isAlive(): Boolean = {
    val result = System.nanoTime() <= expiration

    if (instrument) {
      if (result) alive.incrementAndGet() else dead.incrementAndGet()
    }

    result
  }
}

object FiberSetEntry {
  def apply(): FiberSetEntry = {
    val random = ThreadLocalRandom.current()
    FiberSetEntry(System.nanoTime() + random.nextInt(100000))
  }
}
