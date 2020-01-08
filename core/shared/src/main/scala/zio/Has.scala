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
 * dependency on a service of type `A`. For example,
 * `RIO[Has[Console.Service], Unit]` is an effect that requires a
 * `Console.Service` service. Inside the ZIO library, type aliases are provided
 * as shorthands for common services, e.g.:
 *
 * {{{
 * type Console = Has[ConsoleService]
 * }}}
 *
 * Currently, to be portable across all platforms, all services added to an
 * environment must be monomorphic. Parameterized services are not supported.
 */
final class Has[A] private (private val map: Map[Tagged[_], scala.Any], var cache: Map[Tagged[_], scala.Any] = Map())
    extends Serializable {
  override def equals(that: Any): Boolean = that match {
    case that: Has[_] => map == that.map
  }

  override def hashCode: Int = map.hashCode

  override def toString: String = map.mkString("Map(", ",\n", ")")

  /**
   * The size of the environment, which is the number of services contained
   * in the environment. This is intended primarily for testing purposes.
   */
  def size: Int = map.size
}
object Has {
  private val TaggedAnyRef: Tagged[AnyRef] = implicitly[Tagged[AnyRef]]

  type MustHave[A, B]    = A <:< Has[B]
  type MustNotHave[A, B] = NotExtends[A, Has[B]]

  @silent("define classes/objects inside of package objects")
  trait NotExtends[A, B] extends Serializable

  object NotExtends {
    implicit def notExtends0[A, B]: A NotExtends B      = new NotExtends[A, B] {}
    implicit def notExtends1[A <: B, B]: A NotExtends B = ???
    @annotation.implicitAmbiguous(
      "The environment ${A} already contains service ${B}, are you sure you want to overwrite it? Use Has#update to update a service already inside the environment."
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
     * Adds a service to the environment. The service must be monomorphic rather
     * than parameterized. Parameterized services are not supported.
     *
     * Good: `Logging`, bad: `Logging[String]`.
     */
    def add[B](b: B)(implicit tag: Tagged[B], ev: Self MustNotHave B): Self with Has[B] =
      new Has(self.map + (tag -> b)).asInstanceOf[Self with Has[B]]

    /**
     * Retrieves a service from the environment.
     */
    def get[B](implicit ev: Self <:< Has[_ <: B], tag: Tagged[B]): B =
      self.map
        .getOrElse(
          tag,
          self.cache.getOrElse(
            tag, {
              self.map.collectFirst {
                case (curTag, value) if taggedIsSubtype(curTag, tag) =>
                  self.cache = self.cache + (curTag -> value)
                  value
              }.getOrElse(throw new Error(s"Defect in zio.Has: Could not find ${tag} inside ${self}"))
            }
          )
        )
        .asInstanceOf[B]

    /**
     * Combines this environment with the specified environment. In the event
     * of service collisions, which may not be reflected in statically known
     * types, the right hand side will be preferred.
     */
    def union[B <: Has[_]](that: B): Self with B =
      (new Has(self.map ++ that.map)).asInstanceOf[Self with B]

    def upcast[A: Tagged](implicit ev: Self <:< Has[A]): Has[A] = 
      Has(ev(self).get[A])

    def upcast[A: Tagged, B: Tagged](implicit ev: Self <:< Has[A] with Has[B]): Has[A] with Has[B] = 
      Has.allOf[A, B](ev(self).get[A], ev(self).get[B])

    def upcast[A: Tagged, B: Tagged, C: Tagged](implicit ev: Self <:< Has[A] with Has[B] with Has[C]): Has[A] with Has[B] with Has[C] = 
      Has.allOf[A, B, C](ev(self).get[A], ev(self).get[B], ev(self).get[C])

    def upcast[A: Tagged, B: Tagged, C: Tagged, D: Tagged](implicit ev: Self <:< Has[A] with Has[B] with Has[C] with Has[D]): Has[A] with Has[B] with Has[C] with Has[D] = 
      Has.allOf[A, B, C, D](ev(self).get[A], ev(self).get[B], ev(self).get[C], ev(self).get[D])

    def upcast[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged](implicit ev: Self <:< Has[A] with Has[B] with Has[C] with Has[D] with Has[E]): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] = 
      Has.allOf[A, B, C, D, E](ev(self).get[A], ev(self).get[B], ev(self).get[C], ev(self).get[D], ev(self).get[E])

    /**
     * Updates a service in the environment.
     */
    def update[B: Tagged](f: B => B)(implicit ev: Self MustHave B): Self =
      self.add(f(get[B]))
  }

  /**
   * Constructs a new environment holding the single service. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def apply[A: Tagged](a: A): Has[A] = new Has[AnyRef](Map(), Map(TaggedAnyRef -> (()))).add(a)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def allOf[A: Tagged, B: Tagged](a: A, b: B): Has[A] with Has[B] = Has(a).add(b)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged](a: A, b: B, c: C): Has[A] with Has[B] with Has[C] =
    Has(a).add(b).add(c)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged](
    a: A,
    b: B,
    c: C,
    d: D
  ): Has[A] with Has[B] with Has[C] with Has[D] =
    Has(a).add(b).add(c).add(d)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def allOf[A: Tagged, B: Tagged, C: Tagged, D: Tagged, E: Tagged](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E
  ): Has[A] with Has[B] with Has[C] with Has[D] with Has[E] =
    Has(a).add(b).add(c).add(d).add(e)

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
