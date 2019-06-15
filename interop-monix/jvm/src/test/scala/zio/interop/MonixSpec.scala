package zio.interop

import _root_.monix.eval
import _root_.monix.execution.Scheduler
import org.specs2.concurrent.ExecutionEnv
import zio.Exit.Cause.fail
import zio.interop.monix._
import zio.{ Exit, IO, TestRuntime }

class MonixSpec(implicit ee: ExecutionEnv) extends TestRuntime {
  def is = s2"""
  Monix interoperability spec

  `IO.fromTask` must
    return an `IO` that fails if `Task` failed. $propagateTaskFailure
    return an `IO` that produces the value from `Task`. $propagateTaskResult

  `IO.toTask` must
    produce a successful `IO` of `Task`. $toTaskAlwaysSucceeds
    returns a `Task` that fails if `IO` fails. $propagateFailureToTask
    returns a `Task` that produces the value from `IO`. $propagateResultToTask

  `IO.fromCoeval` must
    return an `IO` that fails if `Coeval` failed. $propagateCoevalFailure
    return an `IO` that produces the value from `Coeval`. $propagateCoevalResult

  `IO.toCoeval` must
    produce a successful `IO` of `Coeval`. $toTaskAlwaysSucceeds
    returns a `Coeval` that fails if `IO` fails. $propagateFailureToCoeval
    returns a `Coeval` that produces the value from `IO`. $propagateResultToCoeval
  """

  implicit val scheduler = Scheduler(ee.executionContext)

  def propagateTaskFailure = {
    val error = new Exception
    val task  = eval.Task.raiseError[Int](error)
    val io    = IO.fromTask(task)

    unsafeRunSync(io) must_=== Exit.Failure(fail(error))
  }

  def propagateTaskResult = {
    val value = 10
    val task  = eval.Task(value)
    val io    = IO.fromTask(task)

    unsafeRun(io) === value
  }

  def toTaskAlwaysSucceeds = {
    val task = IO.fail(new Exception).toTask
    unsafeRun(task) must beAnInstanceOf[eval.Task[Unit]]
  }

  def propagateFailureToTask = {
    val ex   = new Exception
    val task = IO.fail(ex).toTask

    unsafeRun(task).runSyncStep must throwAn(ex)
  }

  def propagateResultToTask = {
    val value = 10
    val task  = IO.succeed(value).toTask

    unsafeRun(task).runSyncStep must beRight(10)
  }

  def propagateCoevalFailure = {
    val error  = new Exception
    val coeval = eval.Coeval.raiseError[Int](error)
    val io     = IO.fromCoeval(coeval)

    unsafeRunSync(io) must_=== Exit.Failure(fail(error))
  }

  def propagateCoevalResult = {
    val value  = 10
    val coeval = eval.Coeval(value)
    val io     = IO.fromCoeval(coeval)

    unsafeRun(io) === value
  }

  def toCoevalAlwaysSucceeds = {
    val coeval = IO.fail(new Exception).toCoeval
    unsafeRun(coeval) must beAnInstanceOf[eval.Coeval[Unit]]
  }

  def propagateFailureToCoeval = {
    val ex     = new Exception
    val coeval = IO.fail(ex).toCoeval

    unsafeRun(coeval).runTry must beFailedTry(ex)
  }

  def propagateResultToCoeval = {
    val value  = 10
    val coeval = IO.succeed(value).toCoeval

    unsafeRun(coeval).runTry must beSuccessfulTry(value)
  }
}
