package migratefrommonix.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Running Effects Unsafely
 *
 * This is a plain App (not TaskApp) to show the unsafe API.
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step9RunningUnsafely"
 */
object Step9RunningUnsafely extends App {
  // runSyncUnsafe — synchronous extraction
  val result: String = Task.eval("hello").runSyncUnsafe()
  println(s"runSyncUnsafe: $result")

  // runToFuture — async run returning a CancelableFuture
  val future = Task.eval("async").runToFuture
  println(s"runToFuture started")
  Thread.sleep(100)
  println(s"future value: ${future.value}")
}
