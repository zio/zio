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
   * A generator of `Cause` values
   */
  final def causes[R <: Random with Sized, E](e: Gen[R, E], t: Gen[R, Throwable]): Gen[R, Cause[E]] = {
    val failure        = e.map(Cause.fail)
    val die            = t.map(Cause.die)
    val interrupt      = Gen.anyLong.map(Cause.interrupt)
    def traced(n: Int) = Gen.suspend(causesN(n - 1).map(Cause.Traced(_, ZTrace(0, Nil, Nil, None))))
    def meta(n: Int)   = Gen.suspend(causesN(n - 1).flatMap(c => Gen.elements(Cause.stack(c), Cause.stackless(c))))

    def sequential(n: Int) = Gen.suspend {
      for {
        i <- Gen.int(1, n - 1)
        l <- causesN(i)
        r <- causesN(n - i)
      } yield Cause.Then(l, r)
    }

    def parallel(n: Int) = Gen.suspend {
      for {
        i <- Gen.int(1, n - 1)
        l <- causesN(i)
        r <- causesN(n - i)
      } yield Cause.Both(l, r)
    }

    def causesN(n: Int): Gen[R, Cause[E]] = Gen.suspend {
      if (n == 1) Gen.oneOf(failure, die, interrupt)
      else if (n == 2) Gen.oneOf(traced(n), meta(n))
      else Gen.oneOf(traced(n), meta(n), sequential(n), parallel(n))
    }

    Gen.small(causesN, 1)
  }

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
