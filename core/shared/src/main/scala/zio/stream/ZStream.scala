/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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
import zio.internal.MutableConcurrentQueue
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.{Queue => ScalaQueue}

private[zio] trait ZStreamPlatformSpecificConstructors {
  self: ZStream.type =>
}

private[zio] trait ZStreamPlatformSpecificOperators {
  self: ZStream.type =>
}

trait ZStream[+R, +E, +A] { self =>
  import ZStream._

  /**
   * Provides a way to buffer elements from the stream.
   *
   * @param capacity
   *   the maximum number of elements to buffer
   * @return
   *   a new stream with buffering applied
   */
  final def buffer(capacity: Int): ZStream[R, E, A] =
    if (capacity < 1) {
      ZStream.dieMessage(s"buffer: capacity must be at least 1, but was $capacity")
    } else if (capacity == 1) {
      // Special case for buffer(1): we want at most one element in flight.
      // We use a SynchronousQueue-like behavior with a single Promise.
      ZStream.unwrap {
        for {
          promise <- Promise.make[Option[A]]
          stream  <- ZStream.fromZIO {
                       (for {
                         stream <- self.process { (chunk, downstream) =>
                                    val iterator = chunk.iterator
                                    def loop: ZIO[R, Option[E], Unit] =
                                      if (iterator.hasNext) {
                                        val a = iterator.next()
                                        downstream.emit(a) *> ZIO
                                          .when(!iterator.hasNext)(downstream.awaitCancellation)
                                          .unlessZIO(promise.isDone)
                                          .zipRight(promise.await)
                                          .flatMap {
                                            case Some(next) =>
                                              downstream.emit(next) *> loop
                                            case None       => ZIO.unit
                                          }
                                      } else {
                                        ZIO.unit
                                      }
                                    loop
                                  }.catchAllCause { cause =>
                                    promise.done(Exit.failCause(cause)) *> ZIO.unit
                                  } <* promise.done(Exit.succeed(None))
                       } yield stream).forkDaemon
                     }.map { fiber =>
                       ZStream.fromQueue(promise).flattenOption ++ ZStream.fromZIO(fiber.join).drain
                     }
        } yield stream
      }
    } else {
      ZStream.effectSuspendTotal {
        val queue = MutableConcurrentQueue.bounded[Either[E, A]](capacity - 1)

        ZStream.unwrap {
          self.process { (chunk, downstream) =>
            val offer = ZIO.foreachDiscard(chunk) { a =>
              queue.offer(Right(a)).unlessZIO(queue.isFull)
            }

            val take = ZIO.uninterruptible {
              ZIO
                .fromEither(queue.poll)
                .foldZIO(
                  _ => downstream.awaitCancellation,
                  item => downstream.emit(item)
                )
            }.forever

            offer.zipPar(take).catchAllCause { cause =>
              queue.offer(Left(cause.squashWith(_.toThrowable))).orDie *> ZIO.unit
            }
          }.map { downstream =>
            ZStream.fromZIO(queue.poll).collect { case Right(a) => a }.flatten ++ ZStream.fromZIO(downstream).drain
          }
        }
      }
    }