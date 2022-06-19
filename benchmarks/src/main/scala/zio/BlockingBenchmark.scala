package zio

import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
class BlockingBenchmark {

  val size = 10000

  @Benchmark
  def catsBlockingGranular(): Int = {

    val io = for {
      _ <- catsRepeat(size)(CIO.blocking(CIO.unit).flatten)
    } yield 0

    io.unsafeRunSync()
  }

  @Benchmark
  def zioBlockingGranular(): Any = {

    val io = for {
      _ <- repeat(size)(ZIO.blocking(ZIO.unit))
    } yield 0

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(io.exitCode)
    }
  }

  @Benchmark
  def zioBlockingRegion(): Any = {

    val io = for {
      _ <- ZIO.blocking(repeat(size)(ZIO.unit))
    } yield 0

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(io.exitCode)
    }
  }

  @Benchmark
  def zioBlockingOverlay(): Any = {

    val io = for {
      _ <- ZIO.blocking(repeat(size)(ZIO.blocking(ZIO.unit)))
    } yield 0

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(io.exitCode)
    }
  }
}
