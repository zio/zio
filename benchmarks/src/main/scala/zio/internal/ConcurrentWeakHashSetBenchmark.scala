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

[info] Benchmark                                                       Mode  Cnt      Score      Error   Units
[info] ConcurrentWeakHashSetAddBenchmark.javaAddConcurrent            thrpt    5   2001,207 Â´â”�Ĺ»  797,789  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.javaAddSerial                thrpt    5   2634,564 Â´â”�Ĺ» 2582,044  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.springAddConcurrent          thrpt    5   1954,243 Â´â”�Ĺ»  701,985  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.springAddSerial              thrpt    5   1392,801 Â´â”�Ĺ»  392,302  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.zioAddConcurrent             thrpt    5   2951,626 Â´â”�Ĺ» 4216,410  ops/ms
[info] ConcurrentWeakHashSetAddBenchmark.zioAddSerial                 thrpt    5   1773,101 Â´â”�Ĺ» 2181,676  ops/ms
[info] ConcurrentWeakHashSetIterateBenchmark.javaIterateConcurrent    thrpt    5    120,396 Â´â”�Ĺ»    3,849  ops/ms
[info] ConcurrentWeakHashSetIterateBenchmark.javaIterateSerial        thrpt    5    143,472 Â´â”�Ĺ»    0,362  ops/ms
[info] ConcurrentWeakHashSetIterateBenchmark.springIterateConcurrent  thrpt    5    997,141 Â´â”�Ĺ»   19,756  ops/ms
[info] ConcurrentWeakHashSetIterateBenchmark.springIterateSerial      thrpt    5    197,030 Â´â”�Ĺ»    0,782  ops/ms
[info] ConcurrentWeakHashSetIterateBenchmark.zioIterateConcurrent     thrpt    5    627,760 Â´â”�Ĺ»   14,633  ops/ms
[info] ConcurrentWeakHashSetIterateBenchmark.zioIterateSerial         thrpt    5    124,203 Â´â”�Ĺ»    0,854  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.javaRemoveConcurrent      thrpt    5   6236,632 Â´â”�Ĺ»   59,456  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.javaRemoveSerial          thrpt    5  21387,491 Â´â”�Ĺ»  515,603  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.springRemoveConcurrent    thrpt    5     67,402 Â´â”�Ĺ»   26,962  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.springRemoveSerial        thrpt    5     63,111 Â´â”�Ĺ»    3,307  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.zioRemoveConcurrent       thrpt    5  14710,350 Â´â”�Ĺ» 1909,388  ops/ms
[info] ConcurrentWeakHashSetRemoveBenchmark.zioRemoveSerial           thrpt    5   9719,193 Â´â”�Ĺ» 2264,788  ops/ms

 */

@State(Scope.Benchmark)
private[this] class AddContext extends BaseContext {
  private var idx: AtomicInteger                   = _
  private var refs: ConcurrentLinkedQueue[TestKey] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.idx = new AtomicInteger(-1)
    this.refs = new ConcurrentLinkedQueue[TestKey]()
    this.javaSetInitializer = { _ => createJavaSet() }
    this.springMapInitializer = { _ => createSpringMap() }
    this.zioSetInitializer = { _ => createZioSet() }
    this.setupBase()
  }

  def createCachedKey(): TestKey = {
    val key = TestKey(this.idx.incrementAndGet())
    this.refs.add(key)
    key
  }
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 10)
@Measurement(iterations = 5, time = 10)
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

@State(Scope.Benchmark)
private[this] class RemoveContext extends BaseContext {
  private val sampleSize             = 100_000
  private var values: Array[TestKey] = (0 to this.sampleSize).map(it => TestKey(it)).toArray
  private var idx: AtomicInteger     = new AtomicInteger(this.sampleSize + 1)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.javaSetInitializer = { _ => createJavaSet(this.values) }
    this.springMapInitializer = { _ => createSpringMap(this.values) }
    this.zioSetInitializer = { _ => createZioSet(this.values) }
    this.setupBase()
  }

  def appendNewKeyToJavaSet(): TestKey = {
    val key = TestKey(this.idx.incrementAndGet())
    this.javaSet.add(key)
    key
  }

  def appendNewKeyToSpringMap(): TestKey = {
    val key = TestKey(this.idx.incrementAndGet())
    this.springMap.put(key, true)
    key
  }

  def appendNewKeyToZioSet(): TestKey = {
    val key = TestKey(this.idx.incrementAndGet())
    this.zioSet.add(key)
    key
  }
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 10)
@Measurement(iterations = 5, time = 10)
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
  private val sampleSize             = 1000
  private var values: Array[TestKey] = (0 to this.sampleSize).map(it => TestKey(it)).toArray

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.javaSetInitializer = { _ => createJavaSet(this.values) }
    this.springMapInitializer = { _ => createSpringMap(this.values) }
    this.zioSetInitializer = { _ => createZioSet(this.values) }
    this.setupBase()
  }
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 10)
@Measurement(iterations = 5, time = 10)
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
  var javaSet: util.Set[TestKey]                         = _

  protected var springMapInitializer: Unit => SpringConcurrentReferenceHashMap[TestKey, Boolean] = _
  var springMap: SpringConcurrentReferenceHashMap[TestKey, Boolean] = _

  protected var zioSetInitializer: Unit => ConcurrentWeakHashSet[TestKey] = _
  var zioSet: ConcurrentWeakHashSet[TestKey] = _

  protected def setupBase(): Unit = {
    this.javaSet = this.javaSetInitializer(())
    this.springMap = this.springMapInitializer(())
    this.zioSet = this.zioSetInitializer(())
  }

  protected def createJavaSet(values: Array[TestKey] = new Array[TestKey](0)): util.Set[TestKey] = {
    val set = Collections.synchronizedSet(Collections.newSetFromMap(new util.WeakHashMap[TestKey, java.lang.Boolean]()))
    set.addAll(values.toSet.asJava)
    set
  }

  protected def createSpringMap(values: Array[TestKey] = new Array(0)): SpringConcurrentReferenceHashMap[TestKey, Boolean] = {
    val map =
      new SpringConcurrentReferenceHashMap[TestKey, Boolean](16, SpringConcurrentReferenceHashMap.ReferenceType.WEAK)
    map.putAll(values.map(it => (it, true)).toMap.asJava)
    map
  }

  protected def createZioSet(values: Array[TestKey] = new Array(0)): ConcurrentWeakHashSet[TestKey] = {
    val set = new ConcurrentWeakHashSet[TestKey]()
    set.addAll(values)
    set
  }

}
