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
   * specified strategy both agree to retry. For pure strategies (which have
   * deterministic initial states/updates), the following law holds:
   * {{{
   * io.retryWith(r && r) === io.retryWith(r)
   * }}}
   */
  final def &&[S2](that: Retry[S2, E]): Retry[(S, S2), E] =
    new Retry[(S, S2), E] {
      type State = (self.State, that.State)

      val initial = self.initial.par(that.initial)

      def proj(state: State): (S, S2) =
        (self.proj(state._1), that.proj(state._2))

      def update(e: E, s: State): IO[E, State] =
        self.update(e, s._1).par(that.update(e, s._2))
    }

  /**
   * Returns a new strategy that retries for as long as either this strategy or
   * the specified strategy want to retry. For pure strategies (which have
   * deterministic initial states/updates), the following law holds:
   * {{{
   * io.retryWith(r || r) === io.retryWith(r)
   * }}}
   */
  final def ||[S2](that: Retry[S2, E]): Retry[Either[S, S2], E] =
    new Retry[Either[S, S2], E] {
      type State =
        Either[(self.State, that.State), Either[self.State, that.State]]

      val initial = self.initial.attempt.par(that.initial.attempt).flatMap(makeState(_))

      private def makeState(state: (Either[E, self.State], Either[E, that.State])): IO[E, State] = state match {
        case (Left(_), Left(e))     => IO.fail(e)
        case (Left(_), Right(s2))   => IO.now(Right(Right(s2)))
        case (Right(s1), Left(_))   => IO.now(Right(Left(s1)))
        case (Right(s1), Right(s2)) => IO.now(Left((s1, s2)))
      }

      def proj(state: State): Either[S, S2] = state match {
        case Left((s, _))    => Left(self.proj(s))
        case Right(Left(s))  => Left(self.proj(s))
        case Right(Right(s)) => Right(that.proj(s))
      }

      def update(e: E, state: State): IO[E, State] = state match {
        case Left((s1, s2)) =>
          self
            .update(e, s1)
            .attempt
            .par(
              that.update(e, s2).attempt
            )
            .flatMap(makeState(_))

        case Right(Left(s1)) =>
          self.update(e, s1).attempt.par(IO.fail(e).attempt).flatMap(makeState(_))

        case Right(Right(s2)) =>
          IO.fail(e).attempt.par(that.update(e, s2).attempt).flatMap(makeState(_))
      }
    }

  /**
   * Returns a new strategy that first tries this strategy, and if it fails,
   * then switches over to the specified strategy. The returned strategy is
   * maximally lazy, not computing the initial state of the specified strategy
   * until when and if this strategy fails.
   * {{{
   * io.retryWith(Retry.never <> r.void) === io.retryWith(r)
   * io.retryWith(r.void <> Retry.never) === io.retryWith(r)
   * }}}
   */
  final def <>[S1 >: S](that: Retry[S1, E]): Retry[S1, E] = new Retry[S1, E] {
    type State = Either[self.State, that.State]

    val initial =
      self.initial.attempt.flatMap {
        case Left(_)  => that.initial.map(Right(_))
        case Right(s) => IO.now(Left(s))
      }

    def proj(state: State): S1 = state.fold[S1](self.proj, that.proj)

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

  /**
   * Returns a new retry strategy with the state transformed by the specified
   * function.
   */
  final def map[S2](f: S => S2): Retry[S2, E] = new Retry[S2, E] {
    type State = self.State
    val initial                              = self.initial
    def proj(state: State): S2               = f(self.proj(state))
    def update(e: E, s: State): IO[E, State] = self.update(e, s)
  }

  /**
   * Returns a new retry strategy that always produces the constant state.
   */
  final def const[S2](s2: S2): Retry[S2, E] = map(_ => s2)

  /**
   * Returns a new retry strategy that always produces unit state.
   */
  final def void: Retry[Unit, E] = const(())
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
   * A retry strategy that always fails.
   */
  final def never[E]: Retry[Unit, E] =
    Retry[Unit, E](IO.unit, (e, _) => IO.fail(e))

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
