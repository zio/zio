package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import java.util.concurrent.{Semaphore => JSemaphore}

/**
 * Compares ZIO's `Semaphore` against `java.util.concurrent.Semaphore` across
 * the uncontended (`fibers <= permits`) and contended (`fibers > permits`)
 * regimes.
 *
 * See https://github.com/zio/zio/issues/9093.
 *
 * ==Reading the Java rows==
 *
 * The two implementations do fundamentally different things when a permit is
 * not available. ZIO's suspends the *fiber* and frees the carrier thread;
 * `java.util.concurrent.Semaphore` parks the *thread*. That makes a direct
 * comparison sensitive to how the Java one is driven, so both framings are
 * measured here and they answer different questions:
 *
 *   - [[javaSemaphoreThreads]] and [[javaSemaphoreThreadsUnfair]] run it the
 *     way it is meant to be run, on plain threads with no runtime involved.
 *     These are the honest targets: what the same hardware can do with a mature
 *     semaphore and no fibers. Both policies are measured because #9093 names
 *     the unfair one specifically, and under contention they are far apart --
 *     at ten threads over one permit, barging is worth about 5x.
 *   - [[javaSemaphoreOnFibers]] calls it from inside a fiber, which is what
 *     application code would be doing if it reached for the Java class instead
 *     of ZIO's. Blocking a scheduler worker there is the thing ZIO's semaphore
 *     exists to avoid, and this row shows what it costs.
 *
 * An earlier version of this benchmark had only the second framing and
 * presented it as "Java's semaphore", which made ZIO's look far worse than it
 * is: at 10 fibers and 1 permit, the fiber-blocking framing measures several
 * times *slower* than ZIO's semaphore, not faster.
 *
 * ==Notes on the harness==
 *
 * The effects each fiber runs, and the semaphores themselves, are built in
 * `@Setup`. `repeat(n)` chains `n` effects with `*>`, so building it inside the
 * measured operation allocated on the order of a thousand `FlatMap` nodes per
 * fiber per op and put that allocation, and the GC it caused, inside the
 * measurement.
 *
 * The guarded effect is `ZIO.succeed(bh.consume(1))` rather than
 * `Exit.succeed(bh.consume(1))`: `Exit.succeed` takes its argument by value, so
 * the latter consumed the blackhole once when the effect was constructed and
 * then guarded a constant.
 *
 * ==Running it==
 *
 * The full `@Param` sweep at the annotated settings is 12 combinations across
 * four benchmarks and takes over an hour. For iterating on a change, name the
 * benchmarks and pin the parameters:
 *
 * {{{
 * // ~4 minutes, +/-4% on the contended row
 * benchmarks/jmh:run -f 5 -wi 8 -i 4 -p fibers=10 -p permits=1 \
 *   zio.SemaphorePermitBenchmark.zioSemaphore \
 *   zio.SemaphorePermitBenchmark.javaSemaphoreThreads
 * }}}
 *
 * Three things govern precision here, and only one of them is obvious:
 *
 *   - '''Forks matter.''' The variance is between forks rather than within
 *     them, so measurement iterations do not damp it. At `-f 2` the
 *     10-fiber/1-permit row carries around 16% error; at `-f 5`, around 3%.
 *   - '''Warmup matters more than measurement.''' Dropping to `-wi 3 -i 3`
 *     takes this row from about 4% error to 23%, while `-wi 8 -i 4` holds 4%
 *     and costs less than half the time of `-wi 10 -i 10`. Trim measurement
 *     iterations before warmup ones.
 *   - '''Between-session variance dwarfs both.''' The error bars above describe
 *     spread within one session and say nothing about reproducibility across
 *     them. The same `javaSemaphoreThreadsUnfair` rows, on the same machine and
 *     the same commit, have differed by nearly 40% between sessions -- far
 *     outside any single session's error -- with machine state, thermal
 *     conditions, background load and JDK all in play. The practical
 *     consequence: '''figures from different sessions are not comparable''', so
 *     take every row a conclusion rests on -- ZIO and Java, before and after --
 *     in one session, back to back. A number carried over from an earlier run
 *     will silently misstate a comparison by more than most changes being
 *     measured are worth.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 1)
@Warmup(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(5)
class SemaphorePermitBenchmark {

  @Param(Array("1", "2", "5", "10"))
  var fibers: Int = _

  @Param(Array("1", "2", "5"))
  var permits: Int = _

  val ops: Int = 1000

  private var fair: List[ZIO[Any, Nothing, Unit]]     = _
  private var unfair: List[ZIO[Any, Nothing, Unit]]   = _
  private var onFibers: List[ZIO[Any, Nothing, Unit]] = _
  private var javaLock: JSemaphore                    = _
  private var javaUnfairLock: JSemaphore              = _

  /**
   * Every row acquires and releases a semaphore that was created once, before
   * measurement. Construction is not what is being compared, and creating one
   * per operation would put an allocation and its initialisation into the
   * measured region for some rows and not others.
   */
  @Setup(Level.Trial)
  def setup(bh: Blackhole): Unit = {
    val body = ZIO.succeed(bh.consume(1))

    val fairSem = unsafeRun(Semaphore.make(permits.toLong))
    fair = List.fill(fibers)(repeat(ops)(fairSem.withPermit(body)))

    val unfairSem = unsafeRun(Semaphore.makeUnfair(permits.toLong))
    unfair = List.fill(fibers)(repeat(ops)(unfairSem.withPermit(body)))

    javaLock = new JSemaphore(permits, true)
    javaUnfairLock = new JSemaphore(permits, false)
    onFibers = List.fill(fibers)(repeat(ops) {
      ZIO.succeed {
        javaLock.acquire()
        try bh.consume(1)
        finally javaLock.release()
      }
    })
  }

  @Benchmark
  def zioSemaphore(): Unit =
    unsafeRun(ZIO.forkAll(fair).flatMap(_.join).unit)

  @Benchmark
  def zioSemaphoreUnfair(): Unit =
    unsafeRun(ZIO.forkAll(unfair).flatMap(_.join).unit)

  /**
   * `java.util.concurrent.Semaphore` on plain threads, with no runtime in the
   * way: what this hardware can do when nothing has to suspend a fiber.
   *
   * The threads are started and joined inside the measured operation, which is
   * the counterpart of the fiber rows forking and joining inside theirs. It is
   * not an even trade -- an OS thread costs far more to start than a fiber --
   * so this row understates the Java semaphore somewhat, and the gap it shows
   * should be read as a lower bound on what a thread-parking implementation can
   * do.
   */
  @Benchmark
  def javaSemaphoreThreads(bh: Blackhole): Unit =
    driveThreads(javaLock, bh)

  /**
   * The same on an unfair `java.util.concurrent.Semaphore`.
   *
   * This is the target named by the second goal of #9093, and it is a much
   * harder one than the fair row under contention: barging lets a thread take a
   * permit that was just released without going through the queue at all, which
   * at ten threads over one permit is worth about 5x over the fair policy.
   */
  @Benchmark
  def javaSemaphoreThreadsUnfair(bh: Blackhole): Unit =
    driveThreads(javaUnfairLock, bh)

  private def driveThreads(lock: JSemaphore, bh: Blackhole): Unit = {
    val threads = (1 to fibers).map { _ =>
      val thread = new Thread(() => {
        var i = 0
        while (i < ops) {
          lock.acquire()
          try bh.consume(1)
          finally lock.release()
          i += 1
        }
      })
      thread.start()
      thread
    }
    threads.foreach(_.join())
  }

  /**
   * `java.util.concurrent.Semaphore` acquired from inside a fiber, which parks
   * the carrier thread. Included to show the cost of that, not as the target to
   * beat.
   */
  @Benchmark
  def javaSemaphoreOnFibers(): Unit =
    unsafeRun(ZIO.forkAll(onFibers).flatMap(_.join).unit)
}
