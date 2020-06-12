package zio.stm

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

object TPriorityQueueSpec extends ZIOBaseSpec {

  def spec = suite("TPriorityQueueSpec")(
    testM("offer and take") {
      checkM(Gen.listOf(Gen.anyInt)) { vs =>
        val transaction = for {
          queue  <- TPriorityQueue.fromIterable(vs)
          values <- queue.takeAll
        } yield values
        assertM(transaction.commit)(equalTo(vs.sorted))
      }
    },
    testM("removeIf") {
      val elems = List(2, 4, 6, 3, 5, 6)
      val transaction = for {
        queue <- TPriorityQueue.fromIterable(elems)
        _     <- queue.removeIf(_ % 2 == 0)
        list  <- queue.toList
      } yield list
      assertM(transaction.commit)(equalTo(List(3, 5)))
    },
    testM("retainIf") {
      val elems = List(2, 4, 6, 3, 5, 6)
      val transaction = for {
        queue <- TPriorityQueue.fromIterable(elems)
        _     <- queue.retainIf(_ % 2 == 0)
        list  <- queue.toList
      } yield list
      assertM(transaction.commit)(equalTo(List(2, 4, 6, 6)))
    }
  )
}
