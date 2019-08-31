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

/**
 * An `AssertResult[A]` is the result of running an assertion on a value.
 * Assert results compose using logical `&&` and `||` and all information will
 * be preserved to provide robust reporting of test results.
 */
sealed trait AssertResult[+A] extends Product with Serializable { self =>
  import AssertResult._

  /**
   * Returns a new assert result that is the logical conjunction of this assert
   * result and the specified assert result.
   */
  final def &&[A1 >: A](that: AssertResult[A1]): AssertResult[A1] =
    both(that)

  /**
   * Returns a new assert result that is the logical disjunction of this assert
   * result and the specified assert result.
   */
  final def ||[A1 >: A](that: AssertResult[A1]): AssertResult[A1] =
    either(that)

  /**
   * Returns a new assert result, with all values mapped to the specified
   * constant.
   */
  final def as[B](b: B): AssertResult[B] =
    map(_ => b)

  /**
   * A named alias for `&&`.
   */
  final def both[A1 >: A](that: AssertResult[A1]): AssertResult[A1] =
    and(self, that)

  /**
   * Returns a new assert result with a filtered, mapped subset of the values
   * in this assert result.
   */
  final def collect[B](p: PartialFunction[A, B]): Option[AssertResult[B]] =
    fold(a => p.lift(a).map(value))(
      {
        case (Some(l), Some(r)) => Some(l && r)
        case (Some(l), None)    => Some(l)
        case (None, Some(r))    => Some(r)
        case (None, None)       => None
      }, {
        case (Some(l), Some(r)) => Some(l || r)
        case _                  => None
      }
    )

  /**
   * A named alias for `||`.
   */
  final def either[A1 >: A](that: AssertResult[A1]): AssertResult[A1] =
    or(self, that)

  /**
   * If this assert value is a success returns `None`. If it is a failure
   * returns a new assert value containing all failures that are relevant to
   * this assert value being a failure.
   */
  final def failures[B](implicit ev: A <:< Either[B, _]): Option[AssertResult[B]] =
    collect(a => ev(a) match { case Left(b) => b })

  /**
   * Folds over the assert result bottom up, first converting values to `B`
   * values, and then combining the `B` values, using the specified functions.
   */
  final def fold[B](caseValue: A => B)(caseAnd: (B, B) => B, caseOr: (B, B) => B): B =
    self match {
      case Value(v) =>
        caseValue(v)
      case And(l, r) =>
        caseAnd(
          l.fold(caseValue)(caseAnd, caseOr),
          r.fold(caseValue)(caseAnd, caseOr)
        )
      case Or(l, r) =>
        caseOr(
          l.fold(caseValue)(caseAnd, caseOr),
          r.fold(caseValue)(caseAnd, caseOr)
        )
    }

  override final def hashCode: Int =
    fold(_.hashCode)(_ & _, _ | _)

  /**
   * Returns a new assert result, with all values mapped by the specified
   * function.
   */
  final def map[B](f: A => B): AssertResult[B] =
    fold(f andThen value)(and, or)

  /**
   * Determines whether the assert result is a failure, where `Left` represents
   * failure, `Right` represents success, and values are combined using logical
   * conjunction and disjunction.
   */
  final def isFailure(implicit ev: A <:< Either[_, _]): Boolean =
    !isSuccess

  /**
   * Determines whether the assert result is a success, where `Left` represents
   * failure, `Right` represents success, and values are combined using logical
   * conjunction and disjunction.
   */
  final def isSuccess(implicit ev: A <:< Either[_, _]): Boolean =
    fold(a => ev(a).isRight)(_ && _, _ || _)

  /**
   * Negates this assert result, converting all successes into failures and
   * failures into successes.
   */
  final def not[B, C](implicit ev: A <:< Either[B, C]): AssertResult[Either[C, B]] =
    fold(a => value(ev(a).swap))(_ || _, _ && _)
}

object AssertResult {

  final case class Value[+A](value: A) extends AssertResult[A]

  final case class And[+A](left: AssertResult[A], right: AssertResult[A]) extends AssertResult[A] { self =>
    override final def equals(that: Any): Boolean = that match {
      case other: AssertResult[_] =>
        equal(other) ||
          commutative(other) ||
          symmetric(associative)(self, other) ||
          symmetric(distributive)(self, other)
      case _ => false
    }
    private def equal(that: AssertResult[_]): Boolean = (self, that) match {
      case (a1: And[_], a2: And[_]) => a1.left == a2.left && a1.right == a2.right
      case _                        => false
    }
    private def associative(left: AssertResult[_], right: AssertResult[_]): Boolean =
      (left, right) match {
        case (And(And(a1, b1), c1), And(a2, And(b2, c2))) =>
          a1 == a2 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def commutative(that: AssertResult[_]): Boolean = (self, that) match {
      case (And(al, bl), And(ar, br)) => al == br && bl == ar
      case _                          => false
    }
    private def distributive(left: AssertResult[_], right: AssertResult[_]): Boolean =
      (left, right) match {
        case (And(a1, Or(b1, c1)), Or(And(a2, b2), And(a3, c2))) =>
          a1 == a2 && a1 == a3 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
  }

  final case class Or[+A](left: AssertResult[A], right: AssertResult[A]) extends AssertResult[A] { self =>
    override final def equals(that: Any): Boolean = that match {
      case other: AssertResult[_] =>
        equal(other) ||
          commutative(other) ||
          symmetric(associative)(self, other) ||
          symmetric(distributive)(self, other)
      case _ => false
    }
    private def equal(that: AssertResult[_]): Boolean = (self, that) match {
      case (o1: Or[_], o2: Or[_]) => o1.left == o2.left && o1.right == o2.right
      case _                      => false
    }
    private def associative(left: AssertResult[_], right: AssertResult[_]): Boolean =
      (left, right) match {
        case (Or(Or(a1, b1), c1), Or(a2, Or(b2, c2))) =>
          a1 == a2 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def commutative(that: AssertResult[_]): Boolean = (self, that) match {
      case (Or(al, bl), Or(ar, br)) => al == br && bl == ar
      case _                        => false
    }
    private def distributive(left: AssertResult[_], right: AssertResult[_]): Boolean =
      (left, right) match {
        case (Or(a1, And(b1, c1)), And(Or(a2, b2), Or(a3, c2))) =>
          a1 == a2 && a1 == a3 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
  }

  /**
   * Returns an assert result that is the logical conjunction of all of the
   * assert results in the specified collection.
   */
  final def all[A](as: Iterable[AssertResult[A]]): Option[AssertResult[A]] =
    if (as.isEmpty) None else Some(as.reduce(_ && _))

  /**
   * Constructs an assert result that is the logical conjunction of two assert
   * results.
   */
  final def and[A](left: AssertResult[A], right: AssertResult[A]): AssertResult[A] =
    And(left, right)

  /**
   * Returns an assert result that is the logical disjunction of all of the
   * assert results in the specified collection succeed.
   */
  final def any[A](as: Iterable[AssertResult[A]]): Option[AssertResult[A]] =
    if (as.isEmpty) None else Some(as.reduce(_ || _))

  /**
   * Combines a collection of assert results to create a single assert result
   * that succeeds if all of the assert results succeed.
   */
  final def collectAll[A](as: Iterable[AssertResult[A]]): Option[AssertResult[A]] =
    foreach(as)(identity)

  /**
   * Constructs a failed assert result with the specified value.
   */
  final def failure[A](a: A): AssertResult[Either[A, Nothing]] =
    value(Left(a))

  /**
   * Applies the function `f` to each element of the `Iterable[A]` to produce
   * a collection of assert results, then combines all of those assert results
   * to create a single assert result that is the logical conjunction of all of
   * the assert results.
   */
  final def foreach[A, B](as: Iterable[A])(f: A => AssertResult[B]): Option[AssertResult[B]] =
    if (as.isEmpty) None else Some(as.map(f).reduce(_ && _))

  /**
   * Constructs an assert result that is the logical disjunction of two assert
   * results.
   */
  final def or[A](left: AssertResult[A], right: AssertResult[A]): AssertResult[A] =
    Or(left, right)

  /**
   * Constructs a successful assert result with the specified value.
   */
  final def success[A](a: A): AssertResult[Either[Nothing, A]] =
    value(Right(a))

  /**
   * An assert result with the unit value.
   */
  final val unit: AssertResult[Unit] =
    value(())

  /**
   * Constructs an assert result with the specified value.
   */
  final def value[A](a: A): AssertResult[A] =
    Value(a)

  private def symmetric[A](f: (A, A) => Boolean): (A, A) => Boolean =
    (a1, a2) => f(a1, a2) || f(a2, a1)
}
