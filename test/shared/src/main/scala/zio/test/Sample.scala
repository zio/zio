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

import zio.stream.ZStream

/**
 * A sample is a single observation from a random variable, together with a
 * stream of "shrinkings" used for minimization of "large" failures.
 */
final case class Sample[-R, +A](value: A, shrink: ZStream[R, Nothing, A]) { self =>
  final def map[B](f: A => B): Sample[R, B] = Sample(f(value), shrink.map(f))

  final def zip[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    Sample((self.value, that.value), self.shrink.zip(that.shrink))

  final def zipWith[R1 <: R, B, C](that: Sample[R1, B])(f: (A, B) => C): Sample[R1, C] =
    (self zip that) map f.tupled
}
object Sample {

  /**
   * A sample without shrinking.
   */
  def noShrink[A](a: => A): Sample[Any, A] = Sample(a, ZStream.empty)
}
