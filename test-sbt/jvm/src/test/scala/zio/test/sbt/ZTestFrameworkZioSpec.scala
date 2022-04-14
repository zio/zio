package zio.test.sbt

import sbt.testing.{SuiteSelector, TaskDef}
import zio.{Duration, ZIO}
import zio.test.{Summary, TestAspect, ZIOSpecAbstract}
import zio.test.render.ConsoleRenderer
import zio.test.sbt.FrameworkSpecInstances.{
  RuntimeExceptionDuringLayerConstructionSpec,
  RuntimeExceptionSpec,
  SimpleSpec,
  TimeOutSpec
}
import zio.test.sbt.TestingSupport.{green, red}

import java.net.BindException
//import zio.test.sbt.TestingSupport.{blue, cyan, red}
import zio.test.{ZIOSpecDefault, assertCompletes, assertTrue, testConsole}

object ZTestFrameworkZioSpec extends ZIOSpecDefault {

  override def spec = suite("test framework in a more ZIO-centric way")(
    test("basic happy path")(
      for {
        _      <- loadAndExecuteAll(Seq(SimpleSpec))
        output <- testOutput
      } yield assertTrue(output.mkString("").contains("1 tests passed. 0 tests failed. 0 tests ignored."))
    ),
    test("displays timeouts")(
      for {
        _      <- loadAndExecuteAll(Seq(TimeOutSpec)).flip
        output <- testOutput
      } yield assertTrue(output.mkString("").contains("Timeout of 1 s exceeded."))
    ),
    test("displays runtime exceptions helpfully")(
      for {
        _      <- loadAndExecuteAll(Seq(RuntimeExceptionSpec)).flip
        output <- testOutput
      } yield assertTrue(
        output.mkString("").contains("0 tests passed. 1 tests failed. 0 tests ignored.")
      ) && assertTrue(
        output.mkString("").contains("Good luck ;)")
      )
    ),
//    test("displays runtime exceptions during spec layer construction")(
//      for {
//        returnError <-
//          loadAndExecuteAll(Seq(SimpleSpec, RuntimeExceptionDuringLayerConstructionSpec)).flip
//        _      <- ZIO.debug("Returned error: " + returnError)
//        output <- testOutput
//      } yield assertTrue(output.length == 2) &&
//        assertTrue(output(0).contains("Top-level defect prevented test execution")) &&
//        assertTrue(output(0).contains("java.net.BindException: Other Kafka container already grabbed your port")) &&
//        assertTrue(output(1).startsWith("0 tests passed. 0 tests failed. 0 tests ignored."))
//    ) @@ TestAspect.nonFlaky,
    test("ensure shared layers are not re-initialized")(
      for {
        _ <- loadAndExecuteAll(
               Seq(FrameworkSpecInstances.Spec1UsingSharedLayer, FrameworkSpecInstances.Spec2UsingSharedLayer)
             )
      } yield assertTrue(FrameworkSpecInstances.counter.get == 1)
    ),
    suite("warn when no tests are executed")(
      test("TODO")(
        for {
          _ <- loadAndExecuteAll(Seq())
        } yield assertCompletes
      )
    ),
    test("displays multi-colored lines")(
      for {
        _ <- loadAndExecuteAll(Seq(FrameworkSpecInstances.MultiLineSharedSpec)).ignore
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
        _ <- loadAndExecuteAll(
               Seq(FrameworkSpecInstances.SimpleFailingSharedSpec),
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

      } yield assertTrue(output.mkString("").contains(expected))
    }
  )

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
      console <- testConsole
      output  <- console.output
    } yield output

  private def loadAndExecuteAll[T <: ZIOSpecAbstract](
    specs: Seq[T],
    testArgs: Array[String] = Array.empty
  ): ZIO[Any, Throwable, Unit] = {
    val tasks =
      specs
        .map(_.getClass.getName)
        .map(fqn => new TaskDef(fqn, ZioSpecFingerprint, false, Array(new SuiteSelector)))
        .toArray

    new ZTestFramework()
      .runner(testArgs, Array(), getClass.getClassLoader)
      .tasksZ(tasks)
      .map(_.executeZ(FrameworkSpecInstances.dummyHandler))
      .getOrElse(ZIO.unit)
  }
}
