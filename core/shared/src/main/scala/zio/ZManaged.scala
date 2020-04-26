/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import scala.reflect.ClassTag

import zio.clock.Clock
import zio.duration.Duration

/**
 * A `Reservation[-R, +E, +A]` encapsulates resource acquisition and disposal
 * without specifying when or how that resource might be used.
 *
 * See [[ZManaged#reserve]] and [[ZIO#reserve]] for details of usage.
 */
final case class Reservation[-R, +E, +A](acquire: ZIO[R, E, A], release: Exit[Any, Any] => ZIO[R, Nothing, Any])

/**
 * A `ZManaged[R, E, A]` is a managed resource of type `A`, which may be used by
 * invoking the `use` method of the resource. The resource will be automatically
 * acquired before the resource is used, and automatically released after the
 * resource is used.
 *
 * Resources do not survive the scope of `use`, meaning that if you attempt to
 * capture the resource, leak it from `use`, and then use it after the resource
 * has been consumed, the resource will not be valid anymore and may fail with
 * some checked error, as per the type of the functions provided by the resource.
 */
final class ZManaged[-R, +E, +A] private (reservation: ZIO[R, E, Reservation[R, E, A]]) extends Serializable { self =>

  /**
   * Symbolic alias for zip.
   */
  def &&&[R1 <: R, E1 >: E, B](that: ZManaged[R1, E1, B]): ZManaged[R1, E1, (A, B)] =
    zipWith(that)((a, b) => (a, b))

  /**
   * Symbolic alias for zipParRight
   */
  def &>[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A1] =
    zipPar(that).map(_._2)

  /**
   * Splits the environment, providing the first part to this effect and the
   * second part to that effect.
   */
  def ***[R1, E1 >: E, B](that: ZManaged[R1, E1, B]): ZManaged[(R, R1), E1, (A, B)] =
    (ZManaged.first[R, R1] >>> self) &&& (ZManaged.second[R, R1] >>> that)

  /**
   * Symbolic alias for zipRight
   */
  def *>[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A1] =
    flatMap(_ => that)

  def +++[R1, B, E1 >: E](that: ZManaged[R1, E1, B]): ZManaged[Either[R, R1], E1, Either[A, B]] =
    for {
      e <- ZManaged.environment[Either[R, R1]]
      r <- e.fold(map(Left(_)).provide(_), that.map(Right(_)).provide(_))
    } yield r

  /**
   * Symbolic alias for zipParLeft
   */
  def <&[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A] = zipPar(that).map(_._1)

  /**
   * Symbolic alias for zipPar
   */
  def <&>[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, (A, A1)] =
    zipWithPar(that)((_, _))

  /**
   * Symbolic alias for zipLeft.
   */
  def <*[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A] =
    flatMap(r => that.map(_ => r))

  /**
   * Symbolic alias for zip.
   */
  def <*>[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, (A, A1)] =
    zipWith(that)((_, _))

  /**
   * Symbolic alias for compose
   */
  def <<<[R1, E1 >: E](that: ZManaged[R1, E1, R]): ZManaged[R1, E1, A] =
    for {
      r1 <- ZManaged.environment[R1]
      r  <- that.provide(r1)
      a  <- provide(r)
    } yield a

  /**
   * Operator alias for `orElse`.
   */
  def <>[R1 <: R, E2, A1 >: A](that: => ZManaged[R1, E2, A1])(implicit ev: CanFail[E]): ZManaged[R1, E2, A1] =
    orElse(that)

  /**
   * Symbolic alias for flatMap
   */
  def >>=[R1 <: R, E1 >: E, B](k: A => ZManaged[R1, E1, B]): ZManaged[R1, E1, B] =
    flatMap(k)

  /**
   * Symbolic alias for andThen
   */
  def >>>[E1 >: E, B](that: ZManaged[A, E1, B]): ZManaged[R, E1, B] =
    self.flatMap(that.provide)

  /**
   * Symbolic alias for join
   */
  def |||[R1, E1 >: E, A1 >: A](that: ZManaged[R1, E1, A1]): ZManaged[Either[R, R1], E1, A1] =
    for {
      either <- ZManaged.environment[Either[R, R1]]
      a1     <- either.fold(provide, that.provide)
    } yield a1

  /**
   * Submerges the error case of an `Either` into the `ZManaged`. The inverse
   * operation of `ZManaged.either`.
   */
  def absolve[E1 >: E, B](implicit ev: A <:< Either[E1, B]): ZManaged[R, E1, B] =
    ZManaged.absolve(self.map(ev))

  /**
   * Attempts to convert defects into a failure, throwing away all information
   * about the cause of the failure.
   */
  def absorb(implicit ev: E <:< Throwable): ZManaged[R, Throwable, A] =
    absorbWith(ev)

  /**
   * Attempts to convert defects into a failure, throwing away all information
   * about the cause of the failure.
   */
  def absorbWith(f: E => Throwable): ZManaged[R, Throwable, A] =
    self.sandbox
      .foldM(
        cause => ZManaged.fail(cause.squashWith(f)),
        ZManaged.succeedNow
      )

  /**
   * Maps this effect to the specified constant while preserving the
   * effects of this effect.
   */
  def as[B](b: => B): ZManaged[R, E, B] =
    map(_ => b)

  /**
   * Maps the success value of this effect to a service.
   */
  def asService[A1 >: A](implicit tagged: Tagged[A1]): ZManaged[R, E, Has[A1]] =
    map(Has(_))

  /**
   * Maps the success value of this effect to an optional value.
   */
  final def asSome: ZManaged[R, E, Option[A]] =
    map(Some(_))

  /**
   * Maps the error value of this effect to an optional value.
   */
  final def asSomeError: ZManaged[R, Option[E], A] =
    mapError(Some(_))

  /**
   * Executes the this effect and then provides its output as an environment to the second effect
   */
  def andThen[E1 >: E, B](that: ZManaged[A, E1, B]): ZManaged[R, E1, B] = self >>> that

  /**
   * Returns an effect whose failure and success channels have been mapped by
   * the specified pair of functions, `f` and `g`.
   */
  def bimap[E1, A1](f: E => E1, g: A => A1)(implicit ev: CanFail[E]): ZManaged[R, E1, A1] =
    mapError(f).map(g)

  /**
   * Recovers from all errors.
   */
  def catchAll[R1 <: R, E2, A1 >: A](
    h: E => ZManaged[R1, E2, A1]
  )(implicit ev: CanFail[E]): ZManaged[R1, E2, A1] =
    foldM(h, ZManaged.succeedNow)

  /**
   * Recovers from all errors with provided Cause.
   *
   * {{{
   * managed.catchAllCause(_ => ZManaged.succeed(defaultConfig))
   * }}}
   *
   * @see [[absorb]], [[sandbox]], [[mapErrorCause]] - other functions that can recover from defects
   */
  def catchAllCause[R1 <: R, E2, A1 >: A](h: Cause[E] => ZManaged[R1, E2, A1]): ZManaged[R1, E2, A1] =
    self.foldCauseM[R1, E2, A1](h, ZManaged.succeedNow)

  /**
   * Recovers from some or all of the error cases.
   */
  def catchSome[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[E, ZManaged[R1, E1, A1]]
  )(implicit ev: CanFail[E]): ZManaged[R1, E1, A1] =
    foldM(pf.applyOrElse[E, ZManaged[R1, E1, A1]](_, ZManaged.fail(_)), ZManaged.succeedNow)

  /**
   * Recovers from some or all of the error Causes.
   */
  def catchSomeCause[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[Cause[E], ZManaged[R1, E1, A1]]
  ): ZManaged[R1, E1, A1] =
    foldCauseM(pf.applyOrElse[Cause[E], ZManaged[R1, E1, A1]](_, ZManaged.halt(_)), ZManaged.succeedNow)

  /**
   * Fail with `e` if the supplied `PartialFunction` does not match, otherwise
   * succeed with the returned value.
   */
  def collect[E1 >: E, B](e: => E1)(pf: PartialFunction[A, B]): ZManaged[R, E1, B] =
    collectM(e)(pf.andThen(ZManaged.succeedNow(_)))

  /**
   * Fail with `e` if the supplied `PartialFunction` does not match, otherwise
   * continue with the returned value.
   */
  def collectM[R1 <: R, E1 >: E, B](e: => E1)(pf: PartialFunction[A, ZManaged[R1, E1, B]]): ZManaged[R1, E1, B] =
    self.flatMap(v => pf.applyOrElse[A, ZManaged[R1, E1, B]](v, _ => ZManaged.fail(e)))

  /**
   * Executes the second effect and then provides its output as an environment to this effect
   */
  def compose[R1, E1 >: E](that: ZManaged[R1, E1, R]): ZManaged[R1, E1, A] =
    self <<< that

  /**
   * Returns an effect whose failure and success have been lifted into an
   * `Either`.The resulting effect cannot fail
   */
  def either(implicit ev: CanFail[E]): ZManaged[R, Nothing, Either[E, A]] =
    fold(Left[E, A], Right[E, A])

  /**
   * Ensures that `f` is executed when this ZManaged is finalized, after
   * the existing finalizer.
   *
   * For usecases that need access to the ZManaged's result, see [[ZManaged#onExit]].
   */
  def ensuring[R1 <: R](f: ZIO[R1, Nothing, Any]): ZManaged[R1, E, A] =
    ZManaged {
      reserve.map(r => r.copy(release = e => r.release(e).ensuring(f)))
    }

  /**
   * Ensures that `f` is executed when this ZManaged is finalized, before
   * the existing finalizer.
   *
   * For usecases that need access to the ZManaged's result, see [[ZManaged#onExitFirst]].
   */
  def ensuringFirst[R1 <: R](f: ZIO[R1, Nothing, Any]): ZManaged[R1, E, A] =
    ZManaged {
      reserve.map(r => r.copy(release = e => f.ensuring(r.release(e))))
    }

  /**
   * Returns a ZManaged that ignores errors raised by the acquire effect and
   * runs it repeatedly until it eventually succeeds.
   */
  def eventually(implicit ev: CanFail[E]): ZManaged[R, Nothing, A] =
    ZManaged {
      reserve.eventually.map(r => Reservation(r.acquire.eventually, r.release))
    }

  /**
   * Zips this effect with its environment
   */
  def first[R1 <: R, A1 >: A]: ZManaged[R1, E, (A1, R1)] = self &&& ZManaged.identity

  /**
   * Returns an effect that models the execution of this effect, followed by
   * the passing of its value to the specified continuation function `k`,
   * followed by the effect that it returns.
   */
  def flatMap[R1 <: R, E1 >: E, B](f0: A => ZManaged[R1, E1, B]): ZManaged[R1, E1, B] =
    ZManaged[R1, E1, B] {
      Ref.make[List[Exit[Any, Any] => ZIO[R1, Nothing, Any]]](Nil).map { finalizers =>
        Reservation(
          acquire = ZIO.uninterruptibleMask { restore =>
            reserve
              .flatMap(resR => finalizers.update(resR.release :: _) *> restore(resR.acquire))
              .flatMap(r => f0(r).reserve)
              .flatMap(resR1 => finalizers.update(resR1.release :: _) *> restore(resR1.acquire))
          },
          release = exitU =>
            for {
              fs    <- finalizers.get
              exits <- ZIO.foreach(fs)(_(exitU).run)
              _     <- ZIO.done(Exit.collectAll(exits).getOrElse(Exit.unit))
            } yield ()
        )
      }
    }

  /**
   * Effectfully map the error channel
   */
  def flatMapError[R1 <: R, E2](f: E => ZManaged[R1, Nothing, E2])(implicit ev: CanFail[E]): ZManaged[R1, E2, A] =
    flipWith(_.flatMap(f))

  /**
   * Returns an effect that performs the outer effect first, followed by the
   * inner effect, yielding the value of the inner effect.
   *
   * This method can be used to "flatten" nested effects.
    **/
  def flatten[R1 <: R, E1 >: E, B](implicit ev: A <:< ZManaged[R1, E1, B]): ZManaged[R1, E1, B] =
    flatMap(ev)

  /**
   * Returns an effect that performs the outer effect first, followed by the
   * inner effect, yielding the value of the inner effect.
   *
   * This method can be used to "flatten" nested effects.
    **/
  def flattenM[R1 <: R, E1 >: E, B](implicit ev: A <:< ZIO[R1, E1, B]): ZManaged[R1, E1, B] =
    mapM(ev)

  /**
   * Flip the error and result
    **/
  def flip: ZManaged[R, A, E] =
    foldM(ZManaged.succeedNow, ZManaged.fail(_))

  /**
   * Flip the error and result, then apply an effectful function to the effect
    **/
  def flipWith[R1, A1, E1](f: ZManaged[R, A, E] => ZManaged[R1, A1, E1]): ZManaged[R1, E1, A1] =
    f(flip).flip

  /**
   * Folds over the failure value or the success value to yield an effect that
   * does not fail, but succeeds with the value returned by the left or right
   * function passed to `fold`.
   */
  def fold[B](failure: E => B, success: A => B)(implicit ev: CanFail[E]): ZManaged[R, Nothing, B] =
    foldM(failure.andThen(ZManaged.succeedNow), success.andThen(ZManaged.succeedNow))

  /**
   * A more powerful version of `fold` that allows recovering from any kind of failure except interruptions.
   */
  def foldCause[B](failure: Cause[E] => B, success: A => B): ZManaged[R, Nothing, B] =
    sandbox.fold(failure, success)

  /**
   * A more powerful version of `foldM` that allows recovering from any kind of failure except interruptions.
   */
  def foldCauseM[R1 <: R, E1, A1](
    failure: Cause[E] => ZManaged[R1, E1, A1],
    success: A => ZManaged[R1, E1, A1]
  ): ZManaged[R1, E1, A1] =
    sandbox.foldM(failure, success)

  /**
   * Recovers from errors by accepting one effect to execute for the case of an
   * error, and one effect to execute for the case of success.
   */
  def foldM[R1 <: R, E2, B](
    failure: E => ZManaged[R1, E2, B],
    success: A => ZManaged[R1, E2, B]
  )(implicit ev: CanFail[E]): ZManaged[R1, E2, B] =
    ZManaged[R1, E2, B] {
      Ref.make[List[Exit[Any, Any] => ZIO[R1, Nothing, Any]]](Nil).map { finalizers =>
        Reservation(
          acquire = {
            val direct =
              ZIO.uninterruptibleMask { restore =>
                reserve
                  .flatMap(res => finalizers.update(res.release :: _).as(res))
                  .flatMap(res => restore(res.acquire))
              }
            val onFailure = (e: E) =>
              ZIO.uninterruptibleMask { restore =>
                failure(e).reserve
                  .flatMap(res => finalizers.update(res.release :: _).as(res))
                  .flatMap(res => restore(res.acquire))
              }
            val onSuccess = (a: A) =>
              ZIO.uninterruptibleMask { restore =>
                success(a).reserve
                  .flatMap(res => finalizers.update(res.release :: _).as(res))
                  .flatMap(res => restore(res.acquire))
              }
            direct.foldM(onFailure, onSuccess)
          },
          release = exitU =>
            for {
              fs    <- finalizers.get
              exits <- ZIO.foreach(fs)(_(exitU).run)
              _     <- ZIO.done(Exit.collectAll(exits).getOrElse(Exit.unit))
            } yield ()
        )
      }
    }

  /**
   * Creates a `ZManaged` value that acquires the original resource in a fiber,
   * and provides that fiber. The finalizer for this value will interrupt the fiber
   * and run the original finalizer.
   */
  def fork: ZManaged[R, Nothing, Fiber.Runtime[E, A]] =
    ZManaged {
      for {
        finalizer <- Ref.make[Exit[Any, Any] => ZIO[R, Nothing, Any]](_ => UIO.unit)
        // The reservation phase of the new `ZManaged` runs uninterruptibly;
        // so to make sure the acquire phase of the original `ZManaged` runs
        // interruptibly, we need to create an interruptible hole in the region.
        fiber <- ZIO.interruptibleMask { restore =>
                  restore(self.reserve.tap(r => finalizer.set(r.release))) >>= (_.acquire)
                }.fork
      } yield Reservation(
        acquire = UIO.succeedNow(fiber),
        release = e => fiber.interrupt *> finalizer.get.flatMap(f => f(e))
      )
    }

  /**
   * Unwraps the optional success of this effect, but can fail with unit value.
   */
  def get[B](implicit ev1: E <:< Nothing, ev2: A <:< Option[B]): ZManaged[R, Unit, B] =
    ZManaged.absolve(mapError(ev1)(CanFail).map(ev2(_).toRight(())))

  /**
   * Returns whether this managed effect is a failure.
   */
  def isFailure: ZManaged[R, Nothing, Boolean] =
    fold(_ => true, _ => false)

  /**
   * Returns whether this managed effect is a success.
   */
  def isSuccess: ZManaged[R, Nothing, Boolean] =
    fold(_ => false, _ => true)

  /**
   * Depending on the environment execute this or the other effect
   */
  def join[R1, E1 >: E, A1 >: A](that: ZManaged[R1, E1, A1]): ZManaged[Either[R, R1], E1, A1] = self ||| that

  def left[R1 <: R, C]: ZManaged[Either[R1, C], E, Either[A, C]] = self +++ ZManaged.identity

  /**
   * Returns an effect whose success is mapped by the specified `f` function.
   */
  def map[B](f0: A => B): ZManaged[R, E, B] =
    ZManaged[R, E, B] {
      reserve.map(token => token.copy(acquire = token.acquire.map(f0)))
    }

  /**
   * Returns an effect whose success is mapped by the specified side effecting
   * `f` function, translating any thrown exceptions into typed failed effects.
   */
  final def mapEffect[B](f: A => B)(implicit ev: E <:< Throwable): ZManaged[R, Throwable, B] =
    foldM(e => ZManaged.fail(ev(e)), a => ZManaged.effect(f(a)))

  /**
   * Returns an effect whose failure is mapped by the specified `f` function.
   */
  def mapError[E1](f: E => E1)(implicit ev: CanFail[E]): ZManaged[R, E1, A] =
    ZManaged(reserve.bimap(f, r => Reservation(r.acquire.mapError(f), r.release)))

  /**
   * Returns an effect whose full failure is mapped by the specified `f` function.
   */
  def mapErrorCause[E1](f: Cause[E] => Cause[E1]): ZManaged[R, E1, A] =
    ZManaged(reserve.mapErrorCause(f).map(r => Reservation(r.acquire.mapErrorCause(f), r.release)))

  /**
   * Effectfully maps the resource acquired by this value.
   */
  def mapM[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): ZManaged[R1, E1, B] =
    ZManaged[R1, E1, B] {
      reserve.map(token => token.copy(acquire = token.acquire.flatMap(f)))
    }

  def memoize: ZManaged[Any, Nothing, ZManaged[R, E, A]] =
    ZManaged.finalizerRef(_ => UIO.unit).mapM { finalizers =>
      for {
        promise <- Promise.make[E, A]
        complete <- ZIO.uninterruptibleMask { restore =>
                     ZIO.accessM[R] { r =>
                       self
                         .provide(r)
                         .reserve
                         .flatMap {
                           case Reservation(acquire, release) =>
                             restore(acquire).ensuring(finalizers.add(release))
                         }
                         .to(promise)
                     }
                   }.once
      } yield (complete *> promise.await).toManaged_
    }

  /**
   * Returns a new effect where the error channel has been merged into the
   * success channel to their common combined type.
   */
  def merge[A1 >: A](implicit ev1: E <:< A1, ev2: CanFail[E]): ZManaged[R, Nothing, A1] =
    self.foldM(e => ZManaged.succeedNow(ev1(e)), ZManaged.succeedNow)

  /**
   * Requires the option produced by this value to be `None`.
   */
  final def none[B](implicit ev: A <:< Option[B]): ZManaged[R, Option[E], Unit] =
    self.foldM(
      e => ZManaged.fail(Some(e)),
      a => a.fold[ZManaged[R, Option[E], Unit]](ZManaged.succeedNow(()))(_ => ZManaged.fail(None))
    )

  /**
   * Ensures that a cleanup function runs when this ZManaged is finalized, after
   * the existing finalizers.
   */
  def onExit[R1 <: R](cleanup: Exit[E, A] => ZIO[R1, Nothing, Any]): ZManaged[R1, E, A] =
    ZManaged {
      Ref.make[Exit[Any, Any] => ZIO[R1, Nothing, Any]](_ => UIO.unit).map { finalizer =>
        Reservation(
          acquire = ZIO.bracketExit(self.reserve)((res, exitA: Exit[E, A]) =>
            finalizer.set(exitU => res.release(exitU).ensuring(cleanup(exitA)))
          )(_.acquire),
          release = e => finalizer.get.flatMap(f => f(e))
        )
      }
    }

  /**
   * Ensures that a cleanup function runs when this ZManaged is finalized, before
   * the existing finalizers.
   */
  def onExitFirst[R1 <: R](cleanup: Exit[E, A] => ZIO[R1, Nothing, Any]): ZManaged[R1, E, A] =
    ZManaged {
      Ref.make[Exit[Any, Any] => ZIO[R1, Nothing, Any]](_ => UIO.unit).map { finalizer =>
        Reservation(
          acquire = ZIO.bracketExit(self.reserve)((res, exitA: Exit[E, A]) =>
            finalizer.set(exitU => cleanup(exitA).ensuring(res.release(exitU)))
          )(_.acquire),
          release = e => finalizer.get.flatMap(f => f(e))
        )
      }
    }

  /**
   * Executes this effect, skipping the error but returning optionally the success.
   */
  def option(implicit ev: CanFail[E]): ZManaged[R, Nothing, Option[A]] =
    fold(_ => None, Some(_))

  /**
   * Converts an option on errors into an option on values.
   */
  final def optional[E1](implicit ev: E <:< Option[E1]): ZManaged[R, E1, Option[A]] =
    self.foldM(
      e => e.fold[ZManaged[R, E1, Option[A]]](ZManaged.succeedNow(Option.empty[A]))(ZManaged.fail(_)),
      a => ZManaged.succeedNow(Some(a))
    )

  /**
   * Translates effect failure into death of the fiber, making all failures unchecked and
   * not a part of the type of the effect.
   */
  def orDie(implicit ev1: E <:< Throwable, ev2: CanFail[E]): ZManaged[R, Nothing, A] =
    orDieWith(ev1)

  /**
   * Keeps none of the errors, and terminates the fiber with them, using
   * the specified function to convert the `E` into a `Throwable`.
   */
  def orDieWith(f: E => Throwable)(implicit ev: CanFail[E]): ZManaged[R, Nothing, A] =
    mapError(f).catchAll(ZManaged.die(_))

  /**
   * Executes this effect and returns its value, if it succeeds, but
   * otherwise executes the specified effect.
   */
  def orElse[R1 <: R, E2, A1 >: A](that: => ZManaged[R1, E2, A1])(implicit ev: CanFail[E]): ZManaged[R1, E2, A1] =
    foldM(_ => that, ZManaged.succeedNow)

  /**
   * Returns an effect that will produce the value of this effect, unless it
   * fails, in which case, it will produce the value of the specified effect.
   */
  def orElseEither[R1 <: R, E2, B](
    that: => ZManaged[R1, E2, B]
  )(implicit ev: CanFail[E]): ZManaged[R1, E2, Either[A, B]] =
    foldM(_ => that.map(Right[A, B]), a => ZManaged.succeedNow(Left[A, B](a)))

  /**
   * Executes this effect and returns its value, if it succeeds, but
   * otherwise fails with the specified error.
   */
  final def orElseFail[E1](e1: => E1)(implicit ev: CanFail[E]): ZManaged[R, E1, A] =
    orElse(ZManaged.fail(e1))

  /**
   * Returns an effect that will produce the value of this effect, unless it
   * fails with the `None` value, in which case it will produce the value of
   * the specified effect.
   */
  final def orElseOptional[R1 <: R, E1, A1 >: A](
    that: => ZManaged[R1, Option[E1], A1]
  )(implicit ev: E <:< Option[E1]): ZManaged[R1, Option[E1], A1] =
    catchAll(ev(_).fold(that)(e => ZManaged.fail(Some(e))))

  /**
   * Executes this effect and returns its value, if it succeeds, but
   * otherwise suceeds with the specified value.
   */
  final def orElseSucceed[A1 >: A](a1: => A1)(implicit ev: CanFail[E]): ZManaged[R, Nothing, A1] =
    orElse(ZManaged.succeedNow(a1))

  /**
   * Preallocates the managed resource, resulting in a ZManaged that reserves and acquires immediately and cannot fail.
   * You should take care that you are not interrupted between running preallocate and actually acquiring the resource
   * as you might leak otherwise.
   */
  def preallocate: ZIO[R, E, Managed[Nothing, A]] =
    ZIO.uninterruptibleMask { restore =>
      for {
        env      <- ZIO.environment[R]
        res      <- reserve
        resource <- restore(res.acquire).onError(err => res.release(Exit.Failure(err)))
      } yield ZManaged.make(ZIO.succeedNow(resource))(_ => res.release(Exit.Success(resource)).provide(env))
    }

  /**
   * Preallocates the managed resource inside an outer managed, resulting in a ZManaged that reserves and acquires immediately and cannot fail.
   */
  def preallocateManaged: ZManaged[R, E, Managed[Nothing, A]] =
    ZManaged.finalizerRef[R](_ => ZIO.unit).mapM { finalizers =>
      ZIO.uninterruptibleMask { restore =>
        for {
          env      <- ZIO.environment[R]
          res      <- reserve
          _        <- finalizers.add(res.release)
          resource <- restore(res.acquire)
        } yield ZManaged.make(ZIO.succeedNow(resource))(_ => finalizers.runAll(Exit.succeed(resource)).provide(env))
      }
    }

  /**
   * Provides the `ZManaged` effect with its required environment, which eliminates
   * its dependency on `R`.
   */
  def provide(r: R)(implicit ev: NeedsEnv[R]): Managed[E, A] =
    provideSome(_ => r)

  /**
   * Provides the part of the environment that is not part of the `ZEnv`,
   * leaving a managed effect that only depends on the `ZEnv`.
   *
   * {{{
   * val loggingLayer: ZLayer[Any, Nothing, Logging] = ???
   *
   * val managed: ZManaged[ZEnv with Logging, Nothing, Unit] = ???
   *
   * val managed2 = managed.provideCustomLayer(loggingLayer)
   * }}}
   */
  def provideCustomLayer[E1 >: E, R1 <: Has[_]](
    layer: ZLayer[ZEnv, E1, R1]
  )(implicit ev: ZEnv with R1 <:< R, tagged: Tagged[R1]): ZManaged[ZEnv, E1, A] =
    provideSomeLayer[ZEnv](layer)

  /**
   * Provides a layer to the `ZManaged`, which translates it to another level.
   */
  def provideLayer[E1 >: E, R0, R1](
    layer: ZLayer[R0, E1, R1]
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): ZManaged[R0, E1, A] =
    layer.build.map(ev1).flatMap(self.provide)

  /**
   * Provides some of the environment required to run this effect,
   * leaving the remainder `R0`.
   *
   * {{{
   * val managed: ZManaged[Console with Logging, Nothing, Unit] = ???
   *
   * managed.provideSome[Console](env =>
   *   new Console with Logging {
   *     val console = env.console
   *     val logging = new Logging.Service[Any] {
   *       def log(line: String) = console.putStrLn(line)
   *     }
   *   }
   * )
   * }}}
   */
  def provideSome[R0](f: R0 => R)(implicit ev: NeedsEnv[R]): ZManaged[R0, E, A] =
    ZManaged(reserve.provideSome(f).map(r => Reservation(r.acquire.provideSome(f), e => r.release(e).provideSome(f))))

  /**
   * Splits the environment into two parts, providing one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * val clockLayer: ZLayer[Any, Nothing, Clock] = ???
   *
   * val managed: ZManaged[Clock with Random, Nothing, Unit] = ???
   *
   * val managed2 = managed.provideSomeLayer[Random](clockLayer)
   * }}}
   */
  final def provideSomeLayer[R0 <: Has[_]]: ZManaged.ProvideSomeLayer[R0, R, E, A] =
    new ZManaged.ProvideSomeLayer[R0, R, E, A](self)

  /**
   * Gives access to wrapped [[Reservation]].
   */
  def reserve: ZIO[R, E, Reservation[R, E, A]] = reservation

  /**
   * Keeps some of the errors, and terminates the fiber with the rest.
   */
  def refineOrDie[E1](
    pf: PartialFunction[E, E1]
  )(implicit ev1: E <:< Throwable, ev2: CanFail[E]): ZManaged[R, E1, A] =
    refineOrDieWith(pf)(ev1)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest.
   */
  def refineToOrDie[E1: ClassTag](implicit ev1: E <:< Throwable, ev2: CanFail[E]): ZManaged[R, E1, A] =
    refineOrDieWith { case e: E1 => e }(ev1)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest, using
   * the specified function to convert the `E` into a `Throwable`.
   */
  def refineOrDieWith[E1](
    pf: PartialFunction[E, E1]
  )(f: E => Throwable)(implicit ev: CanFail[E]): ZManaged[R, E1, A] =
    catchAll(e => pf.lift(e).fold[ZManaged[R, E1, A]](ZManaged.die(f(e)))(ZManaged.fail(_)))

  /**
   * Fail with the returned value if the `PartialFunction` matches, otherwise
   * continue with our held value.
   */
  def reject[E1 >: E](pf: PartialFunction[A, E1]): ZManaged[R, E1, A] =
    rejectM(pf.andThen(ZManaged.fail(_)))

  /**
   * Continue with the returned computation if the `PartialFunction` matches,
   * translating the successful match into a failure, otherwise continue with
   * our held value.
   */
  def rejectM[R1 <: R, E1 >: E](pf: PartialFunction[A, ZManaged[R1, E1, E1]]): ZManaged[R1, E1, A] =
    self.flatMap { v =>
      pf.andThen[ZManaged[R1, E1, A]](_.flatMap(ZManaged.fail(_)))
        .applyOrElse[A, ZManaged[R1, E1, A]](v, ZManaged.succeedNow)
    }

  /**
   * Retries with the specified retry policy.
   * Retries are done following the failure of the original `io` (up to a fixed maximum with
   * `once` or `recurs` for example), so that that `io.retry(Schedule.once)` means
   * "execute `io` and in case of failure, try again once".
   */
  def retry[R1 <: R, S](policy: Schedule[R1, E, S])(implicit ev: CanFail[E]): ZManaged[R1, E, A] = {
    def loop[B](zio: ZIO[R, E, B], state: policy.State): ZIO[R1, E, (policy.State, B)] =
      zio.foldM(
        err =>
          policy
            .update(err, state)
            .foldM(
              _ => ZIO.fail(err),
              loop(zio, _)
            ),
        succ => ZIO.succeedNow((state, succ))
      )
    ZManaged {
      policy.initial.flatMap(initial => loop(reserve, initial)).map {
        case (policyState, Reservation(acquire, release)) =>
          Reservation(loop(acquire, policyState).map(_._2), release)
      }
    }
  }

  def right[R1 <: R, C]: ZManaged[Either[C, R1], E, Either[C, A]] = ZManaged.identity +++ self

  /**
   * Returns an effect that semantically runs the effect on a fiber,
   * producing an [[zio.Exit]] for the completion value of the fiber.
   */
  def run: ZManaged[R, Nothing, Exit[E, A]] =
    foldCauseM(
      cause => ZManaged.succeedNow(Exit.halt(cause)),
      succ => ZManaged.succeedNow(Exit.succeed(succ))
    )

  /**
   * Exposes the full cause of failure of this effect.
   */
  def sandbox: ZManaged[R, Cause[E], A] =
    ZManaged {
      reserve.sandbox.map {
        case Reservation(acquire, release) =>
          Reservation(acquire.sandbox, release)
      }
    }

  /**
   * Companion helper to `sandbox`. Allows recovery, and partial recovery, from
   * errors and defects alike.
   */
  def sandboxWith[R1 <: R, E2, B](
    f: ZManaged[R1, Cause[E], A] => ZManaged[R1, Cause[E2], B]
  ): ZManaged[R1, E2, B] =
    ZManaged.unsandbox(f(self.sandbox))

  /**
   * Zips this effect with its environment
   */
  def second[R1 <: R, A1 >: A]: ZManaged[R1, E, (R1, A1)] = ZManaged.identity[R1] &&& self

  /**
   * Converts an option on values into an option on errors.
   */
  final def some[B](implicit ev: A <:< Option[B]): ZManaged[R, Option[E], B] =
    self.foldM(
      e => ZManaged.fail(Some(e)),
      a => a.fold[ZManaged[R, Option[E], B]](ZManaged.fail(Option.empty[E]))(ZManaged.succeedNow)
    )

  /**
   * Extracts the optional value, or fails with the given error 'e'.
   */
  final def someOrFail[B, E1 >: E](e: => E1)(implicit ev: A <:< Option[B]): ZManaged[R, E1, B] =
    self.flatMap(ev(_) match {
      case Some(value) => ZManaged.succeedNow(value)
      case None        => ZManaged.fail(e)
    })

  /**
   * Extracts the optional value, or fails with a [[java.util.NoSuchElementException]]
   */
  final def someOrFailException[B, E1 >: E](
    implicit ev: A <:< Option[B],
    ev2: NoSuchElementException <:< E1
  ): ZManaged[R, E1, B] =
    self.foldM(e => ZManaged.fail(e), ev(_) match {
      case Some(value) => ZManaged.succeedNow(value)
      case None        => ZManaged.fail(ev2(new NoSuchElementException("None.get")))
    })

  /**
   * Returns an effect that effectfully peeks at the acquired resource.
   */
  def tap[R1 <: R, E1 >: E](f: A => ZManaged[R1, E1, Any]): ZManaged[R1, E1, A] =
    flatMap(a => f(a).as(a))

  /**
   * Returns an effect that effectfully peeks at the failure or success of the acquired resource.
   */
  def tapBoth[R1 <: R, E1 >: E](f: E => ZManaged[R1, E1, Any], g: A => ZManaged[R1, E1, Any])(
    implicit ev: CanFail[E]
  ): ZManaged[R1, E1, A] =
    foldM(
      e => f(e) *> ZManaged.fail(e),
      a => g(a).as(a)
    )

  /**
   * Returns an effect that effectually peeks at the cause of the failure of
   * the acquired resource.
   */
  final def tapCause[R1 <: R, E1 >: E](f: Cause[E] => ZManaged[R1, E1, Any]): ZManaged[R1, E1, A] =
    catchAllCause(c => f(c) *> ZManaged.halt(c))

  /**
   * Returns an effect that effectfully peeks at the failure of the acquired resource.
   */
  def tapError[R1 <: R, E1 >: E](f: E => ZManaged[R1, E1, Any])(implicit ev: CanFail[E]): ZManaged[R1, E1, A] =
    tapBoth(f, ZManaged.succeedNow)

  /**
   * Like [[ZManaged#tap]], but uses a function that returns a ZIO value rather than a
   * ZManaged value.
   */
  def tapM[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Any]): ZManaged[R1, E1, A] =
    mapM(a => f(a).as(a))

  /**
   * Returns a new effect that executes this one and times the acquisition of the resource.
   */
  def timed: ZManaged[R with Clock, E, (Duration, A)] =
    ZManaged {
      clock.nanoTime.flatMap { start =>
        reserve.map {
          case Reservation(acquire, release) =>
            Reservation(acquire.zipWith(clock.nanoTime)((res, end) => (Duration.fromNanos(end - start), res)), release)
        }
      }
    }

  /**
   * Returns an effect that will timeout this resource, returning `None` if the
   * timeout elapses before the resource was reserved and acquired.
   * If the reservation completes successfully (even after the timeout) the release action will be run on a new fiber.
   * `Some` will be returned if acquisition and reservation complete in time
   */
  def timeout(d: Duration): ZManaged[R with Clock, E, Option[A]] = {
    def timeoutReservation[B](
      zio: ZIO[R, E, Reservation[R, E, A]],
      d: Duration
    ): ZIO[R with Clock, E, Option[(Duration, Reservation[R, E, A])]] =
      clock.nanoTime.flatMap { start =>
        zio
          .raceWith(ZIO.sleep(d).interruptible)(
            {
              case (leftDone, rightFiber) =>
                rightFiber.interrupt.flatMap(_ =>
                  leftDone.foldM(
                    ZIO.halt(_),
                    succ => clock.nanoTime.map(end => Some((Duration.fromNanos(end - start), succ)))
                  )
                )
            }, {
              case (exit, leftFiber) =>
                val cleanup = leftFiber.await
                  .flatMap(
                    _.foldM(
                      _ => ZIO.unit,
                      _.release(exit)
                    )
                  )
                  .uninterruptible
                cleanup.fork.as(None).uninterruptible
            }
          )
      }

    ZManaged {
      timeoutReservation(reserve, d).map {
        case Some((spentTime, Reservation(acquire, release))) if spentTime < d =>
          Reservation(acquire.timeout(Duration.fromNanos(d.toNanos - spentTime.toNanos)), release)
        case Some((_, Reservation(_, release))) =>
          Reservation(ZIO.succeedNow(None), release)
        case _ => Reservation(ZIO.succeedNow(None), _ => ZIO.unit)
      }
    }

  }

  /**
   * Constructs a layer from this managed resource.
   */
  def toLayer[A1 >: A](implicit tagged: Tagged[A1]): ZLayer[R, E, Has[A1]] =
    ZLayer.fromManaged(self)

  /**
   * Constructs a layer from this managed resource, which must return one or
   * more services.
   */
  def toLayerMany[A1 <: Has[_]](implicit ev: A <:< A1): ZLayer[R, E, A1] =
    ZLayer(self.map(ev))

  /**
   * Return unit while running the effect
   */
  lazy val unit: ZManaged[R, E, Unit] = as(())

  /**
   * The moral equivalent of `if (!p) exp`
   */
  final def unless(b: => Boolean): ZManaged[R, E, Unit] =
    ZManaged.unless(b)(self)

  /**
   * The moral equivalent of `if (!p) exp` when `p` has side-effects
   */
  final def unlessM[R1 <: R, E1 >: E](b: ZManaged[R1, E1, Boolean]): ZManaged[R1, E1, Unit] =
    ZManaged.unlessM(b)(self)

  /**
   * The inverse operation `ZManaged.sandboxed`
   */
  def unsandbox[E1](implicit ev: E <:< Cause[E1]): ZManaged[R, E1, A] =
    ZManaged.unsandbox(mapError(ev))

  /**
   * Run an effect while acquiring the resource before and releasing it after
   */
  def use[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    reserve.bracketExit((r, e: Exit[Any, Any]) => r.release(e), _.acquire.flatMap(f))

  /**
   *  Run an effect while acquiring the resource before and releasing it after.
   *  This does not provide the resource to the function
   */
  def use_[R1 <: R, E1 >: E, B](f: ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    use(_ => f)

  /**
   * Use the resource until interruption.
   * Useful for resources that you want to acquire and use as long as the application is running, like a HTTP server.
   */
  val useForever: ZIO[R, E, Nothing] = use(_ => ZIO.never)

  /**
   * The moral equivalent of `if (p) exp`
   */
  def when(b: => Boolean): ZManaged[R, E, Unit] =
    ZManaged.when(b)(self)

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  def whenM[R1 <: R, E1 >: E](b: ZManaged[R1, E1, Boolean]): ZManaged[R1, E1, Unit] =
    ZManaged.whenM(b)(self)

  /**
   * Modifies this `ZManaged` to provide a canceler that can be used to eagerly
   * execute the finalizer of this `ZManaged`. The canceler will run
   * uninterruptibly with an exit value indicating that the effect was
   * interrupted, and if completed will cause the regular finalizer to not run.
   */
  def withEarlyRelease: ZManaged[R, E, (URIO[R, Any], A)] =
    ZManaged.fiberId.flatMap(fiberId => withEarlyReleaseExit(Exit.interrupt(fiberId)))

  /**
   * A more powerful version of `withEarlyRelease` that allows specifying an
   * exit value in the event of early release.
   */
  def withEarlyReleaseExit(exit: Exit[Any, Any]): ZManaged[R, E, (URIO[R, Any], A)] =
    ZManaged[R, E, (URIO[R, Any], A)] {
      reserve.flatMap {
        case Reservation(acquire, release) =>
          Ref.make(true).map { finalize =>
            val canceler  = (release(exit) *> finalize.set(false)).uninterruptible
            val finalizer = (e: Exit[Any, Any]) => release(e).whenM(finalize.get)
            Reservation(acquire.map((canceler, _)), finalizer)
          }
      }
    }

  /**
   * Named alias for `<*>`.
   */
  def zip[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, (A, A1)] =
    self <*> that

  /**
   * Named alias for `<*`.
   */
  def zipLeft[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A] =
    self <* that

  /**
   * Named alias for `<&>`.
   */
  def zipPar[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, (A, A1)] =
    self <&> that

  /**
   * Named alias for `<&`.
   */
  def zipParLeft[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A] =
    self <& that

  /**
   * Named alias for `&>`.
   */
  def zipParRight[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A1] =
    self &> that

  /**
   * Named alias for `*>`.
   */
  def zipRight[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A1] =
    self *> that

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in sequence, combining their results with the specified `f` function.
   */
  def zipWith[R1 <: R, E1 >: E, A1, A2](that: ZManaged[R1, E1, A1])(f: (A, A1) => A2): ZManaged[R1, E1, A2] =
    flatMap(r => that.map(r1 => f(r, r1)))

  /**
   * Returns an effect that executes both this effect and the specified effect,
   * in parallel, combining their results with the specified `f` function. If
   * either side fails, then the other side will be interrupted.
   */
  def zipWithPar[R1 <: R, E1 >: E, A1, A2](that: ZManaged[R1, E1, A1])(f0: (A, A1) => A2): ZManaged[R1, E1, A2] = {

    sealed trait State
    case object Running   extends State
    case object Acquiring extends State
    case object Exited    extends State

    ZManaged[R1, E1, A2] {
      ZManaged.FinalizerRef.make[R1](_ => UIO.unit).map { finalizers =>
        Reservation(
          acquire = {
            val left = ZIO.uninterruptibleMask {
              restore =>
                for {
                  ref   <- Ref.make[State](Running)
                  latch <- Promise.make[Nothing, Unit]
                  res   <- self.reservation
                  acquire = ZIO.ifM(ref.getAndSet(Acquiring).map(_ == Running))(
                    restore(res.acquire).ensuring(latch.succeed(())),
                    ZIO.interrupt
                  )
                  release = (exit: Exit[Any, Any]) =>
                    ZIO.ifM(ref.getAndSet(Exited).map(_ == Acquiring))(
                      latch.await *> res.release(exit),
                      res.release(exit)
                    )
                  _ <- finalizers.add(release)
                  a <- acquire
                } yield a
            }
            val right = ZIO.uninterruptibleMask {
              restore =>
                for {
                  ref   <- Ref.make[State](Running)
                  latch <- Promise.make[Nothing, Unit]
                  res   <- that.reserve
                  acquire = ZIO.ifM(ref.getAndSet(Acquiring).map(_ == Running))(
                    restore(res.acquire).ensuring(latch.succeed(())),
                    ZIO.interrupt
                  )
                  release = (exit: Exit[Any, Any]) =>
                    ZIO.ifM(ref.getAndSet(Exited).map(_ == Acquiring))(
                      latch.await *> res.release(exit),
                      res.release(exit)
                    )
                  _  <- finalizers.add(release)
                  a1 <- acquire
                } yield a1
            }
            left.zipWithPar(right)(f0)
          },
          release = finalizers.runAll
        )
      }
    }
  }
}

object ZManaged {

  final class AccessPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](f: R => A): ZManaged[R, Nothing, A] =
      ZManaged.environment.map(f)
  }

  final class AccessMPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](f: R => ZIO[R, E, A]): ZManaged[R, E, A] =
      ZManaged.environment.mapM(f)
  }

  final class AccessManagedPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](f: R => ZManaged[R, E, A]): ZManaged[R, E, A] =
      ZManaged.environment.flatMap(f)
  }

  /**
   * A `FinalizerRef` describes the finalizers associated with a scope.
   *
   */
  trait FinalizerRef[R] {

    /**
     * Adds a finalizer to the finalizers associated with this scope. If the
     * finalizers associated with this scope have already been run this
     * finalizer will be run immediately.
     */
    def add(finalizer: Exit[Any, Any] => URIO[R, Any]): URIO[R, Unit]

    /**
     * Removes the finalizers associated with this scope and returns them.
     */
    def remove: UIO[Set[Exit[Any, Any] => URIO[R, Any]]]

    /**
     * Replaces the finalizers associated with this scope and returns them.
     * If the finalizers associated with this scope have already been run this
     * finalizer will be run immediately.
     */
    def replace(finalizer: Exit[Any, Any] => URIO[R, Any]): URIO[R, Set[Exit[Any, Any] => URIO[R, Any]]]

    /**
     * Runs the specified finalizer and removes it from the finalizers
     * associated with this scope.
     */
    def run(finalizer: Exit[Any, Any] => URIO[R, Any])(exit: Exit[Any, Any]): URIO[R, Unit]

    /**
     * Runs the finalizers associated with this scope sequentially. After this
     * any finalizers added to this scope will be run immediately.
     */
    def runAll(exit: Exit[Any, Any]): URIO[R, Unit]

    /**
     * Runs the finalizers associated with this scope in parallel. After this
     * any finalizers added to this scope will be run immediately.
     */
    def runAllPar(exit: Exit[Any, Any]): URIO[R, Unit]
  }

  object FinalizerRef {

    /**
     * Constructs a new `FinalizerRef` with the specified initial finalizer.
     */
    def make[R](initial: Exit[Any, Any] => URIO[R, Any]): UIO[FinalizerRef[R]] = {

      sealed trait State

      final case class Exited(exit: Exit[Any, Any], done: Set[Exit[Any, Any] => URIO[R, Any]]) extends State
      final case class Running(finalizers: Set[Exit[Any, Any] => URIO[R, Any]])                extends State

      Ref.make[State](Running(Set(initial))).map { ref =>
        new FinalizerRef[R] {

          def add(finalizer: Exit[Any, Any] => URIO[R, Any]): URIO[R, Unit] =
            ref.getAndUpdate {
              case Exited(exit, done)  => Exited(exit, done + finalizer)
              case Running(finalizers) => Running(finalizers + finalizer)
            }.flatMap {
              case Exited(exit, done) if !done.contains(finalizer) => finalizer(exit).unit
              case _                                               => UIO.unit
            }

          def remove: UIO[Set[Exit[Any, Any] => URIO[R, Any]]] =
            ref.modifySome(Set.empty[Exit[Any, Any] => URIO[R, Any]]) {
              case Running(finalizers) => (finalizers, Running(Set.empty))
            }

          def replace(finalizer: Exit[Any, Any] => URIO[R, Any]): URIO[R, Set[Exit[Any, Any] => URIO[R, Any]]] =
            ref.getAndUpdate {
              case Exited(exit, done) => Exited(exit, done + finalizer)
              case Running(_)         => Running(Set(finalizer))
            }.flatMap {
              case Exited(exit, done) if !done.contains(finalizer) => finalizer(exit).as(Set.empty)
              case Running(finalizers)                             => UIO.succeedNow(finalizers)
              case _                                               => UIO.succeedNow(Set.empty)
            }

          def run(finalizer: Exit[Any, Any] => URIO[R, Any])(exit: Exit[Any, Any]): URIO[R, Unit] =
            ref.getAndUpdate {
              case Exited(exit, done)  => Exited(exit, done + finalizer)
              case Running(finalizers) => Running(finalizers - finalizer)
            }.flatMap {
              case Exited(_, done) if !done.contains(finalizer) => finalizer(exit).unit
              case _                                            => finalizer(exit).unit
            }

          def runAll(exit: Exit[Any, Any]): URIO[R, Unit] =
            runAllWith(exit)(ZIO.foreach(_)(_.run))

          def runAllPar(exit: Exit[Any, Any]): URIO[R, Unit] =
            runAllWith(exit)(ZIO.foreachPar(_)(_.run))

          private def runAllWith(
            exit: Exit[Any, Any]
          )(f: Set[URIO[R, Any]] => URIO[R, List[Exit[Nothing, Any]]]): URIO[R, Unit] =
            ref.getAndUpdateSome {
              case Running(finalizers) => Exited(exit, finalizers)
            }.flatMap {
              case Running(finalizers) =>
                for {
                  exits <- f(finalizers.map(_(exit)))
                  _     <- ZIO.done(Exit.collectAllPar(exits).getOrElse(Exit.unit))
                } yield ()
              case _ => UIO.unit
            }
        }
      }
    }
  }

  final class IfM[R, E](private val b: ZManaged[R, E, Boolean]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, A](
      onTrue: => ZManaged[R1, E1, A],
      onFalse: => ZManaged[R1, E1, A]
    ): ZManaged[R1, E1, A] =
      b.flatMap(b => if (b) onTrue else onFalse)
  }

  trait PreallocationScope {
    def apply[R, E, A](managed: ZManaged[R, E, A]): ZIO[R, E, Managed[Nothing, A]]
  }

  final class ProvideSomeLayer[R0 <: Has[_], -R, +E, +A](private val self: ZManaged[R, E, A]) extends AnyVal {
    def apply[E1 >: E, R1 <: Has[_]](
      layer: ZLayer[R0, E1, R1]
    )(implicit ev1: R0 with R1 <:< R, ev2: NeedsEnv[R], tagged: Tagged[R1]): ZManaged[R0, E1, A] =
      self.provideLayer[E1, R0, R0 with R1](ZLayer.identity[R0] ++ layer)
  }

  trait Scope {
    def apply[R, E, A](managed: ZManaged[R, E, A]): ZIO[R, E, (A, UIO[Any])]
  }

  /**
   * Submerges the error case of an `Either` into the `ZManaged`. The inverse
   * operation of `ZManaged.either`.
   */
  def absolve[R, E, A](v: ZManaged[R, E, Either[E, A]]): ZManaged[R, E, A] =
    v.flatMap(fromEither(_))

  /**
   * Create a managed that accesses the environment.
   */
  def access[R]: AccessPartiallyApplied[R] =
    new AccessPartiallyApplied

  /**
   * Create a managed that accesses the environment.
   */
  def accessM[R]: AccessMPartiallyApplied[R] =
    new AccessMPartiallyApplied

  /**
   * Create a managed that accesses the environment.
   */
  def accessManaged[R]: AccessManagedPartiallyApplied[R] =
    new AccessManagedPartiallyApplied

  /**
   * Creates new [[ZManaged]] from wrapped [[Reservation]].
   */
  def apply[R, E, A](reservation: ZIO[R, E, Reservation[R, E, A]]): ZManaged[R, E, A] =
    new ZManaged(reservation)

  /**
   * Evaluate each effect in the structure from left to right, and collect the
   * results. For a parallel version, see `collectAllPar`.
   */
  def collectAll[R, E, A](ms: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, List[A]] =
    foreach(ms)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure from left to right, and discard the
   * results. For a parallel version, see `collectAllPar_`.
   */
  def collectAll_[R, E, A](ms: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, Unit] =
    foreach_(ms)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and collect the
   * results. For a sequential version, see `collectAll`.
   */
  def collectAllPar[R, E, A](as: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, List[A]] =
    foreachPar(as)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and discard the
   * results. For a sequential version, see `collectAll_`.
   */
  def collectAllPar_[R, E, A](as: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, Unit] =
    foreachPar_(as)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and collect the
   * results. For a sequential version, see `collectAll`.
   *
   * Unlike `CollectAllPar`, this method will use at most `n` fibers.
   */
  def collectAllParN[R, E, A](n: Int)(as: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, List[A]] =
    foreachParN(n)(as)(ZIO.identityFn)

  /**
   * Evaluate each effect in the structure in parallel, and discard the
   * results. For a sequential version, see `collectAll_`.
   *
   * Unlike `CollectAllPar_`, this method will use at most `n` fibers.
   */
  def collectAllParN_[R, E, A](n: Int)(as: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, Unit] =
    foreachParN_(n)(as)(ZIO.identityFn)

  /**
   * Returns an effect that dies with the specified `Throwable`.
   * This method can be used for terminating a fiber because a defect has been
   * detected in the code.
   */
  def die(t: => Throwable): ZManaged[Any, Nothing, Nothing] =
    halt(Cause.die(t))

  /**
   * Returns an effect that dies with a [[java.lang.RuntimeException]] having the
   * specified text message. This method can be used for terminating a fiber
   * because a defect has been detected in the code.
   */
  def dieMessage(message: => String): ZManaged[Any, Nothing, Nothing] =
    die(new RuntimeException(message))

  /**
   * Returns an effect from a lazily evaluated [[zio.Exit]] value.
   */
  def done[E, A](r: => Exit[E, A]): ZManaged[Any, E, A] =
    ZManaged.fromEffect(ZIO.done(r))

  /**
   * Lifts a synchronous side-effect into a `ZManaged[R, Throwable, A]`,
   * translating any thrown exceptions into typed failed effects.
   */
  def effect[A](r: => A): ZManaged[Any, Throwable, A] =
    ZManaged.fromEffect(ZIO.effect(r))

  /**
   * Lifts a by-name, pure value into a Managed.
   */
  def effectTotal[A](r: => A): ZManaged[Any, Nothing, A] =
    ZManaged.fromEffect(ZIO.effectTotal(r))

  /**
   * Accesses the whole environment of the effect.
   */
  def environment[R]: ZManaged[R, Nothing, R] =
    ZManaged.fromEffect(ZIO.environment)

  /**
   * Returns an effect that models failure with the specified  error.
   * The moral equivalent of `throw` for pure code.
   */
  def fail[E](error: => E): ZManaged[Any, E, Nothing] =
    halt(Cause.fail(error))

  /**
   * Returns an effect that succeeds with the `Fiber.Id` of the caller.
   */
  val fiberId: ZManaged[Any, Nothing, Fiber.Id] = ZManaged.fromEffect(ZIO.fiberId)

  /**
   * Creates an effect that only executes the provided finalizer as its
   * release action.
   */
  def finalizer[R](f: ZIO[R, Nothing, Any]): ZManaged[R, Nothing, Unit] =
    finalizerExit(_ => f)

  /**
   * Creates an effect that only executes the provided function as its
   * release action.
   */
  def finalizerExit[R](f: Exit[Any, Any] => ZIO[R, Nothing, Any]): ZManaged[R, Nothing, Unit] =
    ZManaged.reserve(Reservation(ZIO.unit, f))

  /**
   * Creates an effect that executes a finalizer stored in a [[FinalizerRef]].
   * The `FinalizerRef` is yielded as the result of the effect, allowing for
   * control flows that require mutating finalizers.
   */
  def finalizerRef[R](
    initial: Exit[Any, Any] => ZIO[R, Nothing, Any]
  ): ZManaged[R, Nothing, FinalizerRef[R]] =
    ZManaged {
      for {
        finalizers <- FinalizerRef.make(initial)
        reservation = Reservation(
          acquire = ZIO.succeedNow(finalizers),
          release = finalizers.runAll
        )
      } yield reservation
    }

  /**
   * Returns an effectful function that extracts out the first element of a
   * tuple.
   */
  def first[A, B]: ZManaged[(A, B), Nothing, A] = fromFunction(_._1)

  /**
   * Returns an effect that performs the outer effect first, followed by the
   * inner effect, yielding the value of the inner effect.
   *
   * This method can be used to "flatten" nested effects.
   */
  def flatten[R, E, A](zManaged: ZManaged[R, E, ZManaged[R, E, A]]): ZManaged[R, E, A] =
    zManaged.flatMap(scala.Predef.identity)

  /**
   * Returns an effect that performs the outer effect first, followed by the
   * inner effect, yielding the value of the inner effect.
   *
   * This method can be used to "flatten" nested effects.
   */
  def flattenM[R, E, A](zManaged: ZManaged[R, E, ZIO[R, E, A]]): ZManaged[R, E, A] =
    zManaged.mapM(scala.Predef.identity)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns the results in a new `List[B]`.
   *
   * For a parallel version of this method, see `foreachPar`.
   * If you do not need the results, see `foreach_` for a more efficient implementation.
   */
  def foreach[R, E, A1, A2](as: Iterable[A1])(f: A1 => ZManaged[R, E, A2]): ZManaged[R, E, List[A2]] =
    as.foldRight[ZManaged[R, E, List[A2]]](succeedNow(Nil))((a, m) => f(a).zipWith(m)(_ :: _))

  /**
   * Applies the function `f` if the argument is non-empty and
   * returns the results in a new `Option[A2]`.
   */
  final def foreach[R, E, A1, A2](in: Option[A1])(f: A1 => ZManaged[R, E, A2]): ZManaged[R, E, Option[A2]] =
    in.fold[ZManaged[R, E, Option[A2]]](succeed(None))(f(_).map(Some(_)))

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and returns
   * the result in a new `List[B]` using the specified execution strategy.
   */
  final def foreachExec[R, E, A, B](
    as: Iterable[A]
  )(exec: ExecutionStrategy)(f: A => ZManaged[R, E, B]): ZManaged[R, E, List[B]] =
    exec match {
      case ExecutionStrategy.Parallel     => ZManaged.foreachPar(as)(f)
      case ExecutionStrategy.ParallelN(n) => ZManaged.foreachParN(n)(as)(f)
      case ExecutionStrategy.Sequential   => ZManaged.foreach(as)(f)
    }

  /**
   * Applies the function `f` to each element of the `Iterable[A]` in parallel,
   * and returns the results in a new `List[B]`.
   *
   * For a sequential version of this method, see `foreach`.
   */
  def foreachPar[R, E, A1, A2](
    as: Iterable[A1]
  )(
    f: A1 => ZManaged[R, E, A2]
  ): ZManaged[R, E, List[A2]] =
    as.foldRight[ZManaged[R, E, List[A2]]](ZManaged.succeedNow(List())) {
      case (a, man) =>
        f(a).zipWithPar(man)(_ :: _)
    }

  /**
   * Applies the function `f` to each element of the `Iterable[A]` in parallel,
   * and returns the results in a new `List[B]`.
   *
   * Unlike `foreachPar`, this method will use at most up to `n` fibers.
   *
   */
  def foreachParN[R, E, A1, A2](
    n: Int
  )(
    as: Iterable[A1]
  )(
    f: A1 => ZManaged[R, E, A2]
  ): ZManaged[R, E, List[A2]] =
    mergeAllParN[R, E, A2, Vector[A2]](n)(as.map(f))(Vector())((acc, a2) => acc :+ a2).map(_.toList)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and runs
   * produced effects sequentially.
   *
   * Equivalent to `foreach(as)(f).unit`, but without the cost of building
   * the list of results.
   */
  def foreach_[R, E, A](as: Iterable[A])(f: A => ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    ZManaged.succeedNow(as.iterator).flatMap { i =>
      def loop: ZManaged[R, E, Unit] =
        if (i.hasNext) f(i.next) *> loop
        else ZManaged.unit
      loop
    }

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and runs
   * produced effects in parallel, discarding the results.
   *
   * For a sequential version of this method, see `foreach_`.
   */
  def foreachPar_[R, E, A](as: Iterable[A])(f: A => ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    as.foldLeft(unit: ZManaged[R, E, Unit])((acc, a) => acc.zipParLeft(f(a)))

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and runs
   * produced effects in parallel, discarding the results.
   *
   * Unlike `foreachPar_`, this method will use at most up to `n` fibers.
   */
  def foreachParN_[R, E, A](
    n: Int
  )(
    as: Iterable[A]
  )(
    f: A => ZManaged[R, E, Any]
  ): ZManaged[R, E, Unit] =
    mergeAllParN[R, E, Any, Unit](n)(as.map(f))(())((_, _) => ())

  /**
   * Creates a [[ZManaged]] from an `AutoCloseable` resource. The resource's `close`
   * method will be used as the release action.
   */
  def fromAutoCloseable[R, E, A <: AutoCloseable](fa: ZIO[R, E, A]): ZManaged[R, E, A] =
    ZManaged.make(fa)(a => UIO(a.close()))

  /**
   * Lifts a ZIO[R, E, A] into ZManaged[R, E, A] with no release action. The
   * effect will be performed interruptibly.
   */
  def fromEffect[R, E, A](fa: ZIO[R, E, A]): ZManaged[R, E, A] =
    ZManaged(IO.succeedNow(Reservation(fa, _ => IO.unit)))

  /**
   * Lifts a ZIO[R, E, A] into ZManaged[R, E, A] with no release action. The
   * effect will be performed uninterruptibly. You usually want the [[ZManaged.fromEffect]]
   * variant.
   */
  def fromEffectUninterruptible[R, E, A](fa: ZIO[R, E, A]): ZManaged[R, E, A] =
    ZManaged(fa.map(a => Reservation(UIO.succeedNow(a), _ => UIO.unit)))

  /**
   * Lifts an `Either` into a `ZManaged` value.
   */
  def fromEither[E, A](v: => Either[E, A]): ZManaged[Any, E, A] =
    effectTotal(v).flatMap(_.fold(fail(_), succeedNow))

  /**
   * Lifts a function `R => A` into a `ZManaged[R, Nothing, A]`.
   */
  def fromFunction[R, A](f: R => A): ZManaged[R, Nothing, A] = ZManaged.fromEffect(ZIO.environment[R]).map(f)

  /**
   * Lifts an effectful function whose effect requires no environment into
   * an effect that requires the input to the function.
   */
  def fromFunctionM[R, E, A](f: R => ZManaged[Any, E, A]): ZManaged[R, E, A] = flatten(fromFunction(f))

  /**
   * Returns an effect that models failure with the specified `Cause`.
   */
  def halt[E](cause: => Cause[E]): ZManaged[Any, E, Nothing] =
    ZManaged.fromEffect(ZIO.halt(cause))

  /**
   * Returns the identity effectful function, which performs no effects
   */
  def identity[R]: ZManaged[R, Nothing, R] = fromFunction(scala.Predef.identity)

  /**
   * Runs `onTrue` if the result of `b` is `true` and `onFalse` otherwise.
   */
  def ifM[R, E](b: ZManaged[R, E, Boolean]): ZManaged.IfM[R, E] =
    new ZManaged.IfM(b)

  /**
   * Returns an effect that is interrupted as if by the fiber calling this
   * method.
   */
  val interrupt: ZManaged[Any, Nothing, Nothing] =
    ZManaged.fromEffect(ZIO.descriptor).flatMap(d => halt(Cause.interrupt(d.id)))

  /**
   * Returns an effect that is interrupted as if by the specified fiber.
   */
  def interruptAs(fiberId: => Fiber.Id): ZManaged[Any, Nothing, Nothing] =
    halt(Cause.interrupt(fiberId))

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
  def iterate[R, E, S](initial: S)(cont: S => Boolean)(body: S => ZManaged[R, E, S]): ZManaged[R, E, S] =
    if (cont(initial)) body(initial).flatMap(iterate(_)(cont)(body))
    else ZManaged.succeedNow(initial)

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
    initial: S
  )(cont: S => Boolean, inc: S => S)(body: S => ZManaged[R, E, A]): ZManaged[R, E, List[A]] =
    if (cont(initial))
      body(initial).flatMap(a => loop(inc(initial))(cont, inc)(body).map(as => a :: as))
    else
      ZManaged.succeedNow(List.empty[A])

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
  def loop_[R, E, S](
    initial: S
  )(cont: S => Boolean, inc: S => S)(body: S => ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    if (cont(initial)) body(initial) *> loop_(inc(initial))(cont, inc)(body)
    else ZManaged.unit

  /**
   * Lifts a `ZIO[R, E, A]` into `ZManaged[R, E, A]` with a release action.
   * The acquire and release actions will be performed uninterruptibly.
   */
  def make[R, R1 <: R, E, A](acquire: ZIO[R, E, A])(release: A => ZIO[R1, Nothing, Any]): ZManaged[R1, E, A] =
    ZManaged(acquire.map(r => Reservation(IO.succeedNow(r), _ => release(r))))

  /**
   * Lifts a synchronous effect into `ZManaged[R, Throwable, A]` with a release action.
   * The acquire and release actions will be performed uninterruptibly.
   */
  def makeEffect[A](acquire: => A)(release: A => Any): ZManaged[Any, Throwable, A] =
    make(Task(acquire))(a => Task(release(a)).orDie)

  /**
   * Lifts a `ZIO[R, E, A]` into `ZManaged[R, E, A]` with a release action that handles `Exit`.
   * The acquire and release actions will be performed uninterruptibly.
   */
  def makeExit[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => ZIO[R, Nothing, Any]): ZManaged[R, E, A] =
    ZManaged(acquire.map(r => Reservation(IO.succeedNow(r), e => release(r, e))))

  /**
   * Lifts a ZIO[R, E, A] into ZManaged[R, E, A] with a release action.
   * The acquire action will be performed interruptibly, while release
   * will be performed uninterruptibly.
   */
  def makeInterruptible[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: A => ZIO[R, Nothing, Any]): ZManaged[R, E, A] =
    ZManaged.fromEffect(acquire).onExitFirst(_.foreach(release))

  /**
   * Sequentially zips the specified effects using the specified combiner
   * function.
   */
  def mapN[R, E, A, B, C](zManaged1: ZManaged[R, E, A], zManaged2: ZManaged[R, E, B])(
    f: (A, B) => C
  ): ZManaged[R, E, C] =
    zManaged1.zipWith(zManaged2)(f)

  /**
   * Sequentially zips the specified effects using the specified combiner
   * function.
   */
  def mapN[R, E, A, B, C, D](
    zManaged1: ZManaged[R, E, A],
    zManaged2: ZManaged[R, E, B],
    zManaged3: ZManaged[R, E, C]
  )(f: (A, B, C) => D): ZManaged[R, E, D] =
    for {
      a <- zManaged1
      b <- zManaged2
      c <- zManaged3
    } yield f(a, b, c)

  /**
   * Sequentially zips the specified effects using the specified combiner
   * function.
   */
  def mapN[R, E, A, B, C, D, F](
    zManaged1: ZManaged[R, E, A],
    zManaged2: ZManaged[R, E, B],
    zManaged3: ZManaged[R, E, C],
    zManaged4: ZManaged[R, E, D]
  )(f: (A, B, C, D) => F): ZManaged[R, E, F] =
    for {
      a <- zManaged1
      b <- zManaged2
      c <- zManaged3
      d <- zManaged4
    } yield f(a, b, c, d)

  /**
   * Returns an effect that executes the specified effects in parallel,
   * combining their results with the specified `f` function. If any effect
   * fails, then the other effects will be interrupted.
   */
  def mapParN[R, E, A, B, C](zManaged1: ZManaged[R, E, A], zManaged2: ZManaged[R, E, B])(
    f: (A, B) => C
  ): ZManaged[R, E, C] =
    zManaged1.zipWithPar(zManaged2)(f)

  /**
   * Returns an effect that executes the specified effects in parallel,
   * combining their results with the specified `f` function. If any effect
   * fails, then the other effects will be interrupted.
   */
  def mapParN[R, E, A, B, C, D](
    zManaged1: ZManaged[R, E, A],
    zManaged2: ZManaged[R, E, B],
    zManaged3: ZManaged[R, E, C]
  )(f: (A, B, C) => D): ZManaged[R, E, D] =
    (zManaged1 <&> zManaged2 <&> zManaged3).map {
      case ((a, b), c) => f(a, b, c)
    }

  /**
   * Returns an effect that executes the specified effects in parallel,
   * combining their results with the specified `f` function. If any effect
   * fails, then the other effects will be interrupted.
   */
  def mapParN[R, E, A, B, C, D, F](
    zManaged1: ZManaged[R, E, A],
    zManaged2: ZManaged[R, E, B],
    zManaged3: ZManaged[R, E, C],
    zManaged4: ZManaged[R, E, D]
  )(f: (A, B, C, D) => F): ZManaged[R, E, F] =
    (zManaged1 <&> zManaged2 <&> zManaged3 <&> zManaged4).map {
      case (((a, b), c), d) => f(a, b, c, d)
    }

  /**
   * Merges an `Iterable[IO]` to a single IO, working sequentially.
   */
  def mergeAll[R, E, A, B](in: Iterable[ZManaged[R, E, A]])(zero: B)(f: (B, A) => B): ZManaged[R, E, B] =
    in.foldLeft[ZManaged[R, E, B]](succeedNow(zero))(_.zipWith(_)(f))

  /**
   * Merges an `Iterable[IO]` to a single IO, working in parallel.
   */
  def mergeAllPar[R, E, A, B](in: Iterable[ZManaged[R, E, A]])(zero: B)(f: (B, A) => B): ZManaged[R, E, B] =
    in.foldLeft[ZManaged[R, E, B]](succeedNow(zero))(_.zipWithPar(_)(f))

  /**
   * Merges an `Iterable[IO]` to a single IO, working in parallel.
   *
   * Unlike `mergeAllPar`, this method will use at most up to `n` fibers.
   *
   * This is not implemented in terms of ZIO.foreach / ZManaged.zipWithPar as otherwise all reservation phases would always run, causing unnecessary work
   */
  def mergeAllParN[R, E, A, B](
    n: Int
  )(
    in: Iterable[ZManaged[R, E, A]]
  )(
    zero: B
  )(
    f: (B, A) => B
  ): ZManaged[R, E, B] =
    ZManaged[R, E, B] {
      Ref.make[List[Exit[Any, Any] => ZIO[R, Nothing, Any]]](Nil).map { finalizers =>
        Reservation(
          Queue.unbounded[(ZManaged[R, E, A], Promise[E, A])].flatMap { queue =>
            val worker = queue.take.flatMap {
              case (a, prom) =>
                ZIO.uninterruptibleMask { restore =>
                  a.reserve
                    .flatMap(res => finalizers.update(res.release :: _).as(res))
                    .flatMap(res => restore(res.acquire))
                }.foldCauseM(
                  _.failureOrCause.fold(prom.fail, prom.halt),
                  prom.succeed
                )
            }
            ZIO.foreach(1 to n)(_ => worker.forever.fork).flatMap { fibers =>
              (for {
                proms <- ZIO.foreach(in) { a =>
                          for {
                            prom <- Promise.make[E, A]
                            _    <- queue.offer((a, prom))
                          } yield prom
                        }
                b <- proms.foldLeft[ZIO[R, E, B]](ZIO.succeedNow(zero))((acc, prom) => acc.zipWith(prom.await)(f))
              } yield b).ensuring((queue.shutdown *> ZIO.foreach_(fibers)(_.interrupt)).uninterruptible)
            }
          },
          exitU =>
            for {
              fs    <- finalizers.get
              exits <- ZIO.foreach(fs)(_(exitU).run)
            } yield Exit.collectAllPar(exits)
        )
      }
    }

  /**
   * Returns a `ZManaged` that never acquires a resource.
   */
  val never: ZManaged[Any, Nothing, Nothing] = ZManaged.fromEffect(ZIO.never)

  /**
   * Creates a scope in which resources can be safely preallocated.
   */
  val preallocationScope: Managed[Nothing, PreallocationScope] =
    scope.map { allocate =>
      new PreallocationScope {
        def apply[R, E, A](managed: ZManaged[R, E, A]) =
          allocate(managed).map {
            case (res, release) =>
              ZManaged.make(ZIO.succeedNow(res))(_ => release)
          }
      }
    }

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working sequentially.
   */
  def reduceAll[R, E, A](a: ZManaged[R, E, A], as: Iterable[ZManaged[R, E, A]])(
    f: (A, A) => A
  ): ZManaged[R, E, A] =
    as.foldLeft[ZManaged[R, E, A]](a)(_.zipWith(_)(f))

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working in parallel.
   */
  def reduceAllPar[R, E, A](a: ZManaged[R, E, A], as: Iterable[ZManaged[R, E, A]])(
    f: (A, A) => A
  ): ZManaged[R, E, A] =
    as.foldLeft[ZManaged[R, E, A]](a)(_.zipWithPar(_)(f))

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working in parallel.
   *
   * Unlike `mergeAllPar`, this method will use at most up to `n` fibers.
   *
   * This is not implemented in terms of ZIO.foreach / ZManaged.zipWithPar as otherwise all reservation phases would always run, causing unnecessary work
   */
  def reduceAllParN[R, E, A](
    n: Long
  )(
    a1: ZManaged[R, E, A],
    as: Iterable[ZManaged[R, E, A]]
  )(
    f: (A, A) => A
  ): ZManaged[R, E, A] =
    ZManaged[R, E, A] {
      Ref.make[List[Exit[Any, Any] => ZIO[R, Nothing, Any]]](Nil).map { finalizers =>
        Reservation(
          Queue.unbounded[(ZManaged[R, E, A], Promise[E, A])].flatMap { queue =>
            val worker = queue.take.flatMap {
              case (a, prom) =>
                ZIO.uninterruptibleMask { restore =>
                  a.reserve
                    .flatMap(res => finalizers.update(res.release :: _).as(res))
                    .flatMap(res => restore(res.acquire))
                }.foldCauseM(
                  _.failureOrCause.fold(prom.fail, prom.halt),
                  prom.succeed
                )
            }
            ZIO.foreach(1L to n)(_ => worker.forever.fork).flatMap {
              fibers =>
                (for {
                  proms <- ZIO.foreach(as) { a =>
                            for {
                              prom <- Promise.make[E, A]
                              _    <- queue.offer((a, prom))
                            } yield prom
                          }
                  zero = ZIO.uninterruptibleMask { restore =>
                    a1.reserve
                      .flatMap(res => finalizers.update(res.release :: _).as(res))
                      .flatMap(res => restore(res.acquire))
                  }
                  result <- proms.foldLeft[ZIO[R, E, A]](zero)((acc, a) => acc.zipWith(a.await)(f))
                } yield result).ensuring((queue.shutdown *> ZIO.foreach_(fibers)(_.interrupt)).uninterruptible)
            }
          },
          exitU =>
            for {
              fs    <- finalizers.get
              exits <- ZIO.foreach(fs)(_(exitU).run)
            } yield Exit.collectAllPar(exits)
        )
      }
    }

  /**
   * Requires that the given `ZManaged[E, Option[A]]` contain a value. If there is no
   * value, then the specified error will be raised.
   */
  def require[R, E, A](error: => E): ZManaged[R, E, Option[A]] => ZManaged[R, E, A] =
    (zManaged: ZManaged[R, E, Option[A]]) => zManaged.flatMap(_.fold[ZManaged[R, E, A]](fail(error))(succeedNow))

  /**
   * Lifts a pure `Reservation[R, E, A]` into `ZManaged[R, E, A]`
   */
  def reserve[R, E, A](reservation: Reservation[R, E, A]): ZManaged[R, E, A] =
    ZManaged(ZIO.succeedNow(reservation))

  def sandbox[R, E, A](v: ZManaged[R, E, A]): ZManaged[R, Cause[E], A] =
    v.sandbox

  /**
   * Creates a scope in which resources can be safely allocated into together with a release action.
   */
  def scope: Managed[Nothing, Scope] =
    ZManaged {
      FinalizerRef.make(_ => UIO.unit).map { finalizers =>
        Reservation(
          acquire = ZIO.succeedNow {
            new Scope {
              override def apply[R, E, A](managed: ZManaged[R, E, A]) =
                ZIO.uninterruptibleMask { restore =>
                  for {
                    env      <- ZIO.environment[R]
                    res      <- managed.reserve
                    resource <- restore(res.acquire).onError(err => res.release(Exit.Failure(err)))
                    release  = res.release.andThen(_.provide(env))
                    _        <- finalizers.add(release)
                    done     = finalizers.run(release)(Exit.succeed(resource))
                  } yield (resource, done)
                }
            }
          },
          release = finalizers.runAll
        )
      }
    }

  /**
   * Returns an effectful function that extracts out the second element of a
   * tuple.
   */
  def second[A, B]: ZManaged[(A, B), Nothing, B] = fromFunction(_._2)

  /**
   * Accesses the specified service in the environment of the effect.
   */
  def service[A](implicit tagged: Tagged[A]): ZManaged[Has[A], Nothing, A] =
    ZManaged.access(_.get[A])

  /**
   * Accesses the specified services in the environment of the effect.
   */
  def services[A: Tagged, B: Tagged]: ZManaged[Has[A] with Has[B], Nothing, (A, B)] =
    ZManaged.access(r => (r.get[A], r.get[B]))

  /**
   * Accesses the specified services in the environment of the effect.
   */
  def services[A: Tagged, B: Tagged, C: Tagged]: ZManaged[Has[A] with Has[B] with Has[C], Nothing, (A, B, C)] =
    ZManaged.access(r => (r.get[A], r.get[B], r.get[C]))

  /**
   * Accesses the specified services in the environment of the effect.
   */
  def services[A: Tagged, B: Tagged, C: Tagged, D: Tagged]
    : ZManaged[Has[A] with Has[B] with Has[C] with Has[D], Nothing, (A, B, C, D)] =
    ZManaged.access(r => (r.get[A], r.get[B], r.get[C], r.get[D]))

  /**
   *  Returns an effect with the optional value.
   */
  def some[A](a: => A): UManaged[Option[A]] =
    succeed(Some(a))

  /**
   * Lifts a lazy, pure value into a Managed.
   */
  def succeed[A](r: => A): ZManaged[Any, Nothing, A] =
    ZManaged(IO.succeedNow(Reservation(IO.succeed(r), _ => IO.unit)))

  /**
   * Returns a lazily constructed Managed.
   */
  def suspend[R, E, A](zManaged: => ZManaged[R, E, A]): ZManaged[R, E, A] =
    flatten(effectTotal(zManaged))

  /**
   * Returns an effectful function that merely swaps the elements in a `Tuple2`.
   */
  def swap[A, B]: ZManaged[(A, B), Nothing, (B, A)] = fromFunction(_.swap)

  /**
   * Returns a ZManaged value that represents a managed resource that can be safely
   * swapped within the scope of the ZManaged. The function provided inside the
   * ZManaged can be used to switch the resource currently in use.
   *
   * When the resource is switched, the finalizer for the previous finalizer will
   * be executed uninterruptibly. If the effect executing inside the [[ZManaged#use]]
   * is interrupted, the finalizer for the resource currently in use is guaranteed
   * to execute.
   *
   * This constructor can be used to create an expressive control flow that uses
   * several instances of a managed resource. For example:
   * {{{
   * def makeWriter: Task[FileWriter]
   * trait FileWriter {
   *   def write(data: Int): Task[Unit]
   *   def close: UIO[Unit]
   * }
   *
   * val elements = List(1, 2, 3, 4)
   * val writingProgram =
   *   ZManaged.switchable[Any, Throwable, FileWriter].use { switchWriter =>
   *     ZIO.foreach_(elements) { element =>
   *       for {
   *         writer <- switchWriter(makeWriter.toManaged(_.close))
   *         _      <- writer.write(element)
   *       } yield ()
   *     }
   *   }
   * }}}
   */
  def switchable[R, E, A]: ZManaged[R, Nothing, ZManaged[R, E, A] => ZIO[R, E, A]] =
    for {
      fiberId      <- ZManaged.fiberId
      finalizerRef <- ZManaged.finalizerRef[R](_ => UIO.unit)
      switch = { (newResource: ZManaged[R, E, A]) =>
        ZIO.uninterruptibleMask { restore =>
          for {
            _ <- finalizerRef
                  .replace(_ => UIO.unit)
                  .flatMap(ZIO.foreach(_)(_(Exit.interrupt(fiberId))))
            reservation <- newResource.reserve
            _           <- finalizerRef.add(reservation.release)
            a           <- restore(reservation.acquire)
          } yield a
        }
      }
    } yield switch

  /**
   * Returns the effect resulting from mapping the success of this effect to unit.
   */
  val unit: ZManaged[Any, Nothing, Unit] = ZManaged.succeedNow(())

  /**
   * The moral equivalent of `if (!p) exp`
   */
  def unless[R, E](b: => Boolean)(zio: => ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    suspend(if (b) unit else zio.unit)

  /**
   * The moral equivalent of `if (!p) exp` when `p` has side-effects
   */
  def unlessM[R, E](b: ZManaged[R, E, Boolean])(zio: => ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    b.flatMap(b => if (b) unit else zio.unit)

  /**
   * The inverse operation to `sandbox`. Submerges the full cause of failure.
   */
  def unsandbox[R, E, A](v: ZManaged[R, Cause[E], A]): ZManaged[R, E, A] =
    v.mapErrorCause(_.flatten)

  /**
   * Unwraps a `ZManaged` that is inside a `ZIO`.
   */
  def unwrap[R, E, A](fa: ZIO[R, E, ZManaged[R, E, A]]): ZManaged[R, E, A] =
    ZManaged.fromEffect(fa).flatten

  /**
   * The moral equivalent of `if (p) exp`
   */
  def when[R, E](b: => Boolean)(zManaged: => ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    ZManaged.suspend(if (b) zManaged.unit else unit)

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given value, otherwise does nothing.
   */
  def whenCase[R, E, A](a: => A)(pf: PartialFunction[A, ZManaged[R, E, Any]]): ZManaged[R, E, Unit] =
    ZManaged.suspend(pf.applyOrElse(a, (_: A) => unit).unit)

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given effectful value, otherwise does nothing.
   */
  def whenCaseM[R, E, A](
    a: ZManaged[R, E, A]
  )(pf: PartialFunction[A, ZManaged[R, E, Any]]): ZManaged[R, E, Unit] =
    a.flatMap(whenCase(_)(pf))

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  def whenM[R, E](b: ZManaged[R, E, Boolean])(zManaged: => ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    b.flatMap(b => if (b) zManaged.unit else unit)

  private[zio] def succeedNow[A](r: A): ZManaged[Any, Nothing, A] =
    ZManaged(IO.succeedNow(Reservation(IO.succeedNow(r), _ => IO.unit)))
}
