/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import zio.blocking.Blocking
import zio.interop.javaz

import java.nio.channels.CompletionHandler
import java.util.concurrent.{ CompletableFuture, CompletionStage, Future }

private[zio] trait ZIOPlatformSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>
  def toCompletableFuture[A1 >: A](implicit ev: E <:< Throwable): URIO[R, CompletableFuture[A1]] =
    toCompletableFutureWith(ev)

  def toCompletableFutureWith[A1 >: A](f: E => Throwable): URIO[R, CompletableFuture[A1]] =
    self.mapError(f).fold(javaz.CompletableFuture_.failedFuture, CompletableFuture.completedFuture[A1])
}

private[zio] trait ZIOCompanionPlatformSpecific {

  def effectAsyncWithCompletionHandler[T](op: CompletionHandler[T, Any] => Any): Task[T] =
    javaz.effectAsyncWithCompletionHandler(op)

  def fromCompletionStage[A](cs: => CompletionStage[A]): Task[A] = javaz.fromCompletionStage(cs)

  /** WARNING: this uses the blocking Future#get, consider using `fromCompletionStage` */
  def fromFutureJava[A](future: => Future[A]): RIO[Blocking, A] = javaz.fromFutureJava(future)

}
