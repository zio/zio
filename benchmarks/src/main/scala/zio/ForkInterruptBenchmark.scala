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
class ForkInterruptBenchmark {
  @Param(Array("100"))
  var size: Int = _

  @Benchmark
  def catsForkInterrupt(): Unit = {
    import cats.effect._

    def loop(i: Int): IO[Unit] =
      if (i < size) IO.never.start.flatMap((fiber: FiberIO[Nothing]) => fiber.cancel *> loop(i + 1))
      else IO.unit

    loop(0).unsafeRunSync()
  }

  @Benchmark
  def zioForkInterrupt(): Unit = zioForkInterrupt(BenchmarkUtil)

  private[this] def zioForkInterrupt(runtime: Runtime[Any]): Unit = {
    def loop(i: Int): UIO[Unit] =
      if (i < size) ZIO.never.fork.flatMap(_.interrupt *> loop(i + 1))
      else ZIO.unit

    Unsafe.unsafeCompat { implicit u =>
      runtime.unsafeRun(loop(0))
    }
  }
}
