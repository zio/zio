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

import zio._

private[stream] final class StreamEffectChunk[-R, +E, +A](override val chunks: StreamEffect[R, E, Chunk[A]])
    extends ZStreamChunk[R, E, A](chunks) { self =>

  override def collectWhile[B](p: PartialFunction[A, B]): ZStreamChunk[R, E, B] =
    StreamEffectChunk {
      StreamEffect {
        self.chunks.processEffect.flatMap { thunk =>
          ZManaged.effectTotal {
            var done = false

            def pull(): Chunk[B] =
              if (done) StreamEffect.end
              else {
                val chunk     = thunk()
                val remaining = chunk.collectWhile(p)
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

  override def drop(n: Int): StreamEffectChunk[R, E, A] =
    StreamEffectChunk {
      StreamEffect {
        self.chunks.processEffect.flatMap { thunk =>
          ZManaged.effectTotal {
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

  override def dropWhile(pred: A => Boolean): StreamEffectChunk[R, E, A] =
    StreamEffectChunk {
      StreamEffect {
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

  override def filter(pred: A => Boolean): StreamEffectChunk[R, E, A] =
    StreamEffectChunk(chunks.map(_.filter(pred)))

  final override def foldWhileManaged[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): ZManaged[R, E, S] =
    chunks.foldWhileManaged(s)(cont)((s, as) => as.foldWhile(s)(cont)(f))

  override def fold[A1 >: A, S](s: S)(f: (S, A1) => S): ZIO[R, E, S] =
    foldWhileManaged(s)(_ => true)(f).use(UIO.succeedNow)

  override def map[B](f: A => B): StreamEffectChunk[R, E, B] =
    StreamEffectChunk(chunks.map(_.map(f)))

  override def mapConcatChunk[B](f: A => Chunk[B]): StreamEffectChunk[R, E, B] =
    StreamEffectChunk(chunks.map(_.flatMap(f)))

  private def processChunk: ZManaged[R, Nothing, () => A] =
    chunks.processEffect.flatMap { thunk =>
      Managed.effectTotal {
        var counter         = 0
        var chunk: Chunk[A] = Chunk.empty
        def pull(): A = {
          while (counter >= chunk.length) {
            chunk = thunk()
            counter = 0
          }
          val elem = chunk(counter)
          counter += 1
          elem
        }
        () => pull()
      }
    }

  override def flattenChunks: StreamEffect[R, E, A] =
    StreamEffect(processChunk)

  override def take(n: Int): StreamEffectChunk[R, E, A] =
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

  override def takeUntil(pred: A => Boolean): StreamEffectChunk[R, E, A] =
    StreamEffectChunk {
      StreamEffect {
        self.chunks.processEffect.flatMap { thunk =>
          Managed.effectTotal {
            var keepTaking = true

            () => {
              if (!keepTaking) StreamEffect.end
              else {
                val chunk = thunk()
                val taken = chunk.takeWhile(!pred(_))
                val last  = chunk.drop(taken.length).take(1)
                if (last.nonEmpty) {
                  keepTaking = false
                }
                taken ++ last
              }
            }
          }
        }
      }
    }

  override def takeWhile(pred: A => Boolean): StreamEffectChunk[R, E, A] =
    StreamEffectChunk {
      StreamEffect {
        self.chunks.processEffect.flatMap { thunk =>
          Managed.effectTotal {
            var done = false

            () => {
              if (done) StreamEffect.end
              else {
                val chunk = thunk()
                val taken = chunk.takeWhile(pred)
                if (taken.length < chunk.length) {
                  done = true
                }
                taken
              }
            }
          }
        }
      }
    }

  override def toInputStream(
    implicit ev0: E <:< Throwable,
    ev1: A <:< Byte
  ): ZManaged[R, E, java.io.InputStream] =
    for {
      pull <- processChunk
      javaStream = {
        new java.io.InputStream {
          override def read(): Int =
            try {
              pull().toInt
            } catch {
              case StreamEffect.End        => -1
              case StreamEffect.Failure(e) => throw e.asInstanceOf[E]
            }
        }
      }
    } yield javaStream
}

private[stream] object StreamEffectChunk {
  def apply[R, E, A](chunks: StreamEffect[R, E, Chunk[A]]): StreamEffectChunk[R, E, A] =
    new StreamEffectChunk(chunks)
}
