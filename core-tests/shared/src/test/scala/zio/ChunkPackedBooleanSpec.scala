package zio

import zio.test.Assertion._
import zio.test._

object ChunkPackedBooleanSpec extends ZIOBaseSpec {

  val genBoolChunk: Gen[Sized, Chunk[Boolean]] =
    for {
      bytes <- Gen.listOf(Gen.boolean)
    } yield Chunk.fromIterable(bytes)

  val genInt: Gen[Sized, Int] =
    Gen.small(Gen.const(_))

  val genEndianness: Gen[Sized, Chunk.BitChunk.Endianness] =
    Gen.elements(Chunk.BitChunk.Endianness.BigEndian, Chunk.BitChunk.Endianness.LittleEndian)

  def toBinaryString(bool: Boolean): String =
    if (bool) "1" else "0"

  def toBinaryString(byte: Byte): String =
    String.format("%8s", (byte.toInt & 0xff).toBinaryString).replace(' ', '0')

  def toBinaryString(int: Int): String =
    String.format("%32s", int.toBinaryString).replace(' ', '0')

  def toBinaryString(long: Long): String =
    String.format("%64s", long.toBinaryString).replace(' ', '0')

  def toBinaryString(bools: Chunk[Boolean], bits: Int, endianness: Chunk.BitChunk.Endianness): String =
    bools
      .sliding(bits, bits)
      .map(x => s"%${bits}s".format(x.map(toBinaryString).mkString).replace(' ', '0'))
      .map(x => if (endianness == Chunk.BitChunk.Endianness.BigEndian) x else x.reverse)
      .mkString

  def spec = suite("ChunkPackedBooleanSpec")(
    test("pack byte") {
      check(genBoolChunk) { bools =>
        val actual   = bools.toPackedByte.map(toBinaryString).mkString
        val expected = toBinaryString(bools, bits = 8, Chunk.BitChunk.Endianness.BigEndian)
        assert(actual)(equalTo(expected))
      }
    },
    test("pack int") {
      check(genBoolChunk, genEndianness) { (bools, endianness) =>
        val actual   = bools.toPackedInt(endianness).map(toBinaryString).mkString
        val expected = toBinaryString(bools, bits = 32, endianness)
        assert(actual)(equalTo(expected))
      }
    },
    test("pack long") {
      check(genBoolChunk, genEndianness) { (bools, endianness) =>
        val actual   = bools.toPackedLong(endianness).map(toBinaryString).mkString
        val expected = toBinaryString(bools, bits = 64, endianness)
        assert(actual)(equalTo(expected))
      }
    }
  )

}
