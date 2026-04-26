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

package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole
import zio.BenchmarkUtil._
import zio.internal.{FiberMailbox, FiberMessage}

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

/**
 * Benchmarks comparing [[FiberMailbox]] against the previous
 * [[java.util.concurrent.ConcurrentLinkedQueue]] for the fiber inbox.
 *
 * Run with:
 * {{{
 *   sbt "benchmarks/jmh:run -f 1 -i 5 -wi 5 FiberMailboxBenchmark"
 * }}}
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
class FiberMailboxBenchmark {

  val ops: Int = 10000

  // ---------------------------------------------------------------------------
  // Single-threaded: offer + poll cycle (the dominant fiber resume pattern)
  // ---------------------------------------------------------------------------

  /**
   * Baseline: each fiber resumption allocates a ConcurrentLinkedQueue Node.
   * This represents the old implementation.
   */
  @Benchmark
  def clq_singleThread_offerPoll(bh: Blackhole): Unit = {
    val q   = new ConcurrentLinkedQueue[AnyRef]()
    val msg = new Object()
    var i   = 0
    while (i < ops) {
      q.add(msg)
      bh.consume(q.poll())
      i += 1
    }
  }

  /**
   * New implementation: offer + poll with zero allocation on the fast path.
   */
  @Benchmark
  def mailbox_singleThread_offerPoll(bh: Blackhole): Unit = {
    val m   = new FiberMailbox()
    val msg = FiberMessage.resumeUnit
    var i   = 0
    while (i < ops) {
      m.offer(msg)
      bh.consume(m.poll())
      i += 1
    }
  }

  // ---------------------------------------------------------------------------
  // Single-threaded: isEmpty check (called in the hot drain loop)
  // ---------------------------------------------------------------------------

  @Benchmark
  def clq_singleThread_isEmpty(bh: Blackhole): Unit = {
    val q = new ConcurrentLinkedQueue[AnyRef]()
    var i = 0
    while (i < ops) {
      bh.consume(q.isEmpty)
      i += 1
    }
  }

  @Benchmark
  def mailbox_singleThread_isEmpty(bh: Blackhole): Unit = {
    val m = new FiberMailbox()
    var i = 0
    while (i < ops) {
      bh.consume(m.isEmpty)
      i += 1
    }
  }

  // ---------------------------------------------------------------------------
  // End-to-end: fiber suspension / resumption throughput
  //
  // This is the real workload: every time a ZIO fiber suspends on an async
  // boundary, one Resume message is offered to the inbox, and immediately
  // polled when the fiber wakes up.
  // ---------------------------------------------------------------------------

  @Benchmark
  def endToEnd_fiberResumptions(): Long = unsafeRun {
    ZIO
      .foreachPar(List.fill(100)(()))(_ => ZIO.yieldNow *> ZIO.unit)
      .as(0L)
  }
}
