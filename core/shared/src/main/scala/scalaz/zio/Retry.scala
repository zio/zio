// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/**
 * A stateful strategy for retrying `IO` actions. See `IO.retryWith`.
 */
trait Retry[E, +S] { self =>

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
  def unary_! : Retry[E, S] = new Retry[E, S] {
    type State = self.State

    val initial = self.initial

    def proj(state: State): S = self.proj(state)

    def update(e: E, s: State): IO[E, State] =
      // FIXME This impl never updates the state leading to infinite recursion
      self.update(e, s).redeem(_ => IO.now(s), _ => IO.fail(e))
  }

  /**
   * Peeks at the visible part of the state, executes some action, and then
   * continues retrying or not based on the specified predicate.
   */
  final def check[A](action: (E, S) => IO[E, A])(pred: A => Boolean): Retry[E, S] =
    new Retry[E, S] {
      type State = self.State

      val initial = self.initial

      def proj(state: State): S = self.proj(state)

      def update(e: E, s0: State): IO[E, State] =
        for {
          s <- self.update(e, s0)
          a <- action(e, proj(s))
          _ <- if (pred(a)) IO.now(s) else IO.fail(e)
        } yield s
    }

  /**
   * Returns a new strategy that retries while the error matches the condition.
   */
  final def whileError(p: E => Boolean): Retry[E, S] =
    check[E]((e, _) => IO.now(e))(p)

  /**
   * Returns a new strategy that retries until the error matches the condition.
   */
  final def untilError(p: E => Boolean): Retry[E, S] =
    whileError(!p(_))

  /*
   * Returns a new strategy that retries until the state matches the condition.
   */
  final def untilState(p: S => Boolean): Retry[E, S] =
    whileState(!p(_))

  /*
   * Returns a new strategy that retries while the state matches the condition.
   */
  final def whileState(p: S => Boolean): Retry[E, S] =
    check[S]((_, s) => IO.now(s))(p)

  /**
   * Returns a new strategy that retries for as long as this strategy and the
   * specified strategy both agree to retry. For pure strategies (which have
   * deterministic initial states/updates), the following law holds:
   * {{{
   * io.retryWith(r && r) === io.retryWith(r)
   * }}}
   */
  final def &&[S2](that0: => Retry[E, S2]): Retry[E, (S, S2)] =
    new Retry[E, (S, S2)] {
      lazy val that = that0

      type State = (self.State, that.State)

      val initial = self.initial.par(that.initial)

      def proj(state: State): (S, S2) =
        (self.proj(state._1), that.proj(state._2))

      def update(e: E, s: State): IO[E, State] =
        self.update(e, s._1).par(that.update(e, s._2))
    }

  final def both[S2](that: => Retry[E, S2]): Retry[E, (S, S2)] =
    self && that

  final def bothWith[S2, A](that: => Retry[E, S2])(f: (S, S2) => A): Retry[E, A] =
    (self && that).map(f.tupled)

  /**
   * Returns a new strategy that retries for as long as either this strategy or
   * the specified strategy want to retry. For pure strategies (which have
   * deterministic initial states/updates), the following law holds:
   * {{{
   * io.retryWith(r || r) === io.retryWith(r)
   * }}}
   * Updates are run in parallel and only the winner updates its state. The
   * loser is terminated and its prior state is used in the next iteration.
   */
  final def ||[S2](that0: => Retry[E, S2]): Retry[E, Either[S, S2]] =
    new Retry[E, Either[S, S2]] {
      lazy val that = that0

      type State =
        Either[(self.State, that.State), Either[self.State, that.State]]

      val initial = self.initial.attempt.par(that.initial.attempt).flatMap(makeState(_))

      def proj(state: State): Either[S, S2] = state match {
        case Left((s, _))    => Left(self.proj(s))
        case Right(Left(s))  => Left(self.proj(s))
        case Right(Right(s)) => Right(that.proj(s))
      }

      def update(e: E, state: State): IO[E, State] =
        state match {
          case Left((s1, s2)) =>
            self
              .update(e, s1)
              .attempt
              .raceBoth(
                that.update(e, s2).attempt
              )
              .flatMap {
                case Left(s1)  => makeState((s1, Right(s2)))
                case Right(s2) => makeState((Right(s1), s2))
              }

          case Right(Left(s1)) =>
            self.update(e, s1).attempt.map(Left(_)).flatMap(makeState2(_))

          case Right(Right(s2)) =>
            that.update(e, s2).attempt.map(Right(_)).flatMap(makeState2(_))
        }

      private def makeState(state: (Either[E, self.State], Either[E, that.State])): IO[E, State] = state match {
        case (Left(_), Left(e))     => IO.fail(e)
        case (Left(_), Right(s2))   => IO.now(Right(Right(s2)))
        case (Right(s1), Left(_))   => IO.now(Right(Left(s1)))
        case (Right(s1), Right(s2)) => IO.now(Left((s1, s2)))
      }

      private def makeState2(state: Either[Either[E, self.State], Either[E, that.State]]): IO[E, State] = state match {
        case Left(Left(e))   => IO.fail(e)
        case Left(Right(s))  => IO.now(Right(Left(s)))
        case Right(Left(e))  => IO.fail(e)
        case Right(Right(s)) => IO.now(Right(Right(s)))
      }
    }

  final def either[S2](that: => Retry[E, S2]): Retry[E, Either[S, S2]] =
    self || that

  final def eitherWith[S2, A](that: => Retry[E, S2])(f: Either[S, S2] => A): Retry[E, A] =
    (self || that).map(f)

  /**
   * Same as `<||>`, but merges the states.
   */
  final def <>[S1 >: S](that: => Retry[E, S1]): Retry[E, S1] =
    (self <||> that).map(_.merge)

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
  final def <||>[S2](that0: => Retry[E, S2]): Retry[E, Either[S, S2]] =
    new Retry[E, Either[S, S2]] {
      lazy val that = that0

      type State = Either[self.State, that.State]

      val initial =
        self.initial.attempt.flatMap {
          case Left(_)  => that.initial.map(Right(_))
          case Right(s) => IO.now(Left(s))
        }

      def proj(state: State): Either[S, S2] =
        state fold [Either[S, S2]] (l => Left(self.proj(l)),
        r => Right(that.proj(r)))

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
   * A named version of the `<||>` operator.
   */
  final def andThen[S2](that0: => Retry[E, S2]): Retry[E, Either[S, S2]] =
    self <||> that0

  /**
   * Returns a new retry strategy with the state transformed by the specified
   * function.
   */
  final def map[S2](f: S => S2): Retry[E, S2] = new Retry[E, S2] {
    type State = self.State
    val initial                              = self.initial
    def proj(state: State): S2               = f(self.proj(state))
    def update(e: E, s: State): IO[E, State] = self.update(e, s)
  }

  /**
   * Returns a new retry strategy that always produces the constant state.
   */
  final def const[S2](s2: S2): Retry[E, S2] = map(_ => s2)

  /**
   * Returns a new retry strategy that always produces unit state.
   */
  final def void: Retry[E, Unit] = const(())

  /**
   * The same as `&&`, but discards the right hand state.
   */
  final def *>[S2](that: => Retry[E, S2]): Retry[E, S2] =
    (self && that).map(_._2)

  /**
   * The same as `&&`, but discards the left hand state.
   */
  final def <*[S2](that: => Retry[E, S2]): Retry[E, S] =
    (self && that).map(_._1)

  /**
   * A new strategy that applies the current one but runs the specified effect
   * for every error.
   */
  final def errorEffect(f: E => IO[E, Unit]): Retry[E, S] =
    updated((e, _, io) => io <* f(e))

  /**
   * A new strategy that applies the current one but runs the specified effect
   * for every state.
   */
  final def stateEffect(f: S => IO[E, Unit]): Retry[E, S] =
    self
      .initialized(io => io.flatMap(s => f(self.proj(s)).const(s)))
      .updated((_, s, io) => io <* f(s))

  /**
   * Returns a new retry strategy that applies the combinator to each update
   * produced by this retry strategy.
   */
  final def updated(f: (E, S, IO[E, self.State]) => IO[E, self.State]): Retry[E, S] =
    new Retry[E, S] {
      type State = self.State
      val initial                              = self.initial
      def proj(state: State): S                = self.proj(state)
      def update(e: E, s: State): IO[E, State] = f(e, self.proj(s), self.update(e, s))
    }

  /**
   * Returns a new retry strategy that applies the combinator to the initial
   * state produced by this retry strategy.
   */
  final def initialized(f: IO[E, self.State] => IO[E, self.State]): Retry[E, S] =
    new Retry[E, S] {
      type State = self.State
      val initial                              = f(self.initial)
      def proj(state: State): S                = self.proj(state)
      def update(e: E, s: State): IO[E, State] = self.update(e, s)
    }
}

object Retry {

  /**
   * Constructs a new retry strategy from an initial state and an update function.
   */
  final def apply[E, S](initial0: IO[E, S], update0: (E, S) => IO[E, S]): Retry[E, S] = new Retry[E, S] {
    type State = S
    val initial                              = initial0
    def proj(state: State): S                = state
    def update(e: E, s: State): IO[E, State] = update0(e, s)
  }

  /**
   * A retry strategy that always fails.
   */
  final def never[E]: Retry[E, Unit] =
    Retry[E, Unit](IO.unit, (e, _) => IO.fail(e))

  /**
   * A retry strategy that always succeeds.
   */
  final def always[E]: Retry[E, Unit] =
    Retry[E, Unit](IO.unit, (e, _) => IO.unit)

  /**
   * A retry strategy that always succeeds with the specified constant state.
   */
  final def point[E, S](s: => S): Retry[E, S] =
    Retry[E, S](IO.point(s), (_, s) => IO.now(s))

  /**
   * A retry strategy that always succeeds, collecting all errors into a list.
   */
  final def errors[E]: Retry[E, List[E]] =
    Retry[E, List[E]](IO.now(Nil), (e, l) => IO.now(e :: l))

  /**
   * A retry strategy that always retries and counts the number of retries.
   */
  final def counted[E]: Retry[E, Int] =
    Retry[E, Int](IO.now(0), (_, i) => IO.now(i + 1))

  /**
   * A retry strategy that always retries and computes the time since the
   * beginning of the process.
   */
  final def elapsed[E]: Retry[E, Duration] = {
    val nanoTime = IO.sync(System.nanoTime())

    Retry[E, (Long, Long)](nanoTime.seq(IO.now(0L)), (_, t) => nanoTime.map(t2 => (t._1, t2 - t._1)))
      .map(t => Duration(t._2, TimeUnit.NANOSECONDS))
  }

  /**
   * A retry strategy that will keep retrying until the specified number of
   * retries is reached.
   */
  final def retries[E](max: Int): Retry[E, Int] = counted.untilState(_ >= max)

  /**
   * A retry strategy that will keep retrying until the specified duration has
   * elapsed.
   */
  final def duration[E](duration: Duration): Retry[E, Duration] = {
    val nanos = duration.toNanos

    elapsed.untilState(_.toNanos >= nanos)
  }

  /**
   * A retry strategy that will always succeed, waiting the specified fixed
   * duration between attempts.
   */
  final def fixed[E](duration: Duration): Retry[E, Int] =
    counted.updated((_, _, io) => io.delay(duration))

  /**
   * A retry strategy that will always succeed, but will wait a certain amount between retries.
   *
   * This is the most flexible version of backoff which allows to compute the
   * time to sleep based on number of retries and previous sleep time.
   *
   * @param base Time to sleep on the first retry
   * @param sleep Function to compute the time to sleep on the next retry.
   *              It receives to parameter (n, d), where n is the number
   *              of retries so far and d is the previous sleep time.
   *              It returns IO so that is can perform effects such as
   *              query the current time or as for a random number.
   */
  final def exponential0[E](base: Duration, sleep: (Int, Duration) => IO[E, Duration]): Retry[E, (Int, Duration)] = {
    val up: ((Int, Duration)) => IO[E, (Int, Duration)] = {
      case (n, d) => sleep(n, d).map((n + 1, _)).delay(d)
    }
    Retry[E, (Int, Duration)](IO.now((0, base)), (_, s) => up(s))
  }

  /**
   * A retry strategy that will always succeed, but will wait a certain amount
   * between retries, given by `base * factor.pow(n)`, where `n` is the
   * number of retries so far.
   */
  final def exponential[E](base: Duration, factor: Double = 2.0): Retry[E, (Int, Duration)] =
    exponential0(base, (n, _) => IO.now(base * math.pow(factor, n.doubleValue)))

  /**
   * Exponential backoff with jitter as described here
   * [[https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/ Exponential Backoff And Jitter]]
   *
   * It computes the next time to sleep in the same way as `exponential` does:
   * {{{
   * val exp = base * math.pow(factor, n)
   * }}}
   * But it adds some randomness to the equation as specified by `rndRangeFactors = (minFac, maxFac)`
   * {{{
   * val sleep = randomBetween(exp * minFac, exp * maxFac)
   * }}}
   * In order to get '''Full Jitter''' use `rndRangeFactors = (0, 1)` (default). If you prefer
   * '''Equal Jitter''' use `rndRangeFactors = (0.5, 0.5)`
   */
  final def exponentialJitter[E](base: Duration,
                                 factor: Double = 2.0,
                                 rndRangeFactors: (Double, Double) = (0.0, 1.0)): Retry[E, (Int, Duration)] = {
    def jitter(n: Int) = {
      val exp                    = base * math.pow(factor, n.doubleValue)
      val (rndMinFac, rndMaxFac) = rndRangeFactors
      IO.sync(util.Random.nextDouble()).map(exp * rndMinFac + exp * rndMaxFac * _)
    }
    exponential0(base, (n, _) => jitter(n))
  }

}
