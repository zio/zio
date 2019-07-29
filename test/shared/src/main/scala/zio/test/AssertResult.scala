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
 * An `AssertResult[L]` is the result of running a test, which may be ignore,
 * success, or failure, with some label of type `L`.
 */
sealed trait AssertResult[+L] { self =>
  import AssertResult._

  /**
   * Returns a new result, with the label mapped to the specified constant.
   */
  final def const[L2](l2: L2): AssertResult[L2] = self.map(_ => l2)

  /**
   * Combines this result with the specified result.
   */
  final def combineWith[L1 >: L](that: AssertResult[L1])(f: (L1, L1) => L1): AssertResult[L1] =
    (self, that) match {
      case (Ignore, that)             => that
      case (self, Ignore)             => self
      case (Success(v1), Success(v2)) => Success(f(v1, v2))
      case (Failure(v1), Failure(v2)) => Failure(f(v1, v2))
      case (Success(_), that)         => that
      case (self, Success(_))         => self
    }

  /**
   * Detemines if the result failed.
   */
  final def failure: Boolean = self match {
    case Failure(_) => true
    case _          => false
  }

  /**
   * Returns a new result, with the label mapped by the specified function.
   */
  final def map[L1](f: L => L1): AssertResult[L1] = self match {
    case Ignore         => Ignore
    case Failure(label) => Failure(f(label))
    case Success(label) => Success(f(label))
  }

  /**
   * Returns a new result, with success and failure inverted, and the label
   * transformed by the specified function.
   */
  final def negate[L1](f: L => L1): AssertResult[L1] = self match {
    case Ignore         => Ignore
    case Failure(label) => Success(f(label))
    case Success(label) => Failure(f(label))
  }

  /**
   * Detemines if the result succeeded.
   */
  final def success: Boolean = self match {
    case Success(_) => true
    case _          => false
  }
}
object AssertResult {
  case object Ignore                     extends AssertResult[Nothing]
  final case class Success[+L](label: L) extends AssertResult[L]
  final case class Failure[+L](label: L) extends AssertResult[L]

  def failure[L](l: L): AssertResult[L] = Failure(l)

  val failureUnit: AssertResult[Unit] = failure(())

  val successUnit: AssertResult[Unit] = success(())

  def success[L](l: L): AssertResult[L] = Success(l)
}
