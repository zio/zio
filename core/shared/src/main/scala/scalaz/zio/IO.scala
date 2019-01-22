// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scalaz.zio.Exit.Cause
import scalaz.zio.clock.Clock
import scalaz.zio.duration._
import scalaz.zio.internal.{Env, Executor}

import scala.concurrent.ExecutionContext
import scala.annotation.switch

/**
 * A `ZIO[R, E, A]` ("Zee-Oh of Are Eeh Aye") is an immutable data structure
 * that models an effectful program. The program requires an environment `R`,
 * and the program may fail with an error `E` or produce a single `A`.
 *
 * Conceptually, this structure is equivalent to `ReaderT[R, EitherT[UIO, E, ?]]`
 * for some infallible effect monad `UIO`, but because monad transformers
 * perform poorly in Scala, this data structure bakes in the reader effect of
 * `ReaderT` with the recoverable error effect of `EitherT` without runtime
 * overhead.
 *
 * `ZIO` values are ordinary immutable values, and may be used like any other
 * values in purely functional code. Because `ZIO` values just *model* effects
 * (like input / output), which must be interpreted by a separate runtime system,
 * `ZIO` values are entirely pure and do not violate referential transparency.
 *
 * `ZIO` values can efficiently describe the following classes of effects:
 *
 *  - '''Pure Values''' &mdash; `ZIO.succeed`
 *  - ```Error Effects``` &mdash; `ZIO.fail`
 *  - '''Synchronous Effects''' &mdash; `IO.sync`
 *  - '''Asynchronous Effects''' &mdash; `IO.async`
 *  - '''Concurrent Effects''' &mdash; `IO#fork`
 *  - '''Resource Effects''' &mdash; `IO#bracket`
 *  - ```Contextual Effects``` &mdash; `ZIO.read`
 *
 * The concurrency model is based on ''fibers'', a user-land lightweight thread,
 * which permit cooperative multitasking, fine-grained interruption, and very
 * high performance with large numbers of concurrently executing fibers.
 *
 * `ZIO` values compose with other `ZIO` values in a variety of ways to build
 * complex, rich, interactive applications. See the methods on `ZIO` for more
 * details about how to compose `ZIO` values.
 *
 * In order to integrate with Scala, `ZIO` values must be interpreted into the
 * Scala runtime. This process of interpretation executes the effects described
 * by a given immutable `ZIO` value. For more information on interpreting `ZIO`
 * values, see the default interpreter in `RTS` or the safe main function in
 * `App`.
 */
sealed abstract class ZIO[-R, +E, +A] extends Serializable { self =>

  /**
   * Embeds this program into one that requires a "bigger" environment.
   */
  final def contramap[R0](f: R0 => R): ZIO[R0, E, A] =
    ZIO.readM(r0 => self.provide(f(r0)))

  /**
   * Maps an `IO[E, A]` into an `IO[E, B]` by applying the specified `A => B` function
   * to the output of this action. Repeated applications of `map`
   * (`io.map(f1).map(f2)...map(f10000)`) are guaranteed stack safe to a depth
   * of at least 10,000.
   */
  final def map[B](f: A => B): ZIO[R, E, B] = (self.tag: @switch) match {
    case ZIO.Tags.Point =>
      val io = self.asInstanceOf[ZIO.Point[A]]

      new ZIO.Point(() => f(io.value()))

    case ZIO.Tags.Strict =>
      val io = self.asInstanceOf[ZIO.Strict[A]]

      new ZIO.Strict(f(io.value))

    case ZIO.Tags.Fail => self.asInstanceOf[ZIO[R, E, B]]

    case _ => new ZIO.FlatMap(self, (a: A) => new ZIO.Strict(f(a)))
  }

  /**
   * Maps an `IO[E, A]` into an `IO[E2, B]` by applying the specified `E => E2` and
   * `A => B` functions to the output of this action. Repeated applications of `bimap`
   * (`io.bimap(f1, g1).bimap(f2, g2)...bimap(f10000, g20000)`) are guaranteed stack safe to a depth
   * of at least 10,000.
   */
  final def bimap[E2, B](f: E => E2, g: A => B): ZIO[R, E2, B] = leftMap(f).map(g)

  /**
   * Creates a composite action that represents this action followed by another
   * one that may depend on the value produced by this one.
   *
   * {{{
   * val parsed = readFile("foo.txt").flatMap(file => parseFile(file))
   * }}}
   */
  final def flatMap[R1 <: R, E1 >: E, B](f0: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] = (self.tag: @switch) match {
    case ZIO.Tags.Fail => self.asInstanceOf[IO[E1, B]]
    case _             => new ZIO.FlatMap(self, f0)
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
  final def fork: ZIO[R, Nothing, Fiber[E, A]] =
    for {
      r     <- ZIO.read[R]
      fiber <- new ZIO.Fork(self.provide(r), None)
    } yield fiber

  /**
   * A more powerful version of `fork` that allows specifying a handler to be
   * invoked on any exceptions that are not handled by the forked fiber.
   */
  final def forkWith(handler: Cause[Any] => UIO[_]): ZIO[R, Nothing, Fiber[E, A]] =
    for {
      r     <- ZIO.read[R]
      fiber <- new ZIO.Fork(self.provide(r), Some(handler))
    } yield fiber

  /**
   * Executes both this action and the specified action in parallel,
   * combining their results using given function `f`.
   * If either individual action fails, then the returned action will fail.
   *
   * TODO: Replace with optimized primitive.
   */
  final def zipWithPar[R1 <: R, E1 >: E, B, C](that: ZIO[R1, E1, B])(f: (A, B) => C): ZIO[R1, E1, C] = {
    def coordinate[A, B](f: (A, B) => C)(winner: Exit[E1, A], loser: Fiber[E1, B]): ZIO[R1, E1, C] =
      winner match {
        case Exit.Success(a)     => loser.join.map(f(a, _))
        case Exit.Failure(cause) => loser.interrupt *> ZIO.halt(cause)
      }
    val g = (b: B, a: A) => f(a, b)
    (self raceWith that)(coordinate(f), coordinate(g))
  }

  /**
   * Executes both this action and the specified action in parallel,
   * returning a tuple of their results. If either individual action fails,
   * then the returned action will fail.
   */
  final def zipPar[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, (A, B)] =
    self.zipWithPar(that)((a, b) => (a, b))

  /**
   * Races this action with the specified action, returning the first
   * result to produce an `A`, whichever it is. If neither action succeeds,
   * then the action will fail with some error.
   */
  final def race[R1 <: R, E1 >: E, A1 >: A](that: ZIO[R1, E1, A1]): ZIO[R1, E1, A1] =
    raceEither(that).map(_.merge)

  /**
   * Races this action with the specified action, returning the first
   * result to produce a value, whichever it is. If neither action succeeds,
   * then the action will fail with some error.
   */
  final def raceEither[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, Either[A, B]] =
    raceWith(that)(
      (exit, right) =>
        exit.redeem[E1, Either[A, B]](
          _ => right.join.map(Right(_)),
          a => ZIO.succeedLeft(a) <* right.interrupt
        ),
      (exit, left) =>
        exit.redeem[E1, Either[A, B]](
          _ => left.join.map(Left(_)),
          b => ZIO.succeedRight(b) <* left.interrupt
        )
    )

  /**
   * Races this action with the specified action, returning the first
   * result to *finish*, whether it is by producing a value or by failing
   * with an error. If either of two actions fails before the other succeeds,
   * the entire race will fail with that error.
   */
  final def raceAttempt[R1 <: R, E1 >: E, A1 >: A](that: ZIO[R1, E1, A1]): ZIO[R1, E1, A1] =
    raceWith(that)(
      { case (l, f) => l.fold(f.interrupt *> ZIO.halt(_), ZIO.succeed) },
      { case (r, f) => r.fold(f.interrupt *> ZIO.halt(_), ZIO.succeed) }
    )

  /**
   * Races this action with the specified action, invoking the
   * specified finisher as soon as one value or the other has been computed.
   */
  final def raceWith[R1 <: R, E1, E2, B, C](
    that: ZIO[R1, E1, B]
  )(
    leftDone: (Exit[E, A], Fiber[E1, B]) => ZIO[R1, E2, C],
    rightDone: (Exit[E1, B], Fiber[E, A]) => ZIO[R1, E2, C]
  ): ZIO[R1, E2, C] = {
    def arbiter[E0, E1, A, B](
      f: (Exit[E0, A], Fiber[E1, B]) => ZIO[R1, E2, C],
      loser: Fiber[E1, B],
      race: Ref[Int],
      done: Promise[E2, C]
    )(res: Exit[E0, A]): ZIO[R1, Nothing, _] =
      ZIO.flatten(race.modify((c: Int) => (if (c > 0) ZIO.unit else f(res, loser).to(done).void) -> (c + 1)))

    for {
      done  <- Promise.make[E2, C]
      race  <- Ref[Int](0)
      child <- Ref[UIO[Any]](ZIO.unit)
      c <- ((for {
            left  <- self.fork.peek(f => child update (_ *> f.interrupt))
            right <- that.fork.peek(f => child update (_ *> f.interrupt))
            _     <- left.await.flatMap(arbiter(leftDone, right, race, done)).fork
            _     <- right.await.flatMap(arbiter(rightDone, left, race, done)).fork
          } yield ()).uninterruptible *> done.await).onInterrupt(
            ZIO.flatten(child.get)
          )
    } yield c
  }

  /**
   * Executes this action and returns its value, if it succeeds, but
   * otherwise executes the specified action.
   */
  final def orElse[R1 <: R, E2, A1 >: A](that: => ZIO[R1, E2, A1]): ZIO[R1, E2, A1] =
    redeemOrElse(that, ZIO.succeed)

  /**
   * Alias for orElse.
   *
   * Executes this action and returns its value, if it succeeds, but
   * otherwise executes the specified action.
   */
  final def <>[R1 <: R, E2, A1 >: A](that: => ZIO[R1, E2, A1]): ZIO[R1, E2, A1] =
    orElse(that)

  /**
   * Executes this action and returns its value, if it succeeds, but
   * otherwise executes the specified action.
   */
  final def orElseEither[R1 <: R, E2, B](that: => ZIO[R1, E2, B]): ZIO[R1, E2, Either[A, B]] =
    redeemOrElse(that.map(Right(_)), ZIO.succeedLeft)

  /**
   * Alias for orElseEither.
   *
   * Executes this action and returns its value, if it succeeds, but
   * otherwise executes the specified action.
   */
  final def <||>[R1 <: R, E2, B](that: => ZIO[R1, E2, B]): ZIO[R1, E2, Either[A, B]] =
    orElseEither(that)

  private final def redeemOrElse[R1 <: R, E2, B](that: => ZIO[R1, E2, B], succ: A => ZIO[R1, E2, B]): ZIO[R1, E2, B] = {
    val err = (cause: Cause[E]) =>
      if (cause.interrupted || cause.isChecked) that else ZIO.halt(cause.asInstanceOf[Cause[Nothing]])

    (self.tag: @switch) match {
      case ZIO.Tags.Fail =>
        val io = self.asInstanceOf[ZIO.Fail[E]]
        err(io.cause)

      case _ => new ZIO.Redeem(self, err, succ)
    }
  }

  /**
   * Maps over the error type. This can be used to lift a "smaller" error into
   * a "larger" error.
   */
  final def leftMap[E2](f: E => E2): ZIO[R, E2, A] =
    self.redeem(f.andThen(ZIO.fail), ZIO.succeed)

  /**
   * Creates a composite action that represents this action followed by another
   * one that may depend on the error produced by this one.
   *
   * {{{
   * val parsed = readFile("foo.txt").flatMapError(error => logErrorToFile(error))
   * }}}
   */
  final def flatMapError[R1 <: R, E2](f: E => ZIO[R1, Nothing, E2]): ZIO[R1, E2, A] =
    self.redeem(f.andThen(_.flip), ZIO.succeed)

  /**
   *  Swaps the error/value parameters, applies the function `f` and flips the parameters back
   */
  final def flipWith[R1, A1, E1](f: ZIO[R, A, E] => ZIO[R1, A1, E1]): ZIO[R1, E1, A1] = f(self.flip).flip

  /**
   * Swaps the error/value around, making it easier to handle errors.
   */
  final def flip: ZIO[R, A, E] =
    self.redeem(ZIO.succeed, ZIO.fail)

  /**
   * Recovers from errors by accepting one action to execute for the case of an
   * error, and one action to execute for the case of success.
   *
   * This method has better performance than `attempt` since no intermediate
   * value is allocated and does not require subsequent calls to `flatMap` to
   * define the next action.
   *
   * The error parameter of the returned `ZIO` may be chosen arbitrarily, since
   * it will depend on the `ZIO`s returned by the given continuations.
   */
  final def redeem[R1 <: R, E2, B](err: E => ZIO[R1, E2, B], succ: A => ZIO[R1, E2, B]): ZIO[R1, E2, B] =
    redeem0((cause: Cause[E]) => cause.checkedOrRefail.fold(err, ZIO.halt), succ)

  /**
   * A more powerful version of redeem that allows recovering from any kind of failure except interruptions.
   */
  final def redeem0[R1 <: R, E2, B](err: Cause[E] => ZIO[R1, E2, B], succ: A => ZIO[R1, E2, B]): ZIO[R1, E2, B] =
    (self.tag: @switch) match {
      case ZIO.Tags.Fail =>
        val io = self.asInstanceOf[ZIO.Fail[E]]
        err(io.cause)

      case _ => new ZIO.Redeem(self, err, succ)
    }

  /**
   * Less powerful version of `redeem` which always returns a successful
   * `IO[Nothing, B]` after applying one of the given mapping functions depending
   * on the result of this `ZIO`
   */
  final def redeemPure[B](err: E => B, succ: A => B): ZIO[R, Nothing, B] =
    redeem(err.andThen(ZIO.succeed), succ.andThen(ZIO.succeed))

  /**
   * Alias for redeemPure
   */
  @deprecated("Use redeemPure", "scalaz-zio 0.6.0")
  final def fold[B](err: E => B, succ: A => B): ZIO[R, Nothing, B] =
    redeemPure(err, succ)

  /**
   * Executes this action, capturing both failure and success and returning
   * the result in an `Either`. This method is useful for recovering from
   * `ZIO` actions that may fail.
   *
   * The error parameter of the returned `ZIO` is Nothing, since
   * it is guaranteed the `ZIO` action does not raise any errors.
   */
  final def attempt: ZIO[R, Nothing, Either[E, A]] =
    self.redeem(ZIO.succeedLeft, ZIO.succeedRight)

  /**
   * Unwraps the optional success of this effect, but can fail with unit value.
   */
  final def get[E1 >: E, B](implicit ev1: E1 =:= Nothing, ev2: A <:< Option[B]): ZIO[R, Unit, B] =
    ZIO.absolve(self.leftMap(ev1).map(_.toRight(())))

  /**
   * Executes this action, skipping the error but returning optionally the success.
   */
  final def option: ZIO[R, Nothing, Option[A]] =
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
  final def bracket[R1 <: R, E1 >: E, B](release: A => UIO[_])(use: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    ZIO.bracket[R1, E1, A, B](this)(release)(use)

  /**
   * A more powerful version of `bracket` that provides information on whether
   * or not `use` succeeded to the release action.
   */
  final def bracket0[R1 <: R, E1 >: E, B](
    release: (A, Exit[E1, B]) => UIO[_]
  )(use: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    ZIO.bracket0[R1, E1, A, B](this)(release)(use)

  /**
   * A less powerful variant of `bracket` where the value produced by this
   * action is not needed.
   */
  final def bracket_[R1 <: R, E1 >: E, B](release: UIO[_])(use: ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    ZIO.bracket[R1, E1, A, B](self)(_ => release)(_ => use)

  /**
   * Executes the specified finalizer, whether this action succeeds, fails, or
   * is interrupted. This method should not be used for cleaning up resources,
   * because it's possible the fiber will be interrupted after acquisition but
   * before the finalizer is added.
   */
  final def ensuring(finalizer: UIO[_]): ZIO[R, E, A] =
    new ZIO.Ensuring(self, finalizer)

  /**
   * Executes the action on the specified `ExecutionContext` and then shifts back
   * to the default one.
   */
  final def on(ec: ExecutionContext): ZIO[R, E, A] =
    self.lock(Executor.fromExecutionContext(Executor.Yielding, Int.MaxValue)(ec))

  /**
   * Forks an action that will be executed on the specified `ExecutionContext`.
   */
  final def forkOn(ec: ExecutionContext): ZIO[R, E, Fiber[E, A]] =
    self.on(ec).fork

  /**
   * Executes the release action only if there was an error.
   */
  final def bracketOnError[R1 <: R, E1 >: E, B](release: A => UIO[_])(use: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    ZIO.bracket0[R1, E1, A, B](this)(
      (a: A, eb: Exit[E1, B]) =>
        eb match {
          case Exit.Failure(_) => release(a)
          case _               => ZIO.unit
        }
    )(use)

  final def managed(release: A => UIO[_]): Managed[R, E, A] =
    Managed[R, E, A](this)(release)

  /**
   * Runs the specified action if this action fails, providing the error to the
   * action if it exists. The provided action will not be interrupted.
   */
  final def onError[R1 <: R](cleanup: Cause[E] => UIO[_]): ZIO[R1, E, A] =
    ZIO.bracket0[R1, E, Unit, A](ZIO.unit)(
      (_, eb: Exit[E, A]) =>
        eb match {
          case Exit.Success(_)     => ZIO.unit
          case Exit.Failure(cause) => cleanup(cause)
        }
    )(_ => self)

  /**
   * Runs the specified action if this action is interrupted.
   */
  final def onInterrupt(cleanup: UIO[_]): ZIO[R, E, A] =
    self.ensuring(
      ZIO.descriptor flatMap (descriptor => if (descriptor.interrupted) cleanup else ZIO.unit)
    )

  /**
   * Runs the specified action if this action is terminated, either because of
   * a defect or because of interruption.
   */
  final def onTermination(cleanup: Cause[Nothing] => UIO[_]): ZIO[R, E, A] =
    ZIO.bracket0[R, E, Unit, A](ZIO.unit)(
      (_, eb: Exit[E, A]) =>
        eb match {
          case Exit.Failure(cause) => cause.checkedOrRefail.fold(_ => ZIO.unit, cleanup)
          case _                   => ZIO.unit
        }
    )(_ => self)

  /**
   * Supervises this action, which ensures that any fibers that are forked by
   * the action are interrupted when this action completes.
   */
  final def supervise: ZIO[R, E, A] = ZIO.supervise(self)

  /**
   * Supervises this action, which ensures that any fibers that are forked by
   * the action are handled by the provided supervisor.
   */
  final def superviseWith(supervisor: Iterable[Fiber[_, _]] => UIO[_]): ZIO[R, E, A] =
    ZIO.superviseWith(self)(supervisor)

  /**
   * Performs this action non-interruptibly. This will prevent the action from
   * being terminated externally, but the action may fail for internal reasons
   * (e.g. an uncaught error) or terminate due to defect.
   */
  final def uninterruptible: ZIO[R, E, A] = new ZIO.Uninterruptible(self)

  /**
   * Recovers from all errors.
   *
   * {{{
   * openFile("config.json").catchAll(_ => IO.succeed(defaultConfig))
   * }}}
   */
  final def catchAll[R1 <: R, E2, A1 >: A](h: E => ZIO[R1, E2, A1]): ZIO[R1, E2, A1] =
    self.redeem[R1, E2, A1](h, ZIO.succeed)

  /**
   * Recovers from some or all of the error cases.
   *
   * {{{
   * openFile("data.json").catchSome {
   *   case FileNotFoundException(_) => openFile("backup.json")
   * }
   * }}}
   */
  final def catchSome[R1 <: R, E1 >: E, A1 >: A](pf: PartialFunction[E, ZIO[R1, E1, A1]]): ZIO[R1, E1, A1] = {
    def tryRescue(t: E): ZIO[R1, E1, A1] = pf.applyOrElse(t, (_: E) => ZIO.fail[E1](t))

    self.redeem[R1, E1, A1](tryRescue, ZIO.succeed)
  }

  /**
   * Translates the checked error (if present) into termination.
   */
  final def orDie[E1 >: E](implicit ev: E1 =:= Throwable): ZIO[R, Nothing, A] =
    self.leftMap(ev).catchAll(ZIO.die)

  /**
   * Maps this action to the specified constant while preserving the
   * effects of this action.
   */
  final def const[B](b: => B): ZIO[R, E, B] = self.map(_ => b)

  /**
   * A variant of `flatMap` that ignores the value produced by this action.
   */
  final def *>[R1 <: R, E1 >: E, B](io: => ZIO[R1, E1, B]): ZIO[R1, E1, B] = self.flatMap(_ => io)

  /**
   * Sequences the specified action after this action, but ignores the
   * value produced by the action.
   */
  final def <*[R1 <: R, E1 >: E, B](io: => ZIO[R1, E1, B]): ZIO[R1, E1, A] = self.flatMap(io.const(_))

  /**
   * Sequentially zips this effect with the specified effect using the
   * specified combiner function.
   */
  final def zipWith[R1 <: R, E1 >: E, B, C](that: ZIO[R1, E1, B])(f: (A, B) => C): ZIO[R1, E1, C] =
    self.flatMap(a => that.map(b => f(a, b)))

  /**
   * Sequentially zips this effect with the specified effect, combining the
   * results into a tuple.
   */
  final def zip[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, (A, B)] =
    self.zipWith(that)((a, b) => (a, b))

  /**
   * Repeats this action forever (until the first error). For more sophisticated
   * schedules, see the `repeat` method.
   */
  final def forever: ZIO[R, E, Nothing] = self *> self.forever

  /**
   * Repeats this action with the specified schedule until the schedule
   * completes, or until the first failure.
   * Repeats are done in addition to the first execution so that
   * `io.repeat(Schedule.once)` means "execute io and in case of success repeat `io` once".
   */
  final def repeat[B](schedule: Schedule[A, B]): ZIO[Clock with R, E, B] =
    repeatOrElse[R, E, B](schedule, (e, _) => ZIO.fail(e))

  /**
   * Repeats this action with the specified schedule until the schedule
   * completes, or until the first failure. In the event of failure the progress
   * to date, together with the error, will be passed to the specified handler.
   */
  final def repeatOrElse[R1 <: R, E2, B](
    schedule: Schedule[A, B],
    orElse: (E, Option[B]) => ZIO[R1, E2, B]
  ): ZIO[Clock with R1, E2, B] =
    repeatOrElse0[R1, B, E2, B](schedule, orElse).map(_.merge)

  /**
   * Repeats this action with the specified schedule until the schedule
   * completes, or until the first failure. In the event of failure the progress
   * to date, together with the error, will be passed to the specified handler.
   */
  final def repeatOrElse0[R1 <: R, B, E2, C](
    schedule: Schedule[A, B],
    orElse: (E, Option[B]) => ZIO[R1, E2, C]
  ): ZIO[Clock with R1, E2, Either[C, B]] = {
    def loop(last: Option[() => B], state: schedule.State): ZIO[Clock with R1, E2, Either[C, B]] =
      self.redeem(
        e => orElse(e, last.map(_())).map(Left(_)),
        a =>
          schedule.update(a, state).flatMap { step =>
            if (!step.cont) ZIO.succeedRight(step.finish())
            else ZIO.succeed(step.state).delay(step.delay).flatMap(s => loop(Some(step.finish), s))
          }
      )

    schedule.initial.flatMap(loop(None, _))
  }

  /**
   * Retries with the specified retry policy.
   * Retries are done following the failure of the original `io` (up to a fixed maximum with
   * `once` or `recurs` for example), so that that `io.retry(Schedule.once)` means
   * "execute `io` and in case of failure, try again once".
   */
  final def retry[E1 >: E, S](policy: Schedule[E1, S]): ZIO[Clock with R, E1, A] =
    retryOrElse(policy, (e: E1, _: S) => ZIO.fail(e))

  /**
   * Retries with the specified schedule, until it fails, and then both the
   * value produced by the schedule together with the last error are passed to
   * the recovery function.
   */
  final def retryOrElse[R1 <: R, A2 >: A, E1 >: E, S, E2](
    policy: Schedule[E1, S],
    orElse: (E1, S) => ZIO[R1, E2, A2]
  ): ZIO[Clock with R1, E2, A2] =
    retryOrElse0(policy, orElse).map(_.merge)

  /**
   * Retries with the specified schedule, until it fails, and then both the
   * value produced by the schedule together with the last error are passed to
   * the recovery function.
   */
  final def retryOrElse0[R1 <: R, E1 >: E, S, E2, B](
    policy: Schedule[E1, S],
    orElse: (E1, S) => ZIO[R1, E2, B]
  ): ZIO[Clock with R1, E2, Either[B, A]] = {
    def loop(state: policy.State): ZIO[Clock with R1, E2, Either[B, A]] =
      self.redeem(
        err =>
          policy
            .update(err, state)
            .flatMap(
              decision =>
                if (decision.cont) ZIO.sleep(decision.delay) *> loop(decision.state)
                else orElse(err, decision.finish()).map(Left(_))
            ),
        succ => ZIO.succeedRight(succ)
      )

    policy.initial.flatMap(loop)
  }

  /**
   * Maps this action to one producing unit, but preserving the effects of
   * this action.
   */
  final def void: ZIO[R, E, Unit] = const(())

  /**
   * Calls the provided function with the result of this action, and
   * sequences the resulting action after this action, but ignores the
   * value produced by the action.
   *
   * {{{
   * readFile("data.json").peek(putStrLn)
   * }}}
   */
  final def peek[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): ZIO[R1, E1, A] = self.flatMap(a => f(a).const(a))

  /**
   * Provides the `ZIO` program with its required environment.
   */
  final def provide(r: R): IO[E, A] = ZIO.provide(r)(self)

  /**
   * Times out an action by the specified duration.
   */
  final def timeout(d: Duration): ZIO[R, E, Option[A]] = timeout0[Option[A]](None)(Some(_))(d)

  /**
   * Times out this action by the specified duration.
   *
   * {{{
   * IO.succeed(1).timeout0(Option.empty[Int])(Some(_))(1.second)
   * }}}
   */
  final def timeout0[B](z: B)(f: A => B)(duration: Duration): ZIO[R, E, B] =
    self.map(f).sandboxWith[R, E, B](io => ZIO.absolve(io.attempt race ZIO.succeedRight(z).delay(duration)))

  /**
   * Flattens a nested action with a specified duration.
   */
  final def timeoutFail[E1 >: E](e: E1)(d: Duration): ZIO[R, E1, A] =
    ZIO.flatten(timeout0[ZIO[R, E1, A]](ZIO.fail(e))(ZIO.succeed)(d))

  /**
   * Returns a new action that executes this one and times the execution.
   */
  final def timed: ZIO[R, E, (Duration, A)] = timed0(system.nanoTime)

  /**
   * A more powerful variation of `timed` that allows specifying the clock.
   */
  final def timed0[R1 <: R, E1 >: E](nanoTime: ZIO[R1, E1, Long]): ZIO[R1, E1, (Duration, A)] =
    summarized[R1, E1, Long, Duration]((start, end) => Duration.fromNanos(end - start))(nanoTime)

  /**
   * Summarizes a action by computing some value before and after execution, and
   * then combining the values to produce a summary, together with the result of
   * execution.
   */
  final def summarized[R1 <: R, E1 >: E, B, C](f: (B, B) => C)(summary: ZIO[R1, E1, B]): ZIO[R1, E1, (C, A)] =
    for {
      start <- summary
      value <- self
      end   <- summary
    } yield (f(start, end), value)

  /**
   * Delays this action by the specified amount of time.
   */
  final def delay(duration: Duration): ZIO[R, E, A] =
    ZIO.sleep(duration) *> self

  /**
   * Locks the execution of this action to the specified executor.
   */
  final def lock(executor: Executor): ZIO[R, E, A] =
    ZIO.lock(executor)(self)

  /**
   * Marks this action as unyielding to the runtime system for better
   * scheduling.
   */
  final def unyielding: ZIO[R, E, A] =
    ZIO.unyielding(self)

  /**
   * Runs this action in a new fiber, resuming when the fiber terminates.
   */
  final def run: ZIO[R, Nothing, Exit[E, A]] =
    new ZIO.Redeem[R, E, Nothing, A, Exit[E, A]](
      self,
      cause => ZIO.succeed(Exit.fail(cause)),
      succ => ZIO.succeed(Exit.succeed(succ))
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
  final def sandbox: ZIO[R, Cause[E], A] = redeem0(ZIO.fail, ZIO.succeed)

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
  final def sandboxWith[R1 <: R, E2, B](f: ZIO[R1, Cause[E], A] => ZIO[R1, Cause[E2], B]): ZIO[R1, E2, B] =
    ZIO.unsandbox(f(self.sandbox))

  /**
   * Widens the action type to any supertype. While `map` suffices for this
   * purpose, this method is significantly faster for this purpose.
   */
  final def as[A1 >: A]: ZIO[R, E, A1] = self.asInstanceOf[ZIO[R, E, A1]]

  /**
   * Keep or break a promise based on the result of this action.
   */
  final def to[E1 >: E, A1 >: A](p: Promise[E1, A1]): ZIO[R, Nothing, Boolean] =
    self.run.flatMap(x => p.done(ZIO.done(x))).onInterrupt(p.interrupt)

  /**
   * An integer that identifies the term in the `ZIO` sum type to which this
   * instance belongs (e.g. `IO.Tags.Point`).
   */
  def tag: Int
}

trait ZIOFunctions extends Serializable {
  // ALL error types in this trait must be a subtype of `UpperE`.
  type UpperE
  // ALL environment types in this trait must be a supertype of `LowerR`.
  type LowerR

  /**
   * Creates a `ZIO` value that represents failure with the specified error.
   * The moral equivalent of `throw` for pure code.
   */
  final def fail[E <: UpperE](error: E): IO[E, Nothing] = halt(Cause.checked(error))

  /**
   * Returns a `ZIO` that fails with the specified `Cause`.
   */
  final def halt[E <: UpperE](cause: Cause[E]): IO[E, Nothing] = new ZIO.Fail(cause)

  /**
   * Lifts a strictly evaluated value into the `ZIO` monad.
   */
  final def succeed[A](a: A): UIO[A] = new ZIO.Strict(a)

  /**
   * Lifts a non-strictly evaluated value into the `ZIO` monad. Do not use this
   * function to capture effectful code. The result is undefined but may
   * include duplicated effects.
   */
  final def succeedLazy[A](a: => A): UIO[A] = new ZIO.Point(() => a)

  @deprecated("Use succeedLazy", "scalaz-zio 0.6.0")
  final def point[A](a: => A): UIO[A] = succeedLazy(a)

  /**
   * Accesses the environment of the program.
   */
  final def read[R >: LowerR]: ZIO[R, Nothing, R] =
    readM(succeed)

  /**
   * Effectfully accesses the environment of the program.
   */
  final def readM[R >: LowerR, E <: UpperE, A](f: R => ZIO[R, E, A]): ZIO[R, E, A] =
    new ZIO.Read(f)

  /**
   * Given an environment `R`, returns a function that can supply the
   * environment to programs that require it, removing their need for any
   * specific environment.
   */
  final def provide[R >: LowerR, E <: UpperE, A](r: R): ZIO[R, E, A] => IO[E, A] =
    (zio: ZIO[R, E, A]) => new ZIO.Provide(r, zio)

  /**
   * Returns a `ZIO` that is interrupted.
   */
  final val interrupt: UIO[Nothing] = halt(Cause.interrupted)

  /**
   * Returns a action that will never produce anything. The moral
   * equivalent of `while(true) {}`, only without the wasted CPU cycles.
   */
  final val never: UIO[Nothing] =
    async[Nothing, Nothing](_ => ())

  /**
   * Returns a `ZIO` that terminates with the specified `Throwable`.
   */
  final def die(t: Throwable): UIO[Nothing] = halt(Cause.unchecked(t))

  /**
   * Imports a synchronous effect into a pure `ZIO` value.
   *
   * {{{
   * val nanoTime: IO[Nothing, Long] = IO.sync(System.nanoTime())
   * }}}
   */
  final def sync[A](effect: => A): UIO[A] = sync0(_ => effect)

  /**
   * Imports a synchronous effect into a pure `ZIO` value. This variant of `sync`
   * lets you use the execution environment of the fiber.
   *
   * {{{
   * val nanoTime: IO[Nothing, Long] = IO.sync(System.nanoTime())
   * }}}
   */
  final def sync0[A](effect: Env => A): UIO[A] = new ZIO.SyncEffect[A](effect)

  /**
   * Imports a synchronous effect into a pure `ZIO` value. This variant of `sync`
   * lets you use the current executor of the fiber.
   */
  final def syncExec[A](effect: Executor => A): UIO[A] =
    for {
      exec <- ZIO.descriptor.map(_.executor)
      a    <- sync(effect(exec))
    } yield a

  /**
   * Shifts execution to a thread in the default `ExecutionContext`.
   */
  @deprecated("use yieldNow", "0.6.0")
  final val shift: UIO[Unit] = yieldNow

  /**
   * Shifts the operation to another execution context.
   *
   * {{{
   *   IO.shift(myPool) *> myTask
   * }}}
   */
  @deprecated("use lock or on", "0.6.0")
  final def shift(ec: ExecutionContext): UIO[Unit] =
    async { (k: UIO[Unit] => Unit) =>
      ec.execute(new Runnable {
        override def run(): Unit = k(ZIO.unit)
      })
    }

  /**
   * Yields to the runtime system, starting on a fresh stack.
   */
  final val yieldNow: UIO[Unit] = ZIO.Yield

  /**
   * Retrieves the supervisor associated with the fiber running the action
   * returned by this method.
   */
  final val supervisor: UIO[Cause[Nothing] => UIO[_]] =
    ZIO.descriptor.map(_.supervisor)

  /**
   * Forks all of the specified values, and returns a composite fiber that
   * produces a list of their results, in order.
   */
  final def forkAll[R >: LowerR, E <: UpperE, A](as: Iterable[ZIO[R, E, A]]): ZIO[R, Nothing, Fiber[E, List[A]]] =
    as.foldRight[ZIO[R, Nothing, Fiber[E, List[A]]]](succeed(Fiber.succeedLazy[E, List[A]](List()))) {
      (aIO, asFiberIO) =>
        asFiberIO.zip(aIO.fork).map {
          case (asFiber, aFiber) =>
            asFiber.zipWith(aFiber)((as, a) => a :: as)
        }
    }

  /**
   * Forks all of the specified values, and returns a composite fiber that
   * produces a list of their results, in order.
   */
  final def forkAll_[R >: LowerR, E <: UpperE, A](as: Iterable[ZIO[R, E, A]]): ZIO[R, Nothing, Unit] =
    as.foldRight[ZIO[R, Nothing, Unit]](ZIO.unit)(_.fork *> _)

  /**
   * Creates a `ZIO` value from `ExitResult`
   */
  final def done[E <: UpperE, A](r: Exit[E, A]): IO[E, A] = r match {
    case Exit.Success(b)     => succeed(b)
    case Exit.Failure(cause) => halt(cause)
  }

  /**
   * Supervises the specified action, which ensures that any actions directly
   * forked by the action are killed upon the action's own termination.
   */
  final def supervise[R >: LowerR, E <: UpperE, A](io: ZIO[R, E, A]): ZIO[R, E, A] =
    superviseWith(io)(Fiber.interruptAll)

  /**
   * Supervises the specified action's spawned fibers.
   */
  final def superviseWith[R >: LowerR, E <: UpperE, A](
    io: ZIO[R, E, A]
  )(supervisor: Iterable[Fiber[_, _]] => IO[Nothing, _]): ZIO[R, E, A] =
    new ZIO.Supervise(io, supervisor)

  /**
   * Flattens a nested action.
   */
  final def flatten[R >: LowerR, E <: UpperE, A](io: ZIO[R, E, ZIO[R, E, A]]): ZIO[R, E, A] = io.flatMap(a => a)

  /**
   * Lazily produces a `ZIO` value whose construction may have actional costs
   * that should be deferred until evaluation.
   *
   * Do not use this method to effectfully construct `ZIO` values. The results
   * will be undefined and most likely involve the physical explosion of your
   * computer in a heap of rubble.
   */
  final def suspend[R >: LowerR, E <: UpperE, A](io: => ZIO[R, E, A]): ZIO[R, E, A] =
    flatten(sync(io))

  /**
   * Safely imports an exception-throwing synchronous effect into a pure `ZIO`
   * value, translating the specified throwables into `E` with the provided
   * user-defined function.
   */
  final def syncCatch[E <: UpperE, A](effect: => A)(f: PartialFunction[Throwable, E]): IO[E, A] =
    absolve[Any, E, A](
      sync(
        try {
          val result = effect
          Right(result)
        } catch f andThen Left[E, A]
      )
    )

  /**
   * Locks the `io` to the specified executor.
   */
  final def lock[R >: LowerR, E <: UpperE, A](executor: Executor)(io: ZIO[R, E, A]): ZIO[R, E, A] =
    new ZIO.Lock(executor, io)

  /**
   * A combinator that allows you to identify long-running `ZIO` values to the
   * runtime system for improved scheduling.
   */
  final def unyielding[R >: LowerR, E <: UpperE, A](io: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.flatten(sync0(env => lock[R, E, A](env.executor(Executor.Unyielding))(io)))

  /**
   * Imports an asynchronous effect into a pure `ZIO` value. See `async0` for
   * the more expressive variant of this function that can return a value
   * synchronously.
   */
  final def async[E <: UpperE, A](register: (ZIO[Any, E, A] => Unit) => Unit): ZIO[Any, E, A] =
    async0((callback: ZIO[Any, E, A] => Unit) => {
      register(callback)

      Async.later
    })

  /**
   * Imports an asynchronous effect into a pure `ZIO` value, possibly returning
   * the value synchronously.
   */
  final def async0[E <: UpperE, A](register: (ZIO[Any, E, A] => Unit) => Async[E, A]): ZIO[Any, E, A] =
    new ZIO.AsyncEffect(register)

  /**
   * Imports an asynchronous effect into a pure `ZIO` value. This formulation is
   * necessary when the effect is itself expressed in terms of `ZIO`.
   */
  final def asyncIO[E <: UpperE, A](register: (IO[E, A] => Unit) => UIO[_]): IO[E, A] =
    for {
      p   <- Promise.make[E, A]
      ref <- Ref[IO[Nothing, Any]](ZIO.unit)
      a <- (for {
            _ <- flatten(sync0(env => register(io => env.unsafeRunAsync_(io.to(p))))).fork
                  .peek(f => ref.set(f.interrupt))
                  .uninterruptible
            a <- p.await
          } yield a).onInterrupt(flatten(ref.get))
    } yield a

  /**
   * Alias for asyncIO
   */
  @deprecated("Use asyncIO", "scalaz-zio 0.6.0")
  final def asyncM[E <: UpperE, A](register: (IO[E, A] => Unit) => UIO[_]): IO[E, A] =
    asyncIO(register)

  /**
   * Imports an asynchronous effect into a pure `ZIO` value. The effect has the
   * option of returning the value synchronously, which is useful in cases
   * where it cannot be determined if the effect is synchronous or asynchronous
   * until the effect is actually executed. The effect also has the option of
   * returning a canceler, which will be used by the runtime to cancel the
   * asynchronous effect if the fiber executing the effect is interrupted.
   */
  final def asyncInterrupt[R >: LowerR, E <: UpperE, A](
    register: (ZIO[R, E, A] => Unit) => Either[Canceler, ZIO[R, E, A]]
  ): ZIO[R, E, A] = {
    import java.util.concurrent.atomic.AtomicBoolean
    import internal.OneShot

    sync((new AtomicBoolean(false), OneShot.make[IO[Nothing, Any]])).flatMap {
      case (started, cancel) =>
        flatten {
          async0((k: UIO[ZIO[R, E, A]] => Unit) => {
            started.set(true)

            try register(io => k(ZIO.succeed(io))) match {
              case Left(canceler) =>
                cancel.set(canceler)
                Async.later
              case Right(io) => Async.now(ZIO.succeed(io))
            } finally if (!cancel.isSet) cancel.set(ZIO.unit)
          })
        }.onInterrupt(flatten(sync(if (started.get) cancel.get() else ZIO.unit)))
    }
  }

  /**
   * Submerges the error case of an `Either` into the `ZIO`. The inverse
   * operation of `IO.attempt`.
   */
  final def absolve[R >: LowerR, E <: UpperE, A](v: ZIO[R, E, Either[E, A]]): ZIO[R, E, A] =
    v.flatMap(fromEither)

  /**
   * The inverse operation `IO.sandboxed`
   *
   * Terminates with exceptions on the `Left` side of the `Either` error, if it
   * exists. Otherwise extracts the contained `IO[E, A]`
   */
  final def unsandbox[R >: LowerR, E <: UpperE, A](v: ZIO[R, Cause[E], A]): ZIO[R, E, A] = v.catchAll[R, E, A](halt)

  /**
   * Lifts an `Either` into a `ZIO` value.
   */
  final def fromEither[E <: UpperE, A](v: Either[E, A]): IO[E, A] =
    v.fold(fail, succeed)

  /**
   * Creates a `ZIO` value that represents the exit value of the specified
   * fiber.
   */
  final def fromFiber[E <: UpperE, A](fiber: Fiber[E, A]): IO[E, A] =
    fiber.join

  /**
   * Creates a `ZIO` value that represents the exit value of the specified
   * fiber.
   */
  final def fromFiberM[E <: UpperE, A](fiber: IO[E, Fiber[E, A]]): IO[E, A] =
    fiber.flatMap(_.join)

  /**
   * Requires that the given `IO[E, Option[A]]` contain a value. If there is no
   * value, then the specified error will be raised.
   */
  final def require[E <: UpperE, A](error: E): IO[E, Option[A]] => IO[E, A] =
    (io: IO[E, Option[A]]) => io.flatMap(_.fold[IO[E, A]](fail[E](error))(succeed[A]))

  /**
   * Acquires a resource, do some work with it, and then release that resource. `bracket`
   * will release the resource no matter the outcome of the computation, and will
   * re-throw any exception that occurred in between.
   */
  final def bracket[R >: LowerR, E <: UpperE, A, B](
    acquire: ZIO[R, E, A]
  )(release: A => UIO[_])(use: A => ZIO[R, E, B]): ZIO[R, E, B] =
    Ref[IO[Nothing, Any]](ZIO.unit).flatMap { m =>
      (for {
        a <- acquire.flatMap(a => m.set(release(a)).const(a)).uninterruptible
        b <- use(a)
      } yield b).ensuring(flatten(m.get))
    }

  /**
   * Acquires a resource, do some work with it, and then release that resource. With `bracket0`
   * not only is the acquired resource be cleaned up, the outcome of the computation is also
   * reified for processing.
   */
  final def bracket0[R >: LowerR, E <: UpperE, A, B](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[E, B]) => UIO[_])(use: A => ZIO[R, E, B]): ZIO[R, E, B] =
    Ref[IO[Nothing, Any]](ZIO.unit).flatMap { m =>
      (for {
        f <- acquire.flatMap(a => use(a).fork.peek(f => m.set(f.interrupt.flatMap(release(a, _))))).uninterruptible
        b <- f.join
      } yield b).ensuring(flatten(m.get))
    }

  /**
   * Apply the function fn to each element of the `Iterable[A]` and
   * return the results in a new `List[B]`. For parallelism use `foreachPar`.
   */
  final def foreach[R >: LowerR, E <: UpperE, A, B](in: Iterable[A])(fn: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    in.foldRight[ZIO[R, E, List[B]]](sync(Nil)) { (a, io) =>
      fn(a).zipWith(io)((b, bs) => b :: bs)
    }

  /**
   * Alias for foreach
   */
  @deprecated("Use foreach", "scalaz-zio 0.6.0")
  final def traverse[R >: LowerR, E <: UpperE, A, B](in: Iterable[A])(fn: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    foreach[R, E, A, B](in)(fn)

  /**
   * Evaluate the elements of an `Iterable[A]` in parallel
   * and collect the results. This is the parallel version of `foreach`.
   */
  final def foreachPar[R >: LowerR, E <: UpperE, A, B](as: Iterable[A])(fn: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    as.foldRight[ZIO[R, E, List[B]]](sync(Nil)) { (a, io) =>
      fn(a).zipWithPar(io)((b, bs) => b :: bs)
    }

  /**
   * Alias for foreachPar
   */
  @deprecated("Use foreachPar", "scalaz-zio 0.6.0")
  final def traversePar[R >: LowerR, E <: UpperE, A, B](as: Iterable[A])(fn: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    foreachPar[R, E, A, B](as)(fn)

  /**
   * Evaluate the elements of a traversable data structure in parallel
   * and collect the results. Only up to `n` tasks run in parallel.
   * This is a version of `foreachPar`, with a throttle.
   */
  final def foreachParN[R >: LowerR, E <: UpperE, A, B](
    n: Long
  )(as: Iterable[A])(fn: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    for {
      semaphore <- Semaphore(n)
      bs <- foreachPar[R, E, A, B](as) { a =>
             semaphore.withPermit(fn(a))
           }
    } yield bs

  /**
   * Alias for foreachParN
   */
  @deprecated("Use foreachParN", "scalaz-zio 0.3.3")
  final def traverseParN[R >: LowerR, E <: UpperE, A, B](
    n: Long
  )(as: Iterable[A])(fn: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    foreachParN[R, E, A, B](n)(as)(fn)

  /**
   * Evaluate each effect in the structure from left to right, and collect
   * the results. For parallelism use `collectAllPar`.
   */
  final def collectAll[R >: LowerR, E <: UpperE, A](in: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    foreach[R, E, ZIO[R, E, A], A](in)(identity(_))

  /**
   * Alias for collectAll
   */
  @deprecated("Use collectAll", "scalaz-zio 0.6.0")
  final def sequence[R >: LowerR, E <: UpperE, A](in: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    collectAll(in)

  /**
   * Evaluate each effect in the structure in parallel, and collect
   * the results. This is the parallel version of `collectAll`.
   */
  final def collectAllPar[R >: LowerR, E <: UpperE, A](as: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    foreachPar[R, E, ZIO[R, E, A], A](as)(identity(_))

  /**
   * Alias for collectAllPar
   */
  @deprecated("Use collectAllPar", "scalaz-zio 0.6.0")
  final def sequencePar[R >: LowerR, E <: UpperE, A](as: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    collectAllPar(as)

  /**
   * Evaluate each effect in the structure in parallel, and collect
   * the results. Only up to `n` tasks run in parallel.
   * This is a version of `collectAllPar`, with a throttle.
   */
  final def collectAllParN[R >: LowerR, E <: UpperE, A](n: Long)(as: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    foreachParN[R, E, ZIO[R, E, A], A](n)(as)(identity(_))

  /**
   * Alias for `collectAllParN`
   */
  @deprecated("Use collectAllParN", "scalaz-zio 0.3.3")
  final def sequenceParN[R >: LowerR, E <: UpperE, A](n: Long)(as: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    collectAllParN[R, E, A](n)(as)

  /**
   * Races an `IO[E, A]` against elements of a `Iterable[IO[E, A]]`. Yields
   * either the first success or the last failure.
   */
  final def raceAll[R >: LowerR, E <: UpperE, A](io: ZIO[R, E, A], ios: Iterable[ZIO[R, E, A]]): ZIO[R, E, A] =
    ios.foldLeft[ZIO[R, E, A]](io)(_ race _)

  /**
   * Reduces an `Iterable[IO]` to a single IO, works in parallel.
   */
  final def reduceAll[R >: LowerR, E <: UpperE, A](a: ZIO[R, E, A], as: Iterable[ZIO[R, E, A]])(
    f: (A, A) => A
  ): ZIO[R, E, A] =
    as.foldLeft(a) { (l, r) =>
      l.zipPar(r).map(f.tupled)
    }

  /**
   * Merges an `Iterable[IO]` to a single IO, works in parallel.
   */
  final def mergeAll[R >: LowerR, E <: UpperE, A, B](
    in: Iterable[ZIO[R, E, A]]
  )(zero: => B, f: (B, A) => B): ZIO[R, E, B] =
    in.foldLeft[ZIO[R, E, B]](succeedLazy[B](zero))((acc, a) => acc.zipPar(a).map(f.tupled))

  /**
   * Strictly-evaluated unit lifted into the `ZIO` monad.
   */
  final val unit: UIO[Unit] = succeed(())

  /**
   * The moral equivalent of `if (p) exp`
   */
  final def when[E <: UpperE](b: Boolean)(io: IO[E, Unit]): IO[E, Unit] =
    if (b) io else unit

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  final def whenM[E <: UpperE](b: IO[Nothing, Boolean])(io: IO[E, Unit]): IO[E, Unit] =
    b.flatMap(b => if (b) io else unit)

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  final def sleep(duration: Duration): UIO[Unit] =
    sync0(identity)
      .flatMap(
        env =>
          asyncInterrupt[Any, Nothing, Unit] { k =>
            val canceler = env.scheduler
              .schedule(() => k(unit), duration)

            Left(sync(canceler()))
          }
      )

  /**
   * Returns information about the current fiber, such as its fiber identity.
   */
  final def descriptor: IO[Nothing, Fiber.Descriptor] = ZIO.Descriptor
}

trait ZIO_E_Any extends ZIO_E_Throwable {
  type UpperE = Any

  /**
   * Lifts an `Option` into a `ZIO`.
   */
  final def fromOption[A](v: Option[A]): IO[Unit, A] =
    v.fold[IO[Unit, A]](fail(()))(succeed(_))
}

trait ZIO_E_Throwable extends ZIOFunctions {
//   implicit val ThrowableSubtypeOfE: Throwable <:< UpperE
  type UpperE >: Throwable

  /**
   *
   * Imports a synchronous effect into a pure `ZIO` value, translating any
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
   * Imports a `Try` into a `ZIO`.
   */
  final def fromTry[A](effect: => scala.util.Try[A]): IO[Throwable, A] =
    syncThrowable(effect).flatMap {
      case scala.util.Success(v) => ZIO.succeed(v)
      case scala.util.Failure(t) => ZIO.fail(t)
    }

  /**
   *
   * Imports a synchronous effect into a pure `ZIO` value, translating any
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
}

object IO extends ZIO_E_Any {
  type LowerR = Any
}
object Task extends ZIO_E_Throwable {
  type UpperE = Throwable
  type LowerR = Any
}
object UIO extends ZIOFunctions {
  type UpperE = Nothing
  type LowerR = Any
}

object ZIO extends ZIO_E_Any {
  type LowerR = Nothing

  @inline
  private final def succeedLeft[E, A]: E => UIO[Either[E, A]] =
    _succeedLeft.asInstanceOf[E => UIO[Either[E, A]]]

  private val _succeedLeft: Any => IO[Any, Either[Any, Any]] =
    e2 => succeed[Either[Any, Any]](Left(e2))

  @inline
  private final def succeedRight[E, A]: A => UIO[Either[E, A]] =
    _succeedRight.asInstanceOf[A => IO[Nothing, Either[E, A]]]

  private val _succeedRight: Any => IO[Any, Either[Any, Any]] =
    a => succeed[Either[Any, Any]](Right(a))

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
    final val Read            = 14
    final val Provide         = 15
  }
  final class FlatMap[R, E, A0, A](val io: ZIO[R, E, A0], val k: A0 => ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.FlatMap
  }

  final class Point[A](val value: () => A) extends UIO[A] {
    override def tag = Tags.Point
  }

  final class Strict[A](val value: A) extends UIO[A] {
    override def tag = Tags.Strict
  }

  final class SyncEffect[A](val effect: Env => A) extends UIO[A] {
    override def tag = Tags.SyncEffect
  }

  final class AsyncEffect[E, A](val register: (ZIO[Any, E, A] => Unit) => Async[E, A]) extends IO[E, A] {
    override def tag = Tags.AsyncEffect
  }

  final class Redeem[R, E, E2, A, B](
    val value: ZIO[R, E, A],
    val err: Cause[E] => ZIO[R, E2, B],
    val succ: A => ZIO[R, E2, B]
  ) extends ZIO[R, E2, B]
      with Function[A, ZIO[R, E2, B]] {

    override def tag = Tags.Redeem

    final def apply(v: A): ZIO[R, E2, B] = succ(v)
  }

  final class Fork[E, A](val value: IO[E, A], val handler: Option[Cause[Any] => UIO[_]]) extends UIO[Fiber[E, A]] {
    override def tag = Tags.Fork
  }

  final class Uninterruptible[R, E, A](val io: ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.Uninterruptible
  }

  final class Supervise[R, E, A](
    val value: ZIO[R, E, A],
    val supervisor: Iterable[Fiber[_, _]] => IO[Nothing, _]
  ) extends ZIO[R, E, A] {
    override def tag = Tags.Supervise
  }

  final class Fail[E](val cause: Cause[E]) extends IO[E, Nothing] {
    override def tag = Tags.Fail
  }

  final class Ensuring[R, E, A](val io: ZIO[R, E, A], val finalizer: UIO[_]) extends ZIO[R, E, A] {
    override def tag = Tags.Ensuring
  }

  final object Descriptor extends IO[Nothing, Fiber.Descriptor] {
    override def tag = Tags.Descriptor
  }

  final class Lock[R, E, A](val executor: Executor, val io: ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.Lock
  }

  final object Yield extends UIO[Unit] {
    override def tag = Tags.Yield
  }

  final class Read[R, E, A](val k: R => ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.Read
  }

  final class Provide[R, E, A](val r: R, val next: ZIO[R, E, A]) extends IO[E, A] {
    override def tag = Tags.Provide
  }
}
