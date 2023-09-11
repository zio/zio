package zio.test

import zio._
import zio.test.TestAspect._

object PrinterTest extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("PrinterTest")(
      test("console is silent") {
        for {
          _ <- Console.printLine("is not printed out")
        } yield assertTrue(true)
      } @@ silent @@ timeout(
        5.seconds
      ) @@ silentLogging,
      test("console is NOT silent but should be") {
        for {
          _ <- Console.printLine("is printed out but shall NOT")
        } yield assertTrue(true)
      } @@ silent @@ timeout(
        5.seconds
      ) @@ silentLogging @@ repeat(Schedule.recurs(0)) 
    )
}