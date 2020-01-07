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

import com.github.ghik.silencer.silent

/**
 * The trait `Has[A]` is used with ZIO environment to express an effect's
 * dependency on an `A` module. For example, `RIO[Has[ConsoleService], Unit]`
 * is an effect that requires a `ConsoleService` implementation. Inside the ZIO
 * library, type aliases are created as shorthands, e.g.:
 *
 * {{{
 * type Console = Has[ConsoleService]
 * }}}
 *
 * All modules in an environment must be monomorphic. Parameterized modules
 * are not supported.
 */
final class Has[+A] private (private val map: Map[Tagged[_], scala.Any], var cache: Map[Tagged[_], scala.Any] = Map())
    extends Serializable {
  override def equals(that: Any): Boolean = that match {
    case that: Has[_] => map == that.map
  }

  override def hashCode: Int = map.hashCode

  override def toString: String = map.mkString("Map(", ",\n", ")")

  def size: Int = (map.size - 1) // Subtract the AnyRef
}
object Has {
  type MustHave[A, B]    = A <:< Has[B]
  type MustNotHave[A, B] = NotExtends[A, Has[B]]

  @silent("define classes/objects inside of package objects")
  trait NotExtends[A, B] extends Serializable

  object NotExtends {
    implicit def notExtends0[A, B]: A NotExtends B      = new NotExtends[A, B] {}
    implicit def notExtends1[A <: B, B]: A NotExtends B = ???
    @annotation.implicitAmbiguous(
      "Due to covariance, the environment ${A} already contains ${B}. Use Has#update to update an existing module."
    )
    implicit def notExtends2[A <: B, B]: A NotExtends B = ???
  }

  trait IsHas[-R] {
    def add[R0 <: R, M: Tagged](r: R0, m: M): R0 with Has[M]
    def union[R0 <: R, R1 <: Has[_]](r: R0, r1: R1): R0 with R1
    def update[R0 <: R, M: Tagged](r: R0, f: M => M)(implicit ev: R0 <:< Has[M]): R0
  }
  object IsHas {
    implicit def ImplicitIs[R <: Has[_]]: IsHas[R] =
      new IsHas[R] {
        def add[R0 <: R, M: Tagged](r: R0, m: M): R0 with Has[M]                         = r.add(m)
        def union[R0 <: R, R1 <: Has[_]](r: R0, r1: R1): R0 with R1                      = r.union[R1](r1)
        def update[R0 <: R, M: Tagged](r: R0, f: M => M)(implicit ev: R0 <:< Has[M]): R0 = r.update(f)
      }
  }

  implicit final class HasSyntax[Self <: Has[_]](val self: Self) extends AnyVal {
    def +[B](b: B)(implicit tag: Tagged[B]): Self with Has[B] = self add b

    def ++[B <: Has[_]](that: B): Self with B = self union [B] that

    /**
     * Adds a module to the environment. The module must be monomorphic rather
     * than parameterized. Parameterized modules are not supported.
     *
     * Good: `Logging`, bad: `Logging[String]`.
     */
    def add[B](b: B)(implicit tag: Tagged[B], ev: Self MustNotHave B): Self with Has[B] =
      (new Has(self.map + (tag -> b))).asInstanceOf[Self with Has[B]]

    /**
     * Retrieves a module from the environment.
     */
    def get[B](implicit ev: Self MustHave B, tag: Tagged[B]): B =
      self.map
        .getOrElse(
          tag,
          self.cache.getOrElse(
            tag, {
              self.map.collectFirst {
                case (curTag, value) if taggedIsSubtype(curTag, tag) =>
                  self.cache = self.cache + (curTag -> value)
                  value
              }.getOrElse(throw new Error("There's probably a bug in Has!"))
            }
          )
        )
        .asInstanceOf[B]

    /**
     * Combines this environment with the specified environment. In the event
     * of module collisions, the right side wins.
     */
    def union[B <: Has[_]](that: B): Self with B =
      (new Has(self.map ++ that.map)).asInstanceOf[Self with B]

    /**
     * Updates a module in the environment.
     */
    def update[B: Tagged](f: B => B)(implicit ev: Self MustHave B): Self =
      self.add(f(get[B]))
  }

  type Any = Has[scala.Any]

  /**
   * Constructs a new environment holding the single module. The module
   * must be monomorphic. Parameterized modules are not supported.
   */
  def apply[A: Tagged](a: A): Has[A] = any.add(a)

  /**
   * Constructs a new environment holding the specified modules. The module
   * must be monomorphic. Parameterized modules are not supported.
   */
  def allOf[A: Tagged, B: Tagged](a: A, b: B): Has[A] with Has[B] = any.add(a).add(b)

  /**
   * Constructs a new environment holding the specified modules. The module
   * must be monomorphic. Parameterized modules are not supported.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged](a: A, b: B, c: C): Has[A] with Has[B] with Has[C] =
    any.add(a).add(b).add(c)

  /**
   * Constructs a new environment holding the specified modules. The module
   * must be monomorphic. Parameterized modules are not supported.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged](
    a: A,
    b: B,
    c: C,
    d: D
  ): Has[A] with Has[B] with Has[C] with Has[D] =
    any.add(a).add(b).add(c).add(d)

  /**
   * Constructs a new environment holding the specified modules. The module
   * must be monomorphic. Parameterized modules are not supported.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] =
    any.add(a).add(b).add(c).add(d).add(e)

  /**
   * Constructs an empty environment that cannot provide anything.
   */
  lazy val any: Any = {
    val anyRef: AnyRef = ().asInstanceOf[AnyRef]

    new Has(Map(implicitly[Tagged[AnyRef]] -> anyRef))
  }

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
