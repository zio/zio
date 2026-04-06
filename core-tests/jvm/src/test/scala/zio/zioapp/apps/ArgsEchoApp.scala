package zio.zioapp.apps

import zio._

// Tests that args are properly passed through to the app
object ArgsEchoApp extends ZIOAppDefault {
  val run = for {
    args <- getArgs
    _    <- ZIO.succeed(println("ARGS:" + args.mkString(",")))
    _    <- ZIO.succeed(println("APP_READY"))
  } yield ()
}
