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

import zio.internal.UpdateOrderLinkedMap

import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import scala.collection.{immutable, mutable}
import scala.util.control.ControlThrowable
import scala.util.hashing.MurmurHash3

/**
 * A `ZEnvironment[R]` holds the services that an effect of type `ZIO[R, E, A]`
 * needs, keyed by their type. `get` finds a service by its `Tag`, and returns
 * the most recently added service whose type is the requested type or a subtype
 * of it.
 *
 * @see
 *   [[https://zio.dev/reference/contextual/zenvironment/ ZEnvironment]]
 */
final class ZEnvironment[+R] private (
  private val map: UpdateOrderLinkedMap[LightTypeTag, Any],
  private val cache: ConcurrentHashMap[LightTypeTag, Any],
  private val scope: Scope
) extends Serializable { self =>
  import ZEnvironment.{ScopeTag, TaggedAny, UnitAny}

  @deprecated("Kept for binary compatibility only. Do not use", "2.1.2")
  private[ZEnvironment] def this(map: Map[LightTypeTag, Any], index: Int, cache: Map[LightTypeTag, Any] = Map.empty) =
    this(UpdateOrderLinkedMap.fromMap(map), ZEnvironment.scalaToJucMap(cache), null)

  @deprecated("Kept for binary compatibility only. Do not use", "2.1.5")
  private[ZEnvironment] def this(
    map: UpdateOrderLinkedMap[LightTypeTag, Any],
    cache: immutable.HashMap[LightTypeTag, Any],
    scope: Scope
  ) = this(map, ZEnvironment.scalaToJucMap(cache), scope)

  @deprecated("Kept for binary compatibility only. Do not use", "2.1.6")
  private[ZEnvironment] def this(
    map: UpdateOrderLinkedMap[LightTypeTag, Any],
    cache: mutable.HashMap[LightTypeTag, Any],
    scope: Scope
  ) = this(map, ZEnvironment.scalaToJucMap(cache), scope)

  /**
   * Combines this environment with the specified environment, like `union`:
   * `that` is first pruned with `prune[R1]`, and when both environments hold a
   * service of the same type, the one in `that` wins.
   */
  def ++[R1: EnvironmentTag](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    self.union[R1](that)

  /**
   * Adds a service to the environment.
   *
   * A service already stored under the same type is replaced.
   */
  def add[A](a: A)(implicit tag: Tag[A]): ZEnvironment[R with A] =
    unsafe.add[A](tag.tag, a)(Unsafe)

  override def equals(that: Any): Boolean = that match {
    case that: ZEnvironment[_] =>
      if (self eq that) true
      else if (self.scope ne that.scope) false
      else if (self.map eq that.map) true
      else if (self.map.size != that.map.size) false
      else self.hashCode == that.hashCode
    case _ => false
  }

  /**
   * Similar to `equals` but uses reference equality on the map's elements.
   * Therefore, this method might result in false negatives but never in false
   * positives.
   *
   * Useful for cases where failing the equality check will not produce an
   * invalid state (e.g., applying optimizations) and we want to avoid the
   * overhead of strict equality.
   */
  private def relaxedEquals(that: ZEnvironment[_]): Boolean =
    if (self eq that) true
    else if (self.scope ne that.scope) false
    else if (self.map eq that.map) true
    else if (self.map.size != that.map.size) false
    else {
      // We check in the reverse order since this is an update-ordered map
      // We could potentially check only the last element but that might result in a false positive so better be safe
      val l   = self.map.reverseIterator
      val r   = that.map.reverseIterator
      var res = true
      while (l.hasNext && res) {
        val (lk, lv) = l.next().asInstanceOf[(LightTypeTag, AnyRef)]
        val (rk, rv) = r.next().asInstanceOf[(LightTypeTag, AnyRef)]
        res = lk == rk && (lv eq rv)
      }
      res
    }

  /**
   * Retrieves a service from the environment.
   *
   * It returns the most recently added service whose type is `A` or a subtype
   * of `A`.
   *
   * {{{
   * trait Animal { def name: String }
   * final case class Dog(name: String) extends Animal
   * final case class Cat(name: String) extends Animal
   *
   * val pets: ZEnvironment[Dog & Cat] =
   *   ZEnvironment.empty.add[Dog](Dog("Rex")).add[Cat](Cat("Tom"))
   *
   * val animal: Animal = pets.get[Animal]
   * // Cat(Tom)
   * }}}
   */
  def get[A >: R](implicit tag: Tag[A]): A =
    unsafe.get[A](tag.tag)(Unsafe)

  /**
   * Retrieves a service from the environment corresponding to the specified
   * key.
   */
  def getAt[K, V](k: K)(implicit ev: R <:< Map[K, V], tagged: EnvironmentTag[Map[K, V]]): Option[V] =
    unsafe.get[Map[K, V]](taggedTagType(tagged))(Unsafe).get(k)

  /**
   * Retrieves a service from the environment if it exists in the environment.
   *
   * Like `get`, it returns the most recently added service whose type is `A` or
   * a subtype of `A`, and returns `None` when there is none.
   */
  def getDynamic[A](implicit tag: Tag[A]): Option[A] =
    Option(unsafe.getOrElse(tag.tag, null.asInstanceOf[A])(Unsafe))

  /**
   * Retrieves the current Scope the environment. Raises a compilation error if
   * the `ZEnvironment` is not statically known to contain a `Scope`. Throws a
   * `java.lang.Error` when the environment holds no `Scope` at run time.
   */
  private[zio] def getScope(implicit ev: R <:< Scope): Scope =
    scope match {
      case null => throw new Error(s"Defect in zio.ZEnvironment: Could not find Scope inside $self")
      case v    => v
    }

  override lazy val hashCode: Int =
    MurmurHash3.productHash((map, scope)): @noinline // See https://github.com/zio/zio/pull/10363 why `@noinline`

  /**
   * Prunes the environment to the set of services statically known to be
   * contained within it.
   *
   * It keeps the services whose type is a type of `R1` or a subtype of one, and
   * drops the others. When `R1` is `Any`, it returns the environment unchanged.
   */
  def prune[R1 >: R](implicit tagged: EnvironmentTag[R1]): ZEnvironment[R1] = {

    def throwError(tags: collection.Set[LightTypeTag]) =
      throw new Error(
        s"Defect in zio.ZEnvironment: $tags statically known to be contained within the environment are missing"
      )

    @inline
    def returnScopeOrThrow(): Scope =
      scope match {
        case null => throwError(Set(ScopeTag))
        case s    => s
      }

    // Optimized pruning for a single service
    def pruneOne(tag: LightTypeTag): ZEnvironment[R1] =
      if (isScopeTag(tag)) {
        new ZEnvironment(
          UpdateOrderLinkedMap.empty[LightTypeTag, Any],
          cache = new ConcurrentHashMap[LightTypeTag, Any],
          scope = returnScopeOrThrow()
        )
      } else {
        val builder = UpdateOrderLinkedMap.newBuilder[LightTypeTag, Any]

        val it0 = self.map.iterator
        while (it0.hasNext) {
          val next    = it0.next()
          val leftTag = next._1

          if (taggedIsSubtype(leftTag, tag)) {
            builder addOne next
          }
        }
        val newMap = builder.result()

        if (newMap.isEmpty) throwError(Set(tag))

        new ZEnvironment(
          newMap,
          cache = new ConcurrentHashMap[LightTypeTag, Any],
          scope = null
        )
      }

    def pruneMany(iSet: Set[LightTypeTag]): ZEnvironment[R1] = {
      // Mutable set lookups are much faster. It also iterates faster. We're better off just allocating here
      // Why are immutable set lookups so slow???
      val set        = new mutable.HashSet ++= iSet
      val tagsAsList = iSet.toList // Lists iterate much faster than sets
      val builder    = UpdateOrderLinkedMap.newBuilder[LightTypeTag, Any]
      val found      = new mutable.HashSet[LightTypeTag]
      val nil        = Nil
      found.sizeHint(set.size)

      val it0 = self.map.iterator
      while (it0.hasNext) {
        val next    = it0.next()
        val leftTag = next._1

        if (set.contains(leftTag)) {
          // Exact match, no need to loop
          found.add(leftTag)
          builder addOne next
        } else {
          // Need to check whether it's a subtype
          var rem = tagsAsList
          while (rem ne nil) {
            val rightTag = rem.head
            if (taggedIsSubtype(leftTag, rightTag)) {
              found.add(rightTag)
              builder addOne next
              rem = nil
            } else {
              rem = rem.tail
            }
          }
        }
      }

      var needsScope = false
      var rem        = tagsAsList
      while (rem ne nil) {
        val tag = rem.head
        if (isScopeTag(tag)) {
          needsScope = true
          found.add(tag)
        }
        rem = rem.tail
      }

      val newMap = builder.result()

      if (set.size > found.size) {
        val missing = set --= found

        // We need to check whether one of the services we added is a subtype of the missing service
        val newTags = newMap.keySet
        missing.foreach { tag =>
          if (newTags.exists(taggedIsSubtype(_, tag))) missing.remove(tag)
        }

        if (!missing.isEmpty) throwError(missing)
      }

      new ZEnvironment(
        newMap,
        cache = new ConcurrentHashMap[LightTypeTag, Any],
        scope = if (needsScope) returnScopeOrThrow() else null
      )
    }

    val tag  = taggedTagType(tagged)
    val iSet = taggedGetServices(tag)

    if (iSet.isEmpty || self.isEmpty) self
    else if (iSet.size == 1) pruneOne(iSet.head)
    else pruneMany(iSet)
  }

  /**
   * The size of the environment, which is the number of services contained in
   * the environment. This is intended primarily for testing purposes.
   */
  def size: Int =
    map.size + (if (scope eq null) 0 else 1)

  /**
   * Returns `true` when the environment holds no service and no `Scope`.
   */
  def isEmpty: Boolean =
    (scope eq null) && map.isEmpty

  override def toString: String = {
    val asList  = map.toList
    val entries = if (scope ne null) (ScopeTag, scope) :: asList else asList
    s"ZEnvironment(${entries.map { case (tag, service) => s"$tag -> $service" }.mkString(", ")})"
  }

  /**
   * Combines this environment with the specified environment.
   *
   * `that` is first pruned with `prune[R1]`, so services it holds beyond its
   * static type are dropped, unless `R1` is `Any`. When both environments hold
   * a service of the same type, the one in `that` wins.
   *
   * {{{
   * val defaults: ZEnvironment[Int & String] = ZEnvironment[Int, String](8080, "localhost")
   * val overrides: ZEnvironment[Int]            = ZEnvironment(9090)
   *
   * val port: Int = defaults.union[Int](overrides).get[Int]
   * // 9090
   * val host: String = defaults.union[Int](overrides).get[String]
   * // localhost
   * }}}
   */
  def union[R1: EnvironmentTag](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    self.unionAll[R1](that.prune)

  /**
   * Combines this environment with the specified environment. In the event of
   * service collisions, which may not be reflected in statically known types,
   * the right hand side will be preferred.
   */
  def unionAll[R1](that: ZEnvironment[R1]): ZEnvironment[R with R1] =
    if (self.relaxedEquals(that)) that.asInstanceOf[ZEnvironment[R with R1]]
    else {
      var newMap = self.map
      val it     = that.map.iterator
      while (it.hasNext) {
        val kv = it.next()
        newMap = newMap.updated(kv._1, kv._2)
      }
      val newScope = if (that.scope eq null) self.scope else that.scope
      // Reuse the cache of the right hand-side
      new ZEnvironment(newMap, cache = new ConcurrentHashMap[LightTypeTag, Any](that.cache), scope = newScope)
    }

  /**
   * Updates a service in the environment.
   */
  def update[A >: R: Tag](f: A => A): ZEnvironment[R] =
    self.add[A](f(get[A]))

  /**
   * Updates a service in the environment corresponding to the specified key.
   *
   * Throws a `java.util.NoSuchElementException` when the map holds no value for
   * `k`.
   */
  def updateAt[K, V](k: K)(f: V => V)(implicit ev: R <:< Map[K, V], tag: Tag[Map[K, V]]): ZEnvironment[R] =
    self.add[Map[K, V]](unsafe.get[Map[K, V]](taggedTagType(tag))(Unsafe).updated(k, f(getAt(k).get)))

  /**
   * Operations on the environment that look services up by a `LightTypeTag`
   * instead of a `Tag`, so the compiler does not check the service type. They
   * need an `Unsafe` value.
   */
  trait UnsafeAPI {

    /**
     * Returns the most recently added service whose type is the type of `tag`
     * or a subtype of it, cast to `A`. Throws a `java.lang.Error` when the
     * environment holds no such service.
     */
    def get[A](tag: LightTypeTag)(implicit unsafe: Unsafe): A
    private[ZEnvironment] def add[A](tag: LightTypeTag, a: A)(implicit unsafe: Unsafe): ZEnvironment[R with A]
    private[ZEnvironment] def update[A >: R](tag: LightTypeTag, f: A => A)(implicit unsafe: Unsafe): ZEnvironment[R]
  }

  /**
   * More unsafe operations on the environment, all of them internal to ZIO.
   */
  trait UnsafeAPI2 {
    private[ZEnvironment] def getOrElse[A](tag: LightTypeTag, default: => A)(implicit unsafe: Unsafe): A
  }

  /**
   * More unsafe operations on the environment, all of them internal to ZIO.
   */
  trait UnsafeAPI3 {
    private[zio] def addScope(scope: Scope)(implicit unsafe: Unsafe): ZEnvironment[R with Scope]
    private[ZEnvironment] def addService[A](tag: LightTypeTag, a: A)(implicit unsafe: Unsafe): ZEnvironment[R with A]
  }

  private def isScopeTag(tag: LightTypeTag): Boolean =
    (tag eq ScopeTag) || taggedIsSubtype(tag, ScopeTag)

  /**
   * The unsafe operations on this environment.
   */
  val unsafe: UnsafeAPI with UnsafeAPI2 with UnsafeAPI3 =
    new UnsafeAPI with UnsafeAPI2 with UnsafeAPI3 with Serializable {
      private[ZEnvironment] def add[A](tag: LightTypeTag, a: A)(implicit unsafe: Unsafe): ZEnvironment[R with A] =
        if (a.isInstanceOf[Scope] && isScopeTag(tag))
          addScope(a.asInstanceOf[Scope]).asInstanceOf[ZEnvironment[R with A]]
        else
          addService[A](tag, a)

      private[zio] def addScope(scope: Scope)(implicit unsafe: Unsafe): ZEnvironment[R with Scope] =
        if (scope eq self.scope) self.asInstanceOf[ZEnvironment[R with Scope]]
        else new ZEnvironment(map, cache = cache, scope = scope)

      private[ZEnvironment] def addService[A](tag: LightTypeTag, a: A)(implicit
        unsafe: Unsafe
      ): ZEnvironment[R with A] = {
        val newCache = new ConcurrentHashMap[LightTypeTag, Any]
        newCache.put(tag, a)
        new ZEnvironment(map.updated(tag, a), cache = newCache, scope = scope)
      }

      def get[A](tag: LightTypeTag)(implicit unsafe: Unsafe): A = {
        val value = getUnsafe[A](tag)
        if (value == null) throw new Error(s"Defect in zio.ZEnvironment: Could not find ${tag} inside ${self}")
        else value
      }

      private[ZEnvironment] def getOrElse[A](tag: LightTypeTag, default: => A)(implicit unsafe: Unsafe): A = {
        val value = getUnsafe[A](tag)
        if (value == null) default
        else value
      }

      private[this] def getUnsafe[A](tag: LightTypeTag): A = {
        val fromCache = self.cache.get(tag)
        if (fromCache != null)
          fromCache.asInstanceOf[A]
        else if ((scope ne null) && isScopeTag(tag))
          scope.asInstanceOf[A]
        else if (self.isEmpty && tag == TaggedAny)
          UnitAny.asInstanceOf[A]
        else {
          val it      = self.map.reverseIterator
          var service = null.asInstanceOf[A]
          while (it.hasNext && service == null) {
            val (curTag, entry) = it.next()
            if (taggedIsSubtype(curTag, tag)) {
              service = entry.asInstanceOf[A]
            }
          }
          if (service != null) {
            self.cache.putIfAbsent(tag, service)
          }
          service
        }
      }

      private[ZEnvironment] def update[A >: R](tag: LightTypeTag, f: A => A)(implicit
        unsafe: Unsafe
      ): ZEnvironment[R] =
        add[A](tag, f(get(tag)))
    }
}

/**
 * Constructors of environments holding zero to five services, and `Patch`,
 * which describes changes to an environment.
 */
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
    new ZEnvironment[Any](
      UpdateOrderLinkedMap.empty[LightTypeTag, Any],
      cache = new ConcurrentHashMap[LightTypeTag, Any],
      scope = null
    )

  @deprecated("kept for bin-compat only")
  private case object MissingService extends ControlThrowable

  // Can't use scala -> java collection conversions because they don't cross compile to Scala 2.12.
  @deprecated("Marked as deprecated to avoid usage in non-deprecated methods", "2.1.16")
  private def scalaToJucMap[K, V](map: collection.Map[K, V]): ConcurrentHashMap[K, V] = {
    val jucMap = new ConcurrentHashMap[K, V]
    map.foreach { case (k, v) => jucMap.put(k, v) }
    jucMap
  }

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
            case AddScope(scope)          => loop(env.unsafe.addScope(scope)(Unsafe), patches.tail)
            case AddService(service, tag) => loop(env.unsafe.addService(tag, service)(Unsafe), patches.tail)
            case AndThen(first, second)   => loop(env, erase(first) :: erase(second) :: patches.tail)
            case _ /* Empty[?] */         => loop(env, patches.tail)
          }

      val env0 = environment.asInstanceOf[ZEnvironment[Out]]
      if (isEmpty) env0
      else {
        val out = loop(environment, self.asInstanceOf[Patch[Any, Any]] :: Nil).asInstanceOf[ZEnvironment[Out]]
        if (env0.relaxedEquals(out)) env0 else out
      }
    }

    /**
     * Combines two patches to produce a new patch that describes applying the
     * updates from this patch and then the updates from the specified patch.
     */
    def combine[Out2](that: Patch[Out, Out2]): Patch[In, Out2] =
      AndThen(self, that)

    /**
     * Boolean flag indicating whether the patch is empty.
     *
     * It is `true` for `Patch.empty` and for a `Patch.diff` that finds no
     * change. A patch built with `combine` is not empty, even when it combines
     * empty patches.
     */
    def isEmpty: Boolean = self.isInstanceOf[Empty[?]]
  }

  /**
   * Constructors of patches: `empty` and `diff`.
   */
  object Patch {

    /**
     * An empty patch which returns the environment unchanged.
     */
    def empty[A]: Patch[A, A] = empty0.asInstanceOf[Patch[A, A]]

    /**
     * Constructs a patch that adds to an environment the services of `newValue`
     * that differ from those of `oldValue`, and the `Scope` of `newValue` when
     * it differs. The patch only adds, so applying it to `oldValue` keeps the
     * services that `newValue` lacks.
     *
     * Services are compared by value while both environments are scanned in the
     * order their services were added. A service that `newValue` holds under a
     * type that `oldValue` lacks can be compared with the most recently added
     * service of `oldValue`, and is left out of the patch when the two are
     * equal by `==`. For example, `diff(ZEnvironment[Int](8080),
     * ZEnvironment[Long](8080L))` is empty, so applying it adds no `Long`.
     *
     * {{{
     * val before: ZEnvironment[Int & String] = ZEnvironment[Int, String](8080, "localhost")
     * val after: ZEnvironment[Int]              = ZEnvironment(9090)
     *
     * val patched = ZEnvironment.Patch.diff(before, after)(before)
     * val port: Int = patched.get[Int]
     * // 9090
     * val host: Option[String] = patched.getDynamic[String]
     * // Some(localhost)
     * }}}
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

          while (oldIt.hasNext && oldTag != newTag) {
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

  private val ScopeTag: LightTypeTag =
    Scope.tag.tag

  private val TaggedAny: LightTypeTag =
    taggedTagType(EnvironmentTag[Any])

  // For some reason we get a SIGFAULT in Scala Native if we don't do this
  private val UnitAny: Any = ()
}
