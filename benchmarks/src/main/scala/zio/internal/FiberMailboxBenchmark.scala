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

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

/**
 * Micro-benchmarks comparing FiberMailbox against a plain ConcurrentLinkedQueue
 * for the access patterns seen in the fiber run loop.
 *
 * Run mailbox micro-benchmarks:
 * {{{
 *   sbt "benchmarks/Jmh/run -i 10 -wi 5 -f 2 zio.internal.FiberMailboxBenchmark"
 * }}}
 *
 * Run end-to-end fork/join benchmark (before vs after):
 * {{{
 *   sbt "benchmarks/Jmh/run -i 10 -wi 5 -f 1 zio.ForkJoinBenchmark"
 * }}}
 *
 * Run run-loop intensive benchmarks:
 * {{{
 *   sbt "benchmarks/Jmh/run -i 10 -wi 5 -f 1 zio.NarrowFlatMapBenchmark"
 *   sbt "benchmarks/Jmh/run -i 10 -wi 5 -f 1 zio.BroadFlatMapBenchmark"
 * }}}
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class FiberMailboxBenchmark {

  val msg: FiberMessage = FiberMessage.resumeUnit

  // --- single add + poll (~99% of fiber traffic: resume-after-async) ---

  @Benchmark
  def mailboxSingleAddPoll(bh: Blackhole): Unit = {
    val m = new FiberMailbox {}
    m.add(msg)
    bh.consume(m.poll())
  }

  @Benchmark
  def clqSingleAddPoll(bh: Blackhole): Unit = {
    val q = new ConcurrentLinkedQueue[FiberMessage]()
    q.add(msg)
    bh.consume(q.poll())
  }

  // --- burst of 4 (CLQ promotion cost; rare in practice) ---

  @Benchmark
  def mailboxBurst4(bh: Blackhole): Unit = {
    val m = new FiberMailbox {}
    m.add(msg); m.add(msg); m.add(msg); m.add(msg)
    bh.consume(m.poll()); bh.consume(m.poll())
    bh.consume(m.poll()); bh.consume(m.poll())
  }

  @Benchmark
  def clqBurst4(bh: Blackhole): Unit = {
    val q = new ConcurrentLinkedQueue[FiberMessage]()
    q.add(msg); q.add(msg); q.add(msg); q.add(msg)
    bh.consume(q.poll()); bh.consume(q.poll())
    bh.consume(q.poll()); bh.consume(q.poll())
  }

  // --- steady-state: 100 sequential add/poll pairs (fast path throughout) ---

  @Benchmark
  def mailboxSteadyState(bh: Blackhole): Unit = {
    val m = new FiberMailbox {}
    var i = 0
    while (i < 100) { m.add(msg); bh.consume(m.poll()); i += 1 }
  }

  @Benchmark
  def clqSteadyState(bh: Blackhole): Unit = {
    val q = new ConcurrentLinkedQueue[FiberMessage]()
    var i = 0
    while (i < 100) { q.add(msg); bh.consume(q.poll()); i += 1 }
  }

  // --- burst steady-state: repeated burst-of-2 cycles (promote → drain → reuse) ---
  // Models a fiber that repeatedly receives two concurrent messages (e.g. a timeout
  // racing an upstream result).  The first cycle promotes the mailbox to CLQ; all
  // subsequent cycles reuse the same CLQ object — no re-allocation per cycle.
  //
  // Allocation profile per cycle:
  //   FiberMailbox — 1st cycle: 1 CLQ + 2 nodes; subsequent cycles: 2 CLQ nodes only.
  //   ConcurrentLinkedQueue (baseline) — 2 CLQ nodes per cycle; CLQ object reused.

  @Benchmark
  def mailboxBurstSteadyState(bh: Blackhole): Unit = {
    val m = new FiberMailbox {}
    var i = 0
    while (i < 50) {
      m.add(msg); m.add(msg)
      bh.consume(m.poll()); bh.consume(m.poll())
      i += 1
    }
  }

  @Benchmark
  def clqBurstSteadyState(bh: Blackhole): Unit = {
    val q = new ConcurrentLinkedQueue[FiberMessage]()
    var i = 0
    while (i < 50) {
      q.add(msg); q.add(msg)
      bh.consume(q.poll()); bh.consume(q.poll())
      i += 1
    }
  }

  // --- post-CLQ-promotion steady state: single add/poll after first burst ---
  //
  // Once promoted to CLQ, the zero-allocation fast path is permanently lost;
  // each message costs one CLQ-node allocation.  The benchmarks above
  // (mailboxSingleAddPoll / mailboxSteadyState) start with a *fresh* mailbox,
  // so they always exercise the fast path.  This benchmark pre-warms the
  // mailbox into CLQ state first, showing the honest steady-state allocation
  // cost for a fiber that has already received a burst.
  //
  // The mailbox is held in a thread-local @State so JMH does not share it
  // across threads; we keep exactly one message in it between iterations
  // so the CLQ is never empty and state never "looks" like the null case.

  val prewarmedMailbox: FiberMailbox = {
    val m = new FiberMailbox {}
    m.add(msg); m.add(msg) // trigger CLQ promotion
    m.poll()               // drain one so CLQ has exactly one item
    m
  }

  @Benchmark
  def mailboxPostPromotionSingleAddPoll(bh: Blackhole): Unit = {
    bh.consume(prewarmedMailbox.poll()) // poll the one message (CLQ path)
    prewarmedMailbox.add(msg)           // re-add so next iteration is identical
  }

  // --- isEmpty (called before every effect step in the run loop) ---

  @Benchmark
  def mailboxIsEmpty(bh: Blackhole): Unit =
    bh.consume(FiberMailboxBenchmark.emptyMailbox.isEmpty)

  @Benchmark
  def clqIsEmpty(bh: Blackhole): Unit =
    bh.consume(FiberMailboxBenchmark.emptyCLQ.isEmpty)
}

object FiberMailboxBenchmark {
  val emptyMailbox = new FiberMailbox {}
  val emptyCLQ     = new ConcurrentLinkedQueue[FiberMessage]()
}
