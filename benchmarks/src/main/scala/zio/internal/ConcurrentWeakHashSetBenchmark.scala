package zio.internal

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import org.springframework.util.{ConcurrentReferenceHashMap => SpringConcurrentReferenceHashMap}

import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit

case class TestKey(name: Int)

abstract class ConcurrentWeakHashSetBenchmarkBase {
  protected val javaSet: util.Set[TestKey] = createWeakJavaSet()
  private def createWeakJavaSet() =
    Collections.synchronizedSet(Collections.newSetFromMap(new util.WeakHashMap[TestKey, java.lang.Boolean]()))

  protected val zioSet: ConcurrentWeakHashSet[TestKey] = createWeakZioSet()
  private def createWeakZioSet()                       = new ConcurrentWeakHashSet[TestKey]()
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
private[this] class ConcurrentWeakHashSetAddBenchmark extends ConcurrentWeakHashSetBenchmarkBase {

  private var idx = -1
  private val springSet = new SpringConcurrentReferenceHashMap[TestKey, java.lang.Boolean](16, SpringConcurrentReferenceHashMap.ReferenceType.WEAK)

  @Benchmark
  def javaAdd(blackhole: Blackhole): Unit = {
    idx += 1
    blackhole.consume(javaSet.add(TestKey(idx)))
  }

  @Benchmark
  def springAdd(blackhole: Blackhole): Unit = {
    idx += 1
    blackhole.consume(springSet.put(TestKey(idx), java.lang.Boolean.TRUE))
  }

  @Benchmark
  def zioAdd(blackhole: Blackhole): Unit = {
    idx += 1
    blackhole.consume(zioSet.add(TestKey(idx)))
  }

}

//
// Quick check up on what's the current state of 'createWeakJavaSet'
//
//@BenchmarkMode(Array(Mode.Throughput))
//@OutputTimeUnit(TimeUnit.MICROSECONDS)
//@Warmup(iterations = 5, time = 1)
//@Measurement(iterations = 5, time = 1)
//@Fork(1)
//@State(Scope.Benchmark)
//private class ConcurrentWeakHashSetRemoveBenchmark extends ConcurrentWeakHashSetBenchmarkBase {
//
//  private val collectionSize = 100
//
//  @Setup(Level.Iteration)
//  def setup(): Unit =
//    (0 to collectionSize).foreach(it => javaSet.add(new TestKey(it)))
//
//  @Benchmark
//  def javaRemove(): Unit =
//    (0 to collectionSize).foreach(it => javaSet.remove(new TestKey(it)))
//
//}
//
//@BenchmarkMode(Array(Mode.Throughput))
//@OutputTimeUnit(TimeUnit.MICROSECONDS)
//@Warmup(iterations = 5, time = 1)
//@Measurement(iterations = 5, time = 1)
//@Fork(1)
//@State(Scope.Thread)
//private class ConcurrentWeakHashSetIteratorBenchmark extends ConcurrentWeakHashSetBenchmarkBase {
//
//  (0 to 1_000).foreach(it => javaSet.add(new TestKey(it)))
//
//  @Benchmark
//  def javaIterator(blackhole: Blackhole): Unit =
//    javaSet.forEach(it => blackhole.consume(it))
//
//}
