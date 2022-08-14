package zio

import zio.Console.printLine
import zio.test.{ZIOSpecDefault, assertCompletes}

object NewOutputCapturingSpec extends ZIOSpecDefault {
  def spec =
    suite("basic suite")(
      test("A") {
        printLine("first A output") *>
          printLine("second A output") *>
          assertCompletes
      },
      test("B") {
        printLine("first B output") *>
          printLine("second B output") *>
          ???
//          assertNever("Don't get here!")
      }
    )

}
