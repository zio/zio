package zio

import zio.test.Assertion._
import zio.test._

object BitChunkByteSpec extends ZIOBaseSpec {

  val genByteChunk: Gen[Sized, Chunk[Byte]] =
    for {
      bytes <- Gen.listOf(Gen.byte)
    } yield Chunk.fromIterable(bytes)

  val genInt: Gen[Sized, Int] =
    Gen.small(Gen.const(_))

  val genBitChunk: Gen[Sized, Chunk.BitChunkByte] =
    for {
      chunk <- genByteChunk
      i     <- Gen.int(0, chunk.length * 8)
      j     <- Gen.int(0, chunk.length * 8)
    } yield Chunk.BitChunkByte(chunk, i min j, i max j)

  def toBinaryString(byte: Byte): String =
    String.format("%8s", (byte.toInt & 0xff).toBinaryString).replace(' ', '0')

  def spec = suite("BitChunkByteSpec")(
    test("drop") {
      check(genByteChunk, genInt) { (bytes, n) =>
        val actual   = bytes.asBitsByte.drop(n).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then drop") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBitsByte.drop(n).drop(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then take") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBitsByte.drop(n).take(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take") {
      check(genByteChunk, genInt) { (bytes, n) =>
        val actual   = bytes.asBitsByte.take(n).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.take(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then drop") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBitsByte.take(n).drop(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.take(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then take") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBitsByte.take(n).take(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.take(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("toBinaryString") {
      check(genByteChunk) { bytes =>
        val actual   = bytes.asBitsByte.toBinaryString
        val expected = bytes.map(toBinaryString).mkString
        assert(actual)(equalTo(expected))
      }
    },
    test("and") {
      check(genBitChunk, genBitChunk) { (l, r) =>
        val anded  = l and r
        val actual = anded.toBinaryString.take(anded.length)
        val expected =
          l.bytes
            .map(toBinaryString)
            .mkString
            .slice(l.minBitIndex, l.maxBitIndex)
            .zip(
              r.bytes.map(toBinaryString).mkString.slice(r.minBitIndex, r.maxBitIndex)
            )
            .map {
              case ('0', '0') => '0'
              case ('0', '1') => '0'
              case ('1', '0') => '0'
              case ('1', '1') => '1'
              case _          => ""
            }
            .mkString

        assert(actual)(equalTo(expected))
      }
    },
    test("or") {
      check(genBitChunk, genBitChunk) { (l, r) =>
        val ored   = l or r
        val actual = ored.toBinaryString.take(ored.length)
        val expected =
          l.bytes
            .map(toBinaryString)
            .mkString
            .slice(l.minBitIndex, l.maxBitIndex)
            .zip(
              r.bytes.map(toBinaryString).mkString.slice(r.minBitIndex, r.maxBitIndex)
            )
            .map {
              case ('0', '0') => '0'
              case ('0', '1') => '1'
              case ('1', '0') => '1'
              case ('1', '1') => '1'
              case _          => ""
            }
            .mkString

        assert(actual)(equalTo(expected))
      }
    },
    test("xor") {
      check(genBitChunk, genBitChunk) { (l, r) =>
        val xored  = l xor r
        val actual = xored.toBinaryString.take(xored.length)
        val expected =
          l.bytes
            .map(toBinaryString)
            .mkString
            .slice(l.minBitIndex, l.maxBitIndex)
            .zip(
              r.bytes.map(toBinaryString).mkString.slice(r.minBitIndex, r.maxBitIndex)
            )
            .map {
              case ('0', '0') => '0'
              case ('0', '1') => '1'
              case ('1', '0') => '1'
              case ('1', '1') => '0'
              case _          => ""
            }
            .mkString

        assert(actual)(equalTo(expected))
      }
    }
  )
}
