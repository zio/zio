package zio.internal

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.infra.results.I_Result

object BoundedHubSingleConcurrencyTests {

  /*
   * Tests that there are no race conditions between publishing and unsubscribing.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("2"), expect = Expect.ACCEPTABLE)
    )
  )
  @State
  class ConcurrentPublishAndUnsubscribe {
    val hub: Hub[Int]                       = new BoundedHubSingle[Int]
    val subscription: Hub.Subscription[Int] = hub.subscribe()
    hub.publish(1)

    @Actor
    def actor1(): Unit = {
      hub.publish(1)
      ()
    }

    @Actor
    def actor2(): Unit =
      subscription.unsubscribe()

    @Arbiter
    def arbiter(r: I_Result): Unit = {
      val subscription = hub.subscribe()
      hub.publish(2)
      r.r1 = subscription.poll(-1)
    }
  }
}
