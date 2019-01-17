// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

/**
 * The `Async` class describes the return value of an asynchronous effect
 * that is imported into an `IO` value. Asynchronous effects can return
 * values synchronously via returning `now`, or promise to invoke a
 * callback via returning `later`.
 */
sealed abstract class Async[+E, +A] extends Product with Serializable

object Async extends Serializable {
  final case class Now[E, A](value: IO[E, A]) extends Async[E, A]
  final case object Later                     extends Async[Nothing, Nothing]

  /**
   * Constructs an `Async` that represents an uninterruptible asynchronous
   * action. The action should invoke the callback passed to the handler when
   * the value is available or the action has failed.
   *
   * See `IO.asyncInterrupt` for more information.
   */
  final val later: Async[Nothing, Nothing] = Later

  /**
   * Constructs an `Async` that represents a synchronous return.
   */
  final def now[E, A](io: IO[E, A]): Async[E, A] = Now(io)
}
