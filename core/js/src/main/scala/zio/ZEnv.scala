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
   * Lifts a function that decorates a [[Clock.Service]] to a function that decoratec an entire ZEnv.
   *
   * Use this with [[ZIO#provideSome]] for maximum effect.
   * {{{
   *   clock.sleep(1.second).provideSome(ZEnv.clockDecorator(oldClock => ???))
   * }}}
   */
  def clockDecorator(f: Clock.Service[Any] => Clock.Service[Any]): ZEnv => ZEnv =
    zEnvDecorator(clockDecorator = f)

  /**
   * Lifts a function that decorates a [[Console.Service]] to a function that decoratec an entire ZEnv.
   */
  def consoleDecorator(f: Console.Service[Any] => Console.Service[Any]): ZEnv => ZEnv =
    zEnvDecorator(consoleDecorator = f)

  /**
   * Lifts a function that decorates a [[System.Service]] to a function that decoratec an entire ZEnv.
   */
  def systemDecorator(f: System.Service[Any] => System.Service[Any]): ZEnv => ZEnv =
    zEnvDecorator(systemDecorator = f)

  /**
   * Lifts a function that decorates a [[Random.Service]] to a function that decoratec an entire ZEnv.
   */
  def randomDecorator(f: Random.Service[Any] => Random.Service[Any]): ZEnv => ZEnv =
    zEnvDecorator(randomDecorator = f)

  /**
   * Create a decorator which modifies chosen parts of a Zenv.
   */
  def zEnvDecorator(
    clockDecorator: Clock.Service[Any] => Clock.Service[Any] = identity,
    consoleDecorator: Console.Service[Any] => Console.Service[Any] = identity,
    systemDecorator: System.Service[Any] => System.Service[Any] = identity,
    randomDecorator: Random.Service[Any] => Random.Service[Any] = identity
  ): ZEnv => ZEnv =
    old =>
      new Clock with Console with System with Random {
        val clock   = clockDecorator(old.clock)
        val console = consoleDecorator(old.console)
        val system  = systemDecorator(old.system)
        val random  = randomDecorator(old.random)
      }
}
