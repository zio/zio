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
import zio.{ Has, IO, UIO, URLayer, ZLayer }

object MockSystem {

  object Env           extends Method[System, String, SecurityException, Option[String]](compose)
  object Property      extends Method[System, String, Throwable, Option[String]](compose)
  object LineSeparator extends Method[System, Unit, Nothing, String](compose)

  private lazy val compose: URLayer[Has[Proxy], System] =
    ZLayer.fromService(invoke =>
      new System.Service {
        def env(variable: String): IO[SecurityException, Option[String]] = invoke(Env, variable)
        def property(prop: String): IO[Throwable, Option[String]]        = invoke(Property, prop)
        val lineSeparator: UIO[String]                                   = invoke(LineSeparator)
      }
    )
}
