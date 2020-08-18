package zio.internal

import java.util.concurrent.RejectedExecutionException

import scala.concurrent.ExecutionContext
import scala.scalajs.js

private[internal] abstract class DefaultExecutors {
  final def makeDefault(yieldOpCount0: Int): Executor =
    new Executor {
      def yieldOpCount = yieldOpCount0

      def submit(runnable: Runnable): Boolean =
        try {
          ExecutionContext.global.execute(runnable)
          true
        } catch {
          case _: RejectedExecutionException => false
        }

      def metrics = None

      override def submitAndYield(runnable: Runnable): Boolean = {
        val _ = setImmediate(() => runnable.run())
        true
      }
    }

  private val setImmediate =
    if (js.typeOf(js.Dynamic.global.setImmediate) == "function") {
      js.Dynamic.global.setImmediate
    } else {
      js.Dynamic.global.setTimeout
    }
}
