package zio

import zio.test._

/**
 * Tests exposing the bug in BitChunk.apply() where minBitIndex was not
 * accounted for.
 *
 * The bug: BitChunk.apply(n) used 'n' directly instead of (n + minBitIndex).
 * After drop(), minBitIndex > 0, so apply(0) returned the wrong bit.
 *
 * These tests FAIL with buggy code and PASS with the fix.
 */
object BitChunkApplyBugSpec extends ZIOBaseSpec {

  def spec = suite("BitChunkApplyBugSpec")(
    suite("BitChunkByte")(
      test("apply(0) after drop(1)") {
        // 0x80 = "10000000" - only the first bit is 1
        // After drop(1), apply(0) should return bit 1 (which is 0), not bit 0 (which is 1)
        val dropped = Chunk(0x80.toByte).asBitsByte.drop(1)

        assertTrue(dropped(0) == false)
      },
      test("apply returns correct bits after drop(3)") {
        // 0xF0 = "11110000"
        // After drop(3), we should get bits 3-7: "10000" = true,false,false,false,false
        val dropped = Chunk(0xf0.toByte).asBitsByte.drop(3)
        val bits    = (0 until 5).map(dropped(_)).toList

        assertTrue(bits == List(true, false, false, false, false))
      },
      test("toPackedByte after drop") {
        // 0xFF, 0x00 = "11111111 00000000"
        // After drop(4).take(8), we get bits 4-11: "11110000"
        // Packed as byte: 0xF0 = -16
        val packed = Chunk(0xff.toByte, 0x00.toByte).asBitsByte.drop(4).take(8).toPackedByte

        assertTrue(packed(0) == 0xf0.toByte)
      }
    ),
    suite("BitChunkInt")(
      test("apply(0) after drop(1)") {
        // 0x80000000 = MSB is 1, rest is 0
        // After drop(1), apply(0) should return bit 1 (which is 0), not bit 0 (which is 1)
        val dropped = Chunk(0x80000000).asBitsInt(Chunk.BitChunk.Endianness.BigEndian).drop(1)

        assertTrue(dropped(0) == false)
      },
      test("apply returns correct bits after drop(4)") {
        // 0xF0000000 = "11110000 00000000 00000000 00000000"
        // After drop(4).take(4), we get bits 4-7: "0000" = all false
        val dropped = Chunk(0xf0000000).asBitsInt(Chunk.BitChunk.Endianness.BigEndian).drop(4).take(4)
        val bits    = (0 until 4).map(dropped(_)).toList

        assertTrue(bits == List(false, false, false, false))
      }
    ),
    suite("BitChunkLong")(
      test("apply(0) after drop(1)") {
        // 0x8000000000000000L = MSB is 1, rest is 0
        // After drop(1), apply(0) should return bit 1 (which is 0), not bit 0 (which is 1)
        val dropped = Chunk(0x8000000000000000L).asBitsLong(Chunk.BitChunk.Endianness.BigEndian).drop(1)

        assertTrue(dropped(0) == false)
      },
      test("apply returns correct bits after drop(8)") {
        // 0xFF00000000000000L = first 8 bits are 1, rest is 0
        // After drop(8).take(8), we get bits 8-15: all 0s
        val dropped = Chunk(0xff00000000000000L).asBitsLong(Chunk.BitChunk.Endianness.BigEndian).drop(8).take(8)
        val bits    = (0 until 8).map(dropped(_)).toList

        assertTrue(bits == List.fill(8)(false))
      }
    )
  )
}
