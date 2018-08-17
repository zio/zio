// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/**
 * A stateful strategy for retrying `IO` actions. See `IO.retryWith`.
 */
trait Retry[E, +A] { self =>

  /**
   * The full type of state used by the retry strategy, including hidden state
   * not exposed via the `A` type parameter.
   */
  type State

  /**
   * Projects out the visible part of the state `A`.
   */
  def value(state: State): A

  /**
   * The initial state of the strategy. This can be an effect, such as
   * `nanoTime`.
   */
  val initial: IO[Nothing, State]

  /**
   * Invoked on an error. This method can return the next state, which will continue
   * the retry process, or it can return a failure, which will terminate the retry.
   */
  def update(e: E, s: State): IO[Nothing, Retry.Decision[State]]

  /**
   * Negates this strategy, returning failures for successes, and successes
   * for failures.
   */
  def unary_! : Retry[E, A] = new Retry[E, A] {
    type State = self.State

    val initial = self.initial

    def value(state: State): A = self.value(state)

    def update(e: E, s: State): IO[Nothing, Retry.Decision[State]] = self.update(e, s).map(!_)
  }

  /**
   * Peeks at the visible part of the state, executes some action, and then
   * continues retrying or not based on the specified predicate.
   */
  final def check[B](action: (E, A) => IO[Nothing, B])(pred: B => Boolean): Retry[E, A] =
    new Retry[E, A] {
      type State = self.State

      val initial = self.initial

      def value(state: State): A = self.value(state)

      def update(e: E, s: State): IO[Nothing, Retry.Decision[State]] =
        for {
          d <- self.update(e, s)
          b <- action(e, value(s))
        } yield d.copy(retry = pred(b))
    }

  /**
   * Returns a new strategy that retries while the error matches the condition.
   */
  final def whileError(p: E => Boolean): Retry[E, A] =
    check[E]((e, _) => IO.now(e))(p)

  /**
   * Returns a new strategy that retries until the error matches the condition.
   */
  final def untilError(p: E => Boolean): Retry[E, A] =
    !whileError(p)

  /*
   * Returns a new strategy that retries until the state matches the condition.
   */
  final def untilState(p: A => Boolean): Retry[E, A] =
    !whileState(p)

  /*
   * Returns a new strategy that retries while the state matches the condition.
   */
  final def whileState(p: A => Boolean): Retry[E, A] =
    check[A]((_, s) => IO.now(s))(p)

  /**
   * Returns a new strategy that retries for as long as this strategy and the
   * specified strategy both agree to retry. For pure strategies (which have
   * deterministic initial states/updates), the following law holds:
   * {{{
   * io.retryWith(r && r) === io.retryWith(r)
   * }}}
   */
  final def &&[A2](that0: => Retry[E, A2]): Retry[E, (A, A2)] =
    new Retry[E, (A, A2)] {
      lazy val that = that0

      type State = (self.State, that.State)

      val initial = self.initial.par(that.initial)

      def value(state: State): (A, A2) =
        (self.value(state._1), that.value(state._2))

      def update(e: E, s: State): IO[Nothing, Retry.Decision[State]] =
        self.update(e, s._1).parWith(that.update(e, s._2))(_ && _)
    }

  final def both[A2](that: => Retry[E, A2]): Retry[E, (A, A2)] =
    self && that

  final def bothWith[A2, A3](that: => Retry[E, A2])(f: (A, A2) => A3): Retry[E, A3] =
    (self && that).map(f.tupled)

  /**
   * Returns a new strategy that retries for as long as either this strategy or
   * the specified strategy want to retry.
   */
  final def ||[A2](that0: => Retry[E, A2]): Retry[E, (A, A2)] =
    new Retry[E, (A, A2)] {
      lazy val that = that0

      type State = (self.State, that.State)

      val initial = self.initial.par(that.initial)

      def value(state: State): (A, A2) = (self.value(state._1), that.value(state._2))

      def update(e: E, state: State): IO[Nothing, Retry.Decision[State]] =
        self.update(e, state._1).parWith(that.update(e, state._2))(_ || _)
    }

  final def either[A2](that: => Retry[E, A2]): Retry[E, (A, A2)] =
    self || that

  final def eitherWith[A2, A3](that: => Retry[E, A2])(f: (A, A2) => A3): Retry[E, A3] =
    (self || that).map(f.tupled)

  /**
   * Same as `<||>`, but merges the states.
   */
  final def <>[S1 >: A](that: => Retry[E, S1]): Retry[E, S1] =
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
  final def <||>[A2](that0: => Retry[E, A2]): Retry[E, Either[A, A2]] =
    new Retry[E, Either[A, A2]] {
      lazy val that = that0

      type State = Either[self.State, that.State]

      val initial = self.initial.map(Left(_))

      def value(state: State): Either[A, A2] =
        state fold [Either[A, A2]] (l => Left(self.value(l)),
        r => Right(that.value(r)))

      def update(e: E, s: State): IO[Nothing, Retry.Decision[State]] =
        s match {
          case Left(s1) =>
            self
              .update(e, s1)
              .flatMap(
                step =>
                  if (step.retry) IO.now(step.map(Left(_)))
                  else that.initial.flatMap(s2 => that.update(e, s2)).map(_.map(Right(_)))
              )
          case Right(s2) =>
            that.update(e, s2).map(_.map(Right(_)))
        }
    }

  /**
   * A named version of the `<||>` operator.
   */
  final def andThen[A2](that0: => Retry[E, A2]): Retry[E, Either[A, A2]] =
    self <||> that0

  /**
   * Returns a new retry strategy with the state transformed by the specified
   * function.
   */
  final def map[A2](f: A => A2): Retry[E, A2] = new Retry[E, A2] {
    type State = self.State
    val initial                                                    = self.initial
    def value(state: State): A2                                    = f(self.value(state))
    def update(e: E, s: State): IO[Nothing, Retry.Decision[State]] = self.update(e, s)
  }

  /**
   * Returns a new retry strategy that always produces the constant state.
   */
  final def const[A2](s2: A2): Retry[E, A2] = map(_ => s2)

  /**
   * Returns a new retry strategy that always produces unit state.
   */
  final def void: Retry[E, Unit] = const(())

  /**
   * The same as `&&`, but discards the right hand state.
   */
  final def *>[A2](that: => Retry[E, A2]): Retry[E, A2] =
    (self && that).map(_._2)

  /**
   * The same as `&&`, but discards the left hand state.
   */
  final def <*[A2](that: => Retry[E, A2]): Retry[E, A] =
    (self && that).map(_._1)

  /**
   * A new strategy that applies the current one but runs the specified effect
   * for every update.
   */
  final def onUpdate(f: (E, Retry.Decision[A]) => IO[Nothing, Unit]): Retry[E, A] =
    new Retry[E, A] {
      type State = self.State
      val initial                = self.initial
      def value(state: State): A = self.value(state)
      def update(e: E, s: State): IO[Nothing, Retry.Decision[State]] =
        self.update(e, s).flatMap(step => f(e, step.map(value)) *> IO.now(step))
    }

  /**
   * Modifies the delay of this retry strategy by applying the specified
   * effectful function to the error, state, and current delay.
   */
  final def modifyDelay(f: (E, A, Duration) => IO[Nothing, Duration]): Retry[E, A] =
    mapStep((e, s) => f(e, s.value, s.delay).map(d => Retry.Decision[Unit](true, d, ())))

  /**
   * Modifies the duration and retry/no-retry status of this strategy.
   */
  final def mapStep(f: (E, Retry.Decision[A]) => IO[Nothing, Retry.Decision[Unit]]): Retry[E, A] =
    new Retry[E, A] {
      type State = self.State
      val initial                = self.initial
      def value(state: State): A = self.value(state)
      def update(e: E, s: State): IO[Nothing, Retry.Decision[State]] =
        for {
          step  <- self.update(e, s)
          step2 <- f(e, step.map(value))
        } yield step.copy(retry = step2.retry, delay = step2.delay)
    }

  /**
   * Delays the retry strategy by the specified amount.
   */
  final def delayed(f: Duration => Duration): Retry[E, A] =
    modifyDelay((_, _, d) => IO.now(f(d)))

  /**
   * Applies random jitter to the retry strategy bounded by the factors
   * 0.0 and 1.0.
   */
  final def jittered: Retry[E, A] = jittered(0.0, 1.0)

  /**
   * Applies random jitter to the retry strategy bounded by the specified factors.
   */
  final def jittered(min: Double, max: Double): Retry[E, A] =
    modifyDelay((_, _, delay) => IO.sync(util.Random.nextDouble()).map(random => delay * min + delay * max * random))
}

object Retry {
  final case class Decision[+A](retry: Boolean, delay: Duration, value: A) { self =>
    final def &&[B](that: Decision[B]): Decision[(A, B)] = {
      def max(d1: Duration, d2: Duration): Duration =
        if (d1 < d2) d2 else d1

      Decision(self.retry && that.retry, max(self.delay, that.delay), (self.value, that.value))
    }

    final def ||[B](that: Decision[B]): Decision[(A, B)] = {
      def min(d1: Duration, d2: Duration): Duration =
        if (d1 < d2) d1 else d2

      Decision(self.retry || that.retry, min(self.delay, that.delay), (self.value, that.value))
    }

    final def map[B](f: A => B): Decision[B] =
      Decision(retry, delay, f(value))

    final def unary_! : Decision[A] = copy(retry = !self.retry)
  }
  object Decision {
    def yes[A](a: A): Decision[A]                = Decision(true, Duration.Zero, a)
    def yesIO[A](a: A): IO[Nothing, Decision[A]] = IO.now(yes(a))

    def no[A](a: A): Decision[A]                = Decision(false, Duration.Zero, a)
    def noIO[A](a: A): IO[Nothing, Decision[A]] = IO.now(no(a))
  }

  /**
   * Constructs a new retry strategy from an initial state and an update function.
   */
  final def apply[E, A](initial0: IO[Nothing, A], update0: (E, A) => IO[Nothing, Retry.Decision[A]]): Retry[E, A] =
    new Retry[E, A] {
      type State = A
      val initial                                                    = initial0
      def value(state: State): A                                     = state
      def update(e: E, s: State): IO[Nothing, Retry.Decision[State]] = update0(e, s)
    }

  /**
   * A retry strategy that always fails.
   */
  final def never[E]: Retry[E, Unit] =
    Retry[E, Unit](IO.unit, (e, s) => Decision.noIO(s))

  /**
   * A retry strategy that always succeeds.
   */
  final def always[E]: Retry[E, Unit] =
    Retry[E, Unit](IO.unit, (_, s) => Decision.yesIO(s))

  /**
   * A retry strategy that always succeeds with the specified constant state.
   */
  final def point[E, A](a: => A): Retry[E, A] =
    always.const(a)

  /**
   * A retry strategy that always succeeds, collecting all errors into a list.
   */
  final def errors[E]: Retry[E, List[E]] =
    Retry[E, List[E]](IO.now(Nil), (e, l) => Decision.yesIO(e :: l))

  /**
   * A retry strategy that always retries and counts the number of retries.
   */
  final def counted[E]: Retry[E, Int] =
    Retry[E, Int](IO.now(0), (_, i) => Decision.yesIO(i + 1))

  /**
   * A retry strategy that always retries and computes the time since the
   * beginning of the process.
   */
  final def elapsed[E]: Retry[E, Duration] = {
    val nanoTime = IO.sync(System.nanoTime())

    Retry[E, (Long, Long)](nanoTime.seq(IO.now(0L)), (_, t) => nanoTime.map(t2 => Decision.yes((t._1, t2 - t._1))))
      .map(t => Duration(t._2, TimeUnit.NANOSECONDS))
  }

  /**
   * A retry strategy that will keep retrying until the specified number of
   * retries is reached.
   */
  final def retries[E](max: Int): Retry[E, Int] = counted.whileState(_ < max)

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
  final def fixed[E](delay: Duration): Retry[E, Int] =
    counted.delayed(_ + delay)

  /**
   * A retry strategy that always succeeds, and computes the state through
   * repeated application of a function to a base value.
   */
  final def stateful[E, A](a: A)(f: A => A): Retry[E, A] =
    Retry[E, A](IO.now(a), (_, a) => Decision.yesIO(f(a)))

  /**
   * A retry strategy that will always succeed, but will wait a certain amount
   * between retries, given by `base * factor.pow(n)`, where `n` is the
   * number of retries so far.
   */
  final def exponential[E](base: Duration, factor: Double = 2.0): Retry[E, Duration] =
    counted.map(i => base * math.pow(factor, i.doubleValue)).modifyDelay((e, s, _) => IO.now(s))
}
