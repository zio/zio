package zio.test.mock

import java.util.concurrent.TimeUnit

import scala.Predef.{ assert => SAssert }
import scala.util.{ Random => SRandom }

import zio._
import zio.duration._

object EnvironmentSpec extends DefaultRuntime {

  def run(): Unit = {
    SAssert(currentTime, "MockEnvironment Clock returns time when it is set")
    SAssert(putStrLn, "MockEnvironment Console writes line to output")
    SAssert(getStrLn, "MockEnvironment Console reads line from input")
    SAssert(nextInt, "MockEnvironment Random returns next integer when data is fed")
    SAssert(env, "MockEnvironment System returns an environment variable when it is set")
    SAssert(property, "MockEnvironment System returns a property when it is set ")
    SAssert(lineSeparator, "MockEnvironment System returns the line separator when it is set ")
  }

  def withEnvironment[E, A](zio: ZIO[MockEnvironment, E, A]): A =
    unsafeRun(mockEnvironmentManaged.use[Any, E, A](r => zio.provide(r)))

  def currentTime =
    withEnvironment {
      for {
        _    <- MockClock.setTime(1.millis)
        time <- clock.currentTime(TimeUnit.MILLISECONDS)
      } yield time == 1L
    }

  def putStrLn =
    withEnvironment {
      for {
        _      <- console.putStrLn("First line")
        _      <- console.putStrLn("Second line")
        output <- MockConsole.output
      } yield output == Vector("First line\n", "Second line\n")
    }

  def getStrLn =
    withEnvironment {
      for {
        _      <- MockConsole.feedLines("Input 1", "Input 2")
        input1 <- console.getStrLn
        input2 <- console.getStrLn
      } yield (input1 == "Input 1") && (input2 == "Input 2")
    }

  def nextInt =
    withEnvironment {
      for {
        _ <- MockRandom.setSeed(4491842685265857478L)
        n <- random.nextInt
      } yield n == new SRandom(4491842685265857478L).nextInt()
    }

  def env =
    withEnvironment {
      for {
        _   <- MockSystem.putEnv("k1", "v1")
        env <- system.env("k1")
      } yield env == Some("v1")
    }

  def property =
    withEnvironment {
      for {
        _   <- MockSystem.putProperty("k1", "v1")
        env <- system.property("k1")
      } yield env == Some("v1")
    }

  def lineSeparator =
    withEnvironment {
      for {
        _       <- MockSystem.setLineSeparator(",")
        lineSep <- system.lineSeparator
      } yield lineSep == ","
    }
}
