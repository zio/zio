package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.internal.SemaphorePlatform

import java.util.concurrent.TimeUnit

/**
 * Measures the acquire/release pair while the waiter queue is non-empty, so
 * that `drain` actually reaches the drain lock rather than stopping at its
 * empty-queue early-out.
 *
 * `SemaphoreDrainBenchmark` leaves the queue empty, so `drain` returns at
 * `waiters.peek() eq null` and never touches the lock or the request counter.
 * That makes it blind to changes in the locking protocol, which is what this
 * benchmark exists to expose.
 *
 * A single waiter asking for more permits than the semaphore will ever hold
 * keeps the queue occupied for the whole run without ever being granted, so
 * each release walks the full drain path and finds nothing to hand out.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Measurement(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 1)
@Warmup(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(2)
class SemaphoreDrainQueuedBenchmark {

  var sem: SemaphorePlatform = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    sem = new SemaphorePlatform(1L, fair = false)
    // Unsatisfiable: pins the queue non-empty without ever being granted.
    sem.enqueue(1000L)
    ()
  }

  /**
   * Unfair mode, so `tryAcquire` still succeeds despite the queued waiter and
   * the pair stays symmetric; the release then runs the full drain.
   */
  @Benchmark
  def acquireRelease(): Boolean = {
    val ok = sem.tryAcquire(1L)
    sem.release(1L)
    ok
  }
}
