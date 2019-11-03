package zio.stream

import zio._
import zio.test.Assertion._
import zio.test._

import scala.{ Stream => _ }

object SinkUtilsSpec
    extends ZIOBaseSpec(
      suite("SinkUtilsSpec")(
        suite("SinkUtils.sinkWithLeftover")(testM("happy") {
          Stream(1, 2, 3, 4, 5, 6)
            .run(SinkUtils.sinkWithLeftover(3, 2, -42).zip(Sink.collectAll[Int]))
            .map(result => {
              assert(result, equalTo((3, List(4, 5, 6))))
            })
        })
      )
    )
