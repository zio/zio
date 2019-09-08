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

import zio.Exit
import zio.test.Assertion._
import zio.test.Assertion.Render._

/**
 * An `Assertion[A]` is capable of producing assertion results on an `A`. As a
 * proposition, assertions compose using logical conjuction and disjunction,
 * and can be negated.
 */
class Assertion[-A] private (render: Render, val run: (=> A) => AssertResult[Either[AssertionValue, Unit]])
    extends ((=> A) => AssertResult[Either[AssertionValue, Unit]]) {
  self =>

  /**
   * Returns a new assertion that succeeds only if both assertions succeed.
   */
  final def &&[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    new Assertion(infix(param(self), "&&", param(that)), { actual =>
      self.run(actual) && that.run(actual)
    })

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
  final def apply(a: => A): AssertResult[Either[AssertionValue, Unit]] =
    run(a)

  override final def equals(that: Any): Boolean = that match {
    case that: Assertion[_] => this.toString == that.toString
  }

  override final def hashCode: Int =
    toString.hashCode

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
    Assertion.assertion[Any]("anything")()(_ => AssertResult.success(()))

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  final def assertion[A](
    name: String
  )(params: RenderParam*)(run: (=> A) => AssertResult[Either[Unit, Unit]]): Assertion[A] =
    assertionRec[A](name)(params: _*)(
      (assertion, a) =>
        run(a).map {
          case Left(_) => Left(AssertionValue(assertion, a))
          case _       => Right(())
        }
    )

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  final def assertionDirect[A](
    name: String
  )(params: RenderParam*)(run: (=> A) => AssertResult[Either[AssertionValue, Unit]]): Assertion[A] =
    new Assertion(function(name, List(params.toList)), run)

  /**
   * Makes a new `Assertion` from a pretty-printing and a function, passing
   * the assertion itself to the specified function, so it can embed a
   * recursive reference into the assert result.
   */
  final def assertionRec[A](
    name: String
  )(params: RenderParam*)(run: (Assertion[A], => A) => AssertResult[Either[AssertionValue, Unit]]): Assertion[A] = {
    lazy val assertion: Assertion[A] = assertionDirect[A](name)(params: _*)(a => run(assertion, a))
    assertion
  }

  /**
   * Makes a new assertion that requires an iterable contain the specified
   * element.
   */
  final def contains[A](element: A): Assertion[Iterable[A]] =
    Assertion.assertion("contains")(param(element)) { actual =>
      if (!actual.exists(_ == element)) AssertResult.failure(())
      else AssertResult.success(())
    }

  /**
   * Makes a new assertion that requires a value equal the specified value.
   */
  final def equalTo[A](expected: A): Assertion[A] =
    Assertion.assertion("equalTo")(param(expected)) { actual =>
      val equal = (expected, actual) match {
        case (left: Array[_], right: Array[_]) => left.sameElements[Any](right)
        case (left, right)                     => left == right
      }
      if (equal) AssertResult.success(()) else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires an iterable contain one element
   * satisfying the given assertion.
   */
  final def exists[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertion("exists")(param(assertion)) { actual =>
      if (!actual.exists(assertion.test(_))) AssertResult.failure(())
      else AssertResult.success(())
    }

  /**
   * Makes a new assertion that requires an exit value to fail.
   */
  final def fails[E](assertion: Assertion[E]): Assertion[Exit[E, Any]] =
    Assertion.assertionRec[Exit[E, Any]]("fails")(param(assertion)) { (self, actual) =>
      actual match {
        case Exit.Failure(cause) if cause.failures.length > 0 => assertion.run(cause.failures.head)

        case _ => AssertResult.failure(AssertionValue(self, actual))
      }
    }

  /**
   * Makes a new assertion that requires an iterable contain only elements
   * satisfying the given assertion.
   */
  final def forall[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertionDirect[Iterable[A]]("forall")(param(assertion)) { actual =>
      actual.map(assertion(_)).toList match {
        case head :: tail =>
          tail.foldLeft(head) {
            case (AssertResult.Value(Right(_)), next) => next
            case (acc, _)                             => acc
          }
        case Nil => AssertResult.success(())
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
    Assertion.assertionDirect[A]("hasField")(param(quoted(name)), param(field(name)), param(assertion)) { actual =>
      assertion(proj(actual))
    }

  /**
   * Makes a new assertion that requires the size of an iterable be satisfied
   * by the specified assertion.
   */
  final def hasSize[A](assertion: Assertion[Int]): Assertion[Iterable[A]] =
    Assertion.assertion[Iterable[A]]("hasSize")(param(assertion)) { actual =>
      assertion.run(actual.size).map {
        case Left(_) => Left(())
        case _       => Right(())
      }
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
    Assertion.assertionRec[Sum]("isCase")(param(termName), param(unapply(termName)), param(assertion)) {
      (self, actual) =>
        term(actual).fold[AssertResult[Either[AssertionValue, Unit]]](
          AssertResult.failure(AssertionValue(self, actual))
        )(assertion(_))
    }

  /**
   * Makes a new assertion that requires a value be true.
   */
  final def isFalse: Assertion[Boolean] = Assertion.assertion(s"isFalse")() { actual =>
    if (!actual) AssertResult.success(()) else AssertResult.failure(())
  }

  /**
   * Makes a new assertion that requires the numeric value be greater than
   * the specified reference value.
   */
  final def isGreaterThan[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion("isGreaterThan")(param(reference)) { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) > 0) AssertResult.success(())
      else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires the numeric value be greater than
   * or equal to the specified reference value.
   */
  final def isGreaterThanEqualTo[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion("isGreaterThanEqualTo")(param(reference)) { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) >= 0) AssertResult.success(())
      else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires an exit value to be interrupted.
   */
  final def isInterrupted: Assertion[Exit[Any, Any]] =
    Assertion.assertionRec[Exit[Any, Any]]("isInterrupted")() { (self, actual) =>
      actual match {
        case Exit.Failure(cause) if cause.interrupted => AssertResult.success(())
        case _                                        => AssertResult.failure(AssertionValue(self, actual))
      }
    }

  /**
   * Makes a new assertion that requires a Left value satisfying a specified
   * assertion.
   */
  final def isLeft[A](assertion: Assertion[A]): Assertion[Either[A, Any]] =
    Assertion.assertionRec[Either[A, Any]]("isLeft")(param(assertion)) { (self, actual) =>
      actual match {
        case Left(a)  => assertion.run(a)
        case Right(_) => AssertResult.failure(AssertionValue(self, actual))
      }
    }

  /**
   * Makes a new assertion that requires the numeric value be less than
   * the specified reference value.
   */
  final def isLessThan[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion("isLessThan")(param(reference)) { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) < 0) AssertResult.success(())
      else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires the numeric value be less than
   * or equal to the specified reference value.
   */
  final def isLessThanEqualTo[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion("isLessThanEqualTo")(param(reference)) { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) <= 0) AssertResult.success(())
      else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires a None value.
   */
  final val isNone: Assertion[Option[Any]] = Assertion.assertion(s"isNone")() { actual =>
    actual match {
      case None    => AssertResult.success(())
      case Some(_) => AssertResult.failure(())
    }
  }

  /**
   * Makes a new assertion that requires a Right value satisfying a specified
   * assertion.
   */
  final def isRight[A](assertion: Assertion[A]): Assertion[Either[Any, A]] =
    Assertion.assertionRec[Either[Any, A]]("isRight")(param(assertion)) { (self, actual) =>
      actual match {
        case Right(a) => assertion.run(a)
        case Left(_)  => AssertResult.failure(AssertionValue(self, actual))
      }
    }

  /**
   * Makes a new assertion that requires a Some value satisfying the specified
   * assertion.
   */
  final def isSome[A](assertion: Assertion[A]): Assertion[Option[A]] =
    Assertion.assertionRec[Option[A]]("isSome")(param(assertion)) { (self, actual) =>
      actual match {
        case Some(a) => assertion.run(a)
        case None    => AssertResult.failure(AssertionValue(self, actual))
      }
    }

  /**
   * Makes an assertion that requires a value have the specified type.
   */
  final def isSubtype[A](assertion: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    Assertion.assertionRec[Any]("isSubtype")(param(C.runtimeClass.getSimpleName)) { (self, actual) =>
      if (C.runtimeClass.isAssignableFrom(actual.getClass())) assertion(actual.asInstanceOf[A])
      else AssertResult.failure(AssertionValue(self, actual))
    }

  /**
   * Makes a new assertion that requires a value be true.
   */
  final def isTrue: Assertion[Boolean] = Assertion.assertion(s"isTrue")() { actual =>
    if (actual) AssertResult.success(()) else AssertResult.failure(())
  }

  /**
   * Makes a new assertion that requires the value be unit.
   */
  final def isUnit: Assertion[Any] =
    Assertion.assertion("isUnit")() { actual =>
      actual match {
        case () => AssertResult.success(())
        case _  => AssertResult.failure(())
      }
    }

  /**
   * Returns a new assertion that requires a numeric value to fall within a
   * specified min and max (inclusive).
   */
  final def isWithin[A: Numeric](min: A, max: A): Assertion[A] =
    Assertion.assertion("isWithin")(param(min), param(max)) { actual =>
      if (implicitly[Numeric[A]].compare(actual, min) < 0) AssertResult.failure(())
      else if (implicitly[Numeric[A]].compare(actual, max) > 0) AssertResult.failure(())
      else AssertResult.success(())
    }

  /**
   * Makes a new assertion that negates the specified assertion.
   */
  final def not[A](assertion: Assertion[A]): Assertion[A] =
    Assertion.assertionRec[A]("not")(param(assertion)) { (self, actual) =>
      if (assertion.run(actual).isSuccess) AssertResult.failure(AssertionValue(self, actual))
      else AssertResult.success(())
    }

  /**
   * Makes a new assertion that always fails.
   */
  final val nothing: Assertion[Any] = Assertion.assertionRec[Any]("nothing")() { (self, actual) =>
    AssertResult.failure(AssertionValue(self, actual))
  }

  /**
   * Makes a new assertion that requires an exit value to succeed.
   */
  final def succeeds[A](assertion: Assertion[A]): Assertion[Exit[Any, A]] =
    Assertion.assertionRec[Exit[Any, A]]("succeeds")(param(assertion)) { (self, actual) =>
      actual match {
        case Exit.Success(a) => assertion.run(a)

        case exit => AssertResult.failure(AssertionValue(self, exit))
      }
    }

  /**
   * Returns a new assertion that requires the expression to throw.
   */
  final def throws[A](assertion: Assertion[Throwable]): Assertion[A] =
    Assertion.assertionRec[A]("throws")(param(assertion)) { (self, actual) =>
      try {
        val _ = actual
        AssertResult.failure(AssertionValue(self, actual))
      } catch {
        case t: Throwable => assertion(t)
      }
    }
}
