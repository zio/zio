package zio.stream

import zio._
import zio.test.Assertion._
import zio.test._

import scala.collection.immutable.ArraySeq.unsafeWrapArray

object RechunkSpec extends ZIOBaseSpec {
  override def spec =
    suite("RechunkSpec")(
      test("rechunk small with rest")(
        assertZIO(ZStream(1, 2, 3, 4, 5).rechunk(2).chunks.runCollect)(
          equalTo(Chunk(Chunk(1, 2), Chunk(3, 4), Chunk(5)))
        )
      ),
      test("rechunk small with no rest")(
        assertZIO(ZStream(1, 2, 3, 4).rechunk(2).chunks.runCollect)(
          equalTo(Chunk(Chunk(1, 2), Chunk(3, 4)))
        )
      ),
      test("rechunk large") {
        val array = (1 to 51).toArray
        for {
          result <- ZStream(unsafeWrapArray(array): _*).rechunk(2).chunks.runCollect
        } yield {
          assertTrue(result.size == 26) &&
          assertTrue(result.dropRight(1).forall(_.size == 2)) &&
          assertTrue(result.flatten sameElements array)
        }
      },
      test("rechunk mixed large/small sizes")(
        check(Gen.chunkOfN(10)(Gen.chunkOfBounded(0, 50)(Gen.int(1, 100))), Gen.int(10, 60)) { (c, n) =>
          for {
            result  <- ZStream.fromChunks(unsafeWrapArray(c.toArray): _*).rechunk(n).chunks.runCollect
            expected = c.flatten.grouped(n).toList
          } yield assertTrue(result.toList == expected)
        }
      )
    )
}
