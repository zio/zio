package zio.examples.test

import zio.clock.Clock
import zio.console.Console
import zio.examples.test.Aspects._
import zio.test.TestAspect._
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, Predicate, TestResult }
import zio.{ UIO, ZIO, ZManaged }

private object Aspects {

  class FakeFile(name: String) {

    def close(): UIO[Unit] = ZIO.accessM[Console](_.console.putStrLn(s"Closing $name")).provide(Console.Live)
  }

  object FakeFile {

    def create(name: String): UIO[FakeFile] = ZIO.succeed(new FakeFile(name))

  }

  val managed                                                = ZManaged.make(FakeFile.create("file.txt"))(_.close())
  val assertion: TestResult => ZIO[Any, Nothing, TestResult] = ZIO.succeed

}

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
        around(managed.map(_ => assertion)) {

          testM("Around (constraint environment) test") {
            assertM(ZIO.succeed(10), Predicate.equals(10))
          }

        },
        eventually {
          testM("Intermittent test") {

            def isEven(n: Long) = n % 2 == 0

            val pipeline = ZIO
              .accessM[Clock] { env =>
                for {
                  currentTime <- env.clock.nanoTime
                  _           <- ZIO.accessM[Console](_.console.putStrLn(s"Current time: $currentTime")).provide(Console.Live)
                } yield isEven(currentTime)

              }
              .provide(Clock.Live)

            assertM(pipeline, Predicate.isTrue)

          }

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
        }
      )
    )
