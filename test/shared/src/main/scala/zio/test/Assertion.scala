/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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
import zio.test.FailureRenderer.FailureMessage.Message
import zio.test.{MessageDesc => M}
import zio.{Cause, Exit, ZIO}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * An `Assertion[A]` is capable of producing assertion results on an `A`. As a
 * proposition, assertions compose using logical conjunction and disjunction,
 * and can be negated.
 */
final class Assertion[-A] private (
  val render: Assertion.Render[A],
  val run: (=> A) => AssertResult
) extends AssertionM[A]
    with ((=> A) => AssertResult) { self =>
  import zio.test.Assertion.Render._

  def runM: (=> A) => AssertResultM = a => BoolAlgebraM(ZIO.succeed(run(a)))

  /**
   * Returns a new assertion that succeeds only if both assertions succeed.
   */
  def &&[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    new Assertion(
      infix((a: A1, b: Boolean) => Message("&&"), param(self), "&&", param(that)),
      actual => self.run(actual) && that.run(actual)
    )

  /**
   * A symbolic alias for `label`.
   */
  override def ??(string: String): Assertion[A] =
    label(string)

  /**
   * Returns a new assertion that succeeds if either assertion succeeds.
   */
  def ||[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    new Assertion(
      infix((a: A1, b: Boolean) => Message("||"), param(self), "||", param(that)),
      actual => self.run(actual) || that.run(actual)
    )

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
    new Assertion(infix((a: A, b: Boolean) => Message(s"[$string]"), param(self), "??", param(quoted(string))), run)

  /**
   * Returns the negation of this assertion.
   */
  override def negate: Assertion[A] =
    Assertion.not(self)

//  @unused("used in the SmartAssertMacro")
  def smartNegate: Assertion[A] =
    Assertion.smartNot(self)

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

  override def withCode(code: String): Assertion[A] =
    new Assertion(render.withCode(code), run)
}

object Assertion extends AssertionVariants {
  type Render[-A] = AssertionM.Render[A]
  val Render = AssertionM.Render
  import Render._

  /**
   * Makes a new assertion that always succeeds.
   */
  val anything: Assertion[Any] =
    Assertion.assertion("anything", M.result + M.is + "anything")()(_ => true)

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  def assertion[A](
    name: String,
    render: (A, Boolean) => Message = M.result + "<NOT IMPLEMENTED>"
  )(
    params: RenderParam*
  )(run: (=> A) => Boolean): Assertion[A] = {
    lazy val assertion: Assertion[A] = assertionDirect(name, render)(params: _*) { actual =>
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
    name: String,
    render: (A, Boolean) => Message
  )(params: RenderParam*)(run: (=> A) => AssertResult): Assertion[A] =
    new Assertion(function(render, name, List(params.toList)), run)

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
    name: String,
    render: (A, Boolean) => Message
  )(params: RenderParam*)(
    assertion: Assertion[B]
  )(get: (=> A) => Option[B], orElse: AssertionData => AssertResult = _.asFailure): Assertion[A] = {
    lazy val resultAssertion: Assertion[A] = assertionDirect[A](name, render)(params: _*) { a =>
      lazy val tryA = Try(a)
      get(tryA.get) match {
        case Some(b) =>
          Try(assertion.run(b)) match {
            case Success(innerResult) =>
              lazy val result: AssertResult =
                if (innerResult.isSuccess) BoolAlgebra.success(AssertionValue(resultAssertion, tryA.get, result))
                else BoolAlgebra.failure(AssertionValue(assertion, b, innerResult))
              result
            case Failure(exception) =>
              lazy val result =
                AssertionData(assertion, ().asInstanceOf[B], Some(exception)).asFailure
              result
          }
        case None =>
          orElse(AssertionData(resultAssertion, tryA.get, None))
      }
    }
    resultAssertion
  }

  /**
   * Makes a new assertion that requires a given numeric value to match a value with some tolerance.
   */
  def approximatelyEquals[A: Numeric](reference: A, tolerance: A): Assertion[A] =
    Assertion.assertion[A](
      "approximatelyEquals",
      M.result + M.does + "equal" + M.value(reference) + "with a tolerance of" + M.value(tolerance)
    )(param(reference), param(tolerance)) { actual =>
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
    Assertion.assertion[Iterable[A]](
      "contains",
      (M.result + M.choice("contains", "does not contain") + M.value(element)).render
    )(
      param(element)
    )(_.exists(_ == element))

  /**
   * Makes a new assertion that requires a `Cause` contain the specified
   * cause.
   */
  def containsCause[E](cause: Cause[E]): Assertion[Cause[E]] =
    Assertion.assertion[Cause[E]]("containsCause", M.result + M.does + "contain cause" + M.value(cause))(
      param(cause)
    )(_.contains(cause))

  /**
   * Makes a new assertion that requires a substring to be present.
   */
  def containsString(element: String): Assertion[String] =
    Assertion.assertion[String](
      "containsString",
      M.result + M.does + "contain" + M.value(element)
    )(
      param(element)
    )(_.contains(element))

  def containsOption[A](element: A): Assertion[Option[A]] =
    Assertion.assertion[Option[A]](
      "containsOption",
      M.result + M.does + "contain" + M.value(element)
    )(
      param(element)
    )(_.contains(element))

  /**
   * Makes a new assertion that requires an exit value to die.
   */
  def dies(assertion: Assertion[Throwable]): Assertion[Exit[Any, Any]] =
    Assertion.assertionRec("dies", M.result + M.did + "die")(param(assertion))(assertion) {
      case Exit.Failure(cause) => cause.dieOption
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an exception to have a certain message.
   */
  def hasMessage(message: Assertion[String]): Assertion[Throwable] =
    Assertion.assertionRec[Throwable, String](
      "hasMessage",
      M.result + M.does + "have message"
    )(param(message))(message)(th => Some(th.getMessage))

  /**
   * Makes a new assertion that requires an exception to have a certain cause.
   */
  def hasThrowableCause(cause: Assertion[Throwable]): Assertion[Throwable] =
    Assertion.assertionRec[Throwable, Throwable](
      "hasThrowableCause",
      M.result + M.does + "have cause"
    )(param(cause))(cause)(th => Some(th.getCause))

  /**
   * Makes a new assertion that requires a given string to end with the specified suffix.
   */
  def endsWith[A](suffix: Seq[A]): Assertion[Seq[A]] =
    Assertion.assertion[Seq[A]](
      "endsWith",
      M.result + M.does + "end with" + M.value(suffix)
    )(param(suffix))(
      _.endsWith(suffix)
    )

  /**
   * Makes a new assertion that requires a given string to end with the specified suffix.
   */
  def endsWithString(suffix: String): Assertion[String] =
    Assertion.assertion[String]("endsWithString", M.result + M.does + "end with" + M.value(suffix))(
      param(suffix)
    )(_.endsWith(suffix))

  /**
   * Makes a new assertion that requires a given string to equal another ignoring case.
   */
  def equalsIgnoreCase(other: String): Assertion[String] =
    Assertion.assertion[String]("equalsIgnoreCase", M.result + M.does + "equal (ignoring case)" + M.value(other))(
      param(other)
    )(_.equalsIgnoreCase(other))

  /**
   * Makes a new assertion that requires an Iterable contain an element
   * satisfying the given assertion. See [[Assertion.contains]] if you only need an Iterable
   * to contain a given element.
   */
  def exists[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec[Iterable[A], A](
      "exists",
//      M.result + M.does + "exist"
      M.result + M.does + "not exist" + M.text(assertion.toString)
//      assertion.render.render(_, _)
    )(param(assertion))(assertion)(
      _.find(assertion.test)
    )

  def smartExists[A](f: A => Boolean): Assertion[Iterable[A]] =
    Assertion.assertion(
      "exists",
      M.choice("At least one", "No") + "element in the" +
        M.result[Iterable[A]](_.toString.takeWhile(_ != '(')) + "satisfies the predicate"
    )()(_.exists(f))

  /**
   * Makes a new assertion that requires an exit value to fail.
   */
  def fails[E](assertion: Assertion[E]): Assertion[Exit[E, Any]] =
    Assertion.assertionRec[Exit[E, Any], E]("fails", M.result + M.does + "fail")(param(assertion))(assertion) {
      case Exit.Failure(cause) => cause.failures.headOption
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an exit value to fail with a cause
   * that meets the specified assertion.
   */
  def failsCause[E](assertion: Assertion[Cause[E]]): Assertion[Exit[E, Any]] =
    Assertion.assertionRec[Exit[E, Any], Cause[E]]("failsCause", M.result + M.does + "fail with cause")(
      param(assertion)
    )(assertion) {
      case Exit.Failure(cause) => Some(cause)
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an Iterable contain only elements
   * satisfying the given assertion.
   */
  def forall[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec[Iterable[A], A]("forall", M.result + M.does + "forall")(param(assertion))(assertion)(
      _.find(!assertion.test(_)),
      _.asSuccess
    )

  /**
   * Makes a new assertion that requires an Iterable to have the same distinct elements
   * as the other Iterable, though not necessarily in the same order.
   */
  def hasSameElementsDistinct[A](other: Iterable[A]): Assertion[Iterable[A]] =
    Assertion.assertion[Iterable[A]](
      "hasSameElementsDistinct",
      M.result + M.does + "have the same distinct elements as" + M.value(other)
    )(param(other))(actual => actual.toSet == other.toSet)

  def ord(n: Int): String = n + {
    if (n % 100 / 10 == 1) "th" else (("thstndrd" + "th" * 6).sliding(2, 2).toSeq(n % 10))
  }

  /**
   * Makes a new assertion that requires a sequence to contain an element
   * satisfying the given assertion on the given position
   */
  def hasAt[A](pos: Int)(assertion: Assertion[A]): Assertion[Seq[A]] =
    Assertion
      .assertionRec[Seq[A], A](
        "hasAt",
        M.valid + "index" + M.value(pos) + "for" + M.result[Seq[A]](_.toString.takeWhile(_ != '(')) + "of size" + M
          .result[Seq[A]](_.length.toString)
      )(
        param(pos),
        param(assertion)
      )(assertion) { actual =>
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
    Assertion
      .assertionRec[A, B](
        "hasField",
        M.result + M.does + "have field" + M.value(name)
      )(
        param(field(name)),
        param(assertion)
      )(assertion) { actual =>
        Some(proj(actual))
      }

  /**
   * Makes a new assertion that requires an Iterable to contain the first
   * element satisfying the given assertion.
   */
  def hasFirst[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec[Iterable[A], A](
      "hasFirst",
      M.result +
        M.choice("has at least one element", "is empty")
    )(param(assertion))(
      assertion
    )(actual => actual.headOption)

  /**
   * Makes a new assertion that requires the intersection of two Iterables
   * satisfy the given assertion.
   */
  def hasIntersection[A](other: Iterable[A])(assertion: Assertion[Iterable[A]]): Assertion[Iterable[A]] =
    Assertion.assertionRec[Iterable[A], Iterable[A]](
      "hasIntersection",
      M.result + M.does + "intersect" + M.value(other)
    )(param(other))(assertion) { actual =>
      val actualSeq = actual.toSeq
      val otherSeq  = other.toSeq

      Some(actualSeq.intersect(otherSeq))
    }

  /**
   * Makes a new assertion that requires a Map to have the specified key
   * with value satisfying the specified assertion.
   */
  def hasKey[K, V](key: K, assertion: Assertion[V]): Assertion[Map[K, V]] =
    Assertion.assertionRec[Map[K, V], V]("hasKey", M.result + M.does + "have key" + M.value(key))(param(key))(
      assertion
    )(_.get(key))

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
    Assertion.assertionRec[Map[K, V], Iterable[K]]("hasKeys", M.result + M.does + "have keys")()(assertion)(actual =>
      Some(actual.keys)
    )

  /**
   * Makes a new assertion that requires an Iterable to contain the last
   * element satisfying the given assertion.
   */
  def hasLast[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec[Iterable[A], A]("hasLast", M.result + M.does + "have a last element")(param(assertion))(
      assertion
    )(actual => actual.lastOption)

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
    Assertion.assertion[Iterable[A]](
      "hasSameElements",
      M.result + M.does + "have the same elements as" + M.value(other)
    )(param(other)) { actual =>
      val actualSeq = actual.toSeq
      val otherSeq  = other.toSeq

      actualSeq.diff(otherSeq).isEmpty && otherSeq.diff(actualSeq).isEmpty
    }

  /**
   * Makes a new assertion that requires the size of an Iterable be satisfied
   * by the specified assertion.
   */
  def hasSize[A](assertion: Assertion[Int]): Assertion[Iterable[A]] =
    Assertion.assertionRec[Iterable[A], Int]("hasSize", M.result + M.does + "have size")(param(assertion))(assertion)(
      actual => Some(actual.size)
    )

  /**
   * Makes a new assertion that requires the size of a string be satisfied by
   * the specified assertion.
   */
  def hasSizeString(assertion: Assertion[Int]): Assertion[String] =
    Assertion.assertionRec[String, Int]("hasSizeString", M.result + M.does + "have size")(param(assertion))(assertion)(
      actual => Some(actual.size)
    )

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
    Assertion.assertionRec[Map[K, V], Iterable[V]]("hasValues", M.result + M.does + "have values")()(assertion)(
      actual => Some(actual.values)
    )

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
    Assertion.assertionRec[Sum, Proj]("isCase", M.result + M.is + "an instance of" + M.value(termName))(
      param(termName),
      param(unapply(termName)),
      param(assertion)
    )(assertion)(
      term(_)
    )

  def smartIsCase[Case](
    termName: String,
    term: Any => Option[Case],
    assertion: Assertion[Case]
  ): Assertion[Case] =
    Assertion.assertionRec[Any, Case]("isCase", M.result + M.is + "an instance of" + M.value(termName))(
      param(termName)
    )(assertion)(
      term(_)
    )

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

    Assertion.assertion[Iterable[Any]]("isDistinct", M.result + M.is + "distinct")()(actual =>
      loop(actual.iterator, Set.empty)
    )
  }

  /**
   * Makes a new assertion that requires an Iterable to be empty.
   */
  val isEmpty: Assertion[Iterable[Any]] =
    Assertion.assertion[Iterable[Any]]("isEmpty", M.result + M.is + "empty")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a given string to be empty.
   */
  val isEmptyString: Assertion[String] =
    Assertion.assertion[String]("isEmptyString", M.result + M.is + "empty")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a value be false.
   */
  def isFalse: Assertion[Boolean] =
    Assertion.assertion[Boolean]("isFalse", M.result + M.is + "false")()(!_)

  /**
   * Makes a new assertion that requires a Failure value satisfying the specified
   * assertion.
   */
  def isFailure(assertion: Assertion[Throwable]): Assertion[Try[Any]] =
    Assertion.assertionRec[Any, Throwable]("isSuccess", M.result + M.is + "an instance of scala.util.Try")(
      param(assertion)
    )(assertion) {
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
    Assertion
      .assertion[A](
        "isGreaterThan",
        M.result + M.is + "greater than" + M.value(reference)
      )(param(reference))(actual => ord.gt(actual, reference))

  /**
   * Makes a new assertion that requires the value be greater than or equal to
   * the specified reference value.
   */
  def isGreaterThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion[A](
      "isGreaterThanEqualTo",
      M.result + M.is + "is greater than or equal to" + M.value(reference)
    )(param(reference))(actual => ord.gteq(actual, reference))

  /**
   * Makes a new assertion that requires an exit value to be interrupted.
   */
  def isInterrupted: Assertion[Exit[Any, Any]] =
    Assertion.assertion[Exit[Any, Any]]("isInterrupted", M.result + M.was + "interrupted")() {
      case Exit.Failure(cause) => cause.interrupted
      case _                   => false
    }

  /**
   * Makes a new assertion that requires a Left value satisfying a specified
   * assertion.
   */
  def isLeft[A](assertion: Assertion[A]): Assertion[Either[A, Any]] =
    Assertion.assertionRec[Either[A, Any], A]("isLeft", M.result + M.is + "an instance of Left")(param(assertion))(
      assertion
    ) {
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
    Assertion.assertion[A]("isLessThan", M.result + M.is + "less than" + M.value(reference))(param(reference))(actual =>
      ord.lt(actual, reference)
    )

  /**
   * Makes a new assertion that requires the value be less than or equal to the
   * specified reference value.
   */
  def isLessThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion[A]("isLessThanEqualTo", M.result + M.is + "less than or equal to" + M.value(reference))(
      param(reference)
    )(actual => ord.lteq(actual, reference))

  /**
   * Makes a new assertion that requires a numeric value is negative.
   */
  def isNegative[A](implicit num: Numeric[A]): Assertion[A] =
    isLessThan(num.zero)

  /**
   * Makes a new assertion that requires a None value.
   */
  val isNone: Assertion[Option[Any]] =
    Assertion.assertion[Option[Any]]("isNone", M.result + M.is + "an instance of None")()(_.isEmpty)

  /**
   * Makes a new assertion that requires an Iterable to be non empty.
   */
  val isNonEmpty: Assertion[Iterable[Any]] =
    Assertion.assertion[Iterable[Any]]("isNonEmpty", M.result + M.is + "non-empty")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a given string to be non empty.
   */
  val isNonEmptyString: Assertion[String] =
    Assertion.assertion[String]("isNonEmptyString", M.result + M.is + "a non-empty String")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a null value.
   */
  val isNull: Assertion[Any] =
    Assertion.assertion[Any]("isNull", M.result + M.is + "null")()(_ == null)

  /**
   * Makes a new assertion that requires a value to be equal to one of the specified values.
   */
  def isOneOf[A](values: Iterable[A]): Assertion[A] =
    Assertion.assertion[A]("isOneOf", M.result + M.is + "one of" + M.value(values))(param(values))(actual =>
      values.exists(_ == actual)
    )

  /**
   * Makes a new assertion that requires a numeric value is positive.
   */
  def isPositive[A](implicit num: Numeric[A]): Assertion[A] =
    isGreaterThan(num.zero)

  /**
   * Makes a new assertions that requires a double value is not a number (NaN).
   */
  def isNaNDouble: Assertion[Double] =
    Assertion.assertion("isNaNDouble")()(_.isNaN)

  /**
   * Makes a new assertions that requires a float value is not a number (NaN).
   */
  def isNaNFloat: Assertion[Float] =
    Assertion.assertion("isNaNFloat")()(_.isNaN)

  /**
   * Makes a new assertions that requires a double value is positive infinity.
   */
  def isPosInfinityDouble: Assertion[Double] =
    Assertion.assertion("isPosInfinityDouble")()(_.isPosInfinity)

  /**
   * Makes a new assertions that requires a float value is positive infinity.
   */
  def isPosInfinityFloat: Assertion[Float] =
    Assertion.assertion("isPosInfinityFloat")()(_.isPosInfinity)

  /**
   * Makes a new assertions that requires a double value is negative infinity.
   */
  def isNegInfinityDouble: Assertion[Double] =
    Assertion.assertion("isNegInfinityDouble")()(_.isNegInfinity)

  /**
   * Makes a new assertions that requires a float value is negative infinity.
   */
  def isNegInfinityFloat: Assertion[Float] =
    Assertion.assertion("isNegInfinityFloat")()(_.isNegInfinity)

  /**
   * Makes a new assertions that requires a double value is finite.
   */
  def isFiniteDouble: Assertion[Double] =
    Assertion.assertion("isFiniteDouble")()(_.abs <= Double.MaxValue)

  /**
   * Makes a new assertions that requires a float value is finite.
   */
  def isFiniteFloat: Assertion[Float] =
    Assertion.assertion("isFiniteFloat")()(_.abs <= Float.MaxValue)

  /**
   * Makes a new assertions that requires a double value is infinite.
   */
  def isInfiniteDouble: Assertion[Double] =
    Assertion.assertion("isInfiniteDouble")()(_.isInfinite)

  /**
   * Makes a new assertions that requires a float value is infinite.
   */
  def isInfiniteFloat: Assertion[Float] =
    Assertion.assertion("isInfiniteFloat")()(_.isInfinite)

  /**
   * Makes a new assertion that requires a Right value satisfying a specified
   * assertion.
   */
  def isRight[A](assertion: Assertion[A]): Assertion[Either[Any, A]] =
    Assertion.assertionRec[Either[Any, A], A]("isRight", M.result + M.is + "an instance of Right")(param(assertion))(
      assertion
    ) {
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
    Assertion.assertionRec[Option[A], A]("isSome", M.result + M.is + "Some")(param(assertion))(
      assertion
    )(
      identity(_)
    )

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

    Assertion.assertion[Iterable[A]]("isSorted", M.result + M.is + "sorted")()(actual => loop(actual.iterator))
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
   *   assert(Duration.fromNanos(1))(isSubtype[Duration.Finite](Assertion.anything))
   * }}}
   */
  def isSubtype[A](assertion: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    Assertion.assertionRec("isSubtype", M.result + M.is + "a subtype of" + M.value(className(C)))(param(className(C)))(
      assertion
    )(C.unapply(_))

  /**
   * Makes a new assertion that requires a Success value satisfying the specified
   * assertion.
   */
  def isSuccess[A](assertion: Assertion[A]): Assertion[Try[A]] =
    Assertion.assertionRec[Try[A], A]("isSuccess", M.result + M.is + "a scala.util.Try.Success")(param(assertion))(
      assertion
    ) {
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
    Assertion.assertion[Boolean](
      "isTrue",
//      M.result + M.is + M.value(true)
      M.choice("Was true", "Was false")
    )()(identity(_))

  /**
   * Makes a new assertion that requires the value be unit.
   */
  val isUnit: Assertion[Unit] =
    Assertion.assertion[Unit]("isUnit", M.result + M.is + "unit")()(_ => true)

  /**
   * Makes a new assertion that requires a value to fall within a
   * specified min and max (inclusive).
   */
  def isWithin[A](min: A, max: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion[A]("isWithin", M.result + M.is + "within" + M.value(min) + "and" + M.value(max))(
      param(min),
      param(max)
    )(actual => ord.gteq(actual, min) && ord.lteq(actual, max))

  /**
   * Makes a new assertion that requires a numeric value is zero.
   */
  def isZero[A](implicit num: Numeric[A]): Assertion[A] =
    equalTo(num.zero)

  /**
   * Makes a new assertion that requires a given string to match the specified regular expression.
   */
  def matchesRegex(regex: String): Assertion[String] =
    Assertion.assertion[String]("matchesRegex", M.result + M.does + "match regex")(param(regex))(_.matches(regex))

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
    Assertion
      .assertionDirect[A]("not", assertion.render.render)(param(assertion))(!assertion.run(_))
      .withCode(assertion.render.codeString)

  private def smartNot[A](assertion: Assertion[A]): Assertion[A] =
    new Assertion(assertion.render.negate, !assertion.run(_))

  /**
   * Makes a new assertion that always fails.
   */
  val nothing: Assertion[Any] =
    Assertion.assertion("nothing", M.result + M.is + "nothing...")()(_ => false)

  /**
   * Makes a new assertion that requires a given sequence to start with the
   * specified prefix.
   */
  def startsWith[A](prefix: Seq[A]): Assertion[Seq[A]] =
    Assertion.assertion[Seq[A]](
      "startsWith", //
      M.result + M.does + "start with" + M.value(prefix)
    )(param(prefix))(
      _.startsWith(prefix)
    )

  /**
   * Makes a new assertion that requires a given string to start with a specified prefix.
   */
  def startsWithString(prefix: String): Assertion[String] =
    Assertion.assertion[String](
      "startsWithString",
      M.result + M.did + "start with" + M.value(prefix)
    )(param(prefix))(
      _.startsWith(prefix)
    )

  /**
   * Makes a new assertion that requires an exit value to succeed.
   */
  def succeeds[A](assertion: Assertion[A]): Assertion[Exit[Any, A]] =
    Assertion.assertionRec[Exit[Any, A], A]("succeeds", M.result + M.is + "a success")(param(assertion))(assertion) {
      case Exit.Success(a) => Some(a)
      case _               => None
    }

  /**
   * Makes a new assertion that requires the expression to throw.
   */
  def throws[A](assertion: Assertion[Throwable]): Assertion[A] =
    Assertion.assertionRec[A, Throwable]("throws", M.result + M.did + "throw an exception")(param(assertion))(
      assertion
    ) { actual =>
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
