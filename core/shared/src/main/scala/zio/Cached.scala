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

/**
 * A [[Cached]] is a possibly resourceful value that is loaded into memory, and
 * which can be refreshed either manually or automatically.
 *
 * Create one with `Cached.manual`, which refreshes only when you call
 * `refresh`, or `Cached.auto`, which also refreshes according to a `Schedule`.
 * The acquisition effect runs once when the `Cached` is created. When the
 * `Scope` that the `Cached` was created in closes, the resources of the current
 * value are released.
 *
 * {{{
 * val program: ZIO[Scope, Nothing, (Int, Int)] =
 *   for {
 *     counter <- Ref.make(0)
 *     cached  <- Cached.manual(counter.updateAndGet(_ + 1))
 *     first   <- cached.get
 *     _       <- cached.refresh
 *     second  <- cached.get
 *   } yield (first, second)
 * // (1,2)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/resource/cached/ Cached]]
 */
sealed trait Cached[+Error, +Resource] {

  /**
   * Retrieves the current value stored in the cache. When the acquisition has
   * not succeeded yet, fails with its error. After a refresh fails, the value
   * from before that refresh is still returned.
   */
  def get(implicit trace: Trace): IO[Error, Resource]

  /**
   * Refreshes the cache. This method will not return until either the refresh
   * is successful, or the refresh operation fails.
   *
   * The resources of the previous value are released before the acquisition
   * effect runs again. When the refresh fails, `refresh` fails with the error
   * and the previous value stays in the cache. Interrupting `refresh` while the
   * acquisition effect is running takes effect after that effect finishes; the
   * cache then keeps the previous value, whose resources were already released,
   * and the resources of the new value stay acquired until the enclosing
   * `Scope` closes.
   */
  def refresh(implicit trace: Trace): IO[Error, Unit]
}

/** Constructors for [[Cached]] values. */
object Cached {

  /**
   * Creates a new [[Cached]] value that is automatically refreshed according to
   * the specified policy. Note that error retrying is not performed
   * automatically, so if you want to retry on errors, you should first apply
   * retry policies to the acquisition effect before passing it to this
   * constructor.
   *
   * The acquisition effect runs once when the `Cached` is created. The first
   * refresh happens after the first delay of the policy, and refreshing stops
   * when the `Scope` closes. A refresh that fails ends the automatic refreshing
   * (the previous value stays available through `get`), but a later call to
   * `refresh` still works.
   *
   * {{{
   * val program: ZIO[Scope, Nothing, Int] =
   *   for {
   *     counter <- Ref.make(0)
   *     cached  <- Cached.auto(counter.updateAndGet(_ + 1), Schedule.recurs(2))
   *     _       <- ZIO.sleep(100.millis)
   *     value   <- cached.get
   *   } yield value
   * // 3
   * }}}
   */
  def auto[R, Error, Resource](acquire: ZIO[R, Error, Resource], policy: Schedule[Any, Any, Any])(implicit
    trace: Trace
  ): ZIO[R with Scope, Nothing, Cached[Error, Resource]] =
    for {
      manual <- manual(acquire)
      _      <- ZIO.acquireRelease(ZIO.interruptible(manual.refresh.schedule(policy)).forkDaemon)(_.interrupt)
    } yield manual

  /**
   * Creates a new [[Cached]] value that must be manually refreshed by calling
   * the refresh method. Note that error retrying is not performed
   * automatically, so if you want to retry on errors, you should first apply
   * retry policies to the acquisition effect before passing it to this
   * constructor.
   *
   * The acquisition effect runs once when the `Cached` is created, with the
   * environment that the constructor was run with, and once more for each
   * `refresh`. When that first run fails, the constructor still succeeds, and
   * `get` fails with the error.
   */
  def manual[R, Error, Resource](
    acquire: ZIO[R, Error, Resource]
  )(implicit trace: Trace): ZIO[R with Scope, Nothing, Cached[Error, Resource]] =
    for {
      env <- ZIO.environment[R]
      ref <- ScopedRef.fromAcquire(acquire.exit)
    } yield Manual(ref, acquire.provideEnvironment(env))

  private final case class Manual[Error, Resource](
    ref: ScopedRef[Exit[Error, Resource]],
    acquire: ZIO[Scope, Error, Resource]
  ) extends Cached[Error, Resource] {
    def get(implicit trace: Trace): IO[Error, Resource] = ref.get.unexit

    def refresh(implicit trace: Trace): IO[Error, Unit] = ref.set[Any, Error](acquire.map(Exit.succeed(_)))
  }
}
