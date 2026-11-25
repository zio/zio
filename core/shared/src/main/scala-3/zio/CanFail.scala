/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.{implicitNotFound, targetName}
import scala.util.NotGiven

/**
 * A value of type `CanFail[E]` provides implicit evidence that an effect with
 * error type `E` can fail, that is, that `E` is not equal to `Nothing`.
 */
@implicitNotFound(
  "This error handling operation assumes your effect can fail. However, " +
    "your effect has Nothing for the error type, which means it cannot " +
    "fail, so there is no need to handle the failure. To find out which " +
    "method you can use instead of this operation, please see the " +
    "reference chart at: https://zio.dev/can_fail"
)
sealed abstract class CanFail[-E]

/**
 * Provides `CanFail[E]` for every error type `E` except `Nothing`. When an
 * error handling operator is called on an effect that cannot fail, the
 * compiler reports that the operation is not needed.
 */
object CanFail extends CanFail[Any] with CanFailLowPriority {

  /**
   * Provides `CanFail[E]` for an error type `E` that is not `Nothing`. On
   * Scala 2 it matches every `E`, and the two ambiguous instances for
   * `Nothing` make the search for `CanFail[Nothing]` fail; on Scala 3 it
   * requires evidence that `E` is not `Nothing`.
   */
  inline given canFail[E](using inline ev: NotGiven[E =:= Nothing]): CanFail[E] = CanFail

  @targetName("canFail")
  @deprecated("Kept for binary compatibility only, do not use", "2.1.16")
  private[zio] def _canFailCompat[E](implicit ev: NotGiven[E =:= Nothing]): CanFail[E] = CanFail
}

private[zio] transparent trait CanFailLowPriority { self: CanFail.type =>
  // In some extremely extremely rare case the type inference prior to macro expansion doesn't work,
  // so we need a non-inlined low priority as a fallback
  /**
   * A lower-priority, non-inline fallback for `canFail`: it also provides
   * `CanFail[E]` when `E` is not `Nothing`.
   */
  implicit def canFailLowPriority[E](implicit ev: NotGiven[E =:= Nothing]): CanFail[E] = self
}
