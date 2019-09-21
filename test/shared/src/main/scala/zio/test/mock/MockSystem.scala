/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

import zio.{ Ref, UIO, ZIO }
import zio.system.System

/**
 * `MockSystem` supports deterministic testing of effects involving system
 * properties. Internally, `MockSystem` maintains mappings of environment
 * variables and system properties that can be set and accessed. No actual
 * environment variables or system properties will be accessed or set as a
 * result of these actions.
 *
 * {{{
 * import zio.system
 * import zio.test.mock._
 *
 * for {
 *   _      <- MockSystem.putProperty("java.vm.name", "VM")
 *   result <- system.property("java.vm.name")
 * } yield result == Some("VM")
 * }}}

 */
trait MockSystem extends System {
  val system: MockSystem.Service[Any]
}

object MockSystem {

  trait Service[R] extends System.Service[R] {
    def putEnv(name: String, value: String): UIO[Unit]
    def putProperty(name: String, value: String): UIO[Unit]
    def setLineSeparator(lineSep: String): UIO[Unit]
    def clearEnv(variable: String): UIO[Unit]
    def clearProperty(prop: String): UIO[Unit]
  }

  case class Mock(systemState: Ref[MockSystem.Data]) extends MockSystem.Service[Any] {

    /**
     * Returns the specified environment variable if it exists.
     */
    override def env(variable: String): ZIO[Any, SecurityException, Option[String]] =
      systemState.get.map(_.envs.get(variable))

    /**
     * Returns the specified system property if it exists.
     */
    override def property(prop: String): ZIO[Any, Throwable, Option[String]] =
      systemState.get.map(_.properties.get(prop))

    /**
     * Returns the system line separator.
     */
    override val lineSeparator: ZIO[Any, Nothing, String] =
      systemState.get.map(_.lineSeparator)

    /**
     * Adds the specified name and value to the mapping of environment
     * variables maintained by this `MockSystem`.
     */
    def putEnv(name: String, value: String): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs.updated(name, value))).unit

    /**
     * Adds the specified name and value to the mapping of system properties
     * maintained by this `MockSystem`.
     */
    def putProperty(name: String, value: String): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties.updated(name, value))).unit

    /**
     * Sets the system line separator maintained by this `MockSystem` to the
     * specified value.
     */
    def setLineSeparator(lineSep: String): UIO[Unit] =
      systemState.update(_.copy(lineSeparator = lineSep)).unit

    /**
     * Clears the mapping of environment variables.
     */
    def clearEnv(variable: String): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs - variable)).unit

    /**
     * Clears the mapping of system properties.
     */
    def clearProperty(prop: String): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties - prop)).unit
  }

  /**
   * Constructs a new `MockSystem` with the specified initial state. This can
   * be useful for providing the required environment to an effect that
   * requires a `Console`, such as with [[ZIO!.provide]].
   */
  def make(data: Data): UIO[MockSystem] =
    makeMock(data).map { mock =>
      new MockSystem {
        val system = mock
      }
    }

  /**
   * Constructs a new `Mock` object that implements the `MockSystem` interface.
   * This can be useful for mixing in with implementations of other interfaces.
   */
  def makeMock(data: Data): UIO[Mock] =
    Ref.make(data).map(Mock(_))

  /**
   * Accesses a `MockSystem` instance in the environment and adds the specified
   * name and value to the mapping of environment variables.
   */
  def putEnv(name: String, value: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.putEnv(name, value))

  /**
   * Accesses a `MockSystem` instance in the environment and adds the specified
   * name and value to the mapping of system properties.
   */
  def putProperty(name: String, value: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.putProperty(name, value))

  /**
   * Accesses a `MockSystem` instance in the environment and sets the line
   * separator to the specified value.
   */
  def setLineSeparator(lineSep: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.setLineSeparator(lineSep))

  /**
   * Accesses a `MockSystem` instance in the environment and clears the mapping
   * of environment variables.
   */
  def clearEnv(variable: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.clearEnv(variable))

  /**
   * Accesses a `MockSystem` instance in the environment and clears the mapping
   * of system properties.
   */
  def clearProperty(prop: String): ZIO[MockSystem, Nothing, Unit] =
    ZIO.accessM(_.system.clearProperty(prop))

  /**
   * The default initial state of the `MockSystem` with no environment variable
   * or system property mappings and the system line separator set to the new
   * line character.
   */
  val DefaultData: Data = Data(Map(), Map(), "\n")

  /**
   * The state of the `MockSystem`.
   */
  case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )
}
