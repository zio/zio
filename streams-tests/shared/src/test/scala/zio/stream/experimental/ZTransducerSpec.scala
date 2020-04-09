package zio.stream.experimental

import zio._
import zio.test._
import zio.test.Assertion._

object ZTransducerSpec extends ZIOBaseSpec {
  def spec = suite("ZTransducerSpec")(
    suite("Combinators")(),
    suite("Constructors")(
      suite("splitLines")(
        testM("preserves data")(
          checkM(weirdStringGenForSplitLines) { lines =>
            val data = lines.mkString("\n")

            ZTransducer.splitLines.push.use { push =>
              for {
                result <- push(Some(Chunk(data)))
                leftover <- push(None).catchAll {
                             case None    => ZIO.succeed(Chunk.empty)
                             case Some(e) => ZIO.fail(e)
                           }
              } yield assert((result ++ leftover).toArray[String].mkString("\n"))(equalTo(lines.mkString("\n")))

            }
          }
        ),
        testM("preserves data in chunks") {
          checkM(weirdStringGenForSplitLines) {
            xs =>
              val data = Chunk.fromIterable(xs.sliding(2, 2).toList.map(_.mkString("\n")))
              val ys   = xs.headOption.map(_ :: xs.drop(1).sliding(2, 2).toList.map(_.mkString)).getOrElse(Nil)

              ZTransducer.splitLines.push.use { push =>
                for {
                  result <- push(Some(data))
                  leftover <- push(None).catchAll {
                               case None    => ZIO.succeed(Chunk.empty)
                               case Some(e) => ZIO.fail(e)
                             }
                } yield assert((result ++ leftover).toArray[String].toList)(equalTo(ys))

              }
          }
        },
        testM("handles leftovers") {
          ZTransducer.splitLines.push.use { push =>
            for {
              result   <- push(Some(Chunk("abc\nbc")))
              leftover <- push(None)
            } yield assert(result.toArray[String].mkString("\n"))(equalTo("abc")) && assert(
              leftover.toArray[String].mkString
            )(equalTo("bc"))
          }
        },
        testM("aggregates chunks") {
          ZTransducer.splitLines.push.use { push =>
            for {
              part1 <- push(Some(Chunk("abc", "\n", "bc", "\n", "bcd", "bcd")))
              part2 <- push(None)
            } yield assert(part1 ++ part2)(equalTo(Chunk("abc", "bc", "bcdbcd")))
          }
        },
        testM("single newline edgecase") {
          ZTransducer.splitLines.push.use { push =>
            for {
              part1 <- push(Some(Chunk("\n")))
              part2 <- push(None).catchAll {
                        case None    => ZIO.succeed(Chunk.empty)
                        case Some(e) => ZIO.fail(e)
                      }
            } yield assert(part1 ++ part2)(equalTo(Chunk("")))
          }
        },
        testM("no newlines in data") {
          ZTransducer.splitLines.push.use { push =>
            for {
              part1 <- push(Some(Chunk("abc", "abc", "abc")))
              part2 <- push(None).catchAll {
                        case None    => ZIO.succeed(Chunk.empty)
                        case Some(e) => ZIO.fail(e)
                      }
            } yield assert(part1 ++ part2)(equalTo(Chunk("abcabcabc")))
          }
        },
        testM("\\r\\n on the boundary") {
          ZTransducer.splitLines.push.use { push =>
            for {
              part1 <- push(Some(Chunk("abc\r", "\nabc")))
              part2 <- push(None)
            } yield assert(part1 ++ part2)(equalTo(Chunk("abc", "abc")))
          }
        }
      ),
      suite("utf8DecodeChunk")(
        testM("regular strings")(checkM(Gen.anyString) { s =>
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk.fromArray(s.getBytes("UTF-8"))))
              part2 <- push(None).catchAll {
                        case None    => ZIO.succeed(Chunk.empty)
                        case Some(e) => ZIO.fail(e)
                      }
            } yield assert((part1 ++ part2).mkString)(equalTo(s))
          }
        }),
        testM("incomplete chunk 1") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk(0xC2.toByte)))
              part2 <- push(Some(Chunk(0xA2.toByte)))
              part3 <- push(None).catchAll {
                        case None    => ZIO.succeed(Chunk.empty)
                        case Some(e) => ZIO.fail(e)
                      }
            } yield assert((part1 ++ part2 ++ part3).mkString.getBytes("UTF-8"))(
              equalTo(Array(0xC2.toByte, 0xA2.toByte))
            )
          }
        },
        testM("incomplete chunk 2") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk(0xE0.toByte, 0xA4.toByte)))
              part2 <- push(Some(Chunk(0xB9.toByte)))
              part3 <- push(None).catchAll {
                        case None    => ZIO.succeed(Chunk.empty)
                        case Some(e) => ZIO.fail(e)
                      }
            } yield assert((part1 ++ part2 ++ part3).mkString.getBytes("UTF-8"))(
              equalTo(Array(0xE0.toByte, 0xA4.toByte, 0xB9.toByte))
            )
          }
        },
        testM("incomplete chunk 3") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte)))
              part2 <- push(Some(Chunk(0x88.toByte)))
              part3 <- push(None).catchAll {
                        case None    => ZIO.succeed(Chunk.empty)
                        case Some(e) => ZIO.fail(e)
                      }
            } yield assert((part1 ++ part2 ++ part3).mkString.getBytes("UTF-8"))(
              equalTo(Array(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte))
            )
          }
        },
        testM("chunk with leftover") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              _ <- push(Some(Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte, 0xF0.toByte, 0x90.toByte)))
              part2 <- push(None).catchAll {
                        case None    => ZIO.succeed(Chunk.empty)
                        case Some(e) => ZIO.fail(e)
                      }
            } yield assert(part2.mkString)(
              equalTo(new String(Array(0xF0.toByte, 0x90.toByte), "UTF-8"))
            )
          }
        }
      )
    )
  )

  val weirdStringGenForSplitLines = Gen
    .listOf(Gen.string(Gen.printableChar).map(_.filterNot(c => c == '\n' || c == '\r')))
    .map(l => if (l.nonEmpty && l.last == "") l ++ List("a") else l)
}
