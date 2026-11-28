/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.FiberScope
import zio.metrics.MetricLabel
import zio.metrics.Metrics
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.IntFunction
import scala.annotation.implicitNotFound
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

/**
 * A `ZIO[R, E, A]` value is an immutable value (called an "effect") that
 * describes an async, concurrent workflow. In order to be executed, the
 * workflow requires a value of type `ZEnvironment[R]`, and when executed, the
 * workflow will either produce a failure of type `E`, or a success of type `A`.
 *
 * The three type parameters:
 *
 *   - `R` is the environment the workflow needs. `Any` means it needs nothing.
 *   - `E` is the type of the error it may fail with. `Nothing` means it cannot
 *     fail.
 *   - `A` is the type of the value it succeeds with.
 *
 * ZIO effects may informally be thought of as functions of the form
 * `ZEnvironment[R] => Either[E, A]`.
 *
 * An effect is lazy: creating it runs nothing. It runs when a `Runtime`
 * executes it (for example as the `run` effect of a `ZIOAppDefault`), and it
 * runs again each time it is executed.
 *
 * ZIO effects model resourceful interaction with the outside world, including
 * synchronous, asynchronous, concurrent, and parallel interaction.
 *
 * The async and concurrent operations of ZIO effects are powered by fibers,
 * which are lightweight, green threads that enable high scalability.
 *
 * To run an effect, you need a `Runtime`, which is capable of executing
 * effects. A runtime holds the environment, the fiber-local values
 * (`FiberRefs`) and the runtime flags that the effects it runs start with.
 *
 * {{{
 * final case class Config(port: Int)
 *
 * val loadPort: ZIO[Config, Nothing, Int] = ZIO.serviceWith[Config](_.port)
 *
 * val checkedPort: ZIO[Config, String, Int] =
 *   loadPort.flatMap { port =>
 *     if (port > 0 && port < 65536) ZIO.succeed(port)
 *     else ZIO.fail(s"invalid port \$port")
 *   }
 *
 * // Nothing has run yet: `program` only describes the workflow.
 * val program: ZIO[Any, String, Int] =
 *   checkedPort.provideEnvironment(ZEnvironment(Config(8080)))
 *
 * val exit: Exit[String, Int] =
 *   Unsafe.unsafe { implicit unsafe =>
 *     Runtime.default.unsafe.run(program)
 *   }
 * // Success(8080)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/core/zio/ ZIO]]
 */
sealed trait ZIO[-R, +E, +A]
    extends Product
    with Serializable
    with ZIOPlatformSpecific[R, E, A]
    with ZIOVersionSpecific[R, E, A] {
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
   * Sequences the specified effect after this effect, and succeeds with the
   * value of this effect, discarding the value produced by `that`.
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
  @deprecated("use negate", "2.0.6")
  final def unary_![R1 <: R, E1 >: E](implicit ev: A <:< Boolean, trace: Trace): ZIO[R1, E1, Boolean] =
    self.map(a => !ev(a))

  /**
   * Returns an effect that submerges the error case of an `Either` into the
   * `ZIO`. The inverse operation of `ZIO#either`.
   */
  final def absolve[E1 >: E, B](implicit ev: A IsSubtypeOfOutput Either[E1, B], trace: Trace): ZIO[R, E1, B] =
    self.flatMap(ev(_) match {
      case Right(v) => Exit.succeed(v)
      case Left(e)  => ZIO.fail(e)
    })

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
        cause => Exit.failCause(Cause.fail(cause.squashWith(f))),
        ZIO.successFn
      )

  /**
   * Maps the success value of this effect to the specified constant value.
   */
  final def as[B](b: => B)(implicit trace: Trace): ZIO[R, E, B] =
    map(_ => b)

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
    ZIO.environmentWith[R] { r =>
      val timeToLive = timeToLive0.toNanos
      val cache      = Ref.Synchronized.unsafe.make[Option[(Long, Promise[E, A])]](None)(Unsafe)

      def compute(start: Long): ZIO[R, Nothing, Option[(Long, Promise[E, A])]] =
        for {
          p <- Promise.make[E, A]
          _ <- self.intoPromise(p)
        } yield Some((start + timeToLive, p))

      val get: ZIO[R, E, A] =
        ZIO.uninterruptibleMask { restore =>
          Clock.nanoTime.flatMap { time =>
            cache.updateSomeAndGetZIO {
              case None                              => restore(compute(time))
              case Some((end, _)) if end - time <= 0 => restore(compute(time))
            }.flatMap(a => restore(a.get._2.await))
          }
        }

      get.provideEnvironment(r) -> cache.set(None)
    }

  /**
   * Recovers from all errors.
   *
   * The handler `h` receives the first typed failure `E` in the cause; the rest
   * of the cause, defects included, is dropped. When the cause holds no typed
   * failure (only defects, see `catchAllDefect`, or interruptions), `h` does
   * not run and the effect fails with that cause.
   *
   * {{{
   * def readConfig(path: String): IO[String, String] =
   *   ZIO.fail(s"\$path not found")
   *
   * val config: UIO[String] =
   *   readConfig("app.conf").catchAll(_ => ZIO.succeed("port=8080"))
   * // succeeds with "port=8080"
   * }}}
   */
  final def catchAll[R1 <: R, E2, A1 >: A](
    h: E => ZIO[R1, E2, A1]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, A1] =
    self.foldZIO[R1, E2, A1](h, ZIO.successFn)

  /**
   * A version of `catchAll` that gives you the trace of the error.
   */
  final def catchAllTrace[R1 <: R, E2, A1 >: A](
    h: ((E, StackTrace)) => ZIO[R1, E2, A1]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, A1] =
    self.foldTraceZIO[R1, E2, A1](h, ZIO.successFn)

  /**
   * Recovers from all errors with provided Cause.
   *
   * The handler `k` receives the full `Cause`: failures, defects, and
   * interruption that this effect raises itself (for example with
   * `ZIO.interrupt`). When the fiber is interrupted from outside while it is
   * interruptible, `k` does not run.
   *
   * {{{
   * val risky: IO[String, Int] =
   *   ZIO.die(new IllegalStateException("pool closed"))
   *
   * val recovered: UIO[Int] =
   *   risky.catchAllCause(cause => ZIO.succeed(if (cause.isDie) -1 else 0))
   * // succeeds with -1
   * }}}
   *
   * @see
   *   [[absorb]], [[sandbox]], [[mapErrorCause]] - other functions that can
   *   recover from defects
   */
  final def catchAllCause[R1 <: R, E2, A1 >: A](k: Cause[E] => ZIO[R1, E2, A1])(implicit
    trace: Trace
  ): ZIO[R1, E2, A1] =
    ZIO.FoldZIO(trace, self, ZIO.successFn[A], k)

  /**
   * Recovers from all defects with provided function.
   *
   * {{{
   * def parsePort(text: String): UIO[Int] = ZIO.succeed(text.toInt)
   *
   * val port: UIO[Int] =
   *   parsePort("http").catchAllDefect(_ => ZIO.succeed(8080))
   * // succeeds with 8080
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
   * Recovers from typed failures, the same as `catchAll`: the handler receives
   * the first `Throwable` in the error channel, fatal ones included.
   *
   * {{{
   * import java.io.IOException
   *
   * def openFile(path: String): IO[IOException, String] =
   *   if (path == "backup.json") ZIO.succeed("{}")
   *   else ZIO.fail(new IOException(s"\$path is missing"))
   *
   * val contents: IO[IOException, String] =
   *   openFile("data.json").catchNonFatalOrDie(_ => openFile("backup.json"))
   * }}}
   */
  @deprecated("Use `catchAll`", "2.1.21")
  final def catchNonFatalOrDie[R1 <: R, E2, A1 >: A](
    h: E => ZIO[R1, E2, A1]
  )(implicit ev1: CanFail[E], ev2: E <:< Throwable, trace: Trace): ZIO[R1, E2, A1] =
    self.catchAll(h)

  /**
   * Recovers from some or all of the error cases.
   *
   * {{{
   * import java.io.{FileNotFoundException, IOException}
   *
   * def openFile(path: String): IO[IOException, String] =
   *   if (path == "backup.json") ZIO.succeed("{}")
   *   else ZIO.fail(new FileNotFoundException(path))
   *
   * val contents: IO[IOException, String] =
   *   openFile("data.json").catchSome {
   *     case _: FileNotFoundException => openFile("backup.json")
   *   }
   * }}}
   */
  final def catchSome[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[E, ZIO[R1, E1, A1]]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A1] = {
    def tryRescue(c: Cause[E]): ZIO[R1, E1, A1] =
      c.failureOrCause.fold(t => pf.applyOrElse(t, (_: E) => Exit.failCause(c)), Exit.failCause)

    self.foldCauseZIO[R1, E1, A1](tryRescue, ZIO.successFn)
  }

  /**
   * A version of `catchSome` that gives you the trace of the error.
   */
  final def catchSomeTrace[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[(E, StackTrace), ZIO[R1, E1, A1]]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A1] = {
    def tryRescue(c: Cause[E]): ZIO[R1, E1, A1] =
      c.failureTraceOrCause.fold(t => pf.applyOrElse(t, (_: (E, StackTrace)) => Exit.failCause(c)), Exit.failCause)

    self.foldCauseZIO[R1, E1, A1](tryRescue, ZIO.successFn)
  }

  /**
   * Recovers from some or all of the error cases with provided cause.
   *
   * {{{
   * def openFile(path: String): IO[String, String] =
   *   if (path == "backup.json") ZIO.succeed("{}")
   *   else ZIO.fail(s"cannot open \$path")
   *
   * val contents: IO[String, String] =
   *   openFile("data.json").catchSomeCause {
   *     case c if c.isInterrupted => openFile("backup.json")
   *   }
   * }}}
   */
  final def catchSomeCause[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[Cause[E], ZIO[R1, E1, A1]]
  )(implicit trace: Trace): ZIO[R1, E1, A1] = {
    def tryRescue(c: Cause[E]): ZIO[R1, E1, A1] =
      pf.applyOrElse(c, (_: Cause[E]) => Exit.failCause(c))

    self.foldCauseZIO[R1, E1, A1](tryRescue, ZIO.successFn)
  }

  /**
   * Recovers from some or all of the defects with provided partial function.
   *
   * {{{
   * def readSetting(key: String): UIO[String] =
   *   ZIO.succeed(throw new IllegalStateException(s"\$key is locked"))
   *
   * val setting: UIO[String] =
   *   readSetting("user.home").catchSomeDefect {
   *     case _: IllegalStateException => ZIO.succeed("/srv/app")
   *   }
   * // succeeds with "/srv/app"
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
    unrefineWith(pf)(ZIO.failFn).catchAll(identity)

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
    collectZIO(e)(pf.andThen(ZIO.successFn))

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
   * When the effect fails, dies or is interrupted, prints `<FAIL> ` followed by
   * the `Cause` instead. Prints with `println`, not through `Console`.
   */
  final def debug(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).exitWith {
        case exit @ Exit.Success(value) => ZIO.succeed(println(value)) *> exit
        case exit @ Exit.Failure(error) => ZIO.succeed(println(s"<FAIL> $error")) *> exit
      }
    }

  /**
   * Taps the effect, printing the result of calling `.toString` on the value.
   * Prefixes the output with the given message.
   *
   * On success it prints `prefix: value`; when the effect fails, dies or is
   * interrupted it prints `<FAIL> prefix: ` followed by the `Cause`.
   */
  final def debug(prefix: => String)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).exitWith {
        case exit @ Exit.Success(value) => ZIO.succeed(println(s"$prefix: $value")) *> exit
        case exit @ Exit.Failure(error) => ZIO.succeed(println(s"<FAIL> $prefix: $error")) *> exit
      }
    }

  /**
   * Returns an effect that is delayed from this effect by the specified
   * [[zio.Duration]].
   */
  final def delay(duration: => Duration)(implicit trace: Trace): ZIO[R, E, A] =
    Clock.sleep(duration) *> self

  /**
   * Returns a new workflow that executes this one and captures the changes in
   * `FiberRef` values.
   */
  def diffFiberRefs(implicit trace: Trace): ZIO[R, E, (FiberRefs.Patch, A)] =
    ZIO.withFiberRuntime[R, E, (FiberRefs.Patch, A)] { (state, _) =>
      val refs0 = state.getFiberRefs()
      self.map { value =>
        val refs1 = state.getFiberRefs()
        (FiberRefs.Patch.diff(refs0, refs1), value)
      }
    }

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
          a     <- restore(fiber.join).onInterrupt(fiber.interruptAsFork(fiberId))
        } yield a
      )
    )

  /**
   * Returns an effect whose failure and success have been lifted into an
   * `Either`. The resulting effect cannot fail, because the failure case has
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
   * logic built on `ensuring`, see `ZIO.acquireReleaseWith`.
   */
  final def ensuring[R1 <: R](finalizer: => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    onExit(_ => finalizer)

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
   * Returns an effect that runs this effect and succeeds with an [[zio.Exit]]
   * describing how it completed: `Exit.Success` with the value, or
   * `Exit.Failure` with the `Cause` of a failure, a defect, or an interruption
   * raised by this effect itself (for example with `ZIO.interrupt`). It runs on
   * the current fiber; nothing is forked.
   *
   * {{{
   * val captured: UIO[Exit[String, Int]] = ZIO.fail("timeout").exit
   * // succeeds with an Exit.Failure whose cause holds the failure "timeout"
   * }}}
   */
  final def exit(implicit trace: Trace): URIO[R, Exit[E, A]] =
    self.foldCause(Exit.failCause, ZIO.successFn)

  /**
   * Extracts this effect as an [[zio.Exit]] and then applies the provided
   * function to produce a new effect
   */
  final def exitWith[R1 <: R, E1, B](f: Exit[E, A] => ZIO[R1, E1, B])(implicit
    trace: Trace
  ): ZIO[R1, E1, B] =
    self.foldCauseZIO(c => f(Exit.failCause(c)), a => f(Exit.succeed(a)))

  /**
   * Maps this effect to the default exit codes.
   */
  @deprecated("This operator swallows errors and is no longer necessary to create a ZIO App.", "2.1.20")
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
      case v          => Exit.succeed(v)
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
   * def readFile(path: String): Task[String] = ZIO.succeed("8080")
   * def parsePort(text: String): Task[Int]   = ZIO.attempt(text.trim.toInt)
   *
   * val port: Task[Int] = readFile("port.txt").flatMap(text => parsePort(text))
   * // succeeds with 8080
   * }}}
   */
  def flatMap[R1 <: R, E1 >: E, B](k: A => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
    ZIO.FlatMap(trace, self, k)

  /**
   * Creates a composite effect that represents this effect followed by another
   * one that may depend on the error produced by this one.
   *
   * The effect returned by `f` produces the new error; it cannot fail itself.
   *
   * {{{
   * final case class ReadError(path: String, reason: String)
   *
   * def readFile(path: String): IO[String, String] =
   *   ZIO.fail(s"\$path is missing")
   *
   * val contents: IO[ReadError, String] =
   *   readFile("users.csv").flatMapError { reason =>
   *     ZIO.logWarning(reason).as(ReadError("users.csv", reason))
   *   }
   * }}}
   */
  final def flatMapError[R1 <: R, E2](
    f: E => URIO[R1, E2]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, A] =
    self.foldZIO(
      success = ZIO.successFn,
      failure = f(_).flip
    )

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
    self.flatMap(ev1)

  /**
   * Returns an effect that swaps the error/success cases. This allows you to
   * use all methods on the error channel, possibly before flipping back.
   */
  final def flip(implicit trace: Trace): ZIO[R, A, E] =
    self.foldZIO(ZIO.successFn, ZIO.failFn)

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
    foldZIO(e => Exit.succeed(failure(e)), a => Exit.succeed(success(a)))

  /**
   * A more powerful version of `fold` that allows recovering from any kind of
   * failure except external interruption.
   */
  final def foldCause[B](failure: Cause[E] => B, success: A => B)(implicit trace: Trace): URIO[R, B] =
    foldCauseZIO(c => Exit.succeed(failure(c)), a => Exit.succeed(success(a)))

  /**
   * A more powerful version of `foldZIO` that allows recovering from any kind
   * of failure except external interruption.
   */
  def foldCauseZIO[R1 <: R, E2, B](
    failure: Cause[E] => ZIO[R1, E2, B],
    success: A => ZIO[R1, E2, B]
  )(implicit trace: Trace): ZIO[R1, E2, B] =
    ZIO.FoldZIO(trace, self, success, failure)

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
    foldCauseZIO(c => c.failureTraceOrCause.fold(failure, Exit.failCause), success)

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
   *
   * {{{
   * def parseAmount(text: String): IO[String, Int] =
   *   ZIO.attempt(text.toInt).orElseFail(s"not a number: \$text")
   *
   * val message: UIO[String] =
   *   parseAmount("12x").foldZIO(
   *     error => ZIO.succeed(s"rejected (\$error)"),
   *     amount => ZIO.succeed(s"accepted \$amount")
   *   )
   * // succeeds with "rejected (not a number: 12x)"
   * }}}
   */
  final def foldZIO[R1 <: R, E2, B](failure: E => ZIO[R1, E2, B], success: A => ZIO[R1, E2, B])(implicit
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R1, E2, B] =
    foldCauseZIO(c => c.failureOrCause.fold(failure, Exit.failCause), success)

  /**
   * Returns a new effect that will pass the success value of this effect to the
   * provided callback, and succeeds with `Some` of the callback's result. If
   * this effect fails or dies, the cause is ignored and the result is `None`.
   */
  final def forEachZIO[R1 <: R, E2, B](f: A => ZIO[R1, E2, B])(implicit trace: Trace): ZIO[R1, E2, Option[B]] =
    self.foldCauseZIO(_ => ZIO.none, a => f(a).map(Some(_)))

  /**
   * Repeats this effect without end. The returned effect never succeeds: it
   * stops only when this effect fails, dies, or the fiber is interrupted, and
   * then completes with that cause.
   *
   * {{{
   * def poll(counter: Ref[Int]): IO[String, Unit] =
   *   counter.updateAndGet(_ + 1).flatMap { n =>
   *     if (n == 3) ZIO.fail(s"queue closed after \$n polls") else ZIO.unit
   *   }
   *
   * val polling: IO[String, Nothing] =
   *   Ref.make(0).flatMap(counter => poll(counter).forever)
   * // fails with "queue closed after 3 polls"
   * }}}
   */
  final def forever(implicit trace: Trace): ZIO[R, E, Nothing] =
    ZIO.suspendSucceed {
      ZIO
        .whileLoop(true)(self)(ZIO.unitFn)
        .asInstanceOf[ZIO[R, E, Nothing]]
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
   * val subtask: UIO[Int] = ZIO.succeed(40).delay(100.millis)
   *
   * val result: UIO[Int] =
   *   for {
   *     fiber <- subtask.fork
   *     // Do other work while `subtask` runs...
   *     a <- fiber.join
   *   } yield a + 2
   * // succeeds with 42
   * }}}
   */
  final def fork(implicit trace: Trace): URIO[R, Fiber.Runtime[E, A]] =
    self.forkWithScopeOverride(null)

  /**
   * Forks the effect in the specified scope. The fiber will be interrupted when
   * the scope is closed.
   */
  final def forkIn(scope: => Scope)(implicit trace: Trace): URIO[R, Fiber.Runtime[E, A]] =
    ZIO.uninterruptibleMask { restore =>
      def interrupt(fiber: Fiber.Runtime[Any, Any]): ZIO[Any, Nothing, Any] =
        ZIO.fiberIdWith { fiberId =>
          if (fiberId == fiber.id) Exit.unit else fiber.interruptAs(fiberId)
        }

      scope.fork.flatMap { child =>
        restore(self).onExit(child.close(_)).forkDaemon.tap { fiber =>
          child.addFinalizer(interrupt(fiber))
        }
      }
    }

  /**
   * Forks the effect into a new fiber attached to the global scope. Because the
   * new fiber is attached to the global scope, when the fiber executing the
   * returned effect terminates, the forked fiber will continue running.
   */
  final def forkDaemon(implicit trace: Trace): URIO[R, Fiber.Runtime[E, A]] =
    self.forkWithScopeOverride(FiberScope.global)

  /**
   * Forks the fiber in a [[Scope]], interrupting it when the scope is closed.
   */
  final def forkScoped(implicit trace: Trace): ZIO[R with Scope, Nothing, Fiber.Runtime[E, A]] =
    ZIO.scopeWith(scope => self.forkIn(scope))

  /**
   * Like fork, and runs `handler` in the forked fiber when it fails with an
   * error `E`. The handler does not recover: the fiber still completes with the
   * original failure. When the cause holds only defects (no typed failure and
   * no interruption), `handler` does not run and the fiber completes with that
   * cause repeated twice in sequence (`cause ++ cause`).
   */
  final def forkWithErrorHandler[R1 <: R](handler: E => URIO[R1, Any])(implicit
    trace: Trace
  ): URIO[R1, Fiber.Runtime[E, A]] =
    onError(c => c.failureOrCause.fold(handler, Exit.failCause)).fork

  private[zio] final def forkWithScopeOverride(
    scopeOverride: FiberScope
  )(implicit trace: Trace): URIO[R, Fiber.Runtime[E, A]] =
    ZIO.withFiberRuntime[R, Nothing, Fiber.Runtime[E, A]] { (parentFiber, parentStatus) =>
      val f = ZIO.succeed(
        ZIO.unsafe.fork(trace, self, parentFiber, parentStatus.runtimeFlags, scopeOverride)(Unsafe)
      )
      if (parentFiber.shouldYieldBeforeFork()) ZIO.yieldNow *> f else f
    }

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
  final def head[B](implicit ev: A IsSubtypeOfOutput Seq[B], trace: Trace): ZIO[R, Option[E], B] =
    self.foldZIO(
      e => Exit.fail(Some(e)),
      a => ev(a).headOption.fold[ZIO[R, Option[E], B]](Exit.failNone)(ZIO.successFn)
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
   *
   * Unlike `ignore`, it also ignores defects: when this effect fails or dies,
   * the `Cause` is logged and the returned effect succeeds with `()`.
   */
  final def ignoreLogged(implicit trace: Trace): URIO[R, Unit] =
    self.foldCauseZIO(
      cause =>
        ZIO.logLevel(LogLevel.Debug) {
          ZIO.logCause("An error was silently ignored because it is not anticipated to be useful", cause)
        },
      _ => Exit.unit
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
    withRuntimeFlags(RuntimeFlags.enableInterruption)

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
    ZIO.uninterruptibleMask(restore => restore(self).exitWith(p.done(_)))

  /**
   * Returns whether this effect is a failure.
   */
  final def isFailure(implicit trace: Trace): URIO[R, Boolean] =
    foldZIO(_ => Exit.`true`, _ => Exit.`false`)

  /**
   * Returns whether this effect is a success.
   */
  final def isSuccess(implicit trace: Trace): URIO[R, Boolean] =
    foldZIO(_ => Exit.`false`, _ => Exit.`true`)

  /**
   * "Zooms in" on the value in the `Left` side of an `Either`, moving the
   * possibility that the value is a `Right` to the error channel.
   */
  final def left[B, C](implicit ev: A IsSubtypeOfOutput Either[B, C], trace: Trace): ZIO[R, Either[E, C], B] =
    self.foldZIO(
      e => Exit.fail(Left(e)),
      a => ev(a).fold(ZIO.successFn, c => ZIO.fail(Right(c)))
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
   * Logs the cause of failure of this workflow.
   */
  final def logError(implicit trace: Trace): ZIO[R, E, A] =
    logError("")

  /**
   * Logs the cause of failure of this workflow with the specified message.
   */
  final def logError(message: => String)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).tapErrorCause { cause =>
        ZIO.logErrorCause(message, cause)
      }
    }

  /**
   * Runs this effect inside a new log span named `label`. Log messages written
   * while it runs carry the span, as `label=<elapsed>ms`; the default log
   * format puts the label in double quotes when it contains a space.
   *
   * {{{
   * def parseRequest(line: String): UIO[Int] =
   *   ZIO.log(s"parsing \$line").as(line.length)
   *
   * val parsed: UIO[Int] = parseRequest("GET /users/7").logSpan("parsing")
   * }}}
   */
  def logSpan(label: => String)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.logSpan(label)(self)

  /**
   * Returns an effect whose success is mapped by the specified `f` function.
   *
   * {{{
   * val doubled: UIO[Int] = ZIO.succeed(21).map(_ * 2)
   * // succeeds with 42
   * }}}
   */
  def map[B](f: A => B)(implicit trace: Trace): ZIO[R, E, B] =
    ZIO.Mapped(trace, self, f)

  /**
   * Returns an effect whose success is mapped by the specified side effecting
   * `f` function, translating any thrown exceptions into typed failed effects.
   */
  final def mapAttempt[B](f: A => B)(implicit ev: E IsSubtypeOfError Throwable, trace: Trace): RIO[R, B] =
    foldZIO(e => Exit.fail(ev(e)), a => ZIO.attempt(f(a)))

  /**
   * Returns an effect whose failure and success channels have been mapped by
   * the specified pair of functions, `f` and `g`.
   */
  final def mapBoth[E2, B](f: E => E2, g: A => B)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E2, B] =
    foldZIO(e => Exit.fail(f(e)), a => Exit.succeed(g(a)))

  /**
   * Returns an effect with its error channel mapped using the specified
   * function. This can be used to lift a "smaller" error into a "larger" error.
   */
  final def mapError[E2](f: E => E2)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E2, A] =
    self.mapErrorCause(_.map(f))

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
    self.foldCauseZIO(c => Exit.failCause(h(c)), ZIO.successFn)

  /**
   * Returns an effect that, if evaluated, will return the lazily computed
   * result of this effect.
   */
  final def memoize(implicit trace: Trace): UIO[ZIO[R, E, A]] =
    ZIO.memoize((_: Unit) => self).map(_.apply(()))

  /**
   * Returns a new effect where the error channel has been merged into the
   * success channel to their common combined type.
   */
  final def merge[A1 >: A](implicit ev1: E IsSubtypeOfError A1, ev2: CanFail[E], trace: Trace): URIO[R, A1] =
    self.foldZIO(e => Exit.succeed(ev1(e)), ZIO.successFn)

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
      e => Exit.fail(Some(e)),
      a => ev(a).fold[ZIO[R, Option[E], Unit]](Exit.unit)(_ => Exit.failNone)
    )

  /**
   * Returns an effect that will be executed at most once, even if it is
   * evaluated multiple times.
   */
  final def once(implicit trace: Trace): UIO[ZIO[R, E, Unit]] =
    ZIO.succeed {
      val ref = new AtomicBoolean(false)

      ZIO.whenDiscard(ref.compareAndSet(false, true))(self)
    }

  /**
   * Runs this effect, then runs `success` with its value or `error` with its
   * typed failure, and succeeds with `()`. When the cause holds a typed
   * failure, `error` runs with the first one and the rest of the cause, defects
   * included, is dropped. When the cause holds no typed failure (only defects
   * or interruptions), neither callback runs and the returned effect fails with
   * that cause repeated twice in sequence (`cause ++ cause`).
   */
  final def onDone[R1 <: R](
    error: E => ZIO[R1, Nothing, Any],
    success: A => ZIO[R1, Nothing, Any]
  )(implicit trace: Trace): ZIO[R1, Nothing, Unit] =
    onDoneCause(
      _.failureOrCause.fold(error, Exit.failCause(_)),
      success
    )

  /**
   * Runs this effect, then runs `success` with its value or `error` with the
   * full `Cause` when it fails, dies or is interrupted, and succeeds with `()`
   * when the cause holds a typed failure, dropping any defects in it. When the
   * cause holds no typed failure (only defects or interruptions), `error` runs
   * and the returned effect then fails with that cause.
   */
  final def onDoneCause[R1 <: R](
    error: Cause[E] => ZIO[R1, Nothing, Any],
    success: A => ZIO[R1, Nothing, Any]
  )(implicit trace: Trace): ZIO[R1, Nothing, Unit] =
    onExit {
      case s: Exit.Success[A] => success(s.value)
      case f: Exit.Failure[E] => error(f.cause)
    }.ignore

  /**
   * Runs the specified effect if this effect fails, providing the error to the
   * effect if it exists. The provided effect will not be interrupted.
   *
   * `cleanup` receives the full `Cause`, and runs when this effect fails, dies
   * or is interrupted. The returned effect then ends with the original cause,
   * followed by the defect of `cleanup` when `cleanup` dies.
   */
  final def onError[R1 <: R](cleanup: Cause[E] => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    onExit {
      case f: Exit.Failure[E] => cleanup(f.cause)
      case _                  => Exit.unit
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
   * priority.
   *
   * When this effect completes, the fiber may keep running the effects that
   * follow on the same thread until its next yield or asynchronous boundary,
   * and then continues on the executor of the enclosing region (the runtime's
   * default executor when there is none). With the `RuntimeFlag.EagerShiftBack`
   * flag enabled, or inside an enclosing `onExecutor`, it shifts back right
   * away.
   */
  final def onExecutor(executor: => Executor)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.onExecutor(executor)(self)

  /**
   * Executes the effect on the specified `ExecutionContext`, the same as
   * `onExecutor` with an executor built from `ec`. Shifting back follows
   * `onExecutor`: at its next yield the fiber continues on the executor of the
   * enclosing region (the runtime's default executor when there is none), or
   * right away with `RuntimeFlag.EagerShiftBack` enabled or inside an enclosing
   * `onExecutor`.
   */
  final def onExecutionContext(ec: => ExecutionContext)(implicit trace: Trace): ZIO[R, E, A] =
    self.onExecutor(Executor.fromExecutionContext(ec))

  /**
   * Ensures that a cleanup functions runs, whether this effect succeeds, fails,
   * or is interrupted.
   */
  final def onExit[R1 <: R](cleanup: Exit[E, A] => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.uninterruptibleMask { restore =>
      restore(self).foldCauseZIO(
        failure1 => {
          val result = Exit.failCause(failure1)
          cleanup(result).foldCauseZIO(
            failure2 => Exit.failCause(failure1 ++ failure2),
            _ => result
          )
        },
        success => {
          val result = Exit.succeed(success)

          cleanup(result).flatMap(_ => result)
        }
      )
    }

  /**
   * Runs the specified effect if this effect is interrupted.
   */
  final def onInterrupt[R1 <: R](cleanup: => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    onExit {
      case f: Exit.Failure[E] if f.cause.isInterruptedOnly => cleanup
      case _                                               => Exit.unit
    }

  /**
   * Calls the specified function, and runs the effect it returns, if this
   * effect is interrupted.
   */
  final def onInterrupt[R1 <: R](cleanup: Set[FiberId] => URIO[R1, Any])(implicit trace: Trace): ZIO[R1, E, A] =
    // TODO: isInterrupted or isInterruptedOnly?
    onExit {
      case f: Exit.Failure[E] if (f.cause.isInterruptedOnly) => cleanup(f.cause.interruptors)
      case _                                                 => Exit.unit
    }

  /**
   * Runs the specified effect if this effect is terminated, either because of a
   * defect or because of interruption.
   */
  final def onTermination[R1 <: R](
    cleanup: Cause[Nothing] => URIO[R1, Any]
  )(implicit trace: Trace): ZIO[R1, E, A] =
    onExit {
      case f: Exit.Failure[E] =>
        val cause = f.cause
        if (cause.isFailure) Exit.unit
        else cleanup(cause.asInstanceOf[Cause[Nothing]])
      case _ => Exit.unit
    }

  /**
   * Executes this effect, skipping the error but returning optionally the
   * success.
   */
  final def option(implicit ev: CanFail[E], trace: Trace): URIO[R, Option[A]] =
    self.foldZIO(_ => Exit.none, a => Exit.succeed(Some(a)))

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
    mapErrorCause(_.flatMap(e => Cause.die(f(e))))

  /**
   * Unearth the unchecked failure of the effect. (opposite of `orDie`)
   * {{{
   *   val f0: Task[Unit] = ZIO.fail(new Exception("failing")).unit
   *   val f1: UIO[Unit] = f0.orDie
   *   val f2: Task[Unit] = f1.resurrect
   * }}}
   */
  final def resurrect(implicit ev1: E IsSubtypeOfError Throwable, trace: Trace): RIO[R, A] =
    self.unrefineWith { case e => e }(ev1)

  /**
   * Executes this effect and returns its value, if it succeeds, but otherwise
   * executes the specified effect.
   *
   * `that` runs when this effect fails with a typed error, or is interrupted
   * from inside (for example by `ZIO.interrupt`). When the cause contains a
   * defect, `that` does not run and the returned effect dies with the defects.
   *
   * {{{
   * val primary: IO[String, Int]   = ZIO.fail("primary down")
   * val secondary: IO[String, Int] = ZIO.succeed(5432)
   *
   * val port: IO[String, Int] = primary.orElse(secondary)
   * // succeeds with 5432
   * }}}
   */
  final def orElse[R1 <: R, E2, A1 >: A](
    that: => ZIO[R1, E2, A1]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, A1] =
    tryOrElse(that, ZIO.successFn)

  /**
   * Returns an effect that will produce the value of this effect, unless it
   * fails, in which case, it will produce the value of the specified effect.
   *
   * As with `orElse`, `that` runs when this effect fails with a typed error or
   * is interrupted from inside (for example by `ZIO.interrupt`). When the cause
   * contains a defect, `that` does not run and the returned effect dies with
   * the defects.
   */
  final def orElseEither[R1 <: R, E2, B](
    that: => ZIO[R1, E2, B]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E2, Either[A, B]] =
    tryOrElse(that.map(Right(_)), ZIO.succeedLeft)

  /**
   * Executes this effect and returns its value, if it succeeds, but otherwise
   * fails with the specified error.
   *
   * As with `orElse`, the error replaces a typed failure or an interruption
   * from inside; when the cause contains a defect, the returned effect dies
   * with the defects instead.
   */
  def orElseFail[E1](e1: => E1)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E1, A] =
    orElse(ZIO.fail(e1))

  /**
   * Returns an effect that will produce the value of this effect, unless it
   * fails with the `None` value, in which case it will produce the value of the
   * specified effect.
   */
  final def orElseOptional[R1 <: R, E1, A1 >: A](
    that: => ZIO[R1, Option[E1], A1]
  )(implicit ev: E IsSubtypeOfError Option[E1], trace: Trace): ZIO[R1, Option[E1], A1] =
    catchAll(ev(_).fold(that)(e => Exit.fail(Some(e))))

  /**
   * Executes this effect and returns its value, if it succeeds, but otherwise
   * succeeds with the specified value.
   *
   * As with `orElse`, the value replaces a typed failure or an interruption
   * from inside; when the cause contains a defect, the returned effect dies
   * with the defects instead.
   */
  final def orElseSucceed[A1 >: A](a1: => A1)(implicit ev: CanFail[E], trace: Trace): URIO[R, A1] =
    orElse(Exit.succeed(a1))

  /**
   * Exposes all parallel errors in a single call: when this effect fails, the
   * typed failures in its cause are collected into a non-empty list, in the
   * order the cause holds them, and the effect fails with that list. When the
   * cause also contains defects or interruptions, those are dropped. When the
   * cause has no typed failure, it is passed through unchanged.
   */
  final def parallelErrors[E1 >: E](implicit trace: Trace): ZIO[R, ::[E1], A] =
    self.foldCauseZIO(
      cause =>
        cause.failures match {
          case Nil            => Exit.failCause(cause.asInstanceOf[Cause[Nothing]])
          case ::(head, tail) => Exit.failCause(Cause.fail(::[E1](head, tail), cause.trace))
        },
      ZIO.successFn
    )

  /**
   * Returns a new scoped workflow that runs finalizers added to the scope of
   * this workflow in parallel.
   */
  final def parallelFinalizers(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.parallelFinalizers(self)

  /**
   * Provides the `ZIO` effect with its required environment, which eliminates
   * its dependency on `R`.
   */
  final def provideEnvironment(r: => ZEnvironment[R])(implicit trace: Trace): IO[E, A] =
    FiberRef.currentEnvironment.locallyWith(_ => r)(self.asInstanceOf[ZIO[Any, E, A]])

  /**
   * Provides a layer to the ZIO effect: builds `layer` in a new scope, runs
   * this effect with the layer's output as its environment, and closes the
   * scope, releasing the layer's resources, when this effect completes. The
   * returned effect needs only the layer's input `R0`.
   *
   * {{{
   * final case class Config(port: Int)
   *
   * val program: ZIO[Config, Nothing, Int] = ZIO.serviceWith[Config](_.port)
   *
   * val provided: UIO[Int] = program.provideLayer(ZLayer.succeed(Config(9000)))
   * // succeeds with 9000
   * }}}
   */
  final def provideLayer[E1 >: E, R0](
    layer: => ZLayer[R0, E1, R]
  )(implicit trace: Trace): ZIO[R0, E1, A] =
    ZIO.scopedWith(scope => layer.build(scope).flatMap(r => self.provideEnvironment(r)))

  /**
   * Transforms the environment being provided to this effect with the specified
   * function.
   */
  final def provideSomeEnvironment[R0](
    f: ZEnvironment[R0] => ZEnvironment[R]
  )(implicit trace: Trace): ZIO[R0, E, A] =
    FiberRef.currentEnvironment
      .locallyWith(f.asInstanceOf[ZEnvironment[Any] => ZEnvironment[Any]])(self.asInstanceOf[ZIO[Any, E, A]])

  /**
   * Splits the environment into two parts, providing one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * trait Logging
   * trait Database
   *
   * val program: ZIO[Logging with Database, Nothing, Unit] = ZIO.unit
   *
   * val loggingLayer: ZLayer[Any, Nothing, Logging] =
   *   ZLayer.succeed(new Logging {})
   *
   * val needsDatabase: ZIO[Database, Nothing, Unit] =
   *   program.provideSomeLayer[Database](loggingLayer)
   * }}}
   */
  final def provideSomeLayer[R0]: ZIO.ProvideSomeLayer[R0, R, E, A] =
    new ZIO.ProvideSomeLayer[R0, R, E, A](self)

  /**
   * Returns an effect that races this effect with the specified effect,
   * returning the first successful `A` from the faster side. If one effect
   * succeeds, the other will be interrupted. When the first effect to finish
   * fails, the race waits for the other one. If neither succeeds, then the
   * effect fails with both causes combined (`Cause.Both`).
   *
   * {{{
   * val fast: UIO[String] = ZIO.succeed("replica-1").delay(10.millis)
   * val slow: UIO[String] = ZIO.succeed("replica-2").delay(1.second)
   *
   * val winner: UIO[String] = fast.race(slow)
   * // succeeds with "replica-1"; `slow` is interrupted
   * }}}
   *
   * WARNING: The raced effect will safely interrupt the "loser", but will not
   * resume until the loser has been cleanly terminated. If early return is
   * desired, then instead of performing `l race r`, perform `l.disconnect race
   * r.disconnect`, which disconnects left and right interrupt signal, allowing
   * a fast return, with interruption performed in the background.
   */
  final def race[R1 <: R, E1 >: E, A1 >: A](that: => ZIO[R1, E1, A1])(implicit trace: Trace): ZIO[R1, E1, A1] =
    ZIO.fiberIdWith { parentFiberId =>
      (self.raceWith(that))(
        (exit, right) =>
          exit.foldExitZIO[Any, E1, A1](
            cause => right.join.mapErrorCause(cause && _),
            a => right.interruptAs(parentFiberId).as(a)
          ),
        (exit, left) =>
          exit.foldExitZIO[Any, E1, A1](
            cause => left.join.mapErrorCause(_ && cause),
            a => left.interruptAs(parentFiberId).as(a)
          )
      )
    }

  /**
   * Races this effect with `that`, the same as `race`.
   */
  @deprecated("use race", "2.0.7")
  final def raceAwait[R1 <: R, E1 >: E, A1 >: A](that: => ZIO[R1, E1, A1])(implicit trace: Trace): ZIO[R1, E1, A1] =
    self race that

  /**
   * Returns an effect that races this effect with all the specified effects,
   * yielding the value of the first effect to succeed with a value. The losers
   * are interrupted one after another in a background fiber, and the result is
   * returned without waiting for them to finish. If every effect fails, the
   * returned effect fails with the cause of the last one to fail.
   */
  final def raceAll[R1 <: R, E1 >: E, A1 >: A](
    ios0: => Iterable[ZIO[R1, E1, A1]]
  )(implicit trace: Trace): ZIO[R1, E1, A1] = ZIO.transplant { graft =>
    val ios = ios0

    def arbiter[E1, A1](
      fibers: List[Fiber[E1, A1]],
      winner: Fiber[E1, A1],
      promise: Promise[E1, (A1, Fiber[E1, A1])],
      fails: Ref[Int]
    )(res: Exit[E1, A1]): URIO[R1, Any] =
      res.foldExitZIO[R1, Nothing, Unit](
        e => ZIO.flatten(fails.modify((c: Int) => (if (c == 0) promise.failCause(e).unit else Exit.unit) -> (c - 1))),
        a =>
          promise
            .succeed(a -> winner)
            .flatMap(set =>
              if (set) fibers.foldLeft(ZIO.unit)((io, f) => if (f eq winner) io else io <* f.interrupt)
              else ZIO.unit
            )
      )

    for {
      done  <- Promise.make[E1, (A1, Fiber[E1, A1])]
      fails <- Ref.make[Int](ios.size)
      c <- ZIO.uninterruptibleMask { restore =>
             for {
               fs <- ZIO.foreach(self :: ios.toList)(io => graft.applyOnExit(ZIO.interruptible(io)).fork)
               _ <- fs.foldLeft[ZIO[R1, E1, Any]](ZIO.unit) { case (io, f) =>
                      io *> f.await.flatMap(arbiter(fs, f, done, fails)).fork
                    }

               inheritAll = { (res: (A1, Fiber[E1, A1])) => res._2.inheritAll.as(res._1) }

               c <- restore(done.await.flatMap(inheritAll))
                      .onInterrupt(fs.foldLeft(ZIO.unit)((io, f) => io <* f.interrupt))
             } yield c
           }
    } yield c
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
  ): ZIO[R1, E1, A1] = {
    val f = (exit: Exit[E1, A1], loser: Fiber[E1, A1]) => loser.interrupt *> exit
    self.raceWith(that)(f, f)
  }

  /**
   * Races this effect with `that`, the same as `raceFirst`.
   */
  @deprecated("use raceFirst", "2.0.7")
  final def raceFirstAwait[R1 <: R, E1 >: E, A1 >: A](that: => ZIO[R1, E1, A1])(implicit
    trace: Trace
  ): ZIO[R1, E1, A1] =
    self raceFirst that

  /**
   * Returns an effect that races this effect with the specified effect,
   * yielding the first result to succeed, as `Left` for this effect or `Right`
   * for `that`. If neither effect succeeds, then the composed effect fails with
   * both causes combined (`Cause.Both`).
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
  private final def raceFibersWith[R1 <: R, ER, E2, B, C](right: => ZIO[R1, ER, B])(
    leftWins: (Fiber.Runtime[E, A], Fiber.Runtime[ER, B]) => ZIO[R1, E2, C],
    rightWins: (Fiber.Runtime[ER, B], Fiber.Runtime[E, A]) => ZIO[R1, E2, C],
    leftScope: FiberScope = null,
    rightScope: FiberScope = null
  )(implicit trace: Trace): ZIO[R1, E2, C] =
    ZIO.withFiberRuntime[R1, E2, C] { (parentFiber, parentStatus) =>
      import java.util.concurrent.atomic.AtomicBoolean

      @inline def complete[E0, E1, A, B](
        winner: Fiber.Runtime[E0, A],
        loser: Fiber.Runtime[E1, B],
        cont: (Fiber.Runtime[E0, A], Fiber.Runtime[E1, B]) => ZIO[R1, E2, C],
        ab: AtomicBoolean,
        cb: ZIO[R1, E2, C] => Any
      ): Unit =
        if (ab.compareAndSet(false, true)) {
          cb(cont(winner, loser))
        }

      val graft    = ZIO.Grafter(parentFiber)
      val leftEff  = graft.applyOnExit(self)
      val rightEff = graft.applyOnExit(right)

      val flags      = parentStatus.runtimeFlags
      val leftFiber  = ZIO.unsafe.makeChildFiber(trace, leftEff, parentFiber, flags, leftScope)(Unsafe)
      val rightFiber = ZIO.unsafe.makeChildFiber(trace, rightEff, parentFiber, flags, rightScope)(Unsafe)

      val startLeft  = leftFiber.startSuspended()(Unsafe)
      val startRight = rightFiber.startSuspended()(Unsafe)

      ZIO.async[R1, E2, C](
        { cb =>
          val raceIndicator = new AtomicBoolean()

          leftFiber.addObserver { _ =>
            complete(leftFiber, rightFiber, leftWins, raceIndicator, cb)
          }(Unsafe)

          rightFiber.addObserver { _ =>
            complete(rightFiber, leftFiber, rightWins, raceIndicator, cb)
          }(Unsafe)

          startLeft(leftEff)
          startRight(rightEff)
          ()
        },
        leftFiber.id <> rightFiber.id
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
          case exit: Exit.Success[?] =>
            winner.inheritAll *> leftDone(exit, loser)
          case exit =>
            leftDone(exit, loser)
        },
      (winner, loser) =>
        winner.await.flatMap {
          case exit: Exit.Success[B] =>
            winner.inheritAll *> rightDone(exit, loser)
          case exit =>
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
    mapErrorCause(_.flatMap(pf.andThen(Cause.fail(_)).applyOrElse(_, (e: E) => Cause.die(f(e)))))

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
      pf.andThen[ZIO[R1, E1, A]](_.flatMap(ZIO.failFn))
        .applyOrElse[A, ZIO[R1, E1, A]](v, ZIO.successFn)
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
    repeatOrElse[R1, E, B](schedule, (e, _) => Exit.fail(e))

  /**
   * Returns a new effect that repeats this effect the specified number of times
   * or until the first failure. Repeats are in addition to the first execution,
   * so that `io.repeatN(1)` yields an effect that executes `io`, and then if
   * that succeeds, executes `io` an additional time.
   */
  final def repeatN(n: => Int)(implicit trace: Trace): ZIO[R, E, A] =
    self.flatMap { a =>
      var i = n
      var s = a
      ZIO
        .whileLoop(i > 0) {
          i -= 1
          self
        }(s = _)
        .as(s)
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
      val driver = schedule0.unsafe.driver(trace, Unsafe)

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

  /**
   * Repeats this effect until its value satisfies the specified predicate or
   * until the first failure.
   */
  final def repeatUntil(p: A => Boolean)(implicit trace: Trace): ZIO[R, E, A] =
    self.flatMap(a => if (p(a)) Exit.succeed(a) else ZIO.yieldNow *> repeatUntil(p))

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
    self.flatMap(a => f(a).flatMap(b => if (b) Exit.succeed(a) else ZIO.yieldNow *> repeatUntilZIO(f)))

  /**
   * Repeats this effect while its value satisfies the specified predicate or
   * until the first failure.
   */
  final def repeatWhile(p: A => Boolean)(implicit trace: Trace): ZIO[R, E, A] =
    repeatUntil(e => !p(e))

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
   *
   * A cause that holds a typed failure is retried; a cause with only defects or
   * interruptions ends the effect at once. When the schedule is done, the
   * effect fails with the last cause.
   *
   * {{{
   * def connect(attempts: Ref[Int]): IO[String, String] =
   *   attempts.updateAndGet(_ + 1).flatMap { n =>
   *     if (n < 3) ZIO.fail(s"attempt \$n refused")
   *     else ZIO.succeed(s"connected on attempt \$n")
   *   }
   *
   * val connected: IO[String, String] =
   *   Ref.make(0).flatMap(attempts => connect(attempts).retry(Schedule.recurs(5)))
   * // succeeds with "connected on attempt 3"
   * }}}
   */
  final def retry[R1 <: R, S](
    policy: => Schedule[R1, E, S]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E, A] =
    ZIO.suspendSucceed {
      val driver = policy.unsafe.driver(trace, Unsafe)
      def loop(): ZIO[R1, E, A] =
        self.catchAllCause { cause =>
          cause.failureOrCause.fold(
            e =>
              driver
                .next(e)
                .foldZIO(
                  _ => driver.last.orDie.flatMap(_ => Exit.failCause(cause)),
                  _ => loop()
                ),
            cause => Exit.failCause(cause)
          )
        }

      loop()
    }

  /**
   * Retries this effect the specified number of times.
   */
  final def retryN(n: => Int)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed {

      def loop(n: Int): ZIO[R, E, A] =
        self.catchAllCause { cause =>
          cause.failureOrCause.fold(
            _ => if (n <= 0) Exit.failCause(cause) else ZIO.yieldNow *> loop(n - 1),
            cause => Exit.failCause(cause)
          )
        }

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
      val driver = schedule0.unsafe.driver(trace, Unsafe)
      def loop(): ZIO[R1, E1, Either[B, A]] =
        self
          .map(Right(_))
          .catchAll(e =>
            driver
              .next(e)
              .foldZIO(
                _ => driver.last.orDie.flatMap(out => orElse(e, out).map(Left(_))),
                _ => loop()
              )
          )

      loop()
    }

  /**
   * Retries this effect until its error satisfies the specified predicate.
   */
  final def retryUntil(f: E => Boolean)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E, A] =
    retryUntilZIO(e => Exit.boolean(f(e)))

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
    self.catchAll(e => f(e).flatMap(b => if (b) Exit.fail(e) else ZIO.yieldNow *> retryUntilZIO(f)))

  /**
   * Retries this effect while its error satisfies the specified predicate.
   */
  final def retryWhile(f: E => Boolean)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E, A] =
    retryWhileZIO(e => Exit.boolean(f(e)))

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
      e => Exit.fail(Right(e)),
      a => ev(a).fold(b => ZIO.fail(Left(b)), ZIO.successFn)
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
   *     case Cause.Die(_: ArithmeticException, _) =>
   *       // Caught defect: divided by zero!
   *       ZIO.unit
   *     case Cause.Fail(_, _) =>
   *       // Caught error: DomainError!
   *       ZIO.unit
   *     case cause =>
   *       // Caught unknown defects, shouldn't recover!
   *       ZIO.refailCause(cause)
   *   }
   * }}}
   */
  final def sandbox(implicit trace: Trace): ZIO[R, Cause[E], A] =
    foldCauseZIO(ZIO.failFn, ZIO.successFn)

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
      val driver = schedule0.unsafe.driver(trace, Unsafe)

      def loop(a: A1): ZIO[R1, E, B] =
        driver.next(a).foldZIO(_ => driver.last.orDie, _ => self.flatMap(loop))

      loop(a)
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
  final def sequentialFinalizers(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.sequentialFinalizers(self)

  /**
   * Converts an option on values into an option on errors.
   */
  final def some[B](implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R, Option[E], B] =
    self.foldZIO(
      e => Exit.fail(Some(e)),
      a => ev(a).fold[ZIO[R, Option[E], B]](ZIO.fail(Option.empty[E]))(ZIO.successFn)
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
   * Extracts the optional value, or returns the given 'default'. Superseded by
   * `someOrElse` with better type inference. This method was left for binary
   * compatibility.
   */
  protected final def someOrElse[B](
    default: => B
  )(implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R, E, B] =
    map(a => ev(a).getOrElse(default))

  /**
   * Extracts the optional value, or returns the given 'default'.
   */
  final def someOrElse[B, C](
    default: => C
  )(implicit ev0: A IsSubtypeOfOutput Option[B], ev1: C <:< B, trace: Trace): ZIO[R, E, B] =
    map(a => ev0(a).getOrElse(default))

  /**
   * Extracts the optional value, or executes the effect 'default'. Superseded
   * by someOrElseZIO with better type inference. This method was left for
   * binary compatibility.
   */
  protected final def someOrElseZIO[B, R1 <: R, E1 >: E](
    default: => ZIO[R1, E1, B]
  )(implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R1, E1, B] =
    self.flatMap(ev(_) match {
      case Some(value) => Exit.succeed(value)
      case None        => default
    })

  /**
   * Extracts the optional value, or executes the effect 'default'.
   */
  final def someOrElseZIO[B, R1 <: R, E1 >: E, C](
    default: => ZIO[R1, E1, C]
  )(implicit ev0: A IsSubtypeOfOutput Option[B], ev1: C <:< B, trace: Trace): ZIO[R1, E1, B] =
    self.flatMap(ev0(_) match {
      case Some(value) => Exit.succeed(value)
      case None        => default.map(ev1)
    })

  /**
   * Extracts the optional value, or fails with the given error 'e'.
   */
  final def someOrFail[B, E1 >: E](
    e: => E1
  )(implicit ev: A IsSubtypeOfOutput Option[B], trace: Trace): ZIO[R, E1, B] =
    self.flatMap(ev(_) match {
      case Some(value) => Exit.succeed(value)
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
    self.foldCauseZIO(
      e => Exit.failCause(e),
      ev(_) match {
        case Some(value) => Exit.succeed(value)
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
   *   veryBadIO.sandboxWith[Any, DomainError, Unit](_.catchSome {
   *     case Cause.Die(_: ArithmeticException, _)=>
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
    FiberRef.currentSupervisor.locallyWith(_.patchAdd(supervisor))(self)

  /**
   * Returns an effect that effectfully "peeks" at the success of this effect.
   *
   * {{{
   * def readFile(path: String): Task[String] = ZIO.succeed("verbose=true")
   *
   * val contents: Task[String] =
   *   readFile("settings.conf").tap(text => Console.printLine(text))
   * }}}
   */
  final def tap[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Any])(implicit trace: Trace): ZIO[R1, E1, A] =
    self.flatMap(a => f(a).as(a))

  /**
   * Returns an effect that effectfully "peeks" at the failure or success of
   * this effect.
   * {{{
   * def readFile(path: String): IO[String, String] =
   *   ZIO.succeed("id,name\n7,Ada")
   *
   * val contents: IO[String, String] =
   *   readFile("users.csv").tapBoth(
   *     error => ZIO.logError(error),
   *     data => ZIO.logInfo(s"read \${data.length} characters")
   *   )
   * }}}
   */
  final def tapBoth[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, Any], g: A => ZIO[R1, E1, Any])(implicit
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.foldCauseZIO(c => c.failureOrCause.fold(f(_) *> Exit.failCause(c), _ => Exit.failCause(c)), a => g(a).as(a))

  /**
   * Returns an effect that effectually "peeks" at the defect of this effect.
   */
  final def tapDefect[R1 <: R, E1 >: E](f: Cause[Nothing] => ZIO[R1, E1, Any])(implicit
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.catchAllCause { cause =>
      cause.keepDefects.fold[ZIO[R1, E1, A]](Exit.failCause(cause))(f(_) *> Exit.failCause(cause))
    }

  /**
   * Returns an effect that effectfully "peeks" at the result of this effect.
   * {{{
   * def readFile(path: String): IO[String, String] =
   *   ZIO.fail(s"\$path is missing")
   *
   * val contents: IO[String, String] =
   *   readFile("orders.json").tapEither { result =>
   *     ZIO.log(result.fold("Error: " + _, "Success: " + _))
   *   }
   * }}}
   */
  final def tapEither[R1 <: R, E1 >: E](f: Either[E, A] => ZIO[R1, E1, Any])(implicit
    ev: CanFail[E],
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.foldCauseZIO(
      c => c.failureOrCause.fold(e => f(Left(e)) *> Exit.failCause(c), _ => Exit.failCause(c)),
      a => f(Right(a)).as(a)
    )

  /**
   * Returns an effect that effectfully "peeks" at the failure of this effect.
   * {{{
   * def readFile(path: String): IO[String, String] =
   *   ZIO.fail(s"\$path is missing")
   *
   * val contents: IO[String, String] =
   *   readFile("report.txt").tapError(error => ZIO.logError(error))
   * }}}
   */
  final def tapError[R1 <: R, E1 >: E](
    f: E => ZIO[R1, E1, Any]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A] =
    self.foldCauseZIO(c => c.failureOrCause.fold(f(_) *> Exit.failCause(c), _ => Exit.failCause(c)), ZIO.successFn)

  /**
   * Returns an effect that effectually "peeks" at the cause of the failure of
   * this effect.
   * {{{
   * def readFile(path: String): IO[String, String] =
   *   ZIO.die(new IllegalStateException(s"\$path: disk unavailable"))
   *
   * val contents: IO[String, String] =
   *   readFile("audit.log").tapErrorCause { cause =>
   *     ZIO.logErrorCause("read failed", cause)
   *   }
   * }}}
   */
  final def tapErrorCause[R1 <: R, E1 >: E](f: Cause[E] => ZIO[R1, E1, Any])(implicit
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.foldCauseZIO(c => f(c) *> Exit.failCause(c), ZIO.successFn)

  /**
   * A version of `tapError` that gives you the trace of the error.
   */
  final def tapErrorTrace[R1 <: R, E1 >: E](
    f: ((E, StackTrace)) => ZIO[R1, E1, Any]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A] =
    self.foldCauseZIO(
      c => c.failureTraceOrCause.fold(f(_) *> Exit.failCause(c), _ => Exit.failCause(c)),
      ZIO.successFn
    )

  /**
   * Returns an effect that effectfully "peeks" at the success of this effect.
   * If the partial function isn't defined at the input, the result is
   * equivalent to the original effect.
   *
   * {{{
   * def readFile(path: String): Task[String] = ZIO.succeed("max-connections=20")
   *
   * val contents: Task[String] =
   *   readFile("pool.conf").tapSome {
   *     case content if content.nonEmpty => Console.printLine(content)
   *   }
   * }}}
   */
  final def tapSome[R1 <: R, E1 >: E](f: PartialFunction[A, ZIO[R1, E1, Any]])(implicit
    trace: Trace
  ): ZIO[R1, E1, A] =
    self.tap(f.applyOrElse(_, (_: A) => Exit.unit))

  /**
   * Returns an effect that effectfully "peeks" at the failure of this effect.
   * If the partial function isn't defined at the input, the result is
   * equivalent to the original effect.
   */
  final def tapSomeError[R1 <: R, E1 >: E](
    f: PartialFunction[E, ZIO[R1, E1, Any]]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A] =
    self.tapError(f.applyOrElse(_, (_: E) => Exit.unit))

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
    ZIO.flatten(timeoutTo(ZIO.fail(e))(ZIO.successFn)(d))

  /**
   * The same as [[timeout]], but instead of producing a `None` in the event of
   * timeout, it will produce the specified failure.
   */
  final def timeoutFailCause[E1 >: E](cause: => Cause[E1])(d: => Duration)(implicit
    trace: Trace
  ): ZIO[R, E1, A] =
    ZIO.flatten(timeoutTo(Exit.failCause(cause))(ZIO.successFn)(d))

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

      if (cause.isEmpty) Exit.unit
      else Exit.failCause(cause)
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
    withRuntimeFlags(RuntimeFlags.disableInterruption)

  /**
   * Returns the effect resulting from mapping the success of this effect to
   * unit.
   */
  final def unit(implicit trace: Trace): ZIO[R, E, Unit] =
    self.map(ZIO.unitFn)

  /**
   * Converts a `ZIO[R, Either[E, B], A]` into a `ZIO[R, E, Either[A, B]]`. The
   * inverse of `left`.
   */
  final def unleft[E1, B](implicit
    ev: E IsSubtypeOfError Either[E1, B],
    trace: Trace
  ): ZIO[R, E1, Either[A, B]] =
    self.foldZIO(
      e => ev(e).fold(e1 => ZIO.fail(e1), b => Exit.succeed(Right(b))),
      a => Exit.succeed(Left(a))
    )

  /**
   * The moral equivalent of `if (!p) Some(exp) else None`
   */
  final def unless(p: => Boolean)(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.unless(p)(self)

  /**
   * The moral equivalent of `if (!p) { expr; () }`
   */
  final def unlessDiscard(p: => Boolean)(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.unlessDiscard(p)(self)

  /**
   * The moral equivalent of `if (!p) Some(exp) else None` when `p` has
   * side-effects
   */
  final def unlessZIO[R1 <: R, E1 >: E](p: => ZIO[R1, E1, Boolean])(implicit
    trace: Trace
  ): ZIO[R1, E1, Option[A]] =
    ZIO.unlessZIO(p)(self)

  /**
   * The moral equivalent of `if (!p) { expr; () }` when `p` has side-effects
   */
  final def unlessZIODiscard[R1 <: R, E1 >: E](p: => ZIO[R1, E1, Boolean])(implicit
    trace: Trace
  ): ZIO[R1, E1, Unit] =
    ZIO.unlessZIODiscard(p)(self)

  /**
   * Takes some defects and converts them into typed errors: when the cause
   * contains a defect that `pf` matches, the effect fails with `pf` applied to
   * the first such defect, and the rest of the cause is dropped. Other causes
   * are kept unchanged.
   */
  final def unrefine[E1 >: E](pf: PartialFunction[Throwable, E1])(implicit trace: Trace): ZIO[R, E1, A] =
    unrefineWith(pf)(identity)

  /**
   * Takes the defects of type `E1` and converts them into typed errors, as
   * `unrefine` does with a partial function that matches `E1`.
   */
  final def unrefineTo[E1 >: E: ClassTag](implicit trace: Trace): ZIO[R, E1, A] =
    unrefine { case e: E1 => e }

  /**
   * Takes some defects and converts them into typed errors, using the specified
   * function to convert the `E` into an `E1`. When the cause contains a defect
   * that `pf` matches, the effect fails with `pf` applied to the first such
   * defect, and the rest of the cause is dropped; otherwise each typed failure
   * is mapped with `f`.
   */
  final def unrefineWith[E1](
    pf: PartialFunction[Throwable, E1]
  )(f: E => E1)(implicit trace: Trace): ZIO[R, E1, A] =
    catchAllCause { cause =>
      cause.find {
        case Cause.Die(t, _) if pf.isDefinedAt(t) => pf(t)
      }.fold(Exit.failCause(cause.map(f)))(ZIO.failFn)
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
      e => ev(e).fold(b => Exit.succeed(Left(b)), e1 => Exit.fail(e1)),
      a => Exit.succeed(Right(a))
    )

  /**
   * Converts an option on errors into an option on values.
   */
  final def unsome[E1](implicit ev: E IsSubtypeOfError Option[E1], trace: Trace): ZIO[R, E1, Option[A]] =
    self.foldZIO(
      e => ev(e).fold[ZIO[R, E1, Option[A]]](Exit.none)(ZIO.failFn),
      a => Exit.succeed(Some(a))
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
   * Submerges the `Exit` that this effect succeeds with: succeeds with its
   * value when it is `Exit.Success`, or fails with its cause when it is
   * `Exit.Failure`. The inverse operation of `exit`.
   */
  final def unexit[E1 >: E, A2](implicit ev: A <:< Exit[E1, A2], trace: Trace): ZIO[R, E1, A2] =
    self.flatMap(exit => exit)

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
   * in parallel. Combines both `Cause[E1]` when both effects fail.
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
    self.exit.zipWith(that.exit)(_.zipWith(_)(f, _ ++ _)).unexit

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, combining their results with the specified `f` function. If
   * both sides fail, then the cause will be combined.
   */
  final def validateWithPar[R1 <: R, E1 >: E, B, C](that: => ZIO[R1, E1, B])(f: (A, B) => C)(implicit
    trace: Trace
  ): ZIO[R1, E1, C] =
    self.exit.zipWithPar(that.exit)(_.zipWith(_)(f, _ && _)).unexit

  /**
   * The moral equivalent of `if (p) Some(exp) else None`
   */
  final def when(p: => Boolean)(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.when(p)(self)

  /**
   * The moral equivalent of `if (p) { expr; () }`
   */
  final def whenDiscard(p: => Boolean)(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.whenDiscard(p)(self)

  /**
   * Executes this workflow when value of the specified `FiberRef` satisfies the
   * predicate.
   */
  final def whenFiberRef[S](ref: => FiberRef[S])(f: S => Boolean)(implicit trace: Trace): ZIO[R, E, (S, Option[A])] =
    ZIO.suspendSucceed {
      ref.getWith { s =>
        if (f(s)) self.map(a => (s, Some(a)))
        else Exit.succeed((s, None))
      }
    }

  /**
   * Executes this workflow when the value of the `Ref` satisfies the predicate.
   */
  final def whenRef[S](ref: => Ref[S])(f: S => Boolean)(implicit trace: Trace): ZIO[R, E, (S, Option[A])] =
    ZIO.suspendSucceed {
      ref.get.flatMap { s =>
        if (f(s)) self.map(a => (s, Some(a)))
        else Exit.succeed((s, None))
      }
    }

  /**
   * The moral equivalent of `if (p) Some(exp) else None` when `p` has
   * side-effects
   */
  final def whenZIO[R1 <: R, E1 >: E](
    p: => ZIO[R1, E1, Boolean]
  )(implicit trace: Trace): ZIO[R1, E1, Option[A]] =
    ZIO.whenZIO(p)(self)

  /**
   * The moral equivalent of `if (p) { expr; () }` when `p` has side-effects
   */
  final def whenZIODiscard[R1 <: R, E1 >: E](
    p: => ZIO[R1, E1, Boolean]
  )(implicit trace: Trace): ZIO[R1, E1, Unit] =
    ZIO.whenZIODiscard(p)(self)

  /**
   * Executes this workflow with the specified implementation of the clock
   * service.
   */
  final def withClock[B <: Clock](clock: => B)(implicit tag: Tag[B], trace: Trace): ZIO[R, E, A] =
    ZIO.withClock(clock)(self)

  /**
   * Executes this workflow with the specified configuration provider.
   */
  final def withConfigProvider[B <: ConfigProvider](
    configProvider: => B
  )(implicit tag: Tag[B], trace: Trace): ZIO[R, E, A] =
    ZIO.withConfigProvider(configProvider)(self)

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
   * Executes this workflow with the specified logger added.
   */
  final def withLogger[B <: ZLogger[String, Any]](logger: => B)(implicit tag: Tag[B], trace: Trace): ZIO[R, E, A] =
    ZIO.withLogger(logger)(self)

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
    ZIO.UpdateRuntimeFlagsWithin(trace, patch, _ => self)

  /**
   * Executes this workflow with the specified implementation of the system
   * service.
   */
  final def withSystem[B <: System](system: => B)(implicit tag: Tag[B], trace: Trace): ZIO[R, E, A] =
    ZIO.withSystem(system)(self)

  /**
   * A named alias for `<*>`.
   *
   * {{{
   * val pair: UIO[(Int, String)] = ZIO.succeed(7).zip(ZIO.succeed("seven"))
   * // succeeds with (7, "seven")
   * }}}
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
   *
   * {{{
   * def fetchUser(id: Int): UIO[String]    = ZIO.succeed(s"user-\$id").delay(50.millis)
   * def fetchOrderCount(id: Int): UIO[Int] = ZIO.succeed(3).delay(50.millis)
   *
   * val both: UIO[(String, Int)] = fetchUser(42).zipPar(fetchOrderCount(42))
   * // succeeds with ("user-42", 3)
   * }}}
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
  )(f: (A, B) => C)(implicit trace: Trace): ZIO[R1, E1, C] =
    ZIO.uninterruptibleMask { restore =>
      val that0 = that
      if (self.isInstanceOf[Exit.Success[?]]) {
        self.zipWith(restore(that0))(f)
      } else if (that0.isInstanceOf[Exit.Success[?]]) {
        restore(self).zipWith(that0)(f)
      } else
        ZIO.transplant { graft =>
          val promise = Promise.unsafe.make[Unit, Boolean](FiberId.None)(Unsafe)
          val ref     = new java.util.concurrent.atomic.AtomicBoolean(false)

          def fork[R, E, A](zio: => ZIO[R, E, A], side: Boolean): ZIO[R, Nothing, Fiber[E, A]] =
            graft
              .applyOnExitWith(restore(zio)) {
                case _: Exit.Success[?] => if (ref.getAndSet(true)) promise.unsafe.done(Exit.boolean(side))(Unsafe)
                case _                  => promise.unsafe.done(Exit.failUnit)(Unsafe)
              }
              .forkDaemon

          fork(self, false).zip(fork(that0, true)).flatMap { case (left, right) =>
            restore(promise.await).foldCauseZIO(
              cause =>
                left.interruptFork *> right.interruptFork *>
                  left.await.zip(right.await).flatMap { case (left, right) =>
                    left.zipWith(right)(f, _ && _) match {
                      case f: Exit.Failure[E] => Exit.failCause(cause.stripFailures && f.cause)
                      case _                  => Exit.failCause(cause.stripFailures)
                    }
                  },
              leftWins =>
                if (leftWins) left.join.zipWith(right.join)(f)
                else right.join.zipWith(left.join)((b, a) => f(a, b))
            )
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
          case Some(c) => Exit.failCause(c)
        },
      success
    )

  private[zio] def trace: Trace
}

/**
 * The companion of the `ZIO` type. It holds the constructors that create
 * effects from values, errors, side effects, callbacks and other data types
 * (`succeed`, `fail`, `attempt`, `fromEither`, `fromFuture`, ...), the
 * operators over collections of effects (`foreach`, `foreachPar`, `collectAll`,
 * ...), resource management (`acquireRelease`, `scoped`), environment access
 * (`service`, `serviceWithZIO`), logging (`log`, `logSpan`, `logAnnotate`) and
 * the fiber-level settings (`withParallelism`, `uninterruptible`, ...).
 *
 * {{{
 * def checkAmount(amount: Int): IO[String, Int] =
 *   if (amount >= 0) ZIO.succeed(amount) else ZIO.fail("negative amount: " + amount)
 *
 * val total: IO[String, Int] =
 *   for {
 *     amounts <- ZIO.foreach(List(12, 30))(checkAmount)
 *     _       <- ZIO.when(amounts.sum > 40)(ZIO.log("large order"))
 *   } yield amounts.sum
 * // succeeds with 42
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/core/zio/ ZIO]]
 */
object ZIO extends ZIOCompanionPlatformSpecific with ZIOCompanionVersionSpecific {
  private[zio] object unsafe {
    def fork[R, E1, E2, A, B](
      trace: Trace,
      effect: ZIO[R, E1, A],
      parentFiber: Fiber.Runtime[E2, B],
      parentRuntimeFlags: RuntimeFlags,
      overrideScope: FiberScope = null
    )(implicit unsafe: Unsafe): internal.FiberRuntime[E1, A] = {
      val childFiber = ZIO.unsafe.makeChildFiber(trace, effect, parentFiber, parentRuntimeFlags, overrideScope)

      childFiber.startConcurrently(effect)

      childFiber
    }

    def makeChildFiber[R, E1, E2, A, B](
      trace: Trace,
      effect: ZIO[R, E1, A],
      parentFiber: Fiber.Runtime[E2, B],
      parentRuntimeFlags: RuntimeFlags,
      overrideScope: FiberScope
    )(implicit unsafe: Unsafe): internal.FiberRuntime[E1, A] = {
      val parentFiberRefs = parentFiber.getFiberRefs()
      val childId         = FiberId.generate(parentFiberRefs)(trace)
      val childFiberRefs  = parentFiberRefs.forkAs(childId) // TODO: Optimize

      val childFiber = internal.FiberRuntime[E1, A](childId, childFiberRefs, parentRuntimeFlags)

      // Call the supervisor who can observe the fork of the child fiber
      val supervisor = childFiber.getSupervisor()

      if (supervisor ne Supervisor.none) {
        val childEnvironment = childFiberRefs.getOrDefault(FiberRef.currentEnvironment)
        supervisor.onStart(
          childEnvironment,
          effect.asInstanceOf[ZIO[Any, Any, Any]],
          Some(parentFiber),
          childFiber
        )
      }

      val parentScope =
        if (overrideScope ne null) overrideScope
        else parentFiber.getFiberRef(FiberRef.forkScopeOverride).getOrElse(parentFiber.scope)

      parentScope.add(parentFiber, parentRuntimeFlags, childFiber)(trace, unsafe)

      childFiber
    }

  }

  /**
   * The level of parallelism for parallel operators.
   */
  @deprecated("Use ZIO.parallelism, parallelismWith, withParallelism or withParallelismMask instead", "2.1.22")
  def Parallelism: FiberRef[Option[Int]] =
    FiberRef.parallelism

  /**
   * Submerges the error case of an `Either` into the `ZIO`. The inverse
   * operation of `ZIO#either`.
   */
  def absolve[R, E, A](v: => ZIO[R, E, Either[E, A]])(implicit trace: Trace): ZIO[R, E, A] =
    suspendSucceed(v).absolve

  /**
   * Constructs a scoped resource from an `acquire` and `release` effect. If
   * `acquire` successfully completes execution then `release` will be added to
   * the finalizers associated with the scope of this effect and is guaranteed
   * to be run when the scope is closed.
   *
   * The `acquire` and `release` effects will be run uninterruptibly. When
   * `acquire` fails, no finalizer is added. Finalizers added to one scope run
   * in the reverse order of their acquisition.
   *
   * {{{
   * import java.io.{ByteArrayInputStream, InputStream}
   *
   * def open(name: String): Task[InputStream] =
   *   ZIO.attempt(new ByteArrayInputStream(name.getBytes("UTF-8")))
   *
   * val firstByte: Task[Int] =
   *   ZIO.scoped {
   *     ZIO.acquireRelease(open("report.csv"))(in => ZIO.succeed(in.close())).flatMap { in =>
   *       ZIO.attempt(in.read())
   *     }
   *   }
   * // succeeds with 114, the byte of 'r'; the stream is closed before the result is returned
   * }}}
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
   * The `release` effect runs when `use` succeeds, fails or is interrupted. It
   * cannot fail with a typed error; if it dies, the returned effect dies even
   * when `use` succeeds, and when `use` also fails the cause holds both the
   * failure of `use` and the defect of `release`.
   *
   * {{{
   * import java.io.{ByteArrayInputStream, InputStream}
   *
   * def openFile(name: String): Task[InputStream] =
   *   ZIO.attempt(new ByteArrayInputStream("id,amount\n7,120".getBytes("UTF-8")))
   * def closeFile(in: InputStream): UIO[Unit] =
   *   ZIO.succeed(in.close())
   * def readHeader(in: InputStream): Task[String] =
   *   ZIO.attempt(scala.io.Source.fromInputStream(in, "UTF-8").getLines().next())
   *
   * val header: Task[String] =
   *   ZIO.acquireReleaseWith(openFile("data.csv"))(closeFile) { file =>
   *     readHeader(file)
   *   }
   * // succeeds with "id,amount"
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
    ZIO.environmentWithZIO[R & Scope] { env =>
      env.getScope
        .addFinalizerExit(exit => finalizer(exit).provideEnvironment(env))
    }

  /**
   * Makes an explicit check to see if the fiber has been interrupted, and if
   * so, performs self-interruption
   */
  def allowInterrupt(implicit trace: Trace): UIO[Unit] =
    descriptorWith(d => if (d.interrupters.nonEmpty) interrupt else Exit.unit)

  /**
   * Same as `asyncInterrupt`, but passes an `Unsafe` value to `register`, so
   * the registration code can call unsafe APIs. `register` returns `Left` with
   * an effect that cancels the operation when the fiber is interrupted, or
   * `Right` with the result when no asynchronous step is needed.
   */
  def asyncInterruptUnsafe[R, E, A](
    register: Unsafe => (ZIO[R, E, A] => Unit) => Either[URIO[R, Any], ZIO[R, E, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: Trace): ZIO[R, E, A] =
    asyncInterrupt(cb => register(Unsafe)(cb), blockingOn)

  /**
   * Converts an asynchronous, callback-style API into a ZIO effect, which will
   * be executed asynchronously.
   *
   * With this variant, the registration function may return a ZIO effect.
   */
  def asyncZIO[R, E, A](
    register: (ZIO[R, E, A] => Unit) => ZIO[R, E, Any]
  )(implicit trace: Trace): ZIO[R, E, A] =
    for {
      p <- Promise.make[E, A]
      r <- ZIO.runtime[R]
      a <- ZIO.uninterruptibleMask { restore =>
             val f = register(k => r.unsafe.fork(k.intoPromise(p))(trace, Unsafe))

             restore(f.catchAllCause(p.refailCause)).fork *> restore(p.await)
           }
    } yield a

  /**
   * Same as `attempt`, but passes an `Unsafe` value to the code, so it can call
   * unsafe APIs. A non-fatal exception thrown by the code becomes the failure
   * of the effect.
   */
  def attemptUnsafe[A](a: Unsafe => A)(implicit trace: Trace): Task[A] =
    ZIO.attempt(a(Unsafe))

  /**
   * Same as `attemptBlockingIO`, but passes an `Unsafe` value to the code. The
   * code runs on the blocking executor; an `IOException` it throws becomes the
   * failure of the effect, and any other non-fatal exception becomes a defect.
   */
  def attemptBlockingIOUnsafe[A](effect: Unsafe => A)(implicit trace: Trace): IO[IOException, A] =
    attemptBlockingIO(effect(Unsafe))

  /**
   * Returns a new effect that, when executed, will execute the original effect
   * on the blocking thread pool.
   */
  def blocking[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    onExecutorWith(_.getFiberRef(FiberRef.currentBlockingExecutor))(zio)

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
    ZIO.withFiberRuntime[R, E, A] { (_, status) =>
      f(InterruptStatus.fromBoolean(RuntimeFlags.interruption(status.runtimeFlags)))
    }

  /**
   * Retrieves the `Clock` service for this workflow.
   */
  def clock(implicit trace: Trace): UIO[Clock] =
    ZIO.clockWith(ZIO.successFn)

  /**
   * Retrieves the `Clock` service for this workflow and uses it to run the
   * specified workflow.
   */
  def clockWith[R, E, A](f: Clock => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.get(Clock.tag)))

  /**
   * Applies `f` to each element from left to right, keeping the values of the
   * effects that succeed and skipping the elements whose effect fails with
   * `None`. The first effect that fails with `Some(e)` makes the result fail
   * with `e`. For a parallel version, see `collectPar`.
   */
  def collect[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(
    f: A => ZIO[R, Option[E], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    foreach[R, E, A, Option[B], Iterable](in)(a => f(a).unsome).map(_.flatten).map(bf.fromSpecific(in))

  /**
   * Applies `f` to each entry of the map from left to right, keeping the
   * entries of the effects that succeed and skipping the entries whose effect
   * fails with `None`. The first effect that fails with `Some(e)` makes the
   * result fail with `e`. For a parallel version, see `collectPar`.
   */
  def collect[R, E, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => ZIO[R, Option[E], (Key2, Value2)])(implicit trace: Trace): ZIO[R, E, Map[Key2, Value2]] =
    foreach[R, E, (Key, Value), Option[(Key2, Value2)], Iterable](map)(f.tupled(_).unsome).map(_.flatten.toMap)

  /**
   * Evaluate each effect in the structure from left to right, and collect the
   * results. For a parallel version, see `collectAllPar`.
   *
   * The first effect that fails stops the evaluation, and the result fails with
   * its error.
   *
   * {{{
   * val prices: UIO[List[Int]] = ZIO.collectAll(List(ZIO.succeed(120), ZIO.succeed(80)))
   * // succeeds with List(120, 80)
   * }}}
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
   * Runs each effect from left to right and collects the values of the ones
   * that succeed, skipping the ones that fail. An effect whose cause holds a
   * typed failure is skipped, even when the cause also holds a defect; an
   * effect whose cause holds only a defect makes the result die.
   */
  def collectAllSuccesses[R, E, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[ZIO[R, E, A]]
  )(implicit bf: BuildFrom[Collection[ZIO[R, E, A]], A, Collection[A]], trace: Trace): URIO[R, Collection[A]] =
    collectAllWith(in.map(_.either)) { case Right(a) => a }.map(bf.fromSpecific(in))

  /**
   * Runs each effect in parallel and collects the values of the ones that
   * succeed, skipping the ones that fail. An effect whose cause holds a typed
   * failure is skipped, even when the cause also holds a defect; an effect
   * whose cause holds only a defect makes the result die.
   */
  def collectAllSuccessesPar[R, E, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[ZIO[R, E, A]]
  )(implicit bf: BuildFrom[Collection[ZIO[R, E, A]], A, Collection[A]], trace: Trace): URIO[R, Collection[A]] =
    collectAllWithPar(in.map(_.either)) { case Right(a) => a }.map(bf.fromSpecific(in))

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
      var a: Option[B] = None
      ZIO.whileLoop(iterator.hasNext && a.isEmpty)(f(iterator.next()))(a = _).as(a)
    }

  /**
   * Applies `f` to each element in parallel, keeping the values of the effects
   * that succeed and skipping the elements whose effect fails with `None`. An
   * effect that fails with `Some(e)` interrupts the effects still running, and
   * the result fails with a cause that holds `e` together with the
   * interruptions of the stopped effects. The kept values are in the order of
   * the input.
   */
  def collectPar[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(
    f: A => ZIO[R, Option[E], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    foreachPar[R, E, A, Option[B], Iterable](in)(a => f(a).unsome).map(_.flatten).map(bf.fromSpecific(in))

  /**
   * Applies `f` to each entry of the map in parallel, keeping the entries of
   * the effects that succeed and skipping the entries whose effect fails with
   * `None`. An effect that fails with `Some(e)` interrupts the effects still
   * running, and the result fails with a cause that holds `e` together with the
   * interruptions of the stopped effects. For a sequential version, see
   * `collect`.
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
    ZIO.suspendSucceed(if (predicate) Exit.succeed(result) else ZIO.fail(error))

  /**
   * Uses the current config provider to load the specified config, or fail with
   * an error of type Config.Error.
   */
  def config[A](config: Config[A])(implicit trace: Trace): ZIO[Any, Config.Error, A] =
    ZIO.configProviderWith(_.load(config))

  /**
   * Uses the current config provider to load the config of type `A`, or fail
   * with an error of type Config.Error.
   */
  def config[A](implicit trace: Trace, config: Config[A]): ZIO[Any, Config.Error, A] =
    ZIO.configProviderWith(_.load[A])

  /**
   * Retrieves the current config provider, and passes it to the specified
   * function, which may return an effect that uses the provider to perform some
   * work or compute some value.
   */
  def configProviderWith[R, E, A](f: ConfigProvider => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.get(ConfigProvider.tag)))

  /**
   * Retrieves the `Console` service for this workflow.
   */
  def console(implicit trace: Trace): UIO[Console] =
    ZIO.consoleWith(ZIO.successFn)

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
    descriptorWith(ZIO.successFn)

  /**
   * Constructs an effect based on information about the current fiber, such as
   * its identity.
   */
  def descriptorWith[R, E, A](f: Fiber.Descriptor => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withFiberRuntime[R, E, A] { (fiberState, status) =>
      val descriptor =
        Fiber.Descriptor(
          fiberState.id,
          status,
          fiberState.getFiberRef(FiberRef.interruptedCause).interruptors,
          fiberState.getCurrentExecutor(),
          isLocked = RuntimeFlags.eagerShiftBack(status.runtimeFlags) ||
            fiberState.getFiberRef(FiberRef.overrideExecutor).isDefined
        )

      f(descriptor)
    }

  /**
   * Returns an effect that dies with the specified `Throwable`. This method can
   * be used for terminating a fiber because a defect has been detected in the
   * code.
   *
   * The `Throwable` is not in the typed error channel: `catchAll` does not
   * catch it, while `catchAllDefect`, `catchAllCause` and `exit` see it.
   *
   * {{{
   * val broken: UIO[String] = ZIO.die(new IllegalStateException("negative stock"))
   * val seen: UIO[String]   = broken.catchAllDefect(t => ZIO.succeed(t.getMessage))
   * // succeeds with "negative stock"
   * }}}
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
  @deprecated(
    "For suspending a side-effecting method that produces an Exit, use `ZIO.suspendSucceed` instead.",
    "2.1.14"
  )
  def done[E, A](r: => Exit[E, A])(implicit trace: Trace): IO[E, A] =
    ZIO.suspendSucceed(r)

  /**
   * Accesses the whole environment of the effect.
   */
  def environment[R](implicit trace: Trace): URIO[R, ZEnvironment[R]] =
    FiberRef.currentEnvironment.get.asInstanceOf[URIO[R, ZEnvironment[R]]]

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
    ZIO.executorWith(ZIO.successFn)

  /**
   * Constructs an effect based on the current executor.
   */
  def executorWith[R, E, A](f: Executor => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withFiberRuntime[R, E, A]((fiberState, _) => f(fiberState.getCurrentExecutor()))

  /**
   * Determines whether any element of the `Iterable[A]` satisfies the effectual
   * predicate `f`.
   */
  def exists[R, E, A](
    as: => Iterable[A]
  )(f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Boolean] =
    succeed(as.iterator).flatMap { iterator =>
      ZIO.iterate(false)(!_ && iterator.hasNext)(_ => f(iterator.next()))
    }

  /**
   * Returns an effect that models failure with the specified error. The moral
   * equivalent of `throw` for pure code.
   *
   * The error is in the typed error channel `E`, so `catchAll`, `orElse` and
   * `either` can handle it.
   *
   * {{{
   * val declined: IO[String, Nothing] = ZIO.fail("card declined")
   * val handled: UIO[String]          = declined.catchAll(e => ZIO.succeed("payment failed: " + e))
   * // succeeds with "payment failed: card declined"
   * }}}
   */
  def fail[E](error: => E)(implicit trace: Trace): IO[E, Nothing] =
    failCause(Cause.fail(error))

  /**
   * Returns an effect that models failure with the specified `Cause`.
   */
  def failCause[E](cause: => Cause[E])(implicit trace0: Trace): IO[E, Nothing] =
    ZIO.withFiberRuntime[Any, E, Nothing] { (state, _) =>
      val trace = state.generateStackTrace()
      val refs  = state.getFiberRefs(false)
      val spans = refs.getOrDefault(FiberRef.currentLogSpan)
      val anns  = refs.getOrDefault(FiberRef.currentLogAnnotations)
      Exit.failCause(cause.applyAll(trace, spans, anns))
    }

  /**
   * Returns the `FiberId` of the fiber executing the effect that calls this
   * method.
   */
  def fiberId(implicit trace: Trace): UIO[FiberId.Runtime] =
    fiberIdWith(ZIO.successFn)

  /**
   * Constructs an effect based on the `FiberId` of the fiber executing the
   * effect that calls this method.
   */
  def fiberIdWith[R, E, A](f: FiberId.Runtime => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withFiberRuntime[R, E, A]((fiberState, _) => f(fiberState.id))

  /**
   * Filters the collection using the specified effectual predicate.
   */
  def filter[R, E, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => ZIO[R, E, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): ZIO[R, E, Collection[A]] =
    ZIO
      .foldLeft(as)(bf.newBuilder(as)) { (builder, a) =>
        f(a).map(b => if (b) builder += a else builder)
      }
      .map(_.result())

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
   * removing all elements that satisfy the predicate.
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
   * each of the specified effects in order until one of them succeeds. When
   * every effect fails, the result fails with the cause of the last one. A
   * cause that holds a typed failure continues the search, even when it also
   * holds a defect; a cause that holds no typed failure (only a defect or an
   * interruption) stops the search, and the result fails with that cause.
   */
  def firstSuccessOf[R, R1 <: R, E, A](
    zio: => ZIO[R, E, A],
    rest: => Iterable[ZIO[R1, E, A]]
  )(implicit trace: Trace): ZIO[R1, E, A] =
    ZIO.suspendSucceed {
      val iterator = rest.iterator

      def loop(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
        zio.catchAllCause { cause =>
          cause.failureOrCause.fold(
            _ => if (iterator.hasNext) loop(iterator.next()) else Exit.failCause(cause),
            cause => Exit.failCause(cause)
          )
        }

      loop(zio)
    }

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
    ZIO.suspendSucceed {
      val iterator = in.iterator
      var s        = zero

      ZIO.whileLoop(iterator.hasNext)(f(s, iterator.next()))(s = _).as(s)
    }

  /**
   * Folds an Iterable[A] using an effectual function f, working sequentially
   * from right to left.
   */
  def foldRight[R, E, S, A](
    in: => Iterable[A]
  )(zero: => S)(f: (A, S) => ZIO[R, E, S])(implicit trace: Trace): ZIO[R, E, S] =
    foldLeft(in.toSeq.reverse)(zero)((s, a) => f(a, s))

  /**
   * Determines whether all elements of the `Iterable[A]` satisfy the effectual
   * predicate `f`.
   */
  def forall[R, E, A](
    as: => Iterable[A]
  )(f: A => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Boolean] =
    succeed(as.iterator).flatMap { iterator =>
      ZIO.iterate(true)(_ && iterator.hasNext)(_ => f(iterator.next()))
    }

  /**
   * Applies the function `f` to each element of the `Collection[A]` and returns
   * the results in a new `Collection[B]`.
   *
   * For a parallel version of this method, see `foreachPar`. If you do not need
   * the results, see `foreachDiscard` for a more efficient implementation.
   *
   * The effects run one after another, from left to right. The first one that
   * fails stops the iteration, and the result fails with its error.
   *
   * {{{
   * def lookup(userId: Int): IO[String, String] =
   *   if (userId > 0) ZIO.succeed("user-" + userId) else ZIO.fail("invalid id: " + userId)
   *
   * val names: IO[String, List[String]] = ZIO.foreach(List(7, 12))(lookup)
   * // succeeds with List(user-7, user-12)
   * }}}
   */
  def foreach[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] = {
    import scala.collection.compat._
    in.sizeCompare(1) match {
      case -1 => ZIO.succeed(bf.fromSpecific(in)(Nil))
      case 0  => ZIO.suspendSucceed(f(in.head).map(b => (bf.newBuilder(in) += b).result()))
      case _ =>
        ZIO.suspendSucceed {
          val iterator = in.iterator
          val builder  = bf.newBuilder(in)

          ZIO.whileLoop(iterator.hasNext)(f(iterator.next()))(builder += _).as(builder.result())
        }
    }
  }

  /**
   * Applies the function `f` to each element of the `Set[A]` and returns the
   * results in a new `Set[B]`.
   *
   * For a parallel version of this method, see `foreachPar`. If you do not need
   * the results, see `foreachDiscard` for a more efficient implementation.
   */
  final def foreach[R, E, A, B](in: Set[A])(f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, Set[B]] =
    in.size match {
      case 0 => ZIO.succeed(Set.empty[B])
      case 1 => ZIO.suspendSucceed(f(in.head).map(Set(_)))
      case n =>
        ZIO.suspendSucceed {
          val iterator = in.iterator
          val builder  = Set.newBuilder[B]
          builder.sizeHint(n)

          ZIO.whileLoop(iterator.hasNext)(f(iterator.next()))(builder += _).as(builder.result())
        }
    }

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
    in.length match {
      case 0 => ZIO.succeed(Array.empty[B])
      case 1 => ZIO.suspendSucceed(f(in(0)).map(Array(_)))
      case size =>
        ZIO.suspendSucceed {
          val iterator = in.iterator
          val arr      = Array.ofDim[B](size)
          var i        = 0

          ZIO
            .whileLoop(iterator.hasNext)(f(iterator.next())) { b =>
              arr(i) = b
              i += 1
            }
            .as(arr)
        }
    }

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
    map.size match {
      case 0 => ZIO.succeed(Map.empty)
      case 1 => ZIO.suspendSucceed(f.tupled(map.head).map(Map.apply(_)))
      case _ =>
        ZIO.suspendSucceed {
          val iterator = map.iterator
          val builder  = Map.newBuilder[Key2, Value2]

          ZIO
            .whileLoop(iterator.hasNext) {
              val kv = iterator.next()
              f(kv._1, kv._2)
            }(builder += _)
            .as(builder.result())
        }
    }

  /**
   * Applies the function `f` if the argument is non-empty and returns the
   * results in a new `Option[B]`.
   */
  final def foreach[R, E, A, B](in: Option[A])(f: A => ZIO[R, E, B])(implicit
    trace: Trace
  ): ZIO[R, E, Option[B]] =
    in.fold[ZIO[R, E, Option[B]]](none)(a => ZIO.suspendSucceed(f(a).map(Some(_))))

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
    ZIO.suspendSucceed {
      import scala.collection.compat._
      val as0 = as
      as0.sizeCompare(1) match {
        case -1 => Exit.unit
        case 0  => f(as0.head).unit
        case _ =>
          val iterator = as0.iterator
          ZIO.whileLoop(iterator.hasNext)(f(iterator.next()))(ZIO.unitFn)
      }
    }

  /**
   * Applies the function `f` to each element of the `Collection[A]` and returns
   * the result in a new `Collection[B]` using the specified execution strategy.
   */
  def foreachExec[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    exec: => ExecutionStrategy
  )(
    f: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    as.size match {
      case 0 => ZIO.succeed(bf.fromSpecific(as)(Nil))
      case 1 => ZIO.suspendSucceed(f(as.head)).map(b => bf.fromSpecific(as)(as.map(_ => b)))
      case size =>
        ZIO.suspendSucceed {
          exec match {
            case ExecutionStrategy.Parallel     => ZIO.foreachParUnbounded(as, size)(f)
            case ExecutionStrategy.ParallelN(n) => ZIO.foreachPar(n)(as, size)(f)
            case ExecutionStrategy.Sequential   => ZIO.foreach(as)(f)
          }
        }
    }

  /**
   * Applies the function `f` to each element of the `Collection[A]` in
   * parallel, and returns the results in a new `Collection[B]`.
   *
   * For a sequential version of this method, see `foreach`.
   *
   * The results are in the order of the input, whatever order the effects
   * finish in. By default the number of effects running at once is unbounded;
   * inside `withParallelism(n)` at most `n` run at once, and with `n` of 1 or
   * less they run one after another. When an effect fails or dies, the effects
   * still running are interrupted, and the result fails with a cause that holds
   * that failure or defect together with the interruptions of the stopped
   * effects.
   *
   * {{{
   * def fetchPrice(itemId: Int): UIO[Int] =
   *   ZIO.sleep((100 - itemId).millis).as(itemId * 10)
   *
   * val prices: UIO[List[Int]] = ZIO.foreachPar(List(3, 1, 2))(fetchPrice)
   * // succeeds with List(30, 10, 20)
   * }}}
   */
  def foreachPar[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    as.size match {
      case 0 =>
        ZIO.succeed(bf.fromSpecific(as)(Nil))
      case 1 =>
        ZIO.suspendSucceed(f(as.head)).map(b => bf.fromSpecific(as)(as.map(_ => b)))
      case size =>
        ZIO.parallelismWith {
          case Some(n) if n < size => foreachPar(n)(as, size)(f)
          case _                   => foreachParUnbounded(as, size)(f)
        }
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
   * val orderIds: List[Int]    = List(101, 102, 103)
   * def ship(id: Int): UIO[Unit] = ZIO.log("shipping order " + id)
   *
   * val shipAll: UIO[Unit] =
   *   orderIds.foldLeft(ZIO.unit) { (acc, id) => acc.zipParLeft(ship(id)) }
   * }}}
   *
   * Additionally, interrupts all effects on any failure. Inside
   * `withParallelism(n)` at most `n` effects run at once.
   */
  def foreachParDiscard[R, E, A](
    as: => Iterable[A]
  )(f: A => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.suspendSucceed {
      val as0 = as
      as0.size match {
        case 0 => Exit.unit
        case 1 => f(as0.head).unit
        case size =>
          ZIO.parallelismWith {
            case Some(n) if n < size => foreachParDiscard(n)(as0, size)(f)
            case _                   => foreachParUnboundedDiscard(as0, size)(f)
          }
      }
    }

  /**
   * Returns an effect that forks all of the specified values, and returns a
   * composite fiber that produces a collection of their results, of the same
   * type as the input and in the same order.
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
  def forkAllDiscard[R, E, A](as: => Iterable[ZIO[R, E, A]])(implicit trace: Trace): URIO[R, Fiber[E, Unit]] =
    ZIO.suspendSucceed {
      ZIO.foreach[R, Nothing, ZIO[R, E, A], Fiber[E, A], Iterable](as)(_.fork)
    }.map(Fiber.collectAllDiscard(_))

  /**
   * Constructs a `ZIO` value of the appropriate type for the specified input.
   */
  def from[Input](input: => Input)(implicit
    constructor: ZIOConstructor[Nothing, Any, Input],
    trace: Trace
  ): ZIO[constructor.OutEnvironment, constructor.OutError, constructor.OutSuccess] =
    constructor.make(input)

  /**
   * Turns an effect that produces an `AutoCloseable` into a scoped resource:
   * the value is acquired uninterruptibly and its `close` method runs when the
   * scope closes. When the produced value is `null`, nothing is closed.
   */
  def fromAutoCloseable[R, E, A <: AutoCloseable](fa: => ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R with Scope, E, A] =
    acquireRelease(fa)(a => if (a eq null) Exit.unit else ZIO.succeed(a.close()))

  /**
   * Lifts an `Either` into a `ZIO` value.
   *
   * The effect succeeds with the value of a `Right` and fails with the value of
   * a `Left`.
   *
   * {{{
   * val parsed: IO[String, Int] = ZIO.fromEither(Right(8080))
   * // succeeds with 8080
   * val rejected: IO[String, Int] = ZIO.fromEither(Left("port out of range"))
   * // fails with "port out of range"
   * }}}
   */
  def fromEither[E, A](v: => Either[E, A])(implicit trace: Trace): IO[E, A] =
    ZIO.suspendSucceed {
      v match {
        case Right(s) => Exit.succeed(s)
        case Left(e)  => ZIO.fail(e)
      }
    }

  /**
   * Lifts an `Either[Cause[E], A]` into a `ZIO` value: the effect succeeds with
   * the value of a `Right` and fails with the cause in a `Left`.
   */
  def fromEitherCause[E, A](v: => Either[Cause[E], A])(implicit trace: Trace): IO[E, A] =
    ZIO.suspendSucceed {
      v match {
        case Right(s) => Exit.succeed(s)
        case Left(c)  => ZIO.failCause(c)
      }
    }

  /**
   * Creates a `ZIO` value that represents the exit value of the specified
   * fiber.
   */
  def fromFiber[E, A](fiber: => Fiber[E, A])(implicit trace: Trace): IO[E, A] =
    ZIO.suspendSucceed(fiber.join)

  /**
   * Creates a `ZIO` value that represents the exit value of the specified
   * fiber.
   */
  def fromFiberZIO[R, E, A](fiber: => ZIO[R, E, Fiber[E, A]])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(fiber.flatMap(_.join))

  /**
   * Returns an effect that, when executed, will both create and launch a
   * [[scala.concurrent.Future]], feeding it an
   * [[scala.concurrent.ExecutionContext]] that is backed by ZIO's own executor.
   *
   * The effect succeeds with the value of the future, or fails with the
   * exception it completes with. When the fiber is interrupted, the effect
   * stops waiting for a plain future at once, and that future keeps running; a
   * `CancelableFuture` is cancelled instead, and the interruption completes
   * when the cancellation does. To stop a future between its steps, see
   * `fromFutureInterrupt`.
   *
   * {{{
   * import scala.concurrent.Future
   *
   * val answer: Task[Int] = ZIO.fromFuture(implicit ec => Future(21 * 2))
   * // succeeds with 42
   * }}}
   */
  def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A])(implicit trace: Trace): Task[A] =
    ZIO.executorWith { executor =>
      ZIO.suspend {
        import scala.util.{Failure, Success}

        val ec = executor.asExecutionContext
        val f  = make(ec)
        f.value match {
          case None =>
            ZIO.asyncInterrupt { (k: Task[A] => Unit) =>
              f.onComplete {
                case s: Success[A] => k(Exit.succeed(s.value))
                case f: Failure[A] => k(ZIO.fail(f.exception))
              }(ec)

              Left {
                f match {
                  case cf: CancelableFuture[A] =>
                    ZIO.suspendSucceedUnsafe(implicit u =>
                      if (cf.isCompleted) Exit.unit else ZIO.fromFutureNow(cf.cancel()).ignore
                    )
                  case _ =>
                    Exit.unit
                }
              }
            }
          case Some(outcome) => outcome.fold(ZIO.fail(_), ZIO.successFn)
        }
      }
    }

  /**
   * Similar to [[fromFuture]], but doesn't attempt to suspend the future. Use
   * only when the future is already suspended by another effect
   *
   * @see
   *   Overloaded method that allows using the ZIO executor-backed execution
   *   context
   */
  private[zio] def fromFutureNow[A](f: concurrent.Future[A])(implicit trace: Trace, unsafe: Unsafe): Task[A] = {
    import scala.util.{Failure, Success}
    f.value match {
      case None =>
        ZIO.executorWith { executor =>
          val ec = executor.asExecutionContext
          ZIO.asyncInterrupt { (k: Task[A] => Unit) =>
            f.onComplete {
              case s: Success[A] => k(Exit.succeed(s.value))
              case f: Failure[A] => k(ZIO.fail(f.exception))
            }(ec)
            Left {
              f match {
                case cf: CancelableFuture[A] =>
                  ZIO.suspendSucceed(if (cf.isCompleted) Exit.unit else ZIO.fromFutureNow(cf.cancel()).ignore)
                case _ => Exit.unit
              }
            }
          }
        }
      case Some(outcome) => outcome.fold(ZIO.fail(_), ZIO.successFn)
    }
  }

  /**
   * Imports a [[scala.concurrent.Promise]]: the effect waits for the promise to
   * complete, then succeeds with its value or fails with its exception.
   */
  def fromPromiseScala[A](promise: => scala.concurrent.Promise[A])(implicit trace: Trace): Task[A] =
    ZIO.suspend(ZIO.fromFutureNow(promise.future)(trace, Unsafe))

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
    ZIO.executorWith { executor =>
      import scala.util.{Failure, Success}
      val interrupted = new java.util.concurrent.atomic.AtomicBoolean(false)
      val latch       = scala.concurrent.Promise[Unit]()
      ZIO.suspend {
        val ec = executor.asExecutionContext
        val interruptibleEC = new scala.concurrent.ExecutionContext {
          def execute(runnable: Runnable): Unit =
            if (!interrupted.get) ec.execute(runnable)
            else latch.success(())
          def reportFailure(cause: Throwable): Unit =
            ec.reportFailure(cause)
        }
        val f = make(interruptibleEC)
        f.value match {
          case None =>
            ZIO.async { (cb: Task[A] => Any) =>
              f.onComplete {
                case s: Success[A] => latch.success(()); cb(Exit.succeed(s.value))
                case f: Failure[A] => latch.success(()); cb(ZIO.fail(f.exception))
              }(interruptibleEC)
            }
          case Some(outcome) => outcome.fold(ZIO.fail(_), ZIO.successFn)
        }
      }.onInterrupt(
        ZIO.suspendSucceedUnsafe { implicit u =>
          interrupted.set(true)
          ZIO.fromFutureNow(latch.future).orDie
        }
      )
    }

  /**
   * Lifts an `Option` into a `ZIO` but preserves the error as an option in the
   * error channel, making it easier to compose in some scenarios.
   *
   * The effect succeeds with the value of a `Some` and fails with `None` when
   * the option is empty.
   *
   * {{{
   * val port: IO[Option[Nothing], Int] = ZIO.fromOption(Some(8080))
   * // succeeds with 8080
   * val required: IO[String, Int] = ZIO.fromOption(Option.empty[Int]).orElseFail("port is not set")
   * // fails with "port is not set"
   * }}}
   */
  def fromOption[A](v: => Option[A])(implicit trace: Trace): IO[Option[Nothing], A] =
    ZIO.suspendSucceed(v.fold[IO[Option[Nothing], A]](fail(None))(ZIO.successFn))

  /**
   * Lifts a `Try` into a `ZIO`.
   */
  def fromTry[A](value: => scala.util.Try[A])(implicit trace: Trace): Task[A] =
    ZIO.suspend(value match {
      case scala.util.Success(v) => Exit.succeed(v)
      case scala.util.Failure(t) => ZIO.fail(t)
    })

  /**
   * Returns a collection of all `FiberRef` values for the fiber running this
   * effect.
   */
  def getFiberRefs(implicit trace: Trace): UIO[FiberRefs] =
    ZIO.withFiberRuntime[Any, Nothing, FiberRefs] { (fiberState, _) =>
      Exit.succeed(fiberState.getFiberRefs())
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
      case Some(v) => Exit.succeed(v)
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
   * Like [[never]], but fibers running this effect won't be garbage collected
   * unless interrupted.
   */
  @deprecated("Use `ZIO.never` instead", "2.1.20")
  def infinity(implicit trace: Trace): UIO[Nothing] =
    ZIO.sleep(Duration.Infinity) *> ZIO.never

  /**
   * Inherits values from all [[FiberRef]] instances into current fiber.
   */
  def inheritFiberRefs(childFiberRefs: FiberRefs)(implicit trace: Trace): UIO[Unit] =
    ZIO.updateFiberRefs { (parentFiberId, parentFiberRefs) =>
      parentFiberRefs.joinAs(parentFiberId)(childFiberRefs)
    }

  /**
   * Returns an effect that is interrupted as if by the fiber calling this
   * method.
   *
   * The effect ends with a cause that holds only an interruption, so the
   * fiber's finalizers run and the fiber exits as interrupted; error handlers
   * such as `catchAll` do not catch it.
   *
   * {{{
   * val stopped: UIO[Boolean] = ZIO.interrupt.exit.map(_.isInterrupted)
   * // succeeds with true
   * }}}
   */
  def interrupt(implicit trace: Trace): UIO[Nothing] =
    fiberIdWith(interruptAs(_))

  /**
   * Returns an effect that is interrupted as if by the specified fiber.
   */
  def interruptAs(fiberId: => FiberId)(implicit trace: Trace): UIO[Nothing] =
    failCause(Cause.interrupt(fiberId))

  /**
   * Prefix form of `ZIO#interruptible`.
   */
  def interruptible[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.UpdateRuntimeFlagsWithin(trace, RuntimeFlags.enableInterruption, _ => zio)

  /**
   * Makes the effect interruptible, but passes it a restore function that can
   * be used to restore the inherited interruptibility from whatever region the
   * effect is composed into.
   */
  def interruptibleMask[R, E, A](
    k: ZIO.InterruptibilityRestorer => ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    interruptionMasked(enable = true, k)

  private def interruptionMasked[R, E, A](
    enable: Boolean,
    f: InterruptibilityRestorer => ZIO[R, E, A]
  )(implicit trace: Trace) =
    ZIO.UpdateRuntimeFlagsWithin(
      trace,
      if (enable) RuntimeFlags.enableInterruption else RuntimeFlags.disableInterruption,
      oldFlags =>
        if (RuntimeFlags.interruption(oldFlags)) f(InterruptibilityRestorer.MakeInterruptible)
        else f(InterruptibilityRestorer.MakeUninterruptible)
    )

  /**
   * Retrieves the definition of a fatal error.
   */
  @deprecated("isFatal is deprecated, kept only for binary compatability. Do not use.", "2.1.21")
  def isFatal(implicit trace: Trace): UIO[Throwable => Boolean] =
    ZIO.succeed(!nonFatal(_))

  /**
   * Constructs an effect based on the definition of a fatal error.
   */
  @deprecated("isFatalWith is deprecated, kept only for binary compatability. Do not use.", "2.1.21")
  def isFatalWith[R, E, A](f: (Throwable => Boolean) => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(f(!nonFatal(_)))

  /**
   * Iterates with the specified effectual function. The moral equivalent of:
   *
   * {{{
   * val initial: Int          = 1
   * def cont(s: Int): Boolean = s < 100
   * def body(s: Int): Int     = s * 2
   *
   * var s = initial
   *
   * while (cont(s)) {
   *   s = body(s)
   * }
   *
   * // the same loop as an effect; both end with 128
   * val doubled: UIO[Int] = ZIO.iterate(initial)(cont)(s => ZIO.succeed(body(s)))
   * }}}
   */
  def iterate[R, E, S](
    initial: => S
  )(cont: S => Boolean)(body: S => ZIO[R, E, S])(implicit trace: Trace): ZIO[R, E, S] =
    ZIO.suspendSucceed {
      var result = initial

      ZIO
        .whileLoop(cont(result))(body(result))(result = _)
        .as(result)
    }

  /**
   * A low-level while-loop with direct support in the ZIO runtime. The only
   * reason to use this constructor is performance.
   *
   * See [[ZIO.iterate]] for a user-friendly version of this operator that is
   * compatible with purely functional code.
   */
  def whileLoop[R, E, A](check: => Boolean)(body: => ZIO[R, E, A])(process: A => Any)(implicit
    trace: Trace
  ): ZIO[R, E, Unit] =
    ZIO.WhileLoop(trace, () => check, () => body, process)

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
   * val initial: Int          = 1
   * def cont(s: Int): Boolean = s <= 3
   * def inc(s: Int): Int      = s + 1
   * def body(s: Int): String  = "page-" + s
   *
   * var s  = initial
   * var as = List.empty[String]
   *
   * while (cont(s)) {
   *   as = body(s) :: as
   *   s = inc(s)
   * }
   *
   * val result = as.reverse
   *
   * // the same loop as an effect; both produce List(page-1, page-2, page-3)
   * val pages: UIO[List[String]] = ZIO.loop(initial)(cont, inc)(s => ZIO.succeed(body(s)))
   * }}}
   */
  def loop[R, E, A, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, List[A]] =
    ZIO.suspendSucceed {
      var s       = initial
      val builder = List.newBuilder[A]

      ZIO
        .whileLoop(cont(s)) {
          val effect = body(s)
          s = inc(s)
          effect
        }(builder += _)
        .as(builder.result())
    }

  /**
   * Loops with the specified effectual function purely for its effects. The
   * moral equivalent of:
   *
   * {{{
   * val initial: Int          = 1
   * def cont(s: Int): Boolean = s <= 3
   * def inc(s: Int): Int      = s + 1
   * def body(s: Int): Unit    = println("sending reminder " + s)
   *
   * var s = initial
   *
   * while (cont(s)) {
   *   body(s)
   *   s = inc(s)
   * }
   *
   * // the same loop as an effect
   * val reminders: UIO[Unit] = ZIO.loopDiscard(initial)(cont, inc)(s => ZIO.succeed(body(s)))
   * }}}
   */
  def loopDiscard[R, E, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    ZIO.suspendSucceed {
      var s = initial
      ZIO.whileLoop(cont(s)) {
        val effect = body(s)
        s = inc(s)
        effect
      }(ZIO.unitFn)
    }

  /**
   * Returns a new effect where boolean value of this effect is negated.
   */
  def not[R, E](effect: => ZIO[R, E, Boolean])(implicit trace: Trace): ZIO[R, E, Boolean] =
    ZIO.suspendSucceed(effect.negate)

  /**
   * Logs the specified message at the current log level.
   *
   * The current log level is `LogLevel.Info` unless it is changed with
   * `ZIO.logLevel`. The current log spans and annotations are added to the
   * entry.
   *
   * {{{
   * val placed: UIO[Unit] = ZIO.log("order 1042 placed")
   * // the default logger prints a line such as
   * // timestamp=... level=INFO thread=#zio-fiber-1 message="order 1042 placed" location=... file=... line=...
   * }}}
   */
  def log(message: => String)(implicit trace: Trace): UIO[Unit] =
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.log(() => message, Cause.empty, None, trace)

      Exit.unit
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
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.log(() => message, cause, None, trace)

      Exit.unit
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
   * Adds the log annotation `key` = `value` to every log entry written by this
   * fiber until the scope closes, when the previous annotations are restored.
   */
  def logAnnotateScoped(key: => String, value: => String)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    logAnnotateScoped(LogAnnotation(key, value))

  /**
   * Adds the specified log annotations to every log entry written by this fiber
   * until the scope closes, when the previous annotations are restored.
   */
  def logAnnotateScoped(logAnnotation: => LogAnnotation, logAnnotations: LogAnnotation*)(implicit
    trace: Trace
  ): ZIO[Scope, Nothing, Unit] =
    logAnnotateScoped(Set(logAnnotation) ++ logAnnotations.toSet)

  /**
   * Adds the specified set of log annotations to every log entry written by
   * this fiber until the scope closes, when the previous annotations are
   * restored.
   */
  def logAnnotateScoped(logAnnotations: => Set[LogAnnotation])(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    FiberRef.currentLogAnnotations.locallyScopedWith(_ ++ logAnnotations.map { case LogAnnotation(key, value) =>
      key -> value
    })

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
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.log(() => message, cause, someDebug, trace)

      Exit.unit
    }

  /**
   * Logs the specified cause at the debug log level.
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
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.log(() => message, cause, someError, trace)

      Exit.unit
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
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.log(() => message, cause, someFatal, trace)

      Exit.unit
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
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.log(() => message, cause, someInfo, trace)

      Exit.unit
    }

  /**
   * Logs the specified cause at the informational log level.
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
   * Sets the level used by `ZIO.log` and `ZIO.logCause` to `level` until the
   * scope closes, when the previous level is restored.
   */
  def logLevelScoped(level: LogLevel)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    FiberRef.currentLogLevel.locallyScoped(level)

  /**
   * Returns a `LogSpan` whose `apply` runs an effect inside a new log span with
   * the specified label. Each log entry written in that effect reports the
   * label and the time elapsed since the span started.
   * {{{
   * final case class Request(path: String)
   * def parseRequest(req: Request): UIO[String] = ZIO.log("parsed " + req.path).as(req.path)
   *
   * val req = Request("/orders/17")
   * ZIO.logSpan("parsing") { parseRequest(req) }
   * // the default logger adds parsing=<elapsed>ms to the log line
   * }}}
   */
  def logSpan(label: => String): LogSpan = new LogSpan(() => label)

  /**
   * Starts a new log span with the specified label that lasts until the scope
   * closes. Each log entry written in the meantime reports the label and the
   * time elapsed since the span started.
   */
  def logSpanScoped(label: => String)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    FiberRef.currentLogSpan.locallyScopedWith { stack =>
      val instant = java.lang.System.currentTimeMillis()
      val logSpan = zio.LogSpan(label, instant)

      logSpan :: stack
    }

  /**
   * Retrieves the log spans associated with the current scope.
   */
  def logSpans(implicit trace: Trace): UIO[List[zio.LogSpan]] =
    FiberRef.currentLogSpan.get

  /**
   * Logs the specified message at the trace log level.
   */
  def logTrace(message: => String)(implicit trace: Trace): UIO[Unit] =
    logTraceCause(message, Cause.empty)

  /**
   * Logs the specified cause at the trace log level.
   */
  def logTraceCause(message: => String, cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.log(() => message, cause, someTrace, trace)

      Exit.unit
    }

  /**
   * Logs the specified cause at the trace log level.
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
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiberState, _) =>
      fiberState.log(() => message, cause, someWarning, trace)

      Exit.unit
    }

  /**
   * Logs the specified cause at the warning log level.
   */
  def logWarningCause(cause: => Cause[Any])(implicit trace: Trace): UIO[Unit] =
    logWarningCause("", cause)

  /**
   * Returns a memoized version of the specified effectual function.
   */
  def memoize[R, E, A, B](f: A => ZIO[R, E, B])(implicit trace: Trace): UIO[A => ZIO[R, E, B]] =
    ZIO.succeed {
      @inline implicit def u: Unsafe = Unsafe

      val ref = Ref.unsafe.make(Map.empty[A, Promise[E, (FiberRefs.Patch, B)]])
      def loop(a: A): ZIO[R, E, B] =
        ZIO.fiberIdWith { fiberId =>
          val isSetter = new AtomicBoolean()
          for {
            promise <- ref.modify { map =>
                         map.getOrElse(a, null) match {
                           case null =>
                             val p = Promise.unsafe.make[E, (FiberRefs.Patch, B)](fiberId)
                             val f2 = ZIO.uninterruptibleMask { restore =>
                               isSetter.set(true)
                               restore(f(a).diffFiberRefs).exitWith {
                                 case ex if ex.isInterruptedOnly =>
                                   ref.unsafe.update(_ - a)
                                   p.unsafe.done(ex)
                                   Exit.unit
                                 case ex =>
                                   p.unsafe.done(ex)
                                   Exit.unit
                               }.fork
                             }
                             (f2.as(p), map.updated(a, p))
                           case p => (Exit.succeed(p), map)
                         }
                       }.flatten
            b <- promise.await.exitWith {
                   case Exit.Success((patch, b))                      => ZIO.patchFiberRefs(patch).as(b)
                   case ex if !isSetter.get() && ex.isInterruptedOnly => loop(a)
                   case ex                                            => ex.asInstanceOf[Exit[E, Nothing]]
                 }
          } yield b
        }

      loop
    }

  /**
   * Merges an `Iterable[IO]` to a single IO, working sequentially.
   */
  def mergeAll[R, E, A, B](
    in: => Iterable[ZIO[R, E, A]]
  )(zero: => B)(f: (B, A) => B)(implicit trace: Trace): ZIO[R, E, B] =
    ZIO.foldLeft(in)(zero)((b, zio) => zio.map(a => f(b, a)))

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
   * Gets current metrics snapshot.
   */
  def metrics(implicit trace: Trace): UIO[Metrics] =
    ZIO.succeedUnsafe { implicit u =>
      Metrics(internal.metrics.metricRegistry.snapshot())
    }

  /**
   * Returns a effect that will never produce anything. The moral equivalent of
   * `while(true) {}`, only without the wasted CPU cycles. Fibers that execute
   * this effect will be automatically garbage collected on the JVM when no
   * explicit references to them are held, because they cannot be reactivated.
   *
   * {{{
   * val waited: UIO[Option[Nothing]] = ZIO.never.timeout(100.millis)
   * // succeeds with None after 100 milliseconds
   * }}}
   */
  def never(implicit trace: Trace): UIO[Nothing] =
    async[Any, Nothing, Nothing](ZIO.unitFn)

  /**
   * Returns an effect that succeeds with the `None` value.
   */
  val none: UIO[Option[Nothing]] =
    succeed(None)(Trace.empty)

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
  def onExecutor[R, E, A](executor: => Executor)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    onExecutorWith(_ => executor)(zio)

  @inline
  private def onExecutorWith[R, E, A](
    getExecutor: Fiber.Runtime[?, ?] => Executor
  )(zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withFiberRuntime[R, E, A] { (fiber, status) =>
      val newExecutor     = getExecutor(fiber)
      val oldOverride     = fiber.getFiberRefOrNull(FiberRef.overrideExecutor)
      val isEmptyOverride = oldOverride == null || oldOverride.isEmpty
      val isLocked        = RuntimeFlags.eagerShiftBack(status.runtimeFlags) || !isEmptyOverride

      val oldExecutor =
        if (isEmptyOverride) Runtime.defaultExecutor
        else oldOverride.get

      if (isLocked && (oldExecutor eq newExecutor)) zio
      else {
        val shift =
          FiberRef.overrideExecutor.locally(Some(newExecutor)) {
            // if not already on target executor, we need to yield
            if (fiber.getRunningExecutor().contains(newExecutor)) zio
            else ZIO.yieldNow *> zio
          }
        if (isLocked)
          shift <* (if (fiber.getRunningExecutor().contains(oldExecutor)) Exit.unit else ZIO.yieldNow)
        else shift
      }
    }

  /**
   * Shifts execution to the specified executor and shifts it back to the
   * previous executor, if any, when the scope is closed.
   */
  def onExecutorScoped(executor: => Executor)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    ZIO.descriptorWith { descriptor =>
      val oldExecutor = descriptor.executor
      val newExecutor = executor

      if (descriptor.isLocked && oldExecutor == newExecutor)
        Exit.unit
      else if (descriptor.isLocked)
        ZIO.acquireRelease(ZIO.shift(executor))(_ => ZIO.shift(descriptor.executor))
      else
        ZIO.acquireRelease(ZIO.shift(executor))(_ => ZIO.unshift)
    }

  /**
   * Applies the specified changes to the `FiberRef` values for the fiber
   * running this workflow.
   */
  def patchFiberRefs(patch: FiberRefs.Patch)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
    updateFiberRefs(patch)

  /**
   * Returns a new scoped workflow that runs finalizers added to the scope of
   * this workflow in parallel.
   */
  def parallelFinalizers[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.environmentWithZIO[R] { environment =>
      environment.getDynamic[Scope] match {
        case None => zio
        case Some(scope) =>
          scope.executionStrategy match {
            case ExecutionStrategy.Parallel => zio
            case _                          => scope.forkWith(ExecutionStrategy.Parallel).flatMap(_.extend[R](zio))
          }
      }
    }

  /**
   * Like `parallelFinalizers`, but passes `f` a `FinalizersRestorer` that runs
   * an inner effect with the finalizer strategy of the enclosing scope
   * (sequential, parallel or parallel with a bound). Without a `Scope` in the
   * environment, the effect runs unchanged and the restorer does nothing.
   */
  def parallelFinalizersMask[R, E, A](f: ZIO.FinalizersRestorer => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.environmentWithZIO[R] { environment =>
      environment.getDynamic[Scope] match {
        case None => f(ZIO.FinalizersRestorer.Identity)
        case Some(scope) =>
          scope.executionStrategy match {
            case ExecutionStrategy.Parallel     => ZIO.parallelFinalizers(f(ZIO.FinalizersRestorer.MakeParallel))
            case ExecutionStrategy.ParallelN(n) => ZIO.parallelFinalizers(f(ZIO.FinalizersRestorer.MakeParallelN(n)))
            case ExecutionStrategy.Sequential   => ZIO.parallelFinalizers(f(ZIO.FinalizersRestorer.MakeSequential))
          }
      }
    }

  /**
   * Retrieves the maximum number of fibers for parallel operators or `None` if
   * it is unbounded.
   */
  def parallelism(implicit trace: Trace): UIO[Option[Int]] =
    FiberRef.parallelism.get

  /**
   * Retrieves the current maximum number of fibers for parallel operators and
   * uses it to run the specified effect.
   */
  def parallelismWith[R, E, A](f: Option[Int] => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    FiberRef.parallelism.getWith(f)

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

  /**
   * Provides the services that `layer` produces to `zio`. The services `zio`
   * needs beyond those (`RIn2`) and the inputs of the layer (`RIn`) stay in the
   * environment of the returned effect.
   */
  def provideLayer[RIn, E, ROut, RIn2, ROut2](layer: ZLayer[RIn, E, ROut])(
    zio: ZIO[ROut with RIn2, E, ROut2]
  )(implicit ev: EnvironmentTag[RIn2], tag: EnvironmentTag[ROut], trace: Trace): ZIO[RIn with RIn2, E, ROut2] =
    zio.provideSomeLayer[RIn with RIn2](layer)

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
   * The losers are interrupted, and the result is returned as soon as the first
   * effect completes, without waiting for the losers' finalizers to finish.
   * This differs from `ZIO#raceFirst`, which waits for the loser to terminate.
   */
  def raceFirst[R, R1 <: R, E, A](zio: ZIO[R, E, A], ios: Iterable[ZIO[R1, E, A]])(implicit
    trace: Trace
  ): ZIO[R1, E, A] =
    (zio.exit raceAll ios.map(_.exit)).unexit

  /**
   * Retrieves the `Random` service for this workflow.
   */
  def random(implicit trace: Trace): UIO[Random] =
    ZIO.randomWith(ZIO.successFn)

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
    ZIO.suspendSucceed(a.flatMap(ZIO.mergeAll(as)(_)(f)))

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

  // NOTE: Prefer using `Exit.failCause` which doesn't require the unused implicit `trace`
  /**
   * Returns an effect that fails with the specified cause as it is, without
   * adding the current stack trace, log spans or annotations to it (unlike
   * `failCause`). Same as `Exit.failCause(cause)`.
   */
  def refailCause[E](cause: Cause[E])(implicit trace: Trace): ZIO[Any, E, Nothing] = Exit.Failure(cause)

  /**
   * Replicates the given effect `n` times. If 0 or negative numbers are given,
   * an empty `Iterable` will be returned. The effects are not run; the returned
   * `Iterable` holds `n` references to the same effect.
   */
  def replicate[R, E, A](n: Int)(effect: ZIO[R, E, A])(implicit trace: Trace): Iterable[ZIO[R, E, A]] =
    Iterable.fill(n)(effect)

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
    ZIO.withFiberRuntime[R, Nothing, Runtime[R]] { (state, status) =>
      val fiberRefs    = state.getFiberRefs()
      val environment  = fiberRefs.getOrDefault(FiberRef.currentEnvironment).asInstanceOf[ZEnvironment[R]]
      val runtimeFlags = status.runtimeFlags
      Exit.succeed(Runtime(environment, fiberRefs, runtimeFlags))
    }

  /**
   * Retrieves an effect that succeeds with the current runtime flags, which
   * govern behavior and features of the runtime system.
   */
  def runtimeFlags(implicit trace: Trace): ZIO[Any, Nothing, RuntimeFlags] =
    ZIO.withFiberRuntime[Any, Nothing, RuntimeFlags] { (_, status) =>
      Exit.succeed(status.runtimeFlags)
    }

  /**
   * Returns the current scope.
   */
  def scope(implicit trace: Trace): ZIO[Scope, Nothing, Scope] =
    scopeWith(ZIO.successFn)

  /**
   * Scopes all resources used in this effect to the lifetime of the effect,
   * ensuring that their finalizers are run as soon as this effect completes
   * execution, whether by success, failure, or interruption.
   *
   * The finalizers run in the reverse order of their acquisition, and the
   * effect returned by `scoped` no longer needs a `Scope`.
   *
   * {{{
   * def openFile(name: String): ZIO[Scope, Nothing, String] =
   *   ZIO.acquireRelease(ZIO.log("open " + name).as(name))(n => ZIO.log("close " + n))
   * def useFile(handle: String): UIO[Int] =
   *   ZIO.succeed(handle.length)
   *
   * val name = "invoices.csv"
   * val size: UIO[Int] =
   *   ZIO.scoped {
   *     openFile(name).flatMap(useFile)
   *   }
   * // succeeds with 12, after logging "open invoices.csv" and "close invoices.csv"
   * }}}
   */
  def scoped[R]: ScopedPartiallyApplied[R] =
    new ScopedPartiallyApplied[R]

  /**
   * Creates a scope, uses it to perform the specified effect, and closes the
   * scope as soon as the effect completes, whether by success, failure, or
   * interruption.
   */
  def scopedWith[R, E, A](f: Scope => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    Scope.make.flatMap(scope => f(scope).onExit(scope.close(_)))

  /**
   * Accesses the current scope and uses it to perform the specified effect.
   */
  def scopeWith[R, E, A](f: Scope => ZIO[R, E, A])(implicit trace: Trace): ZIO[R with Scope, E, A] =
    FiberRef.currentEnvironment.getWith(env => f(env.asInstanceOf[ZEnvironment[Scope]].getScope))

  /**
   * Returns a new scoped workflow that runs finalizers added to the scope of
   * this workflow sequentially in the reverse of the order in which they were
   * added. Note that finalizers are run sequentially by default so this only
   * has meaning if used within a scope where finalizers are being run in
   * parallel.
   */
  def sequentialFinalizers[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.environmentWithZIO[R] { environment =>
      environment.getDynamic[Scope] match {
        case None => zio
        case Some(scope) =>
          scope.executionStrategy match {
            case ExecutionStrategy.Sequential => zio
            case _                            => scope.forkWith(ExecutionStrategy.Sequential).flatMap(_.extend[R](zio))
          }
      }
    }

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
   *
   * The returned effect requires `A` in its environment and succeeds with it.
   *
   * {{{
   * trait PaymentGateway { def charge(cents: Long): UIO[String] }
   *
   * val gateway: URIO[PaymentGateway, PaymentGateway] = ZIO.service[PaymentGateway]
   * val receipt: URIO[PaymentGateway, String]         = gateway.flatMap(_.charge(1999L))
   * }}}
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
   * trait TaxRates { def rateFor(region: String): Double }
   *
   * object TaxRates {
   *   def rateFor(region: String): URIO[TaxRates, Double] = ZIO.serviceWith[TaxRates](_.rateFor(region))
   * }
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
   * trait UserRepo { def find(userId: Int): UIO[String] }
   *
   * object UserRepo {
   *   def find(userId: Int): URIO[UserRepo, String] = ZIO.serviceWithZIO[UserRepo](_.find(userId))
   * }
   *
   * val name: UIO[String] =
   *   UserRepo.find(7).provideLayer(ZLayer.succeed[UserRepo](new UserRepo { def find(userId: Int) = ZIO.succeed("user-" + userId) }))
   * // succeeds with "user-7"
   * }}}
   */
  def serviceWithZIO[Service]: ServiceWithZIOPartiallyApplied[Service] =
    new ServiceWithZIOPartiallyApplied[Service]

  /**
   * Builds a ZIO from the specified function.
   */
  def fromFunction[In](in: In)(implicit constructor: FunctionConstructor[In], trace: Trace): constructor.Out =
    constructor(in)

  /**
   * Builds a ZIO from the specified function.
   */
  def fromFunctionZIO[In](in: In)(implicit constructor: ZIOFunctionConstructor[In], trace: Trace): constructor.Out =
    constructor(in)

  /**
   * Returns an effect that shifts execution to the specified executor. This is
   * useful to specify a default executor that effects sequenced after this one
   * will be run on if they are not shifted somewhere else. It can also be used
   * to implement higher level operators to manage where an effect is run such
   * as [[ZIO!.onExecutor]] and [[ZIO!.onExecutionContext]].
   */
  def shift(executor: => Executor)(implicit trace: Trace): UIO[Unit] =
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiberRuntime, _) =>
      val newExecutor = executor
      fiberRuntime.getFiberRef(FiberRef.overrideExecutor) match {
        case None =>
          fiberRuntime.setFiberRef(FiberRef.overrideExecutor, Some(newExecutor))
          fiberRuntime.getRunningExecutor() match {
            case Some(runningExecutor) if runningExecutor == newExecutor => Exit.unit
            case _                                                       => ZIO.yieldNow
          }
        case Some(overrideExecutor) =>
          if (overrideExecutor eq newExecutor) Exit.unit
          else {
            fiberRuntime.setFiberRef(FiberRef.overrideExecutor, Some(newExecutor))
            ZIO.yieldNow
          }
      }
    }

  /**
   * Returns an effect that suspends for the specified duration. This method is
   * asynchronous: the fiber is suspended, but no thread is blocked while it
   * waits.
   *
   * The sleep uses the `Clock` service of the workflow, so `TestClock` in ZIO
   * Test controls it.
   *
   * {{{
   * val retryLater: UIO[String] = ZIO.sleep(2.seconds).as("retrying")
   * // succeeds with "retrying" after 2 seconds
   * }}}
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
   * val counted: UIO[Boolean] =
   *   ZIO.stateful(0) {
   *     for {
   *       _     <- ZIO.updateState[Int](_ + 1)
   *       state <- ZIO.getState[Int]
   *     } yield state == 1
   *   }
   * // succeeds with true
   * }}}
   */
  def stateful[R]: StatefulPartiallyApplied[R] =
    new StatefulPartiallyApplied[R]

  /**
   * Provides a stateful ZIO workflow with its initial state, using the
   * specified patch type to combine updates to the state by different fibers in
   * a compositional way.
   */
  def statefulPatch[R]: StatefulPatchPartiallyApplied[R] =
    new StatefulPatchPartiallyApplied[R]

  /**
   * Same as `succeedBlocking`, but passes an `Unsafe` value to the code. The
   * code runs on the blocking executor and must not throw; an exception it
   * throws becomes a defect.
   */
  def succeedBlockingUnsafe[A](a: Unsafe => A)(implicit trace: Trace): UIO[A] =
    ZIO.blocking(ZIO.succeedUnsafe(a))

  /**
   * Same as `succeed`, but passes an `Unsafe` value to the code, so it can call
   * unsafe APIs. The code must not throw; an exception it throws becomes a
   * defect.
   */
  def succeedUnsafe[A](a: Unsafe => A)(implicit trace: Trace): UIO[A] =
    ZIO.succeed(a(Unsafe))

  /**
   * Returns a lazily constructed effect, whose construction may itself require
   * effects. When no environment is required (i.e., when R == Any) it is
   * conceptually equivalent to `flatten(attempt(rio))`: a non-fatal exception
   * thrown while constructing the effect becomes its failure.
   */
  def suspend[R, A](rio: => RIO[R, A])(implicit trace: Trace): RIO[R, A] =
    ZIO.suspendSucceed {
      try rio
      catch {
        case t if nonFatal(t) => Exit.Failure(Cause.fail(t))
      }
    }

  /**
   * Returns a lazily constructed effect, whose construction may itself require
   * effects. The effect must not throw any exceptions. When no environment is
   * required (i.e., when R == Any) it is conceptually equivalent to
   * `flatten(succeed(zio))`. If you wonder if the effect throws exceptions, do
   * not use this method, use [[ZIO.suspend]].
   */
  def suspendSucceed[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.FlatMap(trace, ZIO.unit, (_: Any) => zio)

  /**
   * Same as `suspendSucceed`, but passes an `Unsafe` value to the function that
   * constructs the effect. The function runs each time the returned effect
   * runs, and must not throw.
   */
  def suspendSucceedUnsafe[R, E, A](zio: Unsafe => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.FlatMap(trace, ZIO.unit, (_: Any) => zio(Unsafe))

  /**
   * Retrieves the `System` service for this workflow.
   */
  def system(implicit trace: Trace): UIO[System] =
    ZIO.systemWith(ZIO.successFn)

  /**
   * Retrieves the `System` service for this workflow and uses it to run the
   * specified workflow.
   */
  def systemWith[R, E, A](f: System => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.get(System.tag)))

  /**
   * Capture ZIO stack trace at the current point.
   */
  def stackTrace(implicit trace: Trace): UIO[StackTrace] =
    ZIO.withFiberRuntime[Any, Nothing, StackTrace] { (state, _) =>
      Exit.succeed(state.generateStackTrace())
    }

  /**
   * Tags each metric in this effect with the specific tag.
   */
  def tagged(key: => String, value: => String): Tagged =
    tagged(Set(MetricLabel(key, value)))

  /**
   * Tags each metric in this effect with the specific tag.
   */
  def tagged(tag: => MetricLabel, tags: MetricLabel*): Tagged =
    tagged(Set(tag) ++ tags.toSet)

  /**
   * Tags each metric in this effect with the specific tag.
   */
  def tagged(tags: => Set[MetricLabel]): Tagged =
    new Tagged(() => tags)

  /**
   * Adds the metric tag `key` = `value` to every metric updated by this fiber
   * until the scope closes, when the previous tags are restored.
   */
  def taggedScoped(key: => String, value: => String)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    taggedScoped(Set(MetricLabel(key, value)))

  /**
   * Adds the specified metric tags to every metric updated by this fiber until
   * the scope closes, when the previous tags are restored.
   */
  def taggedScoped(tag: => MetricLabel, tags: MetricLabel*)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    taggedScoped(Set(tag) ++ tags.toSet)

  /**
   * Adds the specified set of metric tags to every metric updated by this fiber
   * until the scope closes, when the previous tags are restored.
   */
  def taggedScoped(tags: => Set[MetricLabel])(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    FiberRef.currentTags.locallyScopedWith(_ ++ tags)

  /**
   * Retrieves the metric tags associated with the current scope.
   */
  def tags(implicit trace: Trace): ZIO[Any, Nothing, Set[MetricLabel]] =
    FiberRef.currentTags.get

  /**
   * Transplants specified effects so that when those effects fork other
   * effects, the forked effects will be governed by the scope of the fiber that
   * executes this effect.
   *
   * This can be used to "graft" deep grandchildren onto a higher-level scope,
   * effectively extending their lifespans into the parent scope.
   */
  def transplant[R, E, A](f: Grafter => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withFiberRuntime[R, E, A] { (fiberState, _) =>
      f(Grafter(fiberState))
    }

  /**
   * An effect that succeeds with a unit value.
   */
  val unit: UIO[Unit] =
    succeed(())(Trace.empty)

  /**
   * Prefix form of `ZIO#uninterruptible`.
   */
  def uninterruptible[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.UpdateRuntimeFlagsWithin(trace, RuntimeFlags.disableInterruption, _ => zio)

  /**
   * Makes the effect uninterruptible, but passes it a restore function that can
   * be used to restore the inherited interruptibility from whatever region the
   * effect is composed into.
   */
  def uninterruptibleMask[R, E, A](
    f: ZIO.InterruptibilityRestorer => ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    interruptionMasked(enable = false, f)

  /**
   * The moral equivalent of `if (!p) Some(exp) else None`
   *
   * {{{
   * val cacheHit = false
   * val loaded: UIO[Option[String]] = ZIO.unless(cacheHit)(ZIO.succeed("row 42"))
   * // succeeds with Some(row 42)
   * }}}
   */
  def unless[R, E, A](p: => Boolean)(zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    suspendSucceed(if (p) Exit.none else zio.asSome)

  /**
   * The moral equivalent of `if (!p) { expr; () }`
   */
  def unlessDiscard[R, E](p: => Boolean)(zio: => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    suspendSucceed(if (p) Exit.unit else zio.unit)

  /**
   * The moral equivalent of `if (!p) Some(expr) else None` when `p` has
   * side-effects
   */
  def unlessZIO[R, E](p: => ZIO[R, E, Boolean]): ZIO.UnlessZIO[R, E] =
    new ZIO.UnlessZIO(() => p)

  /**
   * The moral equivalent of `if (!p) { expr; () }` when `p` has side-effects
   */
  def unlessZIODiscard[R, E](p: => ZIO[R, E, Boolean]): ZIO.UnlessZIODiscard[R, E] =
    new ZIO.UnlessZIODiscard(() => p)

  /**
   * The inverse operation of `ZIO#sandbox`.
   *
   * Turns an effect that fails with a `Cause[E]` back into an effect whose
   * cause is that `Cause[E]`: a `Cause.Die` in the error channel makes the
   * effect die, and a `Cause.Fail(e)` makes it fail with `e`.
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
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (state, _) =>
      state.setFiberRefs(f(state.id, state.getFiberRefs()))

      Exit.unit
    }

  /**
   * Updates the runtime flags. This may have a performance impact. For a
   * higher-performance variant, see `ZIO#withRuntimeFlags`.
   */
  def updateRuntimeFlags(patch: RuntimeFlags.Patch)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
    if (patch == RuntimeFlags.Patch.empty) Exit.unit
    else ZIO.UpdateRuntimeFlags(trace, patch)

  /**
   * Updates a state in the environment with the specified function.
   */
  def updateState[S: EnvironmentTag](f: S => S)(implicit trace: Trace): ZIO[ZState[S], Nothing, Unit] =
    ZIO.serviceWithZIO(_.update(f))

  /**
   * Scopes all resources acquired by `resource` to the lifetime of `use`
   * without affecting the scope of any resources acquired by `use`.
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
      if (es.isEmpty) Exit.succeed(bf.fromSpecific(in)(bs))
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
      if (es.isEmpty) Exit.succeed(NonEmptyChunk.nonEmpty(Chunk.fromIterable(bs)))
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
      if (es.isEmpty) Exit.unit
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
      if (es.isEmpty) Exit.succeed(bf.fromSpecific(in)(bs))
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
      if (es.isEmpty) Exit.succeed(NonEmptyChunk.nonEmpty(Chunk.fromIterable(bs)))
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
      if (es.isEmpty) Exit.unit
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
   * The moral equivalent of `if (p) Some(exp) else None`
   *
   * {{{
   * val discount: UIO[Option[Int]] = ZIO.when(250 > 100)(ZIO.succeed(15))
   * // succeeds with Some(15)
   * val skipped: UIO[Option[Int]] = ZIO.when(40 > 100)(ZIO.succeed(15))
   * // succeeds with None, without running the effect
   * }}}
   */
  def when[R, E, A](p: => Boolean)(zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    suspendSucceed(if (p) zio.asSome else Exit.none)

  /**
   * The moral equivalent of `if (p) { expr; () }`
   */
  def whenDiscard[R, E](p: => Boolean)(zio: => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    suspendSucceed(if (p) zio.unit else Exit.unit)

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * value, otherwise does nothing.
   */
  def whenCase[R, E, A, B](a: => A)(pf: PartialFunction[A, ZIO[R, E, B]])(implicit
    trace: Trace
  ): ZIO[R, E, Option[B]] =
    suspendSucceed(pf.andThen(_.asSome).applyOrElse(a, (_: A) => Exit.none))

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * value, and discards the result, otherwise does nothing.
   */
  def whenCaseDiscard[R, E, A](a: => A)(pf: PartialFunction[A, ZIO[R, E, Any]])(implicit
    trace: Trace
  ): ZIO[R, E, Unit] =
    suspendSucceed(pf.andThen(_.unit).applyOrElse(a, (_: A) => Exit.unit))

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * effectful value, otherwise does nothing.
   */
  def whenCaseZIO[R, E, A, B](a: => ZIO[R, E, A])(pf: PartialFunction[A, ZIO[R, E, B]])(implicit
    trace: Trace
  ): ZIO[R, E, Option[B]] =
    ZIO.suspendSucceed(a.flatMap(whenCase(_)(pf)))

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * effectful value, and discards the value, otherwise does nothing.
   */
  def whenCaseZIODiscard[R, E, A](a: => ZIO[R, E, A])(pf: PartialFunction[A, ZIO[R, E, Any]])(implicit
    trace: Trace
  ): ZIO[R, E, Unit] =
    suspendSucceed(a.flatMap(whenCaseDiscard(_)(pf)))

  /**
   * The moral equivalent of `if (p) Some(exp) else None` when `p` has
   * side-effects
   */
  def whenZIO[R, E](p: => ZIO[R, E, Boolean]): ZIO.WhenZIO[R, E] =
    new ZIO.WhenZIO(() => p)

  /**
   * The moral equivalent of `if (p) exp: Unit` when `p` has side-effects
   */
  def whenZIODiscard[R, E](p: => ZIO[R, E, Boolean]): ZIO.WhenZIODiscard[R, E] =
    new ZIO.WhenZIODiscard(() => p)

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
    DefaultServices.currentServices.locallyWith(_.add(clock))(ZIO.suspendSucceed(zio))

  /**
   * Sets the implementation of the clock service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withClockScoped[A <: Clock](clock: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    DefaultServices.currentServices.locallyScopedWith(_.add(clock))

  /**
   * Executes the specified workflow with the specified configuration provider.
   */
  def withConfigProvider[R, E, A <: ConfigProvider, B](configProvider: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    DefaultServices.currentServices.locallyWith(_.add(configProvider))(ZIO.suspendSucceed(zio))

  /**
   * Sets the configuration provider to the specified value and restores it to
   * its original value when the scope is closed.
   */
  def withConfigProviderScoped[A <: ConfigProvider](
    configProvider: => A
  )(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    DefaultServices.currentServices.locallyScopedWith(_.add(configProvider))

  /**
   * Executes the specified workflow with the specified implementation of the
   * console service.
   */
  def withConsole[R, E, A <: Console, B](console: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    DefaultServices.currentServices.locallyWith(_.add(console))(ZIO.suspendSucceed(zio))

  /**
   * Sets the implementation of the console service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withConsoleScoped[A <: Console](console: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    DefaultServices.currentServices.locallyScopedWith(_.add(console))

  /**
   * Executes the specified workflow with the specified logger added.
   */
  def withLogger[R, E, A <: ZLogger[String, Any], B](logger: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    FiberRef.currentLoggers.locallyWith(_ + logger)(ZIO.suspendSucceed(zio))

  /**
   * Adds the specified logger and removes it when the scope is closed.
   */
  def withLoggerScoped[A <: ZLogger[String, Any]](
    logger: => A
  )(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    FiberRef.currentLoggers.locallyScopedWith(_ + logger)

  /**
   * Runs the specified effect with the specified maximum number of fibers for
   * parallel operators.
   */
  def withParallelism[R, E, A](n: => Int)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(FiberRef.parallelism.locally(Some(n))(zio))

  /**
   * Runs the specified effect with the specified maximum number of fibers for
   * parallel operators, but passes it a restore function that can be used to
   * restore the inherited parallelism from whatever region the effect is
   * composed into.
   */
  def withParallelismMask[R, E, A](n: => Int)(f: ZIO.ParallelismRestorer => ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R, E, A] =
    FiberRef.parallelism.getWith {
      case Some(n0) => FiberRef.parallelism.locally(Some(n))(f(ParallelismRestorer.MakeParallel(n0)))
      case _        => FiberRef.parallelism.locally(Some(n))(f(ParallelismRestorer.MakeParallelUnbounded))
    }

  /**
   * Runs the specified effect with an unbounded maximum number of fibers for
   * parallel operators.
   */
  def withParallelismUnbounded[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed(FiberRef.parallelism.locally(None)(zio))

  /**
   * Runs the specified effect with an unbounded maximum number of fibers for
   * parallel operators, but passes it a restore function that can be used to
   * restore the inherited parallelism from whatever region the effect is
   * composed into.
   */
  def withParallelismUnboundedMask[R, E, A](f: ZIO.ParallelismRestorer => ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R, E, A] =
    FiberRef.parallelism.getWith {
      case Some(n) => FiberRef.parallelism.locally(None)(f(ParallelismRestorer.MakeParallel(n)))
      case None    => FiberRef.parallelism.locally(None)(f(ParallelismRestorer.MakeParallelUnbounded))
    }

  /**
   * Executes the specified workflow with the specified implementation of the
   * random service.
   */
  def withRandom[R, E, A <: Random, B](random: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    DefaultServices.currentServices.locallyWith(_.add(random))(ZIO.suspendSucceed(zio))

  /**
   * Sets the implementation of the random service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withRandomScoped[A <: Random](random: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    DefaultServices.currentServices.locallyScopedWith(_.add(random))

  /**
   * Applies the specified runtime flags patch to the current fiber until the
   * scope closes, when the flags it changed are reverted. An empty patch does
   * nothing.
   */
  def withRuntimeFlagsScoped(update: RuntimeFlags.Patch)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    if (update == RuntimeFlags.Patch.empty) {
      ZIO.unit
    } else {
      ZIO.uninterruptible {
        ZIO.runtimeFlags.flatMap { runtimeFlags =>
          val updatedRuntimeFlags = RuntimeFlags.Patch.patch(update)(runtimeFlags)
          val revertRuntimeFlags  = RuntimeFlags.diff(updatedRuntimeFlags, runtimeFlags)
          ZIO.updateRuntimeFlags(update) *>
            ZIO.addFinalizer(ZIO.updateRuntimeFlags(revertRuntimeFlags)).unit
        }
      }
    }

  /**
   * Executes the specified workflow with the specified implementation of the
   * system service.
   */
  def withSystem[R, E, A <: System, B](system: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    DefaultServices.currentServices.locallyWith(_.add(system))(ZIO.suspendSucceed(zio))

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
  def yieldNow(implicit trace: Trace): UIO[Unit] = ZIO.YieldNow(trace, false)

  private[zio] def withFiberRuntime[R, E, A](
    onState: (Fiber.Runtime[E, A], Fiber.Status.Running) => ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    Stateful(trace, onState)

  private final val MaxIterationsPerYield = 512

  private val _IdentityFn: Any => Any = (a: Any) => a

  private val _FailFn: Any => Exit[Any, Nothing] = a => Exit.Failure(Cause.fail(a))

  private val _SuccessFn: Any => Exit[Nothing, Any] = a => Exit.Success(a)

  private val _SecondFn: (Any, Any) => Any = (_: Any, b: Any) => b

  private[zio] def secondFn[A]: (Any, A) => A = _SecondFn.asInstanceOf[(Any, A) => A]

  private[zio] def identityFn[A]: A => A = _IdentityFn.asInstanceOf[A => A]

  private[zio] def failFn[E]: E => Exit[E, Nothing] = _FailFn.asInstanceOf[E => Exit[E, Nothing]]

  private[zio] def successFn[A]: A => Exit[Nothing, A] = _SuccessFn.asInstanceOf[A => Exit[Nothing, A]]

  private[zio] def partitionMap[A, A1, A2](
    iterable: Iterable[A]
  )(f: A => Either[A1, A2]): (Iterable[A1], Iterable[A2]) =
    iterable.foldRight((List.empty[A1], List.empty[A2])) { case (a, (es, bs)) =>
      f(a).fold(
        e => (e :: es, bs),
        b => (es, b :: bs)
      )
    }

  private[zio] val unitFn: Any => Unit         = (_: Any) => ()
  private[zio] val unitZIOFn: Any => UIO[Unit] = (_: Any) => Exit.unit

  /**
   * Adds resource operators to an effect that produces an `AutoCloseable`:
   * `acquireReleaseWithAuto` and `withFinalizerAuto` close the value with its
   * `close` method.
   */
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
     * Like `withFinalizer`, adds a finalizer that calls the `close` method of
     * the `AutoCloseable` when the scope closes. Unlike
     * `ZIO.fromAutoCloseable`, it does not check for `null`: a `null` value
     * makes the finalizer die with a `NullPointerException`.
     */
    def withFinalizerAuto(implicit trace: Trace): ZIO[R with Scope, E, A] =
      ZIO.acquireRelease(io)(a => ZIO.succeed(a.close()))
  }

  /**
   * Adds the short-circuiting logical operators `&&` and `||` to an effect that
   * produces a `Boolean`.
   */
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
      self.flatMap(a => if (a) that else Exit.`false`)

    /**
     * Returns the logical conjunction of the `Boolean` value returned by this
     * effect and the `Boolean` value returned by the specified effect. This
     * operator has "short circuiting" behavior so if the value returned by this
     * effect is true the specified effect will not be evaluated.
     */
    final def ||[R1 <: R, E1 >: E](
      that: => ZIO[R1, E1, Boolean]
    )(implicit trace: Trace): ZIO[R1, E1, Boolean] =
      self.flatMap(a => if (a) Exit.`true` else that)
  }

  /**
   * Adds `refineToOrDie` to an effect whose error type is a `Throwable`.
   */
  implicit final class ZioRefineToOrDieOps[R, E <: Throwable, A](private val self: ZIO[R, E, A]) extends AnyVal {

    /**
     * Keeps some of the errors, and terminates the fiber with the rest.
     */
    def refineToOrDie[E1 <: E: ClassTag](implicit ev: CanFail[E], trace: Trace): ZIO[R, E1, A] =
      self.refineOrDie { case e: E1 => e }
  }

  /**
   * Returned by `ZIO#provideSomeLayer[R0]`; it fixes the remaining environment
   * type `R0` so that the layer's output type can be inferred.
   */
  final class ProvideSomeLayer[R0, -R, +E, +A](private val self: ZIO[R, E, A]) extends AnyVal {

    /**
     * Provides the output of `layer` to the effect, leaving `R0` (which the
     * layer may also take as input) in the environment of the returned effect.
     */
    def apply[E1 >: E, R1](
      layer: => ZLayer[R0, E1, R1]
    )(implicit
      ev: R0 with R1 <:< R,
      tagged: EnvironmentTag[R1],
      trace: Trace
    ): ZIO[R0, E1, A] =
      self.asInstanceOf[ZIO[R0 with R1, E, A]].provideLayer(ZLayer.environment[R0] <*> layer)
  }

  /**
   * Returned by `ZIO#updateService[M]`; it fixes the service type `M`.
   */
  final class UpdateService[-R, +E, +A, M](private val self: ZIO[R, E, A]) extends AnyVal {

    /**
     * Runs the effect with the service `M` in its environment replaced by
     * `f(service)`.
     */
    def apply[R1 <: R with M](
      f: M => M
    )(implicit tag: Tag[M], trace: Trace): ZIO[R1, E, A] =
      self.provideSomeEnvironment(_.update(f))
  }

  /**
   * Returned by `ZIO#updateServiceAt[Service]`; it fixes the service type.
   */
  final class UpdateServiceAt[-R, +E, +A, Service](private val self: ZIO[R, E, A]) extends AnyVal {

    /**
     * Runs the effect with the service stored under `key` in the `Map[Key,
     * Service]` of its environment replaced by `f(service)`. When the key is
     * absent, the effect dies with a `java.util.NoSuchElementException`.
     */
    def apply[R1 <: R with Map[Key, Service], Key](key: => Key)(
      f: Service => Service
    )(implicit tag: Tag[Map[Key, Service]], trace: Trace): ZIO[R1, E, A] =
      self.provideSomeEnvironment(_.updateAt(key)(f))
  }

  /**
   * Evidence that the error type `E` is a supertype of
   * `NoSuchElementException`, so a failed pattern guard can be reported as an
   * `E`. No operator of `ZIO` requires it.
   */
  @implicitNotFound(
    "Pattern guards are only supported when the error type is a supertype of NoSuchElementException. However, your effect has ${E} for the error type."
  )
  sealed abstract class CanFilter[+E] {

    /** Returns the exception as a value of the error type `E`. */
    def apply(t: NoSuchElementException): E
  }

  /** Provides the `CanFilter` instances. */
  object CanFilter {
    private val instance: CanFilter[Any] = new CanFilter[Any] { def apply(t: NoSuchElementException): Any = t }

    /**
     * The `CanFilter` instance for every error type that is a supertype of
     * `NoSuchElementException`.
     */
    implicit def canFilter[E >: NoSuchElementException]: CanFilter[E] = instance.asInstanceOf[CanFilter[E]]
  }

  /**
   * Passed by `ZIO.transplant` to its function: it holds the fiber scope of the
   * fiber that called `transplant`.
   */
  final class Grafter(private val scope: FiberScope) extends AnyVal { self =>

    /**
     * Runs `zio` so that the fibers it forks are children of the scope held by
     * this `Grafter` instead of the fiber that forks them.
     */
    def apply[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      FiberRef.forkScopeOverride.locally(Some(scope))(zio)

    /**
     * Similar to [[apply]] but delays the transfer of children spawned by the
     * effect until the effect has completed, either by success or failure.
     *
     * This is more performant than [[apply]] as:
     *   1. it doesn't require updating [[FiberRef.forkScopeOverride]] which has
     *      a "complex" join
     *   1. only fibers that are still active by the time the effect completes
     *      are transferred.
     *
     * @see
     *   [[applyOnExitWith]] for variant that allows providing a callback
     *   function
     */
    private[zio] def applyOnExit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      applyOnExitWith(zio)(ZIO.unitFn)

    /**
     * More powerful variant of [[applyOnExit]] that allows providing a callback
     * function that is guaranteed to be invoked ''after'' the children have
     * been transferred.
     */
    private[zio] def applyOnExitWith[R, E, A](
      zio: ZIO[R, E, A]
    )(
      ensuring: Exit[E, A] => Unit
    )(implicit trace: Trace): ZIO[R, E, A] =
      ZIO.uninterruptibleMask { restore =>
        restore(zio).exitWith { exit =>
          ZIO.withFiberRuntime[Any, E, A] { (fiber, _) =>
            fiber.transferChildren(self.scope)
            ensuring(exit)
            exit
          }
        }
      }
  }

  /** Companion of `Grafter`; its constructor is internal to ZIO. */
  object Grafter {

    private[zio] def apply(fiberState: Fiber.Runtime[?, ?])(implicit trace: Trace): Grafter = {
      val scopeOverride = fiberState.getFiberRefOrNull(FiberRef.forkScopeOverride)
      val scope =
        if ((scopeOverride eq null) || (scopeOverride eq None)) fiberState.scope
        else scopeOverride.get

      new Grafter(scope)
    }
  }

  /**
   * Returned by `ZIO.ifZIO`; it holds the condition effect.
   */
  final class IfZIO[R, E](private val b: () => ZIO[R, E, Boolean]) extends AnyVal {

    /**
     * Runs the condition, then `onTrue` when it is `true` and `onFalse`
     * otherwise.
     */
    def apply[R1 <: R, E1 >: E, A](onTrue: => ZIO[R1, E1, A], onFalse: => ZIO[R1, E1, A])(implicit
      trace: Trace
    ): ZIO[R1, E1, A] =
      ZIO.suspendSucceed(b().flatMap(b => if (b) onTrue else onFalse))
  }

  /**
   * Returned by `ZIO.unlessZIO`; it holds the condition effect.
   */
  final class UnlessZIO[R, E](private val b: () => ZIO[R, E, Boolean]) extends AnyVal {

    /**
     * Runs the condition, then succeeds with `None` when it is `true`, or runs
     * `zio` and wraps its value in `Some` when it is `false`.
     */
    def apply[R1 <: R, E1 >: E, A](zio: => ZIO[R1, E1, A])(implicit trace: Trace): ZIO[R1, E1, Option[A]] =
      ZIO.suspendSucceed(b()).flatMap(b => if (b) Exit.none else zio.asSome)
  }

  /**
   * Returned by `ZIO.unlessZIODiscard`; it holds the condition effect.
   */
  final class UnlessZIODiscard[R, E](private val b: () => ZIO[R, E, Boolean]) extends AnyVal {

    /**
     * Runs the condition, then runs `zio` and discards its value when the
     * condition is `false`; does nothing more when it is `true`.
     */
    def apply[R1 <: R, E1 >: E](zio: => ZIO[R1, E1, Any])(implicit trace: Trace): ZIO[R1, E1, Unit] =
      ZIO.suspendSucceed(b()).flatMap(b => if (b) Exit.unit else zio.unit)
  }

  /**
   * Returned by `ZIO.whenZIO`; it holds the condition effect.
   */
  final class WhenZIO[R, E](private val b: () => ZIO[R, E, Boolean]) extends AnyVal {

    /**
     * Runs the condition, then runs `zio` and wraps its value in `Some` when it
     * is `true`, or succeeds with `None` when it is `false`.
     */
    def apply[R1 <: R, E1 >: E, A](zio: => ZIO[R1, E1, A])(implicit trace: Trace): ZIO[R1, E1, Option[A]] =
      ZIO.suspendSucceed(b()).flatMap(b => if (b) zio.asSome else Exit.none)
  }

  /**
   * Returned by `ZIO.whenZIODiscard`; it holds the condition effect.
   */
  final class WhenZIODiscard[R, E](private val b: () => ZIO[R, E, Boolean]) extends AnyVal {

    /**
     * Runs the condition, then runs `zio` and discards its value when the
     * condition is `true`; does nothing more when it is `false`.
     */
    def apply[R1 <: R, E1 >: E](zio: => ZIO[R1, E1, Any])(implicit trace: Trace): ZIO[R1, E1, Unit] =
      ZIO.suspendSucceed(b()).flatMap(b => if (b) zio.unit else Exit.unit)
  }

  /**
   * Returned by `ZIO#timeoutTo(b)`; it holds the effect and the fallback value
   * `b`.
   */
  final class TimeoutTo[-R, +E, +A, +B](self: ZIO[R, E, A], b: () => B) {

    /**
     * Races the effect against a timer of `duration`. When the effect completes
     * first, the result is its value mapped with `f` (or its failure). When the
     * timer fires first, the effect is interrupted and the result is `b`,
     * returned once the interrupted effect has stopped and its finalizers have
     * run.
     */
    def apply[B1 >: B](f: A => B1)(duration: => Duration)(implicit
      trace: Trace
    ): ZIO[R, E, B1] =
      ZIO.clockWith(_.scheduler).flatMap { scheduler =>
        ZIO.withFiberRuntime[R, E, B1] { (parentFiber, parentStatus) =>
          import java.util.concurrent.atomic.AtomicBoolean

          // Indicates which side of the race completed first, the effect or
          // the timeout, ensuring that only one of them resumes the parent
          // fiber:
          val raceIndicator = new AtomicBoolean(false)

          val graft    = ZIO.Grafter(parentFiber)
          val childEff = graft.applyOnExit(self)

          val childFiber =
            ZIO.unsafe.makeChildFiber(trace, childEff, parentFiber, parentStatus.runtimeFlags, null)(Unsafe)
          val startChild = childFiber.startSuspended()(Unsafe)

          ZIO.asyncInterrupt[R, E, B1](
            { cb =>
              // Instead of racing the effect against a forked `sleep` fiber we
              // schedule the timeout directly on the clock's scheduler,
              // eliminating one fiber fork, one interruption and one join:
              val cancelTimeout = scheduler.schedule(
                () =>
                  if (raceIndicator.compareAndSet(false, true)) {
                    // The timeout elapsed before the effect completed.
                    // Interrupt the effect and await its interruption before
                    // producing the default value, so that this method does
                    // not return until the underlying effect is actually
                    // interrupted:
                    cb(childFiber.interruptAs(parentFiber.id) *> childFiber.inheritAll.as(b()))
                  },
                duration
              )(Unsafe)

              childFiber.addObserver { exit =>
                if (raceIndicator.compareAndSet(false, true)) {
                  // The effect completed before the timeout elapsed. Cancel
                  // the scheduled timeout and resume with the effect's result:
                  cancelTimeout()
                  cb(childFiber.inheritAll *> exit.mapExit(f))
                }
              }(Unsafe)

              startChild(childEff)

              Left(ZIO.succeed(cancelTimeout()))
            },
            childFiber.id
          )
        }
      }
  }

  /**
   * Returned by `ZIO.acquireReleaseWith(acquire)`; it holds the `acquire`
   * effect.
   */
  final class Acquire[-R, +E, +A](private val acquire: () => ZIO[R, E, A]) extends AnyVal {

    /**
     * Sets the `release` function and returns a `Release`, whose `apply` takes
     * the `use` function.
     */
    def apply[R1](release: A => URIO[R1, Any]): Release[R with R1, E, A] =
      new Release[R with R1, E, A](acquire, release)
  }

  /**
   * Returned by `Acquire#apply(release)`; it holds the `acquire` and `release`
   * effects of `ZIO.acquireReleaseWith`.
   */
  final class Release[-R, +E, +A](acquire: () => ZIO[R, E, A], release: A => URIO[R, Any]) {

    /**
     * Runs `acquire` uninterruptibly, then `use`, then `release`
     * uninterruptibly whether `use` succeeds, fails or is interrupted, and
     * returns the result of `use`.
     */
    def apply[R1 <: R, E1 >: E, B](use: A => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
      acquireReleaseExitWith(acquire())((a: A, _: Exit[E1, B]) => release(a))(use)
  }

  /**
   * Returned by `ZIO.acquireReleaseExitWith(acquire)`; it holds the `acquire`
   * effect.
   */
  final class AcquireExit[-R, +E, +A](private val acquire: () => ZIO[R, E, A]) extends AnyVal {

    /**
     * Sets the `release` function, which also receives the `Exit` of `use`, and
     * returns a `ReleaseExit`, whose `apply` takes the `use` function.
     */
    def apply[R1 <: R, E1 >: E, B](
      release: (A, Exit[E1, B]) => URIO[R1, Any]
    ): ReleaseExit[R1, E, E1, A, B] =
      new ReleaseExit(acquire, release)
  }

  /**
   * Returned by `AcquireExit#apply(release)`; it holds the `acquire` and
   * `release` effects of `ZIO.acquireReleaseExitWith`.
   */
  final class ReleaseExit[-R, +E, E1, +A, B](
    acquire: () => ZIO[R, E, A],
    release: (A, Exit[E1, B]) => URIO[R, Any]
  ) {

    /**
     * Runs `acquire` uninterruptibly, then `use`, then `release` with the
     * `Exit` of `use`, uninterruptibly. Returns the result of `use`; when
     * `release` dies, the defect is added to the cause.
     */
    def apply[R1 <: R, E2 >: E <: E1, B1 <: B](use: A => ZIO[R1, E2, B1])(implicit
      trace: Trace
    ): ZIO[R1, E2, B1] =
      ZIO.uninterruptibleMask[R1, E2, B1](restore =>
        acquire().flatMap { a =>
          attemptOrDieZIO(restore(use(a))).exitWith { e =>
            attemptOrDieZIO(release(a, e)).foldCauseZIO(
              cause2 => Exit.failCause(e.foldExit(_ ++ cause2, _ => cause2)),
              _ => e
            )
          }
        }
      )
  }

  /**
   * Returned by `ZIO.environmentWith[R]`; it fixes the environment type `R`.
   */
  final class EnvironmentWithPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /** Returns an effect that succeeds with `f` applied to the environment. */
    def apply[A](f: ZEnvironment[R] => A)(implicit trace: Trace): URIO[R, A] =
      ZIO.environmentWithZIO(env => Exit.succeed(f(env)))
  }

  /**
   * Returned by `ZIO.environmentWithZIO[R]`; it fixes the environment type `R`.
   */
  final class EnvironmentWithZIOPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Returns an effect that runs the effect `f` builds from the environment.
     */
    def apply[R1 <: R, E, A](f: ZEnvironment[R] => ZIO[R1, E, A])(implicit trace: Trace): ZIO[R with R1, E, A] =
      FiberRef.currentEnvironment.getWith(env => f(env.asInstanceOf[ZEnvironment[R]]))
  }

  /**
   * Returned by `ZIO.scoped[R]`; it fixes the environment type `R` that the
   * scoped effect needs besides `Scope`.
   */
  final class ScopedPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Runs `zio` in a new scope and closes the scope when `zio` completes,
     * running its finalizers.
     */
    def apply[E, A](zio: => ZIO[Scope with R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      ZIO.suspendSucceed(Scope.unsafe.make(Unsafe).use[R](zio))
  }

  /**
   * Returned by `ZIO.using[R]`; it fixes the environment type `R` of the
   * resource.
   */
  final class UsingPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Acquires `resource` in a new scope, runs `use` with it, and closes the
     * scope when `use` completes.
     */
    def apply[R1, E, A, B](
      resource: ZIO[R with Scope, E, A]
    )(use: A => ZIO[R1, E, B])(implicit trace: Trace): ZIO[R with R1, E, B] =
      ZIO.scopedWith(_.extend[R](resource).flatMap(use))
  }

  /**
   * Returned by `ZIO.serviceAt[Service]`; it fixes the service type.
   */
  final class ServiceAtPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Returns an effect that looks up `key` in the `Map[Key, Service]` of the
     * environment, succeeding with `Some(service)`, or `None` when the key is
     * absent.
     */
    def apply[Key](
      key: => Key
    )(implicit tag: EnvironmentTag[Map[Key, Service]], trace: Trace): URIO[Map[Key, Service], Option[Service]] =
      ZIO.environmentWith(_.getAt(key))
  }

  /**
   * Returned by `ZIO.serviceWith[Service]`; it fixes the service type.
   */
  final class ServiceWithPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /** Returns an effect that succeeds with `f` applied to the service. */
    def apply[A](
      f: Service => A
    )(implicit tagged: Tag[Service], trace: Trace): ZIO[Service, Nothing, A] =
      ZIO.serviceWithZIO(service => Exit.succeed(f(service)))
  }

  /**
   * Returned by `ZIO.serviceWithZIO[Service]`; it fixes the service type.
   */
  final class ServiceWithZIOPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /** Returns an effect that runs the effect `f` builds from the service. */
    def apply[R <: Service, E, A](
      f: Service => ZIO[R, E, A]
    )(implicit
      tagged: Tag[Service],
      trace: Trace
    ): ZIO[R with Service, E, A] = {
      val tag = tagged.tag
      FiberRef.currentEnvironment.getWith(environment => f(environment.unsafe.get(tag)(Unsafe)))
    }
  }

  /**
   * Returned by `ZIO.stateful[R]`; it fixes the environment type `R` that the
   * workflow needs besides its state.
   */
  final class StatefulPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Runs `zio` with a `ZState[S]` service that starts with the value `s`.
     */
    def apply[S, E, A](
      s: S
    )(zio: => ZIO[ZState[S] with R, E, A])(implicit tag: EnvironmentTag[S], trace: Trace): ZIO[R, E, A] =
      zio.provideSomeLayer[R](ZState.initial(s))
  }

  /**
   * Returned by `ZIO.statefulPatch[R]`; it fixes the environment type `R` that
   * the workflow needs besides its state.
   */
  final class StatefulPatchPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Runs `zio` with a `ZState[State]` service that starts with `state` and
     * combines the updates of different fibers with `differ`.
     */
    def apply[State, Patch, E, A](
      state: State,
      differ: Differ[State, Patch]
    )(zio: => ZIO[ZState[State] with R, E, A])(implicit
      tag: EnvironmentTag[State],
      trace: Trace
    ): ZIO[R, E, A] =
      zio.provideSomeLayer[R](ZState.initialPatch(state, differ))
  }

  /**
   * Returned by `ZIO.getStateWith[S]`; it fixes the state type `S`.
   */
  final class GetStateWithPartiallyApplied[S](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Returns an effect that succeeds with `f` applied to the current state.
     */
    def apply[A](f: S => A)(implicit tag: EnvironmentTag[S], trace: Trace): ZIO[ZState[S], Nothing, A] =
      ZIO.serviceWithZIO(_.get.map(f))
  }

  /**
   * Returned by `ZIO.logSpan(label)`; it holds the label of the span.
   */
  final class LogSpan(val label: () => String) extends AnyVal {
    import zio.{LogSpan => ZioLogSpan}

    /**
     * Runs `zio` inside a new log span with the label, started when `zio`
     * starts. Each log entry written in `zio` reports the label and the time
     * elapsed since then.
     */
    def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      FiberRef.currentLogSpan.locallyWith { stack =>
        val instant = java.lang.System.currentTimeMillis()
        val logSpan = ZioLogSpan(label(), instant)

        logSpan :: stack
      }(zio)
  }

  /**
   * Returned by `ZIO.logAnnotate`; it holds the log annotations.
   */
  final class LogAnnotate(val annotations: () => Set[LogAnnotation]) { self =>

    /**
     * Runs `zio` with the annotations added to the current ones. An annotation
     * replaces a current one with the same key. Each log entry written in `zio`
     * carries them.
     */
    def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      FiberRef.currentLogAnnotations.locallyWith { stack =>
        var result = stack
        val it     = annotations().iterator
        while (it.hasNext) {
          val ann = it.next()
          result = result.updated(ann.key, ann.value)
        }
        result
      }(zio)
  }

  /**
   * Returned by `ZIO.tagged`; it holds the metric tags.
   */
  final class Tagged(val tags: () => Set[MetricLabel]) { self =>

    /**
     * Runs `zio` with the tags added to the current metric tags; the metrics
     * updated in `zio` carry them.
     */
    def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      FiberRef.currentTags.locallyWith { stack =>
        if (stack.isEmpty) tags()
        else stack ++ tags()
      }(zio)
  }

  @inline
  private def succeedLeft[E, A]: E => UIO[Either[E, A]] =
    _succeedLeft.asInstanceOf[E => UIO[Either[E, A]]]

  private val _succeedLeft: Any => IO[Any, Either[Any, Any]] =
    e2 => Exit.succeed[Either[Any, Any]](Left(e2))

  @inline
  private def succeedRight[E, A]: A => UIO[Either[E, A]] =
    _succeedRight.asInstanceOf[A => UIO[Either[E, A]]]

  private val _succeedRight: Any => IO[Any, Either[Any, Any]] =
    a => Exit.succeed[Either[Any, Any]](Right(a))

  /**
   * A `ZIOConstructor[Environment, Error, In]` knows how to construct a `ZIO`
   * value from an input of type `In`; `ZIO.from` uses it. This allows the type
   * of the `ZIO` value constructed to depend on `In`. The constructed `ZIO`
   * value is guaranteed not to require any services not included in
   * `Environment` or to be able to fail in any ways not described by `Error`.
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

  /**
   * The `ZIOConstructor` instances used by `ZIO.from`, one per input shape:
   * `Either`, `Option`, `Try`, `Fiber`, an effect producing a `Fiber`, a Scala
   * `Future` or `Promise`, a function from `ExecutionContext` to `Future`, and,
   * at lower priority, any other value.
   */
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
     * Constructs a `ZIO[Any, E, A]` from a `Left[Cause[E], A]`.
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
     * Constructs a `ZIO[Any, E, A]` from a `Right[Cause[E], A]`.
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
     * Constructs a `ZIO[Any, E, A]` from a `Fiber[E, A]`.
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
     * Constructs a `ZIO[Any, E, A]` from a `Fiber.Runtime[E, A]`.
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
     * Constructs a `ZIO[Any, E, A]` from a `Fiber.Synthetic[E, A]`.
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
     * Constructs a `ZIO[R, E, A]` from a `ZIO[R, E, Fiber.Runtime[E, A]]`.
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
     * Constructs a `ZIO[R, E, A]` from a `ZIO[R, E, Fiber.Synthetic[E, A]]`.
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
          ZIO.suspend(ZIO.fromFutureNow(input)(trace, Unsafe))
      }

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from a function `ExecutionContext
     * \=> Future[A]`.
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

  /**
   * The `ZIOConstructor` instances for `Either`, `Left` and `Right`, at a lower
   * priority than the ones in the `ZIOConstructor` companion.
   */
  sealed trait ZIOConstructorLowPriority1 extends ZIOConstructorLowPriority2 {

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
     * Constructs a `ZIO[Any, E, A]` from a `Left[E, A]`.
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
     * Constructs a `ZIO[Any, E, A]` from a `Right[E, A]`.
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

  /**
   * The `ZIOConstructor` instance for any other value, which `ZIO.from` wraps
   * with `ZIO.attempt`.
   */
  sealed trait ZIOConstructorLowPriority2 extends ZIOConstructorLowPriority3 {

    /**
     * Constructs a `ZIO[Any, Throwable, A]` from an `A`, with `ZIO.attempt`: a
     * non-fatal exception thrown while computing the value becomes the failure.
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

  /**
   * The lowest-priority `ZIOConstructor` instance and the `WithOut` type alias.
   */
  trait ZIOConstructorLowPriority3 {

    /**
     * The type of the `ZIOConstructor` with the type of the `ZIO` value.
     */
    type WithOut[Environment, Error, In, OutEnvironment0, OutError0, OutSuccess0] =
      ZIOConstructor[Environment, Error, In] {
        type OutEnvironment = OutEnvironment0; type OutError = OutError0; type OutSuccess = OutSuccess0
      }

    /**
     * Constructs a `ZIO[Any, Nothing, A]` from an `A`, with `ZIO.succeed`.
     * `AttemptConstructor` has a higher priority, so `ZIO.from(value)` picks it
     * instead and returns a `ZIO[Any, Throwable, A]`.
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

  private[zio] type Erased = ZIO[Any, Any, Any]

  private[zio] final case class FlatMap[R, E, A1, A2](
    trace: Trace,
    first: ZIO[R, E, A1],
    successK: A1 => ZIO[R, E, A2]
  ) extends Continuation
      with ZIO[R, E, A2]

  private[zio] final case class Mapped[R, E, A1, A2](
    trace: Trace,
    first: ZIO[R, E, A1],
    successK: A1 => A2
  ) extends Continuation
      with ZIO[R, E, A2]

  private[zio] sealed abstract class Continuation {
    def trace: Trace
  }

  /** Runtime internal: builds the continuation frames of the fiber runtime. */
  object Continuation {

    /** Runtime internal: wraps `f` as a continuation frame. */
    def apply[R, E, A, B](f: A => ZIO[R, E, B])(implicit trace: Trace): Continuation =
      FlatMap[R, E, A, B](trace, null, f)
  }
  private[zio] case class FoldZIO[R, E1, E2, A1, A2](
    trace: Trace,
    first: ZIO[R, E1, A1],
    successK: A1 => ZIO[R, E2, A2],
    failureK: Cause[E1] => ZIO[R, E2, A2]
  ) extends Continuation
      with ZIO[R, E2, A2]
  private[zio] final case class Sync[A](trace: Trace, eval: () => A) extends ZIO[Any, Nothing, A]
  private[zio] final case class Async[R, E, A](
    trace: Trace,
    registerCallback: (ZIO[R, E, A] => Unit) => Either[URIO[R, Any], ZIO[R, E, A]],
    blockingOn: () => FiberId
  ) extends ZIO[R, E, A]

  private[zio] final case class UpdateRuntimeFlags(trace: Trace, update: RuntimeFlags.Patch)
      extends Continuation
      with ZIO[Any, Nothing, Unit]

  private[zio] sealed trait UpdateRuntimeFlagsWithin[R, E, A] extends ZIO[R, E, A] {

    /** The patch applied to the runtime flags while the inner effect runs. */
    def update: RuntimeFlags.Patch

    /** Returns the inner effect, given the flags from before the patch. */
    def scope(oldRuntimeFlags: RuntimeFlags): ZIO[R, E, A]
  }
  private[zio] object UpdateRuntimeFlagsWithin extends UpdateRuntimeFlagsWithinPlatformSpecific {
    def apply[R, E, A](trace: Trace, update: RuntimeFlags.Patch, f: IntFunction[ZIO[R, E, A]]): DynamicNoBox[R, E, A] =
      DynamicNoBox(trace, update, f)

    @deprecated("Kept for bin-compat only", "2.1.7")
    final case class Interruptible[R, E, A](trace: Trace, effect: ZIO[R, E, A])
        extends UpdateRuntimeFlagsWithin[R, E, A] {
      def update: RuntimeFlags.Patch = RuntimeFlags.enableInterruption

      def scope(oldRuntimeFlags: RuntimeFlags): ZIO[R, E, A] = effect
    }
    @deprecated("Kept for bin-compat only", "2.1.7")
    final case class Uninterruptible[R, E, A](trace: Trace, effect: ZIO[R, E, A])
        extends UpdateRuntimeFlagsWithin[R, E, A] {
      def update: RuntimeFlags.Patch = RuntimeFlags.disableInterruption

      def scope(oldRuntimeFlags: RuntimeFlags): ZIO[R, E, A] = effect
    }
    @deprecated("Kept for bin-compat only", "2.1.7")
    final case class Dynamic[R, E, A](trace: Trace, update: RuntimeFlags.Patch, f: RuntimeFlags => ZIO[R, E, A])
        extends UpdateRuntimeFlagsWithin[R, E, A] {
      def scope(oldRuntimeFlags: RuntimeFlags): ZIO[R, E, A] = f(oldRuntimeFlags)
    }
    final case class DynamicNoBox[R, E, A](trace: Trace, update: RuntimeFlags.Patch, f: IntFunction[ZIO[R, E, A]])
        extends UpdateRuntimeFlagsWithin[R, E, A] {
      def scope(oldRuntimeFlags: RuntimeFlags): ZIO[R, E, A] = f(oldRuntimeFlags)
    }
  }
  @deprecated("Kept for binary compatibility only", since = "2.1.15")
  private[zio] final case class GenerateStackTrace(trace: Trace) extends ZIO[Any, Nothing, StackTrace]
  private[zio] final case class Stateful[R, E, A](
    trace: Trace,
    onState: (Fiber.Runtime[E, A], Fiber.Status.Running) => ZIO[R, E, A]
  ) extends ZIO[R, E, A]
  private[zio] final case class WhileLoop[R, E, A](
    trace: Trace,
    check: () => Boolean,
    body: () => ZIO[R, E, A],
    process: A => Any
  ) extends ZIO[R, E, Unit] { self =>
    val k: ZIO.Continuation = ZIO.Continuation { (element: A) => process(element); self }(trace)
  }
  private[zio] final case class YieldNow(trace: Trace, forceAsync: Boolean) extends ZIO[Any, Nothing, Unit]

  /**
   * Passed by `ZIO.uninterruptibleMask` and `ZIO.interruptibleMask` to their
   * function: `apply` runs an inner effect with the interruptibility of the
   * region that encloses the masked one.
   */
  sealed trait InterruptibilityRestorer {

    /**
     * Runs `effect` interruptible or uninterruptible, as the enclosing region
     * was.
     */
    def apply[R, E, A](effect: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

    /** Whether the enclosing region is interruptible. */
    def isParentRegionInterruptible: Boolean

    /** Whether the enclosing region is uninterruptible. */
    final def isParentRegionUninterruptible: Boolean = !isParentRegionInterruptible

    /** The interrupt status of the enclosing region. */
    final def parentInterruptStatus: InterruptStatus = InterruptStatus.fromBoolean(isParentRegionInterruptible)
  }

  /** Constructors and the two instances of `InterruptibilityRestorer`. */
  object InterruptibilityRestorer {

    /**
     * Returns `MakeInterruptible` for an interruptible status and
     * `MakeUninterruptible` otherwise.
     */
    def apply(status: InterruptStatus): InterruptibilityRestorer =
      if (status.isInterruptible) MakeInterruptible
      else MakeUninterruptible

    /**
     * Returns an effect that succeeds with the restorer for the interrupt
     * status of the current fiber.
     */
    def make(implicit trace: Trace): ZIO[Any, Nothing, InterruptibilityRestorer] =
      ZIO.checkInterruptible(status => Exit.succeed(InterruptibilityRestorer(status)))

    /** The restorer that runs the inner effect interruptibly. */
    case object MakeInterruptible extends InterruptibilityRestorer {

      /** Runs `effect` interruptibly. */
      def apply[R, E, A](effect: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.UpdateRuntimeFlagsWithin(trace, RuntimeFlags.enableInterruption, _ => effect)

      /** Returns `true`. */
      def isParentRegionInterruptible: Boolean = true
    }

    /** The restorer that runs the inner effect uninterruptibly. */
    case object MakeUninterruptible extends InterruptibilityRestorer {

      /** Runs `effect` uninterruptibly. */
      def apply[R, E, A](effect: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.UpdateRuntimeFlagsWithin(trace, RuntimeFlags.disableInterruption, _ => effect)

      /** Returns `false`. */
      def isParentRegionInterruptible: Boolean = false
    }
  }

  /**
   * Passed by `ZIO.withParallelismMask` and `ZIO.withParallelismUnboundedMask`
   * to their function: `apply` runs an inner effect with the parallelism of the
   * enclosing region.
   */
  sealed trait ParallelismRestorer {

    /** Runs `zio` with the parallelism of the enclosing region. */
    def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
  }

  /** The two instances of `ParallelismRestorer`. */
  object ParallelismRestorer {

    /** The restorer for an enclosing region with a parallelism of `n`. */
    final case class MakeParallel(n: Int) extends ParallelismRestorer {

      /** Runs `zio` with `withParallelism(n)`. */
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        zio.withParallelism(n)
    }

    /** The restorer for an enclosing region with unbounded parallelism. */
    case object MakeParallelUnbounded extends ParallelismRestorer {

      /** Runs `zio` with `withParallelismUnbounded`. */
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        zio.withParallelismUnbounded
    }
  }

  /**
   * Passed by `ZIO.parallelFinalizersMask` to its function: `apply` runs an
   * inner effect with the finalizer strategy (sequential or parallel) of the
   * enclosing scope.
   */
  sealed trait FinalizersRestorer {

    /** Runs `zio` with the finalizer strategy of the enclosing scope. */
    def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
  }

  /** Constructors and instances of `FinalizersRestorer`. */
  object FinalizersRestorer {

    /** Returns the restorer for the specified execution strategy. */
    def apply(executionStrategy: ExecutionStrategy): FinalizersRestorer =
      executionStrategy match {
        case ExecutionStrategy.Sequential =>
          FinalizersRestorer.MakeSequential
        case ExecutionStrategy.Parallel =>
          FinalizersRestorer.MakeParallel
        case ExecutionStrategy.ParallelN(n) =>
          FinalizersRestorer.MakeParallelN(n)
      }

    /** The restorer that runs the inner effect unchanged. */
    case object Identity extends FinalizersRestorer {

      /** Returns `zio` unchanged. */
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        zio
    }

    /** The restorer that runs finalizers in parallel. */
    case object MakeParallel extends FinalizersRestorer {

      /** Runs `zio` with `parallelFinalizers`. */
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        zio.parallelFinalizers
    }

    /** The restorer that runs finalizers in parallel, at most `n` at once. */
    final case class MakeParallelN(n: Int) extends FinalizersRestorer {

      /**
       * Runs `zio` in a child scope whose finalizers run in parallel, at most
       * `n` at once. When the current scope already has a bounded parallel
       * strategy, with any bound, `zio` runs in it unchanged; without a `Scope`
       * in the environment, `zio` runs unchanged.
       */
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.environmentWithZIO[R] { environment =>
          environment.getDynamic[Scope] match {
            case None => zio
            case Some(scope) =>
              scope.executionStrategy match {
                case ExecutionStrategy.ParallelN(n) => zio
                case _                              => scope.forkWith(ExecutionStrategy.ParallelN(n)).flatMap(_.extend[R](zio))
              }
          }
        }
    }

    /** The restorer that runs finalizers sequentially. */
    case object MakeSequential extends FinalizersRestorer {

      /** Runs `zio` with `sequentialFinalizers`. */
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        zio.sequentialFinalizers
    }
  }

  private[zio] val someFatal   = Some(LogLevel.Fatal)
  private[zio] val someError   = Some(LogLevel.Error)
  private[zio] val someWarning = Some(LogLevel.Warning)
  private[zio] val someInfo    = Some(LogLevel.Info)
  private[zio] val someDebug   = Some(LogLevel.Debug)
  private[zio] val someTrace   = Some(LogLevel.Trace)

  @deprecated("use succeed", "2.0.9")
  private[zio] def succeedNow[A](a: A): UIO[A] =
    succeed(a)(Trace.empty)

  private def collectAllParUnboundedDiscard[R, E, A](as: Iterable[ZIO[R, E, A]], size: Int)(implicit
    trace: Trace
  ): ZIO[R, E, Unit] =
    foreachParUnboundedDiscard(as, size)(ZIO.identityFn)

  private def foreachPar[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    parallelism: Int
  )(
    as: Collection[A],
    size: Int
  )(
    fn: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    if (parallelism <= 1)
      foreach(as)(fn)
    else if (parallelism >= size)
      foreachParUnbounded(as, size)(fn)
    else
      ZIO.suspendSucceed {
        val array = Array.ofDim[AnyRef](size)
        val zioFunction: ((A, Int)) => ZIO[R, E, Any] = { case (a, i) =>
          fn(a).flatMap { b => array(i) = b.asInstanceOf[AnyRef]; Exit.unit }
        }
        foreachParDiscard(parallelism)(as.zipWithIndex, size)(zioFunction)
          .as(bf.fromSpecific(as)(array.asInstanceOf[Array[B]]))
      }

  private def foreachParDiscard[R, E, A](
    parallelism: Int
  )(as: Iterable[A], size: Int)(f: A => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    if (parallelism <= 1)
      foreachDiscard(as)(f)
    else if (parallelism >= size)
      foreachParUnboundedDiscard(as, size)(f)
    else
      ZIO.suspendSucceed {
        import scala.jdk.CollectionConverters._

        val queue = new ConcurrentLinkedQueue[A](as.asJavaCollection)

        lazy val worker: ZIO[R, E, Unit] =
          ZIO.suspendSucceed(queue.poll() match {
            case null => Exit.unit
            case a    => f(a) *> worker
          })

        val workers = ZIO.replicate(parallelism)(worker)
        ZIO.collectAllParUnboundedDiscard(workers, parallelism)
      }

  private def foreachParUnbounded[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[A],
    size: Int
  )(
    f: A => ZIO[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: Trace): ZIO[R, E, Collection[B]] =
    ZIO.suspendSucceed {
      val array = Array.ofDim[AnyRef](size)
      val zioFunction: ((A, Int)) => ZIO[R, E, Any] = { case (a, i) =>
        f(a).flatMap(b => ZIO.succeed(array(i) = b.asInstanceOf[AnyRef]))
      }
      foreachParUnboundedDiscard(as.zipWithIndex, size)(zioFunction) *>
        ZIO.succeed(bf.fromSpecific(as)(array.asInstanceOf[Array[B]]))
    }

  private def foreachParUnboundedDiscard[R, E, A](
    as: Iterable[A],
    size: Int
  )(f: A => ZIO[R, E, Any])(implicit trace: Trace): ZIO[R, E, Unit] =
    size match {
      case 0 => Exit.unit
      case 1 => f(as.head).unit
      case size =>
        ZIO.uninterruptibleMask { restore =>
          val promise = Promise.unsafe.make[Unit, Unit](FiberId.None)(Unsafe)
          val ref     = new java.util.concurrent.atomic.AtomicInteger(size)
          ZIO.transplant { graft =>
            ZIO.foreach(as) { a =>
              graft
                .applyOnExitWith(restore(f(a))) {
                  case _: Exit.Success[?] => if (ref.decrementAndGet() == 0) promise.unsafe.done(Exit.unit)(Unsafe)
                  case _                  => promise.unsafe.done(Exit.failUnit)(Unsafe)
                }
                .forkDaemon
            }
          }.flatMap { fibers =>
            restore(promise.await).foldCauseZIO(
              cause =>
                ZIO
                  .foreachParUnbounded(fibers, size)(_.interrupt)
                  .flatMap(Exit.collectAllPar(_) match {
                    case Some(Exit.Failure(causes)) => Exit.failCause(cause.stripFailures && causes)
                    case _                          => Exit.failCause(cause.stripFailures)
                  }),
              _ => {
                val it = fibers.iterator
                ZIO.whileLoop(it.hasNext)(it.next().inheritAll)(ZIO.unitFn)
              }
            )
          }
        }
    }
}

/**
 * An `Exit[E, A]` describes the result of executing an `IO` value. The result
 * is either succeeded with a value `A`, or failed with a `Cause[E]`.
 *
 * It has two cases: `Exit.Success` holds the value, and `Exit.Failure` holds
 * the `Cause`, which records typed failures, defects and interruptions.
 * `ZIO#exit` turns the outcome of an effect into an `Exit`, and `Fiber#await`
 * succeeds with the `Exit` of a fiber.
 *
 * An `Exit` is also a `ZIO[Any, E, A]` that completes at once with its value or
 * cause, so it can be used wherever an effect is expected.
 *
 * {{{
 * val exit: Exit[String, Int] = Exit.succeed(42)
 *
 * val description: String = exit match {
 *   case Exit.Success(value) => s"succeeded with \$value"
 *   case Exit.Failure(cause) => s"failed with \$cause"
 * }
 * // "succeeded with 42"
 *
 * val captured: UIO[Exit[String, Int]] = ZIO.fail("timeout").exit
 * // succeeds with an Exit.Failure whose cause holds the failure "timeout"
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/core/exit/ Exit]]
 */
sealed trait Exit[+E, +A] extends ZIO[Any, E, A] { self =>
  import Exit._

  /**
   * Returns `that` when this exit succeeds. When this exit fails, returns its
   * failure, combined in parallel (`&&`) with the cause of `that` when `that`
   * also fails.
   */
  final def &>[E1 >: E, B](that: Exit[E1, B]): Exit[E1, B] = zipRightWith(self, that)(_ && _)

  /**
   * Returns `that` when this exit succeeds. When this exit fails, returns its
   * failure, combined sequentially (`++`) with the cause of `that` when `that`
   * also fails.
   */
  final def *>[E1 >: E, B](that: Exit[E1, B]): Exit[E1, B] = zipRightWith(self, that)(_ ++ _)

  /**
   * Returns this exit's value when both exits succeed. Otherwise returns the
   * failure of whichever exit failed; when both fail, their causes are combined
   * in parallel (`&&`), this exit's cause first.
   */
  final def <&[E1 >: E, B](that: Exit[E1, B]): Exit[E1, A] = zipRightWith(that, self)((l, r) => r && l)

  /**
   * Zips the values of both exits into a tuple when both succeed. Otherwise
   * returns the failure of whichever exit failed; when both fail, their causes
   * are combined in parallel (`&&`).
   */
  final def <&>[E1 >: E, B](that: Exit[E1, B])(implicit zippable: Zippable[A, B]): Exit[E1, zippable.Out] =
    zipWith(that)(zippable.zip(_, _), _ && _)

  /**
   * Returns this exit's value when both exits succeed. Otherwise returns the
   * failure of whichever exit failed; when both fail, their causes are combined
   * sequentially (`++`), this exit's cause first.
   */
  final def <*[E1 >: E, B](that: Exit[E1, B]): Exit[E1, A] = zipRightWith(that, self)((l, r) => r ++ l)

  /**
   * Zips the values of both exits into a tuple when both succeed. Otherwise
   * returns the failure of whichever exit failed; when both fail, their causes
   * are combined sequentially (`++`).
   */
  final def <*>[E1 >: E, B](that: Exit[E1, B])(implicit zippable: Zippable[A, B]): Exit[E1, zippable.Out] =
    zipWith(that)(zippable.zip(_, _), _ ++ _)

  /**
   * Replaces the success value with the one provided.
   */
  final def asExit[B](b: B): Exit[E, B] =
    self match {
      case _: Success[?] => Exit.succeed(b)
      case e             => e.asInstanceOf[Exit[E, Nothing]]
    }

  /**
   * Returns an option of the cause of failure: `Some(cause)` for an
   * `Exit.Failure`, `None` for an `Exit.Success`.
   */
  final def causeOption: Option[Cause[E]] =
    self match {
      case f: Failure[E] => Some(f.cause)
      case _             => None
    }

  private[zio] final def causeOrNull: Cause[E] =
    self match {
      case f: Failure[E] => f.cause
      case _             => null.asInstanceOf[Cause[E]]
    }

  /**
   * Returns `true` when this exit is a success whose value satisfies `p`, and
   * `false` for a failure.
   */
  final def exists(p: A => Boolean): Boolean =
    foldExit(_ => false, p)

  override final def flatMap[R1, E1 >: E, B](k: A => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
    self match {
      case s: Success[A] => k(s.value)
      case e             => e.asInstanceOf[Exit[E, Nothing]]
    }

  /**
   * Flat maps over the value type.
   */
  final def flatMapExit[E1 >: E, A1](f: A => Exit[E1, A1]): Exit[E1, A1] =
    self match {
      case s: Success[A] => f(s.value)
      case e             => e.asInstanceOf[Exit[E, Nothing]]
    }

  /**
   * Flat maps over the value type.
   */
  final def flatMapExitZIO[E1 >: E, R, E2, A1](f: A => ZIO[R, E2, Exit[E1, A1]]): ZIO[R, E2, Exit[E1, A1]] =
    self match {
      case s: Success[A] => f(s.value)
      case e             => Exit.succeed(e.asInstanceOf[Exit[E, Nothing]])
    }

  /**
   * Flattens an Exit of an Exit into a single Exit value.
   */
  final def flattenExit[E1 >: E, B](implicit ev: A <:< Exit[E1, B]): Exit[E1, B] =
    Exit.flatten(self.mapExit(ev))

  /**
   * A more powerful version of `foldZIO` that allows recovering from any kind
   * of failure except external interruption.
   */
  override final def foldCauseZIO[R, E2, B](
    failure: Cause[E] => ZIO[R, E2, B],
    success: A => ZIO[R, E2, B]
  )(implicit trace: Trace): ZIO[R, E2, B] =
    foldExitZIO(failure, success)

  /**
   * Folds over the value or cause: returns `completed` applied to the value of
   * a success, or `failed` applied to the cause of a failure.
   *
   * {{{
   * val summary: String =
   *   Exit.fail("disk full").foldExit(
   *     cause => s"failed: \${cause.failureOption.getOrElse("?")}",
   *     (bytes: Int) => s"wrote \$bytes bytes"
   *   )
   * // "failed: disk full"
   * }}}
   */
  final def foldExit[Z](failed: Cause[E] => Z, completed: A => Z): Z =
    self match {
      case s: Success[A] => completed(s.value)
      case f: Failure[E] => failed(f.cause)
    }

  /**
   * Returns the effect that `completed` builds from the value of a success, or
   * the effect that `failed` builds from the cause of a failure.
   */
  final def foldExitZIO[R, E1, B](failed: Cause[E] => ZIO[R, E1, B], completed: A => ZIO[R, E1, B])(implicit
    trace: Trace
  ): ZIO[R, E1, B] =
    self match {
      case s: Success[A] => completed(s.value)
      case f: Failure[E] => failed(f.cause)
    }

  /**
   * Returns an effect that runs `f` on the value of a success and succeeds with
   * the `Exit` of that effect. For a failure, `f` does not run and the effect
   * succeeds with this failure. The returned effect does not fail.
   */
  final def foreach[R, E1 >: E, B](f: A => ZIO[R, E1, B])(implicit trace: Trace): ZIO[R, Nothing, Exit[E1, B]] =
    foldExit(c => Exit.succeed(failCause(c)), a => f(a).exit)

  /**
   * Retrieves the `A` if succeeded, or else returns the result of applying
   * `orElse` to the cause of the failure.
   */
  final def getOrElse[A1 >: A](orElse: Cause[E] => A1): A1 = self match {
    case s: Success[A] => s.value
    case f: Failure[E] => orElse(f.cause)
  }

  /**
   * Returns the value of a success. For a failure, throws one `Throwable`
   * chosen from the cause (`Cause#squashTrace`): the first typed failure if
   * there is one, otherwise a `java.lang.InterruptedException` if the fiber was
   * interrupted, otherwise the first defect. The pretty-printed cause,
   * including the stack trace of the caller, is added to the thrown `Throwable`
   * as a suppressed exception.
   */
  final def getOrThrow()(implicit ev: E <:< Throwable, unsafe: Unsafe): A =
    getOrElse(cause => throw cause.traced(externalStackTrace).squashTrace)

  /**
   * Returns the value of a success. For a failure, throws a `FiberFailure` that
   * wraps the whole cause, with the stack trace of the caller attached. Unlike
   * `getOrThrow`, it works for any error type `E`.
   */
  final def getOrThrowFiberFailure()(implicit unsafe: Unsafe): A =
    getOrElse(cause => throw FiberFailure(cause.traced(externalStackTrace)))

  private def externalStackTrace: StackTrace = {
    val stackTrace = new Throwable().getStackTrace.dropWhile(_.getClassName.startsWith("zio.Exit"))
    StackTrace.fromJava(FiberId.None, stackTrace)(Trace.empty)
  }

  /**
   * Determines if the result is a failure.
   */
  final def isFailure: Boolean = !isSuccess

  /**
   * Determines if the result is interrupted.
   */
  final def isInterrupted: Boolean = self match {
    case f: Failure[?] => f.cause.isInterrupted
    case _             => false
  }

  /**
   * Determines if the result is a failure whose cause contains only
   * interruptions, with no typed failure and no defect.
   */
  final def isInterruptedOnly: Boolean = self match {
    case f: Failure[?] => f.cause.isInterruptedOnly
    case _             => false
  }

  /**
   * Determines if the result is a success.
   */
  final def isSuccess: Boolean =
    self.isInstanceOf[Success[?]]

  override final def map[B](f: A => B)(implicit trace: Trace): ZIO[Any, E, B] =
    mapExit(f)

  /**
   * Maps over the value type.
   */
  final def mapExit[A1](f: A => A1): Exit[E, A1] =
    self match {
      case s: Success[A] => Exit.succeed(f(s.value))
      case e             => e.asInstanceOf[Exit[E, Nothing]]
    }

  /**
   * Maps over both the error and value type.
   */
  final def mapBothExit[E1, A1](f: E => E1, g: A => A1): Exit[E1, A1] =
    self match {
      case s: Success[A] => Exit.succeed(g(s.value))
      case e: Failure[E] => failCause(e.cause.map(f))
    }

  /**
   * Maps over the error type.
   */
  final def mapErrorExit[E1](f: E => E1): Exit[E1, A] =
    self match {
      case e: Failure[E] => failCause(e.cause.map(f))
      case s             => s.asInstanceOf[Exit[Nothing, A]]
    }

  /**
   * Maps over the cause type.
   */
  final def mapErrorCauseExit[E1](f: Cause[E] => Cause[E1]): Exit[E1, A] =
    self match {
      case e: Success[A] => e
      case e: Failure[E] => Failure(f(e.cause))
    }

  /**
   * Replaces each typed failure in the cause of this exit with `e1`, the same
   * as `orElseFailExit`. Defects and interruptions stay in the cause unchanged.
   */
  override final def orElseFail[E1](e1: => E1)(implicit ev: CanFail[E], trace: Trace): ZIO[Any, E1, A] =
    orElseFailExit(e1)

  /**
   * Replaces the error value with the one provided.
   */
  final def orElseFailExit[E1](e1: => E1): Exit[E1, A] =
    mapErrorExit(_ => e1)

  /**
   * Converts the `Exit` to an `Either[Throwable, A]`, by wrapping the cause in
   * `FiberFailure` (if the result is failed).
   */
  final def toEither: Either[Throwable, A] = self match {
    case s: Success[A] => Right(s.value)
    case f: Failure[E] => Left(FiberFailure(f.cause))
  }

  /**
   * Converts the `Exit` to a `scala.util.Try`: `Success` with the value, or
   * `Failure` with one `Throwable` chosen from the cause (`Cause#squash`): the
   * first typed failure, otherwise a `java.lang.InterruptedException` for an
   * interruption, otherwise the first defect.
   */
  final def toTry(implicit ev: E <:< Throwable): scala.util.Try[A] =
    self match {
      case s: Success[A] => scala.util.Success(s.value)
      case f: Failure[E] => scala.util.Failure(f.cause.squash)
    }

  /**
   * Returns `Trace.empty`: an `Exit` value records no trace of its own. The
   * stack traces of a failure are kept in its `Cause`.
   */
  final def trace: Trace = Trace.empty

  /**
   * Discards the value.
   */
  final def unitExit: Exit[E, Unit] = self match {
    case _: Success[?] => Exit.unit
    case e             => e.asInstanceOf[Exit[E, Nothing]]
  }

  /**
   * Returns an untraced exit value.
   */
  final def untraced: Exit[E, A] = mapErrorCauseExit(_.untraced)

  /**
   * Named alias for `<*>`.
   */
  final def zip[E1 >: E, B](that: Exit[E1, B])(implicit zippable: Zippable[A, B]): Exit[E1, zippable.Out] =
    self <*> that

  /**
   * Named alias for `<*`.
   */
  final def zipLeft[E1 >: E, B](that: Exit[E1, B]): Exit[E1, A] = self <* that

  /**
   * Named alias for `<&>`.
   */
  final def zipPar[E1 >: E, B](that: Exit[E1, B])(implicit zippable: Zippable[A, B]): Exit[E1, zippable.Out] =
    self <&> that

  /**
   * Named alias for `<&`.
   */
  final def zipParLeft[E1 >: E, B](that: Exit[E1, B]): Exit[E1, A] = self <& that

  /**
   * Named alias for `&>`.
   */
  final def zipParRight[E1 >: E, B](that: Exit[E1, B]): Exit[E1, B] = self &> that

  /**
   * Named alias for `*>`.
   */
  final def zipRight[E1 >: E, B](that: Exit[E1, B]): Exit[E1, B] = self *> that

  /**
   * Zips this together with the specified result using the combination
   * functions.
   */
  final def zipWith[E1 >: E, B, C](that: Exit[E1, B])(
    f: (A, B) => C,
    g: (Cause[E], Cause[E1]) => Cause[E1]
  ): Exit[E1, C] =
    self match {
      case l: Success[A] =>
        that match {
          case r: Success[B] => Exit.succeed(f(l.value, r.value))
          case f             => f.asInstanceOf[Exit[E, C]]
        }
      case l: Failure[E] =>
        that match {
          case r: Failure[E1] => Exit.failCause(g(l.cause, r.cause))
          case _              => l
        }
    }

}

/**
 * Constructors for [[Exit]] values, its two cases `Success` and `Failure`, and
 * functions that combine collections of exits.
 */
object Exit extends Serializable with ExitVersionSpecific {

  /**
   * The successful case of an [[Exit]], holding the value `value`.
   */
  final case class Success[+A](value: A) extends Exit[Nothing, A]

  /**
   * The failed case of an [[Exit]], holding the `cause` of the failure: typed
   * failures, defects or interruptions.
   */
  final case class Failure[+E](cause: Cause[E]) extends Exit[E, Nothing]

  /**
   * Returns an exit that failed because the fiber `id` interrupted it.
   */
  def interrupt(id: FiberId): Exit[Nothing, Nothing] =
    failCause(Cause.interrupt(id))

  /**
   * Collects all Exits, and returns a List of the values if all Exits
   * succeeded, or combines the causes sequentially in case of failures. Returns
   * `None` in case the input iterable is empty
   *
   * @see
   *   [[collectAllDiscard]] For a more performant variant that discard the
   *   successful values
   */
  def collectAll[E, A](exits: Iterable[Exit[E, A]]): Option[Exit[E, List[A]]] =
    collectAllWith(exits)(_ ++ _)

  /**
   * Collects all Exits, and succeeds with Unit if all Exits succeeded, or
   * combines the causes sequentially in case of failures.
   */
  def collectAllDiscard[E, A](exits: Iterable[Exit[E, A]]): Exit[E, Unit] =
    collectAllDiscardWith(exits)(_ ++ _)

  /**
   * Collects all Exits, and returns a List of the values if all Exits
   * succeeded, or combines the causes in parallel in case of failures. Returns
   * `None` in case the input iterable is empty
   *
   * @see
   *   [[collectAllParDiscard]] For a more performant variant that discard the
   *   successful values
   */
  def collectAllPar[E, A](exits: Iterable[Exit[E, A]]): Option[Exit[E, List[A]]] =
    collectAllWith(exits)(_ && _)

  /**
   * Collects all Exits, and succeeds with Unit if all Exits succeeded, or
   * combines the causes in parallel in case of failures.
   */
  def collectAllParDiscard[E, A](exits: Iterable[Exit[E, A]]): Exit[E, Unit] =
    collectAllDiscardWith(exits)(_ && _)

  private def collectAllWith[E, A](
    exits: Iterable[Exit[E, A]]
  )(
    combineError: (Cause[E], Cause[E]) => Cause[E]
  ): Option[Exit[E, List[A]]] =
    if (exits.isEmpty) None
    else {
      val builder = new ListBuffer[A]
      var cause   = null.asInstanceOf[Cause[E]]
      val it      = exits.iterator
      while (it.hasNext) {
        val head = it.next()
        head match {
          case s: Success[A] =>
            if (cause eq null) builder.append(s.value)
          case f: Failure[E] =>
            if (cause eq null) cause = f.cause
            else cause = combineError(cause, f.cause)
        }
      }
      if (cause eq null) Some(Success(builder.result()))
      else Some(Failure(cause))
    }

  private def collectAllDiscardWith[E, A](
    exits: Iterable[Exit[E, A]]
  )(
    combineError: (Cause[E], Cause[E]) => Cause[E]
  ): Exit[E, Unit] = {
    var cause = null.asInstanceOf[Cause[E]]
    val it    = exits.iterator
    while (it.hasNext) {
      val head = it.next()
      head match {
        case f: Failure[E] =>
          if (cause eq null) cause = f.cause
          else cause = combineError(cause, f.cause)
        case _ => ()
      }
    }
    if (cause eq null) Exit.unit
    else Failure(cause)
  }

  /**
   * Returns an exit that failed with the defect `t`.
   */
  def die(t: Throwable): Exit[Nothing, Nothing] =
    failCause(Cause.die(t))

  /**
   * Returns an exit that failed with the typed error `error`.
   */
  def fail[E](error: E): Exit[E, Nothing] =
    failCause(Cause.fail(error))

  /**
   * Returns an exit that failed with the specified cause.
   */
  def failCause[E](cause: Cause[E]): Exit[E, Nothing] =
    Failure(cause)

  /**
   * Flattens an exit of an exit: returns the inner exit when the outer one
   * succeeds, or the outer failure otherwise.
   */
  def flatten[E, A](exit: Exit[E, Exit[E, A]]): Exit[E, A] =
    exit.flatMapExit(identity)

  /**
   * Converts an `Either` to an exit: `Right` becomes a success and `Left` a
   * typed failure.
   */
  def fromEither[E, A](e: Either[E, A]): Exit[E, A] =
    e.fold(fail, succeed)

  /**
   * Converts an `Option` to an exit: `Some` becomes a success, and `None` a
   * typed failure with the error `()`.
   */
  def fromOption[A](o: Option[A]): Exit[Unit, A] =
    o.fold[Exit[Unit, A]](fail(()))(succeed)

  /**
   * Converts a `Try` to an exit: `Success` becomes a success, and `Failure` a
   * typed failure with its `Throwable`.
   */
  def fromTry[A](t: scala.util.Try[A]): Exit[Throwable, A] =
    t match {
      case scala.util.Success(a) => succeed(a)
      case scala.util.Failure(t) => fail(t)
    }

  /**
   * Returns an exit that succeeded with the value `a`.
   */
  def succeed[A](a: A): Exit[Nothing, A] = Success(a)

  /**
   * An exit that succeeded with `()`.
   */
  val unit: Exit[Nothing, Unit] = succeed(())

  /**
   * An exit that succeeded with `None`.
   */
  val none: Exit[Nothing, Option[Nothing]] = Success(None)

  private def zipRightWith[E, E1 >: E, A](left: Exit[E, Any], right: Exit[E1, A])(
    g: (Cause[E], Cause[E1]) => Cause[E1]
  ): Exit[E1, A] =
    left match {
      case l: Failure[E] =>
        right match {
          case r: Failure[E1] => Exit.failCause(g(l.cause, r.cause))
          case _              => l
        }
      case _ =>
        right
    }

  /**
   * Optimized [[succeed]] for Boolean values that uses cached
   * `Exit.succeed(true)` and `Exit.succeed(false)` instances
   */
  private[zio] def boolean(b: Boolean): Exit[Nothing, Boolean] =
    if (b) Exit.`true` else Exit.`false`

  private[zio] val `true`: Exit[Nothing, Boolean]            = Success(true)
  private[zio] val `false`: Exit[Nothing, Boolean]           = Success(false)
  private[zio] val emptyChunk: Exit[Nothing, Chunk[Nothing]] = Success(Chunk.empty)
  private[zio] val failNone: Exit[Option[Nothing], Nothing]  = Failure(Cause.none)
  private[zio] val failUnit: Exit[Unit, Nothing]             = Failure(Cause.unit)

  private[zio] val empty: Exit[Nothing, Nothing] = Exit.failCause[Nothing](Cause.empty)
}
