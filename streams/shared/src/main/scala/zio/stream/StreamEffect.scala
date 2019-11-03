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

private[stream] class StreamEffect[+E, +A](val processEffect: Managed[E, () => A])
    extends ZStream[Any, E, A](
      processEffect.map { thunk =>
        UIO.effectTotal {
          try UIO.succeed(thunk())
          catch {
            case StreamEffect.Failure(e) => IO.fail(Some(e.asInstanceOf[E]))
            case StreamEffect.End        => IO.fail(None)
          }
        }.flatten
      }
    ) { self =>

  override def collect[B](pf: PartialFunction[A, B]): StreamEffect[E, B] =
    StreamEffect[E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal { () =>
          {
            var ob: Option[B]                        = None
            val pfOpt: PartialFunction[A, Option[B]] = pf.andThen(Some(_))

            while (ob.isEmpty) {
              ob = pfOpt.applyOrElse(thunk(), (_: A) => None)
            }

            ob.get
          }
        }
      }
    }

  override def collectWhile[B](pred: PartialFunction[A, B]): StreamEffect[E, B] =
    StreamEffect[E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var done = false

          () => {
            if (done) StreamEffect.end
            else pred.applyOrElse(thunk(), (_: A) => { done = true; StreamEffect.end })
          }
        }
      }
    }

  override def dropWhile(pred: A => Boolean): StreamEffect[E, A] =
    StreamEffect[E, A] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var drop = true

          @annotation.tailrec
          def pull(): A = {
            val a = thunk()
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
    StreamEffect[E, A] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          @annotation.tailrec
          def pull(): A = {
            val a = thunk()
            if (pred(a)) a else pull()
          }

          () => pull()
        }
      }
    }

  final def foldLazyPure[S](s: S)(cont: S => Boolean)(f: (S, A) => S): Managed[E, S] =
    processEffect.flatMap { thunk =>
      def fold(): Either[E, S] = {
        var state            = s
        var done             = false
        var error: Option[E] = None

        while (!done && error.isEmpty && cont(state)) {
          try {
            val a = thunk()
            state = f(state, a)
          } catch {
            case StreamEffect.Failure(e) => error = Some(e.asInstanceOf[E])
            case StreamEffect.End        => done = true
          }
        }

        error.fold[Either[E, S]](Right(state))(Left(_))
      }

      Managed.effectTotal(Managed.fromEither(fold())).flatten
    }

  override def map[B](f0: A => B): StreamEffect[E, B] =
    StreamEffect[E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal { () =>
          f0(thunk())
        }
      }
    }

  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamEffect[E, B] =
    StreamEffect[E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var state = s1

          () => {
            val (s2, b) = f1(state, thunk())
            state = s2
            b
          }
        }
      }
    }

  override def mapConcat[B](f: A => Chunk[B]): StreamEffect[E, B] =
    StreamEffect[E, B] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var chunk: Chunk[B] = Chunk.empty
          var index           = 0

          () => {
            while (index == chunk.length) {
              chunk = f(thunk())
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
        foldLazyPure[ZSink.Step[sink.State, A0]](sink.initialPure)(ZSink.Step.cont) { (s, a) =>
          sink.stepPure(ZSink.Step.state(s), a)
        }.use[Any, E1, B] { step =>
          ZIO.fromEither(sink.extractPure(ZSink.Step.state(step)))
        }

      case sink: ZSink[R, E1, A0, A1, B] => super.run(sink)
    }

  override def take(n: Int): StreamEffect[E, A] =
    StreamEffect[E, A] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var counter = 0

          () => {
            if (counter >= n) StreamEffect.end
            else {
              counter += 1
              thunk()
            }
          }
        }
      }
    }

  override def takeWhile(pred: A => Boolean): StreamEffect[E, A] =
    StreamEffect[E, A] {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal { () =>
          {
            val a = thunk()
            if (pred(a)) a
            else StreamEffect.end
          }
        }
      }
    }

  override def transduce[R, E1 >: E, A1 >: A, B](
    sink: ZSink[R, E1, A1, A1, B]
  ): ZStream[R, E1, B] =
    sink match {
      case sink: SinkPure[E1, A1, A1, B] =>
        StreamEffect[E1, B] {
          import ZSink.Step

          self.processEffect.flatMap { thunk =>
            Managed.effectTotal {
              var step: Step[sink.State, A1] = sink.initialPure
              var needsExtractOnEnd          = false
              var done                       = false

              () => {
                if (done) {
                  StreamEffect.end
                } else {
                  while (!done && Step.cont(step)) {
                    try {
                      val a = thunk()
                      step = sink.stepPure(Step.state(step), a)
                      needsExtractOnEnd = true
                    } catch {
                      case StreamEffect.End =>
                        done = true
                    }
                  }

                  if (done) {
                    if (needsExtractOnEnd)
                      sink.extractPure(Step.state(step)) match {
                        case Left(e)  => StreamEffect.fail(e)
                        case Right(b) => b
                      } else StreamEffect.end
                  } else {
                    sink.extractPure(Step.state(step)) match {
                      case Left(e) => StreamEffect.fail(e)
                      case Right(b) =>
                        val leftover     = Step.leftover(step)
                        val newInit      = sink.initialPure
                        val leftoverStep = sink.stepChunkPure(Step.state(newInit), leftover)
                        step = leftoverStep
                        needsExtractOnEnd = !leftover.isEmpty
                        b
                    }
                  }
                }
              }
            }
          }
        }
      case sink: ZSink[R, E1, A1, A1, B] => super.transduce(sink)
    }
}

private[stream] object StreamEffect extends Serializable {

  case class Failure[E](e: E) extends Throwable(e.toString, null, true, false) {
    override def fillInStackTrace() = this
  }

  case object End extends Throwable("stream end", null, true, false) {
    override def fillInStackTrace() = this
  }

  def end[A]: A = throw End

  def fail[E, A](e: E): A = throw Failure(e)

  final val empty: StreamEffect[Nothing, Nothing] =
    StreamEffect[Nothing, Nothing] {
      Managed.effectTotal { () =>
        end
      }
    }

  final def apply[E, A](pull: Managed[E, () => A]): StreamEffect[E, A] =
    new StreamEffect[E, A](pull)

  final def fromChunk[@specialized A](c: Chunk[A]): StreamEffect[Nothing, A] =
    StreamEffect[Nothing, A] {
      Managed.effectTotal {
        var index = 0
        val len   = c.length

        () => {
          if (index >= len) end
          else {
            val i = index
            index += 1
            c(i)
          }
        }
      }
    }

  final def fromIterable[A](as: Iterable[A]): StreamEffect[Nothing, A] =
    StreamEffect[Nothing, A] {
      Managed.effectTotal {
        val thunk = as.iterator

        () => if (thunk.hasNext) thunk.next() else end
      }
    }

  final def unfold[S, A](s: S)(f0: S => Option[(A, S)]): StreamEffect[Nothing, A] =
    StreamEffect[Nothing, A] {
      Managed.effectTotal {
        var state = s

        () => {
          val opt = f0(state)
          if (opt.isDefined) {
            val res = opt.get
            state = res._2
            res._1
          } else end
        }
      }
    }

  final def succeed[A](a: A): StreamEffect[Nothing, A] =
    StreamEffect[Nothing, A] {
      Managed.effectTotal {
        var done = false

        () => {
          if (!done) {
            done = true
            a
          } else end
        }
      }
    }
}
