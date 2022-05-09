package zio.test.sbt

import sbt.testing.{SuiteSelector, TaskDef}
import zio.{Duration, ZIO}
import zio.test.{ExecutionEventSink, Summary, TestAspect, TestConsole, ZIOSpecAbstract, ZIOSpecDefault, assertCompletes, assertTrue, testConsole}
import zio.test.render.ConsoleRenderer
import zio.test.sbt.FrameworkSpecInstances._
import zio.test.sbt.TestingSupport.{green, red}

object ZTestFrameworkZioSpec extends ZIOSpecDefault {

  override def spec = suite("test framework in a more ZIO-centric way")(
    test("basic happy path")(
      (for {
        _ <- testConsole.debug("Console at beginning of test")
        _      <- loadAndExecute(SimpleSpec)
        output <- testOutput
      } yield assertTrue(
        output.mkString("").contains("suite and test name")
      )
    )),
    test("displays timeouts")(
      for {
        _      <- loadAndExecute(TimeOutSpec).flip
        output <- testOutput
      } yield assertTrue(output.mkString("").contains("Timeout of 1 s exceeded.")) && assertTrue(output.length == 3)
    ),
    test("displays runtime exceptions helpfully")(
      for {
        _ <- ZIO.debug("Gonna execute some scary tests")
        _      <- loadAndExecute(RuntimeExceptionSpec)
        _ <- ZIO.debug("supposedly executed some scary tests")
        output <- testOutput
      } yield assertTrue(
        output.mkString("").contains("0 tests passed. 1 tests failed. 0 tests ignored.")
      ) && assertTrue(
        output.mkString("").contains("Good luck ;)")
      ) && assertTrue(output.length == 3)
    ),
    // TODO Restore this once
    //    https://github.com/zio/zio/pull/6614 is merged
    test("displays runtime exceptions during spec layer construction")(
      for {
        returnError <-
          loadAndExecuteAllZ(Seq(SimpleSpec, RuntimeExceptionDuringLayerConstructionSpec)).flip
      } yield assertTrue(returnError.exists(_.toString.contains("Other Kafka container already grabbed your port")))
    ) @@ TestAspect.nonFlaky,
    test("ensure shared layers are not re-initialized")(
      for {
        _ <- loadAndExecuteAllZ(
               Seq(FrameworkSpecInstances.Spec1UsingSharedLayer, FrameworkSpecInstances.Spec2UsingSharedLayer)
             )
      } yield assertTrue(FrameworkSpecInstances.counter.get == 1)
    ),
    suite("warn when no tests are executed")(
      test("TODO")(
        for {
          _ <- loadAndExecuteAllZ(Seq())
          _ <- testOutput.debug("no tests")
        } yield assertCompletes
      )
    ),
    test("displays multi-colored lines")(
      for {
        _ <- loadAndExecuteAllZ(Seq(FrameworkSpecInstances.MultiLineSharedSpec)).ignore
        output <-
          testOutput
        expected =
          List(
            s"  ${red("- multi-line test")}",
            s"    ${Console.BLUE}Hello,"
            //  TODO Figure out what non-printing garbage is breaking the next line
            // s"${blue("World!")} did not satisfy ${cyan("equalTo(Hello, World!)")}",
            // s"    ${assertSourceLocation()}",
            // s"""${ConsoleRenderer.render(Summary(0, 1, 0, ""))}"""
          ).mkString("\n")
      } yield assertTrue(output.mkString("").contains(expected))
    ),
    test("only executes selected test") {
      for {
        _ <- loadAndExecute(FrameworkSpecInstances.SimpleFailingSharedSpec,
               testArgs = Array("-t", "passing test")
             )
        output <- testOutput
        testTime =
          extractTestRunDuration(output)

        expected =
          List(
            s"${green("+")} some suite",
            s"    ${green("+")} passing test",
            s"""${ConsoleRenderer.render(Summary(1, 0, 0, "", testTime))}"""
          ).mkString("\n")

      } yield assertTrue(output.mkString("").contains(expected)) && assertTrue(output.length == 3)
    }
  ) // TODO restore once the transition to flat specs is complete

  private val durationPattern = "Executed in (\\d+) (.*)".r
  private def extractTestRunDuration(output: Vector[String]): zio.Duration = {
    val (testTimeNumber, testTimeUnit) = output.mkString
      .split("\n")
      .collect { case durationPattern(duration, unit) =>
        (duration, unit)
      }
      .head

    testTimeUnit match {
      case "ms"  => Duration.fromMillis(testTimeNumber.toInt.toLong)
      case "ns"  => Duration.fromNanos(testTimeNumber.toInt.toLong)
      case other => throw new IllegalStateException("Unexpected duration unit: " + other)
    }
  }

  private val testOutput =
    for {
      console <- testConsole.debug("testOutput retrieved in framework zioSpec")
      output  <- console.output
    } yield output

  private def loadAndExecute[T <: ZIOSpecAbstract](
    spec: T,
    testArgs: Array[String] = Array.empty
  ): ZIO[Any, Throwable, Unit] =
    loadAndExecuteAllZ(Seq(spec), testArgs).mapError(_.head)

  private def loadAndExecuteAllZ[T <: ZIOSpecAbstract](
                                                       specs: Seq[T],
                                                       testArgs: Array[String] = Array.empty
                                                     ): ZIO[Any, ::[Throwable], Unit] = {
    val tasks =
      specs
        .map(_.getClass.getName)
        .map(fqn => new TaskDef(fqn, ZioSpecFingerprint, false, Array(new SuiteSelector)))
        .toArray

    for {
      testC <- testConsole
      tasksZ <-
        ZIO.attempt(
          new ZTestFramework()
            .runner(testArgs, Array(), getClass.getClassLoader)
            .tasksZ(tasks, testC)
        ).mapError(error => ::(error, Nil))
      _ <- ZIO.validate(tasksZ.toList){ t => t.run(FrameworkSpecInstances.dummyHandler)}
    } yield ()


//        .map( t => ZIO.attempt(t.run(FrameworkSpecInstances.dummyHandler)))
//        .headOption
//        .getOrElse(ZIO.unit)
  }
}
