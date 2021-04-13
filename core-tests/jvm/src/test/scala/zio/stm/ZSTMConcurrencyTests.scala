package zio.stm

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.infra.results.{I_Result, II_Result}
import zio._

object ZSTMConcurrencyTests {

  val runtime = Runtime.default

  /*
   * Tests that if acquisition of permit is interrupted either acquisition wins
   * and permit is decremented or interrupt wins and permit is unchanged. We
   * should never observed a case where acquisition fails but the number of
   * permits is still decremented.
   *
   * In this test the permit is available so the STM transaction never has to
   * suspend.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("0, 0"), expect = Expect.ACCEPTABLE, desc = "acquire wins"),
      new Outcome(id = Array("1, 1"), expect = Expect.ACCEPTABLE, desc = "interrupt wins")
    )
  )
  @State
  class ConcurrentAcquireAndInterruptDone {
    val promise   = Promise.unsafeMake[Nothing, Unit](Fiber.Id.None)
    val semaphore = runtime.unsafeRun(Semaphore.make(1L))
    var fiber     = null.asInstanceOf[Fiber[Nothing, Unit]]

    @Actor
    def actor1(): Unit = {
      val zio = semaphore.acquire.commit
      fiber = runtime.unsafeRun(zio.fork)
      runtime.unsafeRun(promise.succeed(()))
      runtime.unsafeRun(fiber.await)
      ()
    }

    @Actor
    def actor2(): Unit = {
      val zio = promise.await *> fiber.interrupt
      runtime.unsafeRun(zio)
      ()
    }

    @Arbiter
    def arbiter(r: II_Result): Unit = {
      val zio         = semaphore.permits.get.commit
      val exit        = runtime.unsafeRun(fiber.await)
      val interrupted = exit.fold(_ => true, _ => false)
      val permits     = runtime.unsafeRun(zio)
      r.r1 = if (interrupted) 1 else 0
      r.r2 = permits.toInt
    }
  }

  /*
   * Same test as above except this time the permit may not be immediately
   * available so the STM transaction may have to suspend. Again we should
   * never observe a case where the acquire fails but the number of permits is
   * still decremented.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("0, 0"), expect = Expect.ACCEPTABLE, desc = "acquire wins"),
      new Outcome(id = Array("1, 1"), expect = Expect.ACCEPTABLE, desc = "interrupt wins")
    )
  )
  @State
  class ConcurrentAcquireAndInterruptSuspend {
    val promise   = Promise.unsafeMake[Nothing, Unit](Fiber.Id.None)
    val semaphore = runtime.unsafeRun(Semaphore.make(0L))
    var fiber     = null.asInstanceOf[Fiber[Nothing, Unit]]

    @Actor
    def actor1(): Unit = {
      val zio = semaphore.acquire.commit
      fiber = runtime.unsafeRun(zio.fork)
      runtime.unsafeRun(promise.succeed(()))
      runtime.unsafeRun(fiber.await)
      ()
    }

    @Actor
    def actor2(): Unit = {
      val zio = promise.await *> fiber.interrupt
      runtime.unsafeRun(zio)
      ()
    }

    @Actor
    def actor3(): Unit = {
      val zio = semaphore.release.commit
      runtime.unsafeRun(zio)
      ()
    }

    @Arbiter
    def arbiter(r: II_Result): Unit = {
      val zio         = semaphore.permits.get.commit
      val exit        = runtime.unsafeRun(fiber.await)
      val interrupted = exit.fold(_ => true, _ => false)
      val permits     = runtime.unsafeRun(zio)
      r.r1 = if (interrupted) 1 else 0
      r.r2 = permits.toInt
    }
  }

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
    val promise   = Promise.unsafeMake[Nothing, Unit](Fiber.Id.None)
    val semaphore = runtime.unsafeRun(Semaphore.make(1L))
    var fiber     = null.asInstanceOf[Fiber[Nothing, Unit]]

    @Actor
    def actor1(): Unit = {
      val zio = semaphore.withPermit(ZIO.unit)
      fiber = runtime.unsafeRun(zio.fork)
      runtime.unsafeRun(promise.succeed(()))
      runtime.unsafeRun(fiber.await)
      ()
    }

    @Actor
    def actor2(): Unit = {
      val zio = promise.await *> fiber.interrupt
      runtime.unsafeRun(zio)
      ()
    }

    @Arbiter
    def arbiter(r: I_Result): Unit = {
      val zio     = semaphore.permits.get.commit
      val permits = runtime.unsafeRun(zio)
      r.r1 = permits.toInt
    }
  }

  /**
   * Tests the implementation of `TSemaphore#withPermitManaged`. If a permit
   * is successfully acquired it should be released no matter what.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("1"), expect = Expect.ACCEPTABLE, desc = "permit is released")
    )
  )
  @State
  class ConcurrentWithPermitManaged {
    val promise   = Promise.unsafeMake[Nothing, Unit](Fiber.Id.None)
    val semaphore = runtime.unsafeRun(Semaphore.make(1L))
    var fiber     = null.asInstanceOf[Fiber[Nothing, Unit]]

    @Actor
    def actor1(): Unit = {
      val zio = semaphore.withPermitManaged.use_(ZIO.unit)
      fiber = runtime.unsafeRun(zio.fork)
      runtime.unsafeRun(promise.succeed(()))
      runtime.unsafeRun(fiber.await)
      ()
    }

    @Actor
    def actor2(): Unit = {
      val zio = promise.await *> fiber.interrupt
      runtime.unsafeRun(zio)
      ()
    }

    @Arbiter
    def arbiter(r: I_Result): Unit = {
      val zio     = semaphore.permits.get.commit
      val permits = runtime.unsafeRun(zio)
      r.r1 = permits.toInt
    }
  }
}
