package zio

import scala.collection.immutable.Range
import zio.clock.Clock
import zio.duration._
import zio.test.{ assert, suite, testM, Assertion, DefaultRunnableSpec }
import zio.test.TestUtils.nonFlaky
import zio.ZQueueSpecUtil.waitForSize

object ZQueueSpec
    extends DefaultRunnableSpec(
      suite("ZQueueSpec")(
        testM("sequential offer and take") {
          for {
            queue <- Queue.bounded[Int](100)
            o1    <- queue.offer(10)
            v1    <- queue.take
            o2    <- queue.offer(20)
            v2    <- queue.take
          } yield assert((v1, v2, o1, o2), Assertion.equalTo((10, 20, true, true)))
        },
        testM("sequential take and offer") {
          for {
            queue <- Queue.bounded[String](100)
            f1    <- queue.take.zipWith(queue.take)(_ + _).fork
            _     <- queue.offer("don't ") *> queue.offer("give up :D")
            v     <- f1.join
          } yield assert(v, Assertion.equalTo("don't give up :D"))
        },
        testM("parallel takes and sequential offers ") {
          for {
            queue  <- Queue.bounded[Int](10)
            f      <- IO.forkAll(List.fill(10)(queue.take))
            values = Range.inclusive(1, 10).toList
            _      <- values.map(queue.offer).foldLeft[UIO[Boolean]](IO.succeed(false))(_ *> _)
            v      <- f.join
          } yield assert(v.toSet, Assertion.equalTo(values.toSet))
        },
        testM("parallel offers and sequential takes") {
          for {
            queue  <- Queue.bounded[Int](10)
            values = Range.inclusive(1, 10).toList
            f      <- IO.forkAll(values.map(queue.offer))
            _      <- waitForSize(queue, 10)
            l      <- queue.take.repeat(ZSchedule.recurs(9) *> ZSchedule.identity[Int].collectAll)
            _      <- f.join
          } yield assert(l.toSet, Assertion.equalTo(values.toSet))
        },
        testM("offers are suspended by back pressure") {
          for {
            queue        <- Queue.bounded[Int](10)
            _            <- queue.offer(1).repeat(ZSchedule.recurs(9))
            refSuspended <- Ref.make[Boolean](true)
            f            <- (queue.offer(2) *> refSuspended.set(false)).fork
            _            <- waitForSize(queue, 11)
            isSuspended  <- refSuspended.get
            _            <- f.interrupt
          } yield assert(isSuspended, Assertion.isTrue)
        },
        testM("back pressured offers are retrieved") {
          for {
            queue  <- Queue.bounded[Int](5)
            values = Range.inclusive(1, 10).toList
            _      <- IO.forkAll(values.map(queue.offer))
            _      <- waitForSize(queue, 10)
            l      <- queue.take.repeat(ZSchedule.recurs(9) *> ZSchedule.identity[Int].collectAll)
          } yield assert(l.toSet, Assertion.equalTo(values.toSet))
        },
        testM("take interruption") {
          for {
            queue <- Queue.bounded[Int](100)
            f     <- queue.take.fork
            _     <- waitForSize(queue, -1)
            _     <- f.interrupt
            size  <- queue.size
          } yield assert(size, Assertion.equalTo(0))
        },
        testM("offer interruption") {
          for {
            queue <- Queue.bounded[Int](2)
            _     <- queue.offer(1)
            _     <- queue.offer(1)
            f     <- queue.offer(1).fork
            _     <- waitForSize(queue, 3)
            _     <- f.interrupt
            size  <- queue.size
          } yield assert(size, Assertion.equalTo(2))
        },
        testM("queue is ordered") {
          for {
            queue <- Queue.unbounded[Int]
            _     <- queue.offer(1)
            _     <- queue.offer(2)
            _     <- queue.offer(3)
            v1    <- queue.take
            v2    <- queue.take
            v3    <- queue.take
          } yield assert((v1, v2, v3), Assertion.equalTo((1, 2, 3)))
        },
        testM("takeAll") {
          for {
            queue <- Queue.unbounded[Int]
            _     <- queue.offer(1)
            _     <- queue.offer(2)
            _     <- queue.offer(3)
            v     <- queue.takeAll
          } yield assert(v, Assertion.equalTo(List(1, 2, 3)))
        },
        testM("takeAll with empty queue") {
          for {
            queue <- Queue.unbounded[Int]
            c     <- queue.takeAll
            _     <- queue.offer(1)
            _     <- queue.take
            v     <- queue.takeAll
          } yield assert((c, v), Assertion.equalTo((List.empty[Int], List.empty[Int])))
        },
        testM("takeAll doesn't return more than the queue size") {
          for {
            queue  <- Queue.bounded[Int](4)
            values = List(1, 2, 3, 4)
            _      <- values.map(queue.offer).foldLeft(IO.succeed(false))(_ *> _)
            _      <- queue.offer(5).fork
            _      <- waitForSize(queue, 5)
            v      <- queue.takeAll
            c      <- queue.take
          } yield assert((v.toSet, c), Assertion.equalTo((values.toSet, 5)))
        },
        testM("takeUpTo") {
          for {
            queue <- Queue.bounded[Int](100)
            _     <- queue.offer(10)
            _     <- queue.offer(20)
            list  <- queue.takeUpTo(2)
          } yield assert(list, Assertion.equalTo(List(10, 20)))
        },
        testM("takeUpTo with empty queue") {
          for {
            queue <- Queue.bounded[Int](100)
            list  <- queue.takeUpTo(2)
          } yield assert(list.isEmpty, Assertion.isTrue)
        },
        testM("takeUpTo with empty queue, with max higher than queue size") {
          for {
            queue <- Queue.bounded[Int](100)
            list  <- queue.takeUpTo(101)
          } yield assert(list.isEmpty, Assertion.isTrue)
        },
        testM("takeUpTo with remaining items") {
          for {
            queue <- Queue.bounded[Int](100)
            _     <- queue.offer(10)
            _     <- queue.offer(20)
            _     <- queue.offer(30)
            _     <- queue.offer(40)
            list  <- queue.takeUpTo(2)
          } yield assert(list, Assertion.equalTo(List(10, 20)))
        },
        testM("takeUpTo with not enough items") {
          for {
            queue <- Queue.bounded[Int](100)
            _     <- queue.offer(10)
            _     <- queue.offer(20)
            _     <- queue.offer(30)
            _     <- queue.offer(40)
            list  <- queue.takeUpTo(10)
          } yield assert(list, Assertion.equalTo(List(10, 20, 30, 40)))
        },
        testM("takeUpTo 0") {
          for {
            queue <- Queue.bounded[Int](100)
            _     <- queue.offer(10)
            _     <- queue.offer(20)
            _     <- queue.offer(30)
            _     <- queue.offer(40)
            list  <- queue.takeUpTo(0)
          } yield assert(list.isEmpty, Assertion.isTrue)
        },
        testM("takeUpTo -1") {
          for {
            queue <- Queue.bounded[Int](100)
            _     <- queue.offer(10)
            list  <- queue.takeUpTo(-1)
          } yield assert(list.isEmpty, Assertion.isTrue)
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
          } yield assert((list1, list2), Assertion.equalTo((List(10, 20), List(30, 40))))
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
          } yield assert((list1, list2), Assertion.equalTo((List(10, 20), List(30, 40))))
        },
        testM("takeUpTo doesn't return back-pressured offers") {
          for {
            queue  <- Queue.bounded[Int](4)
            values = List(1, 2, 3, 4)
            _      <- values.map(queue.offer).foldLeft(IO.succeed(false))(_ *> _)
            f      <- queue.offer(5).fork
            _      <- waitForSize(queue, 5)
            l      <- queue.takeUpTo(5)
            _      <- f.interrupt
          } yield assert(l, Assertion.equalTo(List(1, 2, 3, 4)))
        },
        testM("offerAll with takeAll") {
          for {
            queue  <- Queue.bounded[Int](10)
            orders = Range.inclusive(1, 10).toList
            _      <- queue.offerAll(orders)
            _      <- waitForSize(queue, 10)
            l      <- queue.takeAll
          } yield assert(l, Assertion.equalTo(orders))
        },
        testM("offerAll with takeAll and back pressure") {
          for {
            queue  <- Queue.bounded[Int](2)
            orders = Range.inclusive(1, 3).toList
            f      <- queue.offerAll(orders).fork
            size   <- waitForSize(queue, 3)
            l      <- queue.takeAll
            _      <- f.interrupt
          } yield assert((size, l), Assertion.equalTo((3, List(1, 2))))
        },
        testM("offerAll with takeAll and back pressure + interruption") {
          for {
            queue   <- Queue.bounded[Int](2)
            orders1 = Range.inclusive(1, 2).toList
            orders2 = Range.inclusive(3, 4).toList
            _       <- queue.offerAll(orders1)
            f       <- queue.offerAll(orders2).fork
            _       <- waitForSize(queue, 4)
            _       <- f.interrupt
            l1      <- queue.takeAll
            l2      <- queue.takeAll
          } yield assert((l1, l2), Assertion.equalTo((orders1, List.empty[Int])))
        },
        testM("offerAll with takeAll and back pressure, check ordering") {
          for {
            queue  <- Queue.bounded[Int](64)
            orders = Range.inclusive(1, 128).toList
            f      <- queue.offerAll(orders).fork
            _      <- waitForSize(queue, 128)
            l      <- queue.takeAll
            _      <- f.interrupt
          } yield assert(l, Assertion.equalTo(Range.inclusive(1, 64).toList))
        },
        testM("offerAll with pending takers") {
          for {
            queue  <- Queue.bounded[Int](50)
            orders = Range.inclusive(1, 100).toList
            takers <- IO.forkAll(List.fill(100)(queue.take))
            _      <- waitForSize(queue, -100)
            _      <- queue.offerAll(orders)
            l      <- takers.join
            s      <- queue.size
          } yield assert((l.toSet, s), Assertion.equalTo((orders.toSet, 0)))
        },
        testM("offerAll with pending takers, check ordering") {
          for {
            queue  <- Queue.bounded[Int](256)
            orders = Range.inclusive(1, 128).toList
            takers <- IO.forkAll(List.fill(64)(queue.take))
            _      <- waitForSize(queue, -64)
            _      <- queue.offerAll(orders)
            l      <- takers.join
            s      <- queue.size
            values = orders.take(64)
          } yield assert((l.toSet, s), Assertion.equalTo((values.toSet, 64)))
        },
        testM("offerAll with pending takers, check ordering of taker resolution") {
          for {
            queue  <- Queue.bounded[Int](200)
            values = Range.inclusive(1, 100).toList
            takers <- IO.forkAll(List.fill(100)(queue.take))
            _      <- waitForSize(queue, -100)
            f      <- IO.forkAll(List.fill(100)(queue.take))
            _      <- waitForSize(queue, -200)
            _      <- queue.offerAll(values)
            l      <- takers.join
            s      <- queue.size
            _      <- f.interrupt
          } yield assert((l.toSet, s), Assertion.equalTo((values.toSet, -100)))
        },
        testM("offerAll with take and back pressure") {
          for {
            queue  <- Queue.bounded[Int](2)
            orders = Range.inclusive(1, 3).toList
            _      <- queue.offerAll(orders).fork
            _      <- waitForSize(queue, 3)
            v1     <- queue.take
            v2     <- queue.take
            v3     <- queue.take
          } yield assert((v1, v2, v3), Assertion.equalTo((1, 2, 3)))
        },
        testM("multiple offerAll with back pressure") {
          for {
            queue   <- Queue.bounded[Int](2)
            orders  = Range.inclusive(1, 3).toList
            orders2 = Range.inclusive(4, 5).toList
            _       <- queue.offerAll(orders).fork
            _       <- waitForSize(queue, 3)
            _       <- queue.offerAll(orders2).fork
            _       <- waitForSize(queue, 5)
            v1      <- queue.take
            v2      <- queue.take
            v3      <- queue.take
            v4      <- queue.take
            v5      <- queue.take
          } yield assert((v1, v2, v3, v4, v5), Assertion.equalTo((1, 2, 3, 4, 5)))
        },
        testM("offerAll + takeAll, check ordering") {
          for {
            queue  <- Queue.bounded[Int](1000)
            orders = Range.inclusive(2, 1000).toList
            _      <- queue.offer(1)
            _      <- queue.offerAll(orders)
            _      <- waitForSize(queue, 1000)
            v1     <- queue.takeAll
          } yield assert(v1, Assertion.equalTo(Range.inclusive(1, 1000).toList))
        },
        testM("combination of offer, offerAll, take, takeAll") {
          for {
            queue  <- Queue.bounded[Int](32)
            orders = Range.inclusive(3, 35).toList
            _      <- queue.offer(1)
            _      <- queue.offer(2)
            _      <- queue.offerAll(orders).fork
            _      <- waitForSize(queue, 35)
            v      <- queue.takeAll
            v1     <- queue.take
            v2     <- queue.take
            v3     <- queue.take
          } yield assert((v, v1, v2, v3), Assertion.equalTo((Range.inclusive(1, 32).toList, 33, 34, 35)))
        },
        testM("shutdown with take fiber") {
          for {
            queue <- Queue.bounded[Int](3)
            f     <- queue.take.fork
            _     <- waitForSize(queue, -1)
            _     <- queue.shutdown
            res   <- f.join.sandbox.either
          } yield assert(res, Assertion.isLeft(Assertion.equalTo(Cause.interrupt)))
        },
        testM("shutdown with offer fiber") {
          for {
            queue <- Queue.bounded[Int](2)
            _     <- queue.offer(1)
            _     <- queue.offer(1)
            f     <- queue.offer(1).fork
            _     <- waitForSize(queue, 3)
            _     <- queue.shutdown
            res   <- f.join.sandbox.either
          } yield assert(res, Assertion.isLeft(Assertion.equalTo(Cause.interrupt)))
        },
        testM("shutdown with offer") {
          for {
            queue <- Queue.bounded[Int](1)
            _     <- queue.shutdown
            res   <- queue.offer(1).sandbox.either
          } yield assert(res, Assertion.isLeft(Assertion.equalTo(Cause.interrupt)))
        },
        testM("shutdown with take") {
          for {
            queue <- Queue.bounded[Int](1)
            _     <- queue.shutdown
            res   <- queue.take.sandbox.either
          } yield assert(res, Assertion.isLeft(Assertion.equalTo(Cause.interrupt)))
        },
        testM("shutdown with takeAll") {
          for {
            queue <- Queue.bounded[Int](1)
            _     <- queue.shutdown
            res   <- queue.takeAll.sandbox.either
          } yield assert(res, Assertion.isLeft(Assertion.equalTo(Cause.interrupt)))
        },
        testM("shutdown with takeUpTo") {
          for {
            queue <- Queue.bounded[Int](1)
            _     <- queue.shutdown
            res   <- queue.takeUpTo(1).sandbox.either
          } yield assert(res, Assertion.isLeft(Assertion.equalTo(Cause.interrupt)))
        },
        testM("shutdown with size") {
          for {
            queue <- Queue.bounded[Int](1)
            _     <- queue.shutdown
            res   <- queue.size.sandbox.either
          } yield assert(res, Assertion.isLeft(Assertion.equalTo(Cause.interrupt)))
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
          } yield assert((v1, v2), Assertion.equalTo((1, 2)))
        },
        testM("back-pressured offer completes after takeAll") {
          for {
            queue <- Queue.bounded[Int](2)
            _     <- queue.offerAll(List(1, 2))
            f     <- queue.offer(3).fork
            _     <- waitForSize(queue, 3)
            v1    <- queue.takeAll
            _     <- f.join
          } yield assert(v1, Assertion.equalTo(List(1, 2)))
        },
        testM("back-pressured offer completes after takeUpTo") {
          for {
            queue <- Queue.bounded[Int](2)
            _     <- queue.offerAll(List(1, 2))
            f     <- queue.offer(3).fork
            _     <- waitForSize(queue, 3)
            v1    <- queue.takeUpTo(2)
            _     <- f.join
          } yield assert(v1, Assertion.equalTo(List(1, 2)))
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
          } yield assert((v1, v2, v3), Assertion.equalTo((List(1, 2), List(3, 4), List(5))))
        },
        testM("sliding strategy with offer") {
          for {
            queue <- Queue.sliding[Int](2)
            _     <- queue.offer(1)
            v1    <- queue.offer(2)
            v2    <- queue.offer(3)
            l     <- queue.takeAll
          } yield assert((l, v1, v2), Assertion.equalTo((List(2, 3), true, false)))
        },
        testM("sliding strategy with offerAll") {
          for {
            queue <- Queue.sliding[Int](2)
            v     <- queue.offerAll(List(1, 2, 3))
            size  <- queue.size
          } yield assert((size, v), Assertion.equalTo((2, false)))
        },
        testM("sliding strategy with enough capacity") {
          for {
            queue <- Queue.sliding[Int](100)
            _     <- queue.offer(1)
            _     <- queue.offer(2)
            _     <- queue.offer(3)
            l     <- queue.takeAll
          } yield assert(l, Assertion.equalTo(List(1, 2, 3)))
        },
        testM("sliding strategy with offerAll and takeAll") {
          for {
            queue <- Queue.sliding[Int](2)
            v1    <- queue.offerAll(Iterable(1, 2, 3, 4, 5, 6))
            l     <- queue.takeAll
          } yield assert((l, v1), Assertion.equalTo((List(5, 6), false)))
        },
        testM("awaitShutdown") {
          for {
            queue <- Queue.bounded[Int](3)
            p     <- Promise.make[Nothing, Boolean]
            _     <- (queue.awaitShutdown *> p.succeed(true)).fork
            _     <- queue.shutdown
            res   <- p.await
          } yield assert(res, Assertion.isTrue)
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
          } yield assert((res1, res2), Assertion.equalTo((true, true)))
        },
        testM("awaitShutdown when queue is already shutdown") {
          for {
            queue <- Queue.bounded[Int](3)
            _     <- queue.shutdown
            p     <- Promise.make[Nothing, Boolean]
            _     <- (queue.awaitShutdown *> p.succeed(true)).fork
            res   <- p.await
          } yield assert(res, Assertion.isTrue)
        },
        testM("dropping strategy with offerAll") {
          for {
            capacity <- IO.succeed(4)
            queue    <- Queue.dropping[Int](capacity)
            iter     = Range.inclusive(1, 5)
            _        <- queue.offerAll(iter)
            ta       <- queue.takeAll
          } yield assert(ta, Assertion.equalTo(List(1, 2, 3, 4)))
        },
        testM("dropping strategy with offerAll, check offer returns false") {
          for {
            capacity <- IO.succeed(2)
            queue    <- Queue.dropping[Int](capacity)
            v1       <- queue.offerAll(Iterable(1, 2, 3, 4, 5, 6))
            _        <- queue.takeAll
          } yield assert(v1, Assertion.isFalse)
        },
        testM("dropping strategy with offerAll, check ordering") {
          for {
            capacity <- IO.succeed(128)
            queue    <- Queue.dropping[Int](capacity)
            iter     = Range.inclusive(1, 256)
            _        <- queue.offerAll(iter)
            ta       <- queue.takeAll
          } yield assert(ta, Assertion.equalTo(Range.inclusive(1, 128).toList))
        },
        testM("dropping strategy with pending taker") {
          for {
            capacity <- IO.succeed(2)
            queue    <- Queue.dropping[Int](capacity)
            iter     = Range.inclusive(1, 4)
            f        <- queue.take.fork
            _        <- waitForSize(queue, -1)
            oa       <- queue.offerAll(iter.toList)
            j        <- f.join
          } yield assert((j, oa), Assertion.equalTo((1, false)))
        },
        testM("sliding strategy with pending taker") {
          for {
            capacity <- IO.succeed(2)
            queue    <- Queue.sliding[Int](capacity)
            iter     = Range.inclusive(1, 4)
            _        <- queue.take.fork
            _        <- waitForSize(queue, -1)
            oa       <- queue.offerAll(iter.toList)
            t        <- queue.take
          } yield assert((t, oa), Assertion.equalTo((3, false)))
        },
        testM("sliding strategy, check offerAll returns true") {
          for {
            capacity <- IO.succeed(5)
            queue    <- Queue.sliding[Int](capacity)
            iter     = Range.inclusive(1, 3)
            oa       <- queue.offerAll(iter.toList)
          } yield assert(oa, Assertion.isTrue)
        },
        testM("bounded strategy, check offerAll returns true") {
          for {
            capacity <- IO.succeed(5)
            queue    <- Queue.bounded[Int](capacity)
            iter     = Range.inclusive(1, 3)
            oa       <- queue.offerAll(iter.toList)
          } yield assert(oa, Assertion.isTrue)
        },
        testM("poll on empty queue") {
          for {
            queue <- Queue.bounded[Int](5)
            t     <- queue.poll
          } yield assert(t, Assertion.isNone)
        },
        testM("poll on queue just emptied") {
          for {
            queue <- Queue.bounded[Int](5)
            iter  = Range.inclusive(1, 4)
            _     <- queue.offerAll(iter.toList)
            _     <- queue.takeAll
            t     <- queue.poll
          } yield assert(t, Assertion.isNone)
        },
        testM("multiple polls") {
          for {
            queue <- Queue.bounded[Int](5)
            iter  = Range.inclusive(1, 2)
            _     <- queue.offerAll(iter.toList)
            t1    <- queue.poll
            t2    <- queue.poll
            t3    <- queue.poll
            t4    <- queue.poll
          } yield assert(
            (t1, t2, t3, t4),
            Assertion.equalTo((Option(1), Option(2), Option.empty[Int], Option.empty[Int]))
          )
        },
        testM("queue map") {
          for {
            q <- Queue.bounded[Int](100).map(_.map(_.toString))
            _ <- q.offer(10)
            v <- q.take
          } yield assert(v, Assertion.equalTo("10"))
        },
        testM("queue map identity") {
          for {
            q <- Queue.bounded[Int](100).map(_.map(identity))
            _ <- q.offer(10)
            v <- q.take
          } yield assert(v, Assertion.equalTo(10))
        },
        testM("queue mapM") {
          for {
            q <- Queue.bounded[Int](100).map(_.mapM(IO.succeed))
            _ <- q.offer(10)
            v <- q.take
          } yield assert(v, Assertion.equalTo(10))
        },
        testM("queue mapM with success") {
          for {
            q <- Queue.bounded[IO[String, Int]](100).map(_.mapM(identity))
            _ <- q.offer(IO.succeed(10))
            v <- q.take.sandbox.either
          } yield assert(v, Assertion.isRight(Assertion.equalTo(10)))
        },
        testM("queue mapM with failure") {
          for {
            q <- Queue.bounded[IO[String, Int]](100).map(_.mapM(identity))
            _ <- q.offer(IO.fail("Ouch"))
            v <- q.take.sandbox.either
          } yield assert(v, Assertion.isLeft(Assertion.equalTo(Cause.fail("Ouch"))))
        },
        testM("queue both") {
          for {
            q1 <- Queue.bounded[Int](100)
            q2 <- Queue.bounded[Int](100)
            q  = q1 both q2
            _  <- q.offer(10)
            v  <- q.take
          } yield assert(v, Assertion.equalTo((10, 10)))
        },
        testM("queue contramap") {
          for {
            q <- Queue.bounded[String](100).map(_.contramap[Int](_.toString))
            _ <- q.offer(10)
            v <- q.take
          } yield assert(v, Assertion.equalTo("10"))
        },
        testM("queue filterInput") {
          for {
            q  <- Queue.bounded[Int](100).map(_.filterInput[Int](_ % 2 == 0))
            _  <- q.offer(1)
            s1 <- q.size
            _  <- q.offer(2)
            s2 <- q.size
          } yield assert((s1, s2), Assertion.equalTo((0, 1)))
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
          } yield assert((r1, r2, r3, r4), Assertion.equalTo((false, false, false, true)))
        },
        testM("shutdown race condition with offer") {
          nonFlaky {
            for {
              q <- Queue.bounded[Int](2)
              f <- q.offer(1).forever.fork
              _ <- q.shutdown
              _ <- f.await
            } yield true
          }.map(assert(_, Assertion.isTrue))
        },
        testM("shutdown race condition with take") {
          nonFlaky {
            for {
              q <- Queue.bounded[Int](2)
              _ <- q.offer(1)
              _ <- q.offer(1)
              f <- q.take.forever.fork
              _ <- q.shutdown
              _ <- f.await
            } yield true
          }.map(assert(_, Assertion.isTrue))
        }
      )
    )

object ZQueueSpecUtil {
  def waitForSize[A](queue: Queue[A], size: Int): UIO[Int] =
    (queue.size <* clock.sleep(10.millis)).repeat(ZSchedule.doWhile(_ != size)).provide(Clock.Live)
}
