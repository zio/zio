package zio.test.sbt

import sbt.testing._
import zio.test.Assertion.equalTo
import zio.test.sbt.TestingSupport._
import zio.test.{assertCompletes, assert => _, test => _, _}
import zio.{Has, ZDeps, ZIO, ZTraceElement, durationInt}

import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object ZTestFrameworkSpec {

  def main(args: Array[String]): Unit =
    run(tests: _*)

  def tests: Seq[Try[Unit]] = Seq(
    test("should return correct fingerprints")(testFingerprints()),
    test("should report events")(testReportEvents()),
    test("should report durations")(testReportDurations()),
    test("should log messages")(testLogMessages()),
    test("should correctly display colorized output for multi-line strings")(testColored()),
    test("should test only selected test")(testTestSelection()),
    test("should return summary when done")(testSummary()),
    test("should use a shared deps without re-initializing it")(testSharedDeps()),
    test("should warn when no tests are executed")(testNoTestsExecutedWarning())
  )

  def testFingerprints(): Unit = {
    val fingerprints = new ZTestFramework().fingerprints.toSeq
    assertEquals("fingerprints", fingerprints, Seq(RunnableSpecFingerprint, ZioSpecFingerprint))
  }

  def testReportEvents(): Unit = {
    val reported = ArrayBuffer[Event]()
    loadAndExecute(failingSpecFQN, reported.append(_))

    val expected = Set(
      sbtEvent(failingSpecFQN, "failing test", Status.Failure),
      sbtEvent(failingSpecFQN, "passing test", Status.Success),
      sbtEvent(failingSpecFQN, "ignored test", Status.Ignored)
    )

    assertEquals("reported events", reported.toSet, expected)
  }

  private def sbtEvent(fqn: String, label: String, status: Status) =
    ZTestEvent(fqn, new TestSelector(label), status, None, 0, RunnableSpecFingerprint)

  def testReportDurations(): Unit = {
    val reported = ArrayBuffer[Event]()
    loadAndExecute(timedSpecFQN, reported.append(_))

    assert(reported.forall(_.duration() > 0), s"reported events should have positive durations: $reported")
  }

  def testLogMessages()(implicit trace: ZTraceElement): Unit = {
    val loggers = Seq.fill(3)(new MockLogger)

    loadAndExecute(failingSpecFQN, loggers = loggers)

    loggers.map(_.messages) foreach (messages =>
      assertEquals(
        "logged messages",
        messages.mkString.split("\n").dropRight(1).mkString("\n").withNoLineNumbers,
        List(
          s"${reset("info:")} ${red("- some suite")} - ignored: 1",
          s"${reset("info:")}   ${red("- failing test")}",
          s"${reset("info:")}     ${blue("1")} did not satisfy ${cyan("equalTo(2)")}",
          s"${reset("info:")}     ${assertSourceLocation()}",
          reset("info: "),
          s"${reset("info:")}   ${green("+")} passing test",
          s"${reset("info:")}   ${yellow("-")} ${yellow("ignored test")} - ignored: 1"
        ).mkString("\n")
      )
    )
  }

  def testColored(): Unit = {
    val loggers = Seq.fill(3)(new MockLogger)

    loadAndExecute(multiLineSpecFQN, loggers = loggers)
    loggers.map(_.messages) foreach (messages =>
      assertEquals(
        "logged messages",
        messages.mkString.split("\n").dropRight(1).mkString("\n").withNoLineNumbers,
        List(
          s"${reset("info: ")}${red("- multi-line test")}",
          s"${reset("info: ")}  ${Console.BLUE}Hello,",
          s"${reset("info: ")}${blue("World!")} did not satisfy ${cyan("equalTo(Hello, World!)")}",
          s"${reset("info: ")}  ${assertSourceLocation()}",
          s"${reset("info: ")}"
        ).mkString("\n")
//          .mkString("\n")
//          .split('\n')
//          .map(s"${reset("info:")} " + _)
//          .mkString("\n")
      )
    )
  }

  def testTestSelection(): Unit = {
    val loggers = Seq(new MockLogger)

    loadAndExecute(failingSpecFQN, loggers = loggers, testArgs = Array("-t", "passing test"))

    loggers.map(_.messages) foreach (messages =>
      assertEquals(
        "logged messages",
        messages.mkString.split("\n").dropRight(1).mkString("\n"),
        List(
          s"${reset("info:")} ${green("+")} some suite",
          s"${reset("info:")}   ${green("+")} passing test"
        ).mkString("\n")
      )
    )
  }

  private val counter = new AtomicInteger(0)

  lazy val sharedDeps: ZDeps[Any, Nothing, Has[Int]] = {
    ZDeps.fromZIO(ZIO.succeed(counter.getAndUpdate(value => value + 1)))
  }

  val randomFailure =
    zio.test.assert(new java.util.Random().nextInt())(equalTo(2))

  def numberedTest(specIdx: Int, suiteIdx: Int, testIdx: Int) =
    zio.test.test(s"spec $specIdx suite $suiteIdx test $testIdx") {
      assertCompletes
//      randomFailure
    }

  lazy val spec1UsingSharedDeps = Spec1UsingSharedDeps.getClass.getName
  object Spec1UsingSharedDeps extends zio.test.ZIOSpec[Has[Int]] {
    override def deps = sharedDeps

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

  lazy val spec2UsingSharedDeps = Spec2UsingSharedDeps.getClass.getName
  object Spec2UsingSharedDeps extends zio.test.ZIOSpec[Has[Int]] {
    override def deps = sharedDeps

    def spec =
      zio.test.test("test completes with shared deps 2") {
        assertCompletes
      }
  }

  def testSharedDeps(): Unit = {
    val reported = ArrayBuffer[Event]()

//    loadAndExecuteAll(Seq.fill(200)(spec2UsingSharedDeps), reported.append(_))
    loadAndExecuteAll(Seq.fill(2)(spec1UsingSharedDeps), reported.append(_))

    assert(counter.get() == 1)
  }

  def testSummary(): Unit = {
    val taskDef = new TaskDef(failingSpecFQN, RunnableSpecFingerprint, false, Array())
    val runner  = new ZTestFramework().runner(Array(), Array(), getClass.getClassLoader)
    val task = runner
      .tasks(Array(taskDef))
      .map(task => task.asInstanceOf[ZTestTask])
      .map { zTestTask =>
        new ZTestTask(
          zTestTask.taskDef,
          zTestTask.testClassLoader,
          zTestTask.sendSummary.provide(Summary(1, 0, 0, "foo")),
          TestArgs.empty,
          zTestTask.spec
        )
      }
      .head

    task.execute(_ => (), Array.empty)

    assertEquals("done contains summary", runner.done(), "foo\nDone")
  }

  def testNoTestsExecutedWarning(): Unit = {
    val taskDef = new TaskDef(failingSpecFQN, RunnableSpecFingerprint, false, Array())
    val runner  = new ZTestFramework().runner(Array(), Array(), getClass.getClassLoader)
    val task = runner
      .tasks(Array(taskDef))
      .map(task => task.asInstanceOf[ZTestTask])
      .map { zTestTask =>
        new ZTestTask(
          zTestTask.taskDef,
          zTestTask.testClassLoader,
          zTestTask.sendSummary.provide(Summary(0, 0, 0, "foo")),
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
    eventHandler: EventHandler = _ => (),
    loggers: Seq[Logger] = Nil,
    testArgs: Array[String] = Array.empty
  ) =
    loadAndExecuteAll(Seq(fqn), eventHandler, loggers, testArgs)

  private def loadAndExecuteAll(
    fqns: Seq[String],
    eventHandler: EventHandler,
    loggers: Seq[Logger] = Nil,
    testArgs: Array[String] = Array.empty
  ) = {
    val tasks =
      fqns
        .map(fqn =>
          if (fqn.contains("Shared"))
            new TaskDef(fqn, ZioSpecFingerprint, false, Array(new SuiteSelector))
          else
            new TaskDef(fqn, RunnableSpecFingerprint, false, Array(new SuiteSelector))
        )
        .toArray
    val task = new ZTestFramework()
      .runner(testArgs, Array(), getClass.getClassLoader)
      .tasks(tasks)
      .head

    @scala.annotation.tailrec
    def doRun(tasks: Iterable[Task]): Unit = {
      val more = tasks.flatMap(_.execute(eventHandler, loggers.toArray))
      if (more.nonEmpty) {
        doRun(more)
      }
    }
    doRun(Iterable(task))
  }

  lazy val failingSpecFQN = SimpleFailingSpec.getClass.getName
  object SimpleFailingSpec extends DefaultRunnableSpec {
    def spec: Spec[Has[Annotations], TestFailure[Any], TestSuccess] = zio.test.suite("some suite")(
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

  lazy val timedSpecFQN = TimedSpec.getClass.getName
  object TimedSpec extends DefaultRunnableSpec {
    override def spec: ZSpec[Environment, Failure] = test("timed passing test") {
      zio.test.assertCompletes
    } @@ TestAspect.before(Live.live(ZIO.sleep(5.millis))) @@ TestAspect.timed
  }

  lazy val multiLineSpecFQN = MultiLineSpec.getClass.getName
  object MultiLineSpec extends DefaultRunnableSpec {
    def spec: ZSpec[Environment, Failure] = test("multi-line test") {
      zio.test.assert("Hello,\nWorld!")(Assertion.equalTo("Hello, World!"))
    }
  }

  def assertSourceLocation()(implicit trace: ZTraceElement): String = {
    val filePath = Option(trace).collect { case ZTraceElement.SourceLocation(_, file, _, _) =>
      file
    }
    filePath.fold("")(path => cyan(s"at $path:XXX"))
  }

  implicit class TestOutputOps(output: String) {
    def withNoLineNumbers(implicit trace: ZTraceElement): String = {
      val filePath = Option(trace).collect { case ZTraceElement.SourceLocation(_, file, _, _) =>
        file
      }
      filePath.fold(output)(path => output.replaceAll(Pattern.quote(path + ":") + "\\d+", path + ":XXX"))
    }
  }
}
