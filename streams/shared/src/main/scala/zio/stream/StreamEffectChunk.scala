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

private[stream] class StreamEffectChunk[+E, +A](override val chunks: StreamEffect[E, Chunk[A]])
    extends ZStreamChunk[Any, E, A](chunks) { self =>

  override def drop(n: Int): StreamEffectChunk[E, A] =
    StreamEffectChunk {
      StreamEffect {
        self.chunks.processEffect.flatMap { thunk =>
          Managed.effectTotal {
            var counter = n

            def pull(): Chunk[A] = {
              val chunk = thunk()
              if (counter <= 0) chunk
              else {
                val remaining = chunk.drop(counter)
                val dropped   = chunk.length - remaining.length
                counter -= dropped
                if (remaining.isEmpty) pull() else remaining
              }
            }

            () => pull()
          }
        }
      }
    }

  override def dropWhile(pred: A => Boolean): StreamEffectChunk[E, A] =
    StreamEffectChunk {
      StreamEffect[E, Chunk[A]] {
        self.chunks.processEffect.flatMap { thunk =>
          Managed.effectTotal {
            var keepDropping = true

            def pull(): Chunk[A] = {
              val chunk = thunk()
              if (!keepDropping) chunk
              else {
                val remaining = chunk.dropWhile(pred)
                val empty     = remaining.length <= 0

                if (empty) pull()
                else {
                  keepDropping = false
                  remaining
                }
              }
            }

            () => pull()
          }
        }
      }
    }

  override def filter(pred: A => Boolean): StreamEffectChunk[E, A] =
    StreamEffectChunk(chunks.map(_.filter(pred)))

  private[this] final def foldLazyPure[S](s: S)(cont: S => Boolean)(f: (S, A) => S): Managed[E, S] =
    chunks.foldLazyPure(s)(cont) { (s, as) =>
      as.foldLeftLazy(s)(cont)(f)
    }

  override def foldLeft[S](s: S)(f: (S, A) => S): IO[E, S] =
    foldLazyPure(s)(_ => true)(f).use(UIO.succeed)

  override def map[@specialized B](f: A => B): StreamEffectChunk[E, B] =
    StreamEffectChunk(chunks.map(_.map(f)))

  override def mapConcat[B](f: A => Chunk[B]): StreamEffectChunk[E, B] =
    StreamEffectChunk(chunks.map(_.flatMap(f)))

  override def take(n: Int): StreamEffectChunk[E, A] =
    StreamEffectChunk {
      StreamEffect {
        self.chunks.processEffect.flatMap { thunk =>
          Managed.effectTotal {
            var counter = n

            def pull(): Chunk[A] =
              if (counter <= 0) StreamEffect.end
              else {
                val chunk = thunk()
                val taken = chunk.take(counter)
                counter -= taken.length
                taken
              }

            () => pull()
          }
        }
      }
    }

  override def takeWhile(pred: A => Boolean): StreamEffectChunk[E, A] =
    StreamEffectChunk {
      StreamEffect {
        self.chunks.processEffect.flatMap { thunk =>
          Managed.effectTotal {
            var done = false

            def pull(): Chunk[A] =
              if (done) StreamEffect.end
              else {
                val chunk     = thunk()
                val remaining = chunk.takeWhile(pred)
                if (remaining.length < chunk.length) {
                  done = true
                }
                remaining
              }

            () => pull()
          }
        }
      }
    }
}

private[stream] object StreamEffectChunk extends Serializable {
  final def apply[E, A](chunks: StreamEffect[E, Chunk[A]]): StreamEffectChunk[E, A] =
    new StreamEffectChunk(chunks)
}
