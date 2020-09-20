package zio.internal

import java.util.concurrent.RejectedExecutionException

private[internal] abstract class DefaultExecutors {
  final def makeDefault(yieldOpCount0: Int): Executor = {
    val ec = JSExecutionContext.default
    new Executor {
      def yieldOpCount = yieldOpCount0

      def submit(runnable: Runnable): Boolean =
        try {
          ec.execute(runnable)

          true
        } catch {
          case _: RejectedExecutionException => false
        }

      override def submitAndYield(runnable: Runnable): Boolean =
        try {
          ec.executeAsync(runnable)

          true
        } catch {
          case _: RejectedExecutionException => false
        }

      def metrics = None
    }
  }
}
