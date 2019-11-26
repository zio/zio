package zio.stream

import zio.{ Chunk, UIO, ZIOBaseSpec }
import zio.test._
import zio.test.Assertion.equalTo
import java.nio._

object ChunkBufferSpec extends ZIOBaseSpec {

  def spec = suite("ChunkBufferSpec")(
    suite("ByteBuffer")(
      testM("byte array buffer no copying") {
        UIO.effectTotal {
          val array  = Array(1, 2, 3).map(_.toByte)
          val buffer = ByteBuffer.wrap(array)
          assert(Chunk.fromByteBuffer(buffer), equalTo(Chunk(1, 2, 3)))
        }
      },
      testM("byte array buffer partial copying") {
        UIO.effectTotal {
          val buffer = ByteBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toByte)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromByteBuffer(buffer), equalTo(Chunk(5, 6, 7)))
        }
      },
      testM("direct byte buffer copying") {
        UIO.effectTotal {
          val buffer = ByteBuffer.allocateDirect(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toByte)
            i += 1
          }
          buffer.position(2)
          buffer.limit(5)
          assert(Chunk.fromByteBuffer(buffer), equalTo(Chunk(2, 3, 4)))
        }
      }
    ),
    suite("CharBuffer")(
      testM("char array buffer no copying") {
        UIO.effectTotal {
          val array  = Array(1, 2, 3).map(_.toChar)
          val buffer = CharBuffer.wrap(array)
          assert(Chunk.fromCharBuffer(buffer), equalTo(Chunk(1, 2, 3)))
        }
      },
      testM("char array buffer partial copying") {
        UIO.effectTotal {
          val buffer = CharBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toChar)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromCharBuffer(buffer), equalTo(Chunk(5, 6, 7)))
        }
      }
    )
  )
}
