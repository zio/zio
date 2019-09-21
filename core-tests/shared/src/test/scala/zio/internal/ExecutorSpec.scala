package zio.internal
import java.util.concurrent.RejectedExecutionException

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

import scala.concurrent.ExecutionContext

final class TestExecutor(val submitResult: Boolean) extends Executor {
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
  val failing = new TestExecutor(false)
  val y       = new TestExecutor(true)
  val u       = new TestExecutor(true)

  val badEC = new ExecutionContext {
    override def execute(r: Runnable): Unit            = throw new RejectedExecutionException("Rejected: " + r.toString)
    override def reportFailure(cause: Throwable): Unit = ()
  }

  val ec = new ExecutionContext {
    override def execute(r: Runnable): Unit            = ()
    override def reportFailure(cause: Throwable): Unit = ()
  }
}

object SpecDescription {
  def is =
    """ExecutorSpec
      Create the default unyielding executor and check that:
        When converted to an EC, it reports Throwables to stdout                   $exec1

      Create an executor that cannot have tasks submitted to and check that:
        It throws an exception upon submission                                     $fail1
        When converted to an ExecutionContext, it throws an exception              $fail2
        When created from an EC, throw when fed an effect                             $fail3

      Create a yielding executor and check that:
        Runnables can be submitted                                                 $yield1
        When converted to an ExecutionContext, it accepts Runnables                $yield2
        When created from an EC, must not throw when fed an effect                    $yield3

      Create an unyielding executor and check that:
        Runnables can be submitted                                                 $unyield1
        When converted to an ExecutionContext, it accepts Runnables                $unyield2
    """
}

object ExecutorSpec
  extends ZIOBaseSpec(
    suite("ExecutorSpecNew")(
      test("exec1") {
        val t = new CheckPrintThrowable
        TestExecutor.failing.asEC.reportFailure(t)
        assert(t.printed, isTrue)
      },
      test("fail1") {
        assert(TestExecutor.failing.submitOrThrow(() => ()), throwsA[RejectedExecutionException])
      },
      test("fail2") {
        assert(TestExecutor.failing.asEC.execute(() => ()), throwsA[RejectedExecutionException])
      },
      test("fail3") {
        assert(Executor
          .fromExecutionContext(1)(TestExecutor.badEC)
          .submitOrThrow(() => ()), throwsA[RejectedExecutionException])
      },
      test("yield1") {
        assert(TestExecutor.y.submitOrThrow(() => ()), not(throwsA[RejectedExecutionException]))
      },
      test("yield2") {
        assert(TestExecutor.y.asEC.execute(() => ()), not(throwsA[RejectedExecutionException]))
      },
      test("yield3") {
        assert(Executor.fromExecutionContext(1)(TestExecutor.ec).submit(() => ()), not(throwsA[RejectedExecutionException]))
      },
      test("unyield1") {
        assert(TestExecutor.u.submitOrThrow(() => ()), not(throwsA[RejectedExecutionException]))
      },
      test("unyield2") {
        assert(TestExecutor.u.asEC.execute(() => ()), not(throwsA[RejectedExecutionException]))
      }
    )
  )