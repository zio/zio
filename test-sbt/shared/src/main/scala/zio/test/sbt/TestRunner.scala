package zio.test.sbt

import sbt.testing.{Runner, Task, TaskDef}
import zio.{Runtime, Scope, Trace, Unsafe, ZIOAppArgs, ZLayer}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.{ExecutionEventPrinter, Summary, TestArgs, TestOutput, ZIOSpecAbstract}

import java.util.concurrent.ConcurrentLinkedQueue

abstract class TestRunner(
  final override val args: Array[String],
  // attempt to do `final override val remoteArgs: Array[String]`
  // causes compiler error on Scala 3:
  //   error overriding method remoteArgs in trait Runner of type (): Array[String];
  //   value remoteArgs of type Array[String] has incompatible type
  // why overriding `args` parameter on the previous line does NOT cause
  // such error is anybody's guess...
  remoteArgs0: Array[String],
  testClassLoader: ClassLoader,
  runnerType: String
) extends Runner {
  @volatile private var isDone: Boolean = false
  private def verifyNonDone(): Unit     = if (isDone) throw new IllegalStateException(s"Runner $this is already done!")

  final override def remoteArgs(): Array[String] = remoteArgs0

  private val testArgs: TestArgs = TestArgs.parse(args)

  private val sharedRuntimes: ConcurrentLinkedQueue[zio.Runtime.Scoped[TestOutput]] = new ConcurrentLinkedQueue

  protected def sharedRuntimeSupported: Boolean

  private val summaries: ConcurrentLinkedQueue[Summary] = new ConcurrentLinkedQueue

  final protected def addSummary(summary: Summary): Unit = summaries.offer(summary)

  protected def sendSummary(summary: Summary): Unit

  protected def returnSummary: Boolean

  final override def tasks(taskDefs: Array[TaskDef]): Array[Task] =
    tasks(taskDefs, zio.Console.ConsoleLive)(Trace.empty).toArray

  final private[sbt] def tasks(
    taskDefs: Array[TaskDef],
    console: zio.Console
  )(implicit trace: Trace): Array[TestTask] = {
    verifyNonDone()

    val withSpecs: Array[(TaskDef, ZIOSpecAbstract)] = taskDefs.map(taskDef => taskDef -> loadSpec(taskDef))

    // Note: maybe it is because ConsoleLive is not used on non-JVM backends that tests print more than on JVM?
    val sharedRuntime: Option[zio.Runtime.Scoped[TestOutput]] =
      if (!sharedRuntimeSupported) None
      else
        Some {
          val sharedRuntime: Runtime.Scoped[TestOutput] = getSharedRuntime(withSpecs.map(_._2), console)
          sharedRuntimes.offer(sharedRuntime)
          sharedRuntime
        }

    withSpecs.map { case (taskDef, spec) => toTask(taskDef, spec, sharedRuntime) }
  }

  private def getSharedRuntime(
    specs: Array[ZIOSpecAbstract],
    console: zio.Console
  )(implicit trace: Trace): zio.Runtime.Scoped[TestOutput] = {
    val sharedTestOutputLayer: ZLayer[Any, Nothing, TestOutput] =
      ExecutionEventPrinter.live(console, testArgs.testEventRenderer, testArgs.reportsParent) >>> TestOutput.live

    val sharedLayerFromSpecs: ZLayer[Any, Any, Any] =
      (Scope.default <*> ZIOAppArgs.empty) >>> specs
        .map(_.bootstrap)
        .foldLeft(ZLayer.empty: ZLayer[ZIOAppArgs, Any, Any])(_ +!+ _)

    val sharedLayer: ZLayer[Any, Any, TestOutput] = sharedLayerFromSpecs +!+ sharedTestOutputLayer

    zio.Runtime.unsafe.fromLayer(sharedLayer)(trace, Unsafe)
  }

  private def toTask(
    taskDef: TaskDef,
    spec: ZIOSpecAbstract,
    sharedRuntime: Option[zio.Runtime.Scoped[TestOutput]]
  ): TestTask = new ZTestTask(
    taskDef,
    spec,
    runnerType,
    sendSummary,
    testArgs,
    sharedRuntime
  )

  // Used to de-serialize tasks on non-JVM backends
  final protected def toTask(taskDef: TaskDef): TestTask =
    toTask(taskDef, loadSpec(taskDef), None)

  private def loadSpec(taskDef: TaskDef): ZIOSpecAbstract = {
    val fqcn: String = TestRunner.moduleName(taskDef.fullyQualifiedName())
    // Creating the class from magic ether
    org.portablescala.reflect.Reflect
      .lookupLoadableModuleClass(fqcn, testClassLoader)
      .getOrElse(throw new ClassNotFoundException(s"failed to load object: $fqcn"))
      .loadModule()
      .asInstanceOf[ZIOSpecAbstract]
  }

  final override def done(): String = {
    verifyNonDone()
    isDone = true

    // If tests are forked, this will only be relevant in the forked
    // JVM, and will not be set in the original JVM.
    sharedRuntimes.forEach(_.unsafe.shutdown()(Unsafe))

    val summaries: Seq[Summary] = this.summaries.toArray(Array.empty[Summary])
    val total: Int              = summaries.map(_.total).sum
    val ignore: Int             = summaries.map(_.ignore).sum

    val summary: String =
      if (summaries.nonEmpty && total != ignore) {
        val compositeSummary: Summary = summaries.foldLeft(Summary.empty)(_.add(_))
        // Ensures summary is pretty in the same style as rest of the test output
        val renderedSummary: String = testArgs.testRenderer.renderSummary(compositeSummary)
        Colors.coloredLines(renderedSummary)
      } else if (ignore > 0)
        Colors.yellow("All eligible tests are currently ignored")
      else
        Colors.yellow("No tests were executed")

    if (returnSummary) summary
    else {
      // We eagerly print out the info here, rather than returning it
      // from this function as a workaround for this bug when running
      // tests in a forked JVM:
      //    https://github.com/sbt/sbt/issues/3510
      if (summaries.nonEmpty) println(summary)

      // Does not try to return a real summary, because we have already
      // printed this info directly to the console.
      "Completed tests"
    }
  }
}

object TestRunner {
  def moduleName(name: String): String = name.stripSuffix("$") + "$"
}
