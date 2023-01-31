package zio.internal

import zio.test._

object SomeSpecThatJustBlewUp extends ZIOSpecDefault {
  def spec =
    test("go boom") {

      import scala.collection.mutable.ListBuffer

      val list = ListBuffer[Array[Byte]]()

      while (true) {
        list += Array.ofDim[Byte](1048576) // allocate 1MB of memory
      }
      assertNever("We already ran out of memory. We can't get here, silly!")
    }

}
