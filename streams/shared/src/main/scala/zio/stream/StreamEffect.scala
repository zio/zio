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

private[stream] trait StreamEffect[+E, +A] extends ZStream[Any, E, A] { self =>

  def processEffect: Managed[E, () => A]

  override def collect[B](pf: PartialFunction[A, B]): StreamEffect[E, B] =
    new StreamEffect[E, B] {
      def process = StreamEffect.super.collect(pf).process

      def processEffect =
        self.processEffect.flatMap { it =>
          Managed.effectTotal {
            def pull(): B = pf.applyOrElse(it(), (_: A) => pull())
            () => pull()
          }
        }
    }

  override def collectWhile[B](pred: PartialFunction[A, B]): StreamEffect[E, B] =
    new StreamEffect[E, B] {
      def process = StreamEffect.super.collectWhile(pred).process

      def processEffect =
        self.processEffect.flatMap { it =>
          Managed.effectTotal {
            var done = false

            () => {
              if (done) throw StreamEffect.End
              else pred.applyOrElse(it(), (_: A) => { done = true; throw StreamEffect.End })
            }
          }
        }
    }

  override def dropWhile(pred: A => Boolean): StreamEffect[E, A] =
    new StreamEffect[E, A] {
      def process = StreamEffect.super.dropWhile(pred).process

      def processEffect =
        self.processEffect.flatMap { it =>
          Managed.effectTotal {
            var drop = true

            def pull(): A = {
              val a = it()
              if (!drop) a
              else if (!pred(a)) {
                drop = false
                a
              } else pull()
            }

            () => pull()
          }
        }
    }

  override def filter(pred: A => Boolean): StreamEffect[E, A] =
    new StreamEffect[E, A] {
      def process = StreamEffect.super.filter(pred).process

      def processEffect =
        self.processEffect.flatMap { it =>
          Managed.effectTotal {
            def pull(): A = {
              val a = it()
              if (pred(a)) a else pull()
            }

            () => pull()
          }
        }
    }

  final def foldEffect[S](s: S)(cont: S => Boolean)(f: (S, A) => S): Managed[E, S] =
    processEffect.flatMap { it =>
      Managed.effectTotal {
        var state = s
        var done  = false

        while (cont(state) && !done) {
          try {
            val a = it()
            state = f(state, a)
          } catch {
            case StreamEffect.End => done = true
          }
        }

        state
      }
    }

  override def map[B](f0: A => B): StreamEffect[E, B] =
    new StreamEffect[E, B] {
      def process = StreamEffect.super.map(f0).process

      def processEffect =
        self.processEffect.flatMap { it =>
          Managed.effectTotal { () =>
            f0(it())
          }
        }
    }

  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamEffect[E, B] =
    new StreamEffect[E, B] {
      def process = StreamEffect.super.mapAccum(s1)(f1).process

      def processEffect =
        self.processEffect.flatMap { it =>
          Managed.effectTotal {
            var state = s1

            () => {
              val (s2, b) = f1(state, it())
              state = s2
              b
            }
          }
        }
    }

  override def mapConcat[B](f: A => Chunk[B]): StreamEffect[E, B] =
    new StreamEffect[E, B] {
      def process = StreamEffect.super.mapConcat(f).process

      def processEffect =
        self.processEffect.flatMap { it =>
          Managed.effectTotal {
            var chunk: Chunk[B] = Chunk.empty
            var index           = 0

            () => {
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
    }

  override def run[R, E1 >: E, A0, A1 >: A, B](sink: ZSink[R, E1, A0, A1, B]): ZIO[R, E1, B] =
    sink match {
      case sink: SinkPure[E1, A0, A1, B] =>
        foldEffect[ZSink.Step[sink.State, A0]](sink.initialPure)(ZSink.Step.cont) { (s, a) =>
          sink.stepPure(ZSink.Step.state(s), a)
        }.use[Any, E1, B] { step =>
          ZIO.fromEither(sink.extractPure(ZSink.Step.state(step)))
        }

      case sink: ZSink[R, E1, A0, A1, B] => super.run(sink)
    }

  override def take(n: Int): StreamEffect[E, A] =
    new StreamEffect[E, A] {
      def process = StreamEffect.super.take(n).process

      def processEffect =
        self.processEffect.flatMap { it =>
          Managed.effectTotal {
            var counter = 0

            () => {
              if (counter >= n) throw StreamEffect.End
              else {
                counter += 1
                it()
              }
            }
          }
        }
    }

  override def takeWhile(pred: A => Boolean): StreamEffect[E, A] =
    new StreamEffect[E, A] {
      def process = StreamEffect.super.takeWhile(pred).process

      def processEffect =
        self.processEffect.flatMap { it =>
          Managed.effectTotal { () =>
            {
              val a = it()
              if (pred(a)) a
              else throw StreamEffect.End
            }
          }
        }
    }
}

private[stream] object StreamEffect extends Serializable {
  import ZStream.InputStream

  object End extends Exception("") {
    override def fillInStackTrace() = this
  }

  final val empty: StreamEffect[Nothing, Nothing] =
    new StreamEffect[Nothing, Nothing] {
      def process = ZManaged.succeed(InputStream.end)

      def processEffect = Managed.effectTotal { throw End }
    }

  final def fromIterable[A](as: Iterable[A]): StreamEffect[Nothing, A] =
    new StreamEffect[Nothing, A] {
      def process =
        for {
          it <- ZManaged.effectTotal(as.iterator)
          pull = UIO {
            if (it.hasNext) InputStream.emit(it.next)
            else InputStream.end
          }.flatten
        } yield pull

      def processEffect =
        Managed.effectTotal {
          val it = as.iterator

          () => if (it.hasNext) it.next() else throw End
        }
    }

  final def succeed[A](a: A): StreamEffect[Nothing, A] =
    new StreamEffect[Nothing, A] {
      def process =
        for {
          done <- Ref.make(false).toManaged_
        } yield done.get.flatMap {
          if (_) InputStream.end
          else done.set(true) *> InputStream.emit(a)
        }

      def processEffect =
        Managed.effectTotal {
          var done = false

          () => {
            if (!done) {
              done = true
              a
            } else throw End
          }
        }
    }
}
