package zio.test.sbt

import sbt.testing.{EventHandler, Logger, Selector, Task, TaskDef, TestSelector, TestWildcardSelector}
import zio.{CancelableFuture, Console, Scope, Trace, Unsafe, ZEnvironment, ZIO, ZIOAppArgs, ZLayer}
import zio.test._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import zio.stacktracer.TracingImplicits.disableAutoTrace

abstract class BaseTestTask[T](
  taskDef0: TaskDef,
  val testClassLoader: ClassLoader,
  val sendSummary: SendSummary,
  val args: TestArgs,
  val spec: ZIOSpecAbstract,
  val runtime: zio.Runtime[T],
  val console: Console
) extends Task {

  final override def taskDef(): TaskDef = taskDef0

  protected def sharedFilledTestLayer(implicit
    trace: Trace
  ): ZLayer[Any, Nothing, TestEnvironment with ZIOAppArgs with Scope] =
    ZIOAppArgs.empty +!+ testEnvironment +!+ Scope.default

  private[zio] def run(
    eventHandlerZ: ZTestEventHandler
  )(implicit trace: Trace): ZIO[Any, Throwable, Unit] = {
    val fullArgs = searchTermsFromSelectors(taskDef().selectors()) match {
      case Nil   => args
      case terms => args.copy(testSearchTerms = args.testSearchTerms ++ terms)
    }
    (for {
      summary <-
        spec.runSpecWithSharedRuntimeLayer(
          taskDef0.fullyQualifiedName(),
          spec.spec,
          fullArgs,
          runtime,
          eventHandlerZ,
          console
        )
      _ <- sendSummary.provideEnvironment(ZEnvironment(summary))
    } yield ())
      .provideLayer(sharedFilledTestLayer)
  }

  /**
   * If this task def contains only `TestSelector`s and `TestWildcardSelector`s,
   * returns the search terms to add to the test arguments, so that only the
   * test cases specified in the `TestSelector`s and `TestWildcardSelector`s are
   * executed. If this task def contains any other type of selector, no search
   * terms are returned and the entire suite will be executed.
   *
   * @param td
   *   The task def whose selectors to inspect.
   * @param spec
   *   The spec to filter.
   * @return
   *   The search term corresponding to the tests that are selected.
   */
  private def searchTermsFromSelectors(selectors: Array[Selector]): List[String] =
    // If a test is defined as `suite("suite")(test("test"){})`
    // it seems reasonable that we should be able to ask for it to be executed
    // by either its short name "test" or by its full name "suite - test".
    // This *can* be achieved by stripping the suite prefix "suite -" from the names from the selectors.
    // However, this approach breaks down in the presence of nested suites;
    // e.g. if a test is defined as `suite("outer")(suite("inner)(test("test"){}))`,
    // when the outer suite is run:
    // - selector "test" still executes the test - as it should;
    // - selector "inner - test" does not execute the test (wrong prefix gets stipped) - but should;
    // - selector "outer - inner - test" does not execute the test (inner prefix does not get stripped) - but should;
    // - selector "outer - test" does execute the test - but shouldn't, since test with such a full name does not exist.
    // To make this work correctly, `Spec.filterLabels()` has to be adjusted to keep track of the accumulated
    // suite prefix and match the search terms against the full names.
    // Once that is done, we do not need to strip anything here;
    // in fact, we do not even need know what the label of the spec is.
    if (
      selectors.forall(selector => selector.isInstanceOf[TestSelector] || selector.isInstanceOf[TestWildcardSelector])
    )
      selectors.toList.collect {
        case ts: TestSelector          => ts.testName()
        case tws: TestWildcardSelector => tws.testWildcard()
      }
    else Nil

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    implicit val trace                    = Trace.empty
    val zTestHandler                      = new ZTestEventHandlerSbt(eventHandler, taskDef(), args.testRenderer)
    var resOutter: CancelableFuture[Unit] = null
    try {
      val res: CancelableFuture[Unit] =
        runtime.unsafe.runToFuture(run(zTestHandler))(trace, Unsafe.unsafe)

      resOutter = res
      Await.result(res, Duration.Inf)
      Array()
    } catch {
      case t: Throwable =>
        if (resOutter != null) resOutter.cancel()
        throw t
    }
  }

  override def tags(): Array[String] = Array.empty
}
