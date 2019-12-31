package zio

import zio.test.{ assert, suite }
import zio.test.Assertion.equalTo

object ChunkSpec extends ZIOBaseSpec {

  def spec = suite("ChunkSpec")(
    suite("concatenated")(
      zio.test.test("size must match length") {
        val chunk = Chunk.empty ++ Chunk.fromArray(Array(1, 2)) ++ Chunk(3, 4, 5) ++ Chunk.single(6)
        assert(chunk.size)(equalTo(chunk.length))
      }
    ),
    suite("empty")(
      zio.test.test("size must match length") {
        val chunk = Chunk.empty
        assert(chunk.size)(equalTo(chunk.length))
      }
    ),
    suite("fromArray")(
      zio.test.test("size must match length") {
        val chunk = Chunk.fromArray(Array(1, 2, 3))
        assert(chunk.size)(equalTo(chunk.length))
      }
    ),
    suite("fromIterable")(
      zio.test.test("size must match length") {
        val chunk = Chunk.fromIterable(List("1", "2", "3"))
        assert(chunk.size)(equalTo(chunk.length))
      }
    ),
    suite("single")(
      zio.test.test("size must match length") {
        val chunk = Chunk.single(true)
        assert(chunk.size)(equalTo(chunk.length))
      }
    )
  )
}
