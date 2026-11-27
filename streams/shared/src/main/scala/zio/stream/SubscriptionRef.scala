/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

package zio.stream

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `SubscriptionRef[A]` is a `Ref.Synchronized` that can be subscribed to in
 * order to receive the current value as well as all changes to the value. Use
 * it for shared state that one or more observers react to.
 *
 * Read and update it with the usual `Ref` operations (`get`, `set`, `update`,
 * `modify`, `updateZIO`, ...). Each run of `changes` emits the value current
 * when the run starts, followed by the value written by every later update, in
 * order. Every update publishes the value it writes, even when it equals the
 * previous value; an update whose effect fails publishes nothing.
 *
 * {{{
 * import zio._
 *
 * val program: UIO[Chunk[Int]] =
 *   for {
 *     ref     <- SubscriptionRef.make(0)
 *     started <- Promise.make[Nothing, Unit]
 *     fiber   <- ref.changes
 *                  .tap(_ => started.succeed(()))
 *                  .take(3)
 *                  .runCollect
 *                  .fork
 *     _       <- started.await
 *     _       <- ref.set(1)
 *     _       <- ref.update(_ + 1)
 *     values  <- fiber.join
 *   } yield values
 * // program succeeds with Chunk(0, 1, 2)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/stream/subscription-ref/ SubscriptionRef]]
 */
sealed trait SubscriptionRef[A] extends Ref.Synchronized.Internal[A] {

  /**
   * A stream containing the current value of the `Ref` as well as all changes
   * to that value. Each run emits the value current when the run starts, then
   * the value written by each later update. The stream does not end on its own.
   */
  def changes: ZStream[Any, Nothing, A]
}

/**
 * Constructors of [[SubscriptionRef]].
 */
object SubscriptionRef {

  /**
   * Creates a new `SubscriptionRef` with the specified value.
   */
  def make[A](a: => A)(implicit trace: Trace): UIO[SubscriptionRef[A]] =
    for {
      hub       <- Hub.unbounded[A]
      ref       <- Ref.make(a)
      semaphore <- Semaphore.make(1)
    } yield new SubscriptionRef[A] {
      def changes: ZStream[Any, Nothing, A] =
        ZStream.unwrapScoped {
          semaphore.withPermit {
            ref.get.flatMap { a =>
              ZStream.fromHubScoped(hub).map { stream =>
                ZStream(a) ++ stream
              }
            }
          }
        }
      def get(implicit trace: Trace): UIO[A] =
        ref.get
      def modifyZIO[R, E, B](f: A => ZIO[R, E, (B, A)])(implicit trace: Trace): ZIO[R, E, B] =
        semaphore.withPermit(ref.get.flatMap(f).flatMap { case (b, a) => ref.set(a).as(b) <* hub.publish(a) })
      def set(a: A)(implicit trace: Trace): UIO[Unit] =
        semaphore.withPermit(ref.set(a) <* hub.publish(a))
      def setAsync(a: A)(implicit trace: Trace): UIO[Unit] =
        semaphore.withPermit(ref.setAsync(a) <* hub.publish(a))
    }
}
