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

/**
 * The trait `Has[A]` is used to express an effect's dependency on an `A`
 * module. For example, `RIO[Has[ConsoleService], Unit]` is an effect that
 * requires a `ConsoleService` implementation.
 *
 * All modules in an environment must be monomorphic. Parameterized modules
 * are not supported.
 */
final class Has[+A] private (private val map: Map[Tagged[_], scala.Any])
object Has {
  implicit class HasSyntax[Self <: Has[_]](val self: Self) extends AnyVal {

    /**
     * Adds a module to the environment. The module must be monomorphic rather
     * than parameterized. Parameterized modules are not supported.
     *
     * Good: `Logging`, bad: `Logging[String]`.
     */
    final def +[B](b: B)(implicit tag: Tagged[B]): Self with Has[B] =
      (new Has(self.map + (tag -> b))).asInstanceOf[Self with Has[B]]

    final def add[B](b: B)(implicit tag: Tagged[B]): Self with Has[B] =
      this + (b)

    /**
     * Combines this environment with the specified environment. In the event
     * of module collisions, the right side wins.
     */
    final def ++[B <: Has[_]](that: B): Self with B =
      (new Has(self.map ++ that.map)).asInstanceOf[Self with B]

    /**
     * Retrieves a module from the environment.
     */
    final def get[B](implicit ev: Self <:< Has[B], tag: Tagged[B]): B =
      self.map(tag).asInstanceOf[B]

    /**
     * Updates a module in the environment.
     */
    final def update[B: Tagged](f: B => B)(implicit ev: Self <:< Has[B]): Self =
      self + f(get[B])
  }

  type Any = Has[scala.Any]

  /**
   * Constructs a new `Env` holding the single module. The module must be
   * monomorphic. Parameterized modules are not supported.
   */
  def apply[A: Tagged](a: A): Has[A] = any + a

  /**
   * Constructs a new `Env` holding the specified modules. The module must be
   * monomorphic. Parameterized modules are not supported.
   */
  def apply[A: Tagged, B: Tagged](a: A, b: B): Has[A] with Has[B] = any + a + b

  /**
   * Constructs a new `Env` holding the specified modules. The module must be
   * monomorphic. Parameterized modules are not supported.
   */
  def apply[A: Tagged, B: Tagged, C: Tagged](a: A, b: B, c: C): Has[A] with Has[B] with Has[C] = any + a + b + c

  /**
   * Constructs an empty environment that cannot provide anything.
   */
  def any: Any = new Has(Map())

  /**
   * Modifies an environment in a scoped way.
   *
   * {{
   * Env.scoped[Logging](decorateLogger(_)) { effect }
   * }}
   */
  def scoped[A: Tagged](f: A => A): Scoped[A] = new Scoped(f)

  class Scoped[M: Tagged](f: M => M) {
    def apply[R <: Has[M], E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
      ZIO.environment[R].flatMap(env => zio.provide(env.update(f)))
  }
}
