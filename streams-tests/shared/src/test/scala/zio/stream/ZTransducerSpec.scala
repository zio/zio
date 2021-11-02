package zio.stream

import zio._
import zio.test.Assertion._
import zio.test._

import java.nio.charset.StandardCharsets
import scala.io.Source

object ZTransducerSpec extends ZIOBaseSpec {
  import ZIOTag._

  val initErrorParser: ZTransducer[Any, String, Any, Nothing] = ZTransducer.fromZIO(IO.fail("Ouch"))

  def run[R, E, I, O](parser: ZTransducer[R, E, I, O], input: List[Chunk[I]]): ZIO[R, E, Chunk[O]] =
    ZStream.fromChunks(input: _*).transduce(parser).runCollect

  def spec = suite("ZTransducerSpec")(
    suite("Combinators")(
      suite("contramap")(
        test("happy path") {
          val parser = ZTransducer.identity[Int].contramap[String](_.toInt)
          assertM(run(parser, List(Chunk("1"))))(equalTo(Chunk(1)))
        },
        test("error") {
          val parser = initErrorParser.contramap[String](_.toInt)
          assertM(run(parser, List(Chunk("1"))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("contramapZIO")(
        test("happy path") {
          val parser = ZTransducer.identity[Int].contramapZIO[Any, Unit, String](s => UIO.succeed(s.toInt))
          assertM(run(parser, List(Chunk("1"))))(equalTo(Chunk(1)))
        },
        test("error") {
          val parser = initErrorParser.contramapZIO[Any, String, String](s => UIO.succeed(s.toInt))
          assertM(run(parser, List(Chunk("1"))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("filterInput")(
        test("happy path") {
          val filter = ZTransducer.identity[Int].filterInput[Int](_ > 2)
          assertM(run(filter, List(Chunk(1, 2, 3))))(equalTo(Chunk(3)))
        },
        test("error") {
          val parser = initErrorParser.filterInput[Int](_ > 2)
          assertM(run(parser, List(Chunk(1, 2, 3))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("filterInputZIO")(
        test("happy path") {
          val filter = ZTransducer.identity[Int].filterInputZIO[Any, String, Int](x => UIO.succeed(x > 2))
          assertM(run(filter, List(Chunk(1, 2, 3))))(equalTo(Chunk(3)))
        },
        test("error") {
          val parser = initErrorParser.filterInputZIO[Any, String, Int](x => UIO.succeed(x > 2))
          assertM(run(parser, List(Chunk(1, 2, 3))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("map")(
        test("happy path") {
          val parser = ZTransducer.identity[Int].map(_.toString)
          assertM(run(parser, List(Chunk(1))))(equalTo(Chunk("1")))
        },
        test("error") {
          val parser = initErrorParser.map(_.toString)
          assertM(run(parser, List(Chunk(1))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("mapError")(
        test("error") {
          val parser = initErrorParser.mapError(_ => "Error")
          assertM(run(parser, List(Chunk(1))).either)(isLeft(equalTo("Error")))
        }
      ) @@ zioTag(errors),
      suite("mapZIO")(
        test("happy path") {
          val parser = ZTransducer.identity[Int].mapZIO[Any, Unit, String](n => UIO.succeed(n.toString))
          assertM(run(parser, List(Chunk(1))))(equalTo(Chunk("1")))
        },
        test("error") {
          val parser = initErrorParser.mapZIO[Any, String, String](n => UIO.succeed(n.toString))
          assertM(run(parser, List(Chunk(1))).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      )
    ),
    suite("Constructors")(
      suite("collectAllN")(
        test("happy path") {
          assertM(run(ZTransducer.collectAllN[Int](3), List(Chunk(1, 2, 3, 4))))(
            equalTo(Chunk(Chunk(1, 2, 3), Chunk(4)))
          )
        },
        test("empty list") {
          assertM(run(ZTransducer.collectAllN[Int](3), List()))(equalTo(Chunk.empty))
        },
        test("doesn't emit empty trailing chunks") {
          assertM(run(ZTransducer.collectAllN[Int](3), List(Chunk(1, 2, 3))))(equalTo(Chunk(Chunk(1, 2, 3))))
        },
        test("emits chunks when exactly N elements received") {
          ZTransducer.collectAllN[Int](4).push.use { push =>
            push(Some(Chunk(1, 2, 3, 4))).map(result => assert(result)(equalTo(Chunk(Chunk(1, 2, 3, 4)))))
          }
        },
        test("IterableLike#grouped equivalence") {
          check(
            Gen
              .int(0, 10)
              .flatMap(Gen.listOfN(_)(Gen.small(Gen.chunkOfN(_)(Gen.int)))),
            Gen.small(Gen.const(_), 1)
          ) { case (chunks, groupingSize) =>
            for {
              transduced <- ZIO.foreach(chunks)(chunk => run(ZTransducer.collectAllN[Int](groupingSize), List(chunk)))
              regular     = chunks.map(chunk => Chunk.fromArray(chunk.grouped(groupingSize).toArray))
            } yield assert(transduced)(equalTo(regular))
          }
        }
      ),
      suite("collectAllToMapN")(
        test("stop collecting when map size exceeds limit")(
          assertM(
            run(
              ZTransducer.collectAllToMapN[Int, Int](2)(_ % 3)(_ + _),
              List(Chunk(0, 1, 2))
            )
          )(equalTo(Chunk(Map(0 -> 0, 1 -> 1), Map(2 -> 2))))
        ),
        test("keep collecting as long as map size does not exceed the limit")(
          assertM(
            run(
              ZTransducer.collectAllToMapN[Int, Int](3)(_ % 3)(_ + _),
              List(
                Chunk(0, 1, 2),
                Chunk(3, 4, 5),
                Chunk(6, 7, 8, 9)
              )
            )
          )(equalTo(Chunk(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15))))
        ),
        test("doesn't emit empty trailing chunks") {
          assertM(run(ZTransducer.collectAllToMapN[Int, Int](3)(identity[Int])(_ + _), List(Chunk(1, 2, 3))))(
            equalTo(Chunk(Map(1 -> 1, 2 -> 2, 3 -> 3)))
          )
        }
      ),
      suite("collectAllToSetN")(
        test("happy path")(
          assertM(
            run(ZTransducer.collectAllToSetN[Int](3), List(Chunk(1, 2, 1), Chunk(2, 3, 3, 4)))
          )(equalTo(Chunk(Set(1, 2, 3), Set(4))))
        ),
        test("doesn't emit empty trailing chunks") {
          assertM(run(ZTransducer.collectAllToSetN[Int](3), List(Chunk(1, 2, 3))))(equalTo(Chunk(Set(1, 2, 3))))
        }
      ),
      test("collectAllWhile") {
        val parser = ZTransducer.collectAllWhile[Int](_ < 5)
        val input  = List(Chunk(3, 4, 5, 6, 7, 2), Chunk.empty, Chunk(3, 4, 5, 6, 5, 4, 3, 2), Chunk.empty)
        val result = run(parser, input)
        assertM(result)(equalTo(Chunk(List(3, 4), List(2, 3, 4), List(4, 3, 2))))
      },
      test("collectAllWhileM") {
        val parser = ZTransducer.collectAllWhileZIO[Any, Nothing, Int](i => ZIO.succeed(i < 5))
        val input  = List(Chunk(3, 4, 5, 6, 7, 2), Chunk.empty, Chunk(3, 4, 5, 6, 5, 4, 3, 2), Chunk.empty)
        val result = run(parser, input)
        assertM(result)(equalTo(Chunk(List(3, 4), List(2, 3, 4), List(4, 3, 2))))
      },
      suite("fold")(
        test("empty")(
          assertM(
            ZStream.empty
              .aggregate(ZTransducer.fold[Int, Int](0)(_ => true)(_ + _))
              .runCollect
          )(equalTo(Chunk(0)))
        ),
        test("short circuits") {
          val empty: ZStream[Any, Nothing, Int]     = ZStream.empty
          val single: ZStream[Any, Nothing, Int]    = ZStream.succeed(1)
          val double: ZStream[Any, Nothing, Int]    = ZStream(1, 2)
          val failed: ZStream[Any, String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: ZStream[Any, E, Int]) =
            (for {
              effects <- Ref.make[List[Int]](Nil)
              exit <- stream
                        .aggregate(ZTransducer.foldZIO(0)(_ => true) { (_, a) =>
                          effects.update(a :: _) *> UIO.succeed(30)
                        })
                        .runCollect
              result <- effects.get
            } yield (exit, result)).exit

          (assertM(run(empty))(succeeds(equalTo((Chunk(0), Nil)))) <*>
            assertM(run(single))(succeeds(equalTo((Chunk(30), List(1))))) <*>
            assertM(run(double))(succeeds(equalTo((Chunk(30), List(2, 1))))) <*>
            assertM(run(failed))(fails(equalTo("Ouch")))).map { case (r1, r2, r3, r4) =>
            r1 && r2 && r3 && r4
          }
        }
      ),
      suite("foldZIO")(
        test("empty")(
          assertM(
            ZStream.empty
              .aggregate(
                ZTransducer.foldZIO(0)(_ => true)((x, y: Int) => ZIO.succeed(x + y))
              )
              .runCollect
          )(equalTo(Chunk(0)))
        ),
        test("short circuits") {
          val empty: ZStream[Any, Nothing, Int]     = ZStream.empty
          val single: ZStream[Any, Nothing, Int]    = ZStream.succeed(1)
          val double: ZStream[Any, Nothing, Int]    = ZStream(1, 2)
          val failed: ZStream[Any, String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: ZStream[Any, E, Int]) =
            (for {
              effects <- Ref.make[List[Int]](Nil)
              exit <- stream
                        .aggregate(ZTransducer.foldZIO(0)(_ => true) { (_, a) =>
                          effects.update(a :: _) *> UIO.succeed(30)
                        })
                        .runCollect
              result <- effects.get
            } yield exit -> result).exit

          (assertM(run(empty))(succeeds(equalTo((Chunk(0), Nil)))) <*>
            assertM(run(single))(succeeds(equalTo((Chunk(30), List(1))))) <*>
            assertM(run(double))(succeeds(equalTo((Chunk(30), List(2, 1))))) <*>
            assertM(run(failed))(fails(equalTo("Ouch")))).map { case (r1, r2, r3, r4) =>
            r1 && r2 && r3 && r4
          }
        }
      ),
      suite("foldWeighted/foldUntil")(
        test("foldWeighted")(
          assertM(
            ZStream[Long](1, 5, 2, 3)
              .aggregate(
                ZTransducer.foldWeighted(List[Long]())((_, x: Long) => x * 2, 12)((acc, el) => el :: acc).map(_.reverse)
              )
              .runCollect
          )(equalTo(Chunk(List(1L, 5L), List(2L, 3L))))
        ),
        suite("foldWeightedDecompose")(
          test("foldWeightedDecompose")(
            assertM(
              ZStream(1, 5, 1)
                .aggregate(
                  ZTransducer
                    .foldWeightedDecompose(List[Int]())(
                      (_, i: Int) => i.toLong,
                      4,
                      (i: Int) =>
                        if (i > 1) Chunk(i - 1, 1)
                        else Chunk(i)
                    )((acc, el) => el :: acc)
                    .map(_.reverse)
                )
                .runCollect
            )(equalTo(Chunk(List(1, 3), List(1, 1, 1))))
          ),
          test("empty")(
            assertM(
              ZStream.empty
                .aggregate(
                  ZTransducer.foldWeightedDecompose[Int, Int](0)((_, x) => x.toLong, 1000, Chunk.single(_))(_ + _)
                )
                .runCollect
            )(equalTo(Chunk(0)))
          )
        ),
        test("foldWeightedM")(
          assertM(
            ZStream[Long](1, 5, 2, 3)
              .aggregate(
                ZTransducer
                  .foldWeightedZIO(List.empty[Long])((_, a: Long) => UIO.succeedNow(a * 2), 12)((acc, el) =>
                    UIO.succeedNow(el :: acc)
                  )
                  .map(_.reverse)
              )
              .runCollect
          )(equalTo(Chunk(List(1L, 5L), List(2L, 3L))))
        ),
        suite("foldWeightedDecomposeM")(
          test("foldWeightedDecomposeM")(
            assertM(
              ZStream(1, 5, 1)
                .aggregate(
                  ZTransducer
                    .foldWeightedDecomposeZIO(List.empty[Int])(
                      (_, i: Int) => UIO.succeedNow(i.toLong),
                      4,
                      (i: Int) =>
                        UIO.succeedNow(
                          if (i > 1) Chunk(i - 1, 1)
                          else Chunk(i)
                        )
                    )((acc, el) => UIO.succeedNow(el :: acc))
                    .map(_.reverse)
                )
                .runCollect
            )(equalTo(Chunk(List(1, 3), List(1, 1, 1))))
          ),
          test("empty")(
            assertM(
              ZStream.empty
                .aggregate(
                  ZTransducer.foldWeightedDecomposeZIO[Any, Nothing, Int, Int](0)(
                    (_, x) => ZIO.succeed(x.toLong),
                    1000,
                    x => ZIO.succeed(Chunk.single(x))
                  )((x, y) => ZIO.succeed(x + y))
                )
                .runCollect
            )(equalTo(Chunk(0)))
          )
        ),
        test("foldUntil")(
          assertM(
            ZStream[Long](1, 1, 1, 1, 1, 1)
              .aggregate(ZTransducer.foldUntil(0L, 3)(_ + _))
              .runCollect
          )(equalTo(Chunk(3L, 3L)))
        ),
        test("foldUntilM")(
          assertM(
            ZStream[Long](1, 1, 1, 1, 1, 1)
              .aggregate(ZTransducer.foldUntilZIO(0L, 3)((s, a) => UIO.succeedNow(s + a)))
              .runCollect
          )(equalTo(Chunk(3L, 3L)))
        )
      ),
      test("dropWhile")(
        assertM(
          ZStream(1, 2, 3, 4, 5, 1, 2, 3, 4, 5)
            .aggregate(ZTransducer.dropWhile(_ < 3))
            .runCollect
        )(equalTo(Chunk(3, 4, 5, 1, 2, 3, 4, 5)))
      ),
      suite("dropWhileZIO")(
        test("happy path")(
          assertM(
            ZStream(1, 2, 3, 4, 5, 1, 2, 3, 4, 5)
              .aggregate(ZTransducer.dropWhileZIO(x => UIO(x < 3)))
              .runCollect
          )(equalTo(Chunk(3, 4, 5, 1, 2, 3, 4, 5)))
        )
        // test("error")(
        //   assertM {
        //     (ZStream(1,2,3) ++ ZStream.fail("Aie") ++ ZStream(5,1,2,3,4,5))
        //       .aggregate(ZTransducer.dropWhileZIO(x => UIO(x < 3)))
        //       .either
        //       .runCollect
        //   }(equalTo(Chunk(Right(3),Left("Aie"),Right(5),Right(1),Right(2),Right(3),Right(4),Right(5))))
        // )
      ),
      test("fromFunction")(
        assertM(
          ZStream(1, 2, 3, 4, 5)
            .aggregate(ZTransducer.fromFunction[Int, String](_.toString))
            .runCollect
        )(equalTo(Chunk("1", "2", "3", "4", "5")))
      ),
      test("fromFunctionZIO")(
        assertM(
          ZStream("1", "2", "3", "4", "5")
            .transduce(ZTransducer.fromFunctionZIO[Any, Throwable, String, Int](s => Task(s.toInt)))
            .runCollect
        )(equalTo(Chunk(1, 2, 3, 4, 5)))
      ),
      test("groupAdjacentBy")(
        assertM(
          ZStream((1, 1), (1, 2), (1, 3), (2, 1), (2, 2), (1, 4))
            .aggregate(ZTransducer.groupAdjacentBy(_._1))
            .runCollect
        )(
          equalTo(
            Chunk(
              (1, NonEmptyChunk((1, 1), (1, 2), (1, 3))),
              (2, NonEmptyChunk((2, 1), (2, 2))),
              (1, NonEmptyChunk((1, 4)))
            )
          )
        )
      ),
      suite("splitLines")(
        test("preserves data")(
          check(weirdStringGenForSplitLines) { lines =>
            val data = lines.mkString("\n")

            ZTransducer.splitLines.push.use { push =>
              for {
                result   <- push(Some(Chunk(data)))
                leftover <- push(None)
              } yield assert((result ++ leftover).toArray[String].mkString("\n"))(equalTo(lines.mkString("\n")))

            }
          }
        ),
        test("preserves data in chunks") {
          check(weirdStringGenForSplitLines) { xs =>
            val data = Chunk.fromIterable(xs.sliding(2, 2).toList.map(_.mkString("\n")))
            testSplitLines(Seq(data))
          }
        },
        test("handles leftovers") {
          testSplitLines(Seq(Chunk("abc\nbc")))
        },
        test("handles leftovers 2") {
          testSplitLines(Seq(Chunk("aa", "bb"), Chunk("\nbbc\n", "ddb", "bd"), Chunk("abc", "\n"), Chunk("abc")))
        },
        test("aggregates chunks") {
          testSplitLines(Seq(Chunk("abc", "\n", "bc", "\n", "bcd", "bcd")))
        },
        test("single newline edgecase") {
          testSplitLines(Seq(Chunk("\n")))
        },
        test("no newlines in data") {
          testSplitLines(Seq(Chunk("abc", "abc", "abc")))
        },
        test("\\r\\n on the boundary") {
          testSplitLines(Seq(Chunk("abc\r", "\nabc")))
        }
      ),
      suite("splitOn")(
        test("preserves data")(check(Gen.chunkOf(Gen.string.filter(!_.contains("|")).filter(_.nonEmpty))) { lines =>
          val data   = lines.mkString("|")
          val parser = ZTransducer.splitOn("|")
          assertM(run(parser, List(Chunk.single(data))))(equalTo(lines))
        }),
        test("handles leftovers") {
          val parser = ZTransducer.splitOn("\n")
          assertM(run(parser, List(Chunk("ab", "c\nb"), Chunk("c"))))(equalTo(Chunk("abc", "bc")))
        },
        test("aggregates") {
          assertM(
            Stream("abc", "delimiter", "bc", "delimiter", "bcd", "bcd")
              .aggregate(ZTransducer.splitOn("delimiter"))
              .runCollect
          )(equalTo(Chunk("abc", "bc", "bcdbcd")))
        },
        test("single newline edgecase") {
          assertM(
            Stream("test")
              .aggregate(ZTransducer.splitOn("test"))
              .runCollect
          )(equalTo(Chunk("")))
        },
        test("no delimiter in data") {
          assertM(
            Stream("abc", "abc", "abc")
              .aggregate(ZTransducer.splitOn("hello"))
              .runCollect
          )(equalTo(Chunk("abcabcabc")))
        },
        test("delimiter on the boundary") {
          assertM(
            Stream("abc<", ">abc")
              .aggregate(ZTransducer.splitOn("<>"))
              .runCollect
          )(equalTo(Chunk("abc", "abc")))
        }
      ),
      suite("splitOnChunk")(
        test("consecutive delimiter yields empty Chunk") {
          val input         = ZStream.apply(Chunk(1, 2), Chunk(1), Chunk(2, 1, 2, 3, 1, 2), Chunk(1, 2))
          val splitSequence = Chunk(1, 2)
          assertM(input.flattenChunks.transduce(ZTransducer.splitOnChunk(splitSequence)).map(_.size).runCollect)(
            equalTo(Chunk(0, 0, 0, 1, 0))
          )
        },
        test("preserves data")(check(Gen.chunkOf(Gen.byte.filter(_ != 0.toByte))) { bytes =>
          val splitSequence = Chunk[Byte](0, 1)
          val data          = bytes.flatMap(_ +: splitSequence)
          val parser        = ZTransducer.splitOnChunk(splitSequence)
          assertM(run(parser, List(data)).map(_.flatten))(equalTo(bytes))
        }),
        test("handles leftovers") {
          val splitSequence = Chunk(0, 1)
          val parser        = ZTransducer.splitOnChunk(splitSequence)
          assertM(run(parser, List(Chunk(1, 0, 2, 0, 1, 2), Chunk(2))))(equalTo(Chunk(Chunk(1, 0, 2), Chunk(2, 2))))
        },
        test("aggregates") {
          val splitSequence = Chunk(0, 1)
          assertM(
            Stream(1, 2, 0, 1, 3, 4, 0, 1, 5, 6, 5, 6)
              .aggregate(ZTransducer.splitOnChunk(splitSequence))
              .runCollect
          )(equalTo(Chunk(Chunk(1, 2), Chunk(3, 4), Chunk(5, 6, 5, 6))))
        },
        test("aggregates from Chunks") {
          val splitSequence = Chunk(0, 1)
          assertM(
            ZStream
              .fromChunks(Chunk(1, 2), splitSequence, Chunk(3, 4), splitSequence, Chunk(5, 6), Chunk(5, 6))
              .aggregate(ZTransducer.splitOnChunk(splitSequence))
              .runCollect
          )(equalTo(Chunk(Chunk(1, 2), Chunk(3, 4), Chunk(5, 6, 5, 6))))
        },
        test("single delimiter edgecase") {
          assertM(
            Stream(0)
              .aggregate(ZTransducer.splitOnChunk(Chunk(0)))
              .runCollect
          )(equalTo(Chunk(Chunk())))
        },
        test("no delimiter in data") {
          assertM(
            ZStream
              .fromChunks(Chunk(1, 2), Chunk(1, 2), Chunk(1, 2))
              .aggregate(ZTransducer.splitOnChunk(Chunk(1, 1)))
              .runCollect
          )(equalTo(Chunk(Chunk(1, 2, 1, 2, 1, 2))))
        },
        test("delimiter on the boundary") {
          assertM(
            ZStream
              .fromChunks(Chunk(1, 2), Chunk(1, 2))
              .aggregate(ZTransducer.splitOnChunk(Chunk(2, 1)))
              .runCollect
          )(equalTo(Chunk(Chunk(1), Chunk(2))))
        }
      ),
      suite("utf8DecodeChunk")(
        test("regular strings")(check(Gen.string) { s =>
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk.fromArray(s.getBytes("UTF-8"))))
              part2 <- push(None)
            } yield assert((part1 ++ part2).mkString)(equalTo(s))
          }
        }),
        test("incomplete chunk 1") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk(0xc2.toByte)))
              part2 <- push(Some(Chunk(0xa2.toByte)))
              part3 <- push(None)
            } yield assert((part1 ++ part2 ++ part3).mkString.getBytes("UTF-8"))(
              equalTo(Array(0xc2.toByte, 0xa2.toByte))
            )
          }
        },
        test("incomplete chunk 2") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk(0xe0.toByte, 0xa4.toByte)))
              part2 <- push(Some(Chunk(0xb9.toByte)))
              part3 <- push(None)
            } yield assert((part1 ++ part2 ++ part3).mkString.getBytes("UTF-8"))(
              equalTo(Array(0xe0.toByte, 0xa4.toByte, 0xb9.toByte))
            )
          }
        },
        test("incomplete chunk 3") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk(0xf0.toByte, 0x90.toByte, 0x8d.toByte)))
              part2 <- push(Some(Chunk(0x88.toByte)))
              part3 <- push(None)
            } yield assert((part1 ++ part2 ++ part3).mkString.getBytes("UTF-8"))(
              equalTo(Array(0xf0.toByte, 0x90.toByte, 0x8d.toByte, 0x88.toByte))
            )
          }
        },
        test("chunk with leftover") {
          ZTransducer.utf8Decode.push.use { push =>
            for {
              _     <- push(Some(Chunk(0xf0.toByte, 0x90.toByte, 0x8d.toByte, 0x88.toByte, 0xf0.toByte, 0x90.toByte)))
              part2 <- push(None)
            } yield assert(part2.mkString)(
              equalTo(new String(Array(0xf0.toByte, 0x90.toByte), "UTF-8"))
            )
          }
        },
        test("handle byte order mark") {
          check(Gen.string) { s =>
            ZTransducer.utf8Decode.push.use { push =>
              for {
                part1 <- push(Some(Chunk[Byte](-17, -69, -65) ++ Chunk.fromArray(s.getBytes("UTF-8"))))
                part2 <- push(None)
              } yield assert((part1 ++ part2).mkString)(equalTo(s))
            }
          }
        }
      ),
      suite("iso_8859_1")(
        test("ISO-8859-1 strings")(check(Gen.iso_8859_1) { s =>
          ZTransducer.iso_8859_1Decode.push.use { push =>
            for {
              part1 <- push(Some(Chunk.fromArray(s.getBytes(StandardCharsets.ISO_8859_1))))
              part2 <- push(None)
            } yield assert((part1 ++ part2).mkString)(equalTo(s))
          }
        })
      ),
      suite("branchAfter")(
        test("switches transducers") {
          check(Gen.chunkOf(Gen.int)) { data =>
            val test =
              ZStream
                .fromChunk(0 +: data)
                .transduce {
                  ZTransducer.branchAfter(1) { values =>
                    values.toList match {
                      case 0 :: Nil => ZTransducer.identity
                      case _        => ZTransducer.fail("boom")
                    }
                  }
                }
                .runCollect
            assertM(test.exit)(succeeds(equalTo(data)))
          }
        },
        test("finalizes transducers") {
          check(Gen.chunkOf(Gen.int)) { data =>
            val test =
              Ref.make(0).flatMap { ref =>
                ZStream
                  .fromChunk(data)
                  .transduce {
                    ZTransducer.branchAfter(1) { values =>
                      values.toList match {
                        case _ =>
                          ZTransducer {
                            Managed.acquireReleaseWith(
                              ref
                                .update(_ + 1)
                                .as[Option[Chunk[Int]] => UIO[Chunk[Int]]] {
                                  case None    => ZIO.succeedNow(Chunk.empty)
                                  case Some(c) => ZIO.succeedNow(c)
                                }
                            )(_ => ref.update(_ - 1))
                          }
                      }
                    }
                  }
                  .runDrain *> ref.get
              }
            assertM(test.exit)(succeeds(equalTo(0)))
          }
        },
        test("finalizes transducers - inner transducer fails") {
          check(Gen.chunkOf(Gen.int)) { data =>
            val test =
              Ref.make(0).flatMap { ref =>
                ZStream
                  .fromChunk(data)
                  .transduce {
                    ZTransducer.branchAfter(1) { values =>
                      values.toList match {
                        case _ =>
                          ZTransducer {
                            Managed.acquireReleaseWith(
                              ref
                                .update(_ + 1)
                                .as[Option[Chunk[Int]] => IO[String, Chunk[Int]]] { case _ =>
                                  ZIO.fail("boom")
                                }
                            )(_ => ref.update(_ - 1))
                          }
                      }
                    }
                  }
                  .runDrain
                  .ignore *> ref.get
              }
            assertM(test.exit)(succeeds(equalTo(0)))
          }
        },
        test("emits data if less than n are collected") {
          val gen =
            for {
              data <- Gen.chunkOf(Gen.int)
              n    <- Gen.int.filter(_ > data.length)
            } yield (data, n)

          check(gen) { case (data, n) =>
            val test =
              ZStream
                .fromChunk(data)
                .transduce {
                  ZTransducer.branchAfter(n)(ZTransducer.prepend)
                }
                .runCollect
            assertM(test.exit)(succeeds(equalTo(data)))
          }
        }
      ),
      suite("utf16BEDecode")(
        test("regular strings") {
          check(Gen.string) { s =>
            ZTransducer.utf16BEDecode.push.use { push =>
              for {
                part1 <- push(Some(Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16BE))))
                part2 <- push(None)
              } yield assert((part1 ++ part2).mkString)(equalTo(s))
            }
          }
        }
      ),
      suite("utf16FEDecode")(
        test("regular strings") {
          check(Gen.string) { s =>
            ZTransducer.utf16LEDecode.push.use { push =>
              for {
                part1 <- push(Some(Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16LE))))
                part2 <- push(None)
              } yield assert((part1 ++ part2).mkString)(equalTo(s))
            }
          }
        }
      ),
      suite("utf16Decode")(
        test("regular strings") {
          check(Gen.string) { s =>
            ZTransducer.utf16Decode.push.use { push =>
              for {
                part1 <- push(Some(Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16))))
                part2 <- push(None)
              } yield assert((part1 ++ part2).mkString)(equalTo(s))
            }
          }
        },
        test("no magic sequence - parse as big endian") {
          check(Gen.string.filter(_.nonEmpty)) { s =>
            ZTransducer.utf16Decode.push.use { push =>
              for {
                part1 <- push(Some(Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16BE))))
                part2 <- push(None)
              } yield assert((part1 ++ part2).mkString)(equalTo(s))
            }
          }
        },
        test("big endian") {
          check(Gen.string) { s =>
            ZTransducer.utf16Decode.push.use { push =>
              for {
                part1 <- push(Some(Chunk[Byte](-2, -1) ++ Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16BE))))
                part2 <- push(None)
              } yield assert((part1 ++ part2).mkString)(equalTo(s))
            }
          }
        },
        test("little endian") {
          check(Gen.string) { s =>
            ZTransducer.utf16Decode.push.use { push =>
              for {
                part1 <- push(Some(Chunk[Byte](-1, -2) ++ Chunk.fromArray(s.getBytes(StandardCharsets.UTF_16LE))))
                part2 <- push(None)
              } yield assert((part1 ++ part2).mkString)(equalTo(s))
            }
          }
        }
      ),
      suite("usASCII")(
        test("US-ASCII strings") {
          check(Gen.chunkOf(Gen.asciiString)) { chunk =>
            val s = chunk.mkString("")
            ZTransducer.usASCIIDecode.push.use { push =>
              for {
                part1 <- push(Some(Chunk.fromArray(s.getBytes(StandardCharsets.US_ASCII))))
                part2 <- push(None)
              } yield assert((part1 ++ part2).mkString)(equalTo(s))
            }
          }
        }
      )
    )
  )

  val weirdStringGenForSplitLines: Gen[Has[Random] with Has[Sized], Chunk[String]] = Gen
    .chunkOf(Gen.string(Gen.printableChar).map(_.filterNot(c => c == '\n' || c == '\r')))
    .map(l => if (l.nonEmpty && l.last == "") l ++ List("a") else l)

  def testSplitLines(input: Seq[Chunk[String]]): ZIO[Any, Nothing, TestResult] = {
    val str      = input.flatMap(_.mkString).mkString
    val expected = Chunk.fromIterable(Source.fromString(str).getLines().toList)
    ZStream.fromChunks(input: _*).transduce(ZTransducer.splitLines).runCollect.map { res =>
      assert(res)(equalTo(expected))
    }
  }
}
