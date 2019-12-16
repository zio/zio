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

import java.io.{ IOException, InputStream }
import java.{ util => ju }

import zio._
import zio.clock.Clock
import zio.Cause

object Stream extends Serializable {
  import ZStream.Pull

  /**
   * See [[ZStream.empty]]
   */
  final val empty: Stream[Nothing, Nothing] =
    ZStream.empty

  /**
   * See [[ZStream.never]]
   */
  final val never: Stream[Nothing, Nothing] =
    ZStream.never

  /**
   * See [[ZStream.apply[A]*]]
   */
  final def apply[A](as: A*): Stream[Nothing, A] = ZStream(as: _*)

  /**
   * See [[ZStream.apply[R,E,A]*]]
   */
  final def apply[E, A](pull: Managed[Nothing, Pull[Any, E, A]]): Stream[E, A] = ZStream(pull)

  /**
   * See [[ZStream.bracket]]
   */
  final def bracket[E, A](acquire: IO[E, A])(release: A => UIO[Any]): Stream[E, A] =
    ZStream.bracket(acquire)(release)

  /**
   * See [[ZStream.bracketExit]]
   */
  final def bracketExit[E, A](acquire: IO[E, A])(release: (A, Exit[Any, Any]) => UIO[Any]): Stream[E, A] =
    ZStream.bracketExit(acquire)(release)

  /**
   *  @see [[zio.ZStream.crossN]]
   */
  final def crossN[E, A, B, C](stream1: Stream[E, A], stream2: Stream[E, B])(f: (A, B) => C): Stream[E, C] =
    ZStream.crossN(stream1, stream2)(f)

  /**
   *  @see [[zio.ZStream.crossN]]
   */
  final def crossN[E, A, B, C, D](stream1: Stream[E, A], stream2: Stream[E, B], stream3: Stream[E, C])(
    f: (A, B, C) => D
  ): Stream[E, D] =
    ZStream.crossN(stream1, stream2, stream3)(f)

  /**
   *  @see [[zio.ZStream.crossN]]
   */
  final def crossN[E, A, B, C, D, F](
    stream1: Stream[E, A],
    stream2: Stream[E, B],
    stream3: Stream[E, C],
    stream4: Stream[E, D]
  )(
    f: (A, B, C, D) => F
  ): Stream[E, F] =
    ZStream.crossN(stream1, stream2, stream3, stream4)(f)

  /**
   * See [[ZStream.die]]
   */
  final def die(ex: Throwable): Stream[Nothing, Nothing] =
    ZStream.die(ex)

  /**
   * See [[ZStream.dieMessage]]
   */
  final def dieMessage(msg: String): Stream[Nothing, Nothing] =
    ZStream.dieMessage(msg)

  /**
   * See [[ZStream.effectAsync]]
   */
  final def effectAsync[E, A](
    register: (IO[Option[E], A] => Unit) => Unit,
    outputBuffer: Int = 16
  ): Stream[E, A] =
    ZStream.effectAsync(register, outputBuffer)

  /**
   * See [[ZStream.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[E, A](
    register: (IO[Option[E], A] => Unit) => Option[Stream[E, A]],
    outputBuffer: Int = 16
  ): Stream[E, A] =
    ZStream.effectAsyncMaybe(register, outputBuffer)

  /**
   * See [[ZStream.effectAsyncM]]
   */
  final def effectAsyncM[E, A](
    register: (IO[Option[E], A] => Unit) => IO[E, Any],
    outputBuffer: Int = 16
  ): Stream[E, A] =
    ZStream.effectAsyncM(register, outputBuffer)

  /**
   * See [[ZStream.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[E, A](
    register: (IO[Option[E], A] => Unit) => Either[Canceler[Any], Stream[E, A]],
    outputBuffer: Int = 16
  ): Stream[E, A] =
    ZStream.effectAsyncInterrupt(register, outputBuffer)

  /**
   * See [[ZStream.fail]]
   */
  final def fail[E](error: E): Stream[E, Nothing] =
    ZStream.fail(error)

  /**
   * See [[ZStream.finalizer]]
   */
  final def finalizer(finalizer: UIO[Any]): Stream[Nothing, Nothing] =
    ZStream.finalizer(finalizer)

  /**
   * See [[ZStream.flatten]]
   */
  final def flatten[E, A](fa: Stream[E, Stream[E, A]]): Stream[E, A] =
    ZStream.flatten(fa)

  /**
   * See [[ZStream.flattenPar]]
   */
  final def flattenPar[E, A](n: Int, outputBuffer: Int = 16)(
    fa: Stream[E, Stream[E, A]]
  ): Stream[E, A] =
    ZStream.flattenPar(n, outputBuffer)(fa)

  /**
   * See [[ZStream.fromInputStream]]
   */
  final def fromInputStream(
    is: InputStream,
    chunkSize: Int = ZStreamChunk.DefaultChunkSize
  ): StreamEffectChunk[Any, IOException, Byte] =
    ZStream.fromInputStream(is, chunkSize)

  /**
   * See [[ZStream.fromChunk]]
   */
  final def fromChunk[A](c: Chunk[A]): Stream[Nothing, A] =
    ZStream.fromChunk(c)

  /**
   * See [[ZStream.fromEffect]]
   */
  final def fromEffect[E, A](fa: IO[E, A]): Stream[E, A] =
    ZStream.fromEffect(fa)

  /**
   * See [[ZStream.fromPull]]
   */
  final def fromPull[E, A](pull: Pull[Any, E, A]): Stream[E, A] =
    ZStream.fromPull(pull)

  /**
   * See [[ZStream.paginate]]
   */
  final def paginate[A, S](s: S)(f: S => (A, Option[S])): Stream[Nothing, A] =
    ZStream.paginate(s)(f)

  /**
   * See [[ZStream.paginateM]]
   */
  final def paginateM[E, A, S](s: S)(f: S => IO[E, (A, Option[S])]): Stream[E, A] =
    ZStream.paginateM(s)(f)

  /**
   * See [[ZStream.repeatEffect]]
   */
  final def repeatEffect[E, A](fa: IO[E, A]): Stream[E, A] =
    ZStream.repeatEffect(fa)

  /**
   * See [[ZStream.repeatEffectWith]]
   */
  final def repeatEffectWith[E, A](
    fa: IO[E, A],
    schedule: Schedule[Any, Unit, Any]
  ): ZStream[Clock, E, A] = ZStream.repeatEffectWith(fa, schedule)

  /**
   * See [[ZStream.fromIterable]]
   */
  final def fromIterable[A](as: Iterable[A]): Stream[Nothing, A] =
    ZStream.fromIterable(as)

  /**
   * See [[ZStream.fromIterator]]
   */
  final def fromIterator[E, A](iterator: IO[E, Iterator[A]]): Stream[E, A] =
    ZStream.fromIterator(iterator)

  /**
   * See [[ZStream.fromIteratorManaged]]
   */
  final def fromIteratorManaged[E, A](iterator: Managed[E, Iterator[A]]): Stream[E, A] =
    ZStream.fromIteratorManaged(iterator)

  /**
   * See [[ZStream.fromJavaIterator]]
   */
  final def fromJavaIterator[E, A](iterator: IO[E, ju.Iterator[A]]): Stream[E, A] =
    ZStream.fromJavaIterator(iterator)

  /**
   * See [[ZStream.fromJavaIteratorManaged]]
   */
  final def fromJavaIteratorManaged[E, A](iterator: Managed[E, ju.Iterator[A]]): Stream[E, A] =
    ZStream.fromJavaIteratorManaged(iterator)

  /**
   * See [[ZStream.fromQueue]]
   */
  final def fromQueue[E, A](queue: ZQueue[Nothing, Any, Any, E, Nothing, A]): Stream[E, A] =
    ZStream.fromQueue(queue)

  /**
   * See [[ZStream.fromQueueWithShutdown]]
   */
  final def fromQueueWithShutdown[E, A](queue: ZQueue[Nothing, Any, Any, E, Nothing, A]): Stream[E, A] =
    ZStream.fromQueueWithShutdown(queue)

  /**
   * See [[ZStream.repeatPull]]
   */
  final def repeatPull[E, A](pull: Pull[Any, E, A]): Stream[E, A] =
    ZStream.repeatPull(pull)

  /**
   * See [[ZStream.halt]]
   */
  final def halt[E](cause: Cause[E]): Stream[E, Nothing] = fromEffect(ZIO.halt(cause))

  /**
   * See [[ZStream.iterate]]
   */
  final def iterate[A](a: A)(f: A => A): ZStream[Any, Nothing, A] = Stream.unfold(a)(a => Some(a -> f(a)))

  /**
   * See [[ZStream.managed]]
   */
  final def managed[E, A](managed: Managed[E, A]): Stream[E, A] =
    ZStream.managed(managed)

  /**
   * See [[ZStream.mergeAll]]
   */
  final def mergeAll[E, A](n: Int, outputBuffer: Int = 16)(
    streams: Stream[E, A]*
  ): Stream[E, A] =
    ZStream.mergeAll[Any, E, A](n, outputBuffer)(streams: _*)

  /**
   * See [[ZStream.range]]
   */
  final def range(min: Int, max: Int): Stream[Nothing, Int] =
    ZStream.range(min, max)

  /**
   * See [[ZStream.succeed]]
   */
  final def succeed[A](a: A): Stream[Nothing, A] =
    ZStream.succeed(a)

  /**
   * See [[ZStream.unfold]]
   */
  final def unfold[S, A](s: S)(f0: S => Option[(A, S)]): Stream[Nothing, A] =
    ZStream.unfold(s)(f0)

  /**
   * See [[ZStream.unfoldM]]
   */
  final def unfoldM[E, A, S](s: S)(f0: S => IO[E, Option[(A, S)]]): Stream[E, A] =
    ZStream.unfoldM(s)(f0)

  /**
   * See [[ZStream.unwrap]]
   */
  final def unwrap[E, A](fa: IO[E, Stream[E, A]]): Stream[E, A] =
    ZStream.unwrap(fa)

  /**
   * See [[ZStream.unwrapManaged]]
   */
  final def unwrapManaged[E, A](fa: Managed[E, ZStream[Any, E, A]]): Stream[E, A] =
    ZStream.unwrapManaged(fa)

  /**
   *  @see [[zio.ZStream.zipN]]
   */
  final def zipN[E, A, B, C](stream1: Stream[E, A], stream2: Stream[E, B])(f: (A, B) => C): Stream[E, C] =
    ZStream.zipN(stream1, stream2)(f)

  /**
   *  @see [[zio.ZStream.zipN]]
   */
  final def zipN[E, A, B, C, D](stream1: Stream[E, A], stream2: Stream[E, B], stream3: Stream[E, C])(
    f: (A, B, C) => D
  ): Stream[E, D] =
    ZStream.zipN(stream1, stream2, stream3)(f)

  /**
   *  @see [[zio.ZStream.zipN]]
   */
  final def zipN[E, A, B, C, D, F](
    stream1: Stream[E, A],
    stream2: Stream[E, B],
    stream3: Stream[E, C],
    stream4: Stream[E, D]
  )(
    f: (A, B, C, D) => F
  ): Stream[E, F] =
    ZStream.zipN(stream1, stream2, stream3, stream4)(f)

}
