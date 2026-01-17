package zio.internal

import zio.test._

object FiberSetSpec extends ZIOSpecDefault {

  def spec = suite("FiberSet")(
    test("add and iterate a single fiber") {
      val set   = FiberSet()
      val fiber = new TestFiber()

      set.add(fiber)

      val collected = scala.collection.mutable.ListBuffer.empty[TestFiber]
      set.foreach(f => collected += f.asInstanceOf[TestFiber])

      assertTrue(collected.contains(fiber))
    },
    test("add multiple fibers") {
      val set    = FiberSet()
      val fibers = (1 to 10).map(_ => new TestFiber())

      fibers.foreach(set.add)

      val collected = scala.collection.mutable.ListBuffer.empty[TestFiber]
      set.foreach(f => collected += f.asInstanceOf[TestFiber])

      assertTrue(collected.size == 10 && fibers.forall(collected.contains))
    },
    test("remove fiber") {
      val set   = FiberSet()
      val fiber = new TestFiber()

      set.add(fiber)
      set.remove(fiber)

      val collected = scala.collection.mutable.ListBuffer.empty[TestFiber]
      set.foreach(f => collected += f.asInstanceOf[TestFiber])

      assertTrue(collected.isEmpty)
    },
    test("handle terminated fibers") {
      val set       = FiberSet()
      val liveFiber = new TestFiber()
      val deadFiber = new TestFiber()
      deadFiber.terminated = true

      set.add(liveFiber)
      set.add(deadFiber)

      val collected = scala.collection.mutable.ListBuffer.empty[TestFiber]
      set.foreach(f => collected += f.asInstanceOf[TestFiber])

      // FiberSet doesn't filter terminated fibers - that's done at FiberRuntime level
      assertTrue(collected.contains(liveFiber) && collected.contains(deadFiber))
    },
    test("stress test with many fibers") {
      val set    = FiberSet()
      val fibers = (1 to 1000).map(_ => new TestFiber())

      fibers.foreach(set.add)

      val collected = scala.collection.mutable.ListBuffer.empty[TestFiber]
      set.foreach(f => collected += f.asInstanceOf[TestFiber])

      assertTrue(collected.size == 1000)
    }
  )

  class TestFiber(var terminated: Boolean = false) extends FiberSetRef {
    @volatile var _setEpochId: Long = -1L
    @volatile var _setIndex: Int    = -1

    def isTerminated: Boolean = terminated
  }
}
