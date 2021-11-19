/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import izumi.reflect.macrortti.LightTypeTag

final class ZEnvironment[+R] private (
  private val map: Map[LightTypeTag, Any],
  private var cache: Map[LightTypeTag, Any] = Map.empty
) extends Serializable { self =>

  def ++[R1: Tag](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    self.union[R1](that)

  /**
   * Adds a service to the environment.
   */
  def add[A](a: A)(implicit ev: IsNotIntersection[A], tagged: Tag[A]): ZEnvironment[R with A] =
    new ZEnvironment(self.map + (taggedTagType(tagged) -> a))

  override def equals(that: Any): Boolean = that match {
    case that: ZEnvironment[_] => map == that.map
    case _                     => false
  }

  /**
   * Retrieves a service from the environment.
   */
  def get[A >: R](implicit ev: IsNotIntersection[A], tagged: Tag[A]): A =
    unsafeGet(taggedTagType(tagged))

  /**
   * Retrieves a service from the environment corresponding to the specified
   * key.
   */
  def getAt[K, V](k: K)(implicit ev: R <:< Map[K, V], tagged: Tag[Map[K, V]]): Option[V] =
    unsafeGet[Map[K, V]](taggedTagType(tagged)).get(k)

  override def hashCode: Int =
    map.hashCode

  /**
   * Prunes the environment to the set of services statically known to be
   * contained within it.
   */
  def prune[R1 >: R](implicit tagged: Tag[R1]): ZEnvironment[R1] = {
    val tag = taggedTagType(tagged)
    val set = taggedGetServices(tag)

    val missingServices = set.filterNot(tag => map.keys.exists(taggedIsSubtype(_, tag)))
    if (missingServices.nonEmpty) {
      throw new Error(
        s"Defect in zio.ZEnvironment: ${missingServices} statically known to be contained within the environment are missing"
      )
    }

    if (set.isEmpty) self
    else
      new ZEnvironment(filterKeys(self.map)(tag => set.exists(taggedIsSubtype(tag, _)))).asInstanceOf[ZEnvironment[R]]
  }

  /**
   * The size of the environment, which is the number of services contained in
   * the environment. This is intended primarily for testing purposes.
   */
  def size: Int =
    map.size

  override def toString: String =
    s"ZEnvironment($map)"

  /**
   * Combines this environment with the specified environment.
   */
  def union[R1: Tag](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    self.unionAll[R1](that.prune)

  /**
   * Combines this environment with the specified environment. In the event of
   * service collisions, which may not be reflected in statically known types,
   * the right hand side will be preferred.
   */
  def unionAll[R1](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    new ZEnvironment(self.map ++ that.map)

  def unsafeGet[A](tag: LightTypeTag): A = {
    val services = unsafeGetAll[A](tag)
    if (services.size == 1) services.head
    Chunk.fromIterable(services).sortBy(_._1.repr).head._2
  }

  def unsafeGetAll[A](tag: LightTypeTag): Map[LightTypeTag, A] =
    self.map.get(tag) match {
      case Some(a) => Map(tag -> a.asInstanceOf[A])
      case None =>
        self.cache.get(tag) match {
          case Some(a) => Map(tag -> a.asInstanceOf[A])
          case None =>
            val services = self.map.collect {
              case (curTag, value) if taggedIsSubtype(curTag, tag) =>
                self.cache = self.cache + (tag -> value)
                (curTag, value)
            }
            if (services.isEmpty) throw new Error(s"Defect in zio.ZEnvironment: Could not find ${tag} inside ${self}")
            else services.asInstanceOf[Map[LightTypeTag, A]]
        }
    }

  def upcast[R1](implicit ev: R <:< R1): ZEnvironment[R1] =
    new ZEnvironment(map)

  /**
   * Updates a service in the environment.
   */
  def update[A >: R: Tag: IsNotIntersection](f: A => A): ZEnvironment[R] =
    self.add[A](f(get[A]))

  /**
   * Updates a service in the environment correponding to the specified key.
   */
  def updateAt[K, V](k: K)(f: V => V)(implicit ev: R <:< Map[K, V], tag: Tag[Map[K, V]]): ZEnvironment[R] =
    self.add[Map[K, V]](unsafeGet[Map[K, V]](taggedTagType(tag)).updated(k, f(getAt(k).get)))

  /**
   * Filters a map by retaining only keys satisfying a predicate.
   */
  private def filterKeys[K, V](map: Map[K, V])(f: K => Boolean): Map[K, V] =
    map.foldLeft[Map[K, V]](Map.empty) { case (acc, (key, value)) =>
      if (f(key)) acc + (key -> value) else acc
    }
}

object ZEnvironment {

  /**
   * Constructs a new environment holding the single service.
   */
  def apply[A: Tag: IsNotIntersection](a: A): ZEnvironment[A] =
    empty.add[A](a)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def apply[A: Tag: IsNotIntersection, B: Tag: IsNotIntersection](a: A, b: B): ZEnvironment[A with B] =
    ZEnvironment(a).add[B](b)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def apply[A: Tag: IsNotIntersection, B: Tag: IsNotIntersection, C: Tag: IsNotIntersection](
    a: A,
    b: B,
    c: C
  ): ZEnvironment[A with B with C] =
    ZEnvironment(a).add(b).add[C](c)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def apply[A: Tag: IsNotIntersection, B: Tag: IsNotIntersection, C: Tag: IsNotIntersection, D: Tag: IsNotIntersection](
    a: A,
    b: B,
    c: C,
    d: D
  ): ZEnvironment[A with B with C with D] =
    ZEnvironment(a).add(b).add(c).add[D](d)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def apply[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection
  ](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E
  ): ZEnvironment[A with B with C with D with E] =
    ZEnvironment(a).add(b).add(c).add(d).add[E](e)

  /**
   * The empty environment containing no services.
   */
  lazy val empty: ZEnvironment[Any] =
    new ZEnvironment[AnyRef](Map.empty, Map(taggedTagType(TaggedAnyRef) -> (())))

  /**
   * The default ZIO environment.
   */
  lazy val default: ZEnvironment[Clock with Console with Random with System] =
    ZEnvironment[Clock, Console, Random, System](
      Clock.ClockLive,
      Console.ConsoleLive,
      Random.RandomLive,
      System.SystemLive
    )

  private val TaggedAnyRef: Tag[AnyRef] =
    implicitly[Tag[AnyRef]]
}
