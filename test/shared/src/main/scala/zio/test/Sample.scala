/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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
import zio.{ZIO, Zippable, Trace}

/**
 * A sample is a single observation from a random variable, together with a tree
 * of "shrinkings" used for minimization of "large" failures.
 */
final case class Sample[-R, +A](value: A, shrink: ZStream[R, Nothing, Sample[R, A]]) { self =>

  /**
   * Combines this sample with `that` sample. A symbolic alias for `zip`. The
   * values are combined with `Zippable`, which flattens nested tuples and drops
   * `Unit` values, so a pair sample combined with an `Int` sample gives a
   * triple.
   */
  def <*>[R1 <: R, B](
    that: Sample[R1, B]
  )(implicit zippable: Zippable[A, B], trace: Trace): Sample[R1, zippable.Out] =
    self.zip(that)

  /**
   * Filters this sample by replacing it with its shrink tree if the value does
   * not meet the specified predicate and recursively filtering the shrink tree.
   */
  def filter(f: A => Boolean)(implicit trace: Trace): ZStream[R, Nothing, Sample[R, A]] =
    if (f(value)) ZStream(Sample(value, shrink.flatMap(_.filter(f))))
    else shrink.flatMap(_.filter(f))

  /**
   * Applies `f` to the value of this sample. The shrinks of the result are the
   * shrinks of the sample that `f` returns, followed by the shrinks of this
   * sample with `f` applied to each of them.
   */
  def flatMap[R1 <: R, B](f: A => Sample[R1, B])(implicit trace: Trace): Sample[R1, B] = {
    val sample = f(value)
    Sample(sample.value, sample.shrink ++ shrink.map(_.flatMap(f)))
  }

  /**
   * Applies the effectual function `f` to the value of this sample, and to each
   * shrink when the shrink tree is explored.
   */
  def foreach[R1 <: R, B](f: A => ZIO[R1, Nothing, B])(implicit trace: Trace): ZIO[R1, Nothing, Sample[R1, B]] =
    f(value).map(Sample(_, shrink.mapZIO(_.foreach(f))))

  /** Applies `f` to the value of this sample and to every shrink. */
  def map[B](f: A => B)(implicit trace: Trace): Sample[R, B] =
    Sample(f(value), shrink.map(_.map(f)))

  /**
   * Converts the shrink tree into a stream of shrinkings by recursively
   * searching the shrink tree, using the specified function to determine
   * whether a value is a failure. The resulting stream will contain all values
   * explored, regardless of whether they are successes or failures.
   */
  def shrinkSearch(f: A => Boolean)(implicit trace: Trace): ZStream[R, Nothing, A] =
    if (!f(value))
      ZStream(value)
    else
      ZStream(value) ++ shrink.takeUntil(v => f(v.value)).flatMap(_.shrinkSearch(f))

  /**
   * Combines this sample with `that` sample. The shrinks of the result are the
   * shrinks of `that` paired with this value, followed by the shrinks of this
   * sample paired with `that` sample.
   */
  def zip[R1 <: R, B](
    that: Sample[R1, B]
  )(implicit zippable: Zippable[A, B], trace: Trace): Sample[R1, zippable.Out] =
    self.zipWith(that)(zippable.zip(_, _))

  /**
   * Combines this sample with `that` sample using `f`. The shrinks of the
   * result are the shrinks of `that` combined with this value, followed by the
   * shrinks of this sample combined with `that` sample, all with `f`.
   */
  def zipWith[R1 <: R, B, C](that: Sample[R1, B])(f: (A, B) => C)(implicit trace: Trace): Sample[R1, C] =
    self.flatMap(a => that.map(b => f(a, b)))
}

/** Constructors of [[Sample]] values, including the shrinkers for numbers. */
object Sample {

  /**
   * A sample without shrinking.
   */
  def noShrink[A](a: A)(implicit trace: Trace): Sample[Any, A] =
    Sample(a, ZStream.empty)

  /**
   * Creates a sample of `a` that shrinks toward `smallest`. The first shrink is
   * halfway between `smallest` and `a`, and each next one halves the remaining
   * distance to `a`. Once that distance is below `0.001`, the list ends by
   * repeating its previous shrink. When `a` differs from `smallest` by less
   * than `0.002`, the only shrink is `smallest`, and when `a` equals `smallest`
   * there are no shrinks. Following the shrinks of the shrinks reaches
   * `smallest`.
   *
   * {{{
   * import zio._
   *
   * // The shrinks of 1.0 start with 0.5, 0.75, 0.875, ...
   * val shrinks: UIO[List[Double]] =
   *   Sample.shrinkFractional(0.0)(1.0).shrink.map(_.value).runCollect.map(_.toList)
   * }}}
   */
  def shrinkFractional[A](smallest: A)(a: A)(implicit F: Fractional[A], trace: Trace): Sample[Any, A] =
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

  /**
   * Creates a sample of `a` that shrinks toward `smallest`. The first shrink is
   * halfway between `smallest` and `a` (rounded toward `smallest`), and each
   * next one halves the remaining distance to `a`. Following the shrinks of the
   * shrinks reaches `smallest`.
   *
   * {{{
   * import zio._
   *
   * // List(50, 75, 87, 93, 96, 98, 99)
   * val shrinks: UIO[List[Int]] =
   *   Sample.shrinkIntegral(0)(100).shrink.map(_.value).runCollect.map(_.toList)
   * }}}
   */
  def shrinkIntegral[A](smallest: A)(a: A)(implicit I: Integral[A], trace: Trace): Sample[Any, A] =
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

  /**
   * Builds a sample from a seed. `f` returns the value for a seed and the
   * stream of seeds for its shrinks, and each shrink is built from its seed in
   * the same way.
   */
  def unfold[R, A, S](s: S)(f: S => (A, ZStream[R, Nothing, S]))(implicit trace: Trace): Sample[R, A] = {
    val (value, shrink) = f(s)
    Sample(value, shrink.map(unfold(_)(f)))
  }
}
