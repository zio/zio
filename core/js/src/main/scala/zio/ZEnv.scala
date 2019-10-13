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

object ZEnv {

  /**
   * Map the [[Clock.Service]] component of a ZEnv, keeping all other services the same.
   *
   * Use this with [[ZIO#provideSome]] for maximum effect.
   * {{{
   *   clock.sleep(1.second).provideSome(ZEnv.mapClock(oldClock => ???))
   * }}}
   */
  def mapClock(f: Clock.Service[Any] => Clock.Service[Any]): ZEnv => ZEnv =
    mapAll(mapClock = f)

  /**
   * Map the [[Console.Service]] component of a ZEnv, keeping all other services the same.
   */
  def mapConsole(f: Console.Service[Any] => Console.Service[Any]): ZEnv => ZEnv =
    mapAll(mapConsole = f)

  /**
   * Map the [[System.Service]] component of a ZEnv, keeping all other services the same.
   */
  def mapSystem(f: System.Service[Any] => System.Service[Any]): ZEnv => ZEnv =
    mapAll(mapSystem = f)

  /**
   * Map the [[Random.Service]] component of a ZEnv, keeping all other services the same.
   */
  def mapRandom(f: Random.Service[Any] => Random.Service[Any]): ZEnv => ZEnv =
    mapAll(mapRandom = f)

  /**
   * Map all components of a ZEnv individually.
   */
  def mapAll(
    mapClock: Clock.Service[Any] => Clock.Service[Any] = identity,
    mapConsole: Console.Service[Any] => Console.Service[Any] = identity,
    mapSystem: System.Service[Any] => System.Service[Any] = identity,
    mapRandom: Random.Service[Any] => Random.Service[Any] = identity
  ): ZEnv => ZEnv =
    old =>
      new Clock with Console with System with Random {
        val clock   = mapClock(old.clock)
        val console = mapConsole(old.console)
        val system  = mapSystem(old.system)
        val random  = mapRandom(old.random)
      }
}
