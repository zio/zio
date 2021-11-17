// package zio.examples

// import zio._
// import java.io.IOException

// object ZServiceBuilderInjectExample extends ZIOAppDefault {
//   val program: ZIO[OldLady with Console, IOException, Unit] =
//     OldLady.contentsOfStomach.flatMap { contents =>
//       Console.printLine(s"There was an old who lady swallowed:\n- ${contents.mkString("\n- ")}")
//     }

//   val autoServiceBuilder: ZServiceBuilder[Any, Nothing, OldLady] =
//     ZServiceBuilder.wire[OldLady](
//       OldLady.live,
//       Spider.live,
//       Fly.live,
//       Bear.live,
//       Console.live
//     )

//   def run: ZIO[Any, IOException, Unit] =
//     program
//       .inject(OldLady.live, Spider.live, Fly.live, Bear.live, Console.live, ZServiceBuilder.Debug.tree)

//   trait OldLady {
//     def contentsOfStomach: UIO[List[String]]
//   }

//   object OldLady {
//     def contentsOfStomach: ZIO[OldLady, Nothing, List[String]] = ZIO.accessZIO(_.get.contentsOfStomach)

//     def live: URServiceBuilder[Spider with Bear, OldLady] =
//       ZServiceBuilder {
//         for {
//           spiderGuts <- Spider.contentsOfStomach
//         } yield new OldLady {
//           def contentsOfStomach: UIO[List[String]] = UIO("a Spdder" :: spiderGuts)
//         }
//       }
//   }

//   trait Spider {
//     def contentsOfStomach: UIO[List[String]]
//   }

//   object Spider {
//     def contentsOfStomach: ZIO[Spider, Nothing, List[String]] = ZIO.accessZIO(_.get.contentsOfStomach)

//     def live: URServiceBuilder[Fly, Spider] =
//       ZServiceBuilder {
//         for {
//           _ <- ZIO.service[Fly]
//         } yield new Spider {
//           def contentsOfStomach: UIO[List[String]] = UIO(List("a Fly"))
//         }
//       }
//   }

//   trait Bear {}

//   object Bear {
//     def live: URServiceBuilder[Fly, Bear] =
//       ZServiceBuilder.succeed(new Bear {})
//   }

//   trait Fly {}

//   object Fly {

//     def live: URServiceBuilder[Console, Fly] = {
//       println("FLY")

//       Console.printLine("Bzzzzzzzzzz...").orDie.as(new Fly {}).toServiceBuilder
//     }
//   }
// }
