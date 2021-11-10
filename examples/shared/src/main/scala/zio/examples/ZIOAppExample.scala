package zio.examples

import zio._

object ZIOAppExample extends ZIOAppDefault {
  trait Animal
  trait Dog extends Animal
  type Cool <: Dog

  type C = _root_.zio.Clock

  def run =
    (ZIO(Tag[Animal]) <*>
      ZIO(Tag[C])).tap(t => ZIO.debug(t._1.render + " == " + t._2.render)).map(_ == _).debug("EQ")
}
