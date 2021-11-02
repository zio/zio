package zio.concurrent

import zio._
import zio.test._
import zio.test.Assertion._

object CountdownLatchSpec extends ZIOBaseSpec {
  val spec: ZSpec[Environment, Failure] =
    suite("CountdownLatchSpec")(
      suite("Construction")(
        testM("Creates a latch") {
          assertM(CountdownLatch.make(100).flatMap(_.count).run)(succeeds(equalTo(100)))
        },
        testM("Fails with an invalid count") {
          assertM(CountdownLatch.make(0).run)(fails(equalTo(None)))
        }
      ),
      suite("Operations")(
        testM("Fibers wait and get released when countdown reaches 0") {
          for {
            latch  <- CountdownLatch.make(100)
            count  <- Ref.make(0)
            ps     <- ZIO.collectAll(List.fill(10)(Promise.make[Nothing, Unit]))
            _      <- ZIO.forkAll(ps.map(p => latch.await *> count.update(_ + 1) *> p.succeed(())))
            _      <- latch.countDown.repeat(Schedule.recurs(99))
            _      <- ZIO.foreach_(ps)(_.await)
            result <- count.get
          } yield assert(result)(equalTo(10))
        }
      )
    )
}
