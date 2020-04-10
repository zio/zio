package zio

import zio.Chunk.BitChunk
import zio.random.Random
import zio.test.Assertion._
import zio.test._

object BitChunkSpec extends ZIOBaseSpec {

  val genByteChunk: Gen[Random with Sized, Chunk[Byte]] =
    for {
      bytes <- Gen.listOf(Gen.anyByte)
    } yield Chunk.fromIterable(bytes)

  def toBinaryString(byte: Byte): String =
    String.format("%8s", (byte.toInt & 0xFF).toBinaryString).replace(' ', '0')

  def spec = suite("BitChunkSpec")(
    testM("foreach") {
      check(genByteChunk) { bytes =>
        val bitChunk = BitChunk.fromByteChunk(bytes)
        val builder  = new scala.collection.mutable.StringBuilder
        bitChunk.foreach(bit => if (bit) builder.append("1") else builder.append("0"))
        val actual   = builder.toString
        val expected = bitChunk.toBinaryString
        assert(actual)(equalTo(expected))
      }
    },
    testM("toBinaryString") {
      check(genByteChunk) { bytes =>
        val bitChunk = BitChunk.fromByteChunk(bytes)
        val actual   = bitChunk.toBinaryString
        val expected = bytes.map(toBinaryString).mkString("", "", "")
        assert(actual)(equalTo(expected))
      }
    }
  )
}
