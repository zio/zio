/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

import scala.collection.immutable.LongMap

trait Scope {
  def addFinalizer(finalizer: Exit[Any, Any] => UIO[Any]): UIO[Unit]
  def close(exit: Exit[Any, Any]): UIO[Unit]
  def use[R, E, A](zio: ZIO[Scope with R, E, A]): ZIO[R, E, A]
}

object Scope {

  type Finalizer = Exit[Any, Any] => UIO[Any]

  def addFinalizer(finalizer: Exit[Any, Any] => UIO[Any]): ZIO[Scope, Nothing, Unit] =
    ZIO.serviceWithZIO(_.addFinalizer(finalizer))

  def make: UIO[Scope] =
    makeWith(ExecutionStrategy.Sequential)

  def parallel: UIO[Scope] =
    makeWith(ExecutionStrategy.Parallel)

  def makeWith(executionStrategy: ExecutionStrategy): UIO[Scope] =
    ReleaseMap.make.map { releaseMap =>
      new Scope { self =>
        def addFinalizer(finalizer: Exit[Any, Any] => UIO[Any]): UIO[Unit] =
          releaseMap.add(finalizer).unit

        def close(exit: Exit[Any, Any]): UIO[Unit] =
          releaseMap.releaseAll(exit, executionStrategy).unit

        def use[R, E, A](zio: ZIO[Scope with R, E, A]): ZIO[R, E, A] =
          zio.provideSomeEnvironment[R](_ ++ [Scope] ZEnvironment(self)).onExit(self.close(_))
      }
    }

  private sealed abstract class State
  private final case class Exited(nextKey: Long, exit: Exit[Any, Any], update: Finalizer => Finalizer) extends State
  private final case class Running(nextKey: Long, finalizers: LongMap[Finalizer], update: Finalizer => Finalizer)
      extends State

  /**
   * A `ReleaseMap` represents the finalizers associated with a scope.
   *
   * The design of `ReleaseMap` is inspired by ResourceT, written by Michael
   * Snoyman @snoyberg.
   * (https://github.com/snoyberg/conduit/blob/master/resourcet/Control/Monad/Trans/Resource/Internal.hs)
   */
  private abstract class ReleaseMap extends Serializable {

    /**
     * An opaque identifier for a finalizer stored in the map.
     */
    type Key

    /**
     * Adds a finalizer to the finalizers associated with this scope. If the
     * finalizers associated with this scope have already been run this
     * finalizer will be run immediately.
     *
     * The finalizer returned from this method will remove the original
     * finalizer from the map and run it.
     */
    def add(finalizer: Finalizer)(implicit trace: ZTraceElement): UIO[Finalizer]

    /**
     * Adds a finalizer to the finalizers associated with this scope. If the
     * scope is still open, a [[Key]] will be returned. This is an opaque
     * identifier that can be used to activate this finalizer and remove it from
     * the map. from the map. If the scope has been closed, the finalizer will
     * be executed immediately (with the [[Exit]] value with which the scope has
     * ended) and no Key will be returned.
     */
    def addIfOpen(finalizer: Finalizer)(implicit trace: ZTraceElement): UIO[Option[Key]]

    /**
     * Retrieves the finalizer associated with this key.
     */
    def get(key: Key)(implicit trace: ZTraceElement): UIO[Option[Finalizer]]

    /**
     * Runs the specified finalizer and removes it from the finalizers
     * associated with this scope.
     */
    def release(key: Key, exit: Exit[Any, Any])(implicit trace: ZTraceElement): UIO[Any]

    /**
     * Runs the finalizers associated with this scope using the specified
     * execution strategy. After this action finishes, any finalizers added to
     * this scope will be run immediately.
     */
    def releaseAll(exit: Exit[Any, Any], execStrategy: ExecutionStrategy)(implicit trace: ZTraceElement): UIO[Any]

    /**
     * Removes the finalizer associated with this key and returns it.
     */
    def remove(key: Key)(implicit trace: ZTraceElement): UIO[Option[Finalizer]]

    /**
     * Replaces the finalizer associated with this key and returns it. If the
     * finalizers associated with this scope have already been run this
     * finalizer will be run immediately.
     */
    def replace(key: Key, finalizer: Finalizer)(implicit trace: ZTraceElement): UIO[Option[Finalizer]]

    /**
     * Updates the finalizers associated with this scope using the specified
     * function.
     */
    def updateAll(f: Finalizer => Finalizer)(implicit trace: ZTraceElement): UIO[Unit]
  }

  private object ReleaseMap {

    /**
     * Creates a new ReleaseMap.
     */
    def make(implicit trace: ZTraceElement): UIO[ReleaseMap] =
      ZIO.succeed(unsafeMake())

    /**
     * Creates a new ReleaseMap.
     */
    private[zio] def unsafeMake() = {
      // The sorting order of the LongMap uses bit ordering (000, 001, ... 111 but with 64 bits). This
      // works out to be `0 ... Long.MaxValue, Long.MinValue, ... -1`. The order of the map is mainly
      // important for the finalization, in which we want to walk it in reverse order. So we insert
      // into the map using keys that will build it in reverse. That way, when we do the final iteration,
      // the finalizers are already in correct order.
      val initialKey: Long = -1L
      def next(l: Long) =
        if (l == 0L) throw new RuntimeException("ReleaseMap wrapped around")
        else if (l == Long.MinValue) Long.MaxValue
        else l - 1

      val ref: Ref[State] =
        Ref.unsafeMake(Running(initialKey, LongMap.empty, identity))

      new ReleaseMap {
        type Key = Long

        def add(finalizer: Finalizer)(implicit trace: ZTraceElement): UIO[Finalizer] =
          addIfOpen(finalizer).map {
            case Some(key) => release(key, _)
            case None      => _ => UIO.unit
          }

        def addIfOpen(finalizer: Finalizer)(implicit trace: ZTraceElement): UIO[Option[Key]] =
          ref.modify {
            case Exited(nextKey, exit, update) =>
              finalizer(exit).as(None) -> Exited(next(nextKey), exit, update)
            case Running(nextKey, fins, update) =>
              UIO.succeed(Some(nextKey)) -> Running(next(nextKey), fins + (nextKey -> finalizer), update)
          }.flatten

        def get(key: Key)(implicit trace: ZTraceElement): UIO[Option[Finalizer]] =
          ref.get.map {
            case Exited(_, _, _)     => None
            case Running(_, fins, _) => fins get key
          }

        def release(key: Key, exit: Exit[Any, Any])(implicit trace: ZTraceElement): UIO[Any] =
          ref.modify {
            case s @ Exited(_, _, _) => (UIO.unit, s)
            case s @ Running(_, fins, update) =>
              (
                fins.get(key).fold(UIO.unit: UIO[Any])(fin => update(fin)(exit)),
                s.copy(finalizers = fins - key)
              )
          }.flatten

        def releaseAll(exit: Exit[Any, Any], execStrategy: ExecutionStrategy)(implicit trace: ZTraceElement): UIO[Any] =
          ref.modify {
            case s @ Exited(_, _, _) => (UIO.unit, s)
            case Running(nextKey, fins, update) =>
              execStrategy match {
                case ExecutionStrategy.Sequential =>
                  (
                    ZIO
                      .foreach(fins: Iterable[(Long, Finalizer)]) { case (_, fin) =>
                        update(fin).apply(exit).exit
                      }
                      .flatMap(results => ZIO.done(Exit.collectAll(results) getOrElse Exit.unit)),
                    Exited(nextKey, exit, update)
                  )

                case ExecutionStrategy.Parallel =>
                  (
                    ZIO
                      .foreachPar(fins: Iterable[(Long, Finalizer)]) { case (_, finalizer) =>
                        update(finalizer)(exit).exit
                      }
                      .flatMap(results => ZIO.done(Exit.collectAllPar(results) getOrElse Exit.unit)),
                    Exited(nextKey, exit, update)
                  )

                case ExecutionStrategy.ParallelN(n) =>
                  (
                    ZIO
                      .foreachPar(fins: Iterable[(Long, Finalizer)]) { case (_, finalizer) =>
                        update(finalizer)(exit).exit
                      }
                      .flatMap(results => ZIO.done(Exit.collectAllPar(results) getOrElse Exit.unit))
                      .withParallelism(n),
                    Exited(nextKey, exit, update)
                  )

              }
          }.flatten

        def remove(key: Key)(implicit trace: ZTraceElement): UIO[Option[Finalizer]] =
          ref.modify {
            case Exited(nk, exit, update)  => (None, Exited(nk, exit, update))
            case Running(nk, fins, update) => (fins get key, Running(nk, fins - key, update))
          }

        def replace(key: Key, finalizer: Finalizer)(implicit trace: ZTraceElement): UIO[Option[Finalizer]] =
          ref.modify {
            case Exited(nk, exit, update) => (finalizer(exit).as(None), Exited(nk, exit, update))
            case Running(nk, fins, update) =>
              (UIO.succeed(fins get key), Running(nk, fins + (key -> finalizer), update))
          }.flatten

        def updateAll(f: Finalizer => Finalizer)(implicit trace: ZTraceElement): UIO[Unit] =
          ref.update {
            case Exited(key, exit, update)  => Exited(key, exit, update.andThen(f))
            case Running(key, exit, update) => Running(key, exit, update.andThen(f))
          }
      }
    }
  }
}
