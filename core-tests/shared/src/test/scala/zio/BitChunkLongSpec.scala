package zio

import zio.test.Assertion._
import zio.test._

object BitChunkLongSpec extends ZIOBaseSpec {

  val genLongChunk: Gen[Any, Chunk[Long]] =
    for {
      longs <- Gen.listOf(Gen.long)
    } yield Chunk.fromIterable(longs)

  val genInt: Gen[Any, Int] =
    Gen.small(Gen.const(_))

  val genEndianness: Gen[Any, Chunk.BitChunk.Endianness] =
    Gen.elements(Chunk.BitChunk.Endianness.BigEndian, Chunk.BitChunk.Endianness.LittleEndian)

  def toBinaryString(endianness: Chunk.BitChunk.Endianness)(long: Long): String = {
    val endiannessLong =
      if (endianness == Chunk.BitChunk.Endianness.BigEndian) long else java.lang.Long.reverse(long)
    String.format("%64s", endiannessLong.toBinaryString).replace(' ', '0')
  }

  def spec = suite("BitChunkLongSpec")(
    test("drop") {
      check(genLongChunk, genInt, genEndianness) { (longs, n, endianness) =>
        val actual   = longs.asBitsLong(endianness).drop(n).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.drop(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then drop") {
      check(genLongChunk, genInt, genInt, genEndianness) { (longs, n, m, endianness) =>
        val actual   = longs.asBitsLong(endianness).drop(n).drop(m).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.drop(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then take") {
      check(genLongChunk, genInt, genInt, genEndianness) { (longs, n, m, endianness) =>
        val actual   = longs.asBitsLong(endianness).drop(n).take(m).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.drop(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take") {
      check(genLongChunk, genInt, genEndianness) { (longs, n, endianness) =>
        val actual   = longs.asBitsLong(endianness).take(n).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.take(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then drop") {
      check(genLongChunk, genInt, genInt, genEndianness) { (longs, n, m, endianness) =>
        val actual   = longs.asBitsLong(endianness).take(n).drop(m).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.take(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then take") {
      check(genLongChunk, genInt, genInt, genEndianness) { (longs, n, m, endianness) =>
        val actual   = longs.asBitsLong(endianness).take(n).take(m).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.take(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("slice") {
      check(genLongChunk, genInt, genInt, genEndianness) { (bytes, n, m, endianness) =>
        val actual   = bytes.asBitsLong(endianness).slice(n, m).toBinaryString
        val expected = bytes.map(toBinaryString(endianness)).mkString.slice(n, m)
        assert(actual)(equalTo(expected))
      }
    },
    test("toBinaryString") {
      check(genLongChunk, genEndianness) { (longs, endianness) =>
        val actual   = longs.asBitsLong(endianness).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString
        assert(actual)(equalTo(expected))
      }
    }
  )

}
