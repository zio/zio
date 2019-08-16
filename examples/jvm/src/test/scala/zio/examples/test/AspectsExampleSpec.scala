package zio.examples.test

import zio.ZIO
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, Predicate }

object AspectsExampleSpec
    extends DefaultRunnableSpec(
      suite("Aspect examples")(
        testM("Before (preparation) test") {
          assertM(ZIO.succeed(10), Predicate.equals(10))
        },
        testM("After (cleaning) test") {
          assertM(ZIO.succeed(10), Predicate.equals(10))
        },
        testM("Around (constraint environment) test") {
          assertM(ZIO.succeed(10), Predicate.equals(10))
        },
        testM("Intermittent test") {
          assertM(ZIO.succeed(10), Predicate.equals(10))
        },
        testM("Non intermittent test") {
          assertM(ZIO.succeed(10), Predicate.equals(10))
        },
        testM("Timeout test") {
          assertM(ZIO.succeed(10), Predicate.equals(10))
        },
        testM("Retry N test") {
          assertM(ZIO.succeed(10), Predicate.equals(10))
        },
        testM("Retry until succeeds") {
          assertM(ZIO.succeed(10), Predicate.equals(10))
        },
      )
    )
