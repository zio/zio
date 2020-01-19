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

import scala.reflect.ClassTag

import zio.{ Cause, Exit, ZIO }

/**
 * An `Assertion[A]` is capable of producing assertion results on an `A`. As a
 * proposition, assertions compose using logical conjunction and disjunction,
 * and can be negated.
 */
final class Assertion[-A] private (
  val render: Assertion.Render,
  val run: (=> A) => AssertResult
) extends ((=> A) => AssertResult) { self =>
  import zio.test.Assertion.Render._

  /**
   * Returns a new assertion that succeeds only if both assertions succeed.
   */
  def &&[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    new Assertion(infix(param(self), "&&", param(that)), { actual =>
      self.run(actual) && that.run(actual)
    })

  /**
   * A symbolic alias for `label`.
   */
  def ??(string: String): Assertion[A] =
    label(string)

  /**
   * Returns a new assertion that succeeds if either assertion succeeds.
   */
  def ||[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    new Assertion(infix(param(self), "||", param(that)), { actual =>
      self.run(actual) || that.run(actual)
    })

  /**
   * Evaluates the assertion with the specified value.
   */
  def apply(a: => A): AssertResult =
    run(a)

  override def equals(that: Any): Boolean = that match {
    case that: Assertion[_] => this.toString == that.toString
  }

  override def hashCode: Int =
    toString.hashCode

  /**
   * Labels this assertion with the specified string.
   */
  def label(string: String): Assertion[A] =
    new Assertion(infix(param(self), "??", param(quoted(string))), run)

  /**
   * Returns the negation of this assertion.
   */
  def negate: Assertion[A] =
    Assertion.not(self)

  /**
   * Tests the assertion to see if it would succeed on the given element.
   */
  def test(a: A): ZIO[Any, Nothing, Boolean] =
    run(a).isSuccess

  /**
   * Provides a meaningful string rendering of the assertion.
   */
  override def toString: String =
    render.toString
}

object Assertion extends AssertionVariants {
  import zio.test.Assertion.Render._

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
    def field(name: String): String =
      "_." + name

    /**
     * Create a `Render` from an assertion combinator that should be rendered
     * using standard function notation.
     */
    def function(name: String, paramLists: List[List[RenderParam]]): Render =
      Render.Function(name, paramLists)

    /**
     * Create a `Render` from an assertion combinator that should be rendered
     * using infix function notation.
     */
    def infix(left: RenderParam, op: String, right: RenderParam): Render =
      Render.Infix(left, op, right)

    /**
     * Construct a `RenderParam` from an `Assertion`.
     */
    def param[A](assertion: Assertion[A]): RenderParam =
      RenderParam.Assertion(assertion)

    /**
     * Construct a `RenderParam` from a value.
     */
    def param[A](value: A): RenderParam =
      RenderParam.Value(value)

    /**
     * Quote a string so it renders as a valid Scala string when rendered.
     */
    def quoted(string: String): String =
      "\"" + string + "\""

    /**
     * Creates a string representation of an unapply method for a term.
     */
    def unapply(termName: String): String =
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
  val anything: Assertion[Any] =
    Assertion.assertion("anything")()(_ => true)

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  def assertion[A](name: String)(params: RenderParam*)(run: (=> A) => Boolean): Assertion[A] = {
    lazy val assertion: Assertion[A] = assertionDirect(name)(params: _*) { actual =>
      if (run(actual)) BoolAlgebraM.success(AssertionValue(assertion, actual))
      else BoolAlgebraM.failure(AssertionValue(assertion, actual))
    }
    assertion
  }

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  def assertionM[R, E, A](
    name: String
  )(params: RenderParam*)(run: (=> A) => ZIO[Any, Nothing, Boolean]): Assertion[A] = {
    lazy val assertion: Assertion[A] = assertionDirect(name)(params: _*) { actual =>
      BoolAlgebraM.fromEffect(run(actual)).flatMap { p =>
        if (p) BoolAlgebraM.success(AssertionValue(assertion, actual))
        else BoolAlgebraM.failure(AssertionValue(assertion, actual))
      }
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
  )(get: (=> A) => Option[B], orElse: AssertionValue => AssertResult = BoolAlgebraM.failure): Assertion[A] = {
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

  def assertionRecM[R, E, A, B](
    name: String
  )(params: RenderParam*)(
    assertion: Assertion[B]
  )(
    get: (=> A) => ZIO[Any, Nothing, Option[B]],
    orElse: AssertionValue => AssertResult = BoolAlgebraM.failure
  ): Assertion[A] = {
    lazy val result: Assertion[A] = assertionDirect(name)(params: _*) { a =>
      BoolAlgebraM.fromEffect(get(a)).flatMap {
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
  def approximatelyEquals[A: Numeric](reference: A, tolerance: A): Assertion[A] =
    Assertion.assertion("approximatelyEquals")(param(reference), param(tolerance)) { actual =>
      val referenceType = implicitly[Numeric[A]]
      val max           = referenceType.plus(reference, tolerance)
      val min           = referenceType.minus(reference, tolerance)

      referenceType.gteq(actual, min) && referenceType.lteq(actual, max)
    }

  /**
   * Makes a new assertion that requires an iterable contain the specified
   * element. See [[Assertion.exists]] if you want to require an iterable to contain an element
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
   * Makes a new assertion that requires a given string to equal another ignoring case
   */
  def equalsIgnoreCase(other: String): Assertion[String] =
    Assertion.assertion("equalsIgnoreCase")(param(other))(_.equalsIgnoreCase(other))

  /**
   * Makes a new assertion that requires an iterable contain an element
   * satisfying the given assertion. See [[Assertion.contains]] if you only need an iterable
   * to contain a given element.
   */
  def exists[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRecM("exists")(param(assertion))(assertion) { actual =>
      ZIO
        .foreach(actual) { a =>
          assertion.test(a).map { p =>
            if (p) Some(a) else None
          }
        }
        .map(_.find(_.isDefined).flatten)
    }

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
   * Makes a new assertion that requires an iterable contain only elements
   * satisfying the given assertion.
   */
  def forall[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRecM("forall")(param(assertion))(assertion)(
      actual =>
        ZIO
          .foreach(actual) { a =>
            assertion.test(a).map { p =>
              if (p) None else Some(a)
            }
          }
          .map(_.find(_.isDefined).flatten),
      BoolAlgebraM.success
    )

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
   * Makes a new assertion that requires an iterable to contain the first
   * element satisfying the given assertion
   */
  def hasFirst[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("hasFirst")(param(assertion))(assertion) { actual =>
      actual.headOption
    }

  /**
   * Makes a new assertion that requires an iterable to contain the last
   * element satisfying the given assertion
   */
  def hasLast[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionRec("hasLast")(param(assertion))(assertion) { actual =>
      actual.lastOption
    }

  /**
   * Makes a new assertion that requires an Iterable to have the same elements
   * as the specified Iterable, though not necessarily in the same order
   */
  def hasSameElements[A](other: Iterable[A]): Assertion[Iterable[A]] =
    Assertion.assertion("hasSameElements")(param(other)) { actual =>
      val actualSeq = actual.toSeq
      val otherSeq  = other.toSeq

      actualSeq.diff(otherSeq).isEmpty && otherSeq.diff(actualSeq).isEmpty
    }

  /**
   * Makes a new assertion that requires the size of an iterable be satisfied
   * by the specified assertion.
   */
  def hasSize[A](assertion: Assertion[Int]): Assertion[Iterable[A]] =
    Assertion.assertionM("hasSize")(param(assertion)) { actual =>
      assertion.test(actual.size)
    }

  /**
   * Makes a new assertion that requires the size of a string be satisfied by
   * the specified assertion.
   */
  def hasSizeString(assertion: Assertion[Int]): Assertion[String] =
    Assertion.assertionM("hasSizeString")(param(assertion)) { actual =>
      assertion.test(actual.size)
    }

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
   * Makes a new assertion that requires a value be true.
   */
  def isFalse: Assertion[Boolean] =
    Assertion.assertion("isFalse")()(!_)

  /**
   * Makes a new assertion that requires the value be greater than the
   * specified reference value.
   */
  def isGreaterThan[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isGreaterThan")(param(reference)) { actual =>
      ord.gt(actual, reference)
    }

  /**
   * Makes a new assertion that requires the value be greater than or equal to
   * the specified reference value.
   */
  def isGreaterThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isGreaterThanEqualTo")(param(reference)) { actual =>
      ord.gteq(actual, reference)
    }

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
   * Makes a new assertion that requires the value be less than the specified
   * reference value.
   */
  def isLessThan[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isLessThan")(param(reference)) { actual =>
      ord.lt(actual, reference)
    }

  /**
   * Makes a new assertion that requires the value be less than or equal to the
   * specified reference value.
   */
  def isLessThanEqualTo[A](reference: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isLessThanEqualTo")(param(reference)) { actual =>
      ord.lteq(actual, reference)
    }

  /**
   * Makes a new assertion that requires an Iterable to be non empty.
   */
  val isNonEmpty: Assertion[Iterable[Any]] =
    Assertion.assertion("isNonEmpty")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a given string to be non empty
   */
  val isNonEmptyString: Assertion[String] =
    Assertion.assertion("isNonEmptyString")()(_.nonEmpty)

  /**
   * Makes a new assertion that requires a None value.
   */
  val isNone: Assertion[Option[Any]] =
    Assertion.assertion("isNone")()(_.isEmpty)

  /**
   * Makes a new assertion that requires a null value.
   */
  val isNull: Assertion[Any] =
    Assertion.assertion("isNull")()(_ == null)

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
   * Makes a new assertion that requires a Some value satisfying the specified
   * assertion.
   */
  def isSome[A](assertion: Assertion[A]): Assertion[Option[A]] =
    Assertion.assertionRec("isSome")(param(assertion))(assertion)(identity(_))

  /**
   * Makes an assertion that requires a value have the specified type.
   *
   * Example:
   * {{{
   *   assert(Duration.fromNanos(1), isSubtype[Duration.Finite](Assertion.anything))
   * }}}
   */
  def isSubtype[A](assertion: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    Assertion.assertionRec("isSubtype")(param(C.runtimeClass.getSimpleName))(assertion) { actual =>
      if (C.runtimeClass.isAssignableFrom(actual.getClass())) Some(actual.asInstanceOf[A])
      else None
    }

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
   * Returns a new assertion that requires a value to fall within a
   * specified min and max (inclusive).
   */
  def isWithin[A](min: A, max: A)(implicit ord: Ordering[A]): Assertion[A] =
    Assertion.assertion("isWithin")(param(min), param(max)) { actual =>
      ord.gteq(actual, min) && ord.lteq(actual, max)
    }

  /**
   * Makes a new assertion that requires a given string to match the specified regular expression.
   */
  def matchesRegex(regex: String): Assertion[String] =
    Assertion.assertion("matchesRegex")(param(regex))(_.matches(regex))

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
   * Makes a new assertion that requires a given string to start with a specified prefix
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
   * Returns a new assertion that requires the expression to throw.
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
   * Returns a new assertion that requires the expression to throw an instance
   * of given type (or its subtype).
   */
  def throwsA[E: ClassTag]: Assertion[Any] =
    throws(isSubtype[E](anything))
}
