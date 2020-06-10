package zio.stm

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

object TPriorityQueueSpec extends ZIOBaseSpec {

  def spec = suite("TPriorityQueueSpec")(
    testM("offer and take") {
      checkM(Gen.listOf(Gen.anyInt)) { vs =>
        val transaction = for {
          queue  <- TPriorityQueue.fromIterable(vs.zip(vs))
          values <- queue.takeAll
        } yield values
        assertM(transaction.commit)(equalTo(vs.sorted))
      }
    },
    testM("removeIf") {
      val elems = List(1 -> "a", 1 -> "b", 1 -> "c", 2 -> "d", 3 -> "e")
      val transaction = for {
        queue <- TPriorityQueue.fromIterable(elems)
        _     <- queue.removeIf((k, v) => k > 2 || v == "b")
        list  <- queue.toList
      } yield list
      assertM(transaction.commit)(equalTo(List("a", "c", "d")) || equalTo(List("c", "a", "d")))
    },
    testM("retainIf") {
      val elems = List(1 -> "a", 1 -> "b", 1 -> "c", 2 -> "d", 3 -> "e")
      val transaction = for {
        queue <- TPriorityQueue.fromIterable(elems)
        _     <- queue.retainIf((k, v) => k > 2 || v == "b")
        list  <- queue.toList
      } yield list
      assertM(transaction.commit)(equalTo(List("b", "e")))
    }
  )
}
