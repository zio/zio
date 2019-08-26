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
 * An `Assertion[A]` is the result of running a test, which may be ignore,
 * success, or failure, with some message of type `A`. Assertions compose using
 * logical `&&` and `||` and all failures will be preserved to provide robust
 * reporting test results.
 */
sealed trait AssertResult[+A] extends Product with Serializable { self =>
  import AssertResult._

  /**
   * Returns a new assertion that is the logical conjunction of this assertion
   * and the specified assertion.
   */
  final def &&[A1 >: A](that: AssertResult[A1]): AssertResult[A1] =
    both(that)

  /**
   * Returns a new assertion that is the logical disjunction of this assertion
   * and the specified assertion.
   */
  final def ||[A1 >: A](that: AssertResult[A1]): AssertResult[A1] =
    either(that)

  /**
   * Returns a new result, with all failure messages mapped to the specified
   * constant.
   */
  final def as[B](b: B): AssertResult[B] =
    map(_ => b)

  /**
   * A named alias for `&&`.
   */
  final def both[A1 >: A](that: AssertResult[A1]): AssertResult[A1] =
    and(self, that)

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
   * Folds over the assertion bottom up, first converting ignore, success, or
   * failure results to `B` values, and then combining the `B` values, using
   * the specified functions.
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

  /**
   * Returns a new result, with all failure messages mapped by the specified
   * function.
   */
  final def map[B](f: A => B): AssertResult[B] =
    fold(f andThen value)(and, or)

  final def isFailure(implicit ev: A <:< Either[_, _]): Boolean =
    !isSuccess

  final def isSuccess(implicit ev: A <:< Either[_, _]): Boolean =
    fold(a => ev(a).isRight)(_ && _, _ || _)
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
    override final def hashCode: Int =
      assertionHash(self)
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
    override final def hashCode: Int =
      assertionHash(self)
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
   * Returns an assertion that succeeds if all of the assertions in the
   * specified collection succeed.
   */
  final def all[A](as: Iterable[AssertResult[A]]): AssertResult[A] =
    ??? //as.foldRight[Assertion[A]](ignore)(and)

  /**
   * Constructs an assertion that is the logical conjunction of two assertions.
   */
  final def and[A](left: AssertResult[A], right: AssertResult[A]): AssertResult[A] =
    And(left, right)

  /**
   * Returns an assertion that succeeds if any of the assertions in the
   * specified collection succeed.
   */
  final def any[A](as: Iterable[AssertResult[A]]): AssertResult[A] =
    ??? //as.foldRight[Assertion[A]](ignore)(or)

  /**
   * Combines a collection of assertions to create a single assertion that
   * succeeds if all of the assertions succeed.
   */
  final def collectAll[A](as: Iterable[AssertResult[A]]): AssertResult[A] =
    foreach(as)(identity)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` to produce
   * a collection of assertions, then combines all of those assertions to
   * create a single assertion that succeeds if all of the assertions succeed.
   */
  final def foreach[A, B](as: Iterable[A])(f: A => AssertResult[B]): AssertResult[B] =
    ??? //as.foldRight[Assertion[B]](ignore)((a, b) => and(f(a), b))

  /**
   * Constructs an assertion that is the logical disjunction of two assertions.
   */
  final def or[A](left: AssertResult[A], right: AssertResult[A]): AssertResult[A] =
    Or(left, right)

  /**
   * Returns a successful assertion.
   */
  final def value[A](a: A): AssertResult[A] =
    Value(a)

  private def symmetric[A](f: (A, A) => Boolean): (A, A) => Boolean =
    (a1, a2) => f(a1, a2) || f(a2, a1)

  private def assertionHash[A](assertion: AssertResult[A]): Int =
    ???
  // assertion
  //   .fold(s => Some(success(s).hashCode), e => Some(failure(e).hashCode))(
  //     {
  //       case (Some(l), Some(r)) => Some(l & r)
  //       case (Some(l), _)       => Some(l)
  //       case (_, Some(r))       => Some(r)
  //       case _                  => None
  //     }, {
  //       case (Some(l), Some(r)) => Some(l | r)
  //       case (Some(l), _)       => Some(l)
  //       case (_, Some(r))       => Some(r)
  //       case _                  => None
  //     }
  //   )
  //   .getOrElse(ignore.hashCode)
}
