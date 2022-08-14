package zio

import zio.Console.printLine
import zio.test.{ZIOSpecDefault, assertCompletes, assertNever}

object NewOutputCapturingSpec extends ZIOSpecDefault {
  def spec =
    suite("basic suite")(
      test("A") {
        printLine("first A output") *>
          printLine("second A output") *>
          assertCompletes
      },
      test("B Assertion failure") {
        printLine("first B output") *>
          printLine("second B output") *>
          assertNever("Don't get here!")
      },
      test("C Runtime failure") {
        printLine("first C output") *>
          printLine("second C output") *>
          ???
      }
    )

}
