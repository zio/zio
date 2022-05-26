package zio

import zio.random.Random
import zio.test.Assertion.equalTo
import zio.test._

object BitChunkIntSpec extends ZIOBaseSpec {

  val genIntChunk: Gen[Random with Sized, Chunk[Int]] =
    for {
      ints <- Gen.listOf(Gen.anyInt)
    } yield Chunk.fromIterable(ints)

  val genInt: Gen[Random with Sized, Int] =
    Gen.small(Gen.const(_))

  val genEndianness: Gen[Random with Sized, Chunk.Endianness] =
    Gen.elements(Chunk.Endianness.BigEndian, Chunk.Endianness.LittleEndian)

  def toBinaryString(endianness: Chunk.Endianness)(int: Int): String = {
    val endiannessLong =
      if (endianness == Chunk.Endianness.BigEndian) int else java.lang.Integer.reverse(int)
    String.format("%32s", endiannessLong.toBinaryString).replace(' ', '0')
  }

  def spec: ZSpec[Environment, Failure] = suite("BitChunkIntSpec")(
    testM("drop") {
      check(genIntChunk, genInt, genEndianness) { (ints, n, endianness) =>
        val actual   = ints.asBitsInt(endianness).drop(n).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.drop(n)
        assert(actual)(equalTo(expected))
      }
    },
    testM("drop and then drop") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).drop(n).drop(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.drop(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("drop and then take") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).drop(n).take(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.drop(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("take") {
      check(genIntChunk, genInt, genEndianness) { (ints, n, endianness) =>
        val actual   = ints.asBitsInt(endianness).take(n).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.take(n)
        assert(actual)(equalTo(expected))
      }
    },
    testM("take and then drop") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).take(n).drop(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.take(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("take and then take") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).take(n).take(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.take(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("toBinaryString") {
      check(genIntChunk, genEndianness) { (ints, endianness) =>
        val actual   = ints.asBitsInt(endianness).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString
        assert(actual)(equalTo(expected))
      }
    }
  )

}
