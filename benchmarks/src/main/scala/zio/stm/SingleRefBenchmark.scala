package zio.stm

import org.openjdk.jmh.annotations._
import zio.IOBenchmarks._
import zio._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(1)
class SingleRefBenchmark {
  @Param(Array("10"))
  var fibers: Int = _

  @Param(Array("1000"))
  var ops: Int = _

  @Benchmark
  def refContention(): Unit =
    unsafeRun(for {
      ref   <- Ref.make(0)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(ref.update(_ + 1))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def trefContention(): Unit =
    unsafeRun(for {
      tref  <- TRef.make(0).commit
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(tref.update(_ + 1).commit)))
      _     <- fiber.join
    } yield ())
}
