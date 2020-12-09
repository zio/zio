package zio

import zio.ZQueueSpecUtil.waitForSize
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect.{ jvm, nonFlaky }
import zio.test._
import zio.test.environment.Live

import scala.collection.immutable.Range

object ZQueueSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec: ZSpec[Environment, Failure] = suite("ZQueueSpec")(
    testM("sequential offer and take") {
      for {
        queue <- Queue.bounded[Int](100)
        o1    <- queue.offer(10)
        v1    <- queue.take
        o2    <- queue.offer(20)
        v2    <- queue.take
      } yield assert(v1)(equalTo(10)) &&
        assert(v2)(equalTo(20)) &&
        assert(o1)(isTrue) &&
        assert(o2)(isTrue)
    },
    testM("sequential take and offer") {
      for {
        queue <- Queue.bounded[String](100)
        f1    <- queue.take.zipWith(queue.take)(_ + _).fork
        _     <- queue.offer("don't ") *> queue.offer("give up :D")
        v     <- f1.join
      } yield assert(v)(equalTo("don't give up :D"))
    },
    testM("parallel takes and sequential offers ") {
      for {
        queue <- Queue.bounded[Int](10)
        f     <- IO.forkAll(List.fill(10)(queue.take))
        values = Range.inclusive(1, 10).toList
        _     <- values.map(queue.offer).foldLeft[UIO[Boolean]](IO.succeed(false))(_ *> _)
        v     <- f.join
      } yield assert(v.toSet)(equalTo(values.toSet))
    },
    testM("parallel offers and sequential takes") {
      for {
        queue <- Queue.bounded[Int](10)
        values = Range.inclusive(1, 10).toList
        f     <- IO.forkAll(values.map(queue.offer))
        _     <- waitForSize(queue, 10)
        out   <- Ref.make[List[Int]](Nil)
        _     <- queue.take.flatMap(i => out.update(i :: _)).repeatN(9)
        l     <- out.get
        _     <- f.join
      } yield assert(l.toSet)(equalTo(values.toSet))
    },
    testM("offers are suspended by back pressure") {
      for {
        queue        <- Queue.bounded[Int](10)
        _            <- queue.offer(1).repeatN(9)
        refSuspended <- Ref.make[Boolean](true)
        f            <- (queue.offer(2) *> refSuspended.set(false)).fork
        _            <- waitForSize(queue, 11)
        isSuspended  <- refSuspended.get
        _            <- f.interrupt
      } yield assert(isSuspended)(isTrue)
    },
    testM("back pressured offers are retrieved") {
      for {
        queue <- Queue.bounded[Int](5)
        values = Range.inclusive(1, 10).toList
        f     <- IO.forkAll(values.map(queue.offer))
        _     <- waitForSize(queue, 10)
        out   <- Ref.make[List[Int]](Nil)
        _     <- queue.take.flatMap(i => out.update(i :: _)).repeatN(9)
        l     <- out.get
        _     <- f.join
      } yield assert(l.toSet)(equalTo(values.toSet))
    },
    testM("take interruption") {
      for {
        queue <- Queue.bounded[Int](100)
        f     <- queue.take.fork
        _     <- waitForSize(queue, -1)
        _     <- f.interrupt
        size  <- queue.size
      } yield assert(size)(equalTo(0))
    } @@ zioTag(interruption),
    testM("offer interruption") {
      for {
        queue <- Queue.bounded[Int](2)
        _     <- queue.offer(1)
        _     <- queue.offer(1)
        f     <- queue.offer(1).fork
        _     <- waitForSize(queue, 3)
        _     <- f.interrupt
        size  <- queue.size
      } yield assert(size)(equalTo(2))
    } @@ zioTag(interruption),
    testM("queue is ordered") {
      for {
        queue <- Queue.unbounded[Int]
        _     <- queue.offer(1)
        _     <- queue.offer(2)
        _     <- queue.offer(3)
        v1    <- queue.take
        v2    <- queue.take
        v3    <- queue.take
      } yield assert(v1)(equalTo(1)) &&
        assert(v2)(equalTo(2)) &&
        assert(v3)(equalTo(3))
    },
    testM("takeAll") {
      for {
        queue <- Queue.unbounded[Int]
        _     <- queue.offer(1)
        _     <- queue.offer(2)
        _     <- queue.offer(3)
        v     <- queue.takeAll
      } yield assert(v)(equalTo(List(1, 2, 3)))
    },
    testM("takeAll with empty queue") {
      for {
        queue <- Queue.unbounded[Int]
        c     <- queue.takeAll
        _     <- queue.offer(1)
        _     <- queue.take
        v     <- queue.takeAll
      } yield assert(c)(equalTo(List.empty[Int])) &&
        assert(v)(equalTo(List.empty[Int]))
    },
    testM("takeAll doesn't return more than the queue size") {
      for {
        queue <- Queue.bounded[Int](4)
        values = List(1, 2, 3, 4)
        _     <- values.map(queue.offer).foldLeft(IO.succeed(false))(_ *> _)
        _     <- queue.offer(5).fork
        _     <- waitForSize(queue, 5)
        v     <- queue.takeAll
        c     <- queue.take
      } yield assert(v.toSet)(equalTo(values.toSet)) &&
        assert(c)(equalTo(5))
    },
    testM("takeUpTo") {
      for {
        queue <- Queue.bounded[Int](100)
        _     <- queue.offer(10)
        _     <- queue.offer(20)
        list  <- queue.takeUpTo(2)
      } yield assert(list)(equalTo(List(10, 20)))
    },
    testM("takeUpTo with empty queue") {
      for {
        queue <- Queue.bounded[Int](100)
        list  <- queue.takeUpTo(2)
      } yield assert(list.isEmpty)(isTrue)
    },
    testM("takeUpTo with empty queue, with max higher than queue size") {
      for {
        queue <- Queue.bounded[Int](100)
        list  <- queue.takeUpTo(101)
      } yield assert(list.isEmpty)(isTrue)
    },
    testM("takeUpTo with remaining items") {
      for {
        queue <- Queue.bounded[Int](100)
        _     <- queue.offer(10)
        _     <- queue.offer(20)
        _     <- queue.offer(30)
        _     <- queue.offer(40)
        list  <- queue.takeUpTo(2)
      } yield assert(list)(equalTo(List(10, 20)))
    },
    testM("takeUpTo with not enough items") {
      for {
        queue <- Queue.bounded[Int](100)
        _     <- queue.offer(10)
        _     <- queue.offer(20)
        _     <- queue.offer(30)
        _     <- queue.offer(40)
        list  <- queue.takeUpTo(10)
      } yield assert(list)(equalTo(List(10, 20, 30, 40)))
    },
    testM("takeUpTo 0") {
      for {
        queue <- Queue.bounded[Int](100)
        _     <- queue.offer(10)
        _     <- queue.offer(20)
        _     <- queue.offer(30)
        _     <- queue.offer(40)
        list  <- queue.takeUpTo(0)
      } yield assert(list.isEmpty)(isTrue)
    },
    testM("takeUpTo -1") {
      for {
        queue <- Queue.bounded[Int](100)
        _     <- queue.offer(10)
        list  <- queue.takeUpTo(-1)
      } yield assert(list.isEmpty)(isTrue)
    },
    testM("multiple takeUpTo") {
      for {
        queue <- Queue.bounded[Int](100)
        _     <- queue.offer(10)
        _     <- queue.offer(20)
        list1 <- queue.takeUpTo(2)
        _     <- queue.offer(30)
        _     <- queue.offer(40)
        list2 <- queue.takeUpTo(2)
      } yield assert(list1)(equalTo(List(10, 20))) &&
        assert(list2)(equalTo(List(30, 40)))
    },
    testM("consecutive takeUpTo") {
      for {
        queue <- Queue.bounded[Int](100)
        _     <- queue.offer(10)
        _     <- queue.offer(20)
        _     <- queue.offer(30)
        _     <- queue.offer(40)
        list1 <- queue.takeUpTo(2)
        list2 <- queue.takeUpTo(2)
      } yield assert(list1)(equalTo(List(10, 20))) &&
        assert(list2)(equalTo(List(30, 40)))
    },
    testM("takeUpTo doesn't return back-pressured offers") {
      for {
        queue <- Queue.bounded[Int](4)
        values = List(1, 2, 3, 4)
        _     <- values.map(queue.offer).foldLeft(IO.succeed(false))(_ *> _)
        f     <- queue.offer(5).fork
        _     <- waitForSize(queue, 5)
        l     <- queue.takeUpTo(5)
        _     <- f.interrupt
      } yield assert(l)(equalTo(List(1, 2, 3, 4)))
    },
    suite("takeBetween")(
      testM("returns immediately if there is enough elements") {
        for {
          queue <- Queue.bounded[Int](100)
          _     <- queue.offer(10)
          _     <- queue.offer(20)
          _     <- queue.offer(30)
          res   <- queue.takeBetween(2, 5)
        } yield assert(res)(equalTo(List(10, 20, 30)))
      },
      testM("returns an empty list if boundaries are inverted") {
        for {
          queue <- Queue.bounded[Int](100)
          _     <- queue.offer(10)
          _     <- queue.offer(20)
          _     <- queue.offer(30)
          res   <- queue.takeBetween(5, 2)
        } yield assert(res)(isEmpty)
      },
      testM("returns an empty list if boundaries are negative") {
        for {
          queue <- Queue.bounded[Int](100)
          _     <- queue.offer(10)
          _     <- queue.offer(20)
          _     <- queue.offer(30)
          res   <- queue.takeBetween(-5, -2)
        } yield assert(res)(isEmpty)
      },
      testM("blocks until a required minimum of elements is collected") {
        for {
          queue  <- Queue.bounded[Int](100)
          updater = queue.offer(10).forever
          getter  = queue.takeBetween(5, 10)
          res    <- getter.race(updater)
        } yield assert(res)(hasSize(isGreaterThanEqualTo(5)))
      }
    ),
    testM("offerAll with takeAll") {
      for {
        queue <- Queue.bounded[Int](10)
        orders = Range.inclusive(1, 10).toList
        _     <- queue.offerAll(orders)
        _     <- waitForSize(queue, 10)
        l     <- queue.takeAll
      } yield assert(l)(equalTo(orders))
    },
    testM("offerAll with takeAll and back pressure") {
      for {
        queue <- Queue.bounded[Int](2)
        orders = Range.inclusive(1, 3).toList
        f     <- queue.offerAll(orders).fork
        size  <- waitForSize(queue, 3)
        l     <- queue.takeAll
        _     <- f.interrupt
      } yield assert(size)(equalTo(3)) &&
        assert(l)(equalTo(List(1, 2)))
    },
    testM("offerAll with takeAll and back pressure + interruption") {
      for {
        queue  <- Queue.bounded[Int](2)
        orders1 = Range.inclusive(1, 2).toList
        orders2 = Range.inclusive(3, 4).toList
        _      <- queue.offerAll(orders1)
        f      <- queue.offerAll(orders2).fork
        _      <- waitForSize(queue, 4)
        _      <- f.interrupt
        l1     <- queue.takeAll
        l2     <- queue.takeAll
      } yield assert(l1)(equalTo(orders1)) &&
        assert(l2)(equalTo(List.empty[Int]))
    } @@ zioTag(interruption),
    testM("offerAll with takeAll and back pressure, check ordering") {
      for {
        queue <- Queue.bounded[Int](64)
        orders = Range.inclusive(1, 128).toList
        f     <- queue.offerAll(orders).fork
        _     <- waitForSize(queue, 128)
        l     <- queue.takeAll
        _     <- f.interrupt
      } yield assert(l)(equalTo(Range.inclusive(1, 64).toList))
    },
    testM("offerAll with pending takers") {
      for {
        queue  <- Queue.bounded[Int](50)
        orders  = Range.inclusive(1, 100).toList
        takers <- IO.forkAll(List.fill(100)(queue.take))
        _      <- waitForSize(queue, -100)
        _      <- queue.offerAll(orders)
        l      <- takers.join
        s      <- queue.size
      } yield assert(l.toSet)(equalTo(orders.toSet)) &&
        assert(s)(equalTo(0))
    },
    testM("offerAll with pending takers, check ordering") {
      for {
        queue  <- Queue.bounded[Int](256)
        orders  = Range.inclusive(1, 128).toList
        takers <- IO.forkAll(List.fill(64)(queue.take))
        _      <- waitForSize(queue, -64)
        _      <- queue.offerAll(orders)
        l      <- takers.join
        s      <- queue.size
        values  = orders.take(64)
      } yield assert(l.toSet)(equalTo(values.toSet)) &&
        assert(s)(equalTo(64))
    },
    testM("offerAll with pending takers, check ordering of taker resolution") {
      for {
        queue  <- Queue.bounded[Int](200)
        values  = Range.inclusive(1, 100).toList
        takers <- IO.forkAll(List.fill(100)(queue.take))
        _      <- waitForSize(queue, -100)
        f      <- IO.forkAll(List.fill(100)(queue.take))
        _      <- waitForSize(queue, -200)
        _      <- queue.offerAll(values)
        l      <- takers.join
        s      <- queue.size
        _      <- f.interrupt
      } yield assert(l.toSet)(equalTo(values.toSet)) &&
        assert(s)(equalTo(-100))
    },
    testM("offerAll with take and back pressure") {
      for {
        queue <- Queue.bounded[Int](2)
        orders = Range.inclusive(1, 3).toList
        _     <- queue.offerAll(orders).fork
        _     <- waitForSize(queue, 3)
        v1    <- queue.take
        v2    <- queue.take
        v3    <- queue.take
      } yield assert(v1)(equalTo(1)) &&
        assert(v2)(equalTo(2)) &&
        assert(v3)(equalTo(3))
    },
    testM("multiple offerAll with back pressure") {
      for {
        queue  <- Queue.bounded[Int](2)
        orders  = Range.inclusive(1, 3).toList
        orders2 = Range.inclusive(4, 5).toList
        _      <- queue.offerAll(orders).fork
        _      <- waitForSize(queue, 3)
        _      <- queue.offerAll(orders2).fork
        _      <- waitForSize(queue, 5)
        v1     <- queue.take
        v2     <- queue.take
        v3     <- queue.take
        v4     <- queue.take
        v5     <- queue.take
      } yield assert(v1)(equalTo(1)) &&
        assert(v2)(equalTo(2)) &&
        assert(v3)(equalTo(3)) &&
        assert(v4)(equalTo(4)) &&
        assert(v5)(equalTo(5))
    },
    testM("offerAll + takeAll, check ordering") {
      for {
        queue <- Queue.bounded[Int](1000)
        orders = Range.inclusive(2, 1000).toList
        _     <- queue.offer(1)
        _     <- queue.offerAll(orders)
        _     <- waitForSize(queue, 1000)
        v1    <- queue.takeAll
      } yield assert(v1)(equalTo(Range.inclusive(1, 1000).toList))
    },
    testM("combination of offer, offerAll, take, takeAll") {
      for {
        queue <- Queue.bounded[Int](32)
        orders = Range.inclusive(3, 35).toList
        _     <- queue.offer(1)
        _     <- queue.offer(2)
        _     <- queue.offerAll(orders).fork
        _     <- waitForSize(queue, 35)
        v     <- queue.takeAll
        v1    <- queue.take
        v2    <- queue.take
        v3    <- queue.take
      } yield assert(v)(equalTo(Range.inclusive(1, 32).toList)) &&
        assert(v1)(equalTo(33)) &&
        assert(v2)(equalTo(34)) &&
        assert(v3)(equalTo(35))
    },
    testM("shutdown with take fiber") {
      for {
        selfId <- ZIO.fiberId
        queue  <- Queue.bounded[Int](3)
        f      <- queue.take.fork
        _      <- waitForSize(queue, -1)
        _      <- queue.shutdown
        res    <- f.join.sandbox.either
      } yield assert(res.left.map(_.untraced))(isLeft(equalTo(Cause.interrupt(selfId))))
    },
    testM("shutdown with offer fiber") {
      for {
        selfId <- ZIO.fiberId
        queue  <- Queue.bounded[Int](2)
        _      <- queue.offer(1)
        _      <- queue.offer(1)
        f      <- queue.offer(1).fork
        _      <- waitForSize(queue, 3)
        _      <- queue.shutdown
        res    <- f.join.sandbox.either
      } yield assert(res)(isLeft(equalTo(Cause.interrupt(selfId))))
    },
    testM("shutdown with offer") {
      for {
        selfId <- ZIO.fiberId
        queue  <- Queue.bounded[Int](1)
        _      <- queue.shutdown
        res    <- queue.offer(1).sandbox.either
      } yield assert(res)(isLeft(equalTo(Cause.interrupt(selfId))))
    },
    testM("shutdown with take") {
      for {
        selfId <- ZIO.fiberId
        queue  <- Queue.bounded[Int](1)
        _      <- queue.shutdown
        res    <- queue.take.sandbox.either
      } yield assert(res)(isLeft(equalTo(Cause.interrupt(selfId))))
    },
    testM("shutdown with takeAll") {
      for {
        selfId <- ZIO.fiberId
        queue  <- Queue.bounded[Int](1)
        _      <- queue.shutdown
        res    <- queue.takeAll.sandbox.either
      } yield assert(res)(isLeft(equalTo(Cause.interrupt(selfId))))
    },
    testM("shutdown with takeUpTo") {
      for {
        selfId <- ZIO.fiberId
        queue  <- Queue.bounded[Int](1)
        _      <- queue.shutdown
        res    <- queue.takeUpTo(1).sandbox.either
      } yield assert(res)(isLeft(equalTo(Cause.interrupt(selfId))))
    },
    testM("shutdown with size") {
      for {
        selfId <- ZIO.fiberId
        queue  <- Queue.bounded[Int](1)
        _      <- queue.shutdown
        res    <- queue.size.sandbox.either
      } yield assert(res)(isLeft(equalTo(Cause.interrupt(selfId))))
    },
    testM("back-pressured offer completes after take") {
      for {
        queue <- Queue.bounded[Int](2)
        _     <- queue.offerAll(List(1, 2))
        f     <- queue.offer(3).fork
        _     <- waitForSize(queue, 3)
        v1    <- queue.take
        v2    <- queue.take
        _     <- f.join
      } yield assert(v1)(equalTo(1)) &&
        assert(v2)(equalTo(2))
    },
    testM("back-pressured offer completes after takeAll") {
      for {
        queue <- Queue.bounded[Int](2)
        _     <- queue.offerAll(List(1, 2))
        f     <- queue.offer(3).fork
        _     <- waitForSize(queue, 3)
        v1    <- queue.takeAll
        _     <- f.join
      } yield assert(v1)(equalTo(List(1, 2)))
    },
    testM("back-pressured offer completes after takeUpTo") {
      for {
        queue <- Queue.bounded[Int](2)
        _     <- queue.offerAll(List(1, 2))
        f     <- queue.offer(3).fork
        _     <- waitForSize(queue, 3)
        v1    <- queue.takeUpTo(2)
        _     <- f.join
      } yield assert(v1)(equalTo(List(1, 2)))
    },
    testM("back-pressured offerAll completes after takeAll") {
      for {
        queue <- Queue.bounded[Int](2)
        _     <- queue.offerAll(List(1, 2))
        f     <- queue.offerAll(List(3, 4, 5)).fork
        _     <- waitForSize(queue, 5)
        v1    <- queue.takeAll
        v2    <- queue.takeAll
        v3    <- queue.takeAll
        _     <- f.join
      } yield assert(v1)(equalTo(List(1, 2))) &&
        assert(v2)(equalTo(List(3, 4))) &&
        assert(v3)(equalTo(List(5)))
    },
    testM("sliding strategy with offer") {
      for {
        queue <- Queue.sliding[Int](2)
        _     <- queue.offer(1)
        v1    <- queue.offer(2)
        v2    <- queue.offer(3)
        l     <- queue.takeAll
      } yield assert(l)(equalTo(List(2, 3))) &&
        assert(v1)(isTrue) &&
        assert(v2)(isTrue)
    },
    testM("sliding strategy with offerAll") {
      for {
        queue <- Queue.sliding[Int](2)
        v     <- queue.offerAll(List(1, 2, 3))
        size  <- queue.size
      } yield assert(size)(equalTo(2)) &&
        assert(v)(isTrue)
    },
    testM("sliding strategy with enough capacity") {
      for {
        queue <- Queue.sliding[Int](100)
        _     <- queue.offer(1)
        _     <- queue.offer(2)
        _     <- queue.offer(3)
        l     <- queue.takeAll
      } yield assert(l)(equalTo(List(1, 2, 3)))
    },
    testM("sliding strategy with offerAll and takeAll") {
      for {
        queue <- Queue.sliding[Int](2)
        v1    <- queue.offerAll(Iterable(1, 2, 3, 4, 5, 6))
        l     <- queue.takeAll
      } yield assert(l)(equalTo(List(5, 6))) &&
        assert(v1)(isTrue)
    },
    testM("awaitShutdown") {
      for {
        queue <- Queue.bounded[Int](3)
        p     <- Promise.make[Nothing, Boolean]
        _     <- (queue.awaitShutdown *> p.succeed(true)).fork
        _     <- queue.shutdown
        res   <- p.await
      } yield assert(res)(isTrue)
    },
    testM("multiple awaitShutdown") {
      for {
        queue <- Queue.bounded[Int](3)
        p1    <- Promise.make[Nothing, Boolean]
        p2    <- Promise.make[Nothing, Boolean]
        _     <- (queue.awaitShutdown *> p1.succeed(true)).fork
        _     <- (queue.awaitShutdown *> p2.succeed(true)).fork
        _     <- queue.shutdown
        res1  <- p1.await
        res2  <- p2.await
      } yield assert(res1)(isTrue) &&
        assert(res2)(isTrue)
    },
    testM("awaitShutdown when queue is already shutdown") {
      for {
        queue <- Queue.bounded[Int](3)
        _     <- queue.shutdown
        p     <- Promise.make[Nothing, Boolean]
        _     <- (queue.awaitShutdown *> p.succeed(true)).fork
        res   <- p.await
      } yield assert(res)(isTrue)
    },
    testM("dropping strategy with offerAll") {
      for {
        capacity <- IO.succeed(4)
        queue    <- Queue.dropping[Int](capacity)
        iter      = Range.inclusive(1, 5)
        _        <- queue.offerAll(iter)
        ta       <- queue.takeAll
      } yield assert(ta)(equalTo(List(1, 2, 3, 4)))
    },
    testM("dropping strategy with offerAll, check offer returns false") {
      for {
        capacity <- IO.succeed(2)
        queue    <- Queue.dropping[Int](capacity)
        v1       <- queue.offerAll(Iterable(1, 2, 3, 4, 5, 6))
        _        <- queue.takeAll
      } yield assert(v1)(isFalse)
    },
    testM("dropping strategy with offerAll, check ordering") {
      for {
        capacity <- IO.succeed(128)
        queue    <- Queue.dropping[Int](capacity)
        iter      = Range.inclusive(1, 256)
        _        <- queue.offerAll(iter)
        ta       <- queue.takeAll
      } yield assert(ta)(equalTo(Range.inclusive(1, 128).toList))
    },
    testM("dropping strategy with pending taker") {
      for {
        capacity <- IO.succeed(2)
        queue    <- Queue.dropping[Int](capacity)
        iter      = Range.inclusive(1, 4)
        f        <- queue.take.fork
        _        <- waitForSize(queue, -1)
        oa       <- queue.offerAll(iter.toList)
        j        <- f.join
      } yield assert(j)(equalTo(1)) &&
        assert(oa)(isFalse)
    },
    testM("sliding strategy with pending taker") {
      for {
        capacity <- IO.succeed(2)
        queue    <- Queue.sliding[Int](capacity)
        iter      = Range.inclusive(1, 4)
        _        <- queue.take.fork
        _        <- waitForSize(queue, -1)
        oa       <- queue.offerAll(iter.toList)
        t        <- queue.take
      } yield assert(t)(equalTo(3)) &&
        assert(oa)(isTrue)
    },
    testM("sliding strategy, check offerAll returns true") {
      for {
        capacity <- IO.succeed(5)
        queue    <- Queue.sliding[Int](capacity)
        iter      = Range.inclusive(1, 3)
        oa       <- queue.offerAll(iter.toList)
      } yield assert(oa)(isTrue)
    },
    testM("bounded strategy, check offerAll returns true") {
      for {
        capacity <- IO.succeed(5)
        queue    <- Queue.bounded[Int](capacity)
        iter      = Range.inclusive(1, 3)
        oa       <- queue.offerAll(iter.toList)
      } yield assert(oa)(isTrue)
    },
    testM("poll on empty queue") {
      for {
        queue <- Queue.bounded[Int](5)
        t     <- queue.poll
      } yield assert(t)(isNone)
    },
    testM("poll on queue just emptied") {
      for {
        queue <- Queue.bounded[Int](5)
        iter   = Range.inclusive(1, 4)
        _     <- queue.offerAll(iter.toList)
        _     <- queue.takeAll
        t     <- queue.poll
      } yield assert(t)(isNone)
    },
    testM("multiple polls") {
      for {
        queue <- Queue.bounded[Int](5)
        iter   = Range.inclusive(1, 2)
        _     <- queue.offerAll(iter.toList)
        t1    <- queue.poll
        t2    <- queue.poll
        t3    <- queue.poll
        t4    <- queue.poll
      } yield assert(t1)(isSome(equalTo(1))) &&
        assert(t2)(isSome(equalTo(2))) &&
        assert(t3)(isNone) &&
        assert(t4)(isNone)
    },
    testM("queue map") {
      for {
        q <- Queue.bounded[Int](100).map(_.map(_.toString))
        _ <- q.offer(10)
        v <- q.take
      } yield assert(v)(equalTo("10"))
    },
    testM("queue map identity") {
      for {
        q <- Queue.bounded[Int](100).map(_.map(identity))
        _ <- q.offer(10)
        v <- q.take
      } yield assert(v)(equalTo(10))
    },
    testM("queue mapM") {
      for {
        q <- Queue.bounded[Int](100).map(_.mapM(IO.succeed(_)))
        _ <- q.offer(10)
        v <- q.take
      } yield assert(v)(equalTo(10))
    },
    testM("queue mapM with success") {
      for {
        q <- Queue.bounded[IO[String, Int]](100).map(_.mapM(identity))
        _ <- q.offer(IO.succeed(10))
        v <- q.take.sandbox.either
      } yield assert(v)(isRight(equalTo(10)))
    },
    testM("queue mapM with failure") {
      for {
        q <- Queue.bounded[IO[String, Int]](100).map(_.mapM(identity))
        _ <- q.offer(IO.fail("Ouch"))
        v <- q.take.run
      } yield assert(v)(fails(equalTo("Ouch")))
    } @@ zioTag(errors),
    testM("queue both") {
      for {
        q1 <- Queue.bounded[Int](100)
        q2 <- Queue.bounded[Int](100)
        q   = q1 both q2
        _  <- q.offer(10)
        v  <- q.take
      } yield assert(v)(equalTo((10, 10)))
    },
    testM("queue contramap") {
      for {
        q <- Queue.bounded[String](100).map(_.contramap[Int](_.toString))
        _ <- q.offer(10)
        v <- q.take
      } yield assert(v)(equalTo("10"))
    },
    testM("queue dimap") {
      for {
        q <- Queue.bounded[String](100).map(_.dimap[Int, Int](_.toString, _.toInt))
        _ <- q.offer(10)
        v <- q.take
      } yield assert(v)(equalTo(10))
    },
    testM("queue filterInput") {
      for {
        q  <- Queue.bounded[Int](100).map(_.filterInput[Int](_ % 2 == 0))
        _  <- q.offer(1)
        s1 <- q.size
        _  <- q.offer(2)
        s2 <- q.size
      } yield assert(s1)(equalTo(0)) &&
        assert(s2)(equalTo(1))
    },
    testM("queue isShutdown") {
      for {
        queue <- Queue.bounded[Int](5)
        r1    <- queue.isShutdown
        _     <- queue.offer(1)
        r2    <- queue.isShutdown
        _     <- queue.takeAll
        r3    <- queue.isShutdown
        _     <- queue.shutdown
        r4    <- queue.isShutdown
      } yield assert(r1)(isFalse) &&
        assert(r2)(isFalse) &&
        assert(r3)(isFalse) &&
        assert(r4)(isTrue)
    },
    testM("shutdown race condition with offer") {
      for {
        q <- Queue.bounded[Int](2)
        f <- q.offer(1).forever.fork
        _ <- q.shutdown
        _ <- f.await
      } yield assertCompletes
    } @@ jvm(nonFlaky),
    testM("shutdown race condition with take") {
      for {
        q <- Queue.bounded[Int](2)
        _ <- q.offer(1)
        _ <- q.offer(1)
        f <- q.take.forever.fork
        _ <- q.shutdown
        _ <- f.await
      } yield assertCompletes
    } @@ jvm(nonFlaky)
  )
}

object ZQueueSpecUtil {
  def waitForValue[T](ref: UIO[T], value: T): URIO[Live, T] =
    Live.live((ref <* clock.sleep(10.millis)).repeatUntil(_ == value))

  def waitForSize[RA, EA, RB, EB, A, B](queue: ZQueue[RA, EA, RB, EB, A, B], size: Int): URIO[Live, Int] =
    waitForValue(queue.size, size)
}
