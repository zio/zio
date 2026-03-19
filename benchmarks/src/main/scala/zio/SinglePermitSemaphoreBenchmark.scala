/*
 * Copyright 2023-2024 John A. De Goes and the ZIO Contributors
 * All rights reserved.
 */

package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class SinglePermitSemaphoreBenchmark {
  var semaphore: Semaphore = _

  @Setup
  def setup(): Unit = {
    implicit val u: Unsafe     = Unsafe.unsafe
    implicit val trace: Trace = Trace.empty
    semaphore = Runtime.default.unsafe.run(Semaphore.make(1L)).getOrThrow()
  }

  @Benchmark
  def zioSemaphore(): Unit = {
    implicit val u: Unsafe     = Unsafe.unsafe
    implicit val trace: Trace = Trace.empty
    Runtime.default.unsafe.run(semaphore.withPermit(ZIO.unit)).getOrThrow()
  }
}