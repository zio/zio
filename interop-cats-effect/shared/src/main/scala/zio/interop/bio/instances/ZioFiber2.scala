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

package zio
package interop
package bio
package instances

import cats.data.NonEmptyList
import cats.syntax.either._
import zio.Exit.{ Failure, Success }
import zio.interop.bio.FailedWith.{ Errors, Interrupted }

object ZioFiber2 {

  @inline def fromFiber[E, A](fiber: Fiber[E, A]): Fiber2[IO, E, A] =
    new Fiber2[IO, E, A] {

      def await: IO[Nothing, Either[FailedWith[E], A]] =
        fiber.await >>= fromExit

      def cancel: IO[Nothing, Either[FailedWith[E], A]] =
        fiber.await >>= fromExit

      def join: IO[E, A] =
        fiber.join
    }

  private[this] def fromExit[E, A](exit: Exit[E, A]): IO[Nothing, Either[FailedWith[E], A]] =
    exit match {
      case Success(a) =>
        IO.succeed(a.asRight)

      case Failure(cause) =>
        if (cause.died || cause.interrupted || cause.failures.isEmpty) IO.succeed(Interrupted.asLeft)
        else IO.succeed(Errors(NonEmptyList.fromListUnsafe(cause.failures)).asLeft)
    }
}
