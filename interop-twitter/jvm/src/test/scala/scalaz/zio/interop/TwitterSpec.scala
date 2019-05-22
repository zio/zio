package scalaz.zio.interop

import java.util.concurrent.atomic.AtomicInteger

import com.twitter.util.{ Await, Future, JavaTimer, TimeoutException, Timer, Duration => TwitterDuration }
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.AfterAll
import scalaz.zio.Exit.Cause.Fail
import scalaz.zio.duration._
import scalaz.zio.interop.twitter._
import scalaz.zio.{ FiberFailure, Task, TestRuntime, ZIO }

class TwitterSpec(implicit ee: ExecutionEnv) extends TestRuntime with AfterAll {
  def is =
    "Twitter spec".title ^ s2"""
    `Task.fromTwitterFuture` must
      return failing `Task` if future failed.          $propagateFailures
      return successful `Task` if future succeeded.    $propagateResults
      ensure future is interrupted together with task. $propagateInterrupts

    `Runtime.unsafeRunToTwitterFuture` must
      return successful `Future` if Task evaluation succeeded.    $evaluateToSuccessfulFuture
      return failed `Future` if Task evaluation failed.           $evaluateToFailedFuture
      ensure Task evaluation is interrupted together with Future. $evaluateToInterruptedFuture
    """

  private implicit val twitterTimer: Timer = new JavaTimer(true)

  override def afterAll: Unit = twitterTimer.stop()

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
    val value       = new AtomicInteger(0)
    val futureDelay = TwitterDuration.fromSeconds(1)
    val future      = Task.succeed(Future.sleep(futureDelay).map(_ => value.incrementAndGet()))

    val taskTimeout = 500.millis
    val task        = Task.fromTwitterFuture(future).timeout(taskTimeout)

    unsafeRun(task <* ZIO.unit.delay(3.seconds)) must beNone
    value.get() ==== 0
  }

  private def evaluateToSuccessfulFuture = {
    Await.result(this.unsafeRunToTwitterFuture(Task.succeed(1))) ==== 1
    Await.result(this.unsafeRunToTwitterFuture(Task.succeed(2).delay(100.millis))) ==== 2
  }

  private def evaluateToFailedFuture = {
    val e    = new Exception
    val task = ZIO.unit.delay(100.millis) *> Task.fail(e).unit

    Await.result(this.unsafeRunToTwitterFuture(task)) must throwAn(e)
  }

  private def evaluateToInterruptedFuture = {
    val value         = new AtomicInteger(0)
    val futureTimeout = TwitterDuration.fromMilliseconds(100)

    val task = ZIO.unit.delay(500.millis) *> Task.effect(value.incrementAndGet())

    Await.result(this.unsafeRunToTwitterFuture(task).raiseWithin(futureTimeout)) must throwAn[TimeoutException]
    unsafeRun(ZIO.unit.delay(600.millis) *> Task.effect(value.get)) ==== 0
  }
}
