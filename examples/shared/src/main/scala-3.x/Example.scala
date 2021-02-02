import zio._
import zio.console._

import zio.internal.macros.ProvideLayerAutoMacros
import zio.internal.macros.AutoLayerMacroUtils

object Cool extends App {

  val boolLayer =
    ZLayer.succeed(true)

  val layer: ZLayer[Has[Boolean], Unit, Has[Int] with Has[String]] =
    ZLayer.succeed(1) ++ ZLayer.succeed("hello")

  val program: ZIO[Has[String] with Has[Int] with Console, Nothing, Unit] =
    ZIO.services[String, Int].flatMap(in => putStrLn(in.toString()))

  type ++[A,B] <: Has[_] = (A,B) match {
    case (a & Has[_], b & Has[_]) => a & b & Has[_]
    case (a, b & Has[_]) => Has[a] & b
    case (a & Has[_], b) => a & Has[b]
    case (a,b) => Has[a] & Has[b] 
  }

  val layer0 = ZLayer.fromAuto[Console ++ String ++ Int](layer, boolLayer, Console.live) 

  inline def debug[T] = ${AutoLayerMacroUtils.inter[T]}
    // UIO(println(debug[Int ++ String ++ Console])).exitCode

  def run(args: List[String]) =
    program.provideCustomLayerAuto(layer, boolLayer).exitCode
}
