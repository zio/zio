/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

import scala.reflect.ClassTag

import zio.Cause
import zio.Exit
import zio.test.Assertion._
import zio.test.Assertion.Render._

/**
 * An `Assertion[A]` is capable of producing assertion results on an `A`. As a
 * proposition, assertions compose using logical conjuction and disjunction,
 * and can be negated.
 */
class Assertion[-A] private (val render: Render, val run: (=> A) => AssertResult) extends ((=> A) => AssertResult) {
  self =>

  /**
   * Returns a new assertion that succeeds only if both assertions succeed.
   */
  final def &&[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    new Assertion(infix(param(self), "&&", param(that)), { actual =>
      self.run(actual) && that.run(actual)
    })

  /**
   * A symbolic alias for `label`.
   */
  final def ??(string: String): Assertion[A] =
    label(string)

  /**
   * Returns a new assertion that succeeds if either assertion succeeds.
   */
  final def ||[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    new Assertion(infix(param(self), "||", param(that)), { actual =>
      self.run(actual) || that.run(actual)
    })

  /**
   * Evaluates the assertion with the specified value.
   */
  final def apply(a: => A): AssertResult =
    run(a)

  override final def equals(that: Any): Boolean = that match {
    case that: Assertion[_] => this.toString == that.toString
  }

  override final def hashCode: Int =
    toString.hashCode

  /**
   * Labels this assertion with the specified string.
   */
  final def label(string: String): Assertion[A] =
    new Assertion(infix(param(self), "??", param(quoted(string))), run)

  /**
   * Returns the negation of this assertion.
   */
  final def negate: Assertion[A] =
    Assertion.not(self)

  /**
   * Tests the assertion to see if it would succeed on the given element.
   */
  final def test(a: A): Boolean =
    run(a).isSuccess

  /**
   * Provides a meaningful string rendering of the assertion.
   */
  override final def toString: String =
    render.toString
}

object Assertion {

  /**
   * `Render` captures both the name of an assertion as well as the parameters
   * to the assertion combinator for pretty-printing.
   */
  sealed trait Render {
    override final def toString: String = this match {
      case Render.Function(name, paramLists) =>
        name + paramLists.map(_.mkString("(", ", ", ")")).mkString
      case Render.Infix(left, op, right) =>
        "(" + left + " " + op + " " + right + ")"
    }
  }
  object Render {
    final case class Function(name: String, paramLists: List[List[RenderParam]]) extends Render
    final case class Infix(left: RenderParam, op: String, right: RenderParam)    extends Render

    /**
     * Creates a string representation of a field accessor.
     */
    final def field(name: String): String =
      "_." + name

    /**
     * Create a `Render` from an assertion combinator that should be rendered
     * using standard function notation.
     */
    final def function(name: String, paramLists: List[List[RenderParam]]): Render =
      Render.Function(name, paramLists)

    /**
     * Create a `Render` from an assertion combinator that should be rendered
     * using infix function notation.
     */
    final def infix(left: RenderParam, op: String, right: RenderParam): Render =
      Render.Infix(left, op, right)

    /**
     * Construct a `RenderParam` from an `Assertion`.
     */
    final def param[A](assertion: Assertion[A]): RenderParam =
      RenderParam.Assertion(assertion)

    /**
     * Construct a `RenderParam` from a value.
     */
    final def param[A](value: A): RenderParam =
      RenderParam.Value(value)

    /**
     * Quote a string so it renders as a valid Scala string when rendered.
     */
    final def quoted(string: String): String =
      "\"" + string + "\""

    /**
     * Creates a string representation of an unapply method for a term.
     */
    final def unapply(termName: String): String =
      termName + ".unapply"
  }

  sealed trait RenderParam {
    override final def toString: String = this match {
      case RenderParam.Assertion(assertion) => assertion.toString
      case RenderParam.Value(value)         => value.toString
    }
  }
  object RenderParam {
    final case class Assertion[A](assertion: zio.test.Assertion[A]) extends RenderParam
    final case class Value(value: Any)                              extends RenderParam
  }

  /**
   * Makes a new assertion that always succeeds.
   */
  final val anything: Assertion[Any] =
    Assertion.assertion("anything")()(_ => true)

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  final def assertion[A](name: String)(params: RenderParam*)(run: (=> A) => Boolean): Assertion[A] = {
    lazy val assertion: Assertion[A] = assertionDirect(name)(params: _*) { actual =>
      if (run(actual)) BoolAlgebra.success(AssertionValue(assertion, actual))
      else BoolAlgebra.failure(AssertionValue(assertion, actual))
    }
    assertion
  }

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  final def assertionDirect[A](
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
  final def assertionRec[A, B](
    name: String
  )(params: RenderParam*)(
    assertion: Assertion[B]
  )(get: (=> A) => Option[B], orElse: AssertionValue => AssertResult = BoolAlgebra.failure): Assertion[A] = {
    lazy val result: Assertion[A] = assertionDirect(name)(params: _*) { a =>
      get(a) match {
        case Some(b) =>
          assertion.run(b).as(AssertionValue(new Assertion(assertion.render, assertion.run), b))
        case None =>
          orElse(AssertionValue(result, a))
      }
    }
    result
  }

  /**
   * Makes a new assertion that requires a given numeric value to match a value with some tolerance.
   */
  final def approximatelyEquals[A: Numeric](reference: A, tolerance: A): Assertion[A] =
    Assertion.assertion("approximatelyEquals")(param(reference), param(tolerance)) { actual =>
      val referenceType = implicitly[Numeric[A]]
      val max           = referenceType.plus(reference, tolerance)
      val min           = referenceType.minus(reference, tolerance)

      referenceType.gteq(actual, min) && referenceType.lteq(actual, max)
    }

  /**
   * Makes a new assertion that requires an iterable contain the specified
   * element.
   */
  final def contains[A](element: A): Assertion[Iterable[A]] =
    Assertion.assertion("contains")(param(element))(_.exists(_ == element))

  /**
   * Makes a new assertion that requires a `Cause` contain the specified
   * cause.
   */
  final def containsCause[E](cause: Cause[E]): Assertion[Cause[E]] =
    Assertion.assertion("containsCause")(param(cause))(_.contains(cause))

  /**
   * Makes a new assertion that requires a substring to be present.
   */
  final def containsString(element: String): Assertion[String] =
    Assertion.assertion("containsString")(param(element))(_.contains(element))

  /**
   * Makes a new assertion that requires an exit value to die.
   */
  final def dies(assertion: Assertion[Throwable]): Assertion[Exit[Any, Any]] =
    Assertion.assertionRec("dies")(param(assertion))(assertion) {
      case Exit.Failure(cause) => cause.dieOption
      case _                   => None
    }

  /**
   * Makes a new assertion that requires a given string to end with the specified suffix.
   */
  final def endsWith[A](suffix: Seq[A]): Assertion[Seq[A]] =
    Assertion.assertion("endsWith")(param(suffix))(_.endsWith(suffix))

  /**
   * Makes a new assertion that requires a given string to end with the specified suffix.
   */
  final def endsWithString(suffix: String): Assertion[String] =
    Assertion.assertion("endsWithString")(param(suffix))(_.endsWith(suffix))

  /**
   * Makes a new assertion that requires a value equal the specified value.
   */
  final def equalTo(expected: Any): Assertion[Any] =
    Assertion.assertion("equalTo")(param(expected)) { actual =>
      (actual, expected) match {
        case (left: Array[_], right: Array[_]) => left.sameElements[Any](right)
        case (left, right)                     => left == right
      }
    }

  /**
   * Makes a new assertion that requires a given string to equal another ignoring case
   */
  final def equalsIgnoreCase(other: String): Assertion[String] =
    Assertion.assertion("equalsIgnoreCase")(param(other))(_.equalsIgnoreCase(other))

  /**
   * Makes a new assertion that requires an iterable contain one element
   * satisfying the given assertion.
   */
  final def exists[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("exists")(param(assertion))(assertion)(_.find(assertion.test))

  /**
   * Makes a new assertion that requires an exit value to fail.
   */
  final def fails[E](assertion: Assertion[E]): Assertion[Exit[E, Any]] =
    Assertion.assertionRec("fails")(param(assertion))(assertion) {
      case Exit.Failure(cause) => cause.failures.headOption
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an exit value to fail with a cause
   * that meets the specified assertion.
   */
  final def failsCause[E](assertion: Assertion[Cause[E]]): Assertion[Exit[E, Any]] =
    Assertion.assertionRec("failsCause")(param(assertion))(assertion) {
      case Exit.Failure(cause) => Some(cause)
      case _                   => None
    }

  /**
   * Makes a new assertion that requires an iterable contain only elements
   * satisfying the given assertion.
   */
  final def forall[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("forall")(param(assertion))(assertion)(
      {
        case head :: tail =>
          Some(tail.foldLeft(head) {
            case (result, next) if assertion.test(result) => next
            case (acc, _)                                 => acc
          })
        case Nil => None
      },
      BoolAlgebra.success
    )

  /**
   * Makes a new assertion that requires a sequence to contain an element
   * satisfying the given assertion on the given position
   */
  final def hasAt[A](pos: Int)(assertion: Assertion[A]): Assertion[Seq[A]] =
    Assertion.assertionRec("hasAt")(param(assertion))(assertion) { actual =>
      if (pos >= 0 && pos < actual.size) {
        Some(actual.apply(pos))
      } else {
        None
      }
    }

  /**
   * Makes a new assertion that focuses in on a field in a case class.
   *
   * {{{
   * hasField("age", _.age, within(0, 10))
   * }}}
   */
  final def hasField[A, B](name: String, proj: A => B, assertion: Assertion[B]): Assertion[A] =
    Assertion.assertionRec("hasField")(param(quoted(name)), param(field(name)), param(assertion))(assertion) { actual =>
      Some(proj(actual))
    }

  /**
   * Makes a new assertion that requires an iterable to contain the first
   * element satisfying the given assertion
   */
  final def hasFirst[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("hasFirst")(param(assertion))(assertion) { actual =>
      actual.headOption
    }

  /**
   * Makes a new assertion that requires an iterable to contain the last
   * element satisfying the given assertion
   */
  final def hasLast[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("hasLast")(param(assertion))(assertion) { actual =>
      actual.lastOption
    }

  /**
   * Makes a new assertion that requires an Iterable to have the same elements
   * as the specified Iterable, though not necessarily in the same order
   */
  final def hasSameElements[A](other: Iterable[A]): Assertion[Iterable[A]] =
    Assertion.assertion("hasSameElements")(param(other)) { actual =>
      val actualSeq = actual.toSeq
      val otherSeq  = other.toSeq

      actualSeq.diff(otherSeq).isEmpty && otherSeq.diff(actualSeq).isEmpty
    }

  /**
   * Makes a new assertion that requires the size of an iterable be satisfied
   * by the specified assertion.
   */
  final def hasSize[A](assertion: Assertion[Int]): Assertion[Iterable[A]] =
    Assertion.assertion("hasSize")(param(assertion)) { actual =>
      assertion.test(actual.size)
    }

  /**
   * Makes a new assertion that requires the sum type be a specified term.
   *
   * {{{
   * isCase("Some", Some.unapply, anything)
   * }}}
   */
  final def isCase[Sum, Proj](
    termName: String,
    term: Sum => Option[Proj],
    assertion: Assertion[Proj]
  ): Assertion[Sum] =
    Assertion.assertionRec("isCase")(param(termName), param(unapply(termName)), param(assertion))(assertion)(term(_))

  /**
   * Makes a new assertion that requires an Iterable to be empty.
   */
  final val isEmpty: Assertion[Iterable[Any]] =
    Assertion.assertion("isEmpty")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a given string to be empty.
   */
  final val isEmptyString: Assertion[String] =
    Assertion.assertion("isEmptyString")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a value be true.
   */
  final def isFalse: Assertion[Boolean] =
    Assertion.assertion("isFalse")()(!_)

  /**
   * Makes a new assertion that requires the numeric value be greater than
   * the specified reference value.
   */
  final def isGreaterThan[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion("isGreaterThan")(param(reference)) { actual =>
      implicitly[Numeric[A]].compare(actual, reference) > 0
    }

  /**
   * Makes a new assertion that requires the numeric value be greater than
   * or equal to the specified reference value.
   */
  final def isGreaterThanEqualTo[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion("isGreaterThanEqualTo")(param(reference)) { actual =>
      implicitly[Numeric[A]].compare(actual, reference) >= 0
    }

  /**
   * Makes a new assertion that requires an exit value to be interrupted.
   */
  final def isInterrupted: Assertion[Exit[Any, Any]] =
    Assertion.assertion("isInterrupted")() {
      case Exit.Failure(cause) => cause.interrupted
      case _                   => false
    }

  /**
   * Makes a new assertion that requires a Left value satisfying a specified
   * assertion.
   */
  final def isLeft[A](assertion: Assertion[A]): Assertion[Either[A, Any]] =
    Assertion.assertionRec("isLeft")(param(assertion))(assertion) {
      case Left(a)  => Some(a)
      case Right(_) => None
    }

  /**
   * Makes a new assertion that requires the numeric value be less than
   * the specified reference value.
   */
  final def isLessThan[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion("isLessThan")(param(reference)) { actual =>
      implicitly[Numeric[A]].compare(actual, reference) < 0
    }

  /**
   * Makes a new assertion that requires the numeric value be less than
   * or equal to the specified reference value.
   */
  final def isLessThanEqualTo[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion("isLessThanEqualTo")(param(reference)) { actual =>
      implicitly[Numeric[A]].compare(actual, reference) <= 0
    }

  /**
   * Makes a new assertion that requires an Iterable to be non empty.
   */
  final val isNonEmpty: Assertion[Iterable[Any]] =
    Assertion.assertion("isNonEmpty")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a given string to be non empty
   */
  final val isNonEmptyString: Assertion[String] =
    Assertion.assertion("isNonEmptyString")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a None value.
   */
  final val isNone: Assertion[Option[Any]] =
    Assertion.assertion("isNone")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a Right value satisfying a specified
   * assertion.
   */
  final def isRight[A](assertion: Assertion[A]): Assertion[Either[Any, A]] =
    Assertion.assertionRec("isRight")(param(assertion))(assertion) {
      case Right(a) => Some(a)
      case Left(_)  => None
    }

  /**
   * Makes a new assertion that requires a Some value satisfying the specified
   * assertion.
   */
  final def isSome[A](assertion: Assertion[A]): Assertion[Option[A]] =
    Assertion.assertionRec("isSome")(param(assertion))(assertion)(identity(_))

  /**
   * Makes an assertion that requires a value have the specified type.
   *
   * Example:
   * {{{
   *   assert(Duration.fromNanos(1), isSubtype[Duration.Finite](Assertion.anything))
   * }}}
   */
  final def isSubtype[A](assertion: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    Assertion.assertionRec("isSubtype")(param(C.runtimeClass.getSimpleName))(assertion) { actual =>
      if (C.runtimeClass.isAssignableFrom(actual.getClass())) Some(actual.asInstanceOf[A])
      else None
    }

  /**
   * Makes a new assertion that requires a value be true.
   */
  final def isTrue: Assertion[Boolean] =
    Assertion.assertion("isTrue")()(identity(_))

  /**
   * Makes a new assertion that requires the value be unit.
   */
  final def isUnit: Assertion[Any] =
    Assertion.assertion("isUnit")() { actual =>
      actual match {
        case () => true
        case _  => false
      }
    }

  /**
   * Returns a new assertion that requires a numeric value to fall within a
   * specified min and max (inclusive).
   */
  final def isWithin[A: Numeric](min: A, max: A): Assertion[A] =
    Assertion.assertion("isWithin")(param(min), param(max)) { actual =>
      if (implicitly[Numeric[A]].compare(actual, min) < 0) false
      else if (implicitly[Numeric[A]].compare(actual, max) > 0) false
      else true
    }

  /**
   * Makes a new assertion that requires a given string to match the specified regular expression.
   */
  final def matchesRegex(regex: String): Assertion[String] =
    Assertion.assertion("matchesRegex")(param(regex))(_.matches(regex))

  /**
   * Makes a new assertion that negates the specified assertion.
   */
  final def not[A](assertion: Assertion[A]): Assertion[A] =
    new Assertion(assertion.render, a => BoolAlgebra.not(assertion.run(a)))

  /**
   * Makes a new assertion that always fails.
   */
  final val nothing: Assertion[Any] =
    Assertion.assertion("nothing")()(_ => false)

  /**
   * Makes a new assertion that requires a given sequence to start with the
   * specified prefix.
   */
  final def startsWith[A](prefix: Seq[A]): Assertion[Seq[A]] =
    Assertion.assertion("startsWith")(param(prefix))(_.startsWith(prefix))

  /**
   * Makes a new assertion that requires a given string to start with a specified prefix
   */
  final def startsWithString(prefix: String): Assertion[String] =
    Assertion.assertion("startsWithString")(param(prefix))(_.startsWith(prefix))

  /**
   * Makes a new assertion that requires an exit value to succeed.
   */
  final def succeeds[A](assertion: Assertion[A]): Assertion[Exit[Any, A]] =
    Assertion.assertionRec("succeeds")(param(assertion))(assertion) {
      case Exit.Success(a) => Some(a)
      case _               => None
    }

  /**
   * Returns a new assertion that requires the expression to throw.
   */
  final def throws[A](assertion: Assertion[Throwable]): Assertion[A] =
    Assertion.assertionRec("throws")(param(assertion))(assertion) { actual =>
      try {
        val _ = actual
        None
      } catch {
        case t: Throwable => Some(t)
      }
    }

  /**
   * Returns a new assertion that requires the expression to throw an instance
   * of given type (or its subtype).
   */
  final def throwsA[E: ClassTag]: Assertion[Any] =
    throws(isSubtype[E](anything))
}
