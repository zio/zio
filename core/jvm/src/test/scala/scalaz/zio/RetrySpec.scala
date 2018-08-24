package scalaz.zio

import scala.concurrent.duration._
import org.specs2.ScalaCheck
import org.specs2.specification.core.SpecStructure

class RetrySpec extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is: SpecStructure = "RetrySpec".title ^ s2"""
   Retry on failure according to a provided strategy
       for a given number of times $retryN
       fixed delay with error predicate $fixedWithErrorPredicate
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
}
