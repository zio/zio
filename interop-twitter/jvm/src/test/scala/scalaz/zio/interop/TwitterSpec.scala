package scalaz.zio.interop

import java.util.concurrent.atomic.AtomicInteger

import com.twitter.util.{ Duration => TwitterDuration, Future, JavaTimer }
import org.specs2.concurrent.ExecutionEnv
import scalaz.zio.{ FiberFailure, Task, TestRuntime }
import scalaz.zio.Exit.Cause.Fail
import scalaz.zio.duration.Duration
import scalaz.zio.interop.twitter._

import scala.concurrent.duration._

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

    unsafeRun(task) must throwAn(FiberFailure(Fail(error)))
  }

  private def propagateResults = {
    val value  = 10
    val future = Task(Future.value(value))
    val task   = Task.fromTwitterFuture(future)

    unsafeRun(task) ==== value
  }

  private def propagateInterrupts = {
    implicit val timer = new JavaTimer(true)

    val value         = new AtomicInteger(0)
    val futureTimeout = TwitterDuration.fromSeconds(3)
    val taskTimeout   = Duration.fromScala(1.second)
    val future        = Task(Future.sleep(futureTimeout).map(_ => value.incrementAndGet()))
    val task          = Task.fromTwitterFuture(future).timeout(taskTimeout)

    unsafeRun(task) must beNone

    SECONDS.sleep(5)

    value.get() ==== 0
  }
}
