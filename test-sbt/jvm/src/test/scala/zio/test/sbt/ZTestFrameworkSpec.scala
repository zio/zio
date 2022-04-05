package zio.test.sbt

import sbt.testing._
import zio.test.Assertion.equalTo
import zio.test.ExecutionEvent.{RuntimeFailure, SectionEnd, SectionStart, Test}
import zio.test.render.ConsoleRenderer
import zio.test.sbt.TestingSupport._
import zio.test.{assertCompletes, assert => _, test => _, _}
import zio.{ZEnvironment, ZIO, ZLayer, ZTraceElement, durationInt}

import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object ZTestFrameworkSpec {

  def main(args: Array[String]): Unit =
    run(tests: _*)

  def tests: Seq[Try[Unit]] = Seq(
    test("should return correct fingerprints")(testFingerprints()),
    // TODO restore once we are calculating durations again. Fix for #6482
    //test("should report durations")(testReportDurations()),
//    test("should log messages")(testLogMessages()),
//    test("should correctly display colorized output for multi-line strings")(testColored()),
//    test("should test only selected test")(testTestSelection()),
//    test("should return summary when done")(testSummary()),
    test("should use a shared layer without re-initializing it")(testSharedLayer())
//    test("should warn when no tests are executed")(testNoTestsExecutedWarning())
  )

  def testFingerprints(): Unit = {
    val fingerprints = new ZTestFramework().fingerprints.toSeq
    assertEquals("fingerprints", fingerprints, Seq(ZioSpecFingerprint))
  }

  val dummyHandler: EventHandler = (_: Event) => ()

  def testReportDurations(): Unit = {
    val loggers  = Seq(new MockLogger)
    val reported = ArrayBuffer[ExecutionEvent]()

    loadAndExecute(timedSpecFQN, loggers = loggers)

    assert(reported.nonEmpty)
    reported.foreach(println)
    assert(
      reported.forall(event =>
        event match {
          case Test(_, _, _, _, duration, _) => duration > 0
          case RuntimeFailure(_, _, _, _)    => false
          case SectionStart(_, _, _)         => false
          case SectionEnd(_, _, _)           => false
        }
      ),
      s"reported events should have positive durations: $reported"
    )
  }

  def testLogMessages()(implicit trace: ZTraceElement): Unit = {
    val loggers = Seq(new MockLogger)

    loadAndExecute(failingSpecFQN, loggers = loggers)

    loggers.map(_.messages.map(_.withNoLineNumbers)) foreach { messages =>
      assertContains(
        "logs success",
        messages,
        Seq(
          s"${reset("info:")}     ${green("+")} passing test"
        )
      )
      assertContains(
        "logs errors",
        messages,
        Seq(
          s"${reset("info:")}     ${red("- failing test")}",
          s"${reset("info:")}       ${blue("1")} did not satisfy ${cyan("equalTo(2)")}",
          s"${reset("info:")}       ${assertSourceLocation()}"
        )
      )
      assertContains(
        "logs ignored message",
        messages,
        Seq(
          s"${reset("info:")}     ${yellow("-")} ${yellow("ignored test")}"
        )
      )
    }
  }

  def testColored(): Unit = {
    val loggers = Seq.fill(3)(new MockLogger)

    loadAndExecute(multiLineSpecFQN, loggers = loggers)
    loggers.map(_.messages) foreach (messages =>
      assertEquals(
        "logged messages",
        messages.drop(1).mkString("\n").withNoLineNumbers,
        List(
          s"${reset("info: ")}  ${red("- multi-line test")}",
          s"${reset("info: ")}    ${Console.BLUE}Hello,",
          s"${reset("info: ")}${blue("World!")} did not satisfy ${cyan("equalTo(Hello, World!)")}",
          s"${reset("info: ")}    ${assertSourceLocation()}",
          s"""${reset("info: ")}${ConsoleRenderer.render(Summary(0, 1, 0, ""))}"""
        ).mkString("\n")
      )
    )
  }

  def testTestSelection(): Unit = {
    val loggers = Seq(new MockLogger)

    loadAndExecute(failingSpecFQN, loggers = loggers, testArgs = Array("-t", "passing test"))

    loggers.map(_.messages) foreach { messages =>
      val results = messages.drop(1).mkString("\n")
      assertEquals(
        "logged messages",
        results,
        List(
          s"${reset("info:")} ${green("+")} some suite",
          s"${reset("info:")}     ${green("+")} passing test",
          s"""${reset("info: ")}${ConsoleRenderer.render(Summary(1, 0, 0, ""))}"""
        ).mkString("\n")
      )
    }
  }

  private val counter = new AtomicInteger(0)

  lazy val sharedLayer: ZLayer[Any, Nothing, Int] = {
    ZLayer.fromZIO(ZIO.succeed(counter.getAndUpdate(value => value + 1)))
  }

  val randomFailure =
    zio.test.assert(new java.util.Random().nextInt())(equalTo(2))

  def numberedTest(specIdx: Int, suiteIdx: Int, testIdx: Int) =
    zio.test.test(s"spec $specIdx suite $suiteIdx test $testIdx") {
      assertCompletes
    }

  lazy val spec1UsingSharedLayer = Spec1UsingSharedLayer.getClass.getName
  object Spec1UsingSharedLayer extends zio.test.ZIOSpec[Int] {
    override def layer = sharedLayer

    /*
      TODO
        - Create some big entities in each test, to highlight memory usage
        - Wrap BEGIN/END messages around specs, to see if they're overlapping
        - Check how large just the test reports are
            Some of these classes have thousands of lines of tests
     */
    val numberOfSuites = 1
    val numberOfTests  = 1
    def spec =
      suite("basic suite")(
        numberedTest(specIdx = 1, suiteIdx = 1, 1),
        numberedTest(specIdx = 1, suiteIdx = 1, 2),
        numberedTest(specIdx = 1, suiteIdx = 1, 3),
        numberedTest(specIdx = 1, suiteIdx = 1, 4)
      ) @@ TestAspect.parallel
  }

  lazy val spec2UsingSharedLayer = Spec2UsingSharedLayer.getClass.getName
  object Spec2UsingSharedLayer extends zio.test.ZIOSpec[Int] {
    override def layer = sharedLayer

    def spec =
      zio.test.test("test completes with shared layer 2") {
        assertCompletes
      }
  }

  def testSharedLayer(): Unit = {

    val loggers = Seq(new MockLogger)
    loadAndExecuteAll(Seq.fill(3)(spec1UsingSharedLayer), loggers, Array.empty)

    assert(counter.get() == 1)
  }

  def testSummary(): Unit = {
    val taskDef = new TaskDef(failingSpecFQN, ZioSpecFingerprint, false, Array())
    val runner  = new ZTestFramework().runner(Array(), Array(), getClass.getClassLoader)

    val task = runner
      .tasks(Array(taskDef))
      .map(task => task.asInstanceOf[ZTestTask])
      .map { zTestTask =>
        new ZTestTask(
          zTestTask.taskDef,
          zTestTask.testClassLoader,
          zTestTask.sendSummary.provideEnvironment(ZEnvironment(Summary(1, 0, 0, "foo"))),
          TestArgs.empty,
          zTestTask.spec
        )
      }
      .head

    // TODO Figure out how to get correct TestConsole instance in this non-ZIO realm.
    zio.Runtime.default.unsafeRun(
      for {
        console <- testConsole
        output  <- console.output
        _       <- ZIO.debug(s"output: ${output.mkString("\n")}")
      } yield ()
    )

    task.execute(_ => (), Array.empty)

    assertEquals("done contains summary", runner.done(), "foo\nDone")
  }

  def testNoTestsExecutedWarning(): Unit = {
    val taskDef = new TaskDef(failingSpecFQN, ZioSpecFingerprint, false, Array(new TestSelector("nope")))
    val runner  = new ZTestFramework().runner(Array(), Array(), getClass.getClassLoader)
    val task = runner
      .tasks(Array(taskDef))
      .map(task => task.asInstanceOf[ZTestTask])
      .map { zTestTask =>
        new ZTestTask(
          zTestTask.taskDef,
          zTestTask.testClassLoader,
          zTestTask.sendSummary.provideEnvironment(ZEnvironment(Summary(0, 0, 0, "foo"))),
          TestArgs.empty,
          zTestTask.spec
        )
      }
      .head

    task.execute(_ => (), Array.empty)

    assertEquals("warning is displayed", runner.done(), s"${Console.YELLOW}No tests were executed${Console.RESET}")
  }

  private def loadAndExecute(
    fqn: String,
    loggers: Seq[Logger],
    testArgs: Array[String] = Array.empty
  ) =
    loadAndExecuteAll(Seq(fqn), loggers, testArgs)

  private def loadAndExecuteAll(
    fqns: Seq[String],
    loggers: Seq[Logger],
    testArgs: Array[String]
  ) = {

    val tasks =
      fqns
        .map(fqn => new TaskDef(fqn, ZioSpecFingerprint, false, Array(new SuiteSelector)))
        .toArray
    val task = new ZTestFramework()
      .runner(testArgs, Array(), getClass.getClassLoader)
      .tasks(tasks)
      .head

    @scala.annotation.tailrec
    def doRun(tasks: Iterable[Task]): Unit = {
      val more = tasks.flatMap(_.execute(dummyHandler, loggers.toArray))
      if (more.nonEmpty) {
        doRun(more)
      }
    }

    doRun(Iterable(task))
  }

  lazy val failingSpecFQN = SimpleFailingSharedSpec.getClass.getName

  object SimpleFailingSharedSpec extends ZIOSpecDefault {
    def spec: Spec[Annotations, TestFailure[Any], TestSuccess] = zio.test.suite("some suite")(
      test("failing test") {
        zio.test.assert(1)(Assertion.equalTo(2))
      },
      test("passing test") {
        zio.test.assert(1)(Assertion.equalTo(1))
      },
      test("ignored test") {
        zio.test.assert(1)(Assertion.equalTo(2))
      } @@ TestAspect.ignore
    )
  }

  lazy val timedSpecFQN = TimedSharedSpec.getClass.getName
  object TimedSharedSpec extends ZIOSpecDefault {
    override def spec = test("timed passing test") {
      zio.test.assertCompletes
    } @@ TestAspect.before(Live.live(ZIO.sleep(5.millis))) @@ TestAspect.timed
  }

  lazy val multiLineSpecFQN = MultiLineSharedSpec.getClass.getName
  object MultiLineSharedSpec extends ZIOSpecDefault {
    def spec = test("multi-line test") {
      zio.test.assert("Hello,\nWorld!")(Assertion.equalTo("Hello, World!"))
    }
  }

  def assertSourceLocation()(implicit trace: ZTraceElement): String = {
    val filePath = Option(trace).collect { case ZTraceElement(_, file, _) =>
      file
    }
    filePath.fold("")(path => cyan(s"at $path:XXX"))
  }

  implicit class TestOutputOps(output: String) {
    def withNoLineNumbers(implicit trace: ZTraceElement): String = {
      val filePath = Option(trace).collect { case ZTraceElement(_, file, _) =>
        file
      }
      filePath.fold(output)(path => output.replaceAll(Pattern.quote(path + ":") + "\\d+", path + ":XXX"))
    }
  }
}
