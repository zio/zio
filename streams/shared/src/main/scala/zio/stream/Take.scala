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

package zio.stream

import zio._

/**
 * A `Take[E, A]` represents a single `take` from a queue modeling a stream of
 * values. A `Take` may be a failure cause `Cause[E]`, an chunk value `A`
 * or an end-of-stream marker.
 */
case class Take[+E, +A](exit: Exit[Option[E], Chunk[A]]) extends AnyVal {

  /**
   * Transforms `Take[E, A]` to `ZIO[R, E, B]`.
   */
  def done[R]: ZIO[R, Option[E], Chunk[A]] =
    IO.done(exit)

  /**
   * Folds over the failure cause, success value and end-of-stream marker to
   * yield a value.
   */
  def fold[Z](end: => Z, error: Cause[E] => Z, value: Chunk[A] => Z): Z =
    exit.fold(Cause.sequenceCauseOption(_).fold(end)(error), value)

  /**
   * Effectful version of [[Take#fold]].
   *
   * Folds over the failure cause, success value and end-of-stream marker to
   * yield an effect.
   */
  def foldM[R, E1, Z](
    end: => ZIO[R, E1, Z],
    error: Cause[E] => ZIO[R, E1, Z],
    value: Chunk[A] => ZIO[R, E1, Z]
  ): ZIO[R, E1, Z] =
    exit.foldM(Cause.sequenceCauseOption(_).fold(end)(error), value)

  /**
   * Checks if this `take` is done (`Take.end`).
   */
  def isDone: Boolean =
    exit.fold(Cause.sequenceCauseOption(_).isEmpty, _ => false)

  /**
   * Transforms `Take[E, A]` to `Take[E, B]` by applying function `f`.
   */
  def map[B](f: A => B): Take[E, B] =
    Take(exit.map(_.map(f)))

  /**
   * Returns an effect that effectfully "peeks" at the success of this take.
   */
  def tap[R, E1](f: Chunk[A] => ZIO[R, E1, Any]): ZIO[R, E1, Unit] =
    exit.foreach(f).unit
}

object Take {

  /**
   * Creates a `Take[Nothing, A]` with a singleton chunk.
   */
  def single[A](a: A): Take[Nothing, A] =
    Take(Exit.succeed(Chunk.single(a)))

  /**
   * Creates a `Take[Nothing, A]` with the specified chunk.
   */
  def chunk[A](as: Chunk[A]): Take[Nothing, A] =
    Take(Exit.succeed(as))

  /**
   * Creates a failing `Take[E, Nothing]` with the specified failure.
   */
  def fail[E](e: E): Take[E, Nothing] =
    Take(Exit.fail(Some(e)))

  /**
   * Creates an effect from `ZIO[R, E,A]` that does not fail, but suceeds with the `Take[E, A]`.
   * Error from stream when pulling is converted to `Take.halt`. Creates a singleton chunk.
   */
  def fromEffect[R, E, A](zio: ZIO[R, E, A]): URIO[R, Take[E, A]] =
    zio.foldCause(halt, single)

  /**
   * Creates effect from `Pull[R, E, A]` that does not fail, but succeeds with the `Take[E, A]`.
   * Error from stream when pulling is converted to `Take.halt`, end of stream to `Take.end`.
   */
  def fromPull[R, E, A](pull: ZStream.Pull[R, E, A]): URIO[R, Take[E, A]] =
    pull.foldCause(Cause.sequenceCauseOption(_).fold[Take[E, Nothing]](end)(halt), chunk)

  /**
   * Creates a failing `Take[E, Nothing]` with the specified cause.
   */
  def halt[E](c: Cause[E]): Take[E, Nothing] =
    Take(Exit.halt(c.map(Some(_))))

  /**
   * Creates a failing `Take[Nothing, Nothing]` with the specified throwable.
   */
  def die(t: Throwable): Take[Nothing, Nothing] =
    Take(Exit.die(t))

  /**
   * Creates a failing `Take[Nothing, Nothing]` with the specified error message.
   */
  def dieMessage(msg: String): Take[Nothing, Nothing] =
    Take(Exit.die(new RuntimeException(msg)))

  /**
   * Creates a `Take[E, A]` from `Exit[E, A]`.
   */
  def done[E, A](exit: Exit[E, A]): Take[E, A] =
    Take(exit.mapError[Option[E]](Some(_)).map(Chunk.single))

  /**
   * End-of-stream marker
   */
  val end: Take[Nothing, Nothing] =
    Take(Exit.fail(None))
}
