package scalaz.zio.testkit

import java.io.{ ByteArrayOutputStream, PrintStream }

import scalaz.zio._
import scalaz.zio.TestRuntime
import scalaz.zio.testkit.TestConsole.Data

class ConsoleSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "ConsoleSpec".title ^ s2"""
      Outputs nothing        $emptyOutput
      Writes to output       $putStr
      Writes line to output  $putStrLn
      Reads from input       $getStr1
      Fails on empty input   $getStr2
     """

  def stream(): PrintStream = new PrintStream(new ByteArrayOutputStream())

  def emptyOutput =
    unsafeRun(
      for {
        ref         <- Ref.make(Data())
        testConsole <- IO.succeed(TestConsole(ref))
        output      <- testConsole.ref.get.map(_.output)
      } yield output must beEmpty
    )

  def putStr =
    unsafeRun(
      for {
        ref         <- Ref.make(Data())
        testConsole <- IO.succeed(TestConsole(ref))
        _           <- testConsole.putStr("First line")
        _           <- testConsole.putStr("Second line")
        output      <- testConsole.ref.get.map(_.output)
      } yield output must_=== Vector("First line", "Second line")
    )

  def putStrLn =
    unsafeRun(
      for {
        ref         <- Ref.make(Data())
        testConsole <- IO.succeed(TestConsole(ref))
        _           <- testConsole.putStrLn("First line")
        _           <- testConsole.putStrLn("Second line")
        output      <- testConsole.ref.get.map(_.output)
      } yield output must_=== Vector("First line\n", "Second line\n")
    )

  def getStr1 =
    unsafeRun(
      for {
        ref         <- Ref.make(Data(List("Input 1", "Input 2"), Vector.empty))
        testConsole <- IO.succeed(TestConsole(ref))
        input1      <- testConsole.getStrLn
        input2      <- testConsole.getStrLn
      } yield (input1 must_=== "Input 1") and (input2 must_=== "Input 2")
    )

  def getStr2 =
    unsafeRun(
      for {
        ref         <- Ref.make(Data())
        testConsole <- IO.succeed(TestConsole(ref))
        failed      <- testConsole.getStrLn.either
      } yield failed must beLeft
    )
}
