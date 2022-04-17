package zio.examples

import zio.examples.types._
import zio._
import zio.test.{ZIOSpecAbstract, ZIOSpecDefault, assertTrue}

import java.io.IOException

object ZLayerInjectExample extends ZIOAppDefault {
  val program: ZIO[OldLady with Console, IOException, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
    }

  val thing: ULayer[Int] = ZLayer.succeed(12)

  val autoLayer: ZLayer[Any, Nothing, OldLady] =
    ZLayer.make[OldLady with Console](
      OldLady.live,
      Spider.live,
      Fly.live,
      Bear.live,
      Console.live
    )

  def run =
    program.provide(OldLady.live, Spider.live, Fly.live, Bear.live, Console.live)

}

object SmallMinimal1Spec extends ZIOSpecDefault {
  override def spec = suite("SmallMultiSpec1")(
    suite("fast inner suite")(
      test("fast test 1") {
        assertTrue(false)
      }
    )
  )
}
object SmallMinimal2Spec extends ZIOSpecDefault {
//  override val  layer = ZLayer.fromZIO(ZIO.attempt(???))

  override def spec = suite("SmallMultiSpec2")(
    suite("fast inner suite")(
      test("fast test 1") {
        assertTrue(false)
      }
    )
  )
}

object ComposedSpec extends ZIOAppDefault {
  val demo: ZIOSpecAbstract =  SmallMinimal1Spec <> SmallMinimal2Spec
  def run = demo.run

}
