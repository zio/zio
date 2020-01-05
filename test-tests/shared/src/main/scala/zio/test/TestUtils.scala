package zio.test

import scala.concurrent.{ ExecutionContext, Future }

import zio.test.environment.TestEnvironment
import zio.{ UIO, ZIO }

object TestUtils {

  def execute[E, L, S](spec: ZSpec[TestEnvironment, E, L, S]): UIO[ExecutedSpec[E, L, S]] =
    TestExecutor.managed(environment.testEnvironmentManaged)(spec, ExecutionStrategy.Sequential)

  def forAllTests[E, L, S](
    execSpec: UIO[ExecutedSpec[E, L, S]]
  )(f: Either[TestFailure[E], TestSuccess[S]] => Boolean): ZIO[Any, Nothing, Boolean] =
    execSpec.flatMap { results =>
      results.forall { case Spec.TestCase(_, test) => test.map(r => f(r._1)); case _ => ZIO.succeed(true) }
    }

  def isIgnored[E, L, S](spec: ZSpec[environment.TestEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    }
  }

  def isSuccess[E, L, S](spec: ZSpec[environment.TestEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Succeeded(_)) => true
      case _                               => false
    }
  }

  def label(test: => Future[Boolean], label: String): Async[(Boolean, String)] =
    Async
      .fromFuture(test)
      .map(passed => if (passed) (passed, succeed(label)) else (passed, fail(label)))
      .handle { case _ => (false, fail(label)) }

  def report(suites: Iterable[Async[List[(Boolean, String)]]])(implicit ec: ExecutionContext): Unit = {
    val async = Async
      .sequence(suites)
      .map(_.flatten)
      .flatMap { results =>
        val passed = results.forall(_._1)
        results.foreach(result => println(result._2))
        if (passed) Async.succeed(true)
        else Async(ExitUtils.fail()).map(_ => false)
      }
    ExitUtils.await(async.run(ec))
  }

  def scope(tests: List[Async[(Boolean, String)]], label: String): Async[List[(Boolean, String)]] =
    Async.sequence(tests).map { tests =>
      val offset = tests.map { case (passed, label) => (passed, "  " + label) }
      val passed = tests.forall(_._1)
      if (passed) (passed, succeed(label)) :: offset else (passed, fail(label)) :: offset
    }

  private def succeed(s: String): String =
    green("+") + " " + s

  private def fail(s: String): String =
    red("-" + " " + s)

  private def green(s: String): String =
    Console.GREEN + s + Console.RESET

  private def red(s: String): String =
    Console.RED + s + Console.RESET
}
