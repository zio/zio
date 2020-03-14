/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.stream.experimental

import zio.{ Cause, Chunk, ZIO }

/**
 * A `Take[E, A]` represents a single `take` from a queue modeling a stream of
 * values. A `Take` may be a failure cause `Cause[E]`, an element value `A`
 * or an end-of-stream marker.
 */
sealed trait Take[+E, +A] extends Product with Serializable { self =>

  /**
   * Creates a `take` with element value `B` obtained by transforming value of type `A`
   * by applying function `f`. If `take` is a failure `Take.Fail` or an end-of-stream marker
   * `Take.End` original `take` instance is returned.
   */
  final def flatMap[E1 >: E, B](f: A => Take[E1, B]): Take[E1, B] = self match {
    case t @ Take.Fail(_) => t
    case Take.Value(as) =>
      as.map(f).fold[Take[E1, B]](Take.Value[B](Chunk.empty)) {
        case (Take.Value(bs1), Take.Value(bs2)) => Take.Value(bs1 ++ bs2)
        case (t @ Take.Fail(_), _)              => t
        case (Take.End, _)                      => Take.End
        case (_, t @ Take.Fail(_))              => t
        case (_, Take.End)                      => Take.End
      }
    case Take.End => Take.End
  }

  /**
   * Checks if this `take` is a failure (`Take.Fail`).
   */
  final def isFailure: Boolean = self match {
    case Take.Fail(_) => true
    case _            => false
  }

  /**
   * Transforms `Take[E, A]` to `Take[E, B]` by applying function `f`
   * to an element value if `take` is not failure or end-of-stream marker.
   */
  final def map[B](f: A => B): Take[E, B] = self match {
    case t @ Take.Fail(_) => t
    case Take.Value(as)   => Take.Value(as.map(f))
    case Take.End         => Take.End
  }

  final def toPull: ZIO[Any, Option[E], Chunk[A]] = self match {
    case Take.Fail(c)   => ZStream.Pull.halt(c)
    case Take.Value(as) => ZStream.Pull.emit(as)
    case Take.End       => ZStream.Pull.end
  }

  /**
   * Zips this `take` and the specified one together, producing a `take` with tuple of
   * their values.
   */
  final def zip[E1 >: E, B](that: Take[E1, B]): Take[E1, (A, B)] =
    self.zipWith(that)(_ -> _)

  /**
   * Zips this `take` and the specified one together, producing `take` with a value `C` by applying
   * provided function `f` to values from both `takes`. In case both `takes` are `Take.Fail`,
   * `take` with combined cause will be produced.
   * Otherwise, if one of this or that `take` is `Take.Fail` or `Take.End` that one will be returned.
   */
  final def zipWith[E1 >: E, B, C](that: Take[E1, B])(f: (A, B) => C): Take[E1, C] = (self, that) match {
    case (Take.Value(as), Take.Value(bs)) => Take.Value(as.zipWith(bs)(f))
    case (Take.Fail(a), Take.Fail(b))     => Take.Fail(a && b)
    case (Take.End, _)                    => Take.End
    case (t @ Take.Fail(_), _)            => t
    case (_, Take.End)                    => Take.End
    case (_, t @ Take.Fail(_))            => t
  }
}

object Take {

  /**
   * Represents a failure `take` with a `Cause[E]`.
   */
  final case class Fail[+E](value: Cause[E]) extends Take[E, Nothing]

  /**
   * Represents `take` with value of type `A` retrieved from queue modeling stream.
   */
  final case class Value[+A](values: Chunk[A]) extends Take[Nothing, A]

  /**
   * Represents end of stream marker.
   */
  case object End extends Take[Nothing, Nothing]

  /**
   * Creates effect from `Pull[R, E, A]` that does not fail, but succeeds with the `Take[E, A]`.
   * Error from stream when pulling is converted to `Take.Fail`, end of stream to `Take.End`.
   */
  def fromPull[R, E, A](pull: ZIO[R, Option[E], Chunk[A]]): ZIO[R, Nothing, Take[E, A]] =
    pull.foldCause(
      Cause.sequenceCauseOption(_).fold[Take[E, A]](Take.End)(Take.Fail(_)),
      Take.Value(_)
    )
}
