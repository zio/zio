import zio._
import zio.console._

import zio.internal.macros.LayerMacros
import zio.internal.macros.LayerMacroUtils

object Example extends App {

  val boolLayer =
    ZLayer.succeed(true)

  val layer: ZLayer[Has[Boolean], Nothing, Has[Int] with Has[String]] =
    ZLayer.succeed(1) ++ ZLayer.succeed("hello")

  val program: ZIO[Has[String] with Has[Int] with Console, Nothing, Unit] =
    ZIO.services[String, Int].flatMap(in => putStrLn(in.toString).orDie)




  //  val nice : ZLayer[Int ++ String, Nothing, Has[ServiceLive]] =
//    (ServiceLive.apply _).toLayer

  case class ServiceLive(int: Int, string: String)

//  type Fun = Console ++ Has[String] ++ Has[Boolean]

//  val equal = implicitly[(Console & Has[String] & Has[Boolean]) =:= Fun]

//  val program2: URIO[Double ++ Console ++ Int ++ String ++ Boolean ++ Unit, Unit] = ZIO.unit
//  val program2: URIO[Double ++ Console ++ Int, Unit] = ZIO.unit

//  val layer12 = program2.inject()

  def run(args: List[String]) =
    program.injectCustom(layer, boolLayer).exitCode
}