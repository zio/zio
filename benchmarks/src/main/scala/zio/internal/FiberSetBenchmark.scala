package zio.internal

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.nowarn

@State(Scope.Benchmark)
private[this] class FiberSetAddContext {
  private val idx = new AtomicInteger(0)

  var fiberSet: FiberSet[TestFiber]                   = _
  var javaSet: util.Set[TestFiber]                    = _
  var concurrentSet: ConcurrentWeakHashSet[TestFiber] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.fiberSet = FiberSet[TestFiber](_.isAlive())
    this.javaSet =
      Collections.synchronizedSet(Collections.newSetFromMap(new util.WeakHashMap[TestFiber, java.lang.Boolean]()))
    this.concurrentSet = ConcurrentWeakHashSet[TestFiber]()
  }

  def nextFiber(): TestFiber =
    TestFiber(idx.incrementAndGet())
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 2, time = 2)
@Fork(1)
private[this] class FiberSetAddBenchmark {

  @Benchmark
  def fiberSetAddSerial(ctx: FiberSetAddContext): Unit =
    ctx.fiberSet.add(ctx.nextFiber())

  @Threads(6)
  @Benchmark
  def fiberSetAddConcurrent(ctx: FiberSetAddContext): Unit =
    ctx.fiberSet.add(ctx.nextFiber())

  @Benchmark
  def javaWeakSetAddSerial(ctx: FiberSetAddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.javaSet.add(ctx.nextFiber()))

  @Threads(6)
  @Benchmark
  def javaWeakSetAddConcurrent(ctx: FiberSetAddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.javaSet.add(ctx.nextFiber()))

  @Benchmark
  def concurrentWeakHashSetAddSerial(ctx: FiberSetAddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.concurrentSet.add(ctx.nextFiber()))

  @Threads(6)
  @Benchmark
  def concurrentWeakHashSetAddConcurrent(ctx: FiberSetAddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.concurrentSet.add(ctx.nextFiber()))
}

@State(Scope.Benchmark)
private[this] class FiberSetRemoveContext {
  private val sampleSize = 100000
  private val idx        = new AtomicInteger(sampleSize)
  private val values     = (0 until sampleSize).map(TestFiber).toArray

  var fiberSet: FiberSet[TestFiber]                   = _
  var javaSet: util.Set[TestFiber]                    = _
  var concurrentSet: ConcurrentWeakHashSet[TestFiber] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.fiberSet = FiberSet[TestFiber](_.isAlive())
    this.javaSet =
      Collections.synchronizedSet(Collections.newSetFromMap(new util.WeakHashMap[TestFiber, java.lang.Boolean]()))
    this.concurrentSet = ConcurrentWeakHashSet[TestFiber]()

    import scala.jdk.CollectionConverters._
    this.values.foreach(this.fiberSet.add)
    this.javaSet.addAll(this.values.toSet.asJava): @nowarn("msg=JavaConverters")
    this.concurrentSet.addAll(this.values)
  }

  def nextFiber(): TestFiber =
    TestFiber(idx.incrementAndGet())
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 2, time = 2)
@Fork(1)
private[this] class FiberSetRemoveBenchmark {

  @Benchmark
  def fiberSetRemoveSerial(ctx: FiberSetRemoveContext): Unit =
    ctx.fiberSet.remove(ctx.nextFiber())

  @Threads(6)
  @Benchmark
  def fiberSetRemoveConcurrent(ctx: FiberSetRemoveContext): Unit =
    ctx.fiberSet.remove(ctx.nextFiber())

  @Benchmark
  def javaWeakSetRemoveSerial(ctx: FiberSetRemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.javaSet.remove(ctx.nextFiber()))

  @Threads(6)
  @Benchmark
  def javaWeakSetRemoveConcurrent(ctx: FiberSetRemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.javaSet.remove(ctx.nextFiber()))

  @Benchmark
  def concurrentWeakHashSetRemoveSerial(ctx: FiberSetRemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.concurrentSet.remove(ctx.nextFiber()))

  @Threads(6)
  @Benchmark
  def concurrentWeakHashSetRemoveConcurrent(ctx: FiberSetRemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.concurrentSet.remove(ctx.nextFiber()))
}

@State(Scope.Benchmark)
private[this] class FiberSetIterateContext {
  private val sampleSize = 1000
  private val values     = (0 until sampleSize).map(TestFiber).toArray

  var fiberSet: FiberSet[TestFiber]                   = _
  var javaSet: util.Set[TestFiber]                    = _
  var concurrentSet: ConcurrentWeakHashSet[TestFiber] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.fiberSet = FiberSet[TestFiber](_.isAlive())
    this.javaSet =
      Collections.synchronizedSet(Collections.newSetFromMap(new util.WeakHashMap[TestFiber, java.lang.Boolean]()))
    this.concurrentSet = ConcurrentWeakHashSet[TestFiber]()

    import scala.jdk.CollectionConverters._
    this.values.foreach(this.fiberSet.add)
    this.javaSet.addAll(this.values.toSet.asJava): @nowarn("msg=JavaConverters")
    this.concurrentSet.addAll(this.values)
  }
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 2, time = 2)
@Fork(1)
private[this] class FiberSetIterateBenchmark {

  @Benchmark
  def fiberSetIterateSerial(ctx: FiberSetIterateContext, blackhole: Blackhole): Unit =
    ctx.fiberSet.iterator.foreach(blackhole.consume)

  @Threads(6)
  @Benchmark
  def fiberSetIterateConcurrent(ctx: FiberSetIterateContext, blackhole: Blackhole): Unit =
    ctx.fiberSet.iterator.foreach(blackhole.consume)

  @Benchmark
  def javaWeakSetIterateSerial(ctx: FiberSetIterateContext, blackhole: Blackhole): Unit =
    ctx.javaSet.forEach(blackhole.consume)

  @Threads(6)
  @Benchmark
  def javaWeakSetIterateConcurrent(ctx: FiberSetIterateContext, blackhole: Blackhole): Unit =
    ctx.javaSet.forEach(blackhole.consume)

  @Benchmark
  def concurrentWeakHashSetIterateSerial(ctx: FiberSetIterateContext, blackhole: Blackhole): Unit =
    ctx.concurrentSet.foreach(blackhole.consume)

  @Threads(6)
  @Benchmark
  def concurrentWeakHashSetIterateConcurrent(ctx: FiberSetIterateContext, blackhole: Blackhole): Unit =
    ctx.concurrentSet.foreach(blackhole.consume)
}

private[this] final case class TestFiber(id: Int) {
  def isAlive(): Boolean =
    true
}
