package zio

import scala.concurrent.Future

import zio.clock.Clock
import zio.duration._
import zio.random._

class ScheduleSpec extends BaseCrossPlatformSpec {
  def is = "ScheduleSpec".title ^ s2"""
   Repeat on success according to a provided strategy
      for 'recurs(a negative number)' repeats 0 additional time $repeatNeg
      for 'recurs(0)' does repeats 0 additional time $repeat0
      for 'recurs(1)' does repeats 1 additional time $repeat1
      for 'once' does repeats 1 additional time $once
      for 'recurs(a positive given number)' repeats that additional number of time $repeatN
      for 'doWhile(cond)' repeats while the cond still holds $repeatWhile
      for 'doWhileM(cond)' repeats while the effectul cond still holds $repeatWhileM
      for 'doUntil(cond)' repeats until the cond is satisfied $repeatUntil
      for 'doUntilM(cond)' repeats until the effectful cond is satisfied $repeatUntilM
   Collect all inputs into a list
      as long as the condition f holds $collectWhile
      as long as the effectful condition f holds $collectWhile
      until the effectful condition f failes $collectUntil
      until the effectful condition f failes $collectUntilM
   Repeat on failure does not actually repeat $repeatFail
   Repeat a scheduled repeat repeats the whole number $repeatRepeat

   Repeat an action 2 times and call `ensuring` should
      run the specified finalizer as soon as the schedule is complete $ensuringRepeat

   Retry on failure according to a provided strategy
      retry 0 time for `once` when first time succeeds $notRetryOnSuccess
      retry 0 time for `recurs(0)` $retryRecurs0
      retry exactly one time for `once` when second time succeeds $retryOnceSuccess
      retry exactly one time for `once` even if still in error $retryOnceFail
      for a given number of times $retryN
      for a given number of times with random jitter in (0, 1) $retryNUnitIntervalJittered
      for a given number of times with random jitter in custom interval $retryNCustomIntervalJittered
      fixed delay with error predicate $fixedWithErrorPredicate
      fibonacci delay $fibonacci
      linear delay $linear
      exponential delay with default factor $exponential
      exponential delay with other factor $exponentialWithFactor
   Retry according to a provided strategy
     for up to 10 times $recurs10Retry
   Return the result of the fallback after failing and no more retries left
     if fallback succeed - retryOrElse $retryOrElseFallbackSucceed
     if fallback failed - retryOrElse $retryOrElseFallbackFailed
     if fallback succeed - retryOrElseEither $retryOrElseEitherFallbackSucceed
     if fallback failed - retryOrElseEither $retryOrElseEitherFallbackFailed
   Return the result after successful retry
     retry exactly one time for `once` when second time succeeds - retryOrElse $retryOrElseSucceed
     retry exactly one time for `once` when second time succeeds - retryOrElse0 $retryOrElseEitherSucceed
   Retry a failed action 2 times and call `ensuring` should
     run the specified finalizer as soon as the schedule is complete $ensuringRetry
   Retry type parameters should infer correctly $retryTypeInference
   """

  val repeat: Int => ZIO[Clock, Nothing, Int] = (n: Int) =>
    for {
      ref <- Ref.make(0)
      s   <- ref.update(_ + 1).repeat(Schedule.recurs(n))
    } yield s

  /*
   * A repeat with a negative number of times should not repeat the action at all
   */
  def repeatNeg =
    repeat(-5).map(x => x must_=== 1)

  /*
   * A repeat with 0 number of times should not repeat the action at all
   */
  def repeat0 =
    repeat(0).map(x => x must_=== 1)

  def never =
    for {
      ref <- Ref.make(0)
      _   <- ref.update(_ + 1).repeat(Schedule.never)
      res <- ref.get
    } yield res must_=== 1

  def repeat1 =
    repeat(1).map(x => x must_=== 2)

  def once =
    for {
      ref <- Ref.make(0)
      _   <- ref.update(_ + 1).repeat(Schedule.once)
      res <- ref.get
    } yield res must_=== 2

  def repeatN =
    repeat(42).map(x => x must_=== 42 + 1)

  def repeatRepeat = {
    val n = 42
    for {
      ref <- Ref.make(0)
      io  = ref.update(_ + 1).repeat(Schedule.recurs(n))
      _   <- io.repeat(Schedule.recurs(1))
      res <- ref.get
    } yield res must_=== (n + 1) * 2
  }

  def repeatFail = {
    // a method that increment ref and fail with the incremented value in error message
    def incr(ref: Ref[Int]): IO[String, Int] =
      for {
        i <- ref.update(_ + 1)
        _ <- IO.fail(s"Error: $i")
      } yield i

    val repeated =
      (for {
        ref <- Ref.make(0)
        _   <- incr(ref).repeat(Schedule.recurs(42))
      } yield ()).foldM(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success at all")
      )

    repeated.map(x => x must_=== "Error: 1")
  }

  def repeatUntil = {
    def cond: Int => Boolean = _ < 10
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.doUntil(cond))
    } yield i must_=== 1
  }

  def repeatUntilM = {
    def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.doUntilM(cond))
    } yield i must_=== 11
  }

  def repeatWhile = {
    def cond: Int => Boolean = _ < 10
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.doWhile(cond))
    } yield i must_=== 10
  }

  def repeatWhileM = {
    def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.doWhileM(cond))
    } yield i must_=== 1
  }

  def collectWhile = {
    def cond: Int => Boolean = _ < 10
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.collectWhile(cond))
    } yield i must_=== List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  }

  def collectWhileM = {
    def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.collectWhileM(cond))
    } yield i must_=== List(1)
  }

  def collectUntil = {
    def cond: Int => Boolean = _ < 10
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.collectUntil(cond))
    } yield i must_=== List(1)
  }

  def collectUntilM = {
    def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.collectUntilM(cond))
    } yield i must_=== List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
  }

  def ensuringRepeat =
    for {
      p          <- Promise.make[Nothing, Unit]
      r          <- Ref.make(0)
      _          <- r.update(_ + 2).repeat(Schedule.recurs(2)).ensuring(p.succeed(()))
      v          <- r.get
      finalizerV <- p.poll
    } yield (v must_=== 6) and (finalizerV.isDefined must beTrue)

  def retryCollect[R, E, A, E1 >: E, S](
    io: IO[E, A],
    retry: ZSchedule[R, E1, S]
  ): ZIO[R, Nothing, (Either[E1, A], List[(Duration, S)])] = {

    type State = retry.State

    def loop(state: State, ss: List[(Duration, S)]): ZIO[R, Nothing, (Either[E1, A], List[(Duration, S)])] =
      io.foldM(
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
  def notRetryOnSuccess =
    for {
      ref <- Ref.make(0)
      _   <- ref.update(_ + 1).retry(Schedule.once)
      i   <- ref.get
    } yield i must_=== 1

  // one retry on failure
  def retryOnceSuccess =
    for {
      ref <- Ref.make(0)
      _   <- failOn0(ref).retry(Schedule.once)
      r   <- ref.get
    } yield r must_=== 2

  // no more than one retry on retry `once`
  def retryOnceFail =
    (for {
      ref <- Ref.make(0)
      _   <- alwaysFail(ref).retry(Schedule.once)
    } yield ()).foldM(
      err => IO.succeed(err),
      _ => IO.succeed("A failure was expected")
    ) must_=== "Error: 2"

  // 0 retry means "one execution in all, no retry, whatever the output"
  def retryRecurs0 =
    (for {
      ref <- Ref.make(0)
      i   <- alwaysFail(ref).retry(Schedule.recurs(0))
    } yield i)
      .foldM(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success")
      ) must_=== "Error: 1"

  def retryN = {
    val retried  = retryCollect(IO.fail("Error"), Schedule.recurs(5))
    val expected = (Left("Error"), List(1, 2, 3, 4, 5, 6).map((Duration.Zero, _)))
    retried must_=== expected
  }

  def retryNUnitIntervalJittered = {
    val schedule: ZSchedule[Random, Int, Int] = Schedule.recurs(5).delayed(_ => 500.millis).jittered
    val scheduled: UIO[List[(Duration, Int)]] = schedule.run(List(1, 2, 3, 4, 5)).provide(TestRandom)

    val expected = List(1, 2, 3, 4, 5).map((250.millis, _))
    scheduled must_=== expected
  }

  def retryNCustomIntervalJittered = {
    val schedule: ZSchedule[Random, Int, Int] = Schedule.recurs(5).delayed(_ => 500.millis).jittered(2, 4)
    val scheduled: UIO[List[(Duration, Int)]] = schedule.run(List(1, 2, 3, 4, 5)).provide(TestRandom)

    val expected = List(1, 2, 3, 4, 5).map((1500.millis, _))
    scheduled must_=== expected
  }

  def fixedWithErrorPredicate = {
    var i = 0
    val io = IO.effectTotal[Unit](i += 1).flatMap[Any, String, Unit] { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
    }
    val strategy = Schedule.spaced(200.millis).whileInput[String](_ == "KeepTryingError")
    val retried  = retryCollect(io, strategy)
    val expected = (Left("GiveUpError"), List(1, 2, 3, 4, 5).map((200.millis, _)))
    retried must_=== expected
  }

  def recurs10Retry = {
    var i                            = 0
    val strategy: Schedule[Any, Int] = Schedule.recurs(10)
    val io = IO.effectTotal[Unit](i += 1).flatMap { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.succeed(i)
    }
    io.retry(strategy) must_=== 5
  }

  def fibonacci =
    checkErrorWithPredicate(Schedule.fibonacci(100.millis), List(1, 1, 2, 3, 5))

  def linear =
    checkErrorWithPredicate(Schedule.linear(100.millis), List(1, 2, 3, 4, 5))

  def exponential =
    checkErrorWithPredicate(Schedule.exponential(100.millis), List(2, 4, 8, 16, 32))

  def exponentialWithFactor =
    checkErrorWithPredicate(Schedule.exponential(100.millis, 3.0), List(3, 9, 27, 81, 243))

  def checkErrorWithPredicate(schedule: Schedule[Any, Duration], expectedSteps: List[Int]) = {
    var i = 0
    val io = IO.effectTotal[Unit](i += 1).flatMap[Any, String, Unit] { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
    }
    val strategy = schedule.whileInput[String](_ == "KeepTryingError")
    val expected = (Left("GiveUpError"), expectedSteps.map(i => ((i * 100).millis, (i * 100).millis)))
    retryCollect(io, strategy) must_=== expected
  }

  val ioSucceed = (_: String, _: Unit) => IO.succeed("OrElse")

  val ioFail = (_: String, _: Unit) => IO.fail("OrElseFailed")

  def retryOrElseSucceed =
    for {
      ref <- Ref.make(0)
      o   <- failOn0(ref).retryOrElse(Schedule.once, ioFail)
    } yield o must_=== 2

  def retryOrElseFallbackSucceed =
    for {
      ref <- Ref.make(0)
      o   <- alwaysFail(ref).retryOrElse(Schedule.once, ioSucceed)
    } yield o must_=== "OrElse"

  def retryOrElseFallbackFailed =
    (for {
      ref <- Ref.make(0)
      i   <- alwaysFail(ref).retryOrElse(Schedule.once, ioFail)
    } yield i)
      .foldM(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success")
      ) must_=== "OrElseFailed"

  def retryOrElseEitherSucceed =
    for {
      ref <- Ref.make(0)
      o   <- failOn0(ref).retryOrElseEither(Schedule.once, ioFail)
    } yield o must beRight(2)

  def retryOrElseEitherFallbackSucceed =
    for {
      ref <- Ref.make(0)
      o   <- alwaysFail(ref).retryOrElseEither(Schedule.once, ioSucceed)
    } yield o must beLeft("OrElse")

  def retryOrElseEitherFallbackFailed =
    (for {
      ref <- Ref.make(0)
      i   <- alwaysFail(ref).retryOrElseEither(Schedule.once, ioFail)
    } yield i)
      .foldM(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success")
      ) must_=== "OrElseFailed"

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

  /*
   * A function that increments ref each time it is called.
   * It always fails, with the incremented value in error
   */
  def alwaysFail(ref: Ref[Int]): IO[String, Int] =
    for {
      i <- ref.update(_ + 1)
      x <- IO.fail(s"Error: $i")
    } yield x

  def ensuringRetry =
    for {
      p          <- Promise.make[Nothing, Unit]
      v          <- IO.fail("oh no").retry(Schedule.recurs(2)).ensuring(p.succeed(())).option
      finalizerV <- p.poll
    } yield (v must beNone) and (finalizerV.isDefined must beTrue)

  def retryTypeInference = {
    def foo[O](v: O): ZIO[Any with Clock, Error, Either[Failure, Success[O]]] =
      ZIO.fromFuture { _ =>
        Future.successful(v)
      }.foldM(
          _ => ZIO.fail(Error("Some error")),
          ok => {
            ZIO.succeed(Right(Success(ok)))
          }
        )
        .retry(Schedule.spaced(2.seconds) && Schedule.recurs(1))
        .catchAll(
          error => ZIO.succeed(Left(Failure(error.message)))
        )
    foo("Ok").map(ok => ok must_=== Right(Success("Ok")))
  }

  object TestRandom extends Random {
    object random extends Random.Service[Any] {
      val nextBoolean: UIO[Boolean] = UIO.succeed(false)
      def nextBytes(length: Int): UIO[Chunk[Byte]] =
        UIO.succeed(Chunk.empty)
      val nextDouble: UIO[Double] =
        UIO.succeed(0.5)
      val nextFloat: UIO[Float] =
        UIO.succeed(0.5f)
      val nextGaussian: UIO[Double] =
        UIO.succeed(0.5)
      def nextInt(n: Int): UIO[Int] =
        UIO.succeed(n - 1)
      val nextInt: UIO[Int] =
        UIO.succeed(0)
      val nextLong: UIO[Long] =
        UIO.succeed(0L)
      def nextLong(n: Long): UIO[Long] =
        UIO.succeed(0L)
      val nextPrintableChar: UIO[Char] =
        UIO.succeed('A')
      def nextString(length: Int): UIO[String] =
        UIO.succeed("")
      def shuffle[A](list: List[A]): UIO[List[A]] =
        UIO.succeed(list.reverse)
    }
  }

  case class Error(message: String) extends Exception
  case class Failure(message: String)
  case class Success[O](content: O)
}
