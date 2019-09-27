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

package zio

import zio.clock.Clock
import zio.duration.Duration
import zio.random.Random
import java.util.concurrent.TimeUnit
// import zio.random.{ nextDouble, Random }
// import zio.random.Random

/**
 * Defines a stateful, possibly effectful, recurring schedule of actions.
 *
 * A `ZSchedule[R, A, B]` consumes `A` values, and based on the inputs and the
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
trait ZSchedule[-R, -A, +B] extends Serializable { self =>

  /**
   * The internal state type of the schedule.
   */
  type State

  /**
   * The initial state of the schedule.
   */
  val initial: ZIO[R, Nothing, State]

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
  final def &&[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, (B, C)] =
    new ZSchedule[R1, A1, (B, C)] {
      type State        = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val extract = { case (a, (s1, s2)) => (self.extract(a, s1), that.extract(a, s2)) }
      val update = {
        case (a, (s1, s2)) =>
          self.update(a, s1).zipPar(that.update(a, s2))
      }
    }

  /**
   * Split the input
   */
  final def ***[R1 <: R, C, D](that: ZSchedule[R1, C, D]): ZSchedule[R1, (A, C), (B, D)] =
    new ZSchedule[R1, (A, C), (B, D)] {
      type State  = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val extract = { case ((a1, a2), (s1, s2)) => (self.extract(a1, s1), that.extract(a2, s2)) }
      val update = { case ((a1, a2), (s1, s2)) =>
        self.update(a1, s1).zipPar(that.update(a2, s2))
      }
    }

  /**
   * The same as `&&`, but ignores the left output.
   */
  final def *>[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, C] =
    (self && that).map(_._2)

  /**
   * Chooses between two schedules with different outputs.
   */
  final def +++[R1 <: R, C, D](that: ZSchedule[R1, C, D]): ZSchedule[R1, Either[A, C], Either[B, D]] =
    new ZSchedule[R1, Either[A, C], Either[B, D]] {
      type State  = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val extract = { case (a, (s1, s2)) =>
        a.fold(a => Left(self.extract(a, s1)), c => Right(that.extract(c, s2)))
      }
      val update = (a: Either[A, C], s: State) =>
        a match {
          case Left(a)  => self.update(a, s._1).map((_, s._2))
          case Right(c) => that.update(c, s._2).map((s._1, _))
        }
    }

  /**
   * The same as `&&`, but ignores the right output.
   */
  final def <*[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, B] =
    (self && that).map(_._1)

  /**
   * Returns a new schedule that continues only as long as both schedules
   * continue, using the maximum of the delays of the two schedules.
   */
  final def <*>[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, (B, C)] = self zip that

  /**
   * A backwards version of `>>>`.
   */
  final def <<<[R1 <: R, C](that: ZSchedule[R1, C, A]): ZSchedule[R1, C, B] = that >>> self

  /**
   * Returns the composition of this schedule and the specified schedule,
   * by piping the output of this one into the input of the other, and summing
   * delays produced by both.
   */
  final def >>>[R1 <: R, C](that: ZSchedule[R1, B, C]): ZSchedule[R1, A, C] =
    new ZSchedule[R1, A, C] {
      type State  = (self.State, that.State)
      val initial = self.initial.zip(that.initial)
      val extract = { case (a, (s1, s2)) => that.extract(self.extract(a, s1), s2) }
      val update = {
        case (a, (s1, s2)) =>
          for {
            s1 <- self.update(a, s1)
            s2 <- that.update(self.extract(a, s1), s2)
          } yield (s1, s2)
      }
    }

  /**
   * Returns a new schedule that continues as long as either schedule continues,
   * using the minimum of the delays of the two schedules.
   */
  final def ||[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, (B, C)] = {
    new ZSchedule[R1, A1, (B, C)] {
      type State        = (self.State, that.State)
      type RunningState = (self.State, that.State)
      val initial = self.initial zip that.initial
      val extract = { case (a, (s1, s2)) =>
          (self.extract(a, s1), that.extract(a, s2))
      }
      val update = { case (a, (s1, s2)) =>
          self.update(a, s1).raceEither(that.update(a, s2)).map {
            case Left(s1) => (s1, s2)
            case Right(s2) => (s1, s2)
          }
      }
    }
  }

  /**
   * Chooses between two schedules with a common output.
   */
  final def |||[R1 <: R, B1 >: B, C](that: ZSchedule[R1, C, B1]): ZSchedule[R1, Either[A, C], B1] =
    (self +++ that).map(_.merge)

  /**
   * Returns a new schedule with the given delay added to every update.
   */
  final def addDelay(f: B => Duration): ZSchedule[R with Clock, A, B] =
    addDelayM(b => ZIO.succeed(f(b)))

  /**
   * Returns a new schedule with the effectfully calculated delay added to every update.
   */
  final def addDelayM[R1 <: R](f: B => ZIO[R1, Nothing, Duration]): ZSchedule[R1 with Clock, A, B] =
    updated(
      update =>
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
  final def andThen[R1 <: R, A1 <: A, B1 >: B](that: ZSchedule[R1, A1, B1]): ZSchedule[R1, A1, B1] =
    andThenEither(that).map(_.merge)

  /**
   * Returns a new schedule that first executes this schedule to completion,
   * and then executes the specified schedule to completion.
   */
  final def andThenEither[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, Either[B, C]] =
    new ZSchedule[R1, A1, Either[B, C]] {
      type State        = Either[self.State, that.State]
      val initial       = self.initial.map(Left(_))
      val extract = { case (a, s) =>
        s.fold(b => Left(self.extract(a, b)), c => Right(that.extract(a, c)))
      }

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
  final def as[C](c: => C): ZSchedule[R, A, C] = map(_ => c)

  /**
   * A named alias for `&&`.
   */
  final def both[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, (B, C)] = self && that

  /**
   * The same as `both` followed by `map`.
   */
  final def bothWith[R1 <: R, A1 <: A, C, D](that: ZSchedule[R1, A1, C])(f: (B, C) => D): ZSchedule[R1, A1, D] =
    (self && that).map(f.tupled)

  /**
   * Peeks at the state produced by this schedule, executes some action, and
   * then continues the schedule or not based on the specified state predicate.
   */
  final def check[A1 <: A](test: (A1, B) => UIO[Boolean]): ZSchedule[R, A1, B] =
    updated(
      update =>
        (a, s) =>
          test(a, self.extract(a, s)).flatMap {
            case false => ZIO.fail(())
            case true => update(a, s)
          }
    )

  /**
   * Returns a new schedule that collects the outputs of this one into a list.
   */
  final def collectAll: ZSchedule[R, A, List[B]] =
    fold(List.empty[B])((xs, x) => x :: xs).map(_.reverse)

  /**
   * An alias for `<<<`
   */
  final def compose[R1 <: R, C](that: ZSchedule[R1, C, A]): ZSchedule[R1, C, B] = self <<< that

  @deprecated("use as", "1.0.0")
  final def const[C](c: => C): ZSchedule[R, A, C] = as(c)

  /**
   * Returns a new schedule that deals with a narrower class of inputs than
   * this schedule.
   */
  final def contramap[A1](f: A1 => A): ZSchedule[R, A1, B] =
    new ZSchedule[R, A1, B] {
      type State  = self.State
      val initial = self.initial
      val extract = (a, s) => self.extract(f(a), s)
      val update  = (a, s) => self.update(f(a), s)
    }

  /**
   * Returns a new schedule with all durations produced by the schedule
   * transformed into effectful sleeps.
   */
  final def delayed(implicit ev: B <:< Duration): ZSchedule[R with Clock, A, B] =
    addDelay(identity)

  /**
   * Returns a new schedule that contramaps the input and maps the output.
   */
  final def dimap[A1, C](f: A1 => A, g: B => C): ZSchedule[R, A1, C] =
    contramap(f).map(g)

  /**
   * A named alias for `||`.
   */
  final def either[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, (B, C)] = self || that

  /**
   * The same as `either` followed by `map`.
   */
  final def eitherWith[R1 <: R, A1 <: A, C, D](that: ZSchedule[R1, A1, C])(f: (B, C) => D): ZSchedule[R1, A1, D] =
    (self || that).map(f.tupled)

  /**
   * Runs the specified finalizer as soon as the schedule is complete. Note
   * that unlike `ZIO#ensuring`, this method does not guarantee the finalizer
   * will be run. The `Schedule` may not initialize or the driver of the
   * schedule may not run to completion. However, if the `Schedule` ever
   * decides not to continue, then the finalizer will be run.
   *
   * Note that the finalizer will be run multiple times if the schedule tries
   * to advance multiple times. Whatever is done in the finalizer should be idempotent.
   */
  final def ensuring(finalizer: UIO[_]): ZSchedule[R, A, B] =
    updated(
      update =>
        (a: A, s: State) =>
          update(a, s).foldM(
            _ => finalizer *> ZIO.fail(()),
            ZIO.succeed
          )
    )

  /**
   * Puts this schedule into the first element of a tuple, and passes along
   * another value unchanged as the second element of the tuple.
   */
  final def first[R1 <: R, C]: ZSchedule[R1, (A, C), (B, C)] = self *** ZSchedule.identity[C]

  /**
   * Returns a new schedule that folds over the outputs of this one.
   */
  final def fold[Z](z: Z)(f: (Z, B) => Z): ZSchedule[R, A, Z] =
    foldM[Z](IO.succeed(z))((z, b) => IO.succeed(f(z, b)))

  /**
   * Returns a new schedule that effectfully folds over the outputs of this one.
   */
  final def foldM[Z](z: UIO[Z])(f: (Z, B) => UIO[Z]): ZSchedule[R, A, Z] =
    new ZSchedule[R, A, Z] {
      type State        = (self.State, Z)
      val initial = self.initial.zip(z)
      val extract =  (_, s) => s._2
      val update = { case (a, (s, z)) =>
        self
          .update(a, s)
          .flatMap(s => f(z, self.extract(a, s)).map((s, _)))
      }
    }

  /**
   * Returns a new schedule that loops this one forever, resetting the state
   * when this schedule is done.
   */
  final def forever: ZSchedule[R, A, B] =
    new ZSchedule[R, A, B] {
      type State  = self.State
      val initial = self.initial
      val extract = self.extract
      val update  = (a, s) =>
        self.update(a, s) orElse self.initial.flatMap(self.update(a, _))
    }

  /**
   * Returns a new schedule with the specified initial state transformed
   * by the specified initial transformer.
   */
  final def initialized[R1 <: R, A1 <: A](f: ZIO[R1, Nothing, State] => ZIO[R1, Nothing, State]): ZSchedule[R1, A1, B] =
    new ZSchedule[R1, A1, B] {
      type State  = self.State
      val initial = f(self.initial)
      val extract = self.extract
      val update  = self.update
    }

  /**
   * Applies random jitter to all sleeps executed by the schedule.
   */
  final def jittered_[R1 <: R with Clock with Random](min: Double, max: Double)(f: (R1, Clock) => R1): ZSchedule[R1, A, B] = {
    final class Proxy(clock0: Clock.Service[Any], random0: Random.Service[Any]) extends Clock {
      val clock = new Clock.Service[Any] {
        def currentTime(unit: TimeUnit) = clock0.currentTime(unit)
        def currentDateTime = clock0.currentDateTime
        val nanoTime = clock0.nanoTime
        def sleep(duration: Duration) = random0.nextDouble.flatMap { random =>
          val d = duration.toNanos
          val jittered = d * min * (1 - random) + d * max * random
          clock0.sleep(Duration.fromNanos(jittered.toLong))
        }
      }
    }
    new ZSchedule[R1, A, B] {
      type State = (self.State, R1)
      val initial = for {
        init <- self.initial
        env  <- ZIO.environment[R1]
      } yield (init, f(env, new Proxy(env.clock, env.random)))
      val extract = (a, s) => self.extract(a, s._1)
      val update = { case (a, (s, env)) => self.update(a, s).provide(env).map((_, env)) }
    }
  }

  /**
   * Puts this schedule into the first element of a either, and passes along
   * another value unchanged as the second element of the either.
   */
  final def left[C]: ZSchedule[R, Either[A, C], Either[B, C]] = self +++ ZSchedule.identity[C]

  /**
   * Sends every input value to the specified sink.
   */
  final def logInput[R1 <: R, A1 <: A](f: A1 => ZIO[R1, Nothing, Unit]): ZSchedule[R1, A1, B] =
    updated(update => (a, s) => f(a) *> update(a, s))

  /**
   * Sends every output value to the specified sink.
   */
  final def logOutput[R1 <: R](f: B => ZIO[R1, Nothing, Unit]): ZSchedule[R1, A, B] =
    updated(update => (a, s) => update(a, s).flatMap(s1 => f(self.extract(a, s1)).as(s1)))

  /**
   * Returns a new schedule that maps over the output of this one.
   */
  final def map[A1 <: A, C](f: B => C): ZSchedule[R, A1, C] =
    new ZSchedule[R, A1, C] {
      type State  = self.State
      val initial = self.initial
      val extract = (a, s) => f(self.extract(a, s))
      val update  = self.update
    }

  /**
   * A new schedule that applies the current one but runs the specified effect
   * for every decision of this schedule. This can be used to create schedules
   * that log failures, decisions, or computed values.
   */
  final def onDecision[A1 <: A](f: (A1, self.State) => UIO[Unit]): ZSchedule[R, A1, B] =
    updated(update => (a, s) => update(a, s).tap(step => f(a, step)))

  /**
   * Provide all requirements to the schedule.
   */
  final def provide(r: R): ZSchedule[Any, A, B] =
    new ZSchedule[Any, A, B] {
      type State        = self.State
      val initial = self.initial.provide(r)
      val extract = self.extract
      val update  = self.update(_, _).provide(r)
    }

  /**
   * Returns a new schedule that effectfully reconsiders the decision made by
   * this schedule.
   * The provided either will be a Left if the schedule has failed and will contain the old state
   * or a Right with the new state if the schedule has updated successfully.
   */
  final def reconsider[R1 <: R, A1 <: A](
    f: (A1, Either[State, State]) => ZIO[R1, Unit, State]
  ): ZSchedule[R1, A1, B] =
    updated(
      update =>
        (a: A1, s: State) =>
          update(a, s).foldM(
            _ => f(a, Left(s)),
            s1 => f(a, Right(s1))
          )
    )

  /**
   * Emit the number of repetitions of the schedule so far.
   */
  final def repetitions: ZSchedule[R, A, Int] =
    fold(0)((n: Int, _: B) => n + 1)

  /**
   * Puts this schedule into the second element of a either, and passes along
   * another value unchanged as the first element of the either.
   */
  final def right[C]: ZSchedule[R, Either[C, A], Either[C, B]] = ZSchedule.identity[C] +++ self

  /**
   * Puts this schedule into the second element of a tuple, and passes along
   * another value unchanged as the first element of the tuple.
   */
  final def second[C]: ZSchedule[R, (C, A), (C, B)] = ZSchedule.identity[C] *** self

  /**
   * Returns a new schedule with the update function transformed by the
   * specified update transformer.
   */
  final def updated[R1 <: R, A1 <: A, C](
    f: ((A, State) => ZIO[R, Unit, State]) => (A1, State) => ZIO[R1, Unit, State],
  ): ZSchedule[R1, A1, B] =
    new ZSchedule[R1, A1, B] {
      type State  = self.State
      val initial = self.initial
      val extract = self.extract
      val update  = f(self.update)
    }

  /**
   * Returns a new schedule that maps this schedule to a Unit output.
   */
  final def unit: ZSchedule[R, A, Unit] = as(())

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the input of the schedule.
   */
  final def untilInput[A1 <: A](f: A1 => Boolean): ZSchedule[R, A1, B] = untilInputM(a => ZIO.succeed(f(a)))

  /**
   * Returns a new schedule that continues the schedule only until the effectful predicate
   * is satisfied on the input of the schedule.
   */
  final def untilInputM[A1 <: A](f: A1 => UIO[Boolean]): ZSchedule[R, A1, B] =
    updated(
      update =>
        (a, s) =>
          f(a).flatMap {
            case true => ZIO.fail(())
            case false => update(a, s)
          }
    )

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the output value of the schedule.
   */
  final def untilOutput(f: B => Boolean): ZSchedule[R, A, B] = untilOutputM(b => ZIO.succeed(f(b)))

  /**
   * Returns a new schedule that continues the schedule only until the predicate
   * is satisfied on the output value of the schedule.
   */
  final def untilOutputM(f: B => UIO[Boolean]): ZSchedule[R, A, B] =
    updated(
      update =>
        (a, s) =>
          f(self.extract(a, s)).flatMap {
            case true => ZIO.fail(())
            case false => update(a, s)
          }
    )

  /**
   * Returns a new schedule that maps this schedule to a Unit output.
   */
  @deprecated("use unit", "1.0.0")
  final def void: ZSchedule[R, A, Unit] = unit

  /**
   * Returns a new schedule that continues this schedule so long as the
   * predicate is satisfied on the input of the schedule.
   */
  final def whileInput[A1 <: A](f: A1 => Boolean): ZSchedule[R, A1, B] =
    whileInputM(a => IO.succeed(f(a)))

  /**
   * Returns a new schedule that continues this schedule so long as the
   * effectful predicate is satisfied on the input of the schedule.
   */
  final def whileInputM[A1 <: A](f: A1 => UIO[Boolean]): ZSchedule[R, A1, B] =
    check((a, _) => f(a))

  /**
   * Returns a new schedule that continues this schedule so long as the predicate
   * is satisfied on the output value of the schedule.
   */
  final def whileOutput(f: B => Boolean): ZSchedule[R, A, B] =
    check((_, b) => IO.succeed(f(b)))

  /**
   * Named alias for `<*>`.
   */
  final def zip[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, (B, C)] = self && that

  /**
   * Named alias for `<*`.
   */
  final def zipLeft[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, B] =
    self <* that

  /**
   * Named alias for `*>`.
   */
  final def zipRight[R1 <: R, A1 <: A, C](that: ZSchedule[R1, A1, C]): ZSchedule[R1, A1, C] =
    self *> that
}

object ZSchedule {

  implicit class ZScheduleDefaultEnvironmentOps[A, B](sched: ZSchedule[DefaultRuntime#Environment, A, B]) {
    type Env = DefaultRuntime#Environment

    /**
     * Applies random jitter to the schedule bounded by the factors 0.0 and 1.0.
     */
    final def jittered: ZSchedule[Env, A, B] =
      jittered(0.0, 1.0)

    /**
     * Applies random jitter to all sleeps executed by the schedule.
     */
    final def jittered(min: Double, max: Double): ZSchedule[Env, A, B] =
      sched.jittered_[Env](min, max)((old, clock0) =>
        new zio.clock.Clock
          with zio.console.Console
          with zio.system.System
          with zio.random.Random
          with zio.blocking.Blocking {
            val clock = clock0.clock
            val console = old.console
            val system = old.system
            val random = old.random
            val blocking = old.blocking
      })
  }

  final def apply[R, S, A, B](
    initial0: ZIO[R, Nothing, S],
    update0: (A, S) => ZIO[R, Nothing, S],
    extract0: (A, S) => B
  ): ZSchedule[R, A, B] =
    new ZSchedule[R, A, B] {
      type State        = S
      type RunningState = S
      val initial = initial0
      val extract = extract0
      val update  = update0
    }

  /**
   * A schedule that recurs forever, collecting all inputs into a list.
   */
  final def collectAll[A]: Schedule[A, List[A]] = identity[A].collectAll

  /**
   * A schedule that recurs as long as the condition f holds, collecting all inputs into a list.
   */
  final def collectWhile[A](f: A => Boolean): Schedule[A, List[A]] = this.doWhile(f).collectAll

  /**
   * A schedule that recurs as long as the effectful condition holds, collecting all inputs into a list.
   */
  final def collectWhileM[A](f: A => UIO[Boolean]): Schedule[A, List[A]] = this.doWhileM(f).collectAll

  /**
   * A schedule that recurs until the condition f failes, collecting all inputs into a list.
   */
  final def collectUntil[A](f: A => Boolean): Schedule[A, List[A]] = this.doUntil(f).collectAll

  /**
   * A schedule that recurs until the effectful condition f failes, collecting all inputs into a list.
   */
  final def collectUntilM[A](f: A => UIO[Boolean]): Schedule[A, List[A]] = this.doUntilM(f).collectAll

  /**
   * A new schedule derived from the specified schedule which transforms the delays into effectful sleeps.
   */
  final def delayed[R, A](s: ZSchedule[R, A, Duration]): ZSchedule[R with Clock, A, Duration] =
    s.addDelay(x => x)

  /**
   * A schedule that recurs for as long as the predicate evaluates to true.
   */
  final def doWhile[A](f: A => Boolean): Schedule[A, A] =
    doWhileM(a => ZIO.succeed(f(a)))

  /**
   * A schedule that recurs for as long as the effectful predicate evaluates to true.
   */
  final def doWhileM[A](f: A => UIO[Boolean]): Schedule[A, A] =
    identity[A].whileInputM(f)

  /**
   * A schedule that recurs for as long as the predicate is equal.
   */
  final def doWhileEquals[A](a: A): Schedule[A, A] =
    identity[A].whileInput(_ == a)

  /**
   * A schedule that recurs for until the predicate evaluates to true.
   */
  final def doUntil[A](f: A => Boolean): Schedule[A, A] =
    doUntilM(a => ZIO.succeed(f(a)))

  /**
   * A schedule that recurs for until the predicate evaluates to true.
   */
  final def doUntilM[A](f: A => UIO[Boolean]): Schedule[A, A] =
    identity[A].untilInputM(f)

  /**
   * A schedule that recurs for until the predicate is equal.
   */
  final def doUntilEquals[A](a: A): Schedule[A, A] =
    identity[A].untilInput(_ == a)

  /**
   * A schedule that recurs for until the input value becomes applicable to partial function
   * and then map that value with given function.
   * */
  final def doUntil[A, B](pf: PartialFunction[A, B]): Schedule[A, Option[B]] =
    new ZSchedule[Any, A, Option[B]] {
      type State  = Unit
      val initial = ZIO.unit
      val extract = (a, _) => pf.lift(a)
      val update = (a, _) => pf.lift(a).fold[IO[Unit, Unit]](ZIO.succeed(()))(_ => ZIO.fail(()))
    }

  /**
   * A schedule that will recur until the specified duration elapses. Returns
   * the total elapsed time.
   */
  final def duration(duration: Duration): ZSchedule[Clock, Any, Duration] =
    elapsed.untilOutput(_ >= duration)

  /**
   * A schedule that recurs forever without delay. Returns the elapsed time
   * since the schedule began.
   */
  final val elapsed: ZSchedule[Clock, Any, Duration] = {
    ZSchedule[Clock, (Long, Long), Any, Duration](
      clock.nanoTime.map((_, 0L)),
      { case (_, (start, _)) => clock.nanoTime.map(currentTime => (start, currentTime - start)) },
      { case (_, (_, elapsed)) => Duration.fromNanos(elapsed) }
    )
  }

  /**
   * A schedule that always recurs, but will wait a certain amount between
   * repetitions, given by `base * factor.pow(n)`, where `n` is the number of
   * repetitions so far. Returns the current duration between recurrences.
   */
  final def exponential(base: Duration, factor: Double = 2.0): ZSchedule[Clock, Any, Duration] =
    delayed(forever.map(i => base * math.pow(factor, i.doubleValue)))

  /**
   * A schedule that always recurs, increasing delays by summing the
   * preceding two delays (similar to the fibonacci sequence). Returns the
   * current duration between recurrences.
   */
  final def fibonacci(one: Duration): ZSchedule[Clock, Any, Duration] =
    delayed {
      unfold[(Duration, Duration)]((Duration.Zero, one)) {
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
  final def fixed(interval: Duration): ZSchedule[Clock, Any, Int] = interval match {
    case Duration.Infinity                    => once >>> never.as(1)
    case Duration.Finite(nanos) if nanos == 0 => forever
    case Duration.Finite(nanos) =>
      ZSchedule[Clock, (Long, Int, Int), Any, Int](
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
  final val forever: Schedule[Any, Int] = unfold(0)(_ + 1)

  /**
   * A schedule that recurs forever, mapping input values through the
   * specified function.
   */
  final def fromFunction[A, B](f: A => B): Schedule[A, B] = identity[A].map(f)

  /**
   * A schedule that recurs forever, returning each input as the output.
   */
  final def identity[A]: Schedule[A, A] =
    ZSchedule[Any, Unit, A, A](ZIO.succeed(()), (_, _) => ZIO.succeed(()), (a, _) => a)

  /**
   * A schedule that always recurs, but will repeat on a linear time
   * interval, given by `base * n` where `n` is the number of
   * repetitions so far. Returns the current duration between recurrences.
   */
  final def linear(base: Duration): ZSchedule[Clock, Any, Duration] =
    delayed(forever.map(i => base * i.doubleValue()))

  /**
   * A schedule that recurs forever, dumping input values to the specified
   * sink, and returning those same values unmodified.
   */
  final def logInput[R, A](f: A => ZIO[R, Nothing, Unit]): ZSchedule[R, A, A] =
    identity[A].logInput(f)

  /**
   * A schedule that waits forever when updating.
   */
  final val never: Schedule[Any, Unit] =
    ZSchedule[Any, Nothing, Any, Unit](UIO.never, (_, _) => UIO.never, (_, _) => ())

  /**
   * A schedule that executes once.
   */
  final val once: Schedule[Any, Unit] = recurs(1).unit

  /**
   * A schedule that recurs the specified number of times. Returns the number
   * of repetitions so far.
   *
   * If 0 or negative numbers are given, the operation is not done at all so
   * that in `(op: IO[E, A]).repeat(Schedule.recurs(0)) `, op is not done at all.
   */
  final def recurs(n: Int): Schedule[Any, Int] = forever.whileOutput(_ <= n)

  /**
   * A schedule that waits for the specified amount of time between each
   * input. Returns the number of inputs so far.
   *
   * <pre>
   * |action|-----interval-----|action|-----interval-----|action|
   * </pre>
   */
  final def spaced(interval: Duration): ZSchedule[Any, Any, Duration] =
    forever.map(_ => interval)

  /**
   * A schedule that recurs forever, returning the constant for every output.
   */
  final def succeed[A](a: A): Schedule[Any, A] = forever.as(a)

  @deprecated("use succeed", "1.0.0")
  final def succeedLazy[A](a: => A): Schedule[Any, A] =
    succeed(a)

  /**
   * A schedule that always recurs without delay, and computes the output
   * through recured application of a function to a base value.
   */
  final def unfold[A](a: => A)(f: A => A): Schedule[Any, A] =
    unfoldM(IO.succeed(a))(f.andThen(IO.succeed[A](_)))

  /**
   * A schedule that always recurs without delay, and computes the output
   * through recured application of a function to a base value.
   */
  final def unfoldM[R, A](a: ZIO[R, Nothing, A])(f: A => ZIO[R, Nothing, A]): ZSchedule[R, Any, A] =
    ZSchedule[R, A, Any, A](a, (_, a) => f(a), (_, a) => a)
}
