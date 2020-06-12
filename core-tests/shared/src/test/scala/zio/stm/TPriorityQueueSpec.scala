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
      checkM(Gen.listOf(Gen.anyInt), Gen.function(Gen.boolean)) { (as, f) =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          _     <- queue.removeIf(f)
          list  <- queue.toList
        } yield list
        assertM(transaction.commit)(equalTo(as.filterNot(f).sorted))
      }
    },
    testM("retainIf") {
      checkM(Gen.listOf(Gen.anyInt), Gen.function(Gen.boolean)) { (as, f) =>
        val transaction = for {
          queue <- TPriorityQueue.fromIterable(as)
          _     <- queue.retainIf(f)
          list  <- queue.toList
        } yield list
        assertM(transaction.commit)(equalTo(as.filter(f).sorted))
      }
    }
  )
}
