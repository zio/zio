/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

import zio.{IO, UIO}

/**
 * A `Result[-I, +E, +A]` represents the value or failure that will be returned
 * by mock expectation when invoked.
 */
sealed abstract class Result[-I, +E, +A] {
  val io: I => IO[E, A]
}

object Result {

  protected[mock] final case class Succeed[-I, +A](io: I => UIO[A])      extends Result[I, Nothing, A]
  protected[mock] final case class Fail[-I, +E](io: I => IO[E, Nothing]) extends Result[I, E, Nothing]
}
