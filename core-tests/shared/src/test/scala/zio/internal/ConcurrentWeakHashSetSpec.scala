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
    test("Can GC dead refs") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      set.add(Wrapper(1))
      System.gc()
      set.gc()
      assert(set.size())(equalTo(0))
    } @@ flaky
  )

}
