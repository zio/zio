/*
 * Copyright 2017-2023 John A. De Goes and the ZIO Contributors
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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

/**
 * A `Gen[+R, +A]` is a generator of values of type `A`, which requires an
 * environment `R` and may fail with a `TestFailure`.
 *
 * Generators are immutable, and composed using the many combinators provided by
 * this module.
 */
final class Gen[+R, +A] private (private val sample: ZIO[R with Random, Nothing, Sample[A]])
    extends GenSyntax {

  /**
   * Maps the specified function over the generated values.
   */
  final def map[B](f: A => B): Gen[R, B] =
    flatMap(a => Gen.const(f(a)))

  /**
   * Maps the specified function over the generated values. The function may
   * require an environment `R0`.
   */
  final def mapM[R0 <: R, B](f: A => ZIO[R0, Nothing, B]): Gen[R0, B] =
    flatMap(a => Gen.fromZIO(f(a)))

  /**
   * Combines this generator with the specified generator.
   */
  final def zip[R1 <: R, B](that: Gen[R1, B]): Gen[R1, (A, B)] =
    zipWith(that)((_, _))

  /**
   * Combines this generator with the specified generator using the specified
   * function.
   */
  final def zipWith[R1 <: R, B, C](that: Gen[R1, B])(f: (A, B) => C): Gen[R1, C] =
    Gen.sized { size =>
      for {
        sampleA <- this.sample
        sampleB <- that.sample
        sample  <- Sample.zipWith(sampleA, sampleB)(f)
      } yield sample
    }

  /**
   * Combines this generator with the specified generator using the specified
   * function, in parallel.
   */
  final def zipWithPar[R1 <: R, B, C](that: Gen[R1, B])(f: (A, B) => C): Gen[R1, C] =
    Gen.sized { size =>
      ZIO
        .zipWithPar(sample, that.sample)(Sample.zipWith(_, _)(f))
        .mapError(_ => UnhandledError("zipWithPar failed"))
    }

  /**
   * Repeats generation of values using this generator until the generated value
   * satisfies the specified predicate.
   */
  final def retryUntil(predicate: A => Boolean): Gen[R, A] =
    Gen.sized { size =>
      def loop: ZIO[R with Random, Nothing, Sample[A]] =
        sample.flatMap { sample =>
          if (predicate(sample.value)) ZIO.succeed(sample)
          else loop
        }
      loop
    }

  /**
   * Repeats generation of values using this generator until the generated value
   * satisfies the specified effectual predicate.
   */
  final def retryUntilM[R1 <: R](predicate: A => ZIO[R1, Nothing, Boolean]): Gen[R1, A] =
    Gen.sized { size =>
      def loop: ZIO[R1 with Random, Nothing, Sample[A]] =
        sample.flatMap { sample =>
          predicate(sample.value).flatMap {
            case true  => ZIO.succeed(sample)
            case false => loop
          }
        }
      loop
    }

  /**
   * Repeats generation of values using this generator while the generated value
   * satisfies the specified predicate.
   */
  final def retryWhile(predicate: A => Boolean): Gen[R, A] =
    retryUntil(!predicate(_))

  /**
   * Repeats generation of values using this generator while the generated value
   * satisfies the specified effectual predicate.
   */
  final def retryWhileM[R1 <: R](predicate: A => ZIO[R1, Nothing, Boolean]): Gen[R1, A] =
    retryUntilM(a => predicate(a).map(!))

  /**
   * Randomly samples `n` values from this generator.
   */
  final def sampleN(n: Int): ZIO[R, Nothing, Chunk[A]] =
    ZIO
      .collectAll(Chunk.fill(n)(sample))
      .map(_.map(_.value))

  /**
   * Transforms a generator of optional values into a generator of values by
   * filtering out `None` values.
   */
  final def compact[B](implicit ev: A <:< Option[B]): Gen[R, B] =
    flatMap {
      case Some(b) => Gen.const(b)
      case None    => Gen.fail
    }

  /**
   * Flattens a generator of generators into a generator.
   */
  final def flatten[R1 <: R, B](implicit ev: A <:< Gen[R1, B]): Gen[R1, B] =
    flatMap(ev)

  /**
   * Maps the specified function over the generated values, choosing the size
   * parameter.
   */
  final def flatMap[R1 <: R, B](f: A => Gen[R1, B]): Gen[R1, B] =
    Gen.sized { size =>
      sample.flatMap { sample =>
        f(sample.value).sample.map { sample2 =>
          Sample(sample2.value, size => sample.shrink(size).flatMap(s => f(s).sample.map(_.shrink(size))).flatten)
        }
      }
    }

  /**
   * Maps the specified function over the generated values, choosing the size
   * parameter, in parallel.
   */
  final def flatMapPar[R1 <: R, B](f: A => Gen[R1, B]): Gen[R1, B] =
    Gen.sized { size =>
      sample.flatMap { sample =>
        ZIO
          .foreachPar(sample.value) { a =>
            f(a).sample
          }
          .map { samples =>
            Sample(
              samples.head.value,
              size => samples.flatMap(_.shrink(size))
            )
          }
      }
    }

  /**
   * Filters the generated values using the specified predicate.
   */
  final def filter(predicate: A => Boolean): Gen[R, A] =
    retryUntil(predicate)

  /**
   * Filters the generated values using the specified effectual predicate.
   */
  final def filterM[R1 <: R](predicate: A => ZIO[R1, Nothing, Boolean]): Gen[R1, A] =
    retryUntilM(predicate)

  /**
   * Returns a generator that constantly generates the specified value.
   */
  final def unit: Gen[R, Unit] =
    Gen.const(())

  /**
   * Returns a generator that generates elements from the specified sequence.
   */
  final def elements[B >: A, C](bs: C*): Gen[R, C] =
    Gen.elements(bs: _*)

  /**
   * Returns a generator that generates one of the specified generators.
   */
  final def oneOf[R1 <: R, B >: A](gens: Gen[R1, B]*): Gen[R1, B] =
    Gen.oneOf(gens: _*)

  /**
   * Returns a generator that generates one of the specified generators, with
   * the specified weights.
   */
  final def oneOf[R1 <: R, B >: A](gens: (Double, Gen[R1, B])*): Gen[R1, B] =
    Gen.oneOf(gens: _*)

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator.
   */
  final def orElse[R1 <: R, B >: A](that: => Gen[R1, B]): Gen[R1, B] =
    oneOf(this, that)

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator, in parallel.
   */
  final def orElsePar[R1 <: R, B >: A](that: => Gen[R1, B]): Gen[R1, B] =
    Gen.sized { size =>
      ZIO
        .race(sample, that.sample)
        .map {
          case Left(sample)  => sample
          case Right(sample) => sample
        }
        .mapError(_ => UnhandledError("orElsePar failed"))
    }

  /**
   * Provides this generator with its required environment, which eliminates its
   * dependency on `R`.
   */
  final def provide(r: R): Gen[Any, A] =
    new Gen(sample.provideSome[Random](_.union(r)))

  /**
   * Provides this generator with the part of its required environment that is
   * not provided by the specified `Gen`.
   */
  final def provideSome[R0](f: R0 => R): Gen[R0, A] =
    new Gen(sample.provideSome[R0 with Random](env => env.union(f(env.get[Random]))))

  /**
   * Provides this generator with the part of its required environment that is
   * not provided by the specified `Gen`, leaving the remainder `R0`.
   */
  final def provideSomeLayer[R0, R1](layer: ZLayer[R0, Nothing, R1])(implicit
    ev: R <:< R1
  ): Gen[R0, A] =
    new Gen(sample.provideSomeLayer[R0](layer))

  /**
   * Provides this generator with its required environment, which eliminates its
   * dependency on `R`.
   */
  final def provideLayer[R1](layer: ZLayer[Any, Nothing, R])(implicit ev: R <:< R1): Gen[Any, A] =
    new Gen(sample.provideLayer(layer))

  /**
   * Provides this generator with part of its required environment, leaving the
   * remainder `R0`.
   */
  final def provideSomeEnvironment[R0](f: ZEnvironment[R0] => ZEnvironment[R]): Gen[R0, A] =
    new Gen(sample.provideSomeEnvironment(env => env.union(f(env).get[Random])))

  /**
   * Provides this generator with part of its required environment, leaving the
   * remainder `R0`.
   */
  final def provideSomeLayerEnvironment[R0, R1](
    f: ZEnvironment[R0] => ZEnvironment[R1]
  )(implicit ev: R <:< R1): Gen[R0, A] =
    new Gen(sample.provideSomeLayerEnvironment(f))

  /**
   * Provides this generator with its required environment, which eliminates its
   * dependency on `R`.
   */
  final def provideEnvironment(r: ZEnvironment[R]): Gen[Any, A] =
    new Gen(sample.provideSomeEnvironment(_ => r.union(r.get[Random])))

  /**
   * Maps the environment of this generator.
   */
  final def mapEnvironment[R0](f: ZEnvironment[R0] => ZEnvironment[R]): Gen[R0, A] =
    new Gen(sample.mapEnvironment(env => env.union(f(env).get[Random])))

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator, using the specified function to combine the results.
   */
  final def zipWithOrElse[R1 <: R, B >: A, C](
    that: => Gen[R1, B]
  )(f: (A, B) => C): Gen[R1, C] =
    zipWith(that)(f).orElse(Gen.fail)

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator, using the specified function to combine the results,
   * in parallel.
   */
  final def zipWithOrElsePar[R1 <: R, B >: A, C](
    that: => Gen[R1, B]
  )(f: (A, B) => C): Gen[R1, C] =
    zipWithPar(that)(f).orElsePar(Gen.fail)

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator, using the specified function to combine the results.
   */
  final def zipOrElse[R1 <: R, B >: A](that: => Gen[R1, B]): Gen[R1, (A, B)] =
    zipWithOrElse(that)((_, _))

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator, using the specified function to combine the results,
   * in parallel.
   */
  final def zipOrElsePar[R1 <: R, B >: A](that: => Gen[R1, B]): Gen[R1, (A, B)] =
    zipWithOrElsePar(that)((_, _))

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator, using the specified function to combine the results.
   */
  final def zipWithOrElseM[R1 <: R, B >: A, C](
    that: => Gen[R1, B]
  )(f: (A, B) => ZIO[R1, Nothing, C]): Gen[R1, C] =
    zipWithM(that)(f).orElse(Gen.fail)

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator, using the specified function to combine the results.
   */
  final def zipOrElseM[R1 <: R, B >: A](that: => Gen[R1, B]): Gen[R1, (A, B)] =
    zipWithOrElseM(that)((_, _))

  /**
   * Maps the specified function over the generated values, requiring no
   * environment.
   */
  final def mapIO[B](f: A => ZIO[R, Nothing, B]): Gen[R, B] =
    mapM(f)

  /**
   * Maps the specified function over the generated values, requiring no
   * environment, in parallel.
   */
  final def mapIOPar[B](f: A => ZIO[R, Nothing, B]): Gen[R, B] =
    flatMapPar(a => Gen.fromZIO(f(a)))

  /**
   * Maps the specified function over the generated values, requiring no
   * environment.
   */
  final def mapZIO[B](f: A => ZIO[R, Nothing, B]): Gen[R, B] =
    mapM(f)

  /**
   * Maps the specified function over the generated values, requiring no
   * environment, in parallel.
   */
  final def mapZIOPar[B](f: A => ZIO[R, Nothing, B]): Gen[R, B] =
    flatMapPar(a => Gen.fromZIO(f(a)))

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator, using the specified function to combine the results.
   */
  final def zipWithOrElseZIO[R1 <: R, B >: A, C](
    that: => Gen[R1, B]
  )(f: (A, B) => ZIO[R1, Nothing, C]): Gen[R1, C] =
    zipWithOrElseM(that)(f)

  /**
   * Returns a generator that generates values from this generator or the
   * specified generator, using the specified function to combine the results.
   */
  final def zipOrElseZIO[R1 <: R, B >: A](that: => Gen[R1, B]): Gen[R1, (A, B)] =
    zipOrElseM(that)

  private[zio] def widen[B >: A]: Gen[R, B] = this.asInstanceOf[Gen[R, B]]
}

object Gen extends GenLowPriority {

  /**
   * A generator that fails with the specified failure.
   */
  def fail: Gen[Any, Nothing] =
    new Gen(ZIO.succeed(Sample.fail))

  /**
   * A generator that constantly generates the specified value.
   */
  def const[A](a: => A): Gen[Any, A] =
    new Gen(ZIO.succeed(Sample(a, _ => Chunk.empty)))

  /**
   * A generator that generates values from the specified effect.
   */
  def fromZIO[R, A](zio: ZIO[R, Nothing, A]): Gen[R, A] =
    new Gen(zio.map(value => Sample(value, _ => Chunk.empty)))

  /**
   * A generator that generates values from the specified effect, which may
   * fail.
   */
  def fromZIOFail[R, E, A](zio: ZIO[R, E, A]): Gen[R, A] =
    new Gen(zio.either.map {
      case Left(_)  => Sample.fail
      case Right(a) => Sample(a, _ => Chunk.empty)
    })

  /**
   * A generator that generates values from the specified function, which may
   * fail.
   */
  def fromFunction[R, A](f: Random with R => A): Gen[R, A] =
    new Gen(ZIO.succeed(Sample(f, _ => Chunk.empty)))

  /**
   * A generator that generates values from the specified function, which may
   * fail, and which may require an environment.
   */
  def fromFunctionM[R, A](f: Random with R => ZIO[R, Nothing, A]): Gen[R, A] =
    new Gen(f.andThen(_.map(value => Sample(value, _ => Chunk.empty))))

  /**
   * A generator that generates values from the specified function, which may
   * fail, and which may require an environment, in parallel.
   */
  def fromFunctionMPar[R, A](f: Random with R => ZIO[R, Nothing, A]): Gen[R, A] =
    new Gen(ZIO.succeedM(f).flatMapPar(identity).map(value => Sample(value, _ => Chunk.empty)))

  /**
   * A generator that generates values from the specified function, which may
   * fail, and which may require an environment.
   */
  def fromFunctionZIO[R, A](f: Random with R => ZIO[R, Nothing, A]): Gen[R, A] =
    fromFunctionM(f)

  /**
   * A generator that generates values from the specified function, which may
   * fail, and which may require an environment, in parallel.
   */
  def fromFunctionZIOPar[R, A](f: Random with R => ZIO[R, Nothing, A]): Gen[R, A] =
    fromFunctionMPar(f)

  /**
   * A generator that generates values from the specified function, which may
   * fail, and which may require an environment.
   */
  def fromFunctionIO[R, A](f: Random with R => ZIO[R, Nothing, A]): Gen[R, A] =
    fromFunctionM(f)

  /**
   * A generator that generates values from the specified