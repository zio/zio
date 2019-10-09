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

package zio.test.mock

import scala.language.implicitConversions

import zio.{ IO, Managed, Ref, UIO, ZIO }
import zio.test.Assertion
import zio.test.mock.MockException.UnmetExpectationsException
import zio.test.mock.MockSpec.{ Continuation, State }

sealed trait MockSpec[-R, +E, +A] { self =>

  /**
   * Alias for `flatMap`.
   *
   * {{{
   * val mockSpec = MockSpec.expectOut_(Console.Service.GetStrLn)("foo") >>= { input => MockSpec.expectIn(Console.Service.PutStrLn)(equalTo(input)) }
   * }}}
   */
  final def >>=[R1 <: R, E1 >: E, B](k: A => MockSpec[R1, E1, B]): MockSpec[R1, E1, B] = flatMap(k)

  /**
   * Sequences the given mock specification after this specification, but ignores the
   * value produced by the it.
   */
  final def <*[R1 <: R, E1 >: E, B](that: => MockSpec[R1, E1, B]): MockSpec[R1, E1, A] =
    (self zip that).map(_._1)

  /**
   * A variant of `flatMap` that ignores the value produced by this mock specification.
   */
  final def *>[R1 <: R, E1 >: E, B](that: => MockSpec[R1, E1, B]): MockSpec[R1, E1, B] =
    (self zip that).map(_._2)

  /**
   * Sequentially zips this specification with another, combining the results into a tuple.
   */
  final def &&&[R1 <: R, E1 >: E, B](that: => MockSpec[R1, E1, B]): MockSpec[R1, E1, (A, B)] =
    self.flatMap(a => that.map(b => (a, b)))

  /**
   * Alias for `&&&`.
   */
  final def <*>[R1 <: R, E1 >: E, B](that: => MockSpec[R1, E1, B]): MockSpec[R1, E1, (A, B)] =
    self &&& that

  /**
   * Returns a mock specification that models the expectations for execution of mock, followed by
   * the passing of its value to the specified continuation function `k`,
   * followed by the specification that it returns.
   *
   * {{{
   * val mockSpec = MockSpec.expectOut_(Console.Service.GetStrLn)("foo").flatMap(input => MockSpec.expectIn(Console.Service.PutStrLn)(equalTo(input)))
   * }}}
   */
  final def flatMap[R1 <: R, E1 >: E, B](k: A => MockSpec[R1, E1, B]): MockSpec[R1, E1, B] =
    MockSpec.FlatMap(self, k)

  /**
   * Creates a mock service for this specification and lifts it into ZManaged.
   */
  final def managedEnv[R1 <: R](implicit mockable: Mockable[R1]): Managed[Nothing, R1] = {

    def unpack(
      state: MockSpec.State[R, E],
      spec: MockSpec[R, E, Any]
    ): UIO[Either[Any, Any]] = {

      def popContinuation: UIO[Option[Continuation[R, E]]] = state.continuationsRef.modify {
        case (head :: tail) => Some(head) -> tail
        case Nil            => None       -> Nil
      }

      UIO.succeed(spec).flatMap {
        case MockSpec.Succeed(value) =>
          popContinuation.flatMap {
            case Some(cont) => unpack(state, cont(value))
            case None       => UIO.succeed(Right(value))
          }

        case MockSpec.FlatMap(nestedSpec, continue) =>
          for {
            _   <- state.continuationsRef.update(continue.asInstanceOf[MockSpec.Continuation[R, E]] :: _)
            out <- unpack(state, nestedSpec)
          } yield out

        case mockedCall @ MockSpec.MockedCall(_, _) =>
          for {
            _ <- state.mockedCallsRef.update(_ :+ mockedCall.asInstanceOf[MockSpec.MockedCall[Any, Any, Any, Any]])
            out <- popContinuation.flatMap {
                    case Some(cont) => unpack(state, cont(()))
                    case None       => UIO.succeed(Right(()))
                  }
          } yield out
      }
    }

    val makeState =
      for {
        mockedCallsRef   <- Ref.make[List[MockSpec.MockedCall[Any, Any, Any, Any]]](Nil)
        continuationsRef <- Ref.make[List[Continuation[R, E]]](Nil)
      } yield State(mockedCallsRef, continuationsRef)

    val checkUnmetExpectations =
      (state: State[R, E]) =>
        state.mockedCallsRef.get
          .filterOrElse[Any, Nothing, Any](_.isEmpty) { mockedCalls =>
            val expectations = mockedCalls.map(_.expectation)
            ZIO.die(UnmetExpectationsException(expectations))
          }

    val makeEnvironment =
      (state: State[R, E]) =>
        for {
          _    <- unpack(state, self)
          mock = Mock.make(state.mockedCallsRef)
        } yield mockable.environment(mock)

    for {
      state <- Managed.make(makeState)(checkUnmetExpectations)
      env   <- Managed.fromEffect(makeEnvironment(state))
    } yield env
  }

  /**
   * Returns a mock specification whose success is mapped by the specified `f` function.
   */
  final def map[B](f: A => B): MockSpec[R, E, B] =
    flatMap(a => MockSpec.succeed(f(a)))

  /**
   * A named alias for `&&&` or `<*>`.
   */
  final def zip[R1 <: R, E1 >: E, B](that: => MockSpec[R1, E1, B]): MockSpec[R1, E1, (A, B)] =
    self &&& that

  /**
   * A named alias for `<*`.
   */
  final def zipLeft[R1 <: R, E1 >: E, B](that: => MockSpec[R1, E1, B]): MockSpec[R1, E1, A] =
    self <* that

  /**
   * A named alias for `*>`.
   */
  final def zipRight[R1 <: R, E1 >: E, B](that: => MockSpec[R1, E1, B]): MockSpec[R1, E1, B] =
    self *> that
}

object MockSpec {

  private[MockSpec] type Continuation[R, E] = Any => MockSpec[R, E, Any]

  private[MockSpec] final case class State[R, E](
    mockedCallsRef: Ref[List[MockedCall[Any, Any, Any, Any]]],
    continuationsRef: Ref[List[Continuation[R, E]]]
  )

  private[mock] final case class Succeed[A](
    value: A
  ) extends MockSpec[Any, Nothing, A]

  private[mock] final case class FlatMap[R, E, A, B](
    value: MockSpec[R, E, A],
    continue: A => MockSpec[R, E, B]
  ) extends MockSpec[R, E, B]

  private[mock] final case class MockedCall[R, E, A, B](
    expectation: Expectation[A, B],
    returns: A => IO[E, B]
  ) extends MockSpec[R, E, A]

  /**
   * Creates a mock specification expecting method `m` to be called with arguments
   * satisfying assertion `a` and returning value produced by function `f` using input arguments.
   */
  final def expect[R, E, A, B](m: Method[A, B])(a: Assertion[A])(f: A => B): MockSpec[R, E, A] =
    expectM[R, E, A, B](m)(a)(input => ZIO.effectTotal(f(input)))

  /**
   * Creates a mock specification expecting method `m` to be called with arguments
   * satisfying assertion `a` and returning value `v`.
   */
  final def expect_[R, E, A, B](m: Method[A, B])(a: Assertion[A])(v: => B): MockSpec[R, E, A] =
    expect(m)(a)(_ => v)

  /**
   * Creates a mock specification expecting method `m` to be called with any arguments
   * and returning value produced by function `f` using input arguments.
   */
  final def expectAny[R, E, B](m: Method[Any, B])(f: Any => B): MockSpec[R, E, Any] =
    expect(m)(Assertion.anything)(f)

  /**
   * Creates a mock specification expecting method `m` to be called with any arguments
   * and returning value `v`.
   */
  final def expectAny_[R, E, B](m: Method[Any, B])(v: => B): MockSpec[R, E, Any] =
    expect_(m)(Assertion.anything)(v)

  /**
   * Creates a mock specification expecting method `m` to be called with any arguments
   * and returning value produced by effect `f` using input arguments.
   */
  final def expectAnyM[R, E, B](m: Method[Any, B])(v: Any => IO[E, B]): MockSpec[R, E, Any] =
    expectM(m)(Assertion.anything)(v)

  /**
   * Creates a mock specification expecting method `m` to be called with any arguments
   * and returning value produced by effect `f`.
   */
  final def expectAnyM_[R, E, B](m: Method[Any, B])(f: => IO[E, B]): MockSpec[R, E, Any] =
    expectM(m)(Assertion.anything)(_ => f)

  /**
   * Creates a mock specification expecting method `m` to be called with arguments
   * satisfying assertion `a` and returning unit.
   */
  final def expectIn[R, E, A](m: Method[A, Unit])(a: Assertion[A]): MockSpec[R, E, A] =
    expect(m)(a)(_ => ())

  /**
   * Creates a mock specification expecting method `m` to be called with arguments
   * satisfying assertion `a` and returning value produced by effect `f` using input arguments.
   */
  final def expectM[R, E, A, B](m: Method[A, B])(a: Assertion[A])(f: A => IO[E, B]): MockSpec[R, E, A] =
    MockedCall(Expectation(m, a), f)

  /**
   * Creates a mock specification expecting method `m` to be called with arguments
   * satisfying assertion `a` and returning value produced by effect `f`.
   */
  final def expectM_[R, E, A, B](m: Method[A, B])(a: Assertion[A])(f: => IO[E, B]): MockSpec[R, E, A] =
    expectM(m)(a)(_ => f)

  /**
   * Creates a mock specification expecting method `m` to be called with no arguments
   * and returning value `v`.
   */
  final def expectOut[R, E, B](m: Method[Unit, B])(v: => B): MockSpec[R, E, Unit] =
    expect_(m)(Assertion.anything)(v)

  /**
   * Creates a mock specification expecting method `m` to be called with no arguments
   * and returning value produced by effect `f`.
   */
  final def expectOutM[R, E, B](m: Method[Unit, B])(f: => IO[E, B]): MockSpec[R, E, Unit] =
    expectM(m)(Assertion.anything)(_ => f)

  /**
   * Creates a mock specification that holds the final value `a` produced by specification.
   * Models completed specification with no further expectations.
   */
  final def succeed[A](a: A): MockSpec[Any, Nothing, A] =
    Succeed(a)

  /**
   * Automatically converts specification to managed environement.
   */
  implicit final def toManagedEnv[R, E, A](
    spec: MockSpec[R, E, A]
  )(implicit mockable: Mockable[R]): Managed[Nothing, R] =
    spec.managedEnv(mockable)
}
