package zio.test

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.util.concurrent.TimeUnit

object EnvironmentSpec extends ZIOBaseSpec {

  def spec = suite("EnvironmentSpec")(
    test("Clock returns time when it is set") {
      for {
        _    <- TestClock.setTime(1.millis)
        time <- Clock.currentTime(TimeUnit.MILLISECONDS)
      } yield assert(time)(equalTo(1L))
    },
    test("Console writes line to output") {
      for {
        _      <- Console.printLine("First line")
        _      <- Console.printLine("Second line")
        output <- TestConsole.output
      } yield assert(output)(equalTo(Vector("First line\n", "Second line\n")))
    } @@ silent,
    test("Console writes error line to error console") {
      for {
        _      <- Console.printLineError("First line")
        _      <- Console.printLineError("Second line")
        output <- TestConsole.outputErr
      } yield assert(output)(equalTo(Vector("First line\n", "Second line\n")))
    } @@ silent,
    test("Console reads line from input") {
      for {
        _      <- TestConsole.feedLines("Input 1", "Input 2")
        input1 <- Console.readLine
        input2 <- Console.readLine
      } yield {
        assert(input1)(equalTo("Input 1")) &&
        assert(input2)(equalTo("Input 2"))
      }
    },
    test("Random returns next pseudorandom integer") {
      for {
        i <- Random.nextInt
        j <- Random.nextInt
      } yield !assert(i)(equalTo(j))
    },
    test("System returns an environment variable when it is set") {
      for {
        _   <- TestSystem.putEnv("k1", "v1")
        env <- System.env("k1")
      } yield assert(env)(isSome(equalTo("v1")))
    },
    test("System returns a property when it is set") {
      for {
        _   <- TestSystem.putProperty("k1", "v1")
        env <- System.property("k1")
      } yield assert(env)(isSome(equalTo("v1")))
    },
    test("System returns the line separator when it is set") {
      for {
        _       <- TestSystem.setLineSeparator(",")
        lineSep <- System.lineSeparator
      } yield assert(lineSep)(equalTo(","))
    },
    test("Test services can be accessed in live scope") {
      for {
        _ <- TestClock.timeZone
      } yield assertCompletes
    } @@ withLiveClock
  )
}
