package scalaz.zio.interop

import monix.eval.{ Task => MTask }
import monix.execution.Scheduler
import org.specs2.concurrent.ExecutionEnv
import scalaz.zio.ExitResult.Cause.Checked
import scalaz.zio.{ AbstractRTSSpec, FiberFailure, IO }
import scalaz.zio.interop.monixio._

class MonixSpec(implicit ee: ExecutionEnv) extends AbstractRTSSpec {
  def is = s2"""
  Monix interoperability spec

  `IO.fromTask` must
    return an `IO` that fails if `Task` failed. $propagateTaskFailure
    return an `IO` that produces the value from `Task`. $propagateTaskResult

  `IO.toTask` must
    produce a successful `IO` of `Task`. $toTaskAlwaysSucceeds
    returns a `Task` that fails if `IO` fails. $propagateIOFailure
    returns a `Task` that procudes the value from `IO`. $propagateIOResult
  """

  implicit val scheduler = Scheduler(ee.executionContext)

  def propagateTaskFailure = {
    val error = new Exception
    val task  = MTask.raiseError[Int](error)
    val io    = IO.fromTask(task)

    unsafeRun(io) must throwAn(FiberFailure(Checked(error)))
  }

  def propagateTaskResult = {
    val value = 10
    val task  = MTask(value)
    val io    = IO.fromTask(task)

    unsafeRun(io) === value
  }

  def toTaskAlwaysSucceeds = {
    val task = IO.fail(new Exception).toTask
    unsafeRun(task) must beAnInstanceOf[MTask[Unit]]
  }

  def propagateIOFailure = {
    val ex   = new Exception
    val task = IO.fail(ex).toTask

    unsafeRun(task).runSyncStep must throwAn(ex)
  }

  def propagateIOResult = {
    val value = 10
    val task  = IO.now(value).toTask

    unsafeRun(task).runSyncStep must beRight(10)
  }
}
