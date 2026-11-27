/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `Take[E, A]` is one signal of a stream, as it travels through a queue: a
 * chunk of values (`Take.chunk`, `Take.single`), a failure (`Take.fail`,
 * `Take.failCause`, `Take.die`) or the end-of-stream marker (`Take.end`).
 *
 * It wraps an `Exit[Option[E], Chunk[A]]`: a success holds the chunk, a failure
 * with `Some(e)` is an error, and a failure with `None` is the end of the
 * stream. `ZStream#runIntoQueue` and `ZStream#toQueue` write a stream into a
 * queue as takes, ending with `Take.end`; `ZStream.fromQueue` followed by
 * `ZStream#flattenTake` turns such a queue back into a stream. Inspect a single
 * take with `fold`, `foldZIO`, `isSuccess`, `isFailure` and `isDone`.
 *
 * {{{
 * import zio._
 *
 * val takes: List[Take[String, Int]] =
 *   List(Take.chunk(Chunk(1, 2)), Take.single(3), Take.end)
 *
 * val program: ZIO[Any, String, Chunk[Int]] =
 *   for {
 *     queue  <- Queue.unbounded[Take[String, Int]]
 *     _      <- queue.offerAll(takes)
 *     result <- ZStream.fromQueue(queue).flattenTake.runCollect
 *   } yield result
 * // program succeeds with Chunk(1, 2, 3)
 * }}}
 */
case class Take[+E, +A](exit: Exit[Option[E], Chunk[A]]) extends AnyVal {

  /**
   * Returns an effect that succeeds with the chunk of this take, fails with
   * `Some(e)` for a failure and with `None` for the end-of-stream marker.
   */
  @deprecated("use `exit` instead", "2.1.14")
  def done[R](implicit trace: Trace): ZIO[R, Option[E], Chunk[A]] =
    ZIO.done(exit)

  /**
   * Folds over the end-of-stream marker, the failure cause and the chunk of
   * this take to yield a value: `end` for `Take.end`, `error` with the cause
   * for a failure (defects and interruption included), and `value` with the
   * chunk.
   */
  def fold[Z](end: => Z, error: Cause[E] => Z, value: Chunk[A] => Z): Z =
    exit.foldExit(Cause.flipCauseOption(_).fold(end)(error), value)

  /**
   * Effectful version of [[Take#fold]].
   *
   * Folds over the end-of-stream marker, the failure cause and the chunk of
   * this take to yield an effect.
   */
  def foldZIO[R, E1, Z](
    end: => ZIO[R, E1, Z],
    error: Cause[E] => ZIO[R, E1, Z],
    value: Chunk[A] => ZIO[R, E1, Z]
  )(implicit trace: Trace): ZIO[R, E1, Z] =
    exit.foldExitZIO(Cause.flipCauseOption(_).fold(end)(error), value)

  /**
   * Checks if this take is the end-of-stream marker (`Take.end`).
   */
  def isDone: Boolean =
    exit.foldExit(Cause.flipCauseOption(_).isEmpty, _ => false)

  /**
   * Checks if this take is a failure. A take that dies or is interrupted is a
   * failure too; the end-of-stream marker is not.
   */
  def isFailure: Boolean =
    exit.foldExit(Cause.flipCauseOption(_).nonEmpty, _ => false)

  /**
   * Checks if this take holds a chunk of values. The chunk may be empty.
   */
  def isSuccess: Boolean =
    exit.foldExit(_ => false, _ => true)

  /**
   * Transforms `Take[E, A]` to `Take[E, B]` by applying `f` to each value of
   * the chunk. Failures and the end-of-stream marker are unchanged.
   */
  def map[B](f: A => B): Take[E, B] =
    Take(exit.mapExit(_.map(f)))

  /**
   * Returns an effect that runs `f` with the chunk of this take, and succeeds
   * with `Unit`. For a failure or the end-of-stream marker, it does nothing.
   */
  def tap[R, E1](f: Chunk[A] => ZIO[R, E1, Any])(implicit trace: Trace): ZIO[R, E1, Unit] =
    exit.foreach(f).unit
}

/**
 * Constructors of [[Take]]: chunks (`chunk`, `single`), failures (`fail`,
 * `failCause`, `die`, `dieMessage`), the end-of-stream marker (`end`), and
 * conversions from an `Exit` or the result of an effect.
 */
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
   * Creates a failing `Take[E, Nothing]` with the specified cause.
   */
  def failCause[E](c: Cause[E]): Take[E, Nothing] =
    Take(Exit.failCause(c.map(Some(_))))

  /**
   * Creates an effect that runs `zio` and never fails: it succeeds with a take
   * of a singleton chunk when `zio` succeeds, and with `Take.failCause` of its
   * cause (defects included) when `zio` fails.
   */
  def fromZIO[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): URIO[R, Take[E, A]] =
    zio.foldCause(failCause, single)

  /**
   * Creates an effect that runs `pull` and never fails: it succeeds with a take
   * of the pulled chunk, with `Take.end` when `pull` fails with `None`, and
   * with `Take.failCause` of the cause otherwise (defects included).
   */
  def fromPull[R, E, A](pull: ZStream.Pull[R, E, A])(implicit trace: Trace): URIO[R, Take[E, A]] =
    pull.foldCause(Cause.flipCauseOption(_).fold[Take[E, Nothing]](end)(failCause), chunk)

  /**
   * Creates a `Take[Nothing, Nothing]` that dies with the specified throwable.
   */
  def die(t: Throwable): Take[Nothing, Nothing] =
    Take(Exit.die(t))

  /**
   * Creates a `Take[Nothing, Nothing]` that dies with a `RuntimeException` with
   * the specified message.
   */
  def dieMessage(msg: String): Take[Nothing, Nothing] =
    Take(Exit.die(new RuntimeException(msg)))

  /**
   * Creates a `Take[E, A]` from `Exit[E, A]`: a success becomes a singleton
   * chunk, and a failure keeps its cause.
   */
  def done[E, A](exit: Exit[E, A]): Take[E, A] =
    Take(exit.mapErrorExit[Option[E]]((e: E) => Some(e)).mapExit(Chunk.single))

  /**
   * The end-of-stream marker.
   */
  val end: Take[Nothing, Nothing] =
    Take(Exit.failNone)
}
