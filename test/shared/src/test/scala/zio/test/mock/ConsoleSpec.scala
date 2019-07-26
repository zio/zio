package zio.test.mock

import java.io.{ ByteArrayOutputStream, PrintStream }

import zio._
import zio.TestRuntime
import zio.test.mock.TestConsole.Data

class ConsoleSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "ConsoleSpec".title ^ s2"""
      Outputs nothing        $emptyOutput
      Writes to output       $putStr
      Writes line to output  $putStrLn
      Reads from input       $getStr1
      Fails on empty input   $getStr2
      Feeds line to input    $feedLine
     """

  def stream(): PrintStream = new PrintStream(new ByteArrayOutputStream())

  def emptyOutput =
    unsafeRun(
      for {
        testConsole <- TestConsole(Data())
        output      <- testConsole.output
      } yield output must beEmpty
    )

  def putStr =
    unsafeRun(
      for {
        testConsole <- TestConsole(Data())
        _           <- testConsole.putStr("First line")
        _           <- testConsole.putStr("Second line")
        output      <- testConsole.output
      } yield output must_=== Vector("First line", "Second line")
    )

  def putStrLn =
    unsafeRun(
      for {
        testConsole <- TestConsole(Data())
        _           <- testConsole.putStrLn("First line")
        _           <- testConsole.putStrLn("Second line")
        output      <- testConsole.output
      } yield output must_=== Vector("First line\n", "Second line\n")
    )

  def getStr1 =
    unsafeRun(
      for {
        testConsole <- TestConsole(Data(List("Input 1", "Input 2"), Vector.empty))
        input1      <- testConsole.getStrLn
        input2      <- testConsole.getStrLn
      } yield (input1 must_=== "Input 1") and (input2 must_=== "Input 2")
    )

  def getStr2 =
    unsafeRun(
      for {
        testConsole <- TestConsole(Data())
        failed      <- testConsole.getStrLn.either
        message     = failed.fold(_.getMessage, identity)
      } yield (failed must beLeft) and (message must_=== "There is no more input left to read")
    )

  def feedLine =
    unsafeRun(
      for {
        testConsole <- TestConsole(Data())
        _           <- testConsole.feedLine("Input 1")
        _           <- testConsole.feedLine("Input 2")
        input2      <- testConsole.getStrLn
        input1      <- testConsole.getStrLn
      } yield (input2 must_=== "Input 2") and (input1 must_=== "Input 1")
    )
}
