package zio.examples.test

import zio._
import zio.examples.ZServiceBuilderInjectExample.{Bear, Fly, OldLady, Spider}
import zio.test.Assertion.anything
import zio.test._

object ZServiceBuilderInjectExampleSpec extends DefaultRunnableSpec {

  private val exampleZio: ZIO[Has[Console] with Has[OldLady], Nothing, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}").orDie
    }

  def spec: ZSpec[Environment, Failure] =
    suite("AutoServiceBuilderExampleSpec")(
      test("inject") {
        assertM(exampleZio)(anything)
      }.inject(Console.live, OldLady.live, Spider.live, Fly.live, Bear.live),
      test("injectCustom") {
        assertM(exampleZio)(anything)
      }.injectCustom(OldLady.live, Spider.live, Fly.live, Bear.live),
      test("injectShared") {
        assertM(exampleZio)(anything)
      }.injectShared(Console.live, OldLady.live, Spider.live, Fly.live, Bear.live),
      test("injectCustomShared") {
        assertM(exampleZio)(anything)
      }.injectCustomShared(OldLady.live, Spider.live, Fly.live, Bear.live)
    )
}
