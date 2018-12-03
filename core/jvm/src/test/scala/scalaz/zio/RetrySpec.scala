package scalaz.zio

import scala.concurrent.duration._
import org.specs2.ScalaCheck

class RetrySpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "RetrySpec".title ^ s2"""
   Retry on failure according to a provided strategy
      for a given number of times $retryN
      for a given number of times with random jitter in (0, 1) $retryNUnitIntervalJittered
      for a given number of times with random jitter in custom interval $retryNCustomIntervalJittered
      fixed delay with error predicate $fixedWithErrorPredicate
  Retry according to a provided strategy
    for up to 10 times $recurs10Retry
    """

  def retryCollect[E, A, E1 >: E, S](
    io: IO[E, A],
    retry: Schedule[E1, S],
    clock: Clock = Clock.Live
  ): IO[Nothing, (Either[E1, A], List[(Duration, S)])] = {
    type State = retry.State

    def loop(state: State, ss: List[(Duration, S)]): IO[Nothing, (Either[E1, A], List[(Duration, S)])] =
      io.redeem(
        err =>
          retry
            .update(err, state, clock)
            .flatMap(
              step =>
                if (!step.cont) IO.now((Left(err), (step.delay, step.finish()) :: ss))
                else loop(step.state, (step.delay, step.finish()) :: ss)
            ),
        suc => IO.now((Right(suc), ss))
      )

    retry.initial(clock).flatMap(s => loop(s, Nil)).map(x => (x._1, x._2.reverse))
  }

  def retryN = {
    val retried  = unsafeRun(retryCollect(IO.fail("Error"), Schedule.recurs(5)))
    val expected = (Left("Error"), List(1, 2, 3, 4, 5).map((Duration.Zero, _)))
    retried must_=== expected
  }

  def retryNUnitIntervalJittered = {
    val jitter: IO[Nothing, Double]  = IO.sync(0.5)
    val schedule: Schedule[Int, Int] = Schedule.recurs(5).delayed(_ => 500.millis).jittered(jitter)
    val scheduled: List[(Duration, Int)] = unsafeRun(
      schedule.run(List(1, 2, 3, 4, 5), Clock.Live)
    )

    val expected = List(1, 2, 3, 4, 5).map((250.millis, _))
    scheduled must_=== expected
  }

  def retryNCustomIntervalJittered = {
    val jitter: IO[Nothing, Double]  = IO.sync(0.5)
    val schedule: Schedule[Int, Int] = Schedule.recurs(5).delayed(_ => 500.millis).jittered(2, 4, jitter)
    val scheduled: List[(Duration, Int)] = unsafeRun(
      schedule.run(List(1, 2, 3, 4, 5), Clock.Live)
    )

    val expected = List(1, 2, 3, 4, 5).map((1500.millis, _))
    scheduled must_=== expected
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
