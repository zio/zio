package zio.stream

import scala.{ Stream => _ }

import zio._
import zio.test.Assertion.equalTo
import zio.test._

object SinkUtilsSpec extends ZIOBaseSpec {

  def spec = suite("SinkUtilsSpec")(
    suite("SinkUtils.sinkWithLeftover")(
      testM("happy") {
        Stream(1, 2, 3, 4, 5, 6)
          .run(SinkUtils.sinkWithLeftover(3, 2, -42).zip(Sink.collectAll[Int]))
          .map(result => assert(result)(equalTo((3, List(4, 5, 6)))))
      }
    )
  )
}
