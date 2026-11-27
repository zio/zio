package zio.internal

import zio.test._

import java.util.concurrent.atomic.AtomicInteger

/**
 * Many producers, one consumer. Per-producer order has to hold, and the same
 * message instance has to be deliverable many times (the runtime reuses
 * `FiberMessage.resumeUnit`).
 */
object FiberMailboxConcurrencySpec extends zio.ZIOBaseSpec {

  def spec = suite("FiberMailboxConcurrencySpec")(
    test("producers do not lose, duplicate, or reorder their own messages") {
      assertTrue(stress(producers = 8, perProducer = 20000))
    },
    test("one shared message instance is delivered once per offer") {
      val producers = 4
      val each      = 50000
      val total     = producers * each
      val mailbox   = new FiberMailbox
      val shared    = FiberMessage.resumeUnit
      val received  = new AtomicInteger
      val consumer = new Thread(() => {
        var got = 0
        while (got < total) {
          if (mailbox.poll() ne null) got += 1
          else Thread.onSpinWait()
        }
        received.set(got)
      })
      consumer.start()
      val threads = Array.fill(producers)(new Thread(() => {
        var i = 0
        while (i < each) {
          mailbox.add(shared)
          i += 1
        }
      }))
      threads.foreach(_.start())
      threads.foreach(_.join())
      consumer.join(20000)
      assertTrue(!consumer.isAlive, received.get() == total, mailbox.isEmpty())
    }
  )

  /**
   * Encodes `(producer, index)` in a fresh Resume so duplicates and gaps are
   * visible. Returns true when every producer observes `0 until perProducer`
   * exactly once and in order.
   */
  private def stress(producers: Int, perProducer: Int): Boolean = {
    val mailbox  = new FiberMailbox
    val next     = new Array[Int](producers)
    val received = new AtomicInteger
    val failed   = new AtomicInteger
    val total    = producers * perProducer
    val consumer = new Thread(() => {
      var got = 0
      while (got < total && failed.get() == 0) {
        val message = mailbox.poll()
        if (message eq null) Thread.onSpinWait()
        else {
          val encoded =
            message.asInstanceOf[FiberMessage.Resume].effect.asInstanceOf[zio.Exit.Success[Int]].value
          val producer = encoded >>> 24
          val index    = encoded & 0xffffff
          if (index != next(producer)) failed.incrementAndGet()
          else next(producer) = index + 1
          got += 1
        }
      }
      received.set(got)
    })
    consumer.start()
    val threads = Array.tabulate(producers) { producer =>
      new Thread(() => {
        var index = 0
        while (index < perProducer) {
          val encoded = (producer << 24) | index
          mailbox.add(FiberMessage.Resume(zio.Exit.succeed(encoded)))
          index += 1
        }
      })
    }
    threads.foreach(_.start())
    threads.foreach(_.join())
    consumer.join(20000)
    !consumer.isAlive && failed.get() == 0 && received.get() == total
  }
}
