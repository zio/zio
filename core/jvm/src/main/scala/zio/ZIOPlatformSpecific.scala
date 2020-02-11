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

import _root_.java.nio.channels.CompletionHandler
import _root_.java.util.concurrent.{ CompletableFuture, CompletionStage, Future }

import zio.blocking.Blocking
import zio.interop.javaz

private[zio] trait ZIOPlatformSpecific {

  def fromCompletionStage[A](cs: => CompletionStage[A]): Task[A] = javaz.fromCompletionStage(cs)

  /** WARNING: this uses the blocking Future#get, consider using `fromCompletionStage` */
  def fromFutureJava[A](future: => Future[A]): RIO[Blocking, A] = javaz.fromFutureJava(future)

  def toCompletableFuture[E, A](zio: ZIO[Any, E, A])(implicit ev: E <:< Throwable): UIO[CompletableFuture[A]] =
    toCompletableFutureWith(zio)(ev)

  def toCompletableFutureWith[R, E, A](zio: ZIO[Any, E, A])(f: E => Throwable): UIO[CompletableFuture[A]] =
    zio.mapError(f).fold(javaz.CompletableFuture_.failedFuture, CompletableFuture.completedFuture[A])

  def withCompletionHandler[T](op: CompletionHandler[T, Any] => Unit): Task[T] =
    javaz.withCompletionHandler(op)

}
