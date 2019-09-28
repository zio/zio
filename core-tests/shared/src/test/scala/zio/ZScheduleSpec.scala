package zio

import zio.ZScheduleSpecUtil._
import zio.clock.Clock
import zio.duration._
import zio.random.Random
import zio.test.Assertion._
import zio.test.{ assert, assertM, suite, testM, TestResult }

import scala.concurrent.Future
import zio.test.mock.MockRandom
import zio.test.mock.MockClock

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
            checkRepeat(Schedule.recurs(-5), expected = 0)
          },
          testM("for 'recurs(0)' does repeat 0 additional time") {
            // A repeat with 0 number of times should not repeat the action at all
            checkRepeat(Schedule.recurs(0), expected = 0)
          },
          testM("for 'recurs(1)' does repeat 1 additional time") {
            checkRepeat(Schedule.recurs(1), expected = 1)
          },
          testM("for 'once' does repeats 1 additional time") {
            for {
              ref <- Ref.make(0)
              _   <- ref.update(_ + 1).repeat(Schedule.once)
              res <- ref.get
            } yield assert(res, equalTo(2))
          },
          testM("for 'recurs(a positive given number)' repeats that additional number of time") {
            checkRepeat(Schedule.recurs(42), expected = 42)
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
          } yield ()).foldM[Any, Int, String](
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
              .foldM[Any, Int, String](
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
            } yield ()).foldM[Any, Int, String](
              err => IO.succeed(err),
              _ => IO.succeed("A failure was expected")
            )
            assertM(retried, equalTo("Error: 2"))
          },
          testM("for a given number of times with random jitter in (0, 1)") {
            val schedule  = ZSchedule.spaced(500.millis).jittered
            val scheduled = run(schedule >>> ZSchedule.elapsed)(List.fill(5)(()))
            val expected  = List(0.millis, 250.millis, 500.millis, 750.millis, 1000.millis)
            assertM(MockRandom.feedDoubles(0.5, 0.5, 0.5, 0.5, 0.5) *> scheduled, equalTo(expected))
          },
          testM("for a given number of times with random jitter in custom interval") {
            val schedule  = ZSchedule.spaced(500.millis).jittered(2, 4)
            val scheduled = run(schedule >>> ZSchedule.elapsed)((List.fill(5)(())))
            val expected  = List(0, 1500, 3000, 5000, 7000).map(_.millis)
            assertM(MockRandom.feedDoubles(0.5, 0.5, 1, 1, 0.5) *> scheduled, equalTo(expected))
          },
          testM("fixed delay with error predicate") {
            var i = 0
            val io = IO.effectTotal[Unit](i += 1).flatMap[Any, String, Unit] { _ =>
              if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
            }
            val strategy: ZSchedule[Clock, String, (Duration, Int)] = ZSchedule
              .spaced(200.millis)
              .fold((Duration.Zero, 0)) { case (acc, d) => (acc._1 + d, acc._2 + 1) }
              .whileInput[String](_ == "KeepTryingError")
            val expected = ("GiveUpError", (1000.millis, 5))
            val result   = io.retryOrElseEither(strategy, (e: String, s: (Duration, Int)) => ZIO.succeed((e, s)))
            assertM(MockClock.setTime(Duration.Infinity) *> result, isLeft(equalTo(expected)))
          },
          testM("fibonacci delay") {
            assertM(
              run(ZSchedule.fibonacci(100.millis) >>> ZSchedule.elapsed)(List.fill(5)(())),
              equalTo(List(0, 1, 2, 4, 7).map(i => (i * 100).millis))
            )
          },
          testM("linear delay") {
            assertM(
              run(ZSchedule.linear(100.millis) >>> ZSchedule.elapsed)(List.fill(5)(())),
              equalTo(List(0, 1, 3, 6, 10).map(i => (i * 100).millis))
            )
          },
          testM("exponential delay with default factor") {
            assertM(
              run(ZSchedule.exponential(100.millis) >>> ZSchedule.elapsed)(List.fill(5)(())),
              equalTo(List(0, 2, 6, 14, 30).map(i => (i * 100).millis))
            )
          },
          testM("exponential delay with other factor") {
            assertM(
              run(ZSchedule.exponential(100.millis, 3.0) >>> ZSchedule.elapsed)(List.fill(5)(())),
              equalTo(List(0, 3, 12, 39, 120).map(i => (i * 100).millis))
            )
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
              .foldM[Any, Int, String](
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
              .foldM[Any, Int, String](
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
              .retry(ZSchedule.spaced(2.seconds) && Schedule.recurs(1))
              .catchAll(
                error => ZIO.succeed(Left(Failure(error.message)))
              )

          val expected: Either[zio.ZScheduleSpecUtil.Failure, zio.ZScheduleSpecUtil.Success[String]] =
            Right(Success("Ok"))
          assertM(foo("Ok"), equalTo(expected))
        }
      )
    )

object ZScheduleSpecUtil {
  type Env = Clock with Random with zio.console.Console with zio.system.System
  val ioSucceed: (String, Unit) => UIO[String]      = (_: String, _: Unit) => IO.succeed("OrElse")
  val ioFail: (String, Unit) => IO[String, Nothing] = (_: String, _: Unit) => IO.fail("OrElseFailed")

  def repeat[B](schedule: Schedule[Int, B]): ZIO[Any with Clock, Nothing, B] =
    for {
      ref <- Ref.make(0)
      res <- ref.update(_ + 1).repeat(schedule)
    } yield res

  /**
   * Run a schedule using the provided input and collect all outputs
   */
  def run[A, B](sched: ZSchedule[Env, A, B])(xs: Iterable[A]): ZIO[MockClock with Env, Nothing, List[B]] = {
    final class Proxy(clock0: MockClock.Service[Any], env: Env)
        extends Clock
        with Random
        with zio.console.Console
        with zio.system.System {
      val random  = env.random
      val console = env.console
      val system  = env.system
      val clock = new Clock.Service[Any] {
        def currentDateTime                                  = clock0.currentDateTime
        def currentTime(unit: java.util.concurrent.TimeUnit) = clock0.currentTime(unit)
        val nanoTime                                         = clock0.nanoTime
        def sleep(duration: zio.duration.Duration)           = clock0.adjust(duration) *> clock0.sleep(duration)
      }
    }

    def loop(xs: List[A], state: sched.State, acc: List[B]): ZIO[Env, Nothing, List[B]] = xs match {
      case Nil => ZIO.succeed(acc)
      case x :: xs =>
        sched
          .update(x, state)
          .foldM(
            _ => ZIO.succeed(sched.extract(x, state) :: acc),
            s => loop(xs, s, sched.extract(x, state) :: acc)
          )
    }
    ZIO.environment[MockClock with Env].map(e => new Proxy(e.clock, e)) >>>
      sched.initial.flatMap(loop(xs.toList, _, Nil)).map(_.reverse)
  }

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
