package zio

import zio.test.Assertion.equalTo
import zio.test._

import java.nio._

object ChunkBufferSpec extends ZIOBaseSpec {

  def spec = suite("ChunkBufferSpec")(
    suite("ByteBuffer")(
      test("byte array buffer no copying") {
        ZIO.succeed {
          val array  = Array(1, 2, 3).map(_.toByte)
          val buffer = ByteBuffer.wrap(array)
          assert(Chunk.fromByteBuffer(buffer))(equalTo(byteChunk(1, 2, 3)))
        }
      },
      test("byte array buffer partial copying") {
        ZIO.succeed {
          val buffer = ByteBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toByte)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromByteBuffer(buffer))(equalTo(byteChunk(5, 6, 7)))
        }
      },
      test("byte array buffer slice copying") {
        ZIO.succeed {
          val buffer = ByteBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toByte)
            i += 1
          }
          buffer.position(3)
          buffer.limit(7)
          val newBuffer = buffer.slice()
          assert(Chunk.fromByteBuffer(newBuffer))(equalTo(byteChunk(3, 4, 5, 6)))
        }
      },
      test("direct byte buffer copying") {
        ZIO.succeed {
          val buffer = ByteBuffer.allocateDirect(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toByte)
            i += 1
          }
          buffer.position(2)
          buffer.limit(5)
          assert(Chunk.fromByteBuffer(buffer))(equalTo(byteChunk(2, 3, 4)))
        }
      }
    ),
    suite("CharBuffer")(
      test("char array buffer no copying") {
        ZIO.succeed {
          val array  = Array(1, 2, 3).map(_.toChar)
          val buffer = CharBuffer.wrap(array)
          assert(Chunk.fromCharBuffer(buffer))(equalTo(charChunk(1, 2, 3)))
        }
      },
      test("char array buffer partial copying") {
        ZIO.succeed {
          val buffer = CharBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toChar)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromCharBuffer(buffer))(equalTo(charChunk(5, 6, 7)))
        }
      },
      test("char array buffer slice copying") {
        ZIO.succeed {
          val buffer = CharBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toChar)
            i += 1
          }
          buffer.position(3)
          buffer.limit(7)
          val newBuffer = buffer.slice()
          assert(Chunk.fromCharBuffer(newBuffer))(equalTo(charChunk(3, 4, 5, 6)))
        }
      },
      test("direct char buffer copying") {
        ZIO.succeed {
          val byteBuffer = ByteBuffer.allocateDirect(java.lang.Character.BYTES * 10)
          var i          = 0
          while (i < java.lang.Character.BYTES * 10) {
            byteBuffer.put(i, i.toByte)
            i += 1
          }
          val buffer = byteBuffer.asCharBuffer()
          val array  = Array.ofDim[Char](3)
          i = 5
          while (i < 8) {
            array(i - 5) = buffer.get(i)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromCharBuffer(buffer))(equalTo(Chunk.fromArray(array)))
        }
      }
    ),
    suite("DoubleBuffer")(
      test("double array buffer no copying") {
        ZIO.succeed {
          val array  = Array(1, 2, 3).map(_.toDouble)
          val buffer = DoubleBuffer.wrap(array)
          assert(Chunk.fromDoubleBuffer(buffer))(equalTo(doubleChunk(1, 2, 3)))
        }
      },
      test("double array buffer partial copying") {
        ZIO.succeed {
          val buffer = DoubleBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toDouble)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromDoubleBuffer(buffer))(equalTo(doubleChunk(5, 6, 7)))
        }
      },
      test("double array buffer slice copying") {
        ZIO.succeed {
          val buffer = DoubleBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toDouble)
            i += 1
          }
          buffer.position(3)
          buffer.limit(7)
          val newBuffer = buffer.slice()
          assert(Chunk.fromDoubleBuffer(newBuffer))(equalTo(doubleChunk(3, 4, 5, 6)))
        }
      },
      test("direct double buffer copying") {
        ZIO.succeed {
          val byteBuffer = ByteBuffer.allocateDirect(java.lang.Double.BYTES * 10)
          var i          = 0
          while (i < java.lang.Double.BYTES * 10) {
            byteBuffer.put(i, i.toByte)
            i += 1
          }
          val buffer = byteBuffer.asDoubleBuffer()
          val array  = Array.ofDim[Double](3)
          i = 5
          while (i < 8) {
            array(i - 5) = buffer.get(i)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromDoubleBuffer(buffer))(equalTo(Chunk.fromArray(array)))
        }
      }
    ),
    suite("FloatBuffer")(
      test("float array buffer no copying") {
        ZIO.succeed {
          val array  = Array(1, 2, 3).map(_.toFloat)
          val buffer = FloatBuffer.wrap(array)
          assert(Chunk.fromFloatBuffer(buffer))(equalTo(floatChunk(1, 2, 3)))
        }
      },
      test("float array buffer partial copying") {
        ZIO.succeed {
          val buffer = FloatBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toFloat)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromFloatBuffer(buffer))(equalTo(floatChunk(5, 6, 7)))
        }
      },
      test("float array buffer slice copying") {
        ZIO.succeed {
          val buffer = FloatBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toFloat)
            i += 1
          }
          buffer.position(3)
          buffer.limit(7)
          val newBuffer = buffer.slice()
          assert(Chunk.fromFloatBuffer(newBuffer))(equalTo(floatChunk(3, 4, 5, 6)))
        }
      },
      test("direct float buffer copying") {
        ZIO.succeed {
          val byteBuffer = ByteBuffer.allocateDirect(java.lang.Float.BYTES * 10)
          var i          = 0
          while (i < java.lang.Float.BYTES * 10) {
            byteBuffer.put(i, i.toByte)
            i += 1
          }
          val buffer = byteBuffer.asFloatBuffer()
          val array  = Array.ofDim[Float](3)
          i = 5
          while (i < 8) {
            array(i - 5) = buffer.get(i)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromFloatBuffer(buffer))(equalTo(Chunk.fromArray(array)))
        }
      }
    ),
    suite("IntBuffer")(
      test("int array buffer no copying") {
        ZIO.succeed {
          val array  = Array(1, 2, 3)
          val buffer = IntBuffer.wrap(array)
          assert(Chunk.fromIntBuffer(buffer))(equalTo(Chunk(1, 2, 3)))
        }
      },
      test("int array buffer partial copying") {
        ZIO.succeed {
          val buffer = IntBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromIntBuffer(buffer))(equalTo(Chunk(5, 6, 7)))
        }
      },
      test("int array buffer slice copying") {
        ZIO.succeed {
          val buffer = IntBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i)
            i += 1
          }
          buffer.position(3)
          buffer.limit(7)
          val newBuffer = buffer.slice()
          assert(Chunk.fromIntBuffer(newBuffer))(equalTo(Chunk(3, 4, 5, 6)))
        }
      },
      test("direct int buffer copying") {
        ZIO.succeed {
          val byteBuffer = ByteBuffer.allocateDirect(java.lang.Integer.BYTES * 10)
          var i          = 0
          while (i < java.lang.Integer.BYTES * 10) {
            byteBuffer.put(i, i.toByte)
            i += 1
          }
          val buffer = byteBuffer.asIntBuffer()
          val array  = Array.ofDim[Int](3)
          i = 5
          while (i < 8) {
            array(i - 5) = buffer.get(i)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromIntBuffer(buffer))(equalTo(Chunk.fromArray(array)))
        }
      }
    ),
    suite("LongBuffer")(
      test("long array buffer no copying") {
        ZIO.succeed {
          val array  = Array(1, 2, 3).map(_.toLong)
          val buffer = LongBuffer.wrap(array)
          assert(Chunk.fromLongBuffer(buffer))(equalTo(longChunk(1, 2, 3)))
        }
      },
      test("long array buffer partial copying") {
        ZIO.succeed {
          val buffer = LongBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toLong)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromLongBuffer(buffer))(equalTo(longChunk(5, 6, 7)))
        }
      },
      test("long array buffer slice copying") {
        ZIO.succeed {
          val buffer = LongBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toLong)
            i += 1
          }
          buffer.position(3)
          buffer.limit(7)
          val newBuffer = buffer.slice()
          assert(Chunk.fromLongBuffer(newBuffer))(equalTo(longChunk(3, 4, 5, 6)))
        }
      },
      test("direct long buffer copying") {
        ZIO.succeed {
          val byteBuffer = ByteBuffer.allocateDirect(java.lang.Long.BYTES * 10)
          var i          = 0
          while (i < java.lang.Long.BYTES * 10) {
            byteBuffer.put(i, i.toByte)
            i += 1
          }
          val buffer = byteBuffer.asLongBuffer()
          val array  = Array.ofDim[Long](3)
          i = 5
          while (i < 8) {
            array(i - 5) = buffer.get(i)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromLongBuffer(buffer))(equalTo(Chunk.fromArray(array)))
        }
      }
    ),
    suite("ShortBuffer")(
      test("short array buffer no copying") {
        ZIO.succeed {
          val array  = Array(1, 2, 3).map(_.toShort)
          val buffer = ShortBuffer.wrap(array)
          assert(Chunk.fromShortBuffer(buffer))(equalTo(shortChunk(1, 2, 3)))
        }
      },
      test("short array buffer partial copying") {
        ZIO.succeed {
          val buffer = ShortBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toShort)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromShortBuffer(buffer))(equalTo(shortChunk(5, 6, 7)))
        }
      },
      test("short array buffer slice copying") {
        ZIO.succeed {
          val buffer = ShortBuffer.allocate(10)
          var i      = 0
          while (i < 10) {
            buffer.put(i, i.toShort)
            i += 1
          }
          buffer.position(3)
          buffer.limit(7)
          val newBuffer = buffer.slice()
          assert(Chunk.fromShortBuffer(newBuffer))(equalTo(shortChunk(3, 4, 5, 6)))
        }
      },
      test("direct short buffer copying") {
        ZIO.succeed {
          val byteBuffer = ByteBuffer.allocateDirect(java.lang.Short.BYTES * 10)
          var i          = 0
          while (i < java.lang.Short.BYTES * 10) {
            byteBuffer.put(i, i.toByte)
            i += 1
          }
          val buffer = byteBuffer.asShortBuffer()
          val array  = Array.ofDim[Short](3)
          i = 5
          while (i < 8) {
            array(i - 5) = buffer.get(i)
            i += 1
          }
          buffer.position(5)
          buffer.limit(8)
          assert(Chunk.fromShortBuffer(buffer))(equalTo(Chunk.fromArray(array)))
        }
      }
    )
  )

  final def byteChunk(bytes: Byte*): Chunk[Byte] =
    Chunk.fromIterable(bytes)

  final def charChunk(chars: Char*): Chunk[Char] =
    Chunk.fromIterable(chars)

  final def doubleChunk(doubles: Double*): Chunk[Double] =
    Chunk.fromIterable(doubles)

  final def floatChunk(floats: Float*): Chunk[Float] =
    Chunk.fromIterable(floats)

  final def longChunk(longs: Long*): Chunk[Long] =
    Chunk.fromIterable(longs)

  final def shortChunk(shorts: Short*): Chunk[Short] =
    Chunk.fromIterable(shorts)
}
