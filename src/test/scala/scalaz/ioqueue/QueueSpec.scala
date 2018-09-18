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
      `interruptPutter`to interrupt fiber which is suspended on `offer`. ${upTo(1.second)(e3)}
    `take` is called by fiber waiting on values to be added to the queue and join the fiber to get the added values correctly. ${upTo(
      1.second
    )(e4)}
    fork 10 takers and offer 10 values, the values must be correct after join those fibers ${upTo(
      1.second
    )(e5)}
    fork 10 putters and offer for each one 10 values then take the values 100 times, the values must be correct after join those fibers ${upTo(
      1.second
    )(e6)}
    make capacity = 10, then put 20 values then check if the size is 10 ${upTo(1.second)(e7)}
    the order is preserved even if we exceed the capacity of the queue ${upTo(1.second)(e8)}
    take can be interrupted and all resources are released ${upTo(1.second)(e9)}
    offer can be interrupted and all resources are released ${upTo(1.second)(e10)}
    make an unbounded queue, add and retrieve values in correct order ${upTo(1.second)(e11)}
    make an unbounded queue, add and retrieve all values in correct order ${upTo(1.second)(e12)}
    make an unbounded queue, add, take and retrieve all values returning empty list ${upTo(
      1.second
    )(e13)}
    make a bounded queue, add and retrieve all values in correct order ${upTo(1.second)(e14)}
    make an empty queue, and `takeUpTo` with max = 2, and retrieves no values ${upTo(1.second)(e15)}
    make a bounded queue of size 100 and `takeUpTo` with max = 101, and retrieve nil ${upTo(
      1.second
    )(e16)}
    make a bounded queue, offer 2 values, `takeUpTo` with max = 2, and retrieve 2 values ${upTo(
      1.second
    )(e17)}
    make a bounded queue, offer 4 values, `takeUpTo` with max = 2, and retrieve first 2 values ${upTo(
      1.second
    )(e18)}
    make a bounded queue, offer 4 values, `takeUpTo` with max = 10, and retrieve all 4 values ${upTo(
      1.second
    )(e19)}
    make a bounded queue, offer 4 values, `takeUpTo` with max = 0, and retrieve nil ${upTo(
      1.second
    )(e20)}
    make a bounded queue, offer 1 value, `takeUpTo` with max = -1, and retrieve nil ${upTo(
      1.second
    )(e21)}
    make a bounded queue, offer 2 values, `takeUpTo` with max = 2,
       offer 2 values again, and `takeUpTo` with max = 2 again;
      the first `takeUpTo` should retrieve first 2 values and second `takeUpTo` should retrieve second 2 values in order ${upTo(
      1.second
    )(e22)}
    make a bounded queue, offer 4 values, `takeUpTo` with max = 2, and then `takeUpTo` again with max = 2;
      the first `takeUpTo` should retrieve first 2 values and second `takeUpTo` should retrieve second 2 values in order ${upTo(
      1.second
    )(e23)}
    make a bounded queue of size 3, fork offer 4 values, and `takeUpTo` with max=3 retrives first 3 values in correct order ${upTo(
      1.second
    )(e24)}
    make a bounded queue then call `offerAll` to add all values in the queue ${upTo(1.second)(e25)}
    make a bounded queue then call `offerAll` should enqueue all requests if we the exceed capacity of the queue ${upTo(
      1.second
    )(e26)}
    offerAll can be interrupted fiber which is suspended on `offer` ${upTo(1.second)(e27)}
    offerAll does preserve the order of the List ${upTo(1.second)(e28)}
    offerAll does preserve the order of the List when it exceeds the queue's capacity ${upTo(
      1.second
    )(e29)}
    fork some takers, and offer as many elements as there are takers, the values must be correct after joining those fibers ${upTo(
      1.second
    )(e30)}
    fork some takers, and offer more elements than there are takers, the values must be correct after joining those fibers ${upTo(
      1.second
    )(e31)}
    fork some takers, and offer more elements than there are takers and capacity in the queue, the values must be correct after joining those fibers ${upTo(
      1.second
    )(e32)}
    fork some takers, and offer less elements than there are takers in the queue, the values must be correct after joining those fibers ${upTo(
      1.second
    )(e33)}
    make bounded queue offer more elements than there are takers and capacity in the queue, taking elements should work correctly ${upTo(
      1.second
    )(e34)}
    make bounded queue offer more elements than there are takers and capacity in the queue, taking elements should preserve putters queue order ${upTo(
      1.second
    )(e35)}
    make bounded queue offer one element than offerAll elements without exceeding the queue's capacity, the values should be in correct order ${upTo(
      1.second
    )(e36)}
    make bounded queue offer some elements than offerAll elements exceeding the queue's capacity, the values should be in correct order ${upTo(
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
      check <- (queue.interruptTake(new Exception("interrupt take in e2")) <* IO.sleep(1.millis))
                .repeat(Schedule.doWhile(!_))
      _ <- queue.offer(25)
      v <- queue.take
    } yield (check must beTrue).and(v must_== 25)
  )

  def e3 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](0)
      _     <- queue.offer(14).fork
      check <- (queue.interruptOffer(new Exception("interrupt offer in e3")) <* IO.sleep(1.millis))
                .repeat(Schedule.doWhile(!_))
      _ <- queue.offer(12).fork
      v <- queue.take
    } yield (check must beTrue).and(v must_=== 12)
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

  import scala.concurrent.duration._

  def e5 =
    unsafeRun(for {
      queue <- Queue.bounded[Int](10)
      _     <- IO.forkAll(List.fill(10)(queue.take.void))
      _     <- waitForSize(queue, -10)
      _     <- Range.inclusive(1, 10).map(queue.offer).foldLeft[IO[Nothing, Unit]](IO.unit)(_ *> _)
      _     <- queue.offer(37)
      v     <- queue.take
    } yield v must_=== 37)

  def e6 =
    unsafeRun(for {
      queue <- Queue.bounded[Int](10)
      order = Range.inclusive(1, 10).toList
      _     <- IO.forkAll(order.map(queue.offer))
      _     <- waitForSize(queue, 10)
      l     <- queue.take.repeat(Schedule.recurs(10) *> Schedule.identity[Int].collect)
    } yield l.toSet must_=== order.toSet)

  def e7 =
    unsafeRun(for {
      queue <- Queue.bounded[Int](10)
      _     <- queue.offer(1).repeat(Schedule.recurs(20)).fork
      _     <- waitForSize(queue, 11)
      size  <- queue.size
    } yield size must_=== 11)

  def e8 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](5)
      orders = Range.inclusive(1, 10).toList
      _      <- IO.forkAll(orders.map(n => waitForSize(queue, n - 1) *> queue.offer(n)))
      _      <- waitForSize(queue, 10)
      l <- queue.take
            .repeat(Schedule.recurs(10) *> Schedule.identity[Int].collect.map(_.reverse))
    } yield l.reverse must_=== orders)

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

  def e14 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](999)
      _     <- Range.inclusive(1, 999).map(queue.offer).foldLeft(IO.unit)(_ *> _)
      _     <- queue.offer(1000).fork
      _     <- waitForSize(queue, 1000)
      v     <- queue.takeAll
      c     <- queue.take
    } yield (v must_=== Range.inclusive(1, 999).toList).and(c must_=== 1000)
  )

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
    for {
      queue <- Queue.bounded[Int](3)
      _     <- queue.offer(1).fork
      _     <- queue.offer(2).fork
      _     <- queue.offer(3).fork
      _     <- queue.offer(4).fork
      _     <- waitForSize(queue, 4)
      l     <- queue.takeUpTo(4)
    } yield l must_=== List(1, 2, 3)
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
      _      <- f.interrupt(new Exception("interrupt offer in e17"))
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
      s      <- waitForSize(queue, 0)
    } yield (l.toSet must_=== orders.toSet).and(s must_=== 0))

  def e31 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](2000)
      orders = Range.inclusive(1, 1000).toList
      takers <- IO.forkAll(List.fill(500)(queue.take))
      _      <- waitForSize(queue, -500)
      _      <- queue.offerAll(orders)
      l      <- takers.join
      s      <- waitForSize(queue, 500)
    } yield (l.toSet must_=== orders.take(500).toSet).and(s must_=== 500))

  def e32 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](20)
      orders = Range.inclusive(1, 2000).toList
      takers <- IO.forkAll(List.fill(1000)(queue.take))
      _      <- waitForSize(queue, -1000)
      _      <- queue.offerAll(orders).fork
      l      <- takers.join
      s      <- waitForSize(queue, 1000)
    } yield (l.toSet must_=== orders.take(1000).toSet).and(s must_=== 1000))

  def e33 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](2000)
      orders = Range.inclusive(1, 1000).toList
      takers <- IO.forkAll(List.fill(1000)(queue.take))
      _      <- waitForSize(queue, -1000)
      _      <- IO.forkAll(List.fill(1000)(queue.take))
      _      <- waitForSize(queue, -2000)
      _      <- queue.offerAll(orders)
      l      <- takers.join
      s      <- waitForSize(queue, -1000)
    } yield (l.toSet must_=== orders.toSet).and(s must_=== -1000))

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
