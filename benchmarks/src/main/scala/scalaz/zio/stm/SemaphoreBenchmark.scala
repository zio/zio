package scalaz.zio.stm

import scalaz.zio._

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import cats.effect.{ ContextShift, IO => CIO }
import org.openjdk.jmh.annotations._

import IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class SemaphoreBenchmark {
  @Param(Array("10"))
  var fibers: Int = _

  @Param(Array("1000"))
  var ops: Int = _

  @Benchmark
  def semaphoreContention() =
    unsafeRun(for {
      sem   <- Semaphore.make(fibers / 2L)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(ZIO.succeed(1)))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def tsemaphoreContention() =
    unsafeRun(for {
      sem   <- TSemaphore.make(fibers / 2L).commit
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(STM.succeed(1)).commit)))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def semaphoreCatsContention() = {
    import cats.effect.concurrent.Semaphore
    import cats.effect.Concurrent
    implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

    (for {
      sem   <- Semaphore(fibers / 2L)(Concurrent[CIO])
      fiber <- catsForkAll(List.fill(fibers)(catsRepeat(ops)(sem.withPermit(CIO(1)))))
      _     <- fiber.join
    } yield ()).unsafeRunSync()
  }
}
