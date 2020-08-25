package zio

import zio.clock.Clock
import zio.console._
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.test.environment._

object CancelableFutureSpec extends ZIOBaseSpec {

  import ZIOTag._

  def roundtrip[R, A](zio: RIO[R, A]): RIO[R, A] =
    for {
      future <- zio.toFuture
      a      <- RIO.fromFuture(_ => future)
    } yield a

  def spec =
    suite("CancelableFutureSpec")(
      testM("auto-kill regression") {
        val effect = ZIO.unit.delay(1.millisecond)

        val roundtrip = for {
          rt <- ZIO.runtime[Console with Clock]
          _  <- Task.fromFuture(_ => rt.unsafeRunToFuture(effect))
        } yield ()

        val result = roundtrip.orDie.as(0)

        assertM(Live.live(result))(equalTo(0))
      } @@ nonFlaky @@ zioTag(supervision, regression),
      testM("auto-kill regression 2") {
        val effect = clock.nanoTime.map(_.toString()).delay(10.millisecond)

        val roundtrip = for {
          rt <- ZIO.runtime[Console with Clock]
          _  <- Task.fromFuture(_ => rt.unsafeRunToFuture(effect))
        } yield ()

        val result = roundtrip.orDie.forever

        assertM(Live.live(result.timeout(1.seconds)))(isNone)
      } @@ zioTag(supervision, regression),
      testM("unsafeRunToFuture interruptibility") {
        for {
          runtime <- ZIO.runtime[Any]
          f        = runtime.unsafeRunToFuture(UIO.never)
          _       <- UIO(f.cancel())
          r       <- ZIO.fromFuture(_ => f).run
        } yield assert(r.succeeded)(isFalse) // not interrupted, as the Future fails when the effect in interrupted.
      } @@ nonFlaky @@ zioTag(interruption),
      testM("roundtrip preserves interruptibility") {
        for {
          start <- Promise.make[Nothing, Unit]
          end   <- Promise.make[Nothing, Int]
          fiber <- roundtrip((start.succeed(()) *> ZIO.infinity).onInterrupt(end.succeed(42))).fork
          _     <- start.await
          _     <- fiber.interrupt
          value <- end.await
        } yield assert(value)(equalTo(42))
      } @@ zioTag(interruption) @@ nonFlaky,
      testM("survives roundtrip without being auto-killed") {
        val exception = new Exception("Uh oh")
        val value     = 42

        for {
          failure <- roundtrip(ZIO.fail(exception)).either
          success <- roundtrip(ZIO.succeed(value)).either
        } yield assert(failure)(isLeft(equalTo(exception))) && assert(success)(isRight(equalTo(value)))
      } @@ zioTag(supervision) @@ nonFlaky,
      testM("interrupts the underlying task on cancel") {
        for {
          p  <- Promise.make[Nothing, Unit]
          p2 <- Promise.make[Nothing, Int]
          f <- (p.succeed(()) *> IO.never)
                 .onInterrupt(p2.succeed(42))
                 .toFuture
          _    <- p.await
          _    <- ZIO.fromFuture(_ => f.cancel())
          test <- p2.await
        } yield assert(test)(equalTo(42))
      } @@ zioTag(interruption) @@ nonFlaky,
      testM("cancel returns the exit reason") {
        val t = new Exception("test")

        for {
          f1 <- ZIO.succeed(42).toFuture
          f2 <- ZIO.fail(t).toFuture
          _  <- Fiber.fromFuture(f1).await
          _  <- Fiber.fromFuture(f2).await
          e1 <- ZIO.fromFuture(_ => f1.cancel())
          e2 <- ZIO.fromFuture(_ => f2.cancel())
        } yield assert(e1.succeeded)(isTrue) && assert(e2.succeeded)(isFalse)
      } @@ nonFlaky,
      testM("is a scala.concurrent.Future") {
        for {
          f <- ZIO(42).toFuture
          v <- ZIO.fromFuture(_ => f)
        } yield {
          assert(v)(equalTo(42))
        }
      }
    ) @@ zioTag(future)
}
