/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

import org.openjdk.jmh.annotations._
import zio._

import java.util.concurrent.TimeUnit

/**
 * Benchmarks comparing NioScheduler with ZScheduler
 * to demonstrate reduced park/unpark frequency and improved performance.
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class NioSchedulerBenchmarks {

  private var zScheduler: Executor = _
  private var nioScheduler: Executor = _

  @Setup
  def setup(): Unit = {
    zScheduler = Executor.makeDefault(false)
    nioScheduler = Executor.makeNio(false)
  }

  @Benchmark
  def zSchedulerSubmitThroughput(): Boolean = {
    implicit val unsafe: Unsafe = Unsafe.unsafe
    zScheduler.submit(() => ())
  }

  @Benchmark
  def nioSchedulerSubmitThroughput(): Boolean = {
    implicit val unsafe: Unsafe = Unsafe.unsafe
    nioScheduler.submit(() => ())
  }

  @Benchmark
  def zSchedulerBatchSubmit(): Int = {
    implicit val unsafe: Unsafe = Unsafe.unsafe
    var count = 0
    var i = 0
    while (i < 100) {
      if (zScheduler.submit(() => count += 1)) i += 1
    }
    count
  }

  @Benchmark
  def nioSchedulerBatchSubmit(): Int = {
    implicit val unsafe: Unsafe = Unsafe.unsafe
    var count = 0
    var i = 0
    while (i < 100) {
      if (nioScheduler.submit(() => count += 1)) i += 1
    }
    count
  }
}

/**
 * Benchmark to measure context switching overhead
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class NioSchedulerContextSwitchBenchmark {

  private var zScheduler: Executor = _
  private var nioScheduler: Executor = _

  @Setup
  def setup(): Unit = {
    zScheduler = Executor.makeDefault(false)
    nioScheduler = Executor.makeNio(false)
  }

  @Benchmark
  def zSchedulerContextSwitches(): Unit = {
    implicit val unsafe: Unsafe = Unsafe.unsafe
    val latch = new java.util.concurrent.CountDownLatch(1000)
    var i = 0
    while (i < 1000) {
      zScheduler.submit(() => latch.countDown())
      i += 1
    }
    latch.await()
  }

  @Benchmark
  def nioSchedulerContextSwitches(): Unit = {
    implicit val unsafe: Unsafe = Unsafe.unsafe
    val latch = new java.util.concurrent.CountDownLatch(1000)
    var i = 0
    while (i < 1000) {
      nioScheduler.submit(() => latch.countDown())
      i += 1
    }
    latch.await()
  }
}
