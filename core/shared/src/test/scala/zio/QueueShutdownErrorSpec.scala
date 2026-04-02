package zio

import zio.test._
import zio.test.Assertion._

object QueueShutdownErrorSpec extends ZIOSpecDefault {
  def spec = suite("QueueShutdownErrorSpec")(
    test("shutdown with error propagates to take") {
      for {
        queue <- Queue.bounded[Int](10)
        _     <- queue.shutdown(new Exception("boom"))
        exit  <- queue.take.exit
      } yield assert(exit)(fails(hasMessage(equalTo("boom"))))
    },
    test("shutdown with error propagates to offer") {
      for {
        queue <- Queue.bounded[Int](10)
        _     <- queue.shutdown(new Exception("boom"))
        exit  <- queue.offer(1).exit
      } yield assert(exit)(fails(hasMessage(equalTo("boom"))))
    },
    test("shutdown with error propagates to ZStream.fromQueue") {
      import zio.stream._
      for {
        queue <- Queue.bounded[Int](10)
        _     <- queue.offer(1)
        stream = ZStream.fromQueue(queue)
        _     <- queue.shutdown(new Exception("boom"))
        exit  <- stream.runCollect.exit
      } yield assert(exit)(fails(hasMessage(equalTo("boom"))))
    }
  )
}
