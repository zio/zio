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
 * `Laws[Caps, R]` represents a set of laws that values with capabilities
 * `Caps` are expected to satisfy. Laws can be run by providing the
 * capabilities for a type `A` along with a generator of values of type `A` to
 * return a test result. Laws can be combined using `+` to produce a set of
 * laws that require both sets of laws to be satisfied.
 */
sealed trait Laws[-Caps[_], -R] { self =>

  /**
   * Test that values of type `A` satisfy the laws using the specified
   * generator.
   */
  def run[R1 <: R, A](caps: Caps[A], gen: Gen[R1, A]): ZIO[R1, Nothing, TestResult]

  /**
   * Combine these laws with the specified laws to produce a set of laws that
   * require both sets of laws to be satisfied.
   */
  def +[Caps1[x] <: Caps[x], R1 <: R](that: Laws[Caps1, R1]): Laws[Caps1, R1] =
    Laws.Both(self, that)
}

object Laws {

  private trait Both[-Caps[_], -R] extends Laws[Caps, R] {

    def left: Laws[Caps, R]

    def right: Laws[Caps, R]

    final def run[R1 <: R, A](caps: Caps[A], gen: Gen[R1, A]): ZIO[R1, Nothing, TestResult] =
      left.run(caps, gen).zipWith(right.run(caps, gen))(_ && _)
  }

  object Both {
    def apply[Caps[_], R](left0: Laws[Caps, R], right0: Laws[Caps, R]): Laws[Caps, R] =
      new Both[Caps, R] {
        val left  = left0
        val right = right0
      }
  }

  abstract class Law[-Caps[_]](label: String) extends Laws[Caps, Any] { self =>

    def apply[A](caps: Caps[A], a1: A, a2: A, a3: A): TestResult

    final def run[R, A](caps: Caps[A], gen: Gen[R, A]): ZIO[R, Nothing, TestResult] =
      check(gen, gen, gen)(apply(caps, _, _, _).map(_.label(label)))
  }
}
