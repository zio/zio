package zio.test

import zio.Cause.{ Die, Traced }

import scala.concurrent.Future
import zio.clock.Clock
import zio.{ Cause, Ref, ZIO }
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestUtils._
import zio.test.mock.Live

import scala.reflect.ClassTag

object TestAspectSpec extends ZIOBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(jsAppliesTestAspectOnlyOnJS, "js applies test aspect only on ScalaJS"),
    label(jsOnlyRunsTestsOnlyOnScalaJS, "jsOnly runs tests only on ScalaJS"),
    label(jvmAppliesTestAspectOnlyOnJVM, "jvm applies test aspect only on ScalaJS"),
    label(jvmOnlyRunsTestsOnlyOnTheJVM, "jvmOnly runs tests only on the JVM"),
    label(failureMakesTestsPassOnAnyFailure, "failure makes a test pass if the result was a failure"),
    label(failureMakesTestsPassOnSpecifiedException, "failure makes a test pass if it died with an specified failure"),
    label(
      failureDoesNotMakeTestsPassOnUnexpectedException,
      "failure does not make a test pass if it failed with an unexpected exception"
    ),
    label(
      failureDoesNotMakeTestsPassOnUnexpectedCause,
      "failure does not make a test pass if the specified failure does not match"
    ),
    label(
      failureMakesTestsPassOnAnyAssertionFailure,
      "failure makes tests pass on any assertion failure"
    ),
    label(
      failureMakesTestsPassOnExpectedAssertionFailure,
      "failure makes tests pass on an expected assertion failure"
    ),
    label(
      failureDoesNotMakesTestsPassOnUnexpectedAssertionFailure,
      "failure does not make tests pass on unexpected assertion failure"
    ),
    label(timeoutMakesTestsFailAfterGivenDuration, "timeout makes tests fail after given duration"),
    label(timeoutReportProblemWithInterruption, "timeout reports problem with interruption")
  )

  def jsAppliesTestAspectOnlyOnJS: Future[Boolean] =
    unsafeRunToFuture {
      for {
        ref    <- Ref.make(false)
        spec   = test("test")(assert(true, isTrue)) @@ js(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield if (TestPlatform.isJS) result else !result
    }

  def jsOnlyRunsTestsOnlyOnScalaJS: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("Javascript-only")(assert(TestPlatform.isJS, isTrue)) @@ jsOnly
      if (TestPlatform.isJS) succeeded(spec) else ignored(spec)
    }

  def jvmAppliesTestAspectOnlyOnJVM: Future[Boolean] =
    unsafeRunToFuture {
      for {
        ref    <- Ref.make(false)
        spec   = test("test")(assert(true, isTrue)) @@ jvm(after(ref.set(true)))
        _      <- execute(spec)
        result <- ref.get
      } yield if (TestPlatform.isJVM) result else !result
    }

  def jvmOnlyRunsTestsOnlyOnTheJVM: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("JVM-only")(assert(TestPlatform.isJVM, isTrue)) @@ jvmOnly
      if (TestPlatform.isJVM) succeeded(spec) else ignored(spec)
    }

  def failureMakesTestsPassOnAnyFailure: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("test")(assert(throw new java.lang.Exception("boom"), isFalse)) @@ failure
      succeeded(spec)
    }

  def failureMakesTestsPassOnSpecifiedException: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("test")(assert(throw new NullPointerException(), isFalse)) @@ failure(
        failsWithException[NullPointerException]
      )
      succeeded(spec)
    }

  def failureDoesNotMakeTestsPassOnUnexpectedException: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("test")(
        assert(throw new NullPointerException(), isFalse)
      ) @@ failure(failsWithException[IllegalArgumentException])
      failed(spec)
    }

  def failureDoesNotMakeTestsPassOnUnexpectedCause: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("test")(assert(throw new RuntimeException(), isFalse)) @@ failure(
        isCase[TestFailure[String], Cause[String]]("Runtime", {
          case TestFailure.Runtime(e) => Some(e); case _ => None
        }, equalTo(Cause.fail("boom")))
      )
      failed(spec)
    }

  def failureMakesTestsPassOnAnyAssertionFailure: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("test")(assert(true, equalTo(false))) @@ failure
      succeeded(spec)
    }

  def failureMakesTestsPassOnExpectedAssertionFailure: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("test")(assert(true, equalTo(false))) @@ failure(
        isCase[TestFailure[Any], Any]("Assertion", {
          case TestFailure.Assertion(result) => Some(result); case _ => None
        }, anything)
      )
      succeeded(spec)
    }

  def failureDoesNotMakesTestsPassOnUnexpectedAssertionFailure: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("test")(assert(true, equalTo(false))) @@ failure(
        isCase[TestFailure[Boolean], TestResult](
          "Assertion", { case TestFailure.Assertion(result) => Some(result); case _ => None },
          equalTo(assert(42, equalTo(42)))
        )
      )
      failed(spec)
    }

  def timeoutMakesTestsFailAfterGivenDuration: Future[Boolean] =
    unsafeRunToFuture {
      val spec = (testM("timeoutMakesTestsFailAfterGivenDuration") {
        assertM(ZIO.never *> ZIO.unit, equalTo(()))
      }: ZSpec[Live[Clock], Any, String, Any]) @@ timeout(1.nano)
      failedWith(spec, cause => cause == TestTimeoutException("Timeout of 1 ns exceeded."))
    }

  def timeoutReportProblemWithInterruption =
    unsafeRunToFuture {
      val spec = (testM("timeoutReportProblemWithInterruption") {
        assertM(ZIO.never.uninterruptible *> ZIO.unit, equalTo(()))
      }: ZSpec[Live[Clock], Any, String, Any]) @@ timeout(10.millis, 1.nano)
      failedWith(
        spec,
        cause =>
          cause == TestTimeoutException(
            "Timeout of 10 ms exceeded. Couldn't interrupt test within 1 ns, possible resource leak!"
          )
      )
    }

  private def failsWithException[T <: Throwable](implicit ct: ClassTag[T]): Assertion[TestFailure[Any]] =
    isCase(
      "Runtime", {
        case TestFailure.Runtime(Die(e))            => Some(e)
        case TestFailure.Runtime(Traced(Die(e), _)) => Some(e)
        case _                                      => None
      },
      isSubtype[T](anything)
    )
}
