package zio.internal

import scala.concurrent.ExecutionContext
import scala.scalajs.js

private[internal] abstract class DefaultExecutors {
  final def makeDefault(yieldOpCount: Int): Executor =
    Executor.fromExecutionContext(yieldOpCount) {
      new ExecutionContext {
        def execute(runnable: Runnable): Unit = {
          val _ = js.Dynamic.global.setImmediate(() => runnable.run())
        }
        def reportFailure(cause: Throwable): Unit =
          cause.printStackTrace()
      }
    }
}
