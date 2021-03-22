package zio.internal

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.infra.results.{IIIIII_Result, IIII_Result, II_Result}

object RingBufferArbConcurrencyTests {
  /*
   * Tests that [[RingBufferArb.offer]] is atomic.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("1, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("2, 1"), expect = Expect.ACCEPTABLE)
    )
  )
  @State
  class OfferTest {
    val q: RingBufferArb[Int] = RingBufferArb[Int](2)

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
   * Tests that [[RingBufferArb.offer]] honors capacity, i.e. operations are atomic and no values get lost or overwritten.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(
        id = Array("11, 12, 1, 1, -1, -1"),
        expect = Expect.ACCEPTABLE,
        desc = "First thread wins both offers"
      ),
      new Outcome(
        id = Array("21, 22, -1, -1, 1, 1"),
        expect = Expect.ACCEPTABLE,
        desc = "Second thread wins both offers"
      ),
      new Outcome(
        id = Array("11, 21, 1, -1, 1, -1"),
        expect = Expect.ACCEPTABLE,
        desc = "First thread wins first offer, second thread wins second offer"
      ),
      new Outcome(
        id = Array("21, 11, 1, -1, 1, -1"),
        expect = Expect.ACCEPTABLE,
        desc = "Second thread wins first offer, first thread wins second offer"
      )
    )
  )
  @State
  class OfferTestMaxedCapacity {
    val q: RingBufferArb[Int]                = RingBufferArb[Int](2)
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
        expect = Expect.ACCEPTABLE,
        desc = "Both pollers finish before offer starts"
      ),
      /*
       * Only the first offer finishes before pollers start polling.
       * Expect.ACCEPTABLE outcomes are those when at most one poller gets the element during 1st or 2nd poll.
       */
      new Outcome(id = Array("1, -11, -20, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 1, -20, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, -11, 1, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, -11, -20, 1"), expect = Expect.ACCEPTABLE),
      /*
       * Both offers finish before pollers start to poll.
       * Expect.ACCEPTABLE outcomes are those where elements are dequeued exactly once,
       * and if both dequeues happen within a single thread elements are dequeued
       * in order (1 and then 2).
       */
      new Outcome(id = Array("1, 2, -20, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("1, -11, 2, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("1, -11, -20, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 1, 2, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 1, -20, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, -11, 1, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("2, -11, 1, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("2, -11, -20, 1"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 2, 1, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 2, -20, 1"), expect = Expect.ACCEPTABLE)
    )
  )
  @State
  class OfferPollTest {
    val q: RingBufferArb[Int] = RingBufferArb[Int](2)
    var (p11, p12, p21, p22)  = (0, 0, 0, 0)

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
    def arbiter(r: IIII_Result): Unit = {
      r.r1 = p11
      r.r2 = p12
      r.r3 = p21
      r.r4 = p22
    }
  }

  /*
   * Tests that [[RingBufferArb.offerAll]] is atomic.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("1, 2, 3, 4"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("1, 3, 2, 4"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("1, 3, 4, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("3, 4, 1, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("3, 1, 4, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("3, 1, 2, 4"), expect = Expect.ACCEPTABLE)
    )
  )
  @State
  class OfferAllTest {
    val q: RingBufferArb[Int] = RingBufferArb(4)

    @Actor
    def actor1(): Unit = {
      q.offerAll(List(1, 2))
      ()
    }

    @Actor
    def actor2(): Unit = {
      q.offerAll(List(3, 4))
      ()
    }

    @Arbiter
    def arbiter(r: IIII_Result): Unit = {
      r.r1 = q.poll(-1)
      r.r2 = q.poll(-2)
      r.r3 = q.poll(-3)
      r.r4 = q.poll(-4)
    }
  }

  /*
   * Tests that [[RingBufferArb.offerAll]] honors capacity, i.e. operations are atomic and no values get lost or overwritten.
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("1, 2, 0, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("1, 3, 1, 1"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("3, 1, 1, 1"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("3, 4, 2, 0"), expect = Expect.ACCEPTABLE)
    )
  )
  @State
  class OfferAllTestMaxedCapacity {
    val q: RingBufferArb[Int] = RingBufferArb(2)
    var (offer1, offer2)      = (0, 0)

    @Actor
    def actor1(): Unit =
      offer1 = q.offerAll(List(1, 2)).size

    @Actor
    def actor2(): Unit =
      offer2 = q.offerAll(List(3, 4)).size

    @Arbiter
    def arbiter(r: IIII_Result): Unit = {
      r.r1 = q.poll(-1)
      r.r2 = q.poll(-2)

      r.r3 = offer1
      r.r4 = offer2
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
        expect = Expect.ACCEPTABLE,
        desc = "Both pollers finish before offer starts"
      ),
      /*
       * Only the first offer finishes before pollers start polling.
       * Expect.ACCEPTABLE outcomes are those when at most one poller gets the element during 1st or 2nd poll.
       */
      new Outcome(id = Array("1, -11, -20, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 1, -20, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, -11, 1, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, -11, -20, 1"), expect = Expect.ACCEPTABLE),
      /*
       * Both offers finish before pollers start to poll.
       * Expect.ACCEPTABLE outcomes are those where elements are dequeued exactly once,
       * and if both dequeues happen within a single thread elements are dequeued
       * in order (1 and then 2).
       */
      new Outcome(id = Array("1, 2, -20, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("1, -11, 2, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("1, -11, -20, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 1, 2, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 1, -20, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, -11, 1, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("2, -11, 1, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("2, -11, -20, 1"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 2, 1, -21"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("-10, 2, -20, 1"), expect = Expect.ACCEPTABLE)
    )
  )
  @State
  class OfferAllPollUpToTest {
    val q: RingBufferArb[Int] = RingBufferArb(2)
    var (p1, p2, p3, p4)      = (0, 0, 0, 0)

    @Actor
    def actor1(): Unit = {
      q.offerAll(List(1, 2))
      ()
    }

    @Actor
    def actor2(): Unit = {
      val chunk = q.pollUpTo(2).toIndexedSeq
      p1 = chunk.lift(0).getOrElse(-10)
      p2 = chunk.lift(1).getOrElse(-11)
    }

    @Actor
    def actor3(): Unit = {
      val chunk = q.pollUpTo(2).toIndexedSeq
      p3 = chunk.lift(0).getOrElse(-20)
      p4 = chunk.lift(1).getOrElse(-21)
    }

    @Arbiter
    def arbiter(r: IIII_Result): Unit = {
      r.r1 = p1
      r.r2 = p2
      r.r3 = p3
      r.r4 = p4
    }
  }
}
