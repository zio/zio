/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
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

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder

private[zio] trait BuildFromCompat {

  type BuildFrom[-From, -A, +C] = CanBuildFrom[From, A, C]

  implicit class BuildFromOps[From, A, C](private val self: BuildFrom[From, A, C]) {
    def fromSpecific(from: From)(iterable: Iterable[A]): C = {
      val builder = newBuilder(from)
      builder ++= iterable
      builder.result()
    }
    def newBuilder(from: From): Builder[A, C] =
      self.apply(from)
  }
}
