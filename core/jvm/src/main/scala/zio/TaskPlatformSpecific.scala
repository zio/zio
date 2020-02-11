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

import _root_.java.util.concurrent.{ CompletableFuture }

import zio.interop.javaz

private[zio] trait TaskPlatformSpecific {

  implicit class TaskCompletableFutureOps[A](private val io: Task[A]) {
    def toCompletableFuture: UIO[CompletableFuture[A]] =
      io.fold(javaz.CompletableFuture_.failedFuture, CompletableFuture.completedFuture[A])
  }

}
