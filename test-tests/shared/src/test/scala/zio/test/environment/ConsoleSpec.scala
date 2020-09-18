package zio.test.environment

import zio.ZIO
import zio.console._
import zio.test.Assertion._
import zio.test.TestAspect.{ nonFlaky, silent }
import zio.test._
import zio.test.environment.TestConsole._

object ConsoleSpec extends ZIOBaseSpec {

  def spec: ZSpec[TestEnvironment, Any] =
    suite("ConsoleSpec")(
      testM("outputs nothing") {
        for {
          output <- TestConsole.output
        } yield assert(output)(isEmpty)
      },
      testM("writes to output") {
        for {
          _      <- putStr("First line")
          _      <- putStr("Second line")
          output <- TestConsole.output
        } yield assert(output)(equalTo(Vector("First line", "Second line")))
      },
      testM("writes line to output") {
        for {
          _      <- putStrLn("First line")
          _      <- putStrLn("Second line")
          output <- TestConsole.output
        } yield assert(output)(equalTo(Vector("First line\n", "Second line\n")))
      },
      testM("reads from input") {
        {
          for {
            testConsole <- ZIO.environment[Console].map(_.get)
            input1      <- testConsole.getStrLn
            input2      <- testConsole.getStrLn
          } yield {
            assert(input1)(equalTo("Input 1")) &&
            assert(input2)(equalTo("Input 2"))
          }
        }.provideLayer(TestConsole.make(Data(List("Input 1", "Input 2"), Vector.empty)))
      },
      testM("fails on empty input") {
        for {
          failed <- getStrLn.either
          message = failed.fold(_.getMessage, identity)
        } yield {
          assert(failed.isLeft)(isTrue) &&
          assert(message)(equalTo("There is no more input left to read"))
        }
      },
      testM("feeds lines to input") {
        for {
          _      <- feedLines("Input 1", "Input 2")
          input1 <- getStrLn
          input2 <- getStrLn
        } yield {
          assert(input1)(equalTo("Input 1")) &&
          assert(input2)(equalTo("Input 2"))
        }
      },
      testM("clears lines from input") {
        for {
          _      <- feedLines("Input 1", "Input 2")
          _      <- clearInput
          failed <- getStrLn.either
          message = failed.fold(_.getMessage, identity)
        } yield {
          assert(failed.isLeft)(isTrue) &&
          assert(message)(equalTo("There is no more input left to read"))
        }
      },
      testM("clears lines from output") {
        for {
          _      <- putStr("First line")
          _      <- putStr("Second line")
          _      <- clearOutput
          output <- TestConsole.output
        } yield assert(output)(isEmpty)
      },
      testM("output is empty at the start of repeating tests") {
        for {
          output <- TestConsole.output
          _      <- putStrLn("Input")
        } yield assert(output)(isEmpty)
      } @@ nonFlaky
    ) @@ silent
}
