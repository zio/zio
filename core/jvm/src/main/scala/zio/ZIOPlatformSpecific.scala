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
import _root_.java.util.concurrent.{ CompletionStage, Future }

import zio.blocking.Blocking
import zio.interop.javaz

private[zio] trait ZIOPlatformSpecific {

  implicit class ZioObjJavaconcurrentOps(private val zioObj: ZIO.type) {
    def withCompletionHandler[T](op: CompletionHandler[T, Any] => Unit): Task[T] =
      javaz.withCompletionHandler(op)

    def fromCompletionStage[A](csUio: UIO[CompletionStage[A]]): Task[A] = javaz.fromCompletionStage(csUio)

    /** WARNING: this uses the blocking Future#get, consider using `fromCompletionStage` */
    def fromFutureJava[A](futureUio: UIO[Future[A]]): RIO[Blocking, A] = javaz.fromFutureJava(futureUio)
  }

}
