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
    test("pack byte - BitChunkByte with drop and take") {
      // Deterministic version of "pack byte" that directly exposes the bug where
      // BitChunk.apply() didn't account for minBitIndex when accessing sliced chunks.
      // This test uses the EXACT same logic as "pack byte" but with fixed inputs.
      //
      // The bug: BitChunkByte.apply(n) treated 'n' as a raw bit index into the
      // underlying byte array, ignoring minBitIndex. When drop() is called, a new
      // BitChunk is created with minBitIndex > 0, but apply() would still read
      // from the wrong position.
      //
      // Example with these inputs:
      //   bytes = 0x81, 0xA3 = "10000001 10100011" (16 bits)
      //   bls.drop(2).take(11) creates BitChunkByte with minBitIndex=2, maxBitIndex=13
      //   Expected bits: positions 2-12 = "00000110100" (11 bits)
      //
      // With the bug:
      //   apply(0) would return bit 0 ('1') instead of bit 2 ('0')
      //   toPackedByte would pack the wrong bits, producing incorrect output
      //
      // Without the bug:
      //   apply(0) correctly returns bit at (0 + minBitIndex) = bit 2 ('0')
      //   toPackedByte correctly packs bits 2-12

      val bytes = Chunk(0x81.toByte, 0xa3.toByte) // "10000001 10100011"
      val bls   = bytes.asBitsByte

      val drop = 2
      val take = 11

      val bools    = bls.drop(drop).take(take)
      val actual   = bools.toPackedByte.map(toBinaryString).mkString
      val expected = toBinaryString(bools, bits = 8, Chunk.BitChunk.Endianness.BigEndian)

      assert(actual)(equalTo(expected))
    },
    test("pack byte - BitChunkByte with various drop/take combinations") {
      // Tests multiple drop/take combinations on BitChunkByte to ensure the
      // minBitIndex fix works correctly across different slicing scenarios.
      //
      // Each test case creates a sliced BitChunkByte and verifies that
      // toPackedByte produces the correct output by comparing against
      // the expected binary string representation.

      val bytes = Chunk(0xff.toByte, 0x00.toByte, 0xaa.toByte, 0x55.toByte) // "11111111 00000000 10101010 01010101"
      val bls   = bytes.asBitsByte

      val testCases = List(
        (1, 10), // drop 1, take 10 - starts mid-byte
        (3, 15), // drop 3, take 15 - crosses multiple bytes
        (7, 9),  // drop 7, take 9  - crosses byte boundary at awkward position
        (8, 8),  // drop 8, take 8  - exactly the second byte (minBitIndex = 8)
        (4, 20), // drop 4, take 20 - large slice crossing multiple bytes
        (0, 16)  // drop 0 (baseline - minBitIndex = 0, should always work)
      )

      testCases.foldLeft(assertCompletes) { case (acc, (drop, take)) =>
        val bools    = bls.drop(drop).take(take)
        val actual   = bools.toPackedByte.map(toBinaryString).mkString
        val expected = toBinaryString(bools, bits = 8, Chunk.BitChunk.Endianness.BigEndian)
        acc && assert(actual)(equalTo(expected))
      }
    },
    test("pack byte - BitChunkInt with drop and take") {
      // Same bug pattern as BitChunkByte, but for BitChunkInt.
      // BitChunkInt.apply(n) also needs to account for minBitIndex.
      //
      // Input: 0xF0F0F0F0, 0x0F0F0F0F as 64 bits in big-endian order
      // Original: "11110000111100001111000011110000 00001111000011110000111100001111"
      // After drop(4).take(24): bits 4-27 = "000011110000111100001111" (24 bits)

      val ints = Chunk(0xf0f0f0f0, 0x0f0f0f0f)
      val bls  = ints.asBitsInt(Chunk.BitChunk.Endianness.BigEndian)

      val drop = 4
      val take = 24

      val bools    = bls.drop(drop).take(take)
      val actual   = bools.toPackedByte.map(toBinaryString).mkString
      val expected = toBinaryString(bools, bits = 8, Chunk.BitChunk.Endianness.BigEndian)

      assert(actual)(equalTo(expected))
    },
    test("pack byte - BitChunkLong with drop and take") {
      // Same bug pattern as BitChunkByte, but for BitChunkLong.
      // BitChunkLong.apply(n) also needs to account for minBitIndex.
      //
      // Input: 0xFF00FF00FF00FF00L as 64 bits in big-endian order
      // Original: "1111111100000000111111110000000011111111000000001111111100000000"
      // After drop(8).take(32): bits 8-39 = "00000000111111110000000011111111" (32 bits)

      val longs = Chunk(0xff00ff00ff00ff00L)
      val bls   = longs.asBitsLong(Chunk.BitChunk.Endianness.BigEndian)

      val drop = 8
      val take = 32

      val bools    = bls.drop(drop).take(take)
      val actual   = bools.toPackedByte.map(toBinaryString).mkString
      val expected = toBinaryString(bools, bits = 8, Chunk.BitChunk.Endianness.BigEndian)

      assert(actual)(equalTo(expected))
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
