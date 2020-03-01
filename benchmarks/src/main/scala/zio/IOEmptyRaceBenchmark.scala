package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
class IOEmptyRaceBenchmark {
  @Param(Array("1000"))
  var size: Int = _

  @Benchmark
  def monixEmptyRace(): Int = {
    import monix.eval.Task

    def loop(i: Int): monix.eval.Task[Int] =
      if (i < size) Task.race(Task.never, Task.eval(i + 1)).flatMap(_ => loop(i + 1))
      else Task.pure(i)

    loop(0).runSyncUnsafe()
  }

  @Benchmark
  def catsEmptyRace(): Int = {
    import cats.effect.IO

    def loop(i: Int): IO[Int] =
      if (i < size) IO.race(IO.never, IO.delay(i + 1)).flatMap(_ => loop(i + 1))
      else IO.pure(i)

    loop(0).unsafeRunSync()
  }

  @Benchmark
  def zioEmptyRace(): Int = zioEmptyRace(IOBenchmarks)

  @Benchmark
  def zioTracedEmptyRace(): Int = zioEmptyRace(TracedRuntime)

  private[this] def zioEmptyRace(runtime: Runtime[Any]): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) IO.never.raceFirst(IO.effectTotal(i + 1)).flatMap(loop)
      else IO.succeedNow(i)

    runtime.unsafeRun(loop(0))
  }
}
