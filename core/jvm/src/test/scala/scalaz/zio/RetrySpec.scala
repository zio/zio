package scalaz.zio

import org.specs2.ScalaCheck
import scalaz.zio.duration._

class RetrySpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "RetrySpec".title ^ s2"""
   Retry on failure according to a provided strategy
      retry 0 time for `once` when first time succeeds $notRetryOnSuccess
      retry 0 time for `recurs(0)` $retryRecurs0
      retry exactly one time for `once` when second time succeeds $retryOnceSuccess
      retry exactly one time for `once` even if still in error $retryOnceFail
      for a given number of times $retryN
      for a given number of times with random jitter in (0, 1) $retryNUnitIntervalJittered
      for a given number of times with random jitter in custom interval $retryNCustomIntervalJittered
      fixed delay with error predicate $fixedWithErrorPredicate
  Retry according to a provided strategy
    for up to 10 times $recurs10Retry
  """

  def retryCollect[R, E, A, E1 >: E, S](
    io: IO[E, A],
    retry: Schedule[R, E1, S]
  ): ZIO[R, Nothing, (Either[E1, A], List[(Duration, S)])] = {

    type State = retry.State

    def loop(state: State, ss: List[(Duration, S)]): ZIO[R, Nothing, (Either[E1, A], List[(Duration, S)])] =
      io.redeem(
        err =>
          retry
            .update(err, state)
            .flatMap(
              step =>
                if (!step.cont) IO.succeed((Left(err), (step.delay, step.finish()) :: ss))
                else loop(step.state, (step.delay, step.finish()) :: ss)
            ),
        suc => IO.succeed((Right(suc), ss))
      )

    retry.initial.flatMap(s => loop(s, Nil)).map(x => (x._1, x._2.reverse))
  }

  /*
   * Retry `once` means that we try to exec `io`, get and error,
   * try again to exec `io`, and whatever the output is, we return that
   * second result.
   * The three following tests test retry when:
   * - the first time succeeds (no retry)
   * - the first time fails and the second succeeds (one retry, result success)
   * - both first time and retry fail (one retry, result failure)
   */

  // no retry on success
  def notRetryOnSuccess = {
    val retried = unsafeRun(for {
      ref <- Ref.make(0)
      _   <- ref.update(_ + 1).retry(Schedule.once)
      i   <- ref.get
    } yield i)

    retried must_=== 1
  }

  // one retry on failure
  def retryOnceSuccess = {
    /*
     * A function that increments ref each time it is called.
     * It returns either a failure if ref value is 0 or less
     * before increment, and the value in other cases.
     */
    def failOn0(ref: Ref[Int]): IO[String, Int] =
      for {
        i <- ref.update(_ + 1)
        x <- if (i <= 1) IO.fail(s"Error: $i") else IO.succeed(i)
      } yield x
    val retried = unsafeRun(for {
      ref <- Ref.make(0)
      _   <- failOn0(ref).retry(Schedule.once)
      r   <- ref.get
    } yield r)

    retried must_=== 2
  }

  // no more than one retry on retry `once`
  def retryOnceFail = {
    /*
     * A function that increments ref each time it is called.
     * It always fails, with the incremented value in error
     */
    def alwaysFail(ref: Ref[Int]): IO[String, Int] =
      for {
        i <- ref.update(_ + 1)
        x <- IO.fail(s"Error: $i")
      } yield x
    val retried = unsafeRun(
      (for {
        ref <- Ref.make(0)
        _   <- alwaysFail(ref).retry(Schedule.once)
      } yield ()).redeem(
        err => IO.succeed(err),
        _ => IO.succeed("A failure was expected")
      )
    )

    retried must_=== "Error: 2"
  }

  // 0 retry means "one execution in all, no retry, whatever the output"
  def retryRecurs0 = {
    /*
     * A function that increments ref each time it is called.
     */
    def incr(ref: Ref[Int]): IO[String, Int] =
      for {
        i <- ref.update(_ + 1)
        x <- IO.fail(s"Error: $i")
      } yield x
    val retried = unsafeRun(
      (for {
        ref <- Ref.make(0)
        i   <- incr(ref).retry(Schedule.recurs(0))
      } yield i).redeem(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success")
      )
    )

    retried must_=== "Error: 1"
  }

  def retryN = {
    val retried  = unsafeRun(retryCollect(IO.fail("Error"), Schedule.recurs(5)))
    val expected = (Left("Error"), List(1, 2, 3, 4, 5, 6).map((Duration.Zero, _)))
    retried must_=== expected
  }

  def retryNUnitIntervalJittered = {
    val jitter: UIO[Double]               = IO.sync(0.5)
    val schedule: Schedule[Any, Int, Int] = Schedule.recurs(5).delayed(_ => 500.millis).jittered(jitter)
    val scheduled: List[(Duration, Int)] = unsafeRun(
      schedule.run(List(1, 2, 3, 4, 5))
    )

    val expected = List(1, 2, 3, 4, 5).map((250.millis, _))
    scheduled must_=== expected
  }

  def retryNCustomIntervalJittered = {
    val jitter: UIO[Double]               = IO.sync(0.5)
    val schedule: Schedule[Any, Int, Int] = Schedule.recurs(5).delayed(_ => 500.millis).jittered(2, 4, jitter)
    val scheduled: List[(Duration, Int)] = unsafeRun(
      schedule.run(List(1, 2, 3, 4, 5))
    )

    val expected = List(1, 2, 3, 4, 5).map((1500.millis, _))
    scheduled must_=== expected
  }

  def fixedWithErrorPredicate = {
    var i = 0
    val io = IO.sync[Unit](i += 1).flatMap[Any, String, Unit] { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
    }
    val strategy = Schedule.spaced(200.millis).whileInput[String](_ == "KeepTryingError")
    val retried  = unsafeRun(retryCollect(io, strategy))
    val expected = (Left("GiveUpError"), List(1, 2, 3, 4, 5).map((200.millis, _)))
    retried must_=== expected
  }

  def recurs10Retry = {
    var i                                 = 0
    val strategy: Schedule[Any, Any, Int] = Schedule.recurs(10)
    val io = IO.sync[Unit](i += 1).flatMap { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.succeedLazy(i)
    }
    val result   = unsafeRun(io.retry(strategy))
    val expected = 5
    result must_=== expected
  }
}
