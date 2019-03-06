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

private[stream] trait StreamChunkPure[-R, @specialized +A] extends StreamChunk[R, Nothing, A] { self =>
  val chunks: StreamRPure[R, Chunk[A]]

  def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
    chunks.foldPureLazy(s)(cont) { (s, as) =>
      as.foldLeftLazy(s)(cont)(f)
    }

  override def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): UIO[S] =
    IO.succeed(foldPureLazy(s)(_ => true)(f))

  override def map[@specialized B](f: A => B): StreamChunkPure[R, B] =
    StreamChunkPure(chunks.map(_.map(f)))

  override def filter(pred: A => Boolean): StreamChunkPure[R, A] =
    StreamChunkPure(chunks.map(_.filter(pred)))

  override def mapConcat[B](f: A => Chunk[B]): StreamChunkPure[R, B] =
    StreamChunkPure(chunks.map(_.flatMap(f)))
}

object StreamChunkPure extends Serializable {
  final def apply[R, A](chunkStream: StreamRPure[R, Chunk[A]]): StreamChunkPure[R, A] =
    new StreamChunkPure[R, A] {
      val chunks = chunkStream
    }
}
