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

package zio.test.environment

import zio.{ Has, Ref, UIO, ZIO, ZLayer }
import zio.system.System

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
object TestSystem extends Serializable {

  trait Service extends Restorable {
    def putEnv(name: String, value: String): UIO[Unit]
    def putProperty(name: String, value: String): UIO[Unit]
    def setLineSeparator(lineSep: String): UIO[Unit]
    def clearEnv(variable: String): UIO[Unit]
    def clearProperty(prop: String): UIO[Unit]
  }

  case class Test(systemState: Ref[TestSystem.Data]) extends System.Service with TestSystem.Service {

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
     * variables maintained by this `TestSystem`.
     */
    def putEnv(name: String, value: String): UIO[Unit] =
      systemState.update(data => data.copy(envs = data.envs.updated(name, value))).unit

    /**
     * Adds the specified name and value to the mapping of system properties
     * maintained by this `TestSystem`.
     */
    def putProperty(name: String, value: String): UIO[Unit] =
      systemState.update(data => data.copy(properties = data.properties.updated(name, value))).unit

    /**
     * Sets the system line separator maintained by this `TestSystem` to the
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

    /**
     * Saves the `TestSystem``'s current state in an effect which, when run, will restore the `TestSystem`
     * state to the saved state.
     */
    val save: UIO[UIO[Unit]] =
      for {
        sState <- systemState.get
      } yield systemState.set(sState)
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
   * requires a `Console`, such as with [[ZIO!.provide]].
   */
  def live(data: Data): ZLayer.NoDeps[Nothing, System with TestSystem] =
    ZLayer.fromEffect(Ref.make(data).map(ref => Has.allOf[System.Service, TestSystem.Service](Test(ref), Test(ref))))

  val default: ZLayer.NoDeps[Nothing, System with TestSystem] =
    live(DefaultData)

  /**
   * Accesses a `TestSystem` instance in the environment and adds the specified
   * name and value to the mapping of environment variables.
   */
  def putEnv(name: String, value: String): ZIO[TestSystem, Nothing, Unit] =
    ZIO.accessM(_.get.putEnv(name, value))

  /**
   * Accesses a `TestSystem` instance in the environment and adds the specified
   * name and value to the mapping of system properties.
   */
  def putProperty(name: String, value: String): ZIO[TestSystem, Nothing, Unit] =
    ZIO.accessM(_.get.putProperty(name, value))

  /**
   * Accesses a `TestSystem` instance in the environment and saves the system state in an effect which, when run,
   * will restore the `TestSystem` to the saved state
   */
  val save: ZIO[TestSystem, Nothing, UIO[Unit]] = ZIO.accessM[TestSystem](_.get.save)

  /**
   * Accesses a `TestSystem` instance in the environment and sets the line
   * separator to the specified value.
   */
  def setLineSeparator(lineSep: String): ZIO[TestSystem, Nothing, Unit] =
    ZIO.accessM(_.get.setLineSeparator(lineSep))

  /**
   * Accesses a `TestSystem` instance in the environment and clears the mapping
   * of environment variables.
   */
  def clearEnv(variable: String): ZIO[TestSystem, Nothing, Unit] =
    ZIO.accessM(_.get.clearEnv(variable))

  /**
   * Accesses a `TestSystem` instance in the environment and clears the mapping
   * of system properties.
   */
  def clearProperty(prop: String): ZIO[TestSystem, Nothing, Unit] =
    ZIO.accessM(_.get.clearProperty(prop))

  /**
   * The state of the `TestSystem`.
   */
  case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )
}
