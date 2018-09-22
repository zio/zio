package scalaz.ioqueue

import scala.collection.immutable.Range
import scala.concurrent.duration._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.AroundTimeout

import scalaz.zio._

class QueueSpec(implicit ee: ExecutionEnv) extends AbstractRTSSpec with AroundTimeout {

  def is =
    "QueueSpec".title ^ s2"""
    Make a Queue and
    add values then call
      `take` to retrieve them in correct order. ${upTo(1.second)(e1)}
      `interruptTake`to interrupt fiber which is suspended on `take`. ${upTo(1.second)(e2)}
      `interruptOffer`to interrupt fiber which is suspended on `offer`. ${upTo(1.second)(e3)}
    `take` is called by fiber waiting on values to be added to the queue and join the fiber to get the added values correctly. ${upTo(
      1.second
    )(e4)}
    fork 10 takers and offer 10 values, join takers, the result must contain all offered values ${upTo(
      1.second
    )(e5)}
    fork 10 putters and offer for each one 10 values then take the values 100 times, the values must be correct after join those fibers ${upTo(
      1.second
    )(e6)}
    make a bounded queue with capacity = 10, then put 10 values then add 10 other values and check that `offer`is suspended ${upTo(
      1.second
    )(e7)}
    make a bounded queue with capacity = 5, offer 10 values in a fiber and check that you can take the 10 values ${upTo(
      1.second
    )(e8)}
    `take` can be interrupted and all resources in takers are released ${upTo(1.second)(e9)}
    `offer` can be interrupted and all resources in putters are released ${upTo(1.second)(e10)}

    in an unbounded queue add values then call `take`, the values must be in correct order ${upTo(
      1.second
    )(e11)}
    in an unbounded queue add values then call `takeAll`, the values must be in correct order ${upTo(
      1.second
    )(e12)}
    in an unbounded queue call `takeAll` in an empty queue must return an empty list $e13
    in a queue with capacity = 3 add 4 values then call `takeAll`, it must return a list with the 3 first values in correct order $e14

    make an empty queue, and `takeUpTo` with max = 2, must return an empty list ${upTo(1.second)(
      e15
    )}
    make a bounded queue of size 100, call `takeUpTo` with max = 101  without adding values must return an empty list ${upTo(
      1.second
    )(e16)}
    make a bounded queue, offer 2 values, `takeUpTo` with max = 2, must return a list that contains the first 2 offered values ${upTo(
      1.second
    )(e17)}
    make a bounded queue, offer 4 values, `takeUpTo` with max = 2, must return a list that contains the first 2 values ${upTo(
      1.second
    )(e18)}
    make a bounded queue, offer 4 values, `takeUpTo` with max = 10, must return a list that contains the offered values ${upTo(
      1.second
    )(e19)}
    make a bounded queue, offer 4 values, `takeUpTo` with max = 0, must return an empty list ${upTo(
      1.second
    )(e20)}
    make a bounded queue, offer 1 value, `takeUpTo` with max = -1, must return an empty list ${upTo(
      1.second
    )(e21)}
    make a bounded queue, offer 2 values, `takeUpTo` with max = 2, offer 2 values again, and `takeUpTo` with max = 2 again,
      the first result must be a list that contains the first 2 values and the second one must be a list with the second 2 values in order ${upTo(
      1.second
    )(e22)}
    make a bounded queue, offer 4 values, `takeUpTo` with max = 2, and then `takeUpTo` again with max = 2;
      the first result must contain the first 2 values and the second one must contain the next 2 values in order ${upTo(
      1.second
    )(e23)}
    make a bounded queue of size 3, fork offer 4 values, and `takeUpTo` with max=4 must return a list that contains the first 3 values in correct order ${upTo(
      1.second
    )(e24)}
    make a bounded queue of size 10 then call `offerAll` with a list of 10 elements to add all values in the queue ${upTo(
      1.second
    )(e25)}
    make a bounded queue of size 0 then call `offerAll` with a list of 3 elements. The producer should be suspended and the queue should have the same size as the elements offered ${upTo(
      1.second
    )(e26)}
    `offerAll` can be interrupted and all resources are released ${upTo(1.second)(e27)}
    `offerAll should preserve the order of the list ${upTo(1.second)(e28)}
    `offerAll` does preserve the order of the list when it exceeds the queue's capacity ${upTo(
      1.second
    )(e29)}
    make a bounded queue of size 1000 then fork 2000 takers, and offer as many elements as there are takers, the values must be correct after joining those fibers ${upTo(
      1.second
    )(e30)}
    make a bounded queue of size 2000 then fork 500 takers, and offer more elements than there are takers, the values must be correct after joining those fibers ${upTo(
      1.second
    )(e31)}
    make a bounded queue of size 20 then fork 1000 takers, and offer more elements than there are takers and capacity in the queue, the values must be correct after joining those fibers ${upTo(
      1.second
    )(e32)}
    fork some takers, and offer less elements than there are takers in the queue, the values must be correct after joining those fibers ${upTo(
      1.second
    )(e33)}
    make bounded queue of size 0 then offer more elements than there is capacity in the queue, taking elements should work correctly ${upTo(
      1.second
    )(e34)}
    make bounded queue offer more elements than there are takers and capacity in the queue, taking elements should preserve putters queue order ${upTo(
      1.second
    )(e35)}
    make bounded queue of size 1000 then `offer` one element then `offerAll` some elements without exceeding the queue's capacity, when calling `takeAll` the values should be in correct order ${upTo(
      1.second
    )(e36)}
    make bounded queue `offer` some elements then `offerAll` elements exceeding the queue's capacity, the values should be in correct order ${upTo(
      1.second
    )(e37)}
    """

  def e1 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.offer(10)
      v1    <- queue.take
      _     <- queue.offer(20)
      v2    <- queue.take
    } yield (v1 must_=== 10).and(v2 must_=== 20)
  )

  def e2 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.take.fork
      check <- (queue.interruptTake(new Exception("interrupt take in e2")) <* IO
                .sleep(1.millis))
                .repeat(Schedule.doWhile(!_))
      _ <- queue.offer(25)
      v <- queue.take
    } yield (check must beTrue).and(v must_== 25)
  )

  def e3 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](0)
      _     <- queue.offer(14).fork
      check <- (queue
                .interruptOffer(new Exception("interrupt offer in e3")) <* IO.sleep(1.millis))
                .repeat(Schedule.doWhile(!_))
      size <- queue.size
    } yield (check must beTrue).and(size must_=== 0)
  )

  def e4 = unsafeRun(
    for {
      queue <- Queue.bounded[String](100)
      f1 <- queue.take
             .seqWith(queue.take)(_ + _)
             .fork
      _ <- queue.offer("don't ") *> queue.offer("give up :D")
      v <- f1.join
    } yield v must_=== "don't give up :D"
  )

  def e5 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](10)
      f      <- IO.forkAll(List.fill(10)(queue.take))
      values = Range.inclusive(1, 10).toList
      _      <- values.map(queue.offer).foldLeft[IO[Nothing, Unit]](IO.unit)(_ *> _)
      v      <- f.join
    } yield v must containTheSameElementsAs(values))

  def e6 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](10)
      values = Range.inclusive(1, 10).toList
      f      <- IO.forkAll(values.map(queue.offer))
      _      <- waitForSize(queue, 10)
      l      <- queue.take.repeat(Schedule.recurs(10) *> Schedule.identity[Int].collect)
      _      <- f.join
    } yield l must containTheSameElementsAs(values))

  def e7 =
    unsafeRun((for {
      queue        <- Queue.bounded[Int](10)
      _            <- queue.offer(1).repeat(Schedule.recurs(10))
      refSuspended <- Ref[Boolean](true)
      _            <- (queue.offer(2).repeat(Schedule.recurs(10)) *> refSuspended.set(false)).fork
      isSuspended  <- refSuspended.get
    } yield isSuspended must_=== true).supervised)

  def e8 =
    unsafeRun(
      for {
        queue  <- Queue.bounded[Int](5)
        values = Range.inclusive(1, 10).toList
        _      <- IO.forkAll(values.map(queue.offer))
        _      <- waitForSize(queue, 10)
        l <- queue.take
              .repeat(Schedule.recurs(10) *> Schedule.identity[Int].collect)
      } yield l must containTheSameElementsAs(values)
    )

  def e9 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      f     <- queue.take.fork
      _     <- f.interrupt(new Exception("interrupt fiber in e9"))
      size  <- queue.size
    } yield size must_=== 0
  )

  def e10 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](0)
      f     <- queue.offer(1).fork
      _     <- f.interrupt(new Exception("interrupt fiber in e10"))
      size  <- queue.size
    } yield size must_=== 0
  )

  def e11 = unsafeRun(
    for {
      queue <- Queue.unbounded[Int]
      _     <- queue.offer(1)
      _     <- queue.offer(2)
      _     <- queue.offer(3)
      v1    <- queue.take
      v2    <- queue.take
      v3    <- queue.take
    } yield (v1 must_=== 1).and(v2 must_=== 2).and(v3 must_=== 3)
  )

  def e12 = unsafeRun(
    for {
      queue <- Queue.unbounded[Int]
      _     <- queue.offer(1)
      _     <- queue.offer(2)
      _     <- queue.offer(3)
      v     <- queue.takeAll
    } yield v must_=== List(1, 2, 3)
  )

  def e13 = unsafeRun(
    for {
      queue <- Queue.unbounded[Int]
      c     <- queue.takeAll
      _     <- queue.offer(1)
      _     <- queue.take
      v     <- queue.takeAll
    } yield (c must_=== List.empty).and(v must_=== List.empty)
  )

  def e14 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](3)
      values = List(1, 2, 3)
      _      <- values.map(queue.offer).foldLeft(IO.unit)(_ *> _)
      _      <- queue.offer(4).fork
      _      <- waitForSize(queue, 4)
      v      <- queue.takeAll
      c      <- queue.take
    } yield (v must containTheSameElementsAs(values)).and(c must_=== 4))

  def e15 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      list  <- queue.takeUpTo(2)
    } yield list must_=== Nil
  )

  def e16 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      list  <- queue.takeUpTo(101)
    } yield list must_=== Nil
  )

  def e17 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.offer(10)
      _     <- queue.offer(20)
      list  <- queue.takeUpTo(2)
    } yield list must_=== List(10, 20)
  )

  def e18 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.offer(10)
      _     <- queue.offer(20)
      _     <- queue.offer(30)
      _     <- queue.offer(40)
      list  <- queue.takeUpTo(2)
    } yield list must_=== List(10, 20)
  )

  def e19 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.offer(10)
      _     <- queue.offer(20)
      _     <- queue.offer(30)
      _     <- queue.offer(40)
      list  <- queue.takeUpTo(10)
    } yield list must_=== List(10, 20, 30, 40)
  )

  def e20 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.offer(10)
      _     <- queue.offer(20)
      _     <- queue.offer(30)
      _     <- queue.offer(40)
      list  <- queue.takeUpTo(0)
    } yield list must_=== Nil
  )

  def e21 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.offer(10)
      list  <- queue.takeUpTo(-1)
    } yield list must_=== Nil
  )

  def e22 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.offer(10)
      _     <- queue.offer(20)
      list1 <- queue.takeUpTo(2)
      _     <- queue.offer(30)
      _     <- queue.offer(40)
      list2 <- queue.takeUpTo(2)
    } yield (list1, list2) must_=== ((List(10, 20), List(30, 40)))
  )

  def e23 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.offer(10)
      _     <- queue.offer(20)
      _     <- queue.offer(30)
      _     <- queue.offer(40)
      list1 <- queue.takeUpTo(2)
      list2 <- queue.takeUpTo(2)
    } yield (list1, list2) must_=== ((List(10, 20), List(30, 40)))
  )

  def e24 = unsafeRun(
    (for {
      queue  <- Queue.bounded[Int](3)
      values = List(1, 2, 3)
      _      <- values.map(queue.offer).foldLeft(IO.unit)(_ *> _)
      _      <- queue.offer(4).fork
      _      <- waitForSize(queue, 4)
      l      <- queue.takeUpTo(4)
    } yield l must_=== List(1, 2, 3)).supervised
  )

  def e25 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](10)
      orders = Range.inclusive(1, 10).toList
      _      <- queue.offerAll(orders)
      _      <- waitForSize(queue, 10)
      l      <- queue.takeAll
    } yield l must_=== orders)

  def e26 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](0)
      orders = Range.inclusive(1, 3).toList
      _      <- queue.offerAll(orders).fork
      size   <- waitForSize(queue, 3)
      l      <- queue.takeAll
    } yield (size must_=== 3).and(l must_=== Nil))

  def e27 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](0)
      orders = Range.inclusive(1, 3).toList
      f      <- queue.offerAll(orders).fork
      _      <- f.interrupt(new Exception("interrupt offer in e27"))
      l      <- queue.takeAll
    } yield l must_=== Nil)

  def e28 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](1000)
      orders = Range.inclusive(1, 1000).toList
      _      <- queue.offerAll(orders)
      _      <- waitForSize(queue, 1000)
      l      <- queue.takeAll
    } yield l must_=== orders)

  def e29 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](1000)
      orders = Range.inclusive(1, 2000).toList
      _      <- queue.offerAll(orders).fork
      _      <- waitForSize(queue, 2000)
      l      <- queue.takeAll
    } yield l must_=== Range.inclusive(1, 1000).toList)

  def e30 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](1000)
      orders = Range.inclusive(1, 2000).toList
      takers <- IO.forkAll(List.fill(2000)(queue.take))
      _      <- waitForSize(queue, -2000)
      _      <- queue.offerAll(orders)
      l      <- takers.join
      s      <- queue.size
    } yield (l.toSet must_=== orders.toSet).and(s must_=== 0))

  def e31 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](2000)
      orders = Range.inclusive(1, 1000).toList
      takers <- IO.forkAll(List.fill(500)(queue.take))
      _      <- waitForSize(queue, -500)
      _      <- queue.offerAll(orders)
      l      <- takers.join
      s      <- queue.size
      values = orders.take(500)
    } yield (l must containTheSameElementsAs(values)).and(s must_=== 500))

  def e32 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](20)
      orders = Range.inclusive(1, 2000).toList
      takers <- IO.forkAll(List.fill(1000)(queue.take))
      _      <- waitForSize(queue, -1000)
      _      <- queue.offerAll(orders).fork
      l      <- takers.join
      s      <- queue.size
      values = orders.take(1000)
    } yield (l must containTheSameElementsAs(values)).and(s must_=== 1000))

  def e33 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](2000)
      values = Range.inclusive(1, 1000).toList
      takers <- IO.forkAll(List.fill(1000)(queue.take))
      _      <- waitForSize(queue, -1000)
      _      <- IO.forkAll(List.fill(1000)(queue.take))
      _      <- waitForSize(queue, -2000)
      _      <- queue.offerAll(values)
      l      <- takers.join
      s      <- queue.size
    } yield (l must containTheSameElementsAs(values)).and(s must_=== -1000))

  def e34 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](0)
      orders = Range.inclusive(1, 3).toList
      _      <- queue.offerAll(orders).fork
      _      <- waitForSize(queue, 3)
      v1     <- queue.take
      v2     <- queue.take
      v3     <- queue.take
    } yield (v1 must_=== 1).and(v2 must_=== 2).and(v3 must_=== 3))

  def e35 =
    unsafeRun(
      for {
        queue   <- Queue.bounded[Int](0)
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
      } yield
        (v1 must_=== 1).and(v2 must_=== 2).and(v3 must_=== 3).and(v4 must_=== 4).and(v5 must_=== 5)
    )

  def e36 =
    unsafeRun(
      for {
        queue  <- Queue.bounded[Int](1000)
        orders = Range.inclusive(2, 1000).toList
        _      <- queue.offer(1)
        _      <- queue.offerAll(orders)
        _      <- waitForSize(queue, 1000)
        v1     <- queue.takeAll
      } yield v1 must_=== Range.inclusive(1, 1000).toList
    )

  def e37 =
    unsafeRun(
      for {
        queue  <- Queue.bounded[Int](1000)
        orders = Range.inclusive(3, 1003).toList
        _      <- queue.offer(1)
        _      <- queue.offer(2)
        _      <- queue.offerAll(orders).fork
        _      <- waitForSize(queue, 1003)
        v      <- queue.takeAll
        v1     <- queue.take
        v2     <- queue.take
        v3     <- queue.take
      } yield
        (v must_=== Range.inclusive(1, 1000).toList)
          .and(v1 must_=== 1001)
          .and(v2 must_=== 1002)
          .and(v3 must_=== 1003)
    )

  private def waitForSize[A](queue: Queue[A], size: Int): IO[Nothing, Int] =
    (queue.size <* IO.sleep(1.millis)).repeat(Schedule.doWhile(_ != size))

}
