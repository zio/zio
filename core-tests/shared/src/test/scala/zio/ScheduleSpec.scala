package zio

import scala.concurrent.Future

import zio.clock.Clock
import zio.duration._
import zio.test.Assertion._
import zio.test.environment.{ TestClock, TestRandom }
import zio.test.{ assert, assertM, suite, testM, TestResult }

object ScheduleSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("ScheduleSpec")(
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
        } yield assert(res)(equalTo(2))
      },
      testM("for 'recurs(a positive given number)' repeats that additional number of time") {
        checkRepeat(Schedule.recurs(42), expected = 42)
      },
      testM("for 'doWhile(cond)' repeats while the cond still holds") {
        def cond: Int => Boolean = _ < 10
        checkRepeat(Schedule.doWhile(cond), expected = 10)
      },
      testM("for 'doWhileM(cond)' repeats while the effectful cond still holds") {
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
        checkRepeat(Schedule.collectWhile(cond), expected = List(1, 2, 3, 4, 5, 6, 7, 8, 9))
      },
      testM("as long as the effectful condition f holds") {
        def cond = (x: Int) => IO.succeed(x > 10)
        checkRepeat(Schedule.collectWhileM(cond), expected = Nil)
      },
      testM("until the effectful condition f fails") {
        def cond = (i: Int) => i < 10 && i > 1
        checkRepeat(Schedule.collectUntil(cond), expected = List(1))
      },
      testM("until the effectful condition f fails") {
        def cond = (x: Int) => IO.succeed(x > 10)
        checkRepeat(Schedule.collectUntilM(cond), expected = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
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
      assertM(failed)(equalTo("Error: 1"))
    } @@ zioTag(errors),
    testM("Repeat a scheduled repeat repeats the whole number") {
      val n = 42
      for {
        ref <- Ref.make(0)
        io  = ref.update(_ + 1).repeat(Schedule.recurs(n))
        _   <- io.repeat(Schedule.recurs(1))
        res <- ref.get
      } yield assert(res)(equalTo((n + 1) * 2))
    },
    suite("Repeat an action 2 times and call `ensuring` should")(
      testM("run the specified finalizer as soon as the schedule is complete") {
        for {
          p          <- Promise.make[Nothing, Unit]
          r          <- Ref.make(0)
          _          <- r.update(_ + 2).repeat(Schedule.recurs(2)).ensuring(p.succeed(()))
          v          <- r.get
          finalizerV <- p.poll
        } yield assert(v)(equalTo(6)) && assert(finalizerV.isDefined)(equalTo(true))
      }
    ),
    suite("Retry on failure according to a provided strategy")(
      testM("retry 0 time for `once` when first time succeeds") {
        implicit val canFail = CanFail
        for {
          ref <- Ref.make(0)
          _   <- ref.update(_ + 1).retry(Schedule.once)
          i   <- ref.get
        } yield assert(i)(equalTo(1))
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
        failed.map(actual => assert(actual)(equalTo("Error: 1")))
      },
      testM("retry exactly one time for `once` when second time succeeds") {
        // one retry on failure
        for {
          ref <- Ref.make(0)
          _   <- failOn0(ref).retry(Schedule.once)
          r   <- ref.get
        } yield assert(r)(equalTo(2))
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
        assertM(retried)(equalTo("Error: 2"))
      },
      testM("for a given number of times with random jitter in (0, 1)") {
        val schedule  = Schedule.spaced(500.millis).jittered(0, 1)
        val scheduled = TestClock.runAll *> run(schedule >>> testElapsed)(List.fill(5)(()))
        val expected  = List(0.millis, 250.millis, 500.millis, 750.millis, 1000.millis)
        assertM(TestRandom.feedDoubles(0.5, 0.5, 0.5, 0.5, 0.5) *> scheduled)(equalTo(expected))
      },
      testM("for a given number of times with random jitter in custom interval") {
        val schedule  = Schedule.spaced(500.millis).jittered(2, 4)
        val scheduled = TestClock.runAll *> run(schedule >>> testElapsed)((List.fill(5)(())))
        val expected  = List(0, 1500, 3000, 5000, 7000).map(_.millis)
        assertM(TestRandom.feedDoubles(0.5, 0.5, 1, 1, 0.5) *> scheduled)(equalTo(expected))
      },
      testM("for a given number of times with random delay in custom interval") {
        val schedule  = Schedule.randomDelay(2.nanos, 4.nanos)
        val scheduled = TestClock.runAll *> run(schedule >>> testElapsed)((List.fill(5)(())))
        val expected  = List(0, 3, 6, 10, 14).map(_.nanos)
        assertM(TestRandom.feedLongs(1, 1, 2, 2, 1) *> scheduled)(equalTo(expected))
      },
      testM("fixed delay with error predicate") {
        var i = 0
        val io = IO.effectTotal(i += 1).flatMap[Any, String, Unit] { _ =>
          if (i < 5) IO.fail("KeepTryingError") else IO.fail("GiveUpError")
        }
        val strategy = Schedule.spaced(200.millis).whileInput[String](_ == "KeepTryingError")
        val expected = (800.millis, "GiveUpError", 4)
        val result   = io.retryOrElseEither(strategy, (e: String, r: Int) => TestClock.fiberTime.map((_, e, r)))
        assertM(TestClock.runAll *> result)(isLeft(equalTo(expected)))
      },
      testM("fibonacci delay") {
        assertM(
          TestClock
            .setTime(Duration.Infinity) *> run(Schedule.fibonacci(100.millis) >>> testElapsed)(List.fill(5)(()))
        )(equalTo(List(0, 1, 2, 4, 7).map(i => (i * 100).millis)))
      },
      testM("linear delay") {
        assertM(
          TestClock
            .setTime(Duration.Infinity) *> run(Schedule.linear(100.millis) >>> testElapsed)(List.fill(5)(()))
        )(equalTo(List(0, 1, 3, 6, 10).map(i => (i * 100).millis)))
      },
      testM("modified linear delay") {
        assertM(TestClock.runAll *> run(Schedule.linear(100.millis).modifyDelay {
          case (_, d) => ZIO.succeed(d * 2)
        } >>> testElapsed)(List.fill(5)(())))(equalTo(List(0, 1, 3, 6, 10).map(i => (i * 200).millis)))
      },
      testM("exponential delay with default factor") {
        assertM(
          TestClock
            .setTime(Duration.Infinity) *> run(Schedule.exponential(100.millis) >>> testElapsed)(List.fill(5)(()))
        )(equalTo(List(0, 1, 3, 7, 15).map(i => (i * 100).millis)))
      },
      testM("exponential delay with other factor") {
        assertM(
          TestClock.runAll *> run(Schedule.exponential(100.millis, 3.0) >>> testElapsed)(
            List.fill(5)(())
          )
        )(equalTo(List(0, 1, 4, 13, 40).map(i => (i * 100).millis)))
      },
      testM("fromDurations") {
        val schedule = Schedule.fromDurations(4.seconds, 7.seconds, 12.seconds, 19.seconds)
        val expected = List(0.seconds, 4.seconds, 11.seconds, 23.seconds, 42.seconds)
        val actual   = TestClock.runAll *> run(schedule >>> testElapsed)(List.fill(5)(()))
        assertM(actual)(equalTo(expected))
      }
    ) @@ zioTag(errors),
    suite("Retry according to a provided strategy")(
      testM("for up to 10 times") {
        var i        = 0
        val strategy = Schedule.recurs(10)
        val io       = IO.effectTotal(i += 1).flatMap(_ => if (i < 5) IO.fail("KeepTryingError") else IO.succeed(i))
        assertM(io.retry(strategy))(equalTo(5))
      }
    ) @@ zioTag(errors),
    suite("Return the result of the fallback after failing and no more retries left")(
      testM("if fallback succeed - retryOrElse") {
        for {
          ref <- Ref.make(0)
          o   <- alwaysFail(ref).retryOrElse(Schedule.once, ioSucceed)
        } yield assert(o)(equalTo("OrElse": Any))
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
        assertM(failed)(equalTo("OrElseFailed"))
      },
      testM("if fallback succeed - retryOrElseEither") {
        for {
          ref      <- Ref.make(0)
          o        <- alwaysFail(ref).retryOrElseEither(Schedule.once, ioSucceed)
          expected = Left("OrElse")
        } yield assert(o)(equalTo(expected))
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
        assertM(failed)(equalTo("OrElseFailed"))
      }
    ) @@ zioTag(errors),
    suite("Return the result after successful retry")(
      testM("retry exactly one time for `once` when second time succeeds - retryOrElse") {
        for {
          ref <- Ref.make(0)
          o   <- failOn0(ref).retryOrElse(Schedule.once, ioFail)
        } yield assert(o)(equalTo(2))
      },
      testM("retry exactly one time for `once` when second time succeeds - retryOrElse0") {
        for {
          ref      <- Ref.make(0)
          o        <- failOn0(ref).retryOrElseEither(Schedule.once, ioFail)
          expected = Right(2)
        } yield assert(o)(equalTo(expected))
      }
    ) @@ zioTag(errors),
    suite("Retry a failed action 2 times and call `ensuring` should")(
      testM("run the specified finalizer as soon as the schedule is complete") {
        for {
          p          <- Promise.make[Nothing, Unit]
          v          <- IO.fail("oh no").retry(Schedule.recurs(2)).ensuring(p.succeed(())).option
          finalizerV <- p.poll
        } yield assert(v.isEmpty)(equalTo(true)) && assert(finalizerV.isDefined)(equalTo(true))
      }
    ) @@ zioTag(errors),
    testM("`ensuring` should only call finalizer once.") {
      for {
        ref    <- Ref.make(0)
        sched  = Schedule.stop.ensuring(ref.update(_ + 1))
        s      <- sched.initial
        _      <- sched.update((), s).flip
        _      <- sched.update((), s).flip
        result <- ref.get.map(assert(_)(equalTo(1)))
      } yield result
    },
    testM("Retry type parameters should infer correctly") {
      def foo[O](v: O): ZIO[Any with Clock, Error, Either[ScheduleFailure, ScheduleSuccess[O]]] =
        ZIO
          .fromFuture(_ => Future.successful(v))
          .foldM(
            _ => ZIO.fail(ScheduleError("Some error")),
            ok => ZIO.succeed(Right(ScheduleSuccess(ok)))
          )
          .retry(Schedule.spaced(2.seconds) && Schedule.recurs(1))
          .catchAll(error => ZIO.succeed(Left(ScheduleFailure(error.message))))

      val expected = Right(ScheduleSuccess("Ok"))
      assertM(foo("Ok"))(equalTo(expected))
    },
    testM("either should not wait if neither schedule wants to continue") {
      assertM(
        TestClock
          .setTime(Duration.Infinity) *> run(
          (Schedule.stop || (Schedule.spaced(2.seconds) && Schedule.stop)) >>> testElapsed
        )(List.fill(5)(()))
      )(equalTo(List(Duration.Zero)))
    }
  )

  val ioSucceed: (String, Unit) => UIO[String]      = (_: String, _: Unit) => IO.succeed("OrElse")
  val ioFail: (String, Unit) => IO[String, Nothing] = (_: String, _: Unit) => IO.fail("OrElseFailed")

  def repeat[B](schedule: Schedule[Any, Int, B]): ZIO[Any with Clock, Nothing, B] =
    for {
      ref <- Ref.make(0)
      res <- ref.updateAndGet(_ + 1).repeat(schedule)
    } yield res

  /**
   * Run a schedule using the provided input and collect all outputs
   */
  def run[R, A, B](
    sched: Schedule[R, A, B]
  )(xs: Iterable[A]): ZIO[R, Nothing, List[B]] = {
    def loop(xs: List[A], state: sched.State, acc: List[B]): ZIO[R, Nothing, List[B]] = xs match {
      case Nil => ZIO.succeed(acc)
      case x :: xs =>
        sched
          .update(x, state)
          .foldM(
            _ => ZIO.succeed(sched.extract(x, state) :: acc),
            s => loop(xs, s, sched.extract(x, state) :: acc)
          )
    }
    sched.initial.flatMap(loop(xs.toList, _, Nil)).map(_.reverse)
  }

  def checkRepeat[B](schedule: Schedule[Any, Int, B], expected: B): ZIO[Any with Clock, Nothing, TestResult] =
    assertM(repeat(schedule))(equalTo(expected))

  /**
   * A function that increments ref each time it is called.
   * It always fails, with the incremented value in error
   */
  def alwaysFail(ref: Ref[Int]): IO[String, Int] =
    for {
      i <- ref.updateAndGet(_ + 1)
      x <- IO.fail(s"Error: $i")
    } yield x

  /**
   * A function that increments ref each time it is called.
   * It returns either a failure if ref value is 0 or less
   * before increment, and the value in other cases.
   */
  def failOn0(ref: Ref[Int]): IO[String, Int] =
    for {
      i <- ref.updateAndGet(_ + 1)
      x <- if (i <= 1) IO.fail(s"Error: $i") else IO.succeed(i)
    } yield x

  /**
   * A schedule that tracks how much time has elapsed using TestClock#fiberTime
   */
  val testElapsed =
    Schedule[TestClock, Duration, Any, Duration](
      ZIO.succeed(Duration.Zero),
      { case _            => TestClock.fiberTime },
      { case (_, elapsed) => elapsed }
    )

  case class ScheduleError(message: String) extends Exception
  case class ScheduleFailure(message: String)
  case class ScheduleSuccess[O](content: O)
}
