package zio.examples

import zio._

object Main extends ZIOAppDefault {
  val program: ZIO[Int, Nothing, Unit] =
    ZIO.service[Int].unit

  val myLayer: URLayer[Console, Int] =
    ZLayer.succeed(11)

  val stringLayer: URLayer[Double, String] =
    ZLayer.succeed("Hello")

  //
  //
  // ComposedLayer
  //   => Expr
  //   => Unused Types
  // List[Type]
  // Which of the types provided to provide some are unused

  // Int (stringLayer: String)
  // You specified String in your remainder, but it is not required to construct your layer.
  // You can use `provide`
  // OR You can use `provideSome[Boolean]`
  // remainder: List[Type]

//  val huh = ZLayer.make(myLayer) //

  val program2 =
    program.provideSome(myLayer) //

  // program.provide(myLayer, ZLayer.environment[String with Boolean])

  def run =
    ZIO.unit

}
