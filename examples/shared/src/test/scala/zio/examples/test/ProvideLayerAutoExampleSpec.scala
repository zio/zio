package zio.examples.test

import zio.console.Console
import zio.{Has, ZIO, console}
import zio.examples.ProvideLayerAutoExample.{Fly, OldLady, Spider}
import zio.test.Assertion.anything
import zio.test._

object ProvideLayerAutoExampleSpec extends DefaultRunnableSpec {

  private val exampleZio: ZIO[Console with Has[OldLady], Nothing, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      console.putStrLn(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
    }

  def spec =
    suite("AutoLayerExampleSpec")(
      testM("provideLayerAuto") {
        assertM(exampleZio)(anything)
      }.provideLayerAuto(Console.live, OldLady.live, Spider.live, Fly.live),
      testM("provideCustomLayerAuto") {
        assertM(exampleZio)(anything)
      }.provideCustomLayerAuto(OldLady.live, Spider.live, Fly.live),
      testM("provideLayerSharedAuto") {
        assertM(exampleZio)(anything)
      }.provideLayerSharedAuto(Console.live, OldLady.live, Spider.live, Fly.live),
      testM("provideCustomLayerSharedAuto") {
        assertM(exampleZio)(anything)
      }.provideCustomLayerSharedAuto(OldLady.live, Spider.live, Fly.live)
    )
}
