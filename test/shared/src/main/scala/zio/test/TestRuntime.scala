package zio.test

import zio._

/**
 * Test-specific runtime configuration that allows overriding the default
 * executor.
 */
object TestRuntime {

  /**
   * The currently configured default executor for tests.
   */
  @volatile private var _testDefaultExecutor: Executor = Runtime.defaultExecutor

  /**
   * The default executor for tests. This delegates to the configured test
   * executor. Test files should use `TestRuntime.defaultExecutor` instead of
   * `Runtime.defaultExecutor`.
   */
  val defaultExecutor: Executor = new Executor {
    override def metrics(implicit unsafe: Unsafe): Option[zio.internal.ExecutionMetrics] =
      _testDefaultExecutor.metrics

    override def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
      _testDefaultExecutor.submit(runnable)

    override def submitAndYield(runnable: Runnable)(implicit unsafe: Unsafe): Boolean =
      _testDefaultExecutor.submitAndYield(runnable)

    override private[zio] def stealWork(depth: Int): Boolean =
      _testDefaultExecutor.stealWork(depth)

    override private[zio] def isCurrentThreadInExecutor: Boolean =
      _testDefaultExecutor.isCurrentThreadInExecutor
  }

  /**
   * Sets the default executor to use for tests. This should be called during
   * test bootstrap before any tests run.
   */
  def setDefaultExecutor(executor: Executor): Unit =
    _testDefaultExecutor = executor

  /**
   * Resets the default executor back to the standard ZIO default.
   */
  def resetDefaultExecutor(): Unit =
    _testDefaultExecutor = Runtime.defaultExecutor
}
