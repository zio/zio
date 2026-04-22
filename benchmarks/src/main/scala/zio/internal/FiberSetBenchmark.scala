package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.{Duration, Unsafe}

import java.util.concurrent.{TimeUnit, ThreadLocalRandom}
import java.util.concurrent.atomic.AtomicLong

/**
 * Benchmark comparing FiberSet vs WeakConcurrentBag performance.
 *
 * Measures:
 * - Add throughput (hot path vs cold path)
 * - Remove performance
 * - Iteration overhead
 * - GC pause times
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(16)
class FiberSetBenchmark {

  @Param(Array("256", "1024"))
  var hotCapacity: Int = _

  @Param(Array("1000", "10000"))
  var warmCapacity: Int = _

  var fiberSet: FiberSet[FiberEntry] = _
  var weakBag: WeakConcurrentBag[FiberEntry] = _

  implicit val unsafe: Unsafe = Unsafe.unsafe

  @Setup(Level.Iteration)
  def setup(): Unit = {
    fiberSet = FiberSet[FiberEntry](
      hotCapacity = hotCapacity,
      warmCapacity = warmCapacity,
      isAlive = _.isAlive()
    )

    weakBag = WeakConcurrentBag[FiberEntry](
      capacity = warmCapacity,
      isAlive = _.isAlive()
    )

    FiberSetBenchmark.alive.set(0L)
    FiberSetBenchmark.dead.set(0L)
  }

  @Benchmark
  def fiberSet_add(): Unit = {
    fiberSet.add(FiberEntry())
  }

  @Benchmark
  def weakBag_add(): Unit = {
    weakBag.add(FiberEntry())
  }

  @Benchmark
  def fiberSet_add_remove(): Unit = {
    val entry = FiberEntry()
    fiberSet.add(entry)
    fiberSet.remove(entry)
  }

  @Benchmark
  def weakBag_add_remove(): Unit = {
    val entry = FiberEntry()
    weakBag.add(entry)
    // WeakConcurrentBag doesn't support remove, skip for fair comparison
  }

  @Benchmark
  def fiberSet_iterate(): Unit = {
    // Pre-populate with 100 elements
    val set = FiberSet[FiberEntry](hotCapacity, warmCapacity)
    for (_ <- 1 to 100) {
      set.add(FiberEntry())
    }

    // Iterate
    val iter = set.iterator
    while (iter.hasNext) {
      iter.next()
    }
  }

  @Benchmark
  def weakBag_iterate(): Unit = {
    // Pre-populate with 100 elements
    val bag = WeakConcurrentBag[FiberEntry](warmCapacity)
    for (_ <- 1 to 100) {
      bag.add(FiberEntry())
    }

    // Iterate
    val iter = bag.iterator
    while (iter.hasNext) {
      iter.next()
    }
  }

  @Benchmark
  def fiberSet_gc(): Unit = {
    fiberSet.gc()
  }

  @Benchmark
  def weakBag_gc(): Unit = {
    weakBag.gc()
  }

  @TearDown(Level.Iteration)
  def printStats(): Unit = {
    val alive = FiberSetBenchmark.alive.get
    val dead = FiberSetBenchmark.dead.get
    println(s"FiberSet Benchmark - Dead: $dead, Alive: $alive, Total: ${dead + alive}")
  }
}

object FiberSetBenchmark {
  val alive: AtomicLong = new AtomicLong(0L)
  val dead: AtomicLong = new AtomicLong(0L)
}

/**
 * Mock fiber entry for benchmarking.
 *
 * Simulates fibers with varying lifetimes to test GC behavior.
 */
final case class FiberEntry(expiration: Long) {
  import FiberSetBenchmark._

  def isAlive(): Boolean = {
    val result = System.nanoTime() <= expiration

    // Instrument for stats
    if (result) {
      alive.incrementAndGet()
    } else {
      dead.incrementAndGet()
    }

    result
  }
}

object FiberEntry {
  def apply(): FiberEntry = {
    val random = ThreadLocalRandom.current()
    // 80% chance of being alive, 20% chance of being dead
    val lifetime = if (random.nextDouble() < 0.8) {
      100000000L // 100ms in future
    } else {
      -1L // Already expired
    }

    FiberEntry(System.nanoTime() + lifetime)
  }
}
