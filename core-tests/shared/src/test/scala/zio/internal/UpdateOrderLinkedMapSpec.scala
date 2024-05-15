package zio.internal

import zio._
import zio.test.TestAspect._
import zio.test._

import scala.annotation.nowarn

object UpdateOrderLinkedMapSpec extends ZIOBaseSpec {
  private val empty = UpdateOrderLinkedMap.empty[String, Int]

  def spec = suite("UpdateOrderLinkedMap")(
    test("updating with new elements") {
      val m = empty.updated("a", 1).updated("b", 2).updated("c", 3)
      assertTrue(m.iterator.toList == List("a" -> 1, "b" -> 2, "c" -> 3))
    },
    test("updating existing elements") {
      val m = empty.updated("a", 1).updated("b", 2).updated("c", 3).updated("b", 4)
      assertTrue(m.iterator.toList == List("a" -> 1, "c" -> 3, "b" -> 4))
    },
    test("reverseIterator") {
      val m = empty.updated("a", 1).updated("b", 2).updated("c", 3).updated("b", 4).updated("d", 5)
      assertTrue(m.reverseIterator.toList == List("d" -> 5, "b" -> 4, "c" -> 3, "a" -> 1))
    },
    test("builder") {
      val m =
        UpdateOrderLinkedMap
          .newBuilder[String, Int]
          .addOne("a" -> 1)
          .addOne("b" -> 2)
          .addOne("c" -> 3)
          .addOne("b" -> 4)
          .addOne("d" -> 5)
          .result()

      assertTrue(m.reverseIterator.toList == List("d" -> 5, "b" -> 4, "c" -> 3, "a" -> 1))

    },
    test("iterator is stack-safe") {
      (1 to 100000)
        .foldLeft(empty) { case (m, i) =>
          m.updated(i.toString, i)
        }
        .iterator
        .foreach(_ => ())
      assertCompletes
    },
    test("reverseIterator is stack-safe") {
      (1 to 100000)
        .foldLeft(empty) { case (m, i) =>
          m.updated(i.toString, i)
        }
        .reverseIterator
        .foreach(_ => ())
      assertCompletes
    }
  )
}
