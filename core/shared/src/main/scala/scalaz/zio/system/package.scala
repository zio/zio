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

package scalaz.zio

package object system extends System.Service[System] {

  /** Retrieve the value of an environment variable **/
  def env(variable: String): ZIO[System, SecurityException, Option[String]] =
    ZIO.accessM(_.system env variable)

  /** Retrieve the value of a system property **/
  def property(prop: String): ZIO[System, Throwable, Option[String]] =
    ZIO.accessM(_.system property prop)

  /** System-specific line separator **/
  val lineSeparator: ZIO[System, Nothing, String] =
    ZIO.accessM(_.system.lineSeparator)
}
