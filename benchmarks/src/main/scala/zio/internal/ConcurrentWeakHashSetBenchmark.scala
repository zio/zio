package zio.internal

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import org.springframework.util.{ConcurrentReferenceHashMap => SpringConcurrentReferenceHashMap}

import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit

case class TestKey(name: Int)

/* Single-thread */

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
private[this] class ConcurrentWeakHashSetAddBenchmark {

  private val javaSet = Collections.synchronizedSet(Collections.newSetFromMap(new util.WeakHashMap[TestKey, java.lang.Boolean]()))
  private val springSet = new SpringConcurrentReferenceHashMap[TestKey, java.lang.Boolean](16, SpringConcurrentReferenceHashMap.ReferenceType.WEAK)
  private val zioSet = new ConcurrentWeakHashSet[TestKey]()

  private var idx = -1

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

/* Concurrent */

// TODO