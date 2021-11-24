package zio.examples

import zio.examples.types._
import zio._

import java.io.IOException

// 1. Implicit conversions to LayerBuilderType
// 2. Unify errors
// 3. Implicit Environment helper
// 4. Implicit conversion

object ZLayerProvideExample extends ZIOAppDefault {
  lazy val program: ZIO[OldLady with Console, IOException, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
    }

  lazy val thing: ULayer[Int] = ZLayer.succeed(12)

  lazy val autoLayer: ZLayer[String, Nothing, OldLady with Console] =
    ZLayer.make[OldLady with Console](
      OldLady.live,
      Spider.live,
      Fly.live,
      Bear.live,
      Console.live
    )

  lazy val program2: ZIO[String with Console, Nothing, OldLady] =
    ZIO.succeed(new OldLady {
      override def contentsOfStomach: UIO[List[String]] = UIO(List.empty)
    })

//  lazy val layer2 = ZLayer.succeed("Hello") ++ Console.live
//
//  lazy val provided = program2.provideCustom(ZLayer.succeed(1))

  def run =
    Console.print("Hi")

}
