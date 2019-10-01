package zio.test

import scala.concurrent.{ ExecutionContext, Future }

import zio.{ Cause, Schedule, UIO, ZIO }
import zio.clock.Clock
import zio.test.mock.MockEnvironment

object TestUtils {

  final def execute[L, E, S](spec: ZSpec[MockEnvironment, E, L, S]): UIO[ExecutedSpec[L, E, S]] =
    TestExecutor.managed(mock.mockEnvironmentManaged)(spec, ExecutionStrategy.Sequential)

  final def failed[L, E, S](spec: ZSpec[mock.MockEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] =
    succeeded(spec).map(!_)

  final def failedWith(spec: ZSpec[MockEnvironment, Any, String, Any], pred: Throwable => Boolean) =
    forAllTests(execute(spec)) {
      case Left(zio.test.TestFailure.Runtime(Cause.Die(cause))) => pred(cause)
      case _                                                    => false
    }

  final def forAllTests[L, E, S](
    execSpec: UIO[ExecutedSpec[L, E, S]]
  )(f: Either[TestFailure[E], TestSuccess[S]] => Boolean): ZIO[Any, Nothing, Boolean] =
    execSpec.map { results =>
      results.forall { case Spec.TestCase(_, test) => f(test); case _ => true }
    }

  final def ignored[L, E, S](spec: ZSpec[mock.MockEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
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

  final def nonFlaky[R, E](test: ZIO[R, E, Boolean]): ZIO[R with Clock, E, Boolean] =
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

  final def succeeded[L, E, S](spec: ZSpec[mock.MockEnvironment, E, L, S]): ZIO[Any, Nothing, Boolean] = {
    val execSpec = execute(spec)
    forAllTests(execSpec)(_.isRight)
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
