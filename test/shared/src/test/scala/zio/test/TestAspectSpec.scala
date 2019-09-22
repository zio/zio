package zio.test

import scala.concurrent.Future
import zio.{ Cause, DefaultRuntime, Ref }
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestUtils.{ execute, failed, ignored, label, succeeded }

object TestAspectSpec extends DefaultRuntime {

  val run: List[Async[(Boolean, String)]] = List(
    label(jsAppliesTestAspectOnlyOnJS, "js applies test aspect only on ScalaJS"),
    label(jsOnlyRunsTestsOnlyOnScalaJS, "jsOnly runs tests only on ScalaJS"),
    label(jvmAppliesTestAspectOnlyOnJVM, "jvm applies test aspect only on ScalaJS"),
    label(jvmOnlyRunsTestsOnlyOnTheJVM, "jvmOnly runs tests only on the JVM"),
    label(failureMakesTestsPassOnAnyFailure, "failure makes a test pass if the result was a failure"),
    label(failureMakesTestsPassOnAGivenCause, "failure makes a test pass if it passed on a specified failure"),
    label(
      failureMakesTestsFailWhenGivenCauseDoesNotMatch,
      "failure does not make a test pass if the specified failure does not match"
    )
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
      val spec = test("failure aspect")(assert(throw new java.lang.Exception("boom"), isFalse)) @@ failure
      succeeded(spec)
    }

  def failureMakesTestsPassOnAGivenCause: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("failure aspect")(assert(throw new NullPointerException(), isFalse)) @@ failure(
        Assertion.testFails(TestFailure.Runtime(Cause.die(new NullPointerException())))
      )
      succeeded(spec)
    }

  def failureMakesTestsFailWhenGivenCauseDoesNotMatch: Future[Boolean] =
    unsafeRunToFuture {
      val spec = test("failure aspect")(assert(throw new RuntimeException(), isFalse)) @@ failure(
        Assertion.testFails(TestFailure.Runtime(Cause.fail("boom")))
      )
      failed(spec)
    }
}
