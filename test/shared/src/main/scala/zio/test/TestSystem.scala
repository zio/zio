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

import zio.{IO, Layer, Ref, System, UIO, URIO, ZIO, ZLayer, ZTraceElement}
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.ZTrace

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
  def putEnv(name: String, value: String)(implicit trace: ZTraceElement): UIO[Unit]
  def putProperty(name: String, value: String)(implicit trace: ZTraceElement): UIO[Unit]
  def setLineSeparator(lineSep: String)(implicit trace: ZTraceElement): UIO[Unit]
  def clearEnv(variable: String)(implicit trace: ZTraceElement): UIO[Unit]
  def clearProperty(prop: String)(implicit trace: ZTraceElement): UIO[Unit]
}

object TestSystem extends Serializable {

  final case class Test(systemState: Ref[TestSystem.Data]) extends System with TestSystem {

    /**
     * Returns the specified environment variable if it exists.
     */
    def env(variable: => String)(implicit trace: ZTraceElement): IO[SecurityException, Option[String]] =
      systemState.get.map(_.envs.get(variable))

    /**
     * Returns the specified environment variable if it exists or else the
     * specified fallback value.
     */
    def envOrElse(variable: => String, alt: => String)(implicit trace: ZTraceElement): IO[SecurityException, String] =
      System.envOrElseWith(variable, alt)(env(_))

    /**
     * Returns the specified environment variable if it exists or else the
     * specified optional fallback value.
     */
    def envOrOption(variable: => String, alt: => Option[String])(implicit
      trace: ZTraceElement
    ): IO[SecurityException, Option[String]] =
      System.envOrOptionWith(variable, alt)(env(_))

    def envs(implicit trace: ZTraceElement): ZIO[Any, SecurityException, Map[String, String]] =
      systemState.get.map(_.envs)

    /**
     * Returns the system line separator.
     */
    def lineSeparator(implicit trace: ZTraceElement): UIO[String] =
      systemState.get.map(_.lineSeparator)

    def properties(implicit trace: ZTraceElement): ZIO[Any, Throwable, Map[String, String]] =
      systemState.get.map(_.properties)

    /**
     * Returns the specified system property if it exists.
     */
    def property(prop: => String)(implicit trace: ZTraceElement): IO[Throwable, Option[String]] =
      systemState.get.map(_.properties.get(prop))

    /**
     * Returns the specified system property if it exists or else the specified
     * fallback value.
     */
    def propertyOrElse(prop: => String, alt: => String)(implicit trace: ZTraceElement): IO[Throwable, String] =
      System.propertyOrElseWith(prop, alt)(property(_))

    /**
     * Returns the specified system property if it exists or else the specified
     * optional fallback value.
     */
    def propertyOrOption(prop: => String, alt: => Option[String])(implicit
      trace: ZTraceElement
    ): IO[Throwable, Option[String]] =
      System.propertyOrOptionWith(prop, alt)(property(_))

    /**
     * Adds the specified name and value to the mapping of environment variables
     * maintained by this `TestSystem`.
     */
    def putEnv(name: String, value: String)(implicit trace: ZTraceElement): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs.updated(name, value)))

    /**
     * Adds the specified name and value to the mapping of system properties
     * maintained by this `TestSystem`.
     */
    def putProperty(name: String, value: String)(implicit trace: ZTraceElement): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties.updated(name, value)))

    /**
     * Sets the system line separator maintained by this `TestSystem` to the
     * specified value.
     */
    def setLineSeparator(lineSep: String)(implicit trace: ZTraceElement): UIO[Unit] =
      systemState.update(_.copy(lineSeparator = lineSep))

    /**
     * Clears the mapping of environment variables.
     */
    def clearEnv(variable: String)(implicit trace: ZTraceElement): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs - variable))

    /**
     * Clears the mapping of system properties.
     */
    def clearProperty(prop: String)(implicit trace: ZTraceElement): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties - prop))

    /**
     * Saves the `TestSystem``'s current state in an effect which, when run,
     * will restore the `TestSystem` state to the saved state.
     */
    def save(implicit trace: ZTraceElement): UIO[UIO[Unit]] =
      for {
        systemData <- systemState.get
      } yield systemState.set(systemData)
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
    implicit val trace: ZTraceElement = Tracer.newTrace
    Ref.make(data).map(Test(_)).toLayer
  }

  val any: ZLayer[TestSystem, Nothing, TestSystem] =
    ZLayer.environment[TestSystem](Tracer.newTrace)

  val default: Layer[Nothing, TestSystem] =
    live(DefaultData)

  /**
   * Accesses a `TestSystem` instance in the environment and adds the specified
   * name and value to the mapping of environment variables.
   */
  def putEnv(name: => String, value: => String)(implicit trace: ZTraceElement): URIO[TestSystem, Unit] =
    ZIO.serviceWithZIO(_.putEnv(name, value))

  /**
   * Accesses a `TestSystem` instance in the environment and adds the specified
   * name and value to the mapping of system properties.
   */
  def putProperty(name: => String, value: => String)(implicit trace: ZTraceElement): URIO[TestSystem, Unit] =
    ZIO.serviceWithZIO(_.putProperty(name, value))

  /**
   * Accesses a `TestSystem` instance in the environment and saves the system
   * state in an effect which, when run, will restore the `TestSystem` to the
   * saved state
   */
  def save(implicit trace: ZTraceElement): ZIO[TestSystem, Nothing, UIO[Unit]] =
    ZIO.serviceWithZIO(_.save)

  /**
   * Accesses a `TestSystem` instance in the environment and sets the line
   * separator to the specified value.
   */
  def setLineSeparator(lineSep: => String)(implicit trace: ZTraceElement): URIO[TestSystem, Unit] =
    ZIO.serviceWithZIO(_.setLineSeparator(lineSep))

  /**
   * Accesses a `TestSystem` instance in the environment and clears the mapping
   * of environment variables.
   */
  def clearEnv(variable: => String)(implicit trace: ZTraceElement): URIO[TestSystem, Unit] =
    ZIO.serviceWithZIO(_.clearEnv(variable))

  /**
   * Accesses a `TestSystem` instance in the environment and clears the mapping
   * of system properties.
   */
  def clearProperty(prop: => String)(implicit trace: ZTraceElement): URIO[TestSystem, Unit] =
    ZIO.serviceWithZIO(_.clearProperty(prop))

  /**
   * The state of the `TestSystem`.
   */
  final case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )
}
