package scalaz.zio.internal

import org.specs2.Specification

/*
 * This spec is just a sanity check and tests RingBuffer correctness
 * in a single-threaded case.
 */
class MutableConcurrentQueueSpec extends Specification {
  def is =
    "MutableConcurrentQueueSpec".title ^ s2"""
    Make a bounded MutableConcurrentQueue
     of capacity 1 returns a queue of capacity 1. $minSize
     of capacity 2 returns a queue of capacity 2. $exactSize1
     of capacity 3 returns a queue of capacity 3. $exactSize2

    With a RingBuffer of capacity 2
     `offer` of 2 items succeeds, further offers fail. $offerCheck
     `poll` of 2 items from full queue succeeds, further `poll`s return default value. $pollCheck
    """

  def minSize = {
    val q = MutableConcurrentQueue.bounded(1)
    q.capacity must_=== 1
  }

  def exactSize1 = {
    val q = MutableConcurrentQueue.bounded(2)
    q.capacity must_=== 2
  }

  def exactSize2 = {
    val q = MutableConcurrentQueue.bounded(3)
    q.capacity must_=== 3
  }

  def offerCheck = {
    val q = MutableConcurrentQueue.bounded[Int](2)
    (q.offer(1) must beTrue)
      .and(q.size must_=== 1)
      .and(q.offer(2) must beTrue)
      .and(q.size must_=== 2)
      .and(q.offer(3) must beFalse)
      .and(q.isFull must beTrue)
  }

  def pollCheck = {
    val q = MutableConcurrentQueue.bounded[Int](2)
    q.offer(1)
    q.offer(2)
    (q.poll(-1) must_=== 1)
      .and(q.poll(-1) must_=== 2)
      .and(q.poll(-1) must_=== -1)
      .and(q.isEmpty must beTrue)
  }
}
