package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.internal.WeakConcurrentBag

import java.util.concurrent.TimeUnit
import scala.collection.mutable

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class WeakConcurrentBagBenchmark {
  val Ops: Int = 1 << 16
  var weakConcurrentBag: WeakConcurrentBag[Int] = _
  var mutableSet: mutable.Set[Int]              = _

  @Setup(Level.Iteration)
  def setup(): Unit =
    mutableSet = scala.collection.mutable.Set[Int]()
    weakConcurrentBag = new WeakConcurrentBag[Int](Ops)

  @Benchmark
  @OperationsPerInvocation(1 << 16)
  def weakConcurrentBagInsertion(): Unit =
    (0 until Ops).foreach(x => weakConcurrentBag.add(x))

  @Benchmark
  @OperationsPerInvocation(1 << 16)
  def weakConcurrentBagInsertionWithImmediateGc(): Unit = {
    (0 until Ops).foreach { x =>
      weakConcurrentBag.add(x)
      weakConcurrentBag.gc()
    }
  }

  @Benchmark
  @OperationsPerInvocation(1 << 16)
  def mutableSetInsertion(): Unit = {
    (0 until Ops).foreach(x =>
      mutableSet.add(x)
    )
  }
}
