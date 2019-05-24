package scalaz.zio.interop

import java.util.concurrent.atomic.AtomicInteger

import com.twitter.util.{ Await, Future, Promise }
import org.specs2.concurrent.ExecutionEnv
import scalaz.zio.Exit.Cause.Fail
import scalaz.zio.interop.twitter._
import scalaz.zio.{ FiberFailure, Task, TestRuntime, ZIO }

class TwitterSpec(implicit ee: ExecutionEnv) extends TestRuntime {
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
    val value = new AtomicInteger(0)

    val promise = Promise[Unit]()
    promise.setInterruptHandler {
      case e => promise.setException(e)
    }
    val future  = Task(promise.flatMap(_ => Future(value.incrementAndGet())))

    unsafeRun {
      for {
        fiber <- Task.fromTwitterFuture(future).fork
        _     <- fiber.interrupt
        _     <- Task.effect(promise.setDone())
        a     <- fiber.await
      } yield (a.toEither must beLeft) and (value.get ==== 0)
    }
  }

  private def evaluateToSuccessfulFuture =
    Await.result(this.unsafeRunToTwitterFuture(Task.succeed(2))) ==== 2

  private def evaluateToFailedFuture = {
    val e    = new Exception
    val task = Task.fail(e).unit

    Await.result(this.unsafeRunToTwitterFuture(task)) must throwAn(e)
  }

  private def evaluateToInterruptedFuture = {
    val value = new AtomicInteger(0)

    val task: ZIO[Any, Throwable, Future[Int]] = for {
      promise <- scalaz.zio.Promise.make[Throwable, Int]
      t       = promise.await.flatMap(_ => Task.effectTotal(value.incrementAndGet()))
      future  = this.unsafeRunToTwitterFuture(t)
      _       = future.raise(new Exception)
      _       <- promise.succeed(1)
    } yield future

    (Await.result(unsafeRun(task)) must throwAn[InterruptedException]) and (value.get ==== 0)
  }
}
