/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Compares [[FiberSet]] against the two structures it replaces:
 *   - [[WeakConcurrentBag]] — used by `Fiber._roots`
 *   - `synchronizedSet(WeakHashMap)` — used by `FiberRuntime._children`
 *
 * Run with:
 * {{{
 *   sbt "benchmarks/jmh:run -prof gc zio.internal.FiberSetBenchmark"
 * }}}
 */
@State(JScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(8)
class FiberSetBenchmark {

  @Param(Array("1024"))
  var nurserySize: Int = _

  var fiberSet: FiberSet[BenchEntry]         = _
  var weakBag: WeakConcurrentBag[BenchEntry] = _
  var syncWeak: java.util.Set[BenchEntry]    = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    fiberSet = FiberSet[BenchEntry](nurserySize, concurrencyLevel = 8, isAlive = _.isAlive())
    weakBag = WeakConcurrentBag[BenchEntry](nurserySize, _.isAlive())
    syncWeak = Collections.synchronizedSet(
      Collections.newSetFromMap(new java.util.WeakHashMap[BenchEntry, java.lang.Boolean]())
    )
  }

  // ---- FiberSet -------------------------------------------------------

  @Benchmark
  def fiberSet_add(): Unit =
    fiberSet.add(BenchEntry())

  @Benchmark
  def fiberSet_addRemove(): Unit = {
    val e = BenchEntry()
    fiberSet.add(e)
    fiberSet.remove(e)
  }

  // ---- WeakConcurrentBag (baseline for _roots) -----------------------

  @Benchmark
  def weakBag_add(): Unit =
    weakBag.add(BenchEntry())

  // ---- synchronizedSet(WeakHashMap) (baseline for _children) ---------

  @Benchmark
  def syncWeak_add(): Unit =
    syncWeak.add(BenchEntry())

  @Benchmark
  def syncWeak_addRemove(): Unit = {
    val e = BenchEntry()
    syncWeak.add(e)
    syncWeak.remove(e)
  }
}

object FiberSetBenchmark {
  val alive: AtomicLong = new AtomicLong(0L)
  val dead: AtomicLong  = new AtomicLong(0L)
}

final case class BenchEntry(expiration: Long) {
  import FiberSetBenchmark._

  def isAlive(): Boolean = {
    val result = System.nanoTime() <= expiration
    if (result) alive.incrementAndGet() else dead.incrementAndGet()
    result
  }
}

object BenchEntry {
  import java.util.concurrent.ThreadLocalRandom
  def apply(): BenchEntry =
    BenchEntry(System.nanoTime() + ThreadLocalRandom.current().nextInt(100000))
}
