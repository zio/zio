package zio.internal

import org.openjdk.jcstress.annotations._
import org.openjdk.jcstress.infra.results.II_Result

object MailboxConcurrencyTests {

  @JCStressTest
  @Outcome(id = Array("1, 2"), expect = Expect.ACCEPTABLE)
  @Outcome(id = Array("2, 1"), expect = Expect.ACCEPTABLE)
  @State
  class ConcurrentAddTest {

    val q = new Mailbox[Int]()

    @Actor
    def actor1(): Unit =
      q.add(1)

    @Actor
    def actor2(): Unit =
      q.add(2)

    @Arbiter
    def arbiter(r: II_Result): Unit = {
      r.r1 = Option(q.poll()).getOrElse(0)
      r.r2 = Option(q.poll()).getOrElse(0)
    }
  }
}
