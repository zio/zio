// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.duration.Duration

/**
 * Defines a stateful, possibly effectful schedule for repetition.
 *
 * A `Repeat[A, B]` consumes `A` values from some repeated action, continues
 * repeating the action according to some schedule, and produces a `B` value
 * from some internal state, which is updated as the schedule progresses.
 *
 * These values from a monoid, which corresponds to concatenation of schedules.
 */
trait Repeat[-A, +B] { self =>
  import Repeat.Step

  /**
   * The internal state type of the repeat schedule.
   */
  type State

  /**
   * The initial state of the schedule, which depends on the first value.
   */
  val initial: A => IO[Nothing, State]

  /**
   * The starting delay of the schedule.
   */
  val start: IO[Nothing, Duration]

  /**
   * Extracts the value from the internal state.
   */
  val value: State => B

  /**
   * Updates the schedule based on the new value and the current state.
   */
  val update: (A, State) => IO[Nothing, Repeat.Step[State]]

  /**
   * Returns a new schedule that maps over the output of this one.
   */
  final def map[A1 <: A, C](f: B => C): Repeat[A1, C] =
    new Repeat[A1, C] {
      type State = self.State
      val initial = self.initial
      val start   = self.start
      val value   = f.compose(self.value)
      val update  = self.update
    }

  /**
   * Returns a new schedule that deals with a narrower class of inputs than
   * this one.
   */
  final def contramap[A1 <: A](f: A1 => A): Repeat[A1, B] =
    new Repeat[A1, B] {
      type State = self.State
      val initial = self.initial
      val start   = self.start
      val value   = self.value
      val update  = (a, s) => self.update(f(a), s)
    }

  /**
   * Peeks at the value produced by this schedule, executes some action, and
   * then continues the schedule or not based on the specified value predicate.
   */
  final def check[C](action: B => IO[Nothing, C])(f: C => Boolean): Repeat[A, B] =
    updated(
      update =>
        (
          (a,
           s) =>
            update(a, s).flatMap {
              case Step(cont, state, delay) =>
                if (cont)
                  action(value(state)).map(
                    c =>
                      if (f(c)) Step.cont(state, delay)
                      else Step.done(state, delay)
                  )
                else IO.now(Step.done(state, delay))
            }
      )
    )

  /**
   * Returns a new schedule that continues the schedule so long as the predicate
   * is satisfied on the output value of the schedule.
   */
  final def whileValue(f: B => Boolean): Repeat[A, B] =
    check[B](IO.now(_))(f)

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the output value of the schedule.
   */
  final def untilValue(f: B => Boolean): Repeat[A, B] =
    check[B](IO.now(_))(!f(_))

  final def combineWith[A1 <: A, C](that: Repeat[A1, C])(g: (Boolean, Boolean) => Boolean,
                                                         f: (Duration, Duration) => Duration): Repeat[A1, (B, C)] =
    new Repeat[A1, (B, C)] {
      type State = (self.State, that.State)
      val initial = (a: A1) => self.initial(a).par(that.initial(a))
      val start   = self.start.parWith(that.start)(_ max _)
      val value   = (state: State) => (self.value(state._1), that.value(state._2))
      val update  = (a: A1, s: State) => self.update(a, s._1).parWith(that.update(a, s._2))(_.combineWith(_)(g, f))
    }

  /**
   * Returns a new schedule that continues only as long as both schedules
   * continue, using the maximum of the delays of the two schedules.
   */
  final def &&[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, (B, C)] =
    combineWith(that)(_ && _, _ max _)

  /**
   * A named alias for `&&`.
   */
  final def both[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, (B, C)] = self && that

  /**
   * The same as `both` followed by `map`.
   */
  final def bothWith[A1 <: A, C, D](that: Repeat[A1, C])(f: (B, C) => D): Repeat[A1, D] =
    (self && that).map(f.tupled)

  /**
   * The same as `&&`, but ignores the left output.
   */
  final def *>[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, C] =
    (self && that).map(_._2)

  /**
   * The same as `&&`, but ignores the right output.
   */
  final def <*[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, B] =
    (self && that).map(_._1)

  /**
   * Returns a new schedule that continues as long as either schedule continues,
   * using the minimum of the delays of the two schedules.
   */
  final def ||[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, (B, C)] =
    combineWith(that)(_ || _, _ min _)

  /**
   * A named alias for `||`.
   */
  final def either[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, (B, C)] = self || that

  /**
   * The same as `either` followed by `map`.
   */
  final def eitherWith[A1 <: A, C, D](that: Repeat[A1, C])(f: (B, C) => D): Repeat[A1, D] =
    (self || that).map(f.tupled)

  /**
   * Returns a new schedule that first executes this schedule to completion,
   * and then executes the specified schedule to completion.
   */
  final def <||>[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, Either[B, C]] =
    new Repeat[A1, Either[B, C]] {
      type State = Either[self.State, that.State]

      val initial = (a: A1) => self.initial(a).map(Left(_))

      val start = self.start

      val value = (state: State) =>
        state match {
          case Left(v)  => Left(self.value(v))
          case Right(v) => Right(that.value(v))
      }

      val update = (a: A1, state: State) =>
        state match {
          case Left(v) =>
            self.update(a, v).flatMap { step =>
              if (step.cont) IO.now(step.map(Left(_)))
              else that.start.flatMap(start => that.initial(a).map(v => Step.cont(Right(v), start)))
            }
          case Right(v) =>
            that.update(a, v).map(_.map(Right(_)))
      }
    }

  /**
   * An alias for `<||>`
   */
  final def andThen[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, Either[B, C]] =
    self <||> that

  /**
   * The same as `<||>`, but merges the output.
   */
  final def <>[A1 <: A, B1 >: B](that: Repeat[A1, B1]): Repeat[A1, B1] =
    (self <||> that).map(_.merge)

  /**
   * Returns a new schedule that maps this schedule to a constant output.
   */
  final def const[C](c: => C): Repeat[A, C] = map(_ => c)

  /**
   * Returns a new schedule that maps this schedule to a Unit output.
   */
  final def void: Repeat[A, Unit] = const(())

  /**
   * Returns a new schedule that effectfully adjusts the starting delay of the
   * schedule.
   */
  final def startedM(f: Duration => IO[Nothing, Duration]): Repeat[A, B] =
    new Repeat[A, B] {
      type State = self.State
      val initial = self.initial
      val start   = self.start.flatMap(f)
      val value   = self.value
      val update  = self.update
    }

  /**
   * Returns a new schedule that adjusts the starting delay of the schedule.
   */
  final def started(f: Duration => Duration): Repeat[A, B] =
    startedM(d => IO.now(f(d)))

  /**
   * Returns a new repeat schedule with the specified effectful modification
   * applied to each delay produced by this schedule (except the start delay).
   */
  final def modifyDelay(f: (B, Duration) => IO[Nothing, Duration]): Repeat[A, B] =
    updated(
      update =>
        ((a,
          s) =>
           update(a, s).flatMap { step =>
             f(value(step.value), step.delay).map(d => step.delayed(_ => d))
           })
    )

  /**
   * Returns a new repeat schedule with the update function transformed by the
   * specified update transformer.
   */
  final def updated[A1 <: A](
    f: ((A, State) => IO[Nothing, Repeat.Step[State]]) => ((A1, State) => IO[Nothing, Repeat.Step[State]])
  ): Repeat[A1, B] =
    new Repeat[A1, B] {
      type State = self.State
      val initial = self.initial
      val start   = self.start
      val value   = self.value
      val update  = f(self.update)
    }

  /**
   * Returns a new repeat schedule with the specified initial value transforemd
   * by the specified initial transformer.
   */
  final def initialized[A1 <: A](f: (A => IO[Nothing, State]) => (A1 => IO[Nothing, State])): Repeat[A1, B] =
    new Repeat[A1, B] {
      type State = self.State
      val initial = f(self.initial)
      val start   = self.start
      val value   = self.value
      val update  = self.update
    }

  /**
   * Returns a new repeat schedule with the specified pure modification
   * applied to each delay produced by this schedule (except the start delay).
   */
  final def delayed(f: Duration => Duration): Repeat[A, B] =
    modifyDelay((_, d) => IO.now(f(d)))

  /**
   * Applies random jitter to the repeat schedule bounded by the factors
   * 0.0 and 1.0.
   */
  final def jittered: Repeat[A, B] = jittered(0.0, 1.0)

  /**
   * Applies random jitter to the repeat schedule bounded by the specified
   * factors.
   */
  final def jittered(min: Double, max: Double): Repeat[A, B] =
    modifyDelay((_, d) => IO.sync(util.Random.nextDouble()).map(random => d * min + d * max * random))

  /**
   * Sends every output value to the specified sink.
   */
  final def sink[A1 <: A](f: A1 => IO[Nothing, Unit]): Repeat[A1, B] =
    initialized[A1](initial => (a => f(a) *> initial(a))).updated[A1](update => (a, s) => f(a) *> update(a, s))

  /**
   * Returns a new schedule that collects the outputs of this one into a list.
   */
  final def collect: Repeat[A, List[B]] =
    fold(List[B](_))((xs, x) => x :: xs).map(_.reverse)

  /**
   * Returns a new schedule that folds over the outputs of this one.
   */
  final def fold[Z](fz: B => Z)(f: (Z, B) => Z): Repeat[A, Z] =
    foldM[Z](b => IO.now(fz(b)))((z, b) => IO.now(f(z, b)))

  /**
   * Returns a new schedule that effectfully folds over the outputs of this one.
   */
  final def foldM[Z](fz: B => IO[Nothing, Z])(f: (Z, B) => IO[Nothing, Z]): Repeat[A, Z] =
    new Repeat[A, Z] {
      type State = (self.State, Z)

      val initial = (a: A) =>
        for {
          state <- self.initial(a)
          z     <- fz(self.value(state))
        } yield (state, z)

      val start = self.start

      val value = (state: State) => state._2

      val update = (a: A, s0: State) =>
        for {
          step <- self.update(a, s0._1)
          z    <- f(s0._2, self.value(step.value))
        } yield step.map(s => (s, z))
    }

  /**
   * Returns a new schedule that reduces over this one.
   */
  final def reduce[B1 >: B](f: (B1, B1) => B1): Repeat[A, B1] =
    fold[B1](identity)(f)

  /**
   * Returns the composition of this schedule and the specified schedule,
   * by piping the output of this one into the input of the other, and summing
   * delays produced by both.
   */
  final def >>>[C](that: Repeat[B, C]): Repeat[A, C] =
    new Repeat[A, C] {
      type State = (self.State, that.State)
      val initial = (a: A) => self.initial(a).flatMap(s1 => that.initial(self.value(s1)).map(s2 => (s1, s2)))
      val start   = self.start.parWith(that.start)(_ + _)
      val value   = (state: State) => that.value(state._2)
      val update = (a: A, s: State) =>
        self.update(a, s._1).flatMap { step1 =>
          if (step1.cont) that.update(self.value(step1.value), s._2).map { step2 =>
            step1.combineWith(step2)(_ && _, _ + _)
          } else IO.now(Step.done((step1.value, s._2), step1.delay))
      }
    }

  /**
   * A backwards version of `>>>`.
   */
  final def <<<[C](that: Repeat[C, A]): Repeat[C, B] = that >>> self

  /**
   * An alias for `<<<`
   */
  final def compose[C](that: Repeat[C, A]): Repeat[C, B] = self <<< that
}

object Repeat {
  sealed case class Step[+A](cont: Boolean, value: A, delay: Duration) { self =>
    final def map[B](f: A => B): Step[B] = copy(value = f(value))

    final def delayed(f: Duration => Duration): Step[A] = copy(delay = f(delay))

    final def combineWith[B](that: Step[B])(g: (Boolean, Boolean) => Boolean,
                                            f: (Duration, Duration) => Duration): Step[(A, B)] =
      Step(g(self.cont, that.cont), (self.value, that.value), f(self.delay, that.delay))
  }
  object Step {
    def cont[A](a: A, d: Duration): Step[A] = Step(true, a, d)
    def done[A](a: A, d: Duration): Step[A] = Step(false, a, d)
  }

  final def apply[A, B](initial0: A => IO[Nothing, B],
                        start0: IO[Nothing, Duration],
                        update0: (A, B) => IO[Nothing, Repeat.Step[B]]): Repeat[A, B] = new Repeat[A, B] {
    type State = B
    val initial = initial0
    val start   = start0
    val value   = Predef.identity
    val update  = update0
  }

  /**
   * A schedule that repeats forever, returning each input as the output.
   */
  final def identity[A]: Repeat[A, A] =
    Repeat[A, A](IO.now(_), IO.now(Duration.Zero), (a, _) => IO.now(Step.cont(a, Duration.Zero)))

  /**
   * A schedule that never executes.
   */
  final def never: Repeat[Any, Unit] =
    Repeat[Any, Unit](_ => IO.now(()), IO.now(Duration.Inf), (_, s) => IO.now[Step[Unit]](Step.cont(s, Duration.Zero)))

  /**
   * A schedule that executes once.
   */
  final def once: Repeat[Any, Unit] =
    Repeat[Any, Unit](_ => IO.now(()), IO.now(Duration.Zero), (_, s) => IO.now[Step[Unit]](Step.done(s, Duration.Zero)))

  /**
   * A schedule that repeats forever, dumping values to the specified sink,
   * and returning those same values unmodified.
   */
  final def sink[A](f: A => IO[Nothing, Unit]): Repeat[A, A] =
    identity[A].sink(f)

  /**
   * A schedule that repeats forever, producing a count of repetitions.
   */
  final def forever: Repeat[Any, Int] =
    Repeat[Any, Int](_ => IO.now(1),
                     IO.now(Duration.Zero),
                     (_, i) => IO.now[Step[Int]](Step.cont(i + 1, Duration.Zero)))

  /**
   * A schedule that repeats the specified number of times, producing a count
   * of repetitions.
   */
  final def repeats(n: Int): Repeat[Any, Int] = forever.whileValue(_ < n)

  /**
   * A schedule that waits for the specified amount of time between each
   * repetition. Returns the number of repetitions so far.
   *
   * <pre>
   * |action|-----gap-----|action|-----gap-----|action|
   * </pre>
   */
  final def gap(interval: Duration): Repeat[Any, Int] =
    Repeat[Any, Int](
      _ => IO.sync(1),
      IO.now(Duration.Zero),
      (_, n) => IO.sync(Step.cont(n + 1, interval))
    )

  /**
   * A schedule that repeats the action on a fixed interval. Returns the amount
   * of time since the schedule began.
   *
   * If the action takes longer than the interval, then the action will be run
   * immediately, but re-runs will not "pile up".
   *
   * <pre>
   * |---------interval---------|---------interval---------|
   * |action|                   |action|
   * </pre>
   */
  final def fixed(interval: Duration): Repeat[Any, Int] =
    if (interval == Duration.Zero) forever
    else
      Repeat[Any, (Long, Int, Int)](
        _ => IO.sync((System.nanoTime(), 1, 1)),
        IO.now(Duration.Zero),
        (_, t) =>
          t match {
            case (start, n0, i) =>
              IO.sync(System.nanoTime()).map { now =>
                val await = ((start + n0 * interval.toNanos) - now)
                val n = 1 +
                  (if (await < 0) ((now - start) / interval.toNanos).toInt else n0)

                Step.cont((start, n, i + 1), Duration.fromNanos(await.max(0L)))
              }
        }
      ).map(_._3)
}
