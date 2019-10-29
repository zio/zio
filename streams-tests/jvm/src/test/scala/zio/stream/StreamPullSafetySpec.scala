package zio.stream

import zio._
import zio.test._
import zio.test.Assertion.equalTo
import StreamUtils.threePulls

object StreamPullSafetySpec
    extends ZIOBaseSpec(
      suite("StreamPullSafetySpec")(
        testM("Stream.empty is safe to pull again") {
          Stream.empty.process
            .use(threePulls(_))
            .map(assert(_, equalTo(List(Left(None), Left(None), Left(None)))))
        },
        testM("Stream.fail is safe to pull again") {
          Stream
            .fail("Ouch")
            .process
            .use(threePulls(_))
            .map(assert(_, equalTo(List(Left(Some("Ouch")), Left(None), Left(None)))))
        }
      )
    )
