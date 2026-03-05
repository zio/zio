package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.{Collections, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import java.util.{WeakHashMap, Set => JSet}
@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
class FiberSetBenchmark {

  @Param(Array("1000"))
  var elements: Int = _

  var fiberSet: FiberSet[FiberSetBenchmark.FakeEntry]       = _
  var syncWeakSet: JSet[FiberSetBenchmark.FakeEntry]        = _
  var weakBag: WeakConcurrentBag[FiberSetBenchmark.FakeEntry] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    import FiberSetBenchmark._
    val nCpu = Runtime.getRuntime.availableProcessors()
    fiberSet = FiberSet[FakeEntry](elements, nCpu * 2, isAlive)
    syncWeakSet = Collections.synchronizedSet(
      Collections.newSetFromMap(new WeakHashMap[FakeEntry, java.lang.Boolean]())
    )
    weakBag = WeakConcurrentBag[FakeEntry](elements, isAlive)
  }

  @Benchmark
  @Threads(1)
  def fiberSetAddSerial(): Unit =
    fiberSet.add(FiberSetBenchmark.FakeEntry())

  @Benchmark
  @Threads(8)
  def fiberSetAddConcurrent(): Unit =
    fiberSet.add(FiberSetBenchmark.FakeEntry())

  @Benchmark
  @Threads(1)
  def syncWeakSetAddSerial(): Unit =
    syncWeakSet.add(FiberSetBenchmark.FakeEntry())

  @Benchmark
  @Threads(8)
  def syncWeakSetAddConcurrent(): Unit =
    syncWeakSet.add(FiberSetBenchmark.FakeEntry())

  @Benchmark
  @Threads(1)
  def weakBagAddSerial(): Unit =
    weakBag.add(FiberSetBenchmark.FakeEntry())

  @Benchmark
  @Threads(8)
  def weakBagAddConcurrent(): Unit =
    weakBag.add(FiberSetBenchmark.FakeEntry())
}

@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
class FiberSetAddRemoveBenchmark {

  var fiberSet: FiberSet[FiberSetBenchmark.FakeEntry]       = _
  var syncWeakSet: JSet[FiberSetBenchmark.FakeEntry]        = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    import FiberSetBenchmark._
    fiberSet = FiberSet[FakeEntry](256, 1, isAlive)
    syncWeakSet = Collections.synchronizedSet(
      Collections.newSetFromMap(new WeakHashMap[FakeEntry, java.lang.Boolean]())
    )
  }

  @Benchmark
  @Threads(1)
  def fiberSetAddRemove(bh: Blackhole): Unit = {
    val e = FiberSetBenchmark.FakeEntry()
    fiberSet.add(e)
    bh.consume(fiberSet.remove(e))
  }

  @Benchmark
  @Threads(1)
  def syncWeakSetAddRemove(bh: Blackhole): Unit = {
    val e = FiberSetBenchmark.FakeEntry()
    syncWeakSet.add(e)
    bh.consume(syncWeakSet.remove(e))
  }
}

@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
class FiberSetIterateBenchmark {

  @Param(Array("100", "1000"))
  var elements: Int = _

  var fiberSet: FiberSet[FiberSetBenchmark.FakeEntry]        = _
  var syncWeakSet: JSet[FiberSetBenchmark.FakeEntry]         = _
  var refs: Array[FiberSetBenchmark.FakeEntry]               = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    import FiberSetBenchmark._
    fiberSet = FiberSet[FakeEntry](elements, 1, isAlive)
    syncWeakSet = Collections.synchronizedSet(
      Collections.newSetFromMap(new WeakHashMap[FakeEntry, java.lang.Boolean]())
    )
    refs = (0 until elements).map(_ => FakeEntry()).toArray
    refs.foreach { e =>
      fiberSet.add(e)
      syncWeakSet.add(e)
    }
  }

  @Benchmark
  @Threads(1)
  def fiberSetIterate(bh: Blackhole): Unit =
    fiberSet.forEach(e => bh.consume(e))

  @Benchmark
  @Threads(1)
  def syncWeakSetIterate(bh: Blackhole): Unit =
    syncWeakSet.synchronized {
      val it = syncWeakSet.iterator()
      while (it.hasNext) bh.consume(it.next())
    }
}

object FiberSetBenchmark {
  private val counter = new AtomicInteger(0)

  val isAlive: FiberSet.IsAlive[FakeEntry] = _.alive

  final class FakeEntry {
    val id: Int       = counter.getAndIncrement()
    var alive: Boolean = true
    override def hashCode(): Int       = id
    override def equals(obj: Any): Boolean = obj match {
      case that: FakeEntry => this.id == that.id
      case _               => false
    }
    def isAlive(): Boolean = alive
  }

  object FakeEntry {
    def apply(): FakeEntry = new FakeEntry()
  }
}
