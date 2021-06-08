package zio.test.environment

import zio.Console._
import zio.test.Assertion._
import zio.test.TestAspect.{nonFlaky, silent}
import zio.test._
import zio.test.environment.TestConsole._
import zio.{Console, ZIO}

object ConsoleSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] =
    suite("ConsoleSpec")(
      testM("outputs nothing") {
        for {
          output <- TestConsole.output
        } yield assert(output)(isEmpty)
      },
      testM("writes to output") {
        for {
          _      <- print("First line")
          _      <- print("Second line")
          output <- TestConsole.output
        } yield assert(output)(equalTo(Vector("First line", "Second line")))
      },
      testM("writes line to output") {
        for {
          _      <- printLine("First line")
          _      <- printLine("Second line")
          output <- TestConsole.output
        } yield assert(output)(equalTo(Vector("First line\n", "Second line\n")))
      },
      testM("reads from input") {
        {
          for {
            testConsole <- ZIO.service[Console]
            input1      <- testConsole.readLine
            input2      <- testConsole.readLine
          } yield {
            assert(input1)(equalTo("Input 1")) &&
            assert(input2)(equalTo("Input 2"))
          }
        }.provideLayer(TestConsole.make(Data(List("Input 1", "Input 2"), Vector.empty)))
      },
      testM("fails on empty input") {
        for {
          failed <- readLine.either
          message = failed.fold(_.getMessage, identity)
        } yield {
          assert(failed.isLeft)(isTrue) &&
          assert(message)(equalTo("There is no more input left to read"))
        }
      },
      testM("feeds lines to input") {
        for {
          _      <- feedLines("Input 1", "Input 2")
          input1 <- readLine
          input2 <- readLine
        } yield {
          assert(input1)(equalTo("Input 1")) &&
          assert(input2)(equalTo("Input 2"))
        }
      },
      testM("clears lines from input") {
        for {
          _      <- feedLines("Input 1", "Input 2")
          _      <- clearInput
          failed <- readLine.either
          message = failed.fold(_.getMessage, identity)
        } yield {
          assert(failed.isLeft)(isTrue) &&
          assert(message)(equalTo("There is no more input left to read"))
        }
      },
      testM("clears lines from output") {
        for {
          _      <- print("First line")
          _      <- print("Second line")
          _      <- clearOutput
          output <- TestConsole.output
        } yield assert(output)(isEmpty)
      },
      testM("output is empty at the start of repeating tests") {
        for {
          output <- TestConsole.output
          _      <- printLine("Input")
        } yield assert(output)(isEmpty)
      } @@ nonFlaky
    ) @@ silent
}
