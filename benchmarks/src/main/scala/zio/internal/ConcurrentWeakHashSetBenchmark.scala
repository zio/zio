package zio.internal

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import org.springframework.util.{ConcurrentReferenceHashMap => SpringConcurrentReferenceHashMap}

import java.util
import java.util.Collections
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.{MapHasAsJava, SetHasAsJava}

/*
Add:
5 x 5s x 4t
[info] Benchmark                                               Mode  Cnt     Score     Error   Units
[info] ConcurrentWeakHashSetAddBenchmark.javaAddConcurrent    thrpt    5  2422,895 Â´â”�Ĺ» 342,696  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.javaAddSerial        thrpt    5  3279,479 Â´â”�Ĺ» 512,306  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.springAddConcurrent  thrpt    5  2046,821 Â´â”�Ĺ» 414,001  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.springAddSerial      thrpt    5  2063,618 Â´â”�Ĺ» 746,382  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.zioAddConcurrent     thrpt    5  2493,805 Â´â”�Ĺ»  52,801  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.zioAddSerial         thrpt    5  2360,632 Â´â”�Ĺ» 921,255  ops/ms
 */

@State(Scope.Benchmark)
private[this] class AddContext extends BaseContext {

  private var idx: AtomicInteger                   = _
  private var refs: ConcurrentLinkedQueue[TestKey] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.javaSetInitializer = { _ => createJavaSet() }
    this.springMapInitializer = { _ => createSpringMap() }
    this.zioSetInitializer = { _ => createZioSet() }
    this.idx = new AtomicInteger(-1)
    this.refs = new ConcurrentLinkedQueue[TestKey]()
  }

  def createCachedKey(): TestKey = {
    val key = TestKey(idx.incrementAndGet())
    refs.add(key)
    key
  }

}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(1)
private[this] class ConcurrentWeakHashSetAddBenchmark {

  @Benchmark
  def javaAddSerial(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.javaSet.add(ctx.createCachedKey()))

  @Threads(6)
  @Benchmark
  def javaAddConcurrent(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.javaSet.add(ctx.createCachedKey()))

  @Benchmark
  def springAddSerial(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.springMap.put(ctx.createCachedKey(), true))

  @Threads(6)
  @Benchmark
  def springAddConcurrent(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.springMap.put(ctx.createCachedKey(), true))

  @Benchmark
  def zioAddSerial(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSet.add(ctx.createCachedKey()))

  @Threads(6)
  @Benchmark
  def zioAddConcurrent(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSet.add(ctx.createCachedKey()))

}

/*
5 x 5s x 6t
[info] Benchmark                                                     Mode  Cnt      Score     Error   Units
[info] ConcurrentWeakHashSetRemoveBenchmark.javaRemoveConcurrent    thrpt    5   6123,441 Â´â”�Ĺ»  98,916  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.javaRemoveSerial        thrpt    5  17525,706 Â´â”�Ĺ» 453,619  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.springRemoveConcurrent  thrpt    5     58,243 Â´â”�Ĺ»   5,748  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.springRemoveSerial      thrpt    5     43,929 Â´â”�Ĺ»   0,111  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.zioRemoveConcurrent     thrpt    5  14344,684 Â´â”�Ĺ» 513,895  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.zioRemoveSerial         thrpt    5  10381,796 Â´â”�Ĺ» 516,003  ops/ms
 */

@State(Scope.Benchmark)
private[this] class RemoveContext extends BaseContext {

  private val sampleSize             = 100_000
  private var values: Array[TestKey] = _
  private var idx: AtomicInteger     = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.idx = new AtomicInteger(sampleSize + 1)
    this.values = (0 to sampleSize).map(it => TestKey(it)).toArray
    this.javaSetInitializer = { _ => createJavaSet(values) }
    this.springMapInitializer = { _ => createSpringMap(values) }
    this.zioSetInitializer = { _ => createZioSet(values) }
  }

  def appendNewKeyToJavaSet(): TestKey = {
    val key = TestKey(idx.incrementAndGet())
    javaSet.add(key)
    key
  }

  def appendNewKeyToSpringMap(): TestKey = {
    val key = TestKey(idx.incrementAndGet())
    springMap.put(key, true)
    key
  }

  def appendNewKeyToZioSet(): TestKey = {
    val key = TestKey(idx.incrementAndGet())
    zioSet.add(key)
    key
  }
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(1)
private[this] class ConcurrentWeakHashSetRemoveBenchmark {

  @Benchmark
  def javaRemoveSerial(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.javaSet.remove(ctx.appendNewKeyToJavaSet()))

  @Threads(6)
  @Benchmark
  def javaRemoveConcurrent(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.javaSet.remove(ctx.appendNewKeyToJavaSet()))

  @Benchmark
  def springRemoveSerial(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.springMap.remove(ctx.appendNewKeyToSpringMap()))

  @Threads(6)
  @Benchmark
  def springRemoveConcurrent(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.springMap.remove(ctx.appendNewKeyToSpringMap()))

  @Benchmark
  def zioRemoveSerial(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSet.remove(ctx.appendNewKeyToZioSet()))

  @Threads(6)
  @Benchmark
  def zioRemoveConcurrent(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSet.remove(ctx.appendNewKeyToZioSet()))

}

@State(Scope.Benchmark)
private[this] class IterateContext extends BaseContext {

  private var values: Array[TestKey] = _
  private val sampleSize             = 1000

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.values = (0 to sampleSize).map(it => TestKey(it)).toArray
    this.javaSetInitializer = { _ => createJavaSet(values) }
    this.springMapInitializer = { _ => createSpringMap(values) }
    this.zioSetInitializer = { fn_ => createZioSet(values) }
  }
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(1)
private[this] class ConcurrentWeakHashSetIterateBenchmark {

  @Benchmark
  def javaIterateSerial(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.javaSet.forEach(element => blackhole.consume(element))

  @Threads(6)
  @Benchmark
  def javaIterateConcurrent(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.javaSet.forEach(element => blackhole.consume(element))

  @Benchmark
  def springIterateSerial(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.springMap.forEach((key, _) => blackhole.consume(key))

  @Threads(6)
  @Benchmark
  def springIterateConcurrent(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.springMap.forEach((key, _) => blackhole.consume(key))

  @Benchmark
  def zioIterateSerial(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.zioSet.foreach(element => blackhole.consume(element))

  @Threads(6)
  @Benchmark
  def zioIterateConcurrent(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.zioSet.foreach(element => blackhole.consume(element))

}

private[this] case class TestKey(name: Int)

private[this] class BaseContext {

  protected var javaSetInitializer: Unit => util.Set[TestKey] = _
  lazy val javaSet: util.Set[TestKey]                         = { javaSetInitializer.apply(()) }

  protected def createJavaSet(values: Array[TestKey] = new Array[TestKey](0)): util.Set[TestKey] = {
    val set = Collections.synchronizedSet(Collections.newSetFromMap(new util.WeakHashMap[TestKey, java.lang.Boolean]()))
    set.addAll(values.toSet.asJava)
    set
  }

  protected var springMapInitializer: Unit => SpringConcurrentReferenceHashMap[TestKey, Boolean] = _
  lazy val springMap: SpringConcurrentReferenceHashMap[TestKey, Boolean]                         = { springMapInitializer.apply(()) }

  protected def createSpringMap(
    values: Array[TestKey] = new Array[TestKey](0)
  ): SpringConcurrentReferenceHashMap[TestKey, Boolean] = {
    val map =
      new SpringConcurrentReferenceHashMap[TestKey, Boolean](16, SpringConcurrentReferenceHashMap.ReferenceType.WEAK)
    map.putAll(values.map(it => (it, true)).toMap.asJava)
    map
  }

  protected var zioSetInitializer: Unit => ConcurrentWeakHashSet[TestKey] = _
  lazy val zioSet: ConcurrentWeakHashSet[TestKey]                         = { zioSetInitializer.apply(()) }

  protected def createZioSet(values: Array[TestKey] = new Array[TestKey](0)): ConcurrentWeakHashSet[TestKey] = {
    val set = new ConcurrentWeakHashSet[TestKey]()
    set.addAll(values)
    set
  }

}
