// package zio.autowire

// import zio.{test => _, _}
// import zio.test._

// import java.io.IOException

// // https://github.com/kitlangton/zio-magic/issues/91
// object InjectSomeSpec extends DefaultRunnableSpec {

//   final case class TestService(console: Console, clock: Clock) {
//     def somethingMagical(annotate: String): ZIO[Any, IOException, Unit] =
//       for {
//         _ <- console.printLine(s"[$annotate] Imma chargin' my fireball!!!")
//         _ <- clock.sleep(1500.milliseconds)
//         _ <- console.printLine(s"[$annotate] Counterspell!")
//       } yield ()
//   }

//   object TestService {
//     val live: ZServiceBuilder[Clock with Console, Nothing, TestService] =
//       (TestService.apply _).toServiceBuilder
//   }

//   val partial: ZServiceBuilder[Console, Nothing, Clock with Console with TestService] =
//     (Clock.live ++ ZServiceBuilder.service[Console]) >+> TestService.live

//   val partialServiceBuilder: ZServiceBuilder[Console, Nothing, TestService with Clock] =
//     ZServiceBuilder.wireSome[Console, TestService with Clock](
//       Clock.live,
//       TestService.live
//     )

//   def testCase(annotate: String): ZIO[TestService with Clock with Console, IOException, TestResult] =
//     for {
//       service <- ZIO.service[TestService]
//       _       <- service.somethingMagical(annotate)
//       _       <- ZIO.sleep(10.millis)
//       _       <- Console.printLine(s"[$annotate] ...")

//     } yield assertCompletes

//   def spec: ZSpec[Console with TestConsole, Any] =
//     suite("InjectSomeSpec")(
//       test("basic") {
//         testCase("basic").provideSomeServices[Console](partial)
//       },
//       test("injectSome") {
//         testCase("injectSome").injectSome[Console](
//           Clock.live,
//           TestService.live
//         )
//       },
//       test("double injectSome") {
//         testCase("double injectSome")
//           .injectSome[Console with Clock](
//             TestService.live
//           )
//           .injectSome[Console](Clock.live)
//       },
//       test("wireSome") {
//         testCase("wireSome").provideSomeServices[Console](partialServiceBuilder)
//       }
//     ) @@ TestAspect.silent
// }
