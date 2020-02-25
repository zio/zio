package zio

import zio.test.Assertion._
import zio.test._

object CancelableFutureSpec extends ZIOBaseSpec {

  def spec = suite("CancelableFutureSpec")(
    testM("interrupts the underlying task on cancel") {
      for {
        p  <- UIO(println("Here 1")) *> Promise.make[Nothing, Unit]
        p2 <- UIO(println("Here 2")) *> Promise.make[Nothing, Int]
        f <- (p.succeed(()) *> UIO(println("Here 3")) *> IO.never)
              .onInterrupt(UIO(println("Here 4")) *> p2.succeed(42) <* UIO(println("Here 5")))
              .toFuture
        _    <- UIO(println("Here 6")) *> p.await
        _    <- UIO(println("Here 7")) *> UIO(f.cancel())
        test <- UIO(println("Here 8")) *> p2.await <* UIO(println("Here 9"))
      } yield assert(test)(equalTo(42))
    },
    testM("cancel returns the exit reason") {
      val t = new Exception("test")

      for {
        p1 <- Promise.make[Nothing, Unit]
        p2 <- Promise.make[Nothing, Unit]
        f1 <- (ZIO.succeedNow(42) <* p1.succeed(())).toFuture
        f2 <- ZIO.failNow(t).onError(_ => p2.succeed(())).toFuture
        _  <- p1.await *> p2.await
        e1 <- ZIO.fromFuture(_ => f1.cancel())
        e2 <- ZIO.fromFuture(_ => f2.cancel())
      } yield assert(e1.succeeded)(isTrue) && assert(e2.succeeded)(isFalse)
    },
    testM("is a scala.concurrent.Future") {
      for {
        f <- ZIO(42).toFuture
        v <- ZIO.fromFuture(_ => f)
      } yield {
        assert(v)(equalTo(42))
      }
    }
  )
}
