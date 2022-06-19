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
    val promise: Promise[Nothing, Unit] = Unsafe.unsafeCompat { implicit u =>
      Promise.unsafe.make[Nothing, Unit](FiberId.None)
    }
    val semaphore: TSemaphore       = Unsafe.unsafeCompat(implicit u => runtime.unsafeRun(TSemaphore.makeCommit(1L)))
    var fiber: Fiber[Nothing, Unit] = null.asInstanceOf[Fiber[Nothing, Unit]]

    @Actor
    def actor1(): Unit = Unsafe.unsafeCompat { implicit u =>
      val zio = semaphore.withPermit(ZIO.unit)
      fiber = runtime.unsafeRun(zio.fork)
      runtime.unsafeRun(promise.succeed(()))
      runtime.unsafeRun(fiber.await)
      ()
    }

    @Actor
    def actor2(): Unit = Unsafe.unsafeCompat { implicit u =>
      val zio = promise.await *> fiber.interrupt
      runtime.unsafeRun(zio)
      ()
    }

    @Arbiter
    def arbiter(r: I_Result): Unit = Unsafe.unsafeCompat { implicit u =>
      val zio     = semaphore.permits.get.commit
      val permits = runtime.unsafeRun(zio)
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
    val promise: Promise[Nothing, Unit] = Unsafe.unsafeCompat { implicit u =>
      Promise.unsafe.make[Nothing, Unit](FiberId.None)
    }
    val semaphore: TSemaphore       = Unsafe.unsafeCompat(implicit u => runtime.unsafeRun(TSemaphore.makeCommit(1L)))
    var fiber: Fiber[Nothing, Unit] = null.asInstanceOf[Fiber[Nothing, Unit]]

    @Actor
    def actor1(): Unit = Unsafe.unsafeCompat { implicit u =>
      val zio = ZIO.scoped(semaphore.withPermitScoped)
      fiber = runtime.unsafeRun(zio.fork)
      runtime.unsafeRun(promise.succeed(()))
      runtime.unsafeRun(fiber.await)
      ()
    }

    @Actor
    def actor2(): Unit = Unsafe.unsafeCompat { implicit u =>
      val zio = promise.await *> fiber.interrupt
      runtime.unsafeRun(zio)
      ()
    }

    @Arbiter
    def arbiter(r: I_Result): Unit = Unsafe.unsafeCompat { implicit u =>
      val zio     = semaphore.permits.get.commit
      val permits = runtime.unsafeRun(zio)
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
    val inner: TRef[Boolean]       = Unsafe.unsafeCompat(implicit u => runtime.unsafeRun(TRef.makeCommit(false)))
    val outer: TRef[TRef[Boolean]] = Unsafe.unsafeCompat(implicit u => runtime.unsafeRun(TRef.makeCommit(inner)))

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
      Unsafe.unsafeCompat { implicit u =>
        runtime.unsafeRun(zio)
        ()
      }
    }

    @Arbiter
    def arbiter(r: I_Result): Unit =
      r.r1 = 0
  }
}
