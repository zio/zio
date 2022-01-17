/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

package zio.mock

import zio.{Chunk, Random, UIO, URLayer, ZIO, ZTraceElement}
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.UUID

import java.util.UUID

object MockRandom extends Mock[Random] {

  object NextBoolean       extends Effect[Unit, Nothing, Boolean]
  object NextBytes         extends Effect[Int, Nothing, Chunk[Byte]]
  object NextDouble        extends Effect[Unit, Nothing, Double]
  object NextDoubleBetween extends Effect[(Double, Double), Nothing, Double]
  object NextFloat         extends Effect[Unit, Nothing, Float]
  object NextFloatBetween  extends Effect[(Float, Float), Nothing, Float]
  object NextGaussian      extends Effect[Unit, Nothing, Double]
  object NextInt           extends Effect[Unit, Nothing, Int]
  object NextIntBetween    extends Effect[(Int, Int), Nothing, Int]
  object NextIntBounded    extends Effect[Int, Nothing, Int]
  object NextLong          extends Effect[Unit, Nothing, Long]
  object NextLongBetween   extends Effect[(Long, Long), Nothing, Long]
  object NextLongBounded   extends Effect[Long, Nothing, Long]
  object NextPrintableChar extends Effect[Unit, Nothing, Char]
  object NextString        extends Effect[Int, Nothing, String]
  object NextUUID          extends Effect[Unit, Nothing, UUID]
  object SetSeed           extends Effect[Long, Nothing, Unit]
  object Shuffle           extends Effect[Iterable[Any], Nothing, Iterable[Any]]

  val compose: URLayer[Proxy, Random] = {
    implicit val trace = Tracer.newTrace
    ZIO
      .service[Proxy]
      .map(proxy =>
        new Random {
          def nextBoolean(implicit trace: ZTraceElement): UIO[Boolean]                   = proxy(NextBoolean)
          def nextBytes(length: => Int)(implicit trace: ZTraceElement): UIO[Chunk[Byte]] = proxy(NextBytes, length)
          def nextDouble(implicit trace: ZTraceElement): UIO[Double]                     = proxy(NextDouble)
          def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
            trace: ZTraceElement
          ): UIO[Double] =
            proxy(NextDoubleBetween, minInclusive, maxExclusive)
          def nextFloat(implicit trace: ZTraceElement): UIO[Float] = proxy(NextFloat)
          def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit
            trace: ZTraceElement
          ): UIO[Float] =
            proxy(NextFloatBetween, minInclusive, maxExclusive)
          def nextGaussian(implicit trace: ZTraceElement): UIO[Double] = proxy(NextGaussian)
          def nextInt(implicit trace: ZTraceElement): UIO[Int]         = proxy(NextInt)
          def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: ZTraceElement): UIO[Int] =
            proxy(NextIntBetween, minInclusive, maxExclusive)
          def nextIntBounded(n: => Int)(implicit trace: ZTraceElement): UIO[Int] = proxy(NextIntBounded, n)
          def nextLong(implicit trace: ZTraceElement): UIO[Long]                 = proxy(NextLong)
          def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: ZTraceElement): UIO[Long] =
            proxy(NextLongBetween, minInclusive, maxExclusive)
          def nextLongBounded(n: => Long)(implicit trace: ZTraceElement): UIO[Long]  = proxy(NextLongBounded, n)
          def nextPrintableChar(implicit trace: ZTraceElement): UIO[Char]            = proxy(NextPrintableChar)
          def nextString(length: => Int)(implicit trace: ZTraceElement): UIO[String] = proxy(NextString, length)
          def nextUUID(implicit trace: ZTraceElement): UIO[UUID]                     = proxy(NextUUID)
          def setSeed(seed: => Long)(implicit trace: ZTraceElement): UIO[Unit]       = proxy(SetSeed, seed)
          def shuffle[A, Collection[+Element] <: Iterable[Element]](
            collection: => Collection[A]
          )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): UIO[Collection[A]] =
            proxy(Shuffle, collection).asInstanceOf[UIO[Collection[A]]]
        }
      )
      .toLayer
  }
}
