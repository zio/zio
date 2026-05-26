package zio.internal

import zio._
import zio.test._

object FiberMailboxSpec extends ZIOBaseSpec {

  private def message(n: Int): FiberMessage =
    FiberMessage.Resume(ZIO.succeed(n))

  def spec = suite("FiberMailboxSpec")(
    test("starts empty") {
      val mailbox = new FiberMailbox()
      val polled  = mailbox.poll()

      assertTrue(mailbox.isEmpty, polled == null)
    },
    test("uses the single message fast path") {
      val mailbox = new FiberMailbox()
      val first   = message(1)

      mailbox.add(first)

      val nonEmptyBeforePoll = !mailbox.isEmpty
      val firstPolled        = mailbox.poll()
      val nextPolled         = mailbox.poll()

      assertTrue(nonEmptyBeforePoll, firstPolled eq first, mailbox.isEmpty, nextPolled == null)
    },
    test("promotes to FIFO queue when multiple messages are waiting") {
      val mailbox = new FiberMailbox()
      val first   = message(1)
      val second  = message(2)
      val third   = message(3)

      mailbox.add(first)
      mailbox.add(second)
      mailbox.add(third)

      val firstPolled  = mailbox.poll()
      val secondPolled = mailbox.poll()
      val thirdPolled  = mailbox.poll()
      val nextPolled   = mailbox.poll()

      assertTrue(
        firstPolled eq first,
        secondPolled eq second,
        thirdPolled eq third,
        nextPolled == null,
        mailbox.isEmpty
      )
    },
    test("reuses the promoted queue after it has drained") {
      val mailbox = new FiberMailbox()
      val first   = message(1)
      val second  = message(2)
      val third   = message(3)

      mailbox.add(first)
      mailbox.add(second)
      val firstPolled   = mailbox.poll()
      val secondPolled  = mailbox.poll()
      val emptyAfterTwo = mailbox.isEmpty

      mailbox.add(third)
      val thirdPolled     = mailbox.poll()
      val emptyAfterThree = mailbox.isEmpty

      assertTrue(firstPolled eq first, secondPolled eq second, emptyAfterTwo, thirdPolled eq third, emptyAfterThree)
    }
  )
}
