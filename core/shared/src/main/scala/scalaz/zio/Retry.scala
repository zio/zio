// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.duration.Duration

/**
 * A stateful strategy for retrying `IO` actions. See `IO.retryWith`.
 */
trait Retry[+S, E] { self =>

  /**
   * The full type of state used by the retry strategy, including hidden state
   * not exposed via the `S` type parameter.
   */
  type State

  /**
   * Projects out the visible part of the state `S`.
   */
  def proj(state: State): S

  /**
   * The initial state of the strategy. This can be an effect, such as
   * `nanoTime`.
   */
  val initial: IO[E, State]

  /**
   * Invoked on an error. This method can return the next state, which will continue
   * the retry process, or it can return a failure, which will terminate the retry.
   */
  def update(e: E, s: State): IO[E, State]

  /**
   * Negates this strategy, returning failures for successes, and successes
   * for failures.
   */
  def unary_! : Retry[S, E] = new Retry[S, E] {
    type State = self.State

    val initial = self.initial

    def proj(state: State): S = self.proj(state)

    def update(e: E, s: State): IO[E, State] =
      self.update(e, s).redeem(_ => IO.now(s), _ => IO.fail(e))
  }

  /**
   * Peeks at the visible part of the state, executes some action, and then
   * continues retrying or not based on the specified predicate.
   */
  final def check[A](action: (E, S) => IO[E, A])(pred: A => Boolean): Retry[S, E] =
    new Retry[S, E] {
      type State = self.State

      val initial = self.initial

      def proj(state: State): S = self.proj(state)

      def update(e: E, s: State): IO[E, State] =
        for {
          s <- self.update(e, s)
          a <- action(e, proj(s))
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
      type State = (self.State, that.State)

      val initial = self.initial.zip(that.initial)

      def proj(state: State): (S, S2) =
        (self.proj(state._1), that.proj(state._2))

      def update(e: E, s: State): IO[E, State] =
        self.update(e, s._1).zip(that.update(e, s._2))
    }

  /**
   * Returns a new strategy that retries for as long as either this strategy or
   * the specified strategy agree to retry.
   */
  final def ||[S1 >: S](that: Retry[S1, E]): Retry[S1, E] =
    new Retry[S1, E] {
      type State = Either[self.State, that.State]

      val initial =
        self.initial.attempt.flatMap {
          case Left(_)  => that.initial.map(Right(_))
          case Right(s) => IO.now(Left(s))
        }

      def proj(state: State): S1 =
        state.fold[S1](self.proj, that.proj)

      def update(e: E, s: State): IO[E, State] =
        s match {
          case Left(s) =>
            self.update(e, s).attempt.flatMap {
              case Left(_)  => that.initial.map(Right(_))
              case Right(s) => IO.now(Left(s))
            }
          case Right(s) => that.update(e, s).map(Right(_))
        }
    }
}

object Retry {

  /**
   * Constructs a new retry strategy from an initial state and an update function.
   */
  final def apply[S, E](initial0: IO[E, S], update0: (E, S) => IO[E, S]): Retry[S, E] = new Retry[S, E] {
    type State = S
    val initial                              = initial0
    def proj(state: State): S                = state
    def update(e: E, s: State): IO[E, State] = update0(e, s)
  }

  /**
   * Constructs a new retry strategy from an initial state, a projection from
   * the full state to the visible state `S`, and an update function.
   */
  final def hidden[S0, S, E](initial0: IO[E, S0], proj0: S0 => S, update0: (E, S0) => IO[E, S0]): Retry[S, E] =
    new Retry[S, E] {
      type State = S0
      val initial                              = initial0
      def proj(state: State): S                = proj0(state)
      def update(e: E, s: State): IO[E, State] = update0(e, s)
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
  final def timed[E]: Retry[Long, E] = {
    val nanoTime = IO.sync(System.nanoTime())

    Retry.hidden[(Long, Long), Long, E](nanoTime.zip(IO.now(0L)), _._2, (_, t) => nanoTime.map(t2 => (t._1, t2 - t._1)))
  }

  /**
   * A retry strategy that will keep retrying until the specified number of
   * retries is reached.
   */
  final def upTo[E](max: Int): Retry[Int, E] = counted.untilState(_ >= max)

  /**
   * A retry strategy that will keep retrying until the specified duration has
   * elapsed.
   */
  final def upTill[E](duration: Duration): Retry[Long, E] = {
    val nanos = duration.toNanos

    timed.untilState(_ >= nanos)
  }
}
