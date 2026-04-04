package zio.stream

import zio._
import zio.stream.ZStream.fromChunk
import zio.test.Assertion._
import zio.test._

object RechunkSpec extends ZIOBaseSpec {
  override def spec =
    suite("RechunkSpec")(
      test("rechunk small with rest")(
        assertZIO(ZStream(1, 2, 3, 4, 5).rechunk(2).runCollect)(
          equalTo(Chunk(1, 2, 3, 4, 5))
        )
      ),
      test("rechunk small with no rest")(
        assertZIO(ZStream(1, 2, 3, 4).rechunk(2).runCollect)(
          equalTo(Chunk(1, 2, 3, 4))
        )
      ),
      test("rechunk large") {
        val elems = (1 to 51).toList
        for {
          result <- ZStream.fromIterable(elems).rechunk(2).runCollect
        } yield {
          assertTrue(result.toList == elems)
        }
      },
      test("rechunk mixed large/small sizes")(
        check(Gen.chunkOfN(10)(Gen.chunkOfBounded(0, 50)(Gen.int(1, 100))), Gen.int(10, 60)) { (c, n) =>
          val in = ZStream.fromChunk(c).flatMap(fromChunk(_))
          for {
            result  <- in.rechunk(n).runCollect
            expected = c.flatten
          } yield assertTrue(result == expected)
        }
      )
    )
}
