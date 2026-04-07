package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import java.util.concurrent.{Semaphore => JSemaphore}

/**
 * Benchmarks for ZIO Semaphore under various contention scenarios.
 *
 * Compares ZIO Semaphore against JDK Semaphore and Cats Effect Semaphore across
 * different fiber counts and permit configurations.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 3)
@Measurement(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(3)
class SemaphoreContentionBenchmark {

  @Param(Array("2", "10", "50", "100"))
  var fibers: Int = _

  @Param(Array("1", "5", "10"))
  var permits: Int = _

  val opsPerFiber: Int = 1000

  // ---------------------------------------------------------------------------
  // Single-permit (withPermit) — the most common use case
  // ---------------------------------------------------------------------------

  @Benchmark
  def zioSemaphore(): Unit =
    unsafeRun(for {
      sem   <- Semaphore.make(permits.toLong)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(opsPerFiber)(sem.withPermit(ZIO.succeed(1)))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def javaSemaphore(): Unit =
    unsafeRun(for {
      sem <- ZIO.succeed(new JSemaphore(permits))
      fiber <-
        ZIO.forkAll(List.fill(fibers)(repeat(opsPerFiber) {
          ZIO.acquireReleaseWith(ZIO.succeed(sem.acquire()))(_ => ZIO.succeed(sem.release()))(_ => ZIO.succeed(1))
        }))
      _ <- fiber.join
    } yield ())

  @Benchmark
  def catsSemaphore(): Unit = {
    import cats.effect.std.{Semaphore => CESemaphore}
    import cats.effect.unsafe.implicits.global
    import cats.effect.{IO => CIO}

    (for {
      sem  <- CESemaphore[CIO](permits.toLong)
      fibs <- CIO.parSequenceN(fibers)(List.fill(fibers)(catsRepeat(opsPerFiber)(sem.permit.use(_ => CIO(1)))))
      _    <- CIO.unit
    } yield ()).unsafeRunSync()
  }
}

/**
 * Benchmarks for the fast path (no contention).
 *
 * When permits > fibers, every acquire succeeds on the fast path. This measures
 * the overhead of the semaphore machinery itself.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 3)
@Measurement(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(3)
class SemaphoreFastPathBenchmark {

  val ops: Int = 10000

  // ---------------------------------------------------------------------------
  // No contention: permits >= fibers, so every acquire succeeds immediately
  // ---------------------------------------------------------------------------

  @Benchmark
  def zioFastPath(): Unit =
    unsafeRun(for {
      sem <- Semaphore.make(100L)
      _   <- repeat(ops)(sem.withPermit(ZIO.succeed(1)))
    } yield ())

  @Benchmark
  def javaFastPath(): Unit =
    unsafeRun(for {
      sem <- ZIO.succeed(new JSemaphore(100))
      _ <- repeat(ops)(
             ZIO.acquireReleaseWith(ZIO.succeed(sem.acquire()))(_ => ZIO.succeed(sem.release()))(_ => ZIO.succeed(1))
           )
    } yield ())

  @Benchmark
  def catsFastPath(): Unit = {
    import cats.effect.std.{Semaphore => CESemaphore}
    import cats.effect.unsafe.implicits.global
    import cats.effect.{IO => CIO}

    (for {
      sem <- CESemaphore[CIO](100L)
      _   <- catsRepeat(ops)(sem.permit.use(_ => CIO(1)))
    } yield ()).unsafeRunSync()
  }
}

/**
 * Benchmarks for multi-permit acquire (withPermits(n)).
 *
 * Tests the partial allocation path where a fiber requests multiple permits.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 3)
@Measurement(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(3)
class SemaphoreMultiPermitBenchmark {

  @Param(Array("2", "10", "50"))
  var fibers: Int = _

  @Param(Array("1", "3", "5"))
  var acquireSize: Int = _

  val totalPermits: Int = 10
  val opsPerFiber: Int  = 1000

  @Benchmark
  def zioMultiPermit(): Unit =
    unsafeRun(for {
      sem   <- Semaphore.make(totalPermits.toLong)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(opsPerFiber)(sem.withPermits(acquireSize.toLong)(ZIO.succeed(1)))))
      _     <- fiber.join
    } yield ())

  @Benchmark
  def javaMultiPermit(): Unit =
    unsafeRun(for {
      sem <- ZIO.succeed(new JSemaphore(totalPermits))
      fiber <-
        ZIO.forkAll(List.fill(fibers)(repeat(opsPerFiber) {
          ZIO.acquireReleaseWith(ZIO.succeed(sem.acquire(acquireSize)))(_ => ZIO.succeed(sem.release(acquireSize)))(_ =>
            ZIO.succeed(1)
          )
        }))
      _ <- fiber.join
    } yield ())

  @Benchmark
  def catsMultiPermit(): Unit = {
    import cats.effect.std.{Semaphore => CESemaphore}
    import cats.effect.unsafe.implicits.global
    import cats.effect.{IO => CIO}

    (for {
      sem <- CESemaphore[CIO](totalPermits.toLong)
      fibs <-
        CIO.parSequenceN(fibers)(
          List.fill(fibers)(
            catsRepeat(opsPerFiber)(sem.acquireN(acquireSize.toLong) >> CIO(1) <* sem.releaseN(acquireSize.toLong))
          )
        )
      _ <- CIO.unit
    } yield ()).unsafeRunSync()
  }
}
