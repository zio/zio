package zio

import zio.test.Assertion._
import zio.test._

object PromiseSpec extends ZIOBaseSpec {

  import ZIOTag._

  private def empty[E, A]: Promise.internal.Pending[E, A] =
    Promise.internal.State.empty[E, A].asInstanceOf[Promise.internal.Pending[E, A]]

  val n = 10000

  def spec: Spec[Any, TestFailure[Any]] = suite("PromiseSpec")(
    test("complete a promise using succeed") {
      for {
        p <- Promise.make[Nothing, Int]
        s <- p.succeed(32)
        v <- p.await
      } yield assert(s)(isTrue) && assert(v)(equalTo(32))
    },
    test("complete a promise using complete") {
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
    test("complete a promise using completeWith") {
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
    test("fail a promise using fail") {
      for {
        p <- Promise.make[String, Int]
        s <- p.fail("error with fail")
        v <- p.await.exit
      } yield assert(s)(isTrue) && assert(v)(fails(equalTo("error with fail")))
    } @@ zioTag(errors),
    test("fail a promise using complete") {
      for {
        p  <- Promise.make[String, Int]
        r  <- Ref.make(List("first error", "second error"))
        s  <- p.complete(r.modify(as => (as.head, as.tail)).flip)
        v1 <- p.await.exit
        v2 <- p.await.exit
      } yield assert(s)(isTrue) &&
        assert(v1)(fails(equalTo("first error"))) &&
        assert(v2)(fails(equalTo("first error")))
    } @@ zioTag(errors),
    test("fail a promise using completeWith") {
      for {
        p  <- Promise.make[String, Int]
        r  <- Ref.make(List("first error", "second error"))
        s  <- p.completeWith(r.modify(as => (as.head, as.tail)).flip)
        v1 <- p.await.exit
        v2 <- p.await.exit
      } yield assert(s)(isTrue) &&
        assert(v1)(fails(equalTo("first error"))) &&
        assert(v2)(fails(equalTo("second error")))
    } @@ zioTag(errors),
    test("complete a promise twice") {
      for {
        p <- Promise.make[Nothing, Int]
        _ <- p.succeed(1)
        s <- p.complete(ZIO.succeed(9))
        v <- p.await
      } yield assert(s)(isFalse) && assert(v)(equalTo(1))
    },
    test("interrupt a promise") {
      for {
        p <- Promise.make[Exception, Int]
        s <- p.interrupt
      } yield assert(s)(isTrue)
    } @@ zioTag(interruption),
    test("poll a promise that is not completed yet") {
      for {
        p       <- Promise.make[String, Int]
        attempt <- p.poll
      } yield assert(attempt)(isNone)
    },
    test("poll a promise that is completed") {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.succeed(12)
        result <- p.poll.someOrFail("fail").flatten.exit
      } yield assert(result)(succeeds(equalTo(12)))
    },
    test("poll a promise that is failed") {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.fail("failure")
        result <- p.poll.someOrFail("fail").flatten.exit
      } yield assert(result)(fails(equalTo("failure")))
    },
    test("poll a promise that is interrupted") {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.interrupt
        result <- p.poll.someOrFail("fail").flatten.exit
      } yield assert(result)(isInterrupted)
    } @@ zioTag(interruption),
    test("isDone when a promise is completed") {
      for {
        p <- Promise.make[String, Int]
        _ <- p.succeed(0)
        d <- p.isDone
      } yield assert(d)(isTrue)
    },
    test("isDone when a promise is failed") {
      for {
        p <- Promise.make[String, Int]
        _ <- p.fail("failure")
        d <- p.isDone
      } yield assert(d)(isTrue)
    } @@ zioTag(errors),
    test("waiter stack safety") {
      for {
        p      <- Promise.make[Nothing, Unit]
        fibers <- ZIO.foreach(1 to n)(_ => p.await.forkDaemon)
        _      <- p.complete(Exit.unit)
        _      <- ZIO.foreach(fibers)(_.await)
      } yield assertCompletes
    },
    suite("State")(
      suite("add")(
        test("stack safety") {
          (0 to 100000).foldLeft(empty[Nothing, Unit])((acc, _) => acc.add(_ => ()))
          assertCompletes
        }
      ),
      suite("complete")(
        test("one") {
          var increment = 0
          val state     = empty[Nothing, Unit].add(_ => increment += 1)
          state.complete(ZIO.unit)
          assert(increment)(equalTo(1))
        },
        test("multiple") {
          var increment = 0
          val state     = (0 until n).foldLeft(empty[Nothing, Unit])((acc, _) => acc.add(_ => increment += 1))
          state.complete(ZIO.unit)
          assert(increment)(equalTo(n))
        }
      ),
      suite("remove")(
        test("one") {
          var increment = 0
          val cb        = (_: IO[Nothing, Unit]) => increment += 1
          val state     = empty[Nothing, Unit].add(cb)
          val removed   = state.remove(cb)
          removed.complete(ZIO.unit)
          assert(removed)(equalTo(empty[Nothing, Unit])) &&
          assert(increment)(equalTo(0))
        },
        test("multiple") {
          var fired    = 0
          val cb       = (_: IO[Nothing, Unit]) => ()
          val toRemove = (_: IO[Nothing, Unit]) => fired += 1
          val state =
            (0 until n).foldLeft(empty[Nothing, Unit])((acc, i) => if (i < 5) acc.add(cb) else acc.add(toRemove))
          val removed = state.remove(toRemove)
          removed.complete(ZIO.unit)
          assert(removed.size)(equalTo(5)) &&
          assert(fired)(equalTo(0))
        }
      ),
      suite("complete")(
        test("one") {
          var completed = 0
          val state     = empty[Nothing, Unit].add(_ => completed += 1)
          state.complete(ZIO.unit)
          assert(completed)(equalTo(1))
        },
        test("multiple") {
          var completed = List.empty[Int]
          val state     = (0 until n).foldLeft(empty[Nothing, Unit])((acc, i) => acc.add(_ => completed = i :: completed))
          state.complete(ZIO.unit)
          assert(completed)(equalTo(List.range(0, n)))
        }
      )
    )
  )
}
