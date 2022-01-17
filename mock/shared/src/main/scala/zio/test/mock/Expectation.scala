/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

package zio.mock

import zio.mock.Expectation.{And, Chain, Exactly, Or, Repeated}
import zio.mock.Result.{Fail, Succeed}
import zio.mock.internal.{ExpectationState, MockException, MockState, ProxyFactory}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.Assertion
import zio.{IO, Managed, Tag, ULayer, URLayer, ZLayer, ZTraceElement}

import scala.language.implicitConversions

/**
 * An `Expectation[R]` is an immutable tree structure that represents
 * expectations on environment `R`.
 */
sealed abstract class Expectation[R: Tag] { self =>

  /**
   * Operator alias for `and`.
   */
  def &&[R0: Tag](that: Expectation[R0]): Expectation[R with R0] =
    and[R0](that)

  /**
   * Operator alias for `or`.
   */
  def ||[R0: Tag](that: Expectation[R0]): Expectation[R with R0] =
    or[R0](that)

  /**
   * Operator alias for `andThen`.
   */
  def ++[R0: Tag](that: Expectation[R0]): Expectation[R with R0] =
    andThen[R0](that)

  /**
   * Compose two expectations, producing a new expectation to satisfy both.
   *
   * {{ val mockEnv = MockClock.sleep(equalTo(1.second)) and
   * MockConsole.readLine(value("foo")) }}
   */
  def and[R0: Tag](that: Expectation[R0]): Expectation[R with R0] =
    (self, that) match {
      case (And.Items(xs1), And.Items(xs2)) =>
        And(self.mock.compose ++ that.mock.compose)(xs1 ++ xs2).asInstanceOf[Expectation[R with R0]]
      case (And.Items(xs), _) =>
        And(self.mock.compose ++ that.mock.compose)(xs :+ that).asInstanceOf[Expectation[R with R0]]
      case (_, And.Items(xs)) =>
        And(self.mock.compose ++ that.mock.compose)(self :: xs).asInstanceOf[Expectation[R with R0]]
      case _ => And(self.mock.compose ++ that.mock.compose)(self :: that :: Nil).asInstanceOf[Expectation[R with R0]]
    }

  /**
   * Compose two expectations, producing a new expectation to satisfy both
   * sequentially.
   *
   * {{ val mockEnv = MockClock.sleep(equalTo(1.second)) andThen
   * MockConsole.readLine(value("foo")) }}
   */
  def andThen[R0: Tag](that: Expectation[R0]): Expectation[R with R0] =
    (self, that) match {
      case (Chain.Items(xs1), Chain.Items(xs2)) =>
        Chain(self.mock.compose ++ that.mock.compose)(xs1 ++ xs2).asInstanceOf[Expectation[R with R0]]
      case (Chain.Items(xs), _) =>
        Chain(self.mock.compose ++ that.mock.compose)(xs :+ that).asInstanceOf[Expectation[R with R0]]
      case (_, Chain.Items(xs)) =>
        Chain(self.mock.compose ++ that.mock.compose)(self :: xs).asInstanceOf[Expectation[R with R0]]
      case _ => Chain(self.mock.compose ++ that.mock.compose)(self :: that :: Nil).asInstanceOf[Expectation[R with R0]]
    }

  /**
   * Lower-bounded variant of `repeated`, produces a new expectation to satisfy
   * itself sequentially at least given number of times.
   */
  def atLeast(min: Int): Expectation[R] =
    Repeated(self, min to -1)

  /**
   * Upper-bounded variant of `repeated`, produces a new expectation to satisfy
   * itself sequentially at most given number of times.
   */
  def atMost(max: Int): Expectation[R] =
    Repeated(self, 0 to max)

  /**
   * Alias for `atMost(1)`, produces a new expectation to satisfy itself at most
   * once.
   */
  def optional: Expectation[R] =
    atMost(1)

  /**
   * Produces a new expectation to satisfy itself exactly the given number of
   * times.
   */
  def exactly(times: Int): Expectation[R] =
    Exactly(self, times)

  /**
   * Alias for `exactly(2)`, produces a new expectation to satisfy itself
   * exactly two times.
   */
  def twice: Expectation[R] =
    exactly(2)

  /**
   * Alias for `exactly(3)`, produces a new expectation to satisfy itself
   * exactly three times.
   */
  def thrice: Expectation[R] =
    exactly(3)

  /**
   * Compose two expectations, producing a new expectation to satisfy one of
   * them.
   *
   * {{ val mockEnv = MockClock.sleep(equalTo(1.second)) or
   * MockConsole.readLine(value("foo")) }}
   */
  def or[R0: Tag](that: Expectation[R0]): Expectation[R with R0] =
    (self, that) match {
      case (Or.Items(xs1), Or.Items(xs2)) =>
        Or(self.mock.compose ++ that.mock.compose)(xs1 ++ xs2).asInstanceOf[Expectation[R with R0]]
      case (Or.Items(xs), _) =>
        Or(self.mock.compose ++ that.mock.compose)(xs :+ that).asInstanceOf[Expectation[R with R0]]
      case (_, Or.Items(xs)) =>
        Or(self.mock.compose ++ that.mock.compose)(self :: xs).asInstanceOf[Expectation[R with R0]]
      case _ => Or(self.mock.compose ++ that.mock.compose)(self :: that :: Nil).asInstanceOf[Expectation[R with R0]]
    }

  /**
   * Repeats this expectation within given bounds, producing a new expectation
   * to satisfy itself sequentially given number of times.
   *
   * {{{val mockEnv = MockClock.sleep(equalTo(1.second)).repeats(1, 5)}}}
   *
   * NOTE: once another repetition starts executing, it must be completed in
   * order to satisfy the composite expectation. For example (A ++ B).repeats(1,
   * 2) will be satisfied by either A->B (one repetition) or A->B->A->B (two
   * repetitions), but will fail on A->B->A (incomplete second repetition).
   */
  def repeats(range: Range): Expectation[R] =
    Repeated(self, range)

  /**
   * Converts this expectation to ZLayer.
   */
  def toLayer(implicit trace: ZTraceElement): ULayer[R] = Expectation.toLayer(self)

  /**
   * Invocations log.
   */
  private[mock] val invocations: List[Int]

  /**
   * Environment to which expectation belongs.
   */
  private[mock] val mock: Mock[R]

  /**
   * Mock execution state.
   */
  private[mock] val state: ExpectationState
}

object Expectation {

  import ExpectationState._

  /**
   * Models expectations conjunction on environment `R`. Expectations are
   * checked in the order they are provided, meaning that earlier expectations
   * may shadow later ones.
   */
  private[mock] case class And[R: Tag](
    children: List[Expectation[R]],
    state: ExpectationState,
    invocations: List[Int],
    mock: Mock.Composed[R]
  ) extends Expectation[R]

  private[mock] object And {

    def apply[R: Tag](compose: URLayer[Proxy, R])(children: List[Expectation[_]]): And[R] =
      And(
        children.asInstanceOf[List[Expectation[R]]],
        if (children.exists(_.state.isFailed)) Unsatisfied else Satisfied,
        List.empty,
        Mock.Composed(compose)
      )

    object Items {

      private[mock] def unapply[R](and: And[R]): Option[(List[Expectation[R]])] =
        Some(and.children)
    }
  }

  /**
   * Models a call in environment `R` that takes input arguments `I` and returns
   * an effect that may fail with an error `E` or produce a single `A`.
   */
  private[mock] case class Call[R: Tag, I, E, A](
    capability: Capability[R, I, E, A],
    assertion: Assertion[I],
    returns: I => IO[E, A],
    state: ExpectationState,
    invocations: List[Int]
  ) extends Expectation[R] {
    val mock: Mock[R] = capability.mock
  }

  private[mock] object Call {

    def apply[R: Tag, I, E, A](
      capability: Capability[R, I, E, A],
      assertion: Assertion[I],
      returns: I => IO[E, A]
    ): Call[R, I, E, A] =
      Call(capability, assertion, returns, Unsatisfied, List.empty)
  }

  /**
   * Models sequential expectations on environment `R`.
   */
  private[mock] case class Chain[R: Tag](
    children: List[Expectation[R]],
    state: ExpectationState,
    invocations: List[Int],
    mock: Mock.Composed[R]
  ) extends Expectation[R]

  private[mock] object Chain {

    def apply[R: Tag](compose: URLayer[Proxy, R])(children: List[Expectation[_]]): Chain[R] =
      Chain(
        children.asInstanceOf[List[Expectation[R]]],
        if (children.exists(_.state.isFailed)) Unsatisfied else Satisfied,
        List.empty,
        Mock.Composed(compose)
      )

    object Items {

      private[mock] def unapply[R](chain: Chain[R]): Option[(List[Expectation[R]])] =
        Some(chain.children)
    }
  }

  private[mock] case class NoCalls[R: Tag](mock: Mock[R]) extends Expectation[R] {

    override private[mock] val invocations: List[Int] = Nil

    override private[mock] val state: ExpectationState = Satisfied

  }

  /**
   * Models expectations disjunction on environment `R`. Expectations are
   * checked in the order they are provided, meaning that earlier expectations
   * may shadow later ones.
   */
  private[mock] case class Or[R: Tag](
    children: List[Expectation[R]],
    state: ExpectationState,
    invocations: List[Int],
    mock: Mock.Composed[R]
  ) extends Expectation[R]

  private[mock] object Or {

    def apply[R: Tag](compose: URLayer[Proxy, R])(children: List[Expectation[_]]): Or[R] =
      Or(
        children.asInstanceOf[List[Expectation[R]]],
        if (children.exists(_.state == Satisfied)) Satisfied else Unsatisfied,
        List.empty,
        Mock.Composed(compose)
      )

    object Items {

      private[mock] def unapply[R](or: Or[R]): Option[(List[Expectation[R]])] =
        Some(or.children)
    }
  }

  /**
   * Models expectation repetition on environment `R`.
   */
  private[mock] final case class Repeated[R: Tag](
    child: Expectation[R],
    range: Range,
    state: ExpectationState,
    invocations: List[Int],
    started: Int,
    completed: Int
  ) extends Expectation[R] {
    val mock: Mock[R] = child.mock
  }

  private[mock] object Repeated {

    def apply[R: Tag](child: Expectation[R], range: Range): Repeated[R] =
      if (range.step <= 0) throw MockException.InvalidRangeException(range)
      else Repeated(child, range, if (range.start == 0) Satisfied else Unsatisfied, List.empty, 0, 0)
  }

  /**
   * Models expectation exactitude on environment `R`.
   */
  private[mock] final case class Exactly[R: Tag](
    child: Expectation[R],
    times: Int,
    state: ExpectationState,
    invocations: List[Int],
    completed: Int
  ) extends Expectation[R] {
    val mock: Mock[R] = child.mock
  }

  private[mock] object Exactly {

    def apply[R: Tag](child: Expectation[R], times: Int): Exactly[R] =
      Exactly(child, times, if (times == 0) Saturated else Unsatisfied, List.empty, 0)
  }

  /**
   * Expectation result failing with `E`.
   */
  def failure[E](failure: E)(implicit trace: ZTraceElement): Fail[Any, E] = Fail(_ => IO.fail(failure))

  /**
   * Maps the input arguments `I` to expectation result failing with `E`.
   */
  def failureF[I, E](f: I => E)(implicit trace: ZTraceElement): Fail[I, E] = Fail(i => IO.succeed(i).map(f).flip)

  /**
   * Effectfully maps the input arguments `I` to expectation result failing with
   * `E`.
   */
  def failureM[I, E](f: I => IO[E, Nothing])(implicit trace: ZTraceElement): Fail[I, E] = Fail(f)

  /**
   * Expectation result computing forever.
   */
  def never(implicit trace: ZTraceElement): Succeed[Any, Nothing] = valueM(_ => IO.never)

  /**
   * Expectation result succeeding with `Unit`.
   */
  def unit(implicit trace: ZTraceElement): Succeed[Any, Unit] = value(())

  /**
   * Expectation result succeeding with `A`.
   */
  def value[A](value: A)(implicit trace: ZTraceElement): Succeed[Any, A] = Succeed(_ => IO.succeed(value))

  /**
   * Maps the input arguments `I` to expectation result succeeding with `A`.
   */
  def valueF[I, A](f: I => A)(implicit trace: ZTraceElement): Succeed[I, A] = Succeed(i => IO.succeed(i).map(f))

  /**
   * Effectfully maps the input arguments `I` expectation result succeeding with
   * `A`.
   */
  def valueM[I, A](f: I => IO[Nothing, A]): Succeed[I, A] = Succeed(f)

  /**
   * Implicitly converts Expectation to ZLayer mock environment.
   */
  implicit def toLayer[R: Tag](
    trunk: Expectation[R]
  )(implicit trace: ZTraceElement): ULayer[R] =
    ZLayer.fromManagedEnvironment(
      for {
        state <- Managed.acquireReleaseWith(MockState.make(trunk))(MockState.checkUnmetExpectations)
        env   <- (ProxyFactory.mockProxy(state) >>> trunk.mock.compose).build
      } yield env
    )
}
