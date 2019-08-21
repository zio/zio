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
class Predicate[-A] private (render: String, val run: (=> A) => PredicateResult) extends ((=> A) => PredicateResult) {
  self =>
  import Assertion._

  /**
   * Returns a new predicate that succeeds only if both predicates succeed.
   */
  final def &&[A1 <: A](that: => Predicate[A1]): Predicate[A1] =
    Predicate.predicateDirect(s"(${self} && ${that})") { actual =>
      self.run(actual) match {
        case Failure(l) => Failure(l)
        case Success    => that.run(actual)
        case Ignore     => that.run(actual)
      }
    }

  /**
   * Returns a new predicate that succeeds if either predicates succeed.
   */
  final def ||[A1 <: A](that: => Predicate[A1]): Predicate[A1] =
    Predicate.predicateDirect(s"(${self} || ${that})") { actual =>
      self.run(actual) match {
        case Failure(_) => that.run(actual)
        case Success    => Success
        case Ignore     => that.run(actual)
      }
    }

  /**
   * Evaluates the predicate with the specified value.
   */
  final def apply(a: => A): PredicateResult = run(a)

  override final def equals(that: Any): Boolean = that match {
    case that: Predicate[_] => this.toString == that.toString
  }

  override final def hashCode: Int = toString.hashCode

  /**
   * Returns the negation of this predicate.
   */
  final def negate: Predicate[A] = Predicate.not(self)

  /**
   * Tests the predicate to see if it would succeed on the given element.
   */
  final def test(a: A): Boolean = run(a) match {
    case Success => true
    case _       => false
  }

  /**
   * Provides a meaningful string rendering of the predicate.
   */
  override final def toString: String = render
}

object Predicate {

  /**
   * Makes a new predicate that always succeeds.
   */
  final val anything: Predicate[Any] = Predicate.predicate[Any]("anything")(_ => Assertion.success)

  /**
   * Makes a new predicate that requires an iterable contain the specified
   * element.
   */
  final def contains[A](element: A): Predicate[Iterable[A]] =
    Predicate.predicate(s"contains(${element})") { actual =>
      if (!actual.exists(_ == element)) Assertion.failure(())
      else Assertion.success
    }

  /**
   * Makes a new predicate that requires a value equal the specified value.
   */
  final def equals[A](expected: A): Predicate[A] =
    Predicate.predicate(s"equals(${expected})") { actual =>
      if (actual == expected) Assertion.success
      else Assertion.failure(())
    }

  /**
   * Makes a new predicate that requires an iterable contain one element
   * satisfying the given predicate.
   */
  final def exists[A](predicate: Predicate[A]): Predicate[Iterable[A]] =
    Predicate.predicate(s"exists(${predicate})") { actual =>
      if (!actual.exists(predicate.test(_))) Assertion.failure(())
      else Assertion.success
    }

  /**
   * Makes a new predicate that requires an exit value to fail.
   */
  final def fails[E](predicate: Predicate[E]): Predicate[Exit[E, Any]] =
    Predicate.predicateRec[Exit[E, Any]](s"fails(${predicate})") { (self, actual) =>
      actual match {
        case Exit.Failure(cause) if cause.failures.length > 0 => predicate.run(cause.failures.head)

        case _ => Assertion.failure(PredicateValue(self, actual))
      }
    }

  /**
   * Makes a new predicate that requires an iterable contain only elements
   * satisfying the given predicate.
   */
  final def forall[A](predicate: Predicate[A]): Predicate[Iterable[A]] =
    Predicate.predicateDirect[Iterable[A]](s"forall(${predicate})") { actual =>
      actual.map(predicate(_)).toList match {
        case head :: tail =>
          tail.foldLeft(head) {
            case (Assertion.Success, next) => next
            case (acc, _)                  => acc
          }
        case Nil => Assertion.success
      }
    }

  /**
   * Makes a new predicate that focuses in on a field in a case class.
   *
   * {{{
   * hasField("age", _.age, within(0, 10))
   * }}}
   */
  final def hasField[A, B](name: String, proj: A => B, predicate: Predicate[B]): Predicate[A] =
    Predicate.predicateDirect[A]("hasField(\"" + name + "\"" + s", _.${name}, ${predicate})") { actual =>
      predicate(proj(actual))
    }

  /**
   * Makes a new predicate that requires the size of an iterable be satisfied
   * by the specified predicate.
   */
  final def hasSize[A](predicate: Predicate[Int]): Predicate[Iterable[A]] =
    Predicate.predicate[Iterable[A]](s"hasSize(${predicate})") { actual =>
      predicate.run(actual.size).map(_ => ())
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
    predicate: Predicate[Proj]
  ): Predicate[Sum] =
    Predicate.predicateRec[Sum]("isCase(\"" + termName + "\", " + s"${termName}.unapply, ${predicate})") {
      (self, actual) =>
        term(actual).fold(Assertion.failure(PredicateValue(self, actual)))(predicate(_))
    }

  /**
   * Makes a new predicate that requires a value be true.
   */
  final def isFalse: Predicate[Boolean] = Predicate.predicate(s"isFalse") { actual =>
    if (!actual) Assertion.success else Assertion.failure(())
  }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * the specified reference value.
   */
  final def isGreaterThan[A: Numeric](reference: A): Predicate[A] =
    Predicate.predicate(s"isGreaterThan(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(reference, actual) > 0) Assertion.success
      else Assertion.failure(())
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * or equal to the specified reference value.
   */
  final def isGreaterThanEqual[A: Numeric](reference: A): Predicate[A] =
    Predicate.predicate(s"isGreaterThanEqual(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(reference, actual) >= 0) Assertion.success
      else Assertion.failure(())
    }

  /**
   * Makes a new predicate that requires a Left value satisfying a specified
   * predicate.
   */
  final def isLeft[A](predicate: Predicate[A]): Predicate[Either[A, Any]] =
    Predicate.predicateRec[Either[A, Any]](s"isLeft(${predicate})") { (self, actual) =>
      actual match {
        case Left(a)  => predicate.run(a)
        case Right(_) => Assertion.failure(PredicateValue(self, actual))
      }
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * the specified reference value.
   */
  final def isLessThan[A: Numeric](reference: A): Predicate[A] =
    Predicate.predicate(s"isLessThan(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(reference, actual) < 0) Assertion.success
      else Assertion.failure(())
    }

  /**
   * Makes a new predicate that requires the numeric value be greater than
   * the specified reference value.
   */
  final def isLessThanEqual[A: Numeric](reference: A): Predicate[A] =
    Predicate.predicate(s"isLessThanEqual(${reference})") { actual =>
      if (implicitly[Numeric[A]].compare(reference, actual) <= 0) Assertion.success
      else Assertion.failure(())
    }

  /**
   * Makes a new predicate that requires a Some value satisfying the specified
   * predicate.
   */
  final val isNone: Predicate[Option[Any]] = Predicate.predicate(s"isNone") { actual =>
    actual match {
      case None    => Assertion.success
      case Some(_) => Assertion.failure(())
    }
  }

  /**
   * Makes a new predicate that requires a Right value satisfying a specified
   * predicate.
   */
  final def isRight[A](predicate: Predicate[A]): Predicate[Either[Any, A]] =
    Predicate.predicateRec[Either[Any, A]](s"isRight(${predicate})") { (self, actual) =>
      actual match {
        case Right(a) => predicate.run(a)
        case Left(_)  => Assertion.failure(PredicateValue(self, actual))
      }
    }

  /**
   * Makes a new predicate that requires a Some value satisfying the specified
   * predicate.
   */
  final def isSome[A](predicate: Predicate[A]): Predicate[Option[A]] =
    Predicate.predicateRec[Option[A]](s"isSome(${predicate})") { (self, actual) =>
      actual match {
        case Some(a) => predicate.run(a)
        case None    => Assertion.failure(PredicateValue(self, actual))
      }
    }

  /**
   * Makes a predicate that requires a value have the specified type.
   */
  final def isSubtype[A](predicate: Predicate[A])(implicit C: ClassTag[A]): Predicate[Any] =
    Predicate.predicateRec[Any](s"isSubtype[${C.runtimeClass.getSimpleName()}]") { (self, actual) =>
      if (C.runtimeClass.isAssignableFrom(actual.getClass())) predicate(actual.asInstanceOf[A])
      else Assertion.failure(PredicateValue(self, actual))
    }

  /**
   * Makes a new predicate that requires a value be true.
   */
  final def isTrue: Predicate[Boolean] = Predicate.predicate(s"isTrue") { actual =>
    if (actual) Assertion.success else Assertion.failure(())
  }

  /**
   * Makes a new predicate that requires the value be unit.
   */
  final def isUnit: Predicate[Any] =
    Predicate.predicate("isUnit") { actual =>
      actual match {
        case () => Assertion.success
        case _  => Assertion.failure(())
      }
    }

  /**
   * Returns a new predicate that requires a numeric value to fall within a
   * specified min and max (inclusive).
   */
  final def isWithin[A: Numeric](min: A, max: A): Predicate[A] =
    Predicate.predicate(s"isWithin(${min}, ${max})") { actual =>
      if (implicitly[Numeric[A]].compare(actual, min) < 0) Assertion.failure(())
      else if (implicitly[Numeric[A]].compare(actual, max) > 0) Assertion.failure(())
      else Assertion.success
    }

  /**
   * Makes a new predicate that negates the specified predicate.
   */
  final def not[A](predicate: Predicate[A]): Predicate[A] =
    Predicate.predicateRec[A](s"not(${predicate})") { (self, actual) =>
      predicate.run(actual) match {
        case Assertion.Success    => Assertion.Failure(PredicateValue(self, actual))
        case Assertion.Failure(_) => Assertion.Success
        case Assertion.Ignore     => Assertion.Ignore
      }
    }

  /**
   * Makes a new predicate that always fails.
   */
  final val nothing: Predicate[Any] = Predicate.predicateRec[Any]("nothing") { (self, actual) =>
    Assertion.failure(PredicateValue(self, actual))
  }

  /**
   * Makes a new `Predicate` from a pretty-printing and a function.
   */
  final def predicate[A](render: String)(run: (=> A) => Assertion[Unit]): Predicate[A] =
    predicateRec[A](render)((predicate, a) => run(a).map(_ => PredicateValue(predicate, a)))

  /**
   * Makes a new `Predicate` from a pretty-printing and a function.
   */
  final def predicateDirect[A](render: String)(run: (=> A) => PredicateResult): Predicate[A] =
    new Predicate(render, run)

  /**
   * Makes a new `Predicate` from a pretty-printing and a function, passing
   * the predicate itself to the specified function, so it can embed a
   * recursive reference into the assert result.
   */
  final def predicateRec[A](render: String)(run: (Predicate[A], => A) => PredicateResult): Predicate[A] = {
    lazy val predicate: Predicate[A] = predicateDirect[A](render)(a => run(predicate, a))

    predicate
  }

  /**
   * Makes a new predicate that requires an exit value to succeed.
   */
  final def succeeds[A](predicate: Predicate[A]): Predicate[Exit[Any, A]] =
    Predicate.predicateRec[Exit[Any, A]](s"succeeds(${predicate})") { (self, actual) =>
      actual match {
        case Exit.Success(a) => predicate.run(a)

        case exit => Assertion.failure(PredicateValue(self, exit))
      }
    }

  /**
   * Returns a new predicate that requires the expression to throw.
   */
  final def throws[A](predicate: Predicate[Throwable]): Predicate[A] =
    Predicate.predicateRec[A](s"throws(${predicate})") { (self, actual) =>
      try {
        val _ = actual
      } catch {
        case t: Throwable => predicate(t)
      }

      Assertion.failure(PredicateValue(self, actual))
    }
}
