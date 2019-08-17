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

private[stream] trait StreamPure[+A] extends ZStream[Any, Nothing, A] { self =>

  def processPure: () => A

  override def collect[B](pf: PartialFunction[A, B]): StreamPure[B] =
    new StreamPure[B] {
      def process = StreamPure.super.collect(pf).process

      def processPure = {
        def pull: () => B = {
          val a = self.processPure()

          if (pf isDefinedAt a)() => pf(a)
          else pull
        }

        pull
      }
    }

  override def collectWhile[B](pred: PartialFunction[A, B]): StreamPure[B] =
    new StreamPure[B] {
      def process = StreamPure.super.collectWhile(pred).process

      def processPure = {
        var done = false

        def pull: B =
          if (done) throw StreamPure.End
          else pred.applyOrElse(self.processPure(), (_: A) => { done = true; throw StreamPure.End })

        () => pull
      }
    }

  override def dropWhile(pred: A => Boolean): StreamPure[A] =
    new StreamPure[A] {
      def process = StreamPure.super.dropWhile(pred).process

      def processPure = {
        var drop = true

        def pull: () => A = {
          val a = self.processPure()
          if (!drop)() => a
          else if (!pred(a)) {
            drop = false; () => a
          } else pull
        }

        pull
      }
    }

  override def filter(pred: A => Boolean): StreamPure[A] =
    new StreamPure[A] {
      def process = StreamPure.super.filter(pred).process

      def processPure = {
        def pull: () => A = {
          val a = self.processPure()
          if (pred(a))() => a
          else pull
        }

        pull
      }
    }

  final def foldPure[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S = {
    var state = s
    while (cont(s)) {
      state = f(state, processPure())
    }
    state
  }

  override def map[B](f0: A => B): StreamPure[B] =
    new StreamPure[B] {
      def process = StreamPure.super.map(f0).process

      def processPure = () => f0(self.processPure())
    }

  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamPure[B] =
    new StreamPure[B] {
      def process = StreamPure.super.mapAccum(s1)(f1).process

      def processPure = {
        var state = s1

        () => {
          val (s2, b) = f1(s1, self.processPure())
          state = s2
          b
        }
      }
    }

  override def mapConcat[B](f: A => Chunk[B]): StreamPure[B] =
    new StreamPure[B] {
      def process = StreamPure.super.mapConcat(f).process

      def processPure = {
        var chunk: Chunk[B] = Chunk.empty
        var index           = 0

        def pull: B = {
          while (index == chunk.length) {
            chunk = f(self.processPure())
            index = 0
          }
          val b = chunk(index)
          index += 1
          b
        }

        () => pull
      }
    }

  override def run[R, E, A0, A1 >: A, B](sink: ZSink[R, E, A0, A1, B]): ZIO[R, E, B] =
    sink match {
      case sink: SinkPure[E, A0, A1, B] =>
        ZIO.fromEither(
          sink.extractPure(
            ZSink.Step.state(
              foldPure[A1, ZSink.Step[sink.State, A0]](sink.initialPure)(ZSink.Step.cont) { (s, a) =>
                sink.stepPure(ZSink.Step.state(s), a)
              }
            )
          )
        )

      case sink: ZSink[R, E, A0, A1, B] => super.run(sink)
    }

  override def take(n: Int): StreamPure[A] =
    new StreamPure[A] {
      def process = StreamPure.super.take(n).process

      def processPure = {
        var counter = 0

        def pull: A =
          if (counter >= n) throw StreamPure.End
          else {
            counter += 1
            self.processPure()
          }

        () => pull
      }
    }

  override def takeWhile(pred: A => Boolean): StreamPure[A] =
    new StreamPure[A] {
      def process = StreamPure.super.takeWhile(pred).process

      def processPure = () => {
        val a = self.processPure()
        if (pred(a)) a
        else throw StreamPure.End
      }
    }
}

private[stream] object StreamPure extends Serializable {

  object End extends Exception("") {
    override def fillInStackTrace() = this
  }
}
