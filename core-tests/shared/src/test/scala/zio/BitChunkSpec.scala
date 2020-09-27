package zio

import zio.random.Random
import zio.test.Assertion._
import zio.test._

object BitChunkSpec extends ZIOBaseSpec {

  val genByteChunk: Gen[Random with Sized, Chunk[Byte]] =
    for {
      bytes <- Gen.listOf(Gen.anyByte)
    } yield Chunk.fromIterable(bytes)

  val genInt: Gen[Random with Sized, Int] =
    Gen.small(Gen.const(_))

  def toBinaryString(byte: Byte): String =
    String.format("%8s", (byte.toInt & 0xff).toBinaryString).replace(' ', '0')

  def spec: ZSpec[Environment, Failure] = suite("BitChunkSpec")(
    testM("drop") {
      check(genByteChunk, genInt) { (bytes, n) =>
        val actual   = bytes.asBits.drop(n).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n)
        assert(actual)(equalTo(expected))
      }
    },
    testM("drop and then drop") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBits.drop(n).drop(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("drop and then take") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBits.drop(n).take(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("take") {
      check(genByteChunk, genInt) { (bytes, n) =>
        val actual   = bytes.asBits.take(n).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.take(n)
        assert(actual)(equalTo(expected))
      }
    },
    testM("take and then drop") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBits.take(n).drop(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.take(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("take and then take") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBits.take(n).take(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.take(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    testM("toBinaryString") {
      check(genByteChunk) { bytes =>
        val actual   = bytes.asBits.toBinaryString
        val expected = bytes.map(toBinaryString).mkString
        assert(actual)(equalTo(expected))
      }
    }
  )
}
