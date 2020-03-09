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

import zio.Chunk

package object internal {

  /**
   * Creates a single-value sink from a value.
   */
  def ZSinkSucceedNow[A, B](b: B): ZSink[Any, Nothing, A, A, B] =
    ZSink.succeedNow(b)

  /**
   * Creates a `ZStreamChunk` from an eagerly evaluated chunk
   */
  def ZStreamChunkSucceedNow[A](as: Chunk[A]): ZStreamChunk[Any, Nothing, A] =
    new StreamEffectChunk(StreamEffect.succeed(as))

  /**
   * Creates a single-valued pure stream
   */
  def ZStreamSucceedNow[A](a: A): ZStream[Any, Nothing, A] =
    StreamEffect.succeed(a)
}
