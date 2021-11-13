package zio.examples

import zio._
import java.io.IOException

object ZServiceBuilderInjectExample extends ZIOAppDefault {
  val program: ZIO[Has[OldLady] with Has[Console], IOException, Unit] =
    OldLady.contentsOfStomach.flatMap { contents =>
      Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
    }

  val autoServiceBuilder: ZServiceBuilder[Any, Nothing, Has[OldLady]] =
    ZServiceBuilder.wire[Has[OldLady]](
      OldLady.live,
      Spider.live,
      Fly.live,
      Bear.live,
      Console.live
    )

  def run: ZIO[Any, IOException, Unit] =
    program
      .inject(OldLady.live, Spider.live, Fly.live, Bear.live, Console.live, ZServiceBuilder.Debug.tree)

  trait OldLady {
    def contentsOfStomach: UIO[List[String]]
  }

  object OldLady {
    def contentsOfStomach: ZIO[Has[OldLady], Nothing, List[String]] = ZIO.accessZIO(_.get.contentsOfStomach)

    def live: URServiceBuilder[Has[Spider] with Has[Bear], Has[OldLady]] =
      (for {
        spiderGuts <- Spider.contentsOfStomach
      } yield new OldLady {
        def contentsOfStomach: UIO[List[String]] = UIO("a Spdder" :: spiderGuts)
      }).toServiceBuilder
  }

  trait Spider {
    def contentsOfStomach: UIO[List[String]]
  }

  object Spider {
    def contentsOfStomach: ZIO[Has[Spider], Nothing, List[String]] = ZIO.accessZIO(_.get.contentsOfStomach)

    def live: URServiceBuilder[Has[Fly], Has[Spider]] =
      (for {
        _ <- ZIO.service[Fly]
      } yield new Spider {
        def contentsOfStomach: UIO[List[String]] = UIO(List("a Fly"))
      }).toServiceBuilder
  }

  trait Bear {}

  object Bear {
    def live: URServiceBuilder[Has[Fly], Has[Bear]] =
      ZServiceBuilder.succeed(new Bear {})
  }

  trait Fly {}

  object Fly {

    def live: URServiceBuilder[Has[Console], Has[Fly]] = {
      println("FLY")

      Console.printLine("Bzzzzzzzzzz...").orDie.as(new Fly {}).toServiceBuilder
    }
  }
}
