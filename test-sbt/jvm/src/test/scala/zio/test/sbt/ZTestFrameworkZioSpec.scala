package zio.test.sbt

import sbt.testing.{SuiteSelector, TaskDef}
import zio.{Duration, ZIO}
import zio.test.Summary
import zio.test.render.ConsoleRenderer
import zio.test.sbt.FrameworkSpecInstances.{RuntimeExceptionSpec, SimpleSpec}
import zio.test.sbt.TestingSupport.{green, red}
//import zio.test.sbt.TestingSupport.{blue, cyan, red}
import zio.test.{ZIOSpecDefault, assertCompletes, assertTrue, testConsole}

object ZTestFrameworkZioSpec extends ZIOSpecDefault {

  override def spec = suite("test framework in a more ZIO-centric way")(
    test("basic happy path")(
      for {
        _      <- loadAndExecuteAll(Seq(SimpleSpec.getClass.getName))
        output <- testOutput
      } yield assertTrue(output.mkString("").contains("1 tests passed. 0 tests failed. 0 tests ignored."))
    ),
    // TODO Get this enabled
    test("displays runtime exceptions helpfully")(
      for {
        _      <- loadAndExecuteAll(Seq(RuntimeExceptionSpec.getClass.getName)).flip
        output <- testOutput.debug
      } yield assertTrue(output.mkString.contains("0 tests passed. 1 tests failed. 0 tests ignored.")) && assertTrue(output.mkString.contains("Good luck ;)"))
    ),
    test("ensure shared layers are not re-initialized")(
      for {
        _ <- loadAndExecuteAll(
               Seq(FrameworkSpecInstances.spec1UsingSharedLayer, FrameworkSpecInstances.spec2UsingSharedLayer)
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
        _ <- loadAndExecuteAll(Seq(FrameworkSpecInstances.multiLineSpecFQN)).ignore
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
        _      <- loadAndExecuteAll(Seq(FrameworkSpecInstances.failingSpecFQN), testArgs = Array("-t", "passing test"))
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

  private def loadAndExecuteAll(
    fqns: Seq[String],
    testArgs: Array[String] = Array.empty
  ): ZIO[Any, Throwable, Unit] = {

    val tasks =
      fqns
        .map(fqn => new TaskDef(fqn, ZioSpecFingerprint, false, Array(new SuiteSelector)))
        .toArray
    new ZTestFramework()
      .runner(testArgs, Array(), getClass.getClassLoader)
      .tasksZ(tasks)
      .map(_.executeZ(FrameworkSpecInstances.dummyHandler))
      .getOrElse(ZIO.unit)
  }
}
