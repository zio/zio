package zio.examples

import zio._
import zio.console.Console

object ProvideLayerAutoExample extends App {
  val program =
    OldLady.contentsOfStomach.flatMap { contents =>
      console.putStrLn(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
    }

  val autoLayer: ZLayer[Any, Nothing, Has[OldLady]] =
    ZLayer.fromAuto[Has[OldLady]](
      OldLady.live,
      Spider.live,
      Fly.live,
      Console.live,
      ZLayer.succeed(12),
      ZLayer.succeed(true)
    )

  val _ = autoLayer

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program
      .provideCustomLayerAuto(OldLady.live, Spider.live, Fly.live, ZLayer.succeed(12), ZLayer.succeed(true))
      .exitCode

  trait OldLady {
    def contentsOfStomach: UIO[List[String]]
  }

  object OldLady {
    def contentsOfStomach: ZIO[Has[OldLady], Nothing, List[String]] = ZIO.accessM(_.get.contentsOfStomach)

    def live: URLayer[Has[Spider], Has[OldLady]] =
      (for {
        spiderGuts <- Spider.contentsOfStomach
      } yield new OldLady {
        def contentsOfStomach: UIO[List[String]] = UIO("a Spider" :: spiderGuts)
      }).toLayer
  }

  trait Spider {
    def contentsOfStomach: UIO[List[String]]
  }

  object Spider {
    def contentsOfStomach: ZIO[Has[Spider], Nothing, List[String]] = ZIO.accessM(_.get.contentsOfStomach)

    def live: URLayer[Has[Fly], Has[Spider]] =
      (for {
        _ <- ZIO.service[Fly]
      } yield new Spider {
        def contentsOfStomach: UIO[List[String]] = UIO(List("a Fly"))
      }).toLayer
  }

  trait Fly {}

  object Fly {
    def live: URLayer[Console with Has[Boolean] with Has[Int], Has[Fly]] =
      console.putStrLn("Bzzzzzzzzzz...").as(new Fly {}).toLayer
  }
}
