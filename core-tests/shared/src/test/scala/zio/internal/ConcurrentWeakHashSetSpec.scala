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
    test("Add should insert elements") {
      val set  = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val refs = List(Wrapper(1), Wrapper(2), Wrapper(3))
      set.add(refs.head)
      set.addOne(refs(1))
      set.addAll(List(refs(2)))
      assert(set.size())(equalTo(3))
    },
    test("Set resolves duplicated values") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val ref = Wrapper(1)
      set.add(ref)
      set.add(ref)
      assert(set.size())(equalTo(1))
    },
    test("Adding an element to set makes it non-empty") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val ref = Wrapper(Int.MaxValue)
      set.add(ref)
      assertTrue(!set.isEmpty)
    },
    test("Remove should delete reference from set") {
      val set  = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val refs = List(Wrapper(1), Wrapper(2))
      set.addAll(refs)
      set.remove(Wrapper(1))
      set.subtractOne(Wrapper(2))
      assert(set.size())(equalTo(0))
    },
    test("Removing non-existent element should be allowed") {
      val set    = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val result = set.remove(Wrapper(1))
      assertTrue(!result)
    },
    test("Contains should return true if set stores reference to the element") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val ref = Wrapper(1)
      set.add(ref)
      assertTrue(set.contains(ref))
    },
    test("Clearing the set makes it empty") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val ref = Wrapper(1)
      set.add(ref)
      set.clear()
      assertTrue(set.isEmpty)
    },
    test("Can iterate over elements") {
      val set  = new ConcurrentWeakHashSet[Wrapper[Int]]()
      val refs = List(Wrapper(1), Wrapper(2))
      set.addAll(refs)
      val allValues = set.iterator.toList.sortBy(_.value)
      assert(allValues)(equalTo(refs))
    },
    test("Dead references are removed from set") {
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
