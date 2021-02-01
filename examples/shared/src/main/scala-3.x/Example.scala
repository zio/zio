import zio._
import zio.console._

import zio.internal.macros.ProvideLayerAutoMacros

object Cool extends App {
  val layer: ZLayer[Any, Unit, Has[Int] with Has[String]] =
    ZLayer.succeed(1) ++ ZLayer.succeed("hello")

  val program: ZIO[Has[String] with Has[Int] with Console, Nothing, Unit] =
    ZIO.services[String, Int].flatMap(in => putStrLn(in.toString()))

  def run(args: List[String]) =
    program.provideLayerAuto(layer, Console.live).exitCode
}
