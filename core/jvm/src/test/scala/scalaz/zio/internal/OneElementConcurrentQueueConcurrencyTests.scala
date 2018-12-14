package scalaz.zio.internal

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.annotations.Expect._
import org.openjdk.jcstress.infra.results.{ II_Result, I_Result }

import scalaz.zio.internal.impls.OneElementConcurrentQueue

object OneElementConcurrentQueueConcurrencyTests {
  /*
   * Tests that offer is atomic.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("1"), expect = ACCEPTABLE),
      new Outcome(id = Array("2"), expect = ACCEPTABLE)
    )
  )
  @State
  class OfferTest {
    val q = new OneElementConcurrentQueue[Int]()

    @Actor
    def actor1(): Unit = {
      q.offer(1)
      ()
    }

    @Actor
    def actor2(): Unit = {
      q.offer(2)
      ()
    }

    @Arbiter
    def arbiter(r: I_Result): Unit =
      r.r1 = q.poll(-1)
  }

  /*
   * Tests that poll is atomic.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(
        id = Array("-10, -20"),
        expect = ACCEPTABLE,
        desc = "Both pollers finish before offer starts"
      ),
      new Outcome(
        id = Array("1, -20"),
        expect = ACCEPTABLE,
        desc = "First poller polls offered value"
      ),
      new Outcome(
        id = Array("-10, 1"),
        expect = ACCEPTABLE,
        desc = "Second poller polls offered value"
      )
    )
  )
  @State
  class OfferPollTest {
    val q        = new OneElementConcurrentQueue[Int]()
    var (p1, p2) = (0, 0)

    @Actor
    def actor1(): Unit = {
      q.offer(1)
      ()
    }

    @Actor
    def actor2(): Unit =
      p1 = q.poll(-10)

    @Actor
    def actor3(): Unit =
      p2 = q.poll(-20)

    @Arbiter
    def artiber(r: II_Result): Unit = {
      r.r1 = p1
      r.r2 = p2
    }
  }
}
