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
   * Symbolic alias for join
   */
  def |||[R1, E1 >: E, A1 >: A](that: ZManaged[R1, E1, A1]): ZManaged[Either[R, R1], E1, A1] =
    for {
      either <- ZManaged.environment[Either[R, R1]]
      a1     <- either.fold(provide, that.provide)
    } yield a1

  /**
   * Symbolic alias for flatMap
   */
  def >>=[R1 <: R, E1 >: E, B](k: A => ZManaged[R1, E1, B]): ZManaged[R1, E1, B] =
    flatMap(k)

  /**
   * Symbolic alias for andThen
   */
  def >>>[R1 >: A, E1 >: E, B](that: ZManaged[R1, E1, B]): ZManaged[R, E1, B] =
    for {
      r  <- ZManaged.environment[R]
      r1 <- provide(r)
      a  <- that.provide(r1)
    } yield a

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
   * Operator alias for `orElse`.
   */
  def <>[R1 <: R, E2, A1 >: A](that: => ZManaged[R1, E2, A1])(implicit ev: CanFail[E]): ZManaged[R1, E2, A1] =
    orElse(that)

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
   * Symbolic alias for zipLeft.
   */
  def <*[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A] =
    flatMap(r => that.map(_ => r))

  /**
   * Symbolic alias for zip.
   */
  def <*>[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, (A, A1)] =
    zipWith(that)((_, _))

  def +++[R1, B, E1 >: E](that: ZManaged[R1, E1, B]): ZManaged[Either[R, R1], E1, Either[A, B]] =
    for {
      e <- ZManaged.environment[Either[R, R1]]
      r <- e.fold(map(Left(_)).provide(_), that.map(Right(_)).provide(_))
    } yield r

  /**
   * Symbolic alias for zipRight
   */
  def *>[R1 <: R, E1 >: E, A1](that: ZManaged[R1, E1, A1]): ZManaged[R1, E1, A1] =
    flatMap(_ => that)

  /**
   * Submerges the error case of an `Either` into the `ZManaged`. The inverse
   * operation of `ZManaged.either`.
   */
  def absolve[R1 <: R, E1, B](
    implicit ev: ZManaged[R, E, A] <:< ZManaged[R1, E1, Either[E1, B]]
  ): ZManaged[R1, E1, B] =
    ZManaged.absolve(ev(self))

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
        ZManaged.succeed
      )

  /**
   * Replaces the error value (if any) by the value provided.
   */
  def asError[E1](e1: => E1): ZManaged[R, E1, A] = mapError(_ => e1)

  /**
   * Executes the this effect and then provides its output as an environment to the second effect
   */
  def andThen[R1 >: A, E1 >: E, B](that: ZManaged[R1, E1, B]): ZManaged[R, E1, B] = self >>> that

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
    foldM(h, ZManaged.succeed)

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
    self.foldCauseM[R1, E2, A1](h, ZManaged.succeed)

  /**
   * Recovers from some or all of the error cases.
   */
  def catchSome[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[E, ZManaged[R1, E1, A1]]
  )(implicit ev: CanFail[E]): ZManaged[R1, E1, A1] =
    foldM(pf.applyOrElse[E, ZManaged[R1, E1, A1]](_, ZManaged.fail), ZManaged.succeed)

  /**
   * Recovers from some or all of the error Causes.
   */
  def catchSomeCause[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[Cause[E], ZManaged[R1, E1, A1]]
  ): ZManaged[R1, E1, A1] =
    foldCauseM(pf.applyOrElse[Cause[E], ZManaged[R1, E1, A1]](_, ZManaged.halt), ZManaged.succeed)

  /**
   * Fail with `e` if the supplied `PartialFunction` does not match, otherwise
   * succeed with the returned value.
   */
  def collect[E1 >: E, B](e: => E1)(pf: PartialFunction[A, B]): ZManaged[R, E1, B] =
    collectM(e)(pf.andThen(ZManaged.succeed(_)))

  /**
   * Fail with `e` if the supplied `PartialFunction` does not match, otherwise
   * continue with the returned value.
   */
  def collectM[R1 <: R, E1 >: E, B](e: => E1)(pf: PartialFunction[A, ZManaged[R1, E1, B]]): ZManaged[R1, E1, B] =
    self.flatMap { v =>
      pf.applyOrElse[A, ZManaged[R1, E1, B]](v, _ => ZManaged.fail(e))
    }

  /**
   * Executes the second effect and then provides its output as an environment to this effect
   */
  def compose[R1, E1 >: E](that: ZManaged[R1, E1, R]): ZManaged[R1, E1, A] =
    self <<< that

  /**
   * Maps this effect to the specified constant while preserving the
   * effects of this effect.
   */
  def as[B](b: => B): ZManaged[R, E, B] =
    map(_ => b)

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
      reserve.map { r =>
        r.copy(release = e => r.release(e).ensuring(f))
      }
    }

  /**
   * Ensures that `f` is executed when this ZManaged is finalized, before
   * the existing finalizer.
   *
   * For usecases that need access to the ZManaged's result, see [[ZManaged#onExitFirst]].
   */
  def ensuringFirst[R1 <: R](f: ZIO[R1, Nothing, Any]): ZManaged[R1, E, A] =
    ZManaged {
      reserve.map { r =>
        r.copy(release = e => f.ensuring(r.release(e)))
      }
    }

  /**
   * Returns a ZManaged that ignores errors raised by the acquire effect and
   * runs it repeatedly until it eventually succeeds.
   */
  def eventually(implicit ev: CanFail[E]): ZManaged[R, Nothing, A] =
    ZManaged {
      reserve.eventually.map { r =>
        Reservation(r.acquire.eventually, r.release)
      }
    }

  /**
   * Executes this effect and returns its value, if it succeeds, but otherwise
   * returns the specified value.
   */
  def fallback[A1 >: A](a: => A1)(implicit ev: CanFail[E]): ZManaged[R, Nothing, A1] =
    fold(_ => a, identity)

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
   * Flip the error and result
    **/
  def flip: ZManaged[R, A, E] =
    foldM(ZManaged.succeed, ZManaged.fail)

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
    foldM(failure.andThen(ZManaged.succeed), success.andThen(ZManaged.succeed))

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
  def fork: ZManaged[R, Nothing, Fiber[E, A]] =
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
        acquire = UIO.succeed(fiber),
        release = e => fiber.interrupt *> finalizer.get.flatMap(f => f(e))
      )
    }

  /**
   * Unwraps the optional success of this effect, but can fail with unit value.
   */
  def get[E1 >: E, B](implicit ev1: E1 =:= Nothing, ev2: A <:< Option[B]): ZManaged[R, Unit, B] =
    ZManaged.absolve(mapError(ev1).map(ev2(_).toRight(())))

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
   * Effectfully maps the resource acquired by this value.
   */
  def mapM[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): ZManaged[R1, E1, B] =
    ZManaged[R1, E1, B] {
      reserve.map { token =>
        token.copy(acquire = token.acquire.flatMap(f))
      }
    }

  /**
   * Returns an effect whose failure is mapped by the specified `f` function.
   */
  def mapError[E1](f: E => E1)(implicit ev: CanFail[E]): ZManaged[R, E1, A] =
    ZManaged(reserve.mapError(f).map(r => Reservation(r.acquire.mapError(f), r.release)))

  /**
   * Returns an effect whose full failure is mapped by the specified `f` function.
   */
  def mapErrorCause[E1](f: Cause[E] => Cause[E1]): ZManaged[R, E1, A] =
    ZManaged(reserve.mapErrorCause(f).map(r => Reservation(r.acquire.mapErrorCause(f), r.release)))

  def memoize: ZManaged[R, E, ZManaged[R, E, A]] =
    ZManaged {
      RefM.make[Option[(Reservation[R, E, A], Exit[E, A])]](None).map { ref =>
        val acquire1: ZIO[R, E, A] =
          ref.modify {
            case v @ Some((_, e)) => ZIO.succeed(e -> v)
            case None =>
              ZIO.uninterruptibleMask { restore =>
                self.reserve.flatMap { res =>
                  restore(res.acquire).run.map(e => e -> Some(res -> e))
                }
              }
          }.flatMap(ZIO.done)

        val acquire2: ZIO[R, E, ZManaged[R, E, A]] =
          ZIO.succeed(acquire1.toManaged_)

        val release2 = (_: Exit[_, _]) =>
          ref.updateSome {
            case Some((res, e)) => res.release(e).as(None)
          }

        Reservation(acquire2, release2)
      }
    }

  /**
   * Returns a new effect where the error channel has been merged into the
   * success channel to their common combined type.
   */
  def merge[A1 >: A](implicit ev1: E <:< A1, ev2: CanFail[E]): ZManaged[R, Nothing, A1] =
    self.foldM(e => ZManaged.succeed(ev1(e)), ZManaged.succeed)

  /**
   * Ensures that a cleanup function runs when this ZManaged is finalized, after
   * the existing finalizers.
   */
  def onExit[R1 <: R](cleanup: Exit[E, A] => ZIO[R1, Nothing, Any]): ZManaged[R1, E, A] =
    ZManaged {
      Ref.make[Exit[Any, Any] => ZIO[R1, Nothing, Any]](_ => UIO.unit).map { finalizer =>
        Reservation(
          acquire = ZIO.bracketExit(self.reserve)(
            (res, exitA: Exit[E, A]) => finalizer.set(exitU => res.release(exitU).ensuring(cleanup(exitA)))
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
          acquire = ZIO.bracketExit(self.reserve)(
            (res, exitA: Exit[E, A]) => finalizer.set(exitU => cleanup(exitA).ensuring(res.release(exitU)))
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
    mapError(f).catchAll(ZManaged.die)

  /**
   * Executes this effect and returns its value, if it succeeds, but
   * otherwise executes the specified effect.
   */
  def orElse[R1 <: R, E2, A1 >: A](that: => ZManaged[R1, E2, A1])(implicit ev: CanFail[E]): ZManaged[R1, E2, A1] =
    foldM(_ => that, ZManaged.succeed)

  /**
   * Returns an effect that will produce the value of this effect, unless it
   * fails, in which case, it will produce the value of the specified effect.
   */
  def orElseEither[R1 <: R, E2, B](
    that: => ZManaged[R1, E2, B]
  )(implicit ev: CanFail[E]): ZManaged[R1, E2, Either[A, B]] =
    foldM(_ => that.map(Right[A, B]), a => ZManaged.succeed(Left[A, B](a)))

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
      } yield ZManaged.make(ZIO.succeed(resource))(_ => res.release(Exit.Success(resource)).provide(env))
    }

  /**
   * Preallocates the managed resource inside an outer managed, resulting in a ZManaged that reserves and acquires immediately and cannot fail.
   */
  def preallocateManaged: ZManaged[R, E, Managed[Nothing, A]] =
    ZManaged.finalizerRef[R](_ => ZIO.unit).mapM { finalizer =>
      ZIO.uninterruptibleMask { restore =>
        for {
          env      <- ZIO.environment[R]
          res      <- reserve
          _        <- finalizer.set(res.release)
          resource <- restore(res.acquire)
        } yield ZManaged.make(ZIO.succeed(resource))(
          _ => res.release(Exit.Success(resource)).provide(env) *> finalizer.set(_ => ZIO.unit)
        )
      }
    }

  /**
   * Provides the `ZManaged` effect with its required environment, which eliminates
   * its dependency on `R`.
   */
  def provide(r: R)(implicit ev: NeedsEnv[R]): ZManaged[Any, E, A] =
    provideSome(_ => r)

  /**
   * Provides some of the environment required to run this effect,
   * leaving the remainder `R0`.
   */
  def provideSome[R0](f: R0 => R)(implicit ev: NeedsEnv[R]): ZManaged[R0, E, A] =
    ZManaged(reserve.provideSome(f).map(r => Reservation(r.acquire.provideSome(f), e => r.release(e).provideSome(f))))

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
    catchAll { e =>
      pf.lift(e).fold[ZManaged[R, E1, A]](ZManaged.die(f(e)))(ZManaged.fail)
    }

  /**
   * Retries with the specified retry policy.
   * Retries are done following the failure of the original `io` (up to a fixed maximum with
   * `once` or `recurs` for example), so that that `io.retry(Schedule.once)` means
   * "execute `io` and in case of failure, try again once".
   */
  def retry[R1 <: R, E1 >: E, S](policy: Schedule[R1, E1, S])(implicit ev: CanFail[E]): ZManaged[R1, E1, A] = {
    def loop[B](zio: ZIO[R, E1, B], state: policy.State): ZIO[R1, E1, (policy.State, B)] =
      zio.foldM(
        err =>
          policy
            .update(err, state)
            .foldM(
              _ => ZIO.fail(err),
              loop(zio, _)
            ),
        succ => ZIO.succeed((state, succ))
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
      cause => ZManaged.succeed(Exit.halt(cause)),
      succ => ZManaged.succeed(Exit.succeed(succ))
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
   * Returns an effect that effectfully peeks at the acquired resource.
   */
  def tap[R1 <: R, E1 >: E](f: A => ZManaged[R1, E1, Any]): ZManaged[R1, E1, A] =
    flatMap(a => f(a).as(a))

  /**
   * Returns an effect that effectfully peeks at the failure or success of the acquired resource.
   */
  final def tapBoth[R1 <: R, E1 >: E](f: E => ZManaged[R1, E1, Any], g: A => ZManaged[R1, E1, Any])(
    implicit ev: CanFail[E]
  ): ZManaged[R1, E1, A] =
    foldM(
      e => f(e) *> ZManaged.fail(e),
      a => g(a).as(a)
    )

  /**
   * Returns an effect that effectfully peeks at the failure of the acquired resource.
   */
  final def tapError[R1 <: R, E1 >: E](f: E => ZManaged[R1, E1, Any])(implicit ev: CanFail[E]): ZManaged[R1, E1, A] =
    tapBoth(f, ZManaged.succeed)

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
          .raceWith(ZIO.sleep(d))(
            {
              case (leftDone, rightFiber) =>
                rightFiber.interrupt.flatMap(
                  _ =>
                    leftDone.foldM(
                      ZIO.halt,
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
          Reservation(ZIO.succeed(None), release)
        case _ => Reservation(ZIO.succeed(None), _ => ZIO.unit)
      }
    }

  }

  /**
   * Return unit while running the effect
   */
  lazy val unit: ZManaged[R, E, Unit] = as(())

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
  def when(b: Boolean): ZManaged[R, E, Unit] =
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
  def zipWithPar[R1 <: R, E1 >: E, A1, A2](that: ZManaged[R1, E1, A1])(f0: (A, A1) => A2): ZManaged[R1, E1, A2] =
    ZManaged[R1, E1, A2] {
      Ref.make[List[Exit[Any, Any] => ZIO[R1, Nothing, Any]]](Nil).map { finalizers =>
        Reservation(
          acquire = {
            val left = ZIO.uninterruptibleMask { restore =>
              reserve
                .flatMap(res => finalizers.update(fs => res.release :: fs).as(res))
                .flatMap(res => restore(res.acquire))
            }
            val right = ZIO.uninterruptibleMask { restore =>
              that.reserve
                .flatMap(res => finalizers.update(fs => res.release :: fs).as(res))
                .flatMap(res => restore(res.acquire))
            }
            left.zipWithPar(right)(f0)
          },
          release = exitU =>
            for {
              fs    <- finalizers.get
              exits <- ZIO.foreachPar(fs)(_(exitU).run)
              _     <- ZIO.done(Exit.collectAllPar(exits).getOrElse(Exit.unit))
            } yield ()
        )
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

  trait Scope {
    def apply[R, E, A](managed: ZManaged[R, E, A]): ZIO[R, E, Managed[Nothing, A]]
  }

  /**
   * Returns an effectful function that extracts out the first element of a
   * tuple.
   */
  def _1[R, E, A, B](implicit ev: R <:< (A, B)): ZManaged[R, E, A] = fromFunction(_._1)

  /**
   * Returns an effectful function that extracts out the second element of a
   * tuple.
   */
  def _2[R, E, A, B](implicit ev: R <:< (A, B)): ZManaged[R, E, B] = fromFunction(_._2)

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
   * Evaluate each effect in the structure from left to right, and collect
   * the results. For a parallel version, see `collectAllPar`.
   */
  def collectAll[R, E, A1, A2](ms: Iterable[ZManaged[R, E, A2]]): ZManaged[R, E, List[A2]] =
    foreach(ms)(scala.Predef.identity)

  /**
   * Evaluate each effect in the structure in parallel, and collect
   * the results. For a sequential version, see `collectAll`.
   */
  def collectAllPar[R, E, A](as: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, List[A]] =
    foreachPar(as)(scala.Predef.identity)

  /**
   * Evaluate each effect in the structure in parallel, and collect
   * the results. For a sequential version, see `collectAll`.
   *
   * Unlike `CollectAllPar`, this method will use at most `n` fibers.
   */
  def collectAllParN[R, E, A](n: Int)(as: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, List[A]] =
    foreachParN(n)(as)(scala.Predef.identity)

  /**
   * Returns an effect that dies with the specified `Throwable`.
   * This method can be used for terminating a fiber because a defect has been
   * detected in the code.
   */
  def die(t: Throwable): ZManaged[Any, Nothing, Nothing] = halt(Cause.die(t))

  /**
   * Returns an effect that dies with a [[java.lang.RuntimeException]] having the
   * specified text message. This method can be used for terminating a fiber
   * because a defect has been detected in the code.
   */
  def dieMessage(message: String): ZManaged[Any, Nothing, Nothing] = die(new RuntimeException(message))

  /**
   * Returns an effect from a [[zio.Exit]] value.
   */
  def done[E, A](r: Exit[E, A]): ZManaged[Any, E, A] =
    ZManaged.fromEffect(ZIO.done(r))

  /**
   * Lifts a by-name, pure value into a Managed.
   */
  def effectTotal[R, A](r: => A): ZManaged[R, Nothing, A] =
    ZManaged.fromEffect(ZIO.effectTotal(r))

  /**
   * Accesses the whole environment of the effect.
   */
  def environment[R]: ZManaged[R, Nothing, R] =
    ZManaged.fromEffect(ZIO.environment)

  /**
   * Returns an effect that models failure with the specified error.
   * The moral equivalent of `throw` for pure code.
   */
  def fail[E](error: E): ZManaged[Any, E, Nothing] =
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
   * Creates an effect that executes a finalizer stored in a [[Ref]]. The `Ref`
   * is yielded as the result of the effect, allowing for control flows that require
   * mutating finalizers.
   */
  def finalizerRef[R](
    initial: Exit[Any, Any] => ZIO[R, Nothing, Any]
  ): ZManaged[R, Nothing, Ref[Exit[Any, Any] => ZIO[R, Nothing, Any]]] =
    ZManaged {
      for {
        ref <- Ref.make(initial)
        reservation = Reservation(
          acquire = ZIO.succeed(ref),
          release = e => ref.get.flatMap(_.apply(e))
        )
      } yield reservation
    }

  /**
   * Returns an effect that performs the outer effect first, followed by the
   * inner effect, yielding the value of the inner effect.
   *
   * This method can be used to "flatten" nested effects.
    **/
  def flatten[R, E, A](zManaged: ZManaged[R, E, ZManaged[R, E, A]]): ZManaged[R, E, A] =
    zManaged.flatMap(scala.Predef.identity)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns the results in a new `List[B]`.
   *
   * For a parallel version of this method, see `foreachPar`.
   */
  def foreach[R, E, A1, A2](as: Iterable[A1])(f: A1 => ZManaged[R, E, A2]): ZManaged[R, E, List[A2]] =
    as.foldRight[ZManaged[R, E, List[A2]]](succeed(Nil)) { (a, m) =>
      f(a).zipWith(m)(_ :: _)
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
    as.foldRight[ZManaged[R, E, List[A2]]](ZManaged.succeed(List())) {
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
    ZManaged.succeed(as.iterator).flatMap { i =>
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
    as.foldLeft(unit: ZManaged[R, E, Unit]) { (acc, a) =>
      acc.zipParLeft(f(a))
    }

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
    mergeAllParN[R, E, Any, Unit](n)(as.map(f))(()) { (_, _) =>
      ()
    }

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
    ZManaged(IO.succeed(Reservation(fa, _ => IO.unit)))

  /**
   * Lifts a ZIO[R, E, A] into ZManaged[R, E, A] with no release action. The
   * effect will be performed uninterruptibly. You usually want the [[ZManaged.fromEffect]]
   * variant.
   */
  def fromEffectUninterruptible[R, E, A](fa: ZIO[R, E, A]): ZManaged[R, E, A] =
    ZManaged(fa.map(a => Reservation(UIO.succeed(a), _ => UIO.unit)))

  /**
   * Lifts an `Either` into a `ZManaged` value.
   */
  def fromEither[E, A](v: => Either[E, A]): ZManaged[Any, E, A] =
    effectTotal(v).flatMap(_.fold(fail, succeed))

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
  def halt[E](cause: Cause[E]): ZManaged[Any, E, Nothing] =
    ZManaged.fromEffect(ZIO.halt(cause))

  /**
   * Returns the identity effectful function, which performs no effects
   */
  def identity[R]: ZManaged[R, Nothing, R] = fromFunction(scala.Predef.identity)

  /**
   * Returns an effect that is interrupted as if by the fiber calling this
   * method.
   */
  val interrupt: ZManaged[Any, Nothing, Nothing] =
    ZManaged.fromEffect(ZIO.descriptor).flatMap(d => halt(Cause.interrupt(d.id)))

  /**
   * Returns an effect that is interrupted as if by the specified fiber.
   */
  def interruptAs(fiberId: Fiber.Id): ZManaged[Any, Nothing, Nothing] =
    halt(Cause.interrupt(fiberId))

  /**
   * Lifts a `ZIO[R, E, A]` into `ZManaged[R, E, A]` with a release action.
   * The acquire and release actions will be performed uninterruptibly.
   */
  def make[R, R1 <: R, E, A](acquire: ZIO[R, E, A])(release: A => ZIO[R1, Nothing, Any]): ZManaged[R1, E, A] =
    ZManaged(acquire.map(r => Reservation(IO.succeed(r), _ => release(r))))

  /**
   * Lifts a synchronous effect into `ZManaged[R, Throwable, A]` with a release action.
   * The acquire and release actions will be performed uninterruptibly.
   */
  def makeEffect[R, A](acquire: => A)(release: A => Any): ZManaged[R, Throwable, A] =
    make(Task(acquire))(a => Task(release(a)).orDie)

  /**
   * Lifts a `ZIO[R, E, A]` into `ZManaged[R, E, A]` with a release action that handles `Exit`.
   * The acquire and release actions will be performed uninterruptibly.
   */
  def makeExit[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => ZIO[R, Nothing, Any]): ZManaged[R, E, A] =
    ZManaged(acquire.map(r => Reservation(IO.succeed(r), e => release(r, e))))

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
    in.foldLeft[ZManaged[R, E, B]](ZManaged.succeed(zero))((acc, a) => acc.zip(a).map(f.tupled))

  /**
   * Merges an `Iterable[IO]` to a single IO, working in parallel.
   */
  def mergeAllPar[R, E, A, B](in: Iterable[ZManaged[R, E, A]])(zero: B)(f: (B, A) => B): ZManaged[R, E, B] =
    in.foldLeft[ZManaged[R, E, B]](ZManaged.succeed(zero))((acc, a) => acc.zipPar(a).map(f.tupled))

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
                b <- proms.foldLeft[ZIO[R, E, B]](ZIO.succeed(zero)) { (acc, prom) =>
                      acc.zip(prom.await).map(f.tupled)
                    }
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
  def scope[R]: ZManaged[R, Nothing, Scope] =
    ZManaged {
      // we abuse the fact that Function1 will use reference equality
      Ref.make(Set.empty[Exit[Any, Any] => ZIO[R, Nothing, Any]]).map { finalizers =>
        Reservation(
          acquire = ZIO.succeed {
            new Scope {
              override def apply[R, E, A](managed: ZManaged[R, E, A]) =
                ZIO.uninterruptibleMask { restore =>
                  for {
                    env      <- ZIO.environment[R]
                    res      <- managed.reserve
                    resource <- restore(res.acquire).onError(err => res.release(Exit.Failure(err)))
                    release  = res.release.andThen(_.provide(env))
                    _        <- finalizers.update(_ + release)
                  } yield ZManaged
                    .make(ZIO.succeed(resource))(
                      _ => release(Exit.Success(resource)).ensuring(finalizers.update(_ - release))
                    )
                }
            }
          },
          release = exitU =>
            for {
              fs    <- finalizers.get
              exits <- ZIO.foreachPar(fs)(_(exitU).run)
              _     <- ZIO.done(Exit.collectAllPar(exits).getOrElse(Exit.unit))
            } yield ()
        )
      }
    }

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working sequentially.
   */
  def reduceAll[R, E, A](a: ZManaged[R, E, A], as: Iterable[ZManaged[R, E, A]])(
    f: (A, A) => A
  ): ZManaged[R, E, A] =
    as.foldLeft[ZManaged[R, E, A]](a) { (l, r) =>
      l.zip(r).map(f.tupled)
    }

  /**
   * Reduces an `Iterable[IO]` to a single `IO`, working in parallel.
   */
  def reduceAllPar[R, E, A](a: ZManaged[R, E, A], as: Iterable[ZManaged[R, E, A]])(
    f: (A, A) => A
  ): ZManaged[R, E, A] =
    as.foldLeft[ZManaged[R, E, A]](a) { (l, r) =>
      l.zipPar(r).map(f.tupled)
    }

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
                  result <- proms.foldLeft[ZIO[R, E, A]](zero) { (acc, a) =>
                             acc.zip(a.await).map(f.tupled)
                           }
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
  def require[R, E, A](error: E): ZManaged[R, E, Option[A]] => ZManaged[R, E, A] =
    (zManaged: ZManaged[R, E, Option[A]]) => zManaged.flatMap(_.fold[ZManaged[R, E, A]](fail(error))(succeed))

  /**
   * Lifts a pure `Reservation[R, E, A]` into `ZManaged[R, E, A]`
   */
  def reserve[R, E, A](reservation: Reservation[R, E, A]): ZManaged[R, E, A] =
    ZManaged(ZIO.succeed(reservation))

  def sandbox[R, E, A](v: ZManaged[R, E, A]): ZManaged[R, Cause[E], A] =
    v.sandbox

  /**
   *  Alias for [[ZManaged.collectAll]]
   */
  def sequence[R, E, A1, A2](ms: Iterable[ZManaged[R, E, A2]]): ZManaged[R, E, List[A2]] =
    collectAll[R, E, A1, A2](ms)

  /**
   *  Alias for [[ZManaged.collectAllPar]]
   */
  def sequencePar[R, E, A](as: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, List[A]] =
    collectAllPar[R, E, A](as)

  /**
   *  Alias for [[ZManaged.collectAllParN]]
   */
  def sequenceParN[R, E, A](n: Int)(as: Iterable[ZManaged[R, E, A]]): ZManaged[R, E, List[A]] =
    collectAllParN[R, E, A](n)(as)

  /**
   * Lifts a strict, pure value into a Managed.
   */
  def succeed[R, A](r: A): ZManaged[R, Nothing, A] =
    ZManaged(IO.succeed(Reservation(IO.succeed(r), _ => IO.unit)))

  /**
   * Returns a lazily constructed Managed.
   */
  def suspend[R, E, A](zManaged: => ZManaged[R, E, A]): ZManaged[R, E, A] =
    flatten(effectTotal(zManaged))

  /**
   * Returns an effectful function that merely swaps the elements in a `Tuple2`.
   */
  def swap[R, E, A, B](implicit ev: R <:< (A, B)): ZManaged[R, E, (B, A)] = fromFunction(_.swap)

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
                  .modify(f => (f, _ => UIO.unit))
                  .flatMap(f => f(Exit.interrupt(fiberId)))
            reservation <- newResource.reserve
            _           <- finalizerRef.set(reservation.release)
            a           <- restore(reservation.acquire)
          } yield a
        }
      }
    } yield switch

  /**
   * Alias for [[ZManaged.foreach]]
   */
  def traverse[R, E, A1, A2](as: Iterable[A1])(f: A1 => ZManaged[R, E, A2]): ZManaged[R, E, List[A2]] =
    foreach[R, E, A1, A2](as)(f)

  /**
   * Alias for [[ZManaged.foreach_]]
   */
  def traverse_[R, E, A](as: Iterable[A])(f: A => ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    foreach_[R, E, A](as)(f)

  /**
   * Alias for [[ZManaged.foreachPar]]
   */
  def traversePar[R, E, A1, A2](
    as: Iterable[A1]
  )(
    f: A1 => ZManaged[R, E, A2]
  ): ZManaged[R, E, List[A2]] =
    foreachPar[R, E, A1, A2](as)(f)

  /**
   * Alias for [[ZManaged.foreachPar_]]
   */
  def traversePar_[R, E, A](as: Iterable[A])(f: A => ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    foreachPar_[R, E, A](as)(f)

  /**
   * Alias for [[ZManaged.foreachParN]]
   */
  def traverseParN[R, E, A1, A2](
    n: Int
  )(
    as: Iterable[A1]
  )(
    f: A1 => ZManaged[R, E, A2]
  ): ZManaged[R, E, List[A2]] =
    foreachParN[R, E, A1, A2](n)(as)(f)

  /**
   * Alias for [[ZManaged.foreachParN_]]
   */
  def traverseParN_[R, E, A](
    n: Int
  )(
    as: Iterable[A]
  )(
    f: A => ZManaged[R, E, Any]
  ): ZManaged[R, E, Unit] =
    foreachParN_[R, E, A](n)(as)(f)

  /**
   * Returns the effect resulting from mapping the success of this effect to unit.
   */
  val unit: ZManaged[Any, Nothing, Unit] = ZManaged.succeed(())

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
  def when[R, E](b: Boolean)(zManaged: ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    if (b) zManaged.unit else unit

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given value, otherwise does nothing.
   */
  def whenCase[R, E, A](a: A)(pf: PartialFunction[A, ZManaged[R, E, Any]]): ZManaged[R, E, Unit] =
    pf.applyOrElse(a, (_: A) => unit).unit

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
  def whenM[R, E](b: ZManaged[R, E, Boolean])(zManaged: ZManaged[R, E, Any]): ZManaged[R, E, Unit] =
    b.flatMap(b => if (b) zManaged.unit else unit)

}
