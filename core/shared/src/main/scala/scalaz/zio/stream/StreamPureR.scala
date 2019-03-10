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

package scalaz.zio.stream

import scalaz.zio._

private[stream] trait StreamPureR[-R, +A] extends ZStream[R, Nothing, A] { self =>
  def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S

  override def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): UIO[S] =
    IO.succeed(foldPureLazy(s)(_ => true)(f))

  override def run[R1 <: R, E, A0, A1 >: A, B](sink: ZSink[R1, E, A0, A1, B]): ZIO[R1, E, B] =
    sink match {
      case sink: SinkPure[E, A0, A1, B] =>
        ZIO.fromEither(
          sink.extractPure(
            ZSink.Step.state(
              foldPureLazy[A1, ZSink.Step[sink.State, A0]](sink.initialPure)(ZSink.Step.cont) { (s, a) =>
                sink.stepPure(ZSink.Step.state(s), a)
              }
            )
          )
        )

      case sink: ZSink[R1, E, A0, A1, B] => super.run(sink)
    }

  /**
   * Filters this stream by the specified predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  override def filter(pred: A => Boolean): StreamPureR[R, A] = new StreamPureR[R, A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self.foldPureLazy[A, S](s)(cont) { (s, a) =>
        if (pred(a)) f(s, a)
        else s
      }

    override def fold[R1 <: R, E, A1 >: A, S]: ZStream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPureR.super.filter(pred).fold[R1, E, A1, S].flatMap(f0 => f0(s, cont, f))
      }
  }

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def dropWhile(pred: A => Boolean): StreamPureR[R, A] = new StreamPureR[R, A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (Boolean, S)](true -> s)(tp => cont(tp._2)) {
          case ((true, s), a) if pred(a) => true  -> s
          case ((_, s), a)               => false -> f(s, a)
        }
        ._2

    override def fold[R1 <: R, E, A1 >: A, S]: ZStream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPureR.super.dropWhile(pred).fold[R1, E, A1, S].flatMap(f0 => f0(s, cont, f))
      }
  }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def takeWhile(pred: A => Boolean): StreamPureR[R, A] = new StreamPureR[R, A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (Boolean, S)](true -> s)(tp => tp._1 && cont(tp._2)) {
          case ((_, s), a) =>
            if (pred(a)) true -> f(s, a)
            else false        -> s
        }
        ._2

    override def fold[R1 <: R, E, A1 >: A, S]: ZStream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPureR.super.takeWhile(pred).fold[R1, E, A1, S].flatMap(f0 => f0(s, cont, f))
      }
  }

  /**
   * Maps over elements of the stream with the specified function.
   */
  override def map[B](f0: A => B): StreamPureR[R, B] = new StreamPureR[R, B] {
    override def fold[R1 <: R, E, B1 >: B, S]: ZStream.Fold[R1, E, B1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPureR.super.map(f0).fold[R1, E, B1, S].flatMap(f1 => f1(s, cont, f))
      }

    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self.foldPureLazy[A, S](s)(cont)((s, a) => f(s, f0(a)))
  }

  override def mapConcat[B](f0: A => Chunk[B]): StreamPureR[R, B] = new StreamPureR[R, B] {
    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self.foldPureLazy(s)(cont)((s, a) => f0(a).foldLeftLazy(s)(cont)(f))

    override def fold[R1 <: R, E, B1 >: B, S]: ZStream.Fold[R1, E, B1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPureR.super.mapConcat(f0).fold[R1, E, B1, S].flatMap(f1 => f1(s, cont, f))
      }
  }

  override def zipWithIndex[R1 <: R]: StreamPureR[R1, (A, Int)] = new StreamPureR[R1, (A, Int)] {
    override def foldPureLazy[A1 >: (A, Int), S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (S, Int)]((s, 0))(tp => cont(tp._1)) {
          case ((s, index), a) => (f(s, (a, index)), index + 1)
        }
        ._1

    override def fold[R2 <: R1, E, A1 >: (A, Int), S]: ZStream.Fold[R2, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPureR.super.zipWithIndex[R2].fold[R2, E, A1, S].flatMap(f0 => f0(s, cont, f))
      }
  }

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamPureR[R, B] = new StreamPureR[R, B] {
    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self
        .foldPureLazy[A, (S, S1)](s -> s1)(tp => cont(tp._1)) {
          case ((s, s1), a) =>
            val (s2, b) = f1(s1, a)

            f(s, b) -> s2
        }
        ._1

    override def fold[R1 <: R, E, B1 >: B, S]: ZStream.Fold[R1, E, B1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPureR.super.mapAccum(s1)(f1).fold[R1, E, B1, S].flatMap(f0 => f0(s, cont, f))
      }
  }
}

private[stream] object StreamPureR extends Serializable {

  /**
   * Constructs a pure stream from the specified `Iterable`.
   */
  final def fromIterable[A](it: Iterable[A]): StreamPure[A] = new StreamPure[A] {
    override def fold[R1 <: Any, E >: Nothing, A1 >: A, S]: ZStream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        val iterator = it.iterator

        def loop(s: S): ZIO[R1, E, S] =
          ZIO.flatten[R1, E, S] {
            ZIO.effectTotal {
              if (iterator.hasNext && cont(s))
                f(s, iterator.next).flatMap(loop)
              else ZIO.succeed(s)
            }
          }

        loop(s)
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S = {
      val iterator = it.iterator

      def loop(s: S): S =
        if (iterator.hasNext && cont(s)) loop(f(s, iterator.next))
        else s

      loop(s)
    }
  }

  /**
   * Constructs a singleton stream from a strict value.
   */
  final def succeed[A](a: A): StreamPure[A] = new StreamPure[A] {
    override def fold[R <: Any, E >: Nothing, A1 >: A, S]: ZStream.Fold[R, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        if (cont(s)) f(s, a)
        else IO.succeed(s)
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      if (cont(s)) f(s, a)
      else s
  }

  /**
   * Constructs a singleton stream from a lazy value.
   */
  final def succeedLazy[A](a: => A): StreamPure[A] = new StreamPure[A] {
    override def fold[R1 <: Any, E >: Nothing, A1 >: A, S]: ZStream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        if (cont(s)) f(s, a)
        else IO.succeed(s)
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      if (cont(s)) f(s, a)
      else s
  }

  /**
   * Returns the empty stream.
   */
  final val empty: StreamPure[Nothing] = new StreamPure[Nothing] {
    override def fold[R1 <: Any, E >: Nothing, A1 >: Nothing, S]: ZStream.Fold[R1, E, A1, S] =
      IO.succeedLazy((s, _, _) => IO.succeed(s))

    override def foldPureLazy[A1 >: Nothing, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S = s
  }
}
