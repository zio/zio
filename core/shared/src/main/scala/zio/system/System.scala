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

package zio.system

import java.lang.{ System => JSystem }

import zio._

object System extends Serializable {

  val any: ZLayer[System, Nothing, System] =
    ZLayer.requires[System]

  val live: ZLayer.NoDeps[Nothing, System] = ZLayer.succeed(
    new Service {

      def env(variable: String): IO[SecurityException, Option[String]] =
        IO.effect(Option(JSystem.getenv(variable))).refineToOrDie[SecurityException]

      def property(prop: String): IO[Throwable, Option[String]] =
        IO.effect(Option(JSystem.getProperty(prop)))

      val lineSeparator: UIO[String] = IO.effectTotal(JSystem.lineSeparator)
    }
  )
}
