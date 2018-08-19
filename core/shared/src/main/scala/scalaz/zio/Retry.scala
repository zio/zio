// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/**
 * A stateful policy for retrying `IO` actions. See `IO.retry`.
 *
 * A `Retry[E, A]` value can handle errors of type `E`, and produces a value of
 * type `A` at every step. `Retry[E, A]` forms an applicative on the value,
 * allowing rich composition of different retry policies.
 *
 * Retry policies also compose in each of the following ways:
 *
 * 1. Intersection, using the `&&` operator, which requires that both policies
 *    agree to retry, using the longer of the two durations between retries.
 * 2. Union, using the `||` operator, which requires that only one policy
 *    agrees to retry, using the shorter of the two durations between retries.
 * 3. Sequence, using the `<||>` operator, which applies the first policy
 *    until it fails, and then switches over to the second policy.
 */
trait Retry[-E, +A] { self =>

  /**
   * The full type of state used by the retry policy, including hidden state
   * not exposed via the `A` type parameter.
   */
  type State

  /**
   * Extracts the value of this policy from the state.
   */
  val value: State => A

  /**
   * The initial state of the policy. This can be an effect, such as
   * `nanoTime`.
   */
  val initial: IO[Nothing, State]

  /**
   * Invoked on an error. This method can return the next state, which will continue
   * the retry process, or it can return a failure, which will terminate the retry.
   */
  val update: (E, State) => IO[Nothing, Retry.Decision[State]]

  /**
   * Negates this policy, returning failures for successes, and successes
   * for failures.
   */
  def unary_! : Retry[E, A] = updated(update => (e, s) => update(e, s).map(!_))

  /**
   * Peeks at the value produced by this policy, executes some action, and
   * then continues retrying or not based on the specified value predicate.
   */
  final def check[E1 <: E, B](action: (E1, A) => IO[Nothing, B])(pred: B => Boolean): Retry[E1, A] =
    updated(
      update =>
        ((e,
          s) =>
           for {
             d <- update(e, s)
             b <- action(e, value(s))
           } yield d.copy(retry = pred(b)))
    )

  /**
   * Returns a new policy that retries while the error matches the condition.
   */
  final def whileError[E1 <: E](p: E1 => Boolean): Retry[E1, A] =
    check[E1, E1]((e, _) => IO.now(e))(p)

  /**
   * Returns a new policy that retries until the error matches the condition.
   */
  final def untilError[E1 <: E](p: E1 => Boolean): Retry[E1, A] = !whileError(p)

  /*
   * Returns a new policy that retries until the value matches the condition.
   */
  final def untilValue(p: A => Boolean): Retry[E, A] = !whileValue(p)

  /*
   * Returns a new policy that retries while the value matches the condition.
   */
  final def whileValue(p: A => Boolean): Retry[E, A] =
    check[E, A]((_, a) => IO.now(a))(p)

  /**
   * Returns a new policy that retries for as long as this policy and the
   * specified policy both agree to retry, using the longer of the two
   * durations between retries.
   *
   * For pure policies (which have deterministic initial states/updates), the
   * following laws holds:
   *
   * {{{
   * io.retryWith(r && never).void === io.retryWith(never)
   * io.retryWith(r && r).void === io.retryWith(r).void
   * io.retryWith(r1 && r2).map(t => (t._2, t._1)) === io.retryWith(r2 && r1)
   * }}}
   */
  final def &&[E2 <: E, A2](that: Retry[E2, A2]): Retry[E2, (A, A2)] =
    new Retry[E2, (A, A2)] {
      type State = (self.State, that.State)
      val initial = self.initial.par(that.initial)
      val value   = (state: State) => (self.value(state._1), that.value(state._2))
      val update  = (e: E2, s: State) => self.update(e, s._1).parWith(that.update(e, s._2))(_ && _)
    }

  /**
   * A named alias for `&&`.
   */
  final def both[E2 <: E, A2](that: Retry[E2, A2]): Retry[E2, (A, A2)] = self && that

  /**
   * The same as `both` followed by `map`.
   */
  final def bothWith[E2 <: E, A2, A3](that: Retry[E2, A2])(f: (A, A2) => A3): Retry[E2, A3] =
    (self && that).map(f.tupled)

  /**
   * Returns a new policy that retries for as long as either this policy or
   * the specified policy want to retry, using the shorter of the two
   * durations.
   *
   * For pure policies (which have deterministic initial states/updates), the
   * following laws holds:
   *
   * {{{
   * io.retryWith(r || always).void === io.retryWith(r)
   * io.retryWith(r || r).void === io.retryWith(r).void
   * io.retryWith(r1 || r2).map(t => (t._2, t._1)) === io.retryWith(r2 || r1)
   * }}}
   */
  final def ||[E2 <: E, A2](that: Retry[E2, A2]): Retry[E2, (A, A2)] =
    new Retry[E2, (A, A2)] {
      type State = (self.State, that.State)
      val initial = self.initial.par(that.initial)
      val value   = (state: State) => (self.value(state._1), that.value(state._2))
      val update  = (e: E2, state: State) => self.update(e, state._1).parWith(that.update(e, state._2))(_ || _)
    }

  /**
   * A named alias for `||`.
   */
  final def either[E2 <: E, A2](that: Retry[E2, A2]): Retry[E2, (A, A2)] = self || that

  /**
   * The same as `either` followed by `map`.
   */
  final def eitherWith[E2 <: E, A2, A3](that: Retry[E2, A2])(f: (A, A2) => A3): Retry[E2, A3] =
    (self || that).map(f.tupled)

  /**
   * Same as `<||>`, but merges the states.
   */
  final def <>[E2 <: E, A2 >: A](that: Retry[E2, A2]): Retry[E2, A2] =
    (self <||> that).map(_.merge)

  /**
   * Returns a new policy that first tries this policy, and if it fails,
   * then switches over to the specified policy. The returned policy is
   * maximally lazy, not computing the initial state of the alternate policy
   * until when and if this policy fails.
   * {{{
   * io.retryWith(Retry.never <> r.void) === io.retryWith(r)
   * io.retryWith(r.void <> Retry.never) === io.retryWith(r)
   * }}}
   */
  final def <||>[E2 <: E, A2](that: Retry[E2, A2]): Retry[E2, Either[A, A2]] =
    new Retry[E2, Either[A, A2]] {
      type State = Either[self.State, that.State]

      val initial = self.initial.map(Left(_))

      val value = _ match {
        case Left(l)  => Left(self.value(l))
        case Right(r) => Right(that.value(r))
      }

      val update = (e: E2, s: State) =>
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
  final def andThen[E2 <: E, A2](that0: => Retry[E2, A2]): Retry[E2, Either[A, A2]] =
    self <||> that0

  /**
   * Returns a new retry policy with the value transformed by the specified
   * function.
   */
  final def map[A2](f: A => A2): Retry[E, A2] = new Retry[E, A2] {
    type State = self.State
    val initial = self.initial
    val value   = f.compose(self.value)
    val update  = self.update
  }

  /**
   * Returns a new retry policy with the capability to handle a narrower class
   * of errors `E2`.
   */
  final def contramap[E2](f: E2 => E): Retry[E2, A] = new Retry[E2, A] {
    type State = self.State
    val initial = self.initial
    val value   = self.value
    val update  = (e: E2, s: State) => self.update(f(e), s)
  }

  /**
   * Returns a new retry policy that always produces the constant state.
   */
  final def const[A2](a2: A2): Retry[E, A2] = map(_ => a2)

  /**
   * Returns a new retry policy that always produces unit state.
   */
  final def void: Retry[E, Unit] = const(())

  /**
   * The same as `&&`, but discards the right hand state.
   */
  final def *>[E2 <: E, A2](that: => Retry[E2, A2]): Retry[E2, A2] =
    (self && that).map(_._2)

  /**
   * The same as `&&`, but discards the left hand state.
   */
  final def <*[E2 <: E, A2](that: => Retry[E2, A2]): Retry[E2, A] =
    (self && that).map(_._1)

  /**
   * A new policy that applies the current one but runs the specified effect
   * for every decision of this policy. This can be used to create retry
   * policies that log failures, decisions, or computed values.
   */
  final def onDecision[E2 <: E](f: (E2, Retry.Decision[A]) => IO[Nothing, Unit]): Retry[E2, A] =
    updated(update => ((e, s) => update(e, s).peek(step => f(e, step.map(value)))))

  /**
   * Modifies the delay of this retry policy by applying the specified
   * effectful function to the error, state, and current delay.
   */
  final def modifyDelay[E2 <: E](f: (E2, A, Duration) => IO[Nothing, Duration]): Retry[E2, A] =
    reconsiderM((e, s) => f(e, s.value, s.delay).map(d => Retry.Decision[Unit](true, d, ())))

  /**
   * Returns a new retry strategy with the retry decision of this policy
   * modified by the specified effectful function.
   */
  final def reconsiderM[E2 <: E](f: (E2, Retry.Decision[A]) => IO[Nothing, Retry.Decision[Unit]]): Retry[E2, A] =
    updated(
      update =>
        ((e,
          s) =>
           for {
             step  <- update(e, s)
             step2 <- f(e, step.map(value))
           } yield step.copy(retry = step2.retry, delay = step2.delay))
    )

  /**
   * Returns a new retry strategy with the retry decision of this policy
   * modified by the specified function.
   */
  final def reconsider[E2 <: E](f: (E2, Retry.Decision[A]) => Retry.Decision[Unit]): Retry[E2, A] =
    reconsiderM((e, d) => IO.now(f(e, d)))

  /**
   * Returns a new retry strategy with the update function transformed by the
   * specified update transformer.
   */
  final def updated[E2 <: E](
    f: ((E, State) => IO[Nothing, Retry.Decision[State]]) => ((E2, State) => IO[Nothing, Retry.Decision[State]])
  ): Retry[E2, A] =
    new Retry[E2, A] {
      type State = self.State
      val initial = self.initial
      val value   = self.value
      val update  = f(self.update)
    }

  /**
   * Delays the retry policy by the specified amount.
   */
  final def delayed(f: Duration => Duration): Retry[E, A] =
    modifyDelay((_, _, d) => IO.now(f(d)))

  /**
   * Applies random jitter to the retry policy bounded by the factors
   * 0.0 and 1.0.
   */
  final def jittered: Retry[E, A] = jittered(0.0, 1.0)

  /**
   * Applies random jitter to the retry policy bounded by the specified factors.
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
    final def yes[A](a: A): Decision[A]                = Decision(true, Duration.Zero, a)
    final def yesIO[A](a: A): IO[Nothing, Decision[A]] = IO.now(yes(a))

    final def no[A](a: A): Decision[A]                = Decision(false, Duration.Zero, a)
    final def noIO[A](a: A): IO[Nothing, Decision[A]] = IO.now(no(a))
  }

  /**
   * Constructs a new retry policy from an initial value and an update function.
   */
  final def apply[E, A](initial0: IO[Nothing, A], update0: (E, A) => IO[Nothing, Retry.Decision[A]]): Retry[E, A] =
    new Retry[E, A] {
      type State = A
      val initial = initial0
      val value   = Predef.identity
      val update  = update0
    }

  /**
   * A retry policy that always fails.
   */
  final lazy val never: Retry[Any, Unit] = !always

  /**
   * A retry policy that always succeeds.
   */
  final lazy val always: Retry[Any, Unit] = Retry[Any, Unit](IO.unit, (_, s) => Decision.yesIO(s))

  /**
   * A retry policy that always succeeds with the specified constant state.
   */
  final def point[A](a: => A): Retry[Any, A] = always.const(a)

  /**
   * A retry policy that always succeeds, collecting all errors into a list.
   */
  final def errors[E]: Retry[E, List[E]] =
    Retry[E, List[E]](IO.now(Nil), (e, l) => Decision.yesIO(e :: l))

  /**
   * A retry policy that always retries and counts the number of retries.
   */
  final val counted: Retry[Any, Int] =
    Retry[Any, Int](IO.now(0), (_, i) => Decision.yesIO(i + 1))

  /**
   * A retry policy that always retries and computes the time since the
   * beginning of the process.
   */
  final val elapsed: Retry[Any, Duration] = {
    val nanoTime = IO.sync(System.nanoTime())

    Retry[Any, (Long, Long)](nanoTime.seq(IO.now(0L)), (_, t) => nanoTime.map(t2 => Decision.yes((t._1, t2 - t._1))))
      .map(t => Duration(t._2, TimeUnit.NANOSECONDS))
  }

  /**
   * A retry policy that will keep retrying until the specified number of
   * retries is reached.
   */
  final def retries(max: Int): Retry[Any, Int] = counted.whileValue(_ < max)

  /**
   * A retry policy that will keep retrying until the specified duration has
   * elapsed.
   */
  final def duration(duration: Duration): Retry[Any, Duration] = {
    val nanos = duration.toNanos

    elapsed.untilValue(_.toNanos >= nanos)
  }

  /**
   * A retry policy that will always succeed, waiting the specified fixed
   * duration between attempts.
   */
  final def fixed(delay: Duration): Retry[Any, Int] =
    counted.delayed(_ + delay)

  /**
   * A retry policy that always succeeds, and computes the state through
   * repeated application of a function to a base value.
   */
  final def stateful[A](a: A)(f: A => A): Retry[Any, A] =
    Retry[Any, A](IO.now(a), (_, a) => Decision.yesIO(f(a)))

  /**
   * A retry policy that always succeeds, increasing delays by summing the
   * preceeding two delays (similar to the fibonacci sequence)
   */
  final def fibonacci(one: Duration): Retry[Any, Duration] =
    stateful[(Duration, Duration)]((Duration.Zero, one)) {
      case (a1, a2) => (a2, a1 + a2)
    }.map(_._1).modifyDelay((_, delay, _) => IO.now(delay))

  /**
   * A retry policy that will always succeed, but will wait a certain amount
   * between retries, given by `base * factor.pow(n)`, where `n` is the
   * number of retries so far.
   */
  final def exponential(base: Duration, factor: Double = 2.0): Retry[Any, Duration] =
    counted.map(i => base * math.pow(factor, i.doubleValue)).modifyDelay((e, s, _) => IO.now(s))
}
