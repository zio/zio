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

class ZEnvironment[+R] private (private val map: Map[LightTypeTag, Any]) extends Serializable { self =>
  def get[A >: R: Tag]: A =
    unsafeGet(Tag[A]).fold {
      throw new NoSuchElementException(s"No implementation of service ${Tag[A]} in ZEnvironment($map)")
    } {
      case r: A => r
      case r    => throw new ClassCastException(s"Expected ${Tag[A]}, got ${r.getClass}")
    }
  def ++[R1: Tag](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    new ZEnvironment(map ++ that.prune.map)
  def +!+[R1](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    new ZEnvironment(map ++ that.map)
  def +[A: Tag](a: A): ZEnvironment[R with A] =
    new ZEnvironment(map + (Tag[A].tag -> a))
  def getAt[K, V](k: K)(implicit ev: R <:< Map[K, V], tag: Tag[Map[K, V]]): Option[V] =
    unsafeGet(tag).get.asInstanceOf[Map[K, V]].get(k)
  def update[A >: R: Tag](f: A => A): ZEnvironment[R] =
    new ZEnvironment(map.updated(Tag[A].tag, f(get[A])))
  def updateAt[K, V](k: K)(f: V => V)(implicit ev: R <:< Map[K, V], tag: Tag[Map[K, V]]): ZEnvironment[R] =
    new ZEnvironment(map.updated(tag.tag, map.get(tag.tag).get.asInstanceOf[Map[K, V]].updated(k, f)))
  def widen[R1](implicit ev: R <:< R1): ZEnvironment[R1] =
    new ZEnvironment(map)

  private def unsafeGet(tag: Tag[_]): Option[Any] =
    unsafeGetType(tag) orElse unsafeGetSubtype(tag)

  private def unsafeGetType(tag: Tag[_]): Option[Any] =
    map.get(tag.tag)

  private def unsafeGetSubtype(tag: Tag[_]): Option[Any] =
    map.find { case (key, value) => key <:< tag.tag }.map(_._2)

  override def toString: String =
    s"ZEnvironment(${map.toString})"

  /**
   * Prunes the environment to the set of services statically known to be
   * contained within it.
   */
  private def prune[R1 >: R](implicit tagged: Tag[R1]): ZEnvironment[R1] = {
    val tag = taggedTagType(tagged)
    val set = taggedGetHasServices(tag)

    val missingServices = set.filterNot(key => map.exists { case (tag, _) => tag <:< key })
    if (missingServices.nonEmpty) {
      println(set)
      println(map.keySet)
      throw new Error(
        s"Defect in zio.ZEnvironment: ${missingServices} statically known to be contained within the environment are missing"
      )
    }

    if (set.isEmpty) self
    else new ZEnvironment(filterKeys(map)(key => set.exists(tag => key <:< tag))).asInstanceOf[ZEnvironment[R]]
  }

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
  val empty: ZEnvironment[Any] =
    new ZEnvironment(Map.empty)
  lazy val default: ZEnvironment[Clock with Console with Random with System] =
    ZEnvironment[Clock](Clock.ClockLive) ++
      ZEnvironment[Console](Console.ConsoleLive) ++
      ZEnvironment[System](System.SystemLive) ++
      ZEnvironment[Random](Random.RandomLive)
}
