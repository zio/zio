package zio.stream.experimental

import ZSinkUtils._

import zio._
import zio.test.Assertion.equalTo
import zio.test._

object ZSinkSpec extends ZIOBaseSpec {
  def spec = suite("ZSinkSpec")(
    suite("Constructors")(
      suite("collectAll")(
        testM("happy path") {
          val sink = ZSink.collectAll[Int]
          assertM(sinkIteration(sink, 1))(equalTo(List(1)))
        }
        // TODO uncomment when combinators migrated
        // testM("init error") {
        //   val sink = initErrorSink.collectAll
        //   assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        // },
        // testM("step error") {
        //   val sink = stepErrorSink.collectAll
        //   assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        // },
        // testM("extract error") {
        //   val sink = extractErrorSink.collectAll
        //   assertM(sinkIteration(sink, 1).either)(isLeft(equalTo("Ouch")))
        // },
        // testM("interaction with succeed") {
        //   val sink = ZSink.succeed[Int, Int](5).collectAll
        //   for {
        //     init <- sink.initial
        //     s <- sink
        //           .step(init, 1)
        //           .flatMap(sink.step(_, 2))
        //           .flatMap(sink.step(_, 3))
        //     result <- sink.extract(s)
        //   } yield assert(result)(equalTo(List(5, 5, 5, 5) -> Chunk(1, 2, 3)))
        // },
        // testM("interaction with ignoreWhile") {
        //   val sink = ZSink.ignoreWhile[Int](_ < 5).collectAll
        //   for {
        //     result <- sink.initial
        //                .flatMap(sink.step(_, 1))
        //                .flatMap(sink.step(_, 2))
        //                .flatMap(sink.step(_, 3))
        //                .flatMap(sink.step(_, 5))
        //                .flatMap(sink.step(_, 6))
        //                .flatMap(sink.extract)
        //   } yield assert(result)(equalTo(List((), ()) -> Chunk(5, 6)))
        // }
      )
    )
  )
}
