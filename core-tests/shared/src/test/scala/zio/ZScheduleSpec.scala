package zio

import zio.ZScheduleSpecUtil._
import zio.clock.Clock
import zio.duration._
import zio.random.Random
import zio.test.Assertion.equalTo
import zio.test.{ assert, assertM, suite, testM, TestResult }

import scala.concurrent.Future

object ZScheduleSpec
    extends ZIOBaseSpec(
      suite("ZScheduleSpec")(
        /**
         * Retry `once` means that we try to exec `io`, get and error,
         * try again to exec `io`, and whatever the output is, we return that
         * second result.
         * The three following tests test retry when:
         * - the first time succeeds (no retry)
         * - the first time fails and the second succeeds (one retry, result success)
         * - both first time and retry fail (one retry, result failure)
         */
        suite("Repeat on success according to a provided strategy")(
          testM("for 'recurs(a negative number)' repeats 0 additional time") {
            // A repeat with a negative number of times should not repeat the action at all
            checkRepeat(Schedule.recurs(-5), expected = 1)
          },
          testM("for 'recurs(0)' does repeats 0 additional time") {
            // A repeat with 0 number of times should not repeat the action at all
            checkRepeat(Schedule.recurs(0), expected = 1)
          },
          testM("for 'recurs(1)' does repeats 1 additional time") {
            checkRepeat(Schedule.recurs(1), expected = 2)
          },
          testM("for 'once' does repeats 1 additional time") {
            for {
              ref <- Ref.make(0)
              _   <- ref.update(_ + 1).repeat(Schedule.once)
              res <- ref.get
            } yield assert(res, equalTo(2))
          },
          testM("for 'recurs(a positive given number)' repeats that additional number of time") {
            checkRepeat(Schedule.recurs(42), expected = 42 + 1)
          },
          testM("for 'doWhile(cond)' repeats while the cond still holds") {
            def cond: Int => Boolean = _ < 10
            checkRepeat(Schedule.doWhile(cond), expected = 10)
          },
          testM("for 'doWhileM(cond)' repeats while the effectul cond still holds") {
            def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
            checkRepeat(Schedule.doWhileM(cond), expected = 1)
          },
          testM("for 'doWhileEquals(cond)' repeats while the cond is equal") {
            checkRepeat(Schedule.doWhileEquals(1), expected = 2)
          },
          testM("for 'doUntil(cond)' repeats until the cond is satisfied") {
            def cond: Int => Boolean = _ < 10
            checkRepeat(Schedule.doUntil(cond), expected = 1)
          },
          testM("for 'doUntilM(cond)' repeats until the effectful cond is satisfied") {
            def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
            checkRepeat(Schedule.doUntilM(cond), expected = 11)
          },
          testM("for 'doUntilEquals(cond)' repeats until the cond is equal") {
            checkRepeat(Schedule.doUntilEquals(1), expected = 1)
          }
        ),
        suite("Collect all inputs into a list")(
          testM("as long as the condition f holds") {
            def cond: Int => Boolean = _ < 10
            checkRepeat(Schedule.collectWhile(cond), expected = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
          },
          testM("as long as the effectful condition f holds") {
            def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
            checkRepeat(Schedule.collectWhileM(cond), expected = List(1))
          },
          testM("until the effectful condition f fails") {
            def cond: Int => Boolean = _ < 10
            checkRepeat(Schedule.collectUntil(cond), expected = List(1))
          },
          testM("until the effectful condition f fails") {
            def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
            checkRepeat(Schedule.collectUntilM(cond), expected = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))
          }
        ),
        testM("Repeat on failure does not actually repeat") {
          val failed = (for {
            ref <- Ref.make(0)
            _   <- alwaysFail(ref).repeat(Schedule.recurs(42))
          } yield ()).foldM(
            err => IO.succeed(err),
            _ => IO.succeed("it should not be a success at all")
          )
          assertM(failed, equalTo("Error: 1"))
        },
        testM("Repeat a scheduled repeat repeats the whole number") {
          val n = 42
          for {
            ref <- Ref.make(0)
            io  = ref.update(_ + 1).repeat(Schedule.recurs(n))
            _   <- io.repeat(Schedule.recurs(1))
            res <- ref.get
          } yield assert(res, equalTo((n + 1) * 2))
        },
        suite("Repeat an action 2 times and call `ensuring` should")(
          testM("run the specified finalizer as soon as the schedule is complete") {
            for {
              p          <- Promise.make[Nothing, Unit]
              r          <- Ref.make(0)
              _          <- r.update(_ + 2).repeat(Schedule.recurs(2)).ensuring(p.succeed(()))
              v          <- r.get
              finalizerV <- p.poll
            } yield assert(v, equalTo(6)) && assert(finalizerV.isDefined, equalTo(true))
          }
        ),
        suite("Retry on failure according to a provided strategy")(
          testM("retry 0 time for `once` when first time succeeds") {
            for {
              ref <- Ref.make(0)
              _   <- ref.update(_ + 1).retry(Schedule.once)
              i   <- ref.get
            } yield assert(i, equalTo(1))
          },
          testM("retry 0 time for `recurs(0)`") {
            val failed = (for {
              ref <- Ref.make(0)
              i   <- alwaysFail(ref).retry(Schedule.recurs(0))
            } yield i)
              .foldM(
                err => IO.succeed(err),
                _ => IO.succeed("it should not be a success")
              )
            failed.map { actual =>
              assert(actual, equalTo("Error: 1"))
            }
          },
          testM("retry exactly one time for `once` when second time succeeds") {
            // one retry on failure
            for {
              ref <- Ref.make(0)
              _   <- failOn0(ref).retry(Schedule.once)
              r   <- ref.get
            } yield assert(r, equalTo(2))
          },
          testM("retry exactly one time for `once` even if still in error") {
            // no more than one retry on retry `once`
            val retried = (for {
              ref <- Ref.make(0)
              _   <- alwaysFail(ref).retry(Schedule.once)
            } yield ()).foldM(
              err => IO.succeed(err),
              _ => IO.succeed("A failure was expected")
            )
            assertM(retried, equalTo("Error: 2"))
          },
          testM("for a given number of times") {
            val retried  = retryCollect(IO.fail("Error"), Schedule.recurs(5))
            val expected = (Left("Error"): Either[Any, Nothing], List(1, 2, 3, 4, 5, 6).map((Duration.Zero, _)))
            assertM(retried, equalTo(expected))
          },
          testM("for a given number of times with random jitter in (0, 1)") {
            val schedule: ZSchedule[Random, Int, Int] = Schedule.recurs(5).delayed(_ => 500.millis).jittered
            val scheduled: UIO[List[(Duration, Int)]] = schedule.run(List(1, 2, 3, 4, 5)).provide(TestRandom)
            val expected                              = List(1, 2, 3, 4, 5).map((250.millis, _))
            assertM(scheduled, equalTo(expected))
          },
          testM("for a given number of times with random jitter in custom interval") {
            val schedule: ZSchedule[Random, Int, Int] = Schedule.recurs(5).delayed(_ => 500.millis).jittered(2, 4)
            val scheduled: UIO[List[(Duration, Int)]] = schedule.run(List(1, 2, 3, 4, 5)).provide(TestRandom)
            val expected                              = List(1, 2, 3, 4, 5).map((1500.millis, _))
            assertM(scheduled, equalTo(expected))
          },
          testM("fixed delay with error predicate") {
            var i = 0
            val io = IO.effectTotal[Unit](i += 1).flatMap[Any, String, Unit] { _ =>
              if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
            }
            val strategy = Schedule.spaced(200.millis).whileInput[String](_ == "KeepTryingError")
            val retried  = retryCollect(io, strategy)
            val expected = (Left("GiveUpError"): Either[String, Unit], List(1, 2, 3, 4, 5).map((200.millis, _)))
            assertM(retried, equalTo(expected))
          },
          testM("fibonacci delay") {
            checkErrorWithPredicate(Schedule.fibonacci(100.millis), List(1, 1, 2, 3, 5))
          },
          testM("linear delay") {
            checkErrorWithPredicate(Schedule.linear(100.millis), List(1, 2, 3, 4, 5))
          },
          testM("exponential delay with default factor") {
            checkErrorWithPredicate(Schedule.exponential(100.millis), List(2, 4, 8, 16, 32))
          },
          testM("exponential delay with other factor") {
            checkErrorWithPredicate(Schedule.exponential(100.millis, 3.0), List(3, 9, 27, 81, 243))
          }
        ),
        suite("Retry according to a provided strategy")(
          testM("for up to 10 times") {
            var i                            = 0
            val strategy: Schedule[Any, Int] = Schedule.recurs(10)
            val io = IO.effectTotal[Unit](i += 1).flatMap { _ =>
              if (i < 5) IO.fail("KeepTryingError") else IO.succeed(i)
            }
            assertM(io.retry(strategy), equalTo(5))
          }
        ),
        suite("Return the result of the fallback after failing and no more retries left")(
          testM("if fallback succeed - retryOrElse") {
            for {
              ref <- Ref.make(0)
              o   <- alwaysFail(ref).retryOrElse(Schedule.once, ioSucceed)
            } yield assert(o, equalTo("OrElse": Any))
          },
          testM("if fallback failed - retryOrElse") {
            val failed = (for {
              ref <- Ref.make(0)
              i   <- alwaysFail(ref).retryOrElse(Schedule.once, ioFail)
            } yield i)
              .foldM(
                err => IO.succeed(err),
                _ => IO.succeed("it should not be a success")
              )
            assertM(failed, equalTo("OrElseFailed"))
          },
          testM("if fallback succeed - retryOrElseEither") {
            for {
              ref                           <- Ref.make(0)
              o                             <- alwaysFail(ref).retryOrElseEither(Schedule.once, ioSucceed)
              expected: Either[String, Int] = Left("OrElse")
            } yield assert(o, equalTo(expected))
          },
          testM("if fallback failed - retryOrElseEither") {
            val failed = (for {
              ref <- Ref.make(0)
              i   <- alwaysFail(ref).retryOrElseEither(Schedule.once, ioFail)
            } yield i)
              .foldM(
                err => IO.succeed(err),
                _ => IO.succeed("it should not be a success")
              )
            assertM(failed, equalTo("OrElseFailed"))
          }
        ),
        suite("Return the result after successful retry")(
          testM("retry exactly one time for `once` when second time succeeds - retryOrElse") {
            for {
              ref <- Ref.make(0)
              o   <- failOn0(ref).retryOrElse(Schedule.once, ioFail)
            } yield assert(o, equalTo(2))
          },
          testM("retry exactly one time for `once` when second time succeeds - retryOrElse0") {
            for {
              ref                            <- Ref.make(0)
              o                              <- failOn0(ref).retryOrElseEither(Schedule.once, ioFail)
              expected: Either[Nothing, Int] = Right(2)
            } yield assert(o, equalTo(expected))
          }
        ),
        suite("Retry a failed action 2 times and call `ensuring` should")(
          testM("run the specified finalizer as soon as the schedule is complete") {
            for {
              p          <- Promise.make[Nothing, Unit]
              v          <- IO.fail("oh no").retry(Schedule.recurs(2)).ensuring(p.succeed(())).option
              finalizerV <- p.poll
            } yield assert(v.isEmpty, equalTo(true)) && assert(finalizerV.isDefined, equalTo(true))
          }
        ),
        testM("Retry type parameters should infer correctly") {
          def foo[O](v: O): ZIO[Any with Clock, Error, Either[Failure, Success[O]]] =
            ZIO.fromFuture { _ =>
              Future.successful(v)
            }.foldM(
                _ => ZIO.fail(Error("Some error")),
                ok => ZIO.succeed(Right(Success(ok)))
              )
              .retry(Schedule.spaced(2.seconds) && Schedule.recurs(1))
              .catchAll(
                error => ZIO.succeed(Left(Failure(error.message)))
              )

          val expected: Either[Failure, Success[String]] = Right(Success("Ok"))
          assertM(foo("Ok"), equalTo(expected))
        }
      )
    )

object ZScheduleSpecUtil {
  val ioSucceed: (String, Unit) => UIO[String]      = (_: String, _: Unit) => IO.succeed("OrElse")
  val ioFail: (String, Unit) => IO[String, Nothing] = (_: String, _: Unit) => IO.fail("OrElseFailed")

  def repeat[B](schedule: Schedule[Int, B]): ZIO[Any with Clock, Nothing, B] =
    for {
      ref <- Ref.make(0)
      res <- ref.update(_ + 1).repeat(schedule)
    } yield res

  def checkRepeat[B](schedule: Schedule[Int, B], expected: B): ZIO[Any with Clock, Nothing, TestResult] =
    assertM(repeat(schedule), equalTo(expected))

  /**
   * A function that increments ref each time it is called.
   * It always fails, with the incremented value in error
   */
  def alwaysFail(ref: Ref[Int]): IO[String, Int] =
    for {
      i <- ref.update(_ + 1)
      x <- IO.fail(s"Error: $i")
    } yield x

  /**
   * A function that increments ref each time it is called.
   * It returns either a failure if ref value is 0 or less
   * before increment, and the value in other cases.
   */
  def failOn0(ref: Ref[Int]): IO[String, Int] =
    for {
      i <- ref.update(_ + 1)
      x <- if (i <= 1) IO.fail(s"Error: $i") else IO.succeed(i)
    } yield x

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

  def checkErrorWithPredicate(
    schedule: Schedule[Any, Duration],
    expectedSteps: List[Int]
  ): ZIO[Any, Nothing, TestResult] = {
    var i = 0
    val io = IO.effectTotal[Unit](i += 1).flatMap[Any, String, Unit] { _ =>
      if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
    }
    val strategy = schedule.whileInput[String](_ == "KeepTryingError")
    val expected =
      (Left("GiveUpError"): Either[String, Unit], expectedSteps.map(i => ((i * 100).millis, (i * 100).millis)))
    assertM(retryCollect(io, strategy), equalTo(expected))
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
