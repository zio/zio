package zio.stream

import java.util.concurrent.TimeUnit
import scala.{ Stream => _ }
import zio.{ clock, Chunk, IO, Ref, UIO, ZIO, ZIOSpec }
import zio.clock._
import zio.duration._
import zio.test._
import zio.test.Assertion.{ equalTo, fails, isFalse, isLeft, isNone, isRight, isSome, isTrue, succeeds }
import zio.test.mock.MockClock
import SinkUtils._
import ZSink.Step

object SinkSpec
    extends ZIOSpec(
      suite("SinkStep")(
        suite("combinators")(
          testM("happy path") {
            val sink = ZSink.identity[Int].as("const")
            assertM(sinkIteration(sink, 1).run, succeeds(equalTo("const")))
          },
          testM("init error") {
            val sink = initErrorSink.as("const")
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.as("const")
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.as("const")
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))

          }
        ),
        suite("asError")(
          testM("init error") {
            val sink = initErrorSink.asError("Error")
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Error")))

          },
          testM("step error") {
            val sink = stepErrorSink.asError("Error")
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Error")))

          },
          testM("extract error") {
            val sink = extractErrorSink.asError("Error")
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Error")))

          }
        ),
        suite("chunked")(
          testM("happy path") {
            val sink = ZSink.collectAll[Int].chunked
            assertM(sinkIteration(sink, Chunk(1, 2, 3, 4, 5)), equalTo(List(1, 2, 3, 4, 5)))
          },
          testM("empty") {
            val sink = ZSink.collectAll[Int].chunked
            assertM(sinkIteration(sink, Chunk.empty), equalTo(List.empty[Int]))

          },
          testM("init error") {
            val sink = initErrorSink.chunked
            assertM(sinkIteration(sink, Chunk.single(1)).either, isLeft(equalTo("Ouch")))

          },
          testM("step error") {
            val sink = stepErrorSink.chunked
            assertM(sinkIteration(sink, Chunk.single(1)).either, isLeft(equalTo("Ouch")))

          },
          testM("extract error") {
            val sink = extractErrorSink.chunked
            assertM(sinkIteration(sink, Chunk.single(1)).either, isLeft(equalTo("Ouch")))

          }
        ),
        suite("collectAll")(
          testM("happy path") {
            val sink = ZSink.identity[Int].collectAll
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(List(1))))

          },
          testM("init error") {
            val sink = initErrorSink.collectAll
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))

          },
          testM("step error") {
            val sink = stepErrorSink.collectAll
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))

          },
          testM("extract error") {
            val sink = extractErrorSink.collectAll
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("collectAllN")(
          testM("happy path") {
            val sink = ZSink.identity[Int].collectAllN(5)
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(List(1))))
          },
          testM("empty list") {
            val sink = ZSink.identity[Int].collectAllN(0)
            assertM(
              (for {
                init     <- sink.initial
                step     <- sink.step(Step.state(init), 1)
                result   <- sink.extract(Step.state(step))
                leftover = Step.leftover(step)
              } yield result -> leftover).run,
              succeeds(equalTo((List.empty[Int], Chunk.single(1))))
            )
          },
          testM("init error") {
            val sink = initErrorSink.collectAllN(1)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.collectAllN(1)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error empty list") {
            val sink = extractErrorSink.collectAllN(1)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("collectAllWhile")(
          testM("happy path") {
            val sink = ZSink.identity[Int].collectAllWhile(_ < 10)
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(List(1))))
          },
          testM("false predicate") {
            val errorMsg = "No elements have been consumed by the sink"
            val sink     = ZSink.identity[Int].collectAllWhile(_ < 0).mapError(_ => errorMsg)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo(errorMsg)))
          },
          testM("init error") {
            val sink = initErrorSink.collectAllWhile(_ > 1)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.collectAllWhile(_ > 1)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.collectAllWhile(_ > 1)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("contramap")(
          testM("happy path") {
            val sink = ZSink.identity[Int].contramap[String](_.toInt)
            assertM(sinkIteration(sink, "1").either, isRight(equalTo(1)))
          },
          testM("init error") {
            val sink = initErrorSink.contramap[String](_.toInt)
            assertM(sinkIteration(sink, "1").either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.contramap[String](_.toInt)
            assertM(sinkIteration(sink, "1").either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.contramap[String](_.toInt)
            assertM(sinkIteration(sink, "1").either, isLeft(equalTo("Ouch")))

          }
        ),
        suite("contramapM")(
          testM("happy path") {
            val sink = ZSink.identity[Int].contramapM[Any, Unit, String](s => UIO.succeed(s.toInt))
            assertM(sinkIteration(sink, "1").either, isRight(equalTo(1)))
          },
          testM("init error") {
            val sink = initErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
            assertM(sinkIteration(sink, "1").either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
            assertM(sinkIteration(sink, "1").either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
            assertM(sinkIteration(sink, "1").either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("dimap")(
          testM("happy path") {
            val sink = ZSink.identity[Int].dimap[String, String](_.toInt)(_.toString.reverse)
            assertM(sinkIteration(sink, "123").either, isRight(equalTo("321")))
          },
          testM("init error") {
            val sink = initErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
            assertM(sinkIteration(sink, "123").either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
            assertM(sinkIteration(sink, "123").either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
            assertM(sinkIteration(sink, "123").either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("dropWhile")(
          testM("happy path") {
            val sink = ZSink.identity[Int].dropWhile[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo(())))
          },
          testM("false predicate") {
            val sink = ZSink.identity[Int].dropWhile[Int](_ > 5)
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(1)))
          },
          testM("init error") {
            val sink = initErrorSink.dropWhile[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.dropWhile[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.dropWhile[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("flatMap")(
          testM("happy path") {
            val sink = ZSink.identity[Int].flatMap(n => ZSink.succeed(n.toString))
            assertM(sinkIteration(sink, 1).either, isRight(equalTo("1")))
          },
          testM("init error") {
            val sink = initErrorSink.flatMap(n => ZSink.succeed(n.toString))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.flatMap(n => ZSink.succeed(n.toString))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.flatMap(n => ZSink.succeed(n.toString))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("filter")(
          testM("happy path") {
            val sink = ZSink.identity[Int].filter[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(1)))
          },
          testM("false predicate") {
            val sink = ZSink.identity[Int].filter[Int](_ > 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo(())))
          },
          testM("init error") {
            val sink = initErrorSink.filter[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.filter[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extractError") {
            val sink = extractErrorSink.filter[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("filterM")(
          testM("happy path") {
            val sink = ZSink.identity[Int].filterM[Any, Unit, Int](n => UIO.succeed(n < 5))
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(1)))
          },
          testM("false predicate") {
            val sink = ZSink.identity[Int].filterM[Any, Unit, Int](n => UIO.succeed(n > 5))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo(())))
          },
          testM("init error") {
            val sink = initErrorSink.filterM[Any, String, Int](n => UIO.succeed(n < 5))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.filterM[Any, String, Int](n => UIO.succeed(n < 5))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extractError") {
            val sink = extractErrorSink.filterM[Any, String, Int](n => UIO.succeed(n < 5))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("keyed")(
          testM("happy path") {
            val sink = ZSink.identity[Int].keyed((_: Int) + 1)
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(Map(2 -> 1))))
          },
          testM("init error") {
            val sink = initErrorSink.keyed((_: Int) + 1)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.keyed((_: Int) + 1)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.keyed((_: Int) + 1)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("map")(
          testM("happy path") {
            val sink = ZSink.identity[Int].map(_.toString)
            assertM(sinkIteration(sink, 1).either, isRight(equalTo("1")))
          },
          testM("init error") {
            val sink = initErrorSink.map(_.toString)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.map(_.toString)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.map(_.toString)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("mapError")(
          testM("init error") {
            val sink = initErrorSink.mapError(_ => "Error")
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Error")))
          },
          testM("step error") {
            val sink = stepErrorSink.mapError(_ => "Error")
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Error")))
          },
          testM("extract error") {
            val sink = extractErrorSink.mapError(_ => "Error")
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Error")))
          }
        ),
        suite("mapM")(
          testM("happy path") {
            val sink = ZSink.identity[Int].mapM[Any, Unit, String](n => UIO.succeed(n.toString))
            assertM(sinkIteration(sink, 1).either, isRight(equalTo("1")))
          },
          testM("init error") {
            val sink = initErrorSink.mapM[Any, String, String](n => UIO.succeed(n.toString))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.mapM[Any, String, String](n => UIO.succeed(n.toString))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.mapM[Any, String, String](n => UIO.succeed(n.toString))
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("mapRemainder")(
          testM("init error") {
            val sink = initErrorSink.mapRemainder(_.toLong)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.mapRemainder(_.toLong)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.mapRemainder(_.toLong)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("optional")(
          testM("happy path") {
            val sink = ZSink.identity[Int].optional
            assertM(sinkIteration(sink, 1).either, isRight(isSome(equalTo(1))))
          },
          testM("init error") {
            val sink = initErrorSink.optional
            assertM(sinkIteration(sink, 1).either, isRight(isNone))
          },
          testM("step error") {
            val sink = stepErrorSink.optional
            assertM(sinkIteration(sink, 1).either, isRight(isNone))
          },
          testM("extract error") {
            val sink = extractErrorSink.optional
            assertM(sinkIteration(sink, 1).either, isRight(isNone))
          },
          testM("leftover happy path") {
            val sink = ZSink.collectAllN[Int](2).optional
            for {
              init     <- sink.initial
              step1    <- sink.step(Step.state(init), 1)
              step2    <- sink.step(Step.state(step1), 2)
              step3    <- sink.step(Step.state(step2), 3)
              result   <- sink.extract(Step.state(step3))
              leftover = Step.leftover(step3)
            } yield assert(result, isSome(equalTo(List(1, 2)))) && assert(leftover, equalTo(Chunk.single(3)))
          },
          testM("leftover init error") {
            val sink = initErrorSink.optional
            assertM(
              (for {
                init     <- sink.initial
                result   <- sink.extract(Step.state(init))
                leftover = Step.leftover(init)
              } yield result -> leftover).run,
              succeeds(equalTo[(Option[Int], Chunk[Nothing])]((None, Chunk.empty)))
            )
          },
          testM("leftover step error") {
            val sink = stepErrorSink.optional
            assertM(
              (for {
                init     <- sink.initial
                step     <- sink.step(Step.state(init), 1)
                result   <- sink.extract(Step.state(step))
                leftover = Step.leftover(step)
              } yield result -> leftover).run,
              succeeds(equalTo[(Option[Int], Chunk[Int])]((None, Chunk.single(1))))
            )
          },
          testM("leftover extract error") {
            val sink = extractErrorSink.optional
            assertM(
              (for {
                init     <- sink.initial
                step     <- sink.step(Step.state(init), 1)
                result   <- sink.extract(Step.state(step))
                leftover = Step.leftover(step)
              } yield result -> leftover).run,
              succeeds(equalTo[(Option[Int], Chunk[Int])]((None, Chunk.empty)))
            )
          },
          testM("leftover init step error") {
            val sink = initErrorSink.optional
            assertM(
              (for {
                init     <- sink.initial
                step     <- sink.step(Step.state(init), 1)
                result   <- sink.extract(Step.state(step))
                leftover = Step.leftover(step)
              } yield result -> leftover).run,
              succeeds(equalTo[(Option[Int], Chunk[Int])]((None, Chunk.single(1))))
            )
          }
        ),
        suite("orElse")(
          testM("left") {
            val sink = ZSink.identity[Int] orElse ZSink.fail("Ouch")
            assertM(sinkIteration(sink, 1).either, isRight(isLeft(equalTo(1))))
          },
          testM("right") {
            val sink = ZSink.fail("Ouch") orElse ZSink.succeed("Hello")
            assertM(sinkIteration(sink, "whatever").either, isRight(isRight(equalTo("Hello"))))
          },
          testM("init error left") {
            val sink = initErrorSink orElse ZSink.succeed("Hello")
            assertM(sinkIteration(sink, 1).either, isRight(isRight(equalTo("Hello"))))
          },
          testM("init error right") {
            val sink = ZSink.identity[Int] orElse initErrorSink
            assertM(sinkIteration(sink, 1).either, isRight(isLeft(equalTo(1))))
          },
          testM("init error both") {
            val sink = initErrorSink orElse initErrorSink
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error left") {
            val sink = stepErrorSink orElse ZSink.succeed("Hello")
            assertM(sinkIteration(sink, 1).either, isRight(isRight(equalTo("Hello"))))
          },
          testM("step error right") {
            val sink = ZSink.identity[Int] orElse stepErrorSink
            assertM(sinkIteration(sink, 1).either, isRight(isLeft(equalTo(1))))
          },
          testM("step error both") {
            val sink = stepErrorSink orElse stepErrorSink
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error left") {
            val sink = extractErrorSink orElse ZSink.succeed("Hello")
            assertM(sinkIteration(sink, 1).either, isRight(isRight(equalTo("Hello"))))
          },
          testM("extract error right") {
            val sink = ZSink.identity[Int] orElse extractErrorSink
            assertM(sinkIteration(sink, 1).either, isRight(isLeft(equalTo(1))))
          },
          testM("extract error both") {
            val sink = extractErrorSink orElse extractErrorSink
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("raceBoth")(
          testM("left") {
            val sink = ZSink.identity[Int] raceBoth ZSink.succeed("Hello")
            assertM(sinkIteration(sink, 1).either, isRight(isLeft(equalTo(1))))
          },
          testM("init error left ") {
            val sink = initErrorSink raceBoth ZSink.identity[Int]
            assertM(sinkIteration(sink, 1).either, isRight(isRight(equalTo(1))))
          },
          testM("init error right") {
            val sink = ZSink.identity[Int] raceBoth initErrorSink
            assertM(sinkIteration(sink, 1).either, isRight(isLeft(equalTo(1))))
          },
          testM("init error both") {
            val sink = initErrorSink race initErrorSink
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          // testM("step error left") {
          //   val sink = stepErrorSink raceBoth ZSink.identity[Int]
          //   assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          // },
          testM("step error right") {
            val sink = ZSink.identity[Int] raceBoth stepErrorSink
            assertM(sinkIteration(sink, 1).either, isRight(isLeft(equalTo(1))))
          },
          testM("step error both") {
            val sink = stepErrorSink race stepErrorSink
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          // testM("extract error left") {
          //   val sink = extractErrorSink raceBoth ZSink.identity[Int]
          //   assertM(sinkIteration(sink, 1).either, isRight(isLeft(equalTo("Ouch"))))
          // },
          testM("extract error right") {
            val sink = ZSink.identity[Int] raceBoth extractErrorSink
            assertM(sinkIteration(sink, 1).either, isRight(isLeft(equalTo(1))))
          },
          testM("extract error both") {
            val sink = extractErrorSink race extractErrorSink
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("takeWhile")(
          testM("happy path") {
            val sink = ZSink.identity[Int].takeWhile[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(1)))
          },
          testM("false predicate") {
            val sink = ZSink.identity[Int].takeWhile[Int](_ > 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo(())))
          },
          testM("init error") {
            val sink = initErrorSink.takeWhile[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.takeWhile[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.takeWhile[Int](_ < 5)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("untilOutput")(
          testM("happy path") {
            val sink = ZSink.collectAll[Int].untilOutput(_.length > 3)
            for {
              init   <- sink.initial
              step1  <- sink.step(Step.state(init), 1)
              step2  <- sink.step(Step.state(step1), 2)
              step3  <- sink.step(Step.state(step2), 3)
              step4  <- sink.step(Step.state(step3), 4)
              result <- sink.extract(Step.state(step4))
            } yield assert(result, isSome(equalTo(List(1, 2, 3, 4))))
          },
          testM("false predicate") {
            val sink = ZSink.identity[Int].untilOutput(_ < 0)
            assertM(sinkIteration(sink, 1).either, isRight(isNone))
          },
          testM("init error") {
            val sink = initErrorSink.untilOutput(_ == 0)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error") {
            val sink = stepErrorSink.untilOutput(_ == 0)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error") {
            val sink = extractErrorSink.untilOutput(_ == 0)
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("zip (<*>)")(
          testM("happy path") {
            val sink = ZSink.identity[Int] <*> ZSink.succeed("Hello")
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(1 -> "Hello")))
          },
          testM("init error left") {
            val sink = initErrorSink <*> ZSink.identity[Int]
            assertM[Any, Nothing, Either[Any, (Int, Int)]](sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("init error right") {
            val sink = ZSink.identity[Int] <*> initErrorSink
            assertM[Any, Nothing, Either[Any, (Int, Int)]](sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("init error both") {
            val sink = initErrorSink <*> initErrorSink
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error left") {
            val sink = stepErrorSink <*> ZSink.identity[Int]
            assertM[Any, Nothing, Either[Any, (Int, Int)]](sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error right") {
            val sink = ZSink.identity[Int] <*> stepErrorSink
            assertM[Any, Nothing, Either[Any, (Int, Int)]](sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("step error both") {
            val sink = stepErrorSink <*> stepErrorSink
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error left") {
            val sink = extractErrorSink <*> ZSink.identity[Int]
            assertM[Any, Nothing, Either[Any, (Int, Int)]](sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error right") {
            val sink = ZSink.identity[Int] <*> extractErrorSink
            assertM[Any, Nothing, Either[Any, (Int, Int)]](sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          },
          testM("extract error both") {
            val sink = extractErrorSink <*> extractErrorSink
            assertM(sinkIteration(sink, 1).either, isLeft(equalTo("Ouch")))
          }
        ),
        suite("zipLeft (<*)")(
          testM("happy path") {
            val sink = ZSink.identity[Int].zipLeft(ZSink.succeed("Hello"))
            assertM(sinkIteration(sink, 1).either, isRight(equalTo(1)))
          }
        ),
        suite("zipPar")(
          testM("happy path 1") {
            val sink1 = ZSink.collectAllWhile[Int](_ < 5)
            val sink2 = ZSink.collectAllWhile[Int](_ < 3)
            ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
          },
          testM("happy path 2") {
            val sink1 = ZSink.collectAllWhile[Int](_ < 5)
            val sink2 = ZSink.collectAllWhile[Int](_ < 30)
            ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
          },
          testM("happy path 3") {
            val sink1 = ZSink.collectAllWhile[Int](_ < 50)
            val sink2 = ZSink.collectAllWhile[Int](_ < 30)
            ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
          },
          testM("extract error") {
            val sink1 = ZSink.collectAllWhile[Int](_ < 5)
            ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, extractErrorSink)
          },
          testM("step error") {
            val sink1 = ZSink.collectAllWhile[Int](_ < 5)
            ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, stepErrorSink)
          },
          testM("init error") {
            val sink1 = ZSink.collectAllWhile[Int](_ < 5)
            ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, initErrorSink)
          },
          testM("both error")(ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), stepErrorSink, initErrorSink)),
          testM("remainder corner case 1") {
            val sink1 = sinkWithLeftover(3, 1, -42)
            val sink2 = sinkWithLeftover(2, 4, -42)
            ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
          },
          testM("remainder corner case 2") {
            val sink1 = sinkWithLeftover(2, 3, -42)
            val sink2 = sinkWithLeftover(2, 4, -42)
            ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
          }
        ),
        suite("zipRight (*>)")(
          testM("happy path") {
            val sink = ZSink.identity[Int].zipRight(ZSink.succeed("Hello"))
            assertM(sinkIteration(sink, 1).either, isRight(equalTo("Hello")))
          }
        ),
        suite("zipWith")(
          testM("happy path") {
            val sink = ZSink.identity[Int].zipWith(ZSink.succeed("Hello"))((x, y) => x.toString + y.toString)
            assertM(sinkIteration(sink, 1).either, isRight(equalTo("1Hello")))
          }
        ),
        suite("Constructors")(
          testM("short circuits") {
            val empty: Stream[Nothing, Int]     = ZStream.empty
            val single: Stream[Nothing, Int]    = ZStream.succeed(1)
            val double: Stream[Nothing, Int]    = ZStream(1, 2)
            val failed: Stream[String, Nothing] = ZStream.fail("Ouch")

            def run[E](stream: Stream[E, Int]) =
              (for {
                effects <- Ref.make[List[Int]](Nil)
                sink = ZSink.foldM[Any, Nothing, Int, Int, Int](0) { (_, a) =>
                  effects.update(a :: _) *> ZIO.succeed(Step.done(30, Chunk.empty))
                }
                exit <- stream.run(sink)
                n    <- effects.get
              } yield (exit, n)).run

            for {
              res1 <- run(empty)
              res2 <- run(single)
              res3 <- run(double)
              res4 <- run(failed)
            } yield {
              assert(res1, succeeds(equalTo((0, List.empty[Int])))) &&
              assert(res2, succeeds(equalTo((30, List(1))))) &&
              assert(res3, succeeds(equalTo((30, List(1))))) &&
              assert(res4, fails(equalTo("Ouch")))
            }
          },
          // testM("foldLeft") {
          //   val fn = Gen.function[Random with Sized, (String, String), String](Gen.anyString)
          //   checkM(streamOfStrings, fn, Gen.anyString) { (s, f, z) =>
          //     val ft = Function.untupled(f)
          //     for {
          //       res1 <- s.run(ZSink.foldLeft(z)(ft))
          //       res2 <- s.runCollect.map(_.foldLeft(z)(ft))
          //     } yield assert(res1, equalTo(res2))
          //   }
          // },
          // testM("fold") {
          //   checkM(streamOfInts, tupleToStringFn, Gen.anyString) { (s, f, z) =>
          //     val ff = (acc: String, el: Int) => Step.more(f(acc -> el))
          //     val ft = Function.untupled(f)
          //     for {
          //       res1 <- s.run(ZSink.fold(z)(ff))
          //       res2 <- s.runCollect.map(_.foldLeft(z)(ft))
          //     } yield assert(res1, equalTo(res2))
          //   }
          // },
          // testM("foldM") {
          //   val fn = Gen.function[Random with Sized, (String, String), String](Gen.anyString)
          //   checkM(streamOfInts, fn, Gen.const(ZIO.unit)) { (s, f, z) =>
          //     val ff = (acc: String, el: Int) => f(acc, el).map(Step.more)
          //     for {
          //       sinkResult <- z.flatMap(z => s.run(ZSink.foldM(z)(ff)))
          //       foldResult <- s.foldLeft(List[Int]())((acc, el) => el :: acc)
          //                      .map(_.reverse)
          //                      .flatMap(_.foldLeft(z)((acc, el) => acc.flatMap(f(_, el))))
          //     } yield assert(foldResult, succeeds(equalTo(()))) implies assert(sinkResult, equalTo(foldResult))
          //   }
          // },
          testM("collectAllN") {
            assertM(Stream[Int](1, 2, 3).run(Sink.collectAllN[Int](2)), equalTo(List(1, 2)))
          },
          testM("collectAllToSet") {
            assertM(Stream[Int](1, 2, 3, 3, 4).run(Sink.collectAllToSet[Int]), equalTo(Set(1, 2, 3, 4)))
          },
          testM("collectAllToSetN") {
            assertM(Stream[Int](1, 2, 1, 2, 3, 3, 4).run(Sink.collectAllToSetN[Int](3)), equalTo(Set(1, 2, 3)))
          },
          testM("collectAllToMap") {
            assertM(
              Stream[Int](1, 2, 3).run(Sink.collectAllToMap[Int, Int](value => value)),
              equalTo(Map[Int, Int](1 -> 1, 2 -> 2, 3 -> 3))
            )
          },
          testM("collectAllToMapN") {
            assertM(
              Stream[Int](1, 2, 3, 4, 5, 6).run(Sink.collectAllToMapN[Int, Int](2)(value => value % 2)),
              equalTo(Map[Int, Int](1 -> 1, 0 -> 2))
            )
          },
          // testM("collectAllWhile") {
          //   checkM(streamOfStrings, stringToBoolFn) { (s, f) =>
          //     for {
          //       res1 <- s.run(ZSink.collectAllWhile(f)).run
          //       res2 <- s.runCollect.map(_.takeWhile(f)).run
          //     } yield assert(res1, succeeds(equalTo(List.empty[Int]))) ==> assert(res1, equalTo(res2))
          //   }
          // },
          testM("foldWeighted") {
            assertM(
              Stream[Long](1, 5, 2, 3)
                .transduce(Sink.foldWeighted(List[Long]())((_: Long) * 2, 12)((acc, el) => el :: acc).map(_.reverse))
                .runCollect,
              equalTo(List(List(1L, 5L), List(2L, 3L)))
            )
          },
          testM("foldWeightedM") {
            assertM(
              Stream[Long](1, 5, 2, 3)
                .transduce(
                  Sink
                    .foldWeightedM(List[Long]())((a: Long) => UIO.succeed(a * 2), 12)(
                      (acc, el) => UIO.succeed(el :: acc)
                    )
                    .map(_.reverse)
                )
                .runCollect,
              equalTo(List(List(1L, 5L), List(2L, 3L)))
            )
          },
          testM("foldUntil") {
            assertM(
              Stream[Long](1, 1, 1, 1, 1, 1)
                .transduce(Sink.foldUntil(0L, 3)(_ + (_: Long)))
                .runCollect,
              equalTo(List(3L, 3L))
            )
          },
          testM("foldUntilM") {
            assertM(
              Stream[Long](1, 1, 1, 1, 1, 1)
                .transduce(Sink.foldUntilM(0L, 3)((s, a: Long) => UIO.succeed(s + a)))
                .runCollect,
              equalTo(List(3L, 3L))
            )
          },
          testM("fromFunction") {
            assertM(
              Stream(1, 2, 3, 4, 5)
                .transduce(Sink.fromFunction[Int, String](_.toString))
                .runCollect
                .either,
              isRight(equalTo(List("1", "2", "3", "4", "5")))
            )
          },
          testM("fromOutputStream") {
            import java.io.ByteArrayOutputStream

            val output = new ByteArrayOutputStream()
            val data   = "0123456789"
            val stream = Stream(Chunk.fromArray(data.take(5).getBytes), Chunk.fromArray(data.drop(5).getBytes))

            stream.run(ZSink.fromOutputStream(output)).either map { bytesWritten =>
              assert(bytesWritten, isRight(equalTo(10))) && assert(
                new String(output.toByteArray, "UTF-8"),
                equalTo(data)
              )
            }
          },
          testM("pull1") {
            val stream = Stream.fromIterable(List(1))
            val sink   = Sink.pull1(IO.succeed(None: Option[Int]))((i: Int) => Sink.succeed(Some(i): Option[Int]))

            assertM(stream.run(sink), isSome(equalTo(1)))
          }
        ),
        suite("splitLines")(
          testM("preserves data") {
            val listOfStringGen = {
              val charGen = Gen.printableChar.filter(c => c != '\n' && c != '\r')
              Gen.listOf(Gen.string(charGen)).map(l => if (l.nonEmpty && l.last == "") l ++ List("a") else l)
            }

            checkM(listOfStringGen) { lines =>
              val data = lines.mkString("\n")

              for {
                initial      <- ZSink.splitLines.initial.map(Step.state(_))
                middle       <- ZSink.splitLines.step(initial, data)
                result       <- ZSink.splitLines.extract(Step.state(middle))
                sinkLeftover = Step.leftover(middle)
              } yield assert((result ++ sinkLeftover).toArray[String].mkString("\n"), equalTo(lines.mkString("\n")))
            }
          },
          testM("handles leftovers") {
            for {
              initial      <- ZSink.splitLines.initial.map(Step.state(_))
              middle       <- ZSink.splitLines.step(initial, "abc\nbc")
              result       <- ZSink.splitLines.extract(Step.state(middle))
              sinkLeftover = Step.leftover(middle)
            } yield {
              assert(result.toArray[String].mkString("\n"), equalTo("abc")) &&
              assert(sinkLeftover.toArray[String].mkString, equalTo("bc"))
            }
          },
          testM("transduces") {
            assertM(
              Stream("abc", "\n", "bc", "\n", "bcd", "bcd")
                .transduce(ZSink.splitLines)
                .runCollect,
              equalTo(List(Chunk("abc"), Chunk("bc"), Chunk("bcdbcd")))
            )
          },
          testM("single newline edgecase") {
            assertM(
              Stream("\n")
                .transduce(ZSink.splitLines)
                .mapConcat(identity)
                .runCollect,
              equalTo(List(""))
            )
          },
          testM("no newlines in data") {
            assertM(
              Stream("abc", "abc", "abc")
                .transduce(ZSink.splitLines)
                .mapConcat(identity)
                .runCollect,
              equalTo(List("abcabcabc"))
            )
          },
          testM("\\r\\n on the boundary") {
            assertM(
              Stream("abc\r", "\nabc")
                .transduce(ZSink.splitLines)
                .mapConcat(identity)
                .runCollect,
              equalTo(List("abc", "abc"))
            )
          }
        ),
        testM("throttleEnforce") {
          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Option[Int]]) =
            for {
              init1 <- sink.initial
              step1 <- sink.step(Step.state(init1), 1)
              res1  <- sink.extract(Step.state(step1))
              init2 <- sink.initial
              _     <- MockClock.adjust(23.milliseconds)
              step2 <- sink.step(Step.state(init2), 2)
              res2  <- sink.extract(Step.state(step2))
              init3 <- sink.initial
              step3 <- sink.step(Step.state(init3), 3)
              res3  <- sink.extract(Step.state(step3))
              init4 <- sink.initial
              step4 <- sink.step(Step.state(init4), 4)
              res4  <- sink.extract(Step.state(step4))
              _     <- MockClock.adjust(11.milliseconds)
              init5 <- sink.initial
              step5 <- sink.step(Step.state(init5), 5)
              res5  <- sink.extract(Step.state(step5))
            } yield assert(List(res1, res2, res3, res4, res5), equalTo(List(Some(1), Some(2), None, None, Some(5))))

          for {
            clock <- MockClock.make(MockClock.DefaultData)
            test <- ZSink
                     .throttleEnforce[Int](1, 10.milliseconds)(_ => 1)
                     .use(sinkTest)
                     .provide(clock)
          } yield test
        },
        testM("with burst") {
          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Option[Int]]) =
            for {
              init1 <- sink.initial
              step1 <- sink.step(Step.state(init1), 1)
              res1  <- sink.extract(Step.state(step1))
              init2 <- sink.initial
              _     <- MockClock.adjust(23.milliseconds)
              step2 <- sink.step(Step.state(init2), 2)
              res2  <- sink.extract(Step.state(step2))
              init3 <- sink.initial
              step3 <- sink.step(Step.state(init3), 3)
              res3  <- sink.extract(Step.state(step3))
              init4 <- sink.initial
              step4 <- sink.step(Step.state(init4), 4)
              res4  <- sink.extract(Step.state(step4))
              _     <- MockClock.adjust(11.milliseconds)
              init5 <- sink.initial
              step5 <- sink.step(Step.state(init5), 5)
              res5  <- sink.extract(Step.state(step5))
            } yield assert(List(res1, res2, res3, res4, res5), equalTo(List(Some(1), Some(2), Some(3), None, Some(5))))

          for {
            clock <- MockClock.make(MockClock.DefaultData)
            test <- ZSink
                     .throttleEnforce[Int](1, 10.milliseconds, 1)(_ => 1)
                     .use(sinkTest)
                     .provide(clock)
          } yield test
        },
        testM("throttleShape") {
          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
            for {
              init1 <- sink.initial
              step1 <- sink.step(Step.state(init1), 1)
              res1  <- sink.extract(Step.state(step1))
              init2 <- sink.initial
              step2 <- sink.step(Step.state(init2), 2)
              res2  <- sink.extract(Step.state(step2))
              init3 <- sink.initial
              _     <- clock.sleep(4.seconds)
              step3 <- sink.step(Step.state(init3), 3)
              res3  <- sink.extract(Step.state(step3))
            } yield assert(List(res1, res2, res3), equalTo(List(1, 2, 3)))

          for {
            clock <- MockClock.make(MockClock.DefaultData)
            fiber <- ZSink
                      .throttleShape[Int](1, 1.second)(_.toLong)
                      .use(sinkTest)
                      .provide(clock)
                      .fork
            _    <- clock.clock.adjust(8.seconds)
            test <- fiber.join
          } yield test
        },
        testM("infinite bandwidth") {
          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
            for {
              init1   <- sink.initial
              step1   <- sink.step(Step.state(init1), 1)
              res1    <- sink.extract(Step.state(step1))
              init2   <- sink.initial
              step2   <- sink.step(Step.state(init2), 2)
              res2    <- sink.extract(Step.state(step2))
              elapsed <- clock.currentTime(TimeUnit.SECONDS)
            } yield assert(elapsed, equalTo(0L)) && assert(List(res1, res2), equalTo(List(1, 2)))

          for {
            clock <- MockClock.make(MockClock.DefaultData)
            test <- ZSink
                     .throttleShape[Int](1, 0.seconds)(_ => 100000L)
                     .use(sinkTest)
                     .provide(clock)
          } yield test
        },
        testM("with burst") {
          def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
            for {
              init1 <- sink.initial
              step1 <- sink.step(Step.state(init1), 1)
              res1  <- sink.extract(Step.state(step1))
              init2 <- sink.initial
              _     <- MockClock.adjust(2.seconds)
              step2 <- sink.step(Step.state(init2), 2)
              res2  <- sink.extract(Step.state(step2))
              init3 <- sink.initial
              _     <- MockClock.adjust(4.seconds)
              _     <- clock.sleep(4.seconds)
              step3 <- sink.step(Step.state(init3), 3)
              res3  <- sink.extract(Step.state(step3))
            } yield assert(List(res1, res2, res3), equalTo(List(1, 2, 3)))

          for {
            clock <- MockClock.make(MockClock.DefaultData)
            fiber <- ZSink
                      .throttleShape[Int](1, 1.second, 2)(_.toLong)
                      .use(sinkTest)
                      .provide(clock)
                      .fork
            test <- fiber.join
          } yield test
        },
        testM("utf8Decode") {
          checkM(Gen.anyString) { s =>
            assertM(
              Stream
                .fromIterable(s.getBytes("UTF-8"))
                .transduce(ZSink.utf8Decode())
                .runCollect
                .map(_.mkString),
              equalTo(s)
            )
          }
        },
        suite("utf8DecodeChunk")(
          testM("regular strings") {
            checkM(Gen.anyString) { s =>
              assertM(
                Stream(Chunk.fromArray(s.getBytes("UTF-8")))
                  .transduce(ZSink.utf8DecodeChunk)
                  .runCollect
                  .map(_.mkString),
                equalTo(s)
              )
            }
          },
          testM("incomplete chunk 1") {
            for {
              init   <- ZSink.utf8DecodeChunk.initial.map(Step.state(_))
              state1 <- ZSink.utf8DecodeChunk.step(init, Chunk(0xC2.toByte))
              state2 <- ZSink.utf8DecodeChunk.step(Step.state(state1), Chunk(0xA2.toByte))
              string <- ZSink.utf8DecodeChunk.extract(Step.state(state2))
            } yield {
              assert(Step.cont(state1), isTrue) &&
              assert(Step.cont(state2), isFalse) &&
              // ZIO TEST: array equality
              assert(string.getBytes("UTF-8").toList, equalTo(List(0xC2.toByte, 0xA2.toByte)))
            }
          },
          testM("incomplete chunk 2") {
            for {
              init   <- ZSink.utf8DecodeChunk.initial.map(Step.state(_))
              state1 <- ZSink.utf8DecodeChunk.step(init, Chunk(0xE0.toByte, 0xA4.toByte))
              state2 <- ZSink.utf8DecodeChunk.step(Step.state(state1), Chunk(0xB9.toByte))
              string <- ZSink.utf8DecodeChunk.extract(Step.state(state2))
            } yield {
              assert(Step.cont(state1), isTrue) &&
              assert(Step.cont(state2), isFalse) &&
              // ZIO TEST: array equality
              assert(string.getBytes("UTF-8").toList, equalTo(List(0xE0.toByte, 0xA4.toByte, 0xB9.toByte)))
            }
          },
          testM("incomplete chunk 3") {
            for {
              init   <- ZSink.utf8DecodeChunk.initial.map(Step.state(_))
              state1 <- ZSink.utf8DecodeChunk.step(init, Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte))
              state2 <- ZSink.utf8DecodeChunk.step(Step.state(state1), Chunk(0x88.toByte))
              string <- ZSink.utf8DecodeChunk.extract(Step.state(state2))
            } yield {
              assert(Step.cont(state1), isTrue) &&
              assert(Step.cont(state2), isFalse) &&
              // ZIO TEST: array equality
              assert(string.getBytes("UTF-8").toList, equalTo(List(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte)))
            }
          },
          testM("chunk with leftover") {
            for {
              init <- ZSink.utf8DecodeChunk.initial.map(Step.state(_))
              state1 <- ZSink.utf8DecodeChunk
                         .step(
                           init,
                           Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte, 0xF0.toByte, 0x90.toByte)
                         )
            } yield {
              assert(Step.cont(state1), isFalse) &&
              // ZIO TEST: array equality
              assert(
                Step.leftover(state1).flatMap(identity).toArray[Byte].toList,
                equalTo(List(0xF0.toByte, 0x90.toByte))
              )
            }
          }
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
                .foldM((ParserState.Start: ParserState, List.empty[Int])) {
                  (s, a: Char) =>
                    s match {
                      case (ParserState.Start, acc) =>
                        a match {
                          case a if a.isWhitespace => IO.succeed(ZSink.Step.more((ParserState.Start, acc)))
                          case '['                 => IO.succeed(ZSink.Step.more((ParserState.Element(""), acc)))
                          case _                   => IO.fail("Expected '['")
                        }

                      case (ParserState.Element(el), acc) =>
                        a match {
                          case a if a.isDigit => IO.succeed(ZSink.Step.more((ParserState.Element(el + a), acc)))
                          case ','            => IO.succeed(ZSink.Step.more((ParserState.Element(""), acc :+ el.toInt)))
                          case ']'            => IO.succeed(ZSink.Step.done((ParserState.Done, acc :+ el.toInt), Chunk.empty))
                          case _              => IO.fail("Expected a digit or ,")
                        }

                      case (ParserState.Done, acc) =>
                        IO.succeed(ZSink.Step.done((ParserState.Done, acc), Chunk.empty))
                    }
                }
                .map(_._2)
                .chunked

            val src1 = ZStreamChunk.succeed(Chunk.fromArray(Array('[', '1', '2')))
            val src2 = ZStreamChunk.succeed(Chunk.fromArray(Array('3', ',', '4', ']')))

            assertM(
              (for {
                partialParse <- src1.run(numArrayParser)
                fullParse    <- (src1 ++ src2).run(numArrayParser)
              } yield partialParse -> fullParse).run,
              succeeds(equalTo((List.empty[Int], List(123, 4))))
            )
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

            val src1 = ZStreamChunk.succeed(Chunk.fromArray(Array('[', '1', '2')))
            val src2 = ZStreamChunk.succeed(Chunk.fromArray(Array('3', ',', '4', ']')))

            for {
              partialParse <- src1.run(start.chunked).run
              fullParse    <- (src1 ++ src2).run(start.chunked).run
            } yield {
              assert(partialParse, fails(equalTo(("Expected closing brace; instead: None")))) &&
              assert(fullParse, succeeds(equalTo(List(123, 4))))
            }
          }
        )
      )
    )
