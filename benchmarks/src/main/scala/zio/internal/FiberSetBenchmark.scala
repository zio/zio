package zio.internal

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import zio.{Fiber, FiberId, FiberRefs, RuntimeFlags, Trace}

import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@State(Scope.Benchmark)
private[this] class FiberSetContext {
  @Param(Array("10000"))
  var size: Int = _

  private[this] val nextId = new AtomicInteger(0)

  var fiberSet: FiberSet                                        = _
  var weakConcurrentBag: WeakConcurrentBag[Fiber.Runtime[_, _]] = _
  var synchronizedWeakSet: util.Set[Fiber.Runtime[_, _]]        = _
  var fibers: Array[Fiber.Runtime[_, _]]                        = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    fiberSet = FiberSet(size)
    weakConcurrentBag = WeakConcurrentBag[Fiber.Runtime[_, _]](size, _.isAlive())
    synchronizedWeakSet = Collections.synchronizedSet(
      Collections.newSetFromMap(new util.WeakHashMap[Fiber.Runtime[_, _], java.lang.Boolean]())
    )
    fibers = new Array[Fiber.Runtime[_, _]](size)

    var i = 0
    while (i < fibers.length) {
      fibers(i) = newFiber()
      fiberSet.add(fibers(i))
      weakConcurrentBag.add(fibers(i))
      synchronizedWeakSet.add(fibers(i))
      i += 1
    }
  }

  def newFiber(): Fiber.Runtime[_, _] =
    FiberRuntime[Any, Any](
      FiberId.Runtime(nextId.incrementAndGet(), 0L, Trace.empty),
      FiberRefs.empty,
      RuntimeFlags.default
    )

  def nextFiber(): Fiber.Runtime[_, _] = {
    val index = Math.floorMod(nextId.incrementAndGet(), fibers.length)
    fibers(index)
  }

  def removeAndReaddFiberSet(): Boolean = {
    val fiber   = nextFiber()
    val removed = fiberSet.remove(fiber)
    fiberSet.add(fiber)
    removed
  }

  def removeAndReaddSynchronizedWeakSet(): Boolean = {
    val fiber   = nextFiber()
    val removed = synchronizedWeakSet.remove(fiber)
    synchronizedWeakSet.add(fiber)
    removed
  }

  def addWeakConcurrentBag(): Unit =
    weakConcurrentBag.add(newFiber())
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 2, time = 2)
@Fork(1)
private[this] class FiberSetAddBenchmark {

  @Benchmark
  def fiberSetAdd(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.fiberSet.add(ctx.newFiber()))

  @Benchmark
  def synchronizedWeakSetAdd(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.synchronizedWeakSet.add(ctx.newFiber()))

  @Benchmark
  def weakConcurrentBagAdd(ctx: FiberSetContext): Unit =
    ctx.addWeakConcurrentBag()

  @Threads(6)
  @Benchmark
  def fiberSetAddConcurrent(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.fiberSet.add(ctx.newFiber()))

  @Threads(6)
  @Benchmark
  def synchronizedWeakSetAddConcurrent(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.synchronizedWeakSet.add(ctx.newFiber()))

  @Threads(6)
  @Benchmark
  def weakConcurrentBagAddConcurrent(ctx: FiberSetContext): Unit =
    ctx.addWeakConcurrentBag()
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 2, time = 2)
@Fork(1)
private[this] class FiberSetRemoveBenchmark {

  @Benchmark
  def fiberSetRemove(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.removeAndReaddFiberSet())

  @Benchmark
  def synchronizedWeakSetRemove(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.removeAndReaddSynchronizedWeakSet())

  @Threads(6)
  @Benchmark
  def fiberSetRemoveConcurrent(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.removeAndReaddFiberSet())

  @Threads(6)
  @Benchmark
  def synchronizedWeakSetRemoveConcurrent(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.removeAndReaddSynchronizedWeakSet())
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 2, time = 2)
@Fork(1)
private[this] class FiberSetIterationBenchmark {

  @Benchmark
  def fiberSetIterate(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    ctx.fiberSet.foreach(fiber => blackhole.consume(fiber))

  @Benchmark
  def synchronizedWeakSetIterate(ctx: FiberSetContext, blackhole: Blackhole): Unit = {
    val iterator = ctx.synchronizedWeakSet.iterator()
    while (iterator.hasNext) blackhole.consume(iterator.next())
  }

  @Benchmark
  def weakConcurrentBagIterate(ctx: FiberSetContext, blackhole: Blackhole): Unit =
    ctx.weakConcurrentBag.iterator.foreach(fiber => blackhole.consume(fiber))
}
