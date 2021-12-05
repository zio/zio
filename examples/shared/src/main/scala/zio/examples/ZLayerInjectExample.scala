package zio.examples

import zio.examples.types._
import zio._

import java.io.IOException

// 1. Implicit conversions to LayerBuilderType
// 2. Unify errors
// 3. Implicit Environment helper
// 4. Implicit conversion

object ZLayerInjectExample extends ZIOAppDefault {
  val program: ZIO[OldLady with Console, IOException, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
    }

  val thing: ULayer[Int] = ZLayer.succeed(12)

  val autoLayer: ZLayer[Any, Nothing, OldLady] =
    ZLayer.wire[OldLady with Console](
      OldLady.live,
      Spider.live,
      Fly.live,
      Bear.live,
      Console.live
    )

  def run: ZIO[Any, IOException, Unit] =
    program
      .inject(OldLady.live, Spider.live, Fly.live, Bear.live, Console.live)

}
