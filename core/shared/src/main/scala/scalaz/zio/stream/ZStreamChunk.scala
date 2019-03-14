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

trait ZStreamChunk[-R, +E, @specialized +A] { self =>
  val chunks: ZStream[R, E, Chunk[A]]

  def foldLazy[R1 <: R, E1 >: E, A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => ZIO[R1, E1, S]): ZIO[R1, E1, S] =
    chunks.fold[R1, E1, Chunk[A1], S].flatMap { f0 =>
      f0(s, cont, (s, as) => as.foldMLazy(s)(cont)(f))
    }

  def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): ZIO[R, E, S] =
    foldLazy(s)(_ => true)((s, a) => IO.succeed(f(s, a)))

  /**
   * Executes an effectful fold over the stream of chunks.
   */
  def foldLazyChunks[R1 <: R, E1 >: E, A1 >: A, S](
    s: S
  )(cont: S => Boolean)(f: (S, Chunk[A1]) => ZIO[R1, E1, S]): ZIO[R1, E1, S] =
    chunks.fold[R1, E1, Chunk[A1], S].flatMap(f0 => f0(s, cont, f))

  def flattenChunks: ZStream[R, E, A] =
    chunks.flatMap(ZStream.fromChunk)

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  final def run[R1 <: R, E1 >: E, A0, A1 >: A, B](sink: ZSink[R1, E1, A0, Chunk[A1], B]): ZIO[R1, E1, B] =
    chunks.run(sink)

  /**
   * Filters this stream by the specified predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  def filter(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk[R, E, A](self.chunks.map(_.filter(pred)))

  def filterNot(pred: A => Boolean): ZStreamChunk[R, E, A] = filter(!pred(_))

  final def foreachWhile[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Unit] =
    chunks.foreachWhile[R1, E1] { as =>
      as.foldM(true) { (p, a) =>
        if (p) f(a)
        else IO.succeedLazy(p)
      }
    }

  final def foreach[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Unit]): ZIO[R1, E1, Unit] =
    foreachWhile[R1, E1](f(_).const(true))

  final def tap[R1 <: R, E1 >: E](f0: A => ZIO[R1, E1, _]): ZStreamChunk[R1, E1, A] =
    ZStreamChunk(chunks.tap[R1, E1] { as =>
      as.traverse_(f0)
    })

  def dropWhile(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk(new ZStream[R, E, Chunk[A]] {
      override def fold[R1 <: R, E1 >: E, A1 >: Chunk[A], S]: ZStream.Fold[R1, E1, A1, S] =
        IO.succeedLazy { (s, cont, f) =>
          self
            .foldLazyChunks[R1, E1, A, (Boolean, S)](true -> s)(tp => cont(tp._2)) {
              case ((true, s), as) =>
                val remaining = as.dropWhile(pred)

                if (remaining.length > 0) f(s, remaining).map(false -> _)
                else IO.succeed(true                                -> s)
              case ((false, s), as) => f(s, as).map(false -> _)
            }
            .map(_._2.asInstanceOf[S]) // Cast is redundant but unfortunately necessary to appease Scala 2.11
        }
    })

  def takeWhile(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk(new ZStream[R, E, Chunk[A]] {
      override def fold[R1 <: R, E1 >: E, A1 >: Chunk[A], S]: ZStream.Fold[R1, E1, A1, S] =
        IO.succeedLazy { (s, cont, f) =>
          self
            .foldLazyChunks[R1, E1, A, (Boolean, S)](true -> s)(tp => tp._1 && cont(tp._2)) { (s, as) =>
              val remaining = as.takeWhile(pred)

              if (remaining.length == as.length) f(s._2, as).map(true -> _)
              else f(s._2, remaining).map(false                       -> _)
            }
            .map(_._2.asInstanceOf[S]) // Cast is redundant but unfortunately necessary to appease Scala 2.11
        }
    })

  def zipWithIndex: ZStreamChunk[R, E, (A, Int)] =
    ZStreamChunk(
      new ZStream[R, E, Chunk[(A, Int)]] {
        override def fold[R1 <: R, E1 >: E, A1 >: Chunk[(A, Int)], S]: ZStream.Fold[R1, E1, A1, S] =
          IO.succeedLazy { (s, cont, f) =>
            chunks.fold[R1, E1, Chunk[A], (S, Int)].flatMap { f0 =>
              f0((s, 0), tp => cont(tp._1), {
                case ((s, index), as) =>
                  val zipped = as.zipWithIndexFrom(index)

                  f(s, zipped).map((_, index + as.length))
              }).map(_._1.asInstanceOf[S]) // Cast is redundant but unfortunately necessary to appease Scala 2.11
            }
          }
      }
    )

  final def mapAccum[@specialized S1, @specialized B](s1: S1)(f1: (S1, A) => (S1, B)): ZStreamChunk[R, E, B] =
    ZStreamChunk(chunks.mapAccum(s1)((s1: S1, as: Chunk[A]) => as.mapAccum(s1)(f1)))

  def map[@specialized B](f: A => B): ZStreamChunk[R, E, B] =
    ZStreamChunk(chunks.map(_.map(f)))

  def mapConcat[B](f: A => Chunk[B]): ZStreamChunk[R, E, B] =
    ZStreamChunk(chunks.map(_.flatMap(f)))

  final def mapM[R1 <: R, E1 >: E, B](f0: A => ZIO[R1, E1, B]): ZStreamChunk[R1, E1, B] =
    ZStreamChunk(chunks.mapM(_.traverse(f0)))

  final def flatMap[R1 <: R, E1 >: E, B](f0: A => ZStreamChunk[R1, E1, B]): ZStreamChunk[R1, E1, B] =
    ZStreamChunk(
      chunks.flatMap(_.map(f0).foldLeft[ZStream[R1, E1, Chunk[B]]](ZStream.empty)((acc, el) => acc ++ el.chunks))
    )

  final def ++[R1 <: R, E1 >: E, A1 >: A](that: ZStreamChunk[R1, E1, A1]): ZStreamChunk[R1, E1, A1] =
    ZStreamChunk(chunks ++ that.chunks)

  final def toQueue[E1 >: E, A1 >: A](capacity: Int = 1): ZManaged[R, Nothing, Queue[Take[E1, Chunk[A1]]]] =
    chunks.toQueue(capacity)

  final def toQueueWith[R1 <: R, E1 >: E, A1 >: A, Z](
    f: Queue[Take[E1, Chunk[A1]]] => ZIO[R1, E1, Z],
    capacity: Int = 1
  ): ZIO[R1, E1, Z] =
    toQueue[E1, A1](capacity).use(f)
}

object ZStreamChunk {
  final def apply[R, E, A](chunkStream: ZStream[R, E, Chunk[A]]): ZStreamChunk[R, E, A] =
    new ZStreamChunk[R, E, A] {
      val chunks = chunkStream
    }

  final def fromChunks[A](as: Chunk[A]*): StreamChunk[Nothing, A] =
    StreamChunkPure(StreamPure.fromIterable(as))

  final def succeedLazy[A](as: => Chunk[A]): StreamChunk[Nothing, A] =
    StreamChunkPure(StreamPure.succeedLazy(as))

  final val empty: StreamChunk[Nothing, Nothing] = StreamChunkPure(StreamPure.empty)
}
