package zio.internal

import zio.SerializableSpecHelpers._
import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

object MutableConcurrentQueueSpecJVM extends ZIOBaseSpec {

  def spec = suite("MutableConcurrentQueueSpec")(
    suite("Serialization works for")(
      test("a one element queue") {
        val q = MutableConcurrentQueue.bounded[Int](1)
        q.offer(1)
        val returnQ = serializeAndDeserialize(q)
        returnQ.offer(2)

        (assert(returnQ.poll(-1))(equalTo(1))
        && assert(returnQ.poll(-1))(equalTo(-1)))
      },
      test("a pow 2 capacity ring buffer") {
        val q = MutableConcurrentQueue.bounded[Int](3)
        q.offer(1)
        val returnQ = serializeAndDeserialize(q)
        returnQ.offer(2)

        (assert(returnQ.poll(-1))(equalTo(1))
        && assert(returnQ.poll(-1))(equalTo(2))
        && assert(returnQ.poll(-1))(equalTo(-1)))
      },
      test("an arbitrary capacity ring buffer") {
        val q = MutableConcurrentQueue.bounded[Int](2)
        q.offer(1)
        val returnQ = serializeAndDeserialize(q)
        returnQ.offer(2)

        (assert(returnQ.poll(-1))(equalTo(1))
        && assert(returnQ.poll(-1))(equalTo(2))
        && assert(returnQ.poll(-1))(equalTo(-1)))
      }
    )
  )
}
