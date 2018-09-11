package scalaz.zio

import scala.concurrent.duration._
import org.specs2.ScalaCheck

class RetrySpec extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "RetrySpec".title ^ s2"""
   Retry on failure according to a provided strategy
      for a given number of times $retryN
      for a given number of times with random jitter $retryNJittered
      fixed delay with error predicate $fixedWithErrorPredicate
      fixed delay with error predicate and random jitter $fixedWithErrorPredicateJittered
  Retry according to a provided strategy
    for up to 10 times $recurs10Retry
    """

  def retryCollect[E, A, E1 >: E, S](io: IO[E, A],
                                     retry: Schedule[E1, S]): IO[Nothing, (Either[E1, A], List[(Duration, S)])] = {
    type State = retry.State

    def loop(state: State, ss: List[(Duration, S)]): IO[Nothing, (Either[E1, A], List[(Duration, S)])] =
      io.redeem(
        err =>
          retry
            .update(err, state)
            .flatMap(
              step =>
                if (!step.cont) IO.now((Left(err), (step.delay, step.finish()) :: ss))
                else loop(step.state, (step.delay, step.finish()) :: ss)
          ),
        suc => IO.now((Right(suc), ss))
      )

    retry.initial.flatMap(s => loop(s, Nil)).map(x => (x._1, x._2.reverse))
  }

  def retryN = {
    val retried  = unsafeRun(retryCollect(IO.fail("Error"), Schedule.recurs(5)))
    val expected = (Left("Error"), List(1, 2, 3, 4, 5).map((Duration.Zero, _)))
    retried must_=== expected
  }

  def retryNJittered = {
    val retried  = unsafeRun(retryCollect(IO.fail("Error"), Schedule.recurs(5).jittered))
    val expected = (Left("Error"), List(1, 2, 3, 4, 5).map((Duration.Zero, _)))
    retried must_=== expected
  }

  def fixedWithErrorPredicate = {
    var i = 0
    val io = IO.sync[Unit](i += 1).flatMap { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
    }
    val strategy = Schedule.spaced(200.millis).whileInput[String](_ == "KeepTryingError")
    val retried  = unsafeRun(retryCollect(io, strategy))
    val expected = (Left("GiveUpError"), List(1, 2, 3, 4, 5).map((200.millis, _)))
    retried must_=== expected
  }

  def fixedWithErrorPredicateJittered = {
    var i                  = 0
    val duration: Duration = 200.millis
    val io = IO.sync[Unit](i += 1).flatMap { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
    }
    val strategy         = Schedule.spaced(duration).jittered.whileInput[String](_ == "KeepTryingError")
    val (error, results) = unsafeRun(retryCollect(io, strategy))
    val retried          = (error, results.collect { case (dur, count) if dur <= duration => (duration, count) })
    val expected         = (Left("GiveUpError"), List(1, 2, 3, 4, 5).map((duration, _)))
    retried must_=== expected
  }

  def recurs10Retry = {
    var i                            = 0
    val strategy: Schedule[Any, Int] = Schedule.recurs(10)
    val io = IO.sync[Unit](i += 1).flatMap { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.point(i)
    }
    val result   = unsafeRun(io.retry(strategy))
    val expected = 5
    result must_=== expected
  }
}
