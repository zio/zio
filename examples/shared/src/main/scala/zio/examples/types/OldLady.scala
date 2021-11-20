package zio.examples.types

import zio._

trait OldLady {
  def contentsOfStomach: UIO[List[String]]
}

object OldLady {
  def contentsOfStomach: ZIO[OldLady, Nothing, List[String]] = ZIO.serviceWithZIO(_.contentsOfStomach)

  def live: URLayer[Spider with Bear, OldLady] =
    ZLayer {
      for {
        spiderGuts <- Spider.contentsOfStomach
      } yield new OldLady {
        def contentsOfStomach: UIO[List[String]] = UIO("a Spdder" :: spiderGuts)
      }
    }
}

trait Spider {
  def contentsOfStomach: UIO[List[String]]
}

object Spider {
  def contentsOfStomach: ZIO[Spider, Nothing, List[String]] = ZIO.serviceWithZIO(_.contentsOfStomach)

  def live: URLayer[Fly, Spider] =
    ZLayer {
      for {
        _ <- ZIO.service[Fly]
      } yield new Spider {
        def contentsOfStomach: UIO[List[String]] = UIO(List("a Fly"))
      }
    }
}

trait Bear {}

object Bear {
  def live: URLayer[Fly, Bear] =
    ZLayer.succeed(new Bear {})
}

trait Fly {}

object Fly {

  def live: URLayer[Console, Fly] = {
    println("FLY")

    Console.printLine("Bzzzzzzzzzz...").orDie.as(new Fly {}).toLayer
  }
}
