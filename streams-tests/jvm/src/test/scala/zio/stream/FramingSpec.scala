package zio.stream

import scala.{ Stream => _ }

import zio.test.Assertion.{ anything, equalTo, fails, isSubtype }
import zio.test._
import zio.{ Chunk, ZIOBaseSpec }

object FramingSpec extends ZIOBaseSpec {

  override def spec = suite("FramingSpec")(
    testM("happy path") {
      val sink = Framing.delimiter(Chunk.single(0), 1000)
      val in   = Stream(Chunk(1), Chunk(2, 0, 3), Chunk(4, 0, 5), Chunk(6))
      assertM(in.aggregate(sink).runCollect)(
        equalTo(List(Chunk(1, 2), Chunk(3, 4), Chunk(5, 6)))
      )
    },
    testM("split delimiter") {
      val sink = Framing.delimiter(Chunk(-1, -2, -3), 1000)
      val in   = Stream(Chunk(0, 1, -1, -2), Chunk(-3, 2, 3))
      assertM(in.aggregate(sink).runCollect)(
        equalTo(List(Chunk(0, 1), Chunk(2, 3)))
      )
    },
    testM("partial delimiter") {
      val sink = Framing.delimiter(Chunk(-1, -2), 1000)
      val in   = Stream(Chunk(0, 1, -1, 2), Chunk(3, -2, 4))
      assertM(in.aggregate(sink).runCollect)(
        equalTo(List(Chunk(0, 1, -1, 2, 3, -2, 4)))
      )
    },
    testM("deilimter last") {
      val sink = Framing.delimiter(Chunk(-1, -2), 1000)
      val in   = Stream(Chunk(1, 2), Chunk(3, -1, -2))
      assertM(in.aggregate(sink).runCollect)(
        equalTo(List(Chunk(1, 2, 3), Chunk.empty))
      )
    },
    testM("delimiter first") {
      val sink = Framing.delimiter(Chunk(-1, -2), 1000)
      val in   = Stream(Chunk(-1, -2, 1, 2), Chunk(3, 4))
      assertM(in.aggregate(sink).runCollect)(
        equalTo(List(Chunk.empty, Chunk(1, 2, 3, 4)))
      )
    },
    testM("no delimiter") {
      val sink = Framing.delimiter(Chunk(-1), 1000)
      val in   = Stream(Chunk(1, 2), Chunk(3, 4))
      assertM(in.aggregate(sink).runCollect)(
        equalTo(List(Chunk(1, 2, 3, 4)))
      )
    },
    testM("empty stream") {
      val sink = Framing.delimiter(Chunk(-1, -2), 1000)
      val in   = Stream.empty
      assertM(in.aggregate(sink).runCount)(equalTo(0L))
    },
    testM("fails if maximum frame length exceeded") {
      val sink = Framing.delimiter(Chunk(-1), 3)
      val in   = Stream(Chunk(1, 2), Chunk(3, 4, -1, 5))
      assertM(in.aggregate(sink).runCollect.run)(
        fails(isSubtype[IllegalArgumentException](anything))
      )
    },
    testM("succeeds if maximum frame length hit exactly") {
      val sink = Framing.delimiter(Chunk(-1), 4)
      val in   = Stream(Chunk(1, 2), Chunk(3, 4, -1, 5))
      assertM(in.aggregate(sink).runCollect.run)(
        fails(isSubtype[IllegalArgumentException](anything))
      )
    }
  )

}
