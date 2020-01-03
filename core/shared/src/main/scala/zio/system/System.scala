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

package zio.system

import java.lang.{ System => JSystem }
import zio.{ Has, IO, UIO, ZDep }

object System extends Serializable {
  trait Service extends Serializable {
    def env(variable: String): IO[SecurityException, Option[String]]

    def property(prop: String): IO[Throwable, Option[String]]

    val lineSeparator: UIO[String]
  }

  val live: ZDep[Has.Any, Nothing, System] = ZDep.succeed(
    new Service {

      def env(variable: String): IO[SecurityException, Option[String]] =
        IO.effect(Option(JSystem.getenv(variable))).refineToOrDie[SecurityException]

      def property(prop: String): IO[Throwable, Option[String]] =
        IO.effect(Option(JSystem.getProperty(prop)))

      val lineSeparator: UIO[String] = IO.effectTotal(JSystem.lineSeparator)
    }
  )
}
