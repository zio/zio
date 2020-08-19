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

import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import zio.duration._
import zio.random._
import zio.clock.Clock

/**
 * A `Schedule[Env, In, Out]` defines a recurring schedule, which consumes values of type `In`, and
 * which returns values of type `Out`.
 *
 * Schedules are defined as a possibly infinite set of intervals spread out over time. Each
 * interval defines a window in which recurrence is possible.
 *
 * When schedules are used to repeat or retry effects, the starting boundary of each interval
 * produced by a schedule is used as the moment when the effect will be executed again.
 *
 * Schedules compose in the following primary ways:
 *
 *  * Union. This performs the union of the intervals of two schedules.
 *  * Intersection. This performs the intersection of the intervals of two schedules.
 *  * Sequence. This concatenates the intervals of one schedule onto another.
 *
 * In addition, schedule inputs and outputs can be transformed, filtered (to  terminate a
 * schedule early in response to some input or output), and so forth.
 *
 * A variety of other operators exist for transforming and combining schedules, and the companion
 * object for `Schedule` contains all common types of schedules, both for performing retrying, as
 * well as performing repetition.
 */
sealed abstract class Schedule[-Env, -In, +Out] private (
  private[zio] val step: Schedule.StepFunction[Env, In, Out]
) extends Serializable { self =>
  import Schedule.Decision._
  import Schedule._

  /**
   * Returns a new schedule that performs a geometric intersection on the intervals defined
   * by both schedules.
   */
  def &&[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, (Out, Out2)] =
    (self intersectWith that)((l, r) => Schedule.maxOffsetDateTime(l, r))

  /**
   * Returns a new schedule that has both the inputs and outputs of this and the specified
   * schedule.
   */
  def ***[Env1 <: Env, In2, Out2](that: Schedule[Env1, In2, Out2]): Schedule[Env1, (In, In2), (Out, Out2)] = {
    def loop(
      self: StepFunction[Env, In, Out],
      that: StepFunction[Env1, In2, Out2]
    ): StepFunction[Env1, (In, In2), (Out, Out2)] =
      (now: OffsetDateTime, tuple: (In, In2)) => {
        val (in, in2) = tuple

        (self(now, in) zip that(now, in2)).map {
          case (Done(out), Done(out2))           => Done(out -> out2)
          case (Done(out), Continue(out2, _, _)) => Done(out -> out2)
          case (Continue(out, _, _), Done(out2)) => Done(out -> out2)
          case (Continue(out, linterval, lnext), Continue(out2, rinterval, rnext)) =>
            val interval = Schedule.minOffsetDateTime(linterval, rinterval)

            Continue(out -> out2, interval, loop(lnext, rnext))
        }
      }

    Schedule(loop(self.step, that.step))
  }

  /**
   * The same as `&&`, but ignores the left output.
   */
  def *>[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, Out2] =
    (self && that).map(_._2)

  /**
   * A symbolic alias for `andThen`.
   */
  def ++[Env1 <: Env, In1 <: In, Out2 >: Out](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, Out2] =
    self andThen that

  /**
   * Returns a new schedule that allows choosing between feeding inputs to this schedule, or
   * feeding inputs to the specified schedule.
   */
  def +++[Env1 <: Env, In2, Out2](
    that: Schedule[Env1, In2, Out2]
  ): Schedule[Env1, Either[In, In2], Either[Out, Out2]] = {
    def loop(
      self: StepFunction[Env, In, Out],
      that: StepFunction[Env1, In2, Out2]
    ): StepFunction[Env1, Either[In, In2], Either[Out, Out2]] =
      (now: OffsetDateTime, either: Either[In, In2]) => {
        either match {
          case Left(in) =>
            self(now, in).map {
              case Done(out)                     => Done(Left(out))
              case Continue(out, interval, next) => Continue(Left(out), interval, loop(next, that))
            }

          case Right(in2) =>
            that(now, in2).map {
              case Done(out)                      => Done(Right(out))
              case Continue(out2, interval, next) => Continue(Right(out2), interval, loop(self, next))
            }
        }
      }

    Schedule(loop(self.step, that.step))
  }

  /**
   * Operator alias for `andThenEither`.
   */
  def <||>[Env1 <: Env, In1 <: In, Out2](
    that: Schedule[Env1, In1, Out2]
  ): Schedule[Env1, In1, Either[Out, Out2]] = self.andThenEither(that)

  /**
   * The same as `&&`, but ignores the right output.
   */
  def <*[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, Out] =
    (self && that).map(_._1)

  /**
   * An operator alias for `zip`.
   */
  def <*>[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, (Out, Out2)] =
    self zip that

  /**
   * A backwards version of `>>>`.
   */
  def <<<[Env1 <: Env, In2](that: Schedule[Env1, In2, In]): Schedule[Env1, In2, Out] = that >>> self

  /**
   * Returns the composition of this schedule and the specified schedule, by piping the output of
   * this one into the input of the other. Effects described by this schedule will always be
   * executed before the effects described by the second schedule.
   */
  def >>>[Env1 <: Env, Out2](that: Schedule[Env1, Out, Out2]): Schedule[Env1, In, Out2] = {
    def loop(self: StepFunction[Env, In, Out], that: StepFunction[Env1, Out, Out2]): StepFunction[Env1, In, Out2] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).flatMap {
          case Done(out) => that(now, out).map(_.toDone)
          case Continue(out, interval, next1) =>
            that(now, out).map {
              case Done(out2) => Done(out2)
              case Continue(out2, interval2, next2) =>
                val combined = Schedule.maxOffsetDateTime(interval, interval2)

                Continue(out2, combined, loop(next1, next2))
            }
        }

    Schedule(loop(self.step, that.step))
  }

  /**
   * Returns a new schedule that performs a geometric union on the intervals defined
   * by both schedules.
   */
  def ||[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, (Out, Out2)] =
    (self unionWith that)((l, r) => Schedule.minOffsetDateTime(l, r))

  /**
   * Returns a new schedule that chooses between two schedules with a common output.
   */
  def |||[Env1 <: Env, Out1 >: Out, In2](
    that: Schedule[Env1, In2, Out1]
  ): Schedule[Env1, Either[In, In2], Out1] =
    (self +++ that).map(_.merge)

  /**
   * Returns a new schedule with the given delay added to every interval defined by this schedule.
   */
  def addDelay(f: Out => Duration): Schedule[Env, In, Out] = addDelayM(out => ZIO.succeed(f(out)))

  /**
   * Returns a new schedule with the given effectfully computed delay added to every interval
   * defined by this schedule.
   */
  def addDelayM[Env1 <: Env](f: Out => URIO[Env1, Duration]): Schedule[Env1, In, Out] =
    modifyDelayM((out, duration) => f(out).map(duration + _))

  /**
   * The same as `andThenEither`, but merges the output.
   */
  def andThen[Env1 <: Env, In1 <: In, Out2 >: Out](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, Out2] =
    (self andThenEither that).map(_.merge)

  /**
   * Returns a new schedule that first executes this schedule to completion, and then executes the
   * specified schedule to completion.
   */
  def andThenEither[Env1 <: Env, In1 <: In, Out2](
    that: Schedule[Env1, In1, Out2]
  ): Schedule[Env1, In1, Either[Out, Out2]] = {
    def loop(
      self: StepFunction[Env, In, Out],
      that: StepFunction[Env1, In1, Out2],
      onLeft: Boolean
    ): StepFunction[Env1, In1, Either[Out, Out2]] =
      (now: OffsetDateTime, in: In1) =>
        if (onLeft) self(now, in).flatMap {
          case Continue(out, interval, next) => ZIO.succeed(Continue(Left(out), interval, loop(next, that, true)))
          case Done(_)                       => loop(self, that, false)(now, in)
        }
        else
          that(now, in).map {
            case Done(r)                       => Done(Right(r))
            case Continue(out, interval, next) => Continue(Right(out), interval, loop(self, next, false))
          }

    Schedule(loop(self.step, that.step, true))
  }

  /**
   * Returns a new schedule that maps this schedule to a constant output.
   */
  def as[Out2](out2: => Out2): Schedule[Env, In, Out2] = self.map(_ => out2)

  /**
   * Returns a new schedule that passes each input and output of this schedule to the spefcified
   * function, and then determines whether or not to continue based on the return value of the
   * function.
   */
  def check[In1 <: In](test: (In1, Out) => Boolean): Schedule[Env, In1, Out] =
    checkM((in1, out) => ZIO.succeed(test(in1, out)))

  /**
   * Returns a new schedule that passes each input and output of this schedule to the specified
   * function, and then determines whether or not to continue based on the return value of the
   * function.
   */
  def checkM[Env1 <: Env, In1 <: In](test: (In1, Out) => URIO[Env1, Boolean]): Schedule[Env1, In1, Out] = {
    def loop(self: StepFunction[Env, In1, Out]): StepFunction[Env1, In1, Out] =
      (now: OffsetDateTime, in: In1) =>
        self(now, in).flatMap {
          case Done(out) => ZIO.succeed(Done(out))
          case Continue(out, interval, next) =>
            test(in, out).map(b => if (b) Continue(out, interval, loop(next)) else Done(out))
        }

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule that collects the outputs of this one into a chunk.
   */
  def collectAll: Schedule[Env, In, Chunk[Out]] = fold[Chunk[Out]](Chunk.empty)((xs, x) => xs :+ x)

  /**
   * A named alias for `<<<`.
   */
  def compose[Env1 <: Env, In2](that: Schedule[Env1, In2, In]): Schedule[Env1, In2, Out] = that >>> self

  /**
   * Returns a new schedule that combines this schedule with the specified schedule, merging the next
   * intervals according to the specified merge function.
   */
  @deprecated("use intersectWith", "2.0.0")
  def combineWith[Env1 <: Env, In1 <: In, Out2](
    that: Schedule[Env1, In1, Out2]
  )(f: (Interval, Interval) => Interval): Schedule[Env1, In1, (Out, Out2)] =
    intersectWith(that)(f)

  /**
   * Returns a new schedule that deals with a narrower class of inputs than this schedule.
   */
  def contramap[Env1 <: Env, In2](f: In2 => In): Schedule[Env, In2, Out] =
    Schedule((now: OffsetDateTime, in: In2) => step(now, f(in)).map(_.contramap(f)))

  /**
   * Returns a new schedule with the specified effectfully computed delay added before the start
   * of each interval produced by this schedule.
   */
  def delayed(f: Duration => Duration): Schedule[Env, In, Out] = self.delayedM(d => ZIO.succeed(f(d)))

  /**
   * Returns a new schedule with the specified effectfully computed delay added before the start
   * of each interval produced by this schedule.
   */
  def delayedM[Env1 <: Env](f: Duration => URIO[Env1, Duration]): Schedule[Env1, In, Out] =
    modifyDelayM((_, delay) => f(delay))

  /**
   * Returns a new schedule that contramaps the input and maps the output.
   */
  def dimap[In2, Out2](f: In2 => In, g: Out => Out2): Schedule[Env, In2, Out2] =
    contramap(f).map(g)

  /**
   * Returns a driver that can be used to step the schedule, appropriately handling sleeping.
   */
  def driver: UIO[Schedule.Driver[Env with Clock, In, Out]] =
    Ref.make[(Option[Out], StepFunction[Env with Clock, In, Out])]((None, step)).map { ref =>
      val next = (in: In) =>
        for {
          step <- ref.get.map(_._2)
          now  <- clock.currentDateTime.orDie
          dec  <- step(now, in)
          v <- dec match {
                 case Done(out) => ref.set((Some(out), StepFunction.done(out))) *> ZIO.fail(None)
                 case Continue(out, interval, next) =>
                   ref.set((Some(out), next)) *> ZIO.sleep(Duration.fromInterval(now, interval)) as out
               }
        } yield v

      val last = ref.get.flatMap {
        case (None, _)    => ZIO.fail(new NoSuchElementException("There is no value left"))
        case (Some(b), _) => ZIO.succeed(b)
      }

      val reset = ref.set((None, step))

      Schedule.Driver(next, last, reset)
    }

  /**
   * A named alias for `||`.
   */
  def either[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, (Out, Out2)] =
    self || that

  /**
   * The same as `either` followed by `map`.
   */
  def eitherWith[Env1 <: Env, In1 <: In, Out2, Out3](
    that: Schedule[Env1, In1, Out2]
  )(f: (Out, Out2) => Out3): Schedule[Env1, In1, Out3] =
    (self || that).map(f.tupled)

  /**
   * Returns a new schedule that will run the specified finalizer as soon as the schedule is
   * complete. Note that unlike `ZIO#ensuring`, this method does not guarantee the finalizer
   * will be run. The `Schedule` may not initialize or the driver of the schedule may not run
   * to completion. However, if the `Schedule` ever decides not to continue, then the
   * finalizer will be run.
   */
  def ensuring(finalizer: UIO[Any]): Schedule[Env, In, Out] = {
    def loop(self: StepFunction[Env, In, Out]): StepFunction[Env, In, Out] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).flatMap {
          case Done(out)                     => finalizer as Done(out)
          case Continue(out, interval, next) => ZIO.succeed(Continue(out, interval, loop(next)))
        }

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule that packs the input and output of this schedule into the first
   * element of a tuple. This allows carrying information through this schedule.
   */
  def first[X]: Schedule[Env, (In, X), (Out, X)] = self *** Schedule.identity[X]

  /**
   * Returns a new schedule that folds over the outputs of this one.
   */
  def fold[Z](z: Z)(f: (Z, Out) => Z): Schedule[Env, In, Z] = foldM(z)((z, out) => ZIO.succeed(f(z, out)))

  /**
   * Returns a new schedule that effectfully folds over the outputs of this one.
   */
  def foldM[Env1 <: Env, Z](z: Z)(f: (Z, Out) => URIO[Env1, Z]): Schedule[Env1, In, Z] = {
    def loop(z: Z, self: StepFunction[Env, In, Out]): StepFunction[Env1, In, Z] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).flatMap {
          case Done(_) => ZIO.succeed(Done(z))
          case Continue(out, interval, next) =>
            f(z, out).map(z2 => Continue(z2, interval, loop(z2, next)))
        }

    Schedule(loop(z, step))
  }

  /**
   * Returns a new schedule that loops this one continuously, resetting the state
   * when this schedule is done.
   */
  def forever: Schedule[Env, In, Out] = {
    def loop(self: StepFunction[Env, In, Out]): StepFunction[Env, In, Out] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).flatMap {
          case Done(_)                       => loop(step)(now, in)
          case Continue(out, interval, next) => ZIO.succeed(Continue(out, interval, loop(next)))
        }

    Schedule(loop(self.step))
  }

  /**
   * Returns a new schedule that combines this schedule with the specified
   * schedule, continuing as long as both schedules want to continue and
   * merging the next intervals according to the specified merge function.
   */
  def intersectWith[Env1 <: Env, In1 <: In, Out2](
    that: Schedule[Env1, In1, Out2]
  )(f: (Interval, Interval) => Interval): Schedule[Env1, In1, (Out, Out2)] = {
    def loop(
      self: StepFunction[Env, In1, Out],
      that: StepFunction[Env1, In1, Out2]
    ): StepFunction[Env1, In1, (Out, Out2)] = { (now: OffsetDateTime, in: In1) =>
      val left  = self(now, in)
      val right = that(now, in)

      (left zip right).map {
        case (Done(l), Done(r))           => Done(l -> r)
        case (Done(l), Continue(r, _, _)) => Done(l -> r)
        case (Continue(l, _, _), Done(r)) => Done(l -> r)
        case (Continue(l, linterval, lnext), Continue(r, rinterval, rnext)) =>
          val combined = f(linterval, rinterval)

          Continue(l -> r, combined, loop(lnext, rnext))
      }
    }

    Schedule(loop(self.step, that.step))
  }

  /**
   * Returns a new schedule that randomly modifies the size of the intervals of this schedule.
   */
  def jittered: Schedule[Env with Random, In, Out] = jittered(0.0, 1.0)

  /**
   * Returns a new schedule that randomly modifies the size of the intervals of this schedule.
   */
  def jittered(min: Double, max: Double): Schedule[Env with Random, In, Out] =
    delayedM[Env with Random] { duration =>
      nextDouble.map { random =>
        val d        = duration.toNanos
        val jittered = d * min * (1 - random) + d * max * random

        Duration.fromNanos(jittered.toLong)
      }
    }

  /**
   * Returns a new schedule that makes this schedule available on the `Left` side of an `Either`
   * input, allowing propagating some type `X` through this channel on demand.
   */
  def left[X]: Schedule[Env, Either[In, X], Either[Out, X]] = self +++ Schedule.identity[X]

  /**
   * Returns a new schedule that maps the output of this schedule through the specified
   * effectful function.
   */
  def map[Out2](f: Out => Out2): Schedule[Env, In, Out2] = self.mapM(out => ZIO.succeed(f(out)))

  /**
   * Returns a new schedule that maps the output of this schedule through the specified function.
   */
  def mapM[Env1 <: Env, Out2](f: Out => URIO[Env1, Out2]): Schedule[Env1, In, Out2] = {
    def loop(self: StepFunction[Env, In, Out]): StepFunction[Env1, In, Out2] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).flatMap {
          case Done(out) => f(out).map(Done(_))
          case Continue(out, interval, next) =>
            f(out).map(out2 => Continue(out2, interval, loop(next)))
        }

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule that modifies the delay using the specified
   * function.
   */
  def modifyDelay(f: (Out, Duration) => Duration): Schedule[Env, In, Out] =
    modifyDelayM((out, duration) => UIO.succeedNow(f(out, duration)))

  /**
   * Returns a new schedule that modifies the delay using the specified
   * effectual function.
   */
  def modifyDelayM[Env1 <: Env](f: (Out, Duration) => URIO[Env1, Duration]): Schedule[Env1, In, Out] = {
    def loop(self: StepFunction[Env, In, Out]): StepFunction[Env1, In, Out] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).flatMap {
          case Done(out) => ZIO.succeed(Done(out))
          case Continue(out, interval, next) =>
            val delay = Duration(interval.toInstant.toEpochMilli - now.toInstant.toEpochMilli, TimeUnit.MILLISECONDS)

            f(out, delay).map { duration =>
              val newInterval = now.plusNanos(duration.toNanos)

              Continue(out, newInterval, loop(next))
            }
        }

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule that applies the current one but runs the specified effect
   * for every decision of this schedule. This can be used to create schedules
   * that log failures, decisions, or computed values.
   */
  def onDecision[Env1 <: Env](f: Decision[Env, In, Out] => URIO[Env1, Any]): Schedule[Env1, In, Out] = {
    def loop(self: StepFunction[Env, In, Out]): StepFunction[Env1, In, Out] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).flatMap {
          case Done(out)                     => f(Done(out)) as Done(out)
          case Continue(out, interval, next) => f(Continue(out, interval, next)) as Continue(out, interval, loop(next))
        }

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule with its environment provided to it, so the resulting
   * schedule does not require any environment.
   */
  def provide(env: Env): Schedule[Any, In, Out] = {
    def loop(self: StepFunction[Env, In, Out]): StepFunction[Any, In, Out] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).map {
          case Done(out)                     => Done(out)
          case Continue(out, interval, next) => Continue(out, interval, loop(next))
        }.provide(env)

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule with part of its environment provided to it, so the
   * resulting schedule does not require any environment.
   */
  def provideSome[Env2](f: Env2 => Env): Schedule[Env2, In, Out] = {
    def loop(self: StepFunction[Env, In, Out]): StepFunction[Env2, In, Out] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).map {
          case Done(out)                     => Done(out)
          case Continue(out, interval, next) => Continue(out, interval, loop(next))
        }.provideSome(f)

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule that reconsiders every decision made by this schedule, possibly
   * modifying the next interval and the output type in the process.
   */
  def reconsider[Out2](f: Decision[Env, In, Out] => Either[Out2, (Out2, Interval)]): Schedule[Env, In, Out2] =
    reconsiderM(d => ZIO.succeed(f(d)))

  /**
   * Returns a new schedule that effectfully reconsiders every decision made by this schedule,
   * possibly modifying the next interval and the output type in the process.
   */
  def reconsiderM[Env1 <: Env, In1 <: In, Out2](
    f: Decision[Env, In, Out] => URIO[Env1, Either[Out2, (Out2, Interval)]]
  ): Schedule[Env1, In1, Out2] = {
    def loop(self: StepFunction[Env, In, Out]): StepFunction[Env1, In1, Out2] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).flatMap {
          case d @ Done(_) =>
            f(d).map {
              case Left(out2)       => Done(out2)
              case Right((out2, _)) => Done(out2)
            }
          case d @ Continue(_, _, next) =>
            f(d).map {
              case Left(out2)              => Done(out2)
              case Right((out2, interval)) => Continue(out2, interval, loop(next))
            }
        }

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule that outputs the number of repetitions of this one.
   */
  def repetitions: Schedule[Env, In, Int] =
    fold(0)((n: Int, _: Out) => n + 1)

  /**
   * Return a new schedule that automatically resets the schedule to its initial state
   * after some time of inactivity defined by `duration`.
   */
  final def resetAfter(duration: Duration): Schedule[Env, In, Out] =
    (self zip Schedule.elapsed).resetWhen(_._2 >= duration).map(_._1)

  /**
   * Resets the schedule when the specified predicate on the schedule output evaluates to true.
   */
  final def resetWhen(f: Out => Boolean): Schedule[Env, In, Out] = {
    def loop(step: StepFunction[Env, In, Out]): StepFunction[Env, In, Out] =
      (now: OffsetDateTime, in: In) =>
        step(now, in).flatMap {
          case Done(out) => if (f(out)) self.step(now, in) else ZIO.succeed(Done(out))
          case Continue(out, interval, next) =>
            if (f(out)) self.step(now, in) else ZIO.succeed(Continue(out, interval, loop(next)))
        }

    Schedule(loop(self.step))
  }

  /**
   * Returns a new schedule that makes this schedule available on the `Right` side of an `Either`
   * input, allowing propagating some type `X` through this channel on demand.
   */
  def right[X]: Schedule[Env, Either[X, In], Either[X, Out]] = Schedule.identity[X] +++ self

  /**
   * Runs a schedule using the provided inputs, and collects all outputs.
   */
  def run(now: OffsetDateTime, input: Iterable[In]): URIO[Env, Chunk[Out]] = {
    def loop(
      now: OffsetDateTime,
      xs: List[In],
      self: StepFunction[Env, In, Out],
      acc: Chunk[Out]
    ): URIO[Env, Chunk[Out]] =
      xs match {
        case Nil => ZIO.succeedNow(acc)
        case in :: xs =>
          self(now, in).flatMap {
            case Done(out)                     => ZIO.succeed(acc :+ out)
            case Continue(out, interval, next) => loop(interval, xs, next, acc :+ out)
          }
      }

    loop(now, input.toList, self.step, Chunk.empty)
  }

  /**
   * Returns a new schedule that packs the input and output of this schedule into the second
   * element of a tuple. This allows carrying information through this schedule.
   */
  def second[X]: Schedule[Env, (X, In), (X, Out)] = Schedule.identity[X] *** self

  /**
   * Returns a new schedule that effectfully processes every input to this schedule.
   */
  def tapInput[Env1 <: Env, In1 <: In](f: In1 => URIO[Env1, Any]): Schedule[Env1, In1, Out] = {
    def loop(self: StepFunction[Env, In1, Out]): StepFunction[Env1, In1, Out] =
      (now: OffsetDateTime, in: In1) =>
        f(in) *> self(now, in).map {
          case Done(out)                     => Done(out)
          case Continue(out, interval, next) => Continue(out, interval, loop(next))
        }

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule that effectfully processes every output from this schedule.
   */
  def tapOutput[Env1 <: Env](f: Out => URIO[Env1, Any]): Schedule[Env1, In, Out] = {
    def loop(self: StepFunction[Env, In, Out]): StepFunction[Env1, In, Out] =
      (now: OffsetDateTime, in: In) =>
        self(now, in).flatMap {
          case Done(out)                     => f(out) as Done(out)
          case Continue(out, interval, next) => f(out) as Continue(out, interval, loop(next))
        }

    Schedule(loop(step))
  }

  /**
   * Returns a new schedule that combines this schedule with the specified
   * schedule, continuing as long as either schedule wants to continue and
   * merging the next intervals according to the specified merge function.
   */
  def unionWith[Env1 <: Env, In1 <: In, Out2](
    that: Schedule[Env1, In1, Out2]
  )(f: (Interval, Interval) => Interval): Schedule[Env1, In1, (Out, Out2)] = {
    def loop(
      self: StepFunction[Env, In1, Out],
      that: StepFunction[Env1, In1, Out2]
    ): StepFunction[Env1, In1, (Out, Out2)] = { (now: OffsetDateTime, in: In1) =>
      val left  = self(now, in)
      val right = that(now, in)

      (left zip right).map {
        case (Done(l), Done(r)) => Done(l -> r)
        case (Done(l), Continue(r, rinterval, rnext)) =>
          Continue(l -> r, rinterval, loop(StepFunction.done(l), rnext))
        case (Continue(l, linterval, lnext), Done(r)) =>
          Continue(l -> r, linterval, loop(lnext, StepFunction.done(r)))
        case (Continue(l, linterval, lnext), Continue(r, rinterval, rnext)) =>
          val combined = f(linterval, rinterval)

          Continue(l -> r, combined, loop(lnext, rnext))
      }
    }

    Schedule(loop(self.step, that.step))
  }

  /**
   * Returns a new schedule that maps the output of this schedule to unit.
   */
  def unit: Schedule[Env, In, Unit] = self.as(())

  /**
   * Returns a new schedule that continues until the specified predicate on the input evaluates
   * to true.
   */
  def untilInput[In1 <: In](f: In1 => Boolean): Schedule[Env, In1, Out] = check((in, _) => !f(in))

  /**
   * Returns a new schedule that continues until the specified effectful predicate on the input
   * evaluates to true.
   */
  def untilInputM[Env1 <: Env, In1 <: In](f: In1 => URIO[Env1, Boolean]): Schedule[Env1, In1, Out] =
    checkM((in, _) => f(in).map(b => !b))

  /**
   * Returns a new schedule that continues until the specified predicate on the output evaluates
   * to true.
   */
  def untilOutput(f: Out => Boolean): Schedule[Env, In, Out] = check((_, out) => !f(out))

  /**
   * Returns a new schedule that continues until the specified effectful predicate on the output
   * evaluates to true.
   */
  def untilOutputM[Env1 <: Env](f: Out => URIO[Env1, Boolean]): Schedule[Env1, In, Out] =
    checkM((_, out) => f(out).map(b => !b))

  /**
   * Returns a new schedule that continues for as long the specified predicate on the input
   * evaluates to true.
   */
  def whileInput[In1 <: In](f: In1 => Boolean): Schedule[Env, In1, Out] =
    check((in, _) => f(in))

  /**
   * Returns a new schedule that continues for as long the specified effectful predicate on the
   * input evaluates to true.
   */
  def whileInputM[Env1 <: Env, In1 <: In](f: In1 => URIO[Env1, Boolean]): Schedule[Env1, In1, Out] =
    checkM((in, _) => f(in))

  /**
   * Returns a new schedule that continues for as long the specified predicate on the output
   * evaluates to true.
   */
  def whileOutput(f: Out => Boolean): Schedule[Env, In, Out] = check((_, out) => f(out))

  /**
   * Returns a new schedule that continues for as long the specified effectful predicate on the
   * output evaluates to true.
   */
  def whileOutputM[Env1 <: Env](f: Out => URIO[Env1, Boolean]): Schedule[Env1, In, Out] =
    checkM((_, out) => f(out))

  /**
   * A named method for `&&`.
   */
  def zip[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, (Out, Out2)] =
    self && that

  /**
   * The same as `&&`, but ignores the right output.
   */
  def zipLeft[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, Out] = self <* that

  /**
   * The same as `&&`, but ignores the left output.
   */
  def zipRight[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2]): Schedule[Env1, In1, Out2] =
    self *> that

  /**
   * Equivalent to `zip` followed by `map`.
   */
  def zipWith[Env1 <: Env, In1 <: In, Out2, Out3](
    that: Schedule[Env1, In1, Out2]
  )(f: (Out, Out2) => Out3): Schedule[Env1, In1, Out3] =
    (self zip that).map(f.tupled)
}
object Schedule {

  /**
   * Constructs a new schedule from the specified step function.
   */
  def apply[Env, In, Out](step: StepFunction[Env, In, Out]): Schedule[Env, In, Out] =
    new Schedule(step) {}

  /**
   * A schedule that recurs anywhere, collecting all inputs into a list.
   */
  def collectAll[A]: Schedule[Any, A, Chunk[A]] =
    identity[A].collectAll

  /**
   * A schedule that recurs as long as the condition f holds, collecting all inputs into a list.
   */
  def collectWhile[A](f: A => Boolean): Schedule[Any, A, Chunk[A]] =
    recurWhile(f).collectAll

  /**
   * A schedule that recurs as long as the effectful condition holds,
   * collecting all inputs into a list.
   */
  def collectWhileM[Env, A](f: A => URIO[Env, Boolean]): Schedule[Env, A, Chunk[A]] =
    recurWhileM(f).collectAll

  /**
   * A schedule that recurs until the condition f fails, collecting all inputs into a list.
   */
  def collectUntil[A](f: A => Boolean): Schedule[Any, A, Chunk[A]] =
    recurUntil(f).collectAll

  /**
   * A schedule that recurs until the effectful condition f fails, collecting
   * all inputs into a list.
   */
  def collectUntilM[Env, A](f: A => URIO[Env, Boolean]): Schedule[Env, A, Chunk[A]] =
    recurUntilM(f).collectAll

  /**
   * Takes a schedule that produces a delay, and returns a new schedule that uses this delay to
   * further delay intervals in the resulting schedule.
   */
  def delayed[Env, In, Out](schedule: Schedule[Env, In, Duration]): Schedule[Env, In, Duration] =
    schedule.addDelay(x => x)

  /**
   * A schedule that recurs for as long as the predicate evaluates to true.
   */
  def recurWhile[A](f: A => Boolean): Schedule[Any, A, A] =
    identity[A].whileInput(f)

  /**
   * A schedule that recurs for as long as the effectful predicate evaluates to true.
   */
  def recurWhileM[Env, A](f: A => URIO[Env, Boolean]): Schedule[Env, A, A] =
    identity[A].whileInputM(f)

  /**
   * A schedule that recurs for as long as the predicate is equal.
   */
  def recurWhileEquals[A](a: => A): Schedule[Any, A, A] =
    identity[A].whileInput(_ == a)

  /**
   * A schedule that recurs for until the predicate evaluates to true.
   */
  def recurUntil[A](f: A => Boolean): Schedule[Any, A, A] =
    identity[A].untilInput(f)

  /**
   * A schedule that recurs for until the predicate evaluates to true.
   */
  def recurUntilM[Env, A](f: A => URIO[Env, Boolean]): Schedule[Env, A, A] =
    identity[A].untilInputM(f)

  /**
   * A schedule that recurs for until the predicate is equal.
   */
  def recurUntilEquals[A](a: => A): Schedule[Any, A, A] =
    identity[A].untilInput(_ == a)

  /**
   * A schedule that recurs for until the input value becomes applicable to partial function
   * and then map that value with given function.
   */
  def recurUntil[A, B](pf: PartialFunction[A, B]): Schedule[Any, A, Option[B]] =
    identity[A].map(pf.lift(_)).untilOutput(_.isDefined)

  /**
   * A schedule that can recur one time, the specified amount of time into the future.
   */
  def duration(duration: Duration): Schedule[Any, Any, Duration] =
    Schedule((now, _: Any) =>
      ZIO.succeed {
        Decision.Continue(Duration.Zero, now.plusNanos(duration.toNanos), StepFunction.done(duration))
      }
    )

  /**
   * A schedule that occurs everywhere, which returns the total elapsed duration since the
   * first step.
   */
  val elapsed: Schedule[Any, Any, Duration] = {
    def loop(start: Option[OffsetDateTime]): StepFunction[Any, Any, Duration] =
      (now: OffsetDateTime, _: Any) =>
        ZIO.succeed {
          start match {
            case None => Decision.Continue(Duration.Zero, now, loop(Some(now)))
            case Some(start) =>
              val duration =
                Duration(now.toInstant.toEpochMilli() - start.toInstant.toEpochMilli(), TimeUnit.MILLISECONDS)

              Decision.Continue(duration, now, loop(Some(start)))
          }
        }

    Schedule(loop(None))
  }

  /**
   * A schedule that always recurs, but will wait a certain amount between
   * repetitions, given by `base * factor.pow(n)`, where `n` is the number of
   * repetitions so far. Returns the current duration between recurrences.
   */
  def exponential(base: Duration, factor: Double = 2.0): Schedule[Any, Any, Duration] =
    delayed(forever.map(i => base * math.pow(factor, i.doubleValue)))

  /**
   * A schedule that always recurs, increasing delays by summing the
   * preceding two delays (similar to the fibonacci sequence). Returns the
   * current duration between recurrences.
   */
  def fibonacci(one: Duration): Schedule[Any, Any, Duration] =
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
   * |-----interval-----|-----interval-----|-----interval-----|
   * |---------action--------||action|-----|action|-----------|
   * </pre>
   */
  def fixed(interval: Duration): Schedule[Any, Any, Long] = {
    import Decision._
    import java.time.Duration

    val fixedDelay  = interval.toMillis
    val fixedDelayD = Duration.ofMillis(fixedDelay)

    final case class State(startMillis: Long, lastRun: Long)

    def loop(startMillis: Option[State], n: Long): StepFunction[Any, Any, Long] =
      (now: OffsetDateTime, _: Any) =>
        ZIO.succeed(startMillis match {
          case Some(State(startMillis, lastRun)) =>
            val nowMillis     = now.toInstant.toEpochMilli()
            val runningBehind = nowMillis > (lastRun + fixedDelay)
            val boundary      = Duration.ofMillis((nowMillis - startMillis) % fixedDelay)
            val sleepTime     = if (boundary.isZero()) fixedDelayD else boundary
            val nextRun       = if (runningBehind) now else now.plus(sleepTime)

            Continue(
              n + 1L,
              nextRun,
              loop(Some(State(startMillis, nextRun.toInstant().toEpochMilli())), n + 1L)
            )
          case None =>
            val nowMillis = now.toInstant.toEpochMilli()

            Continue(
              n + 1L,
              now.plus(fixedDelayD),
              loop(Some(State(nowMillis, nowMillis)), n + 1L)
            )
        })

    Schedule(loop(None, 0L))
  }

  /**
   * A schedule that always recurs, producing a count of repeats: 0, 1, 2.
   */
  val forever: Schedule[Any, Any, Long] = unfold(0L)(_ + 1L)

  /**
   * A schedule that recurs once with the specified delay.
   */
  def fromDuration(duration: Duration): Schedule[Any, Any, Duration] =
    Schedule((now, _: Any) =>
      ZIO.succeed {
        Decision
          .Continue(Duration.Zero, now.plusNanos(duration.toNanos), StepFunction.done(duration))
      }
    )

  /**
   * A schedule that recurs once for each of the specified durations, delaying
   * each time for the length of the specified duration. Returns the length of
   * the current duration between recurrences.
   */
  def fromDurations(duration: Duration, durations: Duration*): Schedule[Any, Any, Duration] =
    durations.foldLeft(fromDuration(duration)) {
      case (acc, d) => acc ++ fromDuration(d)
    }

  /**
   * A schedule that always recurs, mapping input values through the
   * specified function.
   */
  def fromFunction[A, B](f: A => B): Schedule[Any, A, B] = identity[A].map(f)

  /**
   * A schedule that always recurs, which counts the number of recurrances.
   */
  val count: Schedule[Any, Any, Long] =
    unfold(0L)(_ + 1L)

  /**
   * A schedule that always recurs, which returns inputs as outputs.
   */
  def identity[A]: Schedule[Any, A, A] = {
    lazy val loop: StepFunction[Any, A, A] = (now: OffsetDateTime, in: A) =>
      ZIO.succeed(Decision.Continue(in, now, loop))

    Schedule(loop)
  }

  /**
   * A schedule that always recurs, but will repeat on a linear time
   * interval, given by `base * n` where `n` is the number of
   * repetitions so far. Returns the current duration between recurrences.
   */
  def linear(base: Duration): Schedule[Any, Any, Duration] =
    delayed(forever.map(i => base * (i + 1).doubleValue()))

  /**
   * A schedule that recurs one time.
   */
  val once: Schedule[Any, Any, Unit] = recurs(1).unit

  /**
   * A schedule spanning all time, which can be stepped only the specified number of times before
   * it terminates.
   */
  def recurs(n: Long): Schedule[Any, Any, Long] =
    forever.whileOutput(_ < n)

  /**
   * A schedule spanning all time, which can be stepped only the specified number of times before
   * it terminates.
   */
  def recurs(n: Int): Schedule[Any, Any, Long] = recurs(n.toLong)

  /**
   * Returns a schedule that recurs continuously, each repetition spaced the specified duration
   * from the last run.
   */
  def spaced(duration: Duration): Schedule[Any, Any, Long] =
    forever.addDelay(_ => duration)

  /**
   * A schedule that does not recur, it just stops.
   */
  val stop: Schedule[Any, Any, Unit] = recurs(0).unit

  /**
   * Returns a schedule that repeats one time, producing the specified constant value.
   */
  def succeed[A](a: => A): Schedule[Any, Any, A] =
    forever.as(a)

  /**
   * Unfolds a schedule that repeats one time from the specified state and iterator.
   */
  def unfold[A](a: => A)(f: A => A): Schedule[Any, Any, A] = {
    def loop(a: A): StepFunction[Any, Any, A] =
      (now, _) => ZIO.succeed(Decision.Continue(a, now, loop(f(a))))

    Schedule((now, _) => ZIO.effectTotal(a).map(a => Decision.Continue(a, now, loop(f(a)))))
  }

  /**
   * A schedule that divides the timeline to `interval`-long windows, and sleeps
   * until the nearest window boundary every time it recurs.
   *
   * For example, `windowed(10.seconds)` would produce a schedule as follows:
   * <pre>
   *      10s        10s        10s       10s
   * |----------|----------|----------|----------|
   * |action------|sleep---|act|-sleep|action----|
   * </pre>
   */
  def windowed(interval: Duration): Schedule[Any, Any, Long] = {
    import Decision._

    val millis = interval.toMillis

    def loop(startMillis: Option[Long], n: Long): StepFunction[Any, Any, Long] =
      (now: OffsetDateTime, _: Any) =>
        ZIO.succeed(startMillis match {
          case Some(startMillis) =>
            Continue(
              n + 1,
              now.plus(
                millis - (now.toInstant.toEpochMilli - startMillis) % millis,
                java.time.temporal.ChronoUnit.MILLIS
              ),
              loop(Some(startMillis), n + 1L)
            )
          case None =>
            Continue(
              n + 1L,
              now.plus(millis, java.time.temporal.ChronoUnit.MILLIS),
              loop(Some(now.toInstant.toEpochMilli), n + 1)
            )
        })

    Schedule(loop(None, 0L))
  }

  type Interval = java.time.OffsetDateTime

  def minOffsetDateTime(l: OffsetDateTime, r: OffsetDateTime): OffsetDateTime =
    if (l.compareTo(r) <= 0) l else r

  def maxOffsetDateTime(l: OffsetDateTime, r: OffsetDateTime): OffsetDateTime =
    if (l.compareTo(r) >= 0) l else r

  final case class Driver[-Env, -In, +Out](
    next: In => ZIO[Env, None.type, Out],
    last: IO[NoSuchElementException, Out],
    reset: UIO[Unit]
  )

  type StepFunction[-Env, -In, +Out] = (OffsetDateTime, In) => ZIO[Env, Nothing, Schedule.Decision[Env, In, Out]]
  object StepFunction {
    def done[A](a: => A): StepFunction[Any, Any, A] = (_: OffsetDateTime, _: Any) => ZIO.succeed(Decision.Done(a))
  }

  sealed trait Decision[-Env, -In, +Out] { self =>
    def out: Out

    final def as[Out2](out2: => Out2): Decision[Env, In, Out2] = map(_ => out2)

    final def contramap[In1](f: In1 => In): Decision[Env, In1, Out] =
      self match {
        case Decision.Done(v) => Decision.Done(v)
        case Decision.Continue(v, i, n) =>
          Decision.Continue(v, i, (now: OffsetDateTime, in1: In1) => n(now, f(in1)).map(_.contramap(f)))
      }

    final def map[Out2](f: Out => Out2): Decision[Env, In, Out2] =
      self match {
        case Decision.Done(v) => Decision.Done(f(v))
        case Decision.Continue(v, i, n) =>
          Decision.Continue(f(v), i, (now: OffsetDateTime, in: In) => n(now, in).map(_.map(f)))
      }

    final def toDone: Decision[Env, Any, Out] =
      self match {
        case Decision.Done(v)           => Decision.Done(v)
        case Decision.Continue(v, _, _) => Decision.Done(v)
      }
  }
  object Decision {
    final case class Done[-Env, +Out](out: Out) extends Decision[Env, Any, Out]
    final case class Continue[-Env, -In, +Out](
      out: Out,
      interval: Interval,
      next: StepFunction[Env, In, Out]
    ) extends Decision[Env, In, Out]
  }
}
