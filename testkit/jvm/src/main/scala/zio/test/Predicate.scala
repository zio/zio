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

/**
 * A `Predicate[A]` is capable of producing assertion results on an `A`. As a
 * proposition, predicates compose using logical conjuction and disjunction,
 * and can be negated.
 */
class Predicate[-A] private (render: String, val run: A => AssertResult) { self =>
  import AssertResult._

  /**
   * Returns a new predicate that succeeds only if both predicates succeed.
   */
  final def &&[A1 <: A](that: => Predicate[A1]): Predicate[A1] =
    Predicate.predicate(s"${self} && ${that}") { actual =>
      self.run(actual) match {
        case Failure(message) => Failure(message)
        case Success(_)       => that.run(actual)
        case Pending          => that.run(actual)
      }
    }

  /**
   * Returns a new predicate that succeeds if either predicates succeed.
   */
  final def ||[A1 <: A](that: => Predicate[A1]): Predicate[A1] =
    Predicate.predicate(s"${self} || ${that}") { actual =>
      self.run(actual) match {
        case Failure(_)       => that.run(actual)
        case Success(message) => Success(message)
        case Pending          => that.run(actual)
      }
    }

  /**
   * Provides a meaningful string rendering of the predicate.
   */
  override final def toString: String = render

  override final def equals(that: Any): Boolean = that match {
    case that: Predicate[_] => this.toString == that.toString
  }
}

object Predicate {

  /**
   * Makes a new predicate that requires an iterable contain the specified
   * element.
   */
  final def contains[A](element: A): Predicate[Iterable[A]] =
    Predicate.predicate(s"contains(${element})") { actual =>
      val message =
        Message(s"Expected ${actual} to contain ${element}", s"Expected ${actual} to not contain ${element}")

      val failed = AssertResult.Failure(message)

      if (!actual.exists(_ == element)) failed
      else failed.negate
    }

  /**
   * Makes a new predicate that requires a value equal the specified value.
   */
  final def equals[A](expected: A): Predicate[A] =
    Predicate.predicate(s"equals(${expected})") { actual =>
      val message =
        Message(s"Expected ${expected} but found ${actual}", s"Expected any value other than ${actual}")

      val failed = AssertResult.Failure(message)

      if (actual != expected) failed else failed.negate
    }

  /**
   * Makes a new predicate that always fails.
   */
  final def failure: Predicate[Any] = Predicate.predicate("failure") { actual =>
    val message =
      Message(s"Always succeeds: ${actual}", s"Always fails: ${actual}")

    AssertResult.Failure(message.negate)
  }

  /**
   * Makes a new predicate that requires an exit value to fail.
   */
  final def fails[E](predicate: Predicate[E]): Predicate[Exit[E, Any]] = Predicate.predicate(s"fails(${predicate})") {
    actual =>
      actual match {
        case Exit.Failure(cause) if cause.failures.length > 0 => predicate.run(cause.failures.head)

        case exit =>
          val message = Message(
            s"Expected failure satisfying ${predicate} but found ${exit}",
            s"<unreachable>"
          )

          AssertResult.Failure(message)
      }
  }

  /**
   * Makes a new predicate that requires a value be true.
   */
  final def isTrue: Predicate[Boolean] = Predicate.predicate(s"isTrue") { actual =>
    val message = Message(s"Expected true but found false", s"Expected false but found true")

    val failed = AssertResult.Failure(message)

    if (actual != true) failed
    else failed.negate
  }

  /**
   * Makes a new predicate that requires a value be true.
   */
  final def isFalse: Predicate[Boolean] = not(isTrue)

  /**
   * Makes a new predicate that requires a Left value satisfying a specified
   * predicate.
   */
  final def left[A](predicate: Predicate[A]): Predicate[Either[A, Nothing]] =
    Predicate.predicate(s"left(${predicate})") { actual =>
      actual match {
        case Left(a) => predicate.run(a)
        case Right(_) =>
          val message = Message(
            s"Expected Left satisfying ${predicate} but found ${actual}",
            s"<unreachable>"
          )

          AssertResult.Failure(message)
      }
    }

  /**
   * Makes a new predicate that requires a Some value satisfying the specified
   * predicate.
   */
  final val none: Predicate[Option[Any]] = Predicate.predicate(s"none") { actual =>
    actual match {
      case None =>
        val message = Message(s"<unreachable>", s"Expected Some but found ${actual}")

        AssertResult.Success(message)
      case Some(_) =>
        val message = Message(s"Expected None but found ${actual}", s"<unreachable>")

        AssertResult.Failure(message)
    }
  }

  /**
   * Makes a new predicate that negates the specified predicate.
   */
  final def not[A](predicate: Predicate[A]): Predicate[A] =
    Predicate.predicate(s"not(${predicate})")(actual => predicate.run(actual).negate)

  /**
   * Makes a new `Predicate` from a pretty-printing and a function.
   */
  final def predicate[A](render: String)(run: A => AssertResult): Predicate[A] = new Predicate(render, run)

  /**
   * Makes a new predicate that requires a Right value satisfying a specified
   * predicate.
   */
  final def right[A](predicate: Predicate[A]): Predicate[Either[Nothing, A]] =
    Predicate.predicate(s"right(${predicate})") { actual =>
      actual match {
        case Right(a) => predicate.run(a)
        case Left(_) =>
          val message = Message(
            s"Expected Right satisfying ${predicate} but found ${actual}",
            s"<unreachable>"
          )

          AssertResult.Failure(message)
      }
    }

  /**
   * Makes a new predicate that requires a Some value satisfying the specified
   * predicate.
   */
  final def some[A](predicate: Predicate[A]): Predicate[Option[A]] = Predicate.predicate(s"some(${predicate}") {
    actual =>
      actual match {
        case Some(a) => predicate.run(a)
        case None =>
          val message = Message(
            s"Expected Some satisfying ${predicate} but found ${actual}",
            s"<unreachable>"
          )

          AssertResult.Failure(message)
      }
  }

  /**
   * Makes a new predicate that requires an exit value to succeed.
   */
  final def succeeds[A](predicate: Predicate[A]): Predicate[Exit[Any, A]] =
    Predicate.predicate(s"succeeds(${predicate})") { actual =>
      actual match {
        case Exit.Success(a) => predicate.run(a)

        case exit =>
          val message = Message(
            s"Expected success satisfying ${predicate} but found ${exit}",
            s"<unreachable>"
          )

          AssertResult.Failure(message)
      }
    }

  /**
   * Makes a new predicate that always succeeds.
   */
  final def success: Predicate[Any] = Predicate.predicate("success") { actual =>
    val message =
      Message(s"Always succeeds: ${actual}", s"Always fails: ${actual}")

    AssertResult.Success(message)
  }
}
