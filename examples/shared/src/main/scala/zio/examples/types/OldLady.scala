package zio.examples.types

import zio._

trait OldLady {
  def contentsOfStomach: UIO[List[String]]
}

object OldLady {
  val contentsOfStomach: ZIO[OldLady, Nothing, List[String]] = ZIO.serviceWithZIO(_.contentsOfStomach)

  val live: URLayer[Spider with Bear with Console, OldLady] =
    ZLayer {
      for {
        spiderGuts <- Spider.contentsOfStomach
      } yield new OldLady {
        val contentsOfStomach: UIO[List[String]] = ZIO.succeed("a Spider" :: spiderGuts)
      }
    }
}

trait Spider {
  val contentsOfStomach: UIO[List[String]]
}

object Spider {
  val contentsOfStomach: ZIO[Spider, Nothing, List[String]] = ZIO.serviceWithZIO(_.contentsOfStomach)

  val live: URLayer[Fly, Spider] =
    ZLayer {
      for {
        _ <- ZIO.service[Fly]
      } yield new Spider {
        val contentsOfStomach: UIO[List[String]] = ZIO.succeed(List("a Fly"))
      }
    }
}

trait Bear {}

object Bear {
  val live: URLayer[Fly, Bear] =
    ZLayer.succeed(new Bear {})
}

trait Fly {}

object Fly {

  val live: URLayer[Console, Fly] = {
    println("FLY")

    ZLayer(Console.printLine("Bzzzzzzzzzz...").orDie.as(new Fly {}))
  }
}
