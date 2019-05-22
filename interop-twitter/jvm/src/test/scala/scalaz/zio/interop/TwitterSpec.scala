package scalaz.zio.interop

import java.util.concurrent.atomic.AtomicInteger

import com.twitter.util.{Future, JavaTimer, Duration => TwitterDuration}
import org.specs2.concurrent.ExecutionEnv
import scalaz.zio.duration._
import scalaz.zio.interop.twitter._
import scalaz.zio.{Exit, Task, TestRuntime, ZIO}

class TwitterSpec(implicit ee: ExecutionEnv) extends TestRuntime {
  def is =
    "Twitter spec".title ^ s2"""
    `Task.fromTwitterFuture` must
      return failing `Task` if future failed.          $propagateFailures
      return successful `Task` if future succeeded.    $propagateResults
      ensure future is interrupted together with task. $propagateInterrupts
    """

  private def propagateFailures = {
    val error  = new Exception
    val future = Task(Future.exception[Int](error))
    val task   = Task.fromTwitterFuture(future)

    unsafeRunSync(task) must_=== Exit.fail(error)
  }

  private def propagateResults = {
    val value  = 10
    val future = Task(Future.value(value))
    val task   = Task.fromTwitterFuture(future)

    unsafeRun(task) ==== value
  }

  private def propagateInterrupts = {
    implicit val timer = new JavaTimer(true)

    val value       = new AtomicInteger(0)
    val futureDelay = TwitterDuration.fromSeconds(1)
    val future      = Task.succeed(Future.sleep(futureDelay).map(_ => value.incrementAndGet()))

    val taskTimeout = 500.millis
    val task        = Task.fromTwitterFuture(future).timeout(taskTimeout)

    unsafeRun(task <* ZIO.unit.delay(3.seconds)) must beNone
    value.get() ==== 0
  }
}
