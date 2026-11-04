package zio.internal

import zio._
import zio.test._
import zio.test.Assertion._

import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

object FiberMailboxConcurrencySpec extends ZIOBaseSpec {

  private def message(n: Int): FiberMessage =
    FiberMessage.Resume(ZIO.succeed(n))

  def spec = suite("FiberMailboxConcurrencySpec")(
    test("does not lose or duplicate messages from multiple writers") {
      val mailbox       = new FiberMailbox()
      val writers       = 4
      val perWriter     = 1000
      val totalMessages = writers * perWriter
      val messages      = Array.tabulate(totalMessages)(message)
      val start         = new CountDownLatch(1)
      val done          = new CountDownLatch(writers)
      val failure       = new AtomicReference[Throwable]()

      val threads =
        (0 until writers).map { writer =>
          new Thread(
            () =>
              try {
                start.await()
                var i   = writer * perWriter
                val end = i + perWriter
                while (i < end) {
                  mailbox.add(messages(i))
                  i += 1
                }
              } catch {
                case t: Throwable =>
                  failure.compareAndSet(null, t)
                  ()
              } finally done.countDown(),
            s"fiber-mailbox-writer-$writer"
          )
        }

      threads.foreach(_.start())
      start.countDown()
      done.await()

      val seen = new IdentityHashMap[FiberMessage, java.lang.Boolean]()
      var next = mailbox.poll()
      while (next ne null) {
        seen.put(next, java.lang.Boolean.TRUE)
        next = mailbox.poll()
      }

      assert(failure.get())(isNull) &&
      assertTrue(seen.size() == totalMessages, messages.forall(seen.containsKey), mailbox.isEmpty)
    },
    test("single reader can poll while writers are adding") {
      val mailbox       = new FiberMailbox()
      val writers       = 4
      val perWriter     = 1000
      val totalMessages = writers * perWriter
      val messages      = Array.tabulate(totalMessages)(message)
      val start         = new CountDownLatch(1)
      val done          = new CountDownLatch(writers)
      val readerDone    = new CountDownLatch(1)
      val failure       = new AtomicReference[Throwable]()
      val seen          = new IdentityHashMap[FiberMessage, java.lang.Boolean]()

      val reader = new Thread(
        () =>
          try {
            start.await()
            while (done.getCount() > 0 || !mailbox.isEmpty) {
              val next = mailbox.poll()
              if (next ne null) {
                if (seen.put(next, java.lang.Boolean.TRUE) ne null)
                  throw new AssertionError("duplicate mailbox message")
              } else Thread.`yield`()
            }
          } catch {
            case t: Throwable =>
              failure.compareAndSet(null, t)
              ()
          } finally readerDone.countDown(),
        "fiber-mailbox-reader"
      )

      val threads =
        (0 until writers).map { writer =>
          new Thread(
            () =>
              try {
                start.await()
                var i   = writer * perWriter
                val end = i + perWriter
                while (i < end) {
                  mailbox.add(messages(i))
                  i += 1
                }
              } catch {
                case t: Throwable =>
                  failure.compareAndSet(null, t)
                  ()
              } finally done.countDown(),
            s"fiber-mailbox-writer-$writer"
          )
        }

      reader.start()
      threads.foreach(_.start())
      start.countDown()
      done.await()
      readerDone.await()

      assert(failure.get())(isNull) &&
      assertTrue(seen.size() == totalMessages, messages.forall(seen.containsKey), mailbox.isEmpty)
    }
  ) @@ TestAspect.nonFlaky
}
