package zio.test.environment

import zio.Clock._
import zio._
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import java.util.concurrent.TimeUnit

object EnvironmentSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("EnvironmentSpec")(
    testM("Clock returns time when it is set") {
      for {
        _    <- TestClock.setTime(1.millis)
        time <- Clock.currentTime(TimeUnit.MILLISECONDS)
      } yield assert(time)(equalTo(1L))
    },
    testM("Has[Console] writes line to output") {
      for {
        _      <- Console.putStrLn("First line")
        _      <- Console.putStrLn("Second line")
        output <- TestConsole.output
      } yield assert(output)(equalTo(Vector("First line\n", "Second line\n")))
    } @@ silent,
    testM("Has[Console] writes error line to error console") {
      for {
        _      <- Console.putStrLnErr("First line")
        _      <- Console.putStrLnErr("Second line")
        output <- TestConsole.outputErr
      } yield assert(output)(equalTo(Vector("First line\n", "Second line\n")))
    } @@ silent,
    testM("Has[Console] reads line from input") {
      for {
        _      <- TestConsole.feedLines("Input 1", "Input 2")
        input1 <- Console.getStrLn
        input2 <- Console.getStrLn
      } yield {
        assert(input1)(equalTo("Input 1")) &&
        assert(input2)(equalTo("Input 2"))
      }
    },
    testM("Has[Random] returns next pseudorandom integer") {
      for {
        i <- Random.nextInt
        j <- Random.nextInt
      } yield !assert(i)(equalTo(j))
    },
    testM("Has[Random] is deterministic") {
      for {
        i <- Random.nextInt.provideLayer(testEnvironment)
        j <- Random.nextInt.provideLayer(testEnvironment)
      } yield assert(i)(equalTo(j))
    },
    testM("Has[System] returns an environment variable when it is set") {
      for {
        _   <- TestSystem.putEnv("k1", "v1")
        env <- System.env("k1")
      } yield assert(env)(isSome(equalTo("v1")))
    },
    testM("Has[System] returns a property when it is set") {
      for {
        _   <- TestSystem.putProperty("k1", "v1")
        env <- System.property("k1")
      } yield assert(env)(isSome(equalTo("v1")))
    },
    testM("Has[System] returns the line separator when it is set") {
      for {
        _       <- TestSystem.setLineSeparator(",")
        lineSep <- System.lineSeparator
      } yield assert(lineSep)(equalTo(","))
    },
    testM("clock service can be overwritten") {
      val withLiveClock = TestEnvironment.live ++ Clock.live
      val time          = Clock.nanoTime.provideLayer(withLiveClock)
      assertM(time)(isGreaterThan(0L))
    } @@ nonFlaky
  )
}
