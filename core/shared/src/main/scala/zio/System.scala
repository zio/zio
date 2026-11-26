/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.{System => JSystem}
import scala.jdk.CollectionConverters._

/**
 * A service that reads environment variables, system properties and the line
 * separator of the platform. Reading them through `System` instead of
 * `java.lang.System` keeps each read inside an effect, so tests can replace the
 * service with `TestSystem` from ZIO Test, and `ZIO.withSystem` runs a workflow
 * with another implementation.
 *
 * The default implementation is [[System.SystemLive]]. Environment variable
 * operations can fail with a `SecurityException`; system property operations
 * can fail with any `Throwable`, for example a
 * `java.lang.IllegalArgumentException` on the JVM for an empty property name.
 *
 * {{{
 * val logLevel: IO[SecurityException, String] = System.envOrElse("LOG_LEVEL", "INFO")
 * val tmpDir: Task[Option[String]]             = System.property("java.io.tmpdir")
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/services/system/ System]]
 */
trait System extends Serializable { self =>

  /**
   * Reads the environment variable `variable`, or `None` when it is not set.
   *
   * [[System.SystemLive]] fails with a `SecurityException` when the platform
   * throws one, and dies on other exceptions, such as the
   * `java.lang.NullPointerException` that the JVM throws for a `null` name.
   */
  def env(variable: => String)(implicit trace: Trace): IO[SecurityException, Option[String]]

  /**
   * Reads the environment variable `variable`, or returns `alt` when it is not
   * set.
   */
  def envOrElse(variable: => String, alt: => String)(implicit trace: Trace): IO[SecurityException, String]

  /**
   * Reads the environment variable `variable`, or returns `alt` when it is not
   * set.
   */
  def envOrOption(variable: => String, alt: => Option[String])(implicit
    trace: Trace
  ): IO[SecurityException, Option[String]]

  /**
   * Reads all environment variables into a `Map` from name to value.
   */
  def envs(implicit trace: Trace): IO[SecurityException, Map[String, String]]

  /**
   * Returns the line separator of the platform, such as a line feed (`\n`) on
   * Linux.
   */
  def lineSeparator(implicit trace: Trace): UIO[String]

  /**
   * Reads all system properties into a `Map` from name to value.
   * [[System.SystemLive]] copies them when the effect runs, so later changes do
   * not appear in the map.
   */
  def properties(implicit trace: Trace): IO[Throwable, Map[String, String]]

  /**
   * Reads the system property `prop`, or `None` when it is not set.
   *
   * On the JVM, [[System.SystemLive]] fails with a
   * `java.lang.IllegalArgumentException` when `prop` is empty and with a
   * `java.lang.NullPointerException` when it is `null`. On Scala.js, an empty
   * `prop` gives `None`.
   */
  def property(prop: => String)(implicit trace: Trace): IO[Throwable, Option[String]]

  /**
   * Reads the system property `prop`, or returns `alt` when it is not set.
   */
  def propertyOrElse(prop: => String, alt: => String)(implicit trace: Trace): IO[Throwable, String]

  /**
   * Reads the system property `prop`, or returns `alt` when it is not set.
   */
  def propertyOrOption(prop: => String, alt: => Option[String])(implicit
    trace: Trace
  ): IO[Throwable, Option[String]]

  /**
   * Synchronous versions of the `System` operations, for code that runs outside
   * ZIO effects. Each method reads its value as a side effect when it is
   * called, and needs an implicit `Unsafe` value, which `Unsafe.unsafe {
   * implicit unsafe => ... }` provides. Errors are thrown as exceptions instead
   * of being returned in an effect.
   */
  trait UnsafeAPI extends Serializable {

    /**
     * Returns the value of the environment variable `variable`, or `None` when
     * it is not set.
     */
    def env(variable: String)(implicit unsafe: Unsafe): Option[String]

    /**
     * Returns the value of the environment variable `variable`, or `alt` when
     * it is not set.
     */
    def envOrElse(variable: String, alt: => String)(implicit unsafe: Unsafe): String

    /**
     * Returns the value of the environment variable `variable`, or `alt` when
     * it is not set.
     */
    def envOrOption(variable: String, alt: => Option[String])(implicit unsafe: Unsafe): Option[String]

    /**
     * Returns all environment variables as a `Map` from name to value.
     */
    def envs()(implicit unsafe: Unsafe): Map[String, String]

    /**
     * Returns the line separator of the platform.
     */
    def lineSeparator()(implicit unsafe: Unsafe): String

    /**
     * Returns all system properties as a `Map` from name to value.
     */
    def properties()(implicit unsafe: Unsafe): Map[String, String]

    /**
     * Returns the value of the system property `prop`, or `None` when it is not
     * set.
     */
    def property(prop: String)(implicit unsafe: Unsafe): Option[String]

    /**
     * Returns the value of the system property `prop`, or `alt` when it is not
     * set.
     */
    def propertyOrElse(prop: String, alt: => String)(implicit unsafe: Unsafe): String

    /**
     * Returns the value of the system property `prop`, or `alt` when it is not
     * set.
     */
    def propertyOrOption(prop: String, alt: => Option[String])(implicit unsafe: Unsafe): Option[String]
  }

  /**
   * Returns the synchronous `UnsafeAPI` of this service.
   *
   * The default implementation runs each operation of this service as an effect
   * on `Runtime.default` and throws a `zio.FiberFailure` when the effect fails
   * or dies. [[System.SystemLive]] overrides it to read the platform directly,
   * so it throws the original exception, such as the
   * `java.lang.IllegalArgumentException` that the JVM throws for an empty
   * property name.
   */
  def unsafe: UnsafeAPI =
    new UnsafeAPI {
      def env(variable: String)(implicit unsafe: Unsafe): Option[String] =
        Runtime.default.unsafe.run(self.env(variable)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def envOrElse(variable: String, alt: => String)(implicit unsafe: Unsafe): String =
        Runtime.default.unsafe
          .run(self.envOrElse(variable, alt)(Trace.empty))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()

      def envOrOption(variable: String, alt: => Option[String])(implicit
        unsafe: Unsafe
      ): Option[String] =
        Runtime.default.unsafe
          .run(self.envOrOption(variable, alt)(Trace.empty))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()

      def envs()(implicit unsafe: Unsafe): Map[String, String] =
        Runtime.default.unsafe.run(self.envs(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def lineSeparator()(implicit unsafe: Unsafe): String =
        Runtime.default.unsafe.run(self.lineSeparator(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def properties()(implicit unsafe: Unsafe): Map[String, String] =
        Runtime.default.unsafe.run(self.properties(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def property(prop: String)(implicit unsafe: Unsafe): Option[String] =
        Runtime.default.unsafe.run(self.property(prop)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def propertyOrElse(prop: String, alt: => String)(implicit unsafe: Unsafe): String =
        Runtime.default.unsafe
          .run(self.propertyOrElse(prop, alt)(Trace.empty))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()

      def propertyOrOption(prop: String, alt: => Option[String])(implicit
        unsafe: Unsafe
      ): Option[String] =
        Runtime.default.unsafe
          .run(self.propertyOrOption(prop, alt)(Trace.empty))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()
    }
}

/**
 * Accessors that run each `System` operation with the `System` service of the
 * current fiber, the default implementation [[System.SystemLive]], and the
 * operating system detection [[System.os]].
 *
 * @see
 *   [[https://zio.dev/reference/services/system/ System]]
 */
object System extends SystemPlatformSpecific {

  /**
   * The `Tag` of the `System` service, used to look it up in a `ZEnvironment`.
   */
  implicit val tag: Tag[System] = Tag(EnvironmentTag.tagFromTagMacro[System])

  /**
   * The default `System` service, which every ZIO runtime provides unless a
   * workflow installs another one.
   *
   * On the JVM and Scala Native it reads environment variables with
   * `java.lang.System.getenv`. On Scala.js it reads them from the Node.js
   * `process.env` object, and finds none when `process` is not defined. System
   * properties and the line separator come from `java.lang.System` on every
   * platform.
   */
  object SystemLive extends System {

    /**
     * Reads the environment variable `variable`, or `None` when it is not set.
     * Fails with a `SecurityException` when the platform throws one, and dies
     * on other exceptions.
     */
    def env(variable: => String)(implicit trace: Trace): IO[SecurityException, Option[String]] =
      ZIO.attempt(unsafe.env(variable)(Unsafe.unsafe)).refineToOrDie[SecurityException]

    /**
     * Reads the environment variable `variable`, or returns `alt` when it is
     * not set. Fails with a `SecurityException` when the platform throws one,
     * and dies on other exceptions.
     */
    def envOrElse(variable: => String, alt: => String)(implicit trace: Trace): IO[SecurityException, String] =
      ZIO.attempt(unsafe.envOrElse(variable, alt)(Unsafe.unsafe)).refineToOrDie[SecurityException]

    /**
     * Reads the environment variable `variable`, or returns `alt` when it is
     * not set. Fails with a `SecurityException` when the platform throws one,
     * and dies on other exceptions.
     */
    def envOrOption(variable: => String, alt: => Option[String])(implicit
      trace: Trace
    ): IO[SecurityException, Option[String]] =
      ZIO.attempt(unsafe.envOrOption(variable, alt)(Unsafe.unsafe)).refineToOrDie[SecurityException]

    /**
     * Reads all environment variables into a `Map` from name to value. Fails
     * with a `SecurityException` when the platform throws one, and dies on
     * other exceptions.
     */
    def envs(implicit trace: Trace): IO[SecurityException, Map[String, String]] =
      ZIO.attempt(unsafe.envs()(Unsafe.unsafe)).refineToOrDie[SecurityException]

    /**
     * Returns `java.lang.System.lineSeparator`.
     */
    def lineSeparator(implicit trace: Trace): UIO[String] =
      ZIO.succeed(unsafe.lineSeparator()(Unsafe.unsafe))

    /**
     * Reads all system properties into a `Map`, copied when the effect runs.
     * Fails with any exception that reading them throws.
     */
    def properties(implicit trace: Trace): IO[Throwable, Map[String, String]] =
      ZIO.attempt(unsafe.properties()(Unsafe.unsafe))

    /**
     * Reads the system property `prop` with `java.lang.System.getProperty`, or
     * `None` when it is not set. On the JVM, an empty `prop` fails with a
     * `java.lang.IllegalArgumentException` and a `null` one with a
     * `java.lang.NullPointerException`.
     */
    def property(prop: => String)(implicit trace: Trace): IO[Throwable, Option[String]] =
      ZIO.attempt(unsafe.property(prop)(Unsafe.unsafe))

    /**
     * Reads the system property `prop`, or returns `alt` when it is not set. On
     * the JVM, an empty `prop` fails with a
     * `java.lang.IllegalArgumentException` and a `null` one with a
     * `java.lang.NullPointerException`.
     */
    def propertyOrElse(prop: => String, alt: => String)(implicit trace: Trace): IO[Throwable, String] =
      ZIO.attempt(unsafe.propertyOrElse(prop, alt)(Unsafe.unsafe))

    /**
     * Reads the system property `prop`, or returns `alt` when it is not set. On
     * the JVM, an empty `prop` fails with a
     * `java.lang.IllegalArgumentException` and a `null` one with a
     * `java.lang.NullPointerException`.
     */
    def propertyOrOption(prop: => String, alt: => Option[String])(implicit
      trace: Trace
    ): IO[Throwable, Option[String]] =
      ZIO.attempt(unsafe.propertyOrOption(prop, alt)(Unsafe.unsafe))

    /**
     * Reads the environment and `java.lang.System` directly, without running an
     * effect.
     */
    override val unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def env(variable: String)(implicit unsafe: Unsafe): Option[String] =
          environmentProvider.env(variable)

        override def envOrElse(variable: String, alt: => String)(implicit unsafe: Unsafe): String =
          envOrElseWith(variable, alt)(env)

        override def envOrOption(variable: String, alt: => Option[String])(implicit unsafe: Unsafe): Option[String] =
          envOrOptionWith(variable, alt)(env)

        override def envs()(implicit unsafe: Unsafe): Map[String, String] =
          environmentProvider.envs

        override def lineSeparator()(implicit unsafe: Unsafe): String =
          JSystem.lineSeparator

        override def properties()(implicit unsafe: Unsafe): Map[String, String] =
          JSystem.getProperties.asScala.toMap

        override def property(prop: String)(implicit unsafe: Unsafe): Option[String] =
          Option(JSystem.getProperty(prop))

        override def propertyOrElse(prop: String, alt: => String)(implicit unsafe: Unsafe): String =
          propertyOrElseWith(prop, alt)(property)

        override def propertyOrOption(prop: String, alt: => Option[String])(implicit
          unsafe: Unsafe
        ): Option[String] =
          propertyOrOptionWith(prop, alt)(property)
      }
  }

  /**
   * Reads environment variables for [[System.SystemLive]]. Each platform
   * defines its own instance: `java.lang.System.getenv` on the JVM and Scala
   * Native, and `process.env` on Scala.js.
   */
  private[zio] trait EnvironmentProvider {

    /**
     * Returns the value of the environment variable `variable`, or `None` when
     * it is not set.
     */
    def env(variable: String): Option[String]

    /**
     * Returns all environment variables as a `Map` from name to value.
     */
    def envs: Map[String, String]
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
   * Retrieves the value of a system property or else returns the specified
   * fallback value.
   */
  def propertyOrElse(prop: => String, alt: => String)(implicit trace: Trace): Task[String] =
    ZIO.systemWith(_.propertyOrElse(prop, alt))

  /**
   * Retrieves the value of a system property or else returns the specified
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

  /**
   * The operating system that the program runs on, detected from the `os.name`
   * system property. The property is read once, when the `System` object is
   * initialized, and compared in lower case, in this order: a name containing
   * `win` gives `OS.Windows`, `mac` gives `OS.Mac`, `nix`, `nux` or `aix` gives
   * `OS.Unix`, and `sunos` gives `OS.Solaris`. Any other name, such as
   * `FreeBSD`, or a missing property gives `OS.Unknown`. On Scala.js, where
   * `os.name` is not set, it is `OS.Unknown`.
   */
  lazy val os: OS =
    if (osName.contains("win")) OS.Windows
    else if (osName.contains("mac")) OS.Mac
    else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) OS.Unix
    else if (osName.contains("sunos")) OS.Solaris
    else OS.Unknown

  /**
   * An operating system family, as reported by [[System.os]].
   */
  sealed trait OS { self =>

    /**
     * Returns `true` when this is `OS.Windows`.
     */
    def isWindows: Boolean = self == OS.Windows

    /**
     * Returns `true` when this is `OS.Mac`.
     */
    def isMac: Boolean = self == OS.Mac

    /**
     * Returns `true` when this is `OS.Unix`.
     */
    def isUnix: Boolean = self == OS.Unix

    /**
     * Returns `true` when this is `OS.Solaris`.
     */
    def isSolaris: Boolean = self == OS.Solaris

    /**
     * Returns `true` when this is `OS.Unknown`.
     */
    def isUnknown: Boolean = self == OS.Unknown
  }

  /**
   * The operating system families that [[System.os]] reports.
   */
  object OS {

    /**
     * Windows: an `os.name` that contains `win`.
     */
    case object Windows extends OS

    /**
     * macOS: an `os.name` that contains `mac`, and not `win`.
     */
    case object Mac extends OS

    /**
     * Linux, AIX and other systems whose `os.name` contains `nix`, `nux` or
     * `aix`, and neither `win` nor `mac`.
     */
    case object Unix extends OS

    /**
     * Solaris: an `os.name` that contains `sunos`, and none of the earlier
     * patterns.
     */
    case object Solaris extends OS

    /**
     * Any other `os.name`, such as `FreeBSD`, or no `os.name` at all.
     */
    case object Unknown extends OS
  }
}
