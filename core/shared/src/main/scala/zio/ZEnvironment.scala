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
  def add[A](a: A)(implicit tagged: Tag[A]): ZEnvironment[R with A] =
    new ZEnvironment(self.map + (taggedTagType(tagged) -> a))

  override def equals(that: Any): Boolean = that match {
    case that: ZEnvironment[_] => map == that.map
    case _                     => false
  }

  /**
   * Retrieves a service from the environment.
   */
  def get[A >: R](implicit tagged: Tag[A]): A =
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

  def upcast[R1](implicit ev: R <:< R1): ZEnvironment[R1] =
    new ZEnvironment(map)

  /**
   * Retrieves a service from the environment.
   */
  private[zio] def unsafeGet[A](tag: LightTypeTag): A =
    self.map
      .getOrElse(
        tag,
        self.cache.getOrElse(
          tag,
          self.map.collectFirst {
            case (curTag, value) if taggedIsSubtype(curTag, tag) =>
              self.cache = self.cache + (curTag -> value)
              value
          }.getOrElse(throw new Error(s"Defect in zio.ZEnvironment: Could not find ${tag} inside ${self}"))
        )
      )
      .asInstanceOf[A]

  def update[A >: R: Tag](f: A => A): ZEnvironment[R] =
    new ZEnvironment(map.updated(Tag[A].tag, f(get[A])))

  def updateAt[K, V](k: K)(f: V => V)(implicit ev: R <:< Map[K, V], tag: Tag[Map[K, V]]): ZEnvironment[R] =
    new ZEnvironment(map.updated(tag.tag, map(tag.tag).asInstanceOf[Map[K, V]].updated(k, f)))

  /**
   * Filters a map by retaining only keys satisfying a predicate.
   */
  private def filterKeys[K, V](map: Map[K, V])(f: K => Boolean): Map[K, V] =
    map.foldLeft[Map[K, V]](Map.empty) { case (acc, (key, value)) =>
      if (f(key)) acc + (key -> value) else acc
    }
}

object ZEnvironment {

  def apply[A: Tag](a: A): ZEnvironment[A] =
    new ZEnvironment(Map(Tag[A].tag -> a))
  def apply[A: Tag, B: Tag](a: A, b: B): ZEnvironment[A with B] =
    new ZEnvironment(Map(Tag[A].tag -> a, Tag[B].tag -> b))
  def apply[A: Tag, B: Tag, C: Tag](a: A, b: B, c: C): ZEnvironment[A with B with C] =
    new ZEnvironment(Map(Tag[A].tag -> a, Tag[B].tag -> b, Tag[C].tag -> c))
  def apply[A: Tag, B: Tag, C: Tag, D: Tag](a: A, b: B, c: C, d: D): ZEnvironment[A with B with C with D] =
    new ZEnvironment(Map(Tag[A].tag -> a, Tag[B].tag -> b, Tag[C].tag -> c, Tag[D].tag -> d))
  val empty: ZEnvironment[Any] =
    new ZEnvironment(Map.empty)
  lazy val default: ZEnvironment[Clock with Console with Random with System] =
    ZEnvironment[Clock](Clock.ClockLive) ++
      ZEnvironment[Console](Console.ConsoleLive) ++
      ZEnvironment[System](System.SystemLive) ++
      ZEnvironment[Random](Random.RandomLive)
}
