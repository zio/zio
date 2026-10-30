package zio

import zio.test._

object ConcurrentDequeSpec extends ZIOBaseSpec {

  def spec = suite("ConcurrentDequeSpec")(
    test("ConcurrentDeque implements java.util.Deque with FIFO and LIFO ordering") {
      val deque: java.util.Deque[Int] = new Queue.ConcurrentDeque[Int]()
      deque.offer(1)
      deque.offer(2)
      deque.addFirst(0)
      assertTrue(
        deque.pollFirst() == 0,
        deque.pollFirst() == 1,
        deque.pollLast() == 2,
        deque.isEmpty
      )
    },
    test("bounded queue preserves ordering across back-pressured offers and takes") {
      for {
        queue   <- Queue.bounded[Int](4)
        expected = Chunk.fromIterable(1 to 32)
        fiber   <- ZIO.foreach(expected)(queue.offer).fork
        actual  <- ZIO.foreach(expected)(_ => queue.take)
        _       <- fiber.join
      } yield assertTrue(actual == expected)
    }
  )
}
