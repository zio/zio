/*
 * Copyright 2017-2023 John A. De Goes and the ZIO Contributors
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

import scala.annotation.tailrec

final class ZEnvironment[+R] private (
  private val map: Map[LightTypeTag, (Any, Int)],
  private val index: Int,
  private var cache: Map[LightTypeTag, Any] = Map.empty
) extends Serializable { self =>

  def ++[R1: EnvironmentTag](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    self.union[R1](that)

  /**
   * Adds a service to the environment.
   */
  def add[A](a: A)(implicit tag: Tag[A]): ZEnvironment[R with A] =
    unsafe.add[A](tag.tag, a)(Unsafe.unsafe)

  override def equals(that: Any): Boolean = that match {
    case that: ZEnvironment[_] => self.map == that.map
    case _                     => false
  }

  /**
   * Retrieves a service from the environment.
   */
  def get[A >: R](implicit tag: Tag[A]): A =
    unsafe.get[A](tag.tag)(Unsafe.unsafe)

  /**
   * Retrieves a service from the environment corresponding to the specified
   * key.
   */
  def getAt[K, V](k: K)(implicit ev: R <:< Map[K, V], tagged: EnvironmentTag[Map[K, V]]): Option[V] =
    unsafe.get[Map[K, V]](taggedTagType(tagged))(Unsafe.unsafe).get(k)

  /**
   * Retrieves a service from the environment if it exists in the environment.
   */
  def getDynamic[A](implicit tag: Tag[A]): Option[A] =
    Option(unsafe.getOrElse(tag.tag, null.asInstanceOf[A])(Unsafe.unsafe))

  override def hashCode: Int =
    map.hashCode

  /**
   * Prunes the environment to the set of services statically known to be
   * contained within it.
   */
  def prune[R1 >: R](implicit tagged: EnvironmentTag[R1]): ZEnvironment[R1] = {
    val tag = taggedTagType(tagged)
    val set = taggedGetServices(tag)

    val missingServices =
      set.filterNot(tag => map.keys.exists(taggedIsSubtype(_, tag)) || cache.keys.exists(taggedIsSubtype(_, tag)))
    if (missingServices.nonEmpty) {
      throw new Error(
        s"Defect in zio.ZEnvironment: ${missingServices} statically known to be contained within the environment are missing"
      )
    }

    if (set.isEmpty) self
    else
      new ZEnvironment(filterKeys(self.map)(tag => set.exists(taggedIsSubtype(tag, _))), index)
        .asInstanceOf[ZEnvironment[R]]
  }

  /**
   * The size of the environment, which is the number of services contained in
   * the environment. This is intended primarily for testing purposes.
   */
  def size: Int =
    map.size

  override def toString: String =
    s"ZEnvironment(${map.toList.sortBy(_._2._2).map { case (tag, (service, _)) => s"$tag -> $service" }.mkString(", ")})"

  /**
   * Combines this environment with the specified environment.
   */
  def union[R1: EnvironmentTag](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    self.unionAll[R1](that.prune)

  /**
   * Combines this environment with the specified environment. In the event of
   * service collisions, which may not be reflected in statically known types,
   * the right hand side will be preferred.
   */
  def unionAll[R1](that: ZEnvironment[R1]): ZEnvironment[R with R1] = {
    val (self0, that0) = if (self.index + that.index < self.index) (self.clean, that.clean) else (self, that)
    new ZEnvironment(
      self0.map ++ that0.map.map { case (tag, (service, index)) => (tag, (service, self0.index + index)) },
      self0.index + that0.index
    )
  }

  /**
   * Updates a service in the environment.
   */
  def update[A >: R: Tag](f: A => A): ZEnvironment[R] =
    self.add[A](f(get[A]))

  /**
   * Updates a service in the environment corresponding to the specified key.
   */
  def updateAt[K, V](k: K)(f: V => V)(implicit ev: R <:< Map[K, V], tag: Tag[Map[K, V]]): ZEnvironment[R] =
    self.add[Map[K, V]](unsafe.get[Map[K, V]](taggedTagType(tag))(Unsafe.unsafe).updated(k, f(getAt(k).get)))

  /**
   * Filters a map by retaining only keys satisfying a predicate.
   */
  private def filterKeys[K, V](map: Map[K, V])(f: K => Boolean): Map[K, V] =
    map.foldLeft[Map[K, V]](Map.empty) { case (acc, (key, value)) =>
      if (f(key)) acc.updated(key, value) else acc
    }

  private def clean: ZEnvironment[R] = {
    val (map, index) = self.map.toList.sortBy(_._2._2).foldLeft[(Map[LightTypeTag, (Any, Int)], Int)]((Map.empty, 0)) {
      case ((map, index), (tag, (service, _))) =>
        map.updated(tag, (service -> index)) -> (index + 1)
    }
    new ZEnvironment(map, index)
  }

  trait UnsafeAPI {
    def get[A](tag: LightTypeTag)(implicit unsafe: Unsafe): A
    private[ZEnvironment] def add[A](tag: LightTypeTag, a: A)(implicit unsafe: Unsafe): ZEnvironment[R with A]
    private[ZEnvironment] def update[A >: R](tag: LightTypeTag, f: A => A)(implicit
      unsafe: Unsafe
    ): ZEnvironment[R]
  }

  trait UnsafeAPI2 {
    private[ZEnvironment] def getOrElse[A](tag: LightTypeTag, default: => A)(implicit unsafe: Unsafe): A
  }

  val unsafe: UnsafeAPI with UnsafeAPI2 =
    new UnsafeAPI with UnsafeAPI2 {
      private[ZEnvironment] def add[A](tag: LightTypeTag, a: A)(implicit unsafe: Unsafe): ZEnvironment[R with A] = {
        val self0 = if (index == Int.MaxValue) self.clean else self
        new ZEnvironment(self0.map.updated(tag, a -> self0.index), self0.index + 1)
      }

      def get[A](tag: LightTypeTag)(implicit unsafe: Unsafe): A =
        getOrElse(tag, throw new Error(s"Defect in zio.ZEnvironment: Could not find ${tag} inside ${self}"))

      private[ZEnvironment] def getOrElse[A](tag: LightTypeTag, default: => A)(implicit unsafe: Unsafe): A =
        self.cache.get(tag) match {
          case Some(a) => a.asInstanceOf[A]
          case None =>
            var index      = -1
            val iterator   = self.map.iterator
            var service: A = null.asInstanceOf[A]
            while (iterator.hasNext) {
              val (curTag, (curService, curIndex)) = iterator.next()
              if (taggedIsSubtype(curTag, tag) && curIndex > index) {
                index = curIndex
                service = curService.asInstanceOf[A]
              }
            }
            if (service == null) default
            else {
              self.cache = self.cache.updated(tag, service)
              service
            }
        }

      private[ZEnvironment] def update[A >: R](tag: LightTypeTag, f: A => A)(implicit
        unsafe: Unsafe
      ): ZEnvironment[R] =
        add[A](tag, f(get(tag)))
    }
}

object ZEnvironment {

  /**
   * Constructs a new environment holding no services.
   */
  def apply(): ZEnvironment[Any] =
    empty

  /**
   * Constructs a new environment holding the single service.
   */
  def apply[A: Tag](a: A): ZEnvironment[A] =
    empty.add[A](a)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def apply[A: Tag, B: Tag](a: A, b: B): ZEnvironment[A with B] =
    ZEnvironment(a).add[B](b)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def apply[A: Tag, B: Tag, C: Tag](
    a: A,
    b: B,
    c: C
  ): ZEnvironment[A with B with C] =
    ZEnvironment(a).add(b).add[C](c)

  /**
   * Constructs a new environment holding the specified services. The service
   * must be monomorphic. Parameterized services are not supported.
   */
  def apply[A: Tag, B: Tag, C: Tag, D: Tag](
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
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag
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
  val empty: ZEnvironment[Any] =
    new ZEnvironment[Any](Map.empty, 0, Map((taggedTagType(TaggedAny), ())))

  /**
   * A `Patch[In, Out]` describes an update that transforms a `ZEnvironment[In]`
   * to a `ZEnvironment[Out]` as a data structure. This allows combining updates
   * to different services in the environment in a compositional way.
   */
  sealed trait Patch[-In, +Out] { self =>
    import Patch._

    /**
     * Applies an update to the environment to produce a new environment.
     */
    def apply(environment: ZEnvironment[In]): ZEnvironment[Out] = {

      @tailrec
      def loop(environment: ZEnvironment[Any], patches: List[Patch[Any, Any]]): ZEnvironment[Any] =
        patches match {
          case AddService(service, tag) :: patches => loop(environment.unsafe.add(tag, service)(Unsafe.unsafe), patches)
          case AndThen(first, second) :: patches   => loop(environment, erase(first) :: erase(second) :: patches)
          case Empty() :: patches                  => loop(environment, patches)
          case RemoveService(tag) :: patches       => loop(environment, patches)
          case UpdateService(update, tag) :: patches =>
            loop(environment.unsafe.update(tag, update)(Unsafe.unsafe), patches)
          case Nil => environment
        }

      loop(environment, List(self.asInstanceOf[Patch[Any, Any]])).asInstanceOf[ZEnvironment[Out]]
    }

    /**
     * Combines two patches to produce a new patch that describes applying the
     * updates from this patch and then the updates from the specified patch.
     */
    def combine[Out2](that: Patch[Out, Out2]): Patch[In, Out2] =
      AndThen(self, that)
  }

  object Patch {

    /**
     * An empty patch which returns the environment unchanged.
     */
    def empty[A]: Patch[A, A] =
      Empty()

    /**
     * Constructs a patch that describes the updates necessary to transform the
     * specified old environment into the specified new environment.
     */
    def diff[In, Out](oldValue: ZEnvironment[In], newValue: ZEnvironment[Out]): Patch[In, Out] = {
      val sorted = newValue.map.toList.sortBy { case (_, (_, index)) => index }
      val (missingServices, patch) = sorted.foldLeft[(Map[LightTypeTag, (Any, Int)], Patch[In, Out])](
        oldValue.map -> Patch.Empty().asInstanceOf[Patch[In, Out]]
      ) { case ((map, patch), (tag, (newService, newIndex))) =>
        map.get(tag) match {
          case Some((oldService, oldIndex)) =>
            if (oldService == newService && oldIndex == newIndex)
              map - tag -> patch
            else
              map - tag -> patch.combine(AddService(newService, tag))
          case _ =>
            map - tag -> patch.combine(AddService(newService, tag))
        }
      }
      missingServices.foldLeft(patch) { case (patch, (tag, _)) =>
        patch.combine(RemoveService(tag))
      }
    }

    private final case class AddService[Env, Service](service: Service, tag: LightTypeTag)
        extends Patch[Env, Env with Service]
    private final case class AndThen[In, Out, Out2](first: Patch[In, Out], second: Patch[Out, Out2])
        extends Patch[In, Out2]
    private final case class Empty[Env]()                                   extends Patch[Env, Env]
    private final case class RemoveService[Env, Service](tag: LightTypeTag) extends Patch[Env with Service, Env]
    private final case class UpdateService[Env, Service](update: Service => Service, tag: LightTypeTag)
        extends Patch[Env with Service, Env with Service]

    private def erase[In, Out](patch: Patch[In, Out]): Patch[Any, Any] =
      patch.asInstanceOf[Patch[Any, Any]]
  }

  private lazy val TaggedAny: EnvironmentTag[Any] =
    implicitly[EnvironmentTag[Any]]
}
