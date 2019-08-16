package zio.examples.test

import zio.ZIO
import zio.console.Console
import zio.test.TestAspect._
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, Predicate }

object AspectsExampleSpec
    extends DefaultRunnableSpec(
      suite("Aspect examples")(
        (before(putStrLn("Applying migrations")) >>> after(putStrLn("Cleaning database..."))) {
          testM("Before and After aspects") {

            val pipeline
              : ZIO[Console, Nothing, Unit] = putStrLn("Saving data...") *> putStrLn("Querying data...") *> putStrLn(
              "Updating data..."
            )

            assertM[Console, Unit](pipeline, Predicate.equals(())).provide(Console.Live)

          }

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
