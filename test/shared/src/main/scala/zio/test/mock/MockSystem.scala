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

package zio.test.mock

import zio.{ IO, UIO }
import zio.system.System

trait MockSystem extends System {

  def system: MockSystem.Service[Any]
}

object MockSystem {

  trait Service[R] extends System.Service[R]

  object env           extends Method[MockSystem, String, Option[String]]
  object property      extends Method[MockSystem, String, Option[String]]
  object lineSeparator extends Method[MockSystem, Unit, String]

  implicit val mockable: Mockable[MockSystem] = (mock: Mock) =>
    new MockSystem {
      val system = new Service[Any] {
        def env(variable: String): IO[SecurityException, Option[String]] = mock(MockSystem.env, variable)
        def property(prop: String): IO[Throwable, Option[String]]        = mock(MockSystem.property, prop)
        val lineSeparator: UIO[String]                                   = mock(MockSystem.lineSeparator)
      }
    }
}
