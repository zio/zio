package zio

import zio.Console.printLine
import zio.test.{ZIOSpecDefault, assertCompletes}

object NewOutputCapturingSpec extends ZIOSpecDefault {
 def spec = suite("new output")(
   test("A")(
     for {
       _ <- printLine("Hi A")
       _ <- ZIO.console.debug("Console in test case A")
     } yield assertCompletes
   ),
   test("B")(
     for {
       _ <- printLine("Hi B")
       _ <- ZIO.console.debug("Console in test case B")
     } yield assertCompletes
   )
 )


}
