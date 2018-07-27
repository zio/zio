// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.duration.Duration

/**
 * A stateful strategy for retrying `IO` actions. See `IO.retryWith`.
 */
trait Retry[S, E] { self =>

  /**
   * The initial state of the strategy. This can be an effect, such as
   * `nanoTime`.
   */
  val initial: IO[E, S]

  /**
   * Invoked on an error. This method can return the next state, which will continue
   * the retry process, or it can return a failure, which will terminate the retry.
   */
  def update(e: E, s: S): IO[E, S]

  /**
   * Negates this strategy, returning failures for successes, and successes
   * for failures.
   */
  def unary_! : Retry[S, E] = new Retry[S, E] {
    val initial = self.initial

    def update(e: E, s: S): IO[E, S] =
      self.update(e, s).redeem(_ => IO.now(s), _ => IO.fail(e))
  }

  /**
   * Peeks at the state, executes some action, and then continues retrying or not
   * based on the specified predicate.
   */
  final def check[A](action: (E, S) => IO[E, A])(pred: A => Boolean): Retry[S, E] =
    new Retry[S, E] {
      val initial = self.initial

      def update(e: E, s: S): IO[E, S] =
        for {
          s <- self.update(e, s)
          a <- action(e, s)
          _ <- if (pred(a)) IO.now(s) else IO.fail(e)
        } yield s
    }

  /**
   * Returns a new strategy that retries while the error matches the condition.
   */
  final def whileError(p: E => Boolean): Retry[S, E] =
    check[E]((e, _) => IO.now(e))(p)

  /**
   * Returns a new strategy that retries until the error matches the condition.
   */
  final def untilError(p: E => Boolean): Retry[S, E] = !whileError(p)

  /*
   * Returns a new strategy that retries until the state matches the condition.
   */
  final def untilState(p: S => Boolean): Retry[S, E] = check[S]((_, s) => IO.now(s))(p)

  /*
   * Returns a new strategy that retries while the state matches the condition.
   */
  final def whileState(p: S => Boolean): Retry[S, E] = !untilState(p)

  /**
   * Returns a new strategy that retries for as long as this strategy and the
   * specified strategy both agree to retry.
   */
  final def &&[S2](that: Retry[S2, E]): Retry[(S, S2), E] =
    new Retry[(S, S2), E] {
      val initial = self.initial.zip(that.initial)

      def update(e: E, s: (S, S2)): IO[E, (S, S2)] =
        self.update(e, s._1).zip(that.update(e, s._2))
    }

  /**
   * Returns a new strategy that retries for as long as either this strategy or
   * the specified strategy agree to retry.
   */
  final def ||(that: Retry[S, E]): Retry[S, E] =
    new Retry[S, E] {
      val initial = self.initial orElse that.initial

      def update(e: E, s: S): IO[E, S] =
        (self update (e, s)) orElse (that update (e, s))
    }
}

object Retry {
  final def apply[S, E](initial0: IO[E, S], update0: (E, S) => IO[E, S]): Retry[S, E] = new Retry[S, E] {
    val initial                      = initial0
    def update(e: E, s: S): IO[E, S] = update0(e, s)
  }

  /**
   * A retry strategy that always retries and counts the number of retries.
   */
  final def counted[E]: Retry[Int, E] =
    Retry[Int, E](IO.now(0), (_, i) => IO.now(i + 1))

  /**
   * A retry strategy that always retries and computes the time since the
   * beginning of the process.
   */
  final def timed[E]: Retry[(Long, Long), E] = {
    val nanoTime = IO.sync(System.nanoTime())

    Retry[(Long, Long), E](nanoTime.zip(IO.now(0L)), (_, t) => nanoTime.map(t2 => (t._1, t2 - t._1)))
  }

  final def upTo[E](max: Int): Retry[Int, E] = counted.untilState(_ >= max)

  final def upTill[E](duration: Duration): Retry[(Long, Long), E] = {
    val nanos = duration.toNanos

    timed.untilState(_._2 >= nanos)
  }
}
