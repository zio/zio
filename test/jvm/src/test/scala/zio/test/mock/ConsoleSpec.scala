package zio.test.mock

import java.io.{ ByteArrayOutputStream, PrintStream }

import zio._
import zio.TestRuntime
import zio.test.mock.MockConsole.Data

class ConsoleSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "ConsoleSpec".title ^ s2"""
      Outputs nothing           $emptyOutput
      Writes to output          $putStr
      Writes line to output     $putStrLn
      Reads from input          $getStr1
      Fails on empty input      $getStr2
      Feeds lines to input      $feedLine
      Clears lines from input   $clearInput
      Clears lines from output  $clearOutput
     """

  def stream(): PrintStream = new PrintStream(new ByteArrayOutputStream())

  def emptyOutput =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        output      <- mockConsole.output
      } yield output must beEmpty
    )

  def putStr =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        _           <- mockConsole.putStr("First line")
        _           <- mockConsole.putStr("Second line")
        output      <- mockConsole.output
      } yield output must_=== Vector("First line", "Second line")
    )

  def putStrLn =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        _           <- mockConsole.putStrLn("First line")
        _           <- mockConsole.putStrLn("Second line")
        output      <- mockConsole.output
      } yield output must_=== Vector("First line\n", "Second line\n")
    )

  def getStr1 =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data(List("Input 1", "Input 2"), Vector.empty))
        input1      <- mockConsole.getStrLn
        input2      <- mockConsole.getStrLn
      } yield (input1 must_=== "Input 1") and (input2 must_=== "Input 2")
    )

  def getStr2 =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        failed      <- mockConsole.getStrLn.either
        message     = failed.fold(_.getMessage, identity)
      } yield (failed must beLeft) and (message must_=== "There is no more input left to read")
    )

  def feedLine =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        _           <- mockConsole.feedLines("Input 1", "Input 2")
        input1      <- mockConsole.getStrLn
        input2      <- mockConsole.getStrLn
      } yield (input1 must_=== "Input 1") and (input2 must_=== "Input 2")
    )

  def clearInput =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data(List("Input 1", "Input 2"), Vector.empty))
        _           <- mockConsole.clearInput
        failed      <- mockConsole.getStrLn.either
        message     = failed.fold(_.getMessage, identity)
      } yield (failed must beLeft) and (message must_=== "There is no more input left to read")
    )

  def clearOutput =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data(List.empty, Vector("First line", "Second line")))
        _           <- mockConsole.clearOutput
        output      <- mockConsole.output
      } yield output must_=== Vector.empty
    )
}
