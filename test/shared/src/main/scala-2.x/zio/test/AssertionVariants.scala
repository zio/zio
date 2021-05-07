/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import zio.test.Assertion.Render._
import zio.test.FailureRenderer.{blue, magenta, red}

trait AssertionVariants {

  /**
   * Makes a new assertion that requires a value equal the specified value.
   */
  final def equalTo[A, B](expected: A)(implicit eql: Eql[A, B], diff: OptionalImplicit[Diff[B, A]]): Assertion[B] =
    //    Assertion.assertionRender((b: B) => s"$b != $expected") { actual =>
    Assertion.assertion[B](
      "equalTo",
      (b, success) =>
        diff.value match {
          case Some(diff) if !success => diff.diff(b, expected)
          case _ =>
            (blue(b.toString) + (if (success) magenta(" == ") else red(" != ")) + blue(expected.toString)).toMessage
        }
    )(param(expected)) { actual =>
      (actual, expected) match {
        case (left: Array[_], right: Array[_]) => left.sameElements[Any](right)
        case (left, right)                     => left == right
      }
    }
}
