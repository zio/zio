package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Measures `withPermit` with real suspended fibers, in the regime where permits
 * are scarcer than fibers so that most acquisitions have to queue.
 *
 * This exists because `SemaphorePermitBenchmark` is too noisy to resolve
 * changes to the contended path: its 10-fiber rows come back with error bars
 * around 20%, which is wider than any plausible improvement. Three things are
 * done differently here:
 *
 *   - The effect each fiber runs is built once in `@Setup` rather than inside
 *     the measured op. `repeat(n)` chains `n` effects with `*>`, so building it
 *     per op allocated on the order of a thousand `FlatMap` nodes per fiber and
 *     put that allocation, and the GC it caused, inside the measurement.
 *   - The semaphore is created once per trial rather than per op, so the op is
 *     acquire/release traffic rather than construction.
 *   - The guarded effect is a counter increment rather than a `Blackhole`
 *     consume, so the body is a few nanoseconds and what dominates is the
 *     acquire/release pair and the suspension it causes.
 *
 * What remains inside the op is forking the fibers and joining them, which
 * cannot be hoisted: the contention being measured only exists while several
 * fibers are running at once. [[baseline]] measures exactly that much with the
 * semaphore removed, so the difference is what acquisition costs.
 *
 * Run this with at least 5 forks. The remaining variance is between forks
 * rather than within them -- how the JIT and the scheduler happen to settle for
 * a given JVM -- so iterations do not damp it but forks do. At `-f 2` the
 * single-permit row lands around 16% error, at `-f 5` around 2%:
 *
 * {{{
 * benchmarks/jmh:run -f 5 -wi 10 -i 10 zio.SemaphoreContendedBenchmark
 * }}}
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 1)
@Warmup(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(2)
class SemaphoreContendedBenchmark {

  /** Fibers competing for the semaphore. */
  @Param(Array("10"))
  var fibers: Int = _

  /** Permits available; below `fibers`, so most acquisitions have to queue. */
  @Param(Array("1", "2", "5"))
  var permits: Int = _

  /** Acquisitions per fiber per op. */
  final val ops: Int = 1000

  /**
   * Incremented by the guarded effect so that neither the body nor the
   * acquisition can be optimised away, and read in `@TearDown` so the counter
   * itself stays live.
   */
  private[this] val counter = new AtomicLong(0L)

  private var withSem: List[ZIO[Any, Nothing, Unit]]    = _
  private var withoutSem: List[ZIO[Any, Nothing, Unit]] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val sem  = unsafeRun(Semaphore.make(permits.toLong))
    val body = ZIO.succeed(counter.incrementAndGet()).unit

    withSem = List.fill(fibers)(repeat(ops)(sem.withPermit(body)))
    withoutSem = List.fill(fibers)(repeat(ops)(body))
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit =
    if (counter.get() == 0L) throw new AssertionError("benchmark body never ran")

  /** Fibers contending for `permits` permits. */
  @Benchmark
  def contended(): Unit =
    unsafeRun(ZIO.forkAll(withSem).flatMap(_.join).unit)

  /**
   * The same fibers over the same number of effects with no semaphore: the
   * floor imposed by forking, scheduling and joining alone.
   */
  @Benchmark
  def baseline(): Unit =
    unsafeRun(ZIO.forkAll(withoutSem).flatMap(_.join).unit)
}
