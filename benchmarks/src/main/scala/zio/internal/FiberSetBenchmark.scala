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
import org.openjdk.jmh.infra.Blackhole
import zio.BenchmarkUtil._
import zio._

import java.util.{Collections, WeakHashMap}
import java.util.concurrent.TimeUnit

/**
 * Benchmarks comparing [[FiberSet]] against the previous
 * `Collections.synchronizedSet(new WeakHashMap)` for fiber-children tracking.
 *
 * Run with:
 * {{{
 *   sbt "benchmarks/jmh:run -f 1 -i 5 -wi 5 FiberSetBenchmark"
 * }}}
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
class FiberSetBenchmark {

  val ops: Int = 1000

  // -------------------------------------------------------------------------
  // Single-threaded add + iterate (the dominant fiber-fork / children-scan pattern)
  // -------------------------------------------------------------------------

  /**
   * Baseline: the previous implementation.
   */
  @Benchmark
  def syncWeakHashMap_addIterate(bh: Blackhole): Unit = {
    val s = Collections.synchronizedSet(
      Collections.newSetFromMap(new WeakHashMap[AnyRef, java.lang.Boolean]())
    )
    val elements = Array.fill(ops)(new Object())
    var i        = 0
    while (i < ops) { s.add(elements(i)); i += 1 }
    val it = s.iterator()
    while (it.hasNext) bh.consume(it.next())
  }

  /**
   * New implementation: [[FiberSet]].
   */
  @Benchmark
  def fiberSet_addIterate(bh: Blackhole): Unit = {
    val s        = new FiberSet[AnyRef]()
    val elements = Array.fill(ops)(new Object())
    var i        = 0
    while (i < ops) { s.add(elements(i)); i += 1 }
    val it = s.iterator()
    while (it.hasNext) bh.consume(it.next())
  }

  // -------------------------------------------------------------------------
  // isEmpty check (called in the hot drain loop of the fiber run-loop)
  // -------------------------------------------------------------------------

  @Benchmark
  def syncWeakHashMap_isEmpty(bh: Blackhole): Unit = {
    val s = Collections.synchronizedSet(
      Collections.newSetFromMap(new WeakHashMap[AnyRef, java.lang.Boolean]())
    )
    var i = 0
    while (i < ops) { bh.consume(s.isEmpty); i += 1 }
  }

  @Benchmark
  def fiberSet_isEmpty(bh: Blackhole): Unit = {
    val s = new FiberSet[AnyRef]()
    var i = 0
    while (i < ops) { bh.consume(s.isEmpty); i += 1 }
  }

  // -------------------------------------------------------------------------
  // End-to-end: fiber fork / join throughput (exercises children tracking)
  // -------------------------------------------------------------------------

  @Benchmark
  def endToEnd_fiberForkJoin(): Long = unsafeRun {
    ZIO.foreachPar(List.fill(100)(()))(_ => ZIO.yieldNow *> ZIO.succeed(1L)).map(_.sum)
  }
}
