package zio

import zio.test.Assertion._
import zio.test._

object QueueShutdownCauseSpec extends ZIOBaseSpec {
  def spec = suite("QueueShutdownCauseSpec")(
    test("shutdownCause fails subsequent offers with the cause") {
      for {
        queue <- Queue.bounded[Int](10)
        cause = Cause.fail("boom")
        _     <- queue.shutdownCause(cause)
        exit  <- queue.offer(1).exit
      } yield assert(exit)(failsCause(equalTo(cause)))
    },
    test("shutdownCause fails subsequent takes with the cause") {
      for {
        queue <- Queue.bounded[Int](10)
        cause = Cause.fail("boom")
        _     <- queue.shutdownCause(cause)
        exit  <- queue.take.exit
      } yield assert(exit)(failsCause(equalTo(cause)))
    },
    test("shutdownCause fails existing takers with the cause") {
      for {
        queue <- Queue.bounded[Int](10)
        cause = Cause.fail("boom")
        f     <- queue.take.fork
        _     <- queue.shutdownCause(cause)
        exit  <- f.join.exit
      } yield assert(exit)(failsCause(equalTo(cause)))
    },
    test("shutdownCause fails existing offerors with the cause") {
      for {
        queue <- Queue.bounded[Int](1)
        _     <- queue.offer(1)
        cause = Cause.fail("boom")
        f     <- queue.offer(2).fork
        _     <- queue.shutdownCause(cause)
        exit  <- f.join.exit
      } yield assert(exit)(failsCause(equalTo(cause)))
    },
    test("shutdownCause returns buffered items") {
      for {
        queue <- Queue.bounded[Int](10)
        _     <- queue.offer(1)
        _     <- queue.offer(2)
        cause = Cause.fail("boom")
        items <- queue.shutdownCause(cause)
      } yield assert(items)(equalTo(Chunk(1, 2)))
    },
    test("shutdownCause is atomic") {
      for {
        queue <- Queue.bounded[Int](10)
        cause1 = Cause.fail("boom1")
        cause2 = Cause.fail("boom2")
        _      <- queue.shutdownCause(cause1)
        _      <- queue.shutdownCause(cause2)
        exit   <- queue.take.exit
      } yield assert(exit)(failsCause(equalTo(cause1)))
    },
    test("ZStream.fromQueue fails with the cause") {
      import zio.stream.ZStream
      for {
        queue <- Queue.bounded[Int](10)
        _     <- queue.offer(1)
        cause = Cause.fail("boom")
        _     <- queue.shutdownCause(cause).fork // fork to allow stream to take 1 then fail
        exit  <- ZStream.fromQueue(queue).runCollect.exit
      } yield assert(exit)(failsCause(equalTo(cause)))
    }
  )
}
