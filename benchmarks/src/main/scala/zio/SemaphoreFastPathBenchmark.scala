package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole
import zio.BenchmarkUtil._
import zio.internal.SemaphorePlatform

import java.util.concurrent.TimeUnit

/**
 * Isolates the interpreter cost of an uncontended `withPermit`, which is what
 * any attempt to shorten the fast path has to move.
 *
 * ==Why a separate benchmark==
 *
 * [[SemaphorePermitBenchmark]] at `fibers = 1` already measures an uncontended
 * acquisition, but it forks a fiber and joins it around every thousand
 * acquisitions. Forking and joining cost far more than one acquisition does, so
 * a change worth a few percent per acquisition is hard to see there. This runs
 * the chain directly on the calling thread's fiber, so the measured region is
 * the acquisitions and nothing else.
 *
 * ==What the rows separate==
 *
 * Profiling the uncontended path attributes its RUNNABLE time to the run loop
 * rather than to the semaphore: the dispatch loop takes about 22% of samples,
 * the two stack-unwind loops about 11% between them, and no `SemaphorePlatform`
 * frame appears at all. The cost is the interpreter nodes `withPermits` builds,
 * so the rows here are chosen to price those nodes individually:
 *
 *   - [[withPermit]] is the thing being optimized.
 *   - [[baseline]] is the same body with no semaphore: the floor.
 *   - [[maskOnly]] is `uninterruptibleMask` plus `restore` around the body,
 *     with no semaphore at all. The difference from [[baseline]] is what the
 *     two `UpdateRuntimeFlagsWithin` nodes cost.
 *   - [[exitWithOnly]] is `exitWith` around the body. The difference from
 *     [[baseline]] is what one `FoldCauseZIO` frame plus its `Exit` allocation
 *     costs.
 *   - [[acquireReleaseOnly]] calls `tryAcquire`/`release` directly inside a
 *     single `ZIO.succeed`, with no mask and no fold. This is the semaphore
 *     work with none of the effect graph around it, so `withPermit -
 *     acquireReleaseOnly` is the whole of what the interpreter adds.
 *
 * Together those bracket the change: a fused acquire/body/release node should
 * move [[withPermit]] toward the sum of [[baseline]] and
 * [[acquireReleaseOnly]], and leave the control rows alone.
 *
 * ==What it measured when it was written==
 *
 * On 16 Xeon 8168 cores under Linux, JDK 25, at `-f 5 -wi 8 -i 5`, converting
 * each row to nanoseconds per acquisition and subtracting the baseline:
 *
 * {{{
 * baseline (body only)     3599 ops/s    27.8 ns/acq        -
 * acquireReleaseOnly       2589 ops/s    38.6 ns/acq    +10.8   semaphore work
 * exitWithOnly             2340 ops/s    42.7 ns/acq    +14.9   fold + Exit
 * maskOnly                 1310 ops/s    76.3 ns/acq    +48.5   the two flag nodes
 * withPermit                844 ops/s   118.4 ns/acq    +90.7   all of it
 * }}}
 *
 * So the `uninterruptibleMask` and its `restore` are 54% of what guarding
 * costs, against 16% for the `exitWith` fold and 12% for the semaphore's own
 * CAS and release. The parts sum to 74.3ns against 90.7ns measured; the
 * residual is interaction, more live frames and more dispatch iterations.
 *
 * That ordering is the point of this benchmark: it says to attack the flag
 * nodes first, which is not where the effort would naturally go.
 *
 * ==Running it==
 *
 * {{{
 * benchmarks/jmh:run -f 5 -wi 8 -i 5 zio.SemaphoreFastPathBenchmark
 * }}}
 *
 * The same warning as the other semaphore benchmarks applies: absolute figures
 * move between machines and sessions, so take the rows a conclusion rests on in
 * one session, back to back. The deltas above are what should reproduce, not
 * the ops/s.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 1)
@Warmup(iterations = 8, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(5)
class SemaphoreFastPathBenchmark {

  /**
   * Acquisitions per measured operation. Large enough that the fixed cost of
   * `unsafeRun` is amortized away, small enough that one operation stays short.
   */
  val ops: Int = 10000

  private var withPermitChain: ZIO[Any, Nothing, Unit]     = _
  private var baselineChain: ZIO[Any, Nothing, Unit]       = _
  private var maskChain: ZIO[Any, Nothing, Unit]           = _
  private var exitWithChain: ZIO[Any, Nothing, Unit]       = _
  private var acquireReleaseChain: ZIO[Any, Nothing, Unit] = _

  @Setup(Level.Trial)
  def setup(bh: Blackhole): Unit = {
    val body = ZIO.succeed(bh.consume(1))

    val sem = unsafeRun(Semaphore.make(1L))
    withPermitChain = repeat(ops)(sem.withPermit(body))

    baselineChain = repeat(ops)(body)
    maskChain = repeat(ops)(ZIO.uninterruptibleMask(restore => restore(body)))
    exitWithChain = repeat(ops)(body.exitWith(exit => exit))

    // The semaphore's own work with no effect graph around it: one node total.
    // Uses `SemaphorePlatform` directly, as the drain benchmarks do, rather
    // than widening `Semaphore`'s API for a benchmark's sake.
    val platform = new SemaphorePlatform(1L, true)
    acquireReleaseChain = repeat(ops) {
      ZIO.succeed {
        val acquired = platform.tryAcquire(1L)
        try bh.consume(1)
        finally if (acquired) platform.release(1L)
      }
    }
  }

  /** The path being optimized: an uncontended `withPermit`. */
  @Benchmark
  def withPermit(): Unit = unsafeRun(withPermitChain)

  /** The body alone. Everything above this is what guarding costs. */
  @Benchmark
  def baseline(): Unit = unsafeRun(baselineChain)

  /** The two `UpdateRuntimeFlagsWithin` nodes, without the semaphore. */
  @Benchmark
  def maskOnly(): Unit = unsafeRun(maskChain)

  /** One `FoldCauseZIO` frame and its `Exit`, without the semaphore. */
  @Benchmark
  def exitWithOnly(): Unit = unsafeRun(exitWithChain)

  /** The acquire/release pair with no mask and no fold around it. */
  @Benchmark
  def acquireReleaseOnly(): Unit = unsafeRun(acquireReleaseChain)
}
