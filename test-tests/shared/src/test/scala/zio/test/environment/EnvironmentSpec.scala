package zio.test.environment

import java.util.concurrent.TimeUnit

import zio._
import zio.duration._
import zio.test.Assertion._
import zio.test._

object EnvironmentSpec extends ZIOBaseSpec {

  def spec = suite("EnvironmentSpec")(
    testM("Clock returns time when it is set") {
      for {
        _    <- TestClock.setTime(1.millis)
        _    <- clock.sleep(1.millis)
        time <- clock.currentTime(TimeUnit.MILLISECONDS)
      } yield assert(time)(equalTo(1L))
    },
    testM("Console writes line to output") {
      for {
        _      <- console.putStrLn("First line")
        _      <- console.putStrLn("Second line")
        output <- TestConsole.output
      } yield assert(output)(equalTo(Vector("First line\n", "Second line\n")))
    },
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
    /*Live clock is used to seed random number generator;
            Node.js only has 1ms resolution so need to wait at least that long to avoid flakiness on ScalaJS*/
    testM("Check different copies of TestEnvironment are seeded with different seeds") {
      for {
        i <- Live.live(random.nextInt.provideLayer(TestRandom.random))
        _ <- Live.live(clock.sleep(1.millisecond))
        j <- Live.live(random.nextInt.provideLayer(TestRandom.random))
      } yield !assert(i)(equalTo(j))
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
    }
  )
}
