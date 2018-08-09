package scalaz.ioqueue

import scala.collection.immutable.Range
import scala.concurrent.duration._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.AroundTimeout

import scalaz.zio.IO

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
    make a bounded queue then call `offerAll` to add all values in the queue ${upTo(1.second)(e15)}
    make a bounded queue then call `offerAll` should enqueue all requests if we the exceed capacity of the queue ${upTo(1.second)(e16)}
    `offerAll` can be interrupted fiber which is suspended on `offer` ${upTo(1.second)(e17)}
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
                .doWhile(!_)
      _ <- queue.offer(25)
      v <- queue.take
    } yield (check must beTrue).and(v must_== 25)
  )

  def e3 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](0)
      _     <- queue.offer(14).fork
      check <- (queue.interruptOffer(new Exception("interrupt offer in e3")) <* IO.sleep(1.millis))
                .doWhile(!_)
      _ <- queue.offer(12)
      v <- queue.take
    } yield (check must beTrue).and(v must_=== 12)
  )

  def e4 = unsafeRun(
    for {
      queue <- Queue.bounded[String](100)
      f1 <- queue.take
             .zipWith(queue.take)(_ + _)
             .fork
      _ <- queue.offer("don't ") *> queue.offer("give up :D")
      v <- f1.join
    } yield v must_=== "don't give up :D"
  )

  import scala.concurrent.duration._

  def e5 =
    unsafeRun(for {
      queue <- Queue.bounded[Int](10)
      _     <- IO.forkAll(List.fill(10)(queue.take.toUnit))
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
      l     <- queue.take.repeatNFold[List[Int]](10)(List.empty[Int], (l, i) => i :: l)
    } yield l.toSet must_=== order.toSet)

  def e7 =
    unsafeRun(for {
      queue <- Queue.bounded[Int](10)
      _     <- queue.offer(1).repeatN(20).fork
      _     <- waitForSize(queue, 11)
      size  <- queue.size
    } yield size must_=== 11)

  def e8 =
    unsafeRun(for {
      queue  <- Queue.bounded[Int](5)
      orders = Range.inclusive(1, 10).toList
      _      <- IO.forkAll(orders.map(n => waitForSize(queue, n - 1) *> queue.offer(n)))
      _      <- waitForSize(queue, 10)
      l      <- queue.take.repeatNFold[List[Int]](10)(List.empty[Int], (l, i) => i :: l)
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
      queue <- Queue.bounded[Int](3)
      _     <- queue.offer(1).fork
      _     <- queue.offer(2).fork
      _     <- queue.offer(3).fork
      _     <- queue.offer(4).fork
      _     <- waitForSize(queue, 4)
      v     <- queue.takeAll
      c     <- queue.take
    } yield (v must_=== List(1, 2, 3)).and(c must_=== 4)
  )

  def e15 =  unsafeRun(for {
    queue <- Queue.bounded[Int](10)
    orders = Range.inclusive(1, 10).toList
    _     <- queue.offerAll(orders)
  _       <- waitForSize(queue, 10)
    l     <- queue.takeAll
  } yield l must_=== orders)

  def e16 =  unsafeRun(for {
    queue <- Queue.bounded[Int](0)
    orders = Range.inclusive(1, 3).toList
    _     <- queue.offerAll(orders).fork
    size  <- waitForSize(queue, 3)
  } yield size must_=== 3)

  def e17 =  unsafeRun(for {
    queue <- Queue.bounded[Int](0)
    orders = Range.inclusive(1, 3).toList
    _     <- queue.offerAll(orders).fork
    check <- (queue.interruptOffer(new Exception("interrupt offer in e17")) <* IO.sleep(1.millis))
              .doWhile(!_)
    size  <- waitForSize(queue, 0)
  } yield (size must_=== 0) and (check must_=== true))

  private def waitForSize[A](queue: Queue[A], size: Int): IO[Nothing, Int] =
    (queue.size <* IO.sleep(1.millis)).doWhile(_ != size)
}
