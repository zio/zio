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

import zio.ZIO

/**
 * A `BoolAlgebra[A]` is a description of logical operations on values of type
 * `A`.
 */
sealed abstract class BoolAlgebra[+A] extends Product with Serializable { self =>
  import BoolAlgebra._

  /**
   * Returns a new result that is the logical conjunction of this result and
   * the specified result.
   */
  final def &&[A1 >: A](that: BoolAlgebra[A1]): BoolAlgebra[A1] =
    both(that)

  /**
   * Returns a new result that is the logical disjunction of this result and
   * the specified result.
   */
  final def ||[A1 >: A](that: BoolAlgebra[A1]): BoolAlgebra[A1] =
    either(that)

  /**
   * Returns a new result that is the logical implication of this result and
   * the specified result.
   */
  final def ==>[A1 >: A](that: BoolAlgebra[A1]): BoolAlgebra[A1] =
    implies(that)

  /**
   * Returns a new result that is the logical double implication of this result and
   * the specified result.
   */
  final def <==>[A1 >: A](that: BoolAlgebra[A1]): BoolAlgebra[A1] =
    iff(that)

  /**
   * Returns a new result that is the logical negation of this result.
   */
  final def unary_! : BoolAlgebra[A] =
    negate

  /**
   * Returns a new result, with all values mapped to the specified constant.
   */
  final def as[B](b: B): BoolAlgebra[B] =
    map(_ => b)

  /**
   * A named alias for `&&`.
   */
  final def both[A1 >: A](that: BoolAlgebra[A1]): BoolAlgebra[A1] =
    and(self, that)

  /**
   * A named alias for `||`.
   */
  final def either[A1 >: A](that: BoolAlgebra[A1]): BoolAlgebra[A1] =
    or(self, that)

  /**
   * If this result is a success returns `None`. If it is a failure returns a
   * new result containing all failures that are relevant to this result being
   * a failure.
   */
  final def failures: Option[BoolAlgebra[A]] =
    fold[Either[BoolAlgebra[A], BoolAlgebra[A]]](a => Right(success(a)))(
      {
        case (Right(l), Right(r)) => Right(l && r)
        case (Left(l), Right(_))  => Left(l)
        case (Right(_), Left(r))  => Left(r)
        case (Left(l), Left(r))   => Left(l && r)
      }, {
        case (Right(l), Right(r)) => Right(l || r)
        case (Left(_), Right(r))  => Right(r)
        case (Right(l), Left(_))  => Right(l)
        case (Left(l), Left(r))   => Left(l || r)
      },
      _.swap
    ).fold(Some(_), _ => None)

  /**
   * Returns a new result, with all values mapped to new results using the
   * specified function.
   */
  final def flatMap[B](f: A => BoolAlgebra[B]): BoolAlgebra[B] =
    fold(f)(and, or, not)

  /**
   * Returns a new result, with all values mapped to new results using the
   * specified effectual function.
   */
  final def flatMapM[R, E, B](f: A => ZIO[R, E, BoolAlgebra[B]]): ZIO[R, E, BoolAlgebra[B]] =
    fold(a => f(a))(_.zipWith(_)(_ && _), _.zipWith(_)(_ || _), _.map(!_))

  /**
   * Folds over the result bottom up, first converting values to `B`
   * values, and then combining the `B` values, using the specified functions.
   */
  final def fold[B](caseValue: A => B)(caseAnd: (B, B) => B, caseOr: (B, B) => B, caseNot: B => B): B =
    self match {
      case Value(value) =>
        caseValue(value)
      case And(left, right) =>
        caseAnd(
          left.fold(caseValue)(caseAnd, caseOr, caseNot),
          right.fold(caseValue)(caseAnd, caseOr, caseNot)
        )
      case Or(left, right) =>
        caseOr(
          left.fold(caseValue)(caseAnd, caseOr, caseNot),
          right.fold(caseValue)(caseAnd, caseOr, caseNot)
        )
      case Not(algebra) =>
        caseNot(algebra.fold(caseValue)(caseAnd, caseOr, caseNot))
    }

  override final def hashCode: Int =
    fold(_.hashCode)(_ & _, _ | _, ~_)

  /**
   * A named alias for "==>".
   */
  final def implies[A1 >: A](that: BoolAlgebra[A1]): BoolAlgebra[A1] =
    !self || that

  /**
   * A named alias for "<==>".
   */
  final def iff[A1 >: A](that: BoolAlgebra[A1]): BoolAlgebra[A1] =
    (self ==> that) && (that ==> self)

  /**
   * Determines whether the result is a failure, where values represent success
   * and are combined using logical conjunction, disjunction, and negation.
   */
  final def isFailure: Boolean =
    !isSuccess

  /**
   * Determines whether the result is a success, where values represent success
   * and are combined using logical conjunction, disjunction, and negation.
   */
  final def isSuccess: Boolean =
    fold(_ => true)(_ && _, _ || _, !_)

  /**
   * Returns a new result, with all values mapped by the specified function.
   */
  final def map[B](f: A => B): BoolAlgebra[B] =
    flatMap(f andThen success)

  /**
   * Returns a new result, with all values mapped by the specified effectual
   * function.
   */
  final def mapM[R, E, B](f: A => ZIO[R, E, B]): ZIO[R, E, BoolAlgebra[B]] =
    flatMapM(a => f(a).map(success))

  /**
   * Negates this result, converting all successes into failures and failures
   * into successes.
   */
  final def negate: BoolAlgebra[A] =
    not(self)
}

object BoolAlgebra {

  final case class Value[+A](value: A) extends BoolAlgebra[A] { self =>
    override def equals(that: Any): Boolean = that match {
      case other: BoolAlgebra[Any] =>
        equal(other) ||
          doubleNegative(self, other)
      case _ => false
    }
    private def equal(that: BoolAlgebra[Any]): Boolean = (self, that) match {
      case (a1: Value[Any], a2: Value[Any]) => a1.value == a2.value
      case _                                => false
    }
  }

  final case class And[+A](left: BoolAlgebra[A], right: BoolAlgebra[A]) extends BoolAlgebra[A] { self =>
    override def equals(that: Any): Boolean = that match {
      case other: BoolAlgebra[Any] =>
        equal(other) ||
          commutative(other) ||
          symmetric(associative)(self, other) ||
          symmetric(distributive)(self, other) ||
          doubleNegative(self, other) ||
          deMorgansLaws(other)
      case _ => false
    }
    private def equal(that: BoolAlgebra[Any]): Boolean = (self, that) match {
      case (a1: And[Any], a2: And[Any]) => a1.left == a2.left && a1.right == a2.right
      case _                            => false
    }
    private def associative(left: BoolAlgebra[Any], right: BoolAlgebra[Any]): Boolean =
      (left, right) match {
        case (And(And(a1, b1), c1), And(a2, And(b2, c2))) =>
          a1 == a2 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def commutative(that: BoolAlgebra[Any]): Boolean = (self, that) match {
      case (And(al, bl), And(ar, br)) => al == br && bl == ar
      case _                          => false
    }
    private def distributive(left: BoolAlgebra[Any], right: BoolAlgebra[Any]): Boolean =
      (left, right) match {
        case (And(a1, Or(b1, c1)), Or(And(a2, b2), And(a3, c2))) =>
          a1 == a2 && a1 == a3 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def deMorgansLaws(that: BoolAlgebra[Any]): Boolean =
      (self, that) match {
        case (And(Not(a), Not(b)), Not(Or(c, d))) => a == c && b == d
        case _                                    => false
      }
  }

  final case class Or[+A](left: BoolAlgebra[A], right: BoolAlgebra[A]) extends BoolAlgebra[A] { self =>
    override def equals(that: Any): Boolean = that match {
      case other: BoolAlgebra[Any] =>
        equal(other) ||
          commutative(other) ||
          symmetric(associative)(self, other) ||
          symmetric(distributive)(self, other) ||
          doubleNegative(self, other) ||
          deMorgansLaws(other)
      case _ => false
    }
    private def equal(that: BoolAlgebra[Any]): Boolean = (self, that) match {
      case (o1: Or[Any], o2: Or[Any]) => o1.left == o2.left && o1.right == o2.right
      case _                          => false
    }
    private def associative(left: BoolAlgebra[Any], right: BoolAlgebra[Any]): Boolean =
      (left, right) match {
        case (Or(Or(a1, b1), c1), Or(a2, Or(b2, c2))) =>
          a1 == a2 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def commutative(that: BoolAlgebra[Any]): Boolean = (self, that) match {
      case (Or(al, bl), Or(ar, br)) => al == br && bl == ar
      case _                        => false
    }
    private def distributive(left: BoolAlgebra[Any], right: BoolAlgebra[Any]): Boolean =
      (left, right) match {
        case (Or(a1, And(b1, c1)), And(Or(a2, b2), Or(a3, c2))) =>
          a1 == a2 && a1 == a3 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def deMorgansLaws(that: BoolAlgebra[Any]): Boolean = (self, that) match {
      case (Or(Not(a), Not(b)), Not(And(c, d))) => a == c && b == d
      case _                                    => false
    }
  }

  final case class Not[+A](result: BoolAlgebra[A]) extends BoolAlgebra[A] { self =>
    override def equals(that: Any): Boolean = that match {
      case other: BoolAlgebra[Any] =>
        equal(other) ||
          doubleNegative(other, self) ||
          deMorgansLaws(other)
      case _ =>
        false
    }
    private def equal(that: BoolAlgebra[Any]): Boolean = (self, that) match {
      case (n1: Not[Any], n2: Not[Any]) => n1.result == n2.result
      case _                            => false
    }
    private def deMorgansLaws(that: BoolAlgebra[Any]): Boolean =
      (self, that) match {
        case (Not(Or(a, b)), And(Not(c), Not(d))) => a == c && b == d
        case (Not(And(a, b)), Or(Not(c), Not(d))) => a == c && b == d
        case _                                    => false
      }
  }

  /**
   * Returns a result that is the logical conjunction of all of the results in
   * the specified collection.
   */
  def all[A](as: Iterable[BoolAlgebra[A]]): Option[BoolAlgebra[A]] =
    if (as.isEmpty) None else Some(as.reduce(_ && _))

  /**
   * Returns a result that is the logical conjunction of all of the results
   */
  def all[A](a: BoolAlgebra[A], as: BoolAlgebra[A]*): BoolAlgebra[A] =
    as.foldLeft(a)(_ && _)

  /**
   * Constructs a result that is the logical conjunction of two results.
   */
  def and[A](left: BoolAlgebra[A], right: BoolAlgebra[A]): BoolAlgebra[A] =
    And(left, right)

  /**
   * Returns a result that is the logical disjunction of all of the results in
   * the specified collection.
   */
  def any[A](as: Iterable[BoolAlgebra[A]]): Option[BoolAlgebra[A]] =
    if (as.isEmpty) None else Some(as.reduce(_ || _))

  /**
   * Returns a result that is the logical disjunction of all of the results
   */
  def any[A](a: BoolAlgebra[A], as: BoolAlgebra[A]*): BoolAlgebra[A] =
    as.foldLeft(a)(_ || _)

  /**
   * Combines a collection of results to create a single result that succeeds
   * if all of the results succeed.
   */
  def collectAll[A](as: Iterable[BoolAlgebra[A]]): Option[BoolAlgebra[A]] =
    foreach(as)(identity)

  /**
   * Constructs a failed result with the specified value.
   */
  def failure[A](a: A): BoolAlgebra[A] =
    not(success(a))

  /**
   * Applies the function `f` to each element of the `Iterable[A]` to produce
   * a collection of results, then combines all of those results to create a
   * single result that is the logical conjunction of all of the results.
   */
  def foreach[A, B](as: Iterable[A])(f: A => BoolAlgebra[B]): Option[BoolAlgebra[B]] =
    if (as.isEmpty) None else Some(as.map(f).reduce(_ && _))

  /**
   * Constructs a result that is the logical negation of the specified result.
   */
  def not[A](result: BoolAlgebra[A]): BoolAlgebra[A] =
    Not(result)

  /**
   * Constructs a result a that is the logical disjunction of two results.
   */
  def or[A](left: BoolAlgebra[A], right: BoolAlgebra[A]): BoolAlgebra[A] =
    Or(left, right)

  /**
   * Constructs a successful result with the specified value.
   */
  def success[A](a: A): BoolAlgebra[A] =
    Value(a)

  /**
   * A successful result with the unit value.
   */
  final val unit: BoolAlgebra[Unit] =
    success(())

  private def doubleNegative[A](left: BoolAlgebra[A], right: BoolAlgebra[A]): Boolean =
    (left, right) match {
      case (a, Not(Not(b))) => a == b
      case _                => false
    }

  private def symmetric[A](f: (A, A) => Boolean): (A, A) => Boolean =
    (a1, a2) => f(a1, a2) || f(a2, a1)
}
