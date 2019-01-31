package scalaz.zio
package internal

import java.util.concurrent.RejectedExecutionException

import scala.concurrent.ExecutionContext

import org.specs2.Specification

final class TestExecutor(val role: Executor.Role, val submitResult: Boolean) extends Executor {
  val here: Boolean                       = true
  def shutdown(): Unit                    = ()
  def submit(runnable: Runnable): Boolean = submitResult
  def yieldOpCount: Int                   = 1
  def metrics: None.type                  = None
}

final class CheckPrintThrowable extends Throwable {
  var printed = false

  override def printStackTrace(): Unit = printed = true
}

object TestExecutor {
  val failing = new TestExecutor(Executor.Unyielding, false)
  val y       = new TestExecutor(Executor.Yielding, true)
  val u       = new TestExecutor(Executor.Unyielding, true)

  val badEC = new ExecutionContext {
    override def execute(r: Runnable): Unit            = throw new RejectedExecutionException("Rejected: " + r.toString)
    override def reportFailure(cause: Throwable): Unit = ()
  }

  val ec = new ExecutionContext {
    override def execute(r: Runnable): Unit            = ()
    override def reportFailure(cause: Throwable): Unit = ()
  }
}

class ExecutorSpec extends Specification {
  def is =
    "ExecutorSpec".title ^ s2"""
      Create the default unyielding executor and check that:
        When converted to an EC, it reports Throwables to stdout                   $exec1

      Create an executor that cannot have tasks submitted to and check that:
        It throws an exception upon submission                                     $fail1
        When converted to an ExecutionContext, it throws an exception              $fail2
        When created from an EC, throw when fed a task                             $fail3

      Create a yielding executor and check that:
        Runnables can be submitted                                                 $yield1
        When converted to an ExecutionContext, it accepts Runnables                $yield2
        When created from an EC, must not throw when fed a task                    $yield3

      Create an unyielding executor and check that:
        Runnables can be submitted                                                 $unyield1
        When converted to an ExecutionContext, it accepts Runnables                $unyield2
    """

  def exec1 = {
    val t = new CheckPrintThrowable
    TestExecutor.failing.asEC.reportFailure(t)
    t.printed must beTrue
  }

  def fail1 = TestExecutor.failing.submitOrThrow(() => ()) must throwA[RejectedExecutionException]
  def fail2 = TestExecutor.failing.asEC.execute(() => ()) must throwA[RejectedExecutionException]
  def fail3 =
    Executor
      .fromExecutionContext(Executor.Yielding, 1)(TestExecutor.badEC)
      .submitOrThrow(() => ()) must throwA[RejectedExecutionException]

  def yield1 = TestExecutor.y.submitOrThrow(() => ()) must not(throwA[RejectedExecutionException])
  def yield2 = TestExecutor.y.asEC.execute(() => ()) must not(throwA[RejectedExecutionException])
  def yield3 = Executor.fromExecutionContext(Executor.Yielding, 1)(TestExecutor.ec).submit(() => ()) must beTrue

  def unyield1 = TestExecutor.u.submitOrThrow(() => ()) must not(throwA[RejectedExecutionException])
  def unyield2 = TestExecutor.u.asEC.execute(() => ()) must not(throwA[RejectedExecutionException])
}
