/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import zio.{IO, Layer, Ref, System, UIO, URIO, ZEnv, ZIO, ZLayer, Trace}
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * `TestSystem` supports deterministic testing of effects involving system
 * properties. Internally, `TestSystem` maintains mappings of environment
 * variables and system properties that can be set and accessed. No actual
 * environment variables or system properties will be accessed or set as a
 * result of these actions.
 *
 * {{{
 * import zio.system
 * import zio.test.TestSystem
 *
 * for {
 *   _      <- TestSystem.putProperty("java.vm.name", "VM")
 *   result <- system.property("java.vm.name")
 * } yield result == Some("VM")
 * }}}
 */
trait TestSystem extends System with Restorable {
  def putEnv(name: String, value: String)(implicit trace: Trace): UIO[Unit]
  def putProperty(name: String, value: String)(implicit trace: Trace): UIO[Unit]
  def setLineSeparator(lineSep: String)(implicit trace: Trace): UIO[Unit]
  def clearEnv(variable: String)(implicit trace: Trace): UIO[Unit]
  def clearProperty(prop: String)(implicit trace: Trace): UIO[Unit]
}

object TestSystem extends Serializable {

  final case class Test(systemState: Ref.Atomic[TestSystem.Data]) extends System with TestSystem {

    /**
     * Returns the specified environment variable if it exists.
     */
    def env(variable: => String)(implicit trace: Trace): IO[SecurityException, Option[String]] =
      ZIO.succeed(unsafeEnv(variable))

    /**
     * Returns the specified environment variable if it exists or else the
     * specified fallback value.
     */
    def envOrElse(variable: => String, alt: => String)(implicit trace: Trace): IO[SecurityException, String] =
      ZIO.succeed(unsafeEnvOrElse(variable, alt))

    /**
     * Returns the specified environment variable if it exists or else the
     * specified optional fallback value.
     */
    def envOrOption(variable: => String, alt: => Option[String])(implicit
      trace: Trace
    ): IO[SecurityException, Option[String]] =
      ZIO.succeed(unsafeEnvOrOption(variable, alt))

    def envs(implicit trace: Trace): ZIO[Any, SecurityException, Map[String, String]] =
      ZIO.succeed(unsafeEnvs())

    /**
     * Returns the system line separator.
     */
    def lineSeparator(implicit trace: Trace): UIO[String] =
      ZIO.succeed(unsafeLineSeparator())

    def properties(implicit trace: Trace): ZIO[Any, Throwable, Map[String, String]] =
      ZIO.succeed(unsafeProperties())

    /**
     * Returns the specified system property if it exists.
     */
    def property(prop: => String)(implicit trace: Trace): IO[Throwable, Option[String]] =
      ZIO.succeed(unsafeProperty(prop))

    /**
     * Returns the specified system property if it exists or else the specified
     * fallback value.
     */
    def propertyOrElse(prop: => String, alt: => String)(implicit trace: Trace): IO[Throwable, String] =
      ZIO.succeed(unsafePropertyOrElse(prop, alt))

    /**
     * Returns the specified system property if it exists or else the specified
     * optional fallback value.
     */
    def propertyOrOption(prop: => String, alt: => Option[String])(implicit
      trace: Trace
    ): IO[Throwable, Option[String]] =
      ZIO.succeed(unsafePropertyOrOption(prop, alt))

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
     * Clears the mapping of environment variables.
     */
    def clearEnv(variable: String)(implicit trace: Trace): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs - variable))

    /**
     * Clears the mapping of system properties.
     */
    def clearProperty(prop: String)(implicit trace: Trace): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties - prop))

    /**
     * Saves the `TestSystem``'s current state in an effect which, when run,
     * will restore the `TestSystem` state to the saved state.
     */
    def save(implicit trace: Trace): UIO[UIO[Unit]] =
      for {
        systemData <- systemState.get
      } yield systemState.set(systemData)

    override private[zio] def unsafeEnv(variable: String): Option[String] =
      systemState.unsafeGet.envs.get(variable)

    override private[zio] def unsafeEnvOrElse(variable: String, alt: => String): String =
      System.envOrElseWith(variable, alt)(unsafeEnv)

    override private[zio] def unsafeEnvOrOption(variable: String, alt: => Option[String]): Option[String] =
      System.envOrOptionWith(variable, alt)(unsafeEnv)

    override private[zio] def unsafeEnvs(): Map[String, String] =
      systemState.unsafeGet.envs

    override private[zio] def unsafeLineSeparator(): String =
      systemState.unsafeGet.lineSeparator

    override private[zio] def unsafeProperties(): Map[String, String] =
      systemState.unsafeGet.properties

    override private[zio] def unsafeProperty(prop: String): Option[String] =
      systemState.unsafeGet.properties.get(prop)

    override private[zio] def unsafePropertyOrElse(prop: String, alt: => String): String =
      System.propertyOrElseWith(prop, alt)(unsafeProperty)

    override private[zio] def unsafePropertyOrOption(prop: String, alt: => Option[String]): Option[String] =
      System.propertyOrOptionWith(prop, alt)(unsafeProperty)
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
   * `Console`, such as with `ZIO#provide`.
   */
  def live(data: Data): Layer[Nothing, TestSystem] = {
    implicit val trace: Trace = Tracer.newTrace
    ZLayer.scoped {
      for {
        ref <- ZIO.succeed(Ref.unsafeMake(data))
        test = Test(ref)
        _   <- ZEnv.services.locallyScopedWith(_.add(test))
      } yield test
    }
  }

  val any: ZLayer[TestSystem, Nothing, TestSystem] =
    ZLayer.environment[TestSystem](Tracer.newTrace)

  val default: Layer[Nothing, TestSystem] =
    live(DefaultData)

  /**
   * Accesses a `TestSystem` instance in the environment and adds the specified
   * name and value to the mapping of environment variables.
   */
  def putEnv(name: => String, value: => String)(implicit trace: Trace): UIO[Unit] =
    testSystemWith(_.putEnv(name, value))

  /**
   * Accesses a `TestSystem` instance in the environment and adds the specified
   * name and value to the mapping of system properties.
   */
  def putProperty(name: => String, value: => String)(implicit trace: Trace): UIO[Unit] =
    testSystemWith(_.putProperty(name, value))

  /**
   * Accesses a `TestSystem` instance in the environment and saves the system
   * state in an effect which, when run, will restore the `TestSystem` to the
   * saved state
   */
  def save(implicit trace: Trace): UIO[UIO[Unit]] =
    testSystemWith(_.save)

  /**
   * Accesses a `TestSystem` instance in the environment and sets the line
   * separator to the specified value.
   */
  def setLineSeparator(lineSep: => String)(implicit trace: Trace): UIO[Unit] =
    testSystemWith(_.setLineSeparator(lineSep))

  /**
   * Accesses a `TestSystem` instance in the environment and clears the mapping
   * of environment variables.
   */
  def clearEnv(variable: => String)(implicit trace: Trace): UIO[Unit] =
    testSystemWith(_.clearEnv(variable))

  /**
   * Accesses a `TestSystem` instance in the environment and clears the mapping
   * of system properties.
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
