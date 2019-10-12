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

object ZEnv {

  /**
   * Lifts a function that decorates a [[Clock.Service[Any]]] to a function that decoratec an entire ZEnv.
   *
   * Use this with [[ZIO#provideSome]] for maximum effect.
   * {{{
   *   clock.sleep(1.second).provideSome(ZEnv.clockDecorator(oldClock => ???))
   * }}}
   */
  def clockDecorator(f: Clock.Service[Any] => Clock.Service[Any]): ZEnv => ZEnv =
    old =>
      new ZEnvImpl(
        clock0 = f(old.clock),
        console0 = old.console,
        system0 = old.system,
        random0 = old.random,
        blocking0 = old.blocking
      )

  /**
   * Lifts a function that decorates a [[Console.Service[Any]]] to a function that decoratec an entire ZEnv.
   */
  def consoleDecorator(f: Console.Service[Any] => Console.Service[Any]): ZEnv => ZEnv =
    old =>
      new ZEnvImpl(
        clock0 = old.clock,
        console0 = f(old.console),
        system0 = old.system,
        random0 = old.random,
        blocking0 = old.blocking
      )

  /**
   * Lifts a function that decorates a [[System.Service[Any]]] to a function that decoratec an entire ZEnv.
   */
  def systemDecorator(f: System.Service[Any] => System.Service[Any]): ZEnv => ZEnv =
    old =>
      new ZEnvImpl(
        clock0 = old.clock,
        console0 = old.console,
        system0 = f(old.system),
        random0 = old.random,
        blocking0 = old.blocking
      )

  /**
   * Lifts a function that decorates a [[Random.Service[Any]]] to a function that decoratec an entire ZEnv.
   */
  def randomDecorator(f: Random.Service[Any] => Random.Service[Any]): ZEnv => ZEnv =
    old =>
      new ZEnvImpl(
        clock0 = old.clock,
        console0 = old.console,
        system0 = old.system,
        random0 = f(old.random),
        blocking0 = old.blocking
      )

  /**
   * Lifts a function that decorates a [[Blocking.Service[Any]]] to a function that decoratec an entire ZEnv.
   */
  def blockingDecorator(f: Blocking.Service[Any] => Blocking.Service[Any]): ZEnv => ZEnv =
    old =>
      new ZEnvImpl(
        clock0 = old.clock,
        console0 = old.console,
        system0 = old.system,
        random0 = old.random,
        blocking0 = f(old.blocking)
      )
  private[ZEnv] class ZEnvImpl(
    clock0: Clock.Service[Any],
    console0: Console.Service[Any],
    system0: System.Service[Any],
    random0: Random.Service[Any],
    blocking0: Blocking.Service[Any]
  ) extends Clock
      with Console
      with System
      with Random
      with Blocking {
    val clock    = clock0
    val console  = console0
    val system   = system0
    val random   = random0
    val blocking = blocking0
  }
}
