/*
 * Copyright 2026 ZIO NIO Scheduler Contributors
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

package zio.nio.benchmarks

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import zio._
import zio.nio._
import java.nio.ByteBuffer
import java.nio.channels._
import java.util.concurrent.TimeUnit

/** JMH Benchmarks for NIO Scheduler
  *
  * These benchmarks measure the performance of the NIO scheduler compared to the default ZIO runtime scheduler across
  * various I/O scenarios.
  *
  * Run with: sbt "Test/runMain zio.nio.benchmarks.NioSchedulerBenchmarks"
  *
  * Note: Benchmarks use Unsafe runtime calls for precise measurement. All operations are wrapped with proper error
  * handling to ensure failures are reported correctly.
  */
@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(org.openjdk.jmh.annotations.Mode.Throughput, org.openjdk.jmh.annotations.Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@Threads(4)
class NioSchedulerBenchmarks {

  @Param(Array("100", "1000", "10000"))
  var batchSize: Int = _

  private var nioScheduler: zio.nio.NioScheduler                   = _
  private var selector: Selector                                   = _
  private var running: java.util.concurrent.atomic.AtomicBoolean   = _
  private var scheduledOps: java.util.concurrent.atomic.AtomicLong = _
  private var completedOps: java.util.concurrent.atomic.AtomicLong = _
  private var failedOps: java.util.concurrent.atomic.AtomicLong    = _

  @Setup
  def setup(): Unit = {
    // Setup NIO scheduler
    selector = Selector.open()
    running = new java.util.concurrent.atomic.AtomicBoolean(true)
    scheduledOps = new java.util.concurrent.atomic.AtomicLong(0)
    completedOps = new java.util.concurrent.atomic.AtomicLong(0)
    failedOps = new java.util.concurrent.atomic.AtomicLong(0)

    nioScheduler = new zio.nio.NioSchedulerImpl(
      selector,
      running,
      scheduledOps,
      completedOps,
      failedOps
    )
  }

  @TearDown
  def tearDown(): Unit = {
    running.set(false)
    selector.keys().forEach { key =>
      try key.cancel()
      catch { case _: Exception => }
    }
    selector.close()
  }

  /** Helper method to safely execute ZIO programs with proper error handling. Wraps Unsafe.unsafe calls with explicit
    * failure handling.
    */
  private def unsafeRun[A](program: ZIO[Any, Throwable, A])(implicit unsafe: zio.Unsafe): A = {
    Runtime.default.unsafe.run(program.exit).getOrThrowFiberFailure() match {
      case Exit.Success(value) => value
      case Exit.Failure(cause) => throw cause.squash
    }
  }

  /** Benchmark 1: spawn_many_local Measure: Schedule 10,000 local I/O operations Compare: NioScheduler vs default ZIO
    * runtime
    */
  @Benchmark
  def spawn_many_local_nio(): Unit = {
    val io      = ZIO.succeed(42)
    val program = nioScheduler.scheduleIO(io).repeatN(batchSize - 1)
    Unsafe.unsafe { implicit u =>
      unsafeRun(program)
    }
    ()
  }

  @Benchmark
  def spawn_many_local_default(): Unit = {
    val io      = ZIO.succeed(42)
    val program = io.repeatN(batchSize - 1)
    Unsafe.unsafe { implicit u =>
      unsafeRun(program)
    }
    ()
  }

  /** Benchmark 2: spawn_many_remote Measure: Schedule operations from external threads Compare: NIO vs default
    * scheduler
    */
  @Benchmark
  def spawn_many_remote_nio(): Unit = {
    val io      = ZIO.succeed(42).fork
    val program = nioScheduler.scheduleIO(io).repeatN(batchSize - 1)
    Unsafe.unsafe { implicit u =>
      unsafeRun(program)
    }
    ()
  }

  @Benchmark
  def spawn_many_remote_default(): Unit = {
    val io      = ZIO.succeed(42).fork
    val program = io.repeatN(batchSize - 1)
    Unsafe.unsafe { implicit u =>
      unsafeRun(program)
    }
    ()
  }

  /** Benchmark 3: ping_pong Measure: Channel read/write round-trips Compare: NIO channels vs standard Java NIO
    */
  @Benchmark
  def ping_pong_nio(): Unit = {
    val data    = "test data"
    val channel = Channels.newChannel(new java.io.ByteArrayInputStream(data.getBytes))

    val program = nioScheduler
      .scheduleReadable(channel) { ch =>
        val buffer = ByteBuffer.allocate(1024)
        ch.read(buffer)
        buffer.flip()
        new String(buffer.array(), 0, buffer.remaining())
      }
      .repeatN(batchSize / 100) // Fewer iterations for I/O ops

    Unsafe.unsafe { implicit u =>
      unsafeRun(program)
    }
    ()
  }

  @Benchmark
  def ping_pong_standard(): Unit = {
    val data    = "test data"
    val channel = Channels.newChannel(new java.io.ByteArrayInputStream(data.getBytes))

    val program = ZIO
      .attempt {
        val buffer = ByteBuffer.allocate(1024)
        channel.read(buffer)
        buffer.flip()
        new String(buffer.array(), 0, buffer.remaining())
      }
      .repeatN(batchSize / 100)

    Unsafe.unsafe { implicit u =>
      unsafeRun(program)
    }
    ()
  }

  /** Benchmark 4: yield_many Measure: Batch scheduling throughput
    */
  @Benchmark
  def yield_many_nio(): Unit = {
    val ios     = Chunk.fill(batchSize)(ZIO.succeed(1))
    val program = nioScheduler.scheduleAll(ios)
    Unsafe.unsafe { implicit u =>
      unsafeRun(program)
    }
    ()
  }

  @Benchmark
  def yield_many_default(): Unit = {
    val ios     = Chunk.fill(batchSize)(ZIO.succeed(1))
    val program = ZIO.collectAll(ios)
    Unsafe.unsafe { implicit u =>
      unsafeRun(program)
    }
    ()
  }

  /** Benchmark 5: stats_overhead Measure: Overhead of statistics tracking
    */
  @Benchmark
  def stats_overhead(): Unit = {
    val program = nioScheduler.stats.repeatN(1000)
    Unsafe.unsafe { implicit u =>
      unsafeRun(program)
    }
    ()
  }
}

/** Main entry point for running benchmarks
  */
object NioSchedulerBenchmarks extends ZIOAppDefault {

  def run: ZIO[Any, Any, Any] = {
    ZIO.attempt {
      println("=" * 80)
      println("ZIO NIO Scheduler - JMH Benchmarks")
      println("=" * 80)
      println()

      val options = new OptionsBuilder()
        .include(classOf[NioSchedulerBenchmarks].getName)
        .build()

      println("Running benchmarks...")
      println()

      val runner = new Runner(options)
      runner.run()

      println()
      println("=" * 80)
      println("Benchmarks complete!")
      println("=" * 80)
      println()
      println("Results saved to JMH output files.")
      println("See BENCHMARKS.md for detailed analysis.")
    }.orDie
  }
}
