package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole
import zio.BenchmarkUtil._
import zio._

import java.util.concurrent.TimeUnit
import java.util.concurrent.{Semaphore => JSemaphore}

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 1)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(1)
class SemaphoreBenchmark {
  @Param(Array("1", "2", "5", "10"))
  var fibers: Int = _

  @Param(Array("1", "2", "5"))
  var permits: Int = _

  val ops: Int = 1000

  @Benchmark
  def catsSemaphore(): Unit = {
    import cats.effect.std.Semaphore
    import cats.effect.unsafe.implicits.global
    import cats.effect.{Concurrent, IO => CIO}

    (for {
      sem   <- Semaphore(permits)(Concurrent[CIO])
      fiber <- catsForkAll(Array.fill(fibers)(catsRepeat(ops)(sem.permit.use(_ => CIO(1)))))
      _     <- fiber.join
    } yield ()).unsafeRunSync()
  }

  @Benchmark
  def zioSemaphore(bh: Blackhole): Unit =
    unsafeRun(for {
      sem   <- Semaphore.make(permits.toLong)
      fiber <- ZIO.forkAllDiscard(Array.fill(fibers)(repeat(ops)(sem.withPermit(Exit.succeed(bh.consume(1))))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def javaSemaphoreFair(bh: Blackhole): Unit =
    javaSemaphore(true, bh)

  @Benchmark
  def javaSemaphoreUnfair(bh: Blackhole): Unit =
    javaSemaphore(false, bh)

  private def javaSemaphore(fair: Boolean, bh: Blackhole) =
    unsafeRun(for {
      lock <- ZIO.succeed(new JSemaphore(permits, fair))
      fiber <- ZIO.forkAllDiscard(Array.fill(fibers)(repeat(ops) {
                 ZIO.succeed {
                   lock.acquire()
                   try bh.consume(1)
                   finally lock.release()
                 }
               }))
      _ <- fiber.join
    } yield ())

}
