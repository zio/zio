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
 * success, or failure, with some message of type `A`.
 */
sealed trait Assertion[+A] { self =>
  import Assertion._

  /**
   * Returns a new result that is a success if both this result and the
   * specified result are successes. If both results are failures, the first
   * failure message will be returned.
   */
  final def &&[A1 >: A](that: Assertion[A1]): Assertion[A1] =
    both(that)

  /**
   * Returns a new result that is a success if either this result or the
   * specified result is a success. If both results are failures, the first
   * failure message will be returned.
   */
  final def ||[A1 >: A](that: Assertion[A1]): Assertion[A1] =
    either(that)

  /**
   * Returns a new result, with the message mapped to the specified constant.
   */
  final def as[L2](l2: L2): Assertion[L2] = self.map(_ => l2)

  /**
   * A named alias for `&&`.
   */
  final def both[A1 >: A](that: Assertion[A1]): Assertion[A1] =
    bothWith(that)((a, _) => a)

  /**
   * Returns a new result that is a success if both this result and the
   * specified result are successes. If both results are failures, uses the
   * specified function to combine the messages.
   */
  final def bothWith[A1 >: A](that: Assertion[A1])(f: (A1, A1) => A1): Assertion[A1] =
    (self, that) match {
      case (Ignore, that)             => that
      case (self, Ignore)             => self
      case (Failure(v1), Failure(v2)) => Failure(f(v1, v2))
      case (Success, that)            => that
      case (self, Success)            => self
    }

  /**
   * A named alies for `||`.
   */
  final def either[A1 >: A](that: Assertion[A1]): Assertion[A1] =
    eitherWith(that)((a, _) => a)

  /**
   * Returns a new result that is a success if either this result or the
   * specified result is a success. If both results are failures, uses the
   * specified function to combine the messages.
   */
  final def eitherWith[A1 >: A](that: Assertion[A1])(f: (A1, A1) => A1): Assertion[A1] =
    (self, that) match {
      case (Ignore, that)             => that
      case (self, Ignore)             => self
      case (Failure(v1), Failure(v2)) => Failure(f(v1, v2))
      case _                          => Success
    }

  /**
   * Detemines if the result failed.
   */
  final def failure: Boolean = self match {
    case Failure(_) => true
    case _          => false
  }

  /**
   * Returns a new result, with the message mapped by the specified function.
   */
  final def map[A1](f: A => A1): Assertion[A1] = self match {
    case Ignore           => Ignore
    case Failure(message) => Failure(f(message))
    case Success          => Success
  }

  /**
   * Detemines if the result succeeded.
   */
  final def success: Boolean = self match {
    case Success => true
    case _       => false
  }
}
object Assertion {
  case object Ignore                       extends Assertion[Nothing]
  case object Success                      extends Assertion[Nothing]
  final case class Failure[+A](message: A) extends Assertion[A]

  /**
   * Combines a collection of assertions to create a single assertion that
   * succeeds if all of the assertions succeed, and otherwise fails with a list
   * of the failure messages.
   */
  final def collectAll[A, E](as: Iterable[Assertion[A]]): Assertion[List[A]] =
    foreach(as)(identity)

  /**
   * Constructs a failed assertion with the specified message.
   */
  final def failure[A](a: A): Assertion[A] = Failure(a)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` to produce
   * a collection of assertions, then combines all of those assertions to
   * create a single assertion that succeeds if all of the assertions succeed,
   * and otherwise fails with a list of the failure messages.
   */
  final def foreach[A, B](as: Iterable[A])(f: A => Assertion[B]): Assertion[List[B]] =
    as.foldRight[Assertion[List[B]]](success) { (a, assert) =>
      f(a).map(List(_)).bothWith(assert)((b, bs) => b ::: bs)
    }

  /**
   * Returns a successful assertion.
   */
  final val success: Assertion[Nothing] = Success
}
