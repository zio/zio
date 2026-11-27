package zio.test.sbt

import zio.stacktracer.TracingImplicits.disableAutoTrace
import sbt.testing.{EventHandler, Selector, Task, TaskDef, TestSelector, TestWildcardSelector}
import zio.{Scope, Trace, ZIO, ZIOAppArgs, ZLayer}
import zio.test.{Summary, TestArgs, TestEnvironment, TestOutput, ZIOSpecAbstract, ZTestEventHandler, testEnvironment}

/**
 * The base of [[ZTestTask]]: an sbt task that runs one spec.
 *
 * Before it runs the spec, the task installs the fiber dump signal handlers of [[SignalHandlers]]. When every selector of its `TaskDef` is a `TestSelector` or a `TestWildcardSelector`, the selector names are added to the `-t` search terms, so only the matching tests run; with any other selector, the spec runs with the test arguments unchanged. Test results are reported to sbt through [[ZTestEventHandlerSbt]].
 */
abstract class TestTask(
  // attempt to do `final override val taskDef: TaskDef`
  // causes compiler error on Scala 3:
  //   error overriding method taskDef in trait Task of type (): sbt.testing.TaskDef;
  //   value taskDef of type sbt.testing.TaskDef has incompatible type
  taskDef0: TaskDef,
  spec: ZIOSpecAbstract,
  sendSummary: Summary => Unit,
  testArgs: TestArgs,
  sharedRuntime: Option[zio.Runtime.Scoped[TestOutput]]
) extends Task {
  /** Returns the task definition that names the spec. */
  final override def taskDef(): TaskDef = taskDef0

  /** Returns no tags. */
  final override def tags(): Array[String] = Array.empty

  /**
   * Returns the unsafe API of the shared runtime of this task, or of `Runtime.default` when the task has no shared runtime.
   */
  final protected def unsafeAPI: zio.Runtime[Any]#UnsafeAPI = sharedRuntime.getOrElse(zio.Runtime.default).unsafe

  // Note: SBT loggers should be hooked in...
  final private[sbt] def run(eventHandler: EventHandler)(implicit trace: Trace): ZIO[Any, Throwable, Unit] = {
    SignalHandlers.install()

    val testEventHandler: ZTestEventHandler = new ZTestEventHandlerSbt(
      eventHandler,
      taskDef(),
      testArgs.testRenderer
    )

    sharedRuntime
      .map(runSpecWithSharedRuntimeLayer(testEventHandler, _))
      .getOrElse(runSpecAsApp(testEventHandler))
  }

  // Preferred way of running tests, uses shared layer.
  private def runSpecWithSharedRuntimeLayer(
    testEventHandler: ZTestEventHandler,
    sharedRuntime: zio.Runtime.Scoped[TestOutput]
  )(implicit trace: Trace): ZIO[Any, Throwable, Unit] =
    (for {
      summary <-
        spec.runSpecWithSharedRuntimeLayer(
          taskDef().fullyQualifiedName(),
          spec.spec,
          argsWithSearchTermsFromSelectors,
          sharedRuntime,
          testEventHandler
        )
      _ <- ZIO.succeed(sendSummary(summary))
    } yield ())
      .provideLayer(sharedFilledTestLayer)

  // Used on backends where shared layer is not supported.
  private def runSpecAsApp(
    testEventHandler: ZTestEventHandler
  )(implicit trace: Trace): ZIO[Any, Throwable, Unit] = ZIO.consoleWith { console =>
    (for {
      summary <-
        spec.runSpecAsApp(
          spec.spec,
          argsWithSearchTermsFromSelectors,
          console,
          testEventHandler
        )
      _ <- ZIO.succeed(sendSummary(summary))
    } yield ())
      .provideLayer(sharedFilledTestLayer +!+ spec.bootstrap)
      .mapError {
        case throwable: Throwable => throwable
        case other                => new RuntimeException(s"Unknown error during tests: $other")
      }
  }

  private def sharedFilledTestLayer: ZLayer[Any, Nothing, TestEnvironment with ZIOAppArgs with Scope] =
    ZIOAppArgs.empty +!+ testEnvironment +!+ Scope.default

  private def argsWithSearchTermsFromSelectors: TestArgs = {
    val selectors: Array[Selector] = taskDef().selectors()
    if (
      selectors.forall(selector => selector.isInstanceOf[TestSelector] || selector.isInstanceOf[TestWildcardSelector])
    ) {
      val terms: Seq[String] = selectors.toList.collect {
        case ts: TestSelector          => ts.testName()
        case tws: TestWildcardSelector => tws.testWildcard()
      }
      testArgs.copy(testSearchTerms = testArgs.testSearchTerms ++ terms)
    } else testArgs
  }
}
