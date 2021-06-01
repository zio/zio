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

import zio.internal.macros.LayerMacros

private[zio] trait ScheduleVersionSpecific[-Env, -In, +Out] { self: Schedule[Env, In, Out] =>

  /**
   * Automatically constructs the part of the environment that is not part of the `ZEnv`,
   * leaving an effect that only depends on the `ZEnv`. This will also satisfy transitive
   * `ZEnv` requirements with `ZEnv.any`, allowing them to be provided later.
   *
   * {{{
   * val zio: Schedule[OldLady with Console, Nothing, Unit] = ???
   * val oldLadyLayer: ZLayer[Fly, Nothing, OldLady] = ???
   * val flyLayer: ZLayer[Blocking, Nothing, Fly] = ???
   *
   * // The ZEnv you use later will provide both Blocking to flyLayer and Console to zio
   * val zio2 : Schedule[ZEnv, Nothing, Unit] = zio.injectCustom(oldLadyLayer, flyLayer)
   * }}}
   */
  def injectCustom(layers: ZLayer[_, Nothing, _]*): Schedule[ZEnv, In, Out] =
    macro LayerMacros.injectSomeImpl[Schedule, ZEnv, Env, Nothing, Out]

  /**
   * Automatically assembles a layer for the Schedule effect.
   */
  def inject(layers: ZLayer[_, Nothing, _]*): Schedule[Any, In, Out] =
    macro LayerMacros.injectImpl[Schedule, Env, Nothing, Out]

}
