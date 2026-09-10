package migratefrommonix.monix

import monix.eval.Task
import monix.execution.schedulers.TestScheduler
import scala.concurrent.duration._
import scala.util.Success

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Testing — TestScheduler → TestClock
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step10Testing"
 */
object Step10Testing extends App {
  implicit val testScheduler: TestScheduler = TestScheduler()

  val f = Task.sleep(1.second).as("done").runToFuture
  println(s"Before tick: f.value = ${f.value}") // None

  testScheduler.tick(1.second)
  println(s"After tick: f.value = ${f.value}") // Some(Success("done"))

  assert(f.value == Some(Success("done")))
  println("Assertion passed")
}
