// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.duration.Duration

/**
 * Defines a stateful, possibly effectful, recurring schedule of actions.
 *
 * A `Schedule[A, B]` consumes `A` values, and based on the inputs and the
 * internal state, decides whether to recur or conclude. Every decision is
 * accompanied by a (possibly zero) delay, and an output value of type `B`.
 *
 * Schedules compose in each of the following ways:
 *
 * 1. Intersection, using the `&&` operator, which requires that both schedules
 *    continue, using the longer of the two durations.
 * 2. Union, using the `||` operator, which requires that only one schedule
 *    continues, using the shorter of the two durations.
 * 3. Sequence, using the `<||>` operator, which runs the first schedule until
 *    it ends, and then switches over to the second schedule.
 *
 * Thanks to (1), `Schedule[A, B]` forms an applicative functor on the output
 * value `B`, allowing rich composition of different schedules.
 */
trait Schedule[-A, +B] { self =>

  /**
   * The internal state type of the schedule.
   */
  type State

  /**
   * The initial state of the schedule.
   */
  val initial: IO[Nothing, State]

  /**
   * Updates the schedule based on a new input and the current state.
   */
  val update: (A, State) => IO[Nothing, Schedule.Decision[State, B]]

  /**
   * Returns a new schedule that inverts the decision to continue.
   */
  final def unary_! : Schedule[A, B] =
    updated(update => (a, s) => update(a, s).map(!_))

  /**
   * Returns a new schedule that maps over the output of this one.
   */
  final def map[A1 <: A, C](f: B => C): Schedule[A1, C] =
    new Schedule[A1, C] {
      type State = self.State
      val initial = self.initial
      val update  = (a: A1, s: State) => self.update(a, s).map(_.rightMap(f))
    }

  /**
   * Returns a new schedule that deals with a narrower class of inputs than
   * this schedule.
   */
  final def contramap[A1](f: A1 => A): Schedule[A1, B] =
    new Schedule[A1, B] {
      type State = self.State
      val initial = self.initial
      val update  = (a: A1, s: State) => self.update(f(a), s)
    }

  /**
   * Returns a new schedule that contramaps the input and maps the output.
   */
  final def dimap[A1, C](f: A1 => A, g: B => C): Schedule[A1, C] =
    contramap(f).map(g)

  /**
   * Returns a new schedule that loops this one forever, resetting the state
   * when this schedule is done.
   */
  final def loop: Schedule[A, B] =
    updated(
      update =>
        (a, s) =>
          update(a, s).flatMap { decision =>
            if (decision.cont) IO.now(decision)
            else self.initial.map(state => decision.copy(cont = true, state = state))
      }
    )

  /**
   * Peeks at the state produced by this schedule, executes some action, and
   * then continues the schedule or not based on the specified state predicate.
   */
  final def check[A1 <: A, C](test: (A1, B) => IO[Nothing, Boolean]): Schedule[A1, B] =
    updated(
      update =>
        ((a,
          s) =>
           update(a, s).flatMap { d =>
             if (d.cont) test(a, d.finish()).map(b => d.copy(cont = b))
             else IO.now(d)
           })
    )

  /**
   * Returns a new schedule that continues this schedule so long as the predicate
   * is satisfied on the output value of the schedule.
   */
  final def whileValue(f: B => Boolean): Schedule[A, B] =
    check[A, B]((_, b) => IO.now(f(b)))

  /**
   * Returns a new schedule that continues this schedule so long as the
   * predicate is satisfied on the input of the schedule.
   */
  final def whileInput[A1 <: A](f: A1 => Boolean): Schedule[A1, B] =
    check[A1, B]((a, _) => IO.now(f(a)))

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the output value of the schedule.
   */
  final def untilValue(f: B => Boolean): Schedule[A, B] = !whileValue(f)

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the input of the schedule.
   */
  final def untilInput[A1 <: A](f: A1 => Boolean): Schedule[A1, B] = !whileInput(f)

  final def combineWith[A1 <: A, C](that: Schedule[A1, C])(g: (Boolean, Boolean) => Boolean,
                                                           f: (Duration, Duration) => Duration): Schedule[A1, (B, C)] =
    new Schedule[A1, (B, C)] {
      type State = (self.State, that.State)
      val initial = self.initial.seq(that.initial)
      val update  = (a: A1, s: State) => self.update(a, s._1).seqWith(that.update(a, s._2))(_.combineWith(_)(g, f))
    }

  /**
   * Returns a new schedule that continues only as long as both schedules
   * continue, using the maximum of the delays of the two schedules.
   */
  final def &&[A1 <: A, C](that: Schedule[A1, C]): Schedule[A1, (B, C)] =
    combineWith(that)(_ && _, _ max _)

  /**
   * A named alias for `&&`.
   */
  final def both[A1 <: A, C](that: Schedule[A1, C]): Schedule[A1, (B, C)] = self && that

  /**
   * The same as `both` followed by `map`.
   */
  final def bothWith[A1 <: A, C, D](that: Schedule[A1, C])(f: (B, C) => D): Schedule[A1, D] =
    (self && that).map(f.tupled)

  /**
   * The same as `&&`, but ignores the left output.
   */
  final def *>[A1 <: A, C](that: Schedule[A1, C]): Schedule[A1, C] =
    (self && that).map(_._2)

  /**
   * The same as `&&`, but ignores the right output.
   */
  final def <*[A1 <: A, C](that: Schedule[A1, C]): Schedule[A1, B] =
    (self && that).map(_._1)

  /**
   * Returns a new schedule that continues as long as either schedule continues,
   * using the minimum of the delays of the two schedules.
   */
  final def ||[A1 <: A, C](that: Schedule[A1, C]): Schedule[A1, (B, C)] =
    combineWith(that)(_ || _, _ min _)

  /**
   * A named alias for `||`.
   */
  final def either[A1 <: A, C](that: Schedule[A1, C]): Schedule[A1, (B, C)] = self || that

  /**
   * The same as `either` followed by `map`.
   */
  final def eitherWith[A1 <: A, C, D](that: Schedule[A1, C])(f: (B, C) => D): Schedule[A1, D] =
    (self || that).map(f.tupled)

  /**
   * Returns a new schedule that first executes this schedule to completion,
   * and then executes the specified schedule to completion.
   */
  final def <||>[A1 <: A, C](that: Schedule[A1, C]): Schedule[A1, Either[B, C]] =
    new Schedule[A1, Either[B, C]] {
      type State = Either[self.State, that.State]

      val initial = self.initial.map(Left(_))

      val update = (a: A1, state: State) =>
        state match {
          case Left(v) =>
            self.update(a, v).flatMap { step =>
              if (step.cont) IO.now(step.bimap(Left(_), Left(_)))
              else
                for {
                  state <- that.initial
                  step  <- that.update(a, state)
                } yield step.bimap(Right(_), Right(_))
            }
          case Right(v) =>
            that.update(a, v).map(_.bimap(Right(_), Right(_)))
      }
    }

  /**
   * An alias for `<||>`
   */
  final def andThen[A1 <: A, C](that: Schedule[A1, C]): Schedule[A1, Either[B, C]] =
    self <||> that

  /**
   * The same as `<||>`, but merges the output.
   */
  final def <>[A1 <: A, B1 >: B](that: Schedule[A1, B1]): Schedule[A1, B1] =
    (self <||> that).map(_.merge)

  /**
   * Returns a new schedule that maps this schedule to a constant output.
   */
  final def const[C](c: => C): Schedule[A, C] = map(_ => c)

  /**
   * Returns a new schedule that maps this schedule to a Unit output.
   */
  final def void: Schedule[A, Unit] = const(())

  /**
   * Returns a new schedule that effectfully reconsiders the decision made by
   * this schedule.
   */
  final def reconsiderM[A1 <: A, B1 >: B](
    f: (A1, Schedule.Decision[State, B]) => IO[Nothing, Schedule.Decision[State, B1]]
  ): Schedule[A1, B1] =
    updated(
      update =>
        ((a: A1,
          s) =>
           for {
             step  <- update(a, s)
             step2 <- f(a, step)
           } yield step2)
    )

  /**
   * Returns a new schedule that reconsiders the decision made by this schedule.
   */
  final def reconsider[A1 <: A, B1 >: B](
    f: (A1, Schedule.Decision[State, B]) => Schedule.Decision[State, B1]
  ): Schedule[A1, B1] =
    reconsiderM((a, s) => IO.now(f(a, s)))

  /**
   * A new schedule that applies the current one but runs the specified effect
   * for every decision of this schedule. This can be used to create schedules
   * that log failures, decisions, or computed values.
   */
  final def onDecision[A1 <: A](f: (A1, Schedule.Decision[State, B]) => IO[Nothing, Unit]): Schedule[A1, B] =
    updated(update => ((a, s) => update(a, s).peek(step => f(a, step))))

  /**
   * Returns a new schedule with the specified effectful modification
   * applied to each delay produced by this schedule.
   */
  final def modifyDelay(f: (B, Duration) => IO[Nothing, Duration]): Schedule[A, B] =
    updated(
      update =>
        ((a,
          s) =>
           update(a, s).flatMap { step =>
             f(step.finish(), step.delay).map(d => step.delayed(_ => d))
           })
    )

  /**
   * Returns a new schedule with the update function transformed by the
   * specified update transformer.
   */
  final def updated[A1 <: A, B1 >: B](
    f: (
      (A, State) => IO[Nothing, Schedule.Decision[State, B]]
    ) => ((A1, State) => IO[Nothing, Schedule.Decision[State, B1]])
  ): Schedule[A1, B1] =
    new Schedule[A1, B1] {
      type State = self.State
      val initial = self.initial
      val update  = f(self.update)
    }

  /**
   * Returns a new schedule with the specified initial state transformed
   * by the specified initial transformer.
   */
  final def initialized[A1 <: A](f: IO[Nothing, State] => IO[Nothing, State]): Schedule[A1, B] =
    new Schedule[A1, B] {
      type State = self.State
      val initial = f(self.initial)
      val update  = self.update
    }

  /**
   * Returns a new schedule with the specified pure modification
   * applied to each delay produced by this schedule.
   */
  final def delayed(f: Duration => Duration): Schedule[A, B] =
    modifyDelay((_, d) => IO.now(f(d)))

  /**
   * Applies random jitter to the schedule bounded by the factors 0.0 and 1.0.
   */
  final def jittered: Schedule[A, B] = jittered(0.0, 1.0)

  /**
   * Applies random jitter to the schedule bounded by the specified factors.
   */
  final def jittered(min: Double, max: Double): Schedule[A, B] =
    modifyDelay((_, d) => IO.sync(util.Random.nextDouble()).map(random => d * min + d * max * random))

  /**
   * Sends every input value to the specified sink.
   */
  final def logInput[A1 <: A](f: A1 => IO[Nothing, Unit]): Schedule[A1, B] =
    updated[A1, B](update => (a, s) => f(a) *> update(a, s))

  /**
   * Sends every output value to the specified sink.
   */
  final def logOutput(f: B => IO[Nothing, Unit]): Schedule[A, B] =
    updated[A, B](update => (a, s) => update(a, s).flatMap(step => f(step.finish()) *> IO.now(step)))

  /**
   * Returns a new schedule that collects the outputs of this one into a list.
   */
  final def collect: Schedule[A, List[B]] =
    fold(List.empty[B])((xs, x) => x :: xs).map(_.reverse)

  /**
   * Returns a new schedule that folds over the outputs of this one.
   */
  final def fold[Z](z: Z)(f: (Z, B) => Z): Schedule[A, Z] =
    foldM[Z](IO.now(z))((z, b) => IO.now(f(z, b)))

  /**
   * Returns a new schedule that effectfully folds over the outputs of this one.
   */
  final def foldM[Z](z: IO[Nothing, Z])(f: (Z, B) => IO[Nothing, Z]): Schedule[A, Z] =
    new Schedule[A, Z] {
      type State = (self.State, Z)

      val initial = self.initial.seq(z)

      val update = (a: A, s0: State) =>
        for {
          step <- self.update(a, s0._1)
          z    <- f(s0._2, step.finish())
        } yield step.bimap(s => (s, z), _ => z)
    }

  /**
   * Returns the composition of this schedule and the specified schedule,
   * by piping the output of this one into the input of the other, and summing
   * delays produced by both.
   */
  final def >>>[C](that: Schedule[B, C]): Schedule[A, C] =
    new Schedule[A, C] {
      type State = (self.State, that.State)
      val initial = self.initial.seq(that.initial)
      val update = (a: A, s: State) =>
        self.update(a, s._1).flatMap { step1 =>
          that.update(step1.finish(), s._2).map { step2 =>
            step1.combineWith(step2)(_ && _, _ + _).rightMap(_._2)
          }
      }
    }

  /**
   * A backwards version of `>>>`.
   */
  final def <<<[C](that: Schedule[C, A]): Schedule[C, B] = that >>> self

  /**
   * An alias for `<<<`
   */
  final def compose[C](that: Schedule[C, A]): Schedule[C, B] = self <<< that
}

object Schedule {
  sealed case class Decision[+A, +B](cont: Boolean, delay: Duration, state: A, finish: () => B) { self =>
    final def bimap[C, D](f: A => C, g: B => D): Decision[C, D] = copy(state = f(state), finish = () => g(finish()))
    final def leftMap[C](f: A => C): Decision[C, B]             = copy(state = f(state))
    final def rightMap[C](f: B => C): Decision[A, C]            = copy(finish = () => f(finish()))

    final def unary_! = copy(cont = !cont)

    final def delayed(f: Duration => Duration): Decision[A, B] = copy(delay = f(delay))

    final def combineWith[C, D](that: Decision[C, D])(g: (Boolean, Boolean) => Boolean,
                                                      f: (Duration, Duration) => Duration): Decision[(A, C), (B, D)] =
      Decision(g(self.cont, that.cont),
               f(self.delay, that.delay),
               (self.state, that.state),
               () => (self.finish(), that.finish()))
  }
  object Decision {
    def cont[A, B](d: Duration, a: A, b: => B): Decision[A, B] = Decision(true, d, a, () => b)
    def done[A, B](d: Duration, a: A, b: => B): Decision[A, B] = Decision(false, d, a, () => b)
  }

  final def apply[S, A, B](initial0: IO[Nothing, S],
                           update0: (A, S) => IO[Nothing, Schedule.Decision[S, B]]): Schedule[A, B] =
    new Schedule[A, B] {
      type State = S
      val initial = initial0
      val update  = update0
    }

  /**
   * A schedule that recurs forever, returning each input as the output.
   */
  final def identity[A]: Schedule[A, A] =
    Schedule[Unit, A, A](IO.unit, (a, s) => IO.now(Decision.cont(Duration.Zero, s, a)))

  /**
   * A schedule that recurs forever, returning the constant for every output.
   */
  final def point[A](a: => A): Schedule[Any, A] = forever.const(a)

  /**
   * A schedule that recurs forever, mapping input values through the
   * specified function.
   */
  final def lift[A, B](f: A => B): Schedule[A, B] = identity[A].map(f)

  /**
   * A schedule that never executes. Note that negating this schedule does not
   * produce a schedule that executes.
   */
  final val never: Schedule[Any, Nothing] =
    Schedule[Nothing, Any, Nothing](IO.never, (_, _) => IO.never)

  /**
   * A schedule that executes once.
   */
  final def once: Schedule[Any, Unit] =
    Schedule[Unit, Any, Unit](IO.unit, (_, s) => IO.now(Decision.done(Duration.Zero, s, s)))

  /**
   * A schedule that recurs forever, producing a count of inputs.
   */
  final val forever: Schedule[Any, Int] =
    Schedule[Int, Any, Int](IO.now(0), (_, i) => IO.now(Decision.cont(Duration.Zero, i + 1, i + 1)))

  /**
   * A new schedule derived from the specified schedule which adds the delay
   * specified as output to the existing duration.
   */
  final def delayed[A](s: Schedule[A, Duration]): Schedule[A, Duration] =
    s.modifyDelay((b, d) => IO.now(b + d)).reconsider((_, step) => step.copy(finish = () => step.delay))

  /**
   * A schedule that recurs forever, collecting all inputs into a list.
   */
  final def collect[A]: Schedule[A, List[A]] = identity[A].collect

  /**
   * A schedule that recurs for as long as the predicate evaluates to true.
   */
  final def doWhile[A](f: A => Boolean): Schedule[A, A] =
    identity[A].whileInput(f)

  /**
   * A schedule that recurs for until the predicate evaluates to true.
   */
  final def doUntil[A](f: A => Boolean): Schedule[A, A] =
    identity[A].untilInput(f)

  /**
   * A schedule that recurs forever, dumping input values to the specified
   * sink, and returning those same values unmodified.
   */
  final def logInput[A](f: A => IO[Nothing, Unit]): Schedule[A, A] =
    identity[A].logInput(f)

  /**
   * A schedule that recurs the specified number of times, producing a count
   * of inputs.
   */
  final def recurs(n: Int): Schedule[Any, Int] = forever.whileValue(_ < n)

  /**
   * A schedule that recurs forever, and computes the time since the beginning.
   */
  final val elapsed: Schedule[Any, Duration] = {
    val nanoTime = IO.sync(System.nanoTime())

    Schedule[Long, Any, Duration](nanoTime,
                                  (_, start) =>
                                    for {
                                      duration <- nanoTime.map(_ - start).map(Duration.fromNanos)
                                    } yield Decision.cont(Duration.Zero, start, duration))
  }

  /**
   * A schedule that will recur until the specified duration elapses.
   */
  final def duration(duration: Duration): Schedule[Any, Duration] =
    elapsed.untilValue(_.toNanos >= duration.toNanos)

  /**
   * A schedule that always recurs, and computes the output through
   * recured application of a function to a base value.
   */
  final def unfold[A](a: => A)(f: A => A): Schedule[Any, A] =
    Schedule[A, Any, A](IO.point(a),
                        (_, a) =>
                          IO.now {
                            val a2 = f(a)
                            Decision.cont(Duration.Zero, a2, a2)
                        })

  /**
   * A schedule that waits for the specified amount of time between each
   * input. Returns the number of inputs so far.
   *
   * <pre>
   * |action|-----interval-----|action|-----interval-----|action|
   * </pre>
   */
  final def spaced(interval: Duration): Schedule[Any, Int] =
    forever.delayed(_ + interval)

  /**
   * A schedule that recurs on a fixed interval. Returns the amount of time
   * since the schedule began.
   *
   * If the action takes run between updates longer than the interval, then the
   * action will be run immediately, but re-runs will not "pile up".
   *
   * <pre>
   * |---------interval---------|---------interval---------|
   * |action|                   |action|
   * </pre>
   */
  final def fixed(interval: Duration): Schedule[Any, Int] =
    if (interval == Duration.Zero) forever
    else
      Schedule[(Long, Int, Int), Any, Int](
        IO.sync((System.nanoTime(), 0, 0)),
        (_, t) =>
          t match {
            case (start, n0, i) =>
              IO.sync(System.nanoTime()).map { now =>
                val await = ((start + n0 * interval.toNanos) - now)
                val n = 1 +
                  (if (await < 0) ((now - start) / interval.toNanos).toInt else n0)

                Decision.cont(Duration.fromNanos(await.max(0L)), (start, n, i + 1), i + 1)
              }
        }
      )

  /**
   * A schedule that always recurs, increasing delays by summing the
   * preceeding two delays (similar to the fibonacci sequence).
   */
  final def fibonacci(one: Duration): Schedule[Any, Duration] =
    delayed(unfold[(Duration, Duration)]((Duration.Zero, one)) {
      case (a1, a2) => (a2, a1 + a2)
    }.map(_._1))

  /**
   * A schedule that always recurs, but will wait a certain amount between
   * repetitions, given by `base * factor.pow(n)`, where `n` is the number of
   * repetitions so far.
   */
  final def exponential(base: Duration, factor: Double = 2.0): Schedule[Any, Duration] =
    delayed(forever.map(i => base * math.pow(factor, i.doubleValue)))
}
