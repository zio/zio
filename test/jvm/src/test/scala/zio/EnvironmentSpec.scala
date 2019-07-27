package zio.test.mock

import zio._

class EnvironmentSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "EnvironmentSpec".title ^ s2"""
      Clock:
      Console:
        writes to output        $putStr
        writes line to output   $putStrLn
        reads from input        $getStrLn
      Random:
      System:
  """

  def withEnvironment[E, A](zio: ZIO[TestEnvironment, E, A]): A =
    unsafeRun(testEnvironmentManaged.use[Any, E, A](r => zio.provide(r)))

  def putStr =
    withEnvironment {
      for {
        _      <- console.putStr("First line")
        _      <- console.putStr("Second line")
        output <- TestConsole.output
      } yield output must_=== Vector("First line", "Second line")
    }

  def putStrLn =
    withEnvironment {
      for {
        _      <- console.putStrLn("First line")
        _      <- console.putStrLn("Second line")
        output <- TestConsole.output
      } yield output must_=== Vector("First line\n", "Second line\n")
    }

  def getStrLn =
    withEnvironment {
      for {
        _      <- TestConsole.feedLines("Input 1", "Input 2")
        input1 <- console.getStrLn
        input2 <- console.getStrLn
      } yield (input1 must_=== "Input 1") and (input2 must_=== "Input 2")
    }
}
