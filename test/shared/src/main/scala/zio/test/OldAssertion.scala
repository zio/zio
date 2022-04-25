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

package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.AssertionZIO.RenderParam
import zio.{Cause, Exit, ZIO, ZTraceElement}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * An `OldAssertion[A]` is capable of producing assertion results on an `A`. As
 * a proposition, assertions compose using logical conjunction and disjunction,
 * and can be negated.
 */
final class OldAssertion[-A] private (
  val render: OldAssertion.Render,
  val run: (=> A) => AssertResult
) extends AssertionZIO[A]
    with ((=> A) => AssertResult) { self =>
  import zio.test.OldAssertion.Render._

  def runZIO: (=> A) => AssertResultZIO = a => BoolAlgebraZIO(ZIO.succeed(run(a))(ZTraceElement.empty))

  /**
   * Returns a new assertion that succeeds only if both assertions succeed.
   */
  def &&[A1 <: A](that: => OldAssertion[A1]): OldAssertion[A1] =
    new OldAssertion(infix(param(self), "&&", param(that)), actual => self.run(actual) && that.run(actual))

  /**
   * A symbolic alias for `label`.
   */
  override def ??(string: String): OldAssertion[A] =
    label(string)

  /**
   * Returns a new assertion that succeeds if either assertion succeeds.
   */
  def ||[A1 <: A](that: => OldAssertion[A1]): OldAssertion[A1] =
    new OldAssertion(infix(param(self), "||", param(that)), actual => self.run(actual) || that.run(actual))

  /**
   * Evaluates the assertion with the specified value.
   */
  def apply(a: => A): AssertResult =
    run(a)

  override def canEqual(that: AssertionZIO[_]): Boolean = that match {
    case _: OldAssertion[_] => true
    case _                  => false
  }

  override def equals(that: Any): Boolean = that match {
    case that: OldAssertion[_] if that.canEqual(this) => this.toString == that.toString
    case _                                            => false
  }

  override def hashCode: Int =
    toString.hashCode

  /**
   * Labels this assertion with the specified string.
   */
  override def label(string: String): OldAssertion[A] =
    new OldAssertion(infix(param(self), "??", param(quoted(string))), run)

  /**
   * Returns the negation of this assertion.
   */
  override def negate(implicit trace: ZTraceElement): OldAssertion[A] =
    OldAssertion.not(self)

  /**
   * Tests the assertion to see if it would succeed on the given element.
   */
  def test(a: A): Boolean =
    run(a).isSuccess

  /**
   * Provides a meaningful string rendering of the assertion.
   */
  override def toString: String =
    render.toString
}

object OldAssertion extends AssertionVariants {
  type Render = AssertionZIO.Render
  val Render = AssertionZIO.Render
  import Render._

  /**
   * Makes a new assertion that always succeeds.
   */
  val anything: OldAssertion[Any] =
    OldAssertion.assertion("anything")()(_ => true)

  /**
   * Makes a new `OldAssertion` from a pretty-printing and a function.
   */
  def assertion[A](name: String)(params: RenderParam*)(run: (=> A) => Boolean): OldAssertion[A] = {
    lazy val assertion: OldAssertion[A] = assertionDirect(name)(params: _*) { actual =>
      lazy val tryActual = Try(actual)
      lazy val result: AssertResult =
        if (run(tryActual.get)) BoolAlgebra.success(AssertionValue(assertion, tryActual.get, result))
        else BoolAlgebra.failure(AssertionValue(assertion, tryActual.get, result))
      result
    }
    assertion
  }

  /**
   * Makes a new `OldAssertion` from a pretty-printing and a function.
   */
  def assertionDirect[A](
    name: String
  )(params: RenderParam*)(run: (=> A) => AssertResult): OldAssertion[A] =
    new OldAssertion(function(name, List(params.toList)), run)

  /**
   * Makes a new `OldAssertion[A]` from a pretty-printing, a function `(=> A) =>
   * Option[B]`, and an `OldAssertion[B]`. If the result of applying the
   * function to a given value is `Some[B]`, the `OldAssertion[B]` will be
   * applied to the resulting value to determine if the assertion is satisfied.
   * The result of the `OldAssertion[B]` and any assertions it is composed from
   * will be recursively embedded in the assert result. If the result of the
   * function is `None` the `orElse` parameter will be used to determine whether
   * the assertion is satisfied.
   */
  def assertionRec[A, B](
    name: String
  )(params: RenderParam*)(
    assertion: OldAssertion[B]
  )(get: (=> A) => Option[B], orElse: AssertionData => AssertResult = _.asFailure): OldAssertion[A] = {
    lazy val resultAssertion: OldAssertion[A] = assertionDirect(name)(params: _*) { a =>
      lazy val tryA = Try(a)
      get(tryA.get) match {
        case Some(b) =>
          val innerResult = assertion.run(b)
          lazy val result: AssertResult =
            if (innerResult.isSuccess) BoolAlgebra.success(AssertionValue(resultAssertion, tryA.get, result))
            else BoolAlgebra.failure(AssertionValue(assertion, b, innerResult))
          result
        case None =>
          orElse(AssertionData(resultAssertion, tryA.get))
      }
    }
    resultAssertion
  }

  /**
   * Makes a new assertion that requires a given numeric value to match a value
   * with some tolerance.
   */
  def approximatelyEquals[A: Numeric](reference: A, tolerance: A): OldAssertion[A] =
    OldAssertion.assertion("approximatelyEquals")(param(reference), param(tolerance)) { actual =>
      val referenceType = implicitly[Numeric[A]]
      val max           = referenceType.plus(reference, tolerance)
      val min           = referenceType.minus(reference, tolerance)

      referenceType.gteq(actual, min) && referenceType.lteq(actual, max)
    }

  /**
   * Makes a new assertion that requires an Iterable contain the specified
   * element. See [[OldAssertion.exists]] if you want to require an Iterable to
   * contain an element satisfying an assertion.
   */
  def contains[A](element: A): OldAssertion[Iterable[A]] =
    OldAssertion.assertion("contains")(param(element))(_.exists(_ == element))

  /**
   * Makes a new assertion that requires a `Cause` contain the specified cause.
   */
  def containsCause[E](cause: Cause[E]): OldAssertion[Cause[E]] =
    OldAssertion.assertion("containsCause")(param(cause))(_.contains(cause))

  /**
   * Makes a new assertion that requires a substring to be present.
   */
  def containsString(element: String): OldAssertion[String] =
    OldAssertion.assertion("containsString")(param(element))(_.contains(element))

  /**
   * Makes a new assertion that requires an exit value to die.
   */
  def dies(assertion: OldAssertion[Throwable]): OldAssertion[Exit[Any, Any]] =
    OldAssertion.assertionRec("dies")(param(assertion))(assertion) {
      case Exit.Failure(cause) => cause.dieOption
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an exit value to die with an instance
   * of given type (or its subtype).
   */
  def diesWithA[E: ClassTag]: OldAssertion[Exit[E, Any]] =
    dies(isSubtype[E](anything))

  /**
   * Makes a new assertion that requires an exception to have a certain message.
   */
  def hasMessage(message: OldAssertion[String]): OldAssertion[Throwable] =
    OldAssertion.assertionRec("hasMessage")(param(message))(message)(th => Some(th.getMessage()))

  /**
   * Makes a new assertion that requires an exception to have certain suppressed
   * exceptions.
   */
  def hasSuppressed(cause: OldAssertion[Iterable[Throwable]]): OldAssertion[Throwable] =
    OldAssertion.assertionRec("hasSuppressed")(param(cause))(cause)(th => Some(th.getSuppressed))

  /**
   * Makes a new assertion that requires an exception to have a certain cause.
   */
  def hasThrowableCause(cause: OldAssertion[Throwable]): OldAssertion[Throwable] =
    OldAssertion.assertionRec("hasThrowableCause")(param(cause))(cause)(th => Some(th.getCause()))

  /**
   * Makes a new assertion that requires a given string to end with the
   * specified suffix.
   */
  def endsWith[A](suffix: Seq[A]): OldAssertion[Seq[A]] =
    OldAssertion.assertion("endsWith")(param(suffix))(_.endsWith(suffix))

  /**
   * Makes a new assertion that requires a given string to end with the
   * specified suffix.
   */
  def endsWithString(suffix: String): OldAssertion[String] =
    OldAssertion.assertion("endsWithString")(param(suffix))(_.endsWith(suffix))

  /**
   * Makes a new assertion that requires a given string to equal another
   * ignoring case.
   */
  def equalsIgnoreCase(other: String): OldAssertion[String] =
    OldAssertion.assertion("equalsIgnoreCase")(param(other))(_.equalsIgnoreCase(other))

  /**
   * Makes a new assertion that requires an Iterable contain an element
   * satisfying the given assertion. See [[OldAssertion.contains]] if you only
   * need an Iterable to contain a given element.
   */
  def exists[A](assertion: OldAssertion[A]): OldAssertion[Iterable[A]] =
    OldAssertion.assertionRec("exists")(param(assertion))(assertion)(_.find(assertion.test))

  /**
   * Makes a new assertion that requires an exit value to fail.
   */
  def fails[E](assertion: OldAssertion[E]): OldAssertion[Exit[E, Any]] =
    OldAssertion.assertionRec("fails")(param(assertion))(assertion) {
      case Exit.Failure(cause) => cause.failures.headOption
      case _                   => None
    }

  /**
   * Makes a new assertion that requires the expression to fail with an instance
   * of given type (or its subtype).
   */
  def failsWithA[E: ClassTag]: OldAssertion[Exit[E, Any]] =
    fails(isSubtype[E](anything))

  /**
   * Makes a new assertion that requires an exit value to fail with a cause that
   * meets the specified assertion.
   */
  def failsCause[E](assertion: OldAssertion[Cause[E]]): OldAssertion[Exit[E, Any]] =
    OldAssertion.assertionRec("failsCause")(param(assertion))(assertion) {
      case Exit.Failure(cause) => Some(cause)
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an Iterable contain only elements
   * satisfying the given assertion.
   */
  def forall[A](assertion: OldAssertion[A]): OldAssertion[Iterable[A]] =
    OldAssertion.assertionRec("forall")(param(assertion))(assertion)(
      _.find(!assertion.test(_)),
      _.asSuccess
    )

  /**
   * Makes a new assertion that requires an Iterable to have the same distinct
   * elements as the other Iterable, though not necessarily in the same order.
   */
  def hasSameElementsDistinct[A](other: Iterable[A]): OldAssertion[Iterable[A]] =
    OldAssertion.assertion("hasSameElementsDistinct")(param(other))(actual => actual.toSet == other.toSet)

  /**
   * Makes a new assertion that requires a sequence to contain an element
   * satisfying the given assertion on the given position
   */
  def hasAt[A](pos: Int)(assertion: OldAssertion[A]): OldAssertion[Seq[A]] =
    OldAssertion.assertionRec("hasAt")(param(assertion))(assertion) { actual =>
      if (pos >= 0 && pos < actual.size) {
        Some(actual.apply(pos))
      } else {
        None
      }
    }

  /**
   * Makes a new assertion that requires an Iterable contain at least one of the
   * specified elements.
   */
  def hasAtLeastOneOf[A](other: Iterable[A]): OldAssertion[Iterable[A]] =
    hasIntersection(other)(hasSize(isGreaterThanEqualTo(1)))

  /**
   * Makes a new assertion that requires an Iterable contain at most one of the
   * specified elements.
   */
  def hasAtMostOneOf[A](other: Iterable[A]): OldAssertion[Iterable[A]] =
    hasIntersection(other)(hasSize(isLessThanEqualTo(1)))

  /**
   * Makes a new assertion that focuses in on a field in a case class.
   *
   * {{{
   * hasField("age", _.age, within(0, 10))
   * }}}
   */
  def hasField[A, B](name: String, proj: A => B, assertion: OldAssertion[B]): OldAssertion[A] =
    OldAssertion.assertionRec("hasField")(param(quoted(name)), param(field(name)), param(assertion))(assertion) {
      actual =>
        Some(proj(actual))
    }

  /**
   * Makes a new assertion that requires an Iterable to contain the first
   * element satisfying the given assertion.
   */
  def hasFirst[A](assertion: OldAssertion[A]): OldAssertion[Iterable[A]] =
    OldAssertion.assertionRec("hasFirst")(param(assertion))(assertion)(actual => actual.headOption)

  /**
   * Makes a new assertion that requires the intersection of two Iterables
   * satisfy the given assertion.
   */
  def hasIntersection[A](other: Iterable[A])(assertion: OldAssertion[Iterable[A]]): OldAssertion[Iterable[A]] =
    OldAssertion.assertionRec("hasIntersection")(param(other))(assertion) { actual =>
      val actualSeq = actual.toSeq
      val otherSeq  = other.toSeq

      Some(actualSeq.intersect(otherSeq))
    }

  /**
   * Makes a new assertion that requires a Map to have the specified key with
   * value satisfying the specified assertion.
   */
  def hasKey[K, V](key: K, assertion: OldAssertion[V]): OldAssertion[Map[K, V]] =
    OldAssertion.assertionRec("hasKey")(param(key))(assertion)(_.get(key))

  /**
   * Makes a new assertion that requires a Map to have the specified key.
   */
  def hasKey[K, V](key: K): OldAssertion[Map[K, V]] =
    hasKey(key, anything)

  /**
   * Makes a new assertion that requires a Map have keys satisfying the
   * specified assertion.
   */
  def hasKeys[K, V](assertion: OldAssertion[Iterable[K]]): OldAssertion[Map[K, V]] =
    OldAssertion.assertionRec("hasKeys")()(assertion)(actual => Some(actual.keys))

  /**
   * Makes a new assertion that requires an Iterable to contain the last element
   * satisfying the given assertion.
   */
  def hasLast[A](assertion: OldAssertion[A]): OldAssertion[Iterable[A]] =
    OldAssertion.assertionRec("hasLast")(param(assertion))(assertion)(actual => actual.lastOption)

  /**
   * Makes a new assertion that requires an Iterable contain none of the
   * specified elements.
   */
  def hasNoneOf[A](other: Iterable[A]): OldAssertion[Iterable[A]] =
    hasIntersection(other)(isEmpty)

  /**
   * Makes a new assertion that requires an Iterable contain exactly one of the
   * specified elements.
   */
  def hasOneOf[A](other: Iterable[A]): OldAssertion[Iterable[A]] =
    hasIntersection(other)(hasSize(equalTo(1)))

  /**
   * Makes a new assertion that requires an Iterable to have the same elements
   * as the specified Iterable, though not necessarily in the same order.
   */
  def hasSameElements[A](other: Iterable[A]): OldAssertion[Iterable[A]] =
    OldAssertion.assertion("hasSameElements")(param(other)) { actual =>
      val actualSeq = actual.toSeq
      val otherSeq  = other.toSeq

      actualSeq.diff(otherSeq).isEmpty && otherSeq.diff(actualSeq).isEmpty
    }

  /**
   * Makes a new assertion that requires the size of an Iterable be satisfied by
   * the specified assertion.
   */
  def hasSize[A](assertion: OldAssertion[Int]): OldAssertion[Iterable[A]] =
    OldAssertion.assertionRec("hasSize")(param(assertion))(assertion)(actual => Some(actual.size))

  /**
   * Makes a new assertion that requires the size of a string be satisfied by
   * the specified assertion.
   */
  def hasSizeString(assertion: OldAssertion[Int]): OldAssertion[String] =
    OldAssertion.assertionRec("hasSizeString")(param(assertion))(assertion)(actual => Some(actual.size))

  /**
   * Makes a new assertion that requires the specified Iterable to be a subset
   * of the other Iterable.
   */
  def hasSubset[A](other: Iterable[A]): OldAssertion[Iterable[A]] =
    hasIntersection(other)(hasSameElements(other))

  /**
   * Makes a new assertion that requires a Map have values satisfying the
   * specified assertion.
   */
  def hasValues[K, V](assertion: OldAssertion[Iterable[V]]): OldAssertion[Map[K, V]] =
    OldAssertion.assertionRec("hasValues")()(assertion)(actual => Some(actual.values))

  /**
   * Makes a new assertion that requires the sum type be a specified term.
   *
   * {{{
   * isCase("Some", Some.unapply, anything)
   * }}}
   */
  def isCase[Sum, Proj](
    termName: String,
    term: Sum => Option[Proj],
    assertion: OldAssertion[Proj]
  ): OldAssertion[Sum] =
    OldAssertion.assertionRec("isCase")(param(termName), param(unapply(termName)), param(assertion))(assertion)(term(_))

  /**
   * Makes a new assertion that requires an Iterable is distinct.
   */
  val isDistinct: OldAssertion[Iterable[Any]] = {
    @scala.annotation.tailrec
    def loop(iterator: Iterator[Any], seen: Set[Any]): Boolean = iterator.hasNext match {
      case false => true
      case true =>
        val x = iterator.next()
        if (seen.contains(x)) false else loop(iterator, seen + x)
    }

    OldAssertion.assertion("isDistinct")()(actual => loop(actual.iterator, Set.empty))
  }

  /**
   * Makes a new assertion that requires an Iterable to be empty.
   */
  val isEmpty: OldAssertion[Iterable[Any]] =
    OldAssertion.assertion("isEmpty")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a given string to be empty.
   */
  val isEmptyString: OldAssertion[String] =
    OldAssertion.assertion("isEmptyString")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a value be false.
   */
  def isFalse: OldAssertion[Boolean] =
    OldAssertion.assertion("isFalse")()(!_)

  /**
   * Makes a new assertion that requires a Failure value satisfying the
   * specified assertion.
   */
  def isFailure(assertion: OldAssertion[Throwable]): OldAssertion[Try[Any]] =
    OldAssertion.assertionRec("isFailure")(param(assertion))(assertion) {
      case Failure(a) => Some(a)
      case Success(_) => None
    }

  /**
   * Makes a new assertion that requires a Try value is Failure.
   */
  val isFailure: OldAssertion[Try[Any]] =
    isFailure(anything)

  /**
   * Makes a new assertion that requires the value be greater than the specified
   * reference value.
   */
  def isGreaterThan[A](reference: A)(implicit ord: Ordering[A]): OldAssertion[A] =
    OldAssertion.assertion("isGreaterThan")(param(reference))(actual => ord.gt(actual, reference))

  /**
   * Makes a new assertion that requires the value be greater than or equal to
   * the specified reference value.
   */
  def isGreaterThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): OldAssertion[A] =
    OldAssertion.assertion("isGreaterThanEqualTo")(param(reference))(actual => ord.gteq(actual, reference))

  /**
   * Makes a new assertion that requires an exit value to be interrupted.
   */
  def isInterrupted: OldAssertion[Exit[Any, Any]] =
    OldAssertion.assertion("isInterrupted")() {
      case Exit.Failure(cause) => cause.isInterrupted
      case _                   => false
    }

  /**
   * Makes a new assertion that requires an exit value to be interrupted.
   */
  def isJustInterrupted: OldAssertion[Exit[Any, Any]] =
    OldAssertion.assertion("isJustInterrupted")() {
      case Exit.Failure(Cause.Interrupt(_, _)) => true
      case _                                   => false
    }

  /**
   * Makes a new assertion that requires a Left value satisfying a specified
   * assertion.
   */
  def isLeft[A](assertion: OldAssertion[A]): OldAssertion[Either[A, Any]] =
    OldAssertion.assertionRec("isLeft")(param(assertion))(assertion) {
      case Left(a)  => Some(a)
      case Right(_) => None
    }

  /**
   * Makes a new assertion that requires an Either is Left.
   */
  val isLeft: OldAssertion[Either[Any, Any]] =
    isLeft(anything)

  /**
   * Makes a new assertion that requires the value be less than the specified
   * reference value.
   */
  def isLessThan[A](reference: A)(implicit ord: Ordering[A]): OldAssertion[A] =
    OldAssertion.assertion("isLessThan")(param(reference))(actual => ord.lt(actual, reference))

  /**
   * Makes a new assertion that requires the value be less than or equal to the
   * specified reference value.
   */
  def isLessThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): OldAssertion[A] =
    OldAssertion.assertion("isLessThanEqualTo")(param(reference))(actual => ord.lteq(actual, reference))

  /**
   * Makes a new assertion that requires a numeric value is negative.
   */
  def isNegative[A](implicit num: Numeric[A]): OldAssertion[A] =
    isLessThan(num.zero)

  /**
   * Makes a new assertion that requires a None value.
   */
  val isNone: OldAssertion[Option[Any]] =
    OldAssertion.assertion("isNone")()(_.isEmpty)

  /**
   * Makes a new assertion that requires an Iterable to be non empty.
   */
  val isNonEmpty: OldAssertion[Iterable[Any]] =
    OldAssertion.assertion("isNonEmpty")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a given string to be non empty.
   */
  val isNonEmptyString: OldAssertion[String] =
    OldAssertion.assertion("isNonEmptyString")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a null value.
   */
  val isNull: OldAssertion[Any] =
    OldAssertion.assertion("isNull")()(_ == null)

  /**
   * Makes a new assertion that requires a value to be equal to one of the
   * specified values.
   */
  def isOneOf[A](values: Iterable[A]): OldAssertion[A] =
    OldAssertion.assertion("isOneOf")(param(values))(actual => values.exists(_ == actual))

  /**
   * Makes a new assertion that requires a numeric value is positive.
   */
  def isPositive[A](implicit num: Numeric[A]): OldAssertion[A] =
    isGreaterThan(num.zero)

  /**
   * Makes a new assertions that requires a double value is not a number (NaN).
   */
  def isNaNDouble: OldAssertion[Double] =
    OldAssertion.assertion("isNaNDouble")()(_.isNaN)

  /**
   * Makes a new assertions that requires a float value is not a number (NaN).
   */
  def isNaNFloat: OldAssertion[Float] =
    OldAssertion.assertion("isNaNFloat")()(_.isNaN)

  /**
   * Makes a new assertions that requires a double value is positive infinity.
   */
  def isPosInfinityDouble: OldAssertion[Double] =
    OldAssertion.assertion("isPosInfinityDouble")()(_.isPosInfinity)

  /**
   * Makes a new assertions that requires a float value is positive infinity.
   */
  def isPosInfinityFloat: OldAssertion[Float] =
    OldAssertion.assertion("isPosInfinityFloat")()(_.isPosInfinity)

  /**
   * Makes a new assertions that requires a double value is negative infinity.
   */
  def isNegInfinityDouble: OldAssertion[Double] =
    OldAssertion.assertion("isNegInfinityDouble")()(_.isNegInfinity)

  /**
   * Makes a new assertions that requires a float value is negative infinity.
   */
  def isNegInfinityFloat: OldAssertion[Float] =
    OldAssertion.assertion("isNegInfinityFloat")()(_.isNegInfinity)

  /**
   * Makes a new assertions that requires a double value is finite.
   */
  def isFiniteDouble: OldAssertion[Double] =
    OldAssertion.assertion("isFiniteDouble")()(_.abs <= Double.MaxValue)

  /**
   * Makes a new assertions that requires a float value is finite.
   */
  def isFiniteFloat: OldAssertion[Float] =
    OldAssertion.assertion("isFiniteFloat")()(_.abs <= Float.MaxValue)

  /**
   * Makes a new assertions that requires a double value is infinite.
   */
  def isInfiniteDouble: OldAssertion[Double] =
    OldAssertion.assertion("isInfiniteDouble")()(_.isInfinite)

  /**
   * Makes a new assertions that requires a float value is infinite.
   */
  def isInfiniteFloat: OldAssertion[Float] =
    OldAssertion.assertion("isInfiniteFloat")()(_.isInfinite)

  /**
   * Makes a new assertion that requires a Right value satisfying a specified
   * assertion.
   */
  def isRight[A](assertion: OldAssertion[A]): OldAssertion[Either[Any, A]] =
    OldAssertion.assertionRec("isRight")(param(assertion))(assertion) {
      case Right(a) => Some(a)
      case Left(_)  => None
    }

  /**
   * Makes a new assertion that requires an Either is Right.
   */
  val isRight: OldAssertion[Either[Any, Any]] =
    isRight(anything)

  /**
   * Makes a new assertion that requires a Some value satisfying the specified
   * assertion.
   */
  def isSome[A](assertion: OldAssertion[A]): OldAssertion[Option[A]] =
    OldAssertion.assertionRec("isSome")(param(assertion))(assertion)(identity(_))

  /**
   * Makes a new assertion that requires an Option is Some.
   */
  val isSome: OldAssertion[Option[Any]] =
    isSome(anything)

  /**
   * Makes a new assertion that requires an Iterable is sorted.
   */
  def isSorted[A](implicit ord: Ordering[A]): OldAssertion[Iterable[A]] = {
    @scala.annotation.tailrec
    def loop(iterator: Iterator[A]): Boolean = iterator.hasNext match {
      case false => true
      case true =>
        val x = iterator.next()
        iterator.hasNext match {
          case false => true
          case true =>
            val y = iterator.next()
            if (ord.lteq(x, y)) loop(Iterator(y) ++ iterator) else false
        }
    }

    OldAssertion.assertion("isSorted")()(actual => loop(actual.iterator))
  }

  /**
   * Makes a new assertion that requires an Iterable is sorted in reverse order.
   */
  def isSortedReverse[A](implicit ord: Ordering[A]): OldAssertion[Iterable[A]] =
    isSorted(ord.reverse)

  /**
   * Makes a new assertion that requires a value have the specified type.
   *
   * Example:
   * {{{
   *   assert(Duration.fromNanos(1))(isSubtype[Duration.Finite](OldAssertion.anything))
   * }}}
   */
  def isSubtype[A](assertion: OldAssertion[A])(implicit C: ClassTag[A]): OldAssertion[Any] =
    OldAssertion.assertionRec("isSubtype")(param(className(C)))(assertion)(C.unapply(_))

  /**
   * Makes a new assertion that requires a Success value satisfying the
   * specified assertion.
   */
  def isSuccess[A](assertion: OldAssertion[A]): OldAssertion[Try[A]] =
    OldAssertion.assertionRec("isSuccess")(param(assertion))(assertion) {
      case Success(a) => Some(a)
      case Failure(_) => None
    }

  /**
   * Makes a new assertion that requires a Try value is Success.
   */
  val isSuccess: OldAssertion[Try[Any]] =
    isSuccess(anything)

  /**
   * Makes a new assertion that requires a value be true.
   */
  def isTrue: OldAssertion[Boolean] =
    OldAssertion.assertion("isTrue")()(identity(_))

  /**
   * Makes a new assertion that requires the value be unit.
   */
  val isUnit: OldAssertion[Unit] =
    OldAssertion.assertion("isUnit")()(_ => true)

  /**
   * Makes a new assertion that requires a value to fall within a specified min
   * and max (inclusive).
   */
  def isWithin[A](min: A, max: A)(implicit ord: Ordering[A]): OldAssertion[A] =
    OldAssertion.assertion("isWithin")(param(min), param(max))(actual => ord.gteq(actual, min) && ord.lteq(actual, max))

  /**
   * Makes a new assertion that requires a numeric value is zero.
   */
  def isZero[A](implicit num: Numeric[A]): OldAssertion[A] =
    equalTo(num.zero)

  /**
   * Makes a new assertion that requires a given string to match the specified
   * regular expression.
   */
  def matchesRegex(regex: String): OldAssertion[String] =
    OldAssertion.assertion("matchesRegex")(param(regex))(_.matches(regex))

  /**
   * Makes a new assertion that requires a numeric value is non negative.
   */
  def nonNegative[A](implicit num: Numeric[A]): OldAssertion[A] =
    isGreaterThanEqualTo(num.zero)

  /**
   * Makes a new assertion that requires a numeric value is non positive.
   */
  def nonPositive[A](implicit num: Numeric[A]): OldAssertion[A] =
    isLessThanEqualTo(num.zero)

  /**
   * Makes a new assertion that negates the specified assertion.
   */
  def not[A](assertion: OldAssertion[A]): OldAssertion[A] =
    OldAssertion.assertionDirect("not")(param(assertion))(!assertion.run(_))

  /**
   * Makes a new assertion that always fails.
   */
  val nothing: OldAssertion[Any] =
    OldAssertion.assertion("nothing")()(_ => false)

  /**
   * Makes a new assertion that requires a given sequence to start with the
   * specified prefix.
   */
  def startsWith[A](prefix: Seq[A]): OldAssertion[Seq[A]] =
    OldAssertion.assertion("startsWith")(param(prefix))(_.startsWith(prefix))

  /**
   * Makes a new assertion that requires a given string to start with a
   * specified prefix.
   */
  def startsWithString(prefix: String): OldAssertion[String] =
    OldAssertion.assertion("startsWithString")(param(prefix))(_.startsWith(prefix))

  /**
   * Makes a new assertion that requires an exit value to succeed.
   */
  def succeeds[A](assertion: OldAssertion[A]): OldAssertion[Exit[Any, A]] =
    OldAssertion.assertionRec("succeeds")(param(assertion))(assertion) {
      case Exit.Success(a) => Some(a)
      case _               => None
    }

  /**
   * Makes a new assertion that requires the expression to throw.
   */
  def throws[A](assertion: OldAssertion[Throwable]): OldAssertion[A] =
    OldAssertion.assertionRec("throws")(param(assertion))(assertion) { actual =>
      try {
        val _ = actual
        None
      } catch {
        case t: Throwable => Some(t)
      }
    }

  /**
   * Makes a new assertion that requires the expression to throw an instance of
   * given type (or its subtype).
   */
  def throwsA[E: ClassTag]: OldAssertion[Any] =
    throws(isSubtype[E](anything))
}
