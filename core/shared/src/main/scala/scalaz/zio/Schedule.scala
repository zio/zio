/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Copyright (C) 2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration
import scalaz.zio.random.{ nextDouble, Random }

/**
 * Defines a stateful, possibly effectful, recurring schedule of actions.
 *
 * A `Schedule[R, A, B]` consumes `A` values, and based on the inputs and the
 * internal state, decides whether to continue or halt. Every decision is
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
 * `Schedule[R, A, B]` forms a profunctor on `[A, B]`, an applicative functor on
 * `B`, and a monoid, allowing rich composition of different schedules.
 */
trait Schedule[-R, -A, +B] extends Serializable { self =>

  /**
   * The internal state type of the schedule.
   */
  type State

  /**
   * The initial state of the schedule.
   */
  val initial: ZIO[R, Nothing, State]

  /**
   * Updates the schedule based on a new input and the current state.
   */
  val update: (A, State) => ZIO[R, Nothing, Schedule.Decision[State, B]]

  /**
   * Runs the schedule on the provided list of inputs, returning a list of
   * durations and outputs. This method is useful for testing complicated
   * schedules. Only as many inputs will be used as necessary to run the
   * schedule to completion, and additional inputs will be discarded.
   */
  final def run(as: Iterable[A]): ZIO[R, Nothing, List[(Duration, B)]] = {
    def run0(as: List[A], s: State, acc: List[(Duration, B)]): ZIO[R, Nothing, List[(Duration, B)]] =
      as match {
        case Nil => IO.succeed(acc)
        case a :: as =>
          self.update(a, s).flatMap {
            case Schedule.Decision(cont, delay, s, finish) =>
              val acc2 = (delay -> finish()) :: acc

              if (cont) run0(as, s, acc2)
              else IO.succeed(acc2)
          }
      }

    self.initial.flatMap(s => run0(as.toList, s, Nil)).map(_.reverse)
  }

  /**
   * Returns a new schedule that inverts the decision to continue.
   */
  final def unary_! : Schedule[R, A, B] =
    updated(update => (a, s) => update(a, s).map(!_))

  /**
   * Returns a new schedule that maps over the output of this one.
   */
  final def map[A1 <: A, C](f: B => C): Schedule[R, A1, C] =
    new Schedule[R, A1, C] {
      type State = self.State
      val initial = self.initial
      val update  = (a: A1, s: State) => self.update(a, s).map(_.rightMap(f))
    }

  /**
   * Returns a new schedule that deals with a narrower class of inputs than
   * this schedule.
   */
  final def contramap[A1](f: A1 => A): Schedule[R, A1, B] =
    new Schedule[R, A1, B] {
      type State = self.State
      val initial = self.initial
      val update  = (a: A1, s: State) => self.update(f(a), s)
    }

  /**
   * Returns a new schedule that contramaps the input and maps the output.
   */
  final def dimap[A1, C](f: A1 => A, g: B => C): Schedule[R, A1, C] =
    contramap(f).map(g)

  /**
   * Returns a new schedule that loops this one forever, resetting the state
   * when this schedule is done.
   */
  final def forever: Schedule[R, A, B] =
    updated(
      update =>
        (a, s) =>
          update(a, s).flatMap { decision =>
            if (decision.cont) IO.succeed(decision)
            else self.initial.map(state => decision.copy(cont = true, state = state))
          }
    )

  /**
   * Peeks at the state produced by this schedule, executes some action, and
   * then continues the schedule or not based on the specified state predicate.
   */
  final def check[A1 <: A](test: (A1, B) => UIO[Boolean]): Schedule[R, A1, B] =
    updated(
      update =>
        (a, s) =>
          update(a, s).flatMap { d =>
            if (d.cont) test(a, d.finish()).map(b => d.copy(cont = b))
            else IO.succeed(d)
          }
    )

  /**
   * Returns a new schedule that continues this schedule so long as the predicate
   * is satisfied on the output value of the schedule.
   */
  final def whileOutput(f: B => Boolean): Schedule[R, A, B] =
    check((_, b) => IO.succeed(f(b)))

  /**
   * Returns a new schedule that continues this schedule so long as the
   * predicate is satisfied on the input of the schedule.
   */
  final def whileInput[A1 <: A](f: A1 => Boolean): Schedule[R, A1, B] =
    check((a, _) => IO.succeed(f(a)))

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the output value of the schedule.
   */
  final def untilOutput(f: B => Boolean): Schedule[R, A, B] = !whileOutput(f)

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the input of the schedule.
   */
  final def untilInput[A1 <: A](f: A1 => Boolean): Schedule[R, A1, B] = !whileInput(f)

  final def combineWith[R1 <: R, A1 <: A, C](
    that: Schedule[R1, A1, C]
  )(g: (Boolean, Boolean) => Boolean, f: (Duration, Duration) => Duration): Schedule[R1, A1, (B, C)] =
    new Schedule[R1, A1, (B, C)] {
      type State = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val update  = (a: A1, s: State) => self.update(a, s._1).zipWith(that.update(a, s._2))(_.combineWith(_)(g, f))
    }

  /**
   * Returns a new schedule that continues only as long as both schedules
   * continue, using the maximum of the delays of the two schedules.
   */
  final def &&[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, (B, C)] =
    combineWith(that)(_ && _, _ max _)

  /**
   * A named alias for `&&`.
   */
  final def both[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, (B, C)] = self && that

  /**
   * The same as `both` followed by `map`.
   */
  final def bothWith[R1 <: R, A1 <: A, C, D](that: Schedule[R1, A1, C])(f: (B, C) => D): Schedule[R1, A1, D] =
    (self && that).map(f.tupled)

  /**
   * The same as `&&`, but ignores the left output.
   */
  final def *>[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, C] =
    (self && that).map(_._2)

  /**
   * The same as `&&`, but ignores the right output.
   */
  final def <*[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, B] =
    (self && that).map(_._1)

  /**
   * Returns a new schedule that continues as long as either schedule continues,
   * using the minimum of the delays of the two schedules.
   */
  final def ||[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, (B, C)] =
    combineWith(that)(_ || _, _ min _)

  /**
   * A named alias for `||`.
   */
  final def either[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, (B, C)] = self || that

  /**
   * The same as `either` followed by `map`.
   */
  final def eitherWith[R1 <: R, A1 <: A, C, D](that: Schedule[R1, A1, C])(f: (B, C) => D): Schedule[R1, A1, D] =
    (self || that).map(f.tupled)

  /**
   * Returns a new schedule that first executes this schedule to completion,
   * and then executes the specified schedule to completion.
   */
  final def andThenEither[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, Either[B, C]] =
    new Schedule[R1, A1, Either[B, C]] {
      type State = Either[self.State, that.State]

      val initial = self.initial.map(Left(_))

      val update = (a: A1, state: State) =>
        state match {
          case Left(v) =>
            self.update(a, v).flatMap { step =>
              if (step.cont) IO.succeed(step.bimap(Left(_), Left(_)))
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
   * The same as `andThenEither`, but merges the output.
   */
  final def andThen[R1 <: R, A1 <: A, B1 >: B](that: Schedule[R1, A1, B1]): Schedule[R1, A1, B1] =
    andThenEither(that).map(_.merge)

  /**
   * Returns a new schedule that maps this schedule to a constant output.
   */
  final def const[C](c: => C): Schedule[R, A, C] = map(_ => c)

  /**
   * Returns a new schedule that maps this schedule to a Unit output.
   */
  final def void: Schedule[R, A, Unit] = const(())

  /**
   * Returns a new schedule that effectfully reconsiders the decision made by
   * this schedule.
   */
  final def reconsiderM[A1 <: A, C](
    f: (A1, Schedule.Decision[State, B]) => UIO[Schedule.Decision[State, C]]
  ): Schedule[R, A1, C] =
    updated(
      update =>
        (a: A1, s: State) =>
          for {
            step  <- update(a, s)
            step2 <- f(a, step)
          } yield step2
    )

  /**
   * Returns a new schedule that reconsiders the decision made by this schedule.
   */
  final def reconsider[A1 <: A, C](
    f: (A1, Schedule.Decision[State, B]) => Schedule.Decision[State, C]
  ): Schedule[R, A1, C] =
    reconsiderM((a, s) => IO.succeed(f(a, s)))

  /**
   * A new schedule that applies the current one but runs the specified effect
   * for every decision of this schedule. This can be used to create schedules
   * that log failures, decisions, or computed values.
   */
  final def onDecision[A1 <: A](f: (A1, Schedule.Decision[State, B]) => UIO[Unit]): Schedule[R, A1, B] =
    updated(update => (a, s) => update(a, s).peek(step => f(a, step)))

  /**
   * Returns a new schedule with the specified effectful modification
   * applied to each delay produced by this schedule.
   */
  final def modifyDelay[R1 <: R](f: (B, Duration) => ZIO[R1, Nothing, Duration]): Schedule[R1, A, B] =
    updated(
      update =>
        (a, s) =>
          update(a, s).flatMap { step =>
            f(step.finish(), step.delay).map(d => step.delayed(_ => d))
          }
    )

  /**
   * Returns a new schedule with the update function transformed by the
   * specified update transformer.
   */
  final def updated[R1 <: R, A1 <: A, B1](
    f: (
      (A, State) => ZIO[R, Nothing, Schedule.Decision[State, B]]
    ) => (A1, State) => ZIO[R1, Nothing, Schedule.Decision[State, B1]]
  ): Schedule[R1, A1, B1] =
    new Schedule[R1, A1, B1] {
      type State = self.State
      val initial = self.initial
      val update  = f(self.update)
    }

  /**
   * Returns a new schedule with the specified initial state transformed
   * by the specified initial transformer.
   */
  final def initialized[R1 <: R, A1 <: A](f: ZIO[R1, Nothing, State] => ZIO[R1, Nothing, State]): Schedule[R1, A1, B] =
    new Schedule[R1, A1, B] {
      type State = self.State
      val initial = f(self.initial)
      val update  = self.update
    }

  /**
   * Returns a new schedule with the specified pure modification
   * applied to each delay produced by this schedule.
   */
  final def delayed(f: Duration => Duration): Schedule[R, A, B] =
    modifyDelay((_, d) => IO.succeed(f(d)))

  /**
   * Applies random jitter to the schedule bounded by the factors 0.0 and 1.0.
   */
  final def jittered: Schedule[R with Random, A, B] = jittered(0.0, 1.0)

  /**
   * Applies random jitter to the schedule bounded by the specified factors, with a given random generator.
   */
  final def jittered(min: Double, max: Double): Schedule[R with Random, A, B] =
    modifyDelay((_, d) => nextDouble.map(random => d * min * (1 - random) + d * max * random))

  /**
   * Sends every input value to the specified sink.
   */
  final def logInput[R1 <: R, A1 <: A](f: A1 => ZIO[R1, Nothing, Unit]): Schedule[R1, A1, B] =
    updated[R1, A1, B](update => (a, s) => f(a) *> update(a, s))

  /**
   * Sends every output value to the specified sink.
   */
  final def logOutput[R1 <: R](f: B => UIO[Unit]): Schedule[R1, A, B] =
    updated[R1, A, B](update => (a, s) => update(a, s).flatMap(step => f(step.finish()) *> IO.succeed(step)))

  /**
   * Returns a new schedule that collects the outputs of this one into a list.
   */
  final def collect: Schedule[R, A, List[B]] =
    fold(List.empty[B])((xs, x) => x :: xs).map(_.reverse)

  /**
   * Returns a new schedule that folds over the outputs of this one.
   */
  final def fold[Z](z: Z)(f: (Z, B) => Z): Schedule[R, A, Z] =
    foldM[Z](IO.succeed(z))((z, b) => IO.succeed(f(z, b)))

  /**
   * Returns a new schedule that effectfully folds over the outputs of this one.
   */
  final def foldM[Z](z: UIO[Z])(f: (Z, B) => UIO[Z]): Schedule[R, A, Z] =
    new Schedule[R, A, Z] {
      type State = (self.State, Z)

      val initial = self.initial.zip(z)

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
  final def >>>[R1 <: R, C](that: Schedule[R1, B, C]): Schedule[R1, A, C] =
    new Schedule[R1, A, C] {
      type State = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
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
  final def <<<[R1 <: R, C](that: Schedule[R1, C, A]): Schedule[R1, C, B] = that >>> self

  /**
   * An alias for `<<<`
   */
  final def compose[R1 <: R, C](that: Schedule[R1, C, A]): Schedule[R1, C, B] = self <<< that

  /**
   * Puts this schedule into the first element of a tuple, and passes along
   * another value unchanged as the second element of the tuple.
   */
  final def first[R1 <: R, C]: Schedule[R1, (A, C), (B, C)] = self *** Schedule.identity[C]

  /**
   * Puts this schedule into the second element of a tuple, and passes along
   * another value unchanged as the first element of the tuple.
   */
  final def second[C]: Schedule[R, (C, A), (C, B)] = Schedule.identity[C] *** self

  /**
   * Puts this schedule into the first element of a either, and passes along
   * another value unchanged as the second element of the either.
   */
  final def left[C]: Schedule[R, Either[A, C], Either[B, C]] = self +++ Schedule.identity[C]

  /**
   * Puts this schedule into the second element of a either, and passes along
   * another value unchanged as the first element of the either.
   */
  final def right[C]: Schedule[R, Either[C, A], Either[C, B]] = Schedule.identity[C] +++ self

  /**
   * Split the input
   */
  final def ***[R1 <: R, C, D](that: Schedule[R1, C, D]): Schedule[R1, (A, C), (B, D)] =
    new Schedule[R1, (A, C), (B, D)] {
      type State = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val update = (a: (A, C), s: State) =>
        self.update(a._1, s._1).zipWith(that.update(a._2, s._2))(_.combineWith(_)(_ && _, _ max _))
    }

  /**
   * Chooses between two schedules with a common output.
   */
  final def |||[R1 <: R, B1 >: B, C](that: Schedule[R1, C, B1]): Schedule[R1, Either[A, C], B1] =
    (self +++ that).map(_.merge)

  /**
   * Chooses between two schedules with different outputs.
   */
  final def +++[R1 <: R, C, D](that: Schedule[R1, C, D]): Schedule[R1, Either[A, C], Either[B, D]] =
    new Schedule[R1, Either[A, C], Either[B, D]] {
      type State = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val update = (a: Either[A, C], s: State) =>
        a match {
          case Left(a)  => self.update(a, s._1).map(_.leftMap((_, s._2)).rightMap(Left(_)))
          case Right(c) => that.update(c, s._2).map(_.leftMap((s._1, _)).rightMap(Right(_)))
        }
    }
}

object Schedule extends Serializable {
  sealed case class Decision[+A, +B](cont: Boolean, delay: Duration, state: A, finish: () => B) { self =>
    final def bimap[C, D](f: A => C, g: B => D): Decision[C, D] = copy(state = f(state), finish = () => g(finish()))
    final def leftMap[C](f: A => C): Decision[C, B]             = copy(state = f(state))
    final def rightMap[C](f: B => C): Decision[A, C]            = copy(finish = () => f(finish()))

    final def unary_! = copy(cont = !cont)

    final def delayed(f: Duration => Duration): Decision[A, B] = copy(delay = f(delay))

    final def combineWith[C, D](
      that: Decision[C, D]
    )(g: (Boolean, Boolean) => Boolean, f: (Duration, Duration) => Duration): Decision[(A, C), (B, D)] =
      Decision(
        g(self.cont, that.cont),
        f(self.delay, that.delay),
        (self.state, that.state),
        () => (self.finish(), that.finish())
      )
  }
  object Decision {
    final def cont[A, B](d: Duration, a: A, b: => B): Decision[A, B] = Decision(true, d, a, () => b)
    final def done[A, B](d: Duration, a: A, b: => B): Decision[A, B] = Decision(false, d, a, () => b)
  }

  final def apply[R, S, A, B](
    initial0: ZIO[R, Nothing, S],
    update0: (A, S) => ZIO[R, Nothing, Schedule.Decision[S, B]]
  ): Schedule[R, A, B] =
    new Schedule[R, A, B] {
      type State = S
      val initial = initial0
      val update  = update0
    }

  /**
   * A schedule that recurs forever, returning each input as the output.
   */
  final def identity[A]: Schedule[Any, A, A] =
    Schedule[Any, Unit, A, A](ZIO.unit, (a, s) => IO.succeed(Decision.cont(Duration.Zero, s, a)))

  /**
   * A schedule that recurs forever, returning the constant for every output.
   */
  final def succeed[A](a: A): Schedule[Any, Any, A] = forever.const(a)

  /**
   * A schedule that recurs forever, returning the constant for every output (by-name version).
   */
  final def succeedLazy[A](a: => A): Schedule[Any, Any, A] = forever.const(a)

  /**
   * A schedule that recurs forever, mapping input values through the
   * specified function.
   */
  final def lift[A, B](f: A => B): Schedule[Any, A, B] = identity[A].map(f)

  /**
   * A schedule that never executes. Note that negating this schedule does not
   * produce a schedule that executes.
   */
  final val never: Schedule[Any, Any, Nothing] =
    Schedule[Any, Nothing, Any, Nothing](IO.never, (_, _) => IO.never)

  /**
   * A schedule that recurs forever, producing a count of inputs.
   */
  final val forever: Schedule[Any, Any, Int] = Schedule.unfold(0)(_ + 1)

  /**
   * A schedule that executes once.
   */
  final val once: Schedule[Any, Any, Unit] = recurs(1).void

  /**
   * A new schedule derived from the specified schedule which adds the delay
   * specified as output to the existing duration.
   */
  final def delayed[R, A](s: Schedule[R, A, Duration]): Schedule[R, A, Duration] =
    s.modifyDelay((b, d) => IO.succeed(b + d)).reconsider((_, step) => step.copy(finish = () => step.delay))

  /**
   * A schedule that recurs forever, collecting all inputs into a list.
   */
  final def collect[A]: Schedule[Any, A, List[A]] = identity[A].collect

  /**
   * A schedule that recurs for as long as the predicate evaluates to true.
   */
  final def doWhile[A](f: A => Boolean): Schedule[Any, A, A] =
    identity[A].whileInput(f)

  /**
   * A schedule that recurs for until the predicate evaluates to true.
   */
  final def doUntil[A](f: A => Boolean): Schedule[Any, A, A] =
    identity[A].untilInput(f)

  /**
   * A schedule that recurs forever, dumping input values to the specified
   * sink, and returning those same values unmodified.
   */
  final def logInput[R, A](f: A => ZIO[R, Nothing, Unit]): Schedule[R, A, A] =
    identity[A].logInput(f)

  /**
   * A schedule that recurs the specified number of times. Returns the number
   * of repetitions so far.
   *
   * If 0 or negative numbers are given, the operation is not done at all so
   * that in `(op: IO[E, A]).repeat(Schedule.recurs(0)) `, op is not done at all.
   */
  final def recurs(n: Int): Schedule[Any, Any, Int] = forever.whileOutput(_ <= n)

  /**
   * A schedule that recurs forever without delay. Returns the elapsed time
   * since the schedule began.
   */
  final val elapsed: Schedule[Clock, Any, Duration] = {
    Schedule[Clock, Long, Any, Duration](
      clock.nanoTime,
      (_, start) =>
        for {
          duration <- clock.nanoTime.map(_ - start).map(Duration.fromNanos)
        } yield Decision.cont(Duration.Zero, start, duration)
    )
  }

  /**
   * A schedule that will recur forever with no delay, returning the duration
   * between steps. You can chain this onto the end of schedules to find out
   * what their delay is, e.g. `Schedule.spaced(1.second) >>> Schedule.delay`.
   */
  final val delay: Schedule[Any, Any, Duration] =
    forever.reconsider[Any, Duration]((_, d) => d.copy(finish = () => d.delay))

  /**
   * A schedule that will recur forever with no delay, returning the decision
   * from the steps. You can chain this onto the end of schedules to find out
   * what their decision is, e.g. `Schedule.recurs(5) >>> Schedule.decision`.
   */
  final val decision: Schedule[Any, Any, Boolean] =
    forever.reconsider[Any, Boolean]((_, d) => d.copy(finish = () => d.cont))

  /**
   * A schedule that will recur until the specified duration elapses. Returns
   * the total elapsed time.
   */
  final def duration(duration: Duration): Schedule[Clock, Any, Duration] =
    elapsed.untilOutput(_ >= duration)

  /**
   * A schedule that always recurs without delay, and computes the output
   * through recured application of a function to a base value.
   */
  final def unfold[A](a: => A)(f: A => A): Schedule[Any, Any, A] =
    unfoldM(IO.succeedLazy(a))(f.andThen(IO.succeedLazy[A](_)))

  /**
   * A schedule that always recurs without delay, and computes the output
   * through recured application of a function to a base value.
   */
  final def unfoldM[R, A](a: ZIO[R, Nothing, A])(f: A => ZIO[R, Nothing, A]): Schedule[R, Any, A] =
    Schedule[R, A, Any, A](a, (_, a) => f(a).map(a => Decision.cont(Duration.Zero, a, a)))

  /**
   * A schedule that waits for the specified amount of time between each
   * input. Returns the number of inputs so far.
   *
   * <pre>
   * |action|-----interval-----|action|-----interval-----|action|
   * </pre>
   */
  final def spaced(interval: Duration): Schedule[Any, Any, Int] =
    forever.delayed(_ + interval)

  /**
   * A schedule that recurs on a fixed interval. Returns the number of
   * repetitions of the schedule so far.
   *
   * If the action run between updates takes longer than the interval, then the
   * action will be run immediately, but re-runs will not "pile up".
   *
   * <pre>
   * |---------interval---------|---------interval---------|
   * |action|                   |action|
   * </pre>
   */
  final def fixed(interval: Duration): Schedule[Clock, Any, Int] = interval match {
    case Duration.Infinity                    => once >>> never
    case Duration.Finite(nanos) if nanos == 0 => forever
    case Duration.Finite(nanos) =>
      Schedule[Clock, (Long, Int, Int), Any, Int](
        clock.nanoTime.map(nt => (nt, 1, 0)),
        (_, t) =>
          t match {
            case (start, n0, i) =>
              clock.nanoTime.map { now =>
                val await = (start + n0 * nanos) - now
                val n = 1 +
                  (if (await < 0) ((now - start) / nanos).toInt else n0)

                Decision.cont(Duration.fromNanos(await.max(0L)), (start, n, i + 1), i + 1)
              }
          }
      )
  }

  /**
   * A schedule that always recurs, increasing delays by summing the
   * preceding two delays (similar to the fibonacci sequence). Returns the
   * current duration between recurrences.
   */
  final def fibonacci(one: Duration): Schedule[Any, Any, Duration] =
    delayed(unfold[(Duration, Duration)]((Duration.Zero, one)) {
      case (a1, a2) => (a2, a1 + a2)
    }.map(_._1))

  /**
   * A schedule that always recurs, but will repeat on a linear time
   * interval, given by `base * n` where `n` is the number of
   * repetitions so far. Returns the current duration between recurrences.
   */
  final def linear(base: Duration): Schedule[Any, Any, Duration] =
    delayed(forever.map(i => base * i.doubleValue()))

  /**
   * A schedule that always recurs, but will wait a certain amount between
   * repetitions, given by `base * factor.pow(n)`, where `n` is the number of
   * repetitions so far. Returns the current duration between recurrences.
   */
  final def exponential(base: Duration, factor: Double = 2.0): Schedule[Any, Any, Duration] =
    delayed(forever.map(i => base * math.pow(factor, i.doubleValue)))
}
