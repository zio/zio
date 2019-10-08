package zio.examples.test

import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.examples.test.Aspects._
import zio.random.Random
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.duration._
import zio.{ UIO, ZIO, ZManaged }

private object Aspects {

  class FakeFile(name: String) {

    def close(): UIO[Unit] = ZIO.accessM[Console](_.console.putStrLn(s"Closing $name")).provide(Console.Live)
  }

  object FakeFile {

    def create(name: String): UIO[FakeFile] = ZIO.succeed(new FakeFile(name))

  }

  val managed                                                       = ZManaged.make(FakeFile.create("file.txt"))(_.close())
  val managedAssertion: TestResult => ZIO[Any, Nothing, TestResult] = ZIO.succeed

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

            assertM(pipeline, equalTo(())).provide(Console.Live)

          }

        },
//        around(managed.map(_ => managedAssertion)) {
//
//          testM("Around test") {
//            assertM(ZIO.succeed(10), equalTo(10))
//          }
//
//        },
        eventually {
          testM("Intermittent test") {

            def isEven(n: Long) = n % 2 == 0

            val pipeline = ZIO
              .accessM[Clock] { env =>
                for {
                  currentTime <- env.clock.nanoTime
                } yield isEven(currentTime)

              }
              .provide(Clock.Live)

            assertM(pipeline, isTrue)

          }

        },
        nonFlaky(5) {
          testM("non-flaky test") {

            val chars: ZStream[Random, Nothing, Sample[Random, Char]] = Gen.printableChar.filter(_.isUpper).sample

            assertM(chars
                      .mapM(v => putStrLn(v.value.toString).provide(Console.Live) *> ZIO.succeed(v.value.isUpper))
                      .runCollect,
                    forall(isTrue))
          }
        },
        timeout(3.seconds) {
          testM("Timeout test (will fail)") {

            val blockingEffect = ZIO
              .accessM[Blocking](_.blocking.effectBlocking { Thread.sleep(5000); "value" })
              .either
              .provide(Blocking.Live)

            assertM(blockingEffect, isRight(equalTo("value")))

          }
        },
      )
    )
