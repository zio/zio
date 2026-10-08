package zio.test

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.util.concurrent.TimeUnit

object EnvironmentSpec extends ZIOBaseSpec {

  def spec = suite("EnvironmentSpec")(
    test("Clock returns time when it is set") {
      for {
        _    <- TestClock.adjust(1.millis)
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
    } @@ withLiveClock,
    test("TestEnvironment.live installs fresh services and restores the previous services") {
      val layer = liveEnvironment >>> TestEnvironment.live
      val acquire =
        (for {
          environment      <- ZIO.environment[TestEnvironment]
          testServices     <- TestServices.currentServices.get
          defaultServices  <- DefaultServices.currentServices.get
          fiberIdGenerator <- FiberRef.currentFiberIdGenerator.get
        } yield (
          environment,
          testServices,
          defaultServices,
          fiberIdGenerator
        )).provideLayer(layer)

      for {
        testServicesBefore     <- TestServices.currentServices.get
        defaultServicesBefore  <- DefaultServices.currentServices.get
        fiberIdGeneratorBefore <- FiberRef.currentFiberIdGenerator.get
        first                  <- acquire
        second                 <- acquire
        testServicesAfter      <- TestServices.currentServices.get
        defaultServicesAfter   <- DefaultServices.currentServices.get
        fiberIdGeneratorAfter  <- FiberRef.currentFiberIdGenerator.get
      } yield assertTrue(
        first._1.get[Annotations] eq first._2.get[Annotations],
        first._1.get[Live] eq first._2.get[Live],
        first._1.get[Sized] eq first._2.get[Sized],
        first._1.get[TestConfig] eq first._2.get[TestConfig],
        first._3.get[Clock].isInstanceOf[TestClock],
        first._3.get[Console].isInstanceOf[TestConsole],
        first._3.get[Random].isInstanceOf[TestRandom],
        first._3.get[System].isInstanceOf[TestSystem],
        first._1.get[Annotations] ne second._1.get[Annotations],
        first._3.get[Clock] ne second._3.get[Clock],
        first._1.get[TestConfig] eq second._1.get[TestConfig],
        testServicesBefore eq testServicesAfter,
        defaultServicesBefore eq defaultServicesAfter,
        fiberIdGeneratorBefore eq first._4,
        fiberIdGeneratorBefore eq fiberIdGeneratorAfter
      )
    },
    test("testEnvironment installs and restores the monotonic FiberId generator") {
      FiberRef.currentFiberIdGenerator.locally(FiberId.Gen.Live) {
        for {
          before <- FiberRef.currentFiberIdGenerator.get
          inside <- FiberRef.currentFiberIdGenerator.get.provideLayer(testEnvironment)
          after  <- FiberRef.currentFiberIdGenerator.get
        } yield assertTrue(
          before eq FiberId.Gen.Live,
          inside eq FiberId.Gen.Monotonic,
          after eq FiberId.Gen.Live
        )
      }
    }
  )
}
