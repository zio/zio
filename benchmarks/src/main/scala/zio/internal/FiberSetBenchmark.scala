package zio.internal

import org.openjdk.jmh.annotations._
import zio.internal.legacy.{WeakConcurrentBag, SyncWeakHashSet}
import zio.{Duration, Unsafe}

import java.util.concurrent.TimeUnit
import scala.util.Random

// ---- RootsProfile state: 10,000-entry pre-populated set, autoGcEvery = Some(5.seconds).
@State(Scope.Benchmark)
class RootsProfile {
  final val N                          = 10000
  var fiberSet: FiberSet[AnyRef]       = _
  var bag: WeakConcurrentBag[AnyRef]   = _
  var syncSet: SyncWeakHashSet[AnyRef] = _
  var pool: Array[AnyRef]              = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    implicit val u: Unsafe = Unsafe.unsafe(identity)
    fiberSet = new FiberSet[AnyRef](N, FiberSet.IsAlive.always, Some(Duration.fromSeconds(5)))
    bag = WeakConcurrentBag[AnyRef](N, WeakConcurrentBag.IsAlive.always)
    syncSet = new SyncWeakHashSet[AnyRef]
    pool = Array.fill(N)(new Object)
    var i = 0
    while (i < N) { fiberSet.add(pool(i)); bag.add(pool(i)); syncSet.add(pool(i)); i += 1 }
  }
}

// ---- ChildrenProfile state: 64-entry pre-populated set, autoGcEvery = None.
@State(Scope.Benchmark)
class ChildrenProfile {
  final val N                          = 64
  var fiberSet: FiberSet[AnyRef]       = _
  var bag: WeakConcurrentBag[AnyRef]   = _
  var syncSet: SyncWeakHashSet[AnyRef] = _
  var pool: Array[AnyRef]              = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    implicit val u: Unsafe = Unsafe.unsafe(identity)
    fiberSet = new FiberSet[AnyRef](16, FiberSet.IsAlive.always, None)
    bag = WeakConcurrentBag[AnyRef](16, WeakConcurrentBag.IsAlive.always)
    syncSet = new SyncWeakHashSet[AnyRef]
    pool = Array.fill(N)(new Object)
    var i = 0
    while (i < N) { fiberSet.add(pool(i)); bag.add(pool(i)); syncSet.add(pool(i)); i += 1 }
  }
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class FiberSetBenchmark {

  // Structure selection: one @Param for all methods; dispatch in-body (OfferBenchmark pattern).
  @Param(Array("FiberSet", "WeakConcurrentBag", "SyncWeakHashSet"))
  var struct: String = _

  // ---- RootsProfile benchmarks (read-mostly: iterate dominates).
  @Benchmark
  def rootsAdd(s: RootsProfile): Unit = {
    val e = new Object
    struct match {
      case "FiberSet"          => s.fiberSet.add(e)
      case "WeakConcurrentBag" => s.bag.add(e)
      case "SyncWeakHashSet"   => s.syncSet.add(e)
    }
  }

  @Benchmark
  def rootsRemove(s: RootsProfile): Unit = {
    val e = s.pool(Random.nextInt(s.N))
    struct match {
      case "FiberSet"          => s.fiberSet.remove(e)
      case "WeakConcurrentBag" => s.bag.remove(e)
      case "SyncWeakHashSet"   => s.syncSet.remove(e)
    }
  }

  @Benchmark
  def rootsIterate(s: RootsProfile): Int = {
    var count = 0
    struct match {
      case "FiberSet" =>
        val it = s.fiberSet.iterator; while (it.hasNext) { it.next(); count += 1 }
      case "WeakConcurrentBag" =>
        val it = s.bag.iterator; while (it.hasNext) { it.next(); count += 1 }
      case "SyncWeakHashSet" =>
        val it = s.syncSet.iterator; while (it.hasNext) { it.next(); count += 1 }
    }
    count
  }

  @Benchmark
  def rootsChurn(s: RootsProfile): Unit =
    // 70/30 add/remove churn
    if (Random.nextInt(100) < 70) rootsAdd(s) else rootsRemove(s)

  // ---- ChildrenProfile benchmarks (high churn: add/remove dominates).
  @Benchmark
  def childrenAdd(s: ChildrenProfile): Unit = {
    val e = new Object
    struct match {
      case "FiberSet"          => s.fiberSet.add(e)
      case "WeakConcurrentBag" => s.bag.add(e)
      case "SyncWeakHashSet"   => s.syncSet.add(e)
    }
  }

  @Benchmark
  def childrenRemove(s: ChildrenProfile): Unit = {
    val e = s.pool(Random.nextInt(s.N))
    struct match {
      case "FiberSet"          => s.fiberSet.remove(e)
      case "WeakConcurrentBag" => s.bag.remove(e)
      case "SyncWeakHashSet"   => s.syncSet.remove(e)
    }
  }

  @Benchmark
  def childrenIterate(s: ChildrenProfile): Int = {
    var count = 0
    struct match {
      case "FiberSet" =>
        val it = s.fiberSet.iterator; while (it.hasNext) { it.next(); count += 1 }
      case "WeakConcurrentBag" =>
        val it = s.bag.iterator; while (it.hasNext) { it.next(); count += 1 }
      case "SyncWeakHashSet" =>
        val it = s.syncSet.iterator; while (it.hasNext) { it.next(); count += 1 }
    }
    count
  }

  @Benchmark
  def childrenChurn(s: ChildrenProfile): Unit =
    if (Random.nextInt(100) < 70) childrenAdd(s) else childrenRemove(s)
}
