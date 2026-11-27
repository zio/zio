package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.Unsafe

import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(8)
class FiberSetBenchmark {
  import FiberSetBenchmark._

  var fiberSet: FiberSet[BenchFiber]           = _
  var legacyWeakSet: java.util.Set[BenchFiber] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    fiberSet = new FiberSet[BenchFiber](64, isAliveFn)
    legacyWeakSet = Platform.newConcurrentWeakSet[BenchFiber]()(Unsafe.unsafe)
  }

  @Benchmark
  def fiberSetAddAndComplete(): Boolean = {
    val f = new BenchFiber(true)
    val r = fiberSet.add(f)
    f.alive = false
    r
  }

  @Benchmark
  def fiberSetAddAndRemove(): Boolean = {
    val f = new BenchFiber(true)
    fiberSet.add(f)
    fiberSet.remove(f)
  }

  @Benchmark
  def legacyWeakSetAddAndRemove(): Boolean = {
    val f = new BenchFiber(true)
    legacyWeakSet.add(f)
    legacyWeakSet.remove(f)
  }
}

object FiberSetBenchmark {
  final class BenchFiber(@volatile var alive: Boolean) {
    val token: Int = ThreadLocalRandom.current().nextInt()
  }

  val isAliveFn: WeakConcurrentBag.IsAlive[BenchFiber] =
    new WeakConcurrentBag.IsAlive[BenchFiber] {
      override def apply(value: BenchFiber): Boolean = value.alive
    }
}
