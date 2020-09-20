package zio.internal

import java.util.ArrayList

import scala.concurrent.ExecutionContext
import scala.scalajs.js

/**
 * A specialized `ExecutionContext` optimized for Scala.js.
 */
private[zio] sealed abstract class JSExecutionContext extends ExecutionContext {

  def executeAsync(runnable: Runnable): Unit
}

object JSExecutionContext {

  val default: JSExecutionContext =
    new JSExecutionContext {
      var running = false
      val queue: ArrayList[Runnable] =
        new ArrayList[Runnable]
      def execute(runnable: Runnable): Unit =
        if (!running) {
          running = true
          queue.add(runnable)
          val _ = setImmediate(() => runQueue())
        } else {
          val _ = queue.add(runnable)
        }
      def executeAsync(runnable: Runnable): Unit = {
        val _ = setImmediate(() => runnable.run())
      }
      def reportFailure(cause: Throwable): Unit =
        cause.printStackTrace()
      def runQueue(): Unit = {
        while (!queue.isEmpty()) {
          val runnable = queue.remove(0)
          runnable.run()
        }
        running = false
      }
    }

  private[zio] val setImmediate =
    if (js.typeOf(js.Dynamic.global.setImmediate) == "function") {
      js.Dynamic.global.setImmediate
    } else {
      js.Dynamic.global.setTimeout
    }

  // Change every yield to setImmediate
}
