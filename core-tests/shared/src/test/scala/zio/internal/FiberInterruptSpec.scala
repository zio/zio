package zio.internal

import zio.Promise.internal.Pending
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test._
import zio.test.environment.Live
import zio.{ Promise, ZIOBaseSpec }

object FiberInterruptSpec extends ZIOBaseSpec {

  //Flaky
  override def spec: ZSpec[Environment, Failure] =
    suite("FiberInterruptSpec")(
      testM("must interrupt all Promise joiners") {

        /**
         * The idea here to spawn and interrupt as much joiners as possible
         * And to make sure that, all of them got cleaned up
         */
        val io = for {
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
        } yield joinerSize

        assertM(Live.live(io))(equalTo(0))
      }
    )
}
