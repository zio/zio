package zio.internal

import scala.concurrent.ExecutionContext
import scala.scalajs.js

private[internal] abstract class DefaultExecutors {
  final def makeDefault(yieldOpCount: Int): Executor =
    Executor.fromExecutionContext(yieldOpCount) {
      new ExecutionContext {
        def execute(runnable: Runnable): Unit = {
          val _ = setImmediate(() => runnable.run())
        }
        def reportFailure(cause: Throwable): Unit =
          cause.printStackTrace()
      }
    }

  /**
   * Yields before executing this effect.
   */
  private val setImmediate =
    if (js.typeOf(js.Dynamic.global.setImmediate) == "function") {
      js.Dynamic.global.setImmediate
    } else {
      js.Dynamic.global.setTimeout
    }
}
