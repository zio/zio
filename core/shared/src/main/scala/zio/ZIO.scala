/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

import zio.clock.Clock
import zio.duration._
import zio.internal.tracing.{ ZIOFn, ZIOFn1, ZIOFn2 }
import zio.internal.{ Executor, Platform }
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }
import scala.reflect.ClassTag
import zio.{ TracingStatus => TrasingS }
import zio.{ InterruptStatus => InterruptS }

/**
 * A `ZIO[R, E, A]` ("Zee-Oh of Are Eeh Aye") is an immutable data structure
 * that models an effectful program. The effect requires an environment `R`,
 * and the effect may fail with an error `E` or produce a single `A`.
 *
 * Conceptually, this structure is equivalent to `ReaderT[R, EitherT[UIO, E, ?]]`
 * for some infallible effect monad `UIO`, but because monad transformers
 * perform poorly in Scala, this data structure bakes in the reader effect of
 * `ReaderT` with the recoverable error effect of `EitherT` without runtime
 * overhead.
 *
 * `ZIO` values are ordinary immutable values, and may be used like any other
 * value in purely functional code. Because `ZIO` values just *model* effects
 * (like input / output), which must be interpreted by a separate runtime system,
 * `ZIO` values are entirely pure and do not violate referential transparency.
 *
 * `ZIO` values can efficiently describe the following classes of effects:
 *
 *  - '''Pure Values''' &mdash; `ZIO.succeed`
 *  - ```Error Effects``` &mdash; `ZIO.fail`
 *  - '''Synchronous Effects''' &mdash; `IO.effect`
 *  - '''Asynchronous Effects''' &mdash; `IO.effectAsync`
 *  - '''Concurrent Effects''' &mdash; `IO#fork`
 *  - '''Resource Effects''' &mdash; `IO#bracket`
 *  - ```Contextual Effects``` &mdash; `ZIO.access`
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
 * values, see the default interpreter in `DefaultRuntime` or the safe main function in
 * `App`.
 */
sealed trait ZIO[-R, +E, +A] extends Serializable { self =>

  /**
   * Provides some of the environment required to run this effect,
   * leaving the remainder `R0`.
   *
   * {{{
   * val effect: ZIO[Console with Logging, Nothing, Unit] = ???
   *
   * effect.provideSome[Console](console =>
   *   new Console with Logging {
   *     val console = console
   *     val logging = new Logging {
   *       def log(line: String) = console.putStrLn(line)
   *     }
   *   }
   * )
   * }}}
   */
  final def provideSome[R0](f: R0 => R): ZIO[R0, E, A] =
    ZIO.accessM(r0 => self.provide(f(r0)))

  /**
   * An effectful version of `provideSome`, useful when the act of partial
   * provision requires an effect.
   *
   * {{{
   * val effect: ZIO[Console with Logging, Nothing, Unit] = ???
   *
   * val r0: ZIO[Console, Nothing, Console with Logging] = ???
   *
   * effect.provideSomeM(r0)
   * }}}
   */
  final def provideSomeM[R0, E1 >: E](r0: ZIO[R0, E1, R]): ZIO[R0, E1, A] =
    r0.flatMap(self.provide)

  /**
   * Uses the given Managed[E1, R] to the environment required to run this effect,
   * leaving no outstanding environments and returning IO[E1, A]
   */
  final def provideManaged[E1 >: E](r0: Managed[E1, R]): IO[E1, A] = provideSomeManaged(r0)

  /**
   * Uses the given ZManaged[R0, E1, R] to provide some of the environment required to run this effect,
   * leaving the remainder `R0`.
   */
  final def provideSomeManaged[R0, E1 >: E](r0: ZManaged[R0, E1, R]): ZIO[R0, E1, A] = r0.use(self.provide)

  /**
   * Returns an effect whose success is mapped by the specified `f` function.
   */
  def map[B](f: A => B): ZIO[R, E, B] = new ZIO.FlatMap(self, new ZIO.MapFn(f))

  /**
   * Returns an effect whose failure and success channels have been mapped by
   * the specified pair of functions, `f` and `g`.
   */
  final def bimap[E2, B](f: E => E2, g: A => B): ZIO[R, E2, B] = mapError(f).map(g)

  /**
   * Returns an effect that models the execution of this effect, followed by
   * the passing of its value to the specified continuation function `k`,
   * followed by the effect that it returns.
   *
   * {{{
   * val parsed = readFile("foo.txt").flatMap(file => parseFile(file))
   * }}}
   */
  def flatMap[R1 <: R, E1 >: E, B](k: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    new ZIO.FlatMap(self, k)

  /**
   * Alias for `flatMap`.
   *
   * {{{
   * val parsed = readFile("foo.txt") >>= parseFile
   * }}}
   */
  final def >>=[R1 <: R, E1 >: E, B](k: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] = flatMap(k)

  /**
   * Returns an effect that forks this effect into its own separate fiber,
   * returning the fiber immediately, without waiting for it to compute its
   * value.
   *
   * The returned fiber can be used to interrupt the forked fiber, await its
   * result, or join the fiber. See [[zio.Fiber]] for more information.
   *
   * {{{
   * for {
   *   fiber <- subtask.fork
   *   // Do stuff...
   *   a <- fiber.join
   * } yield a
   * }}}
   */
  final def fork: ZIO[R, Nothing, Fiber[E, A]] = new ZIO.Fork(self)

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, combining their results with the specified `f` function. If
   * either side fails, then the other side will be interrupted.
   */
  final def zipWithPar[R1 <: R, E1 >: E, B, C](that: ZIO[R1, E1, B])(f: (A, B) => C): ZIO[R1, E1, C] = {
    def coordinate[A, B](f: (A, B) => C)(winner: Exit[E1, A], loser: Fiber[E1, B]): ZIO[R1, E1, C] =
      winner match {
        case Exit.Success(a) => loser.join.map(f(a, _))
        case Exit.Failure(cause) =>
          loser.interrupt.flatMap {
            case Exit.Success(_)          => ZIO.halt(cause)
            case Exit.Failure(loserCause) => ZIO.halt(cause && loserCause)
          }
      }
    val g = (b: B, a: A) => f(a, b)
    (self raceWith that)(coordinate(f), coordinate(g))
  }

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, combining their results into a tuple. If either side fails,
   * then the other side will be interrupted, interrupted the result.
   */
  final def <&>[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, (A, B)] =
    self.zipWithPar(that)((a, b) => (a, b))

  /**
   * A named alias for `<&>`.
   */
  final def zipPar[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, (A, B)] =
    self <&> that

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, this effect result returned. If either side fails,
   * then the other side will be interrupted, interrupted the result.
   */
  final def <&[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, A] =
    self.zipWithPar(that)((a, b) => (a, b)).map(_._1)

  /**
   * A named alias for `<&`.
   */
  final def zipParLeft[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, A] =
    self <& that

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, specified effect result returned. If either side fails,
   * then the other side will be interrupted, interrupted the result.
   */
  final def &>[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    self.zipWithPar(that)((a, b) => (a, b)).map(_._2)

  /**
   * A named alias for `&>`.
   */
  final def zipParRight[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    self &> that

  /**
   * Returns an effect that races this effect with the specified effect,
   * returning the first successful `A` from the faster side. If one effect
   * succeeds, the other will be interrupted. If neither succeeds, then the
   * effect will fail with some error.
   */
  final def race[R1 <: R, E1 >: E, A1 >: A](that: ZIO[R1, E1, A1]): ZIO[R1, E1, A1] =
    raceEither(that).map(_.merge)

  /**
   * Returns an effect that races this effect with the specified effect,
   * yielding the first result to succeed. If neither effect succeeds, then the
   * composed effect will fail with some error.
   */
  final def raceEither[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, Either[A, B]] =
    raceWith(that)(
      (exit, right) =>
        exit.foldM[Any, E1, Either[A, B]](
          _ => right.join.map(Right(_)),
          a => ZIO.succeedLeft(a) <* right.interrupt
        ),
      (exit, left) =>
        exit.foldM[Any, E1, Either[A, B]](
          _ => left.join.map(Left(_)),
          b => ZIO.succeedRight(b) <* left.interrupt
        )
    ).refailWithTrace

  /**
   * Returns an effect that races this effect with the specified effect,
   * yielding the first result to complete, whether by success or failure. If
   * neither effect completes, then the composed effect will not complete.
   */
  final def raceAttempt[R1 <: R, E1 >: E, A1 >: A](that: ZIO[R1, E1, A1]): ZIO[R1, E1, A1] =
    raceWith(that)(
      { case (l, f) => f.interrupt *> l.fold(ZIO.halt, ZIO.succeed) },
      { case (r, f) => f.interrupt *> r.fold(ZIO.halt, ZIO.succeed) }
    ).refailWithTrace

  /**
   * Returns an effect that races this effect with the specified effect, calling
   * the specified finisher as soon as one result or the other has been computed.
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
      ZIO.flatten(race.modify((c: Int) => (if (c > 0) ZIO.unit else f(res, loser).to(done).unit) -> (c + 1)))

    for {
      done <- Promise.make[E2, C]
      race <- Ref.make[Int](0)
      c <- ZIO.uninterruptibleMask { restore =>
            for {
              left  <- ZIO.interruptible(self).fork
              right <- ZIO.interruptible(that).fork
              _     <- left.await.flatMap(arbiter(leftDone, right, race, done)).fork
              _     <- right.await.flatMap(arbiter(rightDone, left, race, done)).fork
              c     <- restore(done.await).onInterrupt(left.interrupt *> right.interrupt)
            } yield c
          }
    } yield c
  }

  /**
   * Returns an effect that races this effect with all the specified effects,
   * yielding the value of the first effect to succeed with a value.
   * Losers of the race will be interrupted immediately
   */
  def raceAll[R1 <: R, E1 >: E, A1 >: A](ios: Iterable[ZIO[R1, E1, A1]]): ZIO[R1, E1, A1] = ZIO.raceAll(self, ios)

  /**
   * Executes this effect and returns its value, if it succeeds, but
   * otherwise executes the specified effect.
   */
  final def orElse[R1 <: R, E2, A1 >: A](that: => ZIO[R1, E2, A1]): ZIO[R1, E2, A1] =
    tryOrElse(that, new ZIO.SucceedFn(() => that))

  /**
   * Operator alias for `orElse`.
   */
  final def <>[R1 <: R, E2, A1 >: A](that: => ZIO[R1, E2, A1]): ZIO[R1, E2, A1] =
    orElse(that)

  /**
   * Returns an effect that will produce the value of this effect, unless it
   * fails, in which case, it will produce the value of the specified effect.
   */
  final def orElseEither[R1 <: R, E2, B](that: => ZIO[R1, E2, B]): ZIO[R1, E2, Either[A, B]] =
    tryOrElse(that.map(Right(_)), ZIO.succeedLeft)

  private[this] final def tryOrElse[R1 <: R, E2, B](
    that: => ZIO[R1, E2, B],
    succ: A => ZIO[R1, E2, B]
  ): ZIO[R1, E2, B] =
    new ZIO.Fold[R1, E, E2, A, B](
      self,
      ZIOFn(() => that)(_.stripFailures match {
        case None    => that
        case Some(c) => ZIO.halt(c)
      }),
      succ
    )

  /**
   * Returns an effect that performs the outer effect first, followed by the
   * inner effect, yielding the value of the inner effect.
   *
   * This method can be used to "flatten" nested effects.
   **/
  final def flatten[R1 <: R, E1 >: E, B](implicit ev1: A <:< ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    self.flatMap(a => ev1(a))

  /**
   * Returns an effect with its error channel mapped using the specified
   * function. This can be used to lift a "smaller" error into a "larger"
   * error.
   */
  final def mapError[E2](f: E => E2): ZIO[R, E2, A] =
    self.foldCauseM(new ZIO.MapErrorFn(f), new ZIO.SucceedFn(f))

  /**
   * Returns an effect with its full cause of failure mapped using the
   * specified function. This can be used to transform errors while
   * preserving the original structure of `Cause`.
   *
   * @see [[absorb]], [[sandbox]], [[catchAllCause]] - other functions for dealing with defects
   */
  final def mapErrorCause[E2](h: Cause[E] => Cause[E2]): ZIO[R, E2, A] =
    self.foldCauseM(new ZIO.MapErrorCauseFn(h), new ZIO.SucceedFn(h))

  /**
   * Creates a composite effect that represents this effect followed by another
   * one that may depend on the error produced by this one.
   *
   * {{{
   * val parsed = readFile("foo.txt").flatMapError(error => logErrorToFile(error))
   * }}}
   */
  final def flatMapError[R1 <: R, E2](f: E => ZIO[R1, Nothing, E2]): ZIO[R1, E2, A] =
    flipWith(_ flatMap f)

  /**
   *  Swaps the error/value parameters, applies the function `f` and flips the parameters back
   */
  final def flipWith[R1, A1, E1](f: ZIO[R, A, E] => ZIO[R1, A1, E1]): ZIO[R1, E1, A1] = f(self.flip).flip

  /**
   * Returns an effect that swaps the error/success cases. This allows you to
   * use all methods on the error channel, possibly before flipping back.
   */
  final def flip: ZIO[R, A, E] =
    self.foldM(ZIO.succeed, ZIO.fail)

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
  final def foldM[R1 <: R, E2, B](failure: E => ZIO[R1, E2, B], success: A => ZIO[R1, E2, B]): ZIO[R1, E2, B] =
    foldCauseM(new ZIO.FoldCauseMFailureFn(failure), success)

  /**
   * A more powerful version of `foldM` that allows recovering from any kind of failure except interruptions.
   */
  def foldCauseM[R1 <: R, E2, B](failure: Cause[E] => ZIO[R1, E2, B], success: A => ZIO[R1, E2, B]): ZIO[R1, E2, B] =
    new ZIO.Fold(self, failure, success)

  /**
   * Folds over the failure value or the success value to yield an effect that
   * does not fail, but succeeds with the value returned by the left or right
   * function passed to `fold`.
   */
  final def fold[B](failure: E => B, success: A => B): ZIO[R, Nothing, B] =
    foldM(new ZIO.MapFn(failure), new ZIO.MapFn(success))

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
  final def either: ZIO[R, Nothing, Either[E, A]] =
    self.foldM(ZIO.succeedLeft, ZIO.succeedRight)

  /**
   * Returns an effect that submerges the error case of an `Either` into the
   * `ZIO`. The inverse operation of `ZIO.either`.
   */
  final def absolve[R1 <: R, E1, B](implicit ev1: ZIO[R, E, A] <:< ZIO[R1, E1, Either[E1, B]]): ZIO[R1, E1, B] =
    ZIO.absolve[R1, E1, B](ev1(self))

  /**
   * Unwraps the optional success of this effect, but can fail with unit value.
   */
  final def get[E1 >: E, B](implicit ev1: E1 =:= Nothing, ev2: A <:< Option[B]): ZIO[R, Unit, B] =
    ZIO.absolve(self.mapError(ev1).map(ev2(_).toRight(())))

  /**
   * Returns a new effect that ignores the success or failure of this effect.
   */
  final def ignore: ZIO[R, Nothing, Unit] = self.foldCauseM(_ => ZIO.unit, _ => ZIO.unit)

  /**
   * Executes this effect, skipping the error but returning optionally the success.
   */
  final def option: ZIO[R, Nothing, Option[A]] =
    self.foldCauseM(_ => IO.succeed(None), a => IO.succeed(Some(a)))

  /**
   * A less powerful variant of `bracket` where the resource acquired by this
   * effect is not needed.
   */
  final def bracket_[R1 <: R, E1 >: E]: ZIO.BracketAcquire_[R1, E1] =
    new ZIO.BracketAcquire_(self)

  /**
   * Uncurried version. Doesn't offer curried syntax and has worse
   * type-inference characteristics, but it doesn't allocate intermediate
   * [[zio.ZIO.BracketAcquire_]] and [[zio.ZIO.BracketRelease_]] objects.
   */
  final def bracket_[R1 <: R, E1 >: E, B](
    release: ZIO[R1, Nothing, _],
    use: ZIO[R1, E1, B]
  ): ZIO[R1, E1, B] =
    ZIO.bracket(self, (_: A) => release, (_: A) => use)

  /**
   * Shorthand for the curried version of `ZIO.bracketExit`.
   */
  final def bracketExit[R1 <: R, E1 >: E, A1 >: A]: ZIO.BracketExitAcquire[R1, E1, A1] = ZIO.bracketExit(self)

  /**
   * Shorthand for the uncurried version of `ZIO.bracketExit`.
   */
  final def bracketExit[R1 <: R, E1 >: E, B](
    release: (A, Exit[E1, B]) => ZIO[R1, Nothing, _],
    use: A => ZIO[R1, E1, B]
  ): ZIO[R1, E1, B] = ZIO.bracketExit(self, release, use)

  /**
   * Returns an effect that, if this effect _starts_ execution, then the
   * specified `finalizer` is guaranteed to begin execution, whether this effect
   * succeeds, fails, or is interrupted.
   *
   * Finalizers offer very powerful guarantees, but they are low-level, and
   * should generally not be used for releasing resources. For higher-level
   * logic built on `ensuring`, see `ZIO#bracket`.
   */
  final def ensuring[R1 <: R](finalizer: ZIO[R1, Nothing, _]): ZIO[R1, E, A] =
    ZIO.uninterruptibleMask(
      restore =>
        restore(self)
          .foldCauseM(
            cause1 =>
              finalizer.foldCauseM[R1, E, Nothing](
                cause2 => ZIO.halt(cause1 ++ cause2),
                _ => ZIO.halt(cause1)
              ),
            value =>
              finalizer.foldCauseM[R1, E, A](
                cause1 => ZIO.halt(cause1),
                _ => ZIO.succeed(value)
              )
          )
    )

  /**
   * Executes the effect on the specified `ExecutionContext` and then shifts back
   * to the default one.
   */
  final def on(ec: ExecutionContext): ZIO[R, E, A] =
    self.lock(Executor.fromExecutionContext(Int.MaxValue)(ec))

  /**
   * Forks an effect that will be executed on the specified `ExecutionContext`.
   */
  final def forkOn(ec: ExecutionContext): ZIO[R, E, Fiber[E, A]] =
    self.on(ec).fork

  /**
   * Executes the release effect only if there was an error.
   */
  final def bracketOnError[R1 <: R, E1 >: E, B](
    release: A => ZIO[R1, Nothing, _]
  )(use: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    ZIO.bracketExit(self)(
      (a: A, eb: Exit[E1, B]) =>
        eb match {
          case Exit.Failure(_) => release(a)
          case _               => ZIO.unit
        }
    )(use)

  /**
   * Converts this ZIO to [[zio.Managed]].
   */
  final def toManaged[R1 <: R](release: A => ZIO[R1, Nothing, _]): ZManaged[R1, E, A] =
    ZManaged.make[R1, E, A](this)(release)

  /**
   * Converts this ZIO to [[zio.ZManaged]] with no release action.
   */
  final def toManaged_ : ZManaged[R, E, A] =
    ZManaged.fromEffect[R, E, A](this)

  /**
   * Runs the specified effect if this effect fails, providing the error to the
   * effect if it exists. The provided effect will not be interrupted.
   */
  final def onError[R1 <: R](cleanup: Cause[E] => ZIO[R1, Nothing, _]): ZIO[R1, E, A] =
    ZIO.bracketExit(ZIO.unit)(
      (_, eb: Exit[E, A]) =>
        eb match {
          case Exit.Success(_)     => ZIO.unit
          case Exit.Failure(cause) => cleanup(cause)
        }
    )(_ => self)

  /**
   * Runs the specified effect if this effect is interrupted.
   */
  final def onInterrupt[R1 <: R](cleanup: ZIO[R1, Nothing, Any]): ZIO[R1, E, A] =
    self.ensuring(
      ZIO.descriptorWith(descriptor => if (descriptor.interrupted) cleanup else ZIO.unit)
    )

  /**
   * Runs the specified effect if this effect is terminated, either because of
   * a defect or because of interruption.
   */
  final def onTermination[R1 <: R](cleanup: Cause[Nothing] => ZIO[R1, Nothing, _]): ZIO[R1, E, A] =
    ZIO.bracketExit(ZIO.unit)(
      (_, eb: Exit[E, A]) =>
        eb match {
          case Exit.Failure(cause) => cause.failureOrCause.fold(_ => ZIO.unit, cleanup)
          case _                   => ZIO.unit
        }
    )(_ => self)

  /**
   * Enables supervision for this effect. This will cause fibers forked by
   * this effect to be tracked and will enable their inspection via [[ZIO.children]].
   */
  final def supervised: ZIO[R, E, A] = ZIO.supervised(self)

  /**
   * Disables supervision for this effect. This will cause fibers forked by
   * this effect to not be tracked or appear in the list returned by [[ZIO.children]].
   */
  final def unsupervised: ZIO[R, E, A] = ZIO.unsupervised(self)

  /**
   * Returns a new effect that ensures that any fibers that are forked by
   * the effect are interrupted when this effect completes.
   */
  final def interruptChildren: ZIO[R, E, A] = ZIO.interruptChildren(self)

  /**
   * Supervises this effect, which ensures that any fibers that are forked by
   * the effect are handled by the provided supervisor.
   */
  final def handleChildrenWith[R1 <: R](supervisor: Iterable[Fiber[_, _]] => ZIO[R1, Nothing, _]): ZIO[R1, E, A] =
    ZIO.handleChildrenWith[R1, E, A](self)(supervisor)

  /**
   * Performs this effect uninterruptibly. This will prevent the effect from
   * being terminated externally, but the effect may fail for internal reasons
   * (e.g. an uncaught error) or terminate due to defect.
   *
   * Uninterruptible effects may recover from all failure causes (including
   * interruption of an inner effect that has been made interruptible).
   */
  final def uninterruptible: ZIO[R, E, A] = interruptStatus(InterruptStatus.Uninterruptible)

  /**
   * Performs this effect interruptibly. Because this is the default, this
   * operation only has additional meaning if the effect is located within
   * an uninterruptible section.
   */
  final def interruptible: ZIO[R, E, A] = interruptStatus(InterruptStatus.Interruptible)

  /**
   * Switches the interrupt status for this effect. If `true` is used, then the
   * effect becomes interruptible (the default), while if `false` is used, then
   * the effect becomes uninterruptible. These changes are compositional, so
   * they only affect regions of the effect.
   */
  final def interruptStatus(flag: InterruptStatus): ZIO[R, E, A] = new ZIO.InterruptStatus(self, flag)

  /**
   * Recovers from all errors.
   *
   * {{{
   * openFile("config.json").catchAll(_ => IO.succeed(defaultConfig))
   * }}}
   */
  final def catchAll[R1 <: R, E2, A1 >: A](h: E => ZIO[R1, E2, A1]): ZIO[R1, E2, A1] =
    self.foldM[R1, E2, A1](h, new ZIO.SucceedFn(h))

  /**
   * Recovers from all errors with provided Cause.
   *
   * {{{
   * openFile("config.json").catchAllCause(_ => IO.succeed(defaultConfig))
   * }}}
   *
   * @see [[absorb]], [[sandbox]], [[mapErrorCause]] - other functions that can recover from defects
   */
  final def catchAllCause[R1 <: R, E2, A1 >: A](h: Cause[E] => ZIO[R1, E2, A1]): ZIO[R1, E2, A1] =
    self.foldCauseM[R1, E2, A1](h, new ZIO.SucceedFn(h))

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
    def tryRescue(c: Cause[E]): ZIO[R1, E1, A1] =
      c.failureOrCause.fold(t => pf.applyOrElse(t, (_: E) => ZIO.halt(c)), ZIO.halt)

    self.foldCauseM[R1, E1, A1](ZIOFn(pf)(tryRescue), new ZIO.SucceedFn(pf))
  }

  /**
   * Attach a wrapping trace pointing to this location in case of error.
   *
   * Useful when joining fibers to make the resulting trace mention
   * the `join` point, otherwise only the traces of joined fibers are
   * included.
   *
   * {{{
   *   for {
   *     badFiber <- UIO(1 / 0).fork
   *     _ <- badFiber.join.refailWithTrace
   *   } yield ()
   * }}}
   * */
  final def refailWithTrace: ZIO[R, E, A] =
    foldCauseM(c => ZIO.haltWith(trace => Cause.traced(c, trace())), ZIO.succeed)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest.
   */
  final def refineOrDie[E1](pf: PartialFunction[E, E1])(implicit ev: E <:< Throwable): ZIO[R, E1, A] =
    refineOrDieWith(pf)(ev)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest.
   */
  final def refineToOrDie[E1: ClassTag](implicit ev: E <:< Throwable): ZIO[R, E1, A] =
    refineOrDieWith { case e: E1 => e }(ev)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest, using
   * the specified function to convert the `E` into a `Throwable`.
   */
  final def refineOrDieWith[E1](pf: PartialFunction[E, E1])(f: E => Throwable): ZIO[R, E1, A] =
    self catchAll (err => (pf lift err).fold[ZIO[R, E1, A]](ZIO.die(f(err)))(ZIO.fail(_)))

  /**
   * Translates effect failure into death of the fiber, making all failures unchecked and
   * not a part of the type of the effect.
   */
  final def orDie[E1 >: E](implicit ev: E1 <:< Throwable): ZIO[R, Nothing, A] =
    orDieWith(ev)

  /**
   * Keeps none of the errors, and terminates the fiber with them, using
   * the specified function to convert the `E` into a `Throwable`.
   */
  final def orDieWith(f: E => Throwable): ZIO[R, Nothing, A] =
    (self mapError f) catchAll (IO.die)

  /**
   * Returns an effect that, if evaluated, will return the lazily computed result
   * of this effect.
   */
  final def memoize: ZIO[R, Nothing, IO[E, A]] =
    for {
      r <- ZIO.environment[R]
      p <- Promise.make[E, A]
      l <- Promise.make[Nothing, Unit]
      _ <- (l.await *> ((self provide r) to p)).fork
    } yield l.succeed(()) *> p.await

  /**
   * Maps this effect to the specified constant while preserving the
   * effects of this effect.
   */
  final def const[B](b: => B): ZIO[R, E, B] = self.flatMap(new ZIO.ConstFn(() => b))

  final def <<<[R1, E1 >: E](that: ZIO[R1, E1, R]): ZIO[R1, E1, A] =
    for {
      r1 <- ZIO.environment[R1]
      r  <- that provide r1
      a  <- self provide r
    } yield a

  final def compose[R1, E1 >: E](that: ZIO[R1, E1, R]): ZIO[R1, E1, A] = self <<< that

  final def >>>[R1 >: A, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R, E1, B] =
    for {
      r1 <- ZIO.environment[R]
      r  <- self provide r1
      a  <- that provide r
    } yield a

  final def |||[R1, E1 >: E, A1 >: A](that: ZIO[R1, E1, A1]): ZIO[Either[R, R1], E1, A1] =
    for {
      either <- ZIO.environment[Either[R, R1]]
      a1     <- either.fold(self.provide, that.provide)
    } yield a1

  final def join[R1, E1 >: E, A1 >: A](that: ZIO[R1, E1, A1]): ZIO[Either[R, R1], E1, A1] = self ||| that

  final def +++[R1, B, E1 >: E](that: ZIO[R1, E1, B]): ZIO[Either[R, R1], E1, Either[A, B]] =
    for {
      e <- ZIO.environment[Either[R, R1]]
      r <- e.fold(self.map(Left(_)) provide _, that.map(Right(_)) provide _)
    } yield r

  final def andThen[R1 >: A, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R, E1, B] =
    self >>> that

  final def first[R1 <: R, A1 >: A]: ZIO[R1, E, (A1, R1)] = self &&& ZIO.identity[R1]

  final def second[R1 <: R, A1 >: A]: ZIO[R1, E, (R1, A1)] = ZIO.identity[R1] &&& self

  final def left[R1 <: R, C]: ZIO[Either[R1, C], E, Either[A, C]] = self +++ ZIO.identity[C]

  final def right[R1 <: R, C]: ZIO[Either[C, R1], E, Either[C, A]] = ZIO.identity[C] +++ self

  /**
   * A variant of `flatMap` that ignores the value produced by this effect.
   */
  final def *>[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    self.flatMap(new ZIO.ZipRightFn(() => that))

  /**
   * A named alias for `*>`.
   */
  final def zipRight[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    self *> that

  /**
   * Sequences the specified effect after this effect, but ignores the
   * value produced by the effect.
   */
  final def <*[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B]): ZIO[R1, E1, A] =
    self.flatMap(new ZIO.ZipLeftFn(() => that))

  /**
   * A named alias for `<*`.
   */
  final def zipLeft[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B]): ZIO[R1, E1, A] =
    self <* that

  /**
   * Sequentially zips this effect with the specified effect using the
   * specified combiner function.
   */
  final def zipWith[R1 <: R, E1 >: E, B, C](that: ZIO[R1, E1, B])(f: (A, B) => C): ZIO[R1, E1, C] =
    self.flatMap(a => that.map(ZIOFn(f)(b => f(a, b))))

  /**
   * Sequentially zips this effect with the specified effect, combining the
   * results into a tuple.
   */
  final def &&&[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, (A, B)] =
    self.zipWith(that)((a, b) => (a, b))

  /**
   * Alias for `&&&`.
   */
  final def <*>[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, (A, B)] =
    self &&& that

  /**
   * A named alias for `&&&` or `<*>`.
   */
  final def zip[R1 <: R, E1 >: E, B](that: ZIO[R1, E1, B]): ZIO[R1, E1, (A, B)] =
    self &&& that

  /**
   * The moral equivalent of `if (p) exp`
   */
  final def when[R1 <: R, E1 >: E](b: Boolean): ZIO[R1, E1, Unit] =
    ZIO.when(b)(self)

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  final def whenM[R1 <: R, E1 >: E](
    b: ZIO[R1, Nothing, Boolean]
  ): ZIO[R1, E1, Unit] =
    ZIO.whenM(b)(self)

  /**
   * Repeats this effect forever (until the first error). For more sophisticated
   * schedules, see the `repeat` method.
   */
  final def forever: ZIO[R, E, Nothing] = self *> self.forever

  /**
   * Repeats this effect with the specified schedule until the schedule
   * completes, or until the first failure.
   * Repeats are done in addition to the first execution so that
   * `io.repeat(Schedule.once)` means "execute io and in case of success repeat `io` once".
   */
  final def repeat[R1 <: R, B](schedule: ZSchedule[R1, A, B]): ZIO[R1 with Clock, E, B] =
    repeatOrElse[R1, E, B](schedule, (e, _) => ZIO.fail(e))

  /**
   * Repeats this effect with the specified schedule until the schedule
   * completes, or until the first failure. In the event of failure the progress
   * to date, together with the error, will be passed to the specified handler.
   */
  final def repeatOrElse[R1 <: R, E2, B](
    schedule: ZSchedule[R1, A, B],
    orElse: (E, Option[B]) => ZIO[R1, E2, B]
  ): ZIO[R1 with Clock, E2, B] =
    repeatOrElseEither[R1, B, E2, B](schedule, orElse).map(_.merge)

  /**
   * Repeats this effect with the specified schedule until the schedule
   * completes, or until the first failure. In the event of failure the progress
   * to date, together with the error, will be passed to the specified handler.
   */
  final def repeatOrElseEither[R1 <: R, B, E2, C](
    schedule: ZSchedule[R1, A, B],
    orElse: (E, Option[B]) => ZIO[R1 with Clock, E2, C]
  ): ZIO[R1 with Clock, E2, Either[C, B]] = {
    def loop(last: Option[() => B], state: schedule.State): ZIO[R1 with Clock, E2, Either[C, B]] =
      self.foldM(
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
  final def retry[R1 <: R, E1 >: E, S](policy: ZSchedule[R1, E1, S]): ZIO[R1 with Clock, E1, A] =
    retryOrElse[R1, A, E1, S, E1](policy, (e: E1, _: S) => ZIO.fail(e))

  /**
   * Retries with the specified schedule, until it fails, and then both the
   * value produced by the schedule together with the last error are passed to
   * the recovery function.
   */
  final def retryOrElse[R1 <: R, A2 >: A, E1 >: E, S, E2](
    policy: ZSchedule[R1, E1, S],
    orElse: (E1, S) => ZIO[R1, E2, A2]
  ): ZIO[R1 with Clock, E2, A2] =
    retryOrElseEither(policy, orElse).map(_.merge)

  /**
   * Retries with the specified schedule, until it fails, and then both the
   * value produced by the schedule together with the last error are passed to
   * the recovery function.
   */
  final def retryOrElseEither[R1 <: R, E1 >: E, S, E2, B](
    policy: ZSchedule[R1, E1, S],
    orElse: (E1, S) => ZIO[R1, E2, B]
  ): ZIO[R1 with Clock, E2, Either[B, A]] = {
    def loop(state: policy.State): ZIO[R1 with Clock, E2, Either[B, A]] =
      self.foldM(
        err =>
          policy
            .update(err, state)
            .flatMap(
              decision =>
                if (decision.cont) clock.sleep(decision.delay) *> loop(decision.state)
                else orElse(err, decision.finish()).map(Left(_))
            ),
        succ => ZIO.succeedRight(succ)
      )

    policy.initial.flatMap(loop)
  }

  /**
   * Returns the effect resulting from mapping the success of this effect to unit.
   */
  @deprecated("use unit", "1.0.0")
  final def void: ZIO[R, E, Unit] = unit

  /**
   * Returns the effect resulting from mapping the success of this effect to unit.
   */
  final def unit: ZIO[R, E, Unit] = const(())

  /**
   * Returns an effect that effectfully "peeks" at the success of this effect.
   *
   * {{{
   * readFile("data.json").tap(putStrLn)
   * }}}
   */
  final def tap[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, _]): ZIO[R1, E1, A] = self.flatMap(new ZIO.TapFn(f))

  /**
   * Returns an effect that effectfully "peeks" at the failure or success of
   * this effect.
   * {{{
   * readFile("data.json").tapBoth(logError(_), logData(_))
   * }}}
   */
  final def tapBoth[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, _], g: A => ZIO[R1, E1, _]): ZIO[R1, E1, A] =
    self.foldCauseM(new ZIO.TapErrorRefailFn(f), new ZIO.TapFn(g))

  /**
   * Returns an effect that effectfully "peeks" at the failure of this effect.
   * {{{
   * readFile("data.json").tapError(logError(_))
   * }}}
   */
  final def tapError[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, _]): ZIO[R1, E1, A] =
    self.foldCauseM(new ZIO.TapErrorRefailFn(f), ZIO.succeed)

  /**
   * Provides the `ZIO` effect with its required environment, which eliminates
   * its dependency on `R`.
   */
  final def provide(r: R): IO[E, A] = ZIO.provide(r)(self)

  /**
   * Returns an effect that will timeout this effect, returning `None` if the
   * timeout elapses before the effect has produced a value; and returning
   * `Some` of the produced value otherwise.
   *
   * If the timeout elapses without producing a value, the running effect
   * will be safely interrupted
   *
   */
  final def timeout(d: Duration): ZIO[R with Clock, E, Option[A]] = timeoutTo(None)(Some(_))(d)

  /**
   * Returns an effect that will timeout this effect, returning either the
   * default value if the timeout elapses before the effect has produced a
   * value; and or returning the result of applying the function `f` to the
   * success value of the effect.
   *
   * If the timeout elapses without producing a value, the running effect
   * will be safely interrupted
   *
   * {{{
   * IO.succeed(1).timeoutTo(None)(Some(_))(1.second)
   * }}}
   */
  final def timeoutTo[R1 <: R, E1 >: E, A1 >: A, B](b: B): ZIO.TimeoutTo[R1, E1, A1, B] =
    new ZIO.TimeoutTo(self, b)

  /**
   * The same as [[timeout]], but instead of producing a `None` in the event
   * of timeout, it will produce the specified error.
   */
  final def timeoutFail[E1 >: E](e: E1)(d: Duration): ZIO[R with Clock, E1, A] =
    ZIO.flatten(timeoutTo(ZIO.fail(e))(ZIO.succeed)(d))

  /**
   * Returns a new effect that executes this one and times the execution.
   */
  final def timed: ZIO[R with Clock, E, (Duration, A)] = timedWith(clock.nanoTime)

  /**
   * A more powerful variation of `timed` that allows specifying the clock.
   */
  final def timedWith[R1 <: R, E1 >: E](nanoTime: ZIO[R1, E1, Long]): ZIO[R1, E1, (Duration, A)] =
    summarized[R1, E1, Long, Duration]((start, end) => Duration.fromNanos(end - start))(nanoTime)

  /**
   * Summarizes a effect by computing some value before and after execution, and
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
   * Returns an effect that is delayed from this effect by the specified
   * [[zio.duration.Duration]].
   */
  final def delay(duration: Duration): ZIO[R with Clock, E, A] =
    clock.sleep(duration) *> self

  /**
   * Returns an effect whose execution is locked to the specified executor.
   * This is useful when an effect must be executued somewhere, for example:
   * on a UI thread, inside a client library's thread pool, inside a blocking
   * thread pool, inside a low-latency thread pool, or elsewhere.
   *
   * Use of this method does not alter the execution semantics of other effects
   * composed with this one, making it easy to compositionally reason about
   * where effects are running.
   */
  final def lock(executor: Executor): ZIO[R, E, A] =
    ZIO.lock(executor)(self)

  /**
   * Returns an effect that semantically runs the effect on a fiber,
   * producing an [[zio.Exit]] for the completion value of the fiber.
   */
  final def run: ZIO[R, Nothing, Exit[E, A]] =
    new ZIO.Fold[R, E, Nothing, A, Exit[E, A]](
      self,
      cause => ZIO.succeed(Exit.halt(cause)),
      succ => ZIO.succeed(Exit.succeed(succ))
    )

  /**
   * Exposes the full cause of failure of this effect.
   *
   * {{{
   * case class DomainError()
   *
   * val veryBadIO: IO[DomainError, Unit] =
   *   IO.effectTotal(5 / 0) *> IO.fail(DomainError())
   *
   * val caught: UIO[Unit] =
   *   veryBadIO.sandbox.catchAll {
   *     case Cause.Die(_: ArithmeticException) =>
   *       // Caught defect: divided by zero!
   *       IO.succeed(0)
   *     case Cause.Fail(e) =>
   *       // Caught error: DomainError!
   *       IO.succeed(0)
   *     case cause =>
   *       // Caught unknown defects, shouldn't recover!
   *       IO.halt(cause)
   *    *
   *   }
   * }}}
   *
   * @see [[absorb]], [[catchAllCause]], [[mapErrorCause]] - other functions that can recover from defects
   */
  final def sandbox: ZIO[R, Cause[E], A] = foldCauseM(ZIO.fail, ZIO.succeed)

  /**
   * The inverse operation to `sandbox`. Submerges the full cause of failure.
   */
  final def unsandbox[R1 <: R, E1, A1 >: A](implicit ev1: ZIO[R, E, A] <:< ZIO[R1, Cause[E1], A1]): ZIO[R1, E1, A1] =
    ZIO.unsandbox(ev1(self))

  /**
   * Companion helper to `sandbox`. Allows recovery, and partial recovery, from
   * errors and defects alike, as in:
   *
   * {{{
   * case class DomainError()
   *
   * val veryBadIO: IO[DomainError, Unit] =
   *   IO.effectTotal(5 / 0) *> IO.fail(DomainError())
   *
   * val caught: IO[DomainError, Unit] =
   *   veryBadIO.sandboxWith(_.catchSome {
   *     case Cause.Die(_: ArithmeticException)=>
   *       // Caught defect: divided by zero!
   *       IO.succeed(0)
   *   })
   * }}}
   *
   * Using `sandboxWith` with `catchSome` is better than using
   * `io.sandbox.catchAll` with a partial match, because in
   * the latter, if the match fails, the original defects will
   * be lost and replaced by a `MatchError`
   */
  final def sandboxWith[R1 <: R, E2, B](f: ZIO[R1, Cause[E], A] => ZIO[R1, Cause[E2], B]): ZIO[R1, E2, B] =
    ZIO.unsandbox(f(self.sandbox))

  /**
   * Attempts to convert defects into a failure, throwing away all information
   * about the cause of the failure.
   *
   * @see [[sandbox]], [[catchAllCause]], [[mapErrorCause]] - other functions that can recover from defects
   */
  final def absorb(implicit ev: E <:< Throwable): ZIO[R, Throwable, A] =
    absorbWith(ev)

  /**
   * Attempts to convert defects into a failure, throwing away all information
   * about the cause of the failure.
   */
  final def absorbWith(f: E => Throwable): ZIO[R, Throwable, A] =
    self.sandbox
      .foldM(
        cause => ZIO.fail(cause.squashWith(f)),
        ZIO.succeed
      )

  /**
   * Returns an effect that keeps or breaks a promise based on the result of
   * this effect. Synchronizes interruption, so if this effect is interrupted,
   * the specified promise will be interrupted, too.
   */
  final def to[E1 >: E, A1 >: A](p: Promise[E1, A1]): ZIO[R, Nothing, Boolean] =
    self.run.flatMap(x => p.done(ZIO.done(x))).onInterrupt(p.interrupt)

  /**
   * Converts the effect into a [[scala.concurrent.Future]].
   */
  final def toFuture(implicit ev2: E <:< Throwable): ZIO[R, Nothing, scala.concurrent.Future[A]] =
    self toFutureWith ev2

  /**
   * Converts the effect into a [[scala.concurrent.Future]].
   */
  final def toFutureWith(f: E => Throwable): ZIO[R, Nothing, scala.concurrent.Future[A]] =
    self.fork >>= (_.toFutureWith(f))

  /**
   * Disables ZIO tracing facilities for the duration of the effect.
   *
   * Note: ZIO tracing is cached, as such after the first iteration
   * it has a negligible effect on performance of hot-spots (Additional
   * hash map lookup per flatMap). As such, using `untraced` sections
   * is not guaranteed to result in a noticeable performance increase.
   */
  final def untraced: ZIO[R, E, A] = tracingStatus(TracingStatus.Untraced)

  /**
   * Enables ZIO tracing for this effect. Because this is the default, this
   * operation only has an additional meaning if the effect is located within
   * an `untraced` section, or the current fiber has been spawned by a parent
   * inside an `untraced` section.
   */
  final def traced: ZIO[R, E, A] = tracingStatus(TracingStatus.Traced)

  /**
   * Toggles ZIO tracing support for this effect. If `true` is used, then the
   * effect will accumulate traces, while if `false` is used, then tracing
   * is disabled. These changes are compositional, so they only affect regions
   * of the effect.
   */
  final def tracingStatus(flag: TracingStatus): ZIO[R, E, A] = new ZIO.TracingStatus(self, flag)

  /**
   * Applies `f` if the predicate fails.
   */
  final def filterOrElse[R1 <: R, E1 >: E, A1 >: A](p: A => Boolean)(f: A => ZIO[R1, E1, A1]): ZIO[R1, E1, A1] =
    self.flatMap {
      case v if !p(v) => f(v)
      case v          => ZIO.succeed(v)
    }

  /**
   * Supplies `zio` if the predicate fails.
   */
  final def filterOrElse_[R1 <: R, E1 >: E, A1 >: A](p: A => Boolean)(zio: => ZIO[R1, E1, A1]): ZIO[R1, E1, A1] =
    filterOrElse[R1, E1, A1](p)(_ => zio)

  /**
   * Fails with `e` if the predicate fails.
   */
  final def filterOrFail[E1 >: E](p: A => Boolean)(e: => E1): ZIO[R, E1, A] =
    filterOrElse_[R, E1, A](p)(ZIO.fail(e))

  /**
   * Fail with `e` if the supplied `PartialFunction` does not match, otherwise
   * succeed with the returned value.
   */
  final def collect[E1 >: E, B](e: E1)(pf: PartialFunction[A, B]): ZIO[R, E1, B] =
    collectM(e)(pf.andThen(ZIO.succeed))

  /**
   * Fail with `e` if the supplied `PartialFunction` does not match, otherwise
   * continue with the returned value.
   */
  final def collectM[R1 <: R, E1 >: E, B](e: E1)(pf: PartialFunction[A, ZIO[R1, E1, B]]): ZIO[R1, E1, B] =
    self.flatMap { v =>
      pf.applyOrElse[A, ZIO[R1, E1, B]](v, _ => ZIO.fail(e))
    }

  /**
   * Fail with the returned value if the `PartialFunction` matches, otherwise
   * continue with our held value.
   */
  final def reject[R1 <: R, E1 >: E](pf: PartialFunction[A, E1]): ZIO[R1, E1, A] =
    rejectM(pf.andThen(ZIO.fail))

  /**
   * Continue with the returned computation if the `PartialFunction` matches,
   * translating the successful match into a failure, otherwise continue with
   * our held value.
   */
  final def rejectM[R1 <: R, E1 >: E](pf: PartialFunction[A, ZIO[R1, E1, E1]]): ZIO[R1, E1, A] =
    self.flatMap { v =>
      pf.andThen[ZIO[R1, E1, A]](_.flatMap(ZIO.fail))
        .applyOrElse[A, ZIO[R1, E1, A]](v, ZIO.succeed)
    }

  /**
   * An integer that identifies the term in the `ZIO` sum type to which this
   * instance belongs (e.g. `IO.Tags.Succeed`).
   */
  def tag: Int

  /**
   * Exposes all parallel errors in a single call
   *
   */
  final def parallelErrors[E1 >: E]: ZIO[R, ::[E1], A] =
    self.foldCauseM(
      cause =>
        cause.failures match {
          case Nil            => ZIO.halt(cause.asInstanceOf[Cause[Nothing]])
          case ::(head, tail) => ZIO.fail(::(head, tail))
        },
      ZIO.succeed
    )
}

private[zio] trait ZIOFunctions extends Serializable {
  // ALL error types in this trait must be a subtype of `UpperE`.
  type UpperE
  // ALL environment types in this trait must be a supertype of `LowerR`.
  type LowerR

  /**
   * Returns an effect that models failure with the specified error.
   * The moral equivalent of `throw` for pure code.
   */
  final def fail[E <: UpperE](error: E): IO[E, Nothing] = haltWith(trace => Cause.Traced(Cause.Fail(error), trace()))

  /**
   * Returns an effect that models failure with the specified `Cause`.
   */
  final def halt[E <: UpperE](cause: Cause[E]): IO[E, Nothing] = new ZIO.Fail(_ => cause)

  /**
   * Returns an effect that models failure with the specified `Cause`.
   *
   * This version takes in a lazily-evaluated trace that can be attached to the `Cause`
   * via `Cause.Traced`.
   */
  final def haltWith[E <: UpperE](function: (() => ZTrace) => Cause[E]): IO[E, Nothing] = new ZIO.Fail(function)

  /**
   * Returns an effect that models success with the specified strictly-
   * evaluated value.
   */
  final def succeed[A](a: A): UIO[A] = new ZIO.Succeed(a)

  /**
   * Returns an effect that models success with the specified lazily-evaluated
   * value. This method should not be used to capture effects. See
   * `[[ZIO.effectTotal]]` for capturing total effects, and `[[ZIO.effect]]` for capturing
   * partial effects.
   */
  final def succeedLazy[A](a: => A): UIO[A] = new ZIO.EffectTotal(() => a)

  /**
   * Accesses the whole environment of the effect.
   */
  final def environment[R >: LowerR]: ZIO[R, Nothing, R] = access(r => r)

  /**
   * Accesses the environment of the effect.
   * {{{
   * val portNumber = effect.access(_.config.portNumber)
   * }}}
   */
  final def access[R >: LowerR]: ZIO.AccessPartiallyApplied[R] =
    new ZIO.AccessPartiallyApplied[R]

  /**
   * Effectfully accesses the environment of the effect.
   */
  final def accessM[R >: LowerR]: ZIO.AccessMPartiallyApplied[R] =
    new ZIO.AccessMPartiallyApplied[R]

  /**
   * Given an environment `R`, returns a function that can supply the
   * environment to programs that require it, removing their need for any
   * specific environment.
   *
   * This is similar to dependency injection, and the `provide` function can be
   * thought of as `inject`.
   */
  final def provide[R >: LowerR, E <: UpperE, A](r: R): ZIO[R, E, A] => IO[E, A] =
    (zio: ZIO[R, E, A]) => new ZIO.Provide(r, zio)

  /**
   * Returns an effect that accesses the runtime, which can be used to
   * (unsafely) execute tasks. This is useful for integration with
   * non-functional code that must call back into functional code.
   */
  final def runtime[R >: LowerR]: ZIO[R, Nothing, Runtime[R]] =
    for {
      environment <- environment[R]
      platform    <- suspendWith(ZIO.succeed)
    } yield Runtime(environment, platform)

  /**
   * Returns an effect that is interrupted.
   */
  final val interrupt: UIO[Nothing] = haltWith(trace => Cause.Traced(Cause.Interrupt, trace()))

  /**
   * Returns a effect that will never produce anything. The moral
   * equivalent of `while(true) {}`, only without the wasted CPU cycles.
   */
  final val never: UIO[Nothing] = effectAsync[Any, Nothing, Nothing](_ => ())

  /**
   * Returns an effect that dies with the specified `Throwable`.
   * This method can be used for terminating a fiber because a defect has been
   * detected in the code.
   */
  final def die(t: Throwable): UIO[Nothing] = haltWith(trace => Cause.Traced(Cause.Die(t), trace()))

  /**
   * Returns an effect that dies with a [[java.lang.RuntimeException]] having the
   * specified text message. This method can be used for terminating a fiber
   * because a defect has been detected in the code.
   */
  final def dieMessage(message: String): UIO[Nothing] = die(new RuntimeException(message))

  /**
   * Imports a total synchronous effect into a pure `ZIO` value.
   * The effect must not throw any exceptions. If you wonder if the effect
   * throws exceptions, then do not use this method, use [[Task.effect]],
   * [[IO.effect]], or [[ZIO.effect]].
   *
   * {{{
   * val nanoTime: UIO[Long] = IO.effectTotal(System.nanoTime())
   * }}}
   */
  final def effectTotal[A](effect: => A): UIO[A] = new ZIO.EffectTotal(() => effect)

  /**
   * Returns an effect that yields to the runtime system, starting on a fresh
   * stack. Manual use of this method can improve fairness, at the cost of
   * overhead.
   */
  final val yieldNow: UIO[Unit] = ZIO.Yield

  /**
   * Returns an effect that forks all of the specified values, and returns a
   * composite fiber that produces a list of their results, in order.
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
   * Returns an effect that forks all of the specified values, and returns a
   * composite fiber that produces unit. This version is faster than [[forkAll]]
   * in cases where the results of the forked fibers are not needed.
   */
  final def forkAll_[R >: LowerR, E <: UpperE, A](as: Iterable[ZIO[R, E, A]]): ZIO[R, Nothing, Unit] =
    as.foldRight[ZIO[R, Nothing, Unit]](ZIO.unit)(_.fork *> _)

  /**
   * Returns an effect from a [[zio.Exit]] value.
   */
  final def done[E <: UpperE, A](r: Exit[E, A]): IO[E, A] = r match {
    case Exit.Success(b)     => succeed(b)
    case Exit.Failure(cause) => halt(cause)
  }

  /**
   * Enables supervision for this effect. This will cause fibers forked by
   * this effect to be tracked and will enable their inspection via [[ZIO.children]].
   */
  final def supervised[R >: LowerR, E <: UpperE, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    superviseStatus[R, E, A](SuperviseStatus.Supervised)(zio)

  /**
   * Disables supervision for this effect. This will cause fibers forked by
   * this effect to not be tracked or appear in the list returned by [[ZIO.children]].
   */
  final def unsupervised[R >: LowerR, E <: UpperE, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    superviseStatus[R, E, A](SuperviseStatus.Unsupervised)(zio)

  /**
   * Returns a new effect that has the same effects as this one, but with the
   * supervision status changed as specified.
   */
  final def superviseStatus[R >: LowerR, E <: UpperE, A](status: SuperviseStatus)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    new ZIO.SuperviseStatus(zio, status)

  /**
   * Checks supervision status.
   */
  final def checkSupervised[R >: LowerR, E <: UpperE, A](f: SuperviseStatus => ZIO[R, E, A]): ZIO[R, E, A] =
    descriptorWith(d => f(d.superviseStatus))

  /**
   * Returns a new effect that ensures that any fibers that are forked by
   * the effect are interrupted when this effect completes.
   */
  final def interruptChildren[R >: LowerR, E <: UpperE, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    handleChildrenWith(zio)(Fiber.interruptAll)

  /**
   * Returns an effect that supervises the specified effect, ensuring that all
   * fibers that it forks are passed to the specified supervisor as soon as the
   * supervised effect completes.
   */
  final def handleChildrenWith[R >: LowerR, E <: UpperE, A](
    zio: ZIO[R, E, A]
  )(supervisor: IndexedSeq[Fiber[_, _]] => ZIO[R, Nothing, _]): ZIO[R, E, A] =
    zio.ensuring(children.flatMap(supervisor(_))).supervised

  /**
   * Returns an effect that first executes the outer effect, and then executes
   * the inner effect, returning the value from the inner effect, and effectively
   * flattening a nested effect.
   */
  final def flatten[R >: LowerR, E <: UpperE, A](zio: ZIO[R, E, ZIO[R, E, A]]): ZIO[R, E, A] =
    zio.flatMap(ZIO.identityFn)

  /**
   * Returns a lazily constructed effect, whose construction may itself require
   * effects. This is a shortcut for `flatten(effectTotal(io))`.
   */
  final def suspend[R >: LowerR, E <: UpperE, A](io: => ZIO[R, E, A]): ZIO[R, E, A] =
    suspendWith(_ => io)

  /**
   * Returns a lazily constructed effect, whose construction may itself require
   * effects. This is a shortcut for `flatten(effectTotal(io))`.
   */
  final def suspendWith[R >: LowerR, E <: UpperE, A](io: Platform => ZIO[R, E, A]): ZIO[R, E, A] =
    new ZIO.SuspendWith(io)

  /**
   * Returns an effect that will execute the specified effect fully on the
   * provided executor, before returning to the default executor.
   */
  final def lock[R >: LowerR, E <: UpperE, A](executor: Executor)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    new ZIO.Lock(executor, zio)

  /**
   * Imports an asynchronous effect into a pure `ZIO` value. See `effectAsyncMaybe` for
   * the more expressive variant of this function that can return a value
   * synchronously.
   */
  final def effectAsync[R >: LowerR, E <: UpperE, A](register: (ZIO[R, E, A] => Unit) => Unit): ZIO[R, E, A] =
    effectAsyncMaybe((callback: ZIO[R, E, A] => Unit) => {
      register(callback)

      None
    })

  /**
   * Imports an asynchronous effect into a pure `ZIO` value, possibly returning
   * the value synchronously.
   */
  final def effectAsyncMaybe[R >: LowerR, E <: UpperE, A](
    register: (ZIO[R, E, A] => Unit) => Option[ZIO[R, E, A]]
  ): ZIO[R, E, A] =
    new ZIO.EffectAsync(register)

  /**
   * Imports an asynchronous effect into a pure `ZIO` value. This formulation is
   * necessary when the effect is itself expressed in terms of `ZIO`.
   */
  final def effectAsyncM[R >: LowerR, E <: UpperE, A](
    register: (ZIO[R, E, A] => Unit) => ZIO[R, E, _]
  ): ZIO[R, E, A] =
    for {
      p <- Promise.make[E, A]
      r <- ZIO.runtime[R]
      a <- ZIO.uninterruptibleMask { restore =>
            restore(
              register(k => r.unsafeRunAsync_(k.to(p)))
                .catchAll(p.fail)
            ).fork.flatMap { f =>
              restore(p.await).onInterrupt(f.interrupt)
            }
          }
    } yield a

  /**
   * Imports an asynchronous effect into a pure `IO` value. The effect has the
   * option of returning the value synchronously, which is useful in cases
   * where it cannot be determined if the effect is synchronous or asynchronous
   * until the effect is actually executed. The effect also has the option of
   * returning a canceler, which will be used by the runtime to cancel the
   * asynchronous effect if the fiber executing the effect is interrupted.
   */
  final def effectAsyncInterrupt[R >: LowerR, E <: UpperE, A](
    register: (ZIO[R, E, A] => Unit) => Either[Canceler, ZIO[R, E, A]]
  ): ZIO[R, E, A] = {
    import java.util.concurrent.atomic.AtomicBoolean
    import internal.OneShot

    effectTotal((new AtomicBoolean(false), OneShot.make[UIO[Any]])).flatMap {
      case (started, cancel) =>
        flatten {
          effectAsyncMaybe((k: UIO[ZIO[R, E, A]] => Unit) => {
            started.set(true)

            try register(io => k(ZIO.succeed(io))) match {
              case Left(canceler) =>
                cancel.set(canceler)
                None
              case Right(io) => Some(ZIO.succeed(io))
            } finally if (!cancel.isSet) cancel.set(ZIO.unit)
          })
        }.onInterrupt(flatten(effectTotal(if (started.get) cancel.get() else ZIO.unit)))
    }
  }

  /**
   * Submerges the error case of an `Either` into the `ZIO`. The inverse
   * operation of `IO.either`.
   */
  final def absolve[R >: LowerR, E <: UpperE, A](v: ZIO[R, E, Either[E, A]]): ZIO[R, E, A] =
    v.flatMap(fromEither(_))

  /**
   * The inverse operation `IO.sandboxed`
   *
   * Terminates with exceptions on the `Left` side of the `Either` error, if it
   * exists. Otherwise extracts the contained `IO[E, A]`
   */
  final def unsandbox[R >: LowerR, E <: UpperE, A](v: ZIO[R, Cause[E], A]): ZIO[R, E, A] = v.catchAll[R, E, A](halt)

  /**
   * Returns the identity effectful function, which performs no effects
   */
  final def identity[R >: LowerR]: ZIO[R, Nothing, R] = fromFunction[R, R](ZIO.identityFn[R])

  /**
   * Returns an effectful function that merely swaps the elements in a `Tuple2`.
   */
  final def swap[R >: LowerR, E <: UpperE, A, B](implicit ev: R <:< (A, B)): ZIO[R, E, (B, A)] =
    fromFunction[R, (B, A)](_.swap)

  /**
   * Returns an effectful function that extracts out the first element of a
   * tuple.
   */
  final def _1[R >: LowerR, E <: UpperE, A, B](implicit ev: R <:< (A, B)): ZIO[R, E, A] = fromFunction[R, A](_._1)

  /**
   * Returns an effectful function that extracts out the second element of a
   * tuple.
   */
  final def _2[R >: LowerR, E <: UpperE, A, B](implicit ev: R <:< (A, B)): ZIO[R, E, B] = fromFunction[R, B](_._2)

  /**
   * Lifts a function `R => A` into a `ZIO[R, Nothing, A]`.
   */
  final def fromFunction[R >: LowerR, A](f: R => A): ZIO[R, Nothing, A] =
    environment[R].map(f)

  /**
   * Lifts an effectful function whose effect requires no environment into
   * an effect that requires the input to the function.
   */
  final def fromFunctionM[R >: LowerR, E, A](f: R => IO[E, A]): ZIO[R, E, A] =
    environment[R].flatMap(f)

  /**
   * Lifts an `Either` into a `ZIO` value.
   */
  final def fromEither[E <: UpperE, A](v: => Either[E, A]): IO[E, A] =
    effectTotal(v).flatMap(_.fold(fail, succeed))

  /**
   * Creates a `ZIO` value that represents the exit value of the specified
   * fiber.
   */
  final def fromFiber[E <: UpperE, A](fiber: => Fiber[E, A]): IO[E, A] =
    effectTotal(fiber).flatMap(_.join)

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
   * When this effect represents acquisition of a resource (for example,
   * opening a file, launching a thread, etc.), `bracket` can be used to ensure
   * the acquisition is not interrupted and the resource is always released.
   *
   * The function does two things:
   *
   * 1. Ensures this effect, which acquires the resource, will not be
   * interrupted. Of course, acquisition may fail for internal reasons (an
   * uncaught exception).
   * 2. Ensures the `release` effect will not be interrupted, and will be
   * executed so long as this effect successfully acquires the resource.
   *
   * In between acquisition and release of the resource, the `use` effect is
   * executed.
   *
   * If the `release` effect fails, then the entire effect will fail even
   * if the `use` effect succeeds. If this fail-fast behavior is not desired,
   * errors produced by the `release` effect can be caught and ignored.
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
  final def bracket[R >: LowerR, E <: UpperE, A](acquire: ZIO[R, E, A]): ZIO.BracketAcquire[R, E, A] =
    new ZIO.BracketAcquire[R, E, A](acquire)

  /**
   * Uncurried version. Doesn't offer curried syntax and have worse type-inference
   * characteristics, but guarantees no extra allocations of intermediate
   * [[zio.ZIO.BracketAcquire]] and [[zio.ZIO.BracketRelease]] objects.
   */
  final def bracket[R >: LowerR, E <: UpperE, A, B](
    acquire: ZIO[R, E, A],
    release: A => ZIO[R, Nothing, _],
    use: A => ZIO[R, E, B]
  ): ZIO[R, E, B] =
    bracketExit(acquire, new ZIO.BracketReleaseFn(release): (A, Exit[E, B]) => ZIO[R, Nothing, _], use)

  /**
   * Acquires a resource, uses the resource, and then releases the resource.
   * Neither the acquisition nor the release will be interrupted, and the
   * resource is guaranteed to be released, so long as the `acquire` effect
   * succeeds. If `use` fails, then after release, the returned effect will fail
   * with the same error.
   */
  final def bracketExit[R >: LowerR, E <: UpperE, A](acquire: ZIO[R, E, A]): ZIO.BracketExitAcquire[R, E, A] =
    new ZIO.BracketExitAcquire(acquire)

  /**
   * Uncurried version. Doesn't offer curried syntax and have worse type-inference
   * characteristics, but guarantees no extra allocations of intermediate
   * [[zio.ZIO.BracketExitAcquire]] and [[zio.ZIO.BracketExitRelease]] objects.
   */
  final def bracketExit[R >: LowerR, E <: UpperE, A, B](
    acquire: ZIO[R, E, A],
    release: (A, Exit[E, B]) => ZIO[R, Nothing, _],
    use: A => ZIO[R, E, B]
  ): ZIO[R, E, B] =
    ZIO.uninterruptibleMask[R, E, B](
      restore =>
        acquire.flatMap(ZIOFn(traceAs = use) { a =>
          restore(use(a)).run.flatMap(ZIOFn(traceAs = release) { e =>
            release(a, e).foldCauseM(
              cause2 => ZIO.halt(e.fold(_ ++ cause2, _ => cause2)),
              _ => ZIO.done(e)
            )
          })
        })
    )

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns the results in a new `List[B]`.
   *
   * For a parallel version of this method, see `foreachPar`.
   */
  final def foreach[R >: LowerR, E <: UpperE, A, B](in: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    in.foldRight[ZIO[R, E, List[B]]](effectTotal(Nil)) { (a, io) =>
      f(a).zipWith(io)((b, bs) => b :: bs)
    }

  /**
   * Applies the function `f` to each element of the `Iterable[A]` in parallel,
   * and returns the results in a new `List[B]`.
   *
   * For a sequential version of this method, see `foreach`.
   */
  final def foreachPar[R >: LowerR, E <: UpperE, A, B](as: Iterable[A])(fn: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    as.foldRight[ZIO[R, E, List[B]]](effectTotal(Nil)) { (a, io) =>
        fn(a).zipWithPar(io)((b, bs) => b :: bs)
      }
      .refailWithTrace

  /**
   * Applies the function `f` to each element of the `Iterable[A]` in parallel,
   * and returns the results in a new `List[B]`.
   *
   * Unlike `foreachPar`, this method will use at most up to `n` fibers.
   */
  final def foreachParN[R >: LowerR, E <: UpperE, A, B](
    n: Long
  )(as: Iterable[A])(fn: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    (for {
      semaphore <- Semaphore.make(n)
      bs <- foreachPar[R, E, A, B](as) { a =>
             semaphore.withPermit(fn(a))
           }
    } yield bs).refailWithTrace

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and runs
   * produced effects sequentially.
   *
   * Equivalent to `foreach(as)(f).void`, but without the cost of building
   * the list of results.
   */
  final def foreach_[R >: LowerR, E <: UpperE, A](as: Iterable[A])(f: A => ZIO[R, E, _]): ZIO[R, E, Unit] =
    ZIO.succeedLazy(as.iterator).flatMap { i =>
      def loop: ZIO[R, E, Unit] =
        if (i.hasNext) f(i.next) *> loop
        else ZIO.unit
      loop
    }

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and runs
   * produced effects in parallel, discarding the results.
   *
   * For a sequential version of this method, see `foreach_`.
   */
  final def foreachPar_[R >: LowerR, E <: UpperE, A](as: Iterable[A])(f: A => ZIO[R, E, _]): ZIO[R, E, Unit] =
    ZIO
      .succeedLazy(as.iterator)
      .flatMap { i =>
        def loop(a: A): ZIO[R, E, Unit] =
          if (i.hasNext) f(a).zipWithPar(loop(i.next))((_, _) => ())
          else f(a).unit
        if (i.hasNext) loop(i.next)
        else ZIO.unit
      }
      .refailWithTrace

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and runs
   * produced effects in parallel, discarding the results.
   *
   * Unlike `foreachPar_`, this method will use at most up to `n` fibers.
   */
  final def foreachParN_[R >: LowerR, E <: UpperE, A](
    n: Long
  )(as: Iterable[A])(f: A => ZIO[R, E, _]): ZIO[R, E, Unit] =
    Semaphore
      .make(n)
      .flatMap { semaphore =>
        ZIO.foreachPar_(as) { a =>
          semaphore.withPermit(f(a))
        }
      }
      .refailWithTrace

  /**
   * Evaluate each effect in the structure from left to right, and collect
   * the results. For a parallel version, see `collectAllPar`.
   */
  final def collectAll[R >: LowerR, E <: UpperE, A](in: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    foreach[R, E, ZIO[R, E, A], A](in)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and collect
   * the results. For a sequential version, see `collectAll`.
   */
  final def collectAllPar[R >: LowerR, E <: UpperE, A](as: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    foreachPar[R, E, ZIO[R, E, A], A](as)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and collect
   * the results. For a sequential version, see `collectAll`.
   *
   * Unlike `foreachAllPar`, this method will use at most `n` fibers.
   */
  final def collectAllParN[R >: LowerR, E <: UpperE, A](n: Long)(as: Iterable[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    foreachParN[R, E, ZIO[R, E, A], A](n)(as)(ZIO.identityFn)

  /**
   * Races an `IO[E, A]` against zero or more other effects. Yields either the
   * first success or the last failure.
   */
  final def raceAll[R >: LowerR, R1 >: LowerR <: R, E <: UpperE, A](
    zio: ZIO[R, E, A],
    ios: Iterable[ZIO[R1, E, A]]
  ): ZIO[R1, E, A] =
    ios.foldLeft[ZIO[R1, E, A]](zio)(_ race _).refailWithTrace

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working sequentially.
   */
  final def reduceAll[R >: LowerR, R1 >: LowerR <: R, E <: UpperE, A](a: ZIO[R, E, A], as: Iterable[ZIO[R1, E, A]])(
    f: (A, A) => A
  ): ZIO[R1, E, A] =
    as.foldLeft[ZIO[R1, E, A]](a) { (l, r) =>
      l.zip(r).map(f.tupled)
    }

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working in parallel.
   */
  final def reduceAllPar[R >: LowerR, R1 >: LowerR <: R, E <: UpperE, A](a: ZIO[R, E, A], as: Iterable[ZIO[R1, E, A]])(
    f: (A, A) => A
  ): ZIO[R1, E, A] =
    as.foldLeft[ZIO[R1, E, A]](a) { (l, r) =>
      l.zipPar(r).map(f.tupled)
    }

  /**
   * Merges an `Iterable[IO]` to a single IO, working sequentially.
   */
  final def mergeAll[R >: LowerR, E <: UpperE, A, B](
    in: Iterable[ZIO[R, E, A]]
  )(zero: B)(f: (B, A) => B): ZIO[R, E, B] =
    in.foldLeft[ZIO[R, E, B]](succeedLazy[B](zero))((acc, a) => acc.zip(a).map(f.tupled))

  /**
   * Merges an `Iterable[IO]` to a single IO, working in parallel.
   */
  final def mergeAllPar[R >: LowerR, E <: UpperE, A, B](
    in: Iterable[ZIO[R, E, A]]
  )(zero: B)(f: (B, A) => B): ZIO[R, E, B] =
    in.foldLeft[ZIO[R, E, B]](succeedLazy[B](zero))((acc, a) => acc.zipPar(a).map(f.tupled)).refailWithTrace

  /**
   * Strictly-evaluated unit lifted into the `ZIO` monad.
   */
  final val unit: ZIO[Any, Nothing, Unit] = succeed(())

  /**
   * The moral equivalent of `if (p) exp`
   */
  final def when[R >: LowerR, E <: UpperE](b: Boolean)(zio: ZIO[R, E, _]): ZIO[R, E, Unit] =
    if (b)
      zio.const(())
    else unit

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  final def whenM[R >: LowerR, E <: UpperE](b: ZIO[R, E, Boolean])(zio: ZIO[R, E, _]): ZIO[R, E, Unit] =
    b.flatMap(b => if (b) zio.const(()) else unit)

  /**
   * Folds an `Iterable[A]` using an effectful function `f`, working sequentially.
   */
  final def foldLeft[R >: LowerR, E <: UpperE, S, A](
    in: Iterable[A]
  )(zero: S)(f: (S, A) => ZIO[R, E, S]): ZIO[R, E, S] =
    in.foldLeft(IO.succeed(zero): ZIO[R, E, S]) { (acc, el) =>
      acc.flatMap(f(_, el))
    }

  /**
   * Returns information about the current fiber, such as its identity.
   */
  final def descriptor: UIO[Fiber.Descriptor] = descriptorWith(succeed)

  /**
   * Constructs an effect based on information about the current fiber, such as
   * its identity.
   */
  final def descriptorWith[R >: LowerR, E <: UpperE, A](f: Fiber.Descriptor => ZIO[R, E, A]): ZIO[R, E, A] =
    new ZIO.Descriptor(f)

  /**
   * Checks the interrupt status, and produces the effect returned by the
   * specified callback.
   */
  final def checkInterruptible[R >: LowerR, E <: UpperE, A](f: InterruptStatus => ZIO[R, E, A]): ZIO[R, E, A] =
    new ZIO.CheckInterrupt(f)

  /**
   * Checks the ZIO Tracing status, and produces the effect returned by the
   * specified callback.
   */
  final def checkTraced[R >: LowerR, E <: UpperE, A](f: TracingStatus => ZIO[R, E, A]): ZIO[R, E, A] =
    new ZIO.CheckTracing(f)

  /**
   * Makes an explicit check to see if the fiber has been interrupted, and if
   * so, performs self-interruption.
   */
  final def allowInterrupt: UIO[Unit] =
    descriptorWith(d => if (d.interrupted) interrupt else unit)

  /**
   * Makes the effect uninterruptible, but passes it a restore function that
   * can be used to restore the inherited interruptibility from whatever region
   * the effect is composed into.
   */
  final def uninterruptibleMask[R >: LowerR, E <: UpperE, A](
    k: ZIO.InterruptStatusRestore => ZIO[R, E, A]
  ): ZIO[R, E, A] =
    checkInterruptible(flag => k(new ZIO.InterruptStatusRestore(flag)).uninterruptible)

  /**
   * Makes the effect interruptible, but passes it a restore function that
   * can be used to restore the inherited interruptibility from whatever region
   * the effect is composed into.
   */
  final def interruptibleMask[R >: LowerR, E <: UpperE, A](
    k: ZIO.InterruptStatusRestore => ZIO[R, E, A]
  ): ZIO[R, E, A] =
    checkInterruptible(flag => k(new ZIO.InterruptStatusRestore(flag)).interruptible)

  /**
   * Prefix form of `ZIO#uninterruptible`.
   */
  final def uninterruptible[R >: LowerR, E <: UpperE, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.uninterruptible

  /**
   * Prefix form of `ZIO#interruptible`.
   */
  final def interruptible[R >: LowerR, E <: UpperE, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.interruptible

  /**
   * Prefix form of `ZIO#untraced`.
   */
  final def untraced[R >: LowerR, E <: UpperE, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.untraced

  /**
   * Prefix form of `ZIO#traced`.
   */
  final def traced[R >: LowerR, E <: UpperE, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.traced

  /**
   * Capture ZIO trace at the current point
   * */
  final def trace: UIO[ZTrace] = ZIO.Trace

  /**
   * Provides access to the list of child fibers supervised by this fiber.
   *
   * '''Note:''' supervision must be enabled (via [[ZIO#supervised]]) on the
   * current fiber for this operation to return non-empty lists.
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = descriptorWith(_.children)

  /**
   * Acquires a resource, uses the resource, and then releases the resource.
   * However, unlike `bracket`, the separation of these phases allows
   * the acquisition to be interruptible.
   *
   * Useful for concurrent data structures and other cases where the
   * 'deallocator' can tell if the allocation succeeded or not just by
   * inspecting internal / external state.
   */
  final def reserve[R, E, A, B](reservation: ZIO[R, E, Reservation[R, E, A]])(use: A => ZIO[R, E, B]): ZIO[R, E, B] =
    ZManaged(reservation).use(use)

}

private[zio] trait ZIO_E_Any extends ZIO_E_Throwable {
  type UpperE = Any

  /**
   * Lifts an `Option` into a `ZIO`.
   */
  final def fromOption[A](v: => Option[A]): IO[Unit, A] =
    effectTotal(v).flatMap(_.fold[IO[Unit, A]](fail(()))(succeed))
}

private[zio] trait ZIO_E_Throwable extends ZIOFunctions {
  type UpperE >: Throwable

  /**
   *
   * Imports a synchronous effect into a pure `ZIO` value, translating any
   * throwables into a `Throwable` failure in the returned value.
   *
   * {{{
   * def putStrLn(line: String): Task[Unit] = Task.effect(println(line))
   * }}}
   */
  final def effect[A](effect: => A): Task[A] = new ZIO.EffectPartial(() => effect)

  /**
   * Lifts a `Try` into a `ZIO`.
   */
  final def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    effect(value).flatMap {
      case scala.util.Success(v) => ZIO.succeed(v)
      case scala.util.Failure(t) => ZIO.fail(t)
    }

  /**
   * Imports a function that creates a [[scala.concurrent.Future]] from an
   * [[scala.concurrent.ExecutionContext]] into a `ZIO`.
   */
  final def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    Task.descriptorWith { d =>
      val ec = d.executor.asEC
      val f  = make(ec)
      f.value
        .fold(
          Task.effectAsync { (cb: Task[A] => Unit) =>
            f.onComplete {
              case Success(a) => cb(Task.succeed(a))
              case Failure(t) => cb(Task.fail(t))
            }(ec)
          }
        )(Task.fromTry(_))

    }
}
private[zio] trait ZIO_R_Any extends ZIO_E_Any {
  type LowerR = Nothing

  /**
   * Sleeps for the specified duration. This method is asynchronous, and does
   * not actually block the fiber.
   */
  final def sleep(duration: Duration): ZIO[Clock, Nothing, Unit] =
    clock.sleep(duration)
}

object IO extends ZIO_E_Any {
  type LowerR = Any

  def apply[A](a: => A): Task[A] = effect(a)
}
object Task extends ZIO_E_Throwable {
  type UpperE = Throwable
  type LowerR = Any

  def apply[A](a: => A): Task[A] = effect(a)
}
object TaskR extends ZIO_E_Throwable {
  type UpperE = Throwable
  type LowerR = Nothing

  def apply[A](a: => A): Task[A] = effect(a)
}
object UIO extends ZIOFunctions {
  type UpperE = Nothing
  type LowerR = Any

  def apply[A](a: => A): UIO[A] = effectTotal(a)
}

object ZIO extends ZIO_R_Any {
  def apply[A](a: => A): Task[A] = effect(a)

  private val _IdentityFn: Any => Any    = (a: Any) => a
  private[zio] def identityFn[A]: A => A = _IdentityFn.asInstanceOf[A => A]

  implicit final class ZIOInvariant[R, E, A](private val self: ZIO[R, E, A]) extends AnyVal {
    final def bracket: ZIO.BracketAcquire[R, E, A] =
      new ZIO.BracketAcquire(self)

    final def bracketExit: ZIO.BracketExitAcquire[R, E, A] =
      new ZIO.BracketExitAcquire(self)
  }

  implicit final class ZIOAutocloseableOps[R, E, A <: AutoCloseable](private val io: ZIO[R, E, A]) extends AnyVal {

    /**
     * Like `bracket`, safely wraps a use and release of a resource.
     * This resource will get automatically closed, because it implements `AutoCloseable`.
     */
    def bracketAuto[R1 <: R, E1 >: E, B](use: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
      io.bracket(a => UIO(a.close()))(use)

    /**
     * Converts this ZIO value to a ZManaged value. See [[ZManaged.fromAutoCloseable]].
     */
    def toManaged: ZManaged[R, E, A] = ZManaged.fromAutoCloseable(io)
  }

  final class InterruptStatusRestore(private val flag: zio.InterruptStatus) extends AnyVal {
    def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
      zio.interruptStatus(flag)
  }

  final class TimeoutTo[R, E, A, B](self: ZIO[R, E, A], b: B) {
    def apply[B1 >: B](f: A => B1)(duration: Duration): ZIO[R with Clock, E, B1] =
      self
        .map(f)
        .sandboxWith[R with Clock, E, B1](
          io => ZIO.absolve(io.either race ZIO.succeedRight(b).delay(duration))
        )
  }

  final class BracketAcquire_[-R, +E](private val acquire: ZIO[R, E, _]) extends AnyVal {
    def apply[R1 <: R](release: ZIO[R1, Nothing, _]): BracketRelease_[R1, E] =
      new BracketRelease_(acquire, release)
  }
  final class BracketRelease_[-R, +E](acquire: ZIO[R, E, _], release: ZIO[R, Nothing, _]) {
    def apply[R1 <: R, E1 >: E, B](use: ZIO[R1, E1, B]): ZIO[R1, E1, B] =
      ZIO.bracket(acquire, (_: Any) => release, (_: Any) => use)
  }

  final class BracketAcquire[-R, +E, +A](private val acquire: ZIO[R, E, A]) extends AnyVal {
    def apply[R1 <: R](release: A => ZIO[R1, Nothing, _]): BracketRelease[R1, E, A] =
      new BracketRelease[R1, E, A](acquire, release)
  }
  class BracketRelease[-R, +E, +A](acquire: ZIO[R, E, A], release: A => ZIO[R, Nothing, _]) {
    def apply[R1 <: R, E1 >: E, B](use: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
      ZIO.bracket(acquire, release, use)
  }

  final class BracketExitAcquire[-R, +E, +A](private val acquire: ZIO[R, E, A]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, B](
      release: (A, Exit[E1, B]) => ZIO[R1, Nothing, _]
    ): BracketExitRelease[R1, E, E1, A, B] =
      new BracketExitRelease(acquire, release)
  }
  class BracketExitRelease[-R, +E, E1, +A, B](
    acquire: ZIO[R, E, A],
    release: (A, Exit[E1, B]) => ZIO[R, Nothing, _]
  ) {
    def apply[R1 <: R, E2 >: E <: E1, B1 <: B](use: A => ZIO[R1, E2, B1]): ZIO[R1, E2, B1] =
      ZIO.bracketExit(acquire, release, use)
  }

  final class AccessPartiallyApplied[R >: LowerR](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](f: R => A): ZIO[R, Nothing, A] =
      new ZIO.Read(r => succeed(f(r)))
  }

  final class AccessMPartiallyApplied[R >: LowerR](private val dummy: Boolean = true) extends AnyVal {
    def apply[E <: UpperE, A](f: R => ZIO[R, E, A]): ZIO[R, E, A] =
      new ZIO.Read(f)
  }

  @inline
  private final def succeedLeft[E, A]: E => UIO[Either[E, A]] =
    _succeedLeft.asInstanceOf[E => UIO[Either[E, A]]]

  private val _succeedLeft: Any => IO[Any, Either[Any, Any]] =
    e2 => succeed[Either[Any, Any]](Left(e2))

  @inline
  private final def succeedRight[E, A]: A => UIO[Either[E, A]] =
    _succeedRight.asInstanceOf[A => UIO[Either[E, A]]]

  private val _succeedRight: Any => IO[Any, Either[Any, Any]] =
    a => succeed[Either[Any, Any]](Right(a))

  final class ZipLeftFn[R, E, A, B](override val underlying: () => ZIO[R, E, A]) extends ZIOFn1[B, ZIO[R, E, B]] {
    def apply(a: B): ZIO[R, E, B] =
      underlying().const(a)
  }

  final class ZipRightFn[R, E, A, B](override val underlying: () => ZIO[R, E, B]) extends ZIOFn1[A, ZIO[R, E, B]] {
    def apply(a: A): ZIO[R, E, B] = {
      val _ = a
      underlying()
    }
  }

  final class TapFn[R, E, A](override val underlying: A => ZIO[R, E, _]) extends ZIOFn1[A, ZIO[R, E, A]] {
    def apply(a: A): ZIO[R, E, A] =
      underlying(a).const(a)
  }

  final class MapFn[R, E, A, B](override val underlying: A => B) extends ZIOFn1[A, ZIO[R, E, B]] {
    def apply(a: A): ZIO[R, E, B] =
      new ZIO.Succeed(underlying(a))
  }

  final class ConstFn[R, E, A, B](override val underlying: () => B) extends ZIOFn1[A, ZIO[R, E, B]] {
    def apply(a: A): ZIO[R, E, B] = {
      val _ = a
      new ZIO.Succeed(underlying())
    }
  }

  final class BracketReleaseFn[R, E, A, B](override val underlying: A => ZIO[R, Nothing, _])
      extends ZIOFn2[A, Exit[E, B], ZIO[R, Nothing, _]] {
    override def apply(a: A, exit: Exit[E, B]): ZIO[R, Nothing, _] = {
      val _ = exit
      underlying(a)
    }
  }

  final class SucceedFn[R, E, A](override val underlying: AnyRef) extends ZIOFn1[A, ZIO[R, E, A]] {
    def apply(a: A): ZIO[R, E, A] = new ZIO.Succeed(a)
  }

  final class MapErrorFn[R, E, E2, A](override val underlying: E => E2) extends ZIOFn1[Cause[E], ZIO[R, E2, Nothing]] {
    def apply(a: Cause[E]): ZIO[R, E2, Nothing] =
      ZIO.halt(a.map(underlying))
  }

  final class MapErrorCauseFn[R, E, E2, A](override val underlying: Cause[E] => Cause[E2])
      extends ZIOFn1[Cause[E], ZIO[R, E2, Nothing]] {
    def apply(a: Cause[E]): ZIO[R, E2, Nothing] =
      ZIO.halt(underlying(a))
  }

  final class FoldCauseMFailureFn[R, E, E2, A](override val underlying: E => ZIO[R, E2, A])
      extends ZIOFn1[Cause[E], ZIO[R, E2, A]] {
    def apply(c: Cause[E]): ZIO[R, E2, A] =
      c.failureOrCause.fold(underlying, ZIO.halt)
  }

  final class TapErrorRefailFn[R, E, E1 >: E, A](override val underlying: E => ZIO[R, E1, _])
      extends ZIOFn1[Cause[E], ZIO[R, E1, Nothing]] {
    def apply(c: Cause[E]): ZIO[R, E1, Nothing] =
      c.failureOrCause.fold(underlying(_) *> ZIO.halt(c), _ => ZIO.halt(c))
  }

  private[zio] object Tags {
    final val FlatMap         = 0
    final val Succeed         = 1
    final val EffectTotal     = 2
    final val Fail            = 3
    final val Fold            = 4
    final val InterruptStatus = 5
    final val CheckInterrupt  = 6
    final val EffectPartial   = 7
    final val EffectAsync     = 8
    final val Fork            = 9
    final val SuperviseStatus = 10
    final val Descriptor      = 11
    final val Lock            = 12
    final val Yield           = 13
    final val Access          = 14
    final val Provide         = 15
    final val SuspendWith     = 16
    final val FiberRefNew     = 17
    final val FiberRefModify  = 18
    final val Trace           = 19
    final val TracingStatus   = 20
    final val CheckTracing    = 21
  }
  private[zio] final class FlatMap[R, E, A0, A](val zio: ZIO[R, E, A0], val k: A0 => ZIO[R, E, A])
      extends ZIO[R, E, A] {
    override def tag = Tags.FlatMap
  }

  private[zio] final class Succeed[A](val value: A) extends UIO[A] {
    override def tag = Tags.Succeed
  }

  private[zio] final class EffectTotal[A](val effect: () => A) extends UIO[A] {
    override def tag = Tags.EffectTotal
  }

  private[zio] final class EffectPartial[A](val effect: () => A) extends Task[A] {
    override def tag = Tags.EffectPartial
  }

  private[zio] final class EffectAsync[R, E, A](val register: (ZIO[R, E, A] => Unit) => Option[ZIO[R, E, A]])
      extends ZIO[R, E, A] {
    override def tag = Tags.EffectAsync
  }

  private[zio] final class Fold[R, E, E2, A, B](
    val value: ZIO[R, E, A],
    val failure: Cause[E] => ZIO[R, E2, B],
    val success: A => ZIO[R, E2, B]
  ) extends ZIOFn1[A, ZIO[R, E2, B]]
      with ZIO[R, E2, B]
      with Function[A, ZIO[R, E2, B]] {

    override def tag = Tags.Fold

    override def underlying = success

    final def apply(v: A): ZIO[R, E2, B] = success(v)
  }

  private[zio] final class Fork[R, E, A](val value: ZIO[R, E, A]) extends ZIO[R, Nothing, Fiber[E, A]] {
    override def tag = Tags.Fork
  }

  private[zio] final class InterruptStatus[R, E, A](val zio: ZIO[R, E, A], val flag: InterruptS) extends ZIO[R, E, A] {
    override def tag = Tags.InterruptStatus
  }

  private[zio] final class CheckInterrupt[R, E, A](val k: zio.InterruptStatus => ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.CheckInterrupt
  }

  private[zio] final class SuperviseStatus[R, E, A](val value: ZIO[R, E, A], val status: zio.SuperviseStatus)
      extends ZIO[R, E, A] {
    override def tag = Tags.SuperviseStatus
  }

  private[zio] final class Fail[E, A](val fill: (() => ZTrace) => Cause[E]) extends IO[E, A] { self =>
    override def tag = Tags.Fail

    override final def map[B](f: A => B): IO[E, B] =
      self.asInstanceOf[IO[E, B]]

    override final def flatMap[R1 <: Any, E1 >: E, B](k: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
      self.asInstanceOf[ZIO[R1, E1, B]]
  }

  private[zio] final class Descriptor[R, E, A](val k: Fiber.Descriptor => ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.Descriptor
  }

  private[zio] final class Lock[R, E, A](val executor: Executor, val zio: ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.Lock
  }

  private[zio] object Yield extends UIO[Unit] {
    override def tag = Tags.Yield
  }

  private[zio] final class Read[R, E, A](val k: R => ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.Access
  }

  private[zio] final class Provide[R, E, A](val r: R, val next: ZIO[R, E, A]) extends IO[E, A] {
    override def tag = Tags.Provide
  }

  private[zio] final class SuspendWith[R, E, A](val f: Platform => ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.SuspendWith
  }

  private[zio] final class FiberRefNew[A](val initialValue: A) extends UIO[FiberRef[A]] {
    override def tag = Tags.FiberRefNew
  }

  private[zio] final class FiberRefModify[A, B](val fiberRef: FiberRef[A], val f: A => (B, A)) extends UIO[B] {
    override def tag = Tags.FiberRefModify
  }

  private[zio] object Trace extends UIO[ZTrace] {
    override def tag = Tags.Trace
  }

  private[zio] final class TracingStatus[R, E, A](val zio: ZIO[R, E, A], val flag: TrasingS) extends ZIO[R, E, A] {
    override def tag = Tags.TracingStatus
  }

  private[zio] final class CheckTracing[R, E, A](val k: TrasingS => ZIO[R, E, A]) extends ZIO[R, E, A] {
    override def tag = Tags.CheckTracing
  }
}
