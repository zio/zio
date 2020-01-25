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

package zio.test.mock

import zio.system.System
import zio.{ Has, IO, UIO }
import zio.{ IO, UIO }

object MockSystem {

  object env           extends Method[System.Service, String, Option[String]]
  object property      extends Method[System.Service, String, Option[String]]
  object lineSeparator extends Method[System.Service, Unit, String]

  implicit val mockableSystem: Mockable[System.Service] = (mock: Mock) =>
    Has(new System.Service {
      def env(variable: String): IO[SecurityException, Option[String]] = mock(MockSystem.env, variable)
      def property(prop: String): IO[Throwable, Option[String]]        = mock(MockSystem.property, prop)
      val lineSeparator: UIO[String]                                   = mock(MockSystem.lineSeparator)
    })
}
