package zio.internal

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.infra.results.I_Result

object BoundedHubPow2ConcurrencyTests {

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
    val hub: Hub[Int]                       = new BoundedHubPow2[Int](2)
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

  /*
   * Tests that there are no race conditions between polling and unsubscribing.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("-1"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("1"), expect = Expect.ACCEPTABLE)
    )
  )
  @State
  class ConcurrentPollAndUnsubscribe {
    val hub: Hub[Int]                       = new BoundedHubArb[Int](2)
    val subscription: Hub.Subscription[Int] = hub.subscribe()
    hub.publish(1)
    hub.publish(2)
    var p = 0

    @Actor
    def actor1(): Unit = {
      p = subscription.poll(-1)
      ()
    }

    @Actor
    def actor2(): Unit =
      subscription.unsubscribe()

    @Arbiter
    def arbiter(r: I_Result): Unit =
      r.r1 = p
  }
}
