package zio

import zio.random.Random
import zio.test.Assertion._
import zio.test.TestAspect.samples
import zio.test._

object BitChunkSpec extends ZIOBaseSpec {

  val genByteChunk: Gen[Random with Sized, Chunk[Byte]] =
    for {
      bytes <- Gen.listOf(Gen.anyByte)
    } yield Chunk.fromIterable(bytes)

  def genInefficientBooleanChunk(multipleOf: Int = 1): Gen[Random with Sized, Chunk[Boolean]] =
    Gen.listOf(Gen.listOfN(multipleOf)(Gen.boolean)).map(bits => Chunk.fromIterable(bits.flatten))

  val genInt: Gen[Random with Sized, Int] =
    Gen.small(Gen.const(_))

  def toBinaryString(byte: Byte): String =
    String.format("%8s", (byte.toInt & 0xff).toBinaryString).replace(' ', '0')

  def byteChunkFromBitChunk(chunk: Chunk[Boolean]): Chunk[Byte] =
    Chunk.fromIterator(
      chunk.iterator.grouped(8).map(g => (g.foldLeft(0)((i, b) => (i << 1) | (if (b) 1 else 0)) << (8 - g.size)).toByte)
    )

  def bytesFromBits(bits: Boolean*): Chunk[Byte] =
    byteChunkFromBitChunk(Chunk.fromIterable(bits))

  def compressed(chunk: Chunk[Boolean]): Chunk[Boolean] = byteChunkFromBitChunk(chunk).asBits

  sealed trait BitwiseOperator {
    def apply(left: Chunk[Boolean], right: Chunk[Boolean]): Chunk[Boolean] = this match {
      case BitwiseOperator.AND => left & right
      case BitwiseOperator.OR  => left | right
      case BitwiseOperator.XOR => left ^ right
    }
  }
  object BitwiseOperator {
    case object AND extends BitwiseOperator
    case object OR  extends BitwiseOperator
    case object XOR extends BitwiseOperator
  }
  val genBitwiseOperator = Gen.elements(BitwiseOperator.AND, BitwiseOperator.OR, BitwiseOperator.XOR)

  def spec: ZSpec[Environment, Failure] = suite("BitChunkSpec")(
    testM("drop") {
      check(genByteChunk, genInt) { (bytes, n) =>
        val actual   = bytes.asBits.drop(n).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n)
        assert(actual)(equalTo(expected))
      }
    },
    testM("drop then apply") {
      check(genByteChunk.filterNot(_.isEmpty)) { bytes =>
        val bitChunk = bytes.asBits
        val dropped1 = bitChunk.drop(1)
        assert(dropped1(0))(equalTo(bitChunk(1)))
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
        val l = bytesFromBits(true, false, true, false, true, false, true, false, true).asBits
        val r = bytesFromBits(true, false, false, false, true, true, true, false, true).asBits
        val e = bytesFromBits(true, false, false, false, true, false, true, false, true).asBits
        assert(l & r)(equalTo(e))
      },
      test("& sliced") {
        val l        = bytesFromBits(true, false, true, false, true, false, true, false, true).asBits
        val r        = bytesFromBits(true, false, false, false, true, true, true, false, true).asBits
        val actual   = (l.drop(2).take(4) & r.drop(2).take(5)).toBinaryString
        val expected = "00100"
        assert(actual)(equalTo(expected))
      },
      testM("&") {
        check(genInefficientBooleanChunk(8), genInefficientBooleanChunk(8)) { case (leftBits, rightBits) =>
          val actual   = (compressed(leftBits) & compressed(rightBits)).toBinaryString
          val expected = (leftBits & rightBits).toBinaryString
          assert(actual)(equalTo(expected))
        }
      },
      test("| simple") {
        val l = bytesFromBits(true, false, true, false, true, false, true, false, true).asBits
        val r = bytesFromBits(true, false, false, false, true, true, true, false, true).asBits
        val e = bytesFromBits(true, false, true, false, true, true, true, false, true).asBits
        assert(l | r)(equalTo(e))
      },
      test("| sliced") {
        val l        = bytesFromBits(true, false, true, false, true, false, true, false, true).asBits
        val r        = bytesFromBits(true, false, false, false, true, true, true, false, true).asBits
        val actual   = (l.drop(2).take(4) | r.drop(2).take(5)).toBinaryString
        val expected = "10111"
        assert(actual)(equalTo(expected))
      },
      testM("|") {
        check(genInefficientBooleanChunk(8), genInefficientBooleanChunk(8)) { case (leftBits, rightBits) =>
          val actual   = (compressed(leftBits) | compressed(rightBits)).toBinaryString
          val expected = (leftBits | rightBits).toBinaryString
          assert(actual)(equalTo(expected))
        }
      },
      test("^ simple") {
        val l = bytesFromBits(true, false, true, false, true, false, true, false, true).asBits
        val r = bytesFromBits(true, false, false, false, true, true, true, false, true).asBits
        val e = bytesFromBits(false, false, true, false, false, true, false, false, false).asBits
        assert(l ^ r)(equalTo(e))
      },
      test("^ sliced") {
        val l        = bytesFromBits(true, false, true, false, true, false, true, false, true).asBits
        val r        = bytesFromBits(true, false, false, false, true, true, true, false, true).asBits
        val actual   = (l.drop(2).take(4) ^ r.drop(2).take(5)).toBinaryString
        val expected = "10011"
        assert(actual)(equalTo(expected))
      },
      testM("^") {
        check(genInefficientBooleanChunk(8), genInefficientBooleanChunk(8)) { case (leftBits, rightBits) =>
          val actual   = (compressed(leftBits) ^ compressed(rightBits)).toBinaryString
          val expected = (leftBits ^ rightBits).toBinaryString
          assert(actual)(equalTo(expected))
        }
      },
      test("~ simple") {
        val bits = bytesFromBits(true, false, true, false, true, true, true, false, true).asBits
        val e = bytesFromBits(false, true, false, true, false, false, false, true, false, true, true, true, true, true,
          true, true).asBits
        assert(~bits)(equalTo(e))
      },
      test("~ sliced") {
        val bits   = bytesFromBits(true, false, true, false, true, true, true, false, true).asBits
        val actual = (~(bits.drop(3).take(3))).toBinaryString
        assert(actual)(equalTo("100"))
      },
      testM("~") {
        check(genByteChunk) { bytes =>
          val actual   = (~bytes.asBits).toBinaryString
          val expected = bytes.map(b => (~b).toByte).map(toBinaryString).mkString
          assert(actual)(equalTo(expected))
        }
      },
      test("~ sliced then |") {
        val bits   = bytesFromBits(true, false, true, false, true, true, true, false, true).asBits
        val orBits = bytesFromBits(true, false, false, true, false, true, false, false, false).asBits
        val inv    = ~(bits.drop(3).take(3))
        val or     = orBits.drop(3).take(6)
        val actual = (inv | or).toBinaryString
        assert(actual)(equalTo("101000"))
      },
      testM("compressed bit chunk composes with non-compressed") {
        checkM(genBitwiseOperator, genInefficientBooleanChunk(8), genInefficientBooleanChunk(8)) {
          case (bitwiseOp, leftBits, rightBits) =>
            val expected = bitwiseOp(leftBits, rightBits).toBinaryString
            check(Gen.elements(compressed(leftBits) -> rightBits, leftBits -> compressed(rightBits))) {
              case (left, right) =>
                val actual = bitwiseOp(left, right).toBinaryString
                assert(actual)(equalTo(expected))
            }
        }
      } @@ samples(20),
      testM("works the same when sliced") {
        val gen = for {
          operator      <- genBitwiseOperator
          leftBits      <- genInefficientBooleanChunk(8)
          rightBits     <- genInefficientBooleanChunk(8)
          leftBitsTake  <- Gen.int(0, leftBits.size)
          leftBitsDrop  <- Gen.int(0, leftBits.size - leftBitsTake)
          rightBitsTake <- Gen.int(0, rightBits.size)
          rightBitsDrop <- Gen.int(0, rightBits.size - rightBitsTake)
        } yield (operator, leftBits, leftBitsTake, leftBitsDrop, rightBits, rightBitsTake, rightBitsDrop)
        check(gen) { case (bitwiseOp, leftBits, leftBitsTake, leftBitsDrop, rightBits, rightBitsTake, rightBitsDrop) =>
          val expected = bitwiseOp(
            leftBits.take(leftBitsTake).drop(leftBitsDrop),
            rightBits.take(rightBitsTake).drop(rightBitsDrop)
          ).toBinaryString
          val left  = compressed(leftBits)
          val right = compressed(rightBits)
          val actual = bitwiseOp(
            left.take(leftBitsTake).drop(leftBitsDrop),
            right.take(rightBitsTake).drop(rightBitsDrop)
          ).toBinaryString
          assert(actual)(equalTo(expected))
        }
      } @@ samples(20)
    )
  )
}
