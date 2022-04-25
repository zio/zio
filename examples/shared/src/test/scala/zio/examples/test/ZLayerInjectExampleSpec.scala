//package zio.examples.test
//
//import zio._
//import zio.examples.ZLayerInjectExample.{Bear, Fly, OldLady, Spider}
//import zio.test.Assertion.anything
//import zio.test._
//
//object ZLayerInjectExampleSpec extends DefaultRunnableSpec {
//
//  private val exampleZio: ZIO[Console with OldLady, Nothing, Unit] =
//    OldLady.contentsOfStomach.flatMap { contents =>
//      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}").orDie
//    }
//
//  def spec: Spec[Environment, Failure] =
//    suite("AutoLayerExampleSpec")(
//      test("inject") {
//        assertZIO(exampleZio)(anything)
//      }.provide(Console.live, OldLady.live, Spider.live, Fly.live, Bear.live),
//      test("provideCustom") {
//        assertZIO(exampleZio)(anything)
//      }.provideCustom(OldLady.live, Spider.live, Fly.live, Bear.live),
//      test("injectShared") {
//        assertZIO(exampleZio)(anything)
//      }.provideShared(Console.live, OldLady.live, Spider.live, Fly.live, Bear.live),
//      test("provideCustomShared") {
//        assertZIO(exampleZio)(anything)
//      }.provideCustomShared(OldLady.live, Spider.live, Fly.live, Bear.live)
//    )
//}
