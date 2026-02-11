package zio.internal

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

import java.util.concurrent.atomic.AtomicInteger

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class FiberSetBenchmark {

  // Mock FiberRuntime to avoid needing full ZIO runtime complexity for microbenchmark
  // We just need identity.
  // Using real Fiber.Runtime if possible, but creating them might be expensive.
  // We'll create a simple dummy if we can't easily instantiate Fiber.Runtime.
  // Since Fiber.Runtime is a class, we might need a subclass or mock.

  // Actually, we can just use `null` casted to Fiber.Runtime?
  // No, we need identity for hashcode.

  // Let's assume we can create many generic objects and cast them.
  // FiberSet uses them as Fiber.Runtime[?,?] but only calls identityHashCode.
  // It doesn't call methods on them.
  // So we can use simple Objects.

  val fiberSet = FiberSet.make[AnyRef]()
  val chmSet   = ConcurrentHashMap.newKeySet[AnyRef]()

  // Pre-allocate fibers to avoid allocation during benchmark
  val fibers   = Array.fill(100000)(new Object)
  val fiberIdx = new AtomicInteger(0)

  @Benchmark
  def addFiberSet(): Unit = {
    val idx = fiberIdx.getAndIncrement() % fibers.length
    val f   = fibers(idx)
    fiberSet.add(f)
  }

  @Benchmark
  def addCHM(): Unit = {
    val idx = fiberIdx.getAndIncrement() % fibers.length
    val f   = fibers(idx)
    chmSet.add(f)
  }

  @Benchmark
  def addRemoveFiberSet(blackhole: Blackhole): Unit = {
    val idx = fiberIdx.getAndIncrement() % fibers.length
    val f   = fibers(idx)
    fiberSet.add(f)
    fiberSet.remove(f)
    blackhole.consume(f)
  }

  @Benchmark
  def addRemoveCHM(blackhole: Blackhole): Unit = {
    val idx = fiberIdx.getAndIncrement() % fibers.length
    val f   = fibers(idx)
    chmSet.add(f)
    chmSet.remove(f)
    blackhole.consume(f)
  }
}
