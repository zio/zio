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

package zio.test.mock

import scala.language.implicitConversions

import com.github.ghik.silencer.silent

import zio.test.Assertion
import zio.test.mock.Expectation.{ AnyCall, Call, Empty, FlatMap, Next, State }
import zio.test.mock.MockException.UnmetExpectationsException
import zio.test.mock.ReturnExpectation.{ Fail, Succeed }
import zio.{ Has, IO, Managed, Ref, UIO, ZIO, ZLayer }
import zio.{ IO, Managed, Ref, UIO, ZIO }

/**
 * An `Expectation[-M, +E, +A]` is an immutable data structure that represents
 * expectations on module `M` capabilities.
 *
 * This structure is a monad, because we need the sequential composability and
 * in Scala we get the convenient for-comprehension syntax for free.
 *
 *  - `Empty`   models expectation for no calls, the monadic `unit` value
 *  - `Call`    models a call on `M` modules capability that takes arguments `I`
 *              and returns an effect that may fail with an error `E` or produce
 *              a single `A`
 *  - `FlatMap` models sequential composition of expectations
 *
 * The whole structure is not supposed to be consumed directly by the end user,
 * instead it should be converted into a mocked environment (wrapped in layer)
 * either explicitly via `toLayer` method or via implicit conversion.
 */
sealed trait Expectation[-M, +E, +A] { self =>

  /**
   * A variant of `flatMap` that ignores the value returned by this expectation.
   */
  final def *>[M1 <: M, E1 >: E, B](that: Expectation[M1, E1, B]): Expectation[M1, E1, B] = flatMap(_ => that)

  /**
   * Returns an expectation that models the execution of this expectation,
   * followed by passing of its value to the specified continuation function `k`,
   * followed by the expectation that it returns.
   *
   * {{
   * val mockClock = (MockClock.sleep(equalTo(1.second)) returns unit).flatMap(_ => MockClock.nanoTime returns value(5L))
   * }}
   */
  final def flatMap[M1 <: M, E1 >: E, B](k: A => Expectation[M1, E1, B]): Expectation[M1, E1, B] =
    FlatMap(self, k)

  /**
   * Converts this Expectation to ZManaged mock environment.
   */
  final def toLayer[M1 <: M](implicit mockable: Mockable[M1]): ZLayer.NoDeps[Nothing, Has[M1]] = {

    def extract(
      state: State[M, E],
      expectation: Expectation[M, E, Any]
    ): UIO[Either[Any, Any]] = {

      def popNextExpectation: UIO[Option[Next[M, E]]] = state.nextRef.modify {
        case (head :: tail) => Some(head) -> tail
        case Nil            => None       -> Nil
      }

      UIO.succeed(expectation).flatMap {
        case Empty =>
          popNextExpectation.flatMap {
            case Some(next) => extract(state, next(null))
            case None       => UIO.succeed(Right(()))
          }

        case FlatMap(current, next) =>
          for {
            _   <- state.nextRef.update(next.asInstanceOf[Next[M, E]] :: _)
            out <- extract(state, current)
          } yield out

        case call @ Call(_, _, _) =>
          for {
            _ <- state.callsRef.update(_ :+ call.asInstanceOf[AnyCall])
            out <- popNextExpectation.flatMap {
                    case Some(next) => extract(state, next(null))
                    case None       => UIO.succeed(Right(()))
                  }
          } yield out
      }
    }

    val makeState =
      for {
        callsRef <- Ref.make(List.empty[AnyCall])
        nextRef  <- Ref.make(List.empty[Next[M, E]])
      } yield State(callsRef, nextRef)

    val checkUnmetExpectations =
      (state: State[M, E]) =>
        state.callsRef.get
          .filterOrElse[Any, Nothing, Any](_.isEmpty) { calls =>
            val expectations = calls.map(call => call.method -> call.assertion)
            ZIO.die(UnmetExpectationsException(expectations))
          }

    val makeEnvironment =
      (state: State[M, E]) =>
        for {
          _    <- extract(state, self)
          mock = Mock.make(state.callsRef)
        } yield mockable.environment(mock)

    ZLayer.fromManaged(for {
      state <- Managed.make(makeState)(checkUnmetExpectations)
      env   <- Managed.fromEffect(makeEnvironment(state))
    } yield env)
  }

  /**
   * Returns empty expectation.
   *
   * This is required for the for-comprehension syntax.
   */
  @silent("parameter value f in method map is never used")
  final def map[B](f: A => B): Expectation[M, E, Nothing] =
    flatMap(_ => Empty)

  /**
   * A named alias for `*>`
   */
  final def zipRight[M1 <: M, E1 >: E, B](that: Expectation[M1, E1, B]): Expectation[M1, E1, B] = self *> that
}

object Expectation {

  /**
   * Returns a return expectation to fail with `E`.
   */
  def failure[E](failure: E): Fail[Any, E] = Fail(_ => IO.fail(failure))

  /**
   * Maps the input arguments `I` to a return expectation to fail with `E`.
   */
  def failureF[I, E](f: I => E): Fail[I, E] = Fail(i => IO.succeed(i).map(f).flip)

  /**
   * Effectfully maps the input arguments `I` to a return expectation to fail with `E`.
   */
  def failureM[I, E](f: I => IO[E, Nothing]): Fail[I, E] = Fail(f)

  /**
   * Returns a return expectation to compute forever.
   */
  def never: Succeed[Any, Nothing] = valueM(_ => IO.never)

  /**
   * Returns an expectation for no calls on module `M`.
   */
  def nothing[M]: Expectation[M, Nothing, Nothing] = Empty

  /**
   * Returns a return expectation to succeed with `Unit`.
   */
  def unit: Succeed[Any, Unit] = value(())

  /**
   * Returns a return expectation to succeed with `A`.
   */
  def value[A](value: A): Succeed[Any, A] = Succeed(_ => IO.succeed(value))

  /**
   * Maps the input arguments `I` to a return expectation to succeed with `A`.
   */
  def valueF[I, A](f: I => A): Succeed[I, A] = Succeed(i => IO.succeed(i).map(f))

  /**
   * Effectfully maps the input arguments `I` to a return expectation to succeed with `A`.
   */
  def valueM[I, A](f: I => IO[Nothing, A]): Succeed[I, A] = Succeed(f)

  /**
   * Implicitly converts Expectation to ZManaged mock environment.
   */
  implicit def toLayer[M: Mockable, E, A](
    expectation: Expectation[M, E, A]
  ): ZLayer.NoDeps[Nothing, Has[M]] = expectation.toLayer

  private[Expectation] type AnyCall      = Call[Any, Any, Any, Any]
  private[Expectation] type Next[-M, +E] = Any => Expectation[M, E, Any]

  private[Expectation] final case class State[M, E](
    callsRef: Ref[List[AnyCall]],
    nextRef: Ref[List[Next[M, E]]]
  )

  /**
   * Models expectation for no calls on module `M`.
   *
   * The the `unit` value for Expectation monad.
   */
  private[mock] case object Empty extends Expectation[Any, Nothing, Nothing]

  /**
   * Models a call on module `M` capability that takes input arguments `I` and returns an effect
   * that may fail with an error `E` or produce a single `A`.
   */
  private[mock] final case class Call[-M, I, +E, +A](
    method: Method[M, I, A],
    assertion: Assertion[I],
    returns: I => IO[E, A]
  ) extends Expectation[M, E, A]

  /**
   * Models sequential expectations on module `M`.
   *
   * The `A` value in `next` is not used, it's only required to fit the flatMap signature,
   * which we want to be able to use the for-comprehension syntax.
   */
  private[mock] final case class FlatMap[-M, +E, A, +B](
    current: Expectation[M, E, A],
    next: A => Expectation[M, E, B]
  ) extends Expectation[M, E, B]
}
