package zio.test

import scala.concurrent.{ ExecutionContext, Future }

import zio.{ Schedule, UIO, ZIO }
import zio.test.environment.TestEnvironment

object TestUtils {

  final def execute[L, E, S](spec: ZSpec[TestEnvironment, E, L, S]): UIO[ExecutedSpec[L, E, S]] =
    TestExecutor.managed(environment.testEnvironmentManaged)(spec, ExecutionStrategy.Sequential)

  final def failed[L, E, S](spec: ZSpec[environment.TestEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] =
    succeeded(spec).map(!_)

  final def failedWith(spec: ZSpec[TestEnvironment, Any, String, Any], pred: Throwable => Boolean) =
    forAllTests(execute(spec)) {
      case Left(TestFailure.Runtime(cause)) => cause.dieOption.fold(false)(pred)
      case _                                => false
    }

  final def forAllTests[L, E, S](
    execSpec: UIO[ExecutedSpec[L, E, S]]
  )(f: Either[TestFailure[E], TestSuccess[S]] => Boolean): ZIO[Any, Nothing, Boolean] =
    execSpec.flatMap { results =>
      results.forall { case Spec.TestCase(_, test) => test.map(f); case _ => ZIO.succeed(true) }
    }

  final def ignored[L, E, S](spec: ZSpec[environment.TestEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    }
  }

  final def label(test: => Future[Boolean], label: String): Async[(Boolean, String)] =
    Async
      .fromFuture(test)
      .map(passed => if (passed) (passed, succeed(label)) else (passed, fail(label)))
      .handle { case _ => (false, fail(label)) }

  final def nonFlaky[R, E](test: ZIO[R, E, Boolean]): ZIO[R, E, Boolean] =
    if (TestPlatform.isJS) test
    else test.repeat(Schedule.recurs(100) *> Schedule.identity[Boolean])

  final def report(suites: Iterable[Async[List[(Boolean, String)]]])(implicit ec: ExecutionContext): Unit = {
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

  final def scope(tests: List[Async[(Boolean, String)]], label: String): Async[List[(Boolean, String)]] =
    Async.sequence(tests).map { tests =>
      val offset = tests.map { case (passed, label) => (passed, "  " + label) }
      val passed = tests.forall(_._1)
      if (passed) (passed, succeed(label)) :: offset else (passed, fail(label)) :: offset
    }

  final def succeeded[L, E, S](spec: ZSpec[environment.TestEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec) {
      case Right(TestSuccess.Succeeded(_)) => true
      case _                               => false
    }
  }

  final def timeit[A](label: String)(async: Async[A]): Async[A] =
    for {
      start  <- Async(System.currentTimeMillis)
      result <- async
      stop   <- Async(System.currentTimeMillis)
      _      <- Async(println(s"$label took ${(stop - start) / 1000.0} seconds"))
    } yield result

  private def succeed(s: String): String =
    green("+") + " " + s

  private def fail(s: String): String =
    red("-" + " " + s)

  private def green(s: String): String =
    Console.GREEN + s + Console.RESET

  private def red(s: String): String =
    Console.RED + s + Console.RESET
}
