package zio.autowire

import zio.{test => _, _}
import zio.test._

import java.io.IOException

// https://github.com/kitlangton/zio-magic/issues/91
object ProvideSomeSpec extends ZIOSpecDefault {

  final case class TestService(console: Console, clock: Clock) {
    def somethingMagical(annotate: String): ZIO[Any, IOException, Unit] =
      for {
        _ <- console.printLine(s"[$annotate] Imma chargin' my fireball!!!")
        _ <- clock.sleep(1500.milliseconds)
        _ <- console.printLine(s"[$annotate] Counterspell!")
      } yield ()
  }

  object TestService {
    val live: ZLayer[Clock with Console, Nothing, TestService] =
      (TestService.apply _).toLayer
  }

  val partial: ZLayer[Console, Nothing, Clock with Console with TestService] =
    (Clock.live ++ ZLayer.service[Console]) >+> TestService.live

  val partialLayer: ZLayer[Console, Nothing, TestService with Clock] =
    ZLayer.makeSome[Console, TestService with Clock](
      Clock.live,
      TestService.live
    )

  def testCase(annotate: String): ZIO[TestService with Clock with Console, IOException, TestResult] =
    for {
      service <- ZIO.service[TestService]
      _       <- service.somethingMagical(annotate)
      _       <- ZIO.sleep(10.millis)
      _       <- Console.printLine(s"[$annotate] ...")

    } yield assertCompletes

  def spec: ZSpec[Console with TestConsole with Annotations, Any] =
    suite("provideSomeSpec")(
      test("basic") {
        testCase("basic").provideSomeLayer[Console](partial)
      },
      test("provideSome") {
        testCase("provideSome").provideSome[Console](
          Clock.live,
          TestService.live
        )
      },
      test("double provideSome") {
        testCase("double provideSome")
          .provideSome[Console with Clock](
            TestService.live
          )
          .provideSome[Console](Clock.live)
      },
      test("makeSome") {
        testCase("makeSome").provideSome[Console](partialLayer)
      }
    ) @@ TestAspect.silent
}
