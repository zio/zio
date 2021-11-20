package zio.examples

import zio._

object FunTimes extends ZIOAppDefault {

  trait Dog

  type =?>[-A, +B] = URLayer[A, B]

  val dogLayer: Int =?> Dog =
    ZLayer.succeed(new Dog {})

  val huh: ZIO[Int, Nothing, ZEnvironment[Dog]] =
    ZIO
      .environment[Dog]
      .provide(dogLayer)

  def run =
    huh

}
