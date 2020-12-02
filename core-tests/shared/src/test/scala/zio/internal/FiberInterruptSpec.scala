package zio.internal

import zio.Promise.internal.Pending
import zio.clock.Clock
import zio.test.Assertion.equalTo
import zio.test.{ assert, suite, testM }
import zio.{ Promise, ZIOBaseSpec }
import zio.duration._

object FiberInterruptSpec extends ZIOBaseSpec {

  //Flaky
  override def spec =
    suite("FiberInterruptSpec")(
      testM("must interrupt all Promise joiners") {

        /**
         * The idea here, that spawn and interrupt as much joiners as possible
         * To make sure that, all of them got cleaned up
         */
        (for {
          p <- Promise.make[Throwable, Unit]
          loopFiber <- (for {
                         fb <- p.await.fork
                         _  <- fb.interrupt.fork
                       } yield ()).forever.fork
          _ <- loopFiber.interrupt.delay(30.seconds)
          joinerSize <- p.currentState.map {
                          case Pending(joiners) => joiners.size
                          case _                => 0
                        }.delay(1.second) //Just to make sure all races already resolved
          _ <- p.succeed(())

        } yield {
          assert(joinerSize)(equalTo(0))
        }).provideCustomLayer(Clock.live)
      }
    )
}
