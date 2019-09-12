package zio

import zio.test._
import zio.test.Assertion._

object PromiseSpec
    extends ZIOBaseSpec(
      suite("PromiseSpec")(
        testM("complete a promise using succeed") {
          for {
            p <- Promise.make[Nothing, Int]
            s <- p.succeed(32)
            v <- p.await
          } yield assert(s, isTrue) && assert(v, equalTo(32))
        },
        testM("complete a promise using complete") {
          for {
            p <- Promise.make[Nothing, Int]
            s <- p.complete(IO.succeed(14))
            v <- p.await
          } yield assert(s, isTrue) && assert(v, equalTo(14))
        },
        testM("fail a promise using fail") {
          for {
            p <- Promise.make[String, Int]
            s <- p.fail("error with fail")
            v <- p.await.run
          } yield assert(s, isTrue) && assert(v, fails(equalTo("error with fail")))
        },
        testM("fail a promise using complete") {
          for {
            p <- Promise.make[String, Int]
            s <- p.complete(IO.fail("error with done"))
            v <- p.await.run
          } yield assert(s, isTrue) && assert(v, fails(equalTo("error with done")))
        },
        testM("complete a promise twice") {
          for {
            p <- Promise.make[Nothing, Int]
            _ <- p.succeed(1)
            s <- p.complete(IO.succeed(9))
            v <- p.await
          } yield assert(s, isFalse) && assert(v, equalTo(1))
        },
        testM("interrupt a promise") {
          for {
            p <- Promise.make[Exception, Int]
            s <- p.interrupt
          } yield assert(s, isTrue)
        },
        testM("poll a promise that is not completed yet") {
          for {
            p       <- Promise.make[String, Int]
            attempt <- p.poll
          } yield assert(attempt, isNone)
        },
        testM("poll a promise that is completed") {
          for {
            p      <- Promise.make[String, Int]
            _      <- p.succeed(12)
            result <- p.poll.someOrFail("fail").flatten.run
          } yield assert(result, succeeds(equalTo(12)))
        },
        testM("poll a promise that is failed") {
          for {
            p      <- Promise.make[String, Int]
            _      <- p.fail("failure")
            result <- p.poll.someOrFail("fail").flatten.run
          } yield assert(result, fails(equalTo("failure")))
        },
        testM("poll a promise that is interrupted") {
          for {
            p      <- Promise.make[String, Int]
            _      <- p.interrupt
            result <- p.poll.someOrFail("fail").flatten.run
          } yield assert(result, isInterrupted)
        },
        testM("isDone when a promise is completed") {
          for {
            p <- Promise.make[String, Int]
            _ <- p.succeed(0)
            d <- p.isDone
          } yield assert(d, isTrue)
        },
        testM("isDone when a promise is failed") {
          for {
            p <- Promise.make[String, Int]
            _ <- p.fail("failure")
            d <- p.isDone
          } yield assert(d, isTrue)
        }
      )
    )
