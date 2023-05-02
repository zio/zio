package zio.internal

import zio.test._
import zio.ZIOBaseSpec
import zio.test.Assertion.equalTo
import zio.test.TestAspect.flaky

object ConcurrentWeakHashSetSpec extends ZIOBaseSpec {

  private final case class Wrapper[A](value: A)

  def spec = suite("ConcurrentWeakHashSetSpec")(
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
    test("Empty set is empty") {
      assertTrue(new ConcurrentWeakHashSet[Wrapper[Int]]().isEmpty)
    },
    test("Adding an element makes it non-empty") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      set.add(Wrapper(42))
      assertTrue(!set.isEmpty)
    },
    test("Can GC dead refs") {
      val set = new ConcurrentWeakHashSet[Wrapper[Int]]()
      set.add(Wrapper(1))
      System.gc()
      set.gc()
      assert(set.size())(equalTo(0))
    } @@ flaky
    // test("Size tracking") {
    //   checkAll(gen)(list => assert(ConcurrentWeakHashSet.fromIterable(list).size)(equalTo(list.length)))
    // }
  )

  sealed trait Boolean {
    def unary_! : Boolean = this match {
      case True  => False
      case False => True
    }
  }
  case object True  extends Boolean
  case object False extends Boolean

  val gen: Gen[Any, List[Boolean]] =
    Gen.large(n => Gen.listOfN(n)(Gen.elements(True, False)))
}
