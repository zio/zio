package zio.stm

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.infra.results.I_Result
import zio._

object ZSTMConcurrencyTests {

  val runtime = Runtime.default

  /**
   * Tests the implementation of `TSemaphore#withPermit`. If a permit is
   * successfully acquired it should be released no matter what.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("1"), expect = Expect.ACCEPTABLE, desc = "permit is released")
    )
  )
  @State
  class ConcurrentWithPermit {
    val promise: Promise[Nothing, Unit] = Unsafe.unsafe { implicit unsafe =>
      Promise.unsafe.make[Nothing, Unit](FiberId.None)
    }
    val semaphore: TSemaphore =
      Unsafe.unsafe(implicit unsafe => runtime.unsafe.run(TSemaphore.makeCommit(1L)).getOrThrowFiberFailure())
    var fiber: Fiber[Nothing, Unit] = null.asInstanceOf[Fiber[Nothing, Unit]]

    @Actor
    def actor1(): Unit =
      Unsafe.unsafe { implicit unsafe =>
        val zio = semaphore.withPermit(ZIO.unit)
        fiber = runtime.unsafe.run(zio.fork).getOrThrowFiberFailure()
        runtime.unsafe.run(promise.succeed(())).getOrThrowFiberFailure()
        runtime.unsafe.run(fiber.await).getOrThrowFiberFailure()
        ()
      }

    @Actor
    def actor2(): Unit =
      Unsafe.unsafe { implicit unsafe =>
        val zio = promise.await *> fiber.interrupt
        runtime.unsafe.run(zio).getOrThrowFiberFailure()
        ()
      }

    @Arbiter
    def arbiter(r: I_Result): Unit =
      Unsafe.unsafe { implicit unsafe =>
        val zio     = semaphore.permits.get.commit
        val permits = runtime.unsafe.run(zio).getOrThrowFiberFailure()
        r.r1 = permits.toInt
      }
  }

  /**
   * Tests the implementation of `TSemaphore#withPermitScoped`. If a permit is
   * successfully acquired it should be released no matter what.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("1"), expect = Expect.ACCEPTABLE, desc = "permit is released")
    )
  )
  @State
  class ConcurrentWithPermitScoped {
    val promise: Promise[Nothing, Unit] = Unsafe.unsafe { implicit unsafe =>
      Promise.unsafe.make[Nothing, Unit](FiberId.None)
    }
    val semaphore: TSemaphore =
      Unsafe.unsafe(implicit unsafe => runtime.unsafe.run(TSemaphore.makeCommit(1L)).getOrThrowFiberFailure())
    var fiber: Fiber[Nothing, Unit] = null.asInstanceOf[Fiber[Nothing, Unit]]

    @Actor
    def actor1(): Unit =
      Unsafe.unsafe { implicit unsafe =>
        val zio = ZIO.scoped(semaphore.withPermitScoped)
        fiber = runtime.unsafe.run(zio.fork).getOrThrowFiberFailure()
        runtime.unsafe.run(promise.succeed(())).getOrThrowFiberFailure()
        runtime.unsafe.run(fiber.await).getOrThrowFiberFailure()
        ()
      }

    @Actor
    def actor2(): Unit =
      Unsafe.unsafe { implicit unsafe =>
        val zio = promise.await *> fiber.interrupt
        runtime.unsafe.run(zio).getOrThrowFiberFailure()
        ()
      }

    @Arbiter
    def arbiter(r: I_Result): Unit =
      Unsafe.unsafe { implicit unsafe =>
        val zio     = semaphore.permits.get.commit
        val permits = runtime.unsafe.run(zio).getOrThrowFiberFailure()
        r.r1 = permits.toInt
      }
  }

  /*
   * Tests that no transaction can be committed based on an inconsistent state.
   * We should never observed a case where the transaction fails based on a
   * value that was set by another fiber but never committed.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("0"), expect = Expect.ACCEPTABLE, desc = "permit is released")
    )
  )
  @State
  class ConcurrentGetAndSet {
    val inner: TRef[Boolean] =
      Unsafe.unsafe(implicit unsafe => runtime.unsafe.run(TRef.makeCommit(false)).getOrThrowFiberFailure())
    val outer: TRef[TRef[Boolean]] =
      Unsafe.unsafe(implicit unsafe => runtime.unsafe.run(TRef.makeCommit(inner)).getOrThrowFiberFailure())

    @Actor
    def actor1(): Unit = {
      val stm = for {
        fresh <- TRef.make(false)
        inner <- outer.get
        value <- inner.get
        _ <- if (value) ZSTM.fail("fail")
             else inner.set(true) *> outer.set(fresh)
      } yield value
      val zio = ZIO.foreachParDiscard(1 to 1000)(_ => stm.commit)
      Unsafe.unsafe { implicit unsafe =>
        runtime.unsafe.run(zio).getOrThrowFiberFailure()
        ()
      }
    }

    @Arbiter
    def arbiter(r: I_Result): Unit =
      r.r1 = 0
  }
}
