package scalaz.zio

import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.AroundTimeout

import scala.collection.immutable.Range
import scala.concurrent.duration._

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
    fork 10 takers and offer 10 values, the values must be correct after join those fibers ${upTo(1.second)(e5)}
    fork 10 putters and offer for each one 10 values then take the values 100 times, the values must be correct after join those fibers ${upTo(
      1.second
    )(e6)}
    make capacity = 10, then put 20 values then check if the size is 10 ${upTo(1.second)(e7)}
    the order is preserved even if we exceed the capacity of the queue ${upTo(1.second)(e8)}
    take can be interrupted and all resources are released ${upTo(1.second)(e9)}
    offer can be interrupted and all resources are released ${upTo(1.second)(e10)}
    make an unbounded queue, add and retrieve values in correct order ${upTo(1.second)(e11)}
    """

  def e1 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.offer(10)
      v1    <- queue.take
      _     <- queue.offer(20)
      v2    <- queue.take
    } yield (v1 must_=== 10) and (v2 must_=== 20)
  )
  def e2 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](100)
      _     <- queue.take.fork
      check <- (queue.interruptTake(new Exception("interrupt take in e2")) <* IO.sleep(1.millis)).doWhile(!_)
      _     <- queue.offer(25)
      v     <- queue.take
    } yield (check must beTrue) and (v must_== 25)
  )

  def e3 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](0)
      _     <- queue.offer(14).fork
      check <- (queue.interruptOffer(new Exception("interrupt offer in e3")) <* IO.sleep(1.millis)).doWhile(!_)
      _     <- queue.offer(12)
      v     <- queue.take
    } yield (check must beTrue) and (v must_=== 12)
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
      _     <- f.interrupt
      size  <- queue.size
    } yield size must_=== 0
  )

  def e10 = unsafeRun(
    for {
      queue <- Queue.bounded[Int](0)
      f     <- queue.offer(1).fork
      _     <- f.interrupt
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
    } yield (v1 must_=== 1) and (v2 must_=== 2) and (v3 must_=== 3)
  )

  private def waitForSize[A](queue: Queue[A], size: Int): IO[Nothing, Int] =
    (queue.size <* IO.sleep(1.millis)).doWhile(_ != size)
}
