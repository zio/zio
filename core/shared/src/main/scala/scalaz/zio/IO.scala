// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.annotation.switch
import scala.concurrent.duration._
import Errors._

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
 *  * **Pure Values** &mdash; `IO.point`
 *  * **Synchronous Effects** &mdash; `IO.sync`
 *  * **Asynchronous Effects** &mdash; `IO.async`
 *  * **Concurrent Effects** &mdash; `io.fork`
 *  * **Resource Effects** &mdash; `io.bracket`
 *
 * The concurrency model is based on *fibers*, a user-land lightweight thread,
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
sealed abstract class IO[+E, +A] { self =>

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
  final def bimap[E2, B](f: E => E2, g: A => B): IO[E2, B] = (self.tag: @switch) match {
    case IO.Tags.Point =>
      val io = self.asInstanceOf[IO.Point[A]]

      new IO.Point(() => g(io.value()))

    case IO.Tags.Strict =>
      val io = self.asInstanceOf[IO.Strict[A]]

      new IO.Strict(g(io.value))

    case IO.Tags.SyncEffect =>
      val io = self.asInstanceOf[IO.SyncEffect[A]]

      new IO.SyncEffect(() => g(io.effect()))

    case IO.Tags.Fail =>
      val io = self.asInstanceOf[IO.Fail[E]]

      new IO.Fail(f(io.error))

    case _ => new IO.Redeem(self, (e: E) => new IO.Fail(f(e)), (a: A) => new IO.Strict(g(a)))
  }

  /**
   * Creates a composite action that represents this action followed by another
   * one that may depend on the value produced by this one.
   *
   * {{{
   * val parsed = readFile("foo.txt").flatMap(file => parseFile(file))
   * }}}
   */
  final def flatMap[E1 >: E, B](f0: A => IO[E1, B]): IO[E1, B] = new IO.FlatMap(self, f0)

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
   *   a <- subtask.join
   * } yield a
   * }}}
   */
  final def fork: IO[Nothing, Fiber[E, A]] = new IO.Fork(this, None)

  /**
   * A more powerful version of `fork` that allows specifying a handler to be
   * invoked on any exceptions that are not handled by the forked fiber.
   */
  final def fork0(handler: List[Throwable] => IO[Nothing, Unit]): IO[Nothing, Fiber[E, A]] =
    new IO.Fork(this, Some(handler))

  /**
   * Executes both this action and the specified action in parallel,
   * returning a tuple of their results. If either individual action fails,
   * then the returned action will fail.
   *
   * TODO: Replace with optimized primitive.
   */
  final def par[E1 >: E, B](that: IO[E1, B]): IO[E1, (A, B)] = {
    val s: IO[Nothing, Either[E1, A]] = self.attempt
    s.raceWith[E1, Either[E1, A], Either[E1, B], (A, B)](that.attempt)(
      {
        case (Left(e), fiberb)  => fiberb.interrupt(TerminatedException(e)) *> IO.fail[E1](e)
        case (Right(a), fiberb) => IO.absolve[E1, B](fiberb.join).map((b: B) => (a, b))
      }, {
        case (Left(e), fibera)  => fibera.interrupt(TerminatedException(e)) *> IO.fail[E1](e)
        case (Right(b), fibera) => IO.absolve[E1, A](fibera.join).map((a: A) => (a, b))
      }
    )
  }

  /**
   * Races this action with the specified action, returning the first
   * result to produce an `A`, whichever it is. If neither action succeeds,
   * then the action will be terminated with some error.
   */
  final def race[E1 >: E, A1 >: A](that: IO[E1, A1]): IO[E1, A1] =
    raceWith(that)((a, fiber) => fiber.interrupt(LostRace(Right(fiber))).const(a),
                   (a, fiber) => fiber.interrupt(LostRace(Left(fiber))).const(a))

  /**
   * Races this action with the specified action, invoking the
   * specified finisher as soon as one value or the other has been computed.
   */
  final def raceWith[E1 >: E, A1 >: A, B, C](that: IO[E1, B])(finishLeft: (A1, Fiber[E1, B]) => IO[E1, C],
                                                              finishRight: (B, Fiber[E1, A1]) => IO[E1, C]): IO[E1, C] =
    new IO.Race[E1, A1, B, C](self, that, finishLeft, finishRight)

  /**
   * Executes this action and returns its value, if it succeeds, but
   * otherwise executes the specified action.
   */
  final def orElse[E1 >: E, A1 >: A](that: => IO[E1, A1]): IO[E1, A1] =
    self.redeem(_ => that, IO.now)

  /**
   * Maps over the error type. This can be used to lift a "smaller" error into
   * a "larger" error.
   */
  final def leftMap[E2](f: E => E2): IO[E2, A] =
    self.redeem[E2, A](f.andThen(IO.fail), IO.now)

  /**
   * Lets define separate continuations for the case of failure (`err`) or
   * success (`succ`). Executes this action and based on the result executes
   * the next action, `err` or `succ`.
   * This method is useful for recovering from `IO` actions that may fail
   * just as `attempt` is, but it has better performance since no intermediate
   * value is allocated and does not requiere subsequent calls
   * to `flatMap` to define the next action.
   *
   * The error parameter of the returned `IO` may be chosen arbitrarily, since
   * it will depend on the `IO`s returned by the given continuations.
   */
  final def redeem[E2, B](err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] =
    (self.tag: @switch) match {
      case IO.Tags.Fail =>
        val io = self.asInstanceOf[IO.Fail[E]]
        err(io.error)

      case _ => new IO.Redeem(self, err, succ)
    }

  /**
   * Less powerful version of `redeem` which always returns a successful
   * `IO[E2, B]` after applying one of the given mapping functions depending
   * on the result of `this` `IO`
   */
  final def redeemPure[E2, B](err: E => B, succ: A => B): IO[E2, B] =
    redeem(err.andThen(IO.now), succ.andThen(IO.now))

  /**
   * Executes this action, capturing both failure and success and returning
   * the result in an `Either`. This method is useful for recovering from
   * `IO` actions that may fail.
   *
   * The error parameter of the returned `IO` is Nothing, since
   * it is guaranteed the `IO` action does not raise any errors.
   */
  final def attempt: IO[Nothing, Either[E, A]] =
    self.redeem[Nothing, Either[E, A]](IO.nowLeft, IO.nowRight)

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
  final def bracket[E1 >: E, B](release: A => IO[Nothing, Unit])(use: A => IO[E1, B]): IO[E1, B] =
    IO.bracket[E1, A, B](this)(release)(use)

  /**
   * A more powerful version of `bracket` that provides information on whether
   * or not `use` succeeded to the release action.
   */
  final def bracket0[E1 >: E, B](
    release: (A, Option[Either[E1, B]]) => IO[Nothing, Unit]
  )(use: A => IO[E1, B]): IO[E1, B] =
    IO.bracket0[E1, A, B](this)(release)(use)

  /**
   * A less powerful variant of `bracket` where the value produced by this
   * action is not needed.
   */
  final def bracket_[E1 >: E, B](release: IO[Nothing, Unit])(use: IO[E1, B]): IO[E1, B] =
    IO.bracket[E1, A, B](self)(_ => release)(_ => use)

  /**
   * Executes the specified finalizer, whether this action succeeds, fails, or
   * is interrupted.
   */
  final def ensuring(finalizer: IO[Nothing, Unit]): IO[E, A] =
    new IO.Ensuring(self, finalizer)

  /**
   * Executes the release action only if there was an error.
   */
  final def bracketOnError[E1 >: E, B](release: A => IO[Nothing, Unit])(use: A => IO[E1, B]): IO[E1, B] =
    IO.bracket0[E1, A, B](this)(
      (a: A, eb: Option[Either[E1, B]]) =>
        eb match {
          case Some(Right(_)) => IO.unit
          case _              => release(a)
      }
    )(use)

  /**
   * Runs the cleanup action if this action errors, providing the error to the
   * cleanup action if it exists. The cleanup action will not be interrupted.
   */
  final def onError(cleanup: Option[E] => IO[Nothing, Unit]): IO[E, A] =
    IO.bracket0(IO.unit)(
      (_, eb: Option[Either[E, A]]) =>
        eb match {
          case Some(Right(_)) => IO.unit
          case Some(Left(e))  => cleanup(Some(e))
          case None           => cleanup(None)
      }
    )(_ => self)

  /**
   * Supervises this action, which ensures that any fibers that are forked by
   * the action are interrupted when this action completes.
   */
  final def supervised: IO[E, A] = new IO.Supervise(self)

  /**
   * Performs this action non-interruptibly. This will prevent the action from
   * being terminated externally, but the action may fail for internal reasons
   * (e.g. an uncaught error) or terminate due to defect.
   */
  final def uninterruptibly: IO[E, A] = new IO.Uninterruptible(self)

  /**
   * Recovers from all errors.
   *
   * {{{
   * openFile("config.json").catchAll(_ => IO.now(defaultConfig))
   * }}}
   */
  final def catchAll[E2, A1 >: A](h: E => IO[E2, A1]): IO[E2, A1] =
    self.redeem[E2, A1](h, IO.now)

  /**
   * Recovers from some or all of the error cases.
   *
   * {{{
   * openFile("data.json").catchSome {
   *   case FileNotFoundException(_) => openFile("backup.json")
   * }
   * }}}
   */
  final def catchSome[E1 >: E, A1 >: A](pf: PartialFunction[E1, IO[E1, A1]]): IO[E1, A1] = {
    def tryRescue(t: E1): IO[E1, A1] = pf.applyOrElse(t, (_: E1) => IO.fail(t))

    self.redeem[E1, A1](tryRescue, IO.now)
  }

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
   * Repeats this action forever (until the first error).
   */
  final def forever: IO[E, Nothing] = self *> self.forever

  /**
   * Retries continuously until this action succeeds.
   */
  final def retry: IO[E, A] = self orElse retry

  /**
   * Retries this action the specified number of times, until the first success.
   * Note that the action will always be run at least once, even if `n < 1`.
   */
  final def retryN(n: Int): IO[E, A] = retryBackoff(n, 1.0, Duration.fromNanos(0))

  /**
   * Retries continuously until the action succeeds or the specified duration
   * elapses.
   */
  final def retryFor[B](z: B)(f: A => B)(duration: Duration): IO[E, B] =
    retry.map(f).race(IO.sleep(duration) *> IO.now[B](z))

  /**
   * Retries continuously, increasing the duration between retries each time by
   * the specified multiplication factor, and stopping after the specified upper
   * limit on retries.
   */
  final def retryBackoff(n: Int, factor: Double, duration: Duration): IO[E, A] =
    if (n <= 1) self
    else self orElse (IO.sleep(duration) *> retryBackoff(n - 1, factor, duration * factor))

  /**
   * Repeats this action continuously until the first error, with the specified
   * interval between each full execution.
   */
  final def repeat[B](interval: Duration): IO[E, B] =
    self *> IO.sleep(interval) *> repeat(interval)

  /**
   * Repeats this action n time.
   */
  final def repeatN(n: Int): IO[E, A] = if (n <= 1) self else self *> repeatN(n - 1)

  /**
   * Repeats this action n time with a specified function, which computes
   * a return value.
   */
  final def repeatNFold[B](n: Int)(b: B, f: (B, A) => B): IO[E, B] =
    if (n < 1) IO.now(b) else self.flatMap(a => repeatNFold(n - 1)(f(b, a), f))

  /**
   * Repeats this action continuously until the first error, with the specified
   * interval between the start of each full execution. Note that if the
   * execution of this action takes longer than the specified interval, then the
   * action will instead execute as quickly as possible, but not
   * necessarily at the specified interval.
   */
  final def repeatFixed[B](interval: Duration): IO[E, B] =
    repeatFixed0(IO.sync(System.nanoTime()))(interval)

  final def repeatFixed0[B](nanoTime: IO[Nothing, Long])(interval: Duration): IO[E, B] = {
    val gapNs = interval.toNanos

    def tick(start: Long, n: Int): IO[E, B] =
      self *> nanoTime.flatMap { now =>
        val await = ((start + n * gapNs) - now).max(0L)

        IO.sleep(await.nanoseconds) *> tick(start, n + 1)
      }

    nanoTime.flatMap { start =>
      tick(start, 1)
    }
  }

  /**
   * Repeats this action continuously until the function returns false.
   */
  final def doWhile(f: A => Boolean): IO[E, A] =
    self.flatMap(a => if (f(a)) doWhile(f) else IO.now(a))

  /**
   * Repeats this action continuously until the function returns true.
   */
  final def doUntil(f: A => Boolean): IO[E, A] =
    self.flatMap(a => if (!f(a)) doUntil(f) else IO.now(a))

  /**
   * Maps this action to one producing unit, but preserving the effects of
   * this action.
   */
  final def toUnit: IO[E, Unit] = const(())

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
   * Times out this action by the specified duration.
   *
   * {{{
   * action.timeout(1.second)
   * }}}
   */
  final def timeout[B](z: B)(f: A => B)(duration: Duration): IO[E, B] = {
    val timer = IO.now[B](z)
    self.map(f).race(timer.delay(duration))
  }

  /**
   * Returns a new action that executes this one and times the execution.
   */
  final def timed: IO[E, (Duration, A)] = timed0(IO.sync(System.nanoTime()))

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
   * Runs this action in a new fiber, resuming when the fiber terminates.
   */
  final def run[E1 >: E, A1 >: A]: IO[Nothing, ExitResult[E1, A1]] = new IO.Run(self)

  /**
   * Widens the action type to any supertype. While `map` suffices for this
   * purpose, this method is significantly faster for this purpose.
   */
  def as[A1 >: A]: IO[E, A1] = self.asInstanceOf[IO[E, A1]]

  /**
   * An integer that identifies the term in the `IO` sum type to which this
   * instance belongs (e.g. `IO.Tags.Point`).
   */
  def tag: Int
}

object IO {

  @inline
  private final def nowLeft[E, A]: E => IO[Nothing, Either[E, A]] =
    _nowLeft.asInstanceOf[E => IO[Nothing, Either[E, A]]]

  private val _nowLeft: Any => IO[Any, Either[Any, Any]] =
    e2 => IO.now[Either[Any, Any]](Left(e2))

  @inline
  private final def nowRight[E, A]: A => IO[Nothing, Either[E, A]] =
    _nowRight.asInstanceOf[A => IO[Nothing, Either[E, A]]]

  private val _nowRight: Any => IO[Any, Either[Any, Any]] =
    a => IO.now[Either[Any, Any]](Right(a))

  final object Tags {
    final val FlatMap         = 0
    final val Point           = 1
    final val Strict          = 2
    final val SyncEffect      = 3
    final val Fail            = 4
    final val AsyncEffect     = 5
    final val AsyncIOEffect   = 6
    final val Redeem          = 7
    final val Fork            = 8
    final val Race            = 9
    final val Suspend         = 10
    final val Uninterruptible = 11
    final val Sleep           = 12
    final val Supervise       = 13
    final val Terminate       = 14
    final val Supervisor      = 15
    final val Run             = 16
    final val Ensuring        = 17
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

  final class SyncEffect[A] private[IO] (val effect: () => A) extends IO[Nothing, A] {
    override def tag = Tags.SyncEffect
  }

  final class Fail[E] private[IO] (val error: E) extends IO[E, Nothing] {
    override def tag = Tags.Fail
  }

  final class AsyncEffect[E, A] private[IO] (val register: (Callback[E, A]) => Async[E, A]) extends IO[E, A] {
    override def tag = Tags.AsyncEffect
  }

  final class AsyncIOEffect[E, A] private[IO] (val register: (Callback[E, A]) => IO[E, Unit]) extends IO[E, A] {
    override def tag = Tags.AsyncIOEffect
  }

  final class Redeem[E1, E2, A, B] private[IO] (val value: IO[E1, A],
                                                val err: E1 => IO[E2, B],
                                                val succ: A => IO[E2, B])
      extends IO[E2, B]
      with Function[A, IO[E2, B]] {

    override def tag = Tags.Redeem

    final def apply(v: A): IO[E2, B] = succ(v)
  }

  final class Fork[E, A] private[IO] (val value: IO[E, A], val handler: Option[List[Throwable] => IO[Nothing, Unit]])
      extends IO[Nothing, Fiber[E, A]] {
    override def tag = Tags.Fork
  }

  final class Race[E, A0, A1, A] private[IO] (val left: IO[E, A0],
                                              val right: IO[E, A1],
                                              val finishLeft: (A0, Fiber[E, A1]) => IO[E, A],
                                              val finishRight: (A1, Fiber[E, A0]) => IO[E, A])
      extends IO[E, A] {
    override def tag = Tags.Race
  }

  final class Suspend[E, A] private[IO] (val value: () => IO[E, A]) extends IO[E, A] {
    override def tag = Tags.Suspend
  }

  final class Uninterruptible[E, A] private[IO] (val io: IO[E, A]) extends IO[E, A] {
    override def tag = Tags.Uninterruptible
  }

  final class Sleep private[IO] (val duration: Duration) extends IO[Nothing, Unit] {
    override def tag = Tags.Sleep
  }

  final class Supervise[E, A] private[IO] (val value: IO[E, A]) extends IO[E, A] {
    override def tag = Tags.Supervise
  }

  final class Terminate private[IO] (val causes: List[Throwable]) extends IO[Nothing, Nothing] {
    override def tag = Tags.Terminate
  }

  final class Supervisor private[IO] () extends IO[Nothing, Throwable => IO[Nothing, Unit]] {
    override def tag = Tags.Supervisor
  }

  final class Run[E, A] private[IO] (val value: IO[E, A]) extends IO[Nothing, ExitResult[E, A]] {
    override def tag = Tags.Run
  }

  final class Ensuring[E, A] private[IO] (val io: IO[E, A], val finalizer: IO[Nothing, Unit]) extends IO[E, A] {
    override def tag = Tags.Ensuring
  }

  /**
   * Lifts a strictly evaluated value into the `IO` monad.
   */
  final def now[A](a: A): IO[Nothing, A] = new Strict(a)

  /**
   * Lifts a non-strictly evaluated value into the `IO` monad. Do not use this
   * function to capture effectful code. The result is undefined but may
   * include duplicated effects.
   */
  final def point[A](a: => A): IO[Nothing, A] = new Point(() => a)

  /**
   * Creates an `IO` value that represents failure with the specified error.
   * The moral equivalent of `throw` for pure code.
   */
  final def fail[E](error: E): IO[E, Nothing] = new Fail(error)

  /**
   * Strictly-evaluated unit lifted into the `IO` monad.
   */
  final val unit: IO[Nothing, Unit] = IO.now(())

  /**
   * Creates an `IO` value from `ExitResult`
   */
  final def done[E, A](r: ExitResult[E, A]): IO[E, A] = r match {
    case ExitResult.Completed(b)   => now(b)
    case ExitResult.Terminated(ts) => terminate0(ts)
    case ExitResult.Failed(e, _)   => fail(e)
  }

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  final def sleep(duration: Duration): IO[Nothing, Unit] = new Sleep(duration)

  /**
   * Supervises the specified action, which ensures that any actions directly
   * forked by the action are killed upon the action's own termination.
   */
  final def supervise[E, A](io: IO[E, A]): IO[E, A] = new Supervise(io)

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
  final def suspend[E, A](io: => IO[E, A]): IO[E, A] = new Suspend(() => io)

  /**
   * Terminates the fiber executing this action, running all finalizers.
   */
  final def terminate: IO[Nothing, Nothing] = terminate0(Nil)

  /**
   * Terminates the fiber executing this action with the specified error(s), running all finalizers.
   */
  final def terminate(t: Throwable, ts: Throwable*): IO[Nothing, Nothing] = terminate0(t :: ts.toList)

  /**
   * Terminates the fiber executing this action, running all finalizers.
   */
  final def terminate0(ts: List[Throwable]): IO[Nothing, Nothing] = new Terminate(ts)

  /**
   * Imports a synchronous effect into a pure `IO` value.
   *
   * {{{
   * val nanoTime: IO[Nothing, Long] = IO.sync(System.nanoTime())
   * }}}
   */
  final def sync[A](effect: => A): IO[Nothing, A] = new SyncEffect(() => effect)

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
   * Imports an asynchronous effect into a pure `IO` value. See `async0` for
   * the more expressive variant of this function.
   */
  final def async[E, A](register: (Callback[E, A]) => Unit): IO[E, A] =
    new AsyncEffect((callback: Callback[E, A]) => {
      register(callback)

      Async.later[E, A]
    })

  /**
   * Imports an asynchronous effect into a pure `IO` value. This formulation is
   * necessary when the effect is itself expressed in terms of `IO`.
   */
  final def asyncPure[E, A](register: (Callback[E, A]) => IO[E, Unit]): IO[E, A] = new AsyncIOEffect(register)

  /**
   * Imports an asynchronous effect into a pure `IO` value. The effect has the
   * option of returning the value synchronously, which is useful in cases
   * where it cannot be determined if the effect is synchronous or asynchronous
   * until the effect is actually executed. The effect also has the option of
   * returning a canceler, which will be used by the runtime to cancel the
   * asynchronous effect if the fiber executing the effect is interrupted.
   */
  final def async0[E, A](register: (Callback[E, A]) => Async[E, A]): IO[E, A] = new AsyncEffect(register)

  /**
   * Returns a action that will never produce anything. The moral
   * equivalent of `while(true) {}`, only without the wasted CPU cycles.
   */
  final val never: IO[Nothing, Nothing] =
    IO.async[Nothing, Nothing] { _ =>
      }

  /**
   * Submerges the error case of an `Either` into the `IO`. The inverse
   * operation of `IO.attempt`.
   */
  final def absolve[E, A](v: IO[E, Either[E, A]]): IO[E, A] =
    v.flatMap(fromEither)

  /**
   * Lifts an `Either` into an `IO`.
   */
  final def fromEither[E, A](v: Either[E, A]): IO[E, A] =
    v.fold(IO.fail, IO.now)

  /**
   * Retrieves the supervisor associated with the fiber running the action
   * returned by this method.
   */
  final def supervisor: IO[Nothing, Throwable => IO[Nothing, Unit]] = new Supervisor()

  /**
   * Requires that the given `IO[E, Option[A]]` contain a value. If there is no
   * value, then the specified error will be raised.
   */
  final def require[E, A](error: E): IO[E, Option[A]] => IO[E, A] =
    (io: IO[E, Option[A]]) => io.flatMap(_.fold[IO[E, A]](IO.fail[E](error))(IO.now[A]))

  final def forkAll[E, A](as: Iterable[IO[E, A]]): IO[Nothing, Fiber[E, List[A]]] =
    as.foldRight(IO.point(Fiber.point[E, List[A]](List()))) { (aIO, asFiberIO) =>
      asFiberIO.par(aIO.fork).map {
        case (asFiber, aFiber) =>
          asFiber.zipWith(aFiber)((as, a) => a :: as)
      }
    }

  /**
   * Acquires a resource, do some work with it, and then release that resource. With `bracket0`
   * not only is the acquired resource be cleaned up, the outcome of the computation is also
   * reified for processing.
   */
  final def bracket0[E, A, B](
    acquire: IO[E, A]
  )(release: (A, Option[Either[E, B]]) => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] =
    Ref[Option[(A, Option[Either[E, B]])]](None).flatMap { m =>
      (for {
        a <- acquire
              .flatMap(a => m.set(Some((a, None))).const(a))
              .uninterruptibly
        b <- use(a).attempt.flatMap(
              eb =>
                m.set(Some((a, Some(eb)))) *> (eb match {
                  case Right(b) => IO.now(b)
                  case Left(e)  => fail[E](e)
                })
            )
      } yield b).ensuring(m.get.flatMap(_.fold(unit) { case (a, r) => release(a, r) }))
    }

  /**
   * Acquires a resource, do some work with it, and then release that resource. `bracket`
   * will release the resource no matter the outcome of the computation, and will
   * re-throw any exception that occured in between.
   */
  final def bracket[E, A, B](
    acquire: IO[E, A]
  )(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] =
    Ref[Option[A]](None).flatMap { m =>
      (for {
        a <- acquire.flatMap(a => m.set(Some(a)).const(a)).uninterruptibly
        b <- use(a)
      } yield b).ensuring(m.get.flatMap(_.fold(unit)(release(_))))
    }

  /**
   * Apply the function fn to each element of the `Iterable[A]` and
   * return the results in a new `List[B]`. For parallelism use `parTraverse`.
   */
  final def traverse[E, A, B](in: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    in.foldRight[IO[E, List[B]]](IO.sync(Nil)) { (a, io) =>
      io.zipWith(fn(a))((bs, b) => b :: bs)
    }

  /**
   * Evaluate the elements of an `Iterable[A]` in parallel
   * and collect the results. This is the parallel version of `traverse`.
   */
  def parTraverse[E, A, B](as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    as.foldRight[IO[E, List[B]]](IO.sync(Nil)) { (a, io) =>
      io.par(fn(a)).map { case (bs, b) => b :: bs }
    }

  /**
   * Evaluate each effect in the structure from left to right, and collect
   * the results. For parallelism use `parAll`.
   */
  final def sequence[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    traverse(in)(identity)

  /**
   * Evaluate each effect in the structure in parallel, and collect
   * the results. This is the parallel version of `sequence`.
   */
  final def parAll[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    parTraverse(as)(identity)

  /**
   * Races an `Iterable[IO[E, A]]` against each other. If all of
   * them fail, the last error is returned.
   *
   * _Note_: if the collection is empty, there is no action that can either
   * succeed or fail. Therefore, the only possible output is an IO action
   * that never terminates.
   */
  final def raceAll[E, A](t: Iterable[IO[E, A]]): IO[E, A] =
    t.foldLeft[IO[E, A]](IO.terminate(NothingRaced))(_ race _)

  /**
   * Reduces an `Iterable[IO]` to a single IO, works in parallel.
   */
  final def reduceAll[E, A](a: IO[E, A], as: Iterable[IO[E, A]])(f: (A, A) => A): IO[E, A] =
    as.foldLeft(a) { (l, r) =>
      l.par(r).map(f.tupled)
    }

  /**
   * Merges an `Iterable[IO]` to a single IO, works in parallel.
   */
  final def mergeAll[E, A, B](in: Iterable[IO[E, A]])(zero: B, f: (B, A) => B): IO[E, B] =
    in.foldLeft[IO[E, B]](IO.point[B](zero))((acc, a) => acc.par(a).map(f.tupled))

}
