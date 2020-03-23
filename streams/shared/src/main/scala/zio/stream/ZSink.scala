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

package zio.stream

import scala.collection.mutable

import zio._
import zio.clock.Clock
import zio.duration.Duration

/**
 * A `Sink[E, A0, A, B]` consumes values of type `A`, ultimately producing
 * either an error of type `E`, or a value of type `B` together with a remainder
 * of type `A0`.
 *
 * Sinks form monads and combine in the usual ways.
 */
trait ZSink[-R, +E, +A0, -A, +B] extends Serializable { self =>

  type State

  /**
   * Decides whether the Sink should continue from the current state.
   */
  def cont(state: State): Boolean

  /**
   * Produces a final value of type `B` along with a remainder of type `Chunk[A0]`.
   */
  def extract(state: State): ZIO[R, E, (B, Chunk[A0])]

  /**
   * The initial state of the sink.
   */
  def initial: ZIO[R, E, State]

  /**
   * Steps through one iteration of the sink.
   */
  def step(state: State, a: A): ZIO[R, E, State]

  /**
   * Operator alias for `zipRight`
   */
  final def *>[R1 <: R, E1 >: E, A01 >: A0, A1 >: A0 <: A, C](
    that: ZSink[R1, E1, A01, A1, C]
  ): ZSink[R1, E1, A01, A1, C] =
    self zipRight that

  /**
   * Operator alias for `zipParRight`
   */
  final def &>[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A1 =:= A00): ZSink[R1, E1, A00, A1, C] =
    self zipParRight that

  /**
   * Operator alias for `zipLeft`
   */
  final def <*[R1 <: R, E1 >: E, A01 >: A0, A1 >: A0 <: A, C](
    that: ZSink[R1, E1, A01, A1, C]
  ): ZSink[R1, E1, A01, A1, B] =
    self zipLeft that

  /**
   * Operator alias for `zipParLeft`
   */
  final def <&[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A1 =:= A00): ZSink[R1, E1, A00, A1, B] =
    self zipParLeft that

  /**
   * Operator alias for `zip`
   */
  final def <*>[R1 <: R, E1 >: E, A01 >: A0, A1 >: A0 <: A, C](
    that: ZSink[R1, E1, A01, A1, C]
  ): ZSink[R1, E1, A01, A1, (B, C)] =
    self zip that

  /**
   * Operator alias for `zipPar`.
   */
  final def <&>[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A1 =:= A00): ZSink[R1, E1, A00, A1, (B, C)] =
    self zipPar that

  /**
   * Operator alias for `orElse` for two sinks consuming and producing values of the same type.
   */
  final def <|[R1 <: R, E1, B1 >: B, A00 >: A0, A1 <: A](
    that: ZSink[R1, E1, A00, A1, B1]
  )(implicit ev: A1 =:= A00): ZSink[R1, E1, A00, A1, B1] =
    (self orElse that).map(_.merge)

  /**
   * A named alias for `race`.
   */
  final def |[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, B1 >: B](
    that: ZSink[R1, E1, A00, A1, B1]
  ): ZSink[R1, E1, A00, A1, B1] =
    self.race(that)

  /**
   * Creates a sink that always produces `c`
   */
  final def as[C](c: => C): ZSink[R, E, A0, A, C] = self.map(_ => c)

  /**
   * Creates a sink where every element of type `A` entering the sink is first
   * transformed by `f`
   */
  def contramap[C](f: C => A): ZSink[R, E, A0, C, B] =
    new ZSink[R, E, A0, C, B] {
      type State = self.State
      val initial                  = self.initial
      def step(state: State, c: C) = self.step(state, f(c))
      def extract(state: State)    = self.extract(state)
      def cont(state: State)       = self.cont(state)
    }

  /**
   * Creates a sink where every element of type `A` entering the sink is first
   * transformed by the effectful `f`
   */
  final def contramapM[R1 <: R, E1 >: E, C](f: C => ZIO[R1, E1, A]): ZSink[R1, E1, A0, C, B] =
    new ZSink[R1, E1, A0, C, B] {
      type State = self.State
      val initial                  = self.initial
      def step(state: State, c: C) = f(c).flatMap(self.step(state, _))
      def extract(state: State)    = self.extract(state)
      def cont(state: State)       = self.cont(state)
    }

  /**
   * Creates a sink that transforms entering values with `f` and
   * outgoing values with `g`
   */
  def dimap[C, D](f: C => A)(g: B => D): ZSink[R, E, A0, C, D] =
    new ZSink[R, E, A0, C, D] {
      type State = self.State
      val initial                  = self.initial
      def step(state: State, c: C) = self.step(state, f(c))
      def extract(state: State)    = self.extract(state).map { case (b, leftover) => (g(b), leftover) }
      def cont(state: State)       = self.cont(state)
    }

  /**
   * Creates a sink producing values of type `C` obtained by each produced value of type `B`
   * transformed into a sink by `f`.
   */
  final def flatMap[R1 <: R, E1 >: E, A01 >: A0, A1 >: A0 <: A, C](
    f: B => ZSink[R1, E1, A01, A1, C]
  ): ZSink[R1, E1, A01, A1, C] =
    new ZSink[R1, E1, A01, A1, C] {
      type State = Either[self.State, (ZSink[R1, E1, A01, A1, C], Any, Chunk[A01])]

      val initial = self.initial.flatMap { init =>
        if (self.cont(init)) UIO.succeedNow((Left(init)))
        else
          self.extract(init).flatMap {
            case (b, leftover) =>
              val that = f(b)
              that.initial.flatMap { s1 =>
                that.stepChunk(s1, leftover).map {
                  case (s2, chunk) =>
                    Right((that, s2, chunk))
                }
              }
          }
      }

      def step(state: State, a: A1) =
        state match {
          case Left(s1) =>
            self.step(s1, a).flatMap { s2 =>
              if (self.cont(s2)) UIO.succeedNow(Left(s2))
              else
                self.extract(s2).flatMap {
                  case (b, leftover) =>
                    val that = f(b)
                    that.initial.flatMap { init =>
                      that.stepChunk(init, leftover).map {
                        case (s3, chunk) =>
                          Right((that, s3, chunk))
                      }
                    }
                }
            }

          // If `that` needs to continue, it will have already processed all of the
          // leftovers from `self`, because they were stepped in `initial` or `case Left` above.
          case Right((that, s1, _)) =>
            that.step(s1.asInstanceOf[that.State], a).map(s2 => Right((that, s2, Chunk.empty)))
        }

      def extract(state: State) =
        state match {
          case Left(s1) =>
            self.extract(s1).flatMap {
              case (b, leftover) =>
                val that = f(b)
                that.initial.flatMap { init =>
                  that.stepChunk(init, leftover).flatMap {
                    case (s2, chunk) =>
                      that.extract(s2).map {
                        case (c, cLeftover) =>
                          (c, cLeftover ++ chunk)
                      }
                  }
                }
            }

          case Right((that, s2, chunk)) =>
            that.extract(s2.asInstanceOf[that.State]).map {
              case (c, leftover) =>
                (c, leftover ++ chunk)
            }
        }

      def cont(state: State) =
        state match {
          case Left(s1)             => self.cont(s1)
          case Right((that, s2, _)) => that.cont(s2.asInstanceOf[that.State])
        }
    }

  /**
   * Maps the value produced by this sink.
   */
  def map[C](f: B => C): ZSink[R, E, A0, A, C] =
    new ZSink[R, E, A0, A, C] {
      type State = self.State
      val initial                  = self.initial
      def step(state: State, a: A) = self.step(state, a)
      def extract(state: State)    = self.extract(state).map { case (b, leftover) => (f(b), leftover) }
      def cont(state: State)       = self.cont(state)
    }

  /**
   * Maps any error produced by this sink.
   */
  final def mapError[E1](f: E => E1): ZSink[R, E1, A0, A, B] =
    new ZSink[R, E1, A0, A, B] {
      type State = self.State
      val initial                  = self.initial.mapError(f)
      def step(state: State, a: A) = self.step(state, a).mapError(f)
      def extract(state: State)    = self.extract(state).mapError(f)
      def cont(state: State)       = self.cont(state)
    }

  /**
   * Effectfully maps the value produced by this sink.
   */
  final def mapM[R1 <: R, E1 >: E, C](f: B => ZIO[R1, E1, C]): ZSink[R1, E1, A0, A, C] =
    new ZSink[R1, E1, A0, A, C] {
      type State = self.State
      val initial                  = self.initial
      def step(state: State, a: A) = self.step(state, a)
      def extract(state: State)    = self.extract(state).flatMap { case (b, leftover) => f(b).map((_, leftover)) }
      def cont(state: State)       = self.cont(state)
    }

  /**
   * Maps the remainder produced after this sink is done.
   */
  def mapRemainder[A1](f: A0 => A1): ZSink[R, E, A1, A, B] =
    new ZSink[R, E, A1, A, B] {
      type State = self.State
      val initial                  = self.initial
      def step(state: State, a: A) = self.step(state, a)
      def extract(state: State)    = self.extract(state).map { case (b, leftover) => (b, leftover.map(f)) }
      def cont(state: State)       = self.cont(state)
    }

  /**
   * Runs both sinks in parallel on the same input. If the left one succeeds,
   * its value will be produced. Otherwise, whatever the right one produces
   * will be produced. If the right one succeeds before the left one, it
   * accumulates the full input until the left one fails, so it can return
   * it as the remainder. This allows this combinator to function like `choice`
   * in parser combinator libraries.
   *
   * Left:  ============== FAIL!
   * Right: ===== SUCCEEDS!
   *             xxxxxxxxx <- Should NOT be consumed
   */
  final def orElse[R1 <: R, E1, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A1 =:= A00): ZSink[R1, E1, A00, A1, Either[B, C]] =
    new ZSink[R1, E1, A00, A1, Either[B, C]] {
      import ZSink.internal._

      type State = (Side[E, self.State, (B, Chunk[A00])], Side[E1, that.State, (C, Chunk[A00])])

      def decide(state: State): ZIO[R1, E1, State] =
        state match {
          case (Side.Error(_), Side.Error(e)) => IO.fail(e)
          case sides                          => UIO.succeedNow(sides)
        }

      val leftInit: ZIO[R, Nothing, Side[E, self.State, (B, Chunk[A00])]] =
        self.initial.foldM(
          e => UIO.succeedNow(Side.Error(e)),
          s =>
            if (self.cont(s)) UIO.succeedNow(Side.State(s))
            else self.extract(s).fold(Side.Error(_), Side.Value(_))
        )

      val rightInit: ZIO[R1, Nothing, Side[E1, that.State, (C, Chunk[A00])]] =
        that.initial.foldM(
          e => UIO.succeedNow(Side.Error(e)),
          s =>
            if (that.cont(s)) UIO.succeedNow(Side.State(s))
            else that.extract(s).fold(Side.Error(_), Side.Value(_))
        )

      val initial = leftInit.zipPar(rightInit).flatMap(decide(_))

      def step(state: State, a: A1) = {
        val leftStep: ZIO[R, Nothing, Side[E, self.State, (B, Chunk[A00])]] =
          state._1 match {
            case Side.State(s) =>
              self
                .step(s, a)
                .foldM(
                  e => UIO.succeedNow(Side.Error(e)),
                  s =>
                    if (self.cont(s)) UIO.succeedNow(Side.State(s))
                    else self.extract(s).fold(Side.Error(_), Side.Value(_))
                )

            case side => UIO.succeedNow(side)
          }

        val rightStep: ZIO[R1, Nothing, Side[E1, that.State, (C, Chunk[A00])]] =
          state._2 match {
            case Side.State(s) =>
              that
                .step(s, a)
                .foldM(
                  e => UIO.succeedNow(Side.Error(e)),
                  s =>
                    if (that.cont(s)) UIO.succeedNow(Side.State(s))
                    else that.extract(s).fold(Side.Error(_), Side.Value(_))
                )

            case Side.Value((c, as)) =>
              val as1 = as ++ Chunk.single(ev(a))
              UIO.succeedNow(Side.Value((c, as1)))

            case side => UIO.succeedNow(side)
          }

        leftStep.zipPar(rightStep).flatMap(decide(_))
      }

      def extract(state: State) =
        state match {
          case (Side.Error(_), Side.Error(e))             => IO.fail(e)
          case (Side.Error(_), Side.State(s))             => that.extract(s).map { case (c, leftover) => (Right(c), leftover) }
          case (Side.Error(_), Side.Value((c, leftover))) => UIO.succeedNow((Right(c), leftover))
          case (Side.Value((b, leftover)), _)             => UIO.succeedNow((Left(b), leftover))
          case (Side.State(s), Side.Error(e)) =>
            self.extract(s).map { case (b, leftover) => (Left(b), leftover) }.orElseFail(e)
          case (Side.State(s), Side.Value((c, leftover))) =>
            self.extract(s).map { case (b, ll) => (Left(b), ll) }.catchAll(_ => UIO.succeedNow((Right(c), leftover)))
          case (Side.State(s1), Side.State(s2)) =>
            self
              .extract(s1)
              .map {
                case (b, leftover) =>
                  ((Left(b), leftover))
              }
              .catchAll(_ =>
                that.extract(s2).map {
                  case (c, leftover) =>
                    ((Right(c), leftover))
                }
              )
        }

      def cont(state: State) =
        state match {
          case (Side.Error(_), Side.State(s))   => that.cont(s)
          case (Side.Error(_), _)               => false
          case (Side.State(s1), Side.State(s2)) => self.cont(s1) || that.cont(s2)
          case (Side.State(s), _)               => self.cont(s)
          case (Side.Value(_), _)               => false
        }
    }

  /**
   * Replaces any error produced by this sink.
   */
  final def orElseFail[E1](e1: => E1): ZSink[R, E1, A0, A, B] =
    self.mapError(new ZIO.ConstFn(() => e1))

  /**
   * Narrows the environment by partially building it with `f`
   */
  final def provideSome[R1](f: R1 => R)(implicit ev: NeedsEnv[R]): ZSink[R1, E, A0, A, B] =
    new ZSink[R1, E, A0, A, B] {
      type State = self.State
      val initial                  = self.initial.provideSome(f)
      def step(state: State, a: A) = self.step(state, a).provideSome(f)
      def extract(state: State)    = self.extract(state).provideSome(f)
      def cont(state: State)       = self.cont(state)
    }

  /**
   * Runs both sinks in parallel on the input, returning the result from the
   * one that finishes successfully first.
   */
  final def race[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, B1 >: B](
    that: ZSink[R1, E1, A00, A1, B1]
  ): ZSink[R1, E1, A00, A1, B1] =
    self.raceBoth(that).map(_.merge)

  /**
   * Steps through a chunk of iterations of the sink
   */
  final def stepChunk[A1 <: A](
    state: State,
    as: Chunk[A1]
  ): ZIO[R, E, (State, Chunk[A1])] = {
    val len = as.length

    def loop(state: State, i: Int): ZIO[R, E, (State, Chunk[A1])] =
      if (i >= len) UIO.succeedNow(state -> Chunk.empty)
      else if (self.cont(state)) self.step(state, as(i)).flatMap(loop(_, i + 1))
      else UIO.succeedNow(state -> as.splitAt(i)._2)

    loop(state, 0)
  }

  /**
   * Runs both sinks in parallel on the input, returning the result from the
   * one that finishes successfully first.
   */
  final def raceBoth[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  ): ZSink[R1, E1, A00, A1, Either[B, C]] =
    new ZSink[R1, E1, A00, A1, Either[B, C]] {
      import ZSink.internal._

      type State = (Side[E, self.State, (B, Chunk[A00])], Side[E1, that.State, (C, Chunk[A00])])

      def decide(state: State): ZIO[R1, E1, State] =
        state match {
          case (Side.Error(e1), Side.Error(e2)) => IO.halt(Cause.Both(Cause.fail(e1), Cause.fail(e2)))
          case sides                            => UIO.succeedNow(sides)
        }

      val leftInit: ZIO[R, Nothing, Side[E, self.State, (B, Chunk[A00])]] =
        self.initial.foldM(
          e => UIO.succeedNow(Side.Error(e)),
          s =>
            if (self.cont(s)) UIO.succeedNow(Side.State(s))
            else self.extract(s).fold(Side.Error(_), Side.Value(_))
        )

      val rightInit: ZIO[R1, Nothing, Side[E1, that.State, (C, Chunk[A00])]] =
        that.initial.foldM(
          e => UIO.succeedNow(Side.Error(e)),
          s =>
            if (that.cont(s)) UIO.succeedNow(Side.State(s))
            else that.extract(s).fold(Side.Error(_), Side.Value(_))
        )

      val initial = leftInit.zipPar(rightInit).flatMap(decide(_))

      def step(state: State, a: A1) = {
        val leftStep: ZIO[R, Nothing, Side[E, self.State, (B, Chunk[A00])]] =
          state._1 match {
            case Side.State(s) =>
              self
                .step(s, a)
                .foldM(
                  e => UIO.succeedNow(Side.Error(e)),
                  s =>
                    if (self.cont(s)) UIO.succeedNow(Side.State(s))
                    else self.extract(s).fold(Side.Error(_), Side.Value(_))
                )

            case side => UIO.succeedNow(side)
          }

        val rightStep: ZIO[R1, Nothing, Side[E1, that.State, (C, Chunk[A00])]] =
          state._2 match {
            case Side.State(s) =>
              that
                .step(s, a)
                .foldM(
                  e => UIO.succeedNow(Side.Error(e)),
                  s =>
                    if (that.cont(s)) UIO.succeedNow(Side.State(s))
                    else that.extract(s).fold(Side.Error(_), Side.Value(_))
                )

            case side => UIO.succeedNow(side)
          }

        leftStep.zipPar(rightStep).flatMap(decide(_))
      }

      def extract(state: State) =
        state match {
          case (Side.Error(e1), Side.Error(e2))           => IO.halt(Cause.Both(Cause.fail(e1), Cause.fail(e2)))
          case (Side.Error(_), Side.State(s))             => that.extract(s).map { case (c, leftover) => (Right(c), leftover) }
          case (Side.Error(_), Side.Value((c, leftover))) => UIO.succeedNow((Right(c), leftover))
          case (Side.State(s), Side.Error(e)) =>
            self.extract(s).map { case (b, leftover) => (Left(b), leftover) }.orElseFail(e)
          case (Side.State(s1), Side.State(s2)) =>
            self
              .extract(s1)
              .map {
                case (b, leftover) =>
                  (Left(b), leftover)
              }
              .catchAll(_ =>
                that.extract(s2).map {
                  case (c, leftover) =>
                    (Right(c), leftover)
                }
              )
          case (Side.State(_), Side.Value((c, leftover))) => UIO.succeedNow((Right(c), leftover))
          case (Side.Value((b, leftover)), _)             => UIO.succeedNow((Left(b), leftover))
        }

      def cont(state: State) =
        state match {
          case (Side.State(s1), Side.State(s2)) => self.cont(s1) && that.cont(s2)
          case _                                => false
        }
    }

  /**
   * Performs the specified effect for every element that is consumed by this sink.
   */
  final def tapInput[R1 <: R, E1 >: E, A1 <: A](f: A1 => ZIO[R1, E1, Unit]): ZSink[R1, E1, A0, A1, B] =
    contramapM(a => f(a).as(a))

  /**
   * Performs the specified effect for every element that is produced by this sink.
   */
  final def tapOutput[R1 <: R, E1 >: E](f: B => ZIO[R1, E1, Unit]): ZSink[R1, E1, A0, A, B] =
    mapM(b => f(b).as(b))

  /**
   * Times the invocation of the sink
   */
  final def timed: ZSink[R with Clock, E, A0, A, (Duration, B)] =
    new ZSink[R with Clock, E, A0, A, (Duration, B)] {
      type State = (Long, Long, self.State)

      val initial = for {
        s <- self.initial
        t <- zio.clock.nanoTime
      } yield (t, 0L, s)

      def step(state: State, a: A) =
        state match {
          case (t, total, st) =>
            for {
              s   <- self.step(st, a)
              now <- zio.clock.nanoTime
              t1  = now - t
            } yield (now, total + t1, s)
        }

      def extract(s: State) = self.extract(s._3).map {
        case (b, leftover) =>
          ((Duration.fromNanos(s._2), b), leftover)
      }

      def cont(state: State) = self.cont(state._3)
    }

  /**
   * Creates a sink that ignores all produced elements.
   */
  final def unit: ZSink[R, E, A0, A, Unit] = as(())

  /**
   * Sets the initial state of the sink to the provided state.
   */
  final def update(state: State): ZSink[R, E, A0, A, B] =
    new ZSink[R, E, A0, A, B] {
      type State = self.State
      val initial                  = IO.succeedNow(state)
      def step(state: State, a: A) = self.step(state, a)
      def extract(state: State)    = self.extract(state)
      def cont(state: State)       = self.cont(state)
    }

  /**
   * Runs two sinks in sequence and combines their results into a tuple.
   * The `this` sink will consume inputs until it produces a value. Afterwards,
   * the `that` sink will start consuming inputs until it produces a value.
   *
   * Note that this means that the two sinks will not consume the same inputs,
   * but rather run one after the other.
   */
  final def zip[R1 <: R, E1 >: E, A01 >: A0, A1 >: A0 <: A, C](
    that: ZSink[R1, E1, A01, A1, C]
  ): ZSink[R1, E1, A01, A1, (B, C)] =
    flatMap(b => that.map(c => (b, c)))

  /**
   * Runs two sinks in sequence and keeps only values on the left.
   *
   * See [[zip]] for notes about the behavior of this combinator.
   */
  final def zipLeft[R1 <: R, E1 >: E, A01 >: A0, A1 >: A0 <: A, C](
    that: ZSink[R1, E1, A01, A1, C]
  ): ZSink[R1, E1, A01, A1, B] =
    zipWith(that)((b, _) => b)

  /**
   * Runs both sinks in parallel on the input and combines the results
   * using the provided function.
   */
  final def zipWithPar[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C, D](
    that: ZSink[R1, E1, A00, A1, C]
  )(f: (B, C) => D)(implicit ev: A1 =:= A00): ZSink[R1, E1, A00, A1, D] =
    new ZSink[R1, E1, A00, A1, D] {
      type State = (Either[self.State, (B, Chunk[A00])], Either[that.State, (C, Chunk[A00])])

      val initial = self.initial.zipPar(that.initial).flatMap {
        case (s1, s2) =>
          val left  = if (self.cont(s1)) UIO.succeedNow(Left(s1)) else self.extract(s1).map(Right(_))
          val right = if (that.cont(s2)) UIO.succeedNow(Left(s2)) else that.extract(s2).map(Right(_))
          left.zipPar(right)
      }

      def step(state: State, a: A1) = {
        val leftStep: ZIO[R, E, Either[self.State, (B, Chunk[A00])]] =
          state._1.fold(
            s1 =>
              self.step(s1, a).flatMap { s2 =>
                if (self.cont(s2)) UIO.succeedNow(Left(s2))
                else self.extract(s2).map(Right(_))
              },
            { case (b, leftover) => UIO.succeedNow(Right((b, leftover ++ Chunk.single(a)))) }
          )

        val rightStep: ZIO[R1, E1, Either[that.State, (C, Chunk[A00])]] =
          state._2.fold(
            s1 =>
              that.step(s1, a).flatMap { s2 =>
                if (that.cont(s2)) UIO.succeedNow(Left(s2))
                else that.extract(s2).map(Right(_))
              },
            { case (c, leftover) => UIO.succeedNow(Right((c, leftover ++ Chunk.single(a)))) }
          )

        leftStep.zipPar(rightStep)
      }

      def extract(state: State) = {
        val leftExtract  = state._1.fold(self.extract, UIO.succeedNow)
        val rightExtract = state._2.fold(that.extract, UIO.succeedNow)
        leftExtract.zipPar(rightExtract).map {
          case ((b, ll), (c, rl)) => (f(b, c), List(ll, rl).minBy(_.length))
        }
      }

      def cont(state: State) =
        state match {
          case (Left(s1), Left(s2)) => self.cont(s1) && that.cont(s2)
          case _                    => false
        }
    }

  /**
   * Runs both sinks in parallel on the input and combines the results into a Tuple.
   */
  final def zipPar[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A1 =:= A00): ZSink[R1, E1, A00, A1, (B, C)] =
    zipWithPar(that)((_, _))

  /**
   * Runs both sinks in parallel on the input and combines the results into a Tuple.
   */
  final def zipParRight[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A1 =:= A00): ZSink[R1, E1, A00, A1, C] =
    zipWithPar(that)((_, c) => c)

  /**
   * Runs both sinks in parallel on the input and combines the results into a Tuple.
   */
  final def zipParLeft[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A1 =:= A00): ZSink[R1, E1, A00, A1, B] =
    zipWithPar(that)((b, _) => b)

  /**
   * Runs two sinks in sequence and keeps only values on the right.
   *
   * See [[zip]] for notes about the behavior of this combinator.
   */
  final def zipRight[R1 <: R, E1 >: E, A00 >: A0, A1 >: A0 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  ): ZSink[R1, E1, A00, A1, C] =
    zipWith(that)((_, c) => c)

  /**
   * Runs two sinks in sequence and merges their values with the provided function.
   *
   * See [[zip]] for notes about the behavior of this combinator.
   */
  final def zipWith[R1 <: R, E1 >: E, A00 >: A0, A1 >: A0 <: A, C, D](
    that: ZSink[R1, E1, A00, A1, C]
  )(f: (B, C) => D): ZSink[R1, E1, A00, A1, D] =
    zip(that).map(f.tupled)
}

object ZSink extends ZSinkPlatformSpecificConstructors with Serializable {

  implicit final class InputRemainderOps[R, E, A, B](private val sink: ZSink[R, E, A, A, B]) {

    /**
     * Returns a new sink that tries to produce the `B`, but if there is an
     * error in stepping or extraction, produces `None`.
     */
    def ? : ZSink[R, Nothing, A, A, Option[B]] =
      new ZSink[R, Nothing, A, A, Option[B]] {
        type State = OptionalState
        sealed trait OptionalState
        object OptionalState {
          case class Done(s: sink.State) extends OptionalState
          case class More(s: sink.State) extends OptionalState
          case class Fail(as: Chunk[A])  extends OptionalState
        }

        val initial = sink.initial.fold(
          _ => OptionalState.Fail(Chunk.empty),
          s =>
            if (sink.cont(s)) OptionalState.More(s)
            else OptionalState.Done(s)
        )

        def step(state: State, a: A) =
          state match {
            case OptionalState.More(s1) =>
              sink
                .step(s1, a)
                .fold(
                  _ => OptionalState.Fail(Chunk.single(a)),
                  s2 =>
                    if (sink.cont(s2)) OptionalState.More(s2)
                    else OptionalState.Done(s2)
                )

            case s => UIO.succeedNow(s)
          }

        def extract(state: State) =
          state match {
            case OptionalState.Done(s) =>
              sink
                .extract(s)
                .fold(
                  _ => (None, Chunk.empty), { case (b, as) => (Some(b), as) }
                )

            case OptionalState.More(s) =>
              sink
                .extract(s)
                .fold(
                  _ => (None, Chunk.empty), { case (b, as) => (Some(b), as) }
                )

            case OptionalState.Fail(as) => UIO.succeedNow((None, as))
          }

        def cont(state: State) =
          state match {
            case OptionalState.More(_) => true
            case _                     => false
          }
      }

    /**
     * Takes a `Sink`, and lifts it to be chunked in its input. This
     * will not improve performance, but can be used to adapt non-chunked sinks
     * wherever chunked sinks are required.
     */
    def chunked: ZSink[R, E, A, Chunk[A], B] =
      new ZSink[R, E, A, Chunk[A], B] {
        type State = (sink.State, Chunk[A])
        val initial = sink.initial.map((_, Chunk.empty))
        def step(state: State, a: Chunk[A]) =
          sink.stepChunk(state._1, a).map { case (s, chunk) => (s, chunk) }
        def extract(state: State) = sink.extract(state._1).map { case (b, leftover) => (b, leftover ++ state._2) }
        def cont(state: State)    = sink.cont(state._1)
      }

    /**
     * Repeatedly runs this sink and accumulates its outputs to a list.
     */
    def collectAll: ZSink[R, E, A, A, List[B]] =
      collectAllWith[List[B]](List[B]())((bs, b) => b :: bs).map(_.reverse)

    /**
     * Repeatedly runs this sink until `i` outputs have been accumulated.
     */
    def collectAllN(
      i: Int
    ): ZSink[R, E, A, A, List[B]] =
      new ZSink[R, E, A, A, List[B]] {
        type State = CollectAllNState
        case class CollectAllNState(s: sink.State, bs: List[B], n: Int, leftover: Chunk[A], dirty: Boolean)

        val initial = sink.initial.map(CollectAllNState(_, List(), 0, Chunk(), false))

        def step(state: State, a: A) =
          if (state.n >= i) UIO.succeedNow(state.copy(leftover = state.leftover ++ Chunk.single(a)))
          else if (!sink.cont(state.s))
            for {
              extractResult <- sink.extract(state.s)
              (b, as)       = extractResult
              newState <- if (state.n + 1 < i)
                           for {
                             init          <- sink.initial
                             stepResult    <- sink.stepChunk(init, as ++ state.leftover ++ Chunk.single(a))
                             (s, leftover) = stepResult
                           } yield CollectAllNState(s, b :: state.bs, state.n + 1, leftover, true)
                         else
                           sink.initial.map(
                             CollectAllNState(
                               _,
                               b :: state.bs,
                               state.n + 1,
                               as ++ state.leftover ++ Chunk.single(a),
                               false
                             )
                           )
            } yield newState
          else sink.step(state.s, a).map(s2 => state.copy(s = s2, dirty = true))

        def extract(state: State) =
          if (state.dirty && state.n < i)
            sink.extract(state.s).map {
              case (b, leftover) => ((b :: state.bs).reverse, leftover ++ state.leftover)
            }
          else UIO.succeedNow((state.bs.reverse, state.leftover))

        def cont(state: State) = state.n >= i
      }

    /**
     * Repeatedly runs this sink and accumulates the outputs into a value
     * of type `S`.
     */
    def collectAllWith[S](z: S)(f: (S, B) => S): ZSink[R, E, A, A, S] = collectAllWhileWith[S](_ => true)(z)(f)

    /**
     * Repeatedly runs this sink and accumulates its outputs for as long
     * as incoming values verify the predicate.
     */
    def collectAllWhile(p: A => Boolean): ZSink[R, E, A, A, List[B]] =
      collectAllWhileWith[List[B]](p)(List.empty[B])((bs, b) => b :: bs)
        .map(_.reverse)

    /**
     * Repeatedly runs this sink and accumulates its outputs into a value
     * of type `S` for as long as the incoming values satisfy the predicate.
     */
    def collectAllWhileWith[S](p: A => Boolean)(z: S)(f: (S, B) => S): ZSink[R, E, A, A, S] =
      new ZSink[R, E, A, A, S] {
        type State = CollectAllWhileWithState
        case class CollectAllWhileWithState(
          s: S,
          selfS: sink.State,
          predicateViolated: Boolean,
          leftovers: Chunk[A],
          dirty: Boolean
        )

        val initial = sink.initial.map(CollectAllWhileWithState(z, _, false, Chunk.empty, false))

        def step(state: State, a: A) =
          if (!p(a))
            UIO.succeedNow(state.copy(predicateViolated = true, leftovers = state.leftovers ++ Chunk.single(a)))
          else if (!sink.cont(state.selfS))
            for {
              extractResult <- sink.extract(state.selfS)
              (b, as)       = extractResult
              init          <- sink.initial
              stepResult    <- sink.stepChunk(init, as ++ state.leftovers ++ Chunk.single(a))
              (s, leftover) = stepResult
            } yield CollectAllWhileWithState(f(state.s, b), s, state.predicateViolated, leftover, true)
          else
            sink.step(state.selfS, a).map(s2 => state.copy(selfS = s2, dirty = true))

        def extract(state: State) =
          if (!state.dirty) UIO.succeedNow((state.s, state.leftovers))
          else
            sink.extract(state.selfS).map {
              case (b, leftovers) =>
                (f(state.s, b), leftovers ++ state.leftovers)
            }

        def cont(state: State) = !state.predicateViolated
      }

    /**
     * A named alias for `?`.
     */
    def optional: ZSink[R, Nothing, A, A, Option[B]] = ?

    /**
     * Produces a sink consuming all the elements of type `A` as long as
     * they verify the predicate `pred`.
     */
    def takeWhile(pred: A => Boolean): ZSink[R, E, A, A, B] =
      new ZSink[R, E, A, A, B] {
        type State = (sink.State, Chunk[A])

        val initial = sink.initial.map((_, Chunk.empty))

        def step(state: State, a: A) =
          if (pred(a)) sink.step(state._1, a).map((_, Chunk.empty))
          else UIO.succeedNow((state._1, Chunk.single(a)))

        def extract(state: State) = sink.extract(state._1).map { case (b, leftover) => (b, leftover ++ state._2) }

        def cont(state: State) = state._2.isEmpty && sink.cont(state._1)
      }

    /**
     * Creates a sink that produces values until one verifies
     * the predicate `f`.
     *
     * @note The predicate is only verified when the underlying
     * signals completion, or when the resulting sink is extracted.
     * Sinks that never signal completion (e.g. [[ZSink.collectAll]])
     * will not have the predicate applied to intermediate values.
     */
    def untilOutput(f: B => Boolean): ZSink[R, E, A, A, Option[B]] =
      new ZSink[R, E, A, A, Option[B]] {
        type State = (sink.State, Option[B], Chunk[A], Boolean)

        val initial = sink.initial.map((_, None, Chunk.empty, false))

        def step(state: State, a: A) =
          if (sink.cont(state._1))
            sink
              .step(state._1, a)
              .map((_, state._2, state._3, true))
          else
            sink.extract(state._1).flatMap {
              case (b, leftover) =>
                if (f(b)) UIO.succeedNow((state._1, Some(b), leftover ++ Chunk.single(a), false))
                else
                  for {
                    init          <- sink.initial
                    stepResult    <- sink.stepChunk(init, leftover ++ Chunk.single(a))
                    (s, leftover) = stepResult
                  } yield (s, None, leftover, leftover.nonEmpty)
            }

        def extract(state: State) =
          if (!state._4 || state._2.nonEmpty) UIO.succeedNow((state._2, state._3))
          else
            sink.extract(state._1).map {
              case (b, leftover) =>
                (if (f(b)) Some(b) else None, leftover ++ state._3)
            }

        def cont(state: State) = state._2.isEmpty
      }
  }

  implicit final class NoRemainderOps[R, E, A, B](private val sink: ZSink[R, E, Nothing, A, B]) extends AnyVal {
    private def widen: ZSink[R, E, A, A, B] = sink

    /**
     * Returns a new sink that tries to produce the `B`, but if there is an
     * error in stepping or extraction, produces `None`.
     */
    def ? : ZSink[R, Nothing, A, A, Option[B]] = widen.?

    /**
     * Takes a `Sink`, and lifts it to be chunked in its input. This
     * will not improve performance, but can be used to adapt non-chunked sinks
     * wherever chunked sinks are required.
     */
    def chunked: ZSink[R, E, A, Chunk[A], B] = widen.chunked

    /**
     * Accumulates the output into a list.
     */
    def collectAll: ZSink[R, E, A, A, List[B]] = widen.collectAll

    /**
     * Accumulates the output into a list of maximum size `i`.
     */
    def collectAllN(i: Int): ZSink[R, E, A, A, List[B]] = widen.collectAllN(i)

    /**
     * Accumulates the output into a value of type `S`.
     */
    def collectAllWith[S](z: S)(f: (S, B) => S): ZSink[R, E, A, A, S] = widen.collectAllWith(z)(f)

    /**
     * Accumulates into a list for as long as incoming values verify predicate `p`.
     */
    def collectAllWhile(p: A => Boolean): ZSink[R, E, A, A, List[B]] = widen.collectAllWhile(p)

    /**
     * Accumulates into a value of type `S` for as long as incoming values verify predicate `p`.
     */
    def collectAllWhileWith[S](p: A => Boolean)(z: S)(f: (S, B) => S): ZSink[R, E, A, A, S] =
      widen.collectAllWhileWith(p)(z)(f)

    /**
     * A named alias for `?`.
     */
    def optional: ZSink[R, Nothing, A, A, Option[B]] = widen.?

    /**
     * Produces a sink consuming all the elements of type `A` as long as
     * they verify the predicate `pred`.
     */
    def takeWhile(pred: A => Boolean): ZSink[R, E, A, A, B] = widen.takeWhile(pred)

    /**
     * Creates a sink that produces values until one verifies
     * the predicate `f`.
     *
     * @note The predicate is only verified when the underlying
     * signals completion, or when the resulting sink is extracted.
     * Sinks that never signal completion (e.g. [[ZSink.collectAll]])
     * will not have the predicate applied to intermediate values.
     */
    def untilOutput(f: B => Boolean): ZSink[R, E, A, A, Option[B]] =
      widen untilOutput f
  }

  implicit final class InvariantOps[R, E, A0, A, B](private val sink: ZSink[R, E, A0, A, B]) extends AnyVal { self =>

    /**
     * Drops the first `n`` elements from the sink.
     */
    def drop(n: Long): ZSink[R, E, A0, A, B] =
      new ZSink[R, E, A0, A, B] {
        type State = (sink.State, Long)

        // Cast is redundant but required for Scala 2.11
        val initial = sink.initial.map((_, 0L)).asInstanceOf[ZIO[R, E, this.State]]

        def step(state: State, a: A) =
          if (state._2 < n) UIO.succeedNow((state._1, state._2 + 1))
          else sink.step(state._1, a).map((_, state._2))

        def extract(state: State) = sink.extract(state._1)

        def cont(state: State) = state._2 < n || sink.cont(state._1)
      }

    /**
     * Drops all elements entering the sink for as long as the specified predicate
     * evaluates to `true`.
     */
    def dropWhile(pred: A => Boolean): ZSink[R, E, A0, A, B] =
      new ZSink[R, E, A0, A, B] {
        type State = (sink.State, Boolean)

        // Cast is redundant but required for Scala 2.11
        val initial = sink.initial.map((_, true)).asInstanceOf[ZIO[R, E, this.State]]

        def step(state: State, a: A) =
          if (!state._2) sink.step(state._1, a).map((_, false))
          else {
            if (pred(a)) UIO.succeedNow(state)
            else sink.step(state._1, a).map((_, false))
          }

        def extract(state: State) = sink.extract(state._1)

        def cont(state: State) = sink.cont(state._1)
      }

    /**
     * Filters the inputs fed to this sink.
     */
    def filter(f: A => Boolean): ZSink[R, E, A0, A, B] =
      sink match {
        case self: SinkPure[E, A0, A, B] =>
          new SinkPure[E, A0, A, B] {
            type State = self.State
            // Cast is redundant but required for Scala 2.11
            val initialPure                  = self.initialPure.asInstanceOf[this.State]
            def stepPure(state: State, a: A) = if (f(a)) self.stepPure(state, a) else state
            def extractPure(state: State)    = self.extractPure(state)
            def cont(state: State)           = self.cont(state)
          }

        case _ =>
          new ZSink[R, E, A0, A, B] {
            type State = sink.State
            val initial                  = sink.initial
            def step(state: State, a: A) = if (f(a)) sink.step(state, a) else UIO.succeedNow(state)
            def extract(state: State)    = sink.extract(state)
            def cont(state: State)       = sink.cont(state)
          }
      }

    /**
     * Effectfully filters the inputs fed to this sink.
     */
    def filterM[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Boolean]): ZSink[R1, E1, A0, A, B] =
      new ZSink[R1, E1, A0, A, B] {
        type State = sink.State
        val initial = sink.initial

        def step(state: State, a: A) = f(a).flatMap { b =>
          if (b) sink.step(state, a)
          else UIO.succeedNow(state)
        }

        def extract(state: State) = sink.extract(state)
        def cont(state: State)    = sink.cont(state)
      }

    /**
     * Filters this sink by the specified predicate, dropping all elements for
     * which the predicate evaluates to true.
     */
    def filterNot(f: A => Boolean): ZSink[R, E, A0, A, B] =
      filter(a => !f(a))

    /**
     * Effectfully filters this sink by the specified predicate, dropping all elements for
     * which the predicate evaluates to true.
     */
    def filterNotM[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Boolean]): ZSink[R1, E1, A0, A, B] =
      filterM(a => f(a).map(!_))

    /**
     * Runs `n` sinks in parallel, where `n` is the number of possible keys
     * generated by `f`.
     */
    def keyed[K](f: A => K): ZSink[R, E, (K, Chunk[A0]), A, Map[K, B]] =
      new ZSink[R, E, (K, Chunk[A0]), A, Map[K, B]] {
        type State = Map[K, sink.State]

        val initial =
          sink.initial
            .map(init => Map.empty[K, sink.State].withDefaultValue(init))
            // Cast is redundant but required for Scala 2.11
            .asInstanceOf[ZIO[R, E, this.State]]

        def step(state: State, a: A) = {
          val k = f(a)
          sink.step(state(k), a).map(s => state + (k -> s))
        }.asInstanceOf[ZIO[R, E, this.State]]

        def extract(state: State) =
          ZIO
            .foreach(state.toList) {
              case (k, s) => sink.extract(s).map(k -> _)
            }
            .map { list =>
              val results   = list.map { case (k, (b, _)) => (k, b) }.toMap
              val leftovers = Chunk.fromIterable(list.map { case (k, (_, chunk)) => (k, chunk) }).filter(_._2.nonEmpty)
              (results, leftovers)
            }

        def cont(state: State) = state.values.forall(sink.cont)
      }
  }

  private[ZSink] object internal {
    sealed trait Side[+E, +S, +A]
    object Side {
      final case class Error[+E](e: E) extends Side[E, Nothing, Nothing]
      final case class State[+S](s: S) extends Side[Nothing, S, Nothing]
      final case class Value[+A](a: A) extends Side[Nothing, Nothing, A]
    }

    def assertNonNegative(n: Long): UIO[Unit] =
      if (n < 0) UIO.die(new NegativeArgument(s"Unexpected negative unit value `$n`"))
      else UIO.unit

    def assertPositive(n: Long): UIO[Unit] =
      if (n <= 0) UIO.die(new NonpositiveArgument(s"Unexpected nonpositive unit value `$n`"))
      else UIO.unit

    class NegativeArgument(message: String) extends IllegalArgumentException(message)

    class NonpositiveArgument(message: String) extends IllegalArgumentException(message)
  }

  /**
   * Creates a sink that waits for a single value to be produced.
   */
  def await[A]: ZSink[Any, Unit, Nothing, A, A] = identity

  /**
   * Creates a sink accumulating incoming values into a list.
   */
  def collectAll[A]: ZSink[Any, Nothing, Nothing, A, List[A]] =
    foldLeft[A, List[A]](List.empty[A])((as, a) => a :: as).map(_.reverse)

  /**
   * Creates a sink accumulating incoming values into a list of maximum size `n`.
   */
  def collectAllN[A](n: Long): ZSink[Any, Nothing, A, A, List[A]] =
    foldUntil[List[A], A](List.empty[A], n)((list, element) => element :: list).map(_.reverse)

  /**
   * Creates a sink accumulating incoming values into a set.
   */
  def collectAllToSet[A]: ZSink[Any, Nothing, Nothing, A, Set[A]] =
    foldLeft[A, Set[A]](Set.empty[A])((set, element) => set + element)

  /**
   * Creates a sink accumulating incoming values into a set of maximum size `n`.
   */
  def collectAllToSetN[A](n: Long): ZSink[Any, Nothing, A, A, Set[A]] = {
    type State = (Set[A], Boolean)
    def f(state: State, a: A): (State, Chunk[A]) = {
      val newSet = state._1 + a
      if (newSet.size > n) ((state._1, false), Chunk.single(a))
      else if (newSet.size == n) ((newSet, false), Chunk.empty)
      else ((newSet, true), Chunk.empty)
    }
    fold[A, A, State]((Set.empty, true))(_._2)(f).map(_._1)
  }

  /**
   * Creates a sink accumulating incoming values into a map.
   * Key of each element is determined by supplied function.
   * Combines elements with same key with supplied function f.
   *
   */
  def collectAllToMap[K, A](key: A => K)(f: (A, A) => A): Sink[Nothing, Nothing, A, Map[K, A]] =
    foldLeft[A, Map[K, A]](Map.empty) { (curMap, a) =>
      val k = key(a)
      curMap.get(k).fold(curMap.updated(k, a))(v => curMap.updated(k, f(v, a)))
    }

  /**
   * Creates a sink accumulating incoming values into a map of maximum size `n`.
   * Key of each element is determined by supplied function.
   *
   * Combines elements with same key with supplied function f.
   */
  def collectAllToMapN[K, A](n: Long)(key: A => K)(f: (A, A) => A): Sink[Nothing, A, A, Map[K, A]] = {
    type State = (Map[K, A], Boolean)
    def inner(state: State, a: A): (State, Chunk[A]) = {
      val k      = key(a)
      val curMap = state._1
      curMap
        .get(k)
        .fold(
          if (curMap.size >= n) ((curMap, false), Chunk.single(a))
          else ((curMap.updated(k, a), true), Chunk.empty)
        )(v => ((curMap.updated(k, f(v, a)), true), Chunk.empty))
    }
    fold[A, A, State]((Map.empty, true))(_._2)(inner).map(_._1)
  }

  /**
   * Accumulates incoming elements into a list as long as they verify predicate `p`.
   */
  def collectAllWhile[A](p: A => Boolean): ZSink[Any, Nothing, A, A, List[A]] =
    fold[A, A, (List[A], Boolean)]((Nil, true))(_._2) {
      case ((as, _), a) =>
        if (p(a)) ((a :: as, true), Chunk.empty) else ((as, false), Chunk.single(a))
    }.map(_._1.reverse)

  /**
   * Accumulates incoming elements into a list as long as they verify effectful predicate `p`.
   */
  def collectAllWhileM[R, E, A](p: A => ZIO[R, E, Boolean]): ZSink[R, E, A, A, List[A]] =
    foldM[R, E, A, A, (List[A], Boolean)]((Nil, true))(_._2) {
      case ((as, _), a) =>
        p(a).map(if (_) ((a :: as, true), Chunk.empty) else ((as, false), Chunk.single(a)))
    }.map(_._1.reverse)

  /**
   * Creates a sink which emits the number of elements processed
   */
  def count[A]: ZSink[Any, Nothing, Nothing, A, Long] =
    foldLeft[A, Long](0L) {
      case (accum, _) => accum + 1L
    }

  /**
   * Creates a sink halting with the specified `Throwable`.
   */
  def die(e: => Throwable): ZSink[Any, Nothing, Nothing, Any, Nothing] =
    ZSink.halt(Cause.die(e))

  /**
   * Creates a sink halting with the specified message, wrapped in a
   * `RuntimeException`.
   */
  def dieMessage(m: => String): ZSink[Any, Nothing, Nothing, Any, Nothing] =
    ZSink.halt(Cause.die(new RuntimeException(m)))

  /**
   * Creates a sink consuming all incoming values until completion.
   */
  def drain: ZSink[Any, Nothing, Nothing, Any, Unit] =
    foldLeft(())((s, _) => s)

  /**
   * Creates a sink that drops the first `n` values. Does not fail if there
   * are fewer than `n` input values.
   */
  def drop[A](n: Long): ZSink[Any, Nothing, A, A, Unit] =
    new SinkPure[Nothing, A, A, Unit] {
      type State = Long
      val initialPure                  = 0L
      def stepPure(state: State, a: A) = state + 1
      def extractPure(state: State)    = Right(((), Chunk.empty))
      def cont(state: State)           = state < n
    }

  /**
   * Creates a sink that drops the first `n` values and fails if there are
   * fewer than `n` input values.
   */
  def skip[A](n: Long): ZSink[Any, Unit, A, A, Unit] =
    new SinkPure[Unit, A, A, Unit] {
      type State = Long
      val initialPure                  = 0L
      def stepPure(state: State, a: A) = state + 1
      def extractPure(state: State)    = if (state < n) Left(()) else Right(((), Chunk.empty))
      def cont(state: State)           = state < n
    }

  /**
   * Creates a sink containing the first value.
   */
  def head[A]: ZSink[Any, Nothing, A, A, Option[A]] =
    identity[A].optional

  /**
   * Creates a sink containing the last value.
   */
  def last[A]: ZSink[Any, Nothing, Nothing, A, Option[A]] =
    foldLeft[A, Option[A]](None) { case (_, a) => Some(a) }

  /**
   * Creates a sink failing with a value of type `E`.
   */
  def fail[E](e: => E): ZSink[Any, E, Nothing, Any, Nothing] =
    new SinkPure[E, Nothing, Any, Nothing] {
      type State = Unit
      val initialPure                    = ()
      def stepPure(state: State, a: Any) = ()
      def extractPure(state: State)      = Left(e)
      def cont(state: State)             = false
    }

  /**
   * Creates a sink by folding over a structure of type `S`.
   */
  def fold[A0, A, S](
    z: S
  )(contFn: S => Boolean)(f: (S, A) => (S, Chunk[A0])): ZSink[Any, Nothing, A0, A, S] =
    new SinkPure[Nothing, A0, A, S] {
      type State = (S, Chunk[A0])
      val initialPure                  = (z, Chunk.empty)
      def stepPure(state: State, a: A) = f(state._1, a)
      def extractPure(state: State)    = Right(state)
      def cont(state: State)           = contFn(state._1)
    }

  /**
   * Creates a sink by folding over a structure of type `S`.
   */
  def foldLeft[A, S](z: S)(f: (S, A) => S): ZSink[Any, Nothing, Nothing, A, S] =
    fold(z)(_ => true)((s, a) => (f(s, a), Chunk.empty))

  /**
   * Creates a sink by effectfully folding over a structure of type `S`.
   */
  def foldLeftM[R, E, A, S](z: S)(f: (S, A) => ZIO[R, E, S]): ZSink[R, E, Nothing, A, S] =
    foldM(z)(_ => true)((s, a) => f(s, a).map((_, Chunk.empty)))

  /**
   * Creates a sink by effectfully folding over a structure of type `S`.
   */
  def foldM[R, E, A0, A, S](
    z: S
  )(contFn: S => Boolean)(f: (S, A) => ZIO[R, E, (S, Chunk[A0])]): ZSink[R, E, A0, A, S] =
    new ZSink[R, E, A0, A, S] {
      type State = (S, Chunk[A0])
      val initial                  = UIO.succeedNow((z, Chunk.empty))
      def step(state: State, a: A) = f(state._1, a)
      def extract(state: State)    = UIO.succeedNow(state)
      def cont(state: State)       = contFn(state._1)
    }

  /**
   * Creates a sink that effectfully folds elements of type `A` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`) have
   * been folded.
   *
   * @note Elements that have an individual cost larger than `max` will
   * cause the stream to hang. See [[ZSink.foldWeightedDecomposeM]] for
   * a variant that can handle these.
   */
  def foldWeightedM[R, R1 <: R, E, E1 >: E, A, S](
    z: S
  )(
    costFn: A => ZIO[R, E, Long],
    max: Long
  )(f: (S, A) => ZIO[R1, E1, S]): ZSink[R1, E1, A, A, S] =
    foldWeightedDecomposeM[R, R1, E1, E1, A, S](z)(costFn, max, (a: A) => UIO.succeedNow(Chunk.single(a)))(f)

  /**
   * Creates a sink that effectfully folds elements of type `A` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`) have
   * been folded.
   *
   * The `decompose` function will be used for decomposing elements that
   * cause an `S` aggregate to cross `max` into smaller elements. See
   * [[ZSink.foldWeightedDecompose]] for an example.
   */
  def foldWeightedDecomposeM[R, R1 <: R, E, E1 >: E, A, S](
    z: S
  )(
    costFn: A => ZIO[R, E, Long],
    max: Long,
    decompose: A => ZIO[R, E, Chunk[A]]
  )(f: (S, A) => ZIO[R1, E1, S]): ZSink[R1, E1, A, A, S] =
    new ZSink[R1, E1, A, A, S] {
      type State = FoldWeightedState
      case class FoldWeightedState(s: S, cost: Long, cont: Boolean, leftovers: Chunk[A])

      val initial = UIO.succeedNow(FoldWeightedState(z, 0L, true, Chunk.empty))

      def step(state: State, a: A) =
        costFn(a).flatMap { cost =>
          val newCost = cost + state.cost

          if (newCost > max)
            decompose(a).map(leftovers => state.copy(cont = false, leftovers = state.leftovers ++ leftovers))
          else if (newCost == max)
            f(state.s, a).map(FoldWeightedState(_, newCost, false, Chunk.empty))
          else
            f(state.s, a).map(FoldWeightedState(_, newCost, true, Chunk.empty))
        }

      def extract(state: State) = UIO.succeedNow((state.s, state.leftovers))

      def cont(state: State) = state.cont
    }

  /**
   * Creates a sink that folds elements of type `A` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`)
   * have been folded.
   *
   * @note Elements that have an individual cost larger than `max` will
   * cause the stream to hang. See [[ZSink.foldWeightedDecompose]] for
   * a variant that can handle these.
   */
  def foldWeighted[A, S](
    z: S
  )(costFn: A => Long, max: Long)(
    f: (S, A) => S
  ): ZSink[Any, Nothing, A, A, S] =
    foldWeightedDecompose(z)(costFn, max, (a: A) => Chunk.single(a))(f)

  /**
   * Creates a sink that folds elements of type `A` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`)
   * have been folded.
   *
   * The `decompose` function will be used for decomposing elements that
   * cause an `S` aggregate to cross `max` into smaller elements. For
   * example:
   * {{{
   * Stream(1, 5, 1)
   *  .aggregate(
   *    Sink
   *      .foldWeightedDecompose(List[Int]())((i: Int) => i.toLong, 4,
   *        (i: Int) => Chunk(i - 1, 1)) { (acc, el) =>
   *        el :: acc
   *      }
   *      .map(_.reverse)
   *  )
   *  .runCollect
   * }}}
   *
   * The stream would emit the elements `List(1), List(4), List(1, 1)`.
   * The [[ZSink.foldWeightedDecomposeM]] allows the decompose function
   * to return a `ZIO` value, and consequently it allows the sink to fail.
   */
  def foldWeightedDecompose[A, S](
    z: S
  )(costFn: A => Long, max: Long, decompose: A => Chunk[A])(
    f: (S, A) => S
  ): ZSink[Any, Nothing, A, A, S] =
    new SinkPure[Nothing, A, A, S] {
      type State = FoldWeightedState
      case class FoldWeightedState(s: S, cost: Long, cont: Boolean, leftovers: Chunk[A])

      val initialPure = FoldWeightedState(z, 0L, true, Chunk.empty)

      def stepPure(state: State, a: A) = {
        val newCost = costFn(a) + state.cost

        if (newCost > max)
          state.copy(cont = false, leftovers = state.leftovers ++ decompose(a))
        else if (newCost == max)
          FoldWeightedState(f(state.s, a), newCost, false, Chunk.empty)
        else
          FoldWeightedState(f(state.s, a), newCost, true, Chunk.empty)
      }

      def extractPure(state: State) = Right((state.s, state.leftovers))

      def cont(state: State) = state.cont
    }

  /**
   * Creates a sink that effectfully folds elements of type `A` into a structure
   * of type `S` until `max` elements have been folded.
   *
   * Like [[ZSink.foldWeightedM]], but with a constant cost function of 1.
   */
  def foldUntilM[R, E, S, A](z: S, max: Long)(f: (S, A) => ZIO[R, E, S]): ZSink[R, E, A, A, S] =
    foldWeightedM[R, R, E, E, A, S](z)(_ => UIO.succeedNow(1), max)(f)

  /**
   * Creates a sink that folds elements of type `A` into a structure
   * of type `S` until `max` elements have been folded.
   *
   * Like [[ZSink.foldWeighted]], but with a constant cost function of 1.
   */
  def foldUntil[S, A](z: S, max: Long)(f: (S, A) => S): ZSink[Any, Nothing, A, A, S] =
    foldWeighted[A, S](z)(_ => 1, max)(f)

  /**
   * Creates a single-value sink produced from an effect
   */
  def fromEffect[R, E, B](b: => ZIO[R, E, B]): ZSink[R, E, Nothing, Any, B] =
    new ZSink[R, E, Nothing, Any, B] {
      type State = Unit
      val initial                    = IO.unit
      def step(state: State, a: Any) = IO.unit
      def extract(state: State)      = b.map((_, Chunk.empty))
      def cont(state: State)         = false
    }

  /**
   * Creates a sink that purely transforms incoming values.
   */
  def fromFunction[A, B](f: A => B): ZSink[Any, Unit, Nothing, A, B] =
    identity.map(f)

  /**
   * Creates a sink that effectfully transforms incoming values.
   */
  def fromFunctionM[R, E, A, B](f: A => ZIO[R, E, B]): ZSink[R, Option[E], Nothing, A, B] =
    identity.mapError(_ => None).mapM(f(_).mapError(Some(_)))

  /**
   * Creates a sink halting with a specified cause.
   */
  def halt[E](e: => Cause[E]): ZSink[Any, E, Nothing, Any, Nothing] =
    new Sink[E, Nothing, Any, Nothing] {
      type State = Unit
      val initial                    = UIO.unit
      def step(state: State, a: Any) = UIO.unit
      def extract(state: State)      = IO.halt(e)
      def cont(state: State)         = false
    }

  /**
   * Creates a sink by that merely passes on incoming values.
   */
  def identity[A]: ZSink[Any, Unit, Nothing, A, A] =
    new SinkPure[Unit, Nothing, A, A] {
      type State = Option[A]
      val initialPure                  = None
      def stepPure(state: State, a: A) = Some(a)
      def extractPure(state: State)    = state.fold[Either[Unit, A]](Left(()))(a => Right(a)).map((_, Chunk.empty))
      def cont(state: State)           = state.isEmpty
    }

  /**
   * Creates a sink by starts consuming value as soon as one fails
   * the predicate `p`.
   */
  def ignoreWhile[A](p: A => Boolean): ZSink[Any, Nothing, A, A, Unit] =
    new SinkPure[Nothing, A, A, Unit] {
      type State = Chunk[A]
      val initialPure                  = Chunk.empty
      def stepPure(state: State, a: A) = if (p(a)) state else Chunk.single(a)
      def extractPure(state: State)    = Right(((), state))
      def cont(state: State)           = state.isEmpty
    }

  /**
   * Creates a sink by starts consuming value as soon as one fails
   * the effectful predicate `p`.
   */
  def ignoreWhileM[R, E, A](p: A => ZIO[R, E, Boolean]): ZSink[R, E, A, A, Unit] =
    new ZSink[R, E, A, A, Unit] {
      type State = Chunk[A]
      val initial                  = IO.succeedNow(Chunk.empty)
      def step(state: State, a: A) = p(a).map(if (_) state else Chunk.single(a))
      def extract(state: State)    = IO.succeedNow(((), state))
      def cont(state: State)       = state.isEmpty
    }

  /**
   * Returns a sink that must at least perform one extraction or else
   * will "fail" with `end`.
   */
  def pull1[R, R1 <: R, E, A0, A, B](
    end: ZIO[R1, E, B]
  )(input: A => ZSink[R, E, A0, A, B]): ZSink[R1, E, A0, A, B] =
    new ZSink[R1, E, A0, A, B] {
      type State = Option[(ZSink[R1, E, A0, A, B], Any)]

      val initial = IO.succeedNow(None)

      def step(state: State, a: A) =
        state match {
          case None =>
            val sink = input(a)
            sink.initial.map(s => Some((sink, s)))
          case Some((sink, state)) =>
            sink.step(state.asInstanceOf[sink.State], a).map(state => Some(sink -> state))
        }

      def extract(state: State) =
        state match {
          case None                => end.map((_, Chunk.empty))
          case Some((sink, state)) => sink.extract(state.asInstanceOf[sink.State])
        }

      def cont(state: State) = state.fold(true) {
        case ((sink, s)) => sink.cont(s.asInstanceOf[sink.State])
      }
    }

  /**
   * Creates a sink that consumes the first value verifying the predicate `p`
   * or fails as soon as the sink won't make any more progress.
   */
  def read1[E, A](e: Option[A] => E)(p: A => Boolean): ZSink[Any, E, A, A, A] =
    new SinkPure[E, A, A, A] {
      type State = (Either[E, Option[A]], Chunk[A])

      val initialPure = (Right(None), Chunk.empty)

      def stepPure(state: State, a: A) =
        state match {
          case (Right(Some(_)), _) => (state._1, Chunk(a))
          case (Right(None), _) =>
            if (p(a)) (Right(Some(a)), Chunk.empty)
            else (Left(e(Some(a))), Chunk.single(a))
          case s => (s._1, Chunk.single(a))
        }

      def extractPure(state: State) =
        state match {
          case (Right(Some(a)), _) => Right((a, state._2))
          case (Right(None), _)    => Left(e(None))
          case (Left(e), _)        => Left(e)
        }

      def cont(state: State) =
        state match {
          case (Right(None), _) => true
          case _                => false
        }
    }

  /**
   * Splits strings on newlines. Handles both `\r\n` and `\n`.
   */
  val splitLines: ZSink[Any, Nothing, String, String, Chunk[String]] =
    new SinkPure[Nothing, String, String, Chunk[String]] {
      type State = SplitLinesState
      case class SplitLinesState(
        accumulatedLines: Chunk[String],
        concat: Option[String],
        wasSplitCRLF: Boolean,
        cont: Boolean,
        leftover: Chunk[String]
      )

      val initialPure = SplitLinesState(Chunk.empty, None, false, true, Chunk.empty)

      override def stepPure(state: State, a: String) = {
        val accumulatedLines = state.accumulatedLines
        val concat           = state.concat.getOrElse("") + a
        val wasSplitCRLF     = state.wasSplitCRLF

        if (concat.isEmpty) state
        else {
          val buf = mutable.ArrayBuffer[String]()

          var i =
            // If we had a split CRLF, we start reading from the last character of the
            // leftover (which was the '\r')
            if (wasSplitCRLF) state.concat.map(_.length).getOrElse(1) - 1
            // Otherwise we just skip over the entire previous leftover as it doesn't
            // contain a newline.
            else state.concat.map(_.length).getOrElse(0)

          var sliceStart = 0
          var splitCRLF  = false

          while (i < concat.length) {
            if (concat(i) == '\n') {
              buf += concat.substring(sliceStart, i)
              i += 1
              sliceStart = i
            } else if (concat(i) == '\r' && (i + 1 < concat.length) && (concat(i + 1) == '\n')) {
              buf += concat.substring(sliceStart, i)
              i += 2
              sliceStart = i
            } else if (concat(i) == '\r' && (i == concat.length - 1)) {
              splitCRLF = true
              i += 1
            } else {
              i += 1
            }
          }

          if (buf.isEmpty) SplitLinesState(accumulatedLines, Some(concat), splitCRLF, true, Chunk.empty)
          else {
            val newLines = Chunk.fromArray(buf.toArray[String])
            val leftover = concat.substring(sliceStart, concat.length)

            if (splitCRLF) SplitLinesState(accumulatedLines ++ newLines, Some(leftover), splitCRLF, true, Chunk.empty)
            else {
              val remainder = if (leftover.nonEmpty) Chunk.single(leftover) else Chunk.empty
              SplitLinesState(accumulatedLines ++ newLines, None, splitCRLF, false, remainder)
            }
          }
        }
      }

      override def extractPure(state: State) =
        Right((state.accumulatedLines ++ state.concat.map(Chunk.single(_)).getOrElse(Chunk.empty), state.leftover))

      def cont(state: State) = state.cont
    }

  /**
   * Merges chunks of strings and splits them on newlines. Handles both
   * `\r\n` and `\n`.
   */
  val splitLinesChunk: ZSink[Any, Nothing, Chunk[String], Chunk[String], Chunk[String]] =
    splitLines.contramap[Chunk[String]](_.mkString).mapRemainder(Chunk.single)

  /**
   * Splits strings on a delimiter.
   */
  def splitOn(delimiter: String): ZSink[Any, Nothing, String, String, Chunk[String]] =
    new SinkPure[Nothing, String, String, Chunk[String]] {
      type State = SplitOnState
      case class SplitOnState(
        // Index into the delimiter
        delimiterPointer: Int,
        // Index into the current frame
        framePointer: Int,
        // Signals when extraction of the delimiter is ongoing
        cont: Boolean,
        // Accumulated strings from previous pulls
        leftover: String,
        // Frames already collected
        frames: Chunk[String]
      )

      def initialPure: State = SplitOnState(0, 0, true, "", Chunk.empty)

      def extractPure(state: State): Either[Nothing, (Chunk[String], Chunk[String])] = {
        val (frames, leftover) =
          if (state.cont && !state.leftover.isEmpty)
            state.frames + state.leftover -> Chunk.empty
          else
            state.frames -> Chunk.single(state.leftover)
        Right(frames -> leftover)
      }

      def stepPure(s: State, a: String): State = {
        val frame = s.leftover + a
        val l     = delimiter.length
        val m     = frame.length

        var start = 0
        var i     = s.delimiterPointer
        var j     = s.framePointer

        val buf = mutable.ArrayBuffer[String]()

        while (j < m) {
          while (i < l && j < m && delimiter(i) == frame(j)) {
            i += 1
            j += 1
          }

          if (i == l) {
            // We've found a new frame, store it (_without_ the delimiter),
            // reset the delimiter pointer and advance the start pointer
            buf += frame.substring(start, j - l)
            i = 0
            start = j
          } else if (j == m) {
            // We've reached the end of the frame in the middle of the
            // delimiter; hence, we keep the indices intact so we can
            // start from where we left off in the next step
          } else {
            // We've found a character that does not match the delimiter,
            // reset the delimiter pointer and advance the frame pointer
            // until we find a character that matches, or reach the end
            // of the frame
            i = 0
            while (j < m && frame(j) != delimiter(0)) j += 1
          }
        }

        if (buf.isEmpty) SplitOnState(i, j, true, frame, s.frames)
        else {
          val frames = Chunk.fromArray(buf.toArray[String])
          SplitOnState(0, j - start, i > 0, frame.drop(start), s.frames ++ frames)
        }
      }

      def cont(state: State): Boolean = state.cont
    }

  /**
   * Frames a stream of chunks according to a delimiter.
   *
   * This is designed for use with `ZStream#aggregate`.
   * Regardless of how the input stream is chunked, the output stream will emit
   * a single chunk for each occurrence of the delimiter. The delimiters are
   * not included in the output.
   *
   * Example:
   * {{{
   *   val stream = Stream(Chunk(1), Chunk(2, 3, 100), Chunk(101, 4))
   *   val sink = ZSink.splitOn(Chunk(100, 101), 100)
   *   stream.aggregate(sink).runCollect
   *   // List(Chunk(1, 2, 3), Chunk(4))
   * }}}
   *
   * @param delimiter The delimiter to use for framing.
   * @param maxFrameLength The maximum frame length allowed. If more than this
   *                       many elements appears without the delimiter,
   *                       this sink fails with `IllegalArgumentException`.
   *                       None of the chunks emitted will be larger than this.
   */
  def splitOn[A](
    delimiter: Chunk[A],
    maxFrameLength: Int
  ): ZSink[Any, IllegalArgumentException, Chunk[A], Chunk[A], Chunk[A]] = {

    val delimiterLength = delimiter.length

    def findDelimiter[B](
      pos: Int
    )(chunk: Chunk[B]): Option[(Chunk[B], Chunk[B])] = {

      @scala.annotation.tailrec
      def go(pos: Int): Option[(Chunk[B], Chunk[B])] = {
        val compare = chunk.drop(pos).take(delimiterLength)
        if (compare.length < delimiterLength) {
          None
        } else if (compare == delimiter) {
          val (matched, remaining) = chunk.splitAt(pos)
          Some((matched, remaining.drop(delimiterLength)))
        } else {
          go(pos + 1)
        }
      }

      go(pos)
    }

    ZSink
      .foldM((true, Chunk.empty: Chunk[A]))(_._1) { (acc, in: Chunk[A]) =>
        val buffer       = acc._2
        val searchBuffer = buffer ++ in
        findDelimiter(math.max(0, buffer.length - delimiterLength + 1))(searchBuffer).map {
          case (found, remaining) =>
            if (found.length > maxFrameLength) {
              ZIO.fail(new IllegalArgumentException(s"Delimiter not found within $maxFrameLength elements"))
            } else {
              ZIO.succeedNow(((false, found), Chunk.single(remaining)))
            }
        }.getOrElse {
          if (searchBuffer.length > maxFrameLength) {
            ZIO.fail(new IllegalArgumentException(s"Delimiter not found within $maxFrameLength elements"))
          } else {
            ZIO.succeedNow(((true, searchBuffer), Chunk.empty))
          }
        }
      }
      .map(_._2)
  }

  /**
   * Creates a single-value sink from a value.
   */
  def succeed[A, B](b: => B): ZSink[Any, Nothing, A, A, B] =
    new SinkPure[Nothing, A, A, B] {
      type State = Chunk[A]
      val initialPure                  = Chunk.empty
      def stepPure(state: State, a: A) = state ++ Chunk(a)
      def extractPure(state: State)    = Right((b, state))
      def cont(state: State)           = false
    }

  /**
   * Creates a sink which sums elements, provided they are Numeric
   */
  def sum[A](implicit ev: Numeric[A]): ZSink[Any, Nothing, Nothing, A, A] = {
    val numeric = ev
    foldLeft(numeric.zero) {
      case (acc, a) =>
        numeric.plus(acc, a)
    }
  }

  /**
   * Creates a sink which throttles input elements of type A according to the given bandwidth parameters
   * using the token bucket algorithm. The sink allows for burst in the processing of elements by allowing
   * the token bucket to accumulate tokens up to a `units + burst` threshold. Elements that do not meet the
   * bandwidth constraints are dropped. The weight of each element is determined by the `costFn` function.
   * Elements are mapped to `Option[A]`, and `None` denotes that a given element has been dropped.
   */
  def throttleEnforce[A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => Long
  ): ZManaged[Clock, Nothing, ZSink[Clock, Nothing, Nothing, A, Option[A]]] =
    throttleEnforceM[Any, Nothing, A](units, duration, burst)(a => UIO.succeedNow(costFn(a)))

  /**
   * Creates a sink which throttles input elements of type A according to the given bandwidth parameters
   * using the token bucket algorithm. The sink allows for burst in the processing of elements by allowing
   * the token bucket to accumulate tokens up to a `units + burst` threshold. Elements that do not meet the
   * bandwidth constraints are dropped. The weight of each element is determined by the `costFn` effectful function.
   * Elements are mapped to `Option[A]`, and `None` denotes that a given element has been dropped.
   */
  def throttleEnforceM[R, E, A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => ZIO[R, E, Long]
  ): ZManaged[Clock, Nothing, ZSink[R with Clock, E, Nothing, A, Option[A]]] = {
    import ZSink.internal._

    val maxTokens = if (units + burst < 0) Long.MaxValue else units + burst

    def bucketSink(bucket: Ref[(Long, Long)]) =
      new ZSink[R with Clock, E, Nothing, A, Option[A]] {
        type State = (Ref[(Long, Long)], Option[A], Boolean)

        val initial = UIO.succeedNow((bucket, None, true))

        def step(state: State, a: A) =
          for {
            weight  <- costFn(a)
            current <- clock.nanoTime
            result <- state._1.modify {
                       case (tokens, timestamp) =>
                         val elapsed   = current - timestamp
                         val cycles    = elapsed.toDouble / duration.toNanos
                         val available = checkTokens(tokens + (cycles * units).toLong, maxTokens)
                         if (weight <= available)
                           ((state._1, Some(a), false), (available - weight, current))
                         else
                           ((state._1, None, false), (available, current))
                     }
          } yield result

        def extract(state: State) = UIO.succeedNow((state._2, Chunk.empty))

        def cont(state: State) = state._3
      }

    def checkTokens(sum: Long, max: Long): Long = if (sum < 0) max else math.min(sum, max)

    val sink = for {
      _       <- assertNonNegative(units)
      _       <- assertNonNegative(burst)
      current <- clock.nanoTime
      bucket  <- Ref.make((units, current))
    } yield bucketSink(bucket)

    ZManaged.fromEffect(sink)
  }

  /**
   * Creates a sink which delays input elements of type A according to the given bandwidth parameters
   * using the token bucket algorithm. The sink allows for burst in the processing of elements by allowing
   * the token bucket to accumulate tokens up to a `units + burst` threshold. The weight of each element is
   * determined by the `costFn` function.
   */
  def throttleShape[A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => Long
  ): ZManaged[Clock, Nothing, ZSink[Clock, Nothing, Nothing, A, A]] =
    throttleShapeM[Any, Nothing, A](units, duration, burst)(a => UIO.succeedNow(costFn(a)))

  /**
   * Creates a sink which delays input elements of type A according to the given bandwidth parameters
   * using the token bucket algorithm. The sink allows for burst in the processing of elements by allowing
   * the token bucket to accumulate tokens up to a `units + burst` threshold. The weight of each element is
   * determined by the `costFn` effectful function.
   */
  def throttleShapeM[R, E, A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => ZIO[R, E, Long]
  ): ZManaged[Clock, Nothing, ZSink[R with Clock, E, Nothing, A, A]] = {
    import ZSink.internal._

    val maxTokens = if (units + burst < 0) Long.MaxValue else units + burst

    def bucketSink(bucket: Ref[(Long, Long)]) =
      new ZSink[R with Clock, E, Nothing, A, A] {
        type State = (Ref[(Long, Long)], Promise[Nothing, A], Boolean)

        val initial = Promise.make[Nothing, A].map((bucket, _, true))

        def step(state: State, a: A) =
          for {
            weight  <- costFn(a)
            current <- clock.nanoTime
            delay <- state._1.modify {
                      case (tokens, timestamp) =>
                        val elapsed    = current - timestamp
                        val cycles     = elapsed.toDouble / duration.toNanos
                        val available  = checkTokens(tokens + (cycles * units).toLong, maxTokens)
                        val remaining  = available - weight
                        val waitCycles = if (remaining >= 0) 0 else -remaining.toDouble / units
                        val delay      = Duration.Finite((waitCycles * duration.toNanos).toLong)
                        (delay, (remaining, current))
                    }
            _ <- if (delay <= Duration.Zero) UIO.unit else clock.sleep(delay)
            _ <- state._2.succeed(a)
          } yield (state._1, state._2, false)

        def extract(state: State) = state._2.await.map((_, Chunk.empty))

        def cont(state: State) = state._3
      }

    def checkTokens(sum: Long, max: Long): Long = if (sum < 0) max else math.min(sum, max)

    val sink = for {
      _       <- assertPositive(units)
      _       <- assertNonNegative(burst)
      current <- clock.nanoTime
      bucket  <- Ref.make((units, current))
    } yield bucketSink(bucket)

    ZManaged.fromEffect(sink)
  }

  /**
   * Decodes chunks of bytes into a String.
   *
   * This sink uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  val utf8DecodeChunk: ZSink[Any, Nothing, Chunk[Byte], Chunk[Byte], String] =
    new SinkPure[Nothing, Chunk[Byte], Chunk[Byte], String] {
      type State = (String, Chunk[Byte], Boolean)

      val initialPure = ("", Chunk.empty, true)

      def is2ByteSequenceStart(b: Byte) = (b & 0xE0) == 0xC0
      def is3ByteSequenceStart(b: Byte) = (b & 0xF0) == 0xE0
      def is4ByteSequenceStart(b: Byte) = (b & 0xF8) == 0xF0

      def computeSplit(chunk: Chunk[Byte]) = {
        // There are 3 bad patterns we need to check to detect an incomplete chunk:
        // - 2/3/4 byte sequences that start on the last byte
        // - 3/4 byte sequences that start on the second-to-last byte
        // - 4 byte sequences that start on the third-to-last byte
        //
        // Otherwise, we can convert the entire concatenated chunk to a string.
        val len = chunk.length

        if (len >= 1 &&
            (is2ByteSequenceStart(chunk(len - 1)) ||
            is3ByteSequenceStart(chunk(len - 1)) ||
            is4ByteSequenceStart(chunk(len - 1))))
          len - 1
        else if (len >= 2 &&
                 (is3ByteSequenceStart(chunk(len - 2)) ||
                 is4ByteSequenceStart(chunk(len - 2))))
          len - 2
        else if (len >= 3 && is4ByteSequenceStart(chunk(len - 3)))
          len - 3
        else len
      }

      def stepPure(state: State, a: Chunk[Byte]) =
        if (a.length == 0) (state._1, state._2, false)
        else {
          val (accumulatedString, prevLeftovers, _) = state
          val concat                                = prevLeftovers ++ a
          val (toConvert, leftovers)                = concat.splitAt(computeSplit(concat))

          if (toConvert.length == 0) (accumulatedString, leftovers, true)
          else
            (
              accumulatedString ++ new String(toConvert.toArray[Byte], "UTF-8"),
              leftovers,
              false
            )
        }

      def extractPure(state: State) = {
        val leftover = if (state._2.isEmpty) Chunk.empty else Chunk.single(state._2)
        Right((state._1, leftover))
      }

      def cont(state: State) = state._3
    }

  private[zio] def succeedNow[A, B](b: B): ZSink[Any, Nothing, A, A, B] =
    succeed(b)
}
