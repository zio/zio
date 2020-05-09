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

package object zio extends EitherCompat with PlatformSpecific with VersionSpecific {
  private[zio] type Callback[E, A] = Exit[E, A] => Any

  type Canceler[-R] = URIO[R, Any]

  type RIO[-R, +A]  = ZIO[R, Throwable, A]
  type URIO[-R, +A] = ZIO[R, Nothing, A]
  type IO[+E, +A]   = ZIO[Any, E, A]
  type UIO[+A]      = ZIO[Any, Nothing, A]
  type Task[+A]     = ZIO[Any, Throwable, A]

  type RManaged[-R, +A]  = ZManaged[R, Throwable, A]
  type URManaged[-R, +A] = ZManaged[R, Nothing, A]
  type Managed[+E, +A]   = ZManaged[Any, E, A]
  type UManaged[+A]      = ZManaged[Any, Nothing, A]
  type TaskManaged[+A]   = ZManaged[Any, Throwable, A]

  val Managed: ZManaged.type = ZManaged

  type RLayer[-RIn, +ROut]  = ZLayer[RIn, Throwable, ROut]
  type URLayer[-RIn, +ROut] = ZLayer[RIn, Nothing, ROut]
  type Layer[+E, +ROut]     = ZLayer[Any, E, ROut]
  type ULayer[+ROut]        = ZLayer[Any, Nothing, ROut]
  type TaskLayer[+ROut]     = ZLayer[Any, Throwable, ROut]

  type Queue[A] = ZQueue[Any, Any, Nothing, Nothing, A, A]

  /**
   * A queue that can only be dequeued.
   */
  type Dequeue[+A] = ZQueue[Nothing, Any, Any, Nothing, Nothing, A]

  type Ref[A]      = ZRef[Nothing, Nothing, A, A]
  type ERef[+E, A] = ZRef[E, E, A, A]

  type RefM[A]      = ZRefM[Any, Any, Nothing, Nothing, A, A]
  type ERefM[+E, A] = ZRefM[Any, Any, E, E, A, A]

  object <*> {
    def unapply[A, B](ab: (A, B)): Some[(A, B)] =
      Some((ab._1, ab._2))
  }
}
