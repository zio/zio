/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

package zio.stream

import zio.{RefM, UIO}

/**
 * A `SubscriptionRef[A]` contains a `RefM[A]` and a `Stream` that will emit
 * every change to the `RefM`.
 */
final class SubscriptionRef[A] private (val ref: RefM[A], val changes: Stream[Nothing, A])

object SubscriptionRef {

  /**
   * Creates a new `SubscriptionRef` with the specified value.
   */
  def make[A](a: A): UIO[SubscriptionRef[A]] =
    RefM.dequeueRef(a).map { case (ref, queue) =>
      new SubscriptionRef(ref, ZStream.fromQueue(queue))
    }
}
