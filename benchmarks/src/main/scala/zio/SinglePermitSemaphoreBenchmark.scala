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
    implicit val unsafe: Unsafe = Unsafe.unsafe
    semaphore = Runtime.default.unsafe.run(Semaphore.make(1L)).getOrThrow()
  }

  @Benchmark
  def zioSemaphore(): Unit = {
    implicit val unsafe: Unsafe = Unsafe.unsafe
    Runtime.default.unsafe.run(semaphore.withPermit(ZIO.unit)).getOrThrow()
  }
}