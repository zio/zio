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

import com.github.ghik.silencer.silent

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
class ZStreamChunk[-R, +E, +A](val chunks: ZStream[R, E, Chunk[A]]) extends Serializable {
  self =>

  import ZStream.Pull

  /**
   * Concatenates with another stream in strict order
   */
  final def ++[R1 <: R, E1 >: E, A1 >: A](that: ZStreamChunk[R1, E1, A1]): ZStreamChunk[R1, E1, A1] =
    ZStreamChunk(chunks ++ that.chunks)

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * up to `capacity` chunks in a queue.
   *
   * @note Prefer capacities that are powers of 2 for better performance.
   */
  final def buffer(capacity: Int): ZStreamChunk[R, E, A] =
    ZStreamChunk(chunks.buffer(capacity))

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * elements into a dropping queue.
   *
   * @note Prefer capacities that are powers of 2 for better performance.
   */
  final def bufferDropping(capacity: Int): ZStreamChunk[R, E, A] =
    ZStreamChunk(chunks.bufferDropping(capacity))

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * elements into a sliding queue.
   *
   * @note Prefer capacities that are powers of 2 for better performance.
   */
  final def bufferSliding(capacity: Int): ZStreamChunk[R, E, A] =
    ZStreamChunk(chunks.bufferSliding(capacity))

  /**
   * Allows a faster producer to progress independently of a slower consumer by buffering
   * elements into an unbounded queue.
   */
  final def bufferUnbounded: ZStreamChunk[R, E, A] =
    ZStreamChunk(chunks.bufferUnbounded)

  /**
   * Switches over to the stream produced by the provided function in case this one
   * fails with a typed error.
   */
  final def catchAll[R1 <: R, E2, A1 >: A](
    f: E => ZStreamChunk[R1, E2, A1]
  )(implicit ev: CanFail[E]): ZStreamChunk[R1, E2, A1] =
    self.catchAllCause(_.failureOrCause.fold(f, c => ZStreamChunk(ZStream.halt(c))))

  /**
   * Switches over to the stream produced by the provided function in case this one
   * fails. Allows recovery from all errors, except external interruption.
   */
  final def catchAllCause[R1 <: R, E2, A1 >: A](f: Cause[E] => ZStreamChunk[R1, E2, A1]): ZStreamChunk[R1, E2, A1] =
    ZStreamChunk(chunks.catchAllCause(c => f(c).chunks))

  /**
   * Chunks the stream with specified chunkSize
   *
   * @param chunkSize size of the chunk
   */
  final def chunkN(chunkSize: Int): ZStreamChunk[R, E, A] =
    ZStreamChunk {
      ZStream {
        chunks.process.mapM { xss =>
          Ref.make[(Boolean, Chunk[A])](false -> Chunk.empty).map { state =>
            def pull: Pull[R, E, Chunk[A]] =
              state.get.flatMap {
                case (true, _) => Pull.end

                case (false, chunk) if chunk.length >= chunkSize =>
                  Pull.emitNow(chunk.take(chunkSize)) <* state.set(false -> chunk.drop(chunkSize))

                case (false, chunk0) =>
                  xss.foldM(
                    {
                      case None if chunk0.length == 0 => Pull.end
                      case None                       => Pull.emitNow(chunk0) <* state.set(true -> Chunk.empty)
                      case e @ Some(_)                => ZIO.fail(e)
                    },
                    xs =>
                      state.modify {
                        case (_, chunk) =>
                          if (chunk.length + xs.length >= chunkSize) {
                            val m = chunkSize - chunk.length
                            Pull.emitNow(chunk ++ xs.take(m)) -> (false -> xs.drop(m))
                          } else
                            pull -> (false -> (chunk ++ xs))
                      }.flatten
                  )
              }

            pull
          }
        }
      }
    }

  /**
   * Collects a filtered, mapped subset of the stream.
   */
  final def collect[B](p: PartialFunction[A, B]): ZStreamChunk[R, E, B] =
    ZStreamChunk(self.chunks.map(chunk => chunk.collect(p)))

  /**
   * Transforms all elements of the stream for as long as the specified partial function is defined.
   */
  def collectWhile[B](p: PartialFunction[A, B]): ZStreamChunk[R, E, B] =
    ZStreamChunk {
      ZStream {
        for {
          chunks  <- self.chunks.process
          doneRef <- Ref.make(false).toManaged_
          pull = doneRef.get.flatMap { done =>
            if (done) Pull.end
            else
              for {
                chunk     <- chunks
                remaining = chunk.collectWhile(p)
                _         <- doneRef.set(true).when(remaining.length < chunk.length)
              } yield remaining
          }
        } yield pull
      }
    }

  /**
   * Drops the specified number of elements from this stream.
   */
  def drop(n: Int): ZStreamChunk[R, E, A] =
    ZStreamChunk {
      ZStream {
        for {
          chunks     <- self.chunks.process
          counterRef <- Ref.make(n).toManaged_
          pull = {
            def go: Pull[R, E, Chunk[A]] =
              chunks.flatMap { chunk =>
                counterRef.get.flatMap { cnt =>
                  if (cnt <= 0) Pull.emitNow(chunk)
                  else {
                    val remaining = chunk.drop(cnt)
                    val dropped   = chunk.length - remaining.length
                    counterRef.set(cnt - dropped) *>
                      (if (remaining.isEmpty) go else Pull.emitNow(remaining))
                  }
                }
              }

            go
          }
        } yield pull
      }
    }

  /**
   * Drops all elements of the stream until the specified predicate evaluates
   * to `true`.
   */
  final def dropUntil(pred: A => Boolean): ZStreamChunk[R, E, A] =
    dropWhile(!pred(_)).drop(1)

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def dropWhile(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk {
      ZStream {
        for {
          chunks          <- self.chunks.process
          keepDroppingRef <- Ref.make(true).toManaged_
          pull = {
            def go: Pull[R, E, Chunk[A]] =
              chunks.flatMap { chunk =>
                keepDroppingRef.get.flatMap { keepDropping =>
                  if (!keepDropping) Pull.emitNow(chunk)
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
   * Executes the provided finalizer after this stream's finalizers run.
   */
  final def ensuring[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStreamChunk[R1, E, A] =
    ZStreamChunk(chunks.ensuring(fin))

  /**
   * Executes the provided finalizer before this stream's finalizers run.
   */
  final def ensuringFirst[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStreamChunk[R1, E, A] =
    ZStreamChunk(chunks.ensuringBeforeFinalizer(fin))

  /**
   * Returns a stream whose failures and successes have been lifted into an
   * `Either`. The resulting stream cannot fail, because the failures have
   * been exposed as part of the `Either` success case.
   *
   * @note the stream will end as soon as the first error occurs.
   */
  final def either(implicit ev: CanFail[E]): ZStreamChunk[R, Nothing, Either[E, A]] =
    self.map(Right(_)).catchAll(e => ZStreamChunk.succeedNow(Chunk.single(Left(e))))

  /**
   * Filters this stream by the specified predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  def filter(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk(self.chunks.map(_.filter(pred)))

  /**
   * Filters this stream by the specified effectful predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  final def filterM[R1 <: R, E1 >: E](pred: A => ZIO[R1, E1, Boolean]): ZStreamChunk[R1, E1, A] =
    ZStreamChunk(self.chunks.mapM(_.filterM(pred)))

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
  def flattenChunks: ZStream[R, E, A] = chunks.flatMap(ZStream.fromChunk(_))

  /**
   * Executes a pure fold over the stream of values - reduces all elements in the stream to a value of type `S`.
   * See [[ZStream.fold]]
   */
  def fold[S](s: S)(f: (S, A) => S): ZIO[R, E, S] =
    chunks
      .foldWhileManagedM(s)(_ => true) { (s: S, as: Chunk[A]) =>
        as.foldM[Any, Nothing, S](s)((s, a) => ZIO.succeedNow(f(s, a)))
      }
      .use(ZIO.succeedNow)

  /**
   * Executes an effectful fold over the stream of values.
   * See [[ZStream.foldM]]
   */
  final def foldM[R1 <: R, E1 >: E, S](s: S)(f: (S, A) => ZIO[R1, E1, S]): ZIO[R1, E1, S] =
    chunks
      .foldWhileManagedM[R1, E1, S](s)(_ => true)((s, as) => as.foldM(s)(f))
      .use(ZIO.succeedNow)

  /**
   * Executes a pure fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   * See [[ZStream.foldManaged]]
   */
  final def foldManaged[S](s: S)(f: (S, A) => S): ZManaged[R, E, S] =
    chunks
      .foldWhileManagedM(s)(_ => true) { (s: S, as: Chunk[A]) =>
        as.foldM[Any, Nothing, S](s)((s, a) => ZIO.succeedNow(f(s, a)))
      }

  /**
   * Executes an effectful fold over the stream of values.
   * Returns a Managed value that represents the scope of the stream.
   * See [[ZStream.foldManagedM]]
   */
  final def foldManagedM[R1 <: R, E1 >: E, S](s: S)(f: (S, A) => ZIO[R1, E1, S]): ZManaged[R1, E1, S] =
    chunks.foldWhileManagedM[R1, E1, S](s)(_ => true)((s, as) => as.foldM(s)(f))

  /**
   * Reduces the elements in the stream to a value of type `S`.
   * Stops the fold early when the condition is not fulfilled.
   * See [[ZStream.foldWhile]]
   */
  final def foldWhile[S](s: S)(cont: S => Boolean)(f: (S, A) => S): ZIO[R, E, S] =
    chunks
      .foldWhileManagedM(s)(cont)((s: S, as: Chunk[A]) => as.foldWhileM(s)(cont)((s, a) => ZIO.succeedNow(f(s, a))))
      .use(ZIO.succeedNow)

  /**
   * Executes an effectful fold over the stream of values.
   * Stops the fold early when the condition is not fulfilled.
   * See [[ZStream.foldWhileM]]
   */
  final def foldWhileM[R1 <: R, E1 >: E, S](s: S)(cont: S => Boolean)(f: (S, A) => ZIO[R1, E1, S]): ZIO[R1, E1, S] =
    chunks
      .foldWhileManagedM[R1, E1, S](s)(cont)((s, as) => as.foldWhileM(s)(cont)(f))
      .use(ZIO.succeedNow)

  /**
   * Executes a pure fold over the stream of values.
   * Stops the fold early when the condition is not fulfilled.
   * See [[ZStream.foldWhileManaged]]
   */
  def foldWhileManaged[S](s: S)(cont: S => Boolean)(f: (S, A) => S): ZManaged[R, E, S] =
    chunks.foldWhileManagedM(s)(cont) { (s: S, as: Chunk[A]) =>
      as.foldWhileM[Any, Nothing, S](s)(cont)((s, a) => ZIO.succeedNow(f(s, a)))
    }

  /**
   * Executes an effectful fold over the stream of values.
   * Stops the fold early when the condition is not fulfilled.
   * See [[ZStream.foldWhileManagedM]]
   */
  final def foldWhileManagedM[R1 <: R, E1 >: E, S](
    s: S
  )(cont: S => Boolean)(f: (S, A) => ZIO[R1, E1, S]): ZManaged[R1, E1, S] =
    chunks.foldWhileManagedM[R1, E1, S](s)(cont)((s, as) => as.foldWhileM(s)(cont)(f))

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
      as.foldWhileM(true)(identity) { (p, a) =>
        if (p) f(a)
        else IO.succeedNow(p)
      }
    }

  /**
   * Returns a stream made of the elements of this stream transformed with `f0`
   */
  def map[B](f: A => B): ZStreamChunk[R, E, B] =
    ZStreamChunk(chunks.map(_.map(f)))

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  final def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): ZStreamChunk[R, E, B] =
    ZStreamChunk(chunks.mapAccum(s1)((s2, as) => as.mapAccum(s2)(f1)))

  /**
   * Statefully and effectfully maps over the elements of this stream to produce
   * new elements.
   */
  final def mapAccumM[R1 <: R, E1 >: E, S1, B](s1: S1)(f1: (S1, A) => ZIO[R1, E1, (S1, B)]): ZStreamChunk[R1, E1, B] =
    ZStreamChunk(chunks.mapAccumM[R1, E1, S1, Chunk[B]](s1)((s2, as) => as.mapAccumM(s2)(f1)))

  /**
   * Maps each element to an iterable and flattens the iterables into the output of
   * this stream.
   */
  final def mapConcat[B](f: A => Iterable[B]): ZStreamChunk[R, E, B] =
    mapConcatChunk(f andThen Chunk.fromIterable)

  /**
   * Maps each element to a chunk and flattens the chunks into the output of
   * this stream.
   */
  def mapConcatChunk[B](f: A => Chunk[B]): ZStreamChunk[R, E, B] =
    ZStreamChunk(chunks.map(_.flatMap(f)))

  /**
   * Effectfully maps each element to a chunk and flattens the chunks into the
   * output of this stream.
   */
  final def mapConcatChunkM[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, Chunk[B]]): ZStreamChunk[R1, E1, B] =
    mapM(f).mapConcatChunk(identity)

  /**
   * Effectfully maps each element to an iterable and flattens the iterables into the
   * output of this stream.
   */
  final def mapConcatM[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, Iterable[B]]): ZStreamChunk[R1, E1, B] =
    mapM(a => f(a).map(Chunk.fromIterable(_))).mapConcatChunk(identity)

  /**
   * Transforms the errors that possibly result from this stream.
   */
  final def mapError[E1](f: E => E1)(implicit ev: CanFail[E]): ZStreamChunk[R, E1, A] =
    ZStreamChunk(chunks.mapError(f))

  /**
   * Transforms the errors that possibly result from this stream.
   */
  final def mapErrorCause[E1](f: Cause[E] => Cause[E1]): ZStreamChunk[R, E1, A] =
    ZStreamChunk(chunks.mapErrorCause(f))

  /**
   * Maps over elements of the stream with the specified effectful function.
   */
  final def mapM[R1 <: R, E1 >: E, B](f0: A => ZIO[R1, E1, B]): ZStreamChunk[R1, E1, B] =
    ZStreamChunk(chunks.mapM(_.mapM(f0)))

  /**
   * Switches to the provided stream in case this one fails with a typed error.
   *
   * See also [[ZStream#catchAll]].
   */
  final def orElse[R1 <: R, E2, A1 >: A](
    that: => ZStreamChunk[R1, E2, A1]
  )(implicit ev: CanFail[E]): ZStreamChunk[R1, E2, A1] =
    self.catchAll(_ => that)

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
                chunks.flatMap(chunk => chunkRef.set(chunk) *> indexRef.set(0) *> go)
            }
          }

        go
      }
    } yield pull

  /**
   * Provides the stream with its required environment, which eliminates
   * its dependency on `R`.
   */
  final def provide(r: R)(implicit ev: NeedsEnv[R]): StreamChunk[E, A] =
    provideSome(_ => r)

  /**
   * Provides some of the environment required to run this effect,
   * leaving the remainder `R0`.
   */
  final def provideSome[R0](env: R0 => R)(implicit ev: NeedsEnv[R]): ZStreamChunk[R0, E, A] =
    ZStreamChunk(chunks.provideSome(env))

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  final def run[R1 <: R, E1 >: E, A0, A1 >: A, B](sink: ZSink[R1, E1, A0, Chunk[A1], B]): ZIO[R1, E1, B] =
    chunks.run(sink)

  /**
   * Runs the stream and collects all of its elements in a list.
   *
   * Equivalent to `run(Sink.collectAll[A])`.
   */
  final def runCollect: ZIO[R, E, List[A]] =
    chunks.runCollect.map(_.flatten)

  /**
   * Takes the specified number of elements from this stream.
   */
  def take(n: Int): ZStreamChunk[R, E, A] =
    ZStreamChunk {
      ZStream {
        for {
          chunks     <- self.chunks.process
          counterRef <- Ref.make(n).toManaged_
          pull = counterRef.get.flatMap { cnt =>
            if (cnt <= 0) Pull.end
            else
              for {
                chunk <- chunks
                taken = chunk.take(cnt)
                _     <- counterRef.set(cnt - taken.length)
              } yield taken
          }
        } yield pull
      }
    }

  /**
   * Takes all elements of the stream until the specified predicate evaluates
   * to `true`.
   */
  def takeUntil(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk {
      ZStream {
        for {
          chunks        <- self.chunks.process
          keepTakingRef <- Ref.make(true).toManaged_
          pull = keepTakingRef.get.flatMap { keepTaking =>
            if (!keepTaking) Pull.end
            else
              for {
                chunk <- chunks
                taken = chunk.takeWhile(!pred(_))
                last  = chunk.drop(taken.length).take(1)
                _     <- keepTakingRef.set(false).when(last.nonEmpty)
              } yield taken ++ last
          }
        } yield pull
      }
    }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def takeWhile(pred: A => Boolean): ZStreamChunk[R, E, A] =
    ZStreamChunk {
      ZStream {
        for {
          chunks  <- self.chunks.process
          doneRef <- Ref.make(false).toManaged_
          pull = doneRef.get.flatMap { done =>
            if (done) Pull.end
            else
              for {
                chunk <- chunks
                taken = chunk.takeWhile(pred)
                _     <- doneRef.set(true).when(taken.length < chunk.length)
              } yield taken
          }
        } yield pull
      }
    }

  /**
   * Adds an effect to consumption of every element of the stream.
   */
  final def tap[R1 <: R, E1 >: E](f0: A => ZIO[R1, E1, Any]): ZStreamChunk[R1, E1, A] =
    ZStreamChunk(chunks.tap[R1, E1](as => as.mapM_(f0)))

  @silent("never used")
  def toInputStream(implicit ev0: E <:< Throwable, ev1: A <:< Byte): ZManaged[R, E, java.io.InputStream] =
    for {
      runtime <- ZIO.runtime[R].toManaged_
      pull    <- process
      javaStream = new java.io.InputStream {
        override def read(): Int = {
          val exit = runtime.unsafeRunSync[Option[Throwable], Byte](pull.asInstanceOf[Pull[R, Throwable, Byte]])
          ZStream.exitToInputStreamRead(exit)
        }
      }
    } yield javaStream

  /**
   * Converts the stream to a managed queue. After managed the queue is used,
   * the queue will never again produce chunks and should be discarded.
   */
  final def toQueue[E1 >: E, A1 >: A](capacity: Int = 2): ZManaged[R, Nothing, Queue[Take[E1, Chunk[A1]]]] =
    chunks.toQueue(capacity)

  /**
   * Converts the stream into an unbounded managed queue. After the managed queue
   * is used, the queue will never again produce values and should be discarded.
   */
  final def toQueueUnbounded[E1 >: E, A1 >: A]: ZManaged[R, Nothing, Queue[Take[E1, Chunk[A1]]]] =
    chunks.toQueueUnbounded

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
   * Threads the stream through the transformation function `f`.
   */
  final def via[R2, E2, B](f: ZStreamChunk[R, E, A] => ZStreamChunk[R2, E2, B]): ZStreamChunk[R2, E2, B] = f(self)

  /**
   * Zips this stream together with the index of elements of the stream across chunks.
   */
  final def zipWithIndex: ZStreamChunk[R, E, (A, Long)] =
    self.mapAccum(0L)((index, a) => (index + 1, (a, index)))
}

object ZStreamChunk {

  /**
   * The default chunk size used by the various combinators and constructors of [[ZStreamChunk]].
   */
  final val DefaultChunkSize = 4096

  /**
   * The empty stream of chunks
   */
  val empty: StreamChunk[Nothing, Nothing] =
    new StreamEffectChunk(StreamEffect.empty)

  /**
   * Creates a `ZStreamChunk` from a stream of chunks
   */
  def apply[R, E, A](chunkStream: ZStream[R, E, Chunk[A]]): ZStreamChunk[R, E, A] =
    new ZStreamChunk[R, E, A](chunkStream)

  /**
   * Creates a `ZStreamChunk` from a variable list of chunks
   */
  def fromChunks[A](as: Chunk[A]*): StreamChunk[Nothing, A] =
    new StreamEffectChunk(StreamEffect.fromIterable(as))

  /**
   * Creates a `ZStreamChunk` from a lazily evaluated chunk
   */
  def succeed[A](as: => Chunk[A]): StreamChunk[Nothing, A] =
    new StreamEffectChunk(StreamEffect.succeed(as))

  /**
   * Creates a `ZStreamChunk` from an eagerly evaluated chunk
   */
  private[zio] def succeedNow[A](as: Chunk[A]): StreamChunk[Nothing, A] =
    succeed(as)
}
