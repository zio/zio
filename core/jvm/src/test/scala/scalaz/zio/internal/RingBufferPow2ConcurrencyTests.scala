package scalaz.zio.internal

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.annotations.Expect._
import org.openjdk.jcstress.infra.results.{ IIIIII_Result, IIII_Result, II_Result }

import scalaz.zio.internal.impls.RingBufferPow2

object RingBufferPow2ConcurrencyTests {
  /*
   * Tests that [[RingBufferPow2.offer]] is atomic.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("1, 2"), expect = ACCEPTABLE),
      new Outcome(id = Array("2, 1"), expect = ACCEPTABLE)
    )
  )
  @State
  class OfferTest {
    val q = new RingBufferPow2[Int](2)

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
    def arbiter(r: II_Result): Unit = {
      r.r1 = q.poll(-1)
      r.r2 = q.poll(-2)
    }
  }

  /*
   * Tests that [[RingBufferPow2.offer]] honors capacity, i.e. operations are atomic and no values get lost or overwritten.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("11, 12, 1, 1, -1, -1"), expect = ACCEPTABLE, desc = "First thread wins both offers"),
      new Outcome(id = Array("21, 22, -1, -1, 1, 1"), expect = ACCEPTABLE, desc = "Second thread wins both offers"),
      new Outcome(
        id = Array("11, 21, 1, -1, 1, -1"),
        expect = ACCEPTABLE,
        desc = "First thread wins first offer, second thread wins second offer"
      ),
      new Outcome(
        id = Array("21, 11, 1, -1, 1, -1"),
        expect = ACCEPTABLE,
        desc = "Second thread wins first offer, first thread wins second offer"
      )
    )
  )
  @State
  class OfferTestMaxedCapacity {
    val q                                    = new RingBufferPow2[Int](2)
    var (offer11, offer12, offer21, offer22) = (0, 0, 0, 0)

    @Actor
    def actor1(): Unit = {
      offer11 = if (q.offer(11)) 1 else -1
      offer12 = if (q.offer(12)) 1 else -1
      ()
    }

    @Actor
    def actor2(): Unit = {
      offer21 = if (q.offer(21)) 1 else -1
      offer22 = if (q.offer(22)) 1 else -1
      ()
    }

    @Arbiter
    def arbiter(r: IIIIII_Result): Unit = {
      r.r1 = q.poll(-1)
      r.r2 = q.poll(-2)

      r.r3 = offer11
      r.r4 = offer12

      r.r5 = offer21
      r.r6 = offer22
    }
  }

  /*
   * Tests that polls are atomic, i.e. no value gets polled twice or not polled at all.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(
        id = Array("-10, -11, -20, -21"),
        expect = ACCEPTABLE,
        desc = "Both pollers finish before offer starts"
      ),
      /*
       * Only the first offer finishes before pollers start polling.
       * Acceptable outcomes are those when at most one poller gets the element during 1st or 2nd poll.
       */
      new Outcome(id = Array("1, -11, -20, -21"), expect = ACCEPTABLE),
      new Outcome(id = Array("-10, 1, -20, -21"), expect = ACCEPTABLE),
      new Outcome(id = Array("-10, -11, 1, -21"), expect = ACCEPTABLE),
      new Outcome(id = Array("-10, -11, -20, 1"), expect = ACCEPTABLE),
      /*
       * Both offers finish before pollers start to poll.
       * Acceptable outcomes are those where elements are dequeued exactly once,
       * and if both dequeues happen within a single thread elements are dequeued
       * in order (1 and then 2).
       */
      new Outcome(id = Array("1, 2, -20, -21"), expect = ACCEPTABLE),
      new Outcome(id = Array("1, -11, 2, -21"), expect = ACCEPTABLE),
      new Outcome(id = Array("1, -11, -20, 2"), expect = ACCEPTABLE),
      new Outcome(id = Array("-10, 1, 2, -21"), expect = ACCEPTABLE),
      new Outcome(id = Array("-10, 1, -20, 2"), expect = ACCEPTABLE),
      new Outcome(id = Array("-10, -11, 1, 2"), expect = ACCEPTABLE),
      new Outcome(id = Array("2, -11, 1, -21"), expect = ACCEPTABLE),
      new Outcome(id = Array("2, -11, -20, 1"), expect = ACCEPTABLE),
      new Outcome(id = Array("-10, 2, 1, -21"), expect = ACCEPTABLE),
      new Outcome(id = Array("-10, 2, -20, 1"), expect = ACCEPTABLE)
    )
  )
  @State
  class OfferPollTest {
    val q                    = new RingBufferPow2[Int](2)
    var (p11, p12, p21, p22) = (0, 0, 0, 0)

    @Actor
    def actor1(): Unit = {
      q.offer(1)
      q.offer(2)
      ()
    }

    @Actor
    def actor2(): Unit = {
      p11 = q.poll(-10)
      p12 = q.poll(-11)
    }

    @Actor
    def actor3(): Unit = {
      p21 = q.poll(-20)
      p22 = q.poll(-21)
    }

    @Arbiter
    def artiber(r: IIII_Result): Unit = {
      r.r1 = p11
      r.r2 = p12
      r.r3 = p21
      r.r4 = p22
    }
  }
}
