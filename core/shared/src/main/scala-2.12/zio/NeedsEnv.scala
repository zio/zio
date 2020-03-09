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

package zio

import scala.annotation.implicitAmbiguous

/**
 * A value of type `NeedsEnv[R]` provides implicit evidence that an effect with
 * environment type `R` needs an environment, that is, that `R` is not equal to
 * `Any`.
 */
sealed trait NeedsEnv[+R]

object NeedsEnv extends NeedsEnv[Nothing] {

  implicit def needsEnv[R]: NeedsEnv[R] = NeedsEnv

  // Provide multiple ambiguous values so an implicit NeedsEnv[Any] cannot be found.
  @implicitAmbiguous(
    "This operation assumes that your effect requires an environment. " +
      "However, your effect has Any for the environment type, which means it " +
      "has no requirement, so there is no need to provide the environment."
  )
  implicit val needsEnvAmbiguous1: NeedsEnv[Any] = NeedsEnv
  implicit val needsEnvAmbiguous2: NeedsEnv[Any] = NeedsEnv
}
