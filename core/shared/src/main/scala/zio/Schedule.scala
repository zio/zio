/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio

import java.util.concurrent.TimeUnit

import zio.clock.Clock
import zio.duration.Duration
import zio.random.Random

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
  val initial: URIO[R, State]

  /**
   * Extract the B from the schedule
   */
  val extract: (A, State) => B

  /**
   * Updates the schedule based on a new input and the current state.
   */
  val update: (A, State) => ZIO[R, Unit, State]

  /**
   * Returns a new schedule that continues only as long as both schedules
   * continue, using the maximum of the delays of the two schedules.
   */
  final def &&[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, (B, C)] =
    new Schedule[R1, A1, (B, C)] {
      type State = (self.State, that.State)
      val initial = self.initial.zipPar(that.initial)
      val extract = (a: A1, s: (self.State, that.State)) => (self.extract(a, s._1), that.extract(a, s._2))
      val update  = (a: A1, s: (self.State, that.State)) => self.update(a, s._1).zipPar(that.update(a, s._2))
    }

  /**
   * Split the input
   */
  final def ***[R1 <: R, C, D](that: Schedule[R1, C, D]): Schedule[R1, (A, C), (B, D)] =
    new Schedule[R1, (A, C), (B, D)] {
      type State = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val extract = (a: (A, C), s: (self.State, that.State)) => (self.extract(a._1, s._1), that.extract(a._2, s._2))
      val update  = (a: (A, C), s: (self.State, that.State)) => self.update(a._1, s._1).zipPar(that.update(a._2, s._2))
    }

  /**
   * The same as `&&`, but ignores the left output.
   */
  final def *>[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, C] =
    (self && that).map(_._2)

  /**
   * A symbolic alias for `andThen`.
   */
  final def ++[R1 <: R, A1 <: A, B1 >: B](that: Schedule[R1, A1, B1]): Schedule[R1, A1, B1] =
    andThen(that)

  /**
   * Chooses between two schedules with different outputs.
   */
  final def +++[R1 <: R, C, D](that: Schedule[R1, C, D]): Schedule[R1, Either[A, C], Either[B, D]] =
    new Schedule[R1, Either[A, C], Either[B, D]] {
      type State = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val extract = (a: Either[A, C], s: (self.State, that.State)) =>
        a.fold(a => Left(self.extract(a, s._1)), c => Right(that.extract(c, s._2)))

      val update = (a: Either[A, C], s: State) =>
        a match {
          case Left(a)  => self.update(a, s._1).map((_, s._2))
          case Right(c) => that.update(c, s._2).map((s._1, _))
        }
    }

  /**
   * Operator alias for `andThenEither`.
   */
  final def <||>[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, Either[B, C]] =
    andThenEither(that)

  /**
   * The same as `&&`, but ignores the right output.
   */
  final def <*[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, B] =
    (self && that).map(_._1)

  /**
   * Returns a new schedule that continues only as long as both schedules
   * continue, using the maximum of the delays of the two schedules.
   */
  final def <*>[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, (B, C)] = self zip that

  /**
   * A backwards version of `>>>`.
   */
  final def <<<[R1 <: R, C](that: Schedule[R1, C, A]): Schedule[R1, C, B] = that >>> self

  /**
   * Returns the composition of this schedule and the specified schedule,
   * by piping the output of this one into the input of the other.
   * Effects described by this schedule will always be executed before the effects described by the second schedule.
   */
  final def >>>[R1 <: R, C](that: Schedule[R1, B, C]): Schedule[R1, A, C] =
    new Schedule[R1, A, C] {
      type State = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val extract = (a: A, s: (self.State, that.State)) => that.extract(self.extract(a, s._1), s._2)

      val update = (a: A, s: (self.State, that.State)) =>
        for {
          s1 <- self.update(a, s._1)
          s2 <- that.update(self.extract(a, s._1), s._2)
        } yield (s1, s2)
    }

  /**
   * Returns a new schedule that continues as long as either schedule continues,
   * using the minimum of the delays of the two schedules.
   */
  final def ||[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, (B, C)] =
    new Schedule[R1, A1, (B, C)] {
      type State = (self.State, that.State)
      val initial = self.initial zip that.initial
      val extract = (a: A1, s: (self.State, that.State)) => (self.extract(a, s._1), that.extract(a, s._2))
      val update = (a: A1, s: (self.State, that.State)) =>
        self.update(a, s._1).raceEither(that.update(a, s._2)).map {
          case Left(s1)  => (s1, s._2)
          case Right(s2) => (s._1, s2)
        }
    }

  /**
   * Chooses between two schedules with a common output.
   */
  final def |||[R1 <: R, B1 >: B, C](that: Schedule[R1, C, B1]): Schedule[R1, Either[A, C], B1] =
    (self +++ that).map(_.merge)

  /**
   * Returns a new schedule with the given delay added to every update.
   */
  final def addDelay(f: B => Duration): Schedule[R with Clock, A, B] =
    addDelayM(b => ZIO.succeedNow(f(b)))

  /**
   * Returns a new schedule with the effectfully calculated delay added to every update.
   */
  final def addDelayM[R1 <: R](f: B => ZIO[R1, Nothing, Duration]): Schedule[R1 with Clock, A, B] =
    updated(update =>
      (a, s) =>
        for {
          delay <- f(extract(a, s))
          s1    <- update(a, s)
          _     <- ZIO.sleep(delay)
        } yield s1
    )

  /**
   * The same as `andThenEither`, but merges the output.
   */
  final def andThen[R1 <: R, A1 <: A, B1 >: B](that: Schedule[R1, A1, B1]): Schedule[R1, A1, B1] =
    andThenEither(that).map(_.merge)

  /**
   * Returns a new schedule that first executes this schedule to completion,
   * and then executes the specified schedule to completion.
   */
  final def andThenEither[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, Either[B, C]] =
    new Schedule[R1, A1, Either[B, C]] {
      type State = Either[self.State, that.State]
      val initial = self.initial.map(Left(_))
      val extract = (a: A1, s: Either[self.State, that.State]) =>
        s.fold(b => Left(self.extract(a, b)), c => Right(that.extract(a, c)))

      val update = (a: A1, state: State) =>
        state match {
          case Left(v) =>
            self.update(a, v).map(Left(_)) orElse that.initial.flatMap(that.update(a, _)).map(Right(_))
          case Right(v) =>
            that.update(a, v).map(Right(_))
        }
    }

  /**
   * Returns a new schedule that maps this schedule to a constant output.
   */
  final def as[C](c: => C): Schedule[R, A, C] = map(_ => c)

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
   * Peeks at the output produced by this schedule, executes some action, and
   * then continues the schedule or not based on the specified state predicate.
   */
  final def check[A1 <: A](test: (A1, B) => UIO[Boolean]): Schedule[R, A1, B] =
    updated(update =>
      (a, s) =>
        test(a, self.extract(a, s)).flatMap {
          case false => ZIO.fail(())
          case true  => update(a, s)
        }
    )

  /**
   * Returns a new schedule that collects the outputs of this one into a list.
   */
  final def collectAll: Schedule[R, A, List[B]] =
    fold(List.empty[B])((xs, x) => x :: xs).map(_.reverse)

  /**
   * An alias for `<<<`
   */
  final def compose[R1 <: R, C](that: Schedule[R1, C, A]): Schedule[R1, C, B] = self <<< that

  /**
   * Returns a new schedule that deals with a narrower class of inputs than
   * this schedule.
   */
  final def contramap[A1](f: A1 => A): Schedule[R, A1, B] =
    new Schedule[R, A1, B] {
      type State = self.State
      val initial = self.initial
      val extract = (a: A1, s: self.State) => self.extract(f(a), s)
      val update  = (a: A1, s: self.State) => self.update(f(a), s)
    }

  /**
   * Returns a new schedule with the specified pure modification
   * applied to each delay produced by this schedule.
   */
  final def delayed[R1 <: R](
    f: Duration => Duration
  )(implicit ev1: Has.IsHas[R1], ev2: R1 <:< Clock): Schedule[R1, A, B] = delayedM[R1](d => ZIO.succeedNow(f(d)))

  /**
   * Returns a new schedule with the specified effectful modification
   * applied to each delay produced by this schedule.
   */
  final def delayedM[R1 <: R](
    f: Duration => ZIO[R1, Nothing, Duration]
  )(implicit ev1: Has.IsHas[R1], ev2: R1 <:< Clock): Schedule[R1, A, B] = {
    def proxy(clock0: Clock.Service, r1: R1): Clock.Service = new Clock.Service {
      def currentTime(unit: TimeUnit) = clock0.currentTime(unit)
      def currentDateTime             = clock0.currentDateTime
      val nanoTime                    = clock0.nanoTime
      def sleep(duration: Duration)   = f(duration).flatMap(clock0.sleep).provide(r1)
    }
    new Schedule[R1, A, B] {
      type State = (self.State, R)
      val initial =
        for {
          oldEnv <- ZIO.environment[R1]
          env    = ev1.update[R1, Clock.Service](oldEnv, proxy(_, oldEnv))
          init   <- self.initial.provide(env)
        } yield (init, env)
      val extract = (a: A, s: State) => self.extract(a, s._1)
      val update  = (a: A, s: State) => self.update(a, s._1).provide(s._2).map((_, s._2))
    }
  }

  /**
   * Returns a new schedule that contramaps the input and maps the output.
   */
  final def dimap[A1, C](f: A1 => A, g: B => C): Schedule[R, A1, C] =
    contramap(f).map(g)

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
   * Runs the specified finalizer as soon as the schedule is complete. Note
   * that unlike `ZIO#ensuring`, this method does not guarantee the finalizer
   * will be run. The `Schedule` may not initialize or the driver of the
   * schedule may not run to completion. However, if the `Schedule` ever
   * decides not to continue, then the finalizer will be run.
   */
  final def ensuring(finalizer: UIO[_]): Schedule[R, A, B] =
    new Schedule[R, A, B] {
      type State = (self.State, Ref[UIO[Any]])
      val initial = self.initial <*> Ref.make[UIO[Any]](finalizer)
      val extract = (a: A, s: State) => self.extract(a, s._1)
      val update = (a: A, s: State) =>
        self.update(a, s._1).tapError(_ => s._2.modify(fin => (fin, ZIO.unit)).flatten).map((_, s._2))
    }

  /**
   * Puts this schedule into the first element of a tuple, and passes along
   * another value unchanged as the second element of the tuple.
   */
  final def first[R1 <: R, C]: Schedule[R1, (A, C), (B, C)] = self *** Schedule.identity[C]

  /**
   * Returns a new schedule that folds over the outputs of this one.
   */
  final def fold[Z](z: Z)(f: (Z, B) => Z): Schedule[R, A, Z] =
    foldM(z)((z, b) => ZIO.succeedNow(f(z, b)))

  /**
   * Returns a new schedule that effectfully folds over the outputs of this one.
   */
  final def foldM[R1 <: R, Z](z: Z)(f: (Z, B) => ZIO[R1, Nothing, Z]): Schedule[R1, A, Z] =
    new Schedule[R1, A, Z] {
      type State = (self.State, Z)
      val initial = self.initial.map((_, z))
      val extract = (_: A, s: (self.State, Z)) => s._2
      val update = (a: A, s: (self.State, Z)) =>
        for {
          s1 <- self.update(a, s._1)
          z1 <- f(s._2, self.extract(a, s._1))
        } yield (s1, z1)
    }

  /**
   * Returns a new schedule that loops this one forever, resetting the state
   * when this schedule is done.
   */
  final def forever: Schedule[R, A, B] =
    new Schedule[R, A, B] {
      type State = self.State
      val initial = self.initial
      val extract = self.extract
      val update  = (a: A, s: self.State) => self.update(a, s) orElse self.initial.flatMap(self.update(a, _))
    }

  /**
   * Returns a new schedule with the specified initial state transformed
   * by the specified initial transformer.
   */
  final def initialized[R1 <: R, A1 <: A](f: ZIO[R1, Nothing, State] => ZIO[R1, Nothing, State]): Schedule[R1, A1, B] =
    new Schedule[R1, A1, B] {
      type State = self.State
      val initial = f(self.initial)
      val extract = self.extract
      val update  = self.update
    }

  def jittered[R1 <: R](implicit ev1: Has.IsHas[R1], ev2: R1 <:< Clock): Schedule[R1 with Random, A, B] =
    jittered(0.0, 1.0)

  /**
   * Applies random jitter to all sleeps executed by the schedule.
   */
  final def jittered[R1 <: R](
    min: Double,
    max: Double
  )(implicit ev1: Has.IsHas[R1], ev2: R1 <:< Clock): Schedule[R1 with Random, A, B] =
    delayedM[R1 with Random] { duration =>
      random.nextDouble.map { random =>
        val d        = duration.toNanos
        val jittered = d * min * (1 - random) + d * max * random
        Duration.fromNanos(jittered.toLong)
      }
    }

  /**
   * Puts this schedule into the first element of a either, and passes along
   * another value unchanged as the second element of the either.
   */
  final def left[C]: Schedule[R, Either[A, C], Either[B, C]] = self +++ Schedule.identity[C]

  /**
   * Returns a new schedule that maps over the output of this one.
   */
  final def map[A1 <: A, C](f: B => C): Schedule[R, A1, C] =
    new Schedule[R, A1, C] {
      type State = self.State
      val initial = self.initial
      val extract = (a: A1, s: self.State) => f(self.extract(a, s))
      val update  = self.update
    }

  /**
   * Returns a new schedule with the specified effectful modification
   * applied to each sleep performed by this schedule.
   *
   * Note that this does not apply to sleeps performed in Schedule#initial.
   * All effects executed while calculating the modified duration will run with the old
   * environment.
   */
  final def modifyDelay[R1 <: R](
    f: (B, Duration) => ZIO[R1, Nothing, Duration]
  )(implicit ev1: Has.IsHas[R1], ev2: R1 <:< Clock): Schedule[R1, A, B] = {
    def proxy(clock0: Clock.Service, env: R1, current: B): Clock.Service = new Clock.Service {
      def currentTime(unit: TimeUnit) = clock0.currentTime(unit)
      def currentDateTime             = clock0.currentDateTime
      val nanoTime                    = clock0.nanoTime
      def sleep(duration: Duration)   = f(current, duration).provide(env).flatMap(clock0.sleep)
    }
    new Schedule[R1, A, B] {
      type State = self.State
      val initial = self.initial
      val extract = (a: A, s: self.State) => self.extract(a, s)
      val update = (a: A, s: self.State) =>
        self.update(a, s).provideSome[R1](env => ev1.update[R1, Clock.Service](env, proxy(_, env, self.extract(a, s))))
    }
  }

  /**
   * Returns a new schedule that will not perform any sleep calls between recurrences.
   */
  final def noDelay[R1 <: R](implicit ev1: Has.IsHas[R1], ev2: R1 <:< Clock): Schedule[R1, A, B] = {
    def proxy(clock0: Clock.Service): Clock.Service = new Clock.Service {
      def currentTime(unit: TimeUnit) = clock0.currentTime(unit)
      def currentDateTime             = clock0.currentDateTime
      val nanoTime                    = clock0.nanoTime
      def sleep(duration: Duration)   = ZIO.unit
    }

    provideSome[R1](env => ev1.update[R1, Clock.Service](env, proxy(_)))
  }

  /**
   * A new schedule that applies the current one but runs the specified effect
   * for every decision of this schedule. This can be used to create schedules
   * that log failures, decisions, or computed values.
   */
  final def onDecision[A1 <: A, R1 <: R](f: (A1, Option[self.State]) => URIO[R1, Any]): Schedule[R1, A1, B] =
    updated(update =>
      (a, s) =>
        update(a, s).tapBoth(
          _ => f(a, None),
          state => f(a, Some(state))
        )
    )

  /**
   * Provide all requirements to the schedule.
   */
  final def provide(r: R)(implicit ev: NeedsEnv[R]): Schedule[Any, A, B] =
    provideSome(_ => r)

  /**
   * Provide some of the requirements to the schedule.
   */
  final def provideSome[R1](f: R1 => R)(implicit ev: NeedsEnv[R]): Schedule[R1, A, B] =
    new Schedule[R1, A, B] {
      type State = self.State
      val initial = self.initial.provideSome(f)
      val extract = self.extract
      val update  = (a: A, s: self.State) => self.update(a, s).provideSome(f)
    }

  /**
   * Returns a new schedule that effectfully reconsiders the decision made by
   * this schedule.
   * The provided either will be a Left if the schedule has failed and will contain the old state
   * or a Right with the new state if the schedule has updated successfully.
   */
  final def reconsider[R1 <: R, A1 <: A](
    f: (A1, Either[State, State]) => ZIO[R1, Unit, State]
  ): Schedule[R1, A1, B] =
    updated(update =>
      (a: A1, s: State) =>
        update(a, s).foldM(
          _ => f(a, Left(s)),
          s1 => f(a, Right(s1))
        )
    )

  /**
   * Emit the number of repetitions of the schedule so far.
   */
  final def repetitions: Schedule[R, A, Int] =
    fold(0)((n: Int, _: B) => n + 1)

  /**
   * Puts this schedule into the second element of a either, and passes along
   * another value unchanged as the first element of the either.
   */
  final def right[C]: Schedule[R, Either[C, A], Either[C, B]] = Schedule.identity[C] +++ self

  /**
   * Run a schedule using the provided input and collect all outputs.
   */
  final def run(input: Iterable[A]): ZIO[R, Nothing, List[B]] = {
    def loop(xs: List[A], state: State, acc: List[B]): ZIO[R, Nothing, List[B]] = xs match {
      case Nil => ZIO.succeedNow(acc)
      case x :: xs =>
        update(x, state)
          .foldM(
            _ => ZIO.succeedNow(extract(x, state) :: acc),
            s => loop(xs, s, extract(x, state) :: acc)
          )
    }

    initial
      .flatMap(loop(input.toList, _, Nil))
      .map(_.reverse)
  }

  /**
   * Puts this schedule into the second element of a tuple, and passes along
   * another value unchanged as the first element of the tuple.
   */
  final def second[C]: Schedule[R, (C, A), (C, B)] = Schedule.identity[C] *** self

  /**
   * Sends every input value to the specified sink.
   */
  final def tapInput[R1 <: R, A1 <: A](f: A1 => ZIO[R1, Nothing, Unit]): Schedule[R1, A1, B] =
    updated(update => (a, s) => f(a) *> update(a, s))

  /**
   * Sends every output value to the specified sink.
   */
  final def tapOutput[R1 <: R](f: B => ZIO[R1, Nothing, Unit]): Schedule[R1, A, B] =
    updated(update => (a, s) => update(a, s).flatMap(s1 => f(self.extract(a, s1)).as(s1)))

  /**
   * Returns a new schedule with the update function transformed by the
   * specified update transformer.
   */
  final def updated[R1 <: R, A1 <: A](
    f: ((A, State) => ZIO[R, Unit, State]) => (A1, State) => ZIO[R1, Unit, State]
  ): Schedule[R1, A1, B] =
    new Schedule[R1, A1, B] {
      type State = self.State
      val initial = self.initial
      val extract = self.extract
      val update  = f(self.update)
    }

  /**
   * Returns a new schedule that maps this schedule to a Unit output.
   */
  final def unit: Schedule[R, A, Unit] = as(())

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the input of the schedule.
   */
  final def untilInput[A1 <: A](f: A1 => Boolean): Schedule[R, A1, B] = untilInputM(a => ZIO.succeedNow(f(a)))

  /**
   * Returns a new schedule that continues the schedule only until the effectful predicate
   * is satisfied on the input of the schedule.
   */
  final def untilInputM[A1 <: A](f: A1 => UIO[Boolean]): Schedule[R, A1, B] =
    updated(update =>
      (a, s) =>
        f(a).flatMap {
          case true  => ZIO.fail(())
          case false => update(a, s)
        }
    )

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the output value of the schedule.
   */
  final def untilOutput(f: B => Boolean): Schedule[R, A, B] = untilOutputM(b => ZIO.succeedNow(f(b)))

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the output value of the schedule.
   */
  final def untilOutputM(f: B => UIO[Boolean]): Schedule[R, A, B] =
    updated(update =>
      (a, s) =>
        f(self.extract(a, s)).flatMap {
          case true  => ZIO.fail(())
          case false => update(a, s)
        }
    )

  /**
   * Updates a service in the environment of this effect.
   */
  final def updateService[M] =
    new Schedule.UpdateService[R, A, B, M](self)

  /**
   * Returns a new schedule that continues this schedule so long as the
   * predicate is satisfied on the input of the schedule.
   */
  final def whileInput[A1 <: A](f: A1 => Boolean): Schedule[R, A1, B] =
    whileInputM(a => IO.succeedNow(f(a)))

  /**
   * Returns a new schedule that continues this schedule so long as the
   * effectful predicate is satisfied on the input of the schedule.
   */
  final def whileInputM[A1 <: A](f: A1 => UIO[Boolean]): Schedule[R, A1, B] =
    check((a, _) => f(a))

  /**
   * Returns a new schedule that continues this schedule so long as the predicate
   * is satisfied on the output value of the schedule.
   */
  final def whileOutput(f: B => Boolean): Schedule[R, A, B] =
    whileOutputM(b => IO.succeedNow(f(b)))

  /**
   * Returns a new schedule that continues this schedule so long as the effectful predicate
   * is satisfied on the output value of the schedule.
   */
  final def whileOutputM(f: B => UIO[Boolean]): Schedule[R, A, B] =
    check((_, b) => f(b))

  /**
   * Named alias for `<*>`.
   */
  final def zip[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, (B, C)] = self && that

  /**
   * Named alias for `<*`.
   */
  final def zipLeft[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, B] =
    self <* that

  /**
   * Named alias for `*>`.
   */
  final def zipRight[R1 <: R, A1 <: A, C](that: Schedule[R1, A1, C]): Schedule[R1, A1, C] =
    self *> that
}

object Schedule {

  def apply[R, S, A, B](
    initial0: URIO[R, S],
    update0: (A, S) => URIO[R, S],
    extract0: (A, S) => B
  ): Schedule[R, A, B] =
    new Schedule[R, A, B] {
      type State = S
      val initial = initial0
      val extract = extract0
      val update  = update0
    }

  /**
   * A schedule that recurs forever, collecting all inputs into a list.
   */
  def collectAll[A]: Schedule[Any, A, List[A]] =
    identity[A].collectAll

  /**
   * A schedule that recurs as long as the condition f holds, collecting all inputs into a list.
   */
  def collectWhile[A](f: A => Boolean): Schedule[Any, A, List[A]] =
    this.doWhile(f).collectAll

  /**
   * A schedule that recurs as long as the effectful condition holds, collecting all inputs into a list.
   */
  def collectWhileM[A](f: A => UIO[Boolean]): Schedule[Any, A, List[A]] =
    this.doWhileM(f).collectAll

  /**
   * A schedule that recurs until the condition f fails, collecting all inputs into a list.
   */
  def collectUntil[A](f: A => Boolean): Schedule[Any, A, List[A]] =
    this.doUntil(f).collectAll

  /**
   * A schedule that recurs until the effectful condition f fails, collecting all inputs into a list.
   */
  def collectUntilM[A](f: A => UIO[Boolean]): Schedule[Any, A, List[A]] =
    this.doUntilM(f).collectAll

  /**
   * A new schedule derived from the specified schedule which transforms the delays into effectful sleeps.
   */
  def delayed[R <: Clock, A](s: Schedule[R, A, Duration]): Schedule[R, A, Duration] =
    s.addDelay(x => x)

  /**
   * A schedule that recurs for as long as the predicate evaluates to true.
   */
  def doWhile[A](f: A => Boolean): Schedule[Any, A, A] =
    doWhileM(a => ZIO.succeedNow(f(a)))

  /**
   * A schedule that recurs for as long as the effectful predicate evaluates to true.
   */
  def doWhileM[A](f: A => UIO[Boolean]): Schedule[Any, A, A] =
    identity[A].whileInputM(f)

  /**
   * A schedule that recurs for as long as the predicate is equal.
   */
  def doWhileEquals[A](a: => A): Schedule[Any, A, A] =
    identity[A].whileInput(_ == a)

  /**
   * A schedule that recurs for until the predicate evaluates to true.
   */
  def doUntil[A](f: A => Boolean): Schedule[Any, A, A] =
    doUntilM(a => ZIO.succeedNow(f(a)))

  /**
   * A schedule that recurs for until the predicate evaluates to true.
   */
  def doUntilM[A](f: A => UIO[Boolean]): Schedule[Any, A, A] =
    identity[A].untilInputM(f)

  /**
   * A schedule that recurs for until the predicate is equal.
   */
  def doUntilEquals[A](a: => A): Schedule[Any, A, A] =
    identity[A].untilInput(_ == a)

  /**
   * A schedule that recurs for until the input value becomes applicable to partial function
   * and then map that value with given function.
   * */
  def doUntil[A, B](pf: PartialFunction[A, B]): Schedule[Any, A, Option[B]] =
    new Schedule[Any, A, Option[B]] {
      type State = Unit
      val initial = ZIO.unit
      val extract = (a: A, _: Unit) => pf.lift(a)
      val update  = (a: A, _: Unit) => pf.lift(a).fold[IO[Unit, Unit]](ZIO.succeedNow(()))(_ => ZIO.fail(()))
    }

  /**
   * A schedule that will recur until the specified duration elapses. Returns
   * the total elapsed time.
   */
  def duration(duration: Duration): Schedule[Clock, Any, Duration] =
    elapsed.untilOutput(_ >= duration)

  /**
   * A schedule that recurs forever without delay. Returns the elapsed time
   * since the schedule began.
   */
  val elapsed: Schedule[Clock, Any, Duration] =
    Schedule[Clock, (Long, Long), Any, Duration](
      clock.nanoTime.map((_, 0L)),
      { case (_, (start, _))   => clock.nanoTime.map(currentTime => (start, currentTime - start)) },
      { case (_, (_, elapsed)) => Duration.fromNanos(elapsed) }
    )

  /**
   * A schedule that always recurs, but will wait a certain amount between
   * repetitions, given by `base * factor.pow(n)`, where `n` is the number of
   * repetitions so far. Returns the current duration between recurrences.
   */
  def exponential(base: Duration, factor: Double = 2.0): Schedule[Clock, Any, Duration] =
    delayed(forever.map(i => base * math.pow(factor, i.doubleValue)))

  /**
   * A schedule that always recurs, increasing delays by summing the
   * preceding two delays (similar to the fibonacci sequence). Returns the
   * current duration between recurrences.
   */
  def fibonacci(one: Duration): Schedule[Clock, Any, Duration] =
    delayed {
      unfold[(Duration, Duration)]((one, one)) {
        case (a1, a2) => (a2, a1 + a2)
      }.map(_._1)
    }

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
  def fixed(interval: Duration): Schedule[Clock, Any, Int] = interval match {
    case Duration.Infinity                    => once >>> never.as(1)
    case Duration.Finite(nanos) if nanos == 0 => forever
    case Duration.Finite(nanos) =>
      Schedule[Clock, (Long, Int, Int), Any, Int](
        clock.nanoTime.map(nt => (nt, 1, 0)),
        (_, t) =>
          t match {
            case (start, n0, i) =>
              clock.nanoTime.flatMap { now =>
                val await = (start + n0 * nanos) - now
                val n = 1 +
                  (if (await < 0) ((now - start) / nanos).toInt else n0)

                ZIO.sleep(Duration.fromNanos(await.max(0L))).as((start, n, i + 1))
              }
          },
        (_, s) => s._3
      )
  }

  /**
   * A schedule that recurs forever, producing a count of repeats: 0, 1, 2, ...
   */
  val forever: Schedule[Any, Any, Int] = unfold(0)(_ + 1)

  /**
   * A schedule that recurs once with the specified delay.
   */
  def fromDuration(duration: Duration): Schedule[Clock, Any, Duration] =
    delayed(recurs(1).as(duration))

  /**
   * A schedule that recurs once for each of the specified durations, delaying
   * each time for the length of the specified duration. Returns the length of
   * the current duration between recurrences.
   */
  def fromDurations(duration: Duration, durations: Duration*): Schedule[Clock, Any, Duration] =
    durations.foldLeft(fromDuration(duration))((schedule, duration) => schedule ++ fromDuration(duration))

  /**
   * A schedule that recurs forever, mapping input values through the
   * specified function.
   */
  def fromFunction[A, B](f: A => B): Schedule[Any, A, B] = identity[A].map(f)

  /**
   * A schedule that recurs forever, returning each input as the output.
   */
  def identity[A]: Schedule[Any, A, A] =
    Schedule[Any, Unit, A, A](ZIO.unit, (_, _) => ZIO.unit, (a, _) => a)

  /**
   * A schedule that always recurs, but will repeat on a linear time
   * interval, given by `base * n` where `n` is the number of
   * repetitions so far. Returns the current duration between recurrences.
   */
  def linear(base: Duration): Schedule[Clock, Any, Duration] =
    delayed(forever.map(i => base * (i + 1).doubleValue()))

  /**
   * A schedule that waits forever when updating or initializing.
   */
  val never: Schedule[Any, Any, Nothing] =
    Schedule[Any, Nothing, Any, Nothing](UIO.never, (_, _) => UIO.never, (_, never) => never)

  /**
   * A schedule that executes once.
   */
  val once: Schedule[Any, Any, Unit] = recurs(1).unit

  /**
   * A schedule that sleeps for random duration that is uniformly distributed in the given range.
   * The schedules output is the duration it has slept on the last update, or 0 if it hasn't updated yet.
   */
  def randomDelay(min: Duration, max: Duration): Schedule[Random with Clock, Any, Duration] = {
    val minNanos = min.toNanos
    val maxNanos = max.toNanos
    Schedule[Clock with Random, Duration, Any, Duration](
      ZIO.succeedNow(Duration.Zero), {
        case _ =>
          random.nextLongBounded(maxNanos - minNanos + 1).flatMap { n =>
            val duration = Duration.fromNanos(n + minNanos)
            clock.sleep(duration).as(duration)
          }
      },
      { case (_, duration) => duration }
    )
  }

  /**
   * A schedule that sleeps for random duration that is normally distributed.
   * The schedules output is the duration it has slept on the last update, or 0 if it hasn't updated yet.
   */
  def randomDelayNormal(mean: Duration, std: Duration): Schedule[Random with Clock, Any, Duration] =
    Schedule[Clock with Random, Duration, Any, Duration](
      ZIO.succeedNow(Duration.Zero), {
        case _ =>
          random.nextGaussian.flatMap { n =>
            val duration = mean + std * n
            clock.sleep(duration).as(duration)
          }
      },
      { case (_, duration) => duration }
    )

  /**
   * A schedule that recurs the specified number of times. Returns the number
   * of repetitions so far.
   *
   * If 0 or negative numbers are given, the operation is not repeated at all so
   * that in `(op: IO[E, A]).repeat(Schedule.recurs(0)) `, op is only done once and repeated 0 times.
   */
  def recurs(n: Int): Schedule[Any, Any, Int] =
    forever.whileOutput(_ < n)

  /**
   * A schedule that waits for the specified amount of time between each
   * input. Returns the number of inputs so far.
   *
   * <pre>
   * |action|-----interval-----|action|-----interval-----|action|
   * </pre>
   */
  def spaced(interval: Duration): Schedule[Clock, Any, Int] =
    forever.addDelay(_ => interval)

  /**
   * A schedule that always fails.
   */
  val stop: Schedule[Any, Any, Unit] =
    recurs(0).unit

  /**
   * A schedule that recurs forever, returning the constant for every output.
   */
  def succeed[A](a: A): Schedule[Any, Any, A] =
    forever.as(a)

  /**
   * A schedule that recurs forever, dumping input values to the specified
   * sink, and returning those same values unmodified.
   */
  def tapInput[R, A](f: A => URIO[R, Unit]): Schedule[R, A, A] =
    identity[A].tapInput(f)

  /**
   * A schedule that recurs forever, dumping output values to the specified
   * sink, and returning those same values unmodified.
   */
  def tapOutput[R, A](f: A => URIO[R, Unit]): Schedule[R, A, A] =
    identity[A].tapOutput(f)

  /**
   * A schedule that always recurs without delay, and computes the output
   * through recured application of a function to a base value.
   */
  def unfold[A](a: => A)(f: A => A): Schedule[Any, Any, A] =
    unfoldM(IO.succeedNow(a))(f.andThen(IO.succeedNow[A](_)))

  /**
   * A schedule that always recurs without delay, and computes the output
   * through recured application of a function to a base value.
   */
  def unfoldM[R, A](a: URIO[R, A])(f: A => URIO[R, A]): Schedule[R, Any, A] =
    Schedule[R, A, Any, A](a, (_, a) => f(a), (_, a) => a)

  final class UpdateService[-R, -A, +B, M](private val self: Schedule[R, A, B]) extends AnyVal {
    def apply[R1 <: R with Has[M]](f: M => M)(implicit ev: Has.IsHas[R1], tag: Tag[M]): Schedule[R1, A, B] =
      self.provideSome(ev.update(_, f))
  }
}
