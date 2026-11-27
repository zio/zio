/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.{EnvironmentTag, IO, Layer, Ref, System, Tag, Trace, UIO, URIO, Unsafe, ZIO, ZLayer}
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * `TestSystem` lets a test check effects that read environment variables and
 * system properties. Internally, `TestSystem` maintains mappings of environment
 * variables and system properties that can be set and accessed. No actual
 * environment variables or system properties will be accessed or set as a
 * result of these actions.
 *
 * ZIO Test gives each test its own `TestSystem`, created from
 * [[TestSystem.DefaultData]], so every test starts with no environment
 * variables, no system properties and `"\n"` as the line separator. The
 * accessors on the companion object use the `TestSystem` of the current fiber;
 * outside a test, where `System` is the live service, they die with a
 * `java.lang.Error` saying that `TestSystem` could not be found.
 *
 * {{{
 * import zio._
 * import zio.test.{test, _}
 *
 * test("reads a property set by the test") {
 *   for {
 *     _      <- TestSystem.putProperty("java.vm.name", "VM")
 *     result <- System.property("java.vm.name")
 *   } yield assertTrue(result == Some("VM"))
 * }
 *
 * test("reads an environment variable set by the test") {
 *   for {
 *     _      <- TestSystem.putEnv("APP_PORT", "8080")
 *     port   <- System.env("APP_PORT")
 *     _      <- TestSystem.clearEnv("APP_PORT")
 *     absent <- System.env("APP_PORT")
 *   } yield assertTrue(port == Some("8080"), absent.isEmpty)
 * }
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/services/system/ TestSystem]]
 * @see
 *   [[https://zio.dev/reference/test/aspects/restoring-state-of-test-services/ Restoring State of Test Services]]
 */
trait TestSystem extends System with Restorable {

  /**
   * Adds the specified name and value to the mapping of environment variables,
   * replacing any value already mapped to that name.
   */
  def putEnv(name: String, value: String)(implicit trace: Trace): UIO[Unit]

  /**
   * Adds the specified name and value to the mapping of system properties,
   * replacing any value already mapped to that name.
   */
  def putProperty(name: String, value: String)(implicit trace: Trace): UIO[Unit]

  /**
   * Sets the line separator that `lineSeparator` returns.
   */
  def setLineSeparator(lineSep: String)(implicit trace: Trace): UIO[Unit]

  /**
   * Removes the specified environment variable from the mapping of environment
   * variables. Other variables are kept.
   */
  def clearEnv(variable: String)(implicit trace: Trace): UIO[Unit]

  /**
   * Removes the specified property from the mapping of system properties. Other
   * properties are kept.
   */
  def clearProperty(prop: String)(implicit trace: Trace): UIO[Unit]
}

/**
 * Constructors, layers and accessors for [[TestSystem]]. The accessors such as
 * `putEnv` and `putProperty` use the `TestSystem` of the current fiber, which
 * ZIO Test installs for each test.
 */
object TestSystem extends Serializable {

  /**
   * The `Tag` that identifies the `TestSystem` service in a `ZEnvironment`.
   */
  implicit val tag: Tag[TestSystem] = Tag(EnvironmentTag.tagFromTagMacro[TestSystem])

  private[test] object unsafe {
    def make(data: Data)(implicit unsafe: Unsafe): TestSystem =
      Test(Ref.unsafe.make(data))
  }

  /**
   * The implementation of [[TestSystem]], which keeps the environment
   * variables, system properties and line separator in `systemState`.
   */
  final case class Test(systemState: Ref.Atomic[TestSystem.Data]) extends TestSystem {

    /**
     * Returns the specified environment variable if it exists.
     */
    def env(variable: => String)(implicit trace: Trace): IO[SecurityException, Option[String]] =
      ZIO.succeed(unsafe.env(variable)(Unsafe.unsafe))

    /**
     * Returns the specified environment variable if it exists or else the
     * specified fallback value.
     */
    def envOrElse(variable: => String, alt: => String)(implicit trace: Trace): IO[SecurityException, String] =
      ZIO.succeed(unsafe.envOrElse(variable, alt)(Unsafe.unsafe))

    /**
     * Returns the specified environment variable if it exists or else the
     * specified optional fallback value.
     */
    def envOrOption(variable: => String, alt: => Option[String])(implicit
      trace: Trace
    ): IO[SecurityException, Option[String]] =
      ZIO.succeed(unsafe.envOrOption(variable, alt)(Unsafe.unsafe))

    /**
     * Returns the mapping of environment variables maintained by this
     * `TestSystem`.
     */
    def envs(implicit trace: Trace): ZIO[Any, SecurityException, Map[String, String]] =
      ZIO.succeed(unsafe.envs()(Unsafe.unsafe))

    /**
     * Returns the line separator maintained by this `TestSystem` (`"\n"` in the
     * test environment unless `setLineSeparator` changed it).
     */
    def lineSeparator(implicit trace: Trace): UIO[String] =
      ZIO.succeed(unsafe.lineSeparator()(Unsafe.unsafe))

    /**
     * Returns the mapping of system properties maintained by this `TestSystem`.
     */
    def properties(implicit trace: Trace): ZIO[Any, Throwable, Map[String, String]] =
      ZIO.succeed(unsafe.properties()(Unsafe.unsafe))

    /**
     * Returns the specified system property if it exists.
     */
    def property(prop: => String)(implicit trace: Trace): IO[Throwable, Option[String]] =
      ZIO.succeed(unsafe.property(prop)(Unsafe.unsafe))

    /**
     * Returns the specified system property if it exists or else the specified
     * fallback value.
     */
    def propertyOrElse(prop: => String, alt: => String)(implicit trace: Trace): IO[Throwable, String] =
      ZIO.succeed(unsafe.propertyOrElse(prop, alt)(Unsafe.unsafe))

    /**
     * Returns the specified system property if it exists or else the specified
     * optional fallback value.
     */
    def propertyOrOption(prop: => String, alt: => Option[String])(implicit
      trace: Trace
    ): IO[Throwable, Option[String]] =
      ZIO.succeed(unsafe.propertyOrOption(prop, alt)(Unsafe.unsafe))

    /**
     * Adds the specified name and value to the mapping of environment variables
     * maintained by this `TestSystem`.
     */
    def putEnv(name: String, value: String)(implicit trace: Trace): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs.updated(name, value)))

    /**
     * Adds the specified name and value to the mapping of system properties
     * maintained by this `TestSystem`.
     */
    def putProperty(name: String, value: String)(implicit trace: Trace): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties.updated(name, value)))

    /**
     * Sets the system line separator maintained by this `TestSystem` to the
     * specified value.
     */
    def setLineSeparator(lineSep: String)(implicit trace: Trace): UIO[Unit] =
      systemState.update(_.copy(lineSeparator = lineSep))

    /**
     * Removes the specified environment variable from the mapping of
     * environment variables. Other variables are kept.
     */
    def clearEnv(variable: String)(implicit trace: Trace): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs - variable))

    /**
     * Removes the specified property from the mapping of system properties.
     * Other properties are kept.
     */
    def clearProperty(prop: String)(implicit trace: Trace): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties - prop))

    /**
     * Saves the `TestSystem`'s current state in an effect which, when run, will
     * restore the `TestSystem` state to the saved state. The saved state holds
     * the environment variables, the system properties and the line separator.
     */
    def save(implicit trace: Trace): UIO[UIO[Unit]] =
      for {
        systemData <- systemState.get
      } yield systemState.set(systemData)

    override val unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def env(variable: String)(implicit unsafe: Unsafe): Option[String] =
          systemState.unsafe.get.envs.get(variable)

        override def envOrElse(variable: String, alt: => String)(implicit unsafe: Unsafe): String =
          System.envOrElseWith(variable, alt)(env)

        override def envOrOption(variable: String, alt: => Option[String])(implicit unsafe: Unsafe): Option[String] =
          System.envOrOptionWith(variable, alt)(env)

        override def envs()(implicit unsafe: Unsafe): Map[String, String] =
          systemState.unsafe.get.envs

        override def lineSeparator()(implicit unsafe: Unsafe): String =
          systemState.unsafe.get.lineSeparator

        override def properties()(implicit unsafe: Unsafe): Map[String, String] =
          systemState.unsafe.get.properties

        override def property(prop: String)(implicit unsafe: Unsafe): Option[String] =
          systemState.unsafe.get.properties.get(prop)

        override def propertyOrElse(prop: String, alt: => String)(implicit unsafe: Unsafe): String =
          System.propertyOrElseWith(prop, alt)(property)

        override def propertyOrOption(prop: String, alt: => Option[String])(implicit
          unsafe: Unsafe
        ): Option[String] =
          System.propertyOrOptionWith(prop, alt)(property)
      }
  }

  /**
   * The default initial state of the `TestSystem` with no environment variable
   * or system property mappings and the system line separator set to the new
   * line character.
   */
  val DefaultData: Data = Data(Map(), Map(), "\n")

  /**
   * Constructs a new `TestSystem` with the specified initial state. This can be
   * useful for providing the required environment to an effect that requires a
   * `System`, such as with `ZIO#provideLayer`. While the layer is in scope,
   * `System` methods and the `TestSystem` accessors use the new service.
   */
  def live(data: Data): Layer[Nothing, TestSystem] = {
    implicit val trace: Trace = Tracer.newTrace
    ZLayer.scoped {
      val test = unsafe.make(data)(Unsafe)
      ZIO.withSystemScoped(test).as(test)
    }
  }

  /**
   * A layer that passes on the `TestSystem` already in its input environment.
   */
  val any: ZLayer[TestSystem, Nothing, TestSystem] =
    ZLayer.environment[TestSystem](Tracer.newTrace)

  /**
   * A layer that constructs a `TestSystem` from [[TestSystem.DefaultData]].
   */
  val default: Layer[Nothing, TestSystem] =
    live(DefaultData)

  /**
   * Accesses the current `TestSystem` and adds the specified name and value to
   * the mapping of environment variables.
   */
  def putEnv(name: => String, value: => String)(implicit trace: Trace): UIO[Unit] =
    testSystemWith(_.putEnv(name, value))

  /**
   * Accesses the current `TestSystem` and adds the specified name and value to
   * the mapping of system properties.
   */
  def putProperty(name: => String, value: => String)(implicit trace: Trace): UIO[Unit] =
    testSystemWith(_.putProperty(name, value))

  /**
   * Accesses the current `TestSystem` and saves the system state in an effect
   * which, when run, will restore the `TestSystem` to the saved state.
   */
  def save(implicit trace: Trace): UIO[UIO[Unit]] =
    testSystemWith(_.save)

  /**
   * Accesses the current `TestSystem` and sets the line separator to the
   * specified value.
   */
  def setLineSeparator(lineSep: => String)(implicit trace: Trace): UIO[Unit] =
    testSystemWith(_.setLineSeparator(lineSep))

  /**
   * Accesses the current `TestSystem` and removes the specified environment
   * variable from its mapping of environment variables.
   */
  def clearEnv(variable: => String)(implicit trace: Trace): UIO[Unit] =
    testSystemWith(_.clearEnv(variable))

  /**
   * Accesses the current `TestSystem` and removes the specified property from
   * its mapping of system properties.
   */
  def clearProperty(prop: => String)(implicit trace: Trace): UIO[Unit] =
    testSystemWith(_.clearProperty(prop))

  /**
   * The state of the `TestSystem`.
   */
  final case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )
}
