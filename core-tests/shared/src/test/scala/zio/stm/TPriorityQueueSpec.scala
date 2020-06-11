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
    }
  )
}
