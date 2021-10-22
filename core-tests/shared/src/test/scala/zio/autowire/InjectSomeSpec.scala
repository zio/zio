package zio.autowire

import zio._
import zio.test._

import java.io.IOException
import zio.test.environment.TestConsole

// https://github.com/kitlangton/zio-magic/issues/91
object InjectSomeSpec extends DefaultRunnableSpec {

  final case class TestService(console: Console, clock: Clock) {
    def somethingMagical(annotate: String): ZIO[Any, IOException, Unit] =
      for {
        _ <- console.printLine(s"[$annotate] Imma chargin' my fireball!!!")
        _ <- clock.sleep(1500.milliseconds)
        _ <- console.printLine(s"[$annotate] Counterspell!")
      } yield ()
  }

  object TestService {
    val live: ZDeps[Has[Clock] with Has[Console], Nothing, Has[TestService]] =
      (TestService.apply _).toDeps
  }

  val partial: ZDeps[Has[Console], Nothing, Has[Clock] with Has[Console] with Has[TestService]] =
    (Clock.live ++ ZDeps.service[Console]) >+> TestService.live

  val partialLayer: ZDeps[Has[Console], Nothing, Has[TestService] with Has[Clock]] =
    ZDeps.wireSome[Has[Console], Has[TestService] with Has[Clock]](
      Clock.live,
      TestService.live
    )

  def testCase(annotate: String): ZIO[Has[TestService] with Has[Clock] with Has[Console], IOException, TestResult] =
    for {
      service <- ZIO.service[TestService]
      _       <- service.somethingMagical(annotate)
      _       <- ZIO.sleep(10.millis)
      _       <- Console.printLine(s"[$annotate] ...")

    } yield assertCompletes

  def spec: ZSpec[Has[Console] with Has[TestConsole], Any] =
    suite("InjectSomeSpec")(
      test("basic") {
        testCase("basic").provideSomeDeps[Has[Console]](partial)
      },
      test("injectSome") {
        testCase("injectSome").injectSome[Has[Console]](
          Clock.live,
          TestService.live
        )
      },
      test("double injectSome") {
        testCase("double injectSome")
          .injectSome[Has[Console] with Has[Clock]](
            TestService.live
          )
          .injectSome[Has[Console]](Clock.live)
      },
      test("wireSome") {
        testCase("wireSome").provideSomeDeps[Has[Console]](partialLayer)
      }
    ) @@ TestAspect.silent
}
