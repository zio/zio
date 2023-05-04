package zio.internal

import zio.test._
import zio.ZIOBaseSpec
import zio.test.Assertion.equalTo
import zio.test.TestAspect.flaky

object ConcurrentWeakHashSetSpec extends ZIOBaseSpec {

  private final case class Wrapper[A](value: A)

  def spec = suite("ConcurrentWeakHashSetSpec")(
    test("Empty set is empty") {
      assertTrue(new ConcurrentWeakHashSet[Wrapper[Int]]().isEmpty)
    },
    test("Can add elements") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      set.add(Wrapper(1))
      set.add(Wrapper(2))
      assert(set.size())(equalTo(2))
    },
    test("Resolves duplicates") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      set.add(Wrapper(1))
      set.add(Wrapper(1))
      assert(set.size())(equalTo(1))
    },
    test("Adding an element makes it non-empty") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      set.add(Wrapper(42))
      assertTrue(!set.isEmpty)
    },
    test("Can remove elements") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      set.add(Wrapper(1))
      set.remove(Wrapper(1))
      assert(set.size())(equalTo(0))
      assertTrue(set.isEmpty)
    },
    test("Can remove non-existent elements") {
      val set    = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val result = set.remove(Wrapper(1))
      assertTrue(!result)
      assert(set.size())(equalTo(0))
      assertTrue(set.isEmpty)
    },
    test("Clearing the set makes it empty") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val ref1 = Wrapper(1)
      set.add(ref1)
      set.clear()
      assertTrue(set.isEmpty)
    },
    test("Can iterate over elements") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()

      val ref1 = Wrapper(1)
      set.add(ref1)

      val ref2 = Wrapper(2)
      set.add(ref2)

      val iterator = set.iterator

      assertTrue(iterator.hasNext)
      val first = iterator.next()
      assertTrue(first.value == ref1.value || first.value == ref2.value)

      assertTrue(iterator.hasNext)
      val second = iterator.next()
      assertTrue(second.value == ref1.value || second.value == ref2.value)

      assertTrue(!iterator.hasNext)
    },
    test("Can GC dead refs") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      set.add(Wrapper(1)) // dead ref

      val ref = Wrapper(2)
      set.add(ref) // ref from scope

      System.gc()
      set.gc()

      assert(set.size())(equalTo(1))
      assert(set.iterator.next())(equalTo(ref))
    } @@ flaky
  )

}
