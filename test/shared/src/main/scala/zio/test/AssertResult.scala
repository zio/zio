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
 * An `AssertResult[A]` is the result of running a test, which may be ignore,
 * success, or failure, with some message of type `A`.
 */
sealed trait AssertResult[+A] { self =>
  import AssertResult._

  /**
   * Returns a new result, with the message mapped to the specified constant.
   */
  final def as[L2](l2: L2): AssertResult[L2] = self.map(_ => l2)

  /**
   * Combines this result with the specified result.
   */
  final def combineWith[A1 >: A](that: AssertResult[A1])(f: (A1, A1) => A1): AssertResult[A1] =
    (self, that) match {
      case (Ignore, that)             => that
      case (self, Ignore)             => self
      case (Failure(v1), Failure(v2)) => Failure(f(v1, v2))
      case (Success, that)            => that
      case (self, Success)            => self
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
  final def map[A1](f: A => A1): AssertResult[A1] = self match {
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
object AssertResult {
  case object Ignore                       extends AssertResult[Nothing]
  case object Success                      extends AssertResult[Nothing]
  final case class Failure[+A](message: A) extends AssertResult[A]

  /**
   * Constructs a failed assertion with the specified message.
   */
  def failure[A](a: A): AssertResult[A] = Failure(a)

  /**
   * Returns a successful assertion.
   */
  val success: AssertResult[Nothing] = Success
}
