/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

package zio

import zio.internal.NioExecutor

import java.nio.channels.{SelectableChannel, SelectionKey}

/**
 * Low-level NIO operators for fiber-based non-blocking I/O.
 * These operators require the NioExecutor to be active in the Runtime.
 */
object Nio {

  /**
   * Suspends the current fiber until the specified channel is ready for reading.
   */
  def readAsync(channel: SelectableChannel): ZIO[Any, Throwable, Unit] = 
    ZIO.asyncInterrupt { k =>
      ZIO.executor.flatMap {
        case nio: NioExecutor =>
          ZIO.succeed {
            val fiber = k.asInstanceOf[internal.FiberRunnable]
            nio.register(channel, SelectionKey.OP_READ, fiber)
            Left(ZIO.succeed(channel.close())) // Cancellation logic
          }
        case _ =>
          ZIO.fail(new UnsupportedOperationException("NioExecutor is not active in this Runtime"))
      }
    }

  /**
   * Suspends the current fiber until the specified channel is ready for writing.
   */
  def writeAsync(channel: SelectableChannel): ZIO[Any, Throwable, Unit] = 
    ZIO.asyncInterrupt { k =>
      ZIO.executor.flatMap {
        case nio: NioExecutor =>
          ZIO.succeed {
            val fiber = k.asInstanceOf[internal.FiberRunnable]
            nio.register(channel, SelectionKey.OP_WRITE, fiber)
            Left(ZIO.succeed(channel.close()))
          }
        case _ =>
          ZIO.fail(new UnsupportedOperationException("NioExecutor is not active in this Runtime"))
      }
    }
}
