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

import zio.{ Cause, ZIO }
import zio.stream.{ Take, ZStream }

/**
 * A sample is a single observation from a random variable, together with a
 * tree of "shrinkings" used for minimization of "large" failures.
 */
final case class Sample[-R, +A](value: A, shrink: ZStream[R, Nothing, Sample[R, A]]) { self =>

  final def <&>[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.zipPar(that)

  final def <*>[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.zip(that)

  /**
   * Filters this sample by replacing it with its shrink tree if the value does
   * not meet the specified predicate and recursively filtering the shrink
   * tree.
   */
  final def filter(f: A => Boolean): ZStream[R, Nothing, Sample[R, A]] =
    if (f(value)) ZStream(Sample(value, shrink.flatMap(_.filter(f))))
    else shrink.flatMap(_.filter(f))

  final def flatMap[R1 <: R, B](f: A => Sample[R1, B]): Sample[R1, B] = {
    val sample = f(value)
    Sample(sample.value, sample.shrink ++ shrink.map(_.flatMap(f)))
  }

  final def map[B](f: A => B): Sample[R, B] =
    Sample(f(value), shrink.map(_.map(f)))

  /**
   * Converts the shrink tree into a stream of shrinkings by recursively
   * searching the shrink tree, using the specified function to determine
   * whether a value is a failure. The resulting stream will contain all
   * values explored, regardless of whether they are successes or failures.
   */
  final def shrinkSearch(f: A => Boolean): ZStream[R, Nothing, A] =
    if (!f(value))
      ZStream(value)
    else
      ZStream(value) ++ shrink.takeUntil(v => f(v.value)).flatMap(_.shrinkSearch(f))

  final def traverse[R1 <: R, B](f: A => ZIO[R1, Nothing, B]): ZIO[R1, Nothing, Sample[R1, B]] =
    f(value).map(Sample(_, shrink.mapM(_.traverse(f))))

  final def zip[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.zipWith(that)((_, _))

  final def zipPar[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.zipWithPar(that)((_, _))

  final def zipWith[R1 <: R, B, C](that: Sample[R1, B])(f: (A, B) => C): Sample[R1, C] =
    self.flatMap(a => that.map(b => f(a, b)))

  final def zipWithPar[R1 <: R, B, C](that: Sample[R1, B])(f: (A, B) => C): Sample[R1, C] = {
    val value = f(self.value, that.value)
    val shrink = self.shrink.combine[R1, Nothing, (A, B), Sample[R1, B], Sample[R1, C]](that.shrink)(
      (self.value, that.value)
    ) {
      case ((a, b), left, right) =>
        Take.fromPull(left).zipWithPar(Take.fromPull(right)) {
          case (Take.Value(l), Take.Value(r)) => ((l.value, r.value), Take.Value(l.zipWithPar(r)(f)))
          case (Take.Value(l), Take.End)      => ((l.value, b), Take.Value(l.map(f(_, b))))
          case (Take.End, Take.Value(r))      => ((a, r.value), Take.Value(r.map(f(a, _))))
          case (Take.End, Take.End)           => ((a, b), Take.End)
          case (Take.Fail(e1), Take.Fail(e2)) => ((a, b), Take.Fail(Cause.Both(e1, e2)))
          case (Take.Fail(e), _)              => ((a, b), Take.Fail(e))
          case (_, Take.Fail(e))              => ((a, b), Take.Fail(e))
        }
    }
    Sample(value, shrink)
  }
}
object Sample {

  /**
   * A sample without shrinking.
   */
  final def noShrink[A](a: A): Sample[Any, A] =
    Sample(a, ZStream.empty)

  final def shrinkFractional[A](smallest: A)(a: A)(implicit F: Fractional[A]): Sample[Any, A] =
    Sample.unfold((a)) { max =>
      (max, ZStream.unfold(smallest) { min =>
        val mid = F.plus(min, F.div(F.minus(max, min), F.fromInt(2)))
        if (mid == max) None
        else if (F.toDouble(F.abs(F.minus(max, mid))) < 0.001) Some((min, max))
        else Some((mid, mid))
      })
    }

  final def shrinkIntegral[A](smallest: A)(a: A)(implicit I: Integral[A]): Sample[Any, A] =
    Sample.unfold((a)) { max =>
      (max, ZStream.unfold(smallest) { min =>
        val mid = I.plus(min, I.quot(I.minus(max, min), I.fromInt(2)))
        if (mid == max) None
        else if (I.equiv(I.abs(I.minus(max, mid)), I.one)) Some((mid, max))
        else Some((mid, mid))
      })
    }

  final def unfold[R, A, S](s: S)(f: S => (A, ZStream[R, Nothing, S])): Sample[R, A] = {
    val (value, shrink) = f(s)
    Sample(value, shrink.map(unfold(_)(f)))
  }
}
