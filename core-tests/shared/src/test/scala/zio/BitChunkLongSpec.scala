package zio

import zio.random.Random
import zio.test.Assertion.equalTo
import zio.test._

object BitChunkLongSpec extends ZIOBaseSpec {

  val genLongChunk: Gen[Random with Sized, Chunk[Long]] =
    for {
      longs <- Gen.listOf(Gen.anyLong)
    } yield Chunk.fromIterable(longs)

  val genInt: Gen[Random with Sized, Int] =
    Gen.small(Gen.const(_))

  val genEndianness: Gen[Random with Sized, Chunk.Endianness] =
    Gen.elements(Chunk.Endianness.BigEndian, Chunk.Endianness.LittleEndian)

  def toBinaryString(endianness: Chunk.Endianness)(long: Long): String = {
    val endiannessLong =
      if (endianness == Chunk.Endianness.BigEndian) long else java.lang.Long.reverse(long)
    String.format("%64s", endiannessLong.toBinaryString).replace(' ', '0')
  }

  def spec: ZSpec[Environment, Failure] = suite("BitChunkLongSpec")(
    testM("drop") {
      check(genLongChunk, genInt, genEndianness) { (longs, n, endianness) =>
        val actual   = longs.asBitsLong(endianness).drop(n).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.drop(n)
        assert(actual)(equalTo(expected))
      }
    },
    testM("drop and then drop") {
      check(genLongChunk, genInt, genInt, genEndianness) { (longs, n, m, endianness) =>
        val actual   = longs.asBitsLong(endianness).drop(n).drop(m).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.drop(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("drop and then take") {
      check(genLongChunk, genInt, genInt, genEndianness) { (longs, n, m, endianness) =>
        val actual   = longs.asBitsLong(endianness).drop(n).take(m).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.drop(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("take") {
      check(genLongChunk, genInt, genEndianness) { (longs, n, endianness) =>
        val actual   = longs.asBitsLong(endianness).take(n).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.take(n)
        assert(actual)(equalTo(expected))
      }
    },
    testM("take and then drop") {
      check(genLongChunk, genInt, genInt, genEndianness) { (longs, n, m, endianness) =>
        val actual   = longs.asBitsLong(endianness).take(n).drop(m).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.take(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("take and then take") {
      check(genLongChunk, genInt, genInt, genEndianness) { (longs, n, m, endianness) =>
        val actual   = longs.asBitsLong(endianness).take(n).take(m).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString.take(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("toBinaryString") {
      check(genLongChunk, genEndianness) { (longs, endianness) =>
        val actual   = longs.asBitsLong(endianness).toBinaryString
        val expected = longs.map(toBinaryString(endianness)).mkString
        assert(actual)(equalTo(expected))
      }
    }
  )

}
