package zio

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.infra.results.IIII_Result

object HubConcurrencyTests {

  val runtime = Runtime.default
  /*
   * Tests that there are no race conditions between publishing and taking..
   */
  @JCStressTest
  @Outcome.Outcomes(
    Array(
      new Outcome(id = Array("1, 2, 1, 2"), expect = Expect.ACCEPTABLE),
      new Outcome(id = Array("2, 1, 2, 1"), expect = Expect.ACCEPTABLE)
    )
  )
  @State
  class ManyToManyTest {
    val hub: Hub[Int] = Unsafe.unsafeCompat(implicit u => runtime.unsafeRun(Hub.bounded(2)))
    val left: Dequeue[Int] = Unsafe.unsafeCompat { implicit u =>
      runtime.unsafeRun(Scope.global.extend[Any](hub.subscribe))
    }
    val right: Dequeue[Int] = Unsafe.unsafeCompat { implicit u =>
      runtime.unsafeRun(Scope.global.extend[Any](hub.subscribe))
    }
    var p1 = 0
    var p2 = 0
    var p3 = 0
    var p4 = 0

    @Actor
    def actor1(): Unit = Unsafe.unsafeCompat { implicit u =>
      runtime.unsafeRun(hub.publish(1))
      ()
    }

    @Actor
    def actor2(): Unit = Unsafe.unsafeCompat { implicit u =>
      runtime.unsafeRun(hub.publish(2))
      ()
    }

    @Actor
    def actor3(): Unit =
      Unsafe.unsafeCompat { implicit u =>
        runtime.unsafeRun {
          left.take.zipWith(left.take) { (first, last) =>
            p1 = first
            p2 = last
          }
        }
      }

    @Actor
    def actor4(): Unit =
      Unsafe.unsafeCompat { implicit u =>
        runtime.unsafeRun {
          right.take.zipWith(right.take) { (first, last) =>
            p3 = first
            p4 = last
          }
        }
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
