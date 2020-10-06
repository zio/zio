/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

import zio.stream.ZStream
import zio.{ Cause, Exit, ZIO }

/**
 * A sample is a single observation from a random variable, together with a
 * tree of "shrinkings" used for minimization of "large" failures.
 */
final case class Sample[-R, +A](value: A, shrink: ZStream[R, Nothing, Sample[R, A]]) { self =>

  /**
   * A symbolic alias for `zip`.
   */
  def <&>[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.zip(that)

  /**
   * A symbolic alias for `cross`.
   */
  def <*>[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.cross(that)

  /**
   * Composes this sample with the specified sample to create a cartesian
   * product of values and shrinkings.
   */
  def cross[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.crossWith(that)((_, _))

  /**
   * Composes this sample with the specified sample to create a cartesian
   * product of values and shrinkings with the specified function.
   */
  def crossWith[R1 <: R, B, C](that: Sample[R1, B])(f: (A, B) => C): Sample[R1, C] =
    self.flatMap(a => that.map(b => f(a, b)))

  /**
   * Filters this sample by replacing it with its shrink tree if the value does
   * not meet the specified predicate and recursively filtering the shrink
   * tree.
   */
  def filter(f: A => Boolean): ZStream[R, Nothing, Sample[R, A]] =
    if (f(value)) ZStream(Sample(value, shrink.flatMap(_.filter(f))))
    else shrink.flatMap(_.filter(f))

  def flatMap[R1 <: R, B](f: A => Sample[R1, B]): Sample[R1, B] = {
    val sample = f(value)
    Sample(sample.value, sample.shrink ++ shrink.map(_.flatMap(f)))
  }

  def foreach[R1 <: R, B](f: A => ZIO[R1, Nothing, B]): ZIO[R1, Nothing, Sample[R1, B]] =
    f(value).map(Sample(_, shrink.mapM(_.foreach(f))))

  def map[B](f: A => B): Sample[R, B] =
    Sample(f(value), shrink.map(_.map(f)))

  /**
   * Converts the shrink tree into a stream of shrinkings by recursively
   * searching the shrink tree, using the specified function to determine
   * whether a value is a failure. The resulting stream will contain all
   * values explored, regardless of whether they are successes or failures.
   */
  def shrinkSearch(f: A => Boolean): ZStream[R, Nothing, A] =
    if (!f(value))
      ZStream(value)
    else
      ZStream(value) ++ shrink.takeUntil(v => f(v.value)).flatMap(_.shrinkSearch(f))

  /**
   * Zips two samples together pairwise.
   */
  def zip[R1 <: R, B](that: Sample[R1, B]): Sample[R1, (A, B)] =
    self.zipWith(that)((_, _))

  /**
   * Zips two samples together pairwise with the specified function.
   */
  def zipWith[R1 <: R, B, C](that: Sample[R1, B])(f: (A, B) => C): Sample[R1, C] = {
    type State = (Boolean, Boolean, Option[Sample[R, A]], Option[Sample[R1, B]])
    val value = f(self.value, that.value)
    val shrink = self.shrink
      .combine[R1, Nothing, State, Sample[R1, B], Sample[R1, C]](that.shrink)((false, false, None, None)) {
        case ((leftDone, rightDone, s1, s2), left, right) =>
          left.run.zipWith(right.run) {
            case (Exit.Success(l), Exit.Success(r)) =>
              Exit.succeed((zipWith(r)(f), (leftDone, rightDone, Some(l), Some(r))))

            case (Exit.Success(l), Exit.Failure(cause)) =>
              Cause.sequenceCauseOption(cause) match {
                case Some(c) => Exit.halt(c)
                case None =>
                  s2 match {
                    case Some(r) => Exit.succeed((l.zipWith(r)(f), (leftDone, rightDone, Some(l), s2)))
                    case None    => Exit.succeed((l.map(f(_, that.value)), (leftDone, true, Some(l), s2)))
                  }
              }

            case (Exit.Failure(cause), Exit.Success(r)) =>
              Cause.sequenceCauseOption(cause) match {
                case Some(c) => Exit.halt(c)
                case None =>
                  s1 match {
                    case Some(l) => Exit.succeed((l.zipWith(r)(f), (leftDone, rightDone, s1, Some(r))))
                    case None    => Exit.succeed((r.map(f(self.value, _)), (true, rightDone, s1, Some(r))))
                  }
              }

            case (Exit.Failure(causeL), Exit.Failure(causeR)) =>
              (Cause.sequenceCauseOption(causeL), Cause.sequenceCauseOption(causeR)) match {
                case (None, None) =>
                  (leftDone, rightDone, s1, s2) match {
                    case (false, _, _, Some(r)) => Exit.succeed((r.map(f(self.value, _)), (true, rightDone, s1, s2)))
                    case (_, false, Some(l), _) => Exit.succeed((l.map(f(_, that.value)), (leftDone, true, None, s2)))
                    case _                      => Exit.fail(None)
                  }
                case (Some(cl), Some(cr)) => Exit.halt(Cause.Both(cl, cr))
                case (_, Some(c))         => Exit.halt(c)
                case (Some(c), _)         => Exit.halt(c)
              }
          }
      }
    Sample(value, shrink)
  }
}

object Sample {

  /**
   * A sample without shrinking.
   */
  def noShrink[A](a: A): Sample[Any, A] =
    Sample(a, ZStream.empty)

  def shrinkFractional[A](smallest: A)(a: A)(implicit F: Fractional[A]): Sample[Any, A] =
    Sample.unfold((a)) { max =>
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

  def shrinkIntegral[A](smallest: A)(a: A)(implicit I: Integral[A]): Sample[Any, A] =
    Sample.unfold((a)) { max =>
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

  def unfold[R, A, S](s: S)(f: S => (A, ZStream[R, Nothing, S])): Sample[R, A] = {
    val (value, shrink) = f(s)
    Sample(value, shrink.map(unfold(_)(f)))
  }
}
