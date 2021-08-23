package zio.examples

import zio._

object ZIOAppExample extends ZIOApp {
  final def run =
    for {
      _    <- console.putStrLn("Hello! What is your name?")
      name <- console.getStrLn
      _    <- console.putStrLn(s"Hello, $name! Fancy seeing you here.")
    } yield ()
}
