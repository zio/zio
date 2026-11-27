package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class TimeoutBenchmark {

  @Param(Array("1000"))
  var size: Int = _

  @Benchmark
  def zioBaseline(): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) ZIO.succeed(i + 1).flatMap(loop)
      else ZIO.succeed(i)

    BenchmarkUtil.unsafeRun(loop(0))
  }

  @Benchmark
  def zioTimeout(): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) ZIO.succeed(i + 1).timeout(zio.Duration.fromSeconds(60)).flatMap(o => loop(o.get))
      else ZIO.succeed(i)

    BenchmarkUtil.unsafeRun(loop(0))
  }

  @Benchmark
  def zioYieldBaseline(): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) (ZIO.yieldNow *> ZIO.succeed(i + 1)).flatMap(loop)
      else ZIO.succeed(i)

    BenchmarkUtil.unsafeRun(loop(0))
  }

  @Benchmark
  def zioYieldTimeout(): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size)
        (ZIO.yieldNow *> ZIO.succeed(i + 1)).timeout(zio.Duration.fromSeconds(60)).flatMap(o => loop(o.get))
      else ZIO.succeed(i)

    BenchmarkUtil.unsafeRun(loop(0))
  }

  @Benchmark
  def catsTimeout(): Int = {
    import cats.effect.IO

    def loop(i: Int): IO[Int] =
      if (i < size) IO.delay(i + 1).timeout(FiniteDuration(60, TimeUnit.SECONDS)).flatMap(loop)
      else IO.pure(i)

    loop(0).unsafeRunSync()
  }
}
