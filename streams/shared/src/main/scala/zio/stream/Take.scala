/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package zio.stream

import zio.IO
import zio.Cause

/**
 * A `Take[E, A]` represents a single `take` from a queue modeling a stream of
 * values. A `Take` may be a failure cause `Cause[E]`, an element value `A`
 * or an end-of-stream marker.
 */
sealed trait Take[+E, +A] extends Product with Serializable { self =>
  final def flatMap[E1 >: E, B](f: A => Take[E1, B]): Take[E1, B] = self match {
    case t @ Take.Fail(_) => t
    case Take.Value(a)    => f(a)
    case Take.End         => Take.End
  }

  final def isFailure: Boolean = self match {
    case Take.Fail(_) => true
    case _            => false
  }

  final def map[B](f: A => B): Take[E, B] = self match {
    case t @ Take.Fail(_) => t
    case Take.Value(a)    => Take.Value(f(a))
    case Take.End         => Take.End
  }

  final def zip[E1 >: E, B](that: Take[E1, B]): Take[E1, (A, B)] =
    self.zipWith(that)(_ -> _)

  final def zipWith[E1 >: E, B, C](that: Take[E1, B])(f: (A, B) => C): Take[E1, C] = (self, that) match {
    case (Take.Value(a), Take.Value(b)) => Take.Value(f(a, b))
    case (Take.Fail(a), Take.Fail(b))   => Take.Fail(a && b)
    case (Take.End, _)                  => Take.End
    case (t @ Take.Fail(_), _)          => t
    case (_, Take.End)                  => Take.End
    case (_, t @ Take.Fail(_))          => t
  }
}

object Take {
  final case class Fail[E](value: Cause[E]) extends Take[E, Nothing]
  final case class Value[A](value: A)       extends Take[Nothing, A]
  case object End                           extends Take[Nothing, Nothing]

  final def option[E, A](io: IO[E, Take[E, A]]): IO[E, Option[A]] =
    io.flatMap {
      case Take.End      => IO.succeed(None)
      case Take.Value(a) => IO.succeed(Some(a))
      case Take.Fail(e)  => IO.halt(e)
    }
}
