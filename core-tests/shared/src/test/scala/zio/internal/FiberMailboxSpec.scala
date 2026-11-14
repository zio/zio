package zio.internal

import zio.test._
import zio.{Cause, ZIO}

object FiberMailboxSpec extends zio.ZIOBaseSpec {

  private val resume = FiberMessage.resumeUnit

  private val interrupt =
    FiberMessage.InterruptSignal(Cause.empty)

  private val stateful =
    FiberMessage.Stateful(_ => ())

  def spec = suite("FiberMailboxSpec")(
    test("empty mailbox polls null and is empty") {
      val mailbox = new FiberMailbox
      assertTrue(
        mailbox.poll() == null,
        mailbox.pollRelaxed() == null,
        mailbox.isEmpty()
      )
    },
    test("a single offer is taken from the fast path and the slot can be reused") {
      val mailbox = new FiberMailbox
      mailbox.add(resume)
      val notEmpty = !mailbox.isEmpty()
      val first    = mailbox.pollRelaxed()
      val empty1   = mailbox.isEmpty()
      mailbox.add(interrupt)
      val second = mailbox.poll()
      val third  = mailbox.poll()
      assertTrue(notEmpty, first eq resume, empty1, second eq interrupt, third == null, mailbox.isEmpty())
    },
    test("promotion keeps FIFO order, including a reused message instance") {
      val mailbox = new FiberMailbox
      mailbox.add(resume)
      mailbox.add(interrupt)
      mailbox.add(stateful)
      mailbox.add(resume)
      assertTrue(
        (mailbox.poll() eq resume),
        (mailbox.poll() eq interrupt),
        (mailbox.poll() eq stateful),
        (mailbox.poll() eq resume),
        mailbox.poll() == null,
        mailbox.isEmpty()
      )
    },
    test("relaxed poll observes offers that have already returned") {
      val mailbox = new FiberMailbox
      mailbox.add(resume)
      mailbox.add(interrupt)
      val first  = mailbox.pollRelaxed()
      val second = mailbox.pollRelaxed()
      assertTrue((first eq resume), (second eq interrupt), mailbox.pollRelaxed() == null)
    },
    test("alternating offer and poll does not drop or duplicate") {
      val mailbox = new FiberMailbox
      val n       = 10000
      var i       = 0
      var ok      = true
      while (i < n && ok) {
        val message = if ((i & 1) == 0) resume else interrupt
        mailbox.add(message)
        ok = (mailbox.poll() eq message) && mailbox.isEmpty()
        i += 1
      }
      assertTrue(ok, i == n)
    },
    test("a long single-consumer sequence stays ordered across promotion") {
      val mailbox = new FiberMailbox
      val n       = 1000
      var i       = 0
      while (i < n) {
        mailbox.add(if ((i & 1) == 0) resume else interrupt)
        i += 1
      }
      i = 0
      var ok = true
      while (i < n && ok) {
        val expected = if ((i & 1) == 0) resume else interrupt
        ok = mailbox.poll() eq expected
        i += 1
      }
      assertTrue(ok, i == n, mailbox.isEmpty())
    },
    test("fiber run loop still delivers interrupts and joins") {
      for {
        fiber <- ZIO.never.fork
        _     <- fiber.interrupt
        exit  <- fiber.await
      } yield assertTrue(exit.isInterrupted)
    }
  )
}
