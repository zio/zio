package zio.test

import scala.concurrent.Future

import zio.{ DefaultRuntime, Ref, UIO, ZIO }
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.TestUtils.label

object TestAspectSpec extends DefaultRuntime {

  val run: List[Async[(Boolean, String)]] = List(
    label(jsAppliesTestAspectOnlyOnJS, "js applies test aspect only on ScalaJS"),
    label(jsOnlyRunsTestsOnlyOnScalaJS, "jsOnly runs tests only on ScalaJS"),
    label(jvmAppliesTestAspectOnlyOnJVM, "jvm applies test aspect only on ScalaJS"),
    label(jvmOnlyRunsTestsOnlyOnTheJVM, "jvmOnly runs tests only on the JVM")
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

  def execute[L, E, S](spec: ZSpec[mock.MockEnvironment, E, L, S]): UIO[ExecutedSpec[L, E, S]] =
    TestExecutor.managed(mock.mockEnvironmentManaged)(spec, ExecutionStrategy.Sequential)

  def forAllTests[L, E, S](
    execSpec: UIO[ExecutedSpec[L, E, S]]
  )(f: Either[TestFailure[E], TestSuccess[S]] => Boolean): ZIO[Any, Nothing, Boolean] =
    execSpec.map { results =>
      results.forall { case Spec.TestCase(_, test) => f(test); case _ => true }
    }

  def succeeded[L, E, S](spec: ZSpec[mock.MockEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec)(_.isRight)
  }

  def ignored[L, E, S](spec: ZSpec[mock.MockEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    }
  }
}
