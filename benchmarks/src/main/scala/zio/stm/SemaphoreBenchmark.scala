package zio.stm

import cats.effect.{IO => CIO}
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(1)
class SemaphoreBenchmark {
  @Param(Array("10"))
  var fibers: Int = _

  @Param(Array("1000"))
  var ops: Int = _

  @Benchmark
  def tsemaphoreContention(): Unit =
    unsafeRun(for {
      sem   <- TSemaphore.make(fibers / 2L).commit
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(ZIO.succeedNow(1)))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def semaphoreCatsContention(): Unit = {
    import cats.effect.Concurrent
    import cats.effect.unsafe.implicits.global
    import cats.effect.std.Semaphore

    (for {
      sem   <- Semaphore(fibers / 2L)(Concurrent[CIO])
      fiber <- catsForkAll(List.fill(fibers)(catsRepeat(ops)(sem.permit.use(_ => CIO(1)))))
      _     <- fiber.join
    } yield ()).unsafeRunSync()
  }
}
