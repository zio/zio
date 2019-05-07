package scalaz.zio.interop

import com.twitter.util.Future
import org.specs2.concurrent.ExecutionEnv
import scalaz.zio.{ FiberFailure, Task, TestRuntime }
import scalaz.zio.Exit.Cause.Fail
import scalaz.zio.interop.twitter._

class TwitterSpec(implicit ee: ExecutionEnv) extends TestRuntime {
  def is =
    "Twitter spec".title ^ s2"""
    `Task.fromTwitterFuture` must return
      a failing `Task` if the future failed.                   $propagateFailures
      a successful `Task` that produces the value from future. $propagateResults
    """

  private def propagateFailures = {
    val error  = new Exception
    val future = Future.exception[Int](error)
    val task   = Task.fromTwitterFuture(future)

    unsafeRun(task) must throwAn(FiberFailure(Fail(error)))
  }

  private def propagateResults = {
    val value  = 10
    val future = Future.value(value)
    val task   = Task.fromTwitterFuture(future)

    unsafeRun(task) ==== value
  }
}
