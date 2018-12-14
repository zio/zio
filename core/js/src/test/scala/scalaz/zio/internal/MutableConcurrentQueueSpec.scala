package scalaz.zio.internal

/*
 * This spec is just a sanity check and tests RingBuffer correctness
 * in a single-threaded case.
 */
class MutableConcurrentQueueSpec extends Specification {
  def is =
    "MutableConcurrentQueueSpec".title ^ s2"""
    Make a bounded MutableConcurrentQueue
     of capacity 1 returns a queue of capacity 2. $minSize
     of capacity 3 returns a queue of capacity 3. $e1

    With a RingBuffer of capacity 2
     `offer` of 2 items succeeds, further offers fail. $e2
     `poll` of 2 items from full queue succeeds, further `poll`s return default value. $e3

    RingBuffer can be serialized. $e4
    """

  def minSize = {
    val q = MutableConcurrentQueue.bounded(1)
    q.capacity must_=== 2
  }

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
}
