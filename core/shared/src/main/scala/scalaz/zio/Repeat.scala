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
  def initial(a: A): IO[Nothing, State]

  /**
   * The starting delay of the schedule.
   */
  val start: IO[Nothing, Duration]

  /**
   * Extracts the value from the internal state.
   */
  def value(state: State): B

  /**
   * Updates the schedule based on the new value and the current state.
   */
  def update(a: A, s: State): IO[Nothing, Repeat.Step[State]]

  /**
   * Returns a new schedule that maps over the output of this one.
   */
  final def map[A1 <: A, C](f: B => C): Repeat[A1, C] =
    new Repeat[A1, C] {
      type State = self.State
      def initial(a: A1): IO[Nothing, State] = self.initial(a)
      val start                              = self.start
      def value(state: State): C             = f(self.value(state))
      def update(a: A1, s: State): IO[Nothing, Repeat.Step[State]] =
        self.update(a, s)
    }

  /**
   * Returns a new schedule that deals with a narrower class of inputs than
   * this one.
   */
  final def contramap[A1 <: A](f: A1 => A): Repeat[A1, B] =
    new Repeat[A1, B] {
      type State = self.State
      def initial(a: A1): IO[Nothing, State] = self.initial(a)
      val start                              = self.start
      def value(state: State): B             = self.value(state)
      def update(a: A1, s: State): IO[Nothing, Repeat.Step[State]] =
        self.update(f(a), s)
    }

  /**
   * Peeks at the value produced by this schedule, executes some action, and
   * then continues the schedule or not based on the specified value predicate.
   */
  final def check[C](action: B => IO[Nothing, C])(f: C => Boolean): Repeat[A, B] =
    new Repeat[A, B] {
      type State = self.State
      def initial(a: A): IO[Nothing, State] = self.initial(a)
      val start                             = self.start
      def value(state: State): B            = self.value(state)
      def update(a: A, s: State): IO[Nothing, Repeat.Step[State]] =
        self.update(a, s).flatMap {
          case Step.Done => IO.now(Step.Done)
          case Step.Cont(state, delay) =>
            action(value(state)).map(
              c =>
                if (f(c)) Step.Cont(state, delay)
                else Step.Done
            )
        }
    }

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

  /**
   * Returns a new schedule that continues only as long as both schedules
   * continue, using the maximum of the delays of the two schedules.
   */
  final def &&[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, (B, C)] =
    new Repeat[A1, (B, C)] {
      type State = (self.State, that.State)
      def initial(a: A1): IO[Nothing, State] = self.initial(a).par(that.initial(a))
      val start                              = self.start.parWith(that.start)(_ max _)
      def value(state: State): (B, C)        = (self.value(state._1), that.value(state._2))
      def update(a: A1, s: State): IO[Nothing, Repeat.Step[State]] =
        self.update(a, s._1).parWith(that.update(a, s._2)) {
          case (Step.Cont(v1, d1), Step.Cont(v2, d2)) => Step.Cont((v1, v2), d1 max d2)
          case _                                      => Step.Done
        }
    }

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
   * Returns a new schedule that first executes this schedule to completion,
   * and then executes the specified schedule to completion.
   */
  final def <||>[A1 <: A, C](that: Repeat[A1, C]): Repeat[A1, Either[B, C]] =
    new Repeat[A1, Either[B, C]] {
      type State = Either[self.State, that.State]

      def initial(a: A1): IO[Nothing, State] = self.initial(a).map(Left(_))

      val start = self.start

      def value(state: State): Either[B, C] =
        state match {
          case Left(v)  => Left(self.value(v))
          case Right(v) => Right(that.value(v))
        }

      def update(a: A1, state: State): IO[Nothing, Repeat.Step[State]] =
        state match {
          case Left(v) =>
            self.update(a, v).flatMap {
              case Step.Done =>
                that.start.flatMap(start => that.initial(a).map(v => Step.Cont(Right(v), start)))

              case c => IO.now(c.map(Left(_)))
            }
          case Right(v) => that.update(a, v).map(_.map(Right(_)))
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
   * Returns a new schedule that adjusts the starting delay of the schedule.
   */
  final def restart(f: Duration => Duration): Repeat[A, B] =
    new Repeat[A, B] {
      type State = self.State
      def initial(a: A): IO[Nothing, State] = self.initial(a)
      val start                             = self.start.map(f)
      def value(state: State): B            = self.value(state)
      def update(a: A, s: State): IO[Nothing, Repeat.Step[State]] =
        self.update(a, s)
    }

  /**
   * Returns a new repeat schedule with the specified effectful modification
   * applied to each delay produced by this schedule (except the start delay).
   */
  final def modifyDelay(f: (B, Duration) => IO[Nothing, Duration]): Repeat[A, B] =
    new Repeat[A, B] {
      type State = self.State
      def initial(a: A): IO[Nothing, State] = self.initial(a)
      val start                             = self.start
      def value(state: State): B            = self.value(state)
      def update(a: A, s: State): IO[Nothing, Repeat.Step[State]] =
        self.update(a, s).flatMap {
          case Step.Done => IO.now(Step.Done)
          case Step.Cont(state, delay) =>
            f(value(state), delay).map(Step.Cont(state, _))
        }
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
   * Returns a new schedule that collects the outputs of this one into a list.
   */
  final def collect: Repeat[A, List[B]] =
    fold[List[B]](Nil)((xs, x) => x :: xs).map(_.reverse)

  /**
   * Returns a new schedule that folds over the outputs of this one.
   */
  final def fold[Z](z: Z)(f: (Z, B) => Z): Repeat[A, Z] =
    new Repeat[A, Z] {
      type State = (self.State, Z)
      def initial(a: A): IO[Nothing, State] = self.initial(a).map[State]((_, z))
      val start                             = self.start
      def value(state: State): Z            = state._2
      def update(a: A, s0: State): IO[Nothing, Repeat.Step[State]] =
        self.update(a, s0._1).map(_.map(s => (s, f(s0._2, self.value(s)))))
    }

  /**
   * Returns a new schedule that reduces over this one.
   */
  final def reduce[B1 >: B](f: (B1, B1) => B1): Repeat[A, B1] =
    new Repeat[A, B1] {
      type State = (self.State, B1)
      def initial(a: A): IO[Nothing, State] = self.initial(a).map[State](s => (s, self.value(s)))
      val start                             = self.start
      def value(state: State): B1           = state._2
      def update(a: A, s0: State): IO[Nothing, Repeat.Step[State]] =
        self.update(a, s0._1).map(_.map(s => (s, f(s0._2, self.value(s)))))
    }

  /**
   * Returns the composition of this schedule and the specified schedule,
   * by piping the output of this one into the input of the other, and summing
   * delays produced by both.
   */
  final def >>>[C](that: Repeat[B, C]): Repeat[A, C] =
    new Repeat[A, C] {
      type State = (self.State, that.State)
      def initial(a: A): IO[Nothing, State] =
        self.initial(a).flatMap(s1 => that.initial(self.value(s1)).map(s2 => (s1, s2)))
      val start                  = self.start.parWith(that.start)(_ + _)
      def value(state: State): C = that.value(state._2)
      def update(a: A, s: State): IO[Nothing, Repeat.Step[State]] =
        self.update(a, s._1).flatMap {
          case Step.Done => IO.now(Step.Done)
          case Step.Cont(s1, d1) =>
            that.update(self.value(s1), s._2).map {
              case Step.Done => Step.Done
              case Step.Cont(s2, d2) =>
                Step.Cont((s1, s2), d1 + d2)
            }
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
  sealed trait Step[+A] { self =>
    import Step._

    final def map[B](f: A => B): Step[B] = self match {
      case Cont(v, d) => Cont(f(v), d)
      case Done       => Done
    }
  }
  object Step {
    final case class Cont[A](value: A, delay: Duration = Duration.Zero) extends Step[A]
    final case object Done                                              extends Step[Nothing]
  }

  final def apply[A, B](initial0: A => IO[Nothing, B],
                        start0: IO[Nothing, Duration],
                        update0: (A, B) => IO[Nothing, Repeat.Step[B]]): Repeat[A, B] = new Repeat[A, B] {
    type State = B

    def initial(a: A): IO[Nothing, State] = initial0(a)

    val start: IO[Nothing, Duration] = start0

    def value(state: State): B = state

    def update(a: A, s: State): IO[Nothing, Repeat.Step[B]] =
      update0(a, s)
  }

  /**
   * A schedule that repeats forever, returning each input as the output.
   */
  final def identity[A]: Repeat[A, A] =
    Repeat[A, A](IO.now(_), IO.now(Duration.Zero), (a, _) => IO.now(Step.Cont(a, Duration.Zero)))

  /**
   * A schedule that never executes.
   */
  final def never: Repeat[Any, Unit] =
    Repeat[Any, Unit](_ => IO.now(()), IO.now(Duration.Inf), (_, _) => IO.now[Step[Unit]](Step.Done))

  /**
   * A schedule that executes once.
   */
  final def once: Repeat[Any, Unit] =
    Repeat[Any, Unit](_ => IO.now(()), IO.now(Duration.Zero), (_, _) => IO.now[Step[Unit]](Step.Done))

  /**
   * A schedule that repeats forever, producing a count of repetitions.
   */
  final def forever: Repeat[Any, Int] =
    Repeat[Any, Int](_ => IO.now(1), IO.now(Duration.Zero), (_, i) => IO.now[Step[Int]](Step.Cont(i + 1)))

  /**
   * A schedule that repeats the specified number of times, producing a count
   * of repetitions.
   */
  final def repeat(n: Int): Repeat[Any, Int] = forever.whileValue(_ < n)

  /**
   * A schedule that repeats on the specified interval.
   */
  final def interval(delay: Duration): Repeat[Any, Duration] =
    Repeat[Any, (Long, Duration)](
      _ => IO.sync((System.nanoTime(), Duration.Zero)),
      IO.now(Duration.Zero),
      (_, t) =>
        t match {
          case (initial, _) =>
            IO.sync(System.nanoTime()).map(time => Step.Cont((initial, Duration.fromNanos(time - initial)), delay))
      }
    ).map(_._2)
}
