package zio.test.sbt

import sbt.testing.{Status, TestSelector}
import zio.test.sbt.Colors.{green, red, yellow}
import zio.test.sbt.ExecuteSpecs.{getEvents, getOutput, getOutputs}
import zio.test.{Spec, TestAspect, TestEnvironment, TestResult, ZIOSpecDefault, assertTrue, assert}
import zio.{Scope, ZIO}

object ZTestFrameworkSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("test framework")(
    test("framework fingerprints should be correct")(
      assertTrue(new ZTestFramework().fingerprints().toSeq == Seq(ZioSpecFingerprint))
    ),
    test("basic happy path")(
      for {
        output <- getOutput(FrameworkSpecInstances.SimpleSpec)
      } yield assertTrue(
        output ==
          Seq(
            s"${green("+")} simple suite\n",
            s"  ${green("+")} spec 1 suite 1 test 1\n"
          )
      )
    ),
    test("renders suite names 1 time in plus-combined specs")(
      for {
        output <- getOutput(FrameworkSpecInstances.CombinedWithPlusSpec)
      } yield assertTrue(output.length == 3) && (
        // Look for more generic way of asserting on these lines that can be shuffled
        assertTrue(
          output ==
            Seq(
              s"${green("+")} spec A\n",
              s"  ${green("+")} successful test\n",
              s"  ${yellow("-")} ${yellow("failing test")} - ignored: 1\n"
            )
        ) || assertTrue(
          output ==
            Seq(
              s"${green("+")} spec A\n",
              s"  ${yellow("-")} ${yellow("failing test")} - ignored: 1\n",
              s"  ${green("+")} successful test\n"
            )
        )
      )
    ),
    test("renders suite names 1 time in commas-combined specs")(
      for {
        output <- getOutput(FrameworkSpecInstances.CombinedWithCommasSpec)
      } yield assertTrue(output.length == 3) && (
        // Look for more generic way of asserting on these lines that can be shuffled
        assertTrue(
          output ==
            Seq(
              s"${green("+")} spec A\n",
              s"  ${green("+")} successful test\n",
              s"  ${yellow("-")} ${yellow("failing test")} - ignored: 1\n"
            )
        ) || assertTrue(
          output ==
            Seq(
              s"${green("+")} spec A\n",
              s"  ${yellow("-")} ${yellow("failing test")} - ignored: 1\n",
              s"  ${green("+")} successful test\n"
            )
        )
      )
    ),
    test("displays timeouts")(
      for {
        output <- getOutput(FrameworkSpecInstances.TimeOutSpec)
      } yield assertTrue(output.mkString("").contains("Timeout of 1 s exceeded.")) && assertTrue(output.length == 2)
    ),
    test("displays runtime exceptions helpfully")(
      for {
        output <- getOutput(FrameworkSpecInstances.RuntimeExceptionSpec)
      } yield assertTrue(
        output.mkString("").contains("Good luck ;)")
      ) && assertTrue(output.length == 2)
    ),
    test("displays runtime exceptions during spec layer construction")(
      for {
        returnError <-
          getOutputs(
            Seq(FrameworkSpecInstances.SimpleSpec, FrameworkSpecInstances.RuntimeExceptionDuringLayerConstructionSpec)
          ).flip
      } yield assertTrue(returnError.exists(_.toString.contains("Other Kafka container already grabbed your port")))
    ) @@ TestAspect.nonFlaky,
    test("ensure shared layers are not re-initialized")(
      for {
        _ <- getOutputs(Seq(FrameworkSpecInstances.Spec1UsingSharedLayer, FrameworkSpecInstances.Spec2UsingSharedLayer))
      } yield assertTrue(FrameworkSpecInstances.counter.get == 1)
    ) @@ TestAspect.jvmOnly, // see ZTestBackend.sharedLayerSupported
    test("displays multi-colored lines")(
      for {
        output <- getOutputs(Seq(FrameworkSpecInstances.MultiLineSharedSpec))
        expected =
          Seq(
            s"  ${red("- multi-line test")}",
            s"    ${Console.BLUE}Hello,"
            //  TODO Figure out what non-printing garbage is breaking the next line
            // s"${blue("World!")} did not satisfy ${cyan("equalTo(Hello, World!)")}",
            // s"    ${assertSourceLocation()}",
            // s"""${ConsoleRenderer.render(Summary(0, 1, 0, ""))}"""
          ).mkString("\n")
      } yield assertTrue(output.mkString("").contains(expected))
    ) @@ TestAspect.ignore,
    test("only executes selected test") {
      for {
        output <- getOutput(FrameworkSpecInstances.SimpleFailingSharedSpec, args = Array("-t", "passing test"))
        expected =
          Seq(
            s"${green("+")} some suite\n",
            s"  ${green("+")} passing test\n"
          )

      } yield assertTrue(output.equals(expected))
    },
    test("only execute test with specified tag") {
      for {
        output <- getOutput(FrameworkSpecInstances.TagsSpec, args = Array("-tags", "IntegrationTest"))
        expected =
          Seq(
            s"${green("+")} tag suite\n",
            s"""  ${green("+")} integration test - tagged: "IntegrationTest"\n"""
          )
      } yield assertTrue(output.equals(expected))
    } @@ TestAspect.flaky,
    test("do not execute test with ignored tag") {
      for {
        output <- getOutput(FrameworkSpecInstances.TagsSpec, args = Array("-ignore-tags", "IntegrationTest"))
        expected =
          Seq(
            s"${green("+")} tag suite\n",
            s"""  ${green("+")} unit test - tagged: "UnitTest"\n"""
          )
      } yield assertTrue(output.equals(expected))
    },
    test("do not create layers for test with ignored tag") {
      for {
        output <- getOutput(FrameworkSpecInstances.TagsSpecWithFailingEnv, args = Array("-ignore-tags", "NotExecuted"))
      } yield {
        // Verify all tests with "Executed" tag ran successfully
        assertTrue(
          output.exists(_.contains("""should be executed - 1 - tagged: "Executed"""")),
          output.exists(_.contains("""should be executed - 3 - tagged: "Executed"""")),
          output.exists(_.contains("""should be executed - 5 - tagged: "Executed"""")),
          output.exists(_.contains("""should be executed - 7 - tagged: "Executed"""")),
          output.exists(_.contains("""should be executed - 9 - tagged: "Executed""""))
        ) &&
        // Verify that tests with "NotExecuted" tag did NOT run
        assertTrue(
          !output.exists(_.contains("""should not be executed - 0""")),
          !output.exists(_.contains("""should not be executed - 2""")),
          !output.exists(_.contains("""should not be executed - 4""")),
          !output.exists(_.contains("""should not be executed - 6""")),
          !output.exists(_.contains("""should not be executed - 8"""))
        ) &&
        // Verify that layers for filtered tests were NOT constructed
        // This is the critical assertion: if layers were built, we'd see these error messages
        assertTrue(
          !output.exists(_.contains("should not be called - 0")),
          !output.exists(_.contains("should not be called - 2")),
          !output.exists(_.contains("should not be called - 4")),
          !output.exists(_.contains("should not be called - 6")),
          !output.exists(_.contains("should not be called - 8"))
        )
      }
    },
    test("honor `TestSelector`s") {
      for {
        events         <- getEvents(FrameworkSpecInstances.SimpleFailingSharedSpec)
        failedSelectors = events.collect { case e if e.status() == Status.Failure => e.selector() }.toArray
        failedEvents   <- getEvents(FrameworkSpecInstances.SimpleFailingSharedSpec, selectors = failedSelectors)
      } yield assertTrue(events.length == 3) &&
        assertTrue(failedSelectors.length == 1) &&
        assertTrue(failedEvents.length == 1) &&
        assertTrue(failedEvents.head.status() == Status.Failure) &&
        assertTrue(failedEvents.head.selector().asInstanceOf[TestSelector].testName() == "some suite - failing test")
    },
    test("match tests by long names") {
      def testName(testSelector: String): ZIO[Any, ::[Throwable], String] = getEvents(
        FrameworkSpecInstances.NestedSpec,
        selectors = Array(new TestSelector(testSelector))
      ).map(_.head.selector().asInstanceOf[TestSelector].testName())
      def verify(testName: String): TestResult = assertTrue(testName == "outer - inner - test")
      for {
        short  <- testName("test")
        medium <- testName("inner - test")
        long   <- testName("outer - inner - test")
      } yield verify(short) && verify(medium) && verify(long)
    }
  )
}
