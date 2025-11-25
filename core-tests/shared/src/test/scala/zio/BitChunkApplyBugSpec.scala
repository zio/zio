package zio

import zio.test._

/**
 * Deterministic tests that expose the bug in BitChunk.apply() where minBitIndex
 * was not accounted for. These tests use specific, hand-crafted inputs that
 * directly trigger the bug without relying on property-based testing
 * randomness.
 *
 * The bug: BitChunkByte/Int/Long.apply(n) treated 'n' as a raw bit index into
 * the underlying array, ignoring minBitIndex. This caused incorrect results
 * when accessing elements in sliced/dropped BitChunks.
 *
 * These tests should:
 *   - FAIL with the buggy code (before fix) on ANY Scala version
 *   - PASS with the fixed code on ANY Scala version
 */
object BitChunkApplyBugSpec extends ZIOBaseSpec {

  def spec = suite("BitChunkApplyBugSpec")(
    suite("BitChunkByte.apply with minBitIndex > 0")(
      test("apply(0) on dropped BitChunkByte returns correct first bit") {
        // Byte 0x81 = 10000001 in binary
        // After drop(2), the first bit should be bit index 2 (which is '0'), not bit index 0 (which is '1')
        val bytes    = Chunk(0x81.toByte) // "10000001"
        val bitChunk = bytes.asBitsByte   // BitChunkByte(bytes, min=0, max=8)
        val dropped  = bitChunk.drop(2)   // BitChunkByte(bytes, min=2, max=8), length=6

        // With the bug: apply(0) returns bit 0 = true (the '1' at position 0)
        // Correct:      apply(0) returns bit 2 = false (the '0' at position 2)
        val firstBit = dropped(0)

        assertTrue(!firstBit) // Should be false (bit at position 2 is '0')
      },
      test("apply on dropped BitChunkByte returns all correct bits") {
        // Byte 0xA5 = 10100101 in binary
        val bytes    = Chunk(0xa5.toByte) // "10100101"
        val bitChunk = bytes.asBitsByte   // All 8 bits: 1,0,1,0,0,1,0,1
        val dropped  = bitChunk.drop(3)   // Should be bits 3-7: 0,0,1,0,1

        val bits = (0 until dropped.length).map(dropped(_)).toList

        // Expected: bits at positions 3,4,5,6,7 = 0,0,1,0,1 = false,false,true,false,true
        assertTrue(bits == List(false, false, true, false, true))
      },
      test("toBinaryString on dropped BitChunkByte is correct") {
        // This is the exact failing case from CI: drop(2).take(11) on a specific byte sequence
        val bytes   = Chunk(0x81.toByte, 0xa3.toByte) // "10000001 10100011"
        val dropped = bytes.asBitsByte.drop(2).take(11)

        // Original: "1000000110100011"
        // After drop(2): "00000110100011" (14 bits)
        // After take(11): "00000110100" (11 bits)
        val actual   = dropped.toBinaryString
        val expected = "00000110100"

        assertTrue(actual == expected)
      },
      test("foreach on dropped BitChunkByte iterates correct bits") {
        val bytes    = Chunk(0xf0.toByte) // "11110000"
        val bitChunk = bytes.asBitsByte
        val dropped  = bitChunk.drop(2)   // Should be bits 2-7: 1,1,0,0,0,0

        val collected = scala.collection.mutable.ListBuffer[Boolean]()
        dropped.foreach(b => collected += b)

        // Expected: bits at positions 2,3,4,5,6,7 = 1,1,0,0,0,0
        assertTrue(collected.toList == List(true, true, false, false, false, false))
      },
      test("toPackedByte on dropped BitChunkByte produces correct result") {
        // This tests the interaction between BitChunk and ChunkPackedBoolean
        val bytes   = Chunk(0x81.toByte, 0xa3.toByte) // "10000001 10100011"
        val dropped = bytes.asBitsByte.drop(2).take(11)

        // After drop(2).take(11): "00000110100" (11 bits)
        // Packed as bytes: "00000110" (8 bits) + "100" padded to "10000000" (but only 3 bits matter)
        val packed = dropped.toPackedByte

        // First byte should be 0b00000110 = 6
        assertTrue(packed.length == 2) &&
        assertTrue(packed(0) == 6.toByte)
      }
    ),
    suite("BitChunkInt.apply with minBitIndex > 0")(
      test("apply(0) on dropped BitChunkInt returns correct first bit") {
        val ints     = Chunk(0x80000001) // MSB=1, LSB=1, rest=0
        val bitChunk = ints.asBitsInt(Chunk.BitChunk.Endianness.BigEndian)
        val dropped  = bitChunk.drop(1)  // Drop the MSB

        // After drop(1), first bit should be '0' (the second bit of original), not '1' (the MSB)
        val firstBit = dropped(0)

        assertTrue(!firstBit) // Should be false
      },
      test("toBinaryString on dropped BitChunkInt is correct") {
        val ints    = Chunk(0xf0f0f0f0)
        val dropped = ints.asBitsInt(Chunk.BitChunk.Endianness.BigEndian).drop(4).take(8)

        // Original: "11110000111100001111000011110000"
        // After drop(4).take(8): "00001111"
        val actual   = dropped.toBinaryString
        val expected = "00001111"

        assertTrue(actual == expected)
      }
    ),
    suite("BitChunkLong.apply with minBitIndex > 0")(
      test("apply(0) on dropped BitChunkLong returns correct first bit") {
        val longs    = Chunk(0x8000000000000001L) // MSB=1, LSB=1, rest=0
        val bitChunk = longs.asBitsLong(Chunk.BitChunk.Endianness.BigEndian)
        val dropped  = bitChunk.drop(1)           // Drop the MSB

        // After drop(1), first bit should be '0', not '1'
        val firstBit = dropped(0)

        assertTrue(!firstBit) // Should be false
      },
      test("toBinaryString on dropped BitChunkLong is correct") {
        val longs   = Chunk(0xff00ff00ff00ff00L)
        val dropped = longs.asBitsLong(Chunk.BitChunk.Endianness.BigEndian).drop(8).take(8)

        // Original starts with "11111111 00000000 ..."
        // After drop(8).take(8): "00000000"
        val actual   = dropped.toBinaryString
        val expected = "00000000"

        assertTrue(actual == expected)
      }
    ),
    suite("Regression test: exact CI failure case")(
      test("pack byte with drop(2).take(11) - the exact failing case") {
        // This is the exact shrunk input that failed in CI:
        // Chunk(true, false, false, false, false, false, false, true, true, false, true, false, ...)
        // which corresponds to bytes 0x81, 0xA3, ...
        // with drop=2, take=11

        val bytes =
          Chunk(0x81.toByte, 0xa3.toByte, 0x26.toByte, 0xdb.toByte, 0xe9.toByte, 0x47.toByte, 0x1f.toByte, 0x62.toByte)
        val bools = bytes.asBitsByte.drop(2).take(11)

        // Convert to binary string for comparison
        def toBinaryStringByte(byte: Byte): String =
          String.format("%8s", (byte.toInt & 0xff).toBinaryString).replace(' ', '0')

        val actual = bools.toPackedByte.map(toBinaryStringByte).mkString
        val expected = bytes
          .map(toBinaryStringByte)
          .mkString
          .drop(2)
          .take(11)
          .grouped(8)
          .map(s => s"%8s".format(s).replace(' ', '0'))
          .mkString

        assertTrue(actual == expected)
      }
    ),
    suite("Multiple operations chain")(
      test("drop.drop on BitChunkByte") {
        val bytes    = Chunk(0xff.toByte, 0x00.toByte) // "11111111 00000000"
        val bitChunk = bytes.asBitsByte
        val result   = bitChunk.drop(4).drop(4)        // Should be bits 8-15: "00000000"

        assertTrue(result.toBinaryString == "00000000")
      },
      test("take.drop on BitChunkByte") {
        val bytes    = Chunk(0xff.toByte, 0x00.toByte) // "11111111 00000000"
        val bitChunk = bytes.asBitsByte
        val result   = bitChunk.take(12).drop(4)       // bits 0-11 then drop 4 = bits 4-11: "11110000"

        assertTrue(result.toBinaryString == "11110000")
      },
      test("slice on BitChunkByte") {
        val bytes    = Chunk(0xaa.toByte)   // "10101010"
        val bitChunk = bytes.asBitsByte
        val result   = bitChunk.slice(2, 6) // bits 2-5: "1010"

        assertTrue(result.toBinaryString == "1010")
      }
    )
  )
}
