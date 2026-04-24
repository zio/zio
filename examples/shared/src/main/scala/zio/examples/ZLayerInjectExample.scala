package zio.examples

import zio.examples.types._
import zio._

import java.io.IOException

object ZLayerInjectExample extends ZIOAppDefault {
  val program: ZIO[OldLady, IOException, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
    }

  val thing: ULayer[Int] = ZLayer.succeed(12)

  val autoLayer: ZLayer[Any, Nothing, OldLady] =
    ZLayer.make[OldLady](
      OldLady.live,
      Spider.live,
      Fly.live,
      Bear.live
    )

  def run =
    program.provide(OldLady.live, Spider.live, Fly.live, Bear.live)

}
