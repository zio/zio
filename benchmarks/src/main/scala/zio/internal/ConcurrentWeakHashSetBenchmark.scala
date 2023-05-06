package zio.internal

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import org.springframework.util.{ConcurrentReferenceHashMap => SpringConcurrentReferenceHashMap}

import java.{lang, util}
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ConcurrentSkipListSet, TimeUnit}
import scala.jdk.CollectionConverters.{MapHasAsJava, SetHasAsJava}
import JVMConcurrentWeakSetAdapter.SetElement

import scala.collection.mutable

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
    this.zioSetBasedOnConcurrentSkipListInitializer = { _ => createZioSetBasedOnSkipList() }
    this.zioSetBasedOnConcurrentHashMapInitializer = { _ => createZioSetBasedOnConcurrentHashMap() }
    this.setupBase()
  }

  def createCachedKey(): TestKey = {
    val key = TestKey(this.idx.incrementAndGet())
    this.refs.add(key)
    key
  }
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 2, time = 2)
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

  @Benchmark
  def zioBasedOnSkipListAddSerial(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSetBasedOnConcurrentSkipList.add(ctx.createCachedKey()))

  @Threads(6)
  @Benchmark
  def zioBasedOnSkipListAddConcurrent(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSetBasedOnConcurrentSkipList.add(ctx.createCachedKey()))

  @Benchmark
  def zioBasedOnConcurrentHashMapAddSerial(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSetBasedOnConcurrentHashMap.add(ctx.createCachedKey()))

  @Threads(6)
  @Benchmark
  def zioBasedOnConcurrentHashMapAddConcurrent(ctx: AddContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSetBasedOnConcurrentHashMap.add(ctx.createCachedKey()))

}

@State(Scope.Benchmark)
private[this] class RemoveContext extends BaseContext {
  private val sampleSize             = 100_000
  private val values: Array[TestKey] = (0 to this.sampleSize).map(TestKey).toArray
  private val idx: AtomicInteger     = new AtomicInteger(this.sampleSize + 1)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.javaSetInitializer = { _ => createJavaSet(this.values) }
    this.springMapInitializer = { _ => createSpringMap(this.values) }
    this.zioSetInitializer = { _ => createZioSet(this.values) }
    this.zioSetBasedOnConcurrentSkipListInitializer = { _ => createZioSetBasedOnSkipList(this.values) }
    this.zioSetBasedOnConcurrentHashMapInitializer = { _ => createZioSetBasedOnConcurrentHashMap(this.values) }
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

  def appendNewKeyToZioSetBasedOnConcurrentSkipList(): TestKey = {
    val key = TestKey(this.idx.incrementAndGet())
    this.zioSetBasedOnConcurrentSkipList.add(key)
    key
  }

  def appendNewKeyToZioSetBasedOnConcurrentHashMap(): TestKey = {
    val key = TestKey(this.idx.incrementAndGet())
    this.zioSetBasedOnConcurrentHashMap.add(key)
    key
  }
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 2, time = 2)
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

  @Benchmark
  def zioBasedOnSkipListRemoveSerial(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSetBasedOnConcurrentSkipList.remove(ctx.appendNewKeyToZioSetBasedOnConcurrentSkipList()))

  @Threads(6)
  @Benchmark
  def zioBasedOnSkipListRemoveConcurrent(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSetBasedOnConcurrentSkipList.remove(ctx.appendNewKeyToZioSetBasedOnConcurrentSkipList()))

  @Benchmark
  def zioBasedOnConcurrentHashMapRemoveSerial(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSetBasedOnConcurrentHashMap.remove(ctx.appendNewKeyToZioSetBasedOnConcurrentHashMap()))

  @Threads(6)
  @Benchmark
  def zioBasedOnConcurrentHashMapRemoveConcurrent(ctx: RemoveContext, blackhole: Blackhole): Unit =
    blackhole.consume(ctx.zioSetBasedOnConcurrentHashMap.remove(ctx.appendNewKeyToZioSetBasedOnConcurrentHashMap()))

}

@State(Scope.Benchmark)
private[this] class IterateContext extends BaseContext {
  private val sampleSize             = 1000
  private val values: Array[TestKey] = (0 to this.sampleSize).map(TestKey).toArray

  @Setup(Level.Iteration)
  def setup(): Unit = {
    this.javaSetInitializer = { _ => createJavaSet(this.values) }
    this.springMapInitializer = { _ => createSpringMap(this.values) }
    this.zioSetInitializer = { _ => createZioSet(this.values) }
    this.zioSetBasedOnConcurrentSkipListInitializer = { _ => createZioSetBasedOnSkipList(this.values) }
    this.zioSetBasedOnConcurrentHashMapInitializer = { _ => createZioSetBasedOnConcurrentHashMap(this.values) }
    this.setupBase()
  }
}

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 2, time = 2)
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

  @Benchmark
  def zioBasedOnSkipListIterateSerial(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.zioSetBasedOnConcurrentSkipList.foreach(element => blackhole.consume(element))

  @Threads(6)
  @Benchmark
  def zioBasedOnSkipListIterateConcurrent(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.zioSetBasedOnConcurrentSkipList.foreach(element => blackhole.consume(element))

  @Benchmark
  def zioBasedOnConcurrentHashMapIterateSerial(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.zioSetBasedOnConcurrentHashMap.foreach(element => blackhole.consume(element))

  @Threads(6)
  @Benchmark
  def zioBasedOnConcurrentHashMapIterateConcurrent(ctx: IterateContext, blackhole: Blackhole): Unit =
    ctx.zioSetBasedOnConcurrentHashMap.foreach(element => blackhole.consume(element))

}

private[this] case class TestKey(name: Int)

private[this] class BaseContext {

  protected var javaSetInitializer: Unit => util.Set[TestKey] = _
  var javaSet: util.Set[TestKey]                              = _

  protected var springMapInitializer: Unit => SpringConcurrentReferenceHashMap[TestKey, Boolean] = _
  var springMap: SpringConcurrentReferenceHashMap[TestKey, Boolean]                              = _

  protected var zioSetInitializer: Unit => ConcurrentWeakHashSet[TestKey] = _
  var zioSet: ConcurrentWeakHashSet[TestKey]                              = _

  protected var zioSetBasedOnConcurrentSkipListInitializer: Unit => mutable.Set[TestKey] = _
  var zioSetBasedOnConcurrentSkipList: mutable.Set[TestKey]                              = _

  protected var zioSetBasedOnConcurrentHashMapInitializer: Unit => mutable.Set[TestKey] = _
  var zioSetBasedOnConcurrentHashMap: mutable.Set[TestKey]                              = _

  protected def setupBase(): Unit = {
    this.javaSet = this.javaSetInitializer(())
    this.springMap = this.springMapInitializer(())
    this.zioSet = this.zioSetInitializer(())
    this.zioSetBasedOnConcurrentSkipList = this.zioSetBasedOnConcurrentSkipListInitializer(())
    this.zioSetBasedOnConcurrentHashMap = this.zioSetBasedOnConcurrentHashMapInitializer(())
  }

  protected def createJavaSet(values: Array[TestKey] = new Array[TestKey](0)): util.Set[TestKey] = {
    val set = Collections.synchronizedSet(Collections.newSetFromMap(new util.WeakHashMap[TestKey, java.lang.Boolean]()))
    set.addAll(values.toSet.asJava)
    set
  }

  protected def createSpringMap(
    values: Array[TestKey] = new Array(0)
  ): SpringConcurrentReferenceHashMap[TestKey, Boolean] = {
    val map =
      new SpringConcurrentReferenceHashMap[TestKey, Boolean](16, SpringConcurrentReferenceHashMap.ReferenceType.WEAK)
    map.putAll(values.map((_, true)).toMap.asJava)
    map
  }

  protected def createZioSet(values: Array[TestKey] = new Array(0)): ConcurrentWeakHashSet[TestKey] = {
    val set = new ConcurrentWeakHashSet[TestKey]()
    set.addAll(values)
    set
  }

  protected def createZioSetBasedOnSkipList(
    values: Array[TestKey] = new Array[TestKey](0)
  ): mutable.Set[TestKey] = {
    val set = new JVMConcurrentWeakSetAdapter(
      new ConcurrentSkipListSet[SetElement[TestKey]]()
    )
    values.foreach(set.add)
    set
  }

  protected def createZioSetBasedOnConcurrentHashMap(
    values: Array[TestKey] = new Array[TestKey](0)
  ): mutable.Set[TestKey] = {
    val set = new JVMConcurrentWeakSetAdapter(
      Collections.newSetFromMap(new ConcurrentHashMap[SetElement[TestKey], lang.Boolean])
    )
    values.foreach(set.add)
    set
  }

}
