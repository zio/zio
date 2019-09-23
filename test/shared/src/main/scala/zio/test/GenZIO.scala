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
   * A generator of effects that are the result of chaining the specified
   * effect with itself a random number of times.
   */
  final def chained[R <: Random with Sized, Env, E, A](gen: Gen[R, ZIO[Env, E, A]]): Gen[R, ZIO[Env, E, A]] =
    Gen.small(chainedN(_)(gen))

  /**
   * A generator of effects that are the result of chaining the specified
   * effect with itself a given number of times.
   */
  final def chainedN[R <: Random, Env, E, A](n: Int)(zio: Gen[R, ZIO[Env, E, A]]): Gen[R, ZIO[Env, E, A]] =
    Gen.listOfN(n min 1)(zio).map(_.reduce(_ *> _))

  /**
   * A generator of effects that are the result of applying concurrency
   * combinators to the specified effect that are guaranteed not to change its
   * value.
   */
  final def concurrent[R, E, A](zio: ZIO[R, E, A]): Gen[Any, ZIO[R, E, A]] =
    Gen.const(zio.race(ZIO.never))

  /**
   * A generator of effects that have died with a `Throwable`.
   */
  final def died[R](gen: Gen[R, Throwable]): Gen[R, ZIO[Any, Nothing, Nothing]] =
    gen.map(ZIO.die)

  /**
   * A generator of effects that have failed with an error.
   */
  final def failures[R, E](gen: Gen[R, E]): Gen[R, ZIO[Any, E, Nothing]] =
    gen.map(ZIO.fail)

  /**
   * A generator of effects that are the result of applying parallelism
   * combinators to the specified effect that are guaranteed not to change its
   * value.
   */
  final def parallel[R, E, A](zio: ZIO[R, E, A]): Gen[Any, ZIO[R, E, A]] =
    successes(Gen.unit).map(_.zipParRight(zio))

  /**
   * A generator of successful effects.
   */
  final def successes[R, A](gen: Gen[R, A]): Gen[R, ZIO[Any, Nothing, A]] =
    gen.map(ZIO.succeed)
}
