/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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
  def env(variable: => String)(implicit trace: Trace): IO[SecurityException, Option[String]]

  def envOrElse(variable: => String, alt: => String)(implicit trace: Trace): IO[SecurityException, String]

  def envOrOption(variable: => String, alt: => Option[String])(implicit
    trace: Trace
  ): IO[SecurityException, Option[String]]

  def envs(implicit trace: Trace): IO[SecurityException, Map[String, String]]

  def lineSeparator(implicit trace: Trace): UIO[String]

  def properties(implicit trace: Trace): IO[Throwable, Map[String, String]]

  def property(prop: => String)(implicit trace: Trace): IO[Throwable, Option[String]]

  def propertyOrElse(prop: => String, alt: => String)(implicit trace: Trace): IO[Throwable, String]

  def propertyOrOption(prop: => String, alt: => Option[String])(implicit
    trace: Trace
  ): IO[Throwable, Option[String]]

  private[zio] def unsafeEnv(variable: String): Option[String] =
    Runtime.default.unsafeRun(env(variable)(Trace.empty))(Trace.empty)

  private[zio] def unsafeEnvOrElse(variable: String, alt: => String): String =
    Runtime.default.unsafeRun(envOrElse(variable, alt)(Trace.empty))(Trace.empty)

  private[zio] def unsafeEnvOrOption(variable: String, alt: => Option[String]): Option[String] =
    Runtime.default.unsafeRun(envOrOption(variable, alt)(Trace.empty))(Trace.empty)

  private[zio] def unsafeEnvs(): Map[String, String] =
    Runtime.default.unsafeRun(envs(Trace.empty))(Trace.empty)

  private[zio] def unsafeLineSeparator(): String =
    Runtime.default.unsafeRun(lineSeparator(Trace.empty))(Trace.empty)

  private[zio] def unsafeProperties(): Map[String, String] =
    Runtime.default.unsafeRun(properties(Trace.empty))(Trace.empty)

  private[zio] def unsafeProperty(prop: String): Option[String] =
    Runtime.default.unsafeRun(property(prop)(Trace.empty))(Trace.empty)

  private[zio] def unsafePropertyOrElse(prop: String, alt: => String): String =
    Runtime.default.unsafeRun(propertyOrElse(prop, alt)(Trace.empty))(Trace.empty)

  private[zio] def unsafePropertyOrOption(prop: String, alt: => Option[String]): Option[String] =
    Runtime.default.unsafeRun(propertyOrOption(prop, alt)(Trace.empty))(Trace.empty)
}

object System extends Serializable {

  val any: ZLayer[System, Nothing, System] = {
    implicit val trace = Tracer.newTrace
    ZLayer.service[System]
  }

  val live: Layer[Nothing, System] = {
    implicit val trace = Tracer.newTrace
    ZLayer.succeed[System](SystemLive)
  }

  object SystemLive extends System {
    def env(variable: => String)(implicit trace: Trace): IO[SecurityException, Option[String]] =
      IO.attempt(unsafeEnv(variable)).refineToOrDie[SecurityException]

    def envOrElse(variable: => String, alt: => String)(implicit trace: Trace): IO[SecurityException, String] =
      IO.attempt(unsafeEnvOrElse(variable, alt)).refineToOrDie[SecurityException]

    def envOrOption(variable: => String, alt: => Option[String])(implicit
      trace: Trace
    ): IO[SecurityException, Option[String]] =
      IO.attempt(unsafeEnvOrOption(variable, alt)).refineToOrDie[SecurityException]

    def envs(implicit trace: Trace): IO[SecurityException, Map[String, String]] =
      IO.attempt(unsafeEnvs()).refineToOrDie[SecurityException]

    def lineSeparator(implicit trace: Trace): UIO[String] =
      IO.succeed(unsafeLineSeparator())

    def properties(implicit trace: Trace): IO[Throwable, Map[String, String]] =
      IO.attempt(unsafeProperties())

    def property(prop: => String)(implicit trace: Trace): IO[Throwable, Option[String]] =
      IO.attempt(unsafeProperty(prop))

    def propertyOrElse(prop: => String, alt: => String)(implicit trace: Trace): IO[Throwable, String] =
      IO.attempt(unsafePropertyOrElse(prop, alt))

    def propertyOrOption(prop: => String, alt: => Option[String])(implicit
      trace: Trace
    ): IO[Throwable, Option[String]] =
      IO.attempt(unsafePropertyOrOption(prop, alt))

    override private[zio] def unsafeEnv(variable: String): Option[String] =
      Option(JSystem.getenv(variable))

    override private[zio] def unsafeEnvOrElse(variable: String, alt: => String): String =
      envOrElseWith(variable, alt)(unsafeEnv)

    override private[zio] def unsafeEnvOrOption(variable: String, alt: => Option[String]): Option[String] =
      envOrOptionWith(variable, alt)(unsafeEnv)

    @silent("JavaConverters")
    override private[zio] def unsafeEnvs(): Map[String, String] =
      JSystem.getenv.asScala.toMap

    override private[zio] def unsafeLineSeparator(): String =
      JSystem.lineSeparator

    @silent("JavaConverters")
    override private[zio] def unsafeProperties(): Map[String, String] =
      JSystem.getProperties.asScala.toMap

    override private[zio] def unsafeProperty(prop: String): Option[String] =
      Option(JSystem.getProperty(prop))

    override private[zio] def unsafePropertyOrElse(prop: String, alt: => String): String =
      propertyOrElseWith(prop, alt)(unsafeProperty)

    override private[zio] def unsafePropertyOrOption(prop: String, alt: => Option[String]): Option[String] =
      propertyOrOptionWith(prop, alt)(unsafeProperty)
  }

  private[zio] def envOrElseWith(variable: String, alt: => String)(env: String => Option[String]): String =
    env(variable).getOrElse(alt)

  private[zio] def envOrOptionWith(variable: String, alt: => Option[String])(
    env: String => Option[String]
  ): Option[String] =
    env(variable).orElse(alt)

  private[zio] def propertyOrElseWith(prop: String, alt: => String)(property: String => Option[String]): String =
    property(prop).getOrElse(alt)

  private[zio] def propertyOrOptionWith(prop: String, alt: => Option[String])(
    property: String => Option[String]
  ): Option[String] =
    property(prop).orElse(alt)

  /**
   * Retrieves the value of an environment variable.
   */
  def env(variable: => String)(implicit trace: Trace): IO[SecurityException, Option[String]] =
    ZIO.systemWith(_.env(variable))

  /**
   * Retrieves the value of an environment variable or else returns the
   * specified fallback value.
   */
  def envOrElse(variable: => String, alt: => String)(implicit
    trace: Trace
  ): IO[SecurityException, String] =
    ZIO.systemWith(_.envOrElse(variable, alt))

  /**
   * Retrieves the value of an environment variable or else returns the
   * specified optional fallback value.
   */
  def envOrOption(variable: => String, alt: => Option[String])(implicit
    trace: Trace
  ): IO[SecurityException, Option[String]] =
    ZIO.systemWith(_.envOrOption(variable, alt))

  /**
   * Retrieves the values of all environment variables.
   */
  def envs(implicit trace: Trace): IO[SecurityException, Map[String, String]] =
    ZIO.systemWith(_.envs)

  /**
   * Retrieves the values of all system properties.
   */
  def properties(implicit trace: Trace): Task[Map[String, String]] =
    ZIO.systemWith(_.properties)

  /**
   * Retrieves the value of a system property.
   */
  def property(prop: => String)(implicit trace: Trace): Task[Option[String]] =
    ZIO.systemWith(_.property(prop))

  /**
   * Retrieves the value of a system property or else return the specified
   * fallback value.
   */
  def propertyOrElse(prop: => String, alt: => String)(implicit trace: Trace): Task[String] =
    ZIO.systemWith(_.propertyOrElse(prop, alt))

  /**
   * Retrieves the value of a system property or else return the specified
   * optional fallback value.
   */
  def propertyOrOption(prop: => String, alt: => Option[String])(implicit
    trace: Trace
  ): Task[Option[String]] =
    ZIO.systemWith(_.propertyOrOption(prop, alt))

  /**
   * Retrieves the value of the system-specific line separator.
   */
  def lineSeparator(implicit trace: Trace): UIO[String] =
    ZIO.systemWith(_.lineSeparator)

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
