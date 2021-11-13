/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import com.github.ghik.silencer.silent

import java.lang.{System => JSystem}
import scala.collection.JavaConverters._

trait System extends Serializable {
  def env(variable: => String)(implicit trace: ZTraceElement): IO[SecurityException, Option[String]]

  def envOrElse(variable: => String, alt: => String)(implicit trace: ZTraceElement): IO[SecurityException, String]

  def envOrOption(variable: => String, alt: => Option[String])(implicit
    trace: ZTraceElement
  ): IO[SecurityException, Option[String]]

  def envs(implicit trace: ZTraceElement): IO[SecurityException, Map[String, String]]

  def lineSeparator(implicit trace: ZTraceElement): UIO[String]

  def properties(implicit trace: ZTraceElement): IO[Throwable, Map[String, String]]

  def property(prop: => String)(implicit trace: ZTraceElement): IO[Throwable, Option[String]]

  def propertyOrElse(prop: => String, alt: => String)(implicit trace: ZTraceElement): IO[Throwable, String]

  def propertyOrOption(prop: => String, alt: => Option[String])(implicit
    trace: ZTraceElement
  ): IO[Throwable, Option[String]]
}

object System extends Serializable {

  val any: ZServiceBuilder[Has[System], Nothing, Has[System]] = {
    implicit val trace = Tracer.newTrace
    ZServiceBuilder.service[System]
  }

  val live: ServiceBuilder[Nothing, Has[System]] = {
    implicit val trace = Tracer.newTrace
    ZServiceBuilder.succeed(SystemLive)
  }

  object SystemLive extends System {
    def env(variable: => String)(implicit trace: ZTraceElement): IO[SecurityException, Option[String]] =
      IO.attempt(Option(JSystem.getenv(variable))).refineToOrDie[SecurityException]

    def envOrElse(variable: => String, alt: => String)(implicit trace: ZTraceElement): IO[SecurityException, String] =
      envOrElseWith(variable, alt)(env(_))

    def envOrOption(variable: => String, alt: => Option[String])(implicit
      trace: ZTraceElement
    ): IO[SecurityException, Option[String]] =
      envOrOptionWith(variable, alt)(env(_))

    @silent("JavaConverters")
    def envs(implicit trace: ZTraceElement): IO[SecurityException, Map[String, String]] =
      IO.attempt(JSystem.getenv.asScala.toMap).refineToOrDie[SecurityException]

    def lineSeparator(implicit trace: ZTraceElement): UIO[String] =
      IO.succeed(JSystem.lineSeparator)

    @silent("JavaConverters")
    def properties(implicit trace: ZTraceElement): IO[Throwable, Map[String, String]] =
      IO.attempt(JSystem.getProperties.asScala.toMap)

    def property(prop: => String)(implicit trace: ZTraceElement): IO[Throwable, Option[String]] =
      IO.attempt(Option(JSystem.getProperty(prop)))

    def propertyOrElse(prop: => String, alt: => String)(implicit trace: ZTraceElement): IO[Throwable, String] =
      propertyOrElseWith(prop, alt)(property(_))

    def propertyOrOption(prop: => String, alt: => Option[String])(implicit
      trace: ZTraceElement
    ): IO[Throwable, Option[String]] =
      propertyOrOptionWith(prop, alt)(property(_))

  }

  private[zio] def envOrElseWith(variable: => String, alt: => String)(
    env: String => IO[SecurityException, Option[String]]
  )(implicit trace: ZTraceElement): IO[SecurityException, String] =
    env(variable).map(_.getOrElse(alt))

  private[zio] def envOrOptionWith(variable: => String, alt: => Option[String])(
    env: String => IO[SecurityException, Option[String]]
  )(implicit trace: ZTraceElement): IO[SecurityException, Option[String]] =
    env(variable).map(_.orElse(alt))

  private[zio] def propertyOrElseWith(prop: => String, alt: => String)(
    property: String => IO[Throwable, Option[String]]
  )(implicit trace: ZTraceElement): IO[Throwable, String] =
    property(prop).map(_.getOrElse(alt))

  private[zio] def propertyOrOptionWith(prop: => String, alt: => Option[String])(
    property: String => IO[Throwable, Option[String]]
  )(implicit trace: ZTraceElement): IO[Throwable, Option[String]] =
    property(prop).map(_.orElse(alt))

  // Accessor Methods

  /**
   * Retrieves the value of an environment variable.
   */
  def env(variable: => String)(implicit trace: ZTraceElement): ZIO[Has[System], SecurityException, Option[String]] =
    ZIO.accessZIO(_.get.env(variable))

  /**
   * Retrieves the value of an environment variable or else returns the
   * specified fallback value.
   */
  def envOrElse(variable: => String, alt: => String)(implicit
    trace: ZTraceElement
  ): ZIO[Has[System], SecurityException, String] =
    ZIO.accessZIO(_.get.envOrElse(variable, alt))

  /**
   * Retrieves the value of an environment variable or else returns the
   * specified optional fallback value.
   */
  def envOrOption(variable: => String, alt: => Option[String])(implicit
    trace: ZTraceElement
  ): ZIO[Has[System], SecurityException, Option[String]] =
    ZIO.accessZIO(_.get.envOrOption(variable, alt))

  /**
   * Retrieves the values of all environment variables.
   */
  def envs(implicit trace: ZTraceElement): ZIO[Has[System], SecurityException, Map[String, String]] =
    ZIO.accessZIO(_.get.envs)

  /**
   * Retrieves the values of all system properties.
   */
  def properties(implicit trace: ZTraceElement): ZIO[Has[System], Throwable, Map[String, String]] =
    ZIO.accessZIO(_.get.properties)

  /**
   * Retrieves the value of a system property.
   */
  def property(prop: => String)(implicit trace: ZTraceElement): ZIO[Has[System], Throwable, Option[String]] =
    ZIO.accessZIO(_.get.property(prop))

  /**
   * Retrieves the value of a system property or else return the specified
   * fallback value.
   */
  def propertyOrElse(prop: => String, alt: => String)(implicit trace: ZTraceElement): RIO[Has[System], String] =
    ZIO.accessZIO(_.get.propertyOrElse(prop, alt))

  /**
   * Retrieves the value of a system property or else return the specified
   * optional fallback value.
   */
  def propertyOrOption(prop: => String, alt: => Option[String])(implicit
    trace: ZTraceElement
  ): ZIO[Has[System], Throwable, Option[String]] =
    ZIO.accessZIO(_.get.propertyOrOption(prop, alt))

  /**
   * Retrieves the value of the system-specific line separator.
   */
  def lineSeparator(implicit trace: ZTraceElement): URIO[Has[System], String] =
    ZIO.accessZIO(_.get.lineSeparator)

  private val osName =
    Option(scala.util.Try(java.lang.System.getProperty("os.name")).getOrElse("")).map(_.toLowerCase()).getOrElse("")

  lazy val os: OS =
    if (osName.contains("win")) OS.Windows
    else if (osName.contains("mac")) OS.Mac
    else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) OS.Unix
    else if (osName.contains("sunos")) OS.Solaris
    else OS.Unknown

  sealed trait OS { self =>
    def isWindows: Boolean = self == OS.Windows
    def isMac: Boolean     = self == OS.Mac
    def isUnix: Boolean    = self == OS.Unix
    def isSolaris: Boolean = self == OS.Solaris
    def isUnknown: Boolean = self == OS.Unknown
  }
  object OS {
    case object Windows extends OS
    case object Mac     extends OS
    case object Unix    extends OS
    case object Solaris extends OS
    case object Unknown extends OS
  }
}
