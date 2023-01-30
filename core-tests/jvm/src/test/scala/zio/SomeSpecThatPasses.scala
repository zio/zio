package zio

import zio.test._

object SomeSpecThatPasses extends ZIOSpecDefault {
  def spec =
    test("go boom")(assertCompletes)

}
