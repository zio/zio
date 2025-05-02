/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package object stream {
  type Stream[+E, +A] = ZStream[Any, E, A]

  type UStream[+A] = ZStream[Any, Nothing, A]

  type Sink[+OutErr, -In, +L, +Z] = ZSink[Any, OutErr, In, L, Z]

  private[stream] def unfoldPull[R, A](
    runtime: Runtime[R],
    pull: ZIO[R, Option[Throwable], A]
  )(implicit trace: Trace, unsafe: Unsafe): Iterator[A] =
    runtime.unsafe.run(pull) match {
      case Exit.Success(value) =>
        Iterator.single(value) ++ unfoldPull(runtime, pull)
      case Exit.Failure(cause) =>
        cause.failureOrCause match {
          case Left(None)    => Iterator.empty
          case Left(Some(e)) => Exit.fail(e).getOrThrow()
          case Right(c)      => Exit.failCause(c).getOrThrowFiberFailure()
        }
    }
}
