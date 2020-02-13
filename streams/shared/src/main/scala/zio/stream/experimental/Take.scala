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

import zio.{ Cause, IO }

/**
 * A `Take[E, B, A]` represents a single `take` from a queue modeling a stream of
 * values. A `Take` may be a failure cause `Cause[E]`, an element value `A`
 * or an end-of-stream marker `B`.
 */
sealed trait Take[+E, +B, +A] extends Product with Serializable { self =>
  final def flatMap[E1 >: E, B1 >: B, C](f: A => Take[E1, B1, C]): Take[E1, B1, C] = self match {
    case t @ Take.Fail(_) => t
    case Take.Value(a)    => f(a)
    case Take.End(b)      => Take.End(b)
  }

  final def isFailure: Boolean = self match {
    case Take.Fail(_) => true
    case _            => false
  }

  final def map[B1 >: B, C](f: A => C): Take[E, B1, C] = self match {
    case t @ Take.Fail(_) => t
    case Take.Value(a)    => Take.Value(f(a))
    case Take.End(b)      => Take.End(b)
  }

  final def zip[E1 >: E, B1 >: B, C](that: Take[E1, B1, C]): Take[E1, B1, (A, C)] =
    self.zipWith(that)(_ -> _)

  final def zipWith[E1 >: E, B1 >: B, C, D](that: Take[E1, B1, C])(f: (A, C) => D): Take[E1, B1, D] =
    (self, that) match {
      case (Take.Value(a), Take.Value(b)) => Take.Value(f(a, b))
      case (Take.Fail(a), Take.Fail(b))   => Take.Fail(a && b)
      case (Take.End(b), _)               => Take.End(b)
      case (t @ Take.Fail(_), _)          => t
      case (_, Take.End(b))               => Take.End(b)
      case (_, t @ Take.Fail(_))          => t
    }
}

object Take {
  final case class Fail[+E](value: Cause[E]) extends Take[E, Nothing, Nothing]
  final case class Value[+A](value: A)       extends Take[Nothing, Nothing, A]
  final case class End[+B](marker: B)        extends Take[Nothing, B, Nothing]

  // def fromPull[R, E, A](pull: Pull[R, E, A]): ZIO[R, Nothing, Take[E, A]] =
  //   pull.fold(_.fold[Take[E, A]](Take.End)(e => Take.Fail(Cause.fail(e))), Take.Value(_))

  def either[E, B, A](io: IO[E, Take[E, B, A]]): IO[E, Either[B, A]] =
    io.flatMap {
      case Take.End(b)   => IO.succeedNow(Left(b))
      case Take.Value(a) => IO.succeedNow(Right(a))
      case Take.Fail(e)  => IO.haltNow(e)
    }
}
