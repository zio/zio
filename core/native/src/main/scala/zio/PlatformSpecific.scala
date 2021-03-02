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

package zio

import zio.clock.Clock
import zio.console.Console
import zio.random.Random
import zio.system.System

private[zio] trait PlatformSpecific {
  type ZEnv = Has[Clock] with Has[Console] with Has[System] with Has[Random]

  object ZEnv {

    private[zio] object Services {
      val live: ZEnv =
        Has.allOf[Clock, Console, System, Random](
          Clock.Service.live,
          Console.Service.live,
          System.Service.live,
          Random.Service.live
        )
    }

    val any: ZLayer[ZEnv, Nothing, ZEnv] =
      ZLayer.requires[ZEnv]

    val live: Layer[Nothing, ZEnv] =
      Clock.live ++ Console.live ++ System.live ++ Random.live
  }
}
