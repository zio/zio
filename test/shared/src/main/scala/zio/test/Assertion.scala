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

import zio.Exit

import scala.reflect.ClassTag

/**
 * An `Assertion[A]` is capable of producing assertion results on an `A`. As a
 * proposition, assertions compose using logical conjuction and disjunction,
 * and can be negated.
 */
class Assertion[-A] private (render: String, val run: (=> A) => AssertionResult) extends ((=> A) => AssertionResult) {
  self =>
  import AssertResult._

  /**
   * Returns a new assertion that succeeds only if both assertions succeed.
   */
  final def &&[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    Assertion.assertionDirect(s"(${self} && ${that})") { actual =>
      self.run(actual) match {
        case Failure(l) => Failure(l)
        case Success    => that.run(actual)
        case Ignore     => that.run(actual)
      }
    }

  /**
   * Returns a new assertion that succeeds if either assertion succeeds.
   */
  final def ||[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    Assertion.assertionDirect(s"(${self} || ${that})") { actual =>
      self.run(actual) match {
        case Failure(_) => that.run(actual)
        case Success    => Success
        case Ignore     => that.run(actual)
      }
    }

  /**
   * Evaluates the assertion with the specified value.
   */
  final def apply(a: => A): AssertionResult = run(a)

  override final def equals(that: Any): Boolean = that match {
    case that: Assertion[_] => this.toString == that.toString
  }

  override final def hashCode: Int = toString.hashCode

  /**
   * Returns the negation of this assertion.
   */
  final def negate: Assertion[A] = Assertion.not(self)

  /**
   * Tests the assertion to see if it would succeed on the given element.
   */
  final def test(a: A): Boolean = run(a) match {
    case Success => true
    case _       => false
  }

  /**
   * Provides a meaningful string rendering of the assertion.
   */
  override final def toString: String = render
}

object Assertion {

  /**
   * Makes a new assertion that always succeeds.
   */
  final val anything: Assertion[Any] = Assertion.assertion[Any]("anything")(_ => AssertResult.success)

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  final def assertion[A](render: String)(run: (=> A) => AssertResult[Unit]): Assertion[A] =
    assertionRec[A](render)((assertion, a) => run(a).map(_ => AssertionValue(assertion, a)))

  /**
   * Makes a new `Assertion` from a pretty-printing and a function.
   */
  final def assertionDirect[A](render: String)(run: (=> A) => AssertionResult): Assertion[A] =
    new Assertion(render, run)

  /**
   * Makes a new `Assertion` from a pretty-printing and a function, passing
   * the assertion itself to the specified function, so it can embed a
   * recursive reference into the assert result.
   */
  final def assertionRec[A](render: String)(run: (Assertion[A], => A) => AssertionResult): Assertion[A] = {
    lazy val assertion: Assertion[A] = assertionDirect[A](render)(a => run(assertion, a))

    assertion
  }

  /**
   * Makes a new assertion that requires an iterable contain the specified
   * element.
   */
  final def contains[A](element: A): Assertion[Iterable[A]] =
    Assertion.assertion(s"contains(${element})") { actual =>
      if (!actual.exists(_ == element)) AssertResult.failure(())
      else AssertResult.success
    }

  /**
   * Makes a new assertion that requires a value equal the specified value.
   */
  final def equalTo[A](expected: A): Assertion[A] =
    Assertion.assertion(s"equalTo(${expected})") { actual =>
      if (actual == expected) AssertResult.success
      else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires an iterable contain one element
   * satisfying the given assertion.
   */
  final def exists[A](assertion: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.assertion(s"exists(${assertion})") { actual =>
      if (!actual.exists(assertion.test(_))) AssertResult.failure(())
      else AssertResult.success
    }

  /**
   * Makes a new assertion that requires an exit value to fail.
   */
  final def fails[E](assertion: Assertion[E]): Assertion[Exit[E, Any]] =
    Assertion.assertionRec[Exit[E, Any]](s"fails(${assertion})") { (self, actual) =>
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
    Assertion.assertionDirect[Iterable[A]](s"forall(${assertion})") { actual =>
      actual.map(assertion(_)).toList match {
        case head :: tail =>
          tail.foldLeft(head) {
            case (AssertResult.Success, next) => next
            case (acc, _)                     => acc
          }
        case Nil => AssertResult.success
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
    Assertion.assertionDirect[A]("hasField(\"" + name + "\"" + s", _.${name}, ${assertion})") { actual =>
      assertion(proj(actual))
    }

  /**
   * Makes a new assertion that requires the size of an iterable be satisfied
   * by the specified assertion.
   */
  final def hasSize[A](assertion: Assertion[Int]): Assertion[Iterable[A]] =
    Assertion.assertion[Iterable[A]](s"hasSize(${assertion})") { actual =>
      assertion.run(actual.size).map(_ => ())
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
    Assertion.assertionRec[Sum]("isCase(\"" + termName + "\", " + s"${termName}.unapply, ${assertion})") {
      (self, actual) =>
        term(actual).fold(AssertResult.failure(AssertionValue(self, actual)))(assertion(_))
    }

  /**
   * Makes a new assertion that requires a value be true.
   */
  final def isFalse: Assertion[Boolean] = Assertion.assertion(s"isFalse") { actual =>
    if (!actual) AssertResult.success else AssertResult.failure(())
  }

  /**
   * Makes a new assertion that requires the numeric value be greater than
   * the specified reference value.
   */
  final def isGreaterThan[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion(s"isGreaterThan(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) > 0) AssertResult.success
      else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires the numeric value be greater than
   * or equal to the specified reference value.
   */
  final def isGreaterThanEqualTo[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion(s"isGreaterThanEqualTo(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) >= 0) AssertResult.success
      else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires a Left value satisfying a specified
   * assertion.
   */
  final def isLeft[A](assertion: Assertion[A]): Assertion[Either[A, Any]] =
    Assertion.assertionRec[Either[A, Any]](s"isLeft(${assertion})") { (self, actual) =>
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
    Assertion.assertion(s"isLessThan(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) < 0) AssertResult.success
      else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires the numeric value be less than
   * or equal to the specified reference value.
   */
  final def isLessThanEqualTo[A: Numeric](reference: A): Assertion[A] =
    Assertion.assertion(s"isLessThanEqualTo(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) <= 0) AssertResult.success
      else AssertResult.failure(())
    }

  /**
   * Makes a new assertion that requires a None value.
   */
  final val isNone: Assertion[Option[Any]] = Assertion.assertion(s"isNone") { actual =>
    actual match {
      case None    => AssertResult.success
      case Some(_) => AssertResult.failure(())
    }
  }

  /**
   * Makes a new assertion that requires a Right value satisfying a specified
   * assertion.
   */
  final def isRight[A](assertion: Assertion[A]): Assertion[Either[Any, A]] =
    Assertion.assertionRec[Either[Any, A]](s"isRight(${assertion})") { (self, actual) =>
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
    Assertion.assertionRec[Option[A]](s"isSome(${assertion})") { (self, actual) =>
      actual match {
        case Some(a) => assertion.run(a)
        case None    => AssertResult.failure(AssertionValue(self, actual))
      }
    }

  /**
   * Makes an assertion that requires a value have the specified type.
   */
  final def isSubtype[A](assertion: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    Assertion.assertionRec[Any](s"isSubtype[${C.runtimeClass.getSimpleName()}]") { (self, actual) =>
      if (C.runtimeClass.isAssignableFrom(actual.getClass())) assertion(actual.asInstanceOf[A])
      else AssertResult.failure(AssertionValue(self, actual))
    }

  /**
   * Makes a new assertion that requires a value be true.
   */
  final def isTrue: Assertion[Boolean] = Assertion.assertion(s"isTrue") { actual =>
    if (actual) AssertResult.success else AssertResult.failure(())
  }

  /**
   * Makes a new assertion that requires the value be unit.
   */
  final def isUnit: Assertion[Any] =
    Assertion.assertion("isUnit") { actual =>
      actual match {
        case () => AssertResult.success
        case _  => AssertResult.failure(())
      }
    }

  /**
   * Returns a new assertion that requires a numeric value to fall within a
   * specified min and max (inclusive).
   */
  final def isWithin[A: Numeric](min: A, max: A): Assertion[A] =
    Assertion.assertion(s"isWithin(${min}, ${max})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, min) < 0) AssertResult.failure(())
      else if (implicitly[Numeric[A]].compare(actual, max) > 0) AssertResult.failure(())
      else AssertResult.success
    }

  /**
   * Makes a new assertion that negates the specified assertion.
   */
  final def not[A](assertion: Assertion[A]): Assertion[A] =
    Assertion.assertionRec[A](s"not(${assertion})") { (self, actual) =>
      assertion.run(actual) match {
        case AssertResult.Success    => AssertResult.Failure(AssertionValue(self, actual))
        case AssertResult.Failure(_) => AssertResult.Success
        case AssertResult.Ignore     => AssertResult.Ignore
      }
    }

  /**
   * Makes a new assertion that always fails.
   */
  final val nothing: Assertion[Any] = Assertion.assertionRec[Any]("nothing") { (self, actual) =>
    AssertResult.failure(AssertionValue(self, actual))
  }

  /**
   * Makes a new assertion that requires an exit value to succeed.
   */
  final def succeeds[A](assertion: Assertion[A]): Assertion[Exit[Any, A]] =
    Assertion.assertionRec[Exit[Any, A]](s"succeeds(${assertion})") { (self, actual) =>
      actual match {
        case Exit.Success(a) => assertion.run(a)

        case exit => AssertResult.failure(AssertionValue(self, exit))
      }
    }

  /**
   * Returns a new assertion that requires the expression to throw.
   */
  final def throws[A](assertion: Assertion[Throwable]): Assertion[A] =
    Assertion.assertionRec[A](s"throws(${assertion})") { (self, actual) =>
      try {
        val _ = actual
      } catch {
        case t: Throwable => assertion(t)
      }

      AssertResult.failure(AssertionValue(self, actual))
    }
}
