package zio

import zio.test.Assertion._
import zio.test._

object PromiseSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec: Spec[Any, TestFailure[Any], TestSuccess] = suite("PromiseSpec")(
    testM("complete a promise using succeed") {
      for {
        p <- Promise.make[Nothing, Int]
        s <- p.succeed(32)
        v <- p.await
      } yield assert(s)(isTrue) && assert(v)(equalTo(32))
    },
    testM("complete a promise using complete") {
      for {
        p  <- Promise.make[Nothing, Int]
        r  <- Ref.make(13)
        s  <- p.complete(r.updateAndGet(_ + 1))
        v1 <- p.await
        v2 <- p.await
      } yield assert(s)(isTrue) &&
        assert(v1)(equalTo(14)) &&
        assert(v2)(equalTo(14))
    },
    testM("complete a promise using completeWith") {
      for {
        p  <- Promise.make[Nothing, Int]
        r  <- Ref.make(13)
        s  <- p.completeWith(r.updateAndGet(_ + 1))
        v1 <- p.await
        v2 <- p.await
      } yield assert(s)(isTrue) &&
        assert(v1)(equalTo(14)) &&
        assert(v2)(equalTo(15))
    },
    testM("fail a promise using fail") {
      for {
        p <- Promise.make[String, Int]
        s <- p.fail("error with fail")
        v <- p.await.run
      } yield assert(s)(isTrue) && assert(v)(fails(equalTo("error with fail")))
    } @@ zioTag(errors),
    testM("fail a promise using complete") {
      for {
        p  <- Promise.make[String, Int]
        r  <- Ref.make(List("first error", "second error"))
        s  <- p.complete(r.modify(as => (as.head, as.tail)).flip)
        v1 <- p.await.run
        v2 <- p.await.run
      } yield assert(s)(isTrue) &&
        assert(v1)(fails(equalTo("first error"))) &&
        assert(v2)(fails(equalTo("first error")))
    } @@ zioTag(errors),
    testM("fail a promise using completeWith") {
      for {
        p  <- Promise.make[String, Int]
        r  <- Ref.make(List("first error", "second error"))
        s  <- p.completeWith(r.modify(as => (as.head, as.tail)).flip)
        v1 <- p.await.run
        v2 <- p.await.run
      } yield assert(s)(isTrue) &&
        assert(v1)(fails(equalTo("first error"))) &&
        assert(v2)(fails(equalTo("second error")))
    } @@ zioTag(errors),
    testM("complete a promise twice") {
      for {
        p <- Promise.make[Nothing, Int]
        _ <- p.succeed(1)
        s <- p.complete(IO.succeed(9))
        v <- p.await
      } yield assert(s)(isFalse) && assert(v)(equalTo(1))
    },
    testM("flatMap a promise that completes") {
      def f(v: Int) =
        for {
          p <- Promise.make[Exception, Int]
          _ <- p.succeed(v * 2)
        } yield p
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.flatMap(f)
        _      <- a.succeed(1)
        result <- b.await.run
      } yield assert(result)(succeeds(equalTo(2)))
    },
    testM("flatMap a promise that fails") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.flatMap(_ => Promise.make[Exception, Int])
        _      <- a.fail(exception)
        result <- b.await.run
      } yield assert(result)(fails(equalTo(exception)))
    },
    testM("flatMap a promise that dies") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.flatMap(_ => Promise.make[Exception, Int])
        _      <- a.die(exception)
        result <- b.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("flatMap a promise that is interrupted") {
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.flatMap(_ => Promise.make[Exception, Int])
        _      <- a.interrupt
        result <- b.await.run
      } yield assert(result)(isInterrupted)
    },
    testM("flatMap with a function throwing an exception") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.flatMap[Exception, Int](_ => throw exception)
        _      <- a.succeed(1)
        result <- b.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("flatMap with a function returning a promise that fails") {
      val exception = new Exception
      val failingPromise =
        for {
          p <- Promise.make[Exception, Int]
          _ <- p.fail(exception)
        } yield p
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.flatMap(_ => failingPromise)
        _      <- a.succeed(1)
        result <- b.await.run
      } yield assert(result)(fails(equalTo(exception)))
    },
    testM("flatMap with a function returning a promise that dies") {
      val exception = new Exception
      val dyingPromise =
        for {
          p <- Promise.make[Exception, Int]
          _ <- p.die(exception)
        } yield p
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.flatMap(_ => dyingPromise)
        _      <- a.succeed(1)
        result <- b.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("flatMap with a function returning a promise that is interrupted") {
      val interruptedPromise =
        for {
          p <- Promise.make[Exception, Int]
          _ <- p.interrupt
        } yield p
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.flatMap(_ => interruptedPromise)
        _      <- a.succeed(1)
        result <- b.await.run
      } yield assert(result)(isInterrupted)
    },
    testM("interrupt a promise") {
      for {
        p <- Promise.make[Exception, Int]
        s <- p.interrupt
      } yield assert(s)(isTrue)
    } @@ zioTag(interruption),
    testM("map a promise that completes") {
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.map(_ * 2)
        _      <- a.succeed(1)
        result <- b.await.run
      } yield assert(result)(succeeds(equalTo(2)))
    },
    testM("map a promise that fails") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.map(_ * 2)
        _      <- a.fail(exception)
        result <- b.await.run
      } yield assert(result)(fails(equalTo(exception)))
    },
    testM("map a promise that dies") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.map(_ * 2)
        _      <- a.die(exception)
        result <- b.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("map with a function throwing an exception") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.map[Int](_ => throw exception)
        _      <- a.succeed(1)
        result <- b.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("map a promise that is interrupted") {
      for {
        a      <- Promise.make[Exception, Int]
        b      <- a.map(_ * 2)
        _      <- a.interrupt
        result <- b.await.run
      } yield assert(result)(isInterrupted)
    },
    testM("poll a promise that is not completed yet") {
      for {
        p       <- Promise.make[String, Int]
        attempt <- p.poll
      } yield assert(attempt)(isNone)
    },
    testM("poll a promise that is completed") {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.succeed(12)
        result <- p.poll.someOrFail("fail").flatten.run
      } yield assert(result)(succeeds(equalTo(12)))
    },
    testM("poll a promise that is failed") {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.fail("failure")
        result <- p.poll.someOrFail("fail").flatten.run
      } yield assert(result)(fails(equalTo("failure")))
    },
    testM("poll a promise that is interrupted") {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.interrupt
        result <- p.poll.someOrFail("fail").flatten.run
      } yield assert(result)(isInterrupted)
    } @@ zioTag(interruption),
    testM("isDone when a promise is completed") {
      for {
        p <- Promise.make[String, Int]
        _ <- p.succeed(0)
        d <- p.isDone
      } yield assert(d)(isTrue)
    },
    testM("isDone when a promise is failed") {
      for {
        p <- Promise.make[String, Int]
        _ <- p.fail("failure")
        d <- p.isDone
      } yield assert(d)(isTrue)
    } @@ zioTag(errors),
    testM("zip when both promises complete") {
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zip(b)
        _      <- a.succeed(1)
        _      <- b.succeed(2)
        result <- z.await.run
      } yield assert(result)(succeeds(equalTo((1, 2))))
    },
    testM("zip when this promise fails") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zip(b)
        _      <- a.fail(exception)
        _      <- b.succeed(2)
        result <- z.await.run
      } yield assert(result)(fails(equalTo(exception)))
    },
    testM("zip when the other promise fails") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zip(b)
        _      <- a.succeed(1)
        _      <- b.fail(exception)
        result <- z.await.run
      } yield assert(result)(fails(equalTo(exception)))
    },
    testM("zip this promise dies") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zip(b)
        _      <- a.die(exception)
        _      <- b.succeed(2)
        result <- z.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("zip when the other promise dies") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zip(b)
        _      <- a.succeed(1)
        _      <- b.die(exception)
        result <- z.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("zip when this promise is interrupted") {
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zip(b)
        _      <- a.interrupt
        _      <- b.succeed(2)
        result <- z.await.run
      } yield assert(result)(isInterrupted)
    },
    testM("zip when the other promise is interrupted") {
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zip(b)
        _      <- a.succeed(1)
        _      <- b.interrupt
        result <- z.await.run
      } yield assert(result)(isInterrupted)
    },
    testM("zipWith when both promises complete") {
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zipWith(b)(_ + _)
        _      <- a.succeed(1)
        _      <- b.succeed(2)
        result <- z.await.run
      } yield assert(result)(succeeds(equalTo(3)))
    },
    testM("zipWith when this promise fails") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zipWith(b)(_ + _)
        _      <- a.fail(exception)
        _      <- b.succeed(2)
        result <- z.await.run
      } yield assert(result)(fails(equalTo(exception)))
    },
    testM("zipWith the other promise fails") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zipWith(b)(_ + _)
        _      <- a.succeed(1)
        _      <- b.fail(exception)
        result <- z.await.run
      } yield assert(result)(fails(equalTo(exception)))
    },
    testM("zipWith when this promise dies") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zipWith(b)(_ + _)
        _      <- a.die(exception)
        _      <- b.succeed(2)
        result <- z.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("zipWith when the other promise dies") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zipWith(b)(_ + _)
        _      <- a.succeed(1)
        _      <- b.die(exception)
        result <- z.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("zipWith using a function that throws an exception") {
      val exception = new Exception
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zipWith[Exception, Int, Int](b)((_, _) => throw exception)
        _      <- a.succeed(1)
        _      <- b.succeed(2)
        result <- z.await.run
      } yield assert(result)(dies(equalTo(exception)))
    },
    testM("zipWith when this promise is interrupted") {
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zipWith(b)(_ + _)
        _      <- a.interrupt
        _      <- b.succeed(2)
        result <- z.await.run
      } yield assert(result)(isInterrupted)
    },
    testM("zipWith when the other promise is interrupted") {
      for {
        a      <- Promise.make[Exception, Int]
        b      <- Promise.make[Exception, Int]
        z      <- a.zipWith(b)(_ + _)
        _      <- a.succeed(1)
        _      <- b.interrupt
        result <- z.await.run
      } yield assert(result)(isInterrupted)
    }
  )
}
