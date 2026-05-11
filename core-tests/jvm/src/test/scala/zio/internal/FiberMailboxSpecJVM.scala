package zio.internal

import zio._
import zio.test.TestAspect.timeout
import zio.test._

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}

object FiberMailboxSpecJVM extends ZIOBaseSpec {

  def spec = suite("FiberMailboxSpecJVM")(
    test("preserves all messages and per-producer order with concurrent producers") {
      ZIO.attempt {
        val producers       = math.max(2, java.lang.Runtime.getRuntime.availableProcessors())
        val messagesPer     = 20000
        val expectedTotal   = producers * messagesPer
        val mailbox         = new FiberMailbox[Message]
        val executorService = Executors.newFixedThreadPool(producers)
        val start           = new CountDownLatch(1)
        val done            = new CountDownLatch(producers)
        val seen            = Array.fill(producers, messagesPer)(false)
        val lastSeen        = Array.fill(producers)(-1)

        (0 until producers).foreach { producerId =>
          executorService.submit(new Runnable {
            def run(): Unit =
              try {
                start.await()
                (0 until messagesPer).foreach { sequence =>
                  mailbox.offer(Message(producerId, sequence))
                }
              } finally {
                done.countDown()
              }
          })
        }

        start.countDown()

        var received        = 0
        var duplicate       = false
        var orderViolation  = false
        val deadlineNanos   = java.lang.System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        var keepConsuming   = true
        var producersDone   = false
        var completedInTime = false

        while (keepConsuming) {
          val message = mailbox.poll()

          if (message eq null) {
            if (received == expectedTotal) {
              completedInTime = true
              keepConsuming = false
            } else if (java.lang.System.nanoTime() > deadlineNanos) {
              keepConsuming = false
            } else {
              Thread.`yield`()
            }
          } else {
            if (seen(message.producer)(message.sequence)) duplicate = true
            seen(message.producer)(message.sequence) = true

            if (message.sequence <= lastSeen(message.producer)) orderViolation = true
            lastSeen(message.producer) = message.sequence

            received = received + 1
          }
        }

        producersDone = done.await(30, TimeUnit.SECONDS)
        executorService.shutdownNow()

        val allSeen =
          seen.forall(_.forall(identity)) &&
            lastSeen.forall(_ == messagesPer - 1)

        assertTrue(
          producersDone,
          completedInTime,
          received == expectedTotal,
          !duplicate,
          !orderViolation,
          allSeen,
          mailbox.isEmpty()
        )
      }
    } @@ timeout(60.seconds),
    test("does not retain the last consumed message") {
      ZIO.attempt {
        val mailbox = new FiberMailbox[RetentionToken]
        val queue   = new ReferenceQueue[RetentionToken]
        val ref     = offerAndPoll(mailbox, queue)

        assertTrue(awaitCollection(ref, queue), mailbox.isEmpty())
      }
    } @@ timeout(10.seconds)
  )

  private final case class Message(producer: Int, sequence: Int)

  private final class RetentionToken

  private def offerAndPoll(
    mailbox: FiberMailbox[RetentionToken],
    queue: ReferenceQueue[RetentionToken]
  ): WeakReference[RetentionToken] = {
    var token = new RetentionToken
    val ref   = new WeakReference(token, queue)

    mailbox.offer(token)
    if (mailbox.poll() ne token) throw new AssertionError("Mailbox did not return the offered token")
    token = null

    ref
  }

  private def awaitCollection(
    ref: WeakReference[RetentionToken],
    queue: ReferenceQueue[RetentionToken]
  ): Boolean = {
    var attempts = 0
    var ballast  = Chunk.empty[Array[Byte]]

    while ((queue.poll() eq null) && attempts < 50) {
      java.lang.System.gc()
      ballast = ballast :+ new Array[Byte](1024 * 1024)
      Thread.sleep(10)
      attempts = attempts + 1
    }

    ballast = Chunk.empty

    ref.get() eq null
  }
}
