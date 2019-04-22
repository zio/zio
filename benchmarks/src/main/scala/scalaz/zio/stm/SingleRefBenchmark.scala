package scalaz.zio.stm

import scalaz.zio._

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class SingleRefBenchmark {
  @Param(Array("10"))
  var fibers: Int = _

  @Param(Array("1000"))
  var ops: Int = _

  @Benchmark
  def refContention() =
    unsafeRun(for {
      ref   <- Ref.make(0)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(ref.update(_ + 1))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def trefContention() =
    unsafeRun(for {
      tref  <- TRef.make(0).commit
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(tref.update(_ + 1).commit)))
      _     <- fiber.join
    } yield ())
}
