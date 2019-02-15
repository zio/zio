/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

// Copyright (C) 2018 John A. De Goes. All rights reserved.

package scalaz

package object zio extends EitherCompat {
  private[zio] type Callback[E, A] = Exit[E, A] => Unit

  type Canceler = ZIO[Any, Nothing, _]
  type FiberId  = Long

  type IO[+E, +A] = ZIO[Any, E, A]
  type Task[+A]   = ZIO[Any, Throwable, A]
  type UIO[+A]    = ZIO[Any, Nothing, A]
}
