package zio

import zio.test.Assertion._
import zio.test._

object CancelableFutureSpec
    extends ZIOBaseSpec(
      suite("CancelableFutureSpec")(
        testM("interrupts the underlying task on cancel") {
          for {
            p    <- Promise.make[Nothing, Unit]
            p2   <- Promise.make[Nothing, Int]
            f    <- (p.succeed(()) *> IO.never).onInterrupt(p2.succeed(42)).toFuture
            _    <- p.await
            _    <- f.cancel
            test <- p2.await
          } yield assert(test, equalTo(42))
        } @@ TestAspect.nonFlaky(10),
        testM("cancel returns the exit reason") {
          for {
            t  <- UIO(new Exception("test"))
            f1 <- ZIO.succeed(42).toFuture
            f2 <- ZIO.fail(t).toFuture
            e1 <- f1.cancel
            e2 <- f2.cancel
          } yield assert(e1.succeeded, isTrue) && assert(e2.succeeded, isFalse)
        },
        testM("is a scala.concurrent.Future") {
          for {
            f <- ZIO(42).toFuture
            v <- ZIO.fromFuture(_ => f)
          } yield {
            assert(v, equalTo(42))
          }
        }
      )
    )
