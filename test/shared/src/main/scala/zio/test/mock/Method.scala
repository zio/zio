/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

import com.github.ghik.silencer.silent

import zio.test.Assertion
import zio.test.mock.internal.MockRuntime
import zio.{ =!=, Has, Tagged, ZLayer }

/**
 * A `Model[R, I, A]` represents a capability of environment `R` that
 * takes an input `I` and returns an effect that may produce a single `A`.
 */
abstract class Method[R <: Has[_]: Tagged, I, A] { self =>

  def mock: ZLayer[MockRuntime, Nothing, R]

  /**
   * Provides the `Assertion` on method arguments `I` to produce `ArgumentExpectation`.
   *
   * Available only for methods that do take arguments.
   */
  @silent("parameter value ev in method apply is never used")
  def apply(assertion: Assertion[I])(implicit ev: I =!= Unit): ArgumentExpectation[R, I, A] =
    ArgumentExpectation(self, assertion)

  /**
   * Provides the `ReturnExpectation` to produce the final `Expectation`.
   *
   * Available only for methods that don't take arguments.
   */
  @silent("parameter value ev in method returns is never used")
  def returns[E](returns: ReturnExpectation[I, E, A])(implicit ev: I <:< Unit): Expectation[R] =
    Expectation.Call[R, I, E, A](self, Assertion.isUnit.asInstanceOf[Assertion[I]], returns.io)

  /**
   * Render method fully qualified name.
   */
  override def toString: String = {
    val fragments = getClass.getName.replaceAll("\\$", ".").split("\\.")
    fragments.toList.splitAt(fragments.size - 3) match {
      case (namespace, module :: service :: method :: Nil) =>
        s"""${namespace.mkString(".")}.$module.$service.$method"""
      case _ => fragments.mkString(".")
    }
  }
}
