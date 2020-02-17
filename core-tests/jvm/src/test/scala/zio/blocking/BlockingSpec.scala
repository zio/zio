package zio.blocking

import java.util.concurrent.atomic.AtomicBoolean

import zio.Promise
import zio.ZIO
import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.{ UIO, ZIOBaseSpec }

object BlockingSpec extends ZIOBaseSpec {

  def spec = suite("BlockingSpec")(
    suite("Make a Blocking Service and verify that")(
      testM("effectBlocking completes successfully") {
        assertM(effectBlocking(()))(isUnit)
      },
      testM("effectBlocking runs on the blocking thread pool") {
        for {
          name <- effectBlocking(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("effectBlockingCancelable completes successfully") {
        assertM(effectBlockingCancelable(())(UIO.unit))(isUnit)
      },
      testM("effectBlockingCancelable runs on the blocking thread pool") {
        for {
          name <- effectBlockingCancelable(Thread.currentThread.getName)(UIO.unit)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("effectBlockingCancelable can be interrupted") {
        val release = new AtomicBoolean(false)
        val cancel  = UIO.effectTotal(release.set(true))
        assertM(effectBlockingCancelable(blockingAtomic(release))(cancel).timeout(Duration.Zero))(isNone)
      },
      testM("effectBlockingInterrupt completes successfully") {
        assertM(effectBlockingInterrupt(()))(isUnit)
      },
      testM("effectBlockingInterrupt runs on the blocking thread pool") {
        for {
          name <- effectBlockingInterrupt(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("effectBlockingInterrupt interrupts by using Thread interrupts") {
        // We’re setting up a Promise and fulfill it so that we’re not interrupting
        // the Fiber spawned by `effectBlockingInterrupt` before it has a change to enter
        // the try/catch block which would lead to flaky tests.
        val release = new AtomicBoolean(false)

        def sleep(before: => Unit): Unit =
          try {
            before
            Thread.sleep(60 * 1000L)
          } catch {
            case _: InterruptedException => release.set(true)
          }

        for {
          p   <- Promise.make[Nothing, Unit]
          rts <- ZIO.runtime[Any]
          f   <- effectBlockingInterrupt(sleep(rts.unsafeRunAsync_(p.succeed(())))).fork
          _   <- p.await
          _   <- f.interrupt
        } yield {
          assert(release.get())(isTrue)
        }
      }
    )
  )

  def blockingAtomic(released: AtomicBoolean): Unit =
    while (!released.get()) {
      try {
        Thread.sleep(10L)
      } catch {
        case _: InterruptedException => ()
      }
    }
}
