package zio.internal

import zio.ZIOBaseSpec
import zio.test._

object PartitionedLinkedQueueSpec extends ZIOBaseSpec {

  def spec = suite("PartitionedLinkedQueueSpec")(
    test("partitions round to nearest power of 2") {
      val q = new PartitionedLinkedQueue[String](9, addMetrics = false)

      assertTrue(q.nPartitions() == 16)
    },
    test("addMetrics = true") {
      val q = new PartitionedLinkedQueue[String](9, addMetrics = true)

      q.offer("1")
      q.offerAll(List("2", "3", "4"))
      q.poll(null)
      q.pollUpTo(2)
      val enq  = q.enqueuedCount()
      val deq1 = q.dequeuedCount()
      q.pollUpTo(10)
      val deq2 = q.dequeuedCount()

      assertTrue(enq == 4, deq1 == 3, deq2 == 4)
    },
    test("addMetrics = false") {
      val q = new PartitionedLinkedQueue[String](9, addMetrics = false)

      q.offer("1")
      q.poll(null)

      assertTrue(q.enqueuedCount() == 0, q.dequeuedCount() == 0)
    },
    test("addMetrics = false") {
      val q = new PartitionedLinkedQueue[String](9, addMetrics = false)

      q.offer("1")
      q.poll(null)

      assertTrue(q.enqueuedCount() == 0, q.dequeuedCount() == 0)
    },
    test("offerAll and pollUpTo items") {
      val q = new PartitionedLinkedQueue[String](9, addMetrics = false)

      val oneToHundred = (1 to 100).map(_.toString)
      q.offerAll(oneToHundred)
      val polled = q.pollUpTo(100)

      assertTrue(polled.toSet == oneToHundred.toSet)
    } @@ TestAspect.nonFlaky,
    test("offer and poll items") {
      val q = new PartitionedLinkedQueue[String](9, addMetrics = false)

      val oneToHundred = (1 to 100).map(_.toString)
      oneToHundred.foreach(q.offer)
      val polled = (1 to 100).map(_ => q.poll(null)).toSet

      assertTrue(polled == oneToHundred.toSet)
    } @@ TestAspect.nonFlaky,
    test("queue size") {
      val q = new PartitionedLinkedQueue[String](9, addMetrics = false)

      q.offerAll((1 to 3).map(_.toString))
      assertTrue(q.size() == 3)
    }
  )
}
