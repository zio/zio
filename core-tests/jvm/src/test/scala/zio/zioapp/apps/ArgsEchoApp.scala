package zio.zioapp.apps

import zio._

/**
 * Echoes the command-line args joined by commas, then exits.
 */
object ArgsEchoApp extends ZIOAppDefault {
  val run: ZIO[ZIOAppArgs, Nothing, Unit] =
    for {
      args <- getArgs
      _    <- Console.printLine("ARGS:" + args.mkString(",")).orDie
    } yield ()
}
