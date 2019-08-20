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
sealed trait Assertion[+A] extends Product with Serializable { self =>
  import Assertion._

  /**
   * Returns a new assertion that is the logical conjunction of this assertion
   * and the specified assertion.
   */
  final def &&[A1 >: A](that: Assertion[A1]): Assertion[A1] =
    both(that)

  /**
   * Returns a new assertion that is the logical disjunction of this assertion
   * and the specified assertion.
   */
  final def ||[A1 >: A](that: Assertion[A1]): Assertion[A1] =
    either(that)

  /**
   * Returns a new result, with all failure messages mapped to the specified
   * constant.
   */
  final def as[B](b: B): Assertion[B] =
    self.map(_ => b)

  /**
   * A named alias for `&&`.
   */
  final def both[A1 >: A](that: Assertion[A1]): Assertion[A1] =
    and(self, that)

  /**
   * A named alias for `||`.
   */
  final def either[A1 >: A](that: Assertion[A1]): Assertion[A1] =
    or(self, that)

  /**
   * Collects all failure messages into a list.
   */
  final def failures: List[A] =
    fold(Vector.empty, Vector.empty, a => Vector(a))(_ ++ _, _ ++ _).toList

  /**
   * Folds over the assertion bottom up, first converting ignore, success, or
   * failure results to `B` values, and then combining the `B` values, using
   * the specified functions.
   */
  final def fold[B](caseSkipped: => B, caseSucceeded: => B, caseFailed: A => B)(
    caseAnd: (B, B) => B,
    caseOr: (B, B) => B
  ): B = self match {
    case Ignore =>
      caseSkipped
    case Success =>
      caseSucceeded
    case Failure(a) =>
      caseFailed(a)
    case And(l, r) =>
      caseAnd(
        l.fold(caseSkipped, caseSucceeded, caseFailed)(caseAnd, caseOr),
        r.fold(caseSkipped, caseSucceeded, caseFailed)(caseAnd, caseOr)
      )
    case Or(l, r) =>
      caseOr(
        l.fold(caseSkipped, caseSucceeded, caseFailed)(caseAnd, caseOr),
        r.fold(caseSkipped, caseSucceeded, caseFailed)(caseAnd, caseOr)
      )
  }

  /**
   * Detemines if the result failed.
   */
  final def isFailure: Boolean =
    fold(false, false, _ => true)(_ || _, _ && _)

  /**
   * Detemines if the result succeeded.
   */
  final def isSuccess: Boolean =
    fold(false, true, _ => false)(_ && _, _ || _)

  /**
   * Returns a new result, with all failure messages mapped by the specified
   * function.
   */
  final def map[A1](f: A => A1): Assertion[A1] =
    fold(ignore, success, a => failure(f(a)))(and, or)

  /**
   * Negates this assertion, converting all successes into failures and failures
   * into successes. The new failures will have messages with the specified
   * value.
   */
  final def not[B](b: B): Assertion[B] =
    fold(ignore, failure(b), _ => success)(
      (l, r) => or(l.not(b), r.not(b)),
      (l, r) => and(l.not(b), r.not(b))
    )

  /**
   * Removes all intermediate ignore results from this assertion, returning
   * either an assertion with no ignore results or ignore if all the results
   * of this assertion were ignore.
   */
  final def stripIgnores: Assertion[A] =
    self.fold(ignore, success, failure)(
      {
        case (Ignore, r) => r
        case (l, Ignore) => l
        case (l, r)      => And(l, r)
      }, {
        case (Ignore, r) => r
        case (l, Ignore) => l
        case (l, r)      => Or(l, r)
      }
    )
}

object Assertion {

  case object Ignore extends Assertion[Nothing] { self =>
    override final def equals(that: Any): Boolean =
      (this eq that.asInstanceOf[AnyRef]) || (that match {
        case other: Assertion[_] => equal(other)
        case _                   => false
      })
    private def equal(other: Assertion[_]): Boolean = other match {
      case And(Ignore, r) => self == r
      case And(l, Ignore) => self == l
      case Or(Ignore, r)  => self == r
      case Or(l, Ignore)  => self == l
      case _              => false
    }
  }

  case object Success extends Assertion[Nothing] { self =>
    override final def equals(that: Any): Boolean =
      (this eq that.asInstanceOf[AnyRef]) || (that match {
        case other: Assertion[_] => equal(other)
        case _                   => false
      })
    private def equal(other: Assertion[_]): Boolean = other match {
      case And(Ignore, r) => self == r
      case And(l, Ignore) => self == l
      case Or(Ignore, r)  => self == r
      case Or(l, Ignore)  => self == l
      case _              => false

    }
  }

  final case class Failure[+A](message: A) extends Assertion[A] { self =>
    override final def equals(that: Any): Boolean =
      (this eq that.asInstanceOf[AnyRef]) || (that match {
        case other: Assertion[_] => equal(other)
        case _                   => false
      })
    private def equal(other: Assertion[_]): Boolean = other match {
      case And(Ignore, r) => self == r
      case And(l, Ignore) => self == l
      case Or(Ignore, r)  => self == r
      case Or(l, Ignore)  => self == l
      case Failure(v)     => message == v
      case _              => false
    }
  }

  final case class And[+A](left: Assertion[A], right: Assertion[A]) extends Assertion[A] { self =>
    override final def equals(that: Any): Boolean = that match {
      case other: Assertion[_] =>
        equal(other) ||
          commutative(other) ||
          empty(self, other) ||
          symmetric(associative)(self, other) ||
          symmetric(distributive)(self, other)
      case _ => false
    }
    override final def hashCode: Int =
      assertionHash(self)
    private def equal(that: Assertion[_]): Boolean = (self, that) match {
      case (a1: And[_], a2: And[_]) => a1.left == a2.left && a1.right == a2.right
      case _                        => false
    }
    private def associative(left: Assertion[_], right: Assertion[_]): Boolean =
      (left, right) match {
        case (And(And(a1, b1), c1), And(a2, And(b2, c2))) =>
          a1 == a2 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def commutative(that: Assertion[_]): Boolean = (self, that) match {
      case (And(al, bl), And(ar, br)) => al == br && bl == ar
      case _                          => false
    }
    private def distributive(left: Assertion[_], right: Assertion[_]): Boolean =
      (left, right) match {
        case (And(a1, Or(b1, c1)), Or(And(a2, b2), And(a3, c2))) =>
          a1 == a2 && a1 == a3 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def empty(left: Assertion[_], right: Assertion[_]): Boolean =
      (left, right) match {
        case (And(l, Ignore), v) => l == v
        case (And(Ignore, r), v) => r == v
        case _                   => false
      }
  }

  final case class Or[+A](left: Assertion[A], right: Assertion[A]) extends Assertion[A] { self =>
    override final def equals(that: Any): Boolean = that match {
      case other: Assertion[_] =>
        equal(other) ||
          commutative(other) ||
          empty(self, other) ||
          symmetric(associative)(self, other) ||
          symmetric(distributive)(self, other)
      case _ => false
    }
    override final def hashCode: Int =
      assertionHash(self)
    private def equal(that: Assertion[_]): Boolean = (self, that) match {
      case (o1: Or[_], o2: Or[_]) => o1.left == o2.left && o1.right == o2.right
      case _                      => false
    }
    private def associative(left: Assertion[_], right: Assertion[_]): Boolean =
      (left, right) match {
        case (Or(Or(a1, b1), c1), Or(a2, Or(b2, c2))) =>
          a1 == a2 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def commutative(that: Assertion[_]): Boolean = (self, that) match {
      case (Or(al, bl), Or(ar, br)) => al == br && bl == ar
      case _                        => false
    }
    private def distributive(left: Assertion[_], right: Assertion[_]): Boolean =
      (left, right) match {
        case (Or(a1, And(b1, c1)), And(Or(a2, b2), Or(a3, c2))) =>
          a1 == a2 && a1 == a3 && b1 == b2 && c1 == c2
        case _ =>
          false
      }
    private def empty(left: Assertion[_], right: Assertion[_]): Boolean =
      (left, right) match {
        case (Or(l, Ignore), v) => l == v
        case (Or(Ignore, r), v) => r == v
        case _                  => false
      }
  }

  /**
   * Returns an assertion that succeeds if all of the assertions in the
   * specified collection succeed.
   */
  final def all[A](as: Iterable[Assertion[A]]): Assertion[A] =
    as.foldRight[Assertion[A]](ignore)(and)

  /**
   * Constructs an assertion that is the logical conjunction of two assertions.
   */
  final def and[A](left: Assertion[A], right: Assertion[A]): Assertion[A] =
    And(left, right)

  /**
   * Returns an assertion that succeeds if any of the assertions in the
   * specified collection succeed.
   */
  final def any[A](as: Iterable[Assertion[A]]): Assertion[A] =
    as.foldRight[Assertion[A]](ignore)(or)

  /**
   * Combines a collection of assertions to create a single assertion that
   * succeeds if all of the assertions succeed.
   */
  final def collectAll[A, E](as: Iterable[Assertion[A]]): Assertion[A] =
    foreach(as)(identity)

  /**
   * Constructs a failed assertion with the specified message.
   */
  final def failure[A](a: A): Assertion[A] =
    Failure(a)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` to produce
   * a collection of assertions, then combines all of those assertions to
   * create a single assertion that succeeds if all of the assertions succeed.
   */
  final def foreach[A, B](as: Iterable[A])(f: A => Assertion[B]): Assertion[B] =
    as.foldRight[Assertion[B]](ignore)((a, b) => and(f(a), b))

  /** Returns an ignored assertion. */
  final val ignore: Assertion[Nothing] =
    Ignore

  /**
   * Constructs an assertion that is the logical disjunction of two assertions.
   */
  final def or[A](left: Assertion[A], right: Assertion[A]): Assertion[A] =
    Or(left, right)

  /**
   * Returns a successful assertion.
   */
  final val success: Assertion[Nothing] =
    Success

  private def symmetric[A](f: (A, A) => Boolean): (A, A) => Boolean =
    (a1, a2) => f(a1, a2) || f(a2, a1)

  private def assertionHash[A](assertion: Assertion[A]): Int =
    assertion
      .fold(None, Some(success.hashCode), a => Some(failure(a).hashCode))(
        {
          case (Some(l), Some(r)) => Some(l & r)
          case (Some(l), _)       => Some(l)
          case (_, Some(r))       => Some(r)
          case _                  => None
        }, {
          case (Some(l), Some(r)) => Some(l | r)
          case (Some(l), _)       => Some(l)
          case (_, Some(r))       => Some(r)
          case _                  => None
        }
      )
      .getOrElse(ignore.hashCode)
}
