package zio.stream.experimental

import zio.ZIOBaseSpec
import zio.test.Assertion.equalTo
import zio.test.{ assertM, suite, testM }

object ZSinkExperiment extends ZIOBaseSpec {

  def spec =
    suite("ZSinkExperiment")(
      suite("Combinators")(),
      suite("Constructors")(
        testM("collectSet")(assertM(ZStream(1, 2, 3, 3, 4).run(ZSink.collectSet[Int]))(equalTo(Set(1, 2, 3, 4))))
      )
    )
}
