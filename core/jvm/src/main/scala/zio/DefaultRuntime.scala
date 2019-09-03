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

package zio

import zio.clock.Clock
import zio.console.Console
import zio.system.System
import zio.random.Random
import zio.blocking.Blocking
import zio.effect.Effect
import zio.internal.{ Platform, PlatformLive }

trait DefaultRuntime extends Runtime[Clock with Console with System with Random with Blocking] {
  type Environment = Clock with Console with System with Random with Blocking

  val Platform: Platform = PlatformLive.Default
  val Environment: Environment =
    new Clock.Live with Console.Live with System.Live with Random.Live with Effect.Live with Blocking.Live
}
