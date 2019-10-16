package zio.test.environment

import zio.test.environment.TestConsole.Data
import zio.test.Assertion._
import zio.test._

object ConsoleSpec
    extends ZIOBaseSpec(
      suite("ConsoleSpec")(
        testM("outputs nothing") {
          for {
            testConsole <- TestConsole.makeTest(Data())
            output      <- testConsole.output
          } yield assert(output.isEmpty, isTrue)
        },
        testM("writes to output") {
          for {
            testConsole <- TestConsole.makeTest(Data())
            _           <- testConsole.putStr("First line")
            _           <- testConsole.putStr("Second line")
            output      <- testConsole.output
          } yield assert(output, equalTo(Vector("First line", "Second line")))
        },
        testM("writes line to output") {
          for {
            testConsole <- TestConsole.makeTest(Data())
            _           <- testConsole.putStrLn("First line")
            _           <- testConsole.putStrLn("Second line")
            output      <- testConsole.output
          } yield assert(output, equalTo(Vector("First line\n", "Second line\n")))
        },
        testM("reads from input") {
          for {
            testConsole <- TestConsole.makeTest(Data(List("Input 1", "Input 2"), Vector.empty))
            input1      <- testConsole.getStrLn
            input2      <- testConsole.getStrLn
          } yield {
            assert(input1, equalTo("Input 1"))
            assert(input2, equalTo("Input 2"))
          }
        },
        testM("fails on empty input") {
          for {
            testConsole <- TestConsole.makeTest(Data())
            failed      <- testConsole.getStrLn.either
            message     = failed.fold(_.getMessage, identity)
          } yield {
            assert(failed.isLeft, isTrue)
            assert(message, equalTo("There is no more input left to read"))
          }
        },
        testM("feeds lines to input") {
          for {
            testConsole <- TestConsole.makeTest(Data())
            _           <- testConsole.feedLines("Input 1", "Input 2")
            input1      <- testConsole.getStrLn
            input2      <- testConsole.getStrLn
          } yield {
            assert(input1, equalTo("Input 1"))
            assert(input2, equalTo("Input 2"))
          }
        },
        testM("clears lines from input") {
          for {
            testConsole <- TestConsole.makeTest(Data(List("Input 1", "Input 2"), Vector.empty))
            _           <- testConsole.clearInput
            failed      <- testConsole.getStrLn.either
            message     = failed.fold(_.getMessage, identity)
          } yield {
            assert(failed.isLeft, isTrue)
            assert(message, equalTo("There is no more input left to read"))
          }
        },
        testM("clears lines from output") {
          for {
            testConsole <- TestConsole.makeTest(Data(List.empty, Vector("First line", "Second line")))
            _           <- testConsole.clearOutput
            output      <- testConsole.output
          } yield assert(output, equalTo(Vector.empty))
        }
      )
    )
