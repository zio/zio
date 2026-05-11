package zio.internal

import zio.ZIOBaseSpec
import zio.test._

object FiberMailboxSpec extends ZIOBaseSpec {

  def spec = suite("FiberMailboxSpec")(
    test("poll returns null for an empty mailbox") {
      val mailbox = new FiberMailbox[String]

      assertTrue(mailbox.poll() == null, mailbox.isEmpty())
    },
    test("polls messages in FIFO order from a single producer") {
      val mailbox = new FiberMailbox[String]

      mailbox.offer("a")
      mailbox.offer("b")
      mailbox.offer("c")

      assertTrue(
        !mailbox.isEmpty(),
        mailbox.poll() == "a",
        mailbox.poll() == "b",
        mailbox.poll() == "c",
        mailbox.poll() == null,
        mailbox.isEmpty()
      )
    },
    test("supports repeated drain and refill cycles") {
      val mailbox = new FiberMailbox[String]

      var round = 0
      var ok    = true

      while (round < 1000) {
        mailbox.offer(s"$round-0")
        mailbox.offer(s"$round-1")
        mailbox.offer(s"$round-2")

        ok = ok &&
          mailbox.poll() == s"$round-0" &&
          mailbox.poll() == s"$round-1" &&
          mailbox.poll() == s"$round-2" &&
          mailbox.poll() == null &&
          mailbox.isEmpty()

        round = round + 1
      }

      assertTrue(ok)
    }
  )
}
