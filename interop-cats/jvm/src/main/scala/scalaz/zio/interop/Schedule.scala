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

package scalaz.zio.interop

import cats.effect.ConcurrentEffect
import scalaz.zio.ZSchedule.Decision
import scalaz.zio.clock.Clock
import scalaz.zio.{ TaskR, ZSchedule }
import scalaz.zio.duration.{ Duration => ZDuration }
import scalaz.zio.interop.Schedule.Env
import scalaz.zio.random.Random
import scalaz.zio.Runtime

import scala.concurrent.duration.Duration

/**
 * @see [[scalaz.zio.ZSchedule]]
 */
final class Schedule[F[+ _], -A, +B] private[interop] (private[interop] val underlying: ZSchedule[Env, A, B]) { self =>
  import Schedule.{ fromEffect, toEffect, Env }

  /**
   * @see [[scalaz.zio.ZSchedule.State]]
   */
  type State = underlying.State

  /**
   * @see [[scalaz.zio.ZSchedule.initial]]
   */
  def initial(implicit R: Runtime[Env], F: ConcurrentEffect[F]): F[State] = toEffect(underlying.initial)

  /**
   * @see [[scalaz.zio.ZSchedule.update]]
   */
  def update(implicit R: Runtime[Env], F: ConcurrentEffect[F]): (A, State) => F[ZSchedule.Decision[State, B]] =
    (a, s) => toEffect(underlying.update(a, s))

  /**
   * @see [[scalaz.zio.ZSchedule.run]]
   */
  final def run(as: Iterable[A])(implicit R: Runtime[Env], F: ConcurrentEffect[F]): F[List[(Duration, B)]] =
    toEffect(underlying.run(as).map(_.map { case (d, b) => (d.asScala, b) }))

  /**
   * @see [[scalaz.zio.ZSchedule.unary_!]]
   */
  final def unary_! : Schedule[F, A, B] =
    new Schedule(!underlying)

  /**
   * @see [[scalaz.zio.ZSchedule.map]]
   */
  final def map[A1 <: A, C](f: B => C): Schedule[F, A1, C] =
    new Schedule(underlying.map(f))

  /**
   * @see [[scalaz.zio.ZSchedule.contramap]]
   */
  final def contramap[A1](f: A1 => A): Schedule[F, A1, B] =
    new Schedule(underlying.contramap(f))

  /**
   * @see [[scalaz.zio.ZSchedule.dimap]]
   */
  final def dimap[A1, C](f: A1 => A, g: B => C): Schedule[F, A1, C] =
    new Schedule(underlying.dimap(f, g))

  /**
   * @see [[scalaz.zio.ZSchedule.forever]]
   */
  final def forever: Schedule[F, A, B] =
    new Schedule(underlying.forever)

  /**
   * @see [[scalaz.zio.ZSchedule.check]]
   */
  final def check[A1 <: A](
    test: (A1, B) => F[Boolean]
  )(implicit R: Runtime[Any], F: ConcurrentEffect[F]): Schedule[F, A1, B] =
    new Schedule(underlying.check((a1, b) => fromEffect(test(a1, b)).orDie))

  /**
   * @see [[scalaz.zio.ZSchedule.ensuring]]
   */
  final def ensuring(finalizer: F[_])(implicit R: Runtime[Any], F: ConcurrentEffect[F]): Schedule[F, A, B] =
    new Schedule(underlying.ensuring(fromEffect(finalizer).orDie))

  /**
   * @see [[scalaz.zio.ZSchedule.whileOutput]]
   */
  final def whileOutput(f: B => Boolean): Schedule[F, A, B] =
    new Schedule(underlying.whileOutput(f))

  /**
   * @see [[scalaz.zio.ZSchedule.whileInput]]
   */
  final def whileInput[A1 <: A](f: A1 => Boolean): Schedule[F, A1, B] =
    new Schedule(underlying.whileInput(f))

  /**
   * @see [[scalaz.zio.ZSchedule.untilOutput]]
   */
  final def untilOutput(f: B => Boolean): Schedule[F, A, B] =
    new Schedule(underlying.untilOutput(f))

  /**
   * @see [[scalaz.zio.ZSchedule.untilInput]]
   */
  final def untilInput[A1 <: A](f: A1 => Boolean): Schedule[F, A1, B] =
    new Schedule(underlying.untilInput(f))

  /**
   * @see [[scalaz.zio.ZSchedule.combineWith]]
   */
  final def combineWith[A1 <: A, C](
    that: Schedule[F, A1, C]
  )(g: (Boolean, Boolean) => Boolean, f: (Duration, Duration) => Duration): Schedule[F, A1, (B, C)] = {
    val fz: (ZDuration, ZDuration) => ZDuration = (d1, d2) => ZDuration.fromScala(f(d1.asScala, d2.asScala))
    new Schedule(underlying.combineWith(that.underlying)(g, fz))
  }

  /**
   * @see [[scalaz.zio.ZSchedule.&&]]
   */
  final def &&[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, (B, C)] =
    combineWith(that)(_ && _, _ max _)

  /**
   * @see [[scalaz.zio.ZSchedule.both]]
   */
  final def both[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, (B, C)] = self && that

  /**
   * @see [[scalaz.zio.ZSchedule.bothWith]]
   */
  final def bothWith[A1 <: A, C, D](
    that: Schedule[F, A1, C]
  )(f: (B, C) => D): Schedule[F, A1, D] =
    new Schedule(underlying.bothWith(that.underlying)(f))

  /**
   * @see [[scalaz.zio.ZSchedule.*>]]
   */
  final def *>[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, C] =
    new Schedule(self.underlying *> that.underlying)

  /**
   * @see [[scalaz.zio.ZSchedule.zipRight]]
   */
  final def zipRight[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, C] =
    self *> that

  /**
   * @see [[scalaz.zio.ZSchedule.<*]]
   */
  final def <*[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, B] =
    new Schedule(self.underlying <* that.underlying)

  /**
   * @see [[scalaz.zio.ZSchedule.zipLeft]]
   */
  final def zipLeft[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, B] =
    self <* that

  /**
   * @see [[scalaz.zio.ZSchedule.<*>]]
   */
  final def <*>[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, (B, C)] = self zip that

  /**
   * @see [[scalaz.zio.ZSchedule.zip]]
   */
  final def zip[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, (B, C)] = self && that

  /**
   * @see [[scalaz.zio.ZSchedule.||]]
   */
  final def ||[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, (B, C)] =
    combineWith(that)(_ || _, _ min _)

  /**
   * @see [[scalaz.zio.ZSchedule.either]]
   */
  final def either[A1 <: A, C](that: Schedule[F, A1, C]): Schedule[F, A1, (B, C)] =
    self || that

  /**
   * @see [[scalaz.zio.ZSchedule.eitherWith]]
   */
  final def eitherWith[A1 <: A, C, D](
    that: Schedule[F, A1, C]
  )(f: (B, C) => D): Schedule[F, A1, D] =
    new Schedule(underlying.eitherWith(that.underlying)(f))

  /**
   * @see [[scalaz.zio.ZSchedule.andThenEither]]
   */
  final def andThenEither[A1 <: A, C](
    that: Schedule[F, A1, C]
  ): Schedule[F, A1, Either[B, C]] =
    new Schedule(underlying.andThenEither(that.underlying))

  /**
   * @see [[scalaz.zio.ZSchedule.andThen]]
   */
  final def andThen[A1 <: A, B1 >: B](that: Schedule[F, A1, B1]): Schedule[F, A1, B1] =
    new Schedule(underlying.andThen(that.underlying))

  /**
   * @see [[scalaz.zio.ZSchedule.const]]
   */
  final def const[C](c: => C): Schedule[F, A, C] = map(_ => c)

  /**
   * @see [[scalaz.zio.ZSchedule.unit]]
   */
  final def unit: Schedule[F, A, Unit] = const(())

  /**
   * @see [[scalaz.zio.ZSchedule.reconsiderM]]
   */
  final def reconsiderM[A1 <: A, C](
    f: (A1, ZSchedule.Decision[State, B]) => F[ZSchedule.Decision[State, C]]
  )(implicit R: Runtime[Any], F: ConcurrentEffect[F]): Schedule[F, A1, C] =
    new Schedule(underlying.reconsiderM((a, d) => fromEffect(f(a, d)).orDie))

  /**
   * @see [[scalaz.zio.ZSchedule.reconsider]]
   */
  final def reconsider[A1 <: A, C](
    f: (A1, ZSchedule.Decision[State, B]) => ZSchedule.Decision[State, C]
  )(implicit R: Runtime[Env], F: ConcurrentEffect[F]): Schedule[F, A1, C] =
    reconsiderM((a, d) => F.pure(f(a, d)))

  /**
   * @see [[scalaz.zio.ZSchedule.onDecision]]
   */
  final def onDecision[A1 <: A](
    f: (A1, ZSchedule.Decision[State, B]) => F[Unit]
  )(implicit R: Runtime[Any], F: ConcurrentEffect[F]): Schedule[F, A1, B] =
    new Schedule(underlying.onDecision((a, d) => fromEffect(f(a, d)).orDie))

  /**
   * @see [[scalaz.zio.ZSchedule.modifyDelay]]
   */
  final def modifyDelay(
    f: (B, Duration) => F[Duration]
  )(implicit R: Runtime[Env], F: ConcurrentEffect[F]): Schedule[F, A, B] =
    new Schedule(underlying.modifyDelay((b, d) => fromEffect(f(b, d.asScala)).map(ZDuration.fromScala).orDie))

  /**
   * @see [[scalaz.zio.ZSchedule.updated]]
   */
  final def updated[A1 <: A, B1](
    f: (
      (A, State) => F[ZSchedule.Decision[State, B]]
    ) => (A1, State) => F[ZSchedule.Decision[State, B1]]
  )(implicit R: Runtime[Env], F: ConcurrentEffect[F]): Schedule[F, A1, B1] =
    Schedule(self.initial, f(self.update))

  /**
   * @see [[scalaz.zio.ZSchedule.initialized]]
   */
  final def initialized[A1 <: A](
    f: F[State] => F[State]
  )(implicit R: Runtime[Env], F: ConcurrentEffect[F]): Schedule[F, A1, B] =
    Schedule(f(self.initial), self.update)

  /**
   * @see [[scalaz.zio.ZSchedule.delayed]]
   */
  final def delayed(f: Duration => Duration)(implicit R: Runtime[Env], F: ConcurrentEffect[F]): Schedule[F, A, B] =
    modifyDelay((_, d) => F.pure(f(d)))

  /**
   * @see [[scalaz.zio.ZSchedule.jittered]]
   */
  final def jittered: Schedule[F, A, B] = jittered(0.0, 1.0)

  /**
   * @see [[scalaz.zio.ZSchedule.jittered]]
   */
  final def jittered(min: Double, max: Double): Schedule[F, A, B] =
    new Schedule(underlying.jittered(min, max))

  /**
   * @see [[scalaz.zio.ZSchedule.logInput]]
   */
  final def logInput[A1 <: A](f: A1 => F[Unit])(implicit R: Runtime[Env], F: ConcurrentEffect[F]): Schedule[F, A1, B] =
    new Schedule(underlying.logInput(a1 => fromEffect(f(a1)).orDie))

  /**
   * @see [[scalaz.zio.ZSchedule.logOutput]]
   */
  final def logOutput(f: B => F[Unit])(implicit R: Runtime[Env], F: ConcurrentEffect[F]): Schedule[F, A, B] =
    new Schedule(underlying.logOutput(a1 => fromEffect(f(a1)).orDie))

  /**
   * @see [[scalaz.zio.ZSchedule.collect]]
   */
  final def collect: Schedule[F, A, List[B]] =
    new Schedule(underlying.collect)

  /**
   * @see [[scalaz.zio.ZSchedule.fold]]
   */
  final def fold[Z](z: Z)(f: (Z, B) => Z): Schedule[F, A, Z] =
    new Schedule(underlying.fold(z)(f))

  /**
   * @see [[scalaz.zio.ZSchedule.foldM]]
   */
  final def foldM[Z](z: F[Z])(f: (Z, B) => F[Z])(implicit R: Runtime[Any], F: ConcurrentEffect[F]): Schedule[F, A, Z] =
    new Schedule(underlying.foldM(fromEffect(z).orDie)((z, b) => fromEffect(f(z, b)).orDie))

  /**
   * @see [[scalaz.zio.ZSchedule.>>>]]
   */
  final def >>>[C](that: Schedule[F, B, C]): Schedule[F, A, C] =
    new Schedule(underlying >>> that.underlying)

  /**
   * @see [[scalaz.zio.ZSchedule.<<<]]
   */
  final def <<<[C](that: Schedule[F, C, A]): Schedule[F, C, B] = that >>> self

  /**
   * @see [[scalaz.zio.ZSchedule.compose]]
   */
  final def compose[C](that: Schedule[F, C, A]): Schedule[F, C, B] = self <<< that

  /**
   * @see [[scalaz.zio.ZSchedule.first]]
   */
  final def first[C](implicit F: ConcurrentEffect[F]): Schedule[F, (A, C), (B, C)] = self *** Schedule.identity[F, C]

  /**
   * @see [[scalaz.zio.ZSchedule.second]]
   */
  final def second[C](implicit F: ConcurrentEffect[F]): Schedule[F, (C, A), (C, B)] = Schedule.identity[F, C] *** self

  /**
   * @see [[scalaz.zio.ZSchedule.left]]
   */
  final def left[C](implicit F: ConcurrentEffect[F]): Schedule[F, Either[A, C], Either[B, C]] =
    self +++ Schedule.identity[F, C]

  /**
   * @see [[scalaz.zio.ZSchedule.right]]
   */
  final def right[C](implicit F: ConcurrentEffect[F]): Schedule[F, Either[C, A], Either[C, B]] =
    Schedule.identity[F, C] +++ self

  /**
   * @see [[scalaz.zio.ZSchedule.***]]
   */
  final def ***[C, D](that: Schedule[F, C, D]): Schedule[F, (A, C), (B, D)] =
    new Schedule(underlying *** that.underlying)

  /**
   * @see [[scalaz.zio.ZSchedule.|||]]
   */
  final def |||[B1 >: B, C](that: Schedule[F, C, B1]): Schedule[F, Either[A, C], B1] =
    (self +++ that).map(_.merge)

  /**
   * @see [[scalaz.zio.ZSchedule.+++]]
   */
  final def +++[C, D](that: Schedule[F, C, D]): Schedule[F, Either[A, C], Either[B, D]] =
    new Schedule(underlying +++ that.underlying)
}

object Schedule {
  import scalaz.zio.interop.catz._
  private[interop] type Env = Random with Clock

  private[interop] def toEffect[F[+ _], R, A](zio: TaskR[R, A])(implicit R: Runtime[R], F: ConcurrentEffect[F]): F[A] =
    F.liftIO(taskEffectInstances.toIO(zio))

  private[interop] def fromEffect[F[+ _], R, A](
    eff: F[A]
  )(implicit R: Runtime[R], F: ConcurrentEffect[F]): TaskR[R, A] =
    taskEffectInstances.liftIO[A](F.toIO(eff))

  final def apply[F[+ _]: ConcurrentEffect, S, A, B](
    initial0: F[S],
    update0: (A, S) => F[ZSchedule.Decision[S, B]]
  )(implicit R: Runtime[Env]): Schedule[F, A, B] =
    new Schedule(new ZSchedule[Env, A, B] {
      type State = S
      val initial = fromEffect(initial0).orDie
      val update  = (a: A, s: State) => fromEffect(update0(a, s)).orDie
    })

  /**
   * @see [[scalaz.zio.ZSchedule.identity]]
   */
  def identity[F[+ _]: ConcurrentEffect, A]: Schedule[F, A, A] =
    new Schedule(ZSchedule.identity[A])

  /**
   * @see [[scalaz.zio.ZSchedule.succeed]]
   */
  final def succeed[F[+ _]: ConcurrentEffect, A](a: A): Schedule[F, Any, A] =
    new Schedule(ZSchedule.succeed(a))

  /**
   * @see [[scalaz.zio.ZSchedule.succeedLazy]]
   */
  final def succeedLazy[F[+ _]: ConcurrentEffect, A](a: => A): Schedule[F, Any, A] =
    new Schedule(ZSchedule.succeedLazy(a))

  /**
   * @see [[scalaz.zio.ZSchedule.fromFunction]]
   */
  final def fromFunction[F[+ _]: ConcurrentEffect, A, B](f: A => B): Schedule[F, A, B] =
    new Schedule(ZSchedule.fromFunction(f))

  /**
   * @see [[scalaz.zio.ZSchedule.never]]
   */
  final def never[F[+ _]: ConcurrentEffect]: Schedule[F, Any, Nothing] =
    new Schedule(ZSchedule.never)

  /**
   * @see [[scalaz.zio.ZSchedule.forever]]
   */
  final def forever[F[+ _]: ConcurrentEffect]: Schedule[F, Any, Int] =
    new Schedule(ZSchedule.forever)

  /**
   * @see [[scalaz.zio.ZSchedule.once]]
   */
  final def once[F[+ _]: ConcurrentEffect]: Schedule[F, Any, Unit] =
    new Schedule(ZSchedule.once)

  /**
   * @see [[scalaz.zio.ZSchedule.delayed]]
   */
  final def delayed[F[+ _]: ConcurrentEffect, A](
    s: Schedule[F, A, Duration]
  ): Schedule[F, A, Duration] =
    new Schedule(ZSchedule.delayed(s.underlying.map(ZDuration.fromScala)).map(_.asScala))

  /**
   * @see [[scalaz.zio.ZSchedule.collect]]
   */
  final def collect[F[+ _]: ConcurrentEffect, A]: Schedule[F, A, List[A]] =
    new Schedule(ZSchedule.collect)

  /**
   * @see [[scalaz.zio.ZSchedule.doWhile]]
   */
  final def doWhile[F[+ _]: ConcurrentEffect, A](f: A => Boolean): Schedule[F, A, A] =
    new Schedule(ZSchedule.doWhile(f))

  /**
   * @see [[scalaz.zio.ZSchedule!.doUntil[A](A=>Boolean):Schedule[A, A]*]]
   */
  final def doUntil[F[+ _]: ConcurrentEffect, A](f: A => Boolean): Schedule[F, A, A] =
    new Schedule(ZSchedule.doUntil(f))

  /**
   * @see [[scalaz.zio.ZSchedule!.doUntil[A, B](PartialFunction[A,B])*]]
   */
  final def doUntil[F[+ _]: ConcurrentEffect, A, B](
    pf: PartialFunction[A, B]
  ): Schedule[F, A, Option[B]] =
    new Schedule(ZSchedule.doUntil(pf))

  /**
   * @see [[scalaz.zio.ZSchedule.logInput]]
   */
  final def logInput[F[+ _]: ConcurrentEffect, A](f: A => F[Unit])(implicit R: Runtime[Env]): Schedule[F, A, A] =
    identity[F, A].logInput(f)

  /**
   * @see [[scalaz.zio.ZSchedule.recurs]]
   */
  final def recurs[F[+ _]: ConcurrentEffect](n: Int): Schedule[F, Any, Int] =
    new Schedule(ZSchedule.recurs(n))

  /**
   * @see [[scalaz.zio.ZSchedule.delay]]
   */
  final def delay[F[+ _]: ConcurrentEffect]: Schedule[F, Any, Duration] =
    new Schedule(ZSchedule.delay.map(_.asScala))

  /**
   * @see [[scalaz.zio.ZSchedule.decision]]
   */
  final def decision[F[+ _]: ConcurrentEffect]: Schedule[F, Any, Boolean] =
    new Schedule(ZSchedule.decision)

  /**
   * @see [[scalaz.zio.ZSchedule.unfold]]
   */
  final def unfold[F[+ _]: ConcurrentEffect, A](a: => A)(f: A => A): Schedule[F, Any, A] =
    new Schedule(ZSchedule.unfold(a)(f))

  /**
   * @see [[scalaz.zio.ZSchedule.unfoldM]]
   */
  final def unfoldM[F[+ _]: ConcurrentEffect, A](a: F[A])(f: A => F[A])(implicit R: Runtime[Env]): Schedule[F, Any, A] =
    Schedule[F, A, Any, A](
      a,
      (_: Any, a: A) => ConcurrentEffect[F].map(f(a))(a => Decision.cont(ZDuration.Zero, a, a))
    )

  /**
   * @see [[scalaz.zio.ZSchedule.spaced]]
   */
  final def spaced[F[+ _]: ConcurrentEffect](interval: Duration): Schedule[F, Any, Int] =
    new Schedule(ZSchedule.spaced(ZDuration.fromScala(interval)))

  /**
   * @see [[scalaz.zio.ZSchedule.fibonacci]]
   */
  final def fibonacci[F[+ _]: ConcurrentEffect](one: Duration): Schedule[F, Any, Duration] =
    new Schedule(ZSchedule.fibonacci(ZDuration.fromScala(one)).map(_.asScala))

  /**
   * @see [[scalaz.zio.ZSchedule.linear]]
   */
  final def linear[F[+ _]: ConcurrentEffect](base: Duration): Schedule[F, Any, Duration] =
    new Schedule(ZSchedule.linear(ZDuration.fromScala(base)).map(_.asScala))

  /**
   * @see [[scalaz.zio.ZSchedule.exponential]]
   */
  final def exponential[F[+ _]: ConcurrentEffect](base: Duration, factor: Double = 2.0): Schedule[F, Any, Duration] =
    new Schedule(ZSchedule.exponential(ZDuration.fromScala(base), factor).map(_.asScala))

  /**
   * @see [[scalaz.zio.ZSchedule.elapsed]]
   */
  final def elapsed[F[+ _]: ConcurrentEffect]: Schedule[F, Any, Duration] =
    new Schedule(ZSchedule.elapsed.map(_.asScala))

  /**
   * @see [[scalaz.zio.ZSchedule.duration]]
   */
  final def duration[F[+ _]: ConcurrentEffect](duration: Duration): Schedule[F, Any, Duration] =
    new Schedule(ZSchedule.duration(ZDuration.fromScala(duration)).map(_.asScala))

  /**
   * @see [[scalaz.zio.ZSchedule.fixed]]
   */
  final def fixed[F[+ _]: ConcurrentEffect](interval: Duration): Schedule[F, Any, Int] =
    new Schedule(ZSchedule.fixed(ZDuration.fromScala(interval)))

}
