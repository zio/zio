package zio

import zio.console._

object Example extends App {

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    effect.as(0)

  val effect = for {
    _ <- putStrLn("Starting program")
    runtime <- ZIO.runtime[Console]
    _ <- ZIO.effectTotal(runtime.printFiberDumpOnInterrupt)
        _ <- putStrLn("hook added")
    _ <- ZIO.infinity
  } yield ()
}