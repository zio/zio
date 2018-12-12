package scalaz.zio.internal

import org.specs2.Specification
import scalaz.zio.SerializableSpec._

/*
 * This spec is just a sanity check and tests RingBuffer correctness
 * in a single-threaded case.
 *
 * Concurrent tests are run via jcstress and are in [[RingBufferConcurrencyTests]].
 */
class RingBufferSpec extends Specification {
  def is =
    "RingBufferSpec".title ^ s2"""
    Make a bounded MutableConcurrentQueue of size 3
     returns a queue of size 3. $e1
     `offer` of 2 items succeeds, further offers fail. $e2
     `poll` of 2 items from full queue succeeds, further `poll`s return default value. $e3
     can be serialized. $e4
    """

  def e1 = {
    val q = MutableConcurrentQueue.bounded(3)
    q.capacity must_=== 3
  }

  def e2 = {
    val q = MutableConcurrentQueue.bounded[Int](2)
    (q.offer(1) must beTrue)
      .and(q.size must_=== 1)
      .and(q.offer(2) must beTrue)
      .and(q.size must_=== 2)
      .and(q.offer(3) must beFalse)
      .and(q.isFull must beTrue)
  }

  def e3 = {
    val q = MutableConcurrentQueue.bounded[Int](2)
    q.offer(1)
    q.offer(2)
    (q.poll(-1) must_=== 1)
      .and(q.poll(-1) must_=== 2)
      .and(q.poll(-1) must_=== -1)
      .and(q.isEmpty must beTrue)
  }

  def e4 = {
    val q = MutableConcurrentQueue.bounded[Int](2)
    q.offer(1)
    val returnQ = serializeAndDeserialize(q)
    returnQ.offer(2)
    (returnQ.poll(-1) must_=== 1)
      .and(returnQ.poll(-1) must_=== 2)
      .and(returnQ.poll(-1) must_=== -1)
  }
}
