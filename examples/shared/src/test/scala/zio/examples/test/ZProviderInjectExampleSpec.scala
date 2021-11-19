package zio.examples.test

import zio._
import zio.examples.ZProviderInjectExample.{Bear, Fly, OldLady, Spider}
import zio.test.Assertion.anything
import zio.test._

object ZProviderInjectExampleSpec extends DefaultRunnableSpec {

  private val exampleZio: ZIO[Console with OldLady, Nothing, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}").orDie
    }

  def spec: ZSpec[Environment, Failure] =
    suite("AutoProviderExampleSpec")(
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
