/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.Platform

/**
 * A `ZKeyedPool[Err, Key, Item]` holds a separate `ZPool` of items for each
 * key, such as one pool of connections per host. The pool of a key is created
 * the first time `get` is called with that key.
 *
 * {{{
 * val hosts: UIO[List[String]] =
 *   ZIO.scoped {
 *     for {
 *       counter <- Ref.make(0)
 *       pool <- ZKeyedPool.make(
 *                 (host: String) => counter.updateAndGet(_ + 1).map(n => s"\$host#\$n"),
 *                 2
 *               )
 *       first  <- ZIO.scoped(pool.get("db.example.com"))
 *       second <- ZIO.scoped(pool.get("cache.example.com"))
 *     } yield List(first, second)
 *   }
 * // List(db.example.com#1, cache.example.com#3)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/resource/zkeyedpool/ ZKeyedPool]]
 */
sealed trait ZKeyedPool[+Err, -Key, Item] {

  /**
   * Retrieves an item from the pool belonging to the given key in a scoped
   * effect. Note that if acquisition fails, then the returned effect will fail
   * for that same reason. Retrying a failed acquisition attempt will repeat the
   * acquisition attempt.
   *
   * The first `get` for a key creates the pool of that key. When every item of
   * the key is in use and its pool has reached its maximum size, `get` waits
   * until an item of that key is returned.
   */
  def get(key: Key)(implicit trace: Trace): ZIO[Scope, Err, Item]

  /**
   * Invalidates the specified item in the pools of all keys created so far, so
   * it affects the pool of each key that holds an item equal to it. Such a pool
   * does not release the item at once: it releases the item when the item is
   * returned, or when a later `get` for that key takes it from the pool. It
   * then acquires a replacement when the pool of that key held no more than its
   * minimum number of items or a `get` for that key is waiting; otherwise that
   * pool shrinks by one item.
   */
  def invalidate(item: Item)(implicit trace: Trace): UIO[Unit]

}

/**
 * Constructors of keyed pools, with a fixed size or a size range and time to
 * live, either the same for every key or computed per key.
 */
object ZKeyedPool {

  /**
   * Makes a new pool of the specified fixed size. The pool is returned in a
   * `Scope`, which governs the lifetime of the pool. When the pool is shutdown
   * because the `Scope` is closed, the individual items allocated by the pool
   * will be released in some unspecified order.
   *
   * The size applies to each key: the first `get` for a key creates a pool of
   * `size` items for that key.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], size: => Int)(implicit
    trace: Trace
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    make(get, _ => size)

  /**
   * Makes a new pool of the specified fixed size. The pool is returned in a
   * `Scope`, which governs the lifetime of the pool. When the pool is shutdown
   * because the `Scope` is closed, the individual items allocated by the pool
   * will be released in some unspecified order.
   *
   * The size of the underlying pools can be configured per key.
   *
   * The first `get` for a key creates a pool of `size(key)` items for that key.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], size: Key => Int)(implicit
    trace: Trace
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    makeWith(get, (key: Key) => { val s = size(key); s to s })(_ => None)

  /**
   * Makes a new pool with the specified minimum and maximum sizes and time to
   * live before a pool whose excess items are not being used will be shrunk
   * down to the minimum size. The pool is returned in a `Scope`, which governs
   * the lifetime of the pool. When the pool is shutdown because the `Scope` is
   * closed, the individual items allocated by the pool will be released in some
   * unspecified order.
   *
   * The range and time to live apply to each key: the first `get` for a key
   * creates a pool for that key, which shrinks the way `ZPool.make` with a
   * range and a time to live does.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](
    get: Key => ZIO[Env, Err, Item],
    range: => Range,
    timeToLive: => Duration
  )(implicit
    trace: Trace
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    make(get, _ => range, _ => timeToLive)

  /**
   * Makes a new pool with the specified minimum and maximum sizes and time to
   * live before a pool whose excess items are not being used will be shrunk
   * down to the minimum size. The pool is returned in a `Scope`, which governs
   * the lifetime of the pool. When the pool is shutdown because the `Scope` is
   * closed, the individual items allocated by the pool will be released in some
   * unspecified order.
   *
   * The size of the underlying pools can be configured per key.
   *
   * The first `get` for a key creates a pool for that key with `range(key)` and
   * `timeToLive(key)`, which shrinks the way `ZPool.make` with a range and a
   * time to live does.
   */
  def make[Key, Env: EnvironmentTag, Err, Item](
    get: Key => ZIO[Env, Err, Item],
    range: Key => Range,
    timeToLive: Key => Duration
  )(implicit
    trace: Trace
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    makeWith(get, range)((key: Key) => Some(timeToLive(key)))

  private def makeWith[Key, Env: EnvironmentTag, Err, Item](get: Key => ZIO[Env, Err, Item], range: Key => Range)(
    ttl: Key => Option[Duration]
  )(implicit
    trace: Trace
  ): ZIO[Env with Scope, Nothing, ZKeyedPool[Err, Key, Item]] =
    for {
      environment <- ZIO.environment[Env]
      fiberId     <- ZIO.fiberId
      map         <- ZIO.succeed(Platform.newConcurrentMap[Key, MapValue[Err, Item]]()(Unsafe.unsafe))
      scope       <- ZIO.scope
      getOrCreatePool = (key: Key) =>
                          ZIO.suspendSucceed {
                            var value = map.get(key)
                            if (value eq null) {
                              ZIO.uninterruptibleMask { restore =>
                                val promise = Promise.unsafe.make[Nothing, ZPool[Err, Item]](fiberId)(Unsafe.unsafe)
                                value = MapValue.Pending(promise)
                                val previous = map.putIfAbsent(key, value)
                                if (previous eq null) {
                                  restore(
                                    scope
                                      .extend(
                                        ZPool
                                          .make(
                                            get(key).provideSomeEnvironment[Scope](environment.union[Scope](_)),
                                            range(key),
                                            ttl(key).getOrElse(Duration.Infinity)
                                          )
                                      )
                                  ).foldCauseZIO(
                                    cause => {
                                      map.remove(key, value)
                                      promise.failCause(cause) *> ZIO.failCause(cause)
                                    },
                                    pool => {
                                      map.put(key, MapValue.Complete(pool))
                                      promise.succeed(pool).as(pool)
                                    }
                                  )
                                } else {
                                  previous match {
                                    case MapValue.Complete(pool)   => ZIO.succeed(pool)
                                    case MapValue.Pending(promise) => restore(promise.await)
                                  }
                                }
                              }
                            } else {
                              value match {
                                case MapValue.Complete(pool)   => ZIO.succeed(pool)
                                case MapValue.Pending(promise) => promise.await
                              }
                            }
                          }
      activePools = ZIO.suspendSucceed {
                      ZIO.foreach(Chunk.fromJavaIterator(map.values.iterator)) {
                        case MapValue.Complete(pool)   => ZIO.succeed(pool)
                        case MapValue.Pending(promise) => promise.await
                      }
                    }
    } yield DefaultKeyedPool(getOrCreatePool, activePools)

  private final case class DefaultKeyedPool[Err, Key, Item](
    getOrCreatePool: Key => ZIO[Any, Nothing, ZPool[Err, Item]],
    activePools: ZIO[Any, Nothing, Chunk[ZPool[Err, Item]]]
  ) extends ZKeyedPool[Err, Key, Item] {

    override def get(key: Key)(implicit trace: Trace): ZIO[Scope, Err, Item] =
      getOrCreatePool(key).flatMap(_.get)

    def invalidate(item: Item)(implicit trace: Trace): UIO[Unit] =
      activePools.flatMap(ZIO.foreachDiscard(_)(_.invalidate(item)))
  }

  private sealed trait MapValue[Err, Item]

  private object MapValue {
    final case class Complete[Err, Item](pool: ZPool[Err, Item])                     extends MapValue[Err, Item]
    final case class Pending[Err, Item](promise: Promise[Nothing, ZPool[Err, Item]]) extends MapValue[Err, Item]
  }
}
