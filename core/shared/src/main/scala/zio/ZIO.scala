/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.internal.{FiberScope, Platform}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.IOException
import scala.annotation.implicitNotFound
import scala.collection.mutable.Builder
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success}
import izumi.reflect.macrortti.LightTypeTag

/**
 * A `ZIO[R, E, A]` value is an immutable value (called an "effect") that
 * describes an async, concurrent workflow. In order to be executed, the
 * workflow requires a value of type `ZEnvironment[R]`, and when executed, the
 * workflow will either produce a failure of type `E`, or a success of type `A`.
 *
 * ZIO effects may informally be thought of as functions of the following form:
 *
 * {{{
 * ZEnvironment[R] => Either[E, A]
 * }}}
 *
 * ZIO effects model resourceful interaction with the outside world, including
 * synchronous, asynchronous, concurrent, and parallel interaction.
 *
 * The async and concurrent operations of ZIO effects are powered by fibers,
 * which are lightweight, green threads that enable high scalability.
 *
 * To run an effect, you need a `Runtime`, which is capable of executing
 * effects. Runtimes bundle a thread pool together with the environment that
 * effects need.
 */
sealed trait ZIO[-R, +E, +A] extends Serializable with ZIOPlatformSpecific[R, E, A] with ZIOVersionSpecific[R, E, A] {
  self =>

  /**
   * Returns a new effect that applies the specified aspect to this effect.
   * Aspects are "transformers" that modify the behavior of their input in some
   * well-defined way (for example, adding a timeout).
   */
  final def @@[LowerR <: UpperR, UpperR <: R, LowerE >: E, UpperE >: LowerE, LowerA >: A, UpperA >: LowerA](
    aspect: => ZIOAspect[LowerR, UpperR, LowerE, UpperE, LowerA, UpperA]
  )(implicit trace: Trace): ZIO[UpperR, LowerE, LowerA] =
    ZIO.suspendSucceed(aspect(self))

  /**
   * A symbolic alias for `orDie`.
   */
  final def !(implicit ev1: E <:< Throwable, ev2: CanFail[E], trace: Trace): ZIO[R, Nothing, A] =
    self.orDie

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, returning result of provided effect. If either side fails,
   * then the other side will be interrupted.
   */
  final def &>[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
    self.zipWithPar(that)((_, b) => b)

  /**
   * A variant of `flatMap` that ignores the value produced by this effect.
   */
  final def *>[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
    self.flatMap(_ => that)

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, this effect result returned. If either side fails, then the
   * other side will be interrupted.
   */
  final def <&[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, A] =
    self.zipWithPar(that)((a, _) => a)

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, combining their results into a tuple. If either side fails,
   * then the other side will be interrupted.
   */
  final def <&>[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit
    zippable: Zippable[A, B],
    trace: Trace
  ): ZIO[R1, E1, zippable.Out] =
    self.zipWithPar(that)((a, b) => zippable.zip(a, b))

  /**
   * Sequences the specified effect after this effect, but ignores the value
   * produced by the effect.
   */
  final def <*[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, A] =
    self.flatMap(a => that.as(a))

  /**
   * Sequentially zips this effect with the specified effect, combining the
   * results into a tuple.
   */
  final def <*>[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit
    zippable: Zippable[A, B],
    trace: Trace
  ): ZIO[R1, E1, zippable.Out] =
    self zip that

  /**
   * A symbolic alias for `orElseEither`.
   */
  final def <+>[R1 <: R, E1, B](
    that: => ZIO[R1, E1, B]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, Either[A, B]] =
    self.orElseEither(that)

  /**
   * Operator alias for `orElse`.
   */
  final def <>[R1 <: R, E2, A1 >: A](
    that: => ZIO[R1, E2, A1]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, A1] =
    orElse(that)

  /**
   * A symbolic alias for `raceEither`.
   */
  final def <|>[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit
    trace: Trace
  ): ZIO[R1, E1, Either[A, B]] =
    self.raceEither(that)

  /**
   * Returns the logical negation of the `Boolean` value returned by this
   * effect.
   */
  final def unary_![R1 <: R, E1 >: E](implicit ev: A <:< Boolean, trace: Trace): ZIO[R1, E1, Boolean] =
    self.map(a => !ev(a))

  /**
   * Returns an effect that submerges the error case of an `Either` into the
   * `ZIO`. The inverse operation of `ZIO.either`.
   */
  final def absolve[E1 >: E, B](implicit ev: A IsSubtypeOfOutput Either[E1, B], trace: Trace): ZIO[R, E1, B] =
    ZIO.absolve(self.map(ev))

  /**
   * Attempts to convert defects into a failure, throwing away all information
   * about the cause of the failure.
   */
  final def absorb(implicit ev: E IsSubtypeOfError Throwable, trace: Trace): RIO[R, A] =
    absorbWith(ev)

  /**
   * Attempts to convert defects into a failure, throwing away all information
   * about the cause of the failure.
   */
  final def absorbWith(f: E => Throwable)(implicit trace: Trace): RIO[R, A] =
    self.sandbox
      .foldZIO(
        cause => ZIO.fail(cause.squashWith(f)),
        ZIO.succeedNow
      )

  /**
   * Maps the success value of this effect to the specified constant value.
   */
  final def as[B](b: => B)(implicit trace: Trace): ZIO[R, E, B] =
    self.map(_ => b)

  /**
   * Maps the success value of this effect to a left value.
   */
  final def asLeft(implicit trace: Trace): ZIO[R, E, Either[A, Nothing]] =
    map(Left(_))

  /**
   * Maps the error value of this effect to a left value.
   */
  final def asLeftError(implicit trace: Trace): ZIO[R, Either[E, Nothing], A] =
    mapError(Left(_))

  /**
   * Maps the success value of this effect to a right value.
   */
  final def asRight(implicit trace: Trace): ZIO[R, E, Either[Nothing, A]] =
    map(Right(_))

  /**
   * Maps the error value of this effect to a right value.
   */
  final def asRightError(implicit trace: Trace): ZIO[R, Either[Nothing, E], A] =
    mapError(Right(_))

  /**
   * Maps the success value of this effect to an optional value.
   */
  final def asSome(implicit trace: Trace): ZIO[R, E, Option[A]] =
    map(Some(_))

  /**
   * Maps the error value of this effect to an optional value.
   */
  final def asSomeError(implicit trace: Trace): ZIO[R, Option[E], A] =
    mapError(Some(_))

  /**
   * Returns a new effect that will not succeed with its value before first
   * waiting for the end of all child fibers forked by the effect.
   */
  final def awaitAllChildren(implicit trace: Trace): ZIO[R, E, A] =
    ensuringChildren(Fiber.awaitAll(_))

  /**
   * Returns an effect that, if evaluated, will return the cached result of this
   * effect. Cached results will expire after `timeToLive` duration.
   */
  final def cached(timeToLive: => Duration)(implicit trace: Trace): ZIO[R, Nothing, IO[E, A]] =
    cachedInvalidate(timeToLive).map(_._1)

  /**
   * Returns an effect that, if evaluated, will return the cached result of this
   * effect. Cached results will expire after `timeToLive` duration. In
   * addition, returns an effect that can be used to invalidate the current
   * cached value before the `timeToLive` duration expires.
   */
  final def cachedInvalidate(
    timeToLive0: => Duration
  )(implicit trace: Trace): ZIO[R, Nothing, (IO[E, A], UIO[Unit])] =
    ZIO.suspendSucceed {
      val timeToLive = timeToLive0

      def compute(start: Long): ZIO[R, Nothing, Option[(Long, Promise[E, A])]] =
        for {
          p <- Promise.make[E, A]
          _ <- self.intoPromise(p)
        } yield Some((start + timeToLive.toNanos, p))

      def get(cache: Ref.Synchronized[Option[(Long, Promise[E, A])]]): ZIO[R, E, A] =
        ZIO.uninterruptibleMask { restore =>
          Clock.nanoTime.flatMap { time =>
            cache.updateSomeAndGetZIO {
              case None                              => compute(time)
              case Some((end, _)) if end - time <= 0 => compute(time)
            }.flatMap(a => restore(a.get._2.await))
          }
        }

      def invalidate(cache: Ref.Synchronized[Option[(Long, Promise[E, A])]]): UIO[Unit] =
        cache.set(None)

      for {
        r     <- ZIO.environment[R]
        cache <- Ref.Synchronized.make[Option[(Long, Promise[E, A])]](None)
      } yield (get(cache).provideEnvironment(r), invalidate(cache))
    }

  /**
   * Recovers from all errors.
   *
   * {{{
   * openFile("config.json").catchAll(_ => ZIO.succeed(defaultConfig))
   * }}}
   */
  final def catchAll[R1 <: R, E2, A1 >: A](
    h: E => ZIO[R1, E2, A1]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, A1] =
    self.foldZIO[R1, E2, A1](h, a => ZIO.succeedNow(a))

  /**
   * A version of `catchAll` that gives you the (optional) trace of the error.
   */
  final def catchAllTrace[R1 <: R, E2, A1 >: A](
    h: ((E, StackTrace)) => ZIO[R1, E2, A1]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, A1] =
    self.foldTraceZIO[R1, E2, A1](h, a => ZIO.succeedNow(a))

  /**
   * Recovers from all errors with provided Cause.
   *
   * {{{
   * openFile("config.json").catchAllCause(_ => ZIO.succeed(defaultConfig))
   * }}}
   *
   * @see
   *   [[absorb]], [[sandbox]], [[mapErrorCause]] - other functions that can
   *   recover from defects
   */
  final def catchAllCause[R1 <: R, E2, A1 >: A](h: Cause[E] => ZIO[R1, E2, A1])(implicit
    trace: Trace
  ): ZIO[R1, E2, A1] =
    ZIO.OnFailure(trace, self, h)

  /**
   * Recovers from all defects with provided function.
   *
   * {{{
   * effect.catchSomeDefect(_ => backup())
   * }}}
   *
   * '''WARNING''': There is no sensible way to recover from defects. This
   * method should be used only at the boundary between ZIO and an external
   * system, to transmit information on a defect for diagnostic or explanatory
   * purposes.
   */
  final def catchAllDefect[R1 <: R, E1 >: E, A1 >: A](h: Throwable => ZIO[R1, E1, A1])(implicit
    trace: Trace
  ): ZIO[R1, E1, A1] =
    catchSomeDefect { case t => h(t) }

  /**
   * Recovers from all NonFatal Throwables.
   *
   * {{{
   * openFile("data.json").catchNonFatalOrDie(_ => openFile("backup.json"))
   * }}}
   */
  final def catchNonFatalOrDie[R1 <: R, E2, A1 >: A](
    h: E => ZIO[R1, E2, A1]
  )(implicit ev1: CanFail[E], ev2: E <:< Throwable, trace: Trace): ZIO[R1, E2, A1] = {

    def hh(e: E) =
      ZIO.isFatalWith(isFatal => if (isFatal(e)) ZIO.die(e) else h(e))
    self.foldZIO[R1, E2, A1](hh, ZIO.succeedNow)
  }

  /**
   * Recovers from some or all of the error cases.
   *
   * {{{
   * openFile("data.json").catchSome {
   *   case _: FileNotFoundException => openFile("backup.json")
   * }
   * }}}
   */
  final def catchSome[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[E, ZIO[R1, E1, A1]]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A1] = {
    def tryRescue(c: Cause[E]): ZIO[R1, E1, A1] =
      c.failureOrCause.fold(t => pf.applyOrElse(t, (_: E) => ZIO.failCause(c)), ZIO.failCause(_))

    self.foldCauseZIO[R1, E1, A1](tryRescue, a => ZIO.succeedNow(a))
  }

  /**
   * A version of `catchSome` that gives you the trace of the error.
   */
  final def catchSomeTrace[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[(E, StackTrace), ZIO[R1, E1, A1]]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A1] = {
    def tryRescue(c: Cause[E]): ZIO[R1, E1, A1] =
      c.failureTraceOrCause.fold(t => pf.applyOrElse(t, (_: (E, StackTrace)) => ZIO.failCause(c)), ZIO.failCause(_))

    self.foldCauseZIO[R1, E1, A1](tryRescue, a => ZIO.succeedNow(a))
  }

  /**
   * Recovers from some or all of the error cases with provided cause.
   *
   * {{{
   * openFile("data.json").catchSomeCause {
   *   case c if (c.interrupted) => openFile("backup.json")
   * }
   * }}}
   */
  final def catchSomeCause[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[Cause[E], ZIO[R1, E1, A1]]
  )(implicit trace: Trace): ZIO[R1, E1, A1] = {
    def tryRescue(c: Cause[E]): ZIO[R1, E1, A1] =
      pf.applyOrElse(c, (_: Cause[E]) => ZIO.failCause(c))

    self.foldCauseZIO[R1, E1, A1](tryRescue, a => ZIO.succeedNow(a))
  }

  /**
   * Recovers from some or all of the defects with provided partial function.
   *
   * {{{
   * effect.catchSomeDefect {
   *   case _: SecurityException => backup()
   * }
   * }}}
   *
   * '''WARNING''': There is no sensible way to recover from defects. This
   * method should be used only at the boundary between ZIO and an external
   * system, to transmit information on a defect for diagnostic or explanatory
   * purposes.
   */
  final def catchSomeDefect[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[Throwable, ZIO[R1, E1, A1]]
  )(implicit trace: Trace): ZIO[R1, E1, A1] =
    unrefineWith(pf)(ZIO.fail(_)).catchAll(identity)

  /**
   * Returns an effect that succeeds with the cause of failure of this effect,
   * or `Cause.empty` if the effect did succeed.
   */
  final def cause(implicit trace: Trace): URIO[R, Cause[E]] =
    self.foldCause(c => c, _ => Cause.empty)

  /**
   * Fail with `e` if the supplied `PartialFunction` does not match, otherwise
   * succeed with the returned value.
   */
  final def collect[E1 >: E, B](e: => E1)(pf: PartialFunction[A, B])(implicit trace: Trace): ZIO[R, E1, B] =
    collectZIO(e)(pf.andThen(ZIO.succeedNow(_)))

  /**
   * Fail with `e` if the supplied `PartialFunction` does not match, otherwise
   * continue with the returned value.
   */
  final def collectZIO[R1 <: R, E1 >: E, B](e: => E1)(pf: PartialFunction[A, ZIO[R1, E1, B]])(implicit
    trace: Trace
  ): ZIO[R1, E1, B] =
    self.flatMap(v => pf.applyOrElse[A, ZIO[R1, E1, B]](v, _ => ZIO.fail(e)))

  /**
   * Returns a new effect that will not supervise any fibers forked by this
   * effect.
   */
  final def daemonChildren(implicit trace: Trace): ZIO[R, E, A] =
    FiberRef.forkScopeOverride.locally(Some(FiberScope.global))(self)

  /**
   * Taps the effect, printing the result of calling `.toString` on the value.
   */
  final def debug(implicit trace: Trace): ZIO[R, E, A] =
    self
      .tap(value => ZIO.succeed(println(value)))
      .tapErrorCause(error => ZIO.succeed(println(s"<FAIL> $error")))

  /**
   * Taps the effect, printing the result of calling `.toString` on the value.
   * Prefixes the output with the given message.
   */
  final def debug(prefix: => String)(implicit trace: Trace): ZIO[R, E, A] =
    self
      .tap(value => ZIO.succeed(println(s"$prefix: $value")))
      .tapErrorCause(error => ZIO.succeed(println(s"<FAIL> $prefix: $error")))

  /**
   * Returns an effect that is delayed from this effect by the specified
   * [[zio.Duration]].
   */
  final def delay(duration: => Duration)(implicit trace: Trace): ZIO[R, E, A] =
    Clock.sleep(duration) *> self

  /**
   * Returns an effect that is always interruptible, but whose interruption will
   * be performed in the background.
   *
   * This method is useful to create "fast interrupting" effects. For example,
   * if you call this on an acquire release effect, then even if the effect is
   * "stuck" in acquire or release, its interruption will return immediately,
   * while the acquire / release are performed in the background.
   *
   * See timeout and race for other applications.
   */
  final def disconnect(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.uninterruptibleMask(restore =>
      ZIO.fiberIdWith(fiberId =>
        for {
          fiber <- restore(self).forkDaemon
          a     <- fiber.join.interruptible.onInterrupt(fiber.interruptAsFork(fiberId))
        } yield a
      )
    )

  /**
   * Returns an effect whose failure and success have been lifted into an
   * `Either`.The resulting effect cannot fail, because the failure case has
   * been exposed as part of the `Either` success case.
   *
   * This method is useful for recovering from `ZIO` effects that may fail.
   *
   * The error parameter of the returned `ZIO` is `Nothing`, since it is
   * guaranteed the `ZIO` effect does not model failure.
   */
  final def either(implicit ev: CanFail[E], trace: Trace): URIO[R, Either[E, A]] =
    self.foldZIO(ZIO.succeedLeft, ZIO.succeedRight)

  /**
   * Returns an effect that, if this effect _starts_ execution, then the
   * specified `finalizer` is guaranteed to begin execution, whether this effect
   * succeeds, fails, or is interrupted.
   *
   * For use cases that need access to the effect's result, see [[ZIO#onExit]].
   *
   * Finalizers offer very powerful guarantees, but they are low-level, and
   * should generally not be used for releasing resources. For higher-level
   * logic built on `ensuring`, see `ZIO#acquireReleaseWith`.
   */
  final def ensuring[R1 <: R](finalizer: => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).foldCauseZIO(
        cause1 =>
          finalizer
            .foldCauseZIO(cause2 => ZIO.refailCause(cause1 ++ cause2), _ => ZIO.refailCause(cause1)),
        a => finalizer.map(_ => a)
      )
    } // FIXME: This has to be interned to avoid this overhead

  /**
   * Acts on the children of this fiber (collected into a single fiber),
   * guaranteeing the specified callback will be invoked, whether or not this
   * effect succeeds.
   */
  final def ensuringChild[R1 <: R](f: Fiber[Any, Iterable[Any]] => ZIO[R1, Nothing, Any])(implicit
    trace: Trace
  ): ZIO[R1, E, A] =
    ensuringChildren(children => f(Fiber.collectAll(children)))

  /**
   * Acts on the children of this fiber, guaranteeing the specified callback
   * will be invoked, whether or not this effect succeeds.
   */
  def ensuringChildren[R1 <: R](
    children: Chunk[Fiber.Runtime[Any, Any]] => ZIO[R1, Nothing, Any]
  )(implicit trace: Trace): ZIO[R1, E, A] =
    Supervisor
      .track(true)
      .flatMap(supervisor => self.supervised(supervisor).ensuring(supervisor.value.flatMap(children)))

  /**
   * Returns an effect that ignores errors and runs repeatedly until it
   * eventually succeeds.
   */
  final def eventually(implicit ev: CanFail[E], trace: Trace): URIO[R, A] =
    self <> ZIO.yieldNow *> eventually

  /**
   * Returns an effect that semantically runs the effect on a fiber, producing
   * an [[zio.Exit]] for the completion value of the fiber.
   */
  final def exit(implicit trace: Trace): URIO[R, Exit[E, A]] =
    self.foldCause(Exit.failCause(_), Exit.succeed(_))

  /**
   * Maps this effect to the default exit codes.
   */
  final def exitCode(implicit trace: Trace): URIO[R, ExitCode] =
    self.foldCause(
      _ => ExitCode.failure,
      _ => ExitCode.success
    )

  /**
   * Dies with specified `Throwable` if the predicate fails.
   */
  final def filterOrDie(p: A => Boolean)(t: => Throwable)(implicit trace: Trace): ZIO[R, E, A] =
    self.filterOrElse(p)(ZIO.die(t))

  /**
   * Dies with a [[java.lang.RuntimeException]] having the specified text
   * message if the predicate fails.
   */
  final def filterOrDieMessage(p: A => Boolean)(message: => String)(implicit trace: Trace): ZIO[R, E, A] =
    self.filterOrElse(p)(ZIO.dieMessage(message))

  /**
   * Dies with `t` if the predicate fails.
   */
  final def filterOrDieWith(p: A => Boolean)(t: A => Throwable)(implicit trace: Trace): ZIO[R, E, A] =
    self.filterOrElseWith(p)(a => ZIO.die(t(a)))

  /**
   * Supplies `zio` if the predicate fails.
   */
  final def filterOrElse[R1 <: R, E1 >: E, A1 >: A](p: A => Boolean)(zio: => ZIO[R1, E1, A1])(implicit
    trace: Trace
  ): ZIO[R1, E1, A1] =
    filterOrElseWith[R1, E1, A1](p)(_ => zio)

  /**
   * Applies `f` if the predicate fails.
   */
  final def filterOrElseWith[R1 <: R, E1 >: E, A1 >: A](
    p: A => Boolean
  )(f: A => ZIO[R1, E1, A1])(implicit trace: Trace): ZIO[R1, E1, A1] =
    self.flatMap {
      case v if !p(v) => f(v)
      case v          => ZIO.succeedNow(v)
    }

  /**
   * Fails with `e` if the predicate fails.
   */
  final def filterOrFail[E1 >: E](p: A => Boolean)(e: => E1)(implicit trace: Trace): ZIO[R, E1, A] =
    filterOrElse[R, E1, A](p)(ZIO.fail(e))

  /**
   * Returns an effect that runs this effect and in case of failure, runs each
   * of the specified effects in order until one of them succeeds.
   */
  final def firstSuccessOf[R1 <: R, E1 >: E, A1 >: A](rest: => Iterable[ZIO[R1, E1, A1]])(implicit
    trace: Trace
  ): ZIO[R1, E1, A1] =
    ZIO.firstSuccessOf(self, rest)

  /**
   * Returns an effect that models the execution of this effect, followed by the
   * passing of its value to the specified continuation function `k`, followed
   * by the effect that it returns.
   *
   * {{{
   * val parsed = readFile("foo.txt").flatMap(file => parseFile(file))
   * }}}
   */
  def flatMap[R1 <: R, E1 >: E, B](k: A => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
    ZIO.OnSuccess(trace, self, k)

  /**
   * Creates a composite effect that represents this effect followed by another
   * one that may depend on the error produced by this one.
   *
   * {{{
   * val parsed = readFile("foo.txt").flatMapError(error => logErrorToFile(error))
   * }}}
   */
  final def flatMapError[R1 <: R, E2](
    f: E => URIO[R1, E2]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, A] =
    flipWith(_ flatMap f)

  /**
   * Returns an effect that performs the outer effect first, followed by the
   * inner effect, yielding the value of the inner effect.
   *
   * This method can be used to "flatten" nested effects.
   */
  final def flatten[R1 <: R, E1 >: E, B](implicit
    ev1: A IsSubtypeOfOutput ZIO[R1, E1, B],
    trace: Trace
  ): ZIO[R1, E1, B] =
    self.flatMap(a => ev1(a))

  /**
   * Returns an effect that swaps the error/success cases. This allows you to
   * use all methods on the error channel, possibly before flipping back.
   */
  final def flip(implicit trace: Trace): ZIO[R, A, E] =
    self.foldZIO(ZIO.succeedNow, ZIO.fail(_))

  /**
   * Swaps the error/value parameters, applies the function `f` and flips the
   * parameters back
   */
  final def flipWith[R1, A1, E1](f: ZIO[R, A, E] => ZIO[R1, A1, E1])(implicit trace: Trace): ZIO[R1, E1, A1] =
    f(self.flip).flip

  /**
   * Folds over the failure value or the success value to yield an effect that
   * does not fail, but succeeds with the value returned by the left or right
   * function passed to `fold`.
   */
  final def fold[B](failure: E => B, success: A => B)(implicit ev: CanFail[E], trace: Trace): URIO[R, B] =
    foldZIO(e => ZIO.succeedNow(failure(e)), a => ZIO.succeedNow(success(a)))

  /**
   * A more powerful version of `fold` that allows recovering from any kind of
   * failure except interruptions.
   */
  final def foldCause[B](failure: Cause[E] => B, success: A => B)(implicit trace: Trace): URIO[R, B] =
    foldCauseZIO(c => ZIO.succeedNow(failure(c)), a => ZIO.succeedNow(success(a)))

  /**
   * A more powerful version of `foldZIO` that allows recovering from any kind
   * of failure except interruptions.
   */
  final def foldCauseZIO[R1 <: R, E2, B](
    failure: Cause[E] => ZIO[R1, E2, B],
    success: A => ZIO[R1, E2, B]
  )(implicit trace: Trace): ZIO[R1, E2, B] =
    ZIO.OnSuccessAndFailure(trace, self, success, failure)

  /**
   * A version of `foldZIO` that gives you the trace of the error.
   */
  final def foldTraceZIO[R1 <: R, E2, B](
    failure: ((E, StackTrace)) => ZIO[R1, E2, B],
    success: A => ZIO[R1, E2, B]
  )(implicit
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R1, E2, B] =
    foldCauseZIO(c => c.failureTraceOrCause.fold(failure, ZIO.failCause(_)), success)

  /**
   * Recovers from errors by accepting one effect to execute for the case of an
   * error, and one effect to execute for the case of success.
   *
   * This method has better performance than `either` since no intermediate
   * value is allocated and does not require subsequent calls to `flatMap` to
   * define the next effect.
   *
   * The error parameter of the returned `IO` may be chosen arbitrarily, since
   * it will depend on the `IO`s returned by the given continuations.
   */
  final def foldZIO[R1 <: R, E2, B](failure: E => ZIO[R1, E2, B], success: A => ZIO[R1, E2, B])(implicit
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R1, E2, B] =
    foldCauseZIO(c => c.failureOrCause.fold(failure, ZIO.failCause(_)), success)

  /**
   * Returns a new effect that will pass the success value of this effect to the
   * provided callback. If this effect fails, then the failure will be ignored.
   */
  final def forEachZIO[R1 <: R, E2, B](f: A => ZIO[R1, E2, B])(implicit trace: Trace): ZIO[R1, E2, Option[B]] =
    self.foldCauseZIO(_ => ZIO.none, a => f(a).map(Some(_)))

  /**
   * Repeats this effect forever (until the first error). For more sophisticated
   * schedules, see the `repeat` method.
   */
  final def forever(implicit trace: Trace): ZIO[R, E, Nothing] = {
    lazy val loop: ZIO[R, E, Nothing] = self *> ZIO.yieldNow *> loop

    loop
  }

  /**
   * Returns an effect that forks this effect into its own separate fiber,
   * returning the fiber immediately, without waiting for it to begin executing
   * the effect.
   *
   * You can use the `fork` method whenever you want to execute an effect in a
   * new fiber, concurrently and without "blocking" the fiber executing other
   * effects. Using fibers can be tricky, so instead of using this method
   * directly, consider other higher-level methods, such as `raceWith`,
   * `zipPar`, and so forth.
   *
   * The fiber returned by this method has methods to interrupt the fiber and to
   * wait for it to finish executing the effect. See [[zio.Fiber]] for more
   * information.
   *
   * Whenever you use this method to launch a new fiber, the new fiber is
   * attached to the parent fiber's scope. This means when the parent fiber
   * terminates, the child fiber will be terminated as well, ensuring that no
   * fibers leak. This behavior is called "auto supervision", and if this
   * behavior is not desired, you may use the [[forkDaemon]] or [[forkIn]]
   * methods.
   *
   * {{{
   * for {
   *   fiber <- subtask.fork
   *   // Do stuff...
   *   a <- fiber.join
   * } yield a
   * }}}
   */
  final def fork(implicit trace: Trace): URIO[R, Fiber.Runtime[E, A]] =
    ZIO.unsafeStateful[R, Nothing, Fiber.Runtime[E, A]]((fiberState, status) =>
      ZIO.succeed(ZIO.unsafeFork(trace, self, fiberState, status.runtimeFlags))
    )

  /**
   * Forks the effect in the specified scope. The fiber will be interrupted when
   * the scope is closed.
   */
  final def forkIn(scope: => Scope)(implicit trace: Trace): URIO[R, Fiber.Runtime[E, A]] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).forkDaemon.tap(fiber => scope.addFinalizer(fiber.interrupt))
    }

  /**
   * Forks the effect into a new fiber attached to the global scope. Because the
   * new fiber is attached to the global scope, when the fiber executing the
   * returned effect terminates, the forked fiber will continue running.
   */
  final def forkDaemon(implicit trace: Trace): URIO[R, Fiber.Runtime[E, A]] =
    self.fork.daemonChildren

  /**
   * Forks the fiber in a [[Scope]], interrupting it when the scope is closed.
   */
  final def forkScoped(implicit trace: Trace): ZIO[R with Scope, Nothing, Fiber.Runtime[E, A]] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).forkDaemon.tap(fiber => ZIO.addFinalizer(fiber.interrupt))
    }

  /**
   * Like fork but handles an error with the provided handler.
   */
  final def forkWithErrorHandler[R1 <: R](handler: E => URIO[R1, Any])(implicit
    trace: Trace
  ): URIO[R1, Fiber.Runtime[E, A]] =
    onError(c => c.failureOrCause.fold(handler, ZIO.failCause(_))).fork

  /**
   * Unwraps the optional error, defaulting to the provided value.
   */
  final def flattenErrorOption[E1, E2 <: E1](default: => E2)(implicit
    ev: E IsSubtypeOfError Option[E1],
    trace: Trace
  ): ZIO[R, E1, A] =
    self.mapError(e => ev(e).getOrElse(default))

  /**
   * Returns a successful effect with the head of the list if the list is
   * non-empty or fails with the error `None` if the list is empty.
   */
  final def head[B](implicit ev: A IsSubtypeOfOutput List[B], trace: Trace): ZIO[R, Option[E], B] =
    self.foldZIO(
      e => ZIO.fail(Some(e)),
      a => ev(a).headOption.fold[ZIO[R, Option[E], B]](ZIO.fail(None))(ZIO.succeedNow)
    )

  /**
   * Returns a new effect that ignores the success or failure of this effect.
   */
  final def ignore(implicit trace: Trace): URIO[R, Unit] =
    self.fold(ZIO.unitFn, ZIO.unitFn)

  /**
   * Returns a new effect that ignores the success or failure of this effect,
   * but which also logs failures at the Debug level, just in case the failure
   * turns out to be important.
   */
  final def ignoreLogged(implicit trace: Trace): URIO[R, Unit] =
    self.foldCause(
      cause =>
        ZIO.logLevel(LogLevel.Debug) {
          ZIO.logCause("An error was silently ignored because it is not anticipated to be useful", cause)
        },
      ZIO.unitFn
    )

  /**
   * Returns a new effect that will not succeed with its value before first
   * interrupting all child fibers forked by the effect.
   */
  final def interruptAllChildren(implicit trace: Trace): ZIO[R, E, A] =
    ensuringChildren(Fiber.interruptAll(_))

  /**
   * Returns a new effect that performs the same operations as this effect, but
   * interruptibly, even if composed inside of an uninterruptible region.
   *
   * Note that effects are interruptible by default, so this function only has
   * meaning if used within an uninterruptible region.
   *
   * WARNING: This operator "punches holes" into effects, allowing them to be
   * interrupted in unexpected places. Do not use this operator unless you know
   * exactly what you are doing. Instead, you should use
   * [[ZIO.uninterruptibleMask]].
   */
  final def interruptible(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.UpdateRuntimeFlagsWithin.Interruptible(trace, self)

  /**
   * Switches the interrupt status for this effect. If `true` is used, then the
   * effect becomes interruptible (the default), while if `false` is used, then
   * the effect becomes uninterruptible. These changes are compositional, so
   * they only affect regions of the effect.
   */
  final def interruptStatus(flag: => InterruptStatus)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed {
      if (flag.isInterruptible) self.interruptible
      else self.uninterruptible
    }

  /**
   * Returns an effect that keeps or breaks a promise based on the result of
   * this effect. Synchronizes interruption, so if this effect is interrupted,
   * the specified promise will be interrupted, too.
   */
  final def intoPromise[E1 >: E, A1 >: A](p: => Promise[E1, A1])(implicit trace: Trace): URIO[R, Boolean] =
    ZIO.uninterruptibleMask(restore => restore(self).exit.flatMap(p.done(_)))

  /**
   * Returns whether this effect is a failure.
   */
  final def isFailure(implicit trace: Trace): URIO[R, Boolean] =
    fold(_ => true, _ => false)

  /**
   * Returns whether this effect is a success.
   */
  final def isSuccess(implicit trace: Trace): URIO[R, Boolean] =
    fold(_ => false, _ => true)

  /**
   * "Zooms in" on the value in the `Left` side of an `Either`, moving the
   * possibility that the value is a `Right` to the error channel.
   */
  final def left[B, C](implicit ev: A IsSubtypeOfOutput Either[B, C], trace: Trace): ZIO[R, Either[E, C], B] =
    self.foldZIO(
      e => ZIO.fail(Left(e)),
      a => ev(a).fold(b => ZIO.succeedNow(b), c => ZIO.fail(Right(c)))
    )

  /**
   * Performs the specified operation while "zoomed in" on the `Left` case of an
   * `Either`.
   */
  final def leftWith[R1, E1, A1, B, B1, C, C1](
    f: ZIO[R, Either[E, C], B] => ZIO[R1, Either[E1, C1], B1]
  )(implicit ev: A IsSubtypeOfOutput Either[B, C], trace: Trace): ZIO[R1, E1, Either[B1, C1]] =
    f(self.left).unleft

  /**
   * Adjusts the label for the current logging span.
   * {{{
   * parseRequest(req).logSpan("parsing")
   * }}}
   */
  def logSpan(label: => String)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.logSpan(label)(self)

  /**
   * Returns an effect whose success is mapped by the specified `f` function.
   */
  def map[B](f: A => B)(implicit trace: Trace): ZIO[R, E, B] =
    flatMap(a => ZIO.succeedNow(f(a)))

  /**
   * Returns an effect whose success is mapped by the specified side effecting
   * `f` function, translating any thrown exceptions into typed failed effects.
   */
  final def mapAttempt[B](f: A => B)(implicit ev: E IsSubtypeOfError Throwable, trace: Trace): RIO[R, B] =
    foldZIO(e => ZIO.fail(ev(e)), a => ZIO.attempt(f(a)))

  /**
   * Returns an effect whose failure and success channels have been mapped by
   * the specified pair of functions, `f` and `g`.
   */
  final def mapBoth[E2, B](f: E => E2, g: A => B)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E2, B] =
    foldZIO(e => ZIO.fail(f(e)), a => ZIO.succeedNow(g(a)))

  /**
   * Returns an effect with its error channel mapped using the specified
   * function. This can be used to lift a "smaller" error into a "larger" error.
   */
  final def mapError[E2](f: E => E2)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E2, A] =
    self.foldCauseZIO(c => c.failureOrCause.fold(e => ZIO.fail(f(e)), ZIO.failCause(_)), a => ZIO.succeedNow(a))

  /**
   * Returns an effect with its full cause of failure mapped using the specified
   * function. This can be used to transform errors while preserving the
   * original structure of `Cause`.
   *
   * @see
   *   [[absorb]], [[sandbox]], [[catchAllCause]] - other functions for dealing
   *   with defects
   */
  final def mapErrorCause[E2](h: Cause[E] => Cause[E2])(implicit trace: Trace): ZIO[R, E2, A] =
    self.foldCauseZIO(c => ZIO.failCause(h(c)), a => ZIO.succeedNow(a))

  /**
   * Returns an effect that, if evaluated, will return the lazily computed
   * result of this effect.
   */
  final def memoize(implicit trace: Trace): UIO[ZIO[R, E, A]] =
    for {
      promise  <- Promise.make[E, A]
      complete <- self.intoPromise(promise).once
    } yield complete *> promise.await

  /**
   * Returns a new effect where the error channel has been merged into the
   * success channel to their common combined type.
   */
  final def merge[A1 >: A](implicit ev1: E IsSubtypeOfError A1, ev2: CanFail[E], trace: Trace): URIO[R, A1] =
    self.foldZIO(e => ZIO.succeedNow(ev1(e)), ZIO.succeedNow)

  /**
   * Returns a new effect where boolean value of this effect is negated.
   */
  final def negate(implicit ev: A IsSubtypeOfOutput Boolean, trace: Trace): ZIO[R, E, Boolean] =
    map(result => !ev(result))

  /**
   * Requires the option produced by this value to be `None`.
   */
  final def none[B](implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R, Option[E], Unit] =
    self.foldZIO(
      e => ZIO.fail(Some(e)),
      a => ev(a).fold[ZIO[R, Option[E], Unit]](ZIO.succeedNow(()))(_ => ZIO.fail(None))
    )

  /**
   * Returns an effect that will be executed at most once, even if it is
   * evaluated multiple times.
   */
  final def once(implicit trace: Trace): UIO[ZIO[R, E, Unit]] =
    Ref.make(true).map(ref => self.whenZIO(ref.getAndSet(false)).unit)

  final def onDone[R1 <: R](
    error: E => ZIO[R1, Nothing, Any],
    success: A => ZIO[R1, Nothing, Any]
  )(implicit trace: Trace): ZIO[R1, Nothing, Unit] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).foldZIO(e => restore(error(e)), s => restore(success(s))).forkDaemon.unit
    }

  final def onDoneCause[R1 <: R](
    error: Cause[E] => ZIO[R1, Nothing, Any],
    success: A => ZIO[R1, Nothing, Any]
  )(implicit trace: Trace): ZIO[R1, Nothing, Unit] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).foldCauseZIO(e => restore(error(e)), s => restore(success(s))).forkDaemon.unit
    }

  /**
   * Runs the specified effect if this effect fails, providing the error to the
   * effect if it exists. The provided effect will not be interrupted.
   */
  final def onError[R1 <: R](cleanup: Cause[E] => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    onExit {
      case Exit.Success(_)     => ZIO.unit
      case Exit.Failure(cause) => cleanup(cause)
    }

  /**
   * Returns an effect which is guaranteed to be executed on the specified
   * executor. The specified effect will always run on the specified executor,
   * even in the presence of asynchronous boundaries.
   *
   * This is useful when an effect must be executed somewhere, for example: on a
   * UI thread, inside a client library's thread pool, inside a blocking thread
   * pool, inside a low-latency thread pool, or elsewhere.
   *
   * The `onExecutor` function composes with the innermost `onExecutor` taking
   * priority. Use of this method does not alter the execution semantics of
   * other effects composed with this one, making it easy to compositionally
   * reason about where effects are running.
   */
  final def onExecutor(executor: => Executor)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.onExecutor(executor)(self)

  /**
   * Executes the effect on the specified `ExecutionContext` and then shifts
   * back to the default one.
   */
  final def onExecutionContext(ec: => ExecutionContext)(implicit trace: Trace): ZIO[R, E, A] =
    self.onExecutor(Executor.fromExecutionContext(Int.MaxValue)(ec))

  /**
   * Ensures that a cleanup functions runs, whether this effect succeeds, fails,
   * or is interrupted.
   */
  final def onExit[R1 <: R](cleanup: Exit[E, A] => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.acquireReleaseExitWith(ZIO.unit)((_, exit: Exit[E, A]) => cleanup(exit))(_ => self)

  /**
   * Runs the specified effect if this effect is interrupted.
   */
  final def onInterrupt[R1 <: R](cleanup: => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    onInterrupt(_ => cleanup)

  /**
   * Calls the specified function, and runs the effect it returns, if this
   * effect is interrupted.
   */
  final def onInterrupt[R1 <: R](cleanup: Set[FiberId] => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).foldCauseZIO(
        cause =>
          if (cause.isInterrupted) cleanup(cause.interruptors) *> ZIO.refailCause(cause) else ZIO.refailCause(cause),
        a => ZIO.succeedNow(a)
      )
    }

  /**
   * Runs the specified effect if this effect is terminated, either because of a
   * defect or because of interruption.
   */
  final def onTermination[R1 <: R](
    cleanup: Cause[Nothing] => URIO[R1, Any]
  )(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.acquireReleaseExitWith(ZIO.unit)((_, eb: Exit[E, A]) =>
      eb match {
        case Exit.Failure(cause) => cause.failureOrCause.fold(_ => ZIO.unit, cleanup)
        case _                   => ZIO.unit
      }
    )(_ => self)

  /**
   * Executes this effect, skipping the error but returning optionally the
   * success.
   */
  final def option(implicit ev: CanFail[E], trace: Trace): URIO[R, Option[A]] =
    self.foldZIO(_ => ZIO.succeedNow(None), a => ZIO.succeedNow(Some(a)))

  /**
   * Translates effect failure into death of the fiber, making all failures
   * unchecked and not a part of the type of the effect.
   */
  final def orDie(implicit ev1: E IsSubtypeOfError Throwable, ev2: CanFail[E], trace: Trace): URIO[R, A] =
    orDieWith(ev1)

  /**
   * Keeps none of the errors, and terminates the fiber with them, using the
   * specified function to convert the `E` into a `Throwable`.
   */
  final def orDieWith(f: E => Throwable)(implicit ev: CanFail[E], trace: Trace): URIO[R, A] =
    self.foldZIO(e => ZIO.die(f(e)), ZIO.succeedNow)

  /**
   * Unearth the unchecked failure of the effect. (opposite of `orDie`)
   * {{{
   *   val f0: Task[Unit] = ZIO.fail(new Exception("failing")).unit
   *   val f1: UIO[Unit]  = f0.orDie
   *   val f2: Task[Unit] = f1.resurrect
   * }}}
   */
  final def resurrect(implicit ev1: E IsSubtypeOfError Throwable, trace: Trace): RIO[R, A] =
    self.unrefineWith { case e => e }(ev1)

  /**
   * Executes this effect and returns its value, if it succeeds, but otherwise
   * executes the specified effect.
   */
  final def orElse[R1 <: R, E2, A1 >: A](
    that: => ZIO[R1, E2, A1]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, A1] =
    tryOrElse(that, a => ZIO.succeedNow(a))

  /**
   * Returns an effect that will produce the value of this effect, unless it
   * fails, in which case, it will produce the value of the specified effect.
   */
  final def orElseEither[R1 <: R, E2, B](
    that: => ZIO[R1, E2, B]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, Either[A, B]] =
    tryOrElse(that.map(Right(_)), ZIO.succeedLeft)

  /**
   * Executes this effect and returns its value, if it succeeds, but otherwise
   * fails with the specified error.
   */
  final def orElseFail[E1](e1: => E1)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E1, A] =
    orElse(ZIO.fail(e1))

  /**
   * Returns an effect that will produce the value of this effect, unless it
   * fails with the `None` value, in which case it will produce the value of the
   * specified effect.
   */
  final def orElseOptional[R1 <: R, E1, A1 >: A](
    that: => ZIO[R1, Option[E1], A1]
  )(implicit ev: E IsSubtypeOfError Option[E1], trace: Trace): ZIO[R1, Option[E1], A1] =
    catchAll(ev(_).fold(that)(e => ZIO.fail(Some(e))))

  /**
   * Executes this effect and returns its value, if it succeeds, but otherwise
   * succeeds with the specified value.
   */
  final def orElseSucceed[A1 >: A](a1: => A1)(implicit ev: CanFail[E], trace: Trace): URIO[R, A1] =
    orElse(ZIO.succeedNow(a1))

  /**
   * Exposes all parallel errors in a single call
   */
  final def parallelErrors[E1 >: E](implicit trace: Trace): ZIO[R, ::[E1], A] =
    self.foldCauseZIO(
      cause =>
        cause.failures match {
          case Nil            => ZIO.failCause(cause.asInstanceOf[Cause[Nothing]])
          case ::(head, tail) => ZIO.fail(::(head, tail))
        },
      ZIO.succeedNow
    )

  /**
   * Returns a new scoped workflow that runs finalizers added to the scope of
   * this workflow in parallel.
   */
  final def parallelFinalizers(implicit trace: Trace): ZIO[R with Scope, E, A] =
    ZIO.parallelFinalizers(self)

  /**
   * Provides the `ZIO` effect with its required environment, which eliminates
   * its dependency on `R`.
   */
  final def provideEnvironment(r: => ZEnvironment[R])(implicit trace: Trace): IO[E, A] =
    FiberRef.currentEnvironment.locallyWith(_.unionAll(r))(self.asInstanceOf[ZIO[Any, E, A]])

  /**
   * Provides a layer to the ZIO effect, which translates it to another level.
   */
  final def provideLayer[E1 >: E, R0](
    layer: => ZLayer[R0, E1, R]
  )(implicit trace: Trace): ZIO[R0, E1, A] =
    ZIO.acquireReleaseExitWith(Scope.make)((scope: Scope.Closeable, exit: Exit[Any, Any]) => scope.close(exit)) {
      scope =>
        layer.build(scope).flatMap(r => self.provideEnvironment(r))
    }

  /**
   * Transforms the environment being provided to this effect with the specified
   * function.
   */
  final def provideSomeEnvironment[R0](
    f: ZEnvironment[R0] => ZEnvironment[R]
  )(implicit trace: Trace): ZIO[R0, E, A] =
    ZIO.environmentWithZIO(r0 => self.provideEnvironment(f(r0)))

  /**
   * Splits the environment into two parts, providing one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val zio: ZIO[Logging with Database, Nothing, Unit] = ???
   *
   * val loggingLayer: ZLayer[Any, Nothing, Logging] = ???
   *
   * val zio2 = zio.provideSomeLayer[Database](loggingLayer)
   * }}}
   */
  final def provideSomeLayer[R0]: ZIO.ProvideSomeLayer[R0, R, E, A] =
    new ZIO.ProvideSomeLayer[R0, R, E, A](self)

  /**
   * Returns an effect that races this effect with the specified effect,
   * returning the first successful `A` from the faster side. If one effect
   * succeeds, the other will be interrupted. If neither succeeds, then the
   * effect will fail with some error.
   *
   * Note that both effects are disconnected before being raced. This means that
   * interruption of the loser will always be performed in the background. This
   * is a change in behavior compared to ZIO 2.0. If this behavior is not
   * desired, you can use [[ZIO#raceWith]], which will not disconnect or
   * interrupt losers.
   */
  final def race[R1 <: R, E1 >: E, A1 >: A](that: => ZIO[R1, E1, A1])(implicit trace: Trace): ZIO[R1, E1, A1] =
    ZIO.fiberIdWith { parentFiberId =>
      (self.disconnect.raceWith(that.disconnect))(
        (exit, right) =>
          exit.foldZIO[Any, E1, A1](
            cause => right.join.mapErrorCause(cause && _),
            a => right.interruptAs(parentFiberId).as(a)
          ),
        (exit, left) =>
          exit.foldZIO[Any, E1, A1](
            cause => left.join.mapErrorCause(_ && cause),
            a => left.interruptAs(parentFiberId).as(a)
          )
      )
    }

  /**
   * Returns an effect that races this effect with all the specified effects,
   * yielding the value of the first effect to succeed with a value. Losers of
   * the race will be interrupted immediately
   */
  final def raceAll[R1 <: R, E1 >: E, A1 >: A](
    ios: => Iterable[ZIO[R1, E1, A1]]
  )(implicit trace: Trace): ZIO[R1, E1, A1] = {
    def arbiter[E1, A1](
      fibers: List[Fiber[E1, A1]],
      winner: Fiber[E1, A1],
      promise: Promise[E1, (A1, Fiber[E1, A1])],
      fails: Ref[Int]
    )(res: Exit[E1, A1]): URIO[R1, Any] =
      res.foldZIO[R1, Nothing, Unit](
        e => ZIO.flatten(fails.modify((c: Int) => (if (c == 0) promise.failCause(e).unit else ZIO.unit) -> (c - 1))),
        a =>
          promise
            .succeed(a -> winner)
            .flatMap(set =>
              if (set) fibers.foldLeft(ZIO.unit)((io, f) => if (f eq winner) io else io <* f.interrupt)
              else ZIO.unit
            )
      )

    (for {
      done  <- Promise.make[E1, (A1, Fiber[E1, A1])]
      fails <- Ref.make[Int](ios.size)
      c <- ZIO.uninterruptibleMask { restore =>
             for {
               head <- ZIO.interruptible(self).fork
               tail <- ZIO.foreach(ios)(io => ZIO.interruptible(io).fork)
               fs    = head :: tail.toList
               _ <- fs.foldLeft[ZIO[R1, E1, Any]](ZIO.unit) { case (io, f) =>
                      io *> f.await.flatMap(arbiter(fs, f, done, fails)).fork
                    }

               inheritRefs = { (res: (A1, Fiber[E1, A1])) => res._2.inheritRefs.as(res._1) }

               c <- restore(done.await.flatMap(inheritRefs))
                      .onInterrupt(fs.foldLeft(ZIO.unit)((io, f) => io <* f.interrupt))
             } yield c
           }
    } yield c)
  }

  /**
   * Returns an effect that races this effect with the specified effect,
   * yielding the first result to complete, whether by success or failure. If
   * neither effect completes, then the composed effect will not complete.
   *
   * WARNING: The raced effect will safely interrupt the "loser", but will not
   * resume until the loser has been cleanly terminated. If early return is
   * desired, then instead of performing `l raceFirst r`, perform `l.disconnect
   * raceFirst r.disconnect`, which disconnects left and right interrupt signal,
   * allowing a fast return, with interruption performed in the background.
   */
  final def raceFirst[R1 <: R, E1 >: E, A1 >: A](that: => ZIO[R1, E1, A1])(implicit
    trace: Trace
  ): ZIO[R1, E1, A1] =
    (self.exit race that.exit).flatMap(ZIO.done(_))

  /**
   * Returns an effect that races this effect with the specified effect,
   * yielding the first result to succeed. If neither effect succeeds, then the
   * composed effect will fail with some error.
   *
   * WARNING: The raced effect will safely interrupt the "loser", but will not
   * resume until the loser has been cleanly terminated. If early return is
   * desired, then instead of performing `l raceEither r`, perform `l.disconnect
   * raceEither r.disconnect`, which disconnects left and right interrupt
   * signal, allowing the earliest possible return.
   */
  final def raceEither[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit
    trace: Trace
  ): ZIO[R1, E1, Either[A, B]] =
    self.map(Left(_)) race that.map(Right(_))

  /**
   * Forks this effect and the specified effect into their own fibers, and races
   * them, calling one of two specified callbacks depending on which fiber wins
   * the race. This method does not interrupt, join, or otherwise do anything
   * with the fibers. It can be considered a low-level building block for
   * higher-level operators like `race`.
   */
  private final def raceFibersWith[R1 <: R, ER, E2, B, C](right: ZIO[R1, ER, B])(
    leftWins: (Fiber.Runtime[E, A], Fiber.Runtime[ER, B]) => ZIO[R1, E2, C],
    rightWins: (Fiber.Runtime[ER, B], Fiber.Runtime[E, A]) => ZIO[R1, E2, C]
  )(implicit trace: Trace): ZIO[R1, E2, C] =
    ZIO.unsafeStateful[R1, E2, C] { (parentState, parentStatus) =>
      import java.util.concurrent.atomic.AtomicBoolean

      val parentRuntimeFlags = parentStatus.runtimeFlags

      @inline def complete[E0, E1, A, B](
        winner: Fiber.Runtime[E0, A],
        loser: Fiber.Runtime[E1, B],
        cont: (Fiber.Runtime[E0, A], Fiber.Runtime[E1, B]) => ZIO[R1, E2, C],
        ab: AtomicBoolean,
        cb: ZIO[R1, E2, C] => Any
      ): Any =
        if (ab.compareAndSet(true, false)) {
          cb(cont(winner, loser))
        }

      val raceIndicator = new AtomicBoolean(true)

      val leftFiber  = ZIO.unsafeForkUnstarted(trace, self, parentState, parentRuntimeFlags)
      val rightFiber = ZIO.unsafeForkUnstarted(trace, right, parentState, parentRuntimeFlags)

      leftFiber.unsafeSetFiberRef(FiberRef.forkScopeOverride, Some(parentState.scope))
      rightFiber.unsafeSetFiberRef(FiberRef.forkScopeOverride, Some(parentState.scope))

      ZIO
        .async[R1, E2, C](
          { cb =>
            leftFiber.unsafeAddObserver {
              _ => // TODO: Thread this exit value up, so higher-levels don't need to await on left side
                complete(leftFiber, rightFiber, leftWins, raceIndicator, cb)
            }

            rightFiber.unsafeAddObserver {
              _ => // TODO: Thread this exit value up, so higher-levels don't need to await on right side
                complete(rightFiber, leftFiber, rightWins, raceIndicator, cb)
            }

            leftFiber.startBackground(self)
            rightFiber.startBackground(right)
          },
          FiberId.combineAll(Set(leftFiber.id, rightFiber.id))
        )
    }

  /**
   * Returns an effect that races this effect with the specified effect, calling
   * the specified finisher as soon as one result or the other has been
   * computed.
   */
  final def raceWith[R1 <: R, E1, E2, B, C](that: => ZIO[R1, E1, B])(
    leftDone: (Exit[E, A], Fiber[E1, B]) => ZIO[R1, E2, C],
    rightDone: (Exit[E1, B], Fiber[E, A]) => ZIO[R1, E2, C]
  )(implicit trace: Trace): ZIO[R1, E2, C] =
    self.raceFibersWith(that)(
      (winner, loser) =>
        winner.await.flatMap {
          case exit: Exit.Success[_] =>
            winner.inheritRefs.flatMap(_ => leftDone(exit, loser))
          case exit: Exit.Failure[_] =>
            leftDone(exit, loser)
        },
      (winner, loser) =>
        winner.await.flatMap {
          case exit: Exit.Success[B] =>
            winner.inheritRefs.flatMap(_ => rightDone(exit, loser))
          case exit: Exit.Failure[E1] =>
            rightDone(exit, loser)
        }
    )

  /**
   * Keeps some of the errors, and terminates the fiber with the rest
   */
  final def refineOrDie[E1](
    pf: PartialFunction[E, E1]
  )(implicit ev1: E IsSubtypeOfError Throwable, ev2: CanFail[E], trace: Trace): ZIO[R, E1, A] =
    refineOrDieWith(pf)(ev1)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest, using the
   * specified function to convert the `E` into a `Throwable`.
   */
  final def refineOrDieWith[E1](pf: PartialFunction[E, E1])(
    f: E => Throwable
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R, E1, A] =
    self catchAll (err => (pf lift err).fold[ZIO[R, E1, A]](ZIO.die(f(err)))(ZIO.fail(_)))

  /**
   * Fail with the returned value if the `PartialFunction` matches, otherwise
   * continue with our held value.
   */
  final def reject[E1 >: E](pf: PartialFunction[A, E1])(implicit trace: Trace): ZIO[R, E1, A] =
    rejectZIO(pf.andThen(ZIO.fail(_)))

  /**
   * Continue with the returned computation if the `PartialFunction` matches,
   * translating the successful match into a failure, otherwise continue with
   * our held value.
   */
  final def rejectZIO[R1 <: R, E1 >: E](
    pf: PartialFunction[A, ZIO[R1, E1, E1]]
  )(implicit trace: Trace): ZIO[R1, E1, A] =
    self.flatMap { v =>
      pf.andThen[ZIO[R1, E1, A]](_.flatMap(ZIO.fail(_)))
        .applyOrElse[A, ZIO[R1, E1, A]](v, ZIO.succeedNow)
    }

  /**
   * Returns a new effect that repeats this effect according to the specified
   * schedule or until the first failure. Scheduled recurrences are in addition
   * to the first execution, so that `io.repeat(Schedule.once)` yields an effect
   * that executes `io`, and then if that succeeds, executes `io` an additional
   * time.
   */
  final def repeat[R1 <: R, B](schedule: => Schedule[R1, A, B])(implicit
    trace: Trace
  ): ZIO[R1, E, B] =
    repeatOrElse[R1, E, B](schedule, (e, _) => ZIO.fail(e))

  /**
   * Returns a new effect that repeats this effect the specified number of times
   * or until the first failure. Repeats are in addition to the first execution,
   * so that `io.repeatN(1)` yields an effect that executes `io`, and then if
   * that succeeds, executes `io` an additional time.
   */
  final def repeatN(n: => Int)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed {

      def loop(n: Int): ZIO[R, E, A] =
        self.flatMap(a => if (n <= 0) ZIO.succeedNow(a) else ZIO.yieldNow *> loop(n - 1))

      loop(n)
    }

  /**
   * Returns a new effect that repeats this effect according to the specified
   * schedule or until the first failure, at which point, the failure value and
   * schedule output are passed to the specified handler.
   *
   * Scheduled recurrences are in addition to the first execution, so that
   * `io.repeat(Schedule.once)` yields an effect that executes `io`, and then if
   * that succeeds, executes `io` an additional time.
   */
  final def repeatOrElse[R1 <: R, E2, B](
    schedule: => Schedule[R1, A, B],
    orElse: (E, Option[B]) => ZIO[R1, E2, B]
  )(implicit trace: Trace): ZIO[R1, E2, B] =
    repeatOrElseEither[R1, B, E2, B](schedule, orElse).map(_.merge)

  /**
   * Returns a new effect that repeats this effect according to the specified
   * schedule or until the first failure, at which point, the failure value and
   * schedule output are passed to the specified handler.
   *
   * Scheduled recurrences are in addition to the first execution, so that
   * `io.repeat(Schedule.once)` yields an effect that executes `io`, and then if
   * that succeeds, executes `io` an additional time.
   */
  final def repeatOrElseEither[R1 <: R, B, E2, C](
    schedule0: => Schedule[R1, A, B],
    orElse: (E, Option[B]) => ZIO[R1, E2, C]
  )(implicit trace: Trace): ZIO[R1, E2, Either[C, B]] =
    ZIO.suspendSucceed {
      val schedule = schedule0

      schedule.driver.flatMap { driver =>
        def loop(a: A): ZIO[R1, E2, Either[C, B]] =
          driver
            .next(a)
            .foldZIO(
              _ => driver.last.orDie.map(Right(_)),
              b =>
                self.foldZIO(
                  e => orElse(e, Some(b)).map(Left(_)),
                  a => loop(a)
                )
            )

        self.foldZIO(
          e => orElse(e, None).map(Left(_)),
          a => loop(a)
        )
      }
    }

  /**
   * Repeats this effect until its value satisfies the specified predicate or
   * until the first failure.
   */
  final def repeatUntil(p: A => Boolean)(implicit trace: Trace): ZIO[R, E, A] =
    repeatUntilZIO(a => ZIO.succeed(p(a)))

  /**
   * Repeats this effect until its value is equal to the specified value or
   * until the first failure.
   */
  final def repeatUntilEquals[A1 >: A](a: => A1)(implicit trace: Trace): ZIO[R, E, A1] =
    repeatUntil(_ == a)

  /**
   * Repeats this effect until its value satisfies the specified effectful
   * predicate or until the first failure.
   */
  final def repeatUntilZIO[R1 <: R](f: A => URIO[R1, Boolean])(implicit trace: Trace): ZIO[R1, E, A] =
    self.flatMap(a => f(a).flatMap(b => if (b) ZIO.succeedNow(a) else ZIO.yieldNow *> repeatUntilZIO(f)))

  /**
   * Repeats this effect while its value satisfies the specified predicate or
   * until the first failure.
   */
  final def repeatWhile(p: A => Boolean)(implicit trace: Trace): ZIO[R, E, A] =
    repeatWhileZIO(a => ZIO.succeed(p(a)))

  /**
   * Repeats this effect for as long as its value is equal to the specified
   * value or until the first failure.
   */
  final def repeatWhileEquals[A1 >: A](a: => A1)(implicit trace: Trace): ZIO[R, E, A1] =
    repeatWhile(_ == a)

  /**
   * Repeats this effect while its value satisfies the specified effectful
   * predicate or until the first failure.
   */
  final def repeatWhileZIO[R1 <: R](f: A => URIO[R1, Boolean])(implicit trace: Trace): ZIO[R1, E, A] =
    repeatUntilZIO(e => f(e).map(!_))

  /**
   * Performs this effect the specified number of times and collects the
   * results.
   */
  final def replicateZIO(n: => Int)(implicit trace: Trace): ZIO[R, E, Iterable[A]] =
    ZIO.replicateZIO(n)(self)

  /**
   * Performs this effect the specified number of times, discarding the results.
   */
  final def replicateZIODiscard(n: => Int)(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.replicateZIODiscard(n)(self)

  /**
   * Retries with the specified retry policy. Retries are done following the
   * failure of the original `io` (up to a fixed maximum with `once` or `recurs`
   * for example), so that that `io.retry(Schedule.once)` means "execute `io`
   * and in case of failure, try again once".
   */
  final def retry[R1 <: R, S](
    policy: => Schedule[R1, E, S]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E, A] =
    retryOrElse(policy, (e: E, _: S) => ZIO.fail(e))

  /**
   * Retries this effect the specified number of times.
   */
  final def retryN(n: => Int)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed {

      def loop(n: Int): ZIO[R, E, A] =
        self.catchAll(e => if (n <= 0) ZIO.fail(e) else ZIO.yieldNow *> loop(n - 1))

      loop(n)
    }

  /**
   * Retries with the specified schedule, until it fails, and then both the
   * value produced by the schedule together with the last error are passed to
   * the recovery function.
   */
  final def retryOrElse[R1 <: R, A1 >: A, S, E1](
    policy: => Schedule[R1, E, S],
    orElse: (E, S) => ZIO[R1, E1, A1]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A1] =
    retryOrElseEither(policy, orElse).map(_.merge)

  /**
   * Returns an effect that retries this effect with the specified schedule when
   * it fails, until the schedule is done, then both the value produced by the
   * schedule together with the last error are passed to the specified recovery
   * function.
   */
  final def retryOrElseEither[R1 <: R, Out, E1, B](
    schedule0: => Schedule[R1, E, Out],
    orElse: (E, Out) => ZIO[R1, E1, B]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, Either[B, A]] =
    ZIO.suspendSucceed {
      val schedule = schedule0

      def loop(driver: Schedule.Driver[Any, R1, E, Out]): ZIO[R1, E1, Either[B, A]] =
        self
          .map(Right(_))
          .catchAll(e =>
            driver
              .next(e)
              .foldZIO(
                _ => driver.last.orDie.flatMap(out => orElse(e, out).map(Left(_))),
                _ => loop(driver)
              )
          )

      schedule.driver.flatMap(loop(_))
    }

  /**
   * Retries this effect until its error satisfies the specified predicate.
   */
  final def retryUntil(f: E => Boolean)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E, A] =
    retryUntilZIO(e => ZIO.succeed(f(e)))

  /**
   * Retries this effect until its error is equal to the specified error.
   */
  final def retryUntilEquals[E1 >: E](e: => E1)(implicit ev: CanFail[E1], trace: Trace): ZIO[R, E1, A] =
    retryUntil(_ == e)

  /**
   * Retries this effect until its error satisfies the specified effectful
   * predicate.
   */
  final def retryUntilZIO[R1 <: R](
    f: E => URIO[R1, Boolean]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E, A] =
    self.catchAll(e => f(e).flatMap(b => if (b) ZIO.fail(e) else ZIO.yieldNow *> retryUntilZIO(f)))

  /**
   * Retries this effect while its error satisfies the specified predicate.
   */
  final def retryWhile(f: E => Boolean)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E, A] =
    retryWhileZIO(e => ZIO.succeed(f(e)))

  /**
   * Retries this effect for as long as its error is equal to the specified
   * error.
   */
  final def retryWhileEquals[E1 >: E](e: => E1)(implicit ev: CanFail[E1], trace: Trace): ZIO[R, E1, A] =
    retryWhile(_ == e)

  /**
   * Retries this effect while its error satisfies the specified effectful
   * predicate.
   */
  final def retryWhileZIO[R1 <: R](
    f: E => URIO[R1, Boolean]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E, A] =
    retryUntilZIO(e => f(e).map(!_))

  /**
   * "Zooms in" on the value in the `Right` side of an `Either`, moving the
   * possibility that the value is a `Left` to the error channel.
   */
  final def right[B, C](implicit ev: A IsSubtypeOfOutput Either[B, C], trace: Trace): ZIO[R, Either[B, E], C] =
    self.foldZIO(
      e => ZIO.fail(Right(e)),
      a => ev(a).fold(b => ZIO.fail(Left(b)), c => ZIO.succeedNow(c))
    )

  /**
   * Performs the specified operation while "zoomed in" on the `Right` case of
   * an `Either`.
   */
  final def rightWith[R1, E1, A1, B, B1, C, C1](
    f: ZIO[R, Either[B, E], C] => ZIO[R1, Either[B1, E1], C1]
  )(implicit ev: A IsSubtypeOfOutput Either[B, C], trace: Trace): ZIO[R1, E1, Either[B1, C1]] =
    f(self.right).unright

  /**
   * Exposes the full cause of failure of this effect.
   *
   * {{{
   * final case class DomainError()
   *
   * val veryBadIO: IO[DomainError, Unit] =
   *   ZIO.succeed(5 / 0) *> ZIO.fail(DomainError())
   *
   * val caught: IO[DomainError, Unit] =
   *   veryBadIO.sandbox.mapError(_.untraced).catchAll {
   *     case Cause.Die(_: ArithmeticException) =>
   *       // Caught defect: divided by zero!
   *       ZIO.unit
   *     case Cause.Fail(_) =>
   *       // Caught error: DomainError!
   *       ZIO.unit
   *     case cause =>
   *       // Caught unknown defects, shouldn't recover!
   *       ZIO.failCause(cause)
   *   }
   * }}}
   */
  final def sandbox(implicit trace: Trace): ZIO[R, Cause[E], A] =
    foldCauseZIO(ZIO.fail(_), ZIO.succeedNow)

  /**
   * Runs this effect according to the specified schedule.
   *
   * See [[scheduleFrom]] for a variant that allows the schedule's decision to
   * depend on the result of this effect.
   */
  final def schedule[R1 <: R, B](schedule: => Schedule[R1, Any, B])(implicit
    trace: Trace
  ): ZIO[R1, E, B] =
    scheduleFrom(())(schedule)

  /**
   * Runs this effect according to the specified schedule starting from the
   * specified input value.
   */
  final def scheduleFrom[R1 <: R, A1 >: A, B](a: => A1)(
    schedule0: => Schedule[R1, A1, B]
  )(implicit trace: Trace): ZIO[R1, E, B] =
    ZIO.suspendSucceed {
      val schedule = schedule0

      schedule.driver.flatMap { driver =>
        def loop(a: A1): ZIO[R1, E, B] =
          driver.next(a).foldZIO(_ => driver.last.orDie, _ => self.flatMap(loop))

        loop(a)
      }
    }

  /**
   * Runs this effect according to the specified schedule in a new fiber
   * attached to the current scope.
   */
  final def scheduleFork[R1 <: R, B](schedule: => Schedule[R1, Any, B])(implicit
    trace: Trace
  ): ZIO[R1 with Scope, Nothing, Fiber.Runtime[E, B]] =
    self.schedule(schedule).forkScoped

  /**
   * Returns a new scoped workflow that runs finalizers added to the scope of
   * this workflow sequentially in the reverse of the order in which they were
   * added. Note that finalizers are run sequentially by default so this only
   * has meaning if used within a scope where finalizers are being run in
   * parallel.
   */
  final def sequentialFinalizers(implicit trace: Trace): ZIO[R with Scope, E, A] =
    ZIO.sequentialFinalizers(self)

  /**
   * Converts an option on values into an option on errors.
   */
  final def some[B](implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R, Option[E], B] =
    self.foldZIO(
      e => ZIO.fail(Some(e)),
      a => ev(a).fold[ZIO[R, Option[E], B]](ZIO.fail(Option.empty[E]))(ZIO.succeedNow)
    )

  /**
   * Perfoms the specified operation while "zoomed in" on the `Some` case of an
   * `Option`.
   */
  final def someWith[R1, E1, A1, B, B1](
    f: ZIO[R, Option[E], B] => ZIO[R1, Option[E1], B1]
  )(implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R1, E1, Option[B1]] =
    f(self.some).unsome

  /**
   * Extracts the optional value, or returns the given 'default'.
   */
  final def someOrElse[B](
    default: => B
  )(implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R, E, B] =
    map(a => ev(a).getOrElse(default))

  /**
   * Extracts the optional value, or executes the effect 'default'.
   */
  final def someOrElseZIO[B, R1 <: R, E1 >: E](
    default: => ZIO[R1, E1, B]
  )(implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R1, E1, B] =
    self.flatMap(ev(_) match {
      case Some(value) => ZIO.succeedNow(value)
      case None        => default
    })

  /**
   * Extracts the optional value, or fails with the given error 'e'.
   */
  final def someOrFail[B, E1 >: E](
    e: => E1
  )(implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R, E1, B] =
    self.flatMap(ev(_) match {
      case Some(value) => ZIO.succeedNow(value)
      case None        => ZIO.fail(e)
    })

  /**
   * Extracts the optional value, or fails with a
   * [[java.util.NoSuchElementException]]
   */
  final def someOrFailException[B, E1 >: E](implicit
    ev: A IsSubtypeOfOutput Option[B],
    ev2: NoSuchElementException <:< E1,
    trace: Trace
  ): ZIO[R, E1, B] =
    self.foldZIO(
      e => ZIO.fail(e),
      ev(_) match {
        case Some(value) => ZIO.succeedNow(value)
        case None        => ZIO.fail(ev2(new NoSuchElementException("None.get")))
      }
    )

  /**
   * Companion helper to `sandbox`. Allows recovery, and partial recovery, from
   * errors and defects alike, as in:
   *
   * {{{
   * case class DomainError()
   *
   * val veryBadIO: IO[DomainError, Unit] =
   *   ZIO.succeed(5 / 0) *> ZIO.fail(DomainError())
   *
   * val caught: IO[DomainError, Unit] =
   *   veryBadIO.sandboxWith(_.catchSome {
   *     case Cause.Die(_: ArithmeticException)=>
   *       // Caught defect: divided by zero!
   *       ZIO.succeed(0)
   *   })
   * }}}
   *
   * Using `sandboxWith` with `catchSome` is better than using
   * `io.sandbox.catchAll` with a partial match, because in the latter, if the
   * match fails, the original defects will be lost and replaced by a
   * `MatchError`
   */
  final def sandboxWith[R1 <: R, E2, B](f: ZIO[R1, Cause[E], A] => ZIO[R1, Cause[E2], B])(implicit
    trace: Trace
  ): ZIO[R1, E2, B] =
    ZIO.unsandbox(f(self.sandbox))

  /**
   * Summarizes a effect by computing some value before and after execution, and
   * then combining the values to produce a summary, together with the result of
   * execution.
   */
  final def summarized[R1 <: R, E1 >: E, B, C](
    summary0: => ZIO[R1, E1, B]
  )(f: (B, B) => C)(implicit trace: Trace): ZIO[R1, E1, (C, A)] =
    ZIO.suspendSucceed {
      val summary = summary0

      for {
        start <- summary
        value <- self
        end   <- summary
      } yield (f(start, end), value)
    }

  /**
   * Returns an effect with the behavior of this one, but where all child fibers
   * forked in the effect are reported to the specified supervisor.
   */
  final def supervised(supervisor: => Supervisor[Any])(implicit trace: Trace): ZIO[R, E, A] =
    FiberRef.currentSupervisors.locallyWith(_ + supervisor)(self)

  /**
   * Returns an effect that effectfully "peeks" at the success of this effect.
   *
   * {{{
   * readFile("data.json").tap(printLine)
   * }}}
   */
  final def tap[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Any])(implicit trace: Trace): ZIO[R1, E1, A] =
    self.flatMap(a => f(a).as(a))

  /**
   * Returns an effect that effectfully "peeks" at the failure or success of
   * this effect.
   * {{{
   * readFile("data.json").tapBoth(logError(_), logData(_))
   * }}}
   */
  final def tapBoth[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, Any], g: A => ZIO[R1, E1, Any])(implicit
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.foldCauseZIO(c => c.failureOrCause.fold(f(_) *> ZIO.failCause(c), _ => ZIO.failCause(c)), a => g(a).as(a))

  /**
   * Returns an effect that effectually "peeks" at the defect of this effect.
   */
  final def tapDefect[R1 <: R, E1 >: E](f: Cause[Nothing] => ZIO[R1, E1, Any])(implicit
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.foldCauseZIO(c => f(c.stripFailures) *> ZIO.failCause(c), ZIO.succeedNow)

  /**
   * Returns an effect that effectfully "peeks" at the result of this effect.
   * {{{
   * readFile("data.json").tapEither(result => log(result.fold("Error: " + _, "Success: " + _)))
   * }}}
   */
  final def tapEither[R1 <: R, E1 >: E](f: Either[E, A] => ZIO[R1, E1, Any])(implicit
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.foldCauseZIO(
      c => c.failureOrCause.fold(e => f(Left(e)) *> ZIO.failCause(c), _ => ZIO.failCause(c)),
      a => f(Right(a)).as(a)
    )

  /**
   * Returns an effect that effectfully "peeks" at the failure of this effect.
   * {{{
   * readFile("data.json").tapError(logError(_))
   * }}}
   */
  final def tapError[R1 <: R, E1 >: E](
    f: E => ZIO[R1, E1, Any]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A] =
    self.foldCauseZIO(c => c.failureOrCause.fold(f(_) *> ZIO.failCause(c), _ => ZIO.failCause(c)), ZIO.succeedNow)

  /**
   * Returns an effect that effectually "peeks" at the cause of the failure of
   * this effect.
   * {{{
   * readFile("data.json").tapErrorCause(logCause(_))
   * }}}
   */
  final def tapErrorCause[R1 <: R, E1 >: E](f: Cause[E] => ZIO[R1, E1, Any])(implicit
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.foldCauseZIO(c => f(c) *> ZIO.failCause(c), ZIO.succeedNow)

  /**
   * A version of `tapError` that gives you the trace of the error.
   */
  final def tapErrorTrace[R1 <: R, E1 >: E](
    f: ((E, StackTrace)) => ZIO[R1, E1, Any]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A] =
    self.foldCauseZIO(c => c.failureTraceOrCause.fold(f(_) *> ZIO.failCause(c), _ => ZIO.failCause(c)), ZIO.succeedNow)

  /**
   * Returns an effect that effectfully "peeks" at the success of this effect.
   * If the partial function isn't defined at the input, the result is
   * equivalent to the original effect.
   *
   * {{{
   * readFile("data.json").tapSome {
   *   case content if content.nonEmpty => putStrLn(content)
   * }
   * }}}
   */
  final def tapSome[R1 <: R, E1 >: E](f: PartialFunction[A, ZIO[R1, E1, Any]])(implicit
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.tap(f.applyOrElse(_, (_: A) => ZIO.unit))

  /**
   * Returns a new effect that executes this one and times the execution.
   */
  final def timed(implicit trace: Trace): ZIO[R, E, (Duration, A)] =
    timedWith(Clock.nanoTime)

  /**
   * A more powerful variation of `timed` that allows specifying the workflow
   * that will be used to calculate the current time.
   */
  final def timedWith[R1 <: R, E1 >: E](nanoTime: => ZIO[R1, E1, Long])(implicit
    trace: Trace
  ): ZIO[R1, E1, (Duration, A)] =
    summarized(nanoTime)((start, end) => Duration.fromNanos(end - start))

  /**
   * Returns an effect that will timeout this effect, returning `None` if the
   * timeout elapses before the effect has produced a value; and returning
   * `Some` of the produced value otherwise.
   *
   * If the timeout elapses without producing a value, the running effect will
   * be safely interrupted.
   *
   * WARNING: The effect returned by this method will not itself return until
   * the underlying effect is actually interrupted. This leads to more
   * predictable resource utilization. If early return is desired, then instead
   * of using `effect.timeout(d)`, use `effect.disconnect.timeout(d)`, which
   * first disconnects the effect's interruption signal before performing the
   * timeout, resulting in earliest possible return, before an underlying effect
   * has been successfully interrupted.
   */
  final def timeout(d: => Duration)(implicit trace: Trace): ZIO[R, E, Option[A]] =
    timeoutTo(None)(Some(_))(d)

  /**
   * The same as [[timeout]], but instead of producing a `None` in the event of
   * timeout, it will produce the specified error.
   */
  final def timeoutFail[E1 >: E](e: => E1)(d: => Duration)(implicit
    trace: Trace
  ): ZIO[R, E1, A] =
    ZIO.flatten(timeoutTo(ZIO.fail(e))(ZIO.succeedNow)(d))

  /**
   * The same as [[timeout]], but instead of producing a `None` in the event of
   * timeout, it will produce the specified failure.
   */
  final def timeoutFailCause[E1 >: E](cause: => Cause[E1])(d: => Duration)(implicit
    trace: Trace
  ): ZIO[R, E1, A] =
    ZIO.flatten(timeoutTo(ZIO.failCause(cause))(ZIO.succeedNow)(d))

  /**
   * Returns an effect that will timeout this effect, returning either the
   * default value if the timeout elapses before the effect has produced a
   * value; and or returning the result of applying the function `f` to the
   * success value of the effect.
   *
   * If the timeout elapses without producing a value, the running effect will
   * be safely interrupted
   *
   * {{{
   * ZIO.succeed(1).timeoutTo(None)(Some(_))(1.second)
   * }}}
   */
  final def timeoutTo[B](b: => B): ZIO.TimeoutTo[R, E, A, B] =
    new ZIO.TimeoutTo(self, () => b)

  /**
   * Converts the effect into a [[scala.concurrent.Future]].
   */
  final def toFuture(implicit ev2: E IsSubtypeOfError Throwable, trace: Trace): URIO[R, CancelableFuture[A]] =
    self toFutureWith ev2

  /**
   * Converts the effect into a [[scala.concurrent.Future]].
   */
  final def toFutureWith(f: E => Throwable)(implicit trace: Trace): URIO[R, CancelableFuture[A]] =
    self.fork.flatMap(_.toFutureWith(f))

  /**
   * When this effect succeeds with a cause, then this method returns a new
   * effect that either fails with the cause that this effect succeeded with, or
   * succeeds with unit, depending on whether the cause is empty.
   *
   * This operation is the opposite of [[cause]].
   */
  final def uncause[E1 >: E](implicit ev: A IsSubtypeOfOutput Cause[E1], trace: Trace): ZIO[R, E1, Unit] =
    self.flatMap { a =>
      val cause = ev(a)

      if (cause.isEmpty) ZIO.unit
      else ZIO.failCause(cause)
    }

  /**
   * Performs this effect uninterruptibly. This will prevent the effect from
   * being terminated externally, but the effect may fail for internal reasons
   * (e.g. an uncaught error) or terminate due to defect.
   *
   * Uninterruptible effects may recover from all failure causes (including
   * interruption of an inner effect that has been made interruptible).
   */
  final def uninterruptible(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.UpdateRuntimeFlagsWithin.Uninterruptible(trace, self)

  /**
   * Returns the effect resulting from mapping the success of this effect to
   * unit.
   */
  final def unit(implicit trace: Trace): ZIO[R, E, Unit] =
    as(())

  /**
   * Converts a `ZIO[R, Either[E, B], A]` into a `ZIO[R, E, Either[A, B]]`. The
   * inverse of `left`.
   */
  final def unleft[E1, B](implicit
    ev: E IsSubtypeOfError Either[E1, B],
    trace: Trace
  ): ZIO[R, E1, Either[A, B]] =
    self.foldZIO(
      e => ev(e).fold(e1 => ZIO.fail(e1), b => ZIO.succeedNow(Right(b))),
      a => ZIO.succeedNow(Left(a))
    )

  /**
   * The moral equivalent of `if (!p) exp`
   */
  final def unless(p: => Boolean)(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.unless(p)(self)

  /**
   * The moral equivalent of `if (!p) exp` when `p` has side-effects
   */
  final def unlessZIO[R1 <: R, E1 >: E](p: => ZIO[R1, E1, Boolean])(implicit
    trace: Trace
  ): ZIO[R1, E1, Option[A]] =
    ZIO.unlessZIO(p)(self)

  /**
   * Takes some fiber failures and converts them into errors.
   */
  final def unrefine[E1 >: E](pf: PartialFunction[Throwable, E1])(implicit trace: Trace): ZIO[R, E1, A] =
    unrefineWith(pf)(identity)

  /**
   * Takes some fiber failures and converts them into errors.
   */
  final def unrefineTo[E1 >: E: ClassTag](implicit trace: Trace): ZIO[R, E1, A] =
    unrefine { case e: E1 => e }

  /**
   * Takes some fiber failures and converts them into errors, using the
   * specified function to convert the `E` into an `E1`.
   */
  final def unrefineWith[E1](
    pf: PartialFunction[Throwable, E1]
  )(f: E => E1)(implicit trace: Trace): ZIO[R, E1, A] =
    catchAllCause { cause =>
      cause.find {
        case Cause.Die(t, _) if pf.isDefinedAt(t) => pf(t)
      }.fold(ZIO.failCause(cause.map(f)))(ZIO.fail(_))
    }

  /**
   * Converts a `ZIO[R, Either[B, E], A]` into a `ZIO[R, E, Either[B, A]]`. The
   * inverse of `right`.
   */
  final def unright[E1, B](implicit
    ev: E IsSubtypeOfError Either[B, E1],
    trace: Trace
  ): ZIO[R, E1, Either[B, A]] =
    self.foldZIO(
      e => ev(e).fold(b => ZIO.succeedNow(Left(b)), e1 => ZIO.fail(e1)),
      a => ZIO.succeedNow(Right(a))
    )

  /**
   * Converts an option on errors into an option on values.
   */
  final def unsome[E1](implicit ev: E IsSubtypeOfError Option[E1], trace: Trace): ZIO[R, E1, Option[A]] =
    self.foldZIO(
      e => ev(e).fold[ZIO[R, E1, Option[A]]](ZIO.succeedNow(Option.empty[A]))(ZIO.fail(_)),
      a => ZIO.succeedNow(Some(a))
    )

  /**
   * Updates a service in the environment of this effect.
   */
  final def updateService[M] =
    new ZIO.UpdateService[R, E, A, M](self)

  /**
   * Updates a service at the specified key in the environment of this effect.
   */
  final def updateServiceAt[Service]: ZIO.UpdateServiceAt[R, E, A, Service] =
    new ZIO.UpdateServiceAt[R, E, A, Service](self)

  /**
   * The inverse operation to `sandbox`. Submerges the full cause of failure.
   */
  final def unsandbox[E1](implicit ev: E IsSubtypeOfError Cause[E1], trace: Trace): ZIO[R, E1, A] =
    ZIO.unsandbox(self.mapError(ev))

  /**
   * Sequentially zips the this result with the specified result. Combines both
   * `Cause[E1]` when both effects fail.
   */
  final def validate[R1 <: R, E1 >: E, B](
    that: => ZIO[R1, E1, B]
  )(implicit zippable: Zippable[A, B], trace: Trace): ZIO[R1, E1, zippable.Out] =
    validateWith(that)(zippable.zip(_, _))

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel. Combines both Cause[E1]` when both effects fail.
   */
  final def validatePar[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit
    trace: Trace
  ): ZIO[R1, E1, (A, B)] =
    validateWithPar(that)((_, _))

  /**
   * Sequentially zips this effect with the specified effect using the specified
   * combiner function. Combines the causes in case both effect fail.
   */
  final def validateWith[R1 <: R, E1 >: E, B, C](that: => ZIO[R1, E1, B])(f: (A, B) => C)(implicit
    trace: Trace
  ): ZIO[R1, E1, C] =
    self.exit.zipWith(that.exit)(_.zipWith(_)(f, _ ++ _)).flatMap(ZIO.done(_))

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, combining their results with the specified `f` function. If
   * both sides fail, then the cause will be combined.
   */
  final def validateWithPar[R1 <: R, E1 >: E, B, C](that: => ZIO[R1, E1, B])(f: (A, B) => C)(implicit
    trace: Trace
  ): ZIO[R1, E1, C] =
    self.exit.zipWithPar(that.exit)(_.zipWith(_)(f, _ && _)).flatMap(ZIO.done(_))

  /**
   * The moral equivalent of `if (p) exp`
   */
  final def when(p: => Boolean)(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.when(p)(self)

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  final def whenZIO[R1 <: R, E1 >: E](
    p: => ZIO[R1, E1, Boolean]
  )(implicit trace: Trace): ZIO[R1, E1, Option[A]] =
    ZIO.whenZIO(p)(self)

  /**
   * Executes this workflow with the specified implementation of the clock
   * service.
   */
  final def withClock[B <: Clock](clock: => B)(implicit tag: Tag[B], trace: Trace): ZIO[R, E, A] =
    ZIO.withClock(clock)(self)

  /**
   * Executes this workflow with the specified implementation of the console
   * service.
   */
  final def withConsole[B <: Console](console: => B)(implicit tag: Tag[B], trace: Trace): ZIO[R, E, A] =
    ZIO.withConsole(console)(self)

  /**
   * Returns a new scoped workflow that returns the result of this workflow as
   * well as a finalizer that can be run to close the scope of this workflow.
   */
  final def withEarlyRelease(implicit trace: Trace): ZIO[R with Scope, E, (UIO[Unit], A)] =
    ZIO.scopeWith { parent =>
      parent.fork.flatMap { child =>
        child.extend[R](self).map { a =>
          (ZIO.fiberIdWith(fiberId => child.close(Exit.interrupt(fiberId))), a)
        }
      }
    }

  /**
   * Treats this effect as the acquisition of a resource and adds the specified
   * finalizer to the current scope. This effect will be run uninterruptibly and
   * the finalizer will be run when the scope is closed.
   */
  final def withFinalizer[R1 <: R](finalizer: A => URIO[R1, Any])(implicit
    trace: Trace
  ): ZIO[R1 with Scope, E, A] =
    ZIO.acquireRelease(self)(finalizer)

  /**
   * A more powerful variant of `withFinalizer` that allows the finalizer to
   * depend on the `Exit` value that the scope is closed with.
   */
  final def withFinalizerExit[R1 <: R](finalizer: (A, Exit[Any, Any]) => URIO[R1, Any])(implicit
    trace: Trace
  ): ZIO[R1 with Scope, E, A] =
    ZIO.acquireReleaseExit(self)(finalizer)

  /**
   * Runs this effect with the specified maximum number of fibers for parallel
   * operators.
   */
  final def withParallelism(n: => Int)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withParallelism(n)(self)

  /**
   * Runs this effect with an unbounded maximum number of fibers for parallel
   * operators.
   */
  def withParallelismUnbounded(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withParallelismUnbounded(self)

  /**
   * Executes this workflow with the specified implementation of the random
   * service.
   */
  final def withRandom[B <: Random](random: => B)(implicit tag: Tag[B], trace: Trace): ZIO[R, E, A] =
    ZIO.withRandom(random)(self)

  /**
   * Returns a new ZIO effect that will update the runtime flags according to
   * the specified patch within the scope of this ZIO effect.
   */
  final def withRuntimeFlags(patch: RuntimeFlags.Patch)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.UpdateRuntimeFlagsWithin.Dynamic(trace, patch, _ => self)

  /**
   * Executes this workflow with the specified implementation of the system
   * service.
   */
  final def withSystem[B <: System](system: => B)(implicit tag: Tag[B], trace: Trace): ZIO[R, E, A] =
    ZIO.withSystem(system)(self)

  /**
   * A named alias for `<*>`.
   */
  final def zip[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit
    zippable: Zippable[A, B],
    trace: Trace
  ): ZIO[R1, E1, zippable.Out] =
    self.zipWith(that)((a, b) => zippable.zip(a, b))

  /**
   * A named alias for `<*`.
   */
  final def zipLeft[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, A] =
    self <* that

  /**
   * A named alias for `<&>`.
   */
  final def zipPar[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit
    zippable: Zippable[A, B],
    trace: Trace
  ): ZIO[R1, E1, zippable.Out] =
    self <&> that

  /**
   * A named alias for `<&`.
   */
  final def zipParLeft[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, A] =
    self <& that

  /**
   * A named alias for `&>`.
   */
  final def zipParRight[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
    self &> that

  /**
   * A named alias for `*>`.
   */
  final def zipRight[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
    self *> that

  /**
   * Sequentially zips this effect with the specified effect using the specified
   * combiner function.
   */
  final def zipWith[R1 <: R, E1 >: E, B, C](that: => ZIO[R1, E1, B])(f: (A, B) => C)(implicit
    trace: Trace
  ): ZIO[R1, E1, C] =
    self.flatMap(a => that.map(b => f(a, b)))

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, combining their results with the specified `f` function. If
   * either side fails, then the other side will be interrupted.
   */
  final def zipWithPar[R1 <: R, E1 >: E, B, C](
    that: => ZIO[R1, E1, B]
  )(f: (A, B) => C)(implicit trace: Trace): ZIO[R1, E1, C] = {
    def coordinate[A, B](
      fiberId: FiberId,
      f: (A, B) => C,
      leftWinner: Boolean
    )(winner: Fiber[E1, A], loser: Fiber[E1, B])(implicit trace: Trace): ZIO[R1, E1, C] =
      winner.await.flatMap {
        case Exit.Success(a) =>
          loser.await.flatMap {
            case Exit.Success(b)     => winner.inheritRefs *> loser.inheritRefs *> ZIO.succeed(f(a, b))
            case Exit.Failure(cause) => ZIO.failCause(cause)
          }
        case Exit.Failure(cause) =>
          loser.interruptAs(fiberId).flatMap {
            case Exit.Success(_) => ZIO.failCause(cause)
            case Exit.Failure(loserCause) =>
              if (leftWinner) ZIO.failCause(cause && loserCause)
              else ZIO.failCause(loserCause && cause)
          }
      }

    val g = (b: B, a: A) => f(a, b)

    ZIO.uninterruptibleMask { restore =>
      ZIO.transplant { graft =>
        ZIO.fiberIdWith { fiberId =>
          graft(restore(self)).raceFibersWith(graft(restore(that)))(
            coordinate(fiberId, f, true),
            coordinate(fiberId, g, false)
          )
        }
      }
    }
  }

  private[this] final def tryOrElse[R1 <: R, E2, B](
    that: => ZIO[R1, E2, B],
    success: A => ZIO[R1, E2, B]
  )(implicit trace: Trace): ZIO[R1, E2, B] =
    self.foldCauseZIO(
      cause =>
        cause.keepDefects match {
          case None    => that
          case Some(c) => ZIO.failCause(c)
        },
      success
    )

  private[zio] def trace: Trace
}

object ZIO extends ZIOCompanionPlatformSpecific {
  private def unsafeFork[R, E1, E2, A, B](
    trace: Trace,
    effect: ZIO[R, E1, A],
    parentFiber: internal.FiberRuntime[E2, B],
    parentRuntimeFlags: RuntimeFlags
  ): internal.FiberRuntime[E1, A] = {
    val childFiber = ZIO.unsafeForkUnstarted(trace, effect, parentFiber, parentRuntimeFlags)

    childFiber.startBackground(effect)

    childFiber
  }

  private def unsafeForkUnstarted[R, E1, E2, A, B](
    trace: Trace,
    effect: ZIO[R, E1, A],
    parentFiber: internal.FiberRuntime[E2, B],
    parentRuntimeFlags: RuntimeFlags
  ): internal.FiberRuntime[E1, A] = {
    val childId         = FiberId.unsafeMake(trace)
    val parentFiberRefs = parentFiber.unsafeGetFiberRefs
    val childFiberRefs  = parentFiberRefs.forkAs(childId)

    val childFiber = internal.FiberRuntime[E1, A](childId, childFiberRefs, parentRuntimeFlags)

    childFiber.unsafeForeachSupervisor { supervisor =>
      // Call the supervisor who can observe the fork of the child fiber
      val childEnvironment = childFiberRefs.getOrDefault(FiberRef.currentEnvironment)

      supervisor.unsafeOnStart(
        childEnvironment,
        effect.asInstanceOf[ZIO[Any, Any, Any]],
        Some(parentFiber),
        childFiber
      )

      childFiber.unsafeAddObserver(exit => supervisor.unsafeOnEnd(exit, childFiber))
    }

    val parentScope = parentFiber.unsafeGetFiberRef(FiberRef.forkScopeOverride).getOrElse(parentFiber.scope)

    parentScope.unsafeAdd(parentRuntimeFlags, childFiber)(trace)

    childFiber
  }

  /**
   * The level of parallelism for parallel operators.
   */
  final lazy val Parallelism: FiberRef[Option[Int]] =
    FiberRef.unsafeMake(None)

  /**
   * Submerges the error case of an `Either` into the `ZIO`. The inverse
   * operation of `IO.either`.
   */
  def absolve[R, E, A](v: => ZIO[R, E, Either[E, A]])(implicit trace: Trace): ZIO[R, E, A] =
    suspendSucceed(v).flatMap(fromEither(_))

  /**
   * Constructs a scoped resource from an `acquire` and `release` effect. If
   * `acquire` successfully completes execution then `release` will be added to
   * the finalizers associated with the scope of this effect and is guaranteed
   * to be run when the scope is closed.
   *
   * The `acquire` and `release` effects will be run uninterruptibly.
   */
  def acquireRelease[R, R1, E, A](acquire: => ZIO[R, E, A])(release: A => ZIO[R1, Nothing, Any])(implicit
    trace: Trace
  ): ZIO[R with R1 with Scope, E, A] =
    acquireReleaseExit(acquire)((a, _) => release(a))

  /**
   * A more powerful variant of `acquireRelease` that allows the `release`
   * effect to depend on the `Exit` value specified when the scope is closed.
   */
  def acquireReleaseExit[R, R1, E, A](
    acquire: => ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => ZIO[R1, Nothing, Any])(implicit
    trace: Trace
  ): ZIO[R with R1 with Scope, E, A] =
    ZIO.uninterruptible(acquire.tap(a => ZIO.addFinalizerExit(exit => release(a, exit))))

  /**
   * A variant of `acquireRelease` that allows the `acquire` effect to be
   * interruptible. Since the `acquire` effect could be interrupted after
   * partially acquiring resources, the `release` effect is not allowed to
   * access the resource produced by `acquire` and must independently determine
   * what finalization, if any, needs to be performed (e.g. by examining in
   * memory state).
   */
  def acquireReleaseInterruptible[R, R1, E, A](acquire: => ZIO[R, E, A])(release: ZIO[R1, Nothing, Any])(implicit
    trace: Trace
  ): ZIO[R with R1 with Scope, E, A] =
    acquireReleaseInterruptibleExit(acquire)(_ => release)

  /**
   * A more powerful variant of `acquireReleaseInterruptible` that allows the
   * `release` effect to depend on the `Exit` value specified when the scope is
   * closed.
   */
  def acquireReleaseInterruptibleExit[R, R1, E, A](acquire: => ZIO[R, E, A])(
    release: Exit[Any, Any] => ZIO[R1, Nothing, Any]
  )(implicit trace: Trace): ZIO[R with R1 with Scope, E, A] =
    ZIO.suspendSucceed(acquire.ensuring(ZIO.addFinalizerExit(release)))

  /**
   * Given an effect representing acquisition of a resource (for example,
   * opening a file, launching a thread, etc.), `acquireReleaseWith` can be used
   * to ensure the acquisition is not interrupted and the resource is always
   * released.
   *
   * The function does two things:
   *
   *   1. Ensures this `acquire` effect will not be interrupted. Of course,
   *      acquisition may fail for internal reasons (an uncaught exception).
   *
   *   1. Ensures the `release` effect will not be interrupted, and will be
   *      executed so long as this effect successfully acquires the resource.
   *
   * In between acquisition and release of the resource, the `use` effect is
   * executed.
   *
   * If the `release` effect fails, then the entire effect will fail even if the
   * `use` effect succeeds. If this fail-fast behavior is not desired, errors
   * produced by the `release` effect can be caught and ignored.
   *
   * {{{
   * ZIO.acquireReleaseWith(openFile("data.json"))(closeFile) { file =>
   *   for {
   *     header <- readHeader(file)
   *     ...
   *   } yield result
   * }
   * }}}
   */
  def acquireReleaseWith[R, E, A](acquire: => ZIO[R, E, A]): ZIO.Acquire[R, E, A] =
    new ZIO.Acquire[R, E, A](() => acquire)

  /**
   * Acquires a resource, uses the resource, and then releases the resource.
   * Neither the acquisition nor the release will be interrupted, and the
   * resource is guaranteed to be released, so long as the `acquire` effect
   * succeeds. If `use` fails, then after release, the returned effect will fail
   * with the same error.
   */
  def acquireReleaseExitWith[R, E, A](acquire: => ZIO[R, E, A]): ZIO.AcquireExit[R, E, A] =
    new ZIO.AcquireExit(() => acquire)

  /**
   * Adds a finalizer to the scope of this effect. The finalizer is guaranteed
   * to be run when the scope is closed.
   */
  def addFinalizer[R](finalizer: => URIO[R, Any])(implicit trace: Trace): ZIO[R with Scope, Nothing, Any] =
    addFinalizerExit(_ => finalizer)

  /**
   * A more powerful variant of `addFinalizer` that allows the finalizer to
   * depend on the `Exit` value that the scope is closed with.
   */
  def addFinalizerExit[R](
    finalizer: Exit[Any, Any] => URIO[R, Any]
  )(implicit trace: Trace): ZIO[R with Scope, Nothing, Any] =
    for {
      environment <- ZIO.environment[R]
      scope       <- ZIO.scope
      _           <- scope.addFinalizerExit(exit => finalizer(exit).provideEnvironment(environment))
    } yield ()

  /**
   * Makes an explicit check to see if the fiber has been interrupted, and if
   * so, performs self-interruption
   */
  def allowInterrupt(implicit trace: Trace): UIO[Unit] =
    descriptorWith(d => if (d.interrupters.nonEmpty) interrupt else ZIO.unit)

  /**
   * Imports an asynchronous side-effect into a pure `ZIO` value. See
   * `asyncMaybe` for the more expressive variant of this function that can
   * return a value synchronously.
   *
   * The callback function `ZIO[R, E, A] => Any` must be called at most once.
   *
   * The list of fibers, that may complete the async callback, is used to
   * provide better diagnostics.
   */
  def async[R, E, A](
    register: (ZIO[R, E, A] => Unit) => Any,
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: Trace): ZIO[R, E, A] =
    Async(trace, register, blockingOn)

  /**
   * Imports an asynchronous side-effect into a ZIO effect. The side-effect has
   * the option of returning the value synchronously, which is useful in cases
   * where it cannot be determined if the effect is synchronous or asynchronous
   * until the side-effect is actually executed. The effect also has the option
   * of returning a canceler, which will be used by the runtime to cancel the
   * asynchronous effect if the fiber executing the effect is interrupted.
   *
   * If the register function returns a value synchronously, then the callback
   * function `ZIO[R, E, A] => Any` must not be called. Otherwise the callback
   * function must be called at most once.
   *
   * The list of fibers, that may complete the async callback, is used to
   * provide better diagnostics.
   */
  def asyncInterrupt[R, E, A](
    register: (ZIO[R, E, A] => Unit) => Either[URIO[R, Any], ZIO[R, E, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed {
      val cancelerRef = Ref.unsafeMake[URIO[R, Any]](ZIO.unit)

      ZIO
        .async[R, E, A] { k =>
          val result = register(k(_))

          result match {
            case Left(canceler) => cancelerRef.unsafeSet(canceler)
            case Right(done)    => k(done)
          }
        }
        .onInterrupt(cancelerRef.unsafeGet)
    }

  /**
   * Imports an asynchronous effect into a pure `ZIO` value. This formulation is
   * necessary when the effect is itself expressed in terms of `ZIO`.
   */
  def asyncZIO[R, E, A](
    register: (ZIO[R, E, A] => Unit) => ZIO[R, E, Any]
  )(implicit trace: Trace): ZIO[R, E, A] =
    for {
      p <- Promise.make[E, A]
      r <- ZIO.runtime[R]
      a <- ZIO.uninterruptibleMask { restore =>
             val f = register(k => r.unsafeRunAsync(k.intoPromise(p)))

             restore(f.catchAllCause(p.failCause)).fork *> restore(p.await)
           }
    } yield a

  /**
   * Imports an asynchronous effect into a pure `ZIO` value, possibly returning
   * the value synchronously.
   *
   * If the register function returns a value synchronously, then the callback
   * function `ZIO[R, E, A] => Any` must not be called. Otherwise the callback
   * function must be called at most once.
   *
   * The list of fibers, that may complete the async callback, is used to
   * provide better diagnostics.
   */
  def asyncMaybe[R, E, A](
    register: (ZIO[R, E, A] => Unit) => Option[ZIO[R, E, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: Trace): ZIO[R, E, A] =
    asyncInterrupt(
      register(_) match {
        case None      => Left(ZIO.unit)
        case Some(now) => Right(now)
      },
      blockingOn
    )

  /**
   * Imports a synchronous side-effect into a pure `ZIO` value, translating any
   * thrown exceptions into typed failed effects creating with `ZIO.fail`.
   *
   * {{{
   * def printLine(line: String): Task[Unit] = ZIO.attempt(println(line))
   * }}}
   */
  def attempt[A](effect: => A)(implicit trace: Trace): Task[A] =
    ZIO.unsafeStateful[Any, Throwable, A] { (fiberState, _) =>
      try {
        ZIO.succeedNow(effect)
      } catch {
        // FIXME: Attach stack trace to this cause:
        case t: Throwable if !fiberState.unsafeIsFatal(t) => throw new ZIOError(Cause.fail(t))
      }
    }

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   */
  def attemptBlocking[A](effect: => A)(implicit trace: Trace): Task[A] =
    blocking(ZIO.attempt(effect))

  /**
   * Imports a synchronous effect that does blocking IO into a pure value, with
   * a custom cancel effect.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via the cancel effect.
   */
  def attemptBlockingCancelable[R, A](effect: => A)(cancel: => URIO[R, Any])(implicit trace: Trace): RIO[R, A] =
    blocking(ZIO.attempt(effect)).fork.flatMap(_.join).onInterrupt(cancel)

  /**
   * Imports a synchronous effect that does blocking IO into a pure value,
   * refining the error type to `[[java.io.IOException]]`.
   */
  def attemptBlockingIO[A](effect: => A)(implicit trace: Trace): IO[IOException, A] =
    attemptBlocking(effect).refineToOrDie[IOException]

  /**
   * Locks the specified effect to the blocking thread pool.
   */
  def blocking[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.blockingExecutor.flatMap(zio.onExecutor(_))

  /**
   * Retrieves the executor for all blocking tasks.
   */
  def blockingExecutor(implicit trace: Trace): UIO[Executor] =
    FiberRef.currentBlockingExecutor.get

  /**
   * Checks the interrupt status, and produces the effect returned by the
   * specified callback.
   */
  def checkInterruptible[R, E, A](f: zio.InterruptStatus => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.unsafeStateful[R, E, A] { (_, status) =>
      f(InterruptStatus.fromBoolean(status.runtimeFlags.interruption))
    }

  /**
   * Retrieves the `Clock` service for this workflow.
   */
  def clock(implicit trace: Trace): UIO[Clock] =
    ZIO.clockWith(ZIO.succeedNow)

  /**
   * Retrieves the `Clock` service for this workflow and uses it to run the
   * specified workflow.
   */
  def clockWith[R, E, A](f: Clock => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.get(Clock.tag)))

  /**
   * Evaluate each effect in the structure from left to right, collecting the
   * the successful values and discarding the empty cases. For a parallel
   * version, see `collectPar`.
   */
  def collect[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(
    f: A => ZIO[R, Option[E], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    foreach[R, E, A, Option[B], Iterable](in)(a => f(a).unsome).map(_.flatten).map(bf.fromSpecific(in))

  /**
   * Evaluate each effect in the structure from left to right, collecting the
   * the successful values and discarding the empty cases. For a parallel
   * version, see `collectPar`.
   */
  def collect[R, E, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => ZIO[R, Option[E], (Key2, Value2)])(implicit trace: Trace): ZIO[R, E, Map[Key2, Value2]] =
    foreach[R, E, (Key, Value), Option[(Key2, Value2)], Iterable](map)(f.tupled(_).unsome).map(_.flatten.toMap)

  /**
   * Evaluate each effect in the structure from left to right, and collect the
   * results. For a parallel version, see `collectAllPar`.
   */
  def collectAll[R, E, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[ZIO[R, E, A]]
  )(implicit
    bf: BuildFrom[Collection[ZIO[R, E, A]], A, Collection[A]],
    trace: Trace
  ): ZIO[R, E, Collection[A]] =
    foreach(in)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure from left to right, and collect the
   * results. For a parallel version, see `collectAllPar`.
   */
  def collectAll[R, E, A](in: Set[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, Set[A]] =
    foreach(in)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure from left to right, and collect the
   * results. For a parallel version, see `collectAllPar`.
   */
  def collectAll[R, E, A: ClassTag](in: Array[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, Array[A]] =
    foreach(in)(ZIO.identityFn)

  /**
   * Evaluate effect if present, and return its result as `Option[A]`.
   */
  def collectAll[R, E, A](in: Option[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    foreach(in)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure from left to right, and collect the
   * results. For a parallel version, see `collectAllPar`.
   */
  def collectAll[R, E, A](in: NonEmptyChunk[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, NonEmptyChunk[A]] =
    foreach(in)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure from left to right, and discard the
   * results. For a parallel version, see `collectAllParDiscard`.
   */
  def collectAllDiscard[R, E, A](in: => Iterable[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, Unit] =
    foreachDiscard(in)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and collect the results.
   * For a sequential version, see `collectAll`.
   */
  def collectAllPar[R, E, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[ZIO[R, E, A]]
  )(implicit
    bf: BuildFrom[Collection[ZIO[R, E, A]], A, Collection[A]],
    trace: Trace
  ): ZIO[R, E, Collection[A]] =
    foreachPar(as)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and collect the results.
   * For a sequential version, see `collectAll`.
   */
  def collectAllPar[R, E, A](as: Set[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, Set[A]] =
    foreachPar(as)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and collect the results.
   * For a sequential version, see `collectAll`.
   */
  def collectAllPar[R, E, A: ClassTag](as: Array[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, Array[A]] =
    foreachPar(as)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and collect the results.
   * For a sequential version, see `collectAll`.
   */
  def collectAllPar[R, E, A](as: NonEmptyChunk[ZIO[R, E, A]])(implicit
    trace: Trace
  ): ZIO[R, E, NonEmptyChunk[A]] =
    foreachPar(as)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and discard the results.
   * For a sequential version, see `collectAllDiscard`.
   */
  def collectAllParDiscard[R, E, A](as: => Iterable[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, Unit] =
    foreachParDiscard(as)(ZIO.identityFn)

  /**
   * Evaluate and run each effect in the structure and collect discarding failed
   * ones.
   */
  def collectAllSuccesses[R, E, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[ZIO[R, E, A]]
  )(implicit bf: BuildFrom[Collection[ZIO[R, E, A]], A, Collection[A]], trace: Trace): URIO[R, Collection[A]] =
    collectAllWith(in.map(_.exit)) { case zio.Exit.Success(a) => a }.map(bf.fromSpecific(in))

  /**
   * Evaluate and run each effect in the structure in parallel, and collect
   * discarding failed ones.
   */
  def collectAllSuccessesPar[R, E, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[ZIO[R, E, A]]
  )(implicit bf: BuildFrom[Collection[ZIO[R, E, A]], A, Collection[A]], trace: Trace): URIO[R, Collection[A]] =
    collectAllWithPar(in.map(_.exit)) { case zio.Exit.Success(a) => a }.map(bf.fromSpecific(in))

  /**
   * Evaluate each effect in the structure with `collectAll`, and collect the
   * results with given partial function.
   */
  def collectAllWith[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[ZIO[R, E, A]])(
    f: PartialFunction[A, B]
  )(implicit
    bf: BuildFrom[Collection[ZIO[R, E, A]], B, Collection[B]],
    trace: Trace
  ): ZIO[R, E, Collection[B]] =
    ZIO.collectAll[R, E, A, Iterable](in).map(_.collect(f)).map(bf.fromSpecific(in))

  /**
   * Evaluate each effect in the structure with `collectAllPar`, and collect the
   * results with given partial function.
   */
  def collectAllWithPar[R, E, A, U, Collection[+Element] <: Iterable[Element]](in: Collection[ZIO[R, E, A]])(
    f: PartialFunction[A, U]
  )(implicit
    bf: BuildFrom[Collection[ZIO[R, E, A]], U, Collection[U]],
    trace: Trace
  ): ZIO[R, E, Collection[U]] =
    ZIO.collectAllPar[R, E, A, Iterable](in).map(_.collect(f)).map(bf.fromSpecific(in))

  /**
   * Collects the first element of the `Iterable[A]` for which the effectual
   * function `f` returns `Some`.
   */
  def collectFirst[R, E, A, B](
    as: => Iterable[A]
  )(f: A => ZIO[R, E, Option[B]])(implicit trace: Trace): ZIO[R, E, Option[B]] =
    succeed(as.iterator).flatMap { iterator =>
      def loop: ZIO[R, E, Option[B]] =
        if (iterator.hasNext) f(iterator.next()).flatMap(_.fold(loop)(some(_)))
        else none
      loop
    }

  /**
   * Evaluate each effect in the structure in parallel, collecting the the
   * successful values and discarding the empty cases.
   */
  def collectPar[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(
    f: A => ZIO[R, Option[E], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    foreachPar[R, E, A, Option[B], Iterable](in)(a => f(a).unsome).map(_.flatten).map(bf.fromSpecific(in))

  /**
   * Evaluate each effect in the structure from left to right, collecting the
   * the successful values and discarding the empty cases. For a parallel
   * version, see `collectPar`.
   */
  def collectPar[R, E, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => ZIO[R, Option[E], (Key2, Value2)])(implicit trace: Trace): ZIO[R, E, Map[Key2, Value2]] =
    foreachPar[R, E, (Key, Value), Option[(Key2, Value2)], Iterable](map)(f.tupled(_).unsome).map(_.flatten.toMap)

  /**
   * Similar to Either.cond, evaluate the predicate, return the given A as
   * success if predicate returns true, and the given E as error otherwise
   *
   * For effectful conditionals, see [[ZIO.ifZIO]]
   */
  def cond[E, A](predicate: => Boolean, result: => A, error: => E)(implicit trace: Trace): IO[E, A] =
    ZIO.suspendSucceed(if (predicate) ZIO.succeedNow(result) else ZIO.fail(error))

  /**
   * Retrieves the `Console` service for this workflow.
   */
  def console(implicit trace: Trace): UIO[Console] =
    ZIO.consoleWith(ZIO.succeedNow)

  /**
   * Retrieves the `Console` service for this workflow and uses it to run the
   * specified workflow.
   */
  def consoleWith[R, E, A](f: Console => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.get(Console.tag)))

  /**
   * Prints the specified message to the console for debugging purposes.
   */
  def debug(value: => Any)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(println(value))

  /**
   * Returns information about the current fiber, such as its identity.
   */
  def descriptor(implicit trace: Trace): UIO[Fiber.Descriptor] =
    descriptorWith(succeedNow)

  /**
   * Constructs an effect based on information about the current fiber, such as
   * its identity.
   */
  def descriptorWith[R, E, A](f: Fiber.Descriptor => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.unsafeStateful[R, E, A] { (fiberState, status) =>
      val descriptor =
        Fiber.Descriptor(
          fiberState.id,
          status,
          fiberState.unsafeGetFiberRef(FiberRef.interruptedCause).interruptors,
          fiberState.unsafeGetCurrentExecutor(),
          fiberState.unsafeGetFiberRef(FiberRef.overrideExecutor).isDefined
        )

      f(descriptor)
    }

  /**
   * Returns an effect that dies with the specified `Throwable`. This method can
   * be used for terminating a fiber because a defect has been detected in the
   * code.
   */
  def die(t: => Throwable)(implicit trace: Trace): UIO[Nothing] =
    failCause(Cause.Die(t, StackTrace.none))

  /**
   * Returns an effect that dies with a [[java.lang.RuntimeException]] having
   * the specified text message. This method can be used for terminating a fiber
   * because a defect has been detected in the code.
   */
  def dieMessage(message: => String)(implicit trace: Trace): UIO[Nothing] =
    failCause(Cause.stackless(Cause.die(new RuntimeException(message))))

  /**
   * Returns an effect from a [[zio.Exit]] value.
   */
  def done[E, A](r: => Exit[E, A])(implicit trace: Trace): IO[E, A] =
    ZIO.suspendSucceed {
      r match {
        case Exit.Success(b)     => succeedNow(b)
        case Exit.Failure(cause) => failCause(cause)
      }
    }

  /**
   * Accesses the whole environment of the effect.
   */
  def environment[R](implicit trace: Trace): URIO[R, ZEnvironment[R]] =
    ZIO.suspendSucceed(FiberRef.currentEnvironment.get.asInstanceOf[URIO[R, ZEnvironment[R]]])

  /**
   * Accesses the environment of the effect.
   */
  def environmentWith[R]: ZIO.EnvironmentWithPartiallyApplied[R] =
    new ZIO.EnvironmentWithPartiallyApplied[R]

  /**
   * Effectually accesses the environment of the effect.
   */
  def environmentWithZIO[R]: ZIO.EnvironmentWithZIOPartiallyApplied[R] =
    new ZIO.EnvironmentWithZIOPartiallyApplied[R]

  /**
   * Retrieves the executor for this effect.
   */
  def executor(implicit trace: Trace): UIO[Executor] =
    ZIO.descriptorWith(descriptor => ZIO.succeedNow(descriptor.executor))

  /**
   * Constructs an effect based on the current executor.
   */
  def executorWith[R, E, A](f: Executor => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.descriptorWith(descriptor => f(descriptor.executor))

  /**
   * Determines whether any element of the `Iterable[A]` satisfies the effectual
   * predicate `f`.
   */
  def exists[R, E, A](
    as: => Iterable[A]
  )(f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Boolean] =
    succeed(as.iterator).flatMap { iterator =>
      def loop: ZIO[R, E, Boolean] =
        if (iterator.hasNext) f(iterator.next()).flatMap(b => if (b) succeedNow(b) else loop)
        else succeedNow(false)
      loop
    }

  /**
   * Returns an effect that models failure with the specified error. The moral
   * equivalent of `throw` for pure code.
   */
  def fail[E](error: => E)(implicit trace: Trace): IO[E, Nothing] =
    failCause(Cause.fail(error))

  /**
   * Returns an effect that models failure with the specified `Cause`.
   */
  def failCause[E](cause: => Cause[E])(implicit trace0: Trace): IO[E, Nothing] =
    ZIO.trace(trace0).flatMap(trace => refailCause(cause.traced(trace)))

  /**
   * Returns the `FiberId` of the fiber executing the effect that calls this
   * method.
   */
  def fiberId(implicit trace: Trace): UIO[FiberId.Runtime] =
    fiberIdWith(fiberId => ZIO.succeedNow(fiberId))

  /**
   * Constructs an effect based on the `FiberId` of the fiber executing the
   * effect that calls this method.
   */
  def fiberIdWith[R, E, A](f: FiberId.Runtime => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.descriptorWith(descriptor => f(descriptor.id))

  /**
   * Filters the collection using the specified effectual predicate.
   */
  def filter[R, E, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => ZIO[R, E, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): ZIO[R, E, Collection[A]] =
    as.foldLeft[ZIO[R, E, Builder[A, Collection[A]]]](ZIO.succeed(bf.newBuilder(as))) { (zio, a) =>
      zio.zipWith(f(a))((as, p) => if (p) as += a else as)
    }.map(_.result())

  /**
   * Filters the Set[A] using the specified effectual predicate.
   */
  def filter[R, E, A](as: Set[A])(f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Set[A]] =
    filter[R, E, A, Iterable](as)(f).map(_.toSet)

  /**
   * Filters the collection in parallel using the specified effectual predicate.
   *
   * See [[[zio.ZIO.filter[R,E,A,Collection*]]] for a sequential version.
   */
  def filterPar[R, E, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => ZIO[R, E, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): ZIO[R, E, Collection[A]] =
    ZIO
      .foreachPar[R, E, A, Option[A], Iterable](as)(a => f(a).map(if (_) Some(a) else None))
      .map(_.flatten)
      .map(bf.fromSpecific(as))

  /**
   * Filters the Set[A] in parallel using the specified effectual predicate.
   *
   * See [[[zio.ZIO.filter[R,E,A,Collection*]]] for a sequential version.
   */
  def filterPar[R, E, A](as: Set[A])(f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Set[A]] =
    filterPar[R, E, A, Iterable](as)(f).map(_.toSet)

  /**
   * Filters the collection using the specified effectual predicate, removing
   * all elements that satisfy the predicate.
   */
  def filterNot[R, E, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => ZIO[R, E, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): ZIO[R, E, Collection[A]] =
    filter(as)(f(_).map(!_))

  /**
   * Filters the Set[A] using the specified effectual predicate, removing all
   * elements that satisfy the predicate.
   */
  def filterNot[R, E, A](as: Set[A])(f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Set[A]] =
    filterNot[R, E, A, Iterable](as)(f).map(_.toSet)

  /**
   * Filters the collection in parallel using the specified effectual predicate,
   * emoving all elements that satisfy the predicate.
   *
   * See [[[zio.ZIO.filterNot[R,E,A,Collection*]]] for a sequential version.
   */
  def filterNotPar[R, E, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => ZIO[R, E, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): ZIO[R, E, Collection[A]] =
    filterPar(as)(f(_).map(!_))

  /**
   * Filters the Set[A] in parallel using the specified effectual predicate,
   * removing all elements that satisfy the predicate.
   *
   * See [[[zio.ZIO.filterNot[R,E,A](as:Set*]]] for a sequential version.
   */
  def filterNotPar[R, E, A](as: Set[A])(f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Set[A]] =
    filterNotPar[R, E, A, Iterable](as)(f).map(_.toSet)

  /**
   * Returns an effect that runs the first effect and in case of failure, runs
   * each of the specified effects in order until one of them succeeds.
   */
  def firstSuccessOf[R, R1 <: R, E, A](
    zio: => ZIO[R, E, A],
    rest: => Iterable[ZIO[R1, E, A]]
  )(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.suspendSucceed(rest.foldLeft[ZIO[R1, E, A]](zio)(_ orElse _))

  /**
   * Returns an effect that first executes the outer effect, and then executes
   * the inner effect, returning the value from the inner effect, and
   * effectively flattening a nested effect.
   */
  def flatten[R, E, A](zio: => ZIO[R, E, ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(zio.flatMap(ZIO.identityFn))

  /**
   * Folds an Iterable[A] using an effectual function f, working sequentially
   * from left to right.
   */
  def foldLeft[R, E, S, A](
    in: => Iterable[A]
  )(zero: => S)(f: (S, A) => ZIO[R, E, S])(implicit trace: Trace): ZIO[R, E, S] =
    ZIO.suspendSucceed(in.foldLeft[ZIO[R, E, S]](ZIO.succeedNow(zero))((acc, el) => acc.flatMap(f(_, el))))

  /**
   * Folds an Iterable[A] using an effectual function f, working sequentially
   * from right to left.
   */
  def foldRight[R, E, S, A](
    in: => Iterable[A]
  )(zero: => S)(f: (A, S) => ZIO[R, E, S])(implicit trace: Trace): ZIO[R, E, S] =
    ZIO.suspendSucceed(in.foldRight(ZIO.succeedNow(zero): ZIO[R, E, S])((el, acc) => acc.flatMap(f(el, _))))

  /**
   * Determines whether all elements of the `Iterable[A]` satisfy the effectual
   * predicate `f`.
   */
  def forall[R, E, A](
    as: => Iterable[A]
  )(f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Boolean] =
    succeed(as.iterator).flatMap { iterator =>
      def loop: ZIO[R, E, Boolean] =
        if (iterator.hasNext) f(iterator.next()).flatMap(b => if (b) loop else succeedNow(b))
        else succeedNow(true)
      loop
    }

  /**
   * Applies the function `f` to each element of the `Collection[A]` and returns
   * the results in a new `Collection[B]`.
   *
   * For a parallel version of this method, see `foreachPar`. If you do not need
   * the results, see `foreach_` for a more efficient implementation.
   */
  def foreach[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    ZIO.suspendSucceed {
      val iterator = in.iterator
      val builder  = bf.newBuilder(in)

      lazy val recurse: B => ZIO[R, E, Collection[B]] = { b =>
        builder += b
        loop()
      }

      def loop(): ZIO[R, E, Collection[B]] =
        if (iterator.hasNext) f(iterator.next()).flatMap(recurse)
        else ZIO.succeedNow(builder.result())

      loop()
    }

  /**
   * Applies the function `f` to each element of the `Set[A]` and returns the
   * results in a new `Set[B]`.
   *
   * For a parallel version of this method, see `foreachPar`. If you do not need
   * the results, see `foreachDiscard` for a more efficient implementation.
   */
  final def foreach[R, E, A, B](in: Set[A])(f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, Set[B]] =
    foreach[R, E, A, B, Iterable](in)(f).map(_.toSet)

  /**
   * Applies the function `f` to each element of the `Array[A]` and returns the
   * results in a new `Array[B]`.
   *
   * For a parallel version of this method, see `foreachPar`. If you do not need
   * the results, see `foreachDiscard` for a more efficient implementation.
   */
  final def foreach[R, E, A, B: ClassTag](in: Array[A])(f: A => ZIO[R, E, B])(implicit
    trace: Trace
  ): ZIO[R, E, Array[B]] =
    foreach[R, E, A, B, Iterable](in)(f).map(_.toArray)

  /**
   * Applies the function `f` to each element of the `Map[Key, Value]` and
   * returns the results in a new `Map[Key2, Value2]`.
   *
   * For a parallel version of this method, see `foreachPar`. If you do not need
   * the results, see `foreachDiscard` for a more efficient implementation.
   */
  def foreach[R, E, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => ZIO[R, E, (Key2, Value2)])(implicit trace: Trace): ZIO[R, E, Map[Key2, Value2]] =
    foreach[R, E, (Key, Value), (Key2, Value2), Iterable](map)(f.tupled).map(_.toMap)

  /**
   * Applies the function `f` if the argument is non-empty and returns the
   * results in a new `Option[B]`.
   */
  final def foreach[R, E, A, B](in: Option[A])(f: A => ZIO[R, E, B])(implicit
    trace: Trace
  ): ZIO[R, E, Option[B]] =
    in.fold[ZIO[R, E, Option[B]]](none)(f(_).map(Some(_)))

  /**
   * Applies the function `f` to each element of the `NonEmptyChunk[A]` and
   * returns the results in a new `NonEmptyChunk[B]`.
   *
   * For a parallel version of this method, see `foreachPar`. If you do not need
   * the results, see `foreachDiscard` for a more efficient implementation.
   */
  final def foreach[R, E, A, B](in: NonEmptyChunk[A])(f: A => ZIO[R, E, B])(implicit
    trace: Trace
  ): ZIO[R, E, NonEmptyChunk[B]] =
    in.mapZIO(f)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and runs
   * produced effects sequentially.
   *
   * Equivalent to `foreach(as)(f).unit`, but without the cost of building the
   * list of results.
   */
  def foreachDiscard[R, E, A](
    as: => Iterable[A]
  )(f: A => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.succeed(as.iterator).flatMap { i =>
      lazy val recurse: Any => ZIO[R, E, Unit] = _ => loop()

      def loop(): ZIO[R, E, Unit] =
        if (i.hasNext) f(i.next()).flatMap(recurse)
        else ZIO.unit

      loop()
    }

  /**
   * Applies the function `f` to each element of the `Collection[A]` and returns
   * the result in a new `Collection[B]` using the specified execution strategy.
   */
  final def foreachExec[R, E, A, B, Collection[+Element] <: Iterable[Element]](as: Collection[A])(
    exec: => ExecutionStrategy
  )(
    f: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    ZIO.suspendSucceed {
      exec match {
        case ExecutionStrategy.Parallel     => ZIO.foreachPar(as)(f).withParallelismUnbounded
        case ExecutionStrategy.ParallelN(n) => ZIO.foreachPar(as)(f).withParallelism(n)
        case ExecutionStrategy.Sequential   => ZIO.foreach(as)(f)
      }
    }

  /**
   * Applies the function `f` to each element of the `Collection[A]` in
   * parallel, and returns the results in a new `Collection[B]`.
   *
   * For a sequential version of this method, see `foreach`.
   */
  def foreachPar[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    ZIO.parallelismWith {
      case Some(n) => foreachPar(n)(as)(f)
      case None    => foreachParUnbounded(as)(f)
    }

  /**
   * Applies the function `f` to each element of the `Set[A]` in parallel, and
   * returns the results in a new `Set[B]`.
   *
   * For a sequential version of this method, see `foreach`.
   */
  final def foreachPar[R, E, A, B](as: Set[A])(fn: A => ZIO[R, E, B])(implicit
    trace: Trace
  ): ZIO[R, E, Set[B]] =
    foreachPar[R, E, A, B, Iterable](as)(fn).map(_.toSet)

  /**
   * Applies the function `f` to each element of the `Array[A]` in parallel, and
   * returns the results in a new `Array[B]`.
   *
   * For a sequential version of this method, see `foreach`.
   */
  final def foreachPar[R, E, A, B: ClassTag](as: Array[A])(f: A => ZIO[R, E, B])(implicit
    trace: Trace
  ): ZIO[R, E, Array[B]] =
    foreachPar[R, E, A, B, Iterable](as)(f).map(_.toArray)

  /**
   * Applies the function `f` to each element of the `Map[Key, Value]` in
   * parallel and returns the results in a new `Map[Key2, Value2]`.
   *
   * For a sequential version of this method, see `foreach`.
   */
  def foreachPar[R, E, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => ZIO[R, E, (Key2, Value2)])(implicit trace: Trace): ZIO[R, E, Map[Key2, Value2]] =
    foreachPar[R, E, (Key, Value), (Key2, Value2), Iterable](map)(f.tupled).map(_.toMap)

  /**
   * Applies the function `f` to each element of the `NonEmptyChunk[A]` in
   * parallel, and returns the results in a new `NonEmptyChunk[B]`.
   *
   * For a sequential version of this method, see `foreach`.
   */
  final def foreachPar[R, E, A, B](as: NonEmptyChunk[A])(fn: A => ZIO[R, E, B])(implicit
    trace: Trace
  ): ZIO[R, E, NonEmptyChunk[B]] =
    as.mapZIOPar(fn)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and runs
   * produced effects in parallel, discarding the results.
   *
   * For a sequential version of this method, see `foreachDiscard`.
   *
   * Optimized to avoid keeping full tree of effects, so that method could be
   * able to handle large input sequences. Behaves almost like this code:
   *
   * {{{
   * as.foldLeft(ZIO.unit) { (acc, a) => acc.zipParLeft(f(a)) }
   * }}}
   *
   * Additionally, interrupts all effects on any failure.
   */
  def foreachParDiscard[R, E, A](
    as: => Iterable[A]
  )(f: A => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.parallelismWith {
      case Some(n) => foreachParDiscard(n)(as)(f)
      case None    => foreachParUnboundedDiscard(as)(f)
    }

  /**
   * Returns an effect that forks all of the specified values, and returns a
   * composite fiber that produces a list of their results, in order.
   */
  def forkAll[R, E, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[ZIO[R, E, A]]
  )(implicit
    bf: BuildFrom[Collection[ZIO[R, E, A]], A, Collection[A]],
    trace: Trace
  ): URIO[R, Fiber[E, Collection[A]]] =
    ZIO
      .foreach[R, Nothing, ZIO[R, E, A], Fiber[E, A], Iterable](as)(_.fork)
      .map(Fiber.collectAll[E, A, Iterable](_).map(bf.fromSpecific(as)))

  /**
   * Returns an effect that forks all of the specified values, and returns a
   * composite fiber that produces unit. This version is faster than [[forkAll]]
   * in cases where the results of the forked fibers are not needed.
   */
  def forkAllDiscard[R, E, A](as: => Iterable[ZIO[R, E, A]])(implicit trace: Trace): URIO[R, Unit] =
    ZIO.suspendSucceed(as.foldRight[URIO[R, Unit]](ZIO.unit)(_.fork *> _))

  /**
   * Constructs a `ZIO` value of the appropriate type for the specified input.
   */
  def from[Input](input: => Input)(implicit
    constructor: ZIOConstructor[Nothing, Any, Input],
    trace: Trace
  ): ZIO[constructor.OutEnvironment, constructor.OutError, constructor.OutSuccess] =
    constructor.make(input)

  def fromAutoCloseable[R, E, A <: AutoCloseable](fa: => ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R with Scope, E, A] =
    acquireRelease(fa)(a => ZIO.succeed(a.close()))

  /**
   * Lifts an `Either` into a `ZIO` value.
   */
  def fromEither[E, A](v: => Either[E, A])(implicit trace: Trace): IO[E, A] =
    succeed(v).flatMap(_.fold(fail(_), succeedNow))

  /**
   * Lifts an `Either` into a `ZIO` value.
   */
  def fromEitherCause[E, A](v: => Either[Cause[E], A])(implicit trace: Trace): IO[E, A] =
    succeed(v).flatMap(_.fold(failCause(_), succeedNow))

  /**
   * Creates a `ZIO` value that represents the exit value of the specified
   * fiber.
   */
  def fromFiber[E, A](fiber: => Fiber[E, A])(implicit trace: Trace): IO[E, A] =
    succeed(fiber).flatMap(_.join)

  /**
   * Creates a `ZIO` value that represents the exit value of the specified
   * fiber.
   */
  def fromFiberZIO[R, E, A](fiber: => ZIO[R, E, Fiber[E, A]])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(fiber.flatMap(_.join))

  /**
   * Imports a function that creates a [[scala.concurrent.Future]] from an
   * [[scala.concurrent.ExecutionContext]] into a `ZIO`.
   */
  def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A])(implicit trace: Trace): Task[A] =
    ZIO.descriptorWith { d =>
      val ec = d.executor.asExecutionContext
      ZIO.attempt(make(ec)).flatMap { f =>
        val canceler: UIO[Unit] = f match {
          case cancelable: CancelableFuture[A] =>
            ZIO.suspendSucceed(if (f.isCompleted) ZIO.unit else ZIO.fromFuture(_ => cancelable.cancel()).ignore)
          case _ => ZIO.unit
        }

        f.value
          .fold(
            ZIO.asyncInterrupt { (k: Task[A] => Unit) =>
              f.onComplete {
                case Success(a) => k(ZIO.succeedNow(a))
                case Failure(t) => k(ZIO.fail(t))
              }(ec)

              Left(canceler)
            }
          )(ZIO.fromTry(_))
      }
    }

  /**
   * Imports a [[scala.concurrent.Promise]] we generate a future from promise,
   * and we pass to [fromFuture] to transform into Task[A]
   */
  def fromPromiseScala[A](promise: => scala.concurrent.Promise[A])(implicit trace: Trace): Task[A] =
    ZIO.fromFuture(_ => promise.future)

  /**
   * Imports a function that creates a [[scala.concurrent.Future]] from an
   * [[scala.concurrent.ExecutionContext]] into a `ZIO`. The provided
   * `ExecutionContext` will interrupt the `Future` between asynchronous
   * operations such as `map` and `flatMap` if this effect is interrupted. Note
   * that no attempt will be made to interrupt a `Future` blocking on a
   * synchronous operation and that the `Future` must be created using the
   * provided `ExecutionContext`.
   */
  def fromFutureInterrupt[A](
    make: ExecutionContext => scala.concurrent.Future[A]
  )(implicit trace: Trace): Task[A] =
    ZIO.descriptorWith { d =>
      val ec          = d.executor.asExecutionContext
      val interrupted = new java.util.concurrent.atomic.AtomicBoolean(false)
      val latch       = scala.concurrent.Promise[Unit]()
      val interruptibleEC = new scala.concurrent.ExecutionContext {
        def execute(runnable: Runnable): Unit =
          if (!interrupted.get) ec.execute(runnable)
          else {
            val _ = latch.success(())
          }
        def reportFailure(cause: Throwable): Unit =
          ec.reportFailure(cause)
      }
      attempt(make(interruptibleEC)).flatMap { f =>
        f.value
          .fold(
            ZIO.async { (cb: Task[A] => Any) =>
              f.onComplete {
                case Success(a) => latch.success(()); cb(ZIO.succeedNow(a))
                case Failure(t) => latch.success(()); cb(ZIO.fail(t))
              }(interruptibleEC)
            }
          )(ZIO.fromTry(_))
      }.onInterrupt(
        ZIO.succeed(interrupted.set(true)) *> ZIO.fromFuture(_ => latch.future).orDie
      )
    }

  /**
   * Lifts an `Option` into a `ZIO` but preserves the error as an option in the
   * error channel, making it easier to compose in some scenarios.
   */
  def fromOption[A](v: => Option[A])(implicit trace: Trace): IO[Option[Nothing], A] =
    succeed(v).flatMap(_.fold[IO[Option[Nothing], A]](fail(None))(succeedNow))

  /**
   * Lifts a `Try` into a `ZIO`.
   */
  def fromTry[A](value: => scala.util.Try[A])(implicit trace: Trace): Task[A] =
    attempt(value).flatMap {
      case scala.util.Success(v) => ZIO.succeedNow(v)
      case scala.util.Failure(t) => ZIO.fail(t)
    }

  /**
   * Returns a collection of all `FiberRef` values for the fiber running this
   * effect.
   */
  def getFiberRefs(implicit trace: Trace): UIO[FiberRefs] =
    ZIO.unsafeStateful[Any, Nothing, FiberRefs] { (fiberState, _) =>
      ZIO.succeedNow(fiberState.unsafeGetFiberRefs())
    }

  /**
   * Lifts an Option into a ZIO, if the option is not defined it fails with
   * NoSuchElementException.
   */
  final def getOrFail[A](v: => Option[A])(implicit trace: Trace): Task[A] =
    getOrFailWith(new NoSuchElementException("None.get"))(v)

  /**
   * Lifts an Option into a IO, if the option is not defined it fails with Unit.
   */
  final def getOrFailUnit[A](v: => Option[A])(implicit trace: Trace): IO[Unit, A] =
    getOrFailWith(())(v)

  /**
   * Lifts an Option into a ZIO. If the option is not defined, fail with the `e`
   * value.
   */
  final def getOrFailWith[E, A](e: => E)(v: => Option[A])(implicit trace: Trace): IO[E, A] =
    suspendSucceed(v match {
      case None    => ZIO.fail(e)
      case Some(v) => ZIO.succeedNow(v)
    })

  /**
   * Gets a state from the environment.
   */
  def getState[S: EnvironmentTag](implicit trace: Trace): ZIO[ZState[S], Nothing, S] =
    ZIO.serviceWithZIO(_.get)

  /**
   * Gets a state from the environment and uses it to run the specified
   * function.
   */
  def getStateWith[S]: ZIO.GetStateWithPartiallyApplied[S] =
    new ZIO.GetStateWithPartiallyApplied[S]

  /**
   * Runs `onTrue` if the result of `b` is `true` and `onFalse` otherwise.
   */
  def ifZIO[R, E](b: => ZIO[R, E, Boolean]): ZIO.IfZIO[R, E] =
    new ZIO.IfZIO(() => b)

  /**
   * Like [[never]], but fibers that running this effect won't be garbage
   * collected unless interrupted.
   */
  def infinity(implicit trace: Trace): UIO[Nothing] =
    ZIO.sleep(Duration.fromNanos(Long.MaxValue)) *> ZIO.never

  /**
   * Returns an effect that is interrupted as if by the fiber calling this
   * method.
   */
  def interrupt(implicit trace: Trace): UIO[Nothing] =
    descriptorWith(descriptor => interruptAs(descriptor.id))

  /**
   * Returns an effect that is interrupted as if by the specified fiber.
   */
  def interruptAs(fiberId: => FiberId)(implicit trace: Trace): UIO[Nothing] =
    failCause(Cause.interrupt(fiberId))

  /**
   * Prefix form of `ZIO#interruptible`.
   */
  def interruptible[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(zio.interruptible)

  /**
   * Makes the effect interruptible, but passes it a restore function that can
   * be used to restore the inherited interruptibility from whatever region the
   * effect is composed into.
   */
  def interruptibleMask[R, E, A](
    k: ZIO.InterruptibilityRestorer => ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    checkInterruptible(flag => k(ZIO.InterruptibilityRestorer(flag)).interruptible)

  /**
   * Retrieves the definition of a fatal error.
   */
  def isFatal(implicit trace: Trace): UIO[Throwable => Boolean] =
    isFatalWith(isFatal => ZIO.succeedNow(isFatal))

  /**
   * Constructs an effect based on the definition of a fatal error.
   */
  def isFatalWith[R, E, A](f: (Throwable => Boolean) => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    FiberRef.currentFatal.getWith(fatal => f(t => fatal.exists(_.isAssignableFrom(t.getClass))))

  /**
   * Iterates with the specified effectual function. The moral equivalent of:
   *
   * {{{
   * var s = initial
   *
   * while (cont(s)) {
   *   s = body(s)
   * }
   *
   * s
   * }}}
   */
  def iterate[R, E, S](
    initial: => S
  )(cont: S => Boolean)(body: S => ZIO[R, E, S])(implicit trace: Trace): ZIO[R, E, S] =
    ZIO.suspendSucceed {
      val ref = new java.util.concurrent.atomic.AtomicReference[S](initial)

      ZIO
        .whileLoop(ref)(ref => cont(ref.get)) { ref =>
          body(ref.get).map { input =>
            ref.set(input)
          }
        }
        .as(ref.get)
    }

  def whileLoop[R, E, S, T](
    create: => S
  )(check: S => Boolean)(process: S => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, S] =
    ZIO.WhileLoop(trace, () => create, check, process)

  /**
   * Returns an effect with the value on the left part.
   */
  def left[A](a: => A)(implicit trace: Trace): UIO[Either[A, Nothing]] =
    succeed(Left(a))

  /**
   * Retrieves the current loggers for this effect.
   */
  def loggers(implicit trace: Trace): UIO[Set[ZLogger[String, Any]]] =
    FiberRef.currentLoggers.get

  /**
   * Constructs an effect based on the current loggers.
   */
  def loggersWith[R, E, A](f: Set[ZLogger[String, Any]] => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    FiberRef.currentLoggers.getWith(f)

  /**
   * Loops with the specified effectual function, collecting the results into a
   * list. The moral equivalent of:
   *
   * {{{
   * var s  = initial
   * var as = List.empty[A]
   *
   * while (cont(s)) {
   *   as = body(s) :: as
   *   s  = inc(s)
   * }
   *
   * as.reverse
   * }}}
   */
  def loop[R, E, A, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, List[A]] =
    ZIO.suspendSucceed {

      def loop(initial: S): ZIO[R, E, List[A]] =
        if (cont(initial))
          body(initial).flatMap(a => loop(inc(initial)).map(as => a :: as))
        else
          ZIO.succeedNow(List.empty[A])

      loop(initial)
    }

  /**
   * Loops with the specified effectual function purely for its effects. The
   * moral equivalent of:
   *
   * {{{
   * var s = initial
   *
   * while (cont(s)) {
   *   body(s)
   *   s = inc(s)
   * }
   * }}}
   */
  def loopDiscard[R, E, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.suspendSucceed {

      def loop(initial: S): ZIO[R, E, Unit] =
        if (cont(initial)) body(initial) *> loop(inc(initial))
        else ZIO.unit

      loop(initial)
    }

  /**
   * Returns a new effect where boolean value of this effect is negated.
   */
  def not[R, E](effect: => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Boolean] =
    ZIO.suspendSucceed(effect.negate)

  /**
   * Logs the specified message at the current log level.
   */
  def log(message: => String)(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.unsafeLog(() => message, Cause.empty, None, trace)

      ZIO.unit
    }

  /**
   * Logs the specified cause at the current log level.
   */
  def logCause(cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    logCause("An error occurred", cause)

  /**
   * Logs the specified message and cause at the current log level.
   */
  def logCause(message: => String, cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.unsafeLog(() => message, cause, None, trace)

      ZIO.unit
    }

  /**
   * Annotates each log in this effect with the specified log annotation.
   */
  def logAnnotate(key: => String, value: => String): LogAnnotate =
    logAnnotate(LogAnnotation(key, value))

  /**
   * Annotates each log in this effect with the specified log annotation.
   */
  def logAnnotate(logAnnotation: => LogAnnotation, logAnnotations: LogAnnotation*): LogAnnotate =
    logAnnotate(Set(logAnnotation) ++ logAnnotations.toSet)

  /**
   * Annotates each log in this effect with the specified log annotation.
   */
  def logAnnotate(logAnnotations: => Set[LogAnnotation]): LogAnnotate =
    new LogAnnotate(() => logAnnotations)

  /**
   * Retrieves the log annotations associated with the current scope.
   */
  def logAnnotations(implicit trace: Trace): UIO[Map[String, String]] =
    FiberRef.currentLogAnnotations.get

  /**
   * Logs the specified message at the debug log level.
   */
  def logDebug(message: => String)(implicit trace: Trace): UIO[Unit] =
    logDebugCause(message, Cause.empty)

  /**
   * Logs the specified cause at the debug log level.
   */
  def logDebugCause(message: => String, cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.unsafeLog(() => message, cause, someDebug, trace)

      ZIO.unit
    }

  /**
   * Logs the specified cause at the debug log level..
   */
  def logDebugCause(cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    logDebugCause("", cause)

  /**
   * Logs the specified message at the error log level.
   */
  def logError(message: => String)(implicit trace: Trace): UIO[Unit] =
    logErrorCause(message, Cause.empty)

  /**
   * Logs the specified cause as an error.
   */
  def logErrorCause(message: => String, cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.unsafeLog(() => message, cause, someError, trace)

      ZIO.unit
    }

  /**
   * Logs the specified cause as an error.
   */
  def logErrorCause(cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    logErrorCause("", cause)

  /**
   * Logs the specified message at the fatal log level.
   */
  def logFatal(message: => String)(implicit trace: Trace): UIO[Unit] =
    logFatalCause(message, Cause.empty)

  /**
   * Logs the specified cause at the fatal log level.
   */
  def logFatalCause(message: => String, cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.unsafeLog(() => message, cause, someFatal, trace)

      ZIO.unit
    }

  /**
   * Logs the specified cause at the fatal log level.
   */
  def logFatalCause(cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    logFatalCause("", cause)

  /**
   * Logs the specified message at the informational log level.
   */
  def logInfo(message: => String)(implicit trace: Trace): UIO[Unit] =
    logInfoCause(message, Cause.empty)

  /**
   * Logs the specified cause at the informational log level.
   */
  def logInfoCause(message: => String, cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.unsafeLog(() => message, cause, someInfo, trace)

      ZIO.unit
    }

  /**
   * Logs the specified cause at the informational log level..
   */
  def logInfoCause(cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    logInfoCause("", cause)

  /**
   * Sets the log level for this effect.
   * {{{
   * ZIO.logLevel(LogLevel.Warning) {
   *   ZIO.log("The response time exceeded its threshold!")
   * }
   * }}}
   */
  def logLevel(level: LogLevel): LogLevel =
    level

  /**
   * Adjusts the label for the current logging span.
   * {{{
   * ZIO.logSpan("parsing") { parseRequest(req) }
   * }}}
   */
  def logSpan(label: => String): LogSpan = new LogSpan(() => label)

  /**
   * Logs the specified message at the trace log level.
   */
  def logTrace(message: => String)(implicit trace: Trace): UIO[Unit] =
    logTraceCause(message, Cause.empty)

  /**
   * Logs the specified cause at the trace log level.
   */
  def logTraceCause(message: => String, cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.unsafeLog(() => message, cause, someTrace, trace)

      ZIO.unit
    }

  /**
   * Logs the specified cause at the trace log level..
   */
  def logTraceCause(cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    logTraceCause("", cause)

  /**
   * Logs the specified message at the warning log level.
   */
  def logWarning(message: => String)(implicit trace: Trace): UIO[Unit] =
    logWarningCause(message, Cause.empty)

  /**
   * Logs the specified cause at the warning log level.
   */
  def logWarningCause(message: => String, cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.unsafeLog(() => message, cause, someWarning, trace)

      ZIO.unit
    }

  /**
   * Logs the specified cause at the warning log level..
   */
  def logWarningCause(cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    logWarningCause("", cause)

  /**
   * Returns a memoized version of the specified effectual function.
   */
  def memoize[R, E, A, B](f: A => ZIO[R, E, B])(implicit trace: Trace): UIO[A => ZIO[R, E, B]] =
    Ref.Synchronized.make(Map.empty[A, Promise[E, B]]).map { ref => a =>
      for {
        promise <- ref.modifyZIO { map =>
                     map.get(a) match {
                       case Some(promise) => ZIO.succeedNow((promise, map))
                       case None =>
                         for {
                           promise <- Promise.make[E, B]
                           _       <- f(a).intoPromise(promise).fork
                         } yield (promise, map + (a -> promise))
                     }
                   }
        b <- promise.await
      } yield b
    }

  /**
   * Merges an `Iterable[IO]` to a single IO, working sequentially.
   */
  def mergeAll[R, E, A, B](
    in: => Iterable[ZIO[R, E, A]]
  )(zero: => B)(f: (B, A) => B)(implicit trace: Trace): ZIO[R, E, B] =
    ZIO.suspendSucceed(in.foldLeft[ZIO[R, E, B]](succeedNow(zero))(_.zipWith(_)(f)))

  /**
   * Merges an `Iterable[IO]` to a single IO, working in parallel.
   *
   * Due to the parallel nature of this combinator, `f` must be both:
   *   - commutative: `f(a, b) == f(b, a)`
   *   - associative: `f(a, f(b, c)) == f(f(a, b), c)`
   *
   * It's unsafe to execute side effects inside `f`, as `f` may be executed more
   * than once for some of `in` elements during effect execution.
   */
  def mergeAllPar[R, E, A, B](
    in: => Iterable[ZIO[R, E, A]]
  )(zero: => B)(f: (B, A) => B)(implicit trace: Trace): ZIO[R, E, B] =
    Ref.make(zero).flatMap(acc => foreachParDiscard(in)(_.flatMap(a => acc.update(f(_, a)))) *> acc.get)

  /**
   * Returns a effect that will never produce anything. The moral equivalent of
   * `while(true) {}`, only without the wasted CPU cycles. Fibers that suspended
   * running this effect are automatically garbage collected on the JVM, because
   * they cannot be reactivated.
   */
  def never(implicit trace: Trace): UIO[Nothing] =
    async[Any, Nothing, Nothing](_ => ())

  /**
   * Returns an effect with the empty value.
   */
  lazy val none: UIO[Option[Nothing]] = succeedNow(None)

  /**
   * Lifts an Option into a ZIO. If the option is empty it succeeds with Unit.
   * If the option is defined it fails with the content.
   */
  def noneOrFail[E](o: => Option[E])(implicit trace: Trace): IO[E, Unit] =
    getOrFailUnit(o).flip

  /**
   * Lifts an Option into a ZIO. If the option is empty it succeeds with Unit.
   * If the option is defined it fails with an error adapted with f.
   */
  def noneOrFailWith[E, O](o: => Option[O])(f: O => E)(implicit trace: Trace): IO[E, Unit] =
    getOrFailUnit(o).flip.mapError(f)

  /**
   * Returns an effect that will execute the specified effect fully on the
   * provided executor, before potentially returning to the previous executor. 
   * See [[ZIO!.onExecutor]].
   */
  def onExecutor[R, E, A](newExecutor: => Executor)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.descriptorWith { descriptor =>
      val oldExecutor = descriptor.executor

      if (descriptor.isLocked) ZIO.acquireReleaseWith(ZIO.shift(newExecutor))(_ => ZIO.shift(oldExecutor))(_ => zio)
      else ZIO.acquireReleaseWith(ZIO.shift(newExecutor))(_ => ZIO.unshift)(_ => zio)
    }

  /**
   * Returns a new scoped workflow that runs finalizers added to the scope of
   * this workflow in parallel.
   */
  def parallelFinalizers[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R with Scope, E, A] =
    ZIO.scopeWith(_.forkWith(ExecutionStrategy.Parallel).flatMap(_.extend[R](zio)))

  /**
   * Retrieves the maximum number of fibers for parallel operators or `None` if
   * it is unbounded.
   */
  def parallelism(implicit trace: Trace): UIO[Option[Int]] =
    Parallelism.get

  /**
   * Retrieves the current maximum number of fibers for parallel operators and
   * uses it to run the specified effect.
   */
  def parallelismWith[R, E, A](f: Option[Int] => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    Parallelism.getWith(f)

  /**
   * Feeds elements of type `A` to a function `f` that returns an effect.
   * Collects all successes and failures in a tupled fashion.
   */
  def partition[R, E, A, B](
    in: => Iterable[A]
  )(f: A => ZIO[R, E, B])(implicit ev: CanFail[E], trace: Trace): ZIO[R, Nothing, (Iterable[E], Iterable[B])] =
    ZIO.suspendSucceed(ZIO.foreach(in)(f(_).either).map(partitionMap(_)(ZIO.identityFn)))

  /**
   * Feeds elements of type `A` to a function `f` that returns an effect.
   * Collects all successes and failures in parallel and returns the result as a
   * tuple.
   */
  def partitionPar[R, E, A, B](
    in: => Iterable[A]
  )(f: A => ZIO[R, E, B])(implicit ev: CanFail[E], trace: Trace): ZIO[R, Nothing, (Iterable[E], Iterable[B])] =
    ZIO.suspendSucceed(ZIO.foreachPar(in)(f(_).either).map(ZIO.partitionMap(_)(ZIO.identityFn)))

  /**
   * Given an environment `R`, returns a function that can supply the
   * environment to programs that require it, removing their need for any
   * specific environment.
   *
   * This is similar to dependency injection, and the `provide` function can be
   * thought of as `inject`.
   */
  def provideEnvironment[R, E, A](r: => ZEnvironment[R])(implicit trace: Trace): ZIO[R, E, A] => IO[E, A] =
    _.provideEnvironment(r)

  def provideLayer[RIn, E, ROut, RIn2, ROut2](layer: ZLayer[RIn, E, ROut])(
    zio: ZIO[ROut with RIn2, E, ROut2]
  )(implicit ev: EnvironmentTag[RIn2], tag: EnvironmentTag[ROut], trace: Trace): ZIO[RIn with RIn2, E, ROut2] =
    zio.provideSomeLayer[RIn with RIn2](ZLayer.environment[RIn2] ++ layer)

  /**
   * Races an `IO[E, A]` against zero or more other effects. Yields either the
   * first success or the last failure.
   */
  def raceAll[R, R1 <: R, E, A](
    zio: => ZIO[R, E, A],
    ios: => Iterable[ZIO[R1, E, A]]
  )(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.suspendSucceed(zio.raceAll(ios))

  /**
   * Returns an effect that races this effect with all the specified effects,
   * yielding the first result to complete, whether by success or failure. If
   * neither effect completes, then the composed effect will not complete.
   *
   * WARNING: The raced effect will safely interrupt the "losers", but will not
   * resume until the losers have been cleanly terminated. If early return is
   * desired, then instead of performing `ZIO.raceFirst(l, rs)`, perform
   * `ZIO.raceFirst(l.disconnect, rs.map(_.disconnect))`, which disconnects left
   * and rights interrupt signal, allowing a fast return, with interruption
   * performed in the background.
   */
  def raceFirst[R, R1 <: R, E, A](zio: ZIO[R, E, A], ios: Iterable[ZIO[R1, E, A]])(implicit
    trace: Trace
  ): ZIO[R1, E, A] =
    (zio.exit raceAll ios.map(_.exit)).flatMap(ZIO.done(_))

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working sequentially.
   */
  def random(implicit trace: Trace): UIO[Random] =
    ZIO.randomWith(ZIO.succeedNow)

  /**
   * Retrieves the `Random` service for this workflow and uses it to run the
   * specified workflow.
   */
  def randomWith[R, E, A](f: Random => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.get(Random.tag)))

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working sequentially.
   */
  def reduceAll[R, R1 <: R, E, A](a: => ZIO[R, E, A], as: => Iterable[ZIO[R1, E, A]])(
    f: (A, A) => A
  )(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.suspendSucceed(as.foldLeft[ZIO[R1, E, A]](a)(_.zipWith(_)(f)))

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working in parallel.
   */
  def reduceAllPar[R, R1 <: R, E, A](a0: => ZIO[R, E, A], as0: => Iterable[ZIO[R1, E, A]])(
    f: (A, A) => A
  )(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.suspendSucceed {
      val a  = a0
      val as = as0

      def prepend[Z](z: Z, zs: Iterable[Z]): Iterable[Z] =
        new Iterable[Z] {
          override def iterator: Iterator[Z] = Iterator(z) ++ zs.iterator
        }

      val all = prepend(a, as)
      mergeAllPar(all)(Option.empty[A])((acc, elem) => Some(acc.fold(elem)(f(_, elem)))).map(_.get)
    }

  def refailCause[E](cause: Cause[E])(implicit trace: Trace): ZIO[Any, E, Nothing] = Refail(cause)

  /**
   * Replicates the given effect `n` times. If 0 or negative numbers are given,
   * an empty `Iterable` will be returned. This method is more efficient than
   * using `List.fill` or similar methods, because the returned `Iterable`
   * consumes only a small amount of heap regardless of `n`.
   */
  def replicate[R, E, A](n: Int)(effect: ZIO[R, E, A])(implicit trace: Trace): Iterable[ZIO[R, E, A]] =
    new Iterable[ZIO[R, E, A]] {
      override def iterator: Iterator[ZIO[R, E, A]] = Iterator.range(0, n).map(_ => effect)
    }

  /**
   * Performs this effect the specified number of times and collects the
   * results.
   */
  def replicateZIO[R, E, A](n: => Int)(effect: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Iterable[A]] =
    ZIO.suspendSucceed(ZIO.collectAll(ZIO.replicate(n)(effect)))

  /**
   * Performs this effect the specified number of times, discarding the results.
   */
  def replicateZIODiscard[R, E, A](n: => Int)(effect: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.collectAllDiscard(ZIO.replicate(n)(effect))

  /**
   * Returns an effect with the value on the right part.
   */
  def right[B](b: => B)(implicit trace: Trace): UIO[Either[Nothing, B]] =
    succeed(Right(b))

  /**
   * Returns an effect that accesses the runtime, which can be used to
   * (unsafely) execute tasks. This is useful for integration with legacy code
   * that must call back into ZIO code.
   */
  def runtime[R](implicit trace: Trace): URIO[R, Runtime[R]] =
    for {
      environment  <- ZIO.environment[R]
      fiberRefs    <- ZIO.getFiberRefs
      runtimeFlags <- ZIO.runtimeFlags
    } yield Runtime(environment, fiberRefs, runtimeFlags)

  /**
   * Retrieves an effect that succeeds with the current runtime flags, which
   * govern behavior and features of the runtime system.
   */
  def runtimeFlags(implicit trace: Trace): ZIO[Any, Nothing, RuntimeFlags] =
    ZIO.unsafeStateful[Any, Nothing, RuntimeFlags] { (_, status) =>
      ZIO.succeedNow(status.runtimeFlags)
    }

  /**
   * Returns the current scope.
   */
  def scope(implicit trace: Trace): ZIO[Scope, Nothing, Scope] =
    ZIO.service[Scope]

  /**
   * Scopes all resources uses in this effect to the lifetime of the effect,
   * ensuring that their finalizers are run as soon as this effect completes
   * execution, whether by success, failure, or interruption.
   *
   * {{{
   * ZIO.scoped {
   *   openFile(name).flatMap(useFile)
   * }
   * }}}
   */
  def scoped[R]: ScopedPartiallyApplied[R] =
    new ScopedPartiallyApplied[R]

  /**
   * Accesses the current scope and uses it to perform the specified effect.
   */
  def scopeWith[R, E, A](f: Scope => ZIO[R, E, A])(implicit trace: Trace): ZIO[R with Scope, E, A] =
    ZIO.serviceWithZIO[Scope](f)

  /**
   * Returns a new scoped workflow that runs finalizers added to the scope of
   * this workflow sequentially in the reverse of the order in which they were
   * added. Note that finalizers are run sequentially by default so this only
   * has meaning if used within a scope where finalizers are being run in
   * parallel.
   */
  def sequentialFinalizers[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R with Scope, E, A] =
    ZIO.scopeWith(_.fork.flatMap(_.extend[R](zio)))

  /**
   * Sets the `FiberRef` values for the fiber running this effect to the values
   * in the specified collection of `FiberRef` values.
   */
  def setFiberRefs(fiberRefs: => FiberRefs)(implicit trace: Trace): UIO[Unit] =
    ZIO.suspendSucceed(fiberRefs.setAll)

  /**
   * Sets a state in the environment to the specified value.
   */
  def setState[S: EnvironmentTag](s: => S)(implicit trace: Trace): ZIO[ZState[S], Nothing, Unit] =
    ZIO.serviceWithZIO(_.set(s))

  /**
   * Accesses the specified service in the environment of the effect.
   */
  def service[A: Tag](implicit trace: Trace): URIO[A, A] =
    serviceWith(identity)

  /**
   * Accesses the service corresponding to the specified key in the environment.
   */
  def serviceAt[Service]: ServiceAtPartiallyApplied[Service] =
    new ServiceAtPartiallyApplied[Service]

  /**
   * Accesses the specified service in the environment of the effect.
   *
   * Especially useful for creating "accessor" methods on Services' companion
   * objects.
   *
   * {{{
   * def foo(int: Int) = ZIO.serviceWith[Foo](_.foo(int))
   * }}}
   */
  def serviceWith[Service]: ServiceWithPartiallyApplied[Service] =
    new ServiceWithPartiallyApplied[Service]

  /**
   * Effectfully accesses the specified service in the environment of the
   * effect.
   *
   * Especially useful for creating "accessor" methods on Services' companion
   * objects.
   *
   * {{{
   * def foo(int: Int) = ZIO.serviceWithZIO[Foo](_.foo(int))
   * }}}
   */
  def serviceWithZIO[Service]: ServiceWithZIOPartiallyApplied[Service] =
    new ServiceWithZIOPartiallyApplied[Service]

  /**
   * Returns an effect that shifts execution to the specified executor. This is
   * useful to specify a default executor that effects sequenced after this one
   * will be run on if they are not shifted somewhere else. It can also be used
   * to implement higher level operators to manage where an effect is run such
   * as [[ZIO!.onExecutor]] and [[ZIO!.onExecutionContext]].
   */
  def shift(executor: => Executor)(implicit trace: Trace): UIO[Unit] =
    (FiberRef.overrideExecutor.set(Some(executor)) *> ZIO.yieldNow)

  /**
   * Returns an effect that suspends for the specified duration. This method is
   * asynchronous, and does not actually block the fiber executing the effect.
   */
  def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
    Clock.sleep(duration)

  /**
   * Returns an effect with the optional value.
   */
  def some[A](a: => A)(implicit trace: Trace): UIO[Option[A]] =
    succeed(Some(a))

  /**
   * Provides a stateful ZIO workflow with its initial state, resulting in a
   * workflow that is ready to be run.
   *
   * {{{
   * ZIO.stateful(0) {
   *   for {
   *       _     <- ZIO.updateState[Int](_ + 1)
   *       state <- ZIO.getState[Int]
   *     } yield assertTrue(state == 1)
   *   }
   * }}}
   */
  def stateful[R]: StatefulPartiallyApplied[R] =
    new StatefulPartiallyApplied[R]

  /**
   * Returns an effect that models success with the specified value.
   */
  def succeed[A](a: => A)(implicit trace: Trace): ZIO[Any, Nothing, A] =
    ZIO.Sync(trace, () => a)

  /**
   * Returns a synchronous effect that does blocking and succeeds with the
   * specified value.
   */
  def succeedBlocking[A](a: => A)(implicit trace: Trace): UIO[A] =
    blocking(ZIO.succeed(a))

  /**
   * Returns a lazily constructed effect, whose construction may itself require
   * effects. When no environment is required (i.e., when R == Any) it is
   * conceptually equivalent to `flatten(effect(io))`.
   */
  def suspend[R, A](rio: => RIO[R, A])(implicit trace: Trace): RIO[R, A] =
    ZIO.isFatalWith { isFatal =>
      try rio
      catch {
        case t: Throwable if !isFatal(t) => throw ZIOError(Cause.fail(t))
      }
    }

  /**
   * Returns a lazily constructed effect, whose construction may itself require
   * effects. The effect must not throw any exceptions. When no environment is
   * required (i.e., when R == Any) it is conceptually equivalent to
   * `flatten(succeed(zio))`. If you wonder if the effect throws exceptions, do
   * not use this method, use [[ZIO.suspend]] or [[ZIO.suspend]].
   */
  def suspendSucceed[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.succeed(zio).flatMap(identityFn)

  /**
   * Retrieves the `System` service for this workflow.
   */
  def system(implicit trace: Trace): UIO[System] =
    ZIO.systemWith(ZIO.succeedNow)

  /**
   * Retrieves the `System` service for this workflow and uses it to run the
   * specified workflow.
   */
  def systemWith[R, E, A](f: System => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.get(System.tag)))

  /**
   * Capture ZIO trace at the current point
   */
  def trace(implicit trace: Trace): UIO[StackTrace] =
    GenerateStackTrace(trace)

  /**
   * Transplants specified effects so that when those effects fork other
   * effects, the forked effects will be governed by the scope of the fiber that
   * executes this effect.
   *
   * This can be used to "graft" deep grandchildren onto a higher-level scope,
   * effectively extending their lifespans into the parent scope.
   */
  def transplant[R, E, A](f: Grafter => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.unsafeStateful[R, E, A] { (fiberState, _) =>
      val scopeOverride = fiberState.unsafeGetFiberRef(FiberRef.forkScopeOverride)
      val scope         = scopeOverride.getOrElse(fiberState.scope)

      f(new Grafter(scope))
    }

  /**
   * An effect that succeeds with a unit value.
   */
  lazy val unit: UIO[Unit] =
    succeedNow(())

  /**
   * Prefix form of `ZIO#uninterruptible`.
   */
  def uninterruptible[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(zio.uninterruptible)

  /**
   * Makes the effect uninterruptible, but passes it a restore function that can
   * be used to restore the inherited interruptibility from whatever region the
   * effect is composed into.
   */
  def uninterruptibleMask[R, E, A](
    f: ZIO.InterruptibilityRestorer => ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.UpdateRuntimeFlagsWithin.Dynamic(
      trace,
      RuntimeFlags.disable(RuntimeFlag.Interruption),
      oldFlags =>
        if (oldFlags.isEnabled(RuntimeFlag.Interruption)) f(InterruptibilityRestorer.MakeInterruptible)
        else f(InterruptibilityRestorer.MakeUninterruptible)
    )

  /**
   * The moral equivalent of `if (!p) exp`
   */
  def unless[R, E, A](p: => Boolean)(zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    suspendSucceed(if (p) none else zio.asSome)

  /**
   * The moral equivalent of `if (!p) exp` when `p` has side-effects
   */
  def unlessZIO[R, E](p: => ZIO[R, E, Boolean]): ZIO.UnlessZIO[R, E] =
    new ZIO.UnlessZIO(() => p)

  /**
   * The inverse operation `IO.sandboxed`
   *
   * Terminates with exceptions on the `Left` side of the `Either` error, if it
   * exists. Otherwise extracts the contained `IO[E, A]`
   */
  def unsandbox[R, E, A](v: => ZIO[R, Cause[E], A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(v.mapErrorCause(_.flatten))

  /**
   * Returns an effect indicating that execution is no longer required to be
   * performed on the current executor. The runtime may continue executing on
   * this executor for efficiency but will not automatically shift back to it
   * after completing an effect on another executor.
   */
  def unshift(implicit trace: Trace): UIO[Unit] =
    FiberRef.overrideExecutor.set(None)

  /**
   * Updates the `FiberRef` values for the fiber running this effect using the
   * specified function.
   */
  def updateFiberRefs(f: (FiberId.Runtime, FiberRefs) => FiberRefs)(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (state, _) =>
      state.unsafeSetFiberRefs(f(state.id, state.unsafeGetFiberRefs()))

      ZIO.unit
    }

  /**
   * Updates the runtime flags. This may have a performance impact. For a
   * higher-performance variant, see `ZIO#withRuntimeFlags`.
   */
  def updateRuntimeFlags(patch: RuntimeFlags.Patch)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
    ZIO.UpdateRuntimeFlags(trace, patch)

  /**
   * Updates a state in the environment with the specified function.
   */
  def updateState[S: EnvironmentTag](f: S => S)(implicit trace: Trace): ZIO[ZState[S], Nothing, Unit] =
    ZIO.serviceWithZIO(_.update(f))

  /**
   * Scopes all resources acquired by `resource` to the lifetime of `use`
   * without effecting the scope of any resources acquired by `use`.
   */
  def using[R]: UsingPartiallyApplied[R] =
    new ZIO.UsingPartiallyApplied[R]

  /**
   * Feeds elements of type `A` to `f` and accumulates all errors in error
   * channel or successes in success channel.
   *
   * This combinator is lossy meaning that if there are errors all successes
   * will be lost. To retain all information please use [[partition]].
   */
  def validate[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, E, B]
  )(implicit
    bf: BuildFrom[Collection[A], B, Collection[B]],
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R, ::[E], Collection[B]] =
    partition(in)(f).flatMap { case (es, bs) =>
      if (es.isEmpty) ZIO.succeedNow(bf.fromSpecific(in)(bs))
      else ZIO.fail(::(es.head, es.tail.toList))
    }

  /**
   * Feeds elements of type `A` to `f` and accumulates all errors in error
   * channel or successes in success channel.
   *
   * This combinator is lossy meaning that if there are errors all successes
   * will be lost. To retain all information please use [[partition]].
   */
  def validate[R, E, A, B](in: NonEmptyChunk[A])(
    f: A => ZIO[R, E, B]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R, ::[E], NonEmptyChunk[B]] =
    partition(in)(f).flatMap { case (es, bs) =>
      if (es.isEmpty) ZIO.succeedNow(NonEmptyChunk.nonEmpty(Chunk.fromIterable(bs)))
      else ZIO.fail(::(es.head, es.tail.toList))
    }

  /**
   * Feeds elements of type `A` to `f` and accumulates all errors, discarding
   * the successes.
   */
  def validateDiscard[R, E, A](in: => Iterable[A])(f: A => ZIO[R, E, Any])(implicit
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R, ::[E], Unit] =
    partition(in)(f).flatMap { case (es, _) =>
      if (es.isEmpty) ZIO.unit
      else ZIO.fail(::(es.head, es.tail.toList))
    }

  /**
   * Feeds elements of type `A` to `f `and accumulates, in parallel, all errors
   * in error channel or successes in success channel.
   *
   * This combinator is lossy meaning that if there are errors all successes
   * will be lost. To retain all information please use [[partitionPar]].
   */
  def validatePar[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, E, B]
  )(implicit
    bf: BuildFrom[Collection[A], B, Collection[B]],
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R, ::[E], Collection[B]] =
    partitionPar(in)(f).flatMap { case (es, bs) =>
      if (es.isEmpty) ZIO.succeedNow(bf.fromSpecific(in)(bs))
      else ZIO.fail(::(es.head, es.tail.toList))
    }

  /**
   * Feeds elements of type `A` to `f `and accumulates, in parallel, all errors
   * in error channel or successes in success channel.
   *
   * This combinator is lossy meaning that if there are errors all successes
   * will be lost. To retain all information please use [[partitionPar]].
   */
  def validatePar[R, E, A, B](in: NonEmptyChunk[A])(
    f: A => ZIO[R, E, B]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R, ::[E], NonEmptyChunk[B]] =
    partitionPar(in)(f).flatMap { case (es, bs) =>
      if (es.isEmpty) ZIO.succeedNow(NonEmptyChunk.nonEmpty(Chunk.fromIterable(bs)))
      else ZIO.fail(::(es.head, es.tail.toList))
    }

  /**
   * Feeds elements of type `A` to `f` in parallel and accumulates all errors,
   * discarding the successes.
   */
  def validateParDiscard[R, E, A](in: => Iterable[A])(f: A => ZIO[R, E, Any])(implicit
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R, ::[E], Unit] =
    partitionPar(in)(f).flatMap { case (es, _) =>
      if (es.isEmpty) ZIO.unit
      else ZIO.fail(::(es.head, es.tail.toList))
    }

  /**
   * Feeds elements of type `A` to `f` until it succeeds. Returns first success
   * or the accumulation of all errors.
   */
  def validateFirst[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, E, B]
  )(implicit
    bf: BuildFrom[Collection[A], E, Collection[E]],
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R, Collection[E], B] =
    ZIO.foreach(in)(f(_).flip).flip

  /**
   * Feeds elements of type `A` to `f`, in parallel, until it succeeds. Returns
   * first success or the accumulation of all errors.
   *
   * In case of success all other running fibers are terminated.
   */
  def validateFirstPar[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, E, B]
  )(implicit
    bf: BuildFrom[Collection[A], E, Collection[E]],
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R, Collection[E], B] =
    ZIO.foreachPar(in)(f(_).flip).flip

  /**
   * The moral equivalent of `if (p) exp`
   */
  def when[R, E, A](p: => Boolean)(zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    suspendSucceed(if (p) zio.asSome else none)

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * value, otherwise does nothing.
   */
  def whenCase[R, E, A, B](a: => A)(pf: PartialFunction[A, ZIO[R, E, B]])(implicit
    trace: Trace
  ): ZIO[R, E, Option[B]] =
    suspendSucceed(pf.andThen(_.asSome).applyOrElse(a, (_: A) => none))

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * effectful value, otherwise does nothing.
   */
  def whenCaseZIO[R, E, A, B](a: => ZIO[R, E, A])(pf: PartialFunction[A, ZIO[R, E, B]])(implicit
    trace: Trace
  ): ZIO[R, E, Option[B]] =
    ZIO.suspendSucceed(a.flatMap(whenCase(_)(pf)))

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  def whenZIO[R, E](p: => ZIO[R, E, Boolean]): ZIO.WhenZIO[R, E] =
    new ZIO.WhenZIO(() => p)

  /**
   * Locally installs a supervisor and an effect that succeeds with all the
   * children that have been forked in the returned effect.
   */
  def withChildren[R, E, A](
    get: UIO[Chunk[Fiber.Runtime[Any, Any]]] => ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    Supervisor.track(true).flatMap { supervisor =>
      // Filter out the fiber id of whoever is calling this:
      get(supervisor.value.flatMap(children => ZIO.descriptor.map(d => children.filter(_.id != d.id))))
        .supervised(supervisor)
    }

  /**
   * Executes the specified workflow with the specified implementation of the
   * clock service.
   */
  def withClock[R, E, A <: Clock, B](clock: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    DefaultServices.currentServices.locallyWith(_.add(clock))(zio)

  /**
   * Sets the implementation of the clock service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withClockScoped[A <: Clock](clock: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    DefaultServices.currentServices.locallyScopedWith(_.add(clock))

  /**
   * Executes the specified workflow with the specified implementation of the
   * console service.
   */
  def withConsole[R, E, A <: Console, B](console: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    DefaultServices.currentServices.locallyWith(_.add(console))(zio)

  /**
   * Sets the implementation of the console service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withConsoleScoped[A <: Console](console: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    DefaultServices.currentServices.locallyScopedWith(_.add(console))

  /**
   * Runs the specified effect with the specified maximum number of fibers for
   * parallel operators.
   */
  def withParallelism[R, E, A](n: => Int)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(Parallelism.locally(Some(n))(zio))

  /**
   * Runs the specified effect with an unbounded maximum number of fibers for
   * parallel operators.
   */
  def withParallelismUnbounded[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(Parallelism.locally(None)(zio))

  /**
   * Executes the specified workflow with the specified implementation of the
   * random service.
   */
  def withRandom[R, E, A <: Random, B](random: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    DefaultServices.currentServices.locallyWith(_.add(random))(zio)

  /**
   * Sets the implementation of the random service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withRandomScoped[A <: Random](random: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    DefaultServices.currentServices.locallyScopedWith(_.add(random))

  def withRuntimeFlagsScoped(update: RuntimeFlags.Patch)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    ZIO.uninterruptible {
      ZIO.updateRuntimeFlags(update) *>
        ZIO.addFinalizer(ZIO.updateRuntimeFlags(update.inverse)).unit
    }

  /**
   * Executes the specified workflow with the specified implementation of the
   * system service.
   */
  def withSystem[R, E, A <: System, B](system: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    DefaultServices.currentServices.locallyWith(_.add(system))(zio)

  /**
   * Sets the implementation of the system service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withSystemScoped[A <: System](system: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    DefaultServices.currentServices.locallyScopedWith(_.add(system))

  /**
   * Returns an effect that yields to the runtime system, starting on a fresh
   * stack. Manual use of this method can improve fairness, at the cost of
   * overhead.
   */
  def yieldNow(implicit trace: Trace): UIO[Unit] = ZIO.YieldNow(trace)

  private[zio] def unsafeStateful[R, E, A](
    onState: (internal.FiberRuntime[E, A], Fiber.Status.Active) => ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    Stateful(trace, onState)

  private lazy val _IdentityFn: Any => Any = (a: Any) => a

  private[zio] def identityFn[A]: A => A = _IdentityFn.asInstanceOf[A => A]

  private[zio] def partitionMap[A, A1, A2](
    iterable: Iterable[A]
  )(f: A => Either[A1, A2]): (Iterable[A1], Iterable[A2]) =
    iterable.foldRight((List.empty[A1], List.empty[A2])) { case (a, (es, bs)) =>
      f(a).fold(
        e => (e :: es, bs),
        b => (es, b :: bs)
      )
    }

  private[zio] val unitFn: Any => Unit = (_: Any) => ()

  implicit final class ZIOAutoCloseableOps[R, E, A <: AutoCloseable](private val io: ZIO[R, E, A]) extends AnyVal {

    /**
     * Like `acquireReleaseWith`, safely wraps a use and release of a resource.
     * This resource will get automatically closed, because it implements
     * `AutoCloseable`.
     */
    def acquireReleaseWithAuto[R1 <: R, E1 >: E, B](use: A => ZIO[R1, E1, B])(implicit
      trace: Trace
    ): ZIO[R1, E1, B] =
      acquireReleaseWith(io)(a => ZIO.succeed(a.close()))(use)

    /**
     * Like `withFinalizer, add a finalizer from AutoClosable.
     */
    def withFinalizerAuto(implicit trace: Trace): ZIO[R with Scope, E, A] =
      ZIO.acquireRelease(io)(a => ZIO.succeed(a.close()))
  }

  implicit final class ZIOBooleanOps[R, E](private val self: ZIO[R, E, Boolean]) extends AnyVal {

    /**
     * Returns the logical conjunction of the `Boolean` value returned by this
     * effect and the `Boolean` value returned by the specified effect. This
     * operator has "short circuiting" behavior so if the value returned by this
     * effect is false the specified effect will not be evaluated.
     */
    final def &&[R1 <: R, E1 >: E](
      that: => ZIO[R1, E1, Boolean]
    )(implicit trace: Trace): ZIO[R1, E1, Boolean] =
      self.flatMap(a => if (a) that else ZIO.succeedNow(false))

    /**
     * Returns the logical conjunction of the `Boolean` value returned by this
     * effect and the `Boolean` value returned by the specified effect. This
     * operator has "short circuiting" behavior so if the value returned by this
     * effect is true the specified effect will not be evaluated.
     */
    final def ||[R1 <: R, E1 >: E](
      that: => ZIO[R1, E1, Boolean]
    )(implicit trace: Trace): ZIO[R1, E1, Boolean] =
      self.flatMap(a => if (a) ZIO.succeedNow(true) else that)
  }

  implicit final class ZioRefineToOrDieOps[R, E <: Throwable, A](private val self: ZIO[R, E, A]) extends AnyVal {

    /**
     * Keeps some of the errors, and terminates the fiber with the rest.
     */
    def refineToOrDie[E1 <: E: ClassTag](implicit ev: CanFail[E], trace: Trace): ZIO[R, E1, A] =
      self.refineOrDie { case e: E1 => e }
  }

  final class ProvideSomeLayer[R0, -R, +E, +A](private val self: ZIO[R, E, A]) extends AnyVal {
    def apply[E1 >: E, R1](
      layer: => ZLayer[R0, E1, R1]
    )(implicit
      ev: R0 with R1 <:< R,
      tagged: EnvironmentTag[R1],
      trace: Trace
    ): ZIO[R0, E1, A] =
      self.asInstanceOf[ZIO[R0 with R1, E, A]].provideLayer(ZLayer.environment[R0] ++ layer)
  }

  final class UpdateService[-R, +E, +A, M](private val self: ZIO[R, E, A]) extends AnyVal {
    def apply[R1 <: R with M](
      f: M => M
    )(implicit tag: Tag[M], trace: Trace): ZIO[R1, E, A] =
      self.provideSomeEnvironment(_.update(f))
  }

  final class UpdateServiceAt[-R, +E, +A, Service](private val self: ZIO[R, E, A]) extends AnyVal {
    def apply[R1 <: R with Map[Key, Service], Key](key: => Key)(
      f: Service => Service
    )(implicit tag: Tag[Map[Key, Service]], trace: Trace): ZIO[R1, E, A] =
      self.provideSomeEnvironment(_.updateAt(key)(f))
  }

  @implicitNotFound(
    "Pattern guards are only supported when the error type is a supertype of NoSuchElementException. However, your effect has ${E} for the error type."
  )
  abstract class CanFilter[+E] {
    def apply(t: NoSuchElementException): E
  }

  object CanFilter {
    implicit def canFilter[E >: NoSuchElementException]: CanFilter[E] =
      new CanFilter[E] {
        def apply(t: NoSuchElementException): E = t
      }
  }

  final class Grafter(private val scope: FiberScope) extends AnyVal {
    def apply[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      FiberRef.forkScopeOverride.locally(Some(scope))(zio)
  }

  final class IfZIO[R, E](private val b: () => ZIO[R, E, Boolean]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, A](onTrue: => ZIO[R1, E1, A], onFalse: => ZIO[R1, E1, A])(implicit
      trace: Trace
    ): ZIO[R1, E1, A] =
      ZIO.suspendSucceed(b().flatMap(b => if (b) onTrue else onFalse))
  }

  final class UnlessZIO[R, E](private val b: () => ZIO[R, E, Boolean]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, A](zio: => ZIO[R1, E1, A])(implicit trace: Trace): ZIO[R1, E1, Option[A]] =
      ZIO.suspendSucceed(b().flatMap(b => if (b) none else zio.asSome))
  }

  final class WhenZIO[R, E](private val b: () => ZIO[R, E, Boolean]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, A](zio: => ZIO[R1, E1, A])(implicit trace: Trace): ZIO[R1, E1, Option[A]] =
      ZIO.suspendSucceed(b()).flatMap(b => if (b) zio.asSome else none)
  }

  final class TimeoutTo[-R, +E, +A, +B](self: ZIO[R, E, A], b: () => B) {
    def apply[B1 >: B](f: A => B1)(duration: => Duration)(implicit
      trace: Trace
    ): ZIO[R, E, B1] =
      (self map f) raceFirst (ZIO.sleep(duration).interruptible as b())
  }

  final class Acquire[-R, +E, +A](private val acquire: () => ZIO[R, E, A]) extends AnyVal {
    def apply[R1](release: A => URIO[R1, Any]): Release[R with R1, E, A] =
      new Release[R with R1, E, A](acquire, release)
  }
  final class Release[-R, +E, +A](acquire: () => ZIO[R, E, A], release: A => URIO[R, Any]) {
    def apply[R1 <: R, E1 >: E, B](use: A => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
      acquireReleaseExitWith(acquire())((a: A, _: Exit[E1, B]) => release(a))(use)
  }

  final class AcquireExit[-R, +E, +A](private val acquire: () => ZIO[R, E, A]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, B](
      release: (A, Exit[E1, B]) => URIO[R1, Any]
    ): ReleaseExit[R1, E, E1, A, B] =
      new ReleaseExit(acquire, release)
  }
  final class ReleaseExit[-R, +E, E1, +A, B](
    acquire: () => ZIO[R, E, A],
    release: (A, Exit[E1, B]) => URIO[R, Any]
  ) {
    def apply[R1 <: R, E2 >: E <: E1, B1 <: B](use: A => ZIO[R1, E2, B1])(implicit
      trace: Trace
    ): ZIO[R1, E2, B1] =
      ZIO.uninterruptibleMask[R1, E2, B1](restore =>
        acquire().flatMap({ a =>
          ZIO
            .suspendSucceed(restore(use(a)))
            .exit
            .flatMap({ e =>
              ZIO
                .suspendSucceed(release(a, e))
                .foldCauseZIO(
                  cause2 => ZIO.failCause(e.fold(_ ++ cause2, _ => cause2)),
                  _ => ZIO.done(e)
                )
            })
        })
      )
  }

  final class EnvironmentWithPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](f: ZEnvironment[R] => A)(implicit trace: Trace): URIO[R, A] =
      ZIO.environment.map(f)
  }

  final class EnvironmentWithZIOPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[R1 <: R, E, A](f: ZEnvironment[R] => ZIO[R1, E, A])(implicit trace: Trace): ZIO[R with R1, E, A] =
      ZIO.environment.flatMap(f)
  }

  final class ScopedPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](zio: => ZIO[Scope with R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      Scope.make.flatMap(_.use[R](zio))
  }

  final class UsingPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[R1, E, A, B](
      resource: ZIO[R with Scope, E, A]
    )(use: A => ZIO[R1, E, B])(implicit trace: Trace): ZIO[R with R1, E, B] =
      ZIO.acquireReleaseExitWith {
        Scope.make
      } { (scope: Scope.Closeable, exit: Exit[Any, Any]) =>
        scope.close(exit)
      } { scope =>
        scope.extend[R](resource).flatMap(use)
      }
  }

  final class ServiceAtPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[Key](
      key: => Key
    )(implicit tag: EnvironmentTag[Map[Key, Service]], trace: Trace): URIO[Map[Key, Service], Option[Service]] =
      ZIO.environmentWith(_.getAt(key))
  }

  final class ServiceWithPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](
      f: Service => A
    )(implicit tagged: Tag[Service], trace: Trace): ZIO[Service, Nothing, A] =
      ZIO.serviceWithZIO(service => ZIO.succeedNow(f(service)))
  }

  final class ServiceWithZIOPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[R <: Service, E, A](
      f: Service => ZIO[R, E, A]
    )(implicit
      tagged: Tag[Service],
      trace: Trace
    ): ZIO[R with Service, E, A] = {
      implicit val tag = tagged.tag
      ZIO.suspendSucceed {
        FiberRef.currentEnvironment.get.flatMap(environment => f(environment.unsafeGet(tag)))
      }
    }
  }

  final class StatefulPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[S, E, A](
      s: S
    )(zio: => ZIO[ZState[S] with R, E, A])(implicit tag: EnvironmentTag[S], trace: Trace): ZIO[R, E, A] =
      zio.provideSomeLayer[R](ZState.initial(s))
  }

  final class GetStateWithPartiallyApplied[S](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](f: S => A)(implicit tag: EnvironmentTag[S], trace: Trace): ZIO[ZState[S], Nothing, A] =
      ZIO.serviceWithZIO(_.get.map(f))
  }

  final class LogSpan(val label: () => String) extends AnyVal {
    import zio.{LogSpan => ZioLogSpan}

    def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      FiberRef.currentLogSpan.get.flatMap { stack =>
        val instant = java.lang.System.currentTimeMillis()
        val logSpan = ZioLogSpan(label(), instant)

        FiberRef.currentLogSpan.locally(logSpan :: stack)(zio)
      }
  }

  final class LogAnnotate(val annotations: () => Set[LogAnnotation]) { self =>
    def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      FiberRef.currentLogAnnotations.locallyWith(_ ++ annotations().map { case LogAnnotation(key, value) =>
        key -> value
      })(zio)
  }

  @inline
  private def succeedLeft[E, A]: E => UIO[Either[E, A]] =
    _succeedLeft.asInstanceOf[E => UIO[Either[E, A]]]

  private val _succeedLeft: Any => IO[Any, Either[Any, Any]] =
    e2 => succeedNow[Either[Any, Any]](Left(e2))

  @inline
  private def succeedRight[E, A]: A => UIO[Either[E, A]] =
    _succeedRight.asInstanceOf[A => UIO[Either[E, A]]]

  private val _succeedRight: Any => IO[Any, Either[Any, Any]] =
    a => succeedNow[Either[Any, Any]](Right(a))

  /**
   * A `ZIOConstructor[Input]` knows how to construct a `ZIO` value from an
   * input of type `Input`. This allows the type of the `ZIO` value constructed
   * to depend on `Input`. The constructed `ZIO` value is guaranteed not to
   * require any services not included in `Environment` or to be able to fail in
   * any ways not described by `Error`.
   */
  sealed trait ZIOConstructor[-Environment, +Error, In] {

    /**
     * The environment type of the `ZIO` value
     */
    type OutEnvironment >: Environment

    /**
     * The error type of the `ZIO` value.
     */
    type OutError <: Error

    /**
     * The success type the `ZIO` value.
     */
    type OutSuccess

    /**
     * Constructs a `ZIO` value from the specified input.
     */
    def make(input: => In)(implicit trace: Trace): ZIO[OutEnvironment, OutError, OutSuccess]
  }

  object ZIOConstructor extends ZIOConstructorLowPriority1 {

    /**
     * Constructs a `ZIO[Any, E, A]` from an `Either[Cause[E], A]`.
     */
    implicit def EitherCauseConstructor[E, A]: WithOut[Any, E, Either[Cause[E], A], Any, E, A] =
      new ZIOConstructor[Any, E, Either[Cause[E], A]] {
        type OutEnvironment = Any
        type OutError       = E
        type OutSuccess     = A
        def make(input: => Either[Cause[E], A])(implicit trace: Trace): ZIO[Any, E, A] =
          ZIO.fromEitherCause(input)
      }

    /**
     * Constructs a `ZIO[Any, E, A]` from an `Either[Cause[E], A]`.
     */
    implicit def EitherCauseLeftConstructor[E, A]: WithOut[Any, E, Left[Cause[E], A], Any, E, A] =
      new ZIOConstructor[Any, E, Left[Cause[E], A]] {
        type OutEnvironment = Any
        type OutError       = E
        type OutSuccess     = A
        def make(input: => Left[Cause[E], A])(implicit trace: Trace): ZIO[Any, E, A] =
          ZIO.fromEitherCause(input)
      }

    /**
     * Constructs a `ZIO[Any, E, A]` from an `Either[Cause[E], A]`.
     */
    implicit def EitherCauseRightConstructor[E, A]: WithOut[Any, E, Right[Cause[E], A], Any, E, A] =
      new ZIOConstructor[Any, E, Right[Cause[E], A]] {
        type OutEnvironment = Any
        type OutError       = E
        type OutSuccess     = A
        def make(input: => Right[Cause[E], A])(implicit trace: Trace): ZIO[Any, E, A] =
          ZIO.fromEitherCause(input)
      }

    /**
     * Constructs a `ZIO[Any, E, A]` from a `Fiber[E, A].`
     */
    implicit def FiberConstructor[E, A]: WithOut[Any, E, Fiber[E, A], Any, E, A] =
      new ZIOConstructor[Any, E, Fiber[E, A]] {
        type OutEnvironment = Any
        type OutError       = E
        type OutSuccess     = A
        def make(input: => Fiber[E, A])(implicit trace: Trace): ZIO[Any, E, A] =
          ZIO.fromFiber(input)
      }

    /**
     * Constructs a `ZIO[Any, E, A]` from a `Fiber[E, A].`
     */
    implicit def FiberRuntimeConstructor[E, A]: WithOut[Any, E, Fiber.Runtime[E, A], Any, E, A] =
      new ZIOConstructor[Any, E, Fiber.Runtime[E, A]] {
        type OutEnvironment = Any
        type OutError       = E
        type OutSuccess     = A
        def make(input: => Fiber.Runtime[E, A])(implicit trace: Trace): ZIO[Any, E, A] =
          ZIO.fromFiber(input)
      }

    /**
     * Constructs a `ZIO[Any, E, A]` from a `Fiber[E, A].`
     */
    implicit def FiberSyntheticConstructor[E, A]: WithOut[Any, E, Fiber.Synthetic[E, A], Any, E, A] =
      new ZIOConstructor[Any, E, Fiber.Synthetic[E, A]] {
        type OutEnvironment = Any
        type OutError       = E
        type OutSuccess     = A
        def make(input: => Fiber.Synthetic[E, A])(implicit trace: Trace): ZIO[Any, E, A] =
          ZIO.fromFiber(input)
      }

    /**
     * Constructs a `ZIO[R, E, A]` from a `ZIO[R, E, Fiber[E, A]]`.
     */
    implicit def FiberZIOConstructor[R, E1 <: E3, E2 <: E3, E3, A]: WithOut[R, E3, ZIO[R, E1, Fiber[E2, A]], R, E3, A] =
      new ZIOConstructor[R, E3, ZIO[R, E1, Fiber[E2, A]]] {
        type OutEnvironment = R
        type OutError       = E3
        type OutSuccess     = A
        def make(input: => ZIO[R, E1, Fiber[E2, A]])(implicit trace: Trace): ZIO[R, E3, A] =
          ZIO.fromFiberZIO(input)
      }

    /**
     * Constructs a `ZIO[R, E, A]` from a `ZIO[R, E, Fiber[E, A]]`.
     */
    implicit def FiberZIORuntimeConstructor[R, E1 <: E3, E2 <: E3, E3, A]
      : WithOut[R, E3, ZIO[R, E1, Fiber.Runtime[E2, A]], R, E3, A] =
      new ZIOConstructor[R, E3, ZIO[R, E1, Fiber.Runtime[E2, A]]] {
        type OutEnvironment = R
        type OutError       = E3
        type OutSuccess     = A
        def make(input: => ZIO[R, E1, Fiber.Runtime[E2, A]])(implicit trace: Trace): ZIO[R, E3, A] =
          ZIO.fromFiberZIO(input)
      }

    /**
     * Constructs a `ZIO[R, E, A]` from a `ZIO[R, E, Fiber[E, A]]`.
     */
    implicit def FiberZIOSyntheticConstructor[R, E1 <: E3, E2 <: E3, E3, A]
      : WithOut[R, E3, ZIO[R, E1, Fiber.Synthetic[E2, A]], R, E3, A] =
      new ZIOConstructor[R, E3, ZIO[R, E1, Fiber.Synthetic[E2, A]]] {
        type OutEnvironment = R
        type OutError       = E3
        type OutSuccess     = A
        def make(input: => ZIO[R, E1, Fiber.Synthetic[E2, A]])(implicit trace: Trace): ZIO[R, E3, A] =
          ZIO.fromFiberZIO(input)
      }

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from a `Future[A]`.
     */
    implicit def FutureConstructor[A, FutureLike[A] <: scala.concurrent.Future[A]]
      : WithOut[Any, Throwable, FutureLike[A], Any, Throwable, A] =
      new ZIOConstructor[Any, Throwable, FutureLike[A]] {
        type OutEnvironment = Any
        type OutError       = Throwable
        type OutSuccess     = A
        def make(input: => FutureLike[A])(implicit trace: Trace): ZIO[Any, Throwable, A] =
          ZIO.fromFuture(_ => input)
      }

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from a function `ExecutionContext
     * => Future[A]`.
     */
    implicit def FutureExecutionContextConstructor[A, FutureLike[A] <: scala.concurrent.Future[A]]
      : WithOut[Any, Throwable, scala.concurrent.ExecutionContext => FutureLike[A], Any, Throwable, A] =
      new ZIOConstructor[Any, Throwable, scala.concurrent.ExecutionContext => FutureLike[A]] {
        type OutEnvironment = Any
        type OutError       = Throwable
        type OutSuccess     = A
        def make(input: => (scala.concurrent.ExecutionContext => FutureLike[A]))(implicit
          trace: Trace
        ): ZIO[Any, Throwable, A] =
          ZIO.fromFuture(input)
      }

    /**
     * Constructs a `ZIO[Any, Option[Nothing], A]` from an `Option[A]`.
     */
    implicit def OptionConstructor[A]: WithOut[Any, Option[Nothing], Option[A], Any, Option[Nothing], A] =
      new ZIOConstructor[Any, Option[Nothing], Option[A]] {
        type OutEnvironment = Any
        type OutError       = Option[Nothing]
        type OutSuccess     = A
        def make(input: => Option[A])(implicit trace: Trace): ZIO[Any, Option[Nothing], A] =
          ZIO.fromOption(input)
      }

    /**
     * Constructs a `ZIO[Any, Option[Nothing], Nothing]` from a `None`.
     */
    implicit val OptionNoneConstructor: WithOut[Any, Option[Nothing], None.type, Any, Option[Nothing], Nothing] =
      new ZIOConstructor[Any, Option[Nothing], None.type] {
        type OutEnvironment = Any
        type OutError       = Option[Nothing]
        type OutSuccess     = Nothing
        def make(input: => None.type)(implicit trace: Trace): ZIO[Any, Option[Nothing], Nothing] =
          ZIO.fromOption(input)
      }

    /**
     * Constructs a `ZIO[Any, Option[Nothing], A]` from a `Some[A]`.
     */
    implicit def OptionSomeConstructor[A]: WithOut[Any, Option[Nothing], Some[A], Any, Option[Nothing], A] =
      new ZIOConstructor[Any, Option[Nothing], Some[A]] {
        type OutEnvironment = Any
        type OutError       = Option[Nothing]
        type OutSuccess     = A
        def make(input: => Some[A])(implicit trace: Trace): ZIO[Any, Option[Nothing], A] =
          ZIO.fromOption(input)
      }

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from a `Promise[A]`
     */
    implicit def PromiseScalaConstructor[A, PromiseLike[A] <: scala.concurrent.Promise[A]]
      : WithOut[Any, Throwable, PromiseLike[A], Any, Throwable, A] =
      new ZIOConstructor[Any, Throwable, PromiseLike[A]] {
        type OutEnvironment = Any
        type OutError       = Throwable
        type OutSuccess     = A
        def make(input: => PromiseLike[A])(implicit trace: Trace): ZIO[Any, Throwable, A] =
          ZIO.fromPromiseScala(input)
      }

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from a `Try[A]`.
     */
    implicit def TryConstructor[A]: WithOut[Any, Throwable, scala.util.Try[A], Any, Throwable, A] =
      new ZIOConstructor[Any, Throwable, scala.util.Try[A]] {
        type OutEnvironment = Any
        type OutError       = Throwable
        type OutSuccess     = A
        def make(input: => scala.util.Try[A])(implicit trace: Trace): ZIO[Any, Throwable, A] =
          ZIO.fromTry(input)
      }

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from a `Failure[A]`.
     */
    implicit def TryFailureConstructor[A]: WithOut[Any, Throwable, scala.util.Failure[A], Any, Throwable, A] =
      new ZIOConstructor[Any, Throwable, scala.util.Failure[A]] {
        type OutEnvironment = Any
        type OutError       = Throwable
        type OutSuccess     = A
        def make(input: => scala.util.Failure[A])(implicit trace: Trace): ZIO[Any, Throwable, A] =
          ZIO.fromTry(input)
      }

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from a `Success[A]`.
     */
    implicit def TrySuccessConstructor[A]: WithOut[Any, Throwable, scala.util.Success[A], Any, Throwable, A] =
      new ZIOConstructor[Any, Throwable, scala.util.Success[A]] {
        type OutEnvironment = Any
        type OutError       = Throwable
        type OutSuccess     = A
        def make(input: => scala.util.Success[A])(implicit trace: Trace): ZIO[Any, Throwable, A] =
          ZIO.fromTry(input)
      }
  }

  trait ZIOConstructorLowPriority1 extends ZIOConstructorLowPriority2 {

    /**
     * Constructs a `ZIO[Any, E, A]` from an `Either[E, A]`.
     */
    implicit def EitherConstructor[E, A]: WithOut[Any, E, Either[E, A], Any, E, A] =
      new ZIOConstructor[Any, E, Either[E, A]] {
        type OutEnvironment = Any
        type OutError       = E
        type OutSuccess     = A
        def make(input: => Either[E, A])(implicit trace: Trace): ZIO[Any, E, A] =
          ZIO.fromEither(input)
      }

    /**
     * Constructs a `ZIO[Any, E, A]]` from an `Either[E, A]`.
     */
    implicit def EitherLeftConstructor[E, A]: WithOut[Any, E, Left[E, A], Any, E, A] =
      new ZIOConstructor[Any, E, Left[E, A]] {
        type OutEnvironment = Any
        type OutError       = E
        type OutSuccess     = A
        def make(input: => Left[E, A])(implicit trace: Trace): ZIO[Any, E, A] =
          ZIO.fromEither(input)
      }

    /**
     * Constructs a `ZIO[Any, E, A]` from an `Either[E, A]`.
     */
    implicit def EitherRightConstructor[E, A]: WithOut[Any, E, Right[E, A], Any, E, A] =
      new ZIOConstructor[Any, E, Right[E, A]] {
        type OutEnvironment = Any
        type OutError       = E
        type OutSuccess     = A
        def make(input: => Right[E, A])(implicit trace: Trace): ZIO[Any, E, A] =
          ZIO.fromEither(input)
      }
  }

  trait ZIOConstructorLowPriority2 extends ZIOConstructorLowPriority3 {

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from an `A`.
     */
    implicit def AttemptConstructor[A]: WithOut[Any, Throwable, A, Any, Throwable, A] =
      new ZIOConstructor[Any, Throwable, A] {
        type OutEnvironment = Any
        type OutError       = Throwable
        type OutSuccess     = A
        def make(input: => A)(implicit trace: Trace): ZIO[Any, Throwable, A] =
          ZIO.attempt(input)
      }
  }

  trait ZIOConstructorLowPriority3 {

    /**
     * The type of the `ZIOConstructor` with the type of the `ZIO` value.
     */
    type WithOut[Environment, Error, In, OutEnvironment0, OutError0, OutSuccess0] =
      ZIOConstructor[Environment, Error, In] {
        type OutEnvironment = OutEnvironment0; type OutError = OutError0; type OutSuccess = OutSuccess0
      }

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from an `A`.
     */
    implicit def SucceedConstructor[A]: WithOut[Any, Nothing, A, Any, Nothing, A] =
      new ZIOConstructor[Any, Nothing, A] {
        type OutEnvironment = Any
        type OutError       = Nothing
        type OutSuccess     = A
        def make(input: => A)(implicit trace: Trace): ZIO[Any, Nothing, A] =
          ZIO.succeed(input)
      }
  }

  final case class ZIOError(cause: Cause[Any]) extends Exception with NoStackTrace

  private[zio] sealed trait EvaluationStep { self =>
    def trace: Trace
  }
  private[zio] object EvaluationStep {
    sealed trait UpdateRuntimeFlags extends EvaluationStep {
      final def trace = Trace.empty

      def update: RuntimeFlags.Patch
    }
    object UpdateRuntimeFlags {
      def apply(patch: RuntimeFlags.Patch): UpdateRuntimeFlags =
        new UpdateRuntimeFlags {
          override def update: RuntimeFlags.Patch = patch
        }

      case object MakeInterruptible extends UpdateRuntimeFlags {
        val update: RuntimeFlags.Patch = RuntimeFlags.enable(RuntimeFlag.Interruption)
      }
      case object MakeUninterruptible extends UpdateRuntimeFlags {
        val update: RuntimeFlags.Patch = RuntimeFlags.disable(RuntimeFlag.Interruption)
      }
    }
    final case class UpdateTrace(trace: Trace) extends EvaluationStep
    sealed trait Continuation[R, E1, E2, A, B] extends EvaluationStep { self =>
      def trace: Trace

      def onSuccess(a: A): ZIO[R, E2, B]

      def onFailure(c: Cause[E1]): ZIO[R, E2, B]

      def erase: Continuation.Erased = self.asInstanceOf[Continuation.Erased]
    }
    object Continuation {
      type Erased = Continuation[Any, Any, Any, Any, Any]

      def ensuring[R, E, A](
        finalizer: ZIO[R, Nothing, Any]
      )(implicit trace0: Trace): Continuation[R, E, E, A, A] =
        new Continuation[R, E, E, A, A] {
          def trace                  = trace0
          def onSuccess(a: A)        = finalizer.flatMap(_ => ZIO.succeed(a))
          def onFailure(c: Cause[E]) = finalizer.flatMap(_ => ZIO.refailCause(c))
        }

      def fromSuccess[R, E, A, B](
        f: A => ZIO[R, E, B]
      )(implicit trace0: Trace): Continuation[R, E, E, A, B] =
        new Continuation[R, E, E, A, B] {
          def trace                  = trace0
          def onSuccess(a: A)        = f(a)
          def onFailure(c: Cause[E]) = ZIO.refailCause(c)
        }

      def fromFailure[R, E1, E2, A](
        f: Cause[E1] => ZIO[R, E2, A]
      )(implicit trace0: Trace): Continuation[R, E1, E2, A, A] =
        new Continuation[R, E1, E2, A, A] {
          def trace                   = trace0
          def onSuccess(a: A)         = ZIO.succeed(a)
          def onFailure(c: Cause[E1]) = f(c)
        }
    }
  }

  private[zio] final case class Sync[A](trace: Trace, eval: () => A) extends ZIO[Any, Nothing, A]
  private[zio] final case class Async[R, E, A](
    trace: Trace,
    registerCallback: (ZIO[R, E, A] => Unit) => Any,
    blockingOn: FiberId
  ) extends ZIO[R, E, A]
  private[zio] sealed trait OnSuccessOrFailure[R, E1, E2, A, B]
      extends ZIO[R, E2, B]
      with EvaluationStep.Continuation[R, E1, E2, A, B] {
    self =>
    def first: ZIO[R, E1, A]

    final override def erase: OnSuccessOrFailure[Any, Any, Any, Any, Any] =
      self.asInstanceOf[OnSuccessOrFailure[Any, Any, Any, Any, Any]]
  }
  private[zio] final case class OnSuccessAndFailure[R, E1, E2, A, B](
    trace: Trace,
    first: ZIO[R, E1, A],
    successK: A => ZIO[R, E2, B],
    failureK: Cause[E1] => ZIO[R, E2, B]
  ) extends OnSuccessOrFailure[R, E1, E2, A, B] {
    def onFailure(c: Cause[E1]): ZIO[R, E2, B] = failureK(c)

    def onSuccess(a: A): ZIO[R, E2, B] = successK(a.asInstanceOf[A])
  }
  private[zio] final case class OnSuccess[R, A, E, B](trace: Trace, first: ZIO[R, E, A], successK: A => ZIO[R, E, B])
      extends OnSuccessOrFailure[R, E, E, A, B] {
    def onFailure(c: Cause[E]): ZIO[R, E, B] = ZIO.refailCause(c)(trace)

    def onSuccess(a: A): ZIO[R, E, B] = successK(a.asInstanceOf[A])
  }
  private[zio] final case class OnFailure[R, E1, E2, A](
    trace: Trace,
    first: ZIO[R, E1, A],
    failureK: Cause[E1] => ZIO[R, E2, A]
  ) extends OnSuccessOrFailure[R, E1, E2, A, A] {
    def onFailure(c: Cause[E1]): ZIO[R, E2, A] = failureK(c)

    def onSuccess(a: A): ZIO[R, E2, A] = ZIO.succeed(a)(trace)
  }
  private[zio] final case class UpdateRuntimeFlags(trace: Trace, update: RuntimeFlags.Patch)
      extends ZIO[Any, Nothing, Unit]
  private[zio] sealed trait UpdateRuntimeFlagsWithin[R, E, A] extends ZIO[R, E, A] {
    def update: RuntimeFlags.Patch

    def scope(oldRuntimeFlags: RuntimeFlags): ZIO[R, E, A]
  }
  private[zio] object UpdateRuntimeFlagsWithin {
    final case class Interruptible[R, E, A](trace: Trace, effect: ZIO[R, E, A])
        extends UpdateRuntimeFlagsWithin[R, E, A] {
      def update: RuntimeFlags.Patch = RuntimeFlags.enable(RuntimeFlag.Interruption)

      def scope(oldRuntimeFlags: RuntimeFlags): ZIO[R, E, A] = effect
    }
    final case class Uninterruptible[R, E, A](trace: Trace, effect: ZIO[R, E, A])
        extends UpdateRuntimeFlagsWithin[R, E, A] {
      def update: RuntimeFlags.Patch = RuntimeFlags.disable(RuntimeFlag.Interruption)

      def scope(oldRuntimeFlags: RuntimeFlags): ZIO[R, E, A] = effect
    }
    final case class Dynamic[R, E, A](trace: Trace, update: RuntimeFlags.Patch, f: RuntimeFlags => ZIO[R, E, A])
        extends UpdateRuntimeFlagsWithin[R, E, A] {
      def scope(oldRuntimeFlags: RuntimeFlags): ZIO[R, E, A] = f(oldRuntimeFlags)
    }
  }
  private[zio] final case class GenerateStackTrace(trace: Trace) extends ZIO[Any, Nothing, StackTrace]
  private[zio] final case class Stateful[R, E, A](
    trace: Trace,
    onState: (zio.internal.FiberRuntime[E, A], Fiber.Status.Active) => ZIO[R, E, A]
  ) extends ZIO[R, E, A] { self =>
    def erase: Stateful[Any, Any, Any] = self.asInstanceOf[Stateful[Any, Any, Any]]
  }
  private[zio] final case class Refail[E](cause: Cause[E]) extends ZIO[Any, E, Nothing] {
    def trace: Trace = Trace.empty
  }
  private[zio] final case class WhileLoop[R, E, S](
    trace: Trace,
    create: () => S,
    check: S => Boolean,
    process: S => ZIO[R, E, Any]
  ) extends ZIO[R, E, S] {
    def withCreate(s: S): WhileLoop[R, E, S] = copy(create = () => s)
  }
  private[zio] final case class YieldNow(trace: Trace) extends ZIO[Any, Nothing, Unit]

  sealed trait InterruptibilityRestorer {
    def apply[R, E, A](effect: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

    def isParentRegionInterruptible: Boolean

    final def isParentRegionUninterruptible: Boolean = !isParentRegionInterruptible

    final def parentInterruptStatus: InterruptStatus = InterruptStatus.fromBoolean(isParentRegionInterruptible)
  }
  object InterruptibilityRestorer {
    def apply(status: InterruptStatus): InterruptibilityRestorer =
      if (status.isInterruptible) MakeInterruptible
      else MakeUninterruptible

    case object MakeInterruptible extends InterruptibilityRestorer {
      def apply[R, E, A](effect: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.UpdateRuntimeFlagsWithin.Interruptible(trace, effect)

      def isParentRegionInterruptible: Boolean = true
    }
    case object MakeUninterruptible extends InterruptibilityRestorer {
      def apply[R, E, A](effect: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.UpdateRuntimeFlagsWithin.Uninterruptible(trace, effect)

      def isParentRegionInterruptible: Boolean = false
    }
  }

  private[zio] val someFatal   = Some(LogLevel.Fatal)
  private[zio] val someError   = Some(LogLevel.Error)
  private[zio] val someWarning = Some(LogLevel.Warning)
  private[zio] val someInfo    = Some(LogLevel.Info)
  private[zio] val someDebug   = Some(LogLevel.Debug)
  private[zio] val someTrace   = Some(LogLevel.Trace)

  private[zio] def succeedNow[A](a: A): UIO[A] = ZIO.succeed(a)(Trace.empty) // FIXME

  private def collectAllParUnboundedDiscard[R, E, A](as: => Iterable[ZIO[R, E, A]])(implicit
    trace: Trace
  ): ZIO[R, E, Unit] =
    foreachParUnboundedDiscard(as)(ZIO.identityFn)

  private def foreachPar[R, E, A, B, Collection[+Element] <: Iterable[Element]](n0: => Int)(
    as: Collection[A]
  )(
    fn: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    ZIO.suspendSucceed {
      val n = n0
      if (n < 1) ZIO.dieMessage(s"Unexpected nonpositive value `$n` passed to foreachParN.")
      else {
        val size = as.size
        if (size == 0) ZIO.succeedNow(bf.newBuilder(as).result())
        else {

          def worker(queue: Queue[(A, Int)], array: Array[AnyRef]): ZIO[R, E, Unit] =
            queue.poll.flatMap {
              case Some((a, n)) =>
                fn(a).tap(b => ZIO.succeed(array(n) = b.asInstanceOf[AnyRef])) *> worker(queue, array)
              case None => ZIO.unit
            }

          for {
            array <- ZIO.succeed(Array.ofDim[AnyRef](size))
            queue <- Queue.bounded[(A, Int)](size)
            _     <- queue.offerAll(as.zipWithIndex)
            _     <- ZIO.collectAllParUnboundedDiscard(ZIO.replicate(n)(worker(queue, array)))
          } yield bf.fromSpecific(as)(array.asInstanceOf[Array[B]])
        }
      }
    }

  private def foreachParDiscard[R, E, A](
    n: => Int
  )(as0: => Iterable[A])(f: A => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.suspendSucceed {
      val as   = as0
      val size = as.size
      if (size == 0) ZIO.unit
      else {

        def worker(queue: Queue[A]): ZIO[R, E, Unit] =
          queue.poll.flatMap {
            case Some(a) => f(a) *> worker(queue)
            case None    => ZIO.unit
          }

        for {
          queue <- Queue.bounded[A](size)
          _     <- queue.offerAll(as)
          _     <- ZIO.collectAllParUnboundedDiscard(ZIO.replicate(n)(worker(queue)))
        } yield ()
      }
    }

  private def foreachParUnbounded[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    ZIO.suspendSucceed {
      val array = Array.ofDim[AnyRef](as.size)
      val zioFunction: ((A, Int)) => ZIO[R, E, Any] = { case (a, i) =>
        f(a).flatMap(b => succeedNow(array(i) = b.asInstanceOf[AnyRef]))
      }
      foreachParUnboundedDiscard(as.zipWithIndex)(zioFunction) *>
        succeedNow(bf.fromSpecific(as)(array.asInstanceOf[Array[B]]))
    }

  private def foreachParUnboundedDiscard[R, E, A](
    as0: => Iterable[A]
  )(f: A => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.suspendSucceed {
      val as = as0
      if (as.isEmpty) ZIO.unit
      else {
        val size = as.size
        ZIO.uninterruptibleMask { restore =>
          val promise = Promise.unsafeMake[Unit, Unit](FiberId.None)
          val ref     = new java.util.concurrent.atomic.AtomicInteger(0)
          ZIO.transplant { graft =>
            ZIO.foreach(as) { a =>
              graft {
                restore(ZIO.suspendSucceed(f(a))).foldCauseZIO(
                  cause => promise.fail(()) *> ZIO.failCause(cause),
                  _ =>
                    if (ref.incrementAndGet == size) {
                      promise.unsafeDone(ZIO.unit)
                      ZIO.unit
                    } else {
                      ZIO.unit
                    }
                )
              }.forkDaemon
            }
          }.flatMap { fibers =>
            restore(promise.await).foldCauseZIO(
              cause =>
                ZIO
                  .foreachParUnbounded(fibers)(_.interrupt)
                  .flatMap(Exit.collectAllPar(_) match {
                    case Some(Exit.Failure(causes)) => ZIO.failCause(cause.stripFailures && causes)
                    case _                          => ZIO.failCause(cause.stripFailures)
                  }),
              _ => ZIO.foreachDiscard(fibers)(_.inheritRefs)
            )
          }
        }
      }
    }
}
