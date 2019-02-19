/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio

import scalaz.zio.Exit.Cause

/**
 * Represents a failure in a fiber. This could be caused by some non-
 * recoverable error, such as a defect or system error, by some typed error,
 * or by interruption (or combinations of all of the above).
 *
 * This class is used to wrap ZIO failures into something that can be thrown,
 * to better integrate with Scala exception handling.
 */
final case class FiberFailure(cause: Cause[Any]) extends Throwable {
  override final def getMessage: String = message(cause)

  private final def message(cause: Cause[Any]): String = {
    def gen(t: Throwable): String =
      "The fiber was terminated by a defect: " + t.getMessage + "\n" + t.getStackTrace.mkString("\n")

    cause match {
      case Cause.Fail(t: Throwable) => "A checked error was not handled by a fiber: " + gen(t)
      case Cause.Fail(error)        => "A checked error was not handled by a fiber: " + error.toString
      case Cause.Die(t)             => "An unchecked error was produced by a fiber: " + gen(t)
      case Cause.Interrupt          => "The fiber was terminated by an interruption"
      case Cause.Then(left, right)  => "Both fibers terminated in sequence: \n" + message(left) + "\n" + message(right)
      case Cause.Both(left, right)  => "Both fibers terminated in parallel: \n" + message(left) + "\n" + message(right)
    }
  }
}
