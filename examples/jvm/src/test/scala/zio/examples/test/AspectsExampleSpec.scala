package zio.examples.test

import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.examples.test.Aspects._
import zio.random.Random
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{ DefaultRuntime, Ref, ZIO }

private object Aspects {

  sealed trait Status

  case object Created   extends Status
  case object Ready     extends Status
  case object Happening extends Status
  case object Done      extends Status

  case class RefState(current: Status, previous: List[Status]) {

    def newState(next: Status) = RefState(next, current +: previous)

  }

  private val runtime = new DefaultRuntime {}

  val zRef: Ref[RefState] = runtime.unsafeRun(Ref.make(RefState(Created, Nil)))

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
        sequential {
          suite("Sequential Suite ")(
            around(zRef.update(_.newState(Ready)), zRef.update(_.newState(Done))) {
              testM("Around test") {

                val effect = zRef.update(_.newState(Happening))

                assertM(effect, equalTo(RefState(Happening, List(Ready, Created))))

              }
            },
            identity {
              testM("Identity test (around validation)") {
                val effect = zRef.get
                assertM(effect, equalTo(RefState(Done, List(Happening, Ready, Created))))
              }
            }
          )
        },
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
          testM("Non-flaky test") {

            val chars: ZStream[Random, Nothing, Sample[Random, Char]] = Gen.printableChar.filter(_.isUpper).sample

            assertM(
              chars
                .mapM(v => putStrLn(v.value.toString).provide(Console.Live) *> ZIO.succeed(v.value.isUpper))
                .runCollect,
              forall(isTrue)
            )
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
        testM("Ignore test (alternative style with @@)") {

          assertM(ZIO.succeed(1), equalTo(2))

        } @@ zio.test.TestAspect.ignore
      )
    )
