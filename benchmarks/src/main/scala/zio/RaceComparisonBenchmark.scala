package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
class RaceComparisonBenchmark {
  @Param(Array("1000"))
  var size: Int = _

  /**
   * Benchmark to compare the performance of ZIO's race implementation
   * with cats-effect's race implementation. This benchmark measures the throughput
   * of racing operations where one side completes immediately and the other
   * side never completes.
   */
  
  @Benchmark
  def catsRace(): Int = {
    import cats.effect.IO

    def loop(i: Int): IO[Int] =
      if (i < size) IO.race(IO.never, IO.delay(i + 1)).flatMap(_ => loop(i + 1))
      else IO.pure(i)

    loop(0).unsafeRunSync()
  }

  @Benchmark
  def zioRace(): Int = zioRace(BenchmarkUtil)

  private[this] def zioRace(runtime: Runtime[Any]): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) ZIO.never.race(ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
  }
}