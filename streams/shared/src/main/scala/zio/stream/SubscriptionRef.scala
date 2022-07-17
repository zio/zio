/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
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
 * A `SubscriptionRef[A]` is a `Ref` that can be subscribed to in order to
 * receive the current value as well as all changes to the value.
 */
trait SubscriptionRef[A] extends Ref.Synchronized[A] {

  /**
   * A stream containing the current value of the `Ref` as well as all changes
   * to that value.
   */
  def changes: ZStream[Any, Nothing, A]
}

object SubscriptionRef {

  /**
   * Creates a new `SubscriptionRef` with the specified value.
   */
  def make[A](a: => A)(implicit trace: Trace): UIO[SubscriptionRef[A]] =
    for {
      ref <- Ref.Synchronized.make(a)
      hub <- Hub.unbounded[A]
    } yield new SubscriptionRef[A] {
      def changes: ZStream[Any, Nothing, A] =
        ZStream.unwrapScoped {
          ref.modifyZIO { a =>
            ZStream.fromHubScoped(hub).map { stream =>
              (ZStream(a) ++ stream, a)
            }
          }
        }
      def get(implicit trace: Trace): UIO[A] =
        ref.get
      def modifyZIO[R, E, B](f: A => ZIO[R, E, (B, A)])(implicit trace: Trace): ZIO[R, E, B] =
        ref.modifyZIO(a => f(a).tap { case (_, a) => hub.publish(a) })
      def set(a: A)(implicit trace: Trace): UIO[Unit] =
        ref.modifyZIO(_ => hub.publish(a).as((), a))
      def setAsync(a: A)(implicit trace: Trace): UIO[Unit] =
        ref.modifyZIO(_ => hub.publish(a).as((), a))
    }
}
