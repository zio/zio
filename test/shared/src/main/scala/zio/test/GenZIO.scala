/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio._
import zio.random.Random

trait GenZIO {

  /**
   * Constructs a generator of `IO[E, A]` effects given generators of `E` and
   * `A` values.
   */
  final def io[R <: Random, E, A](e: Gen[R, E], a: Gen[R, A]): Gen[R, IO[E, A]] =
    Gen.either(e, a).map(ZIO.fromEither(_))

  /**
   * Constructs a generator of `RIO[R, A]` effects given a generator of `A`
   * values.
   */
  final def rio[R <: Random, R1, A](a: Gen[R, A]): Gen[R, RIO[R1, A]] =
    Gen
      .function[R, R1, Either[Throwable, A]](Gen.either(Gen.throwable, a))
      .map(f => ZIO.fromFunctionM(r => ZIO.fromEither(f(r))))

  /**
   * Constructs a generator of `Task[A]` effects given a generator of `A`
   * values.
   */
  final def task[R <: Random, A](a: Gen[R, A]): Gen[R, Task[A]] =
    Gen.either(Gen.throwable, a).map(ZIO.fromEither(_))

  /**
   * Constructs a generator of `UIO[A]` effects given a generator of `A`
   * values.
   */
  final def uio[R <: Random, A](a: Gen[R, A]): Gen[R, UIO[A]] =
    a.map(ZIO.succeed)

  /**
   * Constructs a generator of `URIO[A]` effects given a generator of `A`
   * values.
   */
  final def urio[R <: Random, R1, A](a: Gen[R, A]): Gen[R, URIO[R1, A]] =
    Gen.function[R, R1, A](a).map(f => ZIO.fromFunction(f))

  /**
   * Constructs a generator of `ZIO[R, E, A]` effects given generators of
   * `E` and `A` values.
   */
  final def zio[R <: Random, R1, E, A](e: Gen[R, E], a: Gen[R, A]): Gen[R, ZIO[R1, E, A]] =
    Gen
      .function[R, R1, Either[E, A]](Gen.either(e, a))
      .map(f => ZIO.fromFunctionM(r => ZIO.fromEither(f(r))))
}
