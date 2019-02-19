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

package scalaz.zio.system

import scalaz.zio.ZIO

trait System extends Serializable {
  val system: System.Service[Any]
}
object System extends Serializable {
  trait Service[R] extends Serializable {
    def env(variable: String): ZIO[R, Nothing, Option[String]]

    def property(prop: String): ZIO[R, Throwable, Option[String]]

    val lineSeparator: ZIO[R, Nothing, String]
  }
  trait Live extends System {
    val system: Service[Any] = new Service[Any] {
      import java.lang.{ System => JSystem }

      def env(variable: String): ZIO[Any, Nothing, Option[String]] = ZIO.defer(Option(JSystem.getenv(variable)))

      def property(prop: String): ZIO[Any, Throwable, Option[String]] =
        ZIO.syncThrowable(Option(JSystem.getProperty(prop)))

      val lineSeparator: ZIO[Any, Nothing, String] = ZIO.defer(JSystem.lineSeparator)
    }
  }
  object Live extends Live
}
