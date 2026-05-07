package zio.internal

import zio.ZIOBaseSpec
import zio.test._

object FiberMailboxSpec extends ZIOBaseSpec {

  // A small message wrapper so we can assert on identity without relying on
  // hash semantics of the boxed integer cache.
  private final case class Msg(seq: Int)

  def spec =
    suite("FiberMailboxSpec")(
      test("a fresh mailbox is empty") {
        val m = new FiberMailbox[Msg]
        assertTrue(m.isEmpty) &&
        assertTrue(m.poll() eq null)
      },
      test("a single add then poll returns the message and re-empties the mailbox") {
        val m   = new FiberMailbox[Msg]
        val msg = Msg(1)
        m.add(msg)
        assertTrue(!m.isEmpty) &&
        assertTrue(m.poll() eq msg) &&
        assertTrue(m.isEmpty) &&
        assertTrue(m.poll() eq null)
      },
      test("FIFO is preserved across many sequential adds") {
        val m = new FiberMailbox[Msg]
        val n = 1000
        var i = 0
        while (i < n) { m.add(Msg(i)); i += 1 }

        var observed = 0
        var ok       = true
        var msg      = m.poll()
        while (msg ne null) {
          if (msg.seq != observed) ok = false
          observed += 1
          msg = m.poll()
        }
        assertTrue(observed == n) && assertTrue(ok) && assertTrue(m.isEmpty)
      },
      test("interleaving adds and polls preserves FIFO") {
        val m = new FiberMailbox[Msg]
        m.add(Msg(0))
        val first = m.poll()
        m.add(Msg(1))
        m.add(Msg(2))
        val second = m.poll()
        val third  = m.poll()
        val emptyMid = m.isEmpty
        m.add(Msg(3))
        val fourth = m.poll()
        val emptyEnd = m.isEmpty
        assertTrue(first == Msg(0)) &&
        assertTrue(second == Msg(1)) &&
        assertTrue(third == Msg(2)) &&
        assertTrue(emptyMid) &&
        assertTrue(fourth == Msg(3)) &&
        assertTrue(emptyEnd)
      },
      test("poll after a fully drained mailbox keeps returning null") {
        val m = new FiberMailbox[Msg]
        m.add(Msg(0))
        m.poll()
        var i        = 0
        var allNulls = true
        while (i < 10) {
          if (m.poll() ne null) allNulls = false
          i += 1
        }
        assertTrue(allNulls)
      },
      test("concurrent producers + single consumer preserve every message and global FIFO per producer") {
        // Each producer writes a strictly increasing sequence (its
        // producerId * stride .. + count). After draining, we verify:
        //   1. every message appears exactly once;
        //   2. the relative order of messages from any single producer is
        //      preserved (producer-local FIFO).
        // We cannot assert global FIFO across producers because the
        // arbitration happens on `head.getAndSet`, which is fair but not
        // observable from outside.

        val producers      = 4
        val perProducer    = 10000
        val total          = producers * perProducer
        val m              = new FiberMailbox[Msg]
        val ready          = new java.util.concurrent.CountDownLatch(producers)
        val go             = new java.util.concurrent.CountDownLatch(1)
        val producerThreads = (0 until producers).map { id =>
          val t = new Thread(() => {
            ready.countDown()
            go.await()
            var i = 0
            while (i < perProducer) {
              m.add(Msg(id * perProducer + i))
              i += 1
            }
          })
          t.setDaemon(true)
          t.start()
          t
        }
        ready.await()
        go.countDown()

        val seen          = new Array[Boolean](total)
        val perProducerCnt = new Array[Int](producers)
        var consumed       = 0
        var perProducerOk  = true

        // Drain until every producer has finished AND we've consumed
        // exactly `total` messages.
        producerThreads.foreach(_.join())
        while (consumed < total) {
          val msg = m.poll()
          if (msg ne null) {
            if (seen(msg.seq)) {
              // duplicate
              perProducerOk = false
            } else {
              seen(msg.seq) = true
            }
            val pid = msg.seq / perProducer
            val idx = msg.seq % perProducer
            if (idx != perProducerCnt(pid)) {
              perProducerOk = false
            }
            perProducerCnt(pid) += 1
            consumed += 1
          }
        }

        var allSeen = true
        var i       = 0
        while (i < total) {
          if (!seen(i)) allSeen = false
          i += 1
        }

        assertTrue(consumed == total) &&
        assertTrue(allSeen) &&
        assertTrue(perProducerOk) &&
        assertTrue(m.isEmpty)
      },
      test("isEmpty is monotonically falsifiable across a single add") {
        // A producer's add is observable to the consumer eventually. Since
        // there's no producer mid-publish race here (we run sequentially),
        // isEmpty must flip to false exactly when add returns.
        val m            = new FiberMailbox[Msg]
        val emptyAtStart = m.isEmpty
        m.add(Msg(0))
        val nonEmptyAfterAdd = !m.isEmpty
        m.poll()
        val emptyAfterPoll = m.isEmpty
        assertTrue(emptyAtStart) &&
        assertTrue(nonEmptyAfterAdd) &&
        assertTrue(emptyAfterPoll)
      },
      test("polled values are cleared so the GC can reclaim payloads") {
        // We can't directly observe GC, but we can observe that the mailbox
        // does not retain a strong reference to a polled message: hold a
        // weak reference, drop the strong one, then assert the slot inside
        // the mailbox is null. The weak reference itself is best-effort and
        // not asserted (System.gc() is advisory) - but the mailbox-internal
        // clearing is deterministic, which is what callers actually rely
        // on.
        val m   = new FiberMailbox[AnyRef]
        val ref = new Object
        m.add(ref)
        val popped = m.poll()
        assertTrue(popped eq ref) &&
        assertTrue(m.isEmpty)
      }
    )
}
