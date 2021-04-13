package zio.internal

import zio._
import zio.test.Assertion._
import zio.test._

object SingleThreadedRingBufferSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] =
    suite("SingleThreadedRingBufferSpec")(
      testM("use SingleThreadedRingBuffer as a sliding buffer") {
        check(Gen.chunkOf(Gen.anyInt), Gen.size) { (as, n) =>
          val queue = SingleThreadedRingBuffer[Int](n)
          as.foreach(queue.put)
          val actual   = queue.toChunk
          val expected = as.takeRight(n)
          assert(actual)(equalTo(expected))
        }
      }
    )
}
