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

import java.io.{ IOException, InputStream }
import java.{ util => ju }

import zio._

private[stream] final class StreamEffect[-R, +E, +A](val processEffect: ZManaged[R, Nothing, () => A])
    extends ZStream[R, E, A](
      ZStream.Structure.Iterator(
        processEffect.map { thunk =>
          UIO.effectTotal {
            try UIO.succeedNow(thunk())
            catch {
              case StreamEffect.Failure(e) => IO.fail(Some(e.asInstanceOf[E]))
              case StreamEffect.End        => IO.fail(None)
            }
          }.flatten
        }
      )
    ) { self =>

  override def collect[B](pf: PartialFunction[A, B]): StreamEffect[R, E, B] =
    StreamEffect {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal { () =>
          var b = null.asInstanceOf[B]

          while (b == null) {
            b = pf.applyOrElse(thunk(), (_: A) => null.asInstanceOf[B])
          }

          b
        }
      }
    }

  override def collectWhile[B](pred: PartialFunction[A, B]): StreamEffect[R, E, B] =
    StreamEffect {
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

  override def dropWhile(pred: A => Boolean): StreamEffect[R, E, A] =
    StreamEffect {
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

  override def filter(pred: A => Boolean): StreamEffect[R, E, A] =
    StreamEffect {
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

  final override def foldWhileManaged[S](s: S)(cont: S => Boolean)(f: (S, A) => S): ZManaged[R, E, S] =
    processEffect.flatMap { thunk =>
      def fold(): Either[E, S] = {
        var state = s
        var done  = false
        var error = null.asInstanceOf[E]

        while (!done && error == null && cont(state)) {
          try {
            val a = thunk()
            state = f(state, a)
          } catch {
            case StreamEffect.Failure(e) => error = e.asInstanceOf[E]
            case StreamEffect.End        => done = true
          }
        }

        if (error == null) Right(state) else Left(error)
      }

      Managed.effectTotal(Managed.fromEither(fold())).flatten
    }

  override def map[B](f0: A => B): StreamEffect[R, E, B] =
    StreamEffect {
      self.processEffect.flatMap(thunk => Managed.effectTotal(() => f0(thunk())))
    }

  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamEffect[R, E, B] =
    StreamEffect {
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

  override def mapConcatChunk[B](f: A => Chunk[B]): StreamEffect[R, E, B] =
    StreamEffect {
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

  override def run[R1 <: R, E1 >: E, A1 >: A, B](sink: ZSink[R1, E1, Any, A1, B]): ZIO[R1, E1, B] =
    sink match {
      case sink: SinkPure[E1, Any, A1, B] =>
        foldWhileManaged(sink.initialPure)(sink.cont)(sink.stepPure).use[R1, E1, B] { state =>
          ZIO.fromEither(sink.extractPure(state).map(_._1))
        }

      case sink: ZSink[R1, E1, Any, A1, B] => super.run(sink)
    }

  override def take(n: Long): StreamEffect[R, E, A] =
    StreamEffect {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var counter = 0L

          () => {
            if (counter >= n) StreamEffect.end
            else {
              val a = thunk()
              counter += 1
              a
            }
          }
        }
      }
    }

  override def takeUntil(pred: A => Boolean): StreamEffect[R, E, A] =
    StreamEffect {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var keepTaking = true

          () => {
            if (!keepTaking) StreamEffect.end
            else {
              val a = thunk()
              if (pred(a)) {
                keepTaking = false
              }
              a
            }
          }
        }
      }
    }

  override def takeWhile(pred: A => Boolean): StreamEffect[R, E, A] =
    StreamEffect {
      self.processEffect.flatMap { thunk =>
        Managed.effectTotal {
          var done = false

          () => {
            if (done) StreamEffect.end
            else {
              val a = thunk()
              if (pred(a)) a
              else {
                done = true
                StreamEffect.end
              }
            }
          }
        }
      }
    }

  override def aggregate[R1 <: R, E1 >: E, A1 >: A, B](
    sink: ZSink[R1, E1, A1, A1, B]
  ): ZStream[R1, E1, B] = {
    import ZStream.internal.AggregateState

    sink match {
      case sink: SinkPure[E1, A1, A1, B] =>
        StreamEffect {
          self.processEffect.flatMap { thunk =>
            Managed.effectTotal {
              var state: AggregateState[sink.State, A1] = AggregateState.Initial(Chunk.empty)

              @annotation.tailrec
              def go(): B = state match {
                case AggregateState.Initial(leftovers) =>
                  state = AggregateState.Drain(sink.initialPure, leftovers, 0)
                  go()

                case AggregateState.Pull(s, dirty) =>
                  try {
                    val a  = thunk()
                    val ns = sink.stepPure(s, a)
                    state =
                      if (sink.cont(ns)) AggregateState.Pull(ns, true) else AggregateState.Extract(ns, Chunk.empty)
                  } catch {
                    case StreamEffect.End =>
                      state = if (dirty) AggregateState.DirtyDone(s) else AggregateState.Done
                  }
                  go()

                case AggregateState.Extract(s, chunk) =>
                  sink
                    .extractPure(s)
                    .fold(e => {
                      state = AggregateState.Initial(chunk)
                      StreamEffect.failure(e)
                    }, {
                      case (b, leftovers) =>
                        state = AggregateState.Initial(chunk ++ leftovers)
                        b
                    })

                case AggregateState.Drain(s, leftovers, index) =>
                  if (index < leftovers.length) {
                    val ns = sink.stepPure(s, leftovers(index))
                    state =
                      if (sink.cont(ns)) AggregateState.Drain(ns, leftovers, index + 1)
                      else AggregateState.Extract(ns, leftovers.drop(index + 1))
                    go()
                  } else {
                    state =
                      if (sink.cont(s)) AggregateState.Pull(s, index != 0) else AggregateState.Extract(s, Chunk.empty)
                    go()
                  }

                case AggregateState.DirtyDone(s) =>
                  sink
                    .extractPure(s)
                    .fold(StreamEffect.failure, {
                      case (b, _) =>
                        state = AggregateState.Done
                        b
                    })

                case AggregateState.Done =>
                  StreamEffect.end
              }

              () => go()
            }
          }
        }

      case sink: ZSink[R1, E1, A1, A1, B] => super.aggregate(sink)
    }
  }

  override def toInputStream(
    implicit ev0: E <:< Throwable,
    ev1: A <:< Byte
  ): ZManaged[R, E, java.io.InputStream] =
    for {
      pull <- processEffect
      javaStream = new java.io.InputStream {
        override def read(): Int =
          try {
            pull().toInt
          } catch {
            case StreamEffect.End        => -1
            case StreamEffect.Failure(e) => throw e.asInstanceOf[E]
          }
      }
    } yield javaStream
}

private[stream] object StreamEffect extends Serializable {

  final case class Failure[E](e: E) extends Throwable(e.toString, null, true, false) {
    override def fillInStackTrace() = this
  }

  case object End extends Throwable("stream end", null, true, false) {
    override def fillInStackTrace() = this
  }

  def end: Nothing = throw End

  def failure[E](e: E): Nothing = throw Failure(e)

  val empty: StreamEffect[Any, Nothing, Nothing] =
    StreamEffect {
      Managed.effectTotal(() => end)
    }

  def apply[R, E, A](pull: ZManaged[R, Nothing, () => A]): StreamEffect[R, E, A] =
    new StreamEffect(pull)

  def fail[E](e: => E): StreamEffect[Any, E, Nothing] =
    StreamEffect {
      Managed.effectTotal {
        var done = false
        () =>
          if (done) end
          else {
            done = true
            failure(e)
          }
      }
    }

  def fromChunk[A](c: => Chunk[A]): StreamEffect[Any, Nothing, A] =
    StreamEffect {
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

  def fromIterable[A](as: => Iterable[A]): StreamEffect[Any, Nothing, A] =
    StreamEffect {
      Managed.effectTotal {
        val thunk = as.iterator

        () => if (thunk.hasNext) thunk.next() else end
      }
    }

  def fromIteratorTotal[A](iterator: => Iterator[A]): StreamEffect[Any, Nothing, A] =
    StreamEffect {
      Managed.effectTotal {
        val it = iterator

        () => if (it.hasNext) it.next() else end
      }
    }

  def fromIterator[A](iterator: => Iterator[A]): StreamEffect[Any, Throwable, A] =
    StreamEffect {
      Managed.fromEffect {
        for {
          rt <- ZIO.runtime[Any]
          it <- IO.effectTotal {
                 var it: Iterator[A] = null
                 var ex: Throwable   = null

                 try { it = iterator }
                 catch {
                   case e: Throwable if !rt.platform.fatal(e) =>
                     ex = e
                 }

                 () => {
                   if (it != null) {
                     val hasNext: Boolean =
                       try it.hasNext
                       catch {
                         case e: Throwable if !rt.platform.fatal(e) =>
                           StreamEffect.failure(e)
                       }

                     if (hasNext) {
                       try it.next()
                       catch {
                         case e: Throwable if !rt.platform.fatal(e) =>
                           StreamEffect.failure(e)
                       }
                     } else StreamEffect.end
                   } else if (ex != null) {
                     val ex0 = ex
                     ex = null
                     StreamEffect.failure(ex0)
                   } else StreamEffect.end
                 }
               }
        } yield it
      }
    }

  def fromJavaIteratorTotal[A](iterator: => ju.Iterator[A]): StreamEffect[Any, Nothing, A] = {
    val make = () => {
      val it = iterator // Scala 2.13 scala.collection.Iterator has `iterator` in local scope
      new Iterator[A] {
        def next(): A        = it.next()
        def hasNext: Boolean = it.hasNext
      }
    }
    fromIteratorTotal(make())
  }

  def fromJavaIterator[A](iterator: => ju.Iterator[A]): StreamEffect[Any, Throwable, A] = {
    val make = () => {
      val it = iterator // Scala 2.13 scala.collection.Iterator has `iterator` in local scope
      new Iterator[A] {
        def next(): A        = it.next()
        def hasNext: Boolean = it.hasNext
      }
    }
    fromIterator(make())
  }

  def fromInputStream(
    is: => InputStream,
    chunkSize: Int = ZStreamChunk.DefaultChunkSize
  ): StreamEffectChunk[Any, IOException, Byte] =
    StreamEffectChunk {
      StreamEffect {
        Managed.effectTotal {
          var done       = false
          val capturedIs = is

          def pull(): Chunk[Byte] = {
            val buf = Array.ofDim[Byte](chunkSize)
            try {
              val bytesRead = capturedIs.read(buf)
              if (bytesRead < 0) {
                done = true
                end
              } else if (0 < bytesRead && bytesRead < buf.length) Chunk.fromArray(buf).take(bytesRead)
              else Chunk.fromArray(buf)
            } catch {
              case e: IOException => failure(e)
            }
          }

          () => if (done) end else pull()
        }
      }
    }

  def iterate[A](a: A)(f: A => A): StreamEffect[Any, Nothing, A] =
    StreamEffect {
      Managed.effectTotal {
        var state = a

        () => {
          val out = state
          state = f(state)
          out
        }
      }
    }

  def paginate[A, S](s0: S)(f: S => (A, Option[S])): StreamEffect[Any, Nothing, A] =
    StreamEffect {
      Managed.effectTotal {
        var state = Option(s0)

        () =>
          state.fold(end) { s =>
            val res = f(s)
            state = res._2
            res._1
          }
      }
    }

  def unfold[S, A](s: S)(f0: S => Option[(A, S)]): StreamEffect[Any, Nothing, A] =
    StreamEffect {
      Managed.effectTotal {
        var done  = false
        var state = s

        () =>
          if (done) end
          else {
            val opt = f0(state)
            if (opt.isDefined) {
              val res = opt.get
              state = res._2
              res._1
            } else {
              done = true
              end
            }
          }
      }
    }

  def succeed[A](a: => A): StreamEffect[Any, Nothing, A] =
    StreamEffect {
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
