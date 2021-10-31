package zio

import zio.random.Random
import zio.test.Assertion._
import zio.test._

object BitChunkSpec extends ZIOBaseSpec {

  val genByteChunk: Gen[Random with Sized, Chunk[Byte]] =
    for {
      bytes <- Gen.listOf(Gen.anyByte)
    } yield Chunk.fromIterable(bytes)

  def genInefficientBooleanChunk(multipleOf: Int = 1): Gen[Random with Sized, Chunk[Boolean]] = Gen.listOf(Gen.listOfN(multipleOf)(Gen.boolean)).map(bits => Chunk.fromIterable(bits.flatten))

  val genInt: Gen[Random with Sized, Int] =
    Gen.small(Gen.const(_))

  def toBinaryString(byte: Byte): String =
    String.format("%8s", (byte.toInt & 0xff).toBinaryString).replace(' ', '0')

  def byteChunkFromBitChunk(chunk: Chunk[Boolean]): Chunk[Byte] =
    Chunk.fromIterator(chunk.iterator.grouped(8).map(_.foldLeft(0)((i, b) => (i << 1) | (if (b) 1 else 0)).toByte))

  def bytesFromBigEndianBits(bits: Boolean*): Chunk[Byte] =
    Chunk.fromIterator(bits.reverse.grouped(8).map(_.foldRight(0)((b, i) => (i << 1) | (if (b) 1 else 0)).toByte))

  def compressed(chunk: Chunk[Boolean]): Chunk[Boolean] = byteChunkFromBitChunk(chunk).asBits

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
    },
    suite("bitwise operations")(
      test("& simple") {
        val l = bytesFromBigEndianBits(true, false, true, false, true, false, true, false, true).asBits
        val r = bytesFromBigEndianBits(true, false, false, false, true, true, true, false, true).asBits
        val e = bytesFromBigEndianBits(true, false, false, false, true, false, true, false, true).asBits
        assert(l & r)(equalTo(e))
      },
      testM("&") {
        check(genInefficientBooleanChunk(8), genInefficientBooleanChunk(8)) { case (leftBits, rightBits) =>
          val actual = (compressed(leftBits) & compressed(rightBits)).toBinaryString
          val expected = (leftBits & rightBits).toBinaryString
          assert(actual)(equalTo(expected))
        }
      },
      test("| simple") {
        val l = bytesFromBigEndianBits(true, false, true, false, true, false, true, false, true).asBits
        val r = bytesFromBigEndianBits(true, false, false, false, true, true, true, false, true).asBits
        val e = bytesFromBigEndianBits(true, false, true, false, true, true, true, false, true).asBits
        assert(l | r)(equalTo(e))
      },
      testM("|") {
        check(genInefficientBooleanChunk(8), genInefficientBooleanChunk(8)) { case (leftBits, rightBits) =>
          val actual = (compressed(leftBits) | compressed(rightBits)).toBinaryString
          val expected = (leftBits | rightBits).toBinaryString
          assert(actual)(equalTo(expected))
        }
      },
      test("^ simple") {
        val l = bytesFromBigEndianBits(true, false, true, false, true, false, true, false, true).asBits
        val r = bytesFromBigEndianBits(true, false, false, false, true, true, true, false, true).asBits
        val e = bytesFromBigEndianBits(false, false, true, false, false, true, false, false, false).asBits
        assert(l ^ r)(equalTo(e))
      },
      testM("^") {
        check(genInefficientBooleanChunk(8), genInefficientBooleanChunk(8)) { case (leftBits, rightBits) =>
          val actual = (compressed(leftBits) ^ compressed(rightBits)).toBinaryString
          val expected = (leftBits ^ rightBits).toBinaryString
          assert(actual)(equalTo(expected))
        }
      },
      test("~ simple") {
        val bits = bytesFromBigEndianBits(true, false, true, false, true, true, true, false, true).asBits
        val e = bytesFromBigEndianBits(true, true, true, true, true, true, true,
          false, true, false, true, false, false, false, true, false).asBits
        assert(~bits)(equalTo(e))
      },
      testM("~") {
        check(genByteChunk) { bytes =>
          val actual = (~bytes.asBits).toBinaryString
          val expected = bytes.map(b => (~b).toByte).map(toBinaryString).mkString
          assert(actual)(equalTo(expected))
        }
      },
      testM("compressed bit chunk composes with non-compressed") {
        val genOperator = Gen.elements(
          (left: Chunk[Boolean], right: Chunk[Boolean]) => left & right,
          (left: Chunk[Boolean], right: Chunk[Boolean]) => left | right,
          (left: Chunk[Boolean], right: Chunk[Boolean]) => left ^ right,
        )
        checkM(genOperator, genInefficientBooleanChunk(8), genInefficientBooleanChunk(8)) { case (bitwiseOp, leftBits, rightBits) =>
          val expected = bitwiseOp(leftBits, rightBits).toBinaryString
          check(Gen.elements(compressed(leftBits) -> rightBits, leftBits -> compressed(rightBits))) { case (left, right) =>
            val actual = bitwiseOp(left, right).toBinaryString
            assert(actual)(equalTo(expected))
          }
        }
      }
    )
  )
}
