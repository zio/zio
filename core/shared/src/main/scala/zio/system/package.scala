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

package zio

import com.github.ghik.silencer.silent

import java.lang.{ System => JSystem }
import scala.collection.JavaConverters._

package object system {

  type System = Has[System.Service]

  object System extends Serializable {
    trait Service extends Serializable {
      def env(variable: String): IO[SecurityException, Option[String]]

      def envOrElse(variable: String, alt: => String): IO[SecurityException, String]

      def envOrOption(variable: String, alt: => Option[String]): IO[SecurityException, Option[String]]

      def envs: IO[SecurityException, Map[String, String]]

      def lineSeparator: UIO[String]

      def properties: IO[Throwable, Map[String, String]]

      def property(prop: String): IO[Throwable, Option[String]]

      def propertyOrElse(prop: String, alt: => String): IO[Throwable, String]

      def propertyOrOption(prop: String, alt: => Option[String]): IO[Throwable, Option[String]]
    }

    object Service {
      val live: Service = new Service {

        def env(variable: String): IO[SecurityException, Option[String]] =
          IO.effect(Option(JSystem.getenv(variable))).refineToOrDie[SecurityException]

        def envOrElse(variable: String, alt: => String): IO[SecurityException, String] =
          envOrElseWith(variable, alt)(env)

        def envOrOption(variable: String, alt: => Option[String]): IO[SecurityException, Option[String]] =
          envOrOptionWith(variable, alt)(env)

        @silent("JavaConverters")
        val envs: IO[SecurityException, Map[String, String]] =
          IO.effect(JSystem.getenv.asScala.toMap).refineToOrDie[SecurityException]

        val lineSeparator: UIO[String] = IO.effectTotal(JSystem.lineSeparator)

        @silent("JavaConverters")
        val properties: IO[Throwable, Map[String, String]] =
          IO.effect(JSystem.getProperties.asScala.toMap)

        def property(prop: String): IO[Throwable, Option[String]] =
          IO.effect(Option(JSystem.getProperty(prop)))

        def propertyOrElse(prop: String, alt: => String): IO[Throwable, String] =
          propertyOrElseWith(prop, alt)(property)

        def propertyOrOption(prop: String, alt: => Option[String]): IO[Throwable, Option[String]] =
          propertyOrOptionWith(prop, alt)(property)
      }
    }

    val any: ZLayer[System, Nothing, System] =
      ZLayer.requires[System]

    val live: Layer[Nothing, System] =
      ZLayer.succeed(Service.live)

    private[zio] def envOrElseWith(variable: String, alt: => String)(
      env: String => IO[SecurityException, Option[String]]
    ): IO[SecurityException, String] =
      env(variable).map(_.getOrElse(alt))

    private[zio] def envOrOptionWith(variable: String, alt: => Option[String])(
      env: String => IO[SecurityException, Option[String]]
    ): IO[SecurityException, Option[String]] =
      env(variable).map(_.orElse(alt))

    private[zio] def propertyOrElseWith(prop: String, alt: => String)(
      property: String => IO[Throwable, Option[String]]
    ): IO[Throwable, String] =
      property(prop).map(_.getOrElse(alt))

    private[zio] def propertyOrOptionWith(prop: String, alt: => Option[String])(
      property: String => IO[Throwable, Option[String]]
    ): IO[Throwable, Option[String]] =
      property(prop).map(_.orElse(alt))
  }

  /**
   * Retrieves the value of an environment variable.
   */
  def env(variable: => String): ZIO[System, SecurityException, Option[String]] =
    ZIO.accessM(_.get.env(variable))

  /**
   * Retrieves the value of an environment variable or else returns the
   * specified fallback value.
   */
  def envOrElse(variable: String, alt: => String): ZIO[System, SecurityException, String] =
    ZIO.accessM(_.get.envOrElse(variable, alt))

  /**
   * Retrieves the value of an environment variable or else returns the
   * specified optional fallback value.
   */
  def envOrOption(variable: String, alt: => Option[String]): ZIO[System, SecurityException, Option[String]] =
    ZIO.accessM(_.get.envOrOption(variable, alt))

  /**
   * Retrieves the values of all environment variables.
   */
  val envs: ZIO[System, SecurityException, Map[String, String]] =
    ZIO.accessM(_.get.envs)

  /**
   * Retrieves the values of all system properties.
   */
  val properties: ZIO[System, Throwable, Map[String, String]] =
    ZIO.accessM(_.get.properties)

  /**
   * Retrieves the value of a system property.
   */
  def property(prop: => String): ZIO[System, Throwable, Option[String]] =
    ZIO.accessM(_.get.property(prop))

  /**
   * Retrieves the value of a system property or else return the specified
   * fallback value.
   */
  def propertyOrElse(prop: String, alt: => String): RIO[System, String] =
    ZIO.accessM(_.get.propertyOrElse(prop, alt))

  /**
   * Retrieves the value of a system property or else return the specified
   * optional fallback value.
   */
  def propertyOrOption(prop: String, alt: => Option[String]): ZIO[System, Throwable, Option[String]] =
    ZIO.accessM(_.get.propertyOrOption(prop, alt))

  /**
   * Retrieves the value of the system-specific line separator.
   */
  val lineSeparator: URIO[System, String] =
    ZIO.accessM(_.get.lineSeparator)
}
