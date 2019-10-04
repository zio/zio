package zio.test.environment

import java.io.{ ByteArrayOutputStream, PrintStream }

import zio.test.Async
import zio.test.environment.TestConsole.Data
import zio.test.TestUtils.label
import zio.test.ZIOBaseSpec

object ConsoleSpec extends ZIOBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(emptyOutput, "outputs nothing"),
    label(putStr, "writes to output"),
    label(putStrLn, "writes line to output"),
    label(getStr1, "reads from input"),
    label(getStr2, "fails on empty input"),
    label(feedLine, "feeds lines to input"),
    label(clearInput, "clears lines from input"),
    label(clearOutput, "clears lines from output")
  )

  def stream(): PrintStream = new PrintStream(new ByteArrayOutputStream())

  def emptyOutput =
    unsafeRunToFuture(
      for {
        testConsole <- TestConsole.makeTest(Data())
        output      <- testConsole.output
      } yield output.isEmpty
    )

  def putStr =
    unsafeRunToFuture(
      for {
        testConsole <- TestConsole.makeTest(Data())
        _           <- testConsole.putStr("First line")
        _           <- testConsole.putStr("Second line")
        output      <- testConsole.output
      } yield output == Vector("First line", "Second line")
    )

  def putStrLn =
    unsafeRunToFuture(
      for {
        testConsole <- TestConsole.makeTest(Data())
        _           <- testConsole.putStrLn("First line")
        _           <- testConsole.putStrLn("Second line")
        output      <- testConsole.output
      } yield output == Vector("First line\n", "Second line\n")
    )

  def getStr1 =
    unsafeRunToFuture(
      for {
        testConsole <- TestConsole.makeTest(Data(List("Input 1", "Input 2"), Vector.empty))
        input1      <- testConsole.getStrLn
        input2      <- testConsole.getStrLn
      } yield (input1 == "Input 1") && (input2 == "Input 2")
    )

  def getStr2 =
    unsafeRunToFuture(
      for {
        testConsole <- TestConsole.makeTest(Data())
        failed      <- testConsole.getStrLn.either
        message     = failed.fold(_.getMessage, identity)
      } yield (failed.isLeft) && (message == "There is no more input left to read")
    )

  def feedLine =
    unsafeRunToFuture(
      for {
        testConsole <- TestConsole.makeTest(Data())
        _           <- testConsole.feedLines("Input 1", "Input 2")
        input1      <- testConsole.getStrLn
        input2      <- testConsole.getStrLn
      } yield (input1 == "Input 1") && (input2 == "Input 2")
    )

  def clearInput =
    unsafeRunToFuture(
      for {
        testConsole <- TestConsole.makeTest(Data(List("Input 1", "Input 2"), Vector.empty))
        _           <- testConsole.clearInput
        failed      <- testConsole.getStrLn.either
        message     = failed.fold(_.getMessage, identity)
      } yield (failed.isLeft) && (message == "There is no more input left to read")
    )

  def clearOutput =
    unsafeRunToFuture(
      for {
        testConsole <- TestConsole.makeTest(Data(List.empty, Vector("First line", "Second line")))
        _           <- testConsole.clearOutput
        output      <- testConsole.output
      } yield output == Vector.empty
    )
}
