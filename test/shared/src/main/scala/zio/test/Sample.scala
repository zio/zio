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

package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.ZStream
import zio.{ZIO, Zippable, ZTraceElement}

/**
 * A sample is a single observation from a random variable, together with a tree
 * of "shrinkings" used for minimization of "large" failures.
 */
final case class Sample[-R, +A](value: A, shrink: ZStream[R, Nothing, Option[Sample[R, A]]]) { self =>

  /**
   * A symbolic alias for `zip`.
   */
  def <*>[R1 <: R, B](
    that: Sample[R1, B]
  )(implicit zippable: Zippable[A, B], trace: ZTraceElement): Sample[R1, zippable.Out] =
    self.zip(that)

  /**
   * Filters this sample by replacing it with its shrink tree if the value does
   * not meet the specified predicate and recursively filtering the shrink tree.
   */
  def filter(f: A => Boolean)(implicit trace: ZTraceElement): ZStream[R, Nothing, Option[Sample[R, A]]] =
    if (f(value)) ZStream(Some(Sample(value, shrink.flatMap(_.map(_.filter(f)).getOrElse(ZStream.empty)))))
    else shrink.flatMap(_.map(_.filter(f)).getOrElse(ZStream.empty))

  def flatMap[R1 <: R, B](f: A => Sample[R1, B])(implicit trace: ZTraceElement): Sample[R1, B] = {
    val sample = f(value)
    Sample(sample.value, mergeStream(sample.shrink, shrink.map(_.map(_.flatMap(f)))))
  }

  def foreach[R1 <: R, B](f: A => ZIO[R1, Nothing, B])(implicit trace: ZTraceElement): ZIO[R1, Nothing, Sample[R1, B]] =
    f(value).map(Sample(_, shrink.mapZIO(ZIO.foreach(_)(_.foreach(f)))))

  def map[B](f: A => B)(implicit trace: ZTraceElement): Sample[R, B] =
    Sample(f(value), shrink.map(_.map(_.map(f))))

  /**
   * Converts the shrink tree into a stream of shrinkings by recursively
   * searching the shrink tree, using the specified function to determine
   * whether a value is a failure. The resulting stream will contain all values
   * explored, regardless of whether they are successes or failures.
   */
  def shrinkSearch(f: A => Boolean)(implicit trace: ZTraceElement): ZStream[R, Nothing, A] =
    if (!f(value))
      ZStream(value)
    else
      ZStream(value) ++ shrink
        .takeUntil(v => v.fold(false)(v => f(v.value)))
        .flatMap(_.map(_.shrinkSearch(f)).getOrElse(ZStream.empty))

  /**
   * Composes this sample with the specified sample to create a cartesian
   * product of values and shrinkings.
   */
  def zip[R1 <: R, B](
    that: Sample[R1, B]
  )(implicit zippable: Zippable[A, B], trace: ZTraceElement): Sample[R1, zippable.Out] =
    self.zipWith(that)(zippable.zip(_, _))

  /**
   * Composes this sample with the specified sample to create a cartesian
   * product of values and shrinkings with the specified function.
   */
  def zipWith[R1 <: R, B, C](that: Sample[R1, B])(f: (A, B) => C)(implicit trace: ZTraceElement): Sample[R1, C] =
    self.flatMap(a => that.map(b => f(a, b)))
}

object Sample {

  /**
   * A sample without shrinking.
   */
  def noShrink[A](a: A)(implicit trace: ZTraceElement): Sample[Any, A] =
    Sample(a, ZStream.empty)

  def shrinkFractional[A](smallest: A)(a: A)(implicit F: Fractional[A], trace: ZTraceElement): Sample[Any, A] =
    Sample.unfold(a) { max =>
      (
        max,
        ZStream.unfold(smallest) { min =>
          val mid = F.plus(min, F.div(F.minus(max, min), F.fromInt(2)))
          if (mid == max) None
          else if (F.toDouble(F.abs(F.minus(max, mid))) < 0.001) Some((min, max))
          else Some((mid, mid))
        }
      )
    }

  def shrinkIntegral[A](smallest: A)(a: A)(implicit I: Integral[A], trace: ZTraceElement): Sample[Any, A] =
    Sample.unfold(a) { max =>
      (
        max,
        ZStream.unfold(smallest) { min =>
          val mid = I.plus(min, I.quot(I.minus(max, min), I.fromInt(2)))
          if (mid == max) None
          else if (I.equiv(I.abs(I.minus(max, mid)), I.one)) Some((mid, max))
          else Some((mid, mid))
        }
      )
    }

  def unfold[R, A, S](s: S)(f: S => (A, ZStream[R, Nothing, S]))(implicit trace: ZTraceElement): Sample[R, A] = {
    val (value, shrink) = f(s)
    Sample(value, shrink.map(s => Some(unfold(s)(f))).intersperse(None))
  }
}
