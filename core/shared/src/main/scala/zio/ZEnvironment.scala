/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import scala.annotation.tailrec
import scala.collection.immutable.{HashMap, VectorMap}
import scala.collection.mutable
import scala.util.hashing.MurmurHash3

final class ZEnvironment[+R] private (
  private val map: VectorMap[LightTypeTag, Any],
  private var cache: HashMap[LightTypeTag, Any],
  private val scope: Scope
) extends Serializable { self =>
  import ZEnvironment.ScopeTag

  @deprecated("Kept for binary compatibility only. Do not use", "2.1.2")
  private[ZEnvironment] def this(map: Map[LightTypeTag, Any], index: Int, cache: Map[LightTypeTag, Any] = Map.empty) =
    this(VectorMap.empty ++ map, HashMap.from(cache), null)

  def ++[R1: EnvironmentTag](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    self.union[R1](that)

  // Cache the reversing so that repeated lookups are faster
  private lazy val reversedMapEntries = {
    var l  = List.empty[(LightTypeTag, Any)]
    val it = map.iterator
    while (it.hasNext) {
      val next = it.next()
      l = next :: l
    }
    l
  }

  /**
   * Adds a service to the environment.
   */
  def add[A](a: A)(implicit tag: Tag[A]): ZEnvironment[R with A] =
    unsafe.add[A](tag.tag, a)(Unsafe.unsafe)

  override def equals(that: Any): Boolean = that match {
    case that: ZEnvironment[_] =>
      if (self.scope ne that.scope) false
      else if (self.map eq that.map) true
      else if (self.map.size != that.map.size) false
      else self.hashCode == that.hashCode
    case _ => false
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

  override lazy val hashCode: Int = {
    // NOTE: We can't use map.hashCode because that doesn't take ordering into account
    MurmurHash3.productHash((MurmurHash3.orderedHash(map), scope))
  }

  /**
   * Prunes the environment to the set of services statically known to be
   * contained within it.
   */
  def prune[R1 >: R](implicit tagged: EnvironmentTag[R1]): ZEnvironment[R1] = {
    val tag = taggedTagType(tagged)

    // Mutable set lookups are much faster. It also iterates faster. We're better off just allocating here
    // Why are immutable set lookups so slow???
    val set = new mutable.HashSet ++= taggedGetServices(tag)

    if (set.isEmpty || self.isEmpty) self
    else {
      val builder = VectorMap.newBuilder[LightTypeTag, Any]
      val found   = new mutable.HashSet[LightTypeTag]
      found.sizeHint(set.size)

      val it0 = self.map.iterator
      while (it0.hasNext) {
        val next @ (leftTag, _) = it0.next()

        if (set.contains(leftTag)) {
          // Exact match, no need to loop
          found.add(leftTag)
          builder += next
        } else {
          // Need to check whether it's a subtype
          var loop = true
          val it1  = set.iterator
          while (it1.hasNext && loop) {
            val rightTag = it1.next()
            if (taggedIsSubtype(leftTag, rightTag)) {
              found.add(rightTag)
              builder += next
              loop = false
            }
          }
        }
      }
      val scopeTag = set.find(isScopeTag)
      scopeTag.foreach(found.add)

      if (set.size > found.size) {
        val missing = set -- found
        throw new Error(
          s"Defect in zio.ZEnvironment: ${missing} statically known to be contained within the environment are missing"
        )
      }

      new ZEnvironment(
        builder.result(),
        cache = HashMap.empty,
        scope = if (scopeTag.isEmpty) null else scope
      )
    }
  }

  /**
   * The size of the environment, which is the number of services contained in
   * the environment. This is intended primarily for testing purposes.
   */
  def size: Int =
    map.size + (if (scope eq null) 0 else 1)

  def isEmpty: Boolean =
    map.isEmpty && (scope eq null)

  override def toString: String = {
    val asList  = map.toList
    val entries = if (scope ne null) (ScopeTag, scope) :: asList else asList
    s"ZEnvironment(${entries.map { case (tag, service) => s"$tag -> $service" }.mkString(", ")})"
  }

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
  def unionAll[R1](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    if (self == that) that.asInstanceOf[ZEnvironment[R with R1]]
    else {
      val newMap = that.map.foldLeft(self.map) { case (map, (k, v)) =>
        map.removed(k).updated(k, v)
      }
      val newScope = if (that.scope eq null) self.scope else that.scope
      // Reuse the cache of the right hand-side
      new ZEnvironment(newMap, cache = that.cache, scope = newScope)
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

  trait UnsafeAPI {
    def get[A](tag: LightTypeTag)(implicit unsafe: Unsafe): A
    private[ZEnvironment] def add[A](tag: LightTypeTag, a: A)(implicit unsafe: Unsafe): ZEnvironment[R with A]
    private[ZEnvironment] def update[A >: R](tag: LightTypeTag, f: A => A)(implicit unsafe: Unsafe): ZEnvironment[R]

    private[ZEnvironment] def addService[A](tag: LightTypeTag, a: A)(implicit unsafe: Unsafe): ZEnvironment[R with A]
    private[zio] def addScope(scope: Scope)(implicit unsafe: Unsafe): ZEnvironment[R with Scope]
  }

  trait UnsafeAPI2 {
    private[ZEnvironment] def getOrElse[A](tag: LightTypeTag, default: => A)(implicit unsafe: Unsafe): A
  }

  private def isScopeTag(tag: LightTypeTag): Boolean =
    taggedIsSubtype(tag, ScopeTag)

  val unsafe: UnsafeAPI with UnsafeAPI2 =
    new UnsafeAPI with UnsafeAPI2 {
      private[ZEnvironment] def add[A](tag: LightTypeTag, a: A)(implicit unsafe: Unsafe): ZEnvironment[R with A] =
        if (a.isInstanceOf[Scope] && isScopeTag(tag))
          addScope(a.asInstanceOf[Scope]).asInstanceOf[ZEnvironment[R with A]]
        else
          addService[A](tag, a)

      private[zio] def addScope(scope: Scope)(implicit unsafe: Unsafe): ZEnvironment[R with Scope] =
        new ZEnvironment(map, cache = cache, scope = scope)

      private[ZEnvironment] def addService[A](tag: LightTypeTag, a: A)(implicit
        unsafe: Unsafe
      ): ZEnvironment[R with A] = {
        // Remove from the cache all entries that are supertypes of the current tag.
        // Might seem expensive, but `filterNot` will preserve the map in cases there were no removals (i.e., no supertypes in the cache)
        // val newCache = cache.filterNot { case (k, _) => taggedIsSubtype(tag, k) && k != tag }.updated(tag, a)
        val newCache = HashMap(tag -> a)
        new ZEnvironment(map.removed(tag).updated(tag, a), cache = newCache, scope = scope)
      }

      def get[A](tag: LightTypeTag)(implicit unsafe: Unsafe): A =
        getOrElse(tag, throw new Error(s"Defect in zio.ZEnvironment: Could not find ${tag} inside ${self}"))

      private[ZEnvironment] def getOrElse[A](tag: LightTypeTag, default: => A)(implicit unsafe: Unsafe): A = {
        val fromCache = self.cache.getOrElse(tag, null)
        if (fromCache != null) fromCache.asInstanceOf[A]
        else if ((scope ne null) && isScopeTag(tag)) scope.asInstanceOf[A]
        else if (!self.isEmpty) {
          var remaining = reversedMapEntries
          var service   = null.asInstanceOf[A]
          while ((remaining ne Nil) && service == null) {
            val (curTag, entry) = remaining.head
            if (taggedIsSubtype(curTag, tag)) {
              service = entry.asInstanceOf[A]
            }
            remaining = remaining.tail
          }
          if (service == null) {
            default
          } else {
            cache = self.cache.updated(tag, service)
            service
          }
        } else {
          // Don't know why we need to return this if the environment is empty, but tests require it
          ().asInstanceOf[A]
        }
      }

      private[ZEnvironment] def update[A >: R](tag: LightTypeTag, f: A => A)(implicit
        unsafe: Unsafe
      ): ZEnvironment[R] =
        add[A](tag, f(get(tag)))
    }
}

object ZEnvironment {
  private val ScopeTag: LightTypeTag = taggedTagType(EnvironmentTag[Scope])

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
   * Constructs a new environment holding the specified services.
   */
  def apply[A: Tag, B: Tag](a: A, b: B): ZEnvironment[A with B] =
    ZEnvironment(a).add[B](b)

  /**
   * Constructs a new environment holding the specified services.
   */
  def apply[A: Tag, B: Tag, C: Tag](
    a: A,
    b: B,
    c: C
  ): ZEnvironment[A with B with C] =
    ZEnvironment(a).add(b).add[C](c)

  /**
   * Constructs a new environment holding the specified services.
   */
  def apply[A: Tag, B: Tag, C: Tag, D: Tag](
    a: A,
    b: B,
    c: C,
    d: D
  ): ZEnvironment[A with B with C with D] =
    ZEnvironment(a).add(b).add(c).add[D](d)

  /**
   * Constructs a new environment holding the specified services.
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
    new ZEnvironment[Any](VectorMap.empty, cache = HashMap.empty, scope = null)

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
      def loop(env: ZEnvironment[Any], patches: List[Patch[Any, Any]]): ZEnvironment[Any] =
        if (patches eq Nil) env
        else
          patches.head match {
            case AddService(service, tag) => loop(env.unsafe.addService(tag, service)(Unsafe.unsafe), patches.tail)
            case AndThen(first, second)   => loop(env, erase(first) :: erase(second) :: patches.tail)
            case AddScope(scope)          => loop(env.unsafe.addScope(scope)(Unsafe.unsafe), patches.tail)
            case _: Empty[?]              => loop(env, patches.tail)
            case _: RemoveService[?, ?]   => loop(env, patches.tail)
            case _: UpdateService[?, ?]   => loop(env, patches.tail)
          }

      if (self eq empty0) environment.asInstanceOf[ZEnvironment[Out]]
      else loop(environment, self.asInstanceOf[Patch[Any, Any]] :: Nil).asInstanceOf[ZEnvironment[Out]]
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
    def empty[A]: Patch[A, A] = empty0.asInstanceOf[Patch[A, A]]

    /**
     * Constructs a patch that describes the updates necessary to transform the
     * specified old environment into the specified new environment.
     */
    def diff[In, Out](oldValue: ZEnvironment[In], newValue: ZEnvironment[Out]): Patch[In, Out] =
      if (oldValue.map eq newValue.map) {
        if (oldValue.scope eq newValue.scope) empty0.asInstanceOf[Patch[In, Out]]
        else AddScope(newValue.scope).asInstanceOf[Patch[In, Out]]
      } else {
        val oldIt = oldValue.map.iterator
        val newIt = newValue.map.iterator
        var patch = empty0.asInstanceOf[Patch[In, Out]]
        var loop  = true

        /**
         * When the new map is updated, entries in the old map that haven't been
         * updated will match the same order of non-updated entries in the new
         * map. Our goal is to loop until we find a common tag that has a
         * different service.
         *
         * Three scenarios here:
         *   1. We found the tag and the services are the same, so we can
         *      continue in this loop (no patch required)
         *   1. We found the tag and the services are different, so we can exit
         *      this loop
         *   1. We didn't find the old tag, which means this is a completely
         *      different map and we should exit this loop.
         *
         * (TODO: Maybe discard the whole old map if no common tag was found?)
         */
        while (loop && oldIt.hasNext && newIt.hasNext) {
          var (oldTag, oldService) = oldIt.next()
          val (newTag, newService) = newIt.next()

          while (oldTag != newTag && oldIt.hasNext) {
            val old = oldIt.next()
            oldTag = old._1
            oldService = old._2
          }

          if (oldService != newService) {
            loop = false
            patch = patch.combine(AddService(newService, newTag))
          }
        }

        // All entries in the new environment from now on are guaranteed to be new
        while (newIt.hasNext) {
          val (tag, newService) = newIt.next()
          patch = patch.combine(AddService(newService, tag))
        }
        if (oldValue.scope ne newValue.scope) {
          patch = patch.combine(AddScope(newValue.scope))
        }
        patch
      }

    private val empty0 = Empty()
    private final case class Empty[Env]() extends Patch[Env, Env]

    private final case class AddScope[Env, Service](scope: Scope) extends Patch[Env, Env with Scope]
    private final case class AddService[Env, Service](service: Service, tag: LightTypeTag)
        extends Patch[Env, Env with Service]
    private final case class AndThen[In, Out, Out2](first: Patch[In, Out], second: Patch[Out, Out2])
        extends Patch[In, Out2]

    @deprecated("Kept for binary compatibility only. Do not use")
    private final case class RemoveService[Env, Service](tag: LightTypeTag) extends Patch[Env with Service, Env]
    @deprecated("Kept for binary compatibility only. Do not use")
    private final case class UpdateService[Env, Service](update: Service => Service, tag: LightTypeTag)
        extends Patch[Env with Service, Env with Service]

    private def erase[In, Out](patch: Patch[In, Out]): Patch[Any, Any] =
      patch.asInstanceOf[Patch[Any, Any]]
  }

}
