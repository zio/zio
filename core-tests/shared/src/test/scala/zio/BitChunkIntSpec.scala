package zio

import zio.test.Assertion._
import zio.test._

object BitChunkIntSpec extends ZIOBaseSpec {

  val genIntChunk: Gen[Any, Chunk[Int]] =
    for {
      ints <- Gen.listOf(Gen.int)
    } yield Chunk.fromIterable(ints)

  val genInt: Gen[Any, Int] =
    Gen.small(Gen.const(_))

  val genEndianness: Gen[Any, Chunk.BitChunk.Endianness] =
    Gen.elements(Chunk.BitChunk.Endianness.BigEndian, Chunk.BitChunk.Endianness.LittleEndian)

  def toBinaryString(endianness: Chunk.BitChunk.Endianness)(int: Int): String = {
    val endiannessLong =
      if (endianness == Chunk.BitChunk.Endianness.BigEndian) int else java.lang.Integer.reverse(int)
    String.format("%32s", endiannessLong.toBinaryString).replace(' ', '0')
  }

  def spec = suite("BitChunkIntSpec")(
    test("drop") {
      check(genIntChunk, genInt, genEndianness) { (ints, n, endianness) =>
        val actual   = ints.asBitsInt(endianness).drop(n).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.drop(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then drop") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).drop(n).drop(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.drop(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then take") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).drop(n).take(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.drop(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take") {
      check(genIntChunk, genInt, genEndianness) { (ints, n, endianness) =>
        val actual   = ints.asBitsInt(endianness).take(n).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.take(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then drop") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).take(n).drop(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.take(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then take") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).take(n).take(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.take(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("slice") {
      check(genIntChunk, genInt, genInt, genEndianness) { (bytes, n, m, endianness) =>
        val actual   = bytes.asBitsInt(endianness).slice(n, m).toBinaryString
        val expected = bytes.map(toBinaryString(endianness)).mkString.slice(n, m)
        assert(actual)(equalTo(expected))
      }
    },
    test("toBinaryString") {
      check(genIntChunk, genEndianness) { (ints, endianness) =>
        val actual   = ints.asBitsInt(endianness).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString
        assert(actual)(equalTo(expected))
      }
    }
  ) @@ TestAspect.exceptNative

}
