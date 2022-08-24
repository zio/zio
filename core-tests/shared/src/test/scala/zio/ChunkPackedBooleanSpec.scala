package zio

import zio.test.Assertion._
import zio.test._

object ChunkPackedBooleanSpec extends ZIOBaseSpec {

  val genEndianness: Gen[Any, Chunk.BitChunk.Endianness] =
    Gen.elements(Chunk.BitChunk.Endianness.BigEndian, Chunk.BitChunk.Endianness.LittleEndian)

  val genBoolChunk: Gen[Any, Chunk[Boolean]] =
    for {
      endianness   <- genEndianness
      booleanChunk <- Gen.listOf(Gen.boolean).map(Chunk.fromIterable)
      byteChunk    <- Gen.listOf(Gen.byte).map(Chunk.fromIterable).map(x => x.asBitsByte)
      intChunk     <- Gen.listOf(Gen.int).map(Chunk.fromIterable).map(x => x.asBitsInt(endianness))
      longChunk    <- Gen.listOf(Gen.long).map(Chunk.fromIterable).map(x => x.asBitsLong(endianness))
      oneOf        <- Gen.elements(booleanChunk, byteChunk, intChunk, longChunk)
    } yield oneOf

  val genInt: Gen[Any, Int] =
    Gen.small(Gen.const(_))

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
      check(genBoolChunk, genInt, genInt) { (bls, drop, take) =>
        val bools    = bls.drop(drop).take(take)
        val actual   = bools.toPackedByte.map(toBinaryString).mkString
        val expected = toBinaryString(bools, bits = 8, Chunk.BitChunk.Endianness.BigEndian)
        assert(actual)(equalTo(expected))
      }
    },
    test("pack int") {
      check(genBoolChunk, genEndianness, genInt, genInt) { (bls, endianness, drop, take) =>
        val bools    = bls.drop(drop).take(take)
        val actual   = bools.toPackedInt(endianness).map(toBinaryString).mkString
        val expected = toBinaryString(bools, bits = 32, endianness)
        assert(actual)(equalTo(expected))
      }
    },
    test("pack long") {
      check(genBoolChunk, genEndianness, genInt, genInt) { (bls, endianness, drop, take) =>
        val bools    = bls.drop(drop).take(take)
        val actual   = bools.toPackedLong(endianness).map(toBinaryString).mkString
        val expected = toBinaryString(bools, bits = 64, endianness)
        assert(actual)(equalTo(expected))
      }
    },
    test("hashcode") {
      val actual = Chunk(false, true, false).toPackedByte.hashCode
      assert(actual)(anything)
    }
  )

}
