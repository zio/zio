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

package zio.stream

import zio._
import zio.clock.Clock
import zio.duration.Duration

import scala.collection.mutable

/**
 * A `Sink[E, A0, A, B]` consumes values of type `A`, ultimately producing
 * either an error of type `E`, or a value of type `B` together with a remainder
 * of type `A0`.
 *
 * Sinks form monads and combine in the usual ways.
 */
trait ZSink[-R, +E, +A0, -A, +B] { self =>
  import ZSink.Step

  type State

  /**
   * Runs the sink from an initial state and produces a final value
   * of type `B`
   */
  def extract(state: State): ZIO[R, E, B]

  /**
   * The initial state of the sink.
   */
  def initial: ZIO[R, E, Step[State, Nothing]]

  /**
   * Steps through one iteration of the sink
   */
  def step(state: State, a: A): ZIO[R, E, Step[State, A0]]

  /**
   * Operator alias for `zipRight`
   */
  final def *>[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSink[R1, E1, A00, A1, C] =
    zip(that).map(_._2)

  /**
   * Operator alias for `zipLeft`
   */
  final def <*[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSink[R1, E1, A00, A1, B] =
    zip(that).map(_._1)

  /**
   * Operator alias for `zip`
   */
  final def <*>[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSink[R1, E1, A00, A1, (B, C)] =
    self zip that

  /**
   * Operator alias for `orElse` for two sinks consuming and producing values of the same type.
   */
  final def <|[R1 <: R, E1, B1 >: B, A00 >: A0, A1 <: A](
    that: ZSink[R1, E1, A00, A1, B1]
  )(implicit ev: A00 =:= A1, ev2: A1 =:= A00): ZSink[R1, E1, A00, A1, B1] =
    (self orElse that).map(_.merge)

  /**
   * Returns a new sink that tries to produce the `B`, but if there is an
   * error in stepping or extraction, produces `None`.
   */
  final def ? : ZSink[R, Nothing, A0, A, Option[B]] =
    new ZSink[R, Nothing, A0, A, Option[B]] {
      type State = Option[self.State]

      val initial = self.initial.map(Step.leftMap(_)(Option(_))) orElse
        IO.succeed(Step.done(None, Chunk.empty))

      def step(state: State, a: A): ZIO[R, Nothing, Step[State, A0]] =
        state match {
          case None => IO.succeed(Step.done(state, Chunk.empty))
          case Some(state) =>
            self
              .step(state, a)
              .foldM(
                _ => IO.succeed(Step.done[State, A0](Some(state), Chunk.empty)),
                s => IO.succeed(Step.leftMap(s)(Some(_)))
              )
        }

      def extract(state: State): ZIO[R, Nothing, Option[B]] =
        state match {
          case None        => IO.succeed(None)
          case Some(state) => self.extract(state).map(Some(_)) orElse IO.succeed(None)
        }
    }

  /**
   * A named alias for `race`.
   */
  final def |[R1 <: R, E1 >: E, A2 >: A0, A1 <: A, B1 >: B](
    that: ZSink[R1, E1, A2, A1, B1]
  ): ZSink[R1, E1, A2, A1, B1] =
    self.race(that)

  @deprecated("use <*>", "1.0.0")
  final def ~[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSink[R1, E1, A00, A1, (B, C)] =
    zip(that)

  /**
   * Creates a sink that always produces `c`
   */
  final def as[C](c: => C): ZSink[R, E, A0, A, C] = self.map(_ => c)

  /**
   * Replaces any error produced by this sink.
   */
  final def asError[E1](e1: E1): ZSink[R, E1, A0, A, B] = self.mapError(_ => e1)

  /**
   * Takes a `Sink`, and lifts it to be chunked in its input. This
   * will not improve performance, but can be used to adapt non-chunked sinks
   * wherever chunked sinks are required.
   */
  final def chunked[A1 >: A0, A2 <: A]: ZSink[R, E, A1, Chunk[A2], B] =
    new ZSink[R, E, A1, Chunk[A2], B] {
      type State = self.State
      val initial = self.initial
      def step(state: State, a: Chunk[A2]): ZIO[R, E, Step[State, A1]] =
        self.stepChunk(state, a)
      def extract(state: State): ZIO[R, E, B] = self.extract(state)
    }

  /**
   * Accumulates the output into a list.
   */
  final def collectAll[A00 >: A0, A1 <: A](implicit ev: A00 =:= A1): ZSink[R, E, A00, A1, List[B]] =
    collectAllWith[List[B], A00, A1](List.empty[B])((bs, b) => b :: bs).map(_.reverse)

  /**
   * Accumulates the output into a list of maximum size `i`.
   */
  final def collectAllN[A00 >: A0, A1 <: A](i: Int)(implicit ev: A00 =:= A1): ZSink[R, E, A00, A1, List[B]] =
    collectAllWith[(Int, List[B]), A00, A1]((0, Nil))((s, b) => (s._1 + 1, b :: s._2))
      .untilOutput(_._1 >= i)
      .map(_._2.reverse)

  /**
   * Accumulates the output into a value of type `S`.
   */
  final def collectAllWith[S, A00 >: A0, A1 <: A](
    z: S
  )(f: (S, B) => S)(implicit ev: A00 =:= A1): ZSink[R, E, A00, A1, S] =
    new ZSink[R, E, A00, A1, S] {
      type State = (Option[E], S, self.State)

      val initial = self.initial.map(step => Step.leftMap(step)((None, z, _)))

      def step(state: State, a: A1): ZIO[R, E, Step[State, A00]] =
        self
          .step(state._3, a)
          .flatMap { step =>
            if (Step.cont(step)) IO.succeed(Step.more((state._1, state._2, Step.state(step))))
            else {
              val s  = Step.state(step)
              val as = Step.leftover(step)

              self.extract(s).flatMap { b =>
                self.initial.flatMap { init =>
                  self
                    .stepChunk(Step.state(init), as.map(ev))
                    .fold(
                      e => Step.done((Some(e), f(state._2, b), Step.state(init)), Chunk.empty),
                      s => Step.leftMap(s)((state._1, f(state._2, b), _))
                    )
                }
              }
            }
          }

      def extract(state: State): IO[E, S] =
        IO.succeed(state._2)
    }

  /**
   * Accumulates into a list for as long as incoming values verify predicate `p`.
   */
  final def collectAllWhile[A00 >: A0, A1 <: A](
    p: A00 => Boolean
  )(implicit ev: A00 =:= A1, ev2: A1 =:= A00): ZSink[R, E, A00, A1, List[B]] =
    collectAllWhileWith[List[B], A00, A1](p)(List.empty[B])((bs, b) => b :: bs)
      .map(_.reverse)

  /**
   * Accumulates into a value of type `S` for as long as incoming values verify predicate `p`.
   */
  final def collectAllWhileWith[S, A00 >: A0, A1 <: A](
    p: A00 => Boolean
  )(z: S)(f: (S, B) => S)(implicit ev: A00 =:= A1, ev2: A1 =:= A00): ZSink[R, E, A00, A1, S] =
    new ZSink[R, E, A00, A1, S] {
      type State = (S, self.State)

      val initial = self.initial.map(Step.leftMap(_)((z, _)))

      def step(state: State, a: A1): ZIO[R, E, Step[State, A00]] =
        if (!p(a)) self.extract(state._2).map(b => Step.done((f(state._1, b), state._2), Chunk(ev2(a))))
        else
          self.step(state._2, a).flatMap { step =>
            if (Step.cont(step)) IO.succeed(Step.more((state._1, Step.state(step))))
            else {
              val s  = Step.state(step)
              val as = Step.leftover(step)

              self.extract(s).flatMap { b =>
                self.initial.flatMap { init =>
                  self.stepChunk[A1](Step.state(init), as.map(ev)).map(Step.leftMap(_)((f(state._1, b), _)))
                }
              }
            }
          }

      def extract(state: State): IO[E, S] =
        IO.succeed(state._1)
    }

  /**
   * Creates a sink where every element of type `A` entering the sink is first
   * transformed by `f`
   */
  def contramap[C](f: C => A): ZSink[R, E, A0, C, B] =
    new ZSink[R, E, A0, C, B] {
      type State = self.State

      val initial = self.initial

      def step(state: State, c: C) = self.step(state, f(c))

      def extract(state: State): ZIO[R, E, B] = self.extract(state)
    }

  /**
   * Creates a sink where every element of type `A` entering the sink is first
   * transformed by the effectful `f`
   */
  final def contramapM[R1 <: R, E1 >: E, C](f: C => ZIO[R1, E1, A]): ZSink[R1, E1, A0, C, B] =
    new ZSink[R1, E1, A0, C, B] {
      type State = self.State

      val initial = self.initial

      def step(state: State, c: C) = f(c).flatMap(self.step(state, _))

      def extract(state: State): ZIO[R1, E1, B] = self.extract(state)
    }

  @deprecated("use as", "1.0.0")
  final def const[C](c: => C): ZSink[R, E, A0, A, C] = as(c)

  /**
   * Creates a sink that transforms entering values with `f` and
   * outgoing values with `g`
   */
  def dimap[C, D](f: C => A)(g: B => D): ZSink[R, E, A0, C, D] =
    new ZSink[R, E, A0, C, D] {
      type State = self.State

      val initial = self.initial

      def step(state: State, c: C): ZIO[R, E, Step[State, A0]] = self.step(state, f(c))

      def extract(state: State): ZIO[R, E, D] = self.extract(state).map(g)
    }

  /**
   * Drops all elements entering the sink for as long as the specified predicate
   * evaluates to `true`.
   */
  final def dropWhile[A1 <: A](pred: A1 => Boolean): ZSink[R, E, A0, A1, B] =
    new ZSink[R, E, A0, A1, B] {
      type State = (self.State, Boolean)

      val initial = self.initial.map(step => Step.leftMap(step)((_, true)))

      def step(state: State, a: A1): ZIO[R, E, Step[State, A0]] =
        if (!state._2) self.step(state._1, a).map(Step.leftMap(_)((_, false)))
        else {
          if (pred(a)) IO.succeed(ZSink.Step.more((state)))
          else self.step(state._1, a).map(Step.leftMap(_)((_, false)))
        }

      def extract(state: State) = self.extract(state._1)
    }

  /**
   * Creates a sink producing values of type `C` obtained by each produced value of type `B`
   * transformed into a sink by `f`.
   */
  final def flatMap[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    f: B => ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSink[R1, E1, A00, A1, C] =
    new ZSink[R1, E1, A00, A1, C] {
      type State = Either[self.State, (ZSink[R1, E1, A00, A1, C], Any)]

      val initial: ZIO[R1, E1, Step[State, Nothing]] = self.initial.map(Step.leftMap(_)(Left(_)))

      def step(state: State, a: A1): ZIO[R1, E1, Step[State, A00]] = state match {
        case Left(s1) =>
          self.step(s1, a) flatMap { s1 =>
            if (Step.cont(s1)) IO.succeed(Step.more(Left(Step.state(s1))))
            else {
              val as = Step.leftover(s1)

              self.extract(Step.state(s1)).flatMap { b =>
                val that = f(b)

                that.initial.flatMap(
                  s2 =>
                    that
                      .stepChunk[A1](Step.state(s2), as.map(ev))
                      .map(Step.leftMap(_)(s2 => Right((that, s2)))): ZIO[R1, E1, Step[State, A00]] // TODO: Dotty doesn't infer this properly
                )
              }
            }
          }

        case Right((that, s2)) =>
          that.step(s2.asInstanceOf[that.State], a).map(Step.leftMap(_)(s2 => Right((that, s2))))
      }

      def extract(state: State): ZIO[R1, E1, C] =
        state match {
          case Left(s1) =>
            self.extract(s1).flatMap { b =>
              val that = f(b)

              that.initial.flatMap(s2 => that.extract(Step.state(s2)))
            }

          case Right((that, s2)) => that.extract(s2.asInstanceOf[that.State])
        }
    }

  /**
   * Filters the inputs fed to this sink.
   */
  def filter[A1 <: A](f: A1 => Boolean): ZSink[R, E, A0, A1, B] =
    new ZSink[R, E, A0, A1, B] {
      type State = self.State

      val initial = self.initial

      def step(state: State, a: A1) =
        if (f(a)) self.step(state, a)
        else IO.succeed(Step.more(state))

      def extract(state: State) = self.extract(state)
    }

  /**
   * Effectfully filters the inputs fed to this sink.
   */
  final def filterM[R1 <: R, E1 >: E, A1 <: A](f: A1 => ZIO[R1, E1, Boolean]): ZSink[R1, E1, A0, A1, B] =
    new ZSink[R1, E1, A0, A1, B] {
      type State = self.State

      val initial = self.initial

      def step(state: State, a: A1) =
        f(a).flatMap(
          b =>
            if (b) self.step(state, a)
            else IO.succeed(Step.more(state))
        )

      def extract(state: State) = self.extract(state)
    }

  /**
   * Filters this sink by the specified predicate, dropping all elements for
   * which the predicate evaluates to true.
   */
  final def filterNot[A1 <: A](f: A1 => Boolean): ZSink[R, E, A0, A1, B] =
    filter(a => !f(a))

  /**
   * Effectfully ilters this sink by the specified predicate, dropping all elements for
   * which the predicate evaluates to true.
   */
  final def filterNotM[E1 >: E, A1 <: A](f: A1 => IO[E1, Boolean]): ZSink[R, E1, A0, A1, B] =
    filterM(a => f(a).map(!_))

  final def keyed[A1 <: A, K](f: A1 => K): ZSink[R, E, (K, A0), A1, Map[K, B]] =
    new ZSink[R, E, (K, A0), A1, Map[K, B]] {
      type State = Map[K, self.State]

      val initial: ZIO[R, E, Step[State, Nothing]] =
        self.initial.map(Step.leftMap(_)(default => Map[K, self.State]().withDefaultValue(default)))

      def step(state: State, a: A1): ZIO[R, E, Step[State, (K, A0)]] = {
        val k = f(a)
        self.step(state(k), a).map(Step.bimap(_)(s1 => state + (k -> s1), b => (k, b)))
      }

      def extract(state: State): ZIO[R, E, Map[K, B]] =
        ZIO.foreach(state.toList)(s => self.extract(s._2).map((s._1 -> _))).map(_.toMap)
    }

  /**
   * Maps the value produced by this sink.
   */
  def map[C](f: B => C): ZSink[R, E, A0, A, C] =
    new ZSink[R, E, A0, A, C] {
      type State = self.State

      val initial: ZIO[R, E, Step[State, Nothing]] = self.initial

      def step(state: State, a: A): ZIO[R, E, Step[State, A0]] = self.step(state, a)

      def extract(state: State): ZIO[R, E, C] = self.extract(state).map(f)
    }

  /**
   * Maps any error produced by this sink.
   */
  final def mapError[E1](f: E => E1): ZSink[R, E1, A0, A, B] =
    new ZSink[R, E1, A0, A, B] {
      type State = self.State

      val initial = self.initial.mapError(f)

      def step(state: State, a: A): ZIO[R, E1, Step[State, A0]] =
        self.step(state, a).mapError(f)

      def extract(state: State): ZIO[R, E1, B] =
        self.extract(state).mapError(f)
    }

  /**
   * Effectfully maps the value produced by this sink.
   */
  final def mapM[R1 <: R, E1 >: E, C](f: B => ZIO[R1, E1, C]): ZSink[R1, E1, A0, A, C] =
    new ZSink[R1, E1, A0, A, C] {
      type State = self.State

      val initial: ZIO[R1, E, Step[State, Nothing]] = self.initial

      def step(state: State, a: A): ZIO[R1, E, Step[State, A0]] = self.step(state, a)

      def extract(state: State): ZIO[R1, E1, C] = self.extract(state).flatMap(f)
    }

  /**
   * Maps the remainder produced after this sink is done.
   */
  def mapRemainder[A1](f: A0 => A1): ZSink[R, E, A1, A, B] =
    new ZSink[R, E, A1, A, B] {
      type State = self.State

      val initial = self.initial

      def step(state: State, a: A): ZIO[R, E, Step[State, A1]] =
        self.step(state, a).map(Step.map(_)(f))

      def extract(state: State): ZIO[R, E, B] = self.extract(state)
    }

  /**
   * A named alias for `?`.
   */
  final def optional: ZSink[R, Nothing, A0, A, Option[B]] = self.?

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
  )(implicit ev: A00 =:= A1, ev2: A1 =:= A00): ZSink[R1, E1, A00, A1, Either[B, C]] =
    new ZSink[R1, E1, A00, A1, Either[B, C]] {
      import ZSink.internal._

      type State = (Side[E, self.State, B], Side[E1, that.State, (Chunk[A1], C)])

      val l: ZIO[R, Nothing, Step[Either[E, self.State], Nothing]]   = self.initial.either.map(sequence)
      val r: ZIO[R1, Nothing, Step[Either[E1, that.State], Nothing]] = that.initial.either.map(sequence)
      val initial: ZIO[R1, E1, Step[State, Nothing]] =
        l.zipWithPar(r) { (l, r) =>
          Step.both(Step.leftMap(l)(eitherToSide), Step.leftMap(r)(eitherToSide))
        }

      def eitherToSide[E, A, B](e: Either[E, A]): Side[E, A, B] =
        e match {
          case Left(e)  => Side.Error(e)
          case Right(s) => Side.State(s)
        }

      def extract(state: State): ZIO[R1, E1, Either[B, C]] = {
        def fromRight: ZIO[R1, E1, Either[B, C]] = state._2 match {
          case Side.Value((_, c)) => IO.succeed(Right(c))
          case Side.State(s)      => that.extract(s).map(Right(_))
          case Side.Error(e)      => IO.fail(e)
        }

        state match {
          case (Side.Value(b), _) => IO.succeed(Left(b))
          case (Side.State(s), _) => self.extract(s).foldM(_ => fromRight, b => IO.succeed(Left(b)))
          case _                  => fromRight
        }
      }

      def sequence[E, S, A0](e: Either[E, Step[S, A0]]): Step[Either[E, S], A0] =
        e match {
          case Left(e)                   => Step.done(Left(e), Chunk.empty)
          case Right(s) if Step.cont(s)  => Step.more(Right(Step.state(s)))
          case Right(s) if !Step.cont(s) => Step.done(Right(Step.state(s)), Step.leftover(s))
        }

      def step(state: State, a: A1): ZIO[R1, E1, Step[State, A00]] = {
        val leftStep: ZIO[R, Nothing, Step[Side[E, self.State, B], A00]] =
          state._1 match {
            case Side.State(s) =>
              self
                .step(s, a)
                .foldM[R, Nothing, Step[Side[E, self.State, B], A00]]( // TODO: Dotty doesn't infer this properly
                  e => IO.succeed(Step.done(Side.Error(e), Chunk.empty)),
                  s =>
                    if (Step.cont(s)) IO.succeed(Step.more(Side.State(Step.state(s))))
                    else
                      self
                        .extract(Step.state(s))
                        .fold[Step[Side[E, Nothing, B], A00]]( // TODO: Dotty doesn't infer this properly
                          e => Step.done(Side.Error(e), Step.leftover(s)),
                          b => Step.done(Side.Value(b), Step.leftover(s))
                        )
                )
            case s => IO.succeed(Step.done(s, Chunk.empty))
          }
        val rightStep: ZIO[R1, Nothing, Step[Side[E1, that.State, (Chunk[A1], C)], A00]] =
          state._2 match {
            case Side.State(s) =>
              that
                .step(s, a)
                .foldM[R1, Nothing, Step[Side[E1, that.State, (Chunk[A1], C)], A00]]( // TODO: Dotty doesn't infer this properly
                  e => IO.succeed(Step.done(Side.Error(e), Chunk.empty)),
                  s =>
                    if (Step.cont(s)) IO.succeed(Step.more(Side.State(Step.state(s))))
                    else {
                      that
                        .extract(Step.state(s))
                        .fold[Step[Side[E1, Nothing, (Chunk[A1], C)], A00]]( // TODO: Dotty doesn't infer this properly
                          e => Step.done(Side.Error(e), Step.leftover(s)),
                          c => Step.done(Side.Value((Step.leftover(s).map(ev), c)), Step.leftover(s))
                        )
                    }
                )
            case Side.Value((a0, c)) =>
              val a3 = a0 ++ Chunk(a)

              IO.succeed(Step.done(Side.Value((a3, c)), a3.map(ev2)))

            case s => IO.succeed(Step.done(s, Chunk.empty))
          }

        leftStep.zip(rightStep).flatMap {
          case (s1, s2) =>
            if (Step.cont(s1) && Step.cont(s2))
              IO.succeed(Step.more((Step.state(s1), Step.state(s2))))
            else if (!Step.cont(s1) && !Step.cont(s2)) {
              // Step.Done(s1, a1), Step.Done(s2, a2)
              Step.state(s1) match {
                case Side.Error(_) => IO.succeed(Step.done((Step.state(s1), Step.state(s2)), Step.leftover(s2)))
                case Side.Value(_) => IO.succeed(Step.done((Step.state(s1), Step.state(s2)), Step.leftover(s1)))
                case Side.State(s) =>
                  self
                    .extract(s)
                    .fold[Step[State, A00]]( // TODO: Dotty doesn't infer this properly
                      e => Step.done((Side.Error(e), Step.state(s2)), Step.leftover(s2)),
                      b => Step.done((Side.Value(b), Step.state(s2)), Step.leftover(s1))
                    )
              }
            } else if (Step.cont(s1) && !Step.cont(s2)) IO.succeed(Step.more((Step.state(s1), Step.state(s2))))
            else {
              // Step.Done(s1, a1), Step.More(s2)
              Step.state(s1) match {
                case Side.Error(_) => IO.succeed(Step.more((Step.state(s1), Step.state(s2))))
                case Side.Value(_) => IO.succeed(Step.done((Step.state(s1), Step.state(s2)), Step.leftover(s1)))
                case Side.State(s) =>
                  self.extract(s).fold(Side.Error(_), Side.Value(_)).map(s1 => Step.more((s1, Step.state(s2))))
              }

            }
        }
      }
    }

  /**
   * Narrows the environment by partially building it with `f`
   */
  final def provideSome[R1](f: R1 => R): ZSink[R1, E, A0, A, B] =
    new ZSink[R1, E, A0, A, B] {
      type State = self.State

      val initial = self.initial.provideSome(f)

      def step(state: State, a: A): ZIO[R1, E, Step[State, A0]] =
        self.step(state, a).provideSome(f)

      def extract(state: State): ZIO[R1, E, B] =
        self.extract(state).provideSome(f)
    }

  /**
   * Runs both sinks in parallel on the input, returning the result from the
   * one that finishes successfully first.
   */
  final def race[R1 <: R, E1 >: E, A2 >: A0, A1 <: A, B1 >: B](
    that: ZSink[R1, E1, A2, A1, B1]
  ): ZSink[R1, E1, A2, A1, B1] =
    self.raceBoth(that).map(_.merge)

  /**
   * Steps through a chunk of iterations of the sink
   */
  final def stepChunk[A1 <: A](state: State, as: Chunk[A1]): ZIO[R, E, Step[State, A0]] = {
    val len = as.length

    def loop(s: Step[State, A0], i: Int): ZIO[R, E, Step[State, A0]] =
      if (i >= len) IO.succeed(s)
      else if (Step.cont(s)) self.step(Step.state(s), as(i)).flatMap(loop(_, i + 1))
      else IO.succeed(s)

    loop(Step.more(state), 0)
  }

  /**
   * Runs both sinks in parallel on the input, returning the result from the
   * one that finishes successfully first.
   */
  final def raceBoth[R1 <: R, E1 >: E, A2 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A2, A1, C]
  ): ZSink[R1, E1, A2, A1, Either[B, C]] =
    new ZSink[R1, E1, A2, A1, Either[B, C]] {
      type State = (Either[E1, self.State], Either[E1, that.State])

      def sequence[E, S, A0](e: Either[E, Step[S, A0]]): Step[Either[E, S], A0] =
        e match {
          case Left(e)                   => Step.done(Left(e), Chunk.empty)
          case Right(s) if Step.cont(s)  => Step.more(Right(Step.state(s)))
          case Right(s) if !Step.cont(s) => Step.done(Right(Step.state(s)), Step.leftover(s))
        }

      val initial = self.initial.either.map(sequence).zipWithPar(that.initial.either.map(sequence))(Step.both)

      def step(state: State, a: A1): ZIO[R1, E1, Step[State, A2]] =
        state match {
          case (l, r) =>
            val lr = l.fold(e => IO.succeed(Left(e)), self.step(_, a).either)
            val rr = r.fold(e => IO.succeed(Left(e)), that.step(_, a).either)

            lr.zipWithPar(rr) {
              case (Right(s), _) if !Step.cont(s) => Step.done((Right(Step.state(s)), r), Step.leftover(s))
              case (_, Right(s)) if !Step.cont(s) =>
                Step.done((l, Right(Step.state(s))), Step.leftover(s)): Step[State, A2] // TODO: Dotty doesn't infer this properly
              case (lr, rr) => Step.more((lr.map(Step.state), rr.map(Step.state)))
            }
        }

      def extract(state: State): ZIO[R1, E1, Either[B, C]] =
        state match {
          case (Right(s), _) => self.extract(s).map(Left(_))
          case (_, Right(s)) => that.extract(s).map(Right(_))
          case (Left(e), _)  => IO.fail(e)
          case (_, Left(e))  => IO.fail(e)
        }
    }

  /**
   * Runs both sinks in parallel on the input and combines the results into a Tuple.
   */
  final def zipPar[R1 <: R, E1 >: E, A2 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A2, A1, C]
  ): ZSink[R1, E1, A2, A1, (B, C)] =
    new ZSink[R1, E1, A2, A1, (B, C)] {
      type State = (Either[B, self.State], Either[C, that.State])

      override def extract(state: State): ZIO[R1, E1, (B, C)] = {
        val b: ZIO[R, E, B]   = state._1.fold(ZIO.succeed, self.extract)
        val c: ZIO[R1, E1, C] = state._2.fold(ZIO.succeed, that.extract)
        b.zipPar(c)
      }

      override def initial: ZIO[R1, E1, Step[State, Nothing]] =
        self.initial.flatMap { s1 =>
          that.initial.flatMap { s2 =>
            (Step.cont(s1), Step.cont(s2)) match {
              case (false, false) =>
                val zb = self.extract(Step.state(s1))
                val zc = that.extract(Step.state(s2))
                zb.zipWithPar(zc)((b, c) => Step.done((Left(b), Left(c)), Chunk.empty))

              case (false, true) =>
                val zb = self.extract(Step.state(s1))
                zb.map(b => Step.more((Left(b), Right(Step.state(s2)))))

              case (true, false) =>
                val zc = that.extract(Step.state(s2))
                zc.map(c => Step.more((Right(Step.state(s1)), Left(c))))

              case (true, true) =>
                ZIO.succeed(Step.more((Right(Step.state(s1)), Right(Step.state(s2)))))
            }
          }
        }

      override def step(state: State, a: A1): ZIO[R1, E1, Step[State, A2]] = {
        val firstResult: ZIO[R, E, Either[(B, Option[Chunk[A2]]), self.State]] = state._1.fold(
          b => ZIO.succeed(Left((b, None))),
          s =>
            self
              .step(s, a)
              .flatMap { (st: Step[ZSink.this.State, A0]) =>
                if (Step.cont(st))
                  ZIO.succeed(Right(Step.state(st)))
                else
                  self
                    .extract(Step.state(st))
                    .map(b => Left((b, Some(Step.leftover(st)))))
              }
        )

        val secondResult: ZIO[R1, E1, Either[(C, Option[Chunk[A2]]), that.State]] = state._2.fold(
          c => ZIO.succeed(Left((c, None))),
          s =>
            that
              .step(s, a)
              .flatMap { st =>
                if (Step.cont(st))
                  ZIO.succeed(Right(Step.state(st)))
                else
                  that
                    .extract(Step.state(st))
                    .map(c => {
                      val leftover: Chunk[A2] = (Step.leftover(st))
                      Left((c, Some(leftover)))
                    })
              }
        )

        firstResult.zipPar(secondResult).map {
          case (Left((b, rem1)), Left((c, rem2))) =>
            val minLeftover =
              if (rem1.isEmpty && rem2.isEmpty) Chunk.empty else (rem1.toList ++ rem2.toList).minBy(_.length)
            Step.done((Left(b), Left(c)), minLeftover)

          case (Left((b, _)), Right(s2)) =>
            Step.more((Left(b), Right(s2)))

          case (r: Right[_, _], Left((c, _))) => Step.more((r.asInstanceOf[Either[B, self.State]], Left(c)))
          case rights @ (Right(_), Right(_))  => Step.more(rights.asInstanceOf[State])
        }
      }
    }

  /**
   * Times the invocation of the sink
   */
  final def timed: ZSink[R with Clock, E, A0, A, (Duration, B)] =
    new ZSink[R with Clock, E, A0, A, (Duration, B)] {
      type State = (Long, Long, self.State)
      val initial = for {
        step <- self.initial
        t    <- zio.clock.nanoTime
      } yield Step.leftMap(step)((t, 0L, _))

      def step(state: State, a: A): ZIO[R with Clock, E, Step[State, A0]] = state match {
        case (t, total, st) =>
          for {
            step <- self
                     .step(st, a)
            now <- zio.clock.nanoTime
            t1  = now - t
          } yield Step.leftMap(step)((now, total + t1, _))
      }

      def extract(s: State) = self.extract(s._3).map((Duration.fromNanos(s._2), _))
    }

  /**
   * Produces a sink consuming all the elements of type `A` as long as
   * they verify the predicate `pred`.
   */
  final def takeWhile[A1 <: A](pred: A1 => Boolean): ZSink[R, E, A0, A1, B] =
    new ZSink[R, E, A0, A1, B] {
      type State = self.State

      val initial = self.initial

      def step(state: State, a: A1): ZIO[R, E, Step[State, A0]] =
        if (pred(a)) self.step(state, a)
        else IO.succeed(Step.done(state, Chunk.empty))

      def extract(state: State) = self.extract(state)
    }

  /**
   * Creates a sink that ignores all produced elements.
   */
  final def unit: ZSink[R, E, A0, A, Unit] = as(())

  /**
   * Creates a sink that produces values until one verifies
   * the predicate `f`.
   */
  final def untilOutput(f: B => Boolean): ZSink[R, E, A0, A, B] =
    new ZSink[R, E, A0, A, B] {
      type State = self.State

      val initial = self.initial

      def step(state: State, a: A): ZIO[R, E, Step[State, A0]] =
        self.step(state, a).flatMap { s =>
          if (Step.cont(s))
            extract(Step.state(s))
              .foldM(
                _ => IO.succeed(s),
                b => if (f(b)) IO.succeed(Step.done(Step.state(s), Chunk.empty)) else IO.succeed(s)
              )
          else IO.succeed(s)
        }

      def extract(state: State): ZIO[R, E, B] = self.extract(state)
    }

  final def update(state: Step[State, Nothing]): ZSink[R, E, A0, A, B] =
    new ZSink[R, E, A0, A, B] {
      type State = self.State
      val initial: ZIO[R, E, Step[State, Nothing]]             = IO.succeed(state)
      def step(state: State, a: A): ZIO[R, E, Step[State, A0]] = self.step(state, a)
      def extract(state: State): ZIO[R, E, B]                  = self.extract(state)
    }

  @deprecated("use unit", "1.0.0")
  final def void: ZSink[R, E, A0, A, Unit] = unit

  /**
   * Runs two sinks in unison and matches produced values pair-wise.
   */
  final def zip[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSink[R1, E1, A00, A1, (B, C)] =
    flatMap(b => that.map(c => (b, c)))

  /**
   * Runs two sinks in unison and keeps only values on the left.
   */
  final def zipLeft[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSink[R1, E1, A00, A1, B] =
    self <* that

  /**
   * Runs two sinks in unison and keeps only values on the right.
   */
  final def zipRight[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C](
    that: ZSink[R1, E1, A00, A1, C]
  )(implicit ev: A00 =:= A1): ZSink[R1, E1, A00, A1, C] =
    self *> that

  /**
   * Runs two sinks in unison and merges values pair-wise.
   */
  final def zipWith[R1 <: R, E1 >: E, A00 >: A0, A1 <: A, C, D](
    that: ZSink[R1, E1, A00, A1, C]
  )(f: (B, C) => D)(implicit ev: A00 =:= A1): ZSink[R1, E1, A00, A1, D] =
    zip(that).map(f.tupled)

}

object ZSink extends ZSinkPlatformSpecific {
  private[ZSink] object internal {
    sealed trait Side[+E, +S, +A]
    object Side {
      final case class Error[E](value: E) extends Side[E, Nothing, Nothing]
      final case class State[S](value: S) extends Side[Nothing, S, Nothing]
      final case class Value[A](value: A) extends Side[Nothing, Nothing, A]
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

  trait StepModule {
    type Step[+S, +A0]

    def bimap[S, S1, A, B](s: Step[S, A])(f: S => S1, g: A => B): Step[S1, B]
    def both[S1, S2](s1: Step[S1, Nothing], s2: Step[S2, Nothing]): Step[(S1, S2), Nothing]
    def cont[S, A0](s: Step[S, A0]): Boolean
    def done[@specialized S, A0](s: S, a0: Chunk[A0]): Step[S, A0]
    def either[S1, A0](s1: Step[S1, A0], s2: Step[S1, A0]): Step[S1, A0]
    def leftover[S, A0](s: Step[S, A0]): Chunk[A0]
    def leftMap[S, A0, B](s: Step[S, A0])(f: S => B): Step[B, A0]
    def map[S, A0, B](s: Step[S, A0])(f: A0 => B): Step[S, B]
    def more[S](s: S): Step[S, Nothing]
    def state[S, A0](s: Step[S, A0]): S
  }

  private class Done[@specialized S, A0](val s: S, val leftover: Chunk[A0])

  val Step: StepModule = new StepModule {
    type Step[+S, +A0] = AnyRef

    final def bimap[S, S1, A, B](s: Step[S, A])(f: S => S1, g: A => B): Step[S1, B] =
      s match {
        case x: Done[_, _] => new Done(f(x.s.asInstanceOf[S]), x.leftover.asInstanceOf[Chunk[A]].map(g))
        case x             => f(x.asInstanceOf[S]).asInstanceOf[Step[S1, B]]
      }

    final def both[S1, S2](s1: Step[S1, Nothing], s2: Step[S2, Nothing]): Step[(S1, S2), Nothing] =
      (s1, s2) match {
        case (x: Done[_, _], y: Done[_, _]) => done((state(x), state(y)), Chunk.empty)
        case (x, y: Done[_, _])             => done((state(x), state(y)), Chunk.empty)
        case (x: Done[_, _], y)             => done((state(x), state(y)), Chunk.empty)
        case (x, y)                         => more((state(x), state(y)))
      }

    final def cont[S, A0](s: Step[S, A0]): Boolean =
      !s.isInstanceOf[Done[_, _]]

    final def done[@specialized S, A0](s: S, a0: Chunk[A0]): Step[S, A0] =
      new Done(s, a0)

    final def either[S1, A0](s1: Step[S1, A0], s2: Step[S1, A0]): Step[S1, A0] =
      (s1, s2) match {
        case (x: Done[_, _], _: Done[_, _]) => done(state(x), Chunk.empty)
        case (x, _: Done[_, _])             => more(state(x))
        case (_: Done[_, _], y)             => more(state(y))
        case (x, _)                         => more(state(x))
      }

    final def leftover[S, A0](s: Step[S, A0]): Chunk[A0] =
      s match {
        case x: Done[_, _] => x.leftover.asInstanceOf[Chunk[A0]]
        case _             => Chunk.empty
      }

    final def leftMap[S, A0, B](s: Step[S, A0])(f: S => B): Step[B, A0] =
      s match {
        case x: Done[_, _] => new Done(f(x.s.asInstanceOf[S]), x.leftover)
        case x             => f(x.asInstanceOf[S]).asInstanceOf[Step[B, A0]]
      }

    final def map[S, A, B](s: Step[S, A])(f: A => B): Step[S, B] =
      s match {
        case x: Done[_, _] => new Done(x.s.asInstanceOf[S], x.leftover.asInstanceOf[Chunk[A]].map(f))
        case x             => x.asInstanceOf[Step[S, B]]
      }

    final def more[S](s: S): Step[S, Nothing] = s.asInstanceOf[Step[S, Nothing]]

    final def state[S, A0](s: Step[S, A0]): S =
      s match {
        case x: Done[_, _] => x.s.asInstanceOf[S]
        case x             => x.asInstanceOf[S]
      }
  }

  type Step[+S, +A0] = Step.Step[S, A0]

  /**
   * Creates a sink that waits for a single value to be produced.
   */
  final def await[A]: ZSink[Any, Unit, Nothing, A, A] =
    new SinkPure[Unit, Nothing, A, A] {
      type State = Either[Unit, A]

      val initialPure = Step.more(Left(()))

      def stepPure(state: State, a: A) =
        Step.done(Right(a), Chunk.empty)

      def extractPure(state: State): Either[Unit, A] =
        state
    }

  /**
   * Creates a sink accumulating incoming values into a list.
   */
  final def collectAll[A]: ZSink[Any, Nothing, Nothing, A, List[A]] =
    fold[Nothing, A, List[A]](List.empty[A])((as, a) => Step.more(a :: as)).map(_.reverse)

  /**
   * Creates a sink accumulating incoming values into a list of maximum size `n`.
   */
  def collectAllN[A](n: Long): ZSink[Any, Nothing, A, A, List[A]] =
    foldUntil[List[A], A](List.empty[A], n)((list, element) => element :: list).map(_.reverse)

  /**
   * Creates a sink accumulating incoming values into a set.
   */
  def collectAllToSet[A]: ZSink[Any, Nothing, Nothing, A, Set[A]] =
    fold[Nothing, A, Set[A]](Set.empty[A])((set, element) => Step.more(set + element))

  /**
   * Creates a sink accumulating incoming values into a set of maximum size `n`.
   */
  def collectAllToSetN[A](n: Long): ZSink[Any, Nothing, A, A, Set[A]] = {
    def f(set: Set[A], element: A): ZSink.Step[Set[A], A] = {
      val newSet = set + element
      if (newSet.size > n) Step.done(set, Chunk.single(element))
      else if (newSet.size == n) Step.done[Set[A], A](newSet, Chunk.empty)
      else Step.more(newSet)
    }
    fold[A, A, Set[A]](Set.empty[A])(f)
  }

  /**
   * Creates a sink accumulating incoming values into a map.
   * Key of each element is determined by supplied function.
   */
  def collectAllToMap[K, A](key: A => K): ZSink[Any, Nothing, Nothing, A, Map[K, A]] =
    fold[Nothing, A, Map[K, A]](Map.empty[K, A])((map, element) => Step.more(map + (key(element) -> element)))

  /**
   * Creates a sink accumulating incoming values into a map of maximum size `n`.
   * Key of each element is determined by supplied function.
   */
  def collectAllToMapN[K, A](n: Long)(key: A => K): ZSink[Any, Nothing, A, A, Map[K, A]] = {
    def f(map: Map[K, A], element: A): ZSink.Step[Map[K, A], A] = {
      val newMap = map + (key(element) -> element)
      if (newMap.size > n) Step.done(map, Chunk.single(element))
      else if (newMap.size == n) Step.done[Map[K, A], A](newMap, Chunk.empty)
      else Step.more(newMap)
    }
    fold[A, A, Map[K, A]](Map.empty[K, A])(f)
  }

  /**
   * Accumulates incoming elements into a list as long as they verify predicate `p`.
   */
  final def collectAllWhile[A](p: A => Boolean): ZSink[Any, Nothing, A, A, List[A]] =
    collectAllWhileM(a => IO.succeed(p(a)))

  /**
   * Accumulates incoming elements into a list as long as they verify effectful predicate `p`.
   */
  final def collectAllWhileM[R, E, A](p: A => ZIO[R, E, Boolean]): ZSink[R, E, A, A, List[A]] =
    ZSink
      .foldM[R, E, A, A, List[A]](List.empty[A]) { (s, a: A) =>
        p(a).map(if (_) Step.more(a :: s) else Step.done(s, Chunk(a)))
      }
      .map(_.reverse)

  /**
   * Creates a sink halting with the specified `Throwable`.
   */
  final def die(e: Throwable): ZSink[Any, Nothing, Nothing, Any, Nothing] =
    ZSink.halt(Cause.die(e))

  /**
   * Creates a sink halting with the specified message, wrapped in a
   * `RuntimeException`.
   */
  final def dieMessage(m: String): ZSink[Any, Nothing, Nothing, Any, Nothing] =
    ZSink.halt(Cause.die(new RuntimeException(m)))

  /**
   * Creates a sink consuming all incoming values until completion.
   */
  final def drain: ZSink[Any, Nothing, Nothing, Any, Unit] =
    fold(())((s, _) => Step.more(s))

  /**
   * Creates a sink failing with a value of type `E`.
   */
  final def fail[E](e: E): ZSink[Any, E, Nothing, Any, Nothing] =
    new SinkPure[E, Nothing, Any, Nothing] {
      type State = Unit
      val initialPure                                          = Step.done((), Chunk.empty)
      def stepPure(state: State, a: Any): Step[State, Nothing] = Step.done(state, Chunk.empty)
      def extractPure(state: State): Either[E, Nothing]        = Left(e)
    }

  /**
   * Creates a sink by folding over a structure of type `S`.
   */
  final def fold[A0, A, S](z: S)(f: (S, A) => Step[S, A0]): ZSink[Any, Nothing, A0, A, S] =
    new SinkPure[Nothing, A0, A, S] {
      type State = S
      val initialPure                           = Step.more(z)
      def stepPure(s: S, a: A): Step[S, A0]     = f(s, a)
      def extractPure(s: S): Either[Nothing, S] = Right(s)
    }

  /**
   * Creates a sink by folding over a structure of type `S`.
   */
  final def foldLeft[A, S](z: S)(f: (S, A) => S): ZSink[Any, Nothing, Nothing, A, S] =
    new SinkPure[Nothing, Nothing, A, S] {
      type State = S
      val initialPure                            = Step.more(z)
      def stepPure(s: S, a: A): Step[S, Nothing] = Step.more(f(s, a))
      def extractPure(s: S): Either[Nothing, S]  = Right(s)
    }

  /**
   * Creates a sink by effectfully folding over a structure of type `S`.
   */
  final def foldM[R, E, A0, A, S](z: S)(f: (S, A) => ZIO[R, E, Step[S, A0]]): ZSink[R, E, A0, A, S] =
    new ZSink[R, E, A0, A, S] {
      type State = S
      val initial                                  = UIO.succeed(Step.more(z))
      def step(s: S, a: A): ZIO[R, E, Step[S, A0]] = f(s, a)
      def extract(s: S): ZIO[R, E, S]              = ZIO.succeed(s)
    }

  /**
   * Creates a sink that effectfully folds elements of type `A` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`) have
   * been folded.
   */
  final def foldWeightedM[R, R1 <: R, E, E1 >: E, A, S](
    z: S
  )(costFn: A => ZIO[R, E, Long], max: Long)(f: (S, A) => ZIO[R1, E1, S]): ZSink[R1, E1, A, A, S] =
    new ZSink[R1, E1, A, A, S] {
      type State = (S, Long)
      val initial: UIO[Step[State, Nothing]] = UIO.succeed(Step.more(z -> 0))
      def step(s: (S, Long), a: A): ZIO[R1, E1, Step[(S, Long), A]] =
        costFn(a) flatMap { cost =>
          val newCost = cost + s._2

          if (newCost > max) UIO.succeed(Step.done(s, Chunk.single(a)))
          else if (newCost == max) {
            f(s._1, a).map(s => Step.done(s -> newCost, Chunk.empty))
          } else f(s._1, a).map(s => Step.more(s -> newCost))
        }
      def extract(state: (S, Long)): ZIO[R1, E1, S] = UIO.succeed(state._1)
    }

  /**
   * Creates a sink that folds elements of type `A` into a structure
   * of type `S`, until `max` worth of elements (determined by the `costFn`)
   * have been folded.
   */
  final def foldWeighted[A, S](
    z: S
  )(costFn: A => Long, max: Long)(f: (S, A) => S): ZSink[Any, Nothing, A, A, S] =
    new SinkPure[Nothing, A, A, S] {
      type State = (S, Long)
      def initialPure: Step[(S, Long), Nothing] = Step.more(z -> 0)
      def stepPure(s: (S, Long), a: A): Step[(S, Long), A] = {
        val newCost = costFn(a) + s._2

        if (newCost > max) Step.done(s, Chunk.single(a))
        else if (newCost == max) {
          Step.done(f(s._1, a) -> newCost, Chunk.empty)
        } else Step.more((f(s._1, a), newCost))
      }
      def extractPure(s: (S, Long)): Either[Nothing, S] = Right(s._1)
    }

  /**
   * Creates a sink that effectfully folds elements of type `A` into a structure
   * of type `S` until `max` elements have been folded.
   *
   * Like [[ZSink.foldWeightedM]], but with a constant cost function of 1.
   */
  final def foldUntilM[R, E, S, A](z: S, max: Long)(f: (S, A) => ZIO[R, E, S]): ZSink[R, E, A, A, S] =
    foldWeightedM[R, R, E, E, A, S](z)((_: A) => UIO.succeed(1), max)(f)

  /**
   * Creates a sink that folds elements of type `A` into a structure
   * of type `S` until `max` elements have been folded.
   *
   * Like [[ZSink.foldWeighted]], but with a constant cost function of 1.
   */
  final def foldUntil[S, A](z: S, max: Long)(f: (S, A) => S): ZSink[Any, Nothing, A, A, S] =
    foldWeighted[A, S](z)((_: A) => 1, max)(f)

  /**
   * Creates a single-value sink produced from an effect
   */
  final def fromEffect[R, E, B](b: => ZIO[R, E, B]): ZSink[R, E, Nothing, Any, B] =
    new ZSink[R, E, Nothing, Any, B] {
      type State = Unit
      val initial                                                     = IO.succeed(Step.done((), Chunk.empty))
      def step(state: State, a: Any): ZIO[R, E, Step[State, Nothing]] = IO.succeed(Step.done(state, Chunk.empty))
      def extract(state: State): ZIO[R, E, B]                         = b
    }

  /**
   * Creates a sink that purely transforms incoming values.
   */
  final def fromFunction[A, B](f: A => B): ZSink[Any, Unit, Nothing, A, B] =
    new SinkPure[Unit, Nothing, A, B] {
      type State = Option[A]
      val initialPure                                        = Step.more(None)
      def stepPure(state: State, a: A): Step[State, Nothing] = Step.done(Some(a), Chunk.empty)
      def extractPure(state: State): Either[Unit, B]         = state.fold[Either[Unit, B]](Left(()))(a => Right(f(a)))
    }

  /**
   * Creates a sink halting with a specified cause.
   */
  final def halt[E](e: Cause[E]): ZSink[Any, E, Nothing, Any, Nothing] =
    new Sink[E, Nothing, Any, Nothing] {
      type State = Unit
      val initial                                               = UIO.succeed(Step.done((), Chunk.empty))
      def step(state: State, a: Any): UIO[Step[State, Nothing]] = UIO.succeed(Step.done(state, Chunk.empty))
      def extract(state: State): IO[E, Nothing]                 = IO.halt(e)
    }

  /**
   * Creates a sink by that merely passes on incoming values.
   */
  final def identity[A]: ZSink[Any, Unit, Nothing, A, A] =
    new SinkPure[Unit, Nothing, A, A] {
      type State = Option[A]
      val initialPure                  = Step.more(None)
      def stepPure(state: State, a: A) = Step.done(Some(a), Chunk.empty)
      def extractPure(state: State)    = state.fold[Either[Unit, A]](Left(()))(a => Right(a))
    }

  /**
   * Creates a sink by starts consuming value as soon as one verifies
   * the predicate `p`.
   */
  final def ignoreWhile[A](p: A => Boolean): ZSink[Any, Nothing, A, A, Unit] =
    ignoreWhileM(a => IO.succeed(p(a)))

  /**
   * Creates a sink by starts consuming value as soon as one verifies
   * the effectful predicate `p`.
   */
  final def ignoreWhileM[R, E, A](p: A => ZIO[R, E, Boolean]): ZSink[R, E, A, A, Unit] =
    new ZSink[R, E, A, A, Unit] {
      type State = Unit

      val initial = IO.succeed(Step.more(()))

      def step(state: State, a: A): ZIO[R, E, Step[State, A]] =
        p(a).map(if (_) Step.more(()) else Step.done((), Chunk(a)))

      def extract(state: State) = IO.succeed(())
    }

  /**
   * Returns a sink that must at least perform one extraction or else
   * will "fail" with `end`.
   */
  final def pull1[R, R1 <: R, E, A0, A, B](
    end: ZIO[R1, E, B]
  )(input: A => ZSink[R, E, A0, A, B]): ZSink[R1, E, A0, A, B] =
    new ZSink[R1, E, A0, A, B] {
      type State = Option[(ZSink[R1, E, A0, A, B], Any)]

      val initial = IO.succeed(Step.more(None))

      def step(state: State, a: A): ZIO[R1, E, Step[State, A0]] = state match {
        case None =>
          val sink = input(a)

          sink.initial.map(state => Step.more(Some((sink, Step.state(state)))))

        case Some((sink, state)) =>
          sink.step(state.asInstanceOf[sink.State], a).map(Step.leftMap(_)(state => Some(sink -> state)))
      }

      def extract(state: State): ZIO[R1, E, B] = state match {
        case None                => end
        case Some((sink, state)) => sink.extract(state.asInstanceOf[sink.State])
      }
    }

  /**
   * Creates a sink that consumes the first value verifying the predicate `p`
   * or fails as soon as the sink won't make any more progress.
   */
  final def read1[E, A](e: Option[A] => E)(p: A => Boolean): ZSink[Any, E, A, A, A] =
    new SinkPure[E, A, A, A] {
      type State = Either[E, Option[A]]

      val initialPure = Step.more(Right(None))

      def stepPure(state: State, a: A) =
        state match {
          case Right(Some(_)) => Step.done(state, Chunk(a))
          case Right(None) =>
            if (p(a)) Step.done(Right(Some(a)), Chunk.empty)
            else Step.done(Left(e(Some(a))), Chunk(a))
          case s => Step.done(s, Chunk(a))
        }

      def extractPure(state: State) =
        state match {
          case Right(Some(a)) => Right(a)
          case Right(None)    => Left(e(None))
          case Left(e)        => Left(e)
        }
    }

  /**
   * Splits strings on newlines. Handles both `\r\n` and `\n`.
   */
  final val splitLines: ZSink[Any, Nothing, String, String, Chunk[String]] =
    new SinkPure[Nothing, String, String, Chunk[String]] {
      type State = (Chunk[String], Option[String], Boolean)

      override val initialPure: Step[State, Nothing] = Step.more((Chunk.empty, None, false))

      override def stepPure(s: State, a: String): Step[State, String] = {
        val accumulatedLines = s._1
        val concat           = s._2.getOrElse("") + a
        val wasSplitCRLF     = s._3

        if (concat.isEmpty) Step.more(s)
        else {
          val buf = mutable.ArrayBuffer[String]()

          var i =
            // If we had a split CRLF, we start reading from the last character of the
            // leftover (which was the '\r')
            if (wasSplitCRLF) s._2.map(_.length).getOrElse(1) - 1
            // Otherwise we just skip over the entire previous leftover as it doesn't
            // contain a newline.
            else s._2.map(_.length).getOrElse(0)

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

          if (buf.isEmpty) Step.more((accumulatedLines, Some(concat), splitCRLF))
          else {
            val newLines = Chunk.fromArray(buf.toArray[String])
            val leftover = concat.substring(sliceStart, concat.length)

            if (splitCRLF) Step.more((accumulatedLines ++ newLines, Some(leftover), splitCRLF))
            else
              Step.done(
                (accumulatedLines ++ newLines, None, splitCRLF),
                if (leftover.nonEmpty) Chunk.single(leftover) else Chunk.empty
              )
          }
        }
      }

      override def extractPure(s: State): Either[Nothing, Chunk[String]] =
        Right(s._1 ++ s._2.map(Chunk.single(_)).getOrElse(Chunk.empty))
    }

  /**
   * Merges chunks of strings and splits them on newlines. Handles both
   * `\r\n` and `\n`.
   */
  final val splitLinesChunk: ZSink[Any, Nothing, Chunk[String], Chunk[String], Chunk[String]] =
    splitLines.contramap[Chunk[String]](_.mkString).mapRemainder(Chunk.single)

  /**
   * Creates a single-value sink from a value.
   */
  final def succeed[B](b: B): ZSink[Any, Nothing, Nothing, Any, B] =
    new SinkPure[Nothing, Nothing, Any, B] {
      type State = Unit
      val initialPure                                          = Step.done((), Chunk.empty)
      def stepPure(state: State, a: Any): Step[State, Nothing] = Step.done(state, Chunk.empty)
      def extractPure(state: State): Either[Nothing, B]        = Right(b)
    }

  @deprecated("use succeed", "1.0.0")
  final def succeedLazy[B](b: => B): ZSink[Any, Nothing, Nothing, Any, B] =
    succeed(b)

  /**
   * Creates a sink which throttles input elements of type A according to the given bandwidth parameters
   * using the token bucket algorithm. The sink allows for burst in the processing of elements by allowing
   * the token bucket to accumulate tokens up to a `units + burst` threshold. Elements that do not meet the
   * bandwidth constraints are dropped. The weight of each element is determined by the `costFn` function.
   * Elements are mapped to `Option[A]`, and `None` denotes that a given element has been dropped.
   */
  final def throttleEnforce[A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => Long
  ): ZManaged[Clock, Nothing, ZSink[Clock, Nothing, Nothing, A, Option[A]]] =
    throttleEnforceM[Any, Nothing, A](units, duration, burst)(a => UIO.succeed(costFn(a)))

  /**
   * Creates a sink which throttles input elements of type A according to the given bandwidth parameters
   * using the token bucket algorithm. The sink allows for burst in the processing of elements by allowing
   * the token bucket to accumulate tokens up to a `units + burst` threshold. Elements that do not meet the
   * bandwidth constraints are dropped. The weight of each element is determined by the `costFn` effectful function.
   * Elements are mapped to `Option[A]`, and `None` denotes that a given element has been dropped.
   */
  final def throttleEnforceM[R, E, A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => ZIO[R, E, Long]
  ): ZManaged[Clock, Nothing, ZSink[R with Clock, E, Nothing, A, Option[A]]] = {
    import ZSink.internal._

    val maxTokens = if (units + burst < 0) Long.MaxValue else units + burst

    def bucketSink(bucket: Ref[(Long, Long)]) = new ZSink[R with Clock, E, Nothing, A, Option[A]] {
      type State = (Ref[(Long, Long)], Option[A])

      val initial = UIO.succeed(Step.more((bucket, None)))

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
                         (Step.done((state._1, Some(a)), Chunk.empty), (available - weight, current))
                       else
                         (Step.done((state._1, None), Chunk.empty), (available, current))
                   }
        } yield result

      def extract(state: State) = UIO.succeed(state._2)
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
  final def throttleShape[A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => Long
  ): ZManaged[Clock, Nothing, ZSink[Clock, Nothing, Nothing, A, A]] =
    throttleShapeM[Any, Nothing, A](units, duration, burst)(a => UIO.succeed(costFn(a)))

  /**
   * Creates a sink which delays input elements of type A according to the given bandwidth parameters
   * using the token bucket algorithm. The sink allows for burst in the processing of elements by allowing
   * the token bucket to accumulate tokens up to a `units + burst` threshold. The weight of each element is
   * determined by the `costFn` effectful function.
   */
  final def throttleShapeM[R, E, A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => ZIO[R, E, Long]
  ): ZManaged[Clock, Nothing, ZSink[R with Clock, E, Nothing, A, A]] = {
    import ZSink.internal._

    val maxTokens = if (units + burst < 0) Long.MaxValue else units + burst

    def bucketSink(bucket: Ref[(Long, Long)]) = new ZSink[R with Clock, E, Nothing, A, A] {
      type State = (Ref[(Long, Long)], Promise[Nothing, A])

      val initial = Promise.make[Nothing, A].map(promise => Step.more((bucket, promise)))

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
        } yield Step.done(state, Chunk.empty)

      def extract(state: State) = state._2.await
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
   * Decodes individual bytes into a String using UTF-8. Up to `bufferSize` bytes
   * will be buffered by the sink.
   *
   * This sink uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  def utf8Decode(bufferSize: Int = ZStreamChunk.DefaultChunkSize): ZSink[Any, Nothing, Byte, Byte, String] =
    foldUntil(List[Byte](), bufferSize.toLong)((chunk, byte: Byte) => byte :: chunk).mapM { bytes =>
      val chunk = Chunk.fromIterable(bytes.reverse)

      for {
        init   <- utf8DecodeChunk.initial.map(Step.state(_))
        state  <- utf8DecodeChunk.step(init, chunk)
        string <- utf8DecodeChunk.extract(Step.state(state))
      } yield string
    }

  /**
   * Decodes chunks of bytes into a String.
   *
   * This sink uses the String constructor's behavior when handling malformed byte
   * sequences.
   */
  val utf8DecodeChunk: ZSink[Any, Nothing, Chunk[Byte], Chunk[Byte], String] =
    new SinkPure[Nothing, Chunk[Byte], Chunk[Byte], String] {
      type State = (String, Chunk[Byte])

      override def initialPure: Step[State, Nothing] = Step.more(("", Chunk.empty))

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

      override def stepPure(s: State, a: Chunk[Byte]): Step[State, Chunk[Byte]] =
        if (a.length == 0) Step.done(s, Chunk.empty)
        else {
          val (accumulatedString, prevLeftovers) = s
          val concat                             = prevLeftovers ++ a
          val (toConvert, leftovers)             = concat.splitAt(computeSplit(concat))

          if (toConvert.length == 0) Step.more((accumulatedString, leftovers))
          else
            Step.done(
              (accumulatedString ++ new String(toConvert.toArray[Byte], "UTF-8"), Chunk.empty),
              Chunk.single(leftovers)
            )
        }

      override def extractPure(s: State): Either[Nothing, String] =
        Right(s._1)
    }
}
