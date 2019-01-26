// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scalaz.zio.Exit.Cause
import scalaz.zio.duration._
import scalaz.zio.internal.{ Env, Executor }

import scala.concurrent.ExecutionContext
import scala.annotation.switch

/**
 * An `IO[E, A]` ("Eye-Oh of Eeh Aye") is an immutable data structure that
 * describes an effectful action that may fail with an `E`, run forever, or
 * produce a single `A` at some point in the future.
 *
 * Conceptually, this structure is equivalent to `EitherT[F, E, A]` for some
 * infallible effect monad `F`, but because monad transformers perform poorly
 * in Scala, this structure bakes in the `EitherT` without runtime overhead.
 *
 * `IO` values are ordinary immutable values, and may be used like any other
 * values in purely functional code. Because `IO` values just *describe*
 * effects, which must be interpreted by a separate runtime system, they are
 * entirely pure and do not violate referential transparency.
 *
 * `IO` values can efficiently describe the following classes of effects:
 *
 *  - '''Pure Values''' &mdash; `IO.point`
 *  - '''Synchronous Effects''' &mdash; `IO.sync`
 *  - '''Asynchronous Effects''' &mdash; `IO.async`
 *  - '''Concurrent Effects''' &mdash; `io.fork`
 *  - '''Resource Effects''' &mdash; `io.bracket`
 *
 * The concurrency model is based on ''fibers'', a user-land lightweight thread,
 * which permit cooperative multitasking, fine-grained interruption, and very
 * high performance with large numbers of concurrently executing fibers.
 *
 * `IO` values compose with other `IO` values in a variety of ways to build
 * complex, rich, interactive applications. See the methods on `IO` for more
 * details about how to compose `IO` values.
 *
 * In order to integrate with Scala, `IO` values must be interpreted into the
 * Scala runtime. This process of interpretation executes the effects described
 * by a given immutable `IO` value. For more information on interpreting `IO`
 * values, see the default interpreter in `RTS` or the safe main function in
 * `App`.
 */
sealed abstract class IO[+E, +A] extends Serializable { self =>

  /**
   * Maps an `IO[E, A]` into an `IO[E, B]` by applying the specified `A => B` function
   * to the output of this action. Repeated applications of `map`
   * (`io.map(f1).map(f2)...map(f10000)`) are guaranteed stack safe to a depth
   * of at least 10,000.
   */
  final def map[B](f: A => B): IO[E, B] = (self.tag: @switch) match {
    case IO.Tags.Point =>
      val io = self.asInstanceOf[IO.Point[A]]

      new IO.Point(() => f(io.value()))

    case IO.Tags.Strict =>
      val io = self.asInstanceOf[IO.Strict[A]]

      new IO.Strict(f(io.value))

    case IO.Tags.Fail => self.asInstanceOf[IO[E, B]]

    case _ => new IO.FlatMap(self, (a: A) => new IO.Strict(f(a)))
  }

  /**
   * Maps an `IO[E, A]` into an `IO[E2, B]` by applying the specified `E => E2` and
   * `A => B` functions to the output of this action. Repeated applications of `bimap`
   * (`io.bimap(f1, g1).bimap(f2, g2)...bimap(f10000, g20000)`) are guaranteed stack safe to a depth
   * of at least 10,000.
   */
  final def bimap[E2, B](f: E => E2, g: A => B): IO[E2, B] = leftMap(f).map(g)

  /**
   * Creates a composite action that represents this action followed by another
   * one that may depend on the value produced by this one.
   *
   * {{{
   * val parsed = readFile("foo.txt").flatMap(file => parseFile(file))
   * }}}
   */
  final def flatMap[E1 >: E, B](f0: A => IO[E1, B]): IO[E1, B] = (self.tag: @switch) match {
    case IO.Tags.Fail => self.asInstanceOf[IO[E1, B]]
    case _            => new IO.FlatMap(self, f0)
  }

  /**
   * Forks this action into its own separate fiber, returning immediately
   * without the value produced by this action.
   *
   * The `Fiber[E, A]` returned by this action can be used to interrupt the
   * forked fiber with some exception, or to join the fiber to "await" its
   * computed value.
   *
   * {{{
   * for {
   *   fiber <- subtask.fork
   *   // Do stuff...
   *   a <- fiber.join
   * } yield a
   * }}}
   */
  final def fork: IO[Nothing, Fiber[E, A]] = new IO.Fork(this, None)

  /**
   * A more powerful version of `fork` that allows specifying a handler to be
   * invoked on any exceptions that are not handled by the forked fiber.
   */
  final def forkWith(handler: Cause[Any] => IO[Nothing, _]): IO[Nothing, Fiber[E, A]] =
    new IO.Fork(this, Some(handler))

  /**
   * Executes both this action and the specified action in parallel,
   * combining their results using given function `f`.
   * If either individual action fails, then the returned action will fail.
   *
   * TODO: Replace with optimized primitive.
   */
  final def zipWithPar[E1 >: E, B, C](that: IO[E1, B])(f: (A, B) => C): IO[E1, C] = {
    def coordinate[A, B](f: (A, B) => C)(winner: Exit[E1, A], loser: Fiber[E1, B]): IO[E1, C] =
      winner match {
        case Exit.Success(a)     => loser.join.map(f(a, _))
        case Exit.Failure(cause) => loser.interrupt *> IO.halt(cause)
      }
    val g = (b: B, a: A) => f(a, b)
    (self raceWith that)(coordinate(f), coordinate(g))
  }

  /**
   * Executes both this action and the specified action in parallel,
   * returning a tuple of their results. If either individual action fails,
   * then the returned action will fail.
   */
  final def zipPar[E1 >: E, B](that: IO[E1, B]): IO[E1, (A, B)] =
    self.zipWithPar(that)((a, b) => (a, b))

  /**
   * Races this action with the specified action, returning the first
   * result to produce an `A`, whichever it is. If neither action succeeds,
   * then the action will fail with some error.
   */
  final def race[E1 >: E, A1 >: A](that: IO[E1, A1]): IO[E1, A1] =
    raceEither(that).map(_.merge)

  /**
   * Races this action with the specified action, returning the first
   * result to produce a value, whichever it is. If neither action succeeds,
   * then the action will fail with some error.
   */
  final def raceEither[E1 >: E, B](that: IO[E1, B]): IO[E1, Either[A, B]] =
    raceWith(that)(
      (exit, right) =>
        exit.redeem[E1, Either[A, B]](
          _ => right.join.map(Right(_)),
          a => IO.succeedLeft(a) <* right.interrupt
        ),
      (exit, left) =>
        exit.redeem[E1, Either[A, B]](
          _ => left.join.map(Left(_)),
          b => IO.succeedRight(b) <* left.interrupt
        )
    )

  /**
   * Races this action with the specified action, returning the first
   * result to *finish*, whether it is by producing a value or by failing
   * with an error. If either of two actions fails before the other succeeds,
   * the entire race will fail with that error.
   */
  final def raceAttempt[E1 >: E, A1 >: A](that: IO[E1, A1]): IO[E1, A1] =
    raceWith(that)(
      { case (l, f) => l.fold(f.interrupt *> IO.halt(_), IO.succeed) },
      { case (r, f) => r.fold(f.interrupt *> IO.halt(_), IO.succeed) }
    )

  /**
   * Races this action with the specified action, invoking the
   * specified finisher as soon as one value or the other has been computed.
   */
  final def raceWith[E1, E2, B, C](
    that: IO[E1, B]
  )(
    leftDone: (Exit[E, A], Fiber[E1, B]) => IO[E2, C],
    rightDone: (Exit[E1, B], Fiber[E, A]) => IO[E2, C]
  ): IO[E2, C] = {
    def arbiter[E0, E1, A, B](
      f: (Exit[E0, A], Fiber[E1, B]) => IO[E2, C],
      loser: Fiber[E1, B],
      race: Ref[Int],
      done: Promise[E2, C]
    )(res: Exit[E0, A]): IO[Nothing, _] =
      IO.flatten(race.modify((c: Int) => (if (c > 0) IO.unit else f(res, loser).to(done).void) -> (c + 1)))

    for {
      done  <- Promise.make[E2, C]
      race  <- Ref[Int](0)
      child <- Ref[IO[Nothing, Any]](IO.unit)
      c <- ((for {
            left  <- self.fork.peek(f => child update (_ *> f.interrupt))
            right <- that.fork.peek(f => child update (_ *> f.interrupt))
            _     <- left.await.flatMap(arbiter(leftDone, right, race, done)).fork
            _     <- right.await.flatMap(arbiter(rightDone, left, race, done)).fork
          } yield ()).uninterruptible *> done.await).onInterrupt(
            IO.flatten(child.get)
          )
    } yield c
  }

  /**
   * Executes this action and returns its value, if it succeeds, but
   * otherwise executes the specified action.
   */
  final def orElse[E2, A1 >: A](that: => IO[E2, A1]): IO[E2, A1] =
    redeemOrElse(that, IO.succeed)

  /**
   * Alias for orElse.
   *
   * Executes this action and returns its value, if it succeeds, but
   * otherwise executes the specified action.
   */
  final def <>[E2, A1 >: A](that: => IO[E2, A1]): IO[E2, A1] =
    orElse(that)

  /**
   * Executes this action and returns its value, if it succeeds, but
   * otherwise executes the specified action.
   */
  final def orElseEither[E2, B](that: => IO[E2, B]): IO[E2, Either[A, B]] =
    redeemOrElse(that.map(Right(_)), IO.succeedLeft)

  /**
   * Alias for orElseEither.
   *
   * Executes this action and returns its value, if it succeeds, but
   * otherwise executes the specified action.
   */
  final def <||>[E2, B](that: => IO[E2, B]): IO[E2, Either[A, B]] =
    orElseEither(that)

  private final def redeemOrElse[E2, B](that: => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = {
    val err = (cause: Cause[E]) =>
      if (cause.interrupted || cause.isChecked) that else IO.halt(cause.asInstanceOf[Cause[Nothing]])

    (self.tag: @switch) match {
      case IO.Tags.Fail =>
        val io = self.asInstanceOf[IO.Fail[E]]
        err(io.cause)

      case _ => new IO.Redeem(self, err, succ)
    }
  }

  /**
   * Maps over the error type. This can be used to lift a "smaller" error into
   * a "larger" error.
   */
  final def leftMap[E2](f: E => E2): IO[E2, A] =
    self.redeem[E2, A](f.andThen(IO.fail), IO.succeed)

  /**
   * Creates a composite action that represents this action followed by another
   * one that may depend on the error produced by this one.
   *
   * {{{
   * val parsed = readFile("foo.txt").flatMapError(error => logErrorToFile(error))
   * }}}
   */
  final def flatMapError[E2](f: E => IO[Nothing, E2]): IO[E2, A] =
    self.redeem[E2, A](f.andThen(_.flip), IO.succeed)

  /**
   *  Swaps the error/value parameters, applies the function `f` and flips the parameters back
   */
  final def flipWith[A1, E1](f: IO[A, E] => IO[A1, E1]): IO[E1, A1] = f(self.flip).flip

  /**
   * Swaps the error/value around, making it easier to handle errors.
   */
  final def flip: IO[A, E] =
    self.redeem(IO.succeed, IO.fail)

  /**
   * Recovers from errors by accepting one action to execute for the case of an
   * error, and one action to execute for the case of success.
   *
   * This method has better performance than `attempt` since no intermediate
   * value is allocated and does not require subsequent calls to `flatMap` to
   * define the next action.
   *
   * The error parameter of the returned `IO` may be chosen arbitrarily, since
   * it will depend on the `IO`s returned by the given continuations.
   */
  final def redeem[E2, B](err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] =
    redeem0((cause: Cause[E]) => cause.checkedOrRefail.fold(err, IO.halt), succ)

  /**
   * Alias for redeem
   */
  @deprecated("Use redeem", "scalaz-zio 0.6.0")
  final def foldM[E2, B](err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] =
    redeem(err, succ)

  /**
   * A more powerful version of redeem that allows recovering from any kind of failure except interruptions.
   */
  final def redeem0[E2, B](err: Cause[E] => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] =
    (self.tag: @switch) match {
      case IO.Tags.Fail =>
        val io = self.asInstanceOf[IO.Fail[E]]
        err(io.cause)

      case _ => new IO.Redeem(self, err, succ)
    }

  /**
   * Less powerful version of `redeem` which always returns a successful
   * `IO[Nothing, B]` after applying one of the given mapping functions depending
   * on the result of this `IO`
   */
  final def redeemPure[B](err: E => B, succ: A => B): IO[Nothing, B] =
    redeem(err.andThen(IO.succeed), succ.andThen(IO.succeed))

  /**
   * Alias for redeemPure
   */
  @deprecated("Use redeemPure", "scalaz-zio 0.6.0")
  final def fold[B](err: E => B, succ: A => B): IO[Nothing, B] =
    redeemPure(err, succ)

  /**
   * Executes this action, capturing both failure and success and returning
   * the result in an `Either`. This method is useful for recovering from
   * `IO` actions that may fail.
   *
   * The error parameter of the returned `IO` is Nothing, since
   * it is guaranteed the `IO` action does not raise any errors.
   */
  final def attempt: IO[Nothing, Either[E, A]] =
    self.redeem[Nothing, Either[E, A]](IO.succeedLeft, IO.succeedRight)

  /**
   * Unwraps the optional success of this effect, but can fail with unit value.
   */
  final def get[E1 >: E, B](implicit ev1: E1 =:= Nothing, ev2: A <:< Option[B]): IO[Unit, B] =
    IO.absolve(self.leftMap(ev1).map(_.toRight(())))

  /**
   * Executes this action, skipping the error but returning optionally the success.
   */
  final def option: IO[Nothing, Option[A]] =
    self.redeem0(_ => IO.succeed(None), a => IO.succeed(Some(a)))

  /**
   * When this action represents acquisition of a resource (for example,
   * opening a file, launching a thread, etc.), `bracket` can be used to ensure
   * the acquisition is not interrupted and the resource is released.
   *
   * The function does two things:
   *
   * 1. Ensures this action, which acquires the resource, will not be
   * interrupted. Of course, acquisition may fail for internal reasons (an
   * uncaught exception).
   * 2. Ensures the `release` action will not be interrupted, and will be
   * executed so long as this action successfully acquires the resource.
   *
   * In between acquisition and release of the resource, the `use` action is
   * executed.
   *
   * If the `release` action fails, then the entire action will fail even
   * if the `use` action succeeds. If this fail-fast behavior is not desired,
   * errors produced by the `release` action can be caught and ignored.
   *
   * {{{
   * openFile("data.json").bracket(closeFile) { file =>
   *   for {
   *     header <- readHeader(file)
   *     ...
   *   } yield result
   * }
   * }}}
   */
  final def bracket[E1 >: E, B](release: A => IO[Nothing, _])(use: A => IO[E1, B]): IO[E1, B] =
    IO.bracket[E1, A, B](this)(release)(use)

  /**
   * A more powerful version of `bracket` that provides information on whether
   * or not `use` succeeded to the release action.
   */
  final def bracket0[E1 >: E, B](
    release: (A, Exit[E1, B]) => IO[Nothing, _]
  )(use: A => IO[E1, B]): IO[E1, B] =
    IO.bracket0[E1, A, B](this)(release)(use)

  /**
   * A less powerful variant of `bracket` where the value produced by this
   * action is not needed.
   */
  final def bracket_[E1 >: E, B](release: IO[Nothing, _])(use: IO[E1, B]): IO[E1, B] =
    IO.bracket[E1, A, B](self)(_ => release)(_ => use)

  /**
   * Executes the specified finalizer, whether this action succeeds, fails, or
   * is interrupted. This method should not be used for cleaning up resources,
   * because it's possible the fiber will be interrupted after acquisition but
   * before the finalizer is added.
   */
  final def ensuring(finalizer: IO[Nothing, _]): IO[E, A] =
    new IO.Ensuring(self, finalizer)

  /**
   * Executes the action on the specified `ExecutionContext` and then shifts back
   * to the default one.
   */
  final def on(ec: ExecutionContext): IO[E, A] =
    self.lock(Executor.fromExecutionContext(Executor.Yielding, Int.MaxValue)(ec))

  /**
   * Forks an action that will be executed on the specified `ExecutionContext`.
   */
  final def forkOn(ec: ExecutionContext): IO[E, Fiber[E, A]] =
    self.on(ec).fork

  /**
   * Executes the release action only if there was an error.
   */
  final def bracketOnError[E1 >: E, B](release: A => IO[Nothing, _])(use: A => IO[E1, B]): IO[E1, B] =
    IO.bracket0[E1, A, B](this)(
      (a: A, eb: Exit[E1, B]) =>
        eb match {
          case Exit.Failure(_) => release(a)
          case _               => IO.unit
        }
    )(use)

  final def managed(release: A => IO[Nothing, _]): Managed[E, A] =
    Managed[E, A](this)(release)

  /**
   * Runs the specified action if this action fails, providing the error to the
   * action if it exists. The provided action will not be interrupted.
   */
  final def onError(cleanup: Cause[E] => IO[Nothing, _]): IO[E, A] =
    IO.bracket0(IO.unit)(
      (_, eb: Exit[E, A]) =>
        eb match {
          case Exit.Success(_)     => IO.unit
          case Exit.Failure(cause) => cleanup(cause)
        }
    )(_ => self)

  /**
   * Runs the specified action if this action is interrupted.
   */
  final def onInterrupt(cleanup: IO[Nothing, _]): IO[E, A] =
    self.ensuring(
      IO.descriptor flatMap (descriptor => if (descriptor.interrupted) cleanup else IO.unit)
    )

  /**
   * Runs the specified action if this action is terminated, either because of
   * a defect or because of interruption.
   */
  final def onTermination(cleanup: Cause[Nothing] => IO[Nothing, _]): IO[E, A] =
    IO.bracket0(IO.unit)(
      (_, eb: Exit[E, A]) =>
        eb match {
          case Exit.Failure(cause) => cause.checkedOrRefail.fold(_ => IO.unit, cleanup)
          case _                   => IO.unit
        }
    )(_ => self)

  /**
   * Supervises this action, which ensures that any fibers that are forked by
   * the action are interrupted when this action completes.
   */
  final def supervise: IO[E, A] = IO.supervise(self)

  /**
   * Supervises this action, which ensures that any fibers that are forked by
   * the action are handled by the provided supervisor.
   */
  final def superviseWith(supervisor: Iterable[Fiber[_, _]] => IO[Nothing, _]): IO[E, A] =
    IO.superviseWith(self)(supervisor)

  /**
   * Performs this action non-interruptibly. This will prevent the action from
   * being terminated externally, but the action may fail for internal reasons
   * (e.g. an uncaught error) or terminate due to defect.
   */
  final def uninterruptible: IO[E, A] = new IO.Uninterruptible(self)

  /**
   * Recovers from all errors.
   *
   * {{{
   * openFile("config.json").catchAll(_ => IO.succeed(defaultConfig))
   * }}}
   */
  final def catchAll[E2, A1 >: A](h: E => IO[E2, A1]): IO[E2, A1] =
    self.redeem[E2, A1](h, IO.succeed)

  /**
   * Recovers from some or all of the error cases.
   *
   * {{{
   * openFile("data.json").catchSome {
   *   case FileNotFoundException(_) => openFile("backup.json")
   * }
   * }}}
   */
  final def catchSome[E1 >: E, A1 >: A](pf: PartialFunction[E, IO[E1, A1]]): IO[E1, A1] = {
    def tryRescue(t: E): IO[E1, A1] = pf.applyOrElse(t, (_: E) => IO.fail[E1](t))

    self.redeem[E1, A1](tryRescue, IO.succeed)
  }

  /**
   * Translates the checked error (if present) into termination.
   */
  final def orDie[E1 >: E](implicit ev: E1 =:= Throwable): IO[Nothing, A] =
    self.leftMap(ev).catchAll(IO.die)

  /**
   * Maps this action to the specified constant while preserving the
   * effects of this action.
   */
  final def const[B](b: => B): IO[E, B] = self.map(_ => b)

  /**
   * A variant of `flatMap` that ignores the value produced by this action.
   */
  final def *>[E1 >: E, B](io: => IO[E1, B]): IO[E1, B] = self.flatMap(_ => io)

  /**
   * Sequences the specified action after this action, but ignores the
   * value produced by the action.
   */
  final def <*[E1 >: E, B](io: => IO[E1, B]): IO[E1, A] = self.flatMap(io.const(_))

  /**
   * Sequentially zips this effect with the specified effect using the
   * specified combiner function.
   */
  final def zipWith[E1 >: E, B, C](that: IO[E1, B])(f: (A, B) => C): IO[E1, C] =
    self.flatMap(a => that.map(b => f(a, b)))

  /**
   * Sequentially zips this effect with the specified effect, combining the
   * results into a tuple.
   */
  final def zip[E1 >: E, B](that: IO[E1, B]): IO[E1, (A, B)] =
    self.zipWith(that)((a, b) => (a, b))

  /**
   * Repeats this action forever (until the first error). For more sophisticated
   * schedules, see the `repeat` method.
   */
  final def forever: IO[E, Nothing] = self *> self.forever

  /**
   * Repeats this action with the specified schedule until the schedule
   * completes, or until the first failure.
   * Repeats are done in addition to the first execution so that
   * `io.repeat(Schedule.once)` means "execute io and in case of success repeat `io` once".
   */
  final def repeat[B](schedule: Schedule[A, B], clock: Clock = Clock.Live): IO[E, B] =
    repeatOrElse[E, B](schedule, (e, _) => IO.fail(e), clock)

  /**
   * Repeats this action with the specified schedule until the schedule
   * completes, or until the first failure. In the event of failure the progress
   * to date, together with the error, will be passed to the specified handler.
   */
  final def repeatOrElse[E2, B](
    schedule: Schedule[A, B],
    orElse: (E, Option[B]) => IO[E2, B],
    clock: Clock = Clock.Live
  ): IO[E2, B] =
    repeatOrElse0[B, E2, B](schedule, orElse, clock).map(_.merge)

  /**
   * Repeats this action with the specified schedule until the schedule
   * completes, or until the first failure. In the event of failure the progress
   * to date, together with the error, will be passed to the specified handler.
   */
  final def repeatOrElse0[B, E2, C](
    schedule: Schedule[A, B],
    orElse: (E, Option[B]) => IO[E2, C],
    clock: Clock = Clock.Live
  ): IO[E2, Either[C, B]] = {
    def loop(last: Option[() => B], state: schedule.State): IO[E2, Either[C, B]] =
      self.redeem(
        e => orElse(e, last.map(_())).map(Left(_)),
        a =>
          schedule.update(a, state, clock).flatMap { step =>
            if (!step.cont) IO.succeedRight(step.finish())
            else IO.succeed(step.state).delay(step.delay).flatMap(s => loop(Some(step.finish), s))
          }
      )

    schedule.initial(clock).flatMap(loop(None, _))
  }

  /**
   * Retries with the specified retry policy.
   * Retries are done following the failure of the original `io` (up to a fixed maximum with
   * `once` or `recurs` for example), so that that `io.retry(Schedule.once)` means
   * "execute `io` and in case of failure, try again once".
   */
  final def retry[E1 >: E, S](policy: Schedule[E1, S], clock: Clock = Clock.Live): IO[E1, A] =
    retryOrElse(policy, (e: E1, _: S) => IO.fail(e), clock)

  /**
   * Retries with the specified schedule, until it fails, and then both the
   * value produced by the schedule together with the last error are passed to
   * the recovery function.
   */
  final def retryOrElse[A2 >: A, E1 >: E, S, E2](
    policy: Schedule[E1, S],
    orElse: (E1, S) => IO[E2, A2],
    clock: Clock = Clock.Live
  ): IO[E2, A2] =
    retryOrElse0(policy, orElse, clock).map(_.merge)

  /**
   * Retries with the specified schedule, until it fails, and then both the
   * value produced by the schedule together with the last error are passed to
   * the recovery function.
   */
  final def retryOrElse0[E1 >: E, S, E2, B](
    policy: Schedule[E1, S],
    orElse: (E1, S) => IO[E2, B],
    clock: Clock = Clock.Live
  ): IO[E2, Either[B, A]] = {
    def loop(state: policy.State): IO[E2, Either[B, A]] =
      self.redeem(
        err =>
          policy
            .update(err, state, clock)
            .flatMap(
              decision =>
                if (decision.cont) IO.sleep(decision.delay) *> loop(decision.state)
                else orElse(err, decision.finish()).map(Left(_))
            ),
        succ => IO.succeedRight(succ)
      )

    policy.initial(clock).flatMap(loop)
  }

  /**
   * Maps this action to one producing unit, but preserving the effects of
   * this action.
   */
  final def void: IO[E, Unit] = const(())

  /**
   * Calls the provided function with the result of this action, and
   * sequences the resulting action after this action, but ignores the
   * value produced by the action.
   *
   * {{{
   * readFile("data.json").peek(putStrLn)
   * }}}
   */
  final def peek[E1 >: E, B](f: A => IO[E1, B]): IO[E1, A] = self.flatMap(a => f(a).const(a))

  /**
   * Times out an action by the specified duration.
   */
  final def timeout(d: Duration): IO[E, Option[A]] = timeout0[Option[A]](None)(Some(_))(d)

  /**
   * Times out this action by the specified duration.
   *
   * {{{
   * IO.point(1).timeout0(Option.empty[Int])(Some(_))(1.second)
   * }}}
   */
  final def timeout0[B](z: B)(f: A => B)(duration: Duration): IO[E, B] =
    self.map(f).sandboxWith(io => IO.absolve(io.attempt race IO.succeedRight(z).delay(duration)))

  /**
   * Flattens a nested action with a specified duration.
   */
  final def timeoutFail[E1 >: E](e: E1)(d: Duration): IO[E1, A] =
    IO.flatten(timeout0[IO[E1, A]](IO.fail(e))(IO.succeed)(d))

  /**
   * Returns a new action that executes this one and times the execution.
   */
  final def timed: IO[E, (Duration, A)] = timed0(system.nanoTime)

  /**
   * A more powerful variation of `timed` that allows specifying the clock.
   */
  final def timed0[E1 >: E](nanoTime: IO[E1, Long]): IO[E1, (Duration, A)] =
    summarized[E1, Long, Duration]((start, end) => Duration.fromNanos(end - start))(nanoTime)

  /**
   * Summarizes a action by computing some value before and after execution, and
   * then combining the values to produce a summary, together with the result of
   * execution.
   */
  final def summarized[E1 >: E, B, C](f: (B, B) => C)(summary: IO[E1, B]): IO[E1, (C, A)] =
    for {
      start <- summary
      value <- self
      end   <- summary
    } yield (f(start, end), value)

  /**
   * Delays this action by the specified amount of time.
   */
  final def delay(duration: Duration): IO[E, A] =
    IO.sleep(duration) *> self

  /**
   * Locks the execution of this action to the specified executor.
   */
  final def lock(executor: Executor): IO[E, A] =
    IO.lock(executor)(self)

  /**
   * Marks this action as unyielding to the runtime system for better
   * scheduling.
   */
  final def unyielding: IO[E, A] =
    IO.unyielding(self)

  /**
   * Runs this action in a new fiber, resuming when the fiber terminates.
   */
  final def run: IO[Nothing, Exit[E, A]] =
    new IO.Redeem[E, Nothing, A, Exit[E, A]](
      self,
      cause => IO.succeed(Exit.fail(cause)),
      succ => IO.succeed(Exit.succeed(succ))
    )

  /**
   * Runs this action in a new fiber, resuming when the fiber terminates.
   *
   * If the fiber fails with an error it will be captured in Right side of the error Either
   * If the fiber terminates because of defect, list of defects will be captured in the Left side of the Either
   *
   * Allows recovery from errors and defects alike, as in:
   *
   * {{{
   * case class DomainError()
   *
   * val veryBadIO: IO[DomainError, Unit] =
   *   IO.sync(5 / 0) *> IO.fail(DomainError())
   *
   * val caught: IO[Nothing, Unit] =
   *   veryBadIO.sandboxed.catchAll {
   *     case Left((_: ArithmeticException) :: Nil) =>
   *       // Caught defect: divided by zero!
   *       IO.succeed(0)
   *     case Left(ts) =>
   *       // Caught unknown defects, shouldn't recover!
   *       IO.terminate0(ts)
   *     case Right(e) =>
   *       // Caught error: DomainError!
   *      IO.succeed(0)
   *   }
   * }}}
   */
  final def sandbox: IO[Cause[E], A] = redeem0(IO.fail, IO.succeed)

  /**
   * Companion helper to `sandboxed`.
   *
   * Has a performance penalty due to forking a new fiber.
   *
   * Allows recovery, and partial recovery, from errors and defects alike, as in:
   *
   * {{{
   * case class DomainError()
   *
   * val veryBadIO: IO[DomainError, Unit] =
   *   IO.sync(5 / 0) *> IO.fail(DomainError())
   *
   * val caught: IO[DomainError, Unit] =
   *   veryBadIO.sandboxWith(_.catchSome {
   *     case Left((_: ArithmeticException) :: Nil) =>
   *       // Caught defect: divided by zero!
   *       IO.succeed(0)
   *   })
   * }}}
   *
   * Using `sandboxWith` with `catchSome` is better than using
   * `io.sandboxed.catchAll` with a partial match, because in
   * the latter, if the match fails, the original defects will
   * be lost and replaced by a `MatchError`
   */
  final def sandboxWith[E2, B](f: IO[Cause[E], A] => IO[Cause[E2], B]): IO[E2, B] =
    IO.unsandbox(f(self.sandbox))

  /**
   * Widens the action type to any supertype. While `map` suffices for this
   * purpose, this method is significantly faster for this purpose.
   */
  final def as[A1 >: A]: IO[E, A1] = self.asInstanceOf[IO[E, A1]]

  /**
   * Keep or break a promise based on the result of this action.
   */
  final def to[E1 >: E, A1 >: A](p: Promise[E1, A1]): IO[Nothing, Boolean] =
    self.run.flatMap(x => p.done(IO.done(x))).onInterrupt(p.interrupt)

  /**
   * An integer that identifies the term in the `IO` sum type to which this
   * instance belongs (e.g. `IO.Tags.Point`).
   */
  def tag: Int
}

object IO extends Serializable {

  @inline
  private final def succeedLeft[E, A]: E => IO[Nothing, Either[E, A]] =
    _succeedLeft.asInstanceOf[E => IO[Nothing, Either[E, A]]]

  private val _succeedLeft: Any => IO[Any, Either[Any, Any]] =
    e2 => IO.succeed[Either[Any, Any]](Left(e2))

  @inline
  private final def succeedRight[E, A]: A => IO[Nothing, Either[E, A]] =
    _succeedRight.asInstanceOf[A => IO[Nothing, Either[E, A]]]

  private val _succeedRight: Any => IO[Any, Either[Any, Any]] =
    a => IO.succeed[Either[Any, Any]](Right(a))

  final object Tags {
    final val FlatMap         = 0
    final val Point           = 1
    final val Strict          = 2
    final val SyncEffect      = 3
    final val Fail            = 4
    final val AsyncEffect     = 5
    final val Redeem          = 6
    final val Fork            = 7
    final val Uninterruptible = 8
    final val Supervise       = 9
    final val Ensuring        = 10
    final val Descriptor      = 11
    final val Lock            = 12
    final val Yield           = 13
  }

  final class FlatMap[E, A0, A] private[IO] (val io: IO[E, A0], val flatMapper: A0 => IO[E, A]) extends IO[E, A] {
    override def tag = Tags.FlatMap
  }

  final class Point[A] private[IO] (val value: () => A) extends IO[Nothing, A] {
    override def tag = Tags.Point
  }

  final class Strict[A] private[IO] (val value: A) extends IO[Nothing, A] {
    override def tag = Tags.Strict
  }

  final class SyncEffect[A] private[IO] (val effect: Env => A) extends IO[Nothing, A] {
    override def tag = Tags.SyncEffect
  }

  final class AsyncEffect[E, A] private[IO] (val register: (IO[E, A] => Unit) => Async[E, A]) extends IO[E, A] {
    override def tag = Tags.AsyncEffect
  }

  final class Redeem[E, E2, A, B] private[IO] (
    val value: IO[E, A],
    val err: Cause[E] => IO[E2, B],
    val succ: A => IO[E2, B]
  ) extends IO[E2, B]
      with Function[A, IO[E2, B]] {

    override def tag = Tags.Redeem

    final def apply(v: A): IO[E2, B] = succ(v)
  }

  final class Fork[E, A] private[IO] (val value: IO[E, A], val handler: Option[Cause[Any] => IO[Nothing, _]])
      extends IO[Nothing, Fiber[E, A]] {
    override def tag = Tags.Fork
  }

  final class Uninterruptible[E, A] private[IO] (val io: IO[E, A]) extends IO[E, A] {
    override def tag = Tags.Uninterruptible
  }

  final class Supervise[E, A] private[IO] (
    val value: IO[E, A],
    val supervisor: Iterable[Fiber[_, _]] => IO[Nothing, _]
  ) extends IO[E, A] {
    override def tag = Tags.Supervise
  }

  final class Fail[E] private[IO] (val cause: Cause[E]) extends IO[E, Nothing] {
    override def tag = Tags.Fail
  }

  final class Ensuring[E, A] private[IO] (val io: IO[E, A], val finalizer: IO[Nothing, _]) extends IO[E, A] {
    override def tag = Tags.Ensuring
  }

  final object Descriptor extends IO[Nothing, Fiber.Descriptor] {
    override def tag = Tags.Descriptor
  }

  final class Lock[E, A] private[IO] (val executor: Executor, val io: IO[E, A]) extends IO[E, A] {
    override def tag = Tags.Lock
  }

  final object Yield extends IO[Nothing, Unit] {
    override def tag = Tags.Yield
  }

  /**
   * Lifts a strictly evaluated value into the `IO` monad.
   */
  final def succeed[A](a: A): IO[Nothing, A] = new Strict(a)

  /**
   * Lifts a non-strictly evaluated value into the `IO` monad. Do not use this
   * function to capture effectful code. The result is undefined but may
   * include duplicated effects.
   */
  final def succeedLazy[A](a: => A): IO[Nothing, A] = new Point(() => a)

  /**
   * Alias for succeedLazy
   */
  @deprecated("Use succeedLazy", "scalaz-zio 0.6.0")
  final def point[A](a: => A): IO[Nothing, A] = succeedLazy(a)

  /**
   * Creates an `IO` value that represents failure with the specified error.
   * The moral equivalent of `throw` for pure code.
   */
  final def fail[E](error: E): IO[E, Nothing] = halt(Cause.checked(error))

  /**
   * Alias for fail
   */
  @deprecated("Use fail", "scalaz-zio 0.6.0")
  final def raiseChecked[E](error: E): IO[E, Nothing] = fail(error)

  /**
   * Strictly-evaluated unit lifted into the `IO` monad.
   */
  final val unit: IO[Nothing, Unit] = IO.succeed(())

  /**
   * Creates an `IO` value from `Exit`
   */
  final def done[E, A](r: Exit[E, A]): IO[E, A] = r match {
    case Exit.Success(b)     => succeed(b)
    case Exit.Failure(cause) => IO.halt(cause)
  }

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  final def sleep(duration: Duration): IO[Nothing, Unit] =
    IO.sync0(identity)
      .flatMap(
        env =>
          IO.asyncInterrupt[Nothing, Unit] { k =>
            val canceler = env.scheduler
              .schedule(() => k(IO.unit), duration)

            Left(IO.sync(canceler()))
          }
      )

  /**
   * Supervises the specified action, which ensures that any actions directly
   * forked by the action are killed upon the action's own termination.
   */
  final def supervise[E, A](io: IO[E, A]): IO[E, A] = superviseWith(io)(Fiber.interruptAll)

  /**
   * Supervises the specified action's spawned fibers.
   */
  final def superviseWith[E, A](io: IO[E, A])(supervisor: Iterable[Fiber[_, _]] => IO[Nothing, _]): IO[E, A] =
    new Supervise(io, supervisor)

  /**
   * Flattens a nested action.
   */
  final def flatten[E, A](io: IO[E, IO[E, A]]): IO[E, A] = io.flatMap(a => a)

  /**
   * Lazily produces an `IO` value whose construction may have actional costs
   * that should be deferred until evaluation.
   *
   * Do not use this method to effectfully construct `IO` values. The results
   * will be undefined and most likely involve the physical explosion of your
   * computer in a heap of rubble.
   */
  final def suspend[E, A](io: => IO[E, A]): IO[E, A] =
    IO.flatten(IO.sync(io))

  /**
   * Returns an `IO` that is interrupted.
   */
  final def interrupt: IO[Nothing, Nothing] = halt(Cause.interrupted)

  /**
   * Returns an `IO` that terminates with the specified `Throwable`.
   */
  final def die(t: Throwable): IO[Nothing, Nothing] = halt(Cause.unchecked(t))

  /**
   * Alias for die
   */
  @deprecated("Use die", "scalaz-zio 0.6.0")
  final def raiseUnchecked(t: Throwable): IO[Nothing, Nothing] = die(t)

  /**
   * Returns an `IO` that fails with the specified `Cause`.
   */
  final def halt[E](cause: Cause[E]): IO[E, Nothing] = new Fail(cause)

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   *
   * If the returned `IO` is interrupted, the blocked thread running the synchronous effect
   * will be interrupted via `Thread.interrupt`.
   */
  final def blocking[A](effect: => A): IO[Nothing, A] =
    IO.flatten(IO.sync {
      import java.util.concurrent.locks.ReentrantLock
      import java.util.concurrent.atomic.AtomicReference

      val lock   = new ReentrantLock()
      val thread = new AtomicReference[Option[Thread]](None)

      def withLock[B](b: => B): B =
        try {
          lock.lock(); b
        } finally lock.unlock()

      for {
        finalizer <- Ref[IO[Nothing, Unit]](IO.unit)
        a <- (for {
              fiber <- (IO.unyielding(IO.sync[Either[Throwable, A]] {
                        withLock(thread.set(Some(Thread.currentThread())))

                        try Right(effect)
                        catch {
                          case e: InterruptedException =>
                            Thread.interrupted
                            Left(e)
                        } finally withLock(thread.set(None)) // TODO: Signal finalizer to continue
                      }) <* finalizer
                        .set(IO.sync(withLock(thread.get.foreach(_.interrupt()))))).uninterruptible.fork
              either <- fiber.join
              a      <- either.fold[IO[Nothing, A]](IO.die, IO.succeed)
            } yield a).ensuring(IO.flatten(finalizer.get))
      } yield a
    })

  /**
   * Imports a synchronous effect into a pure `IO` value.
   *
   * {{{
   * val nanoTime: IO[Nothing, Long] = IO.sync(System.nanoTime())
   * }}}
   */
  final def sync[A](effect: => A): IO[Nothing, A] = sync0(_ => effect)

  /**
   * Imports a synchronous effect into a pure `IO` value. This variant of `sync`
   * lets you use the execution environment of the fiber.
   *
   * {{{
   * val nanoTime: IO[Nothing, Long] = IO.sync(System.nanoTime())
   * }}}
   */
  final def sync0[A](effect: Env => A): IO[Nothing, A] = new SyncEffect[A](effect)

  /**
   * Imports a synchronous effect into a pure `IO` value. This variant of `sync`
   * lets you use the current executor of the fiber.
   */
  final def syncExec[A](effect: Executor => A): IO[Nothing, A] =
    for {
      exec <- IO.descriptor.map(_.executor)
      a    <- IO.sync(effect(exec))
    } yield a

  /**
   *
   * Imports a synchronous effect into a pure `IO` value, translating any
   * throwables into a `Throwable` failure in the returned value.
   *
   * {{{
   * def putStrLn(line: String): IO[Throwable, Unit] = IO.syncThrowable(println(line))
   * }}}
   */
  final def syncThrowable[A](effect: => A): IO[Throwable, A] =
    syncCatch(effect) {
      case t: Throwable => t
    }

  /**
   *
   * Imports a synchronous effect into a pure `IO` value, translating any
   * exceptions into an `Exception` failure in the returned value.
   *
   * {{{
   * def putStrLn(line: String): IO[Exception, Unit] = IO.syncException(println(line))
   * }}}
   */
  final def syncException[A](effect: => A): IO[Exception, A] =
    syncCatch(effect) {
      case e: Exception => e
    }

  /**
   * Safely imports an exception-throwing synchronous effect into a pure `IO`
   * value, translating the specified throwables into `E` with the provided
   * user-defined function.
   */
  final def syncCatch[E, A](effect: => A)(f: PartialFunction[Throwable, E]): IO[E, A] =
    IO.absolve[E, A](
      IO.sync(
        try {
          val result = effect
          Right(result)
        } catch f andThen Left[E, A]
      )
    )

  /**
   * The moral equivalent of `if (p) exp`
   */
  final def when[E](b: Boolean)(io: IO[E, Unit]): IO[E, Unit] =
    if (b) io else IO.unit

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  final def whenM[E](b: IO[Nothing, Boolean])(io: IO[E, Unit]): IO[E, Unit] =
    b.flatMap(b => if (b) io else IO.unit)

  /**
   * Shifts execution to a thread in the default `ExecutionContext`.
   */
  @deprecated("use yieldNow", "0.6.0")
  final def shift: IO[Nothing, Unit] =
    yieldNow

  /**
   * Shifts the operation to another execution context.
   *
   * {{{
   *   IO.shift(myPool) *> myTask
   * }}}
   */
  @deprecated("use lock or on", "0.6.0")
  final def shift(ec: ExecutionContext): IO[Nothing, Unit] =
    IO.async { (k: IO[Nothing, Unit] => Unit) =>
      ec.execute(new Runnable {
        override def run(): Unit = k(IO.unit)
      })
    }

  /**
   * Locks the `io` to the specified executor.
   */
  final def lock[E, A](executor: Executor)(io: IO[E, A]): IO[E, A] =
    new Lock(executor, io)

  /**
   * A combinator that allows you to identify long-running `IO` values to the
   * runtime system for improved scheduling.
   */
  final def unyielding[E, A](io: IO[E, A]): IO[E, A] =
    IO.flatten(sync0(env => lock(env.executor(Executor.Unyielding))(io)))

  /**
   * Yields to the runtime system, starting on a fresh stack.
   */
  final def yieldNow: IO[Nothing, Unit] = Yield

  /**
   * Imports an asynchronous effect into a pure `IO` value. See `async0` for
   * the more expressive variant of this function that can return a value
   * synchronously.
   */
  final def async[E, A](register: (IO[E, A] => Unit) => Unit): IO[E, A] =
    async0((callback: IO[E, A] => Unit) => {
      register(callback)

      Async.later
    })

  /**
   * Imports an asynchronous effect into a pure `IO` value, possibly returning
   * the value synchronously.
   */
  final def async0[E, A](register: (IO[E, A] => Unit) => Async[E, A]): IO[E, A] =
    new AsyncEffect(register)

  /**
   * Imports an asynchronous effect into a pure `IO` value. This formulation is
   * necessary when the effect is itself expressed in terms of `IO`.
   */
  final def asyncIO[E, A](register: (IO[E, A] => Unit) => IO[Nothing, _]): IO[E, A] =
    for {
      p   <- Promise.make[E, A]
      ref <- Ref[IO[Nothing, Any]](IO.unit)
      a <- (for {
            _ <- IO
                  .flatten(IO.sync0(env => register(io => env.unsafeRunAsync_(io.to(p)))))
                  .fork
                  .peek(f => ref.set(f.interrupt))
                  .uninterruptible
            a <- p.await
          } yield a).onInterrupt(IO.flatten(ref.get))
    } yield a

  /**
   * Alias for asyncIO
   */
  @deprecated("Use asyncIO", "scalaz-zio 0.6.0")
  final def asyncM[E, A](register: (IO[E, A] => Unit) => IO[Nothing, _]): IO[E, A] =
    asyncIO(register)

  /**
   * Imports an asynchronous effect into a pure `IO` value. The effect has the
   * option of returning the value synchronously, which is useful in cases
   * where it cannot be determined if the effect is synchronous or asynchronous
   * until the effect is actually executed. The effect also has the option of
   * returning a canceler, which will be used by the runtime to cancel the
   * asynchronous effect if the fiber executing the effect is interrupted.
   */
  final def asyncInterrupt[E, A](register: (IO[E, A] => Unit) => Either[Canceler, IO[E, A]]): IO[E, A] = {
    import java.util.concurrent.atomic.AtomicBoolean
    import internal.OneShot

    IO.sync((new AtomicBoolean(false), OneShot.make[IO[Nothing, Any]])).flatMap {
      case (started, cancel) =>
        IO.flatten {
          async0[Nothing, IO[E, A]]((k: IO[Nothing, IO[E, A]] => Unit) => {
            started.set(true)

            try register(io => k(IO.succeed(io))) match {
              case Left(canceler) =>
                cancel.set(canceler)
                Async.later
              case Right(io) => Async.now(IO.succeed(io))
            } finally if (!cancel.isSet) cancel.set(IO.unit)
          })
        }.onInterrupt(IO.flatten(IO.sync(if (started.get) cancel.get() else IO.unit)))
    }
  }

  /**
   * Returns a action that will never produce anything. The moral
   * equivalent of `while(true) {}`, only without the wasted CPU cycles.
   */
  final val never: IO[Nothing, Nothing] =
    IO.async[Nothing, Nothing](_ => ())

  /**
   * Submerges the error case of an `Either` into the `IO`. The inverse
   * operation of `IO.attempt`.
   */
  final def absolve[E, A](v: IO[E, Either[E, A]]): IO[E, A] =
    v.flatMap(fromEither)

  /**
   * The inverse operation `IO.sandboxed`
   *
   * Terminates with exceptions on the `Left` side of the `Either` error, if it
   * exists. Otherwise extracts the contained `IO[E, A]`
   */
  final def unsandbox[E, A](v: IO[Cause[E], A]): IO[E, A] = v.catchAll[E, A](IO.halt)

  /**
   * Lifts an `Either` into an `IO`.
   */
  final def fromEither[E, A](v: Either[E, A]): IO[E, A] =
    v.fold(IO.fail, IO.succeed)

  /**
   * Lifts an `Option` into an `IO`.
   */
  final def fromOption[A](v: Option[A]): IO[Unit, A] =
    v.fold[IO[Unit, A]](IO.fail(()))(IO.succeed)

  /**
   * Imports a `Try` into an `IO`.
   */
  final def fromTry[A](effect: => scala.util.Try[A]): IO[Throwable, A] =
    syncThrowable(effect).flatMap {
      case scala.util.Success(v) => IO.succeed(v)
      case scala.util.Failure(t) => IO.fail(t)
    }

  /**
   * Creates an `IO` value that represents the exit value of the specified
   * fiber.
   */
  final def fromFiber[E, A](fiber: Fiber[E, A]): IO[E, A] =
    fiber.join

  /**
   * Creates an `IO` value that represents the exit value of the specified
   * fiber.
   */
  final def fromFiberM[E, A](fiber: IO[E, Fiber[E, A]]): IO[E, A] =
    fiber.flatMap(_.join)

  /**
   * Retrieves the supervisor associated with the fiber running the action
   * returned by this method.
   */
  final def supervisor: IO[Nothing, Cause[Nothing] => IO[Nothing, _]] =
    descriptor.map(_.supervisor)

  /**
   * Requires that the given `IO[E, Option[A]]` contain a value. If there is no
   * value, then the specified error will be raised.
   */
  final def require[E, A](error: E): IO[E, Option[A]] => IO[E, A] =
    (io: IO[E, Option[A]]) => io.flatMap(_.fold[IO[E, A]](IO.fail[E](error))(IO.succeed[A]))

  /**
   * Forks all of the specified values, and returns a composite fiber that
   * produces a list of their results, in order.
   */
  final def forkAll[E, A](as: Iterable[IO[E, A]]): IO[Nothing, Fiber[E, List[A]]] =
    as.foldRight(IO.succeedLazy(Fiber.succeedLazy[E, List[A]](List()))) { (aIO, asFiberIO) =>
      asFiberIO.zip(aIO.fork).map {
        case (asFiber, aFiber) =>
          asFiber.zipWith(aFiber)((as, a) => a :: as)
      }
    }

  /**
   * Forks all of the specified values, and returns a composite fiber that
   * produces a list of their results, in order.
   */
  final def forkAll_[E, A](as: Iterable[IO[E, A]]): IO[Nothing, Unit] =
    as.foldRight(IO.unit)(_.fork *> _)

  /**
   * Acquires a resource, do some work with it, and then release that resource. With `bracket0`
   * not only is the acquired resource be cleaned up, the outcome of the computation is also
   * reified for processing.
   */
  final def bracket0[E, A, B](
    acquire: IO[E, A]
  )(release: (A, Exit[E, B]) => IO[Nothing, _])(use: A => IO[E, B]): IO[E, B] =
    Ref[IO[Nothing, Any]](IO.unit).flatMap { m =>
      (for {
        f <- acquire.flatMap(a => use(a).fork.peek(f => m.set(f.interrupt.flatMap(release(a, _))))).uninterruptible
        b <- f.join
      } yield b).ensuring(IO.flatten(m.get))
    }

  /**
   * Acquires a resource, do some work with it, and then release that resource. `bracket`
   * will release the resource no matter the outcome of the computation, and will
   * re-throw any exception that occurred in between.
   */
  final def bracket[E, A, B](
    acquire: IO[E, A]
  )(release: A => IO[Nothing, _])(use: A => IO[E, B]): IO[E, B] =
    Ref[IO[Nothing, Any]](IO.unit).flatMap { m =>
      (for {
        a <- acquire.flatMap(a => m.set(release(a)).const(a)).uninterruptible
        b <- use(a)
      } yield b).ensuring(IO.flatten(m.get))
    }

  /**
   * Apply the function fn to each element of the `Iterable[A]` and
   * return the results in a new `List[B]`. For parallelism use `foreachPar`.
   */
  final def foreach[E, A, B](in: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    in.foldRight[IO[E, List[B]]](IO.sync(Nil)) { (a, io) =>
      fn(a).zipWith(io)((b, bs) => b :: bs)
    }

  /**
   * Alias for foreach
   */
  @deprecated("Use foreach", "scalaz-zio 0.6.0")
  final def traverse[E, A, B](in: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    foreach(in)(fn)

  /**
   * Evaluate the elements of an `Iterable[A]` in parallel
   * and collect the results. This is the parallel version of `foreach`.
   */
  final def foreachPar[E, A, B](as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    as.foldRight[IO[E, List[B]]](IO.sync(Nil)) { (a, io) =>
      fn(a).zipWithPar(io)((b, bs) => b :: bs)
    }

  /**
   * Alias for foreachPar
   */
  @deprecated("Use foreachPar", "scalaz-zio 0.6.0")
  final def traversePar[E, A, B](as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    foreachPar(as)(fn)

  /**
   * Evaluate the elements of a traversable data structure in parallel
   * and collect the results. Only up to `n` tasks run in parallel.
   * This is a version of `foreachPar`, with a throttle.
   */
  final def foreachParN[E, A, B](n: Long)(as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    for {
      semaphore <- Semaphore(n)
      bs <- foreachPar(as) { a =>
             semaphore.withPermit(fn(a))
           }
    } yield bs

  /**
   * Alias for foreachParN
   */
  @deprecated("Use foreachParN", "scalaz-zio 0.3.3")
  final def traverseParN[E, A, B](n: Long)(as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    foreachParN(n)(as)(fn)

  /**
   * Evaluate each effect in the structure from left to right, and collect
   * the results. For parallelism use `collectAllPar`.
   */
  final def collectAll[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    foreach(in)(identity)

  /**
   * Alias for collectAll
   */
  @deprecated("Use collectAll", "scalaz-zio 0.6.0")
  final def sequence[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    collectAll(in)

  /**
   * Evaluate each effect in the structure in parallel, and collect
   * the results. This is the parallel version of `collectAll`.
   */
  final def collectAllPar[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    foreachPar(as)(identity)

  /**
   * Alias for collectAllPar
   */
  @deprecated("Use collectAllPar", "scalaz-zio 0.6.0")
  final def sequencePar[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    collectAllPar(as)

  /**
   * Evaluate each effect in the structure in parallel, and collect
   * the results. Only up to `n` tasks run in parallel.
   * This is a version of `collectAllPar`, with a throttle.
   */
  final def collectAllParN[E, A](n: Long)(as: Iterable[IO[E, A]]): IO[E, List[A]] =
    foreachParN(n)(as)(identity)

  /**
   * Alias for `collectAllParN`
   */
  @deprecated("Use collectAllParN", "scalaz-zio 0.3.3")
  final def sequenceParN[E, A](n: Long)(as: Iterable[IO[E, A]]): IO[E, List[A]] =
    collectAllParN(n)(as)

  /**
   * Races an `IO[E, A]` against elements of a `Iterable[IO[E, A]]`. Yields
   * either the first success or the last failure.
   */
  final def raceAll[E, A](io: IO[E, A], ios: Iterable[IO[E, A]]): IO[E, A] =
    ios.foldLeft[IO[E, A]](io)(_ race _)

  /**
   * Reduces an `Iterable[IO]` to a single IO, works in parallel.
   */
  final def reduceAll[E, A](a: IO[E, A], as: Iterable[IO[E, A]])(f: (A, A) => A): IO[E, A] =
    as.foldLeft(a) { (l, r) =>
      l.zipPar(r).map(f.tupled)
    }

  /**
   * Merges an `Iterable[IO]` to a single IO, works in parallel.
   */
  final def mergeAll[E, A, B](in: Iterable[IO[E, A]])(zero: B)(f: (B, A) => B): IO[E, B] =
    in.foldLeft[IO[E, B]](IO.succeedLazy[B](zero))((acc, a) => acc.zipPar(a).map(f.tupled))

  /**
   * Returns information about the current fiber, such as its fiber identity.
   */
  final def descriptor: IO[Nothing, Fiber.Descriptor] = Descriptor
}
