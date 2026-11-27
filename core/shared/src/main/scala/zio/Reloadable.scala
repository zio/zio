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

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A [[Reloadable]] is an implementation of some service that can be dynamically
 * reloaded, or swapped out for another implementation on-the-fly.
 *
 * {{{
 * val versions: ZIO[Any, Any, (String, String)] =
 *   Ref.make(0).flatMap { builds =>
 *     val config: ZLayer[Any, String, String] =
 *       ZLayer(builds.updateAndGet(_ + 1).map(n => s"config-v\$n"))
 *
 *     (for {
 *       first  <- Reloadable.get[String]
 *       _      <- Reloadable.reload[String]
 *       second <- Reloadable.get[String]
 *     } yield (first, second)).provide(Reloadable.manual(config))
 *   }
 * // (config-v1,config-v2)
 * }}}
 *
 * @param scopedRef
 *   the reference that holds the current service and the scope of its resources
 * @param reload
 *   rebuilds the service and stores the new one in `scopedRef`
 *
 * @see
 *   [[https://zio.dev/reference/service-pattern/reloadable-services/ Reloadable Services]]
 */
final case class Reloadable[Service](scopedRef: ScopedRef[Service], reload: IO[Any, Unit]) {

  /**
   * Retrieves the current version of the reloadable service.
   */
  def get(implicit trace: Trace): UIO[Service] = scopedRef.get

  /**
   * Forks the reload of the service in the background, ignoring any errors.
   *
   * The reload runs on a daemon fiber, and its failures are logged at the
   * `Debug` level.
   */
  def reloadFork(implicit trace: Trace): UIO[Unit] = reload.ignoreLogged.forkDaemon.unit
}

/**
 * Constructors of reloadable services from layers, and accessors for a
 * `Reloadable` service in the environment.
 */
object Reloadable {

  /**
   * Makes a new reloadable service from a layer that describes the construction
   * of a static service.
   *
   * Its `reload` releases the resources of the current service and then builds
   * the layer again. When that build fails, `reload` fails with the error and
   * `get` keeps returning the previous service, whose resources are already
   * released.
   */
  def manual[In, E, Out: Tag](
    layer: ZLayer[In, E, Out]
  )(implicit trace: Trace): ZLayer[In, E, Reloadable[Out]] =
    ZLayer.scoped[In] {
      for {
        in    <- ZIO.environment[In]
        ref   <- ScopedRef.fromAcquire(layer.build.map(_.get[Out]))
        reload = ref.set[In, E](layer.build.map(_.get[Out])).provideEnvironment(in)
      } yield Reloadable[Out](ref, reload)
    }

  /**
   * Makes a new reloadable service from a layer that describes the construction
   * of a static service. The service is automatically reloaded according to the
   * provided schedule.
   *
   * The first reload runs after the first delay of the schedule, not when the
   * layer is built. A failed reload is logged at the `Debug` level and does not
   * stop the schedule. The reloading fiber is interrupted when the scope of the
   * layer closes.
   */
  def auto[In, E, Out: Tag](layer: ZLayer[In, E, Out], schedule: Schedule[In, Any, Any])(implicit
    trace: Trace
  ): ZLayer[In, E, Reloadable[Out]] =
    ZLayer.scoped[In] {
      for {
        env       <- manual(layer).build
        reloadable = env.get[Reloadable[Out]]
        _ <- ZIO.acquireRelease(ZIO.interruptible(reloadable.reload.ignoreLogged.schedule(schedule).forkDaemon))(
               _.interrupt
             )
      } yield reloadable
    }

  /**
   * Makes a new reloadable service from a layer that describes the construction
   * of a static service. The service is automatically reloaded according to a
   * schedule, which is extracted from the input to the layer.
   */
  def autoFromConfig[In, E, Out: Tag](
    layer: ZLayer[In, E, Out],
    scheduleFromConfig: ZEnvironment[In] => Schedule[In, Any, Any]
  )(implicit
    trace: Trace
  ): ZLayer[In, E, Reloadable[Out]] =
    ZLayer.scoped[In] {
      for {
        in        <- ZIO.environment[In]
        schedule   = scheduleFromConfig(in)
        env       <- auto(layer, schedule).build
        reloadable = env.get[Reloadable[Out]]
      } yield reloadable
    }

  /**
   * Retrieves the current version of the service held by the
   * `Reloadable[Service]` in the environment.
   */
  def get[Service: Tag](implicit trace: Trace): ZIO[Reloadable[Service], Any, Service] =
    ZIO.serviceWithZIO[Reloadable[Service]](_.get)

  /**
   * Reloads the `Reloadable[Service]` in the environment, and fails when the
   * reload fails.
   */
  def reload[Service: Tag](implicit trace: Trace): ZIO[Reloadable[Service], Any, Unit] =
    ZIO.serviceWithZIO[Reloadable[Service]](_.reload)

  /**
   * Forks the reload of the `Reloadable[Service]` in the environment on a
   * daemon fiber, logging its failures at the `Debug` level.
   */
  def reloadFork[Service: Tag](implicit trace: Trace): ZIO[Reloadable[Service], Nothing, Unit] =
    ZIO.serviceWithZIO[Reloadable[Service]](_.reloadFork)
}
