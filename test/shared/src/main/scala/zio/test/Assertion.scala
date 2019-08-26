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
 * A `Predicate[A]` is capable of producing assertion results on an `A`. As a
 * proposition, predicates compose using logical conjuction and disjunction,
 * and can be negated.
 */
class Assertion[-A] private (render: String, val run: (=> A) => PredicateResult) extends ((=> A) => PredicateResult) {
  self =>
  // import AssertResult._

  /**
   * Returns a new predicate that succeeds only if both predicates succeed.
   */
  final def &&[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    Assertion.predicateDirect(s"(${self} && ${that})") { actual =>
      self.run(actual) && that.run(actual)
    }

  /**
   * Returns a new predicate that succeeds if either predicates succeed.
   */
  final def ||[A1 <: A](that: => Assertion[A1]): Assertion[A1] =
    Assertion.predicateDirect(s"(${self} || ${that})") { actual =>
      self.run(actual) || that.run(actual)
    }

  /**
   * Evaluates the predicate with the specified value.
   */
  final def apply(a: => A): PredicateResult =
    run(a)

  override final def equals(that: Any): Boolean = that match {
    case that: Assertion[_] => this.toString == that.toString
  }

  override final def hashCode: Int =
    toString.hashCode

  /**
   * Returns the negation of this predicate.
   */
  final def negate: Assertion[A] =
    Assertion.not(self)

  /**
   * Tests the predicate to see if it would succeed on the given element.
   */
  final def test(a: A): Boolean =
    run(a).isSuccess

  /**
   * Provides a meaningful string rendering of the predicate.
   */
  override final def toString: String =
    render
}

object Assertion {

  /**
   * Makes a new predicate that always succeeds.
   */
  final val anything: Assertion[Any] =
    Assertion.predicate[Any]("anything")(_ => AssertResult.value(Right(())))

  /**
   * Makes a new predicate that requires an iterable contain the specified
   * element.
   */
  final def contains[A](element: A): Assertion[Iterable[A]] =
    Assertion.predicate(s"contains(${element})") { actual =>
      if (!actual.exists(_ == element)) AssertResult.value(Left(()))
      else AssertResult.value(Right(()))
    }

  /**
   * Makes a new predicate that requires a value equal the specified value.
   */
  final def equalTo[A](expected: A): Assertion[A] =
    Assertion.predicate(s"equalTo(${expected})") { actual =>
      if (actual == expected) AssertResult.value(Right(()))
      else AssertResult.value(Left(()))
    }

  /**
   * Makes a new predicate that requires an iterable contain one element
   * satisfying the given predicate.
   */
  final def exists[A](predicate: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.predicate(s"exists(${predicate})") { actual =>
      if (!actual.exists(predicate.test(_))) AssertResult.value(Left(()))
      else AssertResult.value(Right(()))
    }

  /**
   * Makes a new predicate that requires an exit value to fail.
   */
  final def fails[E](predicate: Assertion[E]): Assertion[Exit[E, Any]] =
    Assertion.predicateRec[Exit[E, Any]](s"fails(${predicate})") { (self, actual) =>
      actual match {
        case Exit.Failure(cause) if cause.failures.length > 0 => predicate.run(cause.failures.head)

        case _ => AssertResult.value(Left(AssertionValue(self, actual)))
      }
    }

  /**
   * Makes a new predicate that requires an iterable contain only elements
   * satisfying the given predicate.
   */
  final def forall[A](predicate: Assertion[A]): Assertion[Iterable[A]] =
    Assertion.predicateDirect[Iterable[A]](s"forall(${predicate})") { actual =>
      actual.map(predicate(_)).toList match {
        case head :: tail =>
          tail.foldLeft(head) {
            case (AssertResult.Value(Right(_)), next) => next
            case (acc, _)                             => acc
          }
        case Nil => AssertResult.value(Right(()))
      }
    }

  /**
   * Makes a new predicate that focuses in on a field in a case class.
   *
   * {{{
   * hasField("age", _.age, within(0, 10))
   * }}}
   */
  final def hasField[A, B](name: String, proj: A => B, predicate: Assertion[B]): Assertion[A] =
    Assertion.predicateDirect[A]("hasField(\"" + name + "\"" + s", _.${name}, ${predicate})") { actual =>
      predicate(proj(actual))
    }

  /**
   * Makes a new predicate that requires the size of an iterable be satisfied
   * by the specified predicate.
   */
  final def hasSize[A](predicate: Assertion[Int]): Assertion[Iterable[A]] =
    Assertion.predicate[Iterable[A]](s"hasSize(${predicate})") { actual =>
      predicate.run(actual.size).map {
        case Left(_) => Left(())
        case _       => Right(())
      }
    }

  /**
   * Makes a new predicate that requires the sum type be a specified term.
   *
   * {{{
   * isCase("Some", Some.unapply, anything)
   * }}}
   */
  final def isCase[Sum, Proj](
    termName: String,
    term: Sum => Option[Proj],
    predicate: Assertion[Proj]
  ): Assertion[Sum] =
    Assertion.predicateRec[Sum]("isCase(\"" + termName + "\", " + s"${termName}.unapply, ${predicate})") {
      (self, actual) =>
        term(actual).fold[PredicateResult](AssertResult.value(Left(AssertionValue(self, actual))))(predicate(_))
    }

  /**
   * Makes a new predicate that requires a value be true.
   */
  final def isFalse: Assertion[Boolean] = Assertion.predicate(s"isFalse") { actual =>
    if (!actual) AssertResult.value(Right(())) else AssertResult.value(Left(()))
  }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * the specified reference value.
   */
  final def isGreaterThan[A: Numeric](reference: A): Assertion[A] =
    Assertion.predicate(s"isGreaterThan(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) > 0) AssertResult.value(Right(()))
      else AssertResult.value(Left(()))
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * or equal to the specified reference value.
   */
  final def isGreaterThanEqualTo[A: Numeric](reference: A): Assertion[A] =
    Assertion.predicate(s"isGreaterThanEqualTo(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) >= 0) AssertResult.value(Right(()))
      else AssertResult.value(Left(()))
    }

  /**
   * Makes a new predicate that requires a Left value satisfying a specified
   * predicate.
   */
  final def isLeft[A](predicate: Assertion[A]): Assertion[Either[A, Any]] =
    Assertion.predicateRec[Either[A, Any]](s"isLeft(${predicate})") { (self, actual) =>
      actual match {
        case Left(a)  => predicate.run(a)
        case Right(_) => AssertResult.value(Left(AssertionValue(self, actual)))
      }
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * the specified reference value.
   */
  final def isLessThan[A: Numeric](reference: A): Assertion[A] =
    Assertion.predicate(s"isLessThan(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) < 0) AssertResult.value(Right(()))
      else AssertResult.value(Left(()))
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * the specified reference value.
   */
  final def isLessThanEqualTo[A: Numeric](reference: A): Assertion[A] =
    Assertion.predicate(s"isLessThanEqualTo(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, reference) <= 0) AssertResult.value(Right(()))
      else AssertResult.value(Left(()))
    }

  /**
   * Makes a new predicate that requires a Some value satisfying the specified
   * predicate.
   */
  final val isNone: Assertion[Option[Any]] = Assertion.predicate(s"isNone") { actual =>
    actual match {
      case None    => AssertResult.value(Right(()))
      case Some(_) => AssertResult.value(Left(()))
    }
  }

  /**
   * Makes a new predicate that requires a Right value satisfying a specified
   * predicate.
   */
  final def isRight[A](predicate: Assertion[A]): Assertion[Either[Any, A]] =
    Assertion.predicateRec[Either[Any, A]](s"isRight(${predicate})") { (self, actual) =>
      actual match {
        case Right(a) => predicate.run(a)
        case Left(_)  => AssertResult.value(Left(AssertionValue(self, actual)))
      }
    }

  /**
   * Makes a new predicate that requires a Some value satisfying the specified
   * predicate.
   */
  final def isSome[A](predicate: Assertion[A]): Assertion[Option[A]] =
    Assertion.predicateRec[Option[A]](s"isSome(${predicate})") { (self, actual) =>
      actual match {
        case Some(a) => predicate.run(a)
        case None    => AssertResult.value(Left(AssertionValue(self, actual)))
      }
    }

  /**
   * Makes a predicate that requires a value have the specified type.
   */
  final def isSubtype[A](predicate: Assertion[A])(implicit C: ClassTag[A]): Assertion[Any] =
    Assertion.predicateRec[Any](s"isSubtype[${C.runtimeClass.getSimpleName()}]") { (self, actual) =>
      if (C.runtimeClass.isAssignableFrom(actual.getClass())) predicate(actual.asInstanceOf[A])
      else AssertResult.value(Left(AssertionValue(self, actual)))
    }

  /**
   * Makes a new predicate that requires a value be true.
   */
  final def isTrue: Assertion[Boolean] = Assertion.predicate(s"isTrue") { actual =>
    if (actual) AssertResult.value(Right(())) else AssertResult.value(Left(()))
  }

  /**
   * Makes a new predicate that requires the value be unit.
   */
  final def isUnit: Assertion[Any] =
    Assertion.predicate("isUnit") { actual =>
      actual match {
        case () => AssertResult.value(Right(()))
        case _  => AssertResult.value(Left(()))
      }
    }

  /**
   * Returns a new predicate that requires a numeric value to fall within a
   * specified min and max (inclusive).
   */
  final def isWithin[A: Numeric](min: A, max: A): Assertion[A] =
    Assertion.predicate(s"isWithin(${min}, ${max})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, min) < 0) AssertResult.value(Left(()))
      else if (implicitly[Numeric[A]].compare(actual, max) > 0) AssertResult.value(Left(()))
      else AssertResult.value(Right(()))
    }

  /**
   * Makes a new predicate that negates the specified predicate.
   */
  final def not[A](predicate: Assertion[A]): Assertion[A] =
    Assertion.predicateRec[A](s"not(${predicate})") { (self, actual) =>
      if (predicate.run(actual).isSuccess) AssertResult.value(Left(AssertionValue(self, actual)))
      else AssertResult.value(Right(()))
    }

  /**
   * Makes a new predicate that always fails.
   */
  final val nothing: Assertion[Any] = Assertion.predicateRec[Any]("nothing") { (self, actual) =>
    AssertResult.value(Left(AssertionValue(self, actual)))
  }

  /**
   * Makes a new `Predicate` from a pretty-printing and a function.
   */
  final def predicate[A](render: String)(run: (=> A) => AssertResult[Either[Unit, Unit]]): Assertion[A] =
    predicateRec[A](render)(
      (predicate, a) =>
        run(a).map {
          case Left(_) => Left(AssertionValue(predicate, a))
          case _       => Right(())
        }
    )

  /**
   * Makes a new `Predicate` from a pretty-printing and a function.
   */
  final def predicateDirect[A](render: String)(run: (=> A) => PredicateResult): Assertion[A] =
    new Assertion(render, run)

  /**
   * Makes a new `Predicate` from a pretty-printing and a function, passing
   * the predicate itself to the specified function, so it can embed a
   * recursive reference into the assert result.
   */
  final def predicateRec[A](render: String)(run: (Assertion[A], => A) => PredicateResult): Assertion[A] = {
    lazy val predicate: Assertion[A] = predicateDirect[A](render)(a => run(predicate, a))
    predicate
  }

  /**
   * Makes a new predicate that requires an exit value to succeed.
   */
  final def succeeds[A](predicate: Assertion[A]): Assertion[Exit[Any, A]] =
    Assertion.predicateRec[Exit[Any, A]](s"succeeds(${predicate})") { (self, actual) =>
      actual match {
        case Exit.Success(a) => predicate.run(a)

        case exit => AssertResult.value(Left(AssertionValue(self, exit)))
      }
    }

  /**
   * Returns a new predicate that requires the expression to throw.
   */
  final def throws[A](predicate: Assertion[Throwable]): Assertion[A] =
    Assertion.predicateRec[A](s"throws(${predicate})") { (self, actual) =>
      try {
        val _ = actual
      } catch {
        case t: Throwable => predicate(t)
      }

      AssertResult.value(Left(AssertionValue(self, actual)))
    }
}
