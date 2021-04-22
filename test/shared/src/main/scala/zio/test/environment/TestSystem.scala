/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

package zio.test.environment

import zio.{Has, IO, Layer, Ref, System, UIO, URIO, ZIO, ZLayer}

/**
 * `TestSystem` supports deterministic testing of effects involving system
 * properties. Internally, `TestSystem` maintains mappings of environment
 * variables and system properties that can be set and accessed. No actual
 * environment variables or system properties will be accessed or set as a
 * result of these actions.
 *
 * {{{
 * import zio.system
 * import zio.test.environment.TestSystem
 *
 * for {
 *   _      <- TestSystem.putProperty("java.vm.name", "VM")
 *   result <- system.property("java.vm.name")
 * } yield result == Some("VM")
 * }}}
 */
trait TestSystem extends Restorable {
  def putEnv(name: String, value: String): UIO[Unit]
  def putProperty(name: String, value: String): UIO[Unit]
  def setLineSeparator(lineSep: String): UIO[Unit]
  def clearEnv(variable: String): UIO[Unit]
  def clearProperty(prop: String): UIO[Unit]
}

object TestSystem extends Serializable {

  final case class Test(systemState: Ref[TestSystem.Data]) extends System with TestSystem {

    /**
     * Returns the specified environment variable if it exists.
     */
    def env(variable: String): IO[SecurityException, Option[String]] =
      systemState.get.map(_.envs.get(variable))

    /**
     * Returns the specified environment variable if it exists or else the
     * specified fallback value.
     */
    def envOrElse(variable: String, alt: => String): IO[SecurityException, String] =
      System.envOrElseWith(variable, alt)(env)

    /**
     * Returns the specified environment variable if it exists or else the
     * specified optional fallback value.
     */
    def envOrOption(variable: String, alt: => Option[String]): IO[SecurityException, Option[String]] =
      System.envOrOptionWith(variable, alt)(env)

    val envs: ZIO[Any, SecurityException, Map[String, String]] =
      systemState.get.map(_.envs)

    /**
     * Returns the system line separator.
     */
    val lineSeparator: UIO[String] =
      systemState.get.map(_.lineSeparator)

    val properties: ZIO[Any, Throwable, Map[String, String]] =
      systemState.get.map(_.properties)

    /**
     * Returns the specified system property if it exists.
     */
    def property(prop: String): IO[Throwable, Option[String]] =
      systemState.get.map(_.properties.get(prop))

    /**
     * Returns the specified system property if it exists or else the
     * specified fallback value.
     */
    def propertyOrElse(prop: String, alt: => String): IO[Throwable, String] =
      System.propertyOrElseWith(prop, alt)(property)

    /**
     * Returns the specified system property if it exists or else the
     * specified optional fallback value.
     */
    def propertyOrOption(prop: String, alt: => Option[String]): IO[Throwable, Option[String]] =
      System.propertyOrOptionWith(prop, alt)(property)

    /**
     * Adds the specified name and value to the mapping of environment
     * variables maintained by this `TestSystem`.
     */
    def putEnv(name: String, value: String): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs.updated(name, value)))

    /**
     * Adds the specified name and value to the mapping of system properties
     * maintained by this `TestSystem`.
     */
    def putProperty(name: String, value: String): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties.updated(name, value)))

    /**
     * Sets the system line separator maintained by this `TestSystem` to the
     * specified value.
     */
    def setLineSeparator(lineSep: String): UIO[Unit] =
      systemState.update(_.copy(lineSeparator = lineSep))

    /**
     * Clears the mapping of environment variables.
     */
    def clearEnv(variable: String): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs - variable))

    /**
     * Clears the mapping of system properties.
     */
    def clearProperty(prop: String): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties - prop))

    /**
     * Saves the `TestSystem``'s current state in an effect which, when run, will restore the `TestSystem`
     * state to the saved state.
     */
    val save: UIO[UIO[Unit]] =
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
   * Constructs a new `TestSystem` with the specified initial state. This can
   * be useful for providing the required environment to an effect that
   * requires a `Console`, such as with `ZIO#provide`.
   */
  def live(data: Data): Layer[Nothing, Has[System] with Has[TestSystem]] =
    ZLayer.many(
      Ref.make(data).map(ref => Has.allOf[System, TestSystem](Test(ref), Test(ref)))
    )

  val any: ZLayer[Has[System] with Has[TestSystem], Nothing, Has[System] with Has[TestSystem]] =
    ZLayer.requires[Has[System] with Has[TestSystem]]

  val default: Layer[Nothing, Has[System] with Has[TestSystem]] =
    live(DefaultData)

  /**
   * Accesses a `TestSystem` instance in the environment and adds the specified
   * name and value to the mapping of environment variables.
   */
  def putEnv(name: => String, value: => String): URIO[Has[TestSystem], Unit] =
    ZIO.accessM(_.get.putEnv(name, value))

  /**
   * Accesses a `TestSystem` instance in the environment and adds the specified
   * name and value to the mapping of system properties.
   */
  def putProperty(name: => String, value: => String): URIO[Has[TestSystem], Unit] =
    ZIO.accessM(_.get.putProperty(name, value))

  /**
   * Accesses a `TestSystem` instance in the environment and saves the system state in an effect which, when run,
   * will restore the `TestSystem` to the saved state
   */
  val save: ZIO[Has[TestSystem], Nothing, UIO[Unit]] =
    ZIO.accessM(_.get.save)

  /**
   * Accesses a `TestSystem` instance in the environment and sets the line
   * separator to the specified value.
   */
  def setLineSeparator(lineSep: => String): URIO[Has[TestSystem], Unit] =
    ZIO.accessM(_.get.setLineSeparator(lineSep))

  /**
   * Accesses a `TestSystem` instance in the environment and clears the mapping
   * of environment variables.
   */
  def clearEnv(variable: => String): URIO[Has[TestSystem], Unit] =
    ZIO.accessM(_.get.clearEnv(variable))

  /**
   * Accesses a `TestSystem` instance in the environment and clears the mapping
   * of system properties.
   */
  def clearProperty(prop: => String): URIO[Has[TestSystem], Unit] =
    ZIO.accessM(_.get.clearProperty(prop))

  /**
   * The state of the `TestSystem`.
   */
  final case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )
}
