package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import java.util.concurrent.{Semaphore => JSemaphore}

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 4, timeUnit = TimeUnit.SECONDS, time = 1)
@Warmup(iterations = 8, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(3)
class SemaphoreWithPermitBenchmark {
  @Param(Array("1", "10"))
  var fibers: Int = _

  @Param(Array("1", "5"))
  var permits: Int = _

  private val ops: Int = 1000

  @Benchmark
  def zioSemaphore(bh: Blackhole): Unit =
    unsafeRun(for {
      sem   <- Semaphore.make(permits.toLong)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(ZIO.succeed(bh.consume(1))))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def zioSemaphoreUnfair(bh: Blackhole): Unit =
    unsafeRun(for {
      sem   <- Semaphore.makeUnfair(permits.toLong)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(ZIO.succeed(bh.consume(1))))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def javaSemaphoreUnfair(bh: Blackhole): Unit =
    unsafeRun(for {
      lock <- ZIO.succeed(new JSemaphore(permits, false))
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops) {
                 ZIO.succeed {
                   lock.acquire()
                   try bh.consume(1)
                   finally lock.release()
                 }
               }))
      _ <- fiber.join
    } yield ())

  @Benchmark
  def javaSemaphoreFair(bh: Blackhole): Unit =
    unsafeRun(for {
      lock <- ZIO.succeed(new JSemaphore(permits, true))
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops) {
                 ZIO.succeed {
                   lock.acquire()
                   try bh.consume(1)
                   finally lock.release()
                 }
               }))
      _ <- fiber.join
    } yield ())

  @Benchmark
  def baselineNoSemaphore(bh: Blackhole): Unit =
    unsafeRun(for {
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(ZIO.succeed(bh.consume(1)))))
      _     <- fiber.join
    } yield ())
}
