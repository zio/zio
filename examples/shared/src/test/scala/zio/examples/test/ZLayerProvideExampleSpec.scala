package zio.examples.test

import zio._
import zio.examples.types._
import zio.test.Assertion.anything
import zio.test._

object ZLayerProvideExampleSpec extends ZIOSpecDefault {

  private val exampleZio: ZIO[Console with OldLady, Nothing, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}").orDie
    }

  def spec =
    suite("AutoLayerExampleSpec")(
      test("provide") {
        assertM(exampleZio)(anything)
      }.provide(Console.live, OldLady.live, Spider.live, Fly.live, Bear.live),
      test("provideCustom") {
        assertM(exampleZio)(anything)
      }.provideCustom(OldLady.live, Spider.live, Fly.live, Bear.live),
      test("provideShared") {
        assertM(exampleZio)(anything)
      }.provideShared(Console.live, OldLady.live, Spider.live, Fly.live, Bear.live),
      test("provideCustomShared") {
        assertM(exampleZio)(anything)
      }.provideCustomShared(OldLady.live, Spider.live, Fly.live, Bear.live)
    )
}
