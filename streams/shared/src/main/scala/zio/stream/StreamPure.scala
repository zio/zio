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

      def processPure = new Function0[B] {
        val it = self.processPure

        def apply() = pf.applyOrElse(it(), (_: A) => apply())
      }
    }

  override def collectWhile[B](pred: PartialFunction[A, B]): StreamPure[B] =
    new StreamPure[B] {
      def process = StreamPure.super.collectWhile(pred).process

      def processPure = new Function0[B] {
        val it   = self.processPure
        var done = false

        def apply() =
          if (done) throw StreamPure.End
          else pred.applyOrElse(it(), (_: A) => { done = true; throw StreamPure.End })
      }
    }

  override def dropWhile(pred: A => Boolean): StreamPure[A] =
    new StreamPure[A] {
      def process = StreamPure.super.dropWhile(pred).process

      def processPure = new Function0[A] {
        val it   = self.processPure
        var drop = true

        def apply() = {
          val a = it()
          if (!drop) a
          else if (!pred(a)) {
            drop = false
            a
          } else apply()
        }
      }
    }

  override def filter(pred: A => Boolean): StreamPure[A] =
    new StreamPure[A] {
      def process = StreamPure.super.filter(pred).process

      def processPure = new Function0[A] {
        val it = self.processPure

        def apply() = {
          val a = it()
          if (pred(a)) a
          else apply()
        }
      }
    }

  final def foldPure[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S = {
    val it    = processPure
    var state = s
    var done  = false
    while (cont(state) && !done) {
      try {
        val a = it()
        state = f(state, a)
      } catch {
        case StreamPure.End => done = true
      }
    }
    state
  }

  override def map[B](f0: A => B): StreamPure[B] =
    new StreamPure[B] {
      def process = StreamPure.super.map(f0).process

      def processPure = new Function0[B] {
        val it = self.processPure

        def apply() = f0(it())
      }
    }

  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamPure[B] =
    new StreamPure[B] {
      def process = StreamPure.super.mapAccum(s1)(f1).process

      def processPure = new Function0[B] {
        val it    = self.processPure
        var state = s1

        def apply() = {
          val (s2, b) = f1(state, it())
          state = s2
          b
        }
      }
    }

  override def mapConcat[B](f: A => Chunk[B]): StreamPure[B] =
    new StreamPure[B] {
      def process = StreamPure.super.mapConcat(f).process

      def processPure = new Function0[B] {
        val it              = self.processPure
        var chunk: Chunk[B] = Chunk.empty
        var index           = 0

        def apply() = {
          while (index == chunk.length) {
            chunk = f(it())
            index = 0
          }
          val b = chunk(index)
          index += 1
          b
        }
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

      def processPure = new Function0[A] {
        val it      = self.processPure
        var counter = 0

        def apply() =
          if (counter >= n) throw StreamPure.End
          else {
            counter += 1
            it()
          }
      }
    }

  override def takeWhile(pred: A => Boolean): StreamPure[A] =
    new StreamPure[A] {
      def process = StreamPure.super.takeWhile(pred).process

      def processPure = new Function0[A] {
        val it = self.processPure

        def apply() = {
          val a = it()
          if (pred(a)) a
          else throw StreamPure.End
        }
      }
    }
}

private[stream] object StreamPure extends Serializable {
  import ZStream.InputStream

  object End extends Exception("") {
    override def fillInStackTrace() = this
  }

  final val empty: StreamPure[Nothing] =
    new StreamPure[Nothing] {
      def process = ZManaged.succeed(InputStream.end)

      def processPure = throw End
    }

  final def fromIterable[A](as: Iterable[A]): StreamPure[A] =
    new StreamPure[A] {
      def process =
        for {
          it <- ZManaged.effectTotal(as.iterator)
          pull = UIO {
            if (it.hasNext) InputStream.emit(it.next)
            else InputStream.end
          }.flatten
        } yield pull

      def processPure = new Function0[A] {
        val it = as.iterator

        def apply() = if (it.hasNext) it.next() else throw End
      }
    }

  final def succeed[A](a: A): StreamPure[A] =
    new StreamPure[A] {
      def process =
        for {
          done <- Ref.make(false).toManaged_
        } yield done.get.flatMap {
          if (_) InputStream.end
          else done.set(true) *> InputStream.emit(a)
        }

      def processPure = new Function0[A] {
        var done = false

        def apply() =
          if (!done) {
            done = true
            a
          } else throw End
      }
    }
}
