package zio.test.mock

import java.io.{ ByteArrayOutputStream, PrintStream }

import scala.Predef.{ assert => SAssert, _ }

import zio._
import zio.test.mock.MockConsole.Data

object ConsoleSpec extends DefaultRuntime {

  def run(): Unit = {
    SAssert(emptyOutput, "MockConsole outputs nothing")
    SAssert(putStr, "MockConsole writes to output")
    SAssert(putStrLn, "MockConsole writes line to output")
    SAssert(getStr1, "MockConsole reads from input")
    SAssert(getStr2, "MockConsole fails on empty input")
    SAssert(feedLine, "MockConsole feeds lines to input")
    SAssert(clearInput, "MockConsole clears lines from input")
    SAssert(clearOutput, "MockConsole clears lines from output")
  }

  def stream(): PrintStream = new PrintStream(new ByteArrayOutputStream())

  def emptyOutput =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        output      <- mockConsole.output
      } yield output.isEmpty
    )

  def putStr =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        _           <- mockConsole.putStr("First line")
        _           <- mockConsole.putStr("Second line")
        output      <- mockConsole.output
      } yield output == Vector("First line", "Second line")
    )

  def putStrLn =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        _           <- mockConsole.putStrLn("First line")
        _           <- mockConsole.putStrLn("Second line")
        output      <- mockConsole.output
      } yield output == Vector("First line\n", "Second line\n")
    )

  def getStr1 =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data(List("Input 1", "Input 2"), Vector.empty))
        input1      <- mockConsole.getStrLn
        input2      <- mockConsole.getStrLn
      } yield (input1 == "Input 1") && (input2 == "Input 2")
    )

  def getStr2 =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        failed      <- mockConsole.getStrLn.either
        message     = failed.fold(_.getMessage, identity)
      } yield (failed.isLeft) && (message == "There is no more input left to read")
    )

  def feedLine =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data())
        _           <- mockConsole.feedLines("Input 1", "Input 2")
        input1      <- mockConsole.getStrLn
        input2      <- mockConsole.getStrLn
      } yield (input1 == "Input 1") && (input2 == "Input 2")
    )

  def clearInput =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data(List("Input 1", "Input 2"), Vector.empty))
        _           <- mockConsole.clearInput
        failed      <- mockConsole.getStrLn.either
        message     = failed.fold(_.getMessage, identity)
      } yield (failed.isLeft) && (message == "There is no more input left to read")
    )

  def clearOutput =
    unsafeRun(
      for {
        mockConsole <- MockConsole.makeMock(Data(List.empty, Vector("First line", "Second line")))
        _           <- mockConsole.clearOutput
        output      <- mockConsole.output
      } yield output == Vector.empty
    )
}
