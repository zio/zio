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

/**
 * A `ZStreamChunk[R, E, A]` represents an effectful stream that can produce values of
 * type `A`, or potentially fail with a value of type `E`.
 *
 * `ZStreamChunk` differs from `ZStream` in that elements in the stream are processed
 * in batches, which is orders of magnitude more efficient than dealing with each
 * element individually.
 *
 * `ZStreamChunk` is particularly suited for situations where you are dealing with values
 * of primitive types, e.g. those coming off a `java.io.InputStream`
 */
class ZStreamChunk[-R, +E, @specialized +A](val chunks: ZStream[R, E, Chunk[A]]) { self =>
  import ZStream.Pull

  /**
   * Concatenates with another stream in strict order
   */
  final def ++[R1 <: R, E1 >: E, A1 >: A](that: ZStreamChunk[R1, E1, A1]): ZStreamChunk[R1, E1, A1] =
    ZStreamChunk(chunks ++ that.chunks)

  /**
   * Collects a filtered, mapped subset of the stream.
   */
  final def collect[B](p: PartialFunction[A, B]): ZStreamChunk[R, E, B] =
    ZStreamChunk(self.chunks.map(chunk => chunk.collect(p)))

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  final def dropWhile(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk {
      ZStream[R, E, Chunk[A]] {
        for {
          chunks          <- self.chunks.process
          keepDroppingRef <- Ref.make(true).toManaged_
          pull = {
            def go: Pull[R, E, Chunk[A]] =
              chunks.flatMap { chunk =>
                keepDroppingRef.get.flatMap { keepDropping =>
                  if (!keepDropping) Pull.emit(chunk)
                  else {
                    val remaining = chunk.dropWhile(pred)
                    val empty     = remaining.length <= 0

                    if (empty) go
                    else keepDroppingRef.set(false).as(remaining)
                  }
                }
              }

            go
          }
        } yield pull
      }
    }

  /**
   * Filters this stream by the specified predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  def filter(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk[R, E, A](self.chunks.map(_.filter(pred)))

  /**
   * Filters this stream by the specified predicate, removing all elements for
   * which the predicate evaluates to true.
   */
  final def filterNot(pred: A => Boolean): ZStreamChunk[R, E, A] = filter(!pred(_))

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f0`
   */
  final def flatMap[R1 <: R, E1 >: E, B](f0: A => ZStreamChunk[R1, E1, B]): ZStreamChunk[R1, E1, B] =
    ZStreamChunk(
      chunks.flatMap(_.map(f0).foldLeft[ZStream[R1, E1, Chunk[B]]](ZStream.empty)((acc, el) => acc ++ el.chunks))
    )

  /**
   * Returns a stream made of the concatenation of all the chunks in this stream
   */
  final def flattenChunks: ZStream[R, E, A] = chunks.flatMap(ZStream.fromChunk)

  /**
   * Executes an effectful fold over the stream of values.
   */
  final def foldManaged[R1 <: R, E1 >: E, A1 >: A, S](
    s: S
  )(cont: S => Boolean)(f: (S, A1) => ZIO[R1, E1, S]): ZManaged[R1, E1, S] =
    chunks.foldManaged[R1, E1, Chunk[A1], S](s)(cont) { (s, as) =>
      as.foldMLazy(s)(cont)(f)
    }

  final def fold[R1 <: R, E1 >: E, A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => ZIO[R1, E1, S]): ZIO[R1, E1, S] =
    foldManaged[R1, E1, A1, S](s)(cont)(f).use(ZIO.succeed)

  /**
   * Executes an effectful fold over the stream of chunks.
   */
  final def foldChunksManaged[R1 <: R, E1 >: E, A1 >: A, S](
    s: S
  )(cont: S => Boolean)(f: (S, Chunk[A1]) => ZIO[R1, E1, S]): ZManaged[R1, E1, S] =
    chunks.foldManaged[R1, E1, Chunk[A1], S](s)(cont)(f)

  final def foldChunks[R1 <: R, E1 >: E, A1 >: A, S](
    s: S
  )(cont: S => Boolean)(f: (S, Chunk[A1]) => ZIO[R1, E1, S]): ZIO[R1, E1, S] =
    chunks.fold[R1, E1, Chunk[A1], S](s)(cont)(f)

  /**
   * Reduces the elements in the stream to a value of type `S`
   */
  def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): ZIO[R, E, S] =
    fold[R, E, A1, S](s)(_ => true)((s, a) => ZIO.succeed(f(s, a)))

  /**
   * Consumes all elements of the stream, passing them to the specified callback.
   */
  final def foreach[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Unit]): ZIO[R1, E1, Unit] =
    foreachWhile[R1, E1](f(_).as(true))

  /**
   * Consumes elements of the stream, passing them to the specified callback,
   * and terminating consumption when the callback returns `false`.
   */
  final def foreachWhile[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Unit] =
    chunks.foreachWhile[R1, E1] { as =>
      as.foldMLazy(true)(identity) { (p, a) =>
        if (p) f(a)
        else IO.succeed(p)
      }
    }

  /**
   * Returns a stream made of the elements of this stream transformed with `f0`
   */
  def map[@specialized B](f: A => B): ZStreamChunk[R, E, B] =
    ZStreamChunk(chunks.map(_.map(f)))

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  final def mapAccum[@specialized S1, @specialized B](s1: S1)(f1: (S1, A) => (S1, B)): ZStreamChunk[R, E, B] =
    ZStreamChunk(chunks.mapAccum(s1)((s1: S1, as: Chunk[A]) => as.mapAccum(s1)(f1)))

  /**
   * Maps each element to a chunk, and flattens the chunks into the output of
   * this stream.
   */
  def mapConcat[B](f: A => Chunk[B]): ZStreamChunk[R, E, B] =
    ZStreamChunk(chunks.map(_.flatMap(f)))

  /**
   * Maps over elements of the stream with the specified effectful function.
   */
  final def mapM[R1 <: R, E1 >: E, B](f0: A => ZIO[R1, E1, B]): ZStreamChunk[R1, E1, B] =
    ZStreamChunk(chunks.mapM(_.mapM(f0)))

  final def process =
    for {
      chunks   <- self.chunks.process
      chunkRef <- Ref.make[Chunk[A]](Chunk.empty).toManaged_
      indexRef <- Ref.make(0).toManaged_
      pull = {
        def go: Pull[R, E, A] =
          chunkRef.get.flatMap { chunk =>
            indexRef.get.flatMap { index =>
              if (index < chunk.length) indexRef.set(index + 1).as(chunk(index))
              else
                chunks.flatMap { chunk =>
                  chunkRef.set(chunk) *> indexRef.set(0) *> go
                }
            }
          }

        go
      }
    } yield pull

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  final def run[R1 <: R, E1 >: E, A0, A1 >: A, B](sink: ZSink[R1, E1, A0, Chunk[A1], B]): ZIO[R1, E1, B] =
    chunks.run(sink)

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  final def takeWhile(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk {
      ZStream[R, E, Chunk[A]] {
        for {
          chunks  <- self.chunks.process
          doneRef <- Ref.make(false).toManaged_
          pull = doneRef.get.flatMap { done =>
            if (done) Pull.end
            else
              for {
                chunk     <- chunks
                remaining = chunk.takeWhile(pred)
                _         <- doneRef.set(true).when(remaining.length < chunk.length)
              } yield remaining
          }
        } yield pull
      }
    }

  /**
   * Adds an effect to consumption of every element of the stream.
   */
  final def tap[R1 <: R, E1 >: E](f0: A => ZIO[R1, E1, _]): ZStreamChunk[R1, E1, A] =
    ZStreamChunk(chunks.tap[R1, E1] { as =>
      as.mapM_(f0)
    })

  /**
   * Converts the stream to a managed queue. After managed queue is used, the
   * queue will never again produce chunks and should be discarded.
   */
  final def toQueue[E1 >: E, A1 >: A](capacity: Int = 2): ZManaged[R, E1, Queue[Take[E1, Chunk[A1]]]] =
    chunks.toQueue(capacity)

  /**
   * Converts the stream to a managed queue and immediately consume its
   * elements.
   */
  final def toQueueWith[R1 <: R, E1 >: E, A1 >: A, Z](
    f: Queue[Take[E1, Chunk[A1]]] => ZIO[R1, E1, Z],
    capacity: Int = 1
  ): ZIO[R1, E1, Z] =
    toQueue[E1, A1](capacity).use(f)

  /**
   * Zips this stream together with the index of elements of the stream across chunks.
   */
  final def zipWithIndex: ZStreamChunk[R, E, (A, Int)] =
    self.mapAccum(0)((index, a) => (index + 1, (a, index)))
}

object ZStreamChunk {

  /**
   * The default chunk size used by the various combinators and constructors of [[ZStreamChunk]].
   */
  final val DefaultChunkSize: Int = 4096

  /**
   * The empty stream of chunks
   */
  final val empty: StreamChunk[Nothing, Nothing] =
    new StreamChunk[Nothing, Nothing](Stream.empty)

  /**
   * Creates a `ZStreamChunk` from a stream of chunks
   */
  final def apply[R, E, A](chunkStream: ZStream[R, E, Chunk[A]]): ZStreamChunk[R, E, A] =
    new ZStreamChunk[R, E, A](chunkStream)

  /**
   * Creates a `ZStreamChunk` from a variable list of chunks
   */
  final def fromChunks[A](as: Chunk[A]*): StreamChunk[Nothing, A] =
    new StreamChunk[Nothing, A](Stream.fromIterable(as))

  /**
   * Creates a `ZStreamChunk` from a chunk
   */
  final def succeed[A](as: Chunk[A]): StreamChunk[Nothing, A] =
    new StreamChunk[Nothing, A](Stream.succeed(as))
}
