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
import zio.test.mock.internal.MockRuntime
import zio.{ Has, IO, UIO, ZLayer }

object MockSystem {

  sealed trait Tag[I, A] extends Method[System, I, A] {
    val mock = MockSystem.mock
  }

  object Env           extends Tag[String, Option[String]]
  object Property      extends Tag[String, Option[String]]
  object LineSeparator extends Tag[Unit, String]

  private lazy val mock: ZLayer[Has[MockRuntime], Nothing, System] =
    ZLayer.fromService(mock =>
      new System.Service {
        def env(variable: String): IO[SecurityException, Option[String]] = mock(Env, variable)
        def property(prop: String): IO[Throwable, Option[String]]        = mock(Property, prop)
        val lineSeparator: UIO[String]                                   = mock(LineSeparator)
      }
    )
}
