package zio.examples

import zio._

object ZLayerInjectExample extends ZIOApp {
  val program =
    OldLady.contentsOfStomach.flatMap { contents =>
      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
    }

  val autoLayer: ZLayer[Any, Nothing, Has[OldLady]] =
    ZLayer.wire[Has[OldLady]](
      OldLady.live,
      Spider.live,
      Fly.live,
      Bear.live,
      Console.live
    )

  def run =
    program
      .inject(OldLady.live, Spider.live, Fly.live, Bear.live, Console.live, ZLayer.Debug.tree)

  trait OldLady {
    def contentsOfStomach: UIO[List[String]]
  }

  object OldLady {
    def contentsOfStomach: ZIO[Has[OldLady], Nothing, List[String]] = ZIO.accessZIO(_.get.contentsOfStomach)

    def live: URLayer[Has[Spider] with Has[Bear], Has[OldLady]] =
      (for {
        spiderGuts <- Spider.contentsOfStomach
      } yield new OldLady {
        def contentsOfStomach: UIO[List[String]] = UIO("a Spdder" :: spiderGuts)
      }).toLayer
  }

  trait Spider {
    def contentsOfStomach: UIO[List[String]]
  }

  object Spider {
    def contentsOfStomach: ZIO[Has[Spider], Nothing, List[String]] = ZIO.accessZIO(_.get.contentsOfStomach)

    def live: URLayer[Has[Fly], Has[Spider]] =
      (for {
        _ <- ZIO.service[Fly]
      } yield new Spider {
        def contentsOfStomach: UIO[List[String]] = UIO(List("a Fly"))
      }).toLayer
  }

  trait Bear {}

  object Bear {
    def live: URLayer[Has[Fly], Has[Bear]] =
      ZLayer.succeed(new Bear {})
  }

  trait Fly {}

  object Fly {

    def live: URLayer[Has[Console], Has[Fly]] = {
      println("FLY")

      Console.printLine("Bzzzzzzzzzz...").orDie.as(new Fly {}).toLayer
    }
  }
}
