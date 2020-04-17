package zio.stm

import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext

import cats.effect.{ ContextShift, IO => CIO }
import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._
import zio._

@State(Scope.Thread)
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
  def semaphoreContention() =
    unsafeRun(for {
      sem   <- Semaphore.make(fibers / 2L)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(ZIO.succeedNow(1)))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def tsemaphoreContention() =
    unsafeRun(for {
      sem   <- TSemaphore.make(fibers / 2L).commit
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(STM.succeedNow(1)).commit)))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def semaphoreCatsContention() = {
    import cats.effect.Concurrent
    import cats.effect.concurrent.Semaphore
    implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

    (for {
      sem   <- Semaphore(fibers / 2L)(Concurrent[CIO])
      fiber <- catsForkAll(List.fill(fibers)(catsRepeat(ops)(sem.withPermit(CIO(1)))))
      _     <- fiber.join
    } yield ()).unsafeRunSync()
  }
}
