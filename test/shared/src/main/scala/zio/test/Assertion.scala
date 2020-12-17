/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

import zio.test.AssertionM.RenderParam
import zio.{Cause, Exit, ZIO}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * An `Assertion[A]` is capable of producing assertion results on an `A`. As a
 * proposition, assertions compose using logical conjunction and disjunction,
 * and can be negated.
 */
final class Assertion[-A] private (
  val render: Assertion.Render,
  val run: (=> A) => AssertResult
) extends AssertionM[A]
    with ((=> A) => AssertResult) { self =>
  import zio.test.Assertion.Render._

  def runM: (=> A) => AssertResultM = a => BoolAlgebraM(ZIO.succeed(run(a)))

  /**
   * Returns a new assertion that succeeds only if both assertions succeed.
   */
  def &&[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    new Assertion(infix(param(self), "&&", param(that)), actual => self.run(actual) && that.run(actual))

  /**
   * A symbolic alias for `label`.
   */
  override def ??(string: String): Assertion[A] =
    label(string)

  /**
   * Returns a new assertion that succeeds if either assertion succeeds.
   */
  def ||[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    new Assertion(infix(param(self), "||", param(that)), actual => self.run(actual) || that.run(actual))

  /**
   * Evaluates the assertion with the specified value.
   */
  def apply(a: => A): AssertResult =
    run(a)

  override def canEqual(that: AssertionM[_]): Boolean = that match {
    case _: Assertion[_] => true
    case _               => false
  }

  override def equals(that: Any): Boolean = that match {
    case that: Assertion[_] if that.canEqual(this) => this.toString == that.toString
    case _                                         => false
  }

  override def hashCode: Int =
    toString.hashCode

  /**
   * Labels this assertion with the specified string.
   */
  override def label(string: String): Assertion[A] =
    new Assertion(infix(param(self), "??", param(quoted(string))), run)

  /**
   * Returns the negation of this assertion.
   */
  override def negate: Assertion[A] =
    Assertion.not(self)

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

object Assertion extends AssertionVariants {
  type Render = AssertionM.Render
  val Render = AssertionM.Render
  import Render._

  /**
   * Makes a new assertion that always succeeds.
   */
  val anything: Assertion[Any] =
    Assertion.assertion("anything")()(_ => true)

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  def assertion[A](name: String)(params: RenderParam*)(run: (=> A) => Boolean): Assertion[A] = {
    lazy val assertion: Assertion[A] = assertionDirect(name)(params: _*) { actual =>
      lazy val tryActual = Try(actual)
      lazy val result: AssertResult =
        if (run(tryActual.get)) BoolAlgebra.success(AssertionValue(assertion, tryActual.get, result))
        else BoolAlgebra.failure(AssertionValue(assertion, tryActual.get, result))
      result
    }
    assertion
  }

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  def assertionDirect[A](
    name: String
  )(params: RenderParam*)(run: (=> A) => AssertResult): Assertion[A] =
    new Assertion(function(name, List(params.toList)), run)

  /**
   * Makes a new `Assertion[A]` from a pretty-printing, a function
   * `(=> A) => Option[B]`, and an `Assertion[B]`. If the result of applying
   * the function to a given value is `Some[B]`, the `Assertion[B]` will be
   * applied to the resulting value to determine if the assertion is satisfied.
   * The result of the `Assertion[B]` and any assertions it is composed from
   * will be recursively embedded in the assert result. If the result of the
   * function is `None` the `orElse` parameter will be used to determine
   * whether the assertion is satisfied.
   */
  def assertionRec[A, B](
    name: String
  )(params: RenderParam*)(
    assertion: Assertion[B]
  )(get: (=> A) => Option[B], orElse: AssertionData => AssertResult = _.asFailure): Assertion[A] = {
    lazy val resultAssertion: Assertion[A] = assertionDirect(name)(params: _*) { a =>
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
   * Makes a new assertion that requires a given numeric value to match a value with some tolerance.
   */
  def approximatelyEquals[A: Numeric](reference: A, tolerance: A): Assertion[A] =
    Assertion.assertion("approximatelyEquals")(param(reference), param(tolerance)) { actual =>
      val referenceType = implicitly[Numeric[A]]
      val max           = referenceType.plus(reference, tolerance)
      val min           = referenceType.minus(reference, tolerance)

      referenceType.gteq(actual, min) && referenceType.lteq(actual, max)
    }

  /**
   * Makes a new assertion that requires an Iterable contain the specified
   * element. See [[Assertion.exists]] if you want to require an Iterable to contain an element
   * satisfying an assertion.
   */
  def contains[A](element: A): Assertion[Iterable[A]] =
    Assertion.assertion("contains")(param(element))(_.exists(_ == element))

  /**
   * Makes a new assertion that requires a `Cause` contain the specified
   * cause.
   */
  def containsCause[E](cause: Cause[E]): Assertion[Cause[E]] =
    Assertion.assertion("containsCause")(param(cause))(_.contains(cause))

  /**
   * Makes a new assertion that requires a substring to be present.
   */
  def containsString(element: String): Assertion[String] =
    Assertion.assertion("containsString")(param(element))(_.contains(element))

  /**
   * Makes a new assertion that requires an exit value to die.
   */
  def dies(assertion: Assertion[Throwable]): Assertion[Exit[Any, Any]] =
    Assertion.assertionRec("dies")(param(assertion))(assertion) {
      case Exit.Failure(cause) => cause.dieOption
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an exception to have a certain message.
   */
  def hasMessage(message: Assertion[String]): Assertion[Throwable] =
    Assertion.assertionRec("hasMessage")(param(message))(message)(th => Some(th.getMessage))

  /**
   * Makes a new assertion that requires an exception to have a certain cause.
   */
  def hasThrowableCause(cause: Assertion[Throwable]): Assertion[Throwable] =
    Assertion.assertionRec("hasThrowableCause")(param(cause))(cause)(th => Some(th.getCause))

  /**
   * Makes a new assertion that requires a given string to end with the specified suffix.
   */
  def endsWith[A](suffix: Seq[A]): Assertion[Seq[A]] =
    Assertion.assertion("endsWith")(param(suffix))(_.endsWith(suffix))

  /**
   * Makes a new assertion that requires a given string to end with the specified suffix.
   */
  def endsWithString(suffix: String): Assertion[String] =
    Assertion.assertion("endsWithString")(param(suffix))(_.endsWith(suffix))

  /**
   * Makes a new assertion that requires a given string to equal another ignoring case.
   */
  def equalsIgnoreCase(other: String): Assertion[String] =
    Assertion.assertion("equalsIgnoreCase")(param(other))(_.equalsIgnoreCase(other))

  /**
   * Makes a new assertion that requires an Iterable contain an element
   * satisfying the given assertion. See [[Assertion.contains]] if you only need an Iterable
   * to contain a given element.
   */
  def exists[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("exists")(param(assertion))(assertion)(_.find(assertion.test))

  /**
   * Makes a new assertion that requires an exit value to fail.
   */
  def fails[E](assertion: Assertion[E]): Assertion[Exit[E, Any]] =
    Assertion.assertionRec("fails")(param(assertion))(assertion) {
      case Exit.Failure(cause) => cause.failures.headOption
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an exit value to fail with a cause
   * that meets the specified assertion.
   */
  def failsCause[E](assertion: Assertion[Cause[E]]): Assertion[Exit[E, Any]] =
    Assertion.assertionRec("failsCause")(param(assertion))(assertion) {
      case Exit.Failure(cause) => Some(cause)
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an Iterable contain only elements
   * satisfying the given assertion.
   */
  def forall[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("forall")(param(assertion))(assertion)(
      _.find(!assertion.test(_)),
      _.asSuccess
    )

  /**
   * Makes a new assertion that requires an Iterable to have the same distinct elements
   * as the other Iterable, though not necessarily in the same order.
   */
  def hasSameElementsDistinct[A](other: Iterable[A]): Assertion[Iterable[A]] =
    Assertion.assertion("hasSameElementsDistinct")(param(other))(actual => actual.toSet == other.toSet)

  /**
   * Makes a new assertion that requires a sequence to contain an element
   * satisfying the given assertion on the given position
   */
  def hasAt[A](pos: Int)(assertion: Assertion[A]): Assertion[Seq[A]] =
    Assertion.assertionRec("hasAt")(param(assertion))(assertion) { actual =>
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
  def hasAtLeastOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(isGreaterThanEqualTo(1)))

  /**
   * Makes a new assertion that requires an Iterable contain at most one of the
   * specified elements.
   */
  def hasAtMostOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(isLessThanEqualTo(1)))

  /**
   * Makes a new assertion that focuses in on a field in a case class.
   *
   * {{{
   * hasField("age", _.age, within(0, 10))
   * }}}
   */
  def hasField[A, B](name: String, proj: A => B, assertion: Assertion[B]): Assertion[A] =
    Assertion.assertionRec("hasField")(param(quoted(name)), param(field(name)), param(assertion))(assertion) { actual =>
      Some(proj(actual))
    }

  /**
   * Makes a new assertion that requires an Iterable to contain the first
   * element satisfying the given assertion.
   */
  def hasFirst[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("hasFirst")(param(assertion))(assertion)(actual => actual.headOption)

  /**
   * Makes a new assertion that requires the intersection of two Iterables
   * satisfy the given assertion.
   */
  def hasIntersection[A](other: Iterable[A])(assertion: Assertion[Iterable[A]]): Assertion[Iterable[A]] =
    Assertion.assertionRec("hasIntersection")(param(other))(assertion) { actual =>
      val actualSeq = actual.toSeq
      val otherSeq  = other.toSeq

      Some(actualSeq.intersect(otherSeq))
    }

  /**
   * Makes a new assertion that requires a Map to have the specified key
   * with value satisfying the specified assertion.
   */
  def hasKey[K, V](key: K, assertion: Assertion[V]): Assertion[Map[K, V]] =
    Assertion.assertionRec("hasKey")(param(key))(assertion)(_.get(key))

  /**
   * Makes a new assertion that requires a Map to have the specified key.
   */
  def hasKey[K, V](key: K): Assertion[Map[K, V]] =
    hasKey(key, anything)

  /**
   * Makes a new assertion that requires a Map have keys satisfying the
   * specified assertion.
   */
  def hasKeys[K, V](assertion: Assertion[Iterable[K]]): Assertion[Map[K, V]] =
    Assertion.assertionRec("hasKeys")()(assertion)(actual => Some(actual.keys))

  /**
   * Makes a new assertion that requires an Iterable to contain the last
   * element satisfying the given assertion.
   */
  def hasLast[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("hasLast")(param(assertion))(assertion)(actual => actual.lastOption)

  /**
   * Makes a new assertion that requires an Iterable contain none of the
   * specified elements.
   */
  def hasNoneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(isEmpty)

  /**
   * Makes a new assertion that requires an Iterable contain exactly one of the
   * specified elements.
   */
  def hasOneOf[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSize(equalTo(1)))

  /**
   * Makes a new assertion that requires an Iterable to have the same elements
   * as the specified Iterable, though not necessarily in the same order.
   */
  def hasSameElements[A](other: Iterable[A]): Assertion[Iterable[A]] =
    Assertion.assertion("hasSameElements")(param(other)) { actual =>
      val actualSeq = actual.toSeq
      val otherSeq  = other.toSeq

      actualSeq.diff(otherSeq).isEmpty && otherSeq.diff(actualSeq).isEmpty
    }

  /**
   * Makes a new assertion that requires the size of an Iterable be satisfied
   * by the specified assertion.
   */
  def hasSize[A](assertion: Assertion[Int]): Assertion[Iterable[A]] =
    Assertion.assertionRec("hasSize")(param(assertion))(assertion)(actual => Some(actual.size))

  /**
   * Makes a new assertion that requires the size of a string be satisfied by
   * the specified assertion.
   */
  def hasSizeString(assertion: Assertion[Int]): Assertion[String] =
    Assertion.assertionRec("hasSizeString")(param(assertion))(assertion)(actual => Some(actual.size))

  /**
   * Makes a new assertion that requires the specified Iterable to be a subset of the
   * other Iterable.
   */
  def hasSubset[A](other: Iterable[A]): Assertion[Iterable[A]] =
    hasIntersection(other)(hasSameElements(other))

  /**
   * Makes a new assertion that requires a Map have values satisfying the
   * specified assertion.
   */
  def hasValues[K, V](assertion: Assertion[Iterable[V]]): Assertion[Map[K, V]] =
    Assertion.assertionRec("hasValues")()(assertion)(actual => Some(actual.values))

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
    assertion: Assertion[Proj]
  ): Assertion[Sum] =
    Assertion.assertionRec("isCase")(param(termName), param(unapply(termName)), param(assertion))(assertion)(term(_))

  /**
   * Makes a new assertion that requires an Iterable is distinct.
   */
  val isDistinct: Assertion[Iterable[Any]] = {
    @scala.annotation.tailrec
    def loop(iterator: Iterator[Any], seen: Set[Any]): Boolean = iterator.hasNext match {
      case false => true
      case true =>
        val x = iterator.next()
        if (seen.contains(x)) false else loop(iterator, seen + x)
    }

    Assertion.assertion("isDistinct")()(actual => loop(actual.iterator, Set.empty))
  }

  /**
   * Makes a new assertion that requires an Iterable to be empty.
   */
  val isEmpty: Assertion[Iterable[Any]] =
    Assertion.assertion("isEmpty")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a given string to be empty.
   */
  val isEmptyString: Assertion[String] =
    Assertion.assertion("isEmptyString")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a value be false.
   */
  def isFalse: Assertion[Boolean] =
    Assertion.assertion("isFalse")()(!_)

  /**
   * Makes a new assertion that requires a Failure value satisfying the specified
   * assertion.
   */
  def isFailure(assertion: Assertion[Throwable]): Assertion[Try[Any]] =
    Assertion.assertionRec("isSuccess")(param(assertion))(assertion) {
      case Failure(a) => Some(a)
      case Success(_) => None
    }

  /**
   * Makes a new assertion that requires a Try value is Failure.
   */
  val isFailure: Assertion[Try[Any]] =
    isFailure(anything)

  /**
   * Makes a new assertion that requires the value be greater than the
   * specified reference value.
   */
  def isGreaterThan[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isGreaterThan")(param(reference))(actual => ord.gt(actual, reference))

  /**
   * Makes a new assertion that requires the value be greater than or equal to
   * the specified reference value.
   */
  def isGreaterThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isGreaterThanEqualTo")(param(reference))(actual => ord.gteq(actual, reference))

  /**
   * Makes a new assertion that requires an exit value to be interrupted.
   */
  def isInterrupted: Assertion[Exit[Any, Any]] =
    Assertion.assertion("isInterrupted")() {
      case Exit.Failure(cause) => cause.interrupted
      case _                   => false
    }

  /**
   * Makes a new assertion that requires a Left value satisfying a specified
   * assertion.
   */
  def isLeft[A](assertion: Assertion[A]): Assertion[Either[A, Any]] =
    Assertion.assertionRec("isLeft")(param(assertion))(assertion) {
      case Left(a)  => Some(a)
      case Right(_) => None
    }

  /**
   * Makes a new assertion that requires an Either is Left.
   */
  val isLeft: Assertion[Either[Any, Any]] =
    isLeft(anything)

  /**
   * Makes a new assertion that requires the value be less than the specified
   * reference value.
   */
  def isLessThan[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isLessThan")(param(reference))(actual => ord.lt(actual, reference))

  /**
   * Makes a new assertion that requires the value be less than or equal to the
   * specified reference value.
   */
  def isLessThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isLessThanEqualTo")(param(reference))(actual => ord.lteq(actual, reference))

  /**
   * Makes a new assertion that requires a numeric value is negative.
   */
  def isNegative[A](implicit num: Numeric[A]): Assertion[A] =
    isLessThan(num.zero)

  /**
   * Makes a new assertion that requires a None value.
   */
  val isNone: Assertion[Option[Any]] =
    Assertion.assertion("isNone")()(_.isEmpty)

  /**
   * Makes a new assertion that requires an Iterable to be non empty.
   */
  val isNonEmpty: Assertion[Iterable[Any]] =
    Assertion.assertion("isNonEmpty")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a given string to be non empty.
   */
  val isNonEmptyString: Assertion[String] =
    Assertion.assertion("isNonEmptyString")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a null value.
   */
  val isNull: Assertion[Any] =
    Assertion.assertion("isNull")()(_ == null)

  /**
   * Makes a new assertion that requires a value to be equal to one of the specified values.
   */
  def isOneOf[A](values: Iterable[A]): Assertion[A] =
    Assertion.assertion("isOneOf")(param(values))(actual => values.exists(_ == actual))

  /**
   * Makes a new assertion that requires a numeric value is positive.
   */
  def isPositive[A](implicit num: Numeric[A]): Assertion[A] =
    isGreaterThan(num.zero)

  /**
   * Makes a new assertion that requires a Right value satisfying a specified
   * assertion.
   */
  def isRight[A](assertion: Assertion[A]): Assertion[Either[Any, A]] =
    Assertion.assertionRec("isRight")(param(assertion))(assertion) {
      case Right(a) => Some(a)
      case Left(_)  => None
    }

  /**
   * Makes a new assertion that requires an Either is Right.
   */
  val isRight: Assertion[Either[Any, Any]] =
    isRight(anything)

  /**
   * Makes a new assertion that requires a Some value satisfying the specified
   * assertion.
   */
  def isSome[A](assertion: Assertion[A]): Assertion[Option[A]] =
    Assertion.assertionRec("isSome")(param(assertion))(assertion)(identity(_))

  /**
   * Makes a new assertion that requires an Option is Some.
   */
  val isSome: Assertion[Option[Any]] =
    isSome(anything)

  /**
   * Makes a new assertion that requires an Iterable is sorted.
   */
  def isSorted[A](implicit ord: Ordering[A]): Assertion[Iterable[A]] = {
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

    Assertion.assertion("isSorted")()(actual => loop(actual.iterator))
  }

  /**
   * Makes a new assertion that requires an Iterable is sorted in reverse order.
   */
  def isSortedReverse[A](implicit ord: Ordering[A]): Assertion[Iterable[A]] =
    isSorted(ord.reverse)

  /**
   * Makes a new assertion that requires a value have the specified type.
   *
   * Example:
   * {{{
   *   assert(Duration.fromNanos(1), isSubtype[Duration.Finite](Assertion.anything))
   * }}}
   */
  def isSubtype[A](assertion: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    Assertion.assertionRec("isSubtype")(param(className(C)))(assertion)(C.unapply(_))

  /**
   * Makes a new assertion that requires a Success value satisfying the specified
   * assertion.
   */
  def isSuccess[A](assertion: Assertion[A]): Assertion[Try[A]] =
    Assertion.assertionRec("isSuccess")(param(assertion))(assertion) {
      case Success(a) => Some(a)
      case Failure(_) => None
    }

  /**
   * Makes a new assertion that requires a Try value is Success.
   */
  val isSuccess: Assertion[Try[Any]] =
    isSuccess(anything)

  /**
   * Makes a new assertion that requires a value be true.
   */
  def isTrue: Assertion[Boolean] =
    Assertion.assertion("isTrue")()(identity(_))

  /**
   * Makes a new assertion that requires the value be unit.
   */
  val isUnit: Assertion[Unit] =
    Assertion.assertion("isUnit")()(_ => true)

  /**
   * Makes a new assertion that requires a value to fall within a
   * specified min and max (inclusive).
   */
  def isWithin[A](min: A, max: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isWithin")(param(min), param(max))(actual => ord.gteq(actual, min) && ord.lteq(actual, max))

  /**
   * Makes a new assertion that requires a numeric value is zero.
   */
  def isZero[A](implicit num: Numeric[A]): Assertion[A] =
    equalTo(num.zero)

  /**
   * Makes a new assertion that requires a given string to match the specified regular expression.
   */
  def matchesRegex(regex: String): Assertion[String] =
    Assertion.assertion("matchesRegex")(param(regex))(_.matches(regex))

  /**
   * Makes a new assertion that requires a numeric value is non negative.
   */
  def nonNegative[A](implicit num: Numeric[A]): Assertion[A] =
    isGreaterThanEqualTo(num.zero)

  /**
   * Makes a new assertion that requires a numeric value is non positive.
   */
  def nonPositive[A](implicit num: Numeric[A]): Assertion[A] =
    isLessThanEqualTo(num.zero)

  /**
   * Makes a new assertion that negates the specified assertion.
   */
  def not[A](assertion: Assertion[A]): Assertion[A] =
    Assertion.assertionDirect("not")(param(assertion))(!assertion.run(_))

  /**
   * Makes a new assertion that always fails.
   */
  val nothing: Assertion[Any] =
    Assertion.assertion("nothing")()(_ => false)

  /**
   * Makes a new assertion that requires a given sequence to start with the
   * specified prefix.
   */
  def startsWith[A](prefix: Seq[A]): Assertion[Seq[A]] =
    Assertion.assertion("startsWith")(param(prefix))(_.startsWith(prefix))

  /**
   * Makes a new assertion that requires a given string to start with a specified prefix.
   */
  def startsWithString(prefix: String): Assertion[String] =
    Assertion.assertion("startsWithString")(param(prefix))(_.startsWith(prefix))

  /**
   * Makes a new assertion that requires an exit value to succeed.
   */
  def succeeds[A](assertion: Assertion[A]): Assertion[Exit[Any, A]] =
    Assertion.assertionRec("succeeds")(param(assertion))(assertion) {
      case Exit.Success(a) => Some(a)
      case _               => None
    }

  /**
   * Makes a new assertion that requires the expression to throw.
   */
  def throws[A](assertion: Assertion[Throwable]): Assertion[A] =
    Assertion.assertionRec("throws")(param(assertion))(assertion) { actual =>
      try {
        val _ = actual
        None
      } catch {
        case t: Throwable => Some(t)
      }
    }

  /**
   * Makes a new assertion that requires the expression to throw an instance
   * of given type (or its subtype).
   */
  def throwsA[E: ClassTag]: Assertion[Any] =
    throws(isSubtype[E](anything))
}
