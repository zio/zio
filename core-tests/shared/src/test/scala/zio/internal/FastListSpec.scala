package zio.internal

import zio.test._
import zio.ZIOBaseSpec

object FastListSpec extends ZIOBaseSpec {
  import zio.internal.FastList._

  def spec =
    suite("FastListSpec") {
      test("Nil is empty") {
        assertTrue(List.Nil.isEmpty)
      } +
        test("Singleton is not empty") {
          assertTrue(List(42).nonEmpty)
        } +
        test("Head of singleton is element") {
          assertTrue(List(42).head == 42)
        } +
        test("Tail of singleton is Nil") {
          assertTrue(List(42).tail == List.Nil)
        } +
        test("dropWhile") {
          assertTrue(List(1, 2, 3, 4, 5).dropWhile(_ < 3) == List(3, 4, 5))
        } +
        test("filter") {
          assertTrue(List(1, 2, 3, 4, 5).filter(_ % 2 == 0) == List(2, 4))
        } +
        test("foldLeft") {
          assertTrue(List(1, 2, 3, 4, 5).foldLeft(0)(_ + _) == 15)
        } +
        test("split head/tail") {
          val list = (0 to 1000).toList

          val fastList = List(list: _*)

          var building = Seq[Int]().toList
          var current  = fastList

          while (current.nonEmpty) {
            val head = current.head

            current = current.tail

            building = head :: building
          }

          assertTrue(list == building.reverse)
        } +
        test("forall positive") {
          assertTrue(List(1, 2, 3, 4, 5).forall(_ <= 5) == true)
        } +
        test("forall negative") {
          assertTrue(List(1, 2, 3, 4, 5).forall(_ < 5) == false)
        } +
        test("exists positive") {
          assertTrue(List(1, 2, 3, 4, 5).exists(_ > 4) == true)
        } +
        test("exists negative") {
          assertTrue(List(1, 2, 3, 4, 5).exists(_ > 5) == false)
        }
    }
}
