package zio.examples.test

import zio.console.Console
import zio.{Has, ZIO, console}
import zio.examples.ProvideLayerAutoExample.{Bear, Fly, OldLady, Spider}
import zio.test.Assertion.anything
import zio.test._

object ProvideLayerAutoExampleSpec extends DefaultRunnableSpec {

  private val exampleZio: ZIO[Console with Has[OldLady], Nothing, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      console.putStrLn(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
    }

  def spec =
    suite("AutoLayerExampleSpec")(
      testM("provideLayer") {
        assertM(exampleZio)(anything)
      }.provideLayer(Console.live, OldLady.live, Spider.live, Fly.live, Bear.live),
      testM("provideCustomLayer") {
        assertM(exampleZio)(anything)
      }.provideCustomLayer(OldLady.live, Spider.live, Fly.live, Bear.live),
      testM("provideLayerManualSharedAuto") {
        assertM(exampleZio)(anything)
      }.provideLayerManualSharedAuto(Console.live, OldLady.live, Spider.live, Fly.live, Bear.live),
      testM("provideCustomLayerManualSharedAuto") {
        assertM(exampleZio)(anything)
      }.provideCustomLayerManualSharedAuto(OldLady.live, Spider.live, Fly.live, Bear.live)
    )
}
