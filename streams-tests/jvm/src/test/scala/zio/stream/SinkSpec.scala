package zio.stream

import java.util.concurrent.TimeUnit

import scala.{ Stream => _ }

import SinkUtils._

import zio._
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion.{ anything, equalTo, fails, isFalse, isLeft, isSome, isSubtype, isTrue, succeeds }
import zio.test._
import zio.test.environment.TestClock

object SinkSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("SinkSpec")(
    suite("Combinators")(
      suite("as")(
        testM("happy path") {
          val sink = ZSink.identity[Int].as("const")
          assertM(sinkIteration(sink, 1))(equalTo(("const", Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.as("const")
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.as("const")
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.as("const")
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("chunked")(
        testM("happy path") {
          val sink = ZSink.collectAll[Int].chunked
          assertM(sinkIteration(sink, Chunk(1, 2, 3, 4, 5)))(equalTo((List(1, 2, 3, 4, 5), Chunk.empty)))
        },
        testM("empty") {
          val sink = ZSink.collectAll[Int].chunked
          assertM(sinkIteration(sink, Chunk.empty))(equalTo(Nil -> Chunk.empty))
        },
        testM("init error") {
          val sink = initErrorSink.chunked
          assertM(sinkIteration(sink, Chunk.single(1)).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.chunked
          assertM(sinkIteration(sink, Chunk.single(1)).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.chunked
          assertM(sinkIteration(sink, Chunk.single(1)).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("leftover") {
          val sink = ZSink.collectAllN[Int](2).chunked
          for {
            init           <- sink.initial
            step           <- sink.step(init, Chunk(1, 2, 3, 4, 5))
            result         <- sink.extract(step)
            (b, leftovers) = result
          } yield assert(b)(equalTo(List(1, 2))) && assert(leftovers)(equalTo(Chunk(3, 4, 5)))
        },
        testM("leftover append") {
          val sink = ZSink.ignoreWhile[Int](_ < 0).chunked
          for {
            init   <- sink.initial
            step   <- sink.step(init, Chunk(1, 2, 3, 4, 5))
            result <- sink.extract(step)
          } yield assert(result._2)(equalTo(Chunk(1, 2, 3, 4, 5)))
        }
      ),
      suite("collectAll")(
        testM("happy path") {
          val sink = ZSink.identity[Int].collectAll
          assertM(sinkIteration(sink, 1))(equalTo((List(1), Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.collectAll
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.collectAll
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.collectAll
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("interaction with succeed") {
          val sink = ZSink.succeed[Int, Int](5).collectAll
          for {
            init <- sink.initial
            s <- sink
                  .step(init, 1)
                  .flatMap(sink.step(_, 2))
                  .flatMap(sink.step(_, 3))
            result <- sink.extract(s)
          } yield assert(result)(equalTo(List(5, 5, 5, 5) -> Chunk(1, 2, 3)))
        },
        testM("interaction with ignoreWhile") {
          val sink = ZSink.ignoreWhile[Int](_ < 5).collectAll
          for {
            result <- sink.initial
                       .flatMap(sink.step(_, 1))
                       .flatMap(sink.step(_, 2))
                       .flatMap(sink.step(_, 3))
                       .flatMap(sink.step(_, 5))
                       .flatMap(sink.step(_, 6))
                       .flatMap(sink.extract)
          } yield assert(result)(equalTo(List((), ()) -> Chunk(5, 6)))
        }
      ),
      suite("collectAllN")(
        testM("happy path") {
          val sink = ZSink.identity[Int].collectAllN(3)
          for {
            result <- sink.initial
                       .flatMap(sink.step(_, 1))
                       .flatMap(sink.step(_, 2))
                       .flatMap(sink.step(_, 3))
                       .flatMap(sink.step(_, 4))
                       .flatMap(sink.extract)
          } yield assert(result)(equalTo(List(1, 2, 3) -> Chunk(4)))
        },
        testM("empty list") {
          val sink = ZSink.identity[Int].collectAllN(0)
          for {
            init   <- sink.initial
            result <- sink.extract(init)
          } yield assert(result)(equalTo(Nil -> Chunk.empty))
        },
        testM("init error") {
          val sink = initErrorSink.collectAllN(1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.collectAllN(1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.collectAllN(1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("collectAllWhile")(
        testM("happy path") {
          val sink = ZSink.collectAll[Int].collectAllWhile(_ < 4)
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            step4  <- sink.step(step3, 4)
            result <- sink.extract(step4)
          } yield assert(result)(equalTo(List(List(1, 2, 3)) -> Chunk.single(4)))
        },
        testM("false predicate") {
          val sink = ZSink.identity[Int].collectAllWhile(_ < 0)
          assertM(sinkIteration(sink, 1))(equalTo(Nil -> Chunk.single(1)))
        },
        testM("init error") {
          val sink = initErrorSink.collectAllWhile(_ < 4)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.collectAllWhile(_ < 4)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.collectAllWhile(_ < 4)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("contramap")(
        testM("happy path") {
          val sink = ZSink.identity[Int].contramap[String](_.toInt)
          assertM(sinkIteration(sink, "1"))(equalTo(1 -> Chunk.empty))
        },
        testM("init error") {
          val sink = initErrorSink.contramap[String](_.toInt)
          assertM(sinkIteration(sink, "1").either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.contramap[String](_.toInt)
          assertM(sinkIteration(sink, "1").either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.contramap[String](_.toInt)
          assertM(sinkIteration(sink, "1").either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("contramapM")(
        testM("happy path") {
          val sink = ZSink.identity[Int].contramapM[Any, Unit, String](s => UIO.succeed(s.toInt))
          assertM(sinkIteration(sink, "1"))(equalTo((1, Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
          assertM(sinkIteration(sink, "1").either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
          assertM(sinkIteration(sink, "1").either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
          assertM(sinkIteration(sink, "1").either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("count")(
        testM("ints") {
          checkM(Gen.listOfN(30)(Gen.anyInt)) { (ints: List[Int]) =>
            val stream = Stream.fromIterable(ints)
            assertM(stream.runCount <&> stream.run(Sink.count))(equalTo((30L, 30L)))
          }
        },
        testM("foos") {
          case class Foo()
          checkM(Gen.listOfN(10)(Gen.const(Foo()))) { (foos: List[Foo]) =>
            val stream = Stream.fromIterable(foos)
            assertM(stream.runCount <&> stream.run(Sink.count))(equalTo((10L, 10L)))
          }
        }
      ),
      suite("dimap")(
        testM("happy path") {
          val sink = ZSink.identity[Int].dimap[String, String](_.toInt)(_.toString.reverse)
          assertM(sinkIteration(sink, "123"))(equalTo(("321", Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
          assertM(sinkIteration(sink, "123").either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
          assertM(sinkIteration(sink, "123").either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
          assertM(sinkIteration(sink, "123").either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("drop constructor")(
        testM("happy path - drops zero elements") {
          val sink = ZSink.drop(0) *> ZSink.collectAll[Int]
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            result <- sink.extract(step3)
          } yield assert(result)(equalTo((List(1, 2, 3), Chunk.empty)))
        },
        testM("happy path - drops more than one element") {
          val sink = ZSink.drop(3) *> ZSink.collectAll[Int]
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            step4  <- sink.step(step3, 4)
            step5  <- sink.step(step4, 5)
            result <- sink.extract(step5)
          } yield assert(result)(equalTo((List(4, 5), Chunk.empty)))
        },
        testM("happy path - does not fail when there is not enough input") {
          val sink = ZSink.drop(3) *> ZSink.collectAll[Int]
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            result <- sink.extract(step1)
          } yield assert(result)(equalTo((List(), Chunk.empty)))
        }
      ),
      suite("skip constructor")(
        testM("happy path - drops zero elements") {
          val sink = ZSink.skip(0) *> ZSink.collectAll[Int]
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            result <- sink.extract(step3)
          } yield assert(result)(equalTo((List(1, 2, 3), Chunk.empty)))
        },
        testM("happy path - drops more than one element") {
          val sink = ZSink.skip(3) *> ZSink.collectAll[Int]
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            step4  <- sink.step(step3, 4)
            step5  <- sink.step(step4, 5)
            result <- sink.extract(step5)
          } yield assert(result)(equalTo((List(4, 5), Chunk.empty)))
        },
        testM("fail when there is not enough input") {
          val sink = ZSink.skip(4) *> ZSink.collectAll[Int]
          val io = for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            result <- sink.extract(step3)
          } yield result
          assertM(io.either)(isLeft(equalTo(())))
        } @@ zioTag(errors)
      ),
      suite("drop")(
        testM("happy path - drops zero elements") {
          val sink = ZSink.collectAll[Int].drop(0)
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            result <- sink.extract(step3)
          } yield assert(result)(equalTo((List(1, 2, 3), Chunk.empty)))
        },
        testM("happy path - drops more than one element") {
          val sink = ZSink.collectAll[Int].drop(3)
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            step4  <- sink.step(step3, 4)
            step5  <- sink.step(step4, 5)
            result <- sink.extract(step5)
          } yield assert(result)(equalTo((List(4, 5), Chunk.empty)))
        },
        testM("happy path") {
          val sink = ZSink.identity[Int].drop(1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo(())))
        },
        testM("init error") {
          val sink = initErrorSink.drop(1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.drop(1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.drop(1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("dropWhile")(
        testM("happy path") {
          val sink = ZSink.identity[Int].dropWhile(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo(())))
        },
        testM("false predicate") {
          val sink = ZSink.identity[Int].dropWhile(_ > 5)
          assertM(sinkIteration(sink, 1))(equalTo((1, Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.dropWhile(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.dropWhile(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.dropWhile(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("flatMap")(
        testM("happy path") {
          val sink = ZSink.identity[Int].flatMap(n => ZSink.succeed[Int, String](n.toString))
          assertM(sinkIteration(sink, 1))(equalTo(("1", Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.flatMap(n => ZSink.succeed[Int, String](n.toString))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.flatMap(n => ZSink.succeed[Int, String](n.toString))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.flatMap(n => ZSink.succeed[Int, String](n.toString))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("self done") {
          val sink = ZSink.succeed(3).flatMap(n => ZSink.collectAllN[Int](n.toLong))
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            step4  <- sink.step(step3, 4)
            step5  <- sink.step(step4, 5)
            result <- sink.extract(step5)
          } yield assert(result)(equalTo((List(1, 2, 3), Chunk(4, 5))))
        },
        testM("self more") {
          val sink = ZSink.collectAll[Int].flatMap(list => ZSink.succeed[Int, Int](list.headOption.getOrElse(0)))
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            result <- sink.extract(step3)
          } yield assert(result)(equalTo((1, Chunk.empty)))
        },
        testM("pass leftover") {
          val sink = ZSink.ignoreWhile[Int](_ < 3).flatMap(_ => ZSink.identity[Int])
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            result <- sink.extract(step3)
          } yield assert(result)(equalTo((3, Chunk.empty)))
        },
        testM("end leftover") {
          val sink = ZSink.ignoreWhile[Int](_ < 3).flatMap(_ => ZSink.ignoreWhile[Int](_ < 3))
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            result <- sink.extract(step3)
          } yield assert(result)(equalTo(((), Chunk.single(3))))
        }
      ),
      suite("filter")(
        testM("happy path") {
          val sink = ZSink.identity[Int].filter(_ < 5)
          assertM(sinkIteration(sink, 1))(equalTo((1, Chunk.empty)))
        },
        testM("false predicate") {
          val sink = ZSink.identity[Int].filter(_ > 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo(())))
        },
        testM("init error") {
          val sink = initErrorSink.filter(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.filter(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extractError") {
          val sink = extractErrorSink.filter(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("filterM")(
        testM("happy path") {
          val sink = ZSink.identity[Int].filterM[Any, Unit](n => UIO.succeed(n < 5))
          assertM(sinkIteration(sink, 1))(equalTo((1, Chunk.empty)))
        },
        testM("false predicate") {
          val sink = ZSink.identity[Int].filterM[Any, Unit](n => UIO.succeed(n > 5))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo(())))
        },
        testM("init error") {
          val sink = initErrorSink.filterM[Any, String](n => UIO.succeed(n < 5))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.filterM[Any, String](n => UIO.succeed(n < 5))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extractError") {
          val sink = extractErrorSink.filterM[Any, String](n => UIO.succeed(n < 5))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("keyed")(
        testM("happy path") {
          val sink = ZSink.identity[Int].keyed(_ + 1)
          assertM(sinkIteration(sink, 1))(equalTo((Map(2 -> 1), Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.keyed(_ + 1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.keyed(_ + 1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.keyed(_ + 1)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("map")(
        testM("happy path") {
          val sink = ZSink.identity[Int].map(_.toString)
          assertM(sinkIteration(sink, 1))(equalTo(("1", Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.map(_.toString)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.map(_.toString)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.map(_.toString)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("mapError")(
        testM("init error") {
          val sink = initErrorSink.mapError(_ => "Error")
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Error")))
        },
        testM("step error") {
          val sink = stepErrorSink.mapError(_ => "Error")
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Error")))
        },
        testM("extract error") {
          val sink = extractErrorSink.mapError(_ => "Error")
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Error")))
        }
      ) @@ zioTag(errors),
      suite("mapM")(
        testM("happy path") {
          val sink = ZSink.identity[Int].mapM[Any, Unit, String](n => UIO.succeed(n.toString))
          assertM(sinkIteration(sink, 1))(equalTo(("1", Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.mapM[Any, String, String](n => UIO.succeed(n.toString))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.mapM[Any, String, String](n => UIO.succeed(n.toString))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.mapM[Any, String, String](n => UIO.succeed(n.toString))
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("mapRemainder")(
        testM("init error") {
          val sink = initErrorSink.mapRemainder(_.toLong)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        },
        testM("step error") {
          val sink = stepErrorSink.mapRemainder(_.toLong)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        },
        testM("extract error") {
          val sink = extractErrorSink.mapRemainder(_.toLong)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        }
      ) @@ zioTag(errors),
      suite("optional")(
        testM("happy path") {
          val sink = ZSink.identity[Int].optional
          assertM(sinkIteration(sink, 1))(equalTo((Some(1), Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.optional
          for {
            init   <- sink.initial
            result <- sink.extract(init)
          } yield assert(result)(equalTo((None, Chunk.empty)))
        } @@ zioTag(errors),
        testM("step error") {
          val s = new ZSink[Any, String, Nothing, Any, Nothing] {
            type State = Unit
            val initial                    = UIO.unit
            def step(state: State, a: Any) = IO.fail("Ouch")
            def extract(state: State)      = IO.fail("Ouch")
            def cont(state: State)         = true
          }
          assertM(sinkIteration(s.optional, 1))(equalTo((None, Chunk.single(1))))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.optional
          assertM(sinkIteration(sink, 1))(equalTo((None, Chunk.empty)))
        } @@ zioTag(errors),
        testM("with leftover") {
          val sink = Sink.ignoreWhile[Int](_ < 0).optional
          assertM(sinkIteration(sink, 1))(equalTo((Some(()), Chunk.single(1))))
        }
      ),
      suite("orElse")(
        testM("left") {
          val sink = ZSink.identity[Int] orElse ZSink.fail("Ouch")
          assertM(sinkIteration(sink, 1))(equalTo((Left(1), Chunk.empty)))
        },
        testM("right") {
          val sink = ZSink.fail("Ouch") orElse ZSink.identity[Int]
          assertM(sinkIteration(sink, 1))(equalTo((Right(1), Chunk.empty)))
        },
        testM("init error left") {
          val sink = initErrorSink orElse ZSink.identity[Int]
          assertM(sinkIteration(sink, 1))(equalTo((Right(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("init error right") {
          val sink = ZSink.identity[Int] orElse initErrorSink
          assertM(sinkIteration(sink, 1))(equalTo((Left(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("init error both") {
          val sink = initErrorSink orElse initErrorSink
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error left") {
          val sink = stepErrorSink orElse ZSink.identity[Int]
          assertM(sinkIteration(sink, 1))(equalTo((Right(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("step error right") {
          val sink = ZSink.identity[Int] orElse stepErrorSink
          assertM(sinkIteration(sink, 1))(equalTo((Left(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("step error both") {
          val sink = stepErrorSink orElse stepErrorSink
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error left") {
          val sink = extractErrorSink orElse ZSink.identity[Int]
          assertM(sinkIteration(sink, 1))(equalTo((Right(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("extract error right") {
          val sink = ZSink.identity[Int] orElse extractErrorSink
          assertM(sinkIteration(sink, 1))(equalTo((Left(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("extract error both") {
          val sink = extractErrorSink orElse extractErrorSink
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("left short right long") {
          val sink = ZSink.collectAllN[Int](2) orElse ZSink.collectAll[Int]
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            result <- sink.extract(step2)
          } yield assert(result)(equalTo((Left(List(1, 2)), Chunk.empty)))
        },
        testM("left long right short") {
          val sink = ZSink.collectAll[Int] orElse ZSink.collectAllN[Int](2)
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            step4  <- sink.step(step3, 4)
            result <- sink.extract(step4)
          } yield assert(result)(equalTo((Left(List(1, 2, 3, 4)), Chunk.empty)))
        },
        testM("left long fail right short") {
          val sink = (ZSink.collectAll[Int] <* ZSink.fail("Ouch")) orElse ZSink.collectAllN[Int](2)
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            step4  <- sink.step(step3, 4)
            step5  <- sink.step(step4, 5)
            result <- sink.extract(step5)
          } yield assert(result)(equalTo((Right(List(1, 2)), Chunk(3, 4, 5))))
        }
      ),
      suite("orElseFail")(
        testM("init error") {
          val sink = initErrorSink.orElseFail("Error")
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Error")))
        },
        testM("step error") {
          val sink = stepErrorSink.orElseFail("Error")
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Error")))
        },
        testM("extract error") {
          val sink = extractErrorSink.orElseFail("Error")
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Error")))
        }
      ) @@ zioTag(errors),
      suite("raceBoth")(
        testM("left") {
          val sink = ZSink.identity[Int] raceBoth ZSink.succeed[Int, String]("Hello")
          assertM(sinkIteration(sink, 1))(equalTo((Left(1), Chunk.empty)))
        },
        testM("init error left") {
          val sink = initErrorSink raceBoth ZSink.identity[Int]
          assertM(sinkIteration(sink, 1))(equalTo((Right(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("init error right") {
          val sink = ZSink.identity[Int] raceBoth initErrorSink
          assertM(sinkIteration(sink, 1))(equalTo((Left(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("init error both") {
          val sink = initErrorSink raceBoth initErrorSink
          assertM(sinkIteration(sink, 1).foldCause(_.failures, _ => List.empty[String]))(equalTo(List("Ouch", "Ouch")))
        } @@ zioTag(errors),
        testM("step error left") {
          val sink = stepErrorSink raceBoth ZSink.identity[Int]
          assertM(sinkIteration(sink, 1))(equalTo((Right(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("step error right") {
          val sink = ZSink.identity[Int] raceBoth stepErrorSink
          assertM(sinkIteration(sink, 1))(equalTo((Left(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("step error both") {
          val sink = stepErrorSink raceBoth stepErrorSink
          assertM(sinkIteration(sink, 1).foldCause(_.failures, _ => List.empty[String]))(equalTo(List("Ouch", "Ouch")))
        } @@ zioTag(errors),
        testM("extract error left") {
          val sink = extractErrorSink raceBoth ZSink.identity[Int]
          assertM(sinkIteration(sink, 1))(equalTo((Right(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("extract error right") {
          val sink = ZSink.identity[Int] raceBoth extractErrorSink
          assertM(sinkIteration(sink, 1))(equalTo((Left(1), Chunk.empty)))
        } @@ zioTag(errors),
        testM("extract error both") {
          val sink = extractErrorSink raceBoth extractErrorSink
          assertM(sinkIteration(sink, 1).foldCause(_.failures, _ => List.empty[String]))(equalTo(List("Ouch", "Ouch")))
        } @@ zioTag(errors),
        testM("left wins") {
          val sink = ZSink.collectAllN[Int](2) raceBoth ZSink.collectAll[Int]
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            result <- sink.extract(step2)
          } yield assert(result)(equalTo((Left(List(1, 2)), Chunk.empty)))
        },
        testM("right wins") {
          val sink = ZSink.collectAll[Int] raceBoth ZSink.collectAllN[Int](2)
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            result <- sink.extract(step2)
          } yield assert(result)(equalTo((Right(List(1, 2)), Chunk.empty)))
        }
      ),
      suite("takeWhile")(
        testM("happy path") {
          val sink = Sink.collectAll[Int].takeWhile(_ < 5)
          for {
            init   <- sink.initial
            step1  <- sink.step(init, 1)
            step2  <- sink.step(step1, 2)
            step3  <- sink.step(step2, 3)
            step4  <- sink.step(step3, 4)
            step5  <- sink.step(step4, 5)
            cont   = sink.cont(step5)
            result <- sink.extract(step5)
          } yield assert(cont)(isFalse) && assert(result)(equalTo((List(1, 2, 3, 4), Chunk.single(5))))
        },
        testM("false predicate") {
          val sink = ZSink.identity[Int].takeWhile(_ > 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo(())))
        },
        testM("init error") {
          val sink = initErrorSink.takeWhile(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.takeWhile(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.takeWhile(_ < 5)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      testM("tapInput") {
        for {
          ref <- Ref.make(0)
          sink = ZSink
            .fromFunction[Int, Int](_ * 2)
            .tapInput((n: Int) => ref.update(_ + n).unit)
          _      <- sinkIteration(sink, 1)
          _      <- sinkIteration(sink, 2)
          _      <- sinkIteration(sink, 3)
          result <- ref.get
        } yield assert(result)(equalTo(6))
      },
      testM("tapOutput") {
        for {
          ref <- Ref.make(0)
          sink = ZSink
            .fromFunction[Int, Int](_ * 2)
            .tapOutput(n => ref.update(_ + n).unit)
          _      <- sinkIteration(sink, 1)
          _      <- sinkIteration(sink, 2)
          _      <- sinkIteration(sink, 3)
          result <- ref.get
        } yield assert(result)(equalTo(12))
      },
      suite("untilOutput")(
        testM("happy path") {
          val sink = ZSink.collectAllN[Int](3).untilOutput(_.sum > 3)
          for {
            under <- sink.initial
                      .flatMap(sink.stepChunk(_, Chunk(1, 2)).map(_._1))
                      .flatMap(sink.extract)
            over <- sink.initial
                     .flatMap(sink.stepChunk(_, Chunk(1, 2)).map(_._1))
                     .flatMap(sink.stepChunk(_, Chunk(2, 2)).map(_._1))
                     .flatMap(sink.extract)
          } yield assert(under)(equalTo(None         -> Chunk.empty)) &&
            assert(over)(equalTo(Some(List(1, 2, 2)) -> Chunk(2)))
        },
        testM("false predicate") {
          val sink = ZSink.identity[Int].untilOutput(_ < 0)
          assertM(sinkIteration(sink, 1))(equalTo((None, Chunk.empty)))
        },
        testM("init error") {
          val sink = initErrorSink.untilOutput(_ == 0)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error") {
          val sink = stepErrorSink.untilOutput(_ == 0)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error") {
          val sink = extractErrorSink.untilOutput(_ == 0)
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("zip (<*>)")(
        testM("happy path") {
          val sink = ZSink.identity[Int] <*> ZSink.succeed[Int, String]("Hello")
          assertM(sinkIteration(sink, 1))(equalTo(((1, "Hello"), Chunk.empty)))
        },
        testM("init error left") {
          val sink = initErrorSink <*> ZSink.identity[Int]
          assertM(sinkIteration(sink, 1).either)(isLeft[Any](equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("init error right") {
          val sink = ZSink.identity[Int] <*> initErrorSink
          assertM(sinkIteration(sink, 1).either)(isLeft[Any](equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("init error both") {
          val sink = initErrorSink <*> initErrorSink
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error left") {
          val sink = stepErrorSink <*> ZSink.identity[Int]
          assertM(sinkIteration(sink, 1).either)(isLeft[Any](equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error right") {
          val sink = ZSink.identity[Int] <*> stepErrorSink
          assertM(sinkIteration(sink, 1).either)(isLeft[Any](equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("step error both") {
          val sink = stepErrorSink <*> stepErrorSink
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error left") {
          val sink = extractErrorSink <*> ZSink.identity[Int]
          assertM(sinkIteration(sink, 1).either)(isLeft[Any](equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error right") {
          val sink = ZSink.identity[Int] <*> extractErrorSink
          assertM(sinkIteration(sink, 1).either)(isLeft[Any](equalTo("Ouch")))
        } @@ zioTag(errors),
        testM("extract error both") {
          val sink = extractErrorSink <*> extractErrorSink
          assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        } @@ zioTag(errors)
      ),
      suite("zipLeft (<*)")(
        testM("happy path") {
          val sink = ZSink.identity[Int].zipLeft(ZSink.succeed[Int, String]("Hello"))
          assertM(sinkIteration(sink, 1))(equalTo((1, Chunk.empty)))
        }
      ),
      suite("zipPar")(
        testM("happy path 1") {
          val sink1 = ZSink.collectAllWhile[Int](_ < 5)
          val sink2 = ZSink.collectAllWhile[Int](_ < 3)
          ZipParLaws.laws(zipParLawsStream, sink1, sink2)
        },
        testM("happy path 2") {
          val sink1 = ZSink.collectAllWhile[Int](_ < 5)
          val sink2 = ZSink.collectAllWhile[Int](_ < 30)
          ZipParLaws.laws(zipParLawsStream, sink1, sink2)
        },
        testM("happy path 3") {
          val sink1 = ZSink.collectAllWhile[Int](_ < 50)
          val sink2 = ZSink.collectAllWhile[Int](_ < 30)
          ZipParLaws.laws(zipParLawsStream, sink1, sink2)
        },
        testM("extract error") {
          val sink1 = ZSink.collectAllWhile[Int](_ < 5)
          ZipParLaws.laws(zipParLawsStream, sink1, extractErrorSink)
        } @@ zioTag(errors),
        testM("step error") {
          val sink1 = ZSink.collectAllWhile[Int](_ < 5)
          ZipParLaws.laws(zipParLawsStream, sink1, stepErrorSink)
        } @@ zioTag(errors),
        testM("init error") {
          val sink1 = ZSink.collectAllWhile[Int](_ < 5)
          ZipParLaws.laws(zipParLawsStream, sink1, initErrorSink)
        } @@ zioTag(errors),
        testM("both error") {
          ZipParLaws.laws(zipParLawsStream, stepErrorSink, initErrorSink)
        } @@ zioTag(errors),
        testM("remainder corner case 1") {
          val sink1 = sinkWithLeftover(2, 3, -42)
          val sink2 = sinkWithLeftover(2, 4, -42)
          ZipParLaws.laws(zipParLawsStream, sink1, sink2)
        },
        testM("remainder corner case 2") {
          val sink1 = sinkWithLeftover(3, 1, -42)
          val sink2 = sinkWithLeftover(2, 4, -42)
          ZipParLaws.laws(zipParLawsStream, sink1, sink2)
        },
        testM("zipPar should continue only if both continue") {
          val sink1          = ZSink.collectAllN[Int](3)
          val sink2          = ZSink.collectAllN[Int](2)
          val expectedResult = List(List(1, 2), List(3, 4), List(5, 6))

          Stream(1, 2, 3, 4, 5, 6)
            .aggregate(sink1 zipPar sink2)
            .runCollect
            .map { result =>
              val (l, r) = result.unzip

              assert(l)(equalTo(expectedResult) ?? "Left sink result") &&
              assert(r)(equalTo(expectedResult) ?? "Right sink result")
            }
        }
      ),
      suite("zipRight (*>)")(
        testM("happy path") {
          val sink = ZSink.identity[Int].zipRight(ZSink.succeed[Int, String]("Hello"))
          assertM(sinkIteration(sink, 1))(equalTo(("Hello", Chunk.empty)))
        }
      ),
      suite("zipWith")(testM("happy path") {
        val sink =
          ZSink.identity[Int].zipWith(ZSink.succeed[Int, String]("Hello"))((x, y) => x.toString + y.toString)
        assertM(sinkIteration(sink, 1))(equalTo(("1Hello", Chunk.empty)))
      })
    ),
    suite("Constructors")(
      testM("head")(
        assertM(
          Stream[Int](1, 2, 3)
            .run(ZSink.head[Int])
        )(isSome(equalTo(1)))
      ),
      testM("last")(
        assertM(
          Stream[Int](1, 2, 3)
            .run(ZSink.last[Int])
        )(isSome(equalTo(3)))
      ),
      testM("foldLeft")(
        checkM(
          Gen.small(pureStreamGen(Gen.anyInt, _)),
          Gen.function2(Gen.anyString),
          Gen.anyString
        ) { (s, f, z) =>
          for {
            xs <- s.run(ZSink.foldLeft(z)(f))
            ys <- s.runCollect.map(_.foldLeft(z)(f))
          } yield assert(xs)(equalTo(ys))
        }
      ),
      suite("fold")(
        testM("fold")(checkM(Gen.small(pureStreamGen(Gen.anyInt, _)), Gen.function2(Gen.anyString), Gen.anyString) {
          (s, f, z) =>
            for {
              xs <- s.run(ZSink.foldLeft(z)(f))
              ys <- s.runCollect.map(_.foldLeft(z)(f))
            } yield assert(xs)(equalTo(ys))
        }),
        testM("short circuits") {
          val empty: Stream[Nothing, Int]     = ZStream.empty
          val single: Stream[Nothing, Int]    = ZStream.succeed(1)
          val double: Stream[Nothing, Int]    = ZStream(1, 2)
          val failed: Stream[String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: Stream[E, Int]) =
            for {
              effects <- Ref.make[List[Int]](Nil)
              sink = ZSink.foldM[Any, Nothing, Int, Int, Int](0)(_ => true) { (_, a) =>
                effects.update(a :: _) *> UIO.succeed((30, Chunk.empty))
              }
              exit   <- stream.run(sink).run
              result <- effects.get
            } yield (exit, result)

          for {
            ms <- run(empty)
            ns <- run(single)
            os <- run(double)
            ps <- run(failed)
          } yield {
            assert(ms)(equalTo((Exit.succeed(0), Nil))) &&
            assert(ns)(equalTo((Exit.succeed(30), List(1)))) &&
            assert(os)(equalTo((Exit.succeed(30), List(2, 1)))) &&
            assert(ps)(equalTo((Exit.fail("Ouch"): Exit[String, Int], Nil)))
          }
        }
      ),
      suite("foldM")(
        testM("foldM") {
          val ioGen = successes(Gen.anyString)
          checkM(Gen.small(pureStreamGen(Gen.anyInt, _)), Gen.function2(ioGen), ioGen) { (s, f, z) =>
            for {
              sinkResult <- z.flatMap(z => s.run(ZSink.foldLeftM(z)(f)))
              foldResult <- s.fold(List[Int]())((acc, el) => el :: acc)
                             .map(_.reverse)
                             .flatMap(_.foldLeft(z)((acc, el) => acc.flatMap(f(_, el))))
                             .run
            } yield assert(foldResult.succeeded)(isTrue) implies assert(foldResult)(succeeds(equalTo(sinkResult)))
          }
        },
        testM("short circuits") {
          val empty: Stream[Nothing, Int]     = ZStream.empty
          val single: Stream[Nothing, Int]    = ZStream.succeed(1)
          val double: Stream[Nothing, Int]    = ZStream(1, 2)
          val failed: Stream[String, Nothing] = ZStream.fail("Ouch")

          def run[E](stream: Stream[E, Int]) =
            (for {
              effects <- Ref.make[List[Int]](Nil)
              sink = ZSink.foldM[Any, E, Int, Int, Int](0)(_ => true) { (_, a) =>
                effects.update(a :: _) *> UIO.succeed((30, Chunk.empty))
              }
              exit   <- stream.run(sink)
              result <- effects.get
            } yield exit -> result).run

          (assertM(run(empty))(succeeds(equalTo((0, Nil)))) <*>
            assertM(run(single))(succeeds(equalTo((30, List(1))))) <*>
            assertM(run(double))(succeeds(equalTo((30, List(2, 1))))) <*>
            assertM(run(failed))(fails(equalTo("Ouch")))).map {
            case (((r1, r2), r3), r4) => r1 && r2 && r3 && r4
          }
        }
      ),
      suite("collectAll")(
        testM("collectAllN")(
          assertM(
            Stream[Int](1, 2, 3)
              .run(Sink.collectAllN[Int](2))
          )(equalTo(List(1, 2)))
        ),
        testM("collectAllToSet")(
          assertM(
            Stream[Int](1, 2, 3, 3, 4)
              .run(Sink.collectAllToSet[Int])
          )(equalTo(Set(1, 2, 3, 4)))
        ),
        testM("collectAllToSetN")(
          assertM(
            Stream[Int](1, 2, 1, 2, 3, 3, 4)
              .run(Sink.collectAllToSetN[Int](3))
          )(equalTo(Set(1, 2, 3)))
        ),
        testM("collectAllToMap")(
          assertM(
            Stream
              .range(0, 10)
              .run(Sink.collectAllToMap[Int, Int](value => value % 3)(_ + _))
          )(equalTo(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15)))
        ),
        suite("collectAllToMapN")(
          testM("stop collecting when map size exceeds limit")(
            assertM(
              Stream
                .range(0, 10)
                .run(Sink.collectAllToMapN[Int, Int](2)(value => value % 3)(_ + _))
            )(equalTo(Map[Int, Int](0 -> 0, 1 -> 1)))
          ),
          testM("collects entire stream if the number of keys doesn't exceed `n`")(
            assertM(
              Stream
                .range(0, 10)
                .run(Sink.collectAllToMapN[Int, Int](3)(value => value % 3)(_ + _))
            )(equalTo(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15)))
          ),
          testM("keep collecting as long as map size does not exceed the limit")(
            assertM(
              Stream(0, 1, 3, 4, 2)
                .run(Sink.collectAllToMapN[Int, Int](2)(value => value % 3)(_ + _))
            )(equalTo(Map[Int, Int](0 -> 3, 1 -> 5)))
          )
        ),
        testM("collectAllWhile")(
          checkM(Gen.small(pureStreamGen(Gen.anyString, _)), Gen.function(Gen.boolean)) { (s, f) =>
            for {
              sinkResult <- s.run(ZSink.collectAllWhile(f))
              listResult <- s.runCollect.map(_.takeWhile(f)).run
            } yield assert(listResult.succeeded)(isTrue) implies assert(listResult)(succeeds(equalTo(sinkResult)))
          }
        )
      ),
      suite("foldWeighted/foldUntil")(
        testM("foldWeighted")(
          assertM(
            Stream[Long](1, 5, 2, 3)
              .aggregate(
                Sink.foldWeighted[Long, List[Long]](List())(_ * 2, 12)((acc, el) => el :: acc).map(_.reverse)
              )
              .runCollect
          )(equalTo(List(List(1L, 5L), List(2L, 3L))))
        ),
        testM("foldWeightedDecompose")(
          assertM(
            Stream(1, 5, 1)
              .aggregate(
                Sink
                  .foldWeightedDecompose(List[Int]())((i: Int) => i.toLong, 4, (i: Int) => Chunk(i - 1, 1)) {
                    (acc, el) => el :: acc
                  }
                  .map(_.reverse)
              )
              .runCollect
          )(equalTo(List(List(1), List(4), List(1, 1))))
        ),
        testM("foldWeightedM")(
          assertM(
            Stream[Long](1, 5, 2, 3)
              .aggregate(
                Sink
                  .foldWeightedM(List[Long]())((a: Long) => UIO.succeed(a * 2), 12)((acc, el) => UIO.succeed(el :: acc))
                  .map(_.reverse)
              )
              .runCollect
          )(equalTo(List(List(1L, 5L), List(2L, 3L))))
        ),
        testM("foldWeightedDecomposeM")(
          assertM(
            Stream(1, 5, 1)
              .aggregate(
                Sink
                  .foldWeightedDecomposeM(List[Int]())(
                    (i: Int) => UIO.succeed(i.toLong),
                    4,
                    (i: Int) => UIO.succeed(Chunk(i - 1, 1))
                  )((acc, el) => UIO.succeed(el :: acc))
                  .map(_.reverse)
              )
              .runCollect
          )(equalTo(List(List(1), List(4), List(1, 1))))
        ),
        testM("foldUntil")(
          assertM(
            Stream[Long](1, 1, 1, 1, 1, 1)
              .aggregate(Sink.foldUntil(0L, 3)(_ + (_: Long)))
              .runCollect
          )(equalTo(List(3L, 3L)))
        ),
        testM("foldUntilM")(
          assertM(
            Stream[Long](1, 1, 1, 1, 1, 1)
              .aggregate(Sink.foldUntilM(0L, 3)((s, a: Long) => UIO.succeed(s + a)))
              .runCollect
          )(equalTo(List(3L, 3L)))
        ),
        testM("fromFunction")(
          assertM(
            Stream(1, 2, 3, 4, 5)
              .aggregate(Sink.fromFunction[Int, String](_.toString))
              .runCollect
          )(equalTo(List("1", "2", "3", "4", "5")))
        ),
        testM("fromFunctionM")(
          assertM(
            Stream("1", "2", "3", "4", "5")
              .transduce(Sink.fromFunctionM[Throwable, String, Int](s => Task(s.toInt)))
              .runCollect
          )(equalTo(List(1, 2, 3, 4, 5)))
        )
      ),
      testM("fromOutputStream") {
        import java.io.ByteArrayOutputStream

        val output = new ByteArrayOutputStream()
        val data   = "0123456789"
        val stream = Stream(Chunk.fromArray(data.take(5).getBytes), Chunk.fromArray(data.drop(5).getBytes))

        for {
          bytesWritten <- stream.run(ZSink.fromOutputStream(output))
        } yield assert(bytesWritten)(equalTo(10)) && assert(new String(output.toByteArray, "UTF-8"))(equalTo(data))
      },
      testM("pull1") {
        val stream = Stream.fromIterable(List(1))
        val sink   = Sink.pull1(IO.succeed(Option.empty[Int]))((i: Int) => Sink.succeed[Int, Option[Int]](Some(i)))

        assertM(stream.run(sink))(isSome(equalTo(1)))
      },
      suite("splitLines")(
        testM("preserves data")(
          checkM(weirdStringGenForSplitLines) { lines =>
            val data = lines.mkString("\n")

            for {
              initial            <- ZSink.splitLines.initial
              middle             <- ZSink.splitLines.step(initial, data)
              res                <- ZSink.splitLines.extract(middle)
              (result, leftover) = res
            } yield assert((result ++ leftover).toArray[String].mkString("\n"))(equalTo(lines.mkString("\n")))
          }
        ),
        testM("handles leftovers") {
          for {
            initial            <- ZSink.splitLines.initial
            middle             <- ZSink.splitLines.step(initial, "abc\nbc")
            res                <- ZSink.splitLines.extract(middle)
            (result, leftover) = res
          } yield assert(result.toArray[String].mkString("\n"))(equalTo("abc")) && assert(
            leftover.toArray[String].mkString
          )(equalTo("bc"))
        },
        testM("aggregates") {
          assertM(
            Stream("abc", "\n", "bc", "\n", "bcd", "bcd")
              .aggregate(ZSink.splitLines)
              .runCollect
          )(equalTo(List(Chunk("abc"), Chunk("bc"), Chunk("bcdbcd"))))
        },
        testM("single newline edgecase") {
          assertM(
            Stream("\n")
              .aggregate(ZSink.splitLines)
              .mapConcatChunk(identity)
              .runCollect
          )(equalTo(List("")))
        },
        testM("no newlines in data") {
          assertM(
            Stream("abc", "abc", "abc")
              .aggregate(ZSink.splitLines)
              .mapConcatChunk(identity)
              .runCollect
          )(equalTo(List("abcabcabc")))
        },
        testM("\\r\\n on the boundary") {
          assertM(
            Stream("abc\r", "\nabc")
              .aggregate(ZSink.splitLines)
              .mapConcatChunk(identity)
              .runCollect
          )(equalTo(List("abc", "abc")))
        }
      ),
      testM("splitLinesChunk")(
        checkM(weirdStringGenForSplitLines) { xs =>
          val chunks = Chunk.fromIterable(xs.sliding(2, 2).toList.map(_.mkString("\n")))
          val ys     = xs.headOption.map(_ :: xs.drop(1).sliding(2, 2).toList.map(_.mkString)).getOrElse(Nil)

          for {
            initial            <- ZSink.splitLinesChunk.initial
            middle             <- ZSink.splitLinesChunk.step(initial, chunks)
            res                <- ZSink.splitLinesChunk.extract(middle)
            (result, leftover) = res
          } yield assert((result ++ leftover.flatten).toArray[String].toList)(equalTo(ys))
        }
      ),
      suite("splitOn string")(
        testM("preserves data")(checkM(Gen.listOf(Gen.anyString).filter(_.nonEmpty)) { lines =>
          val data = lines.mkString("|")
          val sink = ZSink.splitOn("|")

          for {
            initial            <- sink.initial
            middle             <- sink.step(initial, data)
            res                <- sink.extract(middle)
            (result, leftover) = res
          } yield assert((result ++ leftover).toArray[String].mkString("|"))(equalTo(data))
        }),
        testM("handles leftovers") {
          val sink = ZSink.splitOn("\n")
          for {
            initial            <- sink.initial
            middle             <- sink.step(initial, "abc\nbc")
            res                <- sink.extract(middle)
            (result, leftover) = res
          } yield assert(result.toArray[String].mkString("\n"))(equalTo("abc")) && assert(
            leftover.toArray[String].mkString
          )(equalTo("bc"))
        },
        testM("aggregates") {
          assertM(
            Stream("abc", "delimiter", "bc", "delimiter", "bcd", "bcd")
              .aggregate(ZSink.splitOn("delimiter"))
              .runCollect
          )(equalTo(List(Chunk("abc"), Chunk("bc"), Chunk("bcdbcd"))))
        },
        testM("single newline edgecase") {
          assertM(
            Stream("test")
              .aggregate(ZSink.splitOn("test"))
              .mapConcatChunk(identity)
              .runCollect
          )(equalTo(List("")))
        },
        testM("no delimiter in data") {
          assertM(
            Stream("abc", "abc", "abc")
              .aggregate(ZSink.splitOn("hello"))
              .mapConcatChunk(identity)
              .runCollect
          )(equalTo(List("abcabcabc")))
        },
        testM("delimiter on the boundary") {
          assertM(
            Stream("abc<", ">abc")
              .aggregate(ZSink.splitOn("<>"))
              .mapConcatChunk(identity)
              .runCollect
          )(equalTo(List("abc", "abc")))
        }
      ),
      suite("splitOn chunk")(
        testM("happy path") {
          val sink = ZSink.splitOn(Chunk.single(0), 1000)
          val in   = Stream(Chunk(1), Chunk(2, 0, 3), Chunk(4, 0, 5), Chunk(6))
          assertM(in.aggregate(sink).runCollect)(
            equalTo(List(Chunk(1, 2), Chunk(3, 4), Chunk(5, 6)))
          )
        },
        testM("split delimiter") {
          val sink = ZSink.splitOn(Chunk(-1, -2, -3), 1000)
          val in   = Stream(Chunk(0, 1, -1, -2), Chunk(-3, 2, 3))
          assertM(in.aggregate(sink).runCollect)(
            equalTo(List(Chunk(0, 1), Chunk(2, 3)))
          )
        },
        testM("partial delimiter") {
          val sink = ZSink.splitOn(Chunk(-1, -2), 1000)
          val in   = Stream(Chunk(0, 1, -1, 2), Chunk(3, -2, 4))
          assertM(in.aggregate(sink).runCollect)(
            equalTo(List(Chunk(0, 1, -1, 2, 3, -2, 4)))
          )
        },
        testM("deilimter last") {
          val sink = ZSink.splitOn(Chunk(-1, -2), 1000)
          val in   = Stream(Chunk(1, 2), Chunk(3, -1, -2))
          assertM(in.aggregate(sink).runCollect)(
            equalTo(List(Chunk(1, 2, 3), Chunk.empty))
          )
        },
        testM("delimiter first") {
          val sink = ZSink.splitOn(Chunk(-1, -2), 1000)
          val in   = Stream(Chunk(-1, -2, 1, 2), Chunk(3, 4))
          assertM(in.aggregate(sink).runCollect)(
            equalTo(List(Chunk.empty, Chunk(1, 2, 3, 4)))
          )
        },
        testM("no delimiter") {
          val sink = ZSink.splitOn(Chunk(-1), 1000)
          val in   = Stream(Chunk(1, 2), Chunk(3, 4))
          assertM(in.aggregate(sink).runCollect)(
            equalTo(List(Chunk(1, 2, 3, 4)))
          )
        },
        testM("empty stream") {
          val sink = ZSink.splitOn(Chunk(-1, -2), 1000)
          val in   = Stream.empty
          assertM(in.aggregate(sink).runCount)(equalTo(0L))
        },
        testM("fails if maximum frame length exceeded") {
          val sink = ZSink.splitOn(Chunk(-1, -2), 3)
          val in   = Stream(Chunk(1, 2), Chunk(3, 4, -1, -2, 5))
          assertM(in.aggregate(sink).runCollect.run)(
            fails(isSubtype[IllegalArgumentException](anything))
          )
        },
        testM("succeeds if maximum frame length hit exactly") {
          val sink = ZSink.splitOn(Chunk(-1, -2), 4)
          val in   = Stream(Chunk(1, 2), Chunk(3, 4, -1, -2, 5))
          assertM(in.aggregate(sink).runCollect)(
            equalTo(List(Chunk(1, 2, 3, 4), Chunk(5)))
          )
        }
      ),
      suite("sum")(
        testM("Long") {
          checkM(Gen.listOfN(10)(Gen.anyLong)) { longs =>
            val stream = Stream.fromIterable(longs)
            (assertM(stream.run(ZSink.sum[Long]) <&> stream.runSum)(equalTo((longs.sum, longs.sum))))
          }
        },
        testM("Int") {
          checkM(Gen.listOfN(10)(Gen.anyInt)) { ints =>
            val stream = Stream.fromIterable(ints)
            (assertM(stream.run(ZSink.sum[Int]) <&> stream.runSum)(equalTo((ints.sum, ints.sum))))
          }
        },
        testM("Double") {
          checkM(Gen.listOfN(10)(Gen.anyDouble)) { doubles =>
            val stream = Stream.fromIterable(doubles)
            assertM(stream.run(ZSink.sum[Double]) <&> stream.runSum)(equalTo((doubles.sum, doubles.sum)))
          }
        }
      ),
      suite("throttleEnforce")(
        testM("throttleEnforce") {

          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Option[Int]]) =
            for {
              init1 <- sink.initial
              step1 <- sink.step(init1, 1)
              res1  <- sink.extract(step1).map(_._1)
              init2 <- sink.initial
              _     <- TestClock.advance(23.milliseconds)
              step2 <- sink.step(init2, 2)
              res2  <- sink.extract(step2).map(_._1)
              init3 <- sink.initial
              step3 <- sink.step(init3, 3)
              res3  <- sink.extract(step3).map(_._1)
              init4 <- sink.initial
              step4 <- sink.step(init4, 4)
              res4  <- sink.extract(step4).map(_._1)
              _     <- TestClock.advance(11.milliseconds)
              init5 <- sink.initial
              step5 <- sink.step(init5, 5)
              res5  <- sink.extract(step5).map(_._1)
            } yield assert(List(res1, res2, res3, res4, res5))(equalTo(List(Some(1), Some(2), None, None, Some(5))))

          ZSink.throttleEnforce[Int](1, 10.milliseconds)(_ => 1).use(sinkTest)
        },
        testM("with burst") {

          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Option[Int]]) =
            for {
              init1 <- sink.initial
              step1 <- sink.step(init1, 1)
              res1  <- sink.extract(step1).map(_._1)
              init2 <- sink.initial
              _     <- TestClock.advance(23.milliseconds)
              step2 <- sink.step(init2, 2)
              res2  <- sink.extract(step2).map(_._1)
              init3 <- sink.initial
              step3 <- sink.step(init3, 3)
              res3  <- sink.extract(step3).map(_._1)
              init4 <- sink.initial
              step4 <- sink.step(init4, 4)
              res4  <- sink.extract(step4).map(_._1)
              _     <- TestClock.advance(11.milliseconds)
              init5 <- sink.initial
              step5 <- sink.step(init5, 5)
              res5  <- sink.extract(step5).map(_._1)
            } yield assert(List(res1, res2, res3, res4, res5))(equalTo(List(Some(1), Some(2), Some(3), None, Some(5))))

          ZSink.throttleEnforce[Int](1, 10.milliseconds, 1)(_ => 1).use(sinkTest)
        }
      ),
      suite("throttleShape")(
        testM("throttleShape") {

          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
            for {
              init1 <- sink.initial
              step1 <- sink.step(init1, 1)
              res1  <- sink.extract(step1).map(_._1)
              init2 <- sink.initial
              step2 <- sink.step(init2, 2)
              res2  <- sink.extract(step2).map(_._1)
              init3 <- sink.initial
              _     <- clock.sleep(4.seconds)
              step3 <- sink.step(init3, 3)
              res3  <- sink.extract(step3).map(_._1)
            } yield assert(List(res1, res2, res3))(equalTo(List(1, 2, 3)))

          for {
            fiber <- ZSink
                      .throttleShape[Int](1, 1.second)(_.toLong)
                      .use(sinkTest)
                      .fork
            _    <- TestClock.adjust(8.seconds)
            test <- fiber.join
          } yield test
        },
        testM("infinite bandwidth") {

          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
            for {
              init1   <- sink.initial
              step1   <- sink.step(init1, 1)
              res1    <- sink.extract(step1).map(_._1)
              init2   <- sink.initial
              step2   <- sink.step(init2, 2)
              res2    <- sink.extract(step2).map(_._1)
              elapsed <- clock.currentTime(TimeUnit.SECONDS)
            } yield assert(elapsed)(equalTo(0L)) && assert(List(res1, res2))(equalTo(List(1, 2)))

          ZSink.throttleShape[Int](1, 0.seconds)(_ => 100000L).use(sinkTest)
        },
        testM("with burst") {

          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
            for {
              init1 <- sink.initial
              step1 <- sink.step(init1, 1)
              res1  <- sink.extract(step1).map(_._1)
              init2 <- sink.initial
              _     <- TestClock.adjust(2.seconds)
              step2 <- sink.step(init2, 2)
              res2  <- sink.extract(step2).map(_._1)
              init3 <- sink.initial
              _     <- TestClock.adjust(4.seconds)
              _     <- clock.sleep(4.seconds)
              step3 <- sink.step(init3, 3)
              res3  <- sink.extract(step3).map(_._1)
            } yield assert(List(res1, res2, res3))(equalTo(List(1, 2, 3)))

          for {
            fiber <- ZSink
                      .throttleShape[Int](1, 1.second, 2)(_.toLong)
                      .use(sinkTest)
                      .fork
            test <- fiber.join
          } yield test
        }
      ),
      suite("utf8DecodeChunk")(
        testM("regular strings")(checkM(Gen.anyString) { s =>
          assertM(
            Stream(Chunk.fromArray(s.getBytes("UTF-8")))
              .aggregate(ZSink.utf8DecodeChunk)
              .runCollect
              .map(_.mkString)
          )(equalTo(s))
        }),
        testM("incomplete chunk 1") {
          for {
            init        <- ZSink.utf8DecodeChunk.initial
            state1      <- ZSink.utf8DecodeChunk.step(init, Chunk(0xC2.toByte))
            state2      <- ZSink.utf8DecodeChunk.step(state1, Chunk(0xA2.toByte))
            result      <- ZSink.utf8DecodeChunk.extract(state2)
            (string, _) = result
          } yield assert(ZSink.utf8DecodeChunk.cont(state1))(isTrue) &&
            assert(ZSink.utf8DecodeChunk.cont(state2))(isFalse) &&
            assert(string.getBytes("UTF-8"))(equalTo(Array(0xC2.toByte, 0xA2.toByte)))
        },
        testM("incomplete chunk 2") {
          for {
            init        <- ZSink.utf8DecodeChunk.initial
            state1      <- ZSink.utf8DecodeChunk.step(init, Chunk(0xE0.toByte, 0xA4.toByte))
            state2      <- ZSink.utf8DecodeChunk.step(state1, Chunk(0xB9.toByte))
            result      <- ZSink.utf8DecodeChunk.extract(state2)
            (string, _) = result
          } yield assert(ZSink.utf8DecodeChunk.cont(state1))(isTrue) &&
            assert(ZSink.utf8DecodeChunk.cont(state2))(isFalse) &&
            assert(string.getBytes("UTF-8"))(equalTo(Array(0xE0.toByte, 0xA4.toByte, 0xB9.toByte)))
        },
        testM("incomplete chunk 3") {
          for {
            init        <- ZSink.utf8DecodeChunk.initial
            state1      <- ZSink.utf8DecodeChunk.step(init, Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte))
            state2      <- ZSink.utf8DecodeChunk.step(state1, Chunk(0x88.toByte))
            result      <- ZSink.utf8DecodeChunk.extract(state2)
            (string, _) = result
          } yield assert(ZSink.utf8DecodeChunk.cont(state1))(isTrue) &&
            assert(ZSink.utf8DecodeChunk.cont(state2))(isFalse) &&
            assert(string.getBytes("UTF-8"))(equalTo(Array(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte)))
        },
        testM("chunk with leftover") {
          for {
            init <- ZSink.utf8DecodeChunk.initial
            state1 <- ZSink.utf8DecodeChunk
                       .step(
                         init,
                         Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte, 0xF0.toByte, 0x90.toByte)
                       )
            result <- ZSink.utf8DecodeChunk.extract(state1).map(_._2.flatMap[Byte](identity).toArray[Byte])
          } yield assert(ZSink.utf8DecodeChunk.cont(state1))(isFalse) && assert(result)(
            equalTo(Array(0xF0.toByte, 0x90.toByte))
          )
        }
      )
    ),
    suite("Usecases")(
      testM("Number array parsing with Sink.foldM") {
        sealed trait ParserState
        object ParserState {
          case object Start               extends ParserState
          case class Element(acc: String) extends ParserState
          case object Done                extends ParserState
        }

        val numArrayParser =
          ZSink
            .foldM((ParserState.Start: ParserState, List.empty[Int], true))(_._3) {
              (s, a: Char) =>
                s match {
                  case (ParserState.Start, acc, _) =>
                    a match {
                      case a if a.isWhitespace => UIO.succeed(((ParserState.Start, acc, true), Chunk.empty))
                      case '['                 => UIO.succeed(((ParserState.Element(""), acc, true), Chunk.empty))
                      case _                   => IO.fail("Expected '['")
                    }

                  case (ParserState.Element(el), acc, _) =>
                    a match {
                      case a if a.isDigit =>
                        UIO.succeed(((ParserState.Element(el + a), acc, true), Chunk.empty))
                      case ',' => UIO.succeed(((ParserState.Element(""), acc :+ el.toInt, true), Chunk.empty))
                      case ']' => UIO.succeed(((ParserState.Done, acc :+ el.toInt, false), Chunk.empty))
                      case _   => IO.fail("Expected a digit or ,")
                    }

                  case (ParserState.Done, acc, _) =>
                    UIO.succeed(((ParserState.Done, acc, false), Chunk.empty))
                }
            }
            .map(_._2)
            .chunked

        val src1         = ZStreamChunk.succeed(Chunk.fromArray(Array('[', '1', '2')))
        val src2         = ZStreamChunk.succeed(Chunk.fromArray(Array('3', ',', '4', ']')))
        val partialParse = src1.run(numArrayParser).run
        val fullParse    = (src1 ++ src2).run(numArrayParser).run

        assertM(partialParse)(succeeds(equalTo(Nil)))
          .zipWith(assertM(fullParse)(succeeds(equalTo(List(123, 4)))))(_ && _)
      },
      testM("Number array parsing with combinators") {
        val comma: ZSink[Any, Nothing, Char, Char, List[Char]] = ZSink.collectAllWhile[Char](_ == ',')
        val brace: ZSink[Any, String, Char, Char, Char] =
          ZSink.read1[String, Char](a => s"Expected closing brace; instead: $a")((_: Char) == ']')
        val number: ZSink[Any, String, Char, Char, Int] =
          ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt)
        val numbers = (number <*> (comma *> number).collectAllWhile(_ != ']'))
          .map(tp => tp._1 :: tp._2)

        val elements = numbers <* brace

        lazy val start: ZSink[Any, String, Char, Char, List[Int]] =
          ZSink.pull1(IO.fail("Input was empty")) {
            case a if a.isWhitespace => start
            case '['                 => elements
            case _                   => ZSink.fail("Expected '['")
          }

        val src1         = ZStreamChunk.succeed(Chunk.fromArray(Array('[', '1', '2')))
        val src2         = ZStreamChunk.succeed(Chunk.fromArray(Array('3', ',', '4', ']')))
        val partialParse = src1.run(start.chunked).run
        val fullParse    = (src1 ++ src2).run(start.chunked).run

        assertM(partialParse)(fails(equalTo("Expected closing brace; instead: None"))).zipWith(
          assertM(fullParse)(succeeds(equalTo(List(123, 4))))
        )(_ && _)
      }
    )
  )
}
