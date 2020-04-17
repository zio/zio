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

import zio.Cause
import zio._
import zio.clock.Clock
import zio.stm.TQueue

object Stream extends Serializable {
  import ZStream.Pull

  /**
   * See [[ZStream.empty]]
   */
  val empty: Stream[Nothing, Nothing] =
    ZStream.empty

  /**
   * See [[ZStream.never]]
   */
  val never: Stream[Nothing, Nothing] =
    ZStream.never

  /**
   * See [[ZStream.apply[A]*]]
   */
  def apply[A](as: A*): Stream[Nothing, A] = ZStream(as: _*)

  /**
   * See [[ZStream.apply[R,E,A]*]]
   */
  def apply[E, A](pull: Managed[Nothing, Pull[Any, E, A]]): Stream[E, A] = ZStream(pull)

  /**
   * See [[ZStream.bracket]]
   */
  def bracket[E, A](acquire: IO[E, A])(release: A => UIO[Any]): Stream[E, A] =
    ZStream.bracket(acquire)(release)

  /**
   * See [[ZStream.bracketExit]]
   */
  def bracketExit[E, A](acquire: IO[E, A])(release: (A, Exit[Any, Any]) => UIO[Any]): Stream[E, A] =
    ZStream.bracketExit(acquire)(release)

  /**
   *  @see [[ZStream.crossN[R,E,A,B,C]*]]
   */
  def crossN[E, A, B, C](stream1: Stream[E, A], stream2: Stream[E, B])(f: (A, B) => C): Stream[E, C] =
    ZStream.crossN(stream1, stream2)(f)

  /**
   *  @see [[ZStream.crossN[R,E,A,B,C,D]*]]
   */
  def crossN[E, A, B, C, D](stream1: Stream[E, A], stream2: Stream[E, B], stream3: Stream[E, C])(
    f: (A, B, C) => D
  ): Stream[E, D] =
    ZStream.crossN(stream1, stream2, stream3)(f)

  /**
   *  @see [[ZStream.crossN[R,E,A,B,C,D,F]*]]
   */
  def crossN[E, A, B, C, D, F](
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
  def die(ex: => Throwable): Stream[Nothing, Nothing] =
    ZStream.die(ex)

  /**
   * See [[ZStream.dieMessage]]
   */
  def dieMessage(msg: => String): Stream[Nothing, Nothing] =
    ZStream.dieMessage(msg)

  /**
   * See [[ZStream.effectAsync]]
   */
  def effectAsync[E, A](
    register: (IO[Option[E], A] => Unit) => Unit,
    outputBuffer: Int = 16
  ): Stream[E, A] =
    ZStream.effectAsync(register, outputBuffer)

  /**
   * See [[ZStream.effectAsyncMaybe]]
   */
  def effectAsyncMaybe[E, A](
    register: (IO[Option[E], A] => Unit) => Option[Stream[E, A]],
    outputBuffer: Int = 16
  ): Stream[E, A] =
    ZStream.effectAsyncMaybe(register, outputBuffer)

  /**
   * See [[ZStream.effectAsyncM]]
   */
  def effectAsyncM[E, A](
    register: (IO[Option[E], A] => Unit) => IO[E, Any],
    outputBuffer: Int = 16
  ): Stream[E, A] =
    ZStream.effectAsyncM(register, outputBuffer)

  /**
   * See [[ZStream.effectAsyncInterrupt]]
   */
  def effectAsyncInterrupt[E, A](
    register: (IO[Option[E], A] => Unit) => Either[Canceler[Any], Stream[E, A]],
    outputBuffer: Int = 16
  ): Stream[E, A] =
    ZStream.effectAsyncInterrupt(register, outputBuffer)

  /**
   * See [[ZStream.fail]]
   */
  def fail[E](error: => E): Stream[E, Nothing] =
    ZStream.fail(error)

  /**
   * See [[ZStream.finalizer]]
   */
  def finalizer(finalizer: UIO[Any]): Stream[Nothing, Nothing] =
    ZStream.finalizer(finalizer)

  /**
   * See [[ZStream.flatten]]
   */
  def flatten[E, A](fa: Stream[E, Stream[E, A]]): Stream[E, A] =
    ZStream.flatten(fa)

  /**
   * See [[ZStream.flattenPar]]
   */
  def flattenPar[E, A](n: Int, outputBuffer: Int = 16)(
    fa: Stream[E, Stream[E, A]]
  ): Stream[E, A] =
    ZStream.flattenPar(n, outputBuffer)(fa)

  /**
   * See [[ZStream.fromInputStream]]
   */
  def fromInputStream(
    is: => InputStream,
    chunkSize: Int = ZStreamChunk.DefaultChunkSize
  ): ZStreamChunk[Any, IOException, Byte] =
    ZStream.fromInputStream(is, chunkSize)

  /**
   * See [[ZStream.fromChunk]]
   */
  def fromChunk[A](c: => Chunk[A]): Stream[Nothing, A] =
    ZStream.fromChunk(c)

  /**
   * See [[ZStream.fromEffect]]
   */
  def fromEffect[E, A](fa: IO[E, A]): Stream[E, A] =
    ZStream.fromEffect(fa)

  /**
   * See [[ZStream.fromEffectOption]]
   */
  def fromEffectOption[E, A](fa: IO[Option[E], A]): Stream[E, A] =
    ZStream.fromEffectOption(fa)

  /**
   * See [[ZStream.paginate]]
   */
  def paginate[A, S](s: S)(f: S => (A, Option[S])): Stream[Nothing, A] =
    ZStream.paginate(s)(f)

  /**
   * See [[ZStream.paginateM]]
   */
  def paginateM[E, A, S](s: S)(f: S => IO[E, (A, Option[S])]): Stream[E, A] =
    ZStream.paginateM(s)(f)

  /**
   * See [[ZStream.repeatEffect]]
   */
  def repeatEffect[E, A](fa: IO[E, A]): Stream[E, A] =
    ZStream.repeatEffect(fa)

  /**
   * See [[ZStream.repeatEffectWith]]
   */
  def repeatEffectWith[E, A](
    fa: IO[E, A],
    schedule: Schedule[Any, Unit, Any]
  ): ZStream[Clock, E, A] = ZStream.repeatEffectWith(fa, schedule)

  /**
   * See [[ZStream.repeatEffectOption]]
   */
  def repeatEffectOption[E, A](fa: IO[Option[E], A]): Stream[E, A] =
    ZStream.repeatEffectOption(fa)

  /**
   * See [[ZStream.fromIterable]]
   */
  def fromIterable[A](as: => Iterable[A]): Stream[Nothing, A] =
    ZStream.fromIterable(as)

  /**
   * See [[ZStream.fromIterableM]]
   */
  def fromIterableM[E, A](iterable: IO[E, Iterable[A]]): Stream[E, A] =
    ZStream.fromIterableM(iterable)

  /**
   * See [[ZStream.fromIteratorTotal]]
   */
  def fromIteratorTotal[A](iterator: => Iterator[A]): Stream[Nothing, A] =
    ZStream.fromIteratorTotal(iterator)

  /**
   * See [[ZStream.fromIterator]]
   */
  def fromIterator[A](iterator: => Iterator[A]): Stream[Throwable, A] =
    ZStream.fromIterator(iterator)

  /**
   * See [[ZStream.fromIteratorEffect]]
   */
  def fromIteratorEffect[A](iterator: IO[Throwable, Iterator[A]]): Stream[Throwable, A] =
    ZStream.fromIteratorEffect(iterator)

  /**
   * See [[ZStream.fromIteratorManaged]]
   */
  def fromIteratorManaged[A](iterator: Managed[Throwable, Iterator[A]]): Stream[Throwable, A] =
    ZStream.fromIteratorManaged(iterator)

  /**
   * See [[ZStream.fromJavaIteratorTotal]]
   */
  def fromJavaIteratorTotal[A](iterator: => ju.Iterator[A]): Stream[Nothing, A] =
    ZStream.fromJavaIteratorTotal(iterator)

  /**
   * See [[ZStream.fromJavaIterator]]
   */
  def fromJavaIterator[A](iterator: => ju.Iterator[A]): Stream[Throwable, A] =
    ZStream.fromJavaIterator(iterator)

  /**
   * See [[ZStream.fromJavaIteratorEffect]]
   */
  def fromJavaIteratorEffect[A](iterator: IO[Throwable, ju.Iterator[A]]): Stream[Throwable, A] =
    ZStream.fromJavaIteratorEffect(iterator)

  /**
   * See [[ZStream.fromJavaIteratorManaged]]
   */
  def fromJavaIteratorManaged[A](iterator: Managed[Throwable, ju.Iterator[A]]): Stream[Throwable, A] =
    ZStream.fromJavaIteratorManaged(iterator)

  /**
   * See [[ZStream.fromQueue]]
   */
  def fromQueue[E, A](queue: ZQueue[Nothing, Any, Any, E, Nothing, A]): Stream[E, A] =
    ZStream.fromQueue(queue)

  /**
   * See [[ZStream.fromQueueWithShutdown]]
   */
  def fromQueueWithShutdown[E, A](queue: ZQueue[Nothing, Any, Any, E, Nothing, A]): Stream[E, A] =
    ZStream.fromQueueWithShutdown(queue)

  /**
   * See [[ZStream.fromSchedule]]
   */
  def fromSchedule[A](schedule: Schedule[Any, Any, A]): Stream[Nothing, A] =
    ZStream.fromSchedule(schedule)

  /**
   * See [[ZStream.fromTQueue]]
   */
  def fromTQueue[A](queue: TQueue[A]): Stream[Nothing, A] =
    ZStream.fromTQueue(queue)

  /**
   * See [[ZStream.halt]]
   */
  def halt[E](cause: => Cause[E]): Stream[E, Nothing] =
    ZStream.halt(cause)

  /**
   * See [[ZStream.iterate]]
   */
  def iterate[A](a: A)(f: A => A): ZStream[Any, Nothing, A] = Stream.unfold(a)(a => Some(a -> f(a)))

  /**
   * See [[ZStream.managed]]
   */
  def managed[E, A](managed: Managed[E, A]): Stream[E, A] =
    ZStream.managed(managed)

  /**
   * See [[ZStream.mergeAll]]
   */
  def mergeAll[E, A](n: Int, outputBuffer: Int = 16)(
    streams: Stream[E, A]*
  ): Stream[E, A] =
    ZStream.mergeAll[Any, E, A](n, outputBuffer)(streams: _*)

  /**
   * See [[ZStream.range]]
   */
  def range(min: Int, max: Int): Stream[Nothing, Int] =
    ZStream.range(min, max)

  /**
   * See [[ZStream.subscriptionRef]]
   */
  def subscriptionRef[A](a: A): UIO[SubscriptionRef[A]] =
    ZStream.subscriptionRef(a)

  /**
   * See [[ZStream.succeed]]
   */
  def succeed[A](a: => A): Stream[Nothing, A] =
    ZStream.succeed(a)

  /**
   * See [[ZStream.unfold]]
   */
  def unfold[S, A](s: S)(f0: S => Option[(A, S)]): Stream[Nothing, A] =
    ZStream.unfold(s)(f0)

  /**
   * See [[ZStream.unfoldM]]
   */
  def unfoldM[E, A, S](s: S)(f0: S => IO[E, Option[(A, S)]]): Stream[E, A] =
    ZStream.unfoldM(s)(f0)

  /**
   * See [[ZStream.unwrap]]
   */
  def unwrap[E, A](fa: IO[E, Stream[E, A]]): Stream[E, A] =
    ZStream.unwrap(fa)

  /**
   * See [[ZStream.unwrapManaged]]
   */
  def unwrapManaged[E, A](fa: Managed[E, ZStream[Any, E, A]]): Stream[E, A] =
    ZStream.unwrapManaged(fa)

  /**
   *  @see [[ZStream.zipN[R,E,A,B,C]*]]
   */
  def zipN[E, A, B, C](stream1: Stream[E, A], stream2: Stream[E, B])(f: (A, B) => C): Stream[E, C] =
    ZStream.zipN(stream1, stream2)(f)

  /**
   *  @see [[ZStream.zipN[R,E,A,B,C,D]*]]
   */
  def zipN[E, A, B, C, D](stream1: Stream[E, A], stream2: Stream[E, B], stream3: Stream[E, C])(
    f: (A, B, C) => D
  ): Stream[E, D] =
    ZStream.zipN(stream1, stream2, stream3)(f)

  /**
   *  @see [[ZStream.zipN[R,E,A,B,C,D,F]*]]
   */
  def zipN[E, A, B, C, D, F](
    stream1: Stream[E, A],
    stream2: Stream[E, B],
    stream3: Stream[E, C],
    stream4: Stream[E, D]
  )(
    f: (A, B, C, D) => F
  ): Stream[E, F] =
    ZStream.zipN(stream1, stream2, stream3, stream4)(f)

  private[zio] def succeedNow[A](a: A): Stream[Nothing, A] =
    ZStream.succeedNow(a)
}
