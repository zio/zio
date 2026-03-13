package zio.internal

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

import zio._
import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.infra.results.I_Result

/**
 * jcstress tests that stress ZScheduler (default executor) under yield and
 * concurrent submit. Run with: coreTestsJVM/jcstress:run -t 8 Use -t &lt;N&gt;
 * with N = logical CPU count (e.g. cores+2) to stress atomics without
 * oversaturating.
 */
object ZSchedulerConcurrencyTests {

  private val runtime = Runtime.default
  private val Iters   = 200

  /**
   * Multiple actors each run a ZIO that repeatedly increments a shared Ref and
   * yields. Arbiter checks total count equals expected (no lost updates, no
   * duplicates).
   */
  @JCStressTest
  @Outcome(id = Array("800"), expect = Expect.ACCEPTABLE, desc = "All increments applied")
  @Outcome(expect = Expect.FORBIDDEN, desc = "Lost or duplicate update")
  @State
  class YieldAndCount {
    val ref: zio.Ref[Int] = Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(zio.Ref.make(0)).getOrThrowFiberFailure()
    }

    @Actor
    def actor1(): Unit =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe
          .run(
            ZIO.foreachDiscard(1 to Iters)(_ => ZIO.yieldNow *> ref.update(_ + 1))
          )
          .getOrThrowFiberFailure()
      }

    @Actor
    def actor2(): Unit =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe
          .run(
            ZIO.foreachDiscard(1 to Iters)(_ => ZIO.yieldNow *> ref.update(_ + 1))
          )
          .getOrThrowFiberFailure()
      }

    @Actor
    def actor3(): Unit =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe
          .run(
            ZIO.foreachDiscard(1 to Iters)(_ => ZIO.yieldNow *> ref.update(_ + 1))
          )
          .getOrThrowFiberFailure()
      }

    @Actor
    def actor4(): Unit =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe
          .run(
            ZIO.foreachDiscard(1 to Iters)(_ => ZIO.yieldNow *> ref.update(_ + 1))
          )
          .getOrThrowFiberFailure()
      }

    @Arbiter
    def arbiter(r: I_Result): Unit =
      Unsafe.unsafe { implicit u =>
        r.r1 = runtime.unsafe.run(ref.get).getOrThrowFiberFailure()
      }
  }

  private val SubmitIters  = 200
  private val SubmitActors = 4
  private val SubmitTotal  = SubmitIters * SubmitActors
  private val ArbiterWaitNanos =
    30L * 1000 * 1000 * 1000

  @JCStressTest
  @Outcome(id = Array("800"), expect = Expect.ACCEPTABLE, desc = "All submitted runnables executed")
  @Outcome(expect = Expect.FORBIDDEN, desc = "Lost or extra executions")
  @State
  class SubmitOnlyCount {
    val executor: Executor = Executor.makeDefault(false)
    val count: AtomicLong  = new AtomicLong(0L)

    @Actor
    def actor1(): Unit = submitMany(SubmitIters)

    @Actor
    def actor2(): Unit = submitMany(SubmitIters)

    @Actor
    def actor3(): Unit = submitMany(SubmitIters)

    @Actor
    def actor4(): Unit = submitMany(SubmitIters)

    def submitMany(n: Int): Unit =
      Unsafe.unsafe { implicit u =>
        var i = 0
        while (i < n) {
          executor.submit(new Runnable {
            override def run(): Unit = { count.incrementAndGet(); () }
          })
          i += 1
        }
      }

    @Arbiter
    def arbiter(r: I_Result): Unit = {
      val deadline = java.lang.System.nanoTime() + ArbiterWaitNanos
      while (count.get() != SubmitTotal && java.lang.System.nanoTime() < deadline) {
        LockSupport.parkNanos(1000 * 1000)
      }
      r.r1 = count.get().toInt
    }
  }
}
