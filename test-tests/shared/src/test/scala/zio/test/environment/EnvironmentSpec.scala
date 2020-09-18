package zio.test.environment

import java.util.concurrent.TimeUnit

import zio._
import zio.clock._
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object EnvironmentSpec extends ZIOBaseSpec {

  def spec: ZSpec[TestEnvironment, Any] = suite("EnvironmentSpec")(
    testM("Clock returns time when it is set") {
      for {
        _    <- TestClock.setTime(1.millis)
        time <- clock.currentTime(TimeUnit.MILLISECONDS)
      } yield assert(time)(equalTo(1L))
    },
    testM("Console writes line to output") {
      for {
        _      <- console.putStrLn("First line")
        _      <- console.putStrLn("Second line")
        output <- TestConsole.output
      } yield assert(output)(equalTo(Vector("First line\n", "Second line\n")))
    } @@ silent,
    testM("Console writes error line to error console") {
      for {
        _      <- console.putStrLnErr("First line")
        _      <- console.putStrLnErr("Second line")
        output <- TestConsole.outputErr
      } yield assert(output)(equalTo(Vector("First line\n", "Second line\n")))
    } @@ silent,
    testM("Console reads line from input") {
      for {
        _      <- TestConsole.feedLines("Input 1", "Input 2")
        input1 <- console.getStrLn
        input2 <- console.getStrLn
      } yield {
        assert(input1)(equalTo("Input 1")) &&
        assert(input2)(equalTo("Input 2"))
      }
    },
    testM("Random returns next pseudorandom integer") {
      for {
        i <- random.nextInt
        j <- random.nextInt
      } yield !assert(i)(equalTo(j))
    },
    testM("Random is deterministic") {
      for {
        i <- random.nextInt.provideLayer(testEnvironment)
        j <- random.nextInt.provideLayer(testEnvironment)
      } yield assert(i)(equalTo(j))
    },
    testM("System returns an environment variable when it is set") {
      for {
        _   <- TestSystem.putEnv("k1", "v1")
        env <- system.env("k1")
      } yield assert(env)(isSome(equalTo("v1")))
    },
    testM("System returns a property when it is set") {
      for {
        _   <- TestSystem.putProperty("k1", "v1")
        env <- system.property("k1")
      } yield assert(env)(isSome(equalTo("v1")))
    },
    testM("System returns the line separator when it is set") {
      for {
        _       <- TestSystem.setLineSeparator(",")
        lineSep <- system.lineSeparator
      } yield assert(lineSep)(equalTo(","))
    },
    testM("clock service can be overwritten") {
      val withLiveClock = TestEnvironment.live ++ Clock.live
      val time          = clock.nanoTime.provideLayer(withLiveClock)
      assertM(time)(isGreaterThan(0L))
    } @@ nonFlaky
  )
}
