package zio.examples

import zio._

object FunTimes extends ZIOAppDefault {

  def run =
    ZIO.environment[Int & String]

}
