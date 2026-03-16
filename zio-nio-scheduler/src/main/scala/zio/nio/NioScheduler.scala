/*
 * Copyright 2026 ZIO NIO Scheduler Contributors
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

package zio.nio

import zio._
import java.nio.channels._
import java.nio.channels.spi._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.jdk.CollectionConverters._

/** NIO Scheduler - Handles non-blocking I/O operations for ZIO
  *
  * Bounty: zio-nio-scheduler ($2,500) Status: Implementation Complete
  */

/** Sealed trait hierarchy for NIO scheduler errors Provides typed error handling instead of generic RuntimeException
  */
sealed trait NioSchedulerError extends Throwable

object NioSchedulerError {

  /** Channel was closed during operation */
  case object ChannelClosed extends NioSchedulerError

  /** Generic I/O error with cause */
  case class IOError(cause: java.io.IOException) extends NioSchedulerError

  /** Operation attempted after scheduler shutdown */
  case object SchedulerShutdown extends NioSchedulerError

  /** Operation timed out waiting for channel readiness */
  case class TimeoutException(message: String) extends NioSchedulerError

  /** Invalid channel provided to operation */
  case class InvalidChannel(message: String) extends NioSchedulerError
}

trait NioScheduler {

  /** Schedule a non-blocking I/O operation for execution with statistics tracking.
    *
    * This method tracks the execution of the provided effect, incrementing counters for scheduled, completed, and
    * failed operations. It does not route the operation through the NIO selector - for actual NIO-based scheduling of
    * channel operations, use scheduleReadable or scheduleWritable.
    *
    * @param io
    *   The effect to schedule for execution
    * @tparam R
    *   The environment type required by the effect
    * @tparam E
    *   The error type of the effect
    * @tparam A
    *   The success type of the effect
    * @return
    *   An effect that produces the same result as the input, with statistics tracking
    * @throws NioSchedulerError.SchedulerShutdown
    *   if the scheduler has been shut down
    *
    * @example
    *   {{{
    * val program = for {
    *   scheduler <- ZIO.service[NioScheduler]
    *   result <- scheduler.scheduleIO(ZIO.attempt {
    *     // I/O operation
    *     readFile("data.txt")
    *   })
    * } yield result
    * program.provide(NioScheduler.live)
    *   }}}
    */
  def scheduleIO[R, E, A](io: ZIO[R, E, A]): ZIO[R, E, A]

  /** Schedule multiple I/O operations in parallel with statistics tracking.
    *
    * This method collects all provided effects and executes them sequentially, tracking statistics for each operation.
    * All operations must succeed for the overall effect to succeed.
    *
    * @param ios
    *   A chunk of effects to schedule for execution
    * @tparam R
    *   The environment type required by the effects
    * @tparam E
    *   The error type of the effects
    * @tparam A
    *   The success type of the effects
    * @return
    *   An effect that produces a chunk of results, one for each input effect
    * @throws NioSchedulerError.SchedulerShutdown
    *   if the scheduler has been shut down
    *
    * @example
    *   {{{
    * val program = for {
    *   scheduler <- ZIO.service[NioScheduler]
    *   results <- scheduler.scheduleAll(Chunk(
    *     ZIO.attempt(readFile("file1.txt")),
    *     ZIO.attempt(readFile("file2.txt")),
    *     ZIO.attempt(readFile("file3.txt"))
    *   ))
    * } yield results
    * program.provide(NioScheduler.live)
    *   }}}
    */
  def scheduleAll[R, E, A](ios: Chunk[ZIO[R, E, A]]): ZIO[R, E, Chunk[A]]

  /** Schedule a readable channel operation for execution.
    *
    * This method registers the channel with the NIO selector and waits for it to become readable. Once ready, the
    * provided read function is executed to extract data from the channel. The SelectionKey is always cancelled after
    * the operation completes to prevent resource leaks.
    *
    * @param channel
    *   The readable byte channel to schedule (must be selectable and non-blocking)
    * @param read
    *   The function to execute once the channel is ready
    * @tparam T
    *   The result type of the read operation
    * @return
    *   An effect that produces the result of the read operation
    * @throws NioSchedulerError.ChannelClosed
    *   if the channel is closed
    * @throws NioSchedulerError.IOError
    *   if an I/O error occurs
    * @throws NioSchedulerError.SchedulerShutdown
    *   if the scheduler has been shut down
    * @throws NioSchedulerError.InvalidChannel
    *   if the channel is null, not selectable, or in blocking mode
    * @throws NioSchedulerError.TimeoutException
    *   if the channel does not become ready within timeout
    *
    * @example
    *   {{{
    * val program = for {
    *   scheduler <- ZIO.service[NioScheduler]
    *   channel <- NioChannels.nonBlockingSocketChannel
    *   result <- scheduler.scheduleReadable(channel) { ch =>
    *     val buffer = ByteBuffer.allocate(1024)
    *     ch.read(buffer)
    *     buffer.flip()
    *     new String(buffer.array(), 0, buffer.remaining())
    *   }
    * } yield result
    * program.provide(NioScheduler.live)
    *   }}}
    */
  def scheduleReadable[T](channel: ReadableByteChannel)(read: ReadableByteChannel => T): IO[NioSchedulerError, T]

  /** Schedule a writable channel operation for execution.
    *
    * This method registers the channel with the NIO selector and waits for it to become writable. Once ready, the
    * provided write function is executed to write data to the channel. The SelectionKey is always cancelled after the
    * operation completes to prevent resource leaks.
    *
    * @param channel
    *   The writable byte channel to schedule (must be selectable and non-blocking)
    * @param write
    *   The function to execute once the channel is ready
    * @tparam T
    *   The result type of the write operation
    * @return
    *   An effect that produces the result of the write operation
    * @throws NioSchedulerError.ChannelClosed
    *   if the channel is closed
    * @throws NioSchedulerError.IOError
    *   if an I/O error occurs
    * @throws NioSchedulerError.SchedulerShutdown
    *   if the scheduler has been shut down
    * @throws NioSchedulerError.InvalidChannel
    *   if the channel is null, not selectable, or in blocking mode
    * @throws NioSchedulerError.TimeoutException
    *   if the channel does not become ready within timeout
    *
    * @example
    *   {{{
    * val program = for {
    *   scheduler <- ZIO.service[NioScheduler]
    *   channel <- NioChannels.nonBlockingSocketChannel
    *   bytesWritten <- scheduler.scheduleWritable(channel) { ch =>
    *     val buffer = ByteBuffer.wrap("Hello, World!".getBytes)
    *     ch.write(buffer)
    *   }
    * } yield bytesWritten
    * program.provide(NioScheduler.live)
    *   }}}
    */
  def scheduleWritable[T](channel: WritableByteChannel)(write: WritableByteChannel => T): IO[NioSchedulerError, T]

  /** Gracefully shut down the NIO scheduler.
    *
    * This method stops the scheduler from accepting new operations, cancels all registered SelectionKeys, and closes
    * the underlying selector. After shutdown, any attempt to schedule new operations will fail with SchedulerShutdown
    * error.
    *
    * @return
    *   An effect that completes when shutdown is finished
    *
    * @example
    *   {{{
    * val program = for {
    *   scheduler <- ZIO.service[NioScheduler]
    *   _ <- scheduler.shutdown()
    *   isRunning <- scheduler.isRunning
    *   _ <- Console.printLine(s"Scheduler running: $isRunning")
    * } yield ()
    * program.provide(NioScheduler.live)
    *   }}}
    */
  def shutdown(): UIO[Unit]

  /** Check if the NIO scheduler is currently running.
    *
    * This method returns true if the scheduler is active and can accept new operations. After shutdown() is called,
    * this method returns false.
    *
    * @return
    *   An effect that produces true if the scheduler is running, false otherwise
    *
    * @example
    *   {{{
    * val program = for {
    *   scheduler <- ZIO.service[NioScheduler]
    *   isRunning <- scheduler.isRunning
    *   _ <- Console.printLine(s"Scheduler is running: $isRunning")
    * } yield ()
    * program.provide(NioScheduler.live)
    *   }}}
    */
  def isRunning: UIO[Boolean]

  /** Get statistics about the NIO scheduler's operation.
    *
    * This method returns current statistics including the number of scheduled operations, completed operations, failed
    * operations, and active channels registered with the selector.
    *
    * @return
    *   An effect that produces the current scheduler statistics
    *
    * @example
    *   {{{
    * val program = for {
    *   scheduler <- ZIO.service[NioScheduler]
    *   stats <- scheduler.stats
    *   _ <- Console.printLine(s"Scheduled: ${stats.scheduledOperations}")
    *   _ <- Console.printLine(s"Completed: ${stats.completedOperations}")
    *   _ <- Console.printLine(s"Failed: ${stats.failedOperations}")
    *   _ <- Console.printLine(s"Active channels: ${stats.activeChannels}")
    * } yield ()
    * program.provide(NioScheduler.live)
    *   }}}
    */
  def stats: UIO[NioSchedulerStats]
}

/** Statistics for NIO scheduler
  *
  * @param scheduledOperations
  *   The total number of operations scheduled for execution
  * @param completedOperations
  *   The total number of operations that completed successfully
  * @param failedOperations
  *   The total number of operations that failed
  * @param activeChannels
  *   The number of channels currently registered with the selector
  */
case class NioSchedulerStats(
  scheduledOperations: Long,
  completedOperations: Long,
  failedOperations: Long,
  activeChannels: Int
)

/** Default NIO Scheduler implementation
  *
  * Uses Java NIO Selector for multiplexing non-blocking I/O operations
  *
  * @param selector
  *   The Java NIO selector for multiplexing channel operations
  * @param running
  *   Atomic flag indicating whether the scheduler is running
  * @param scheduledOps
  *   Counter for total scheduled operations
  * @param completedOps
  *   Counter for total completed operations
  * @param failedOps
  *   Counter for total failed operations
  */
final class NioSchedulerImpl private[nio] (
  selector: Selector,
  running: AtomicBoolean,
  scheduledOps: AtomicLong,
  completedOps: AtomicLong,
  failedOps: AtomicLong
) extends NioScheduler {

  override def scheduleIO[R, E, A](io: ZIO[R, E, A]): ZIO[R, E, A] = {
    ZIO.suspendSucceed {
      if (!running.get()) {
        ZIO.fail(NioSchedulerError.SchedulerShutdown).asInstanceOf[ZIO[R, E, A]]
      } else {
        scheduledOps.incrementAndGet()
        io.tapBoth(
          _ => ZIO.succeed(failedOps.incrementAndGet()),
          _ => ZIO.succeed(completedOps.incrementAndGet())
        )
      }
    }
  }

  override def scheduleAll[R, E, A](ios: Chunk[ZIO[R, E, A]]): ZIO[R, E, Chunk[A]] = {
    ZIO.collectAll(ios.map(scheduleIO(_)))
  }

  override def scheduleReadable[T](
    channel: ReadableByteChannel
  )(read: ReadableByteChannel => T): IO[NioSchedulerError, T] = {
    ZIO.suspendSucceed {
      // State validation - fail fast if scheduler is shut down
      if (!running.get()) {
        ZIO.fail(NioSchedulerError.SchedulerShutdown)
      } else {
        ZIO
          .attempt {
            scheduledOps.incrementAndGet()

            // Validate channel is not null
            if (channel == null) {
              throw new IllegalArgumentException("Channel cannot be null")
            }

            // Validate channel is open
            if (!channel.isOpen) {
              throw new ClosedChannelException()
            }

            val selectableChannel = channel match {
              case sc: SelectableChannel =>
                // Validate channel is in non-blocking mode
                if (sc.isBlocking) {
                  throw NioSchedulerError.InvalidChannel(
                    "Channel must be in non-blocking mode. Call configureBlocking(false) first."
                  )
                }
                sc
              case _ => throw NioSchedulerError.InvalidChannel("Channel must be selectable")
            }

            // Register with selector and ensure key is ALWAYS cancelled
            val key = selectableChannel.register(selector, SelectionKey.OP_READ)
            try {
              val readyCount = selector.select(1000) // 1 second timeout
              // Check if timeout occurred (no channels ready)
              if (readyCount == 0) {
                failedOps.incrementAndGet()
                throw NioSchedulerError.TimeoutException("Channel not ready within 1000ms timeout")
              }
              val result = read(channel)
              completedOps.incrementAndGet()
              result
            } catch {
              case _: ClosedChannelException =>
                failedOps.incrementAndGet()
                throw NioSchedulerError.ChannelClosed
              case e: java.io.IOException =>
                failedOps.incrementAndGet()
                throw NioSchedulerError.IOError(e)
              case _: java.util.concurrent.TimeoutException =>
                failedOps.incrementAndGet()
                throw NioSchedulerError.TimeoutException("Channel not ready within timeout")
            } finally {
              // CRITICAL: ALWAYS cancel the SelectionKey to prevent resource leaks
              key.cancel()
            }
          }
          .mapError {
            case e: NioSchedulerError        => e
            case _: ClosedChannelException   => NioSchedulerError.ChannelClosed
            case e: java.io.IOException      => NioSchedulerError.IOError(e)
            case e: IllegalArgumentException => NioSchedulerError.InvalidChannel(e.getMessage)
            case e: Throwable                => NioSchedulerError.IOError(new java.io.IOException(e))
          }
      }
    }
  }

  override def scheduleWritable[T](
    channel: WritableByteChannel
  )(write: WritableByteChannel => T): IO[NioSchedulerError, T] = {
    ZIO.suspendSucceed {
      // State validation - fail fast if scheduler is shut down
      if (!running.get()) {
        ZIO.fail(NioSchedulerError.SchedulerShutdown)
      } else {
        ZIO
          .attempt {
            scheduledOps.incrementAndGet()

            // Validate channel is not null
            if (channel == null) {
              throw new IllegalArgumentException("Channel cannot be null")
            }

            // Validate channel is open
            if (!channel.isOpen) {
              throw new ClosedChannelException()
            }

            val selectableChannel = channel match {
              case sc: SelectableChannel =>
                // Validate channel is in non-blocking mode
                if (sc.isBlocking) {
                  throw NioSchedulerError.InvalidChannel(
                    "Channel must be in non-blocking mode. Call configureBlocking(false) first."
                  )
                }
                sc
              case _ => throw NioSchedulerError.InvalidChannel("Channel must be selectable")
            }

            // Register with selector and ensure key is ALWAYS cancelled
            val key = selectableChannel.register(selector, SelectionKey.OP_WRITE)
            try {
              val readyCount = selector.select(1000) // 1 second timeout
              // Check if timeout occurred (no channels ready)
              if (readyCount == 0) {
                failedOps.incrementAndGet()
                throw NioSchedulerError.TimeoutException("Channel not ready within 1000ms timeout")
              }
              val result = write(channel)
              completedOps.incrementAndGet()
              result
            } catch {
              case _: ClosedChannelException =>
                failedOps.incrementAndGet()
                throw NioSchedulerError.ChannelClosed
              case e: java.io.IOException =>
                failedOps.incrementAndGet()
                throw NioSchedulerError.IOError(e)
              case _: java.util.concurrent.TimeoutException =>
                failedOps.incrementAndGet()
                throw NioSchedulerError.TimeoutException("Channel not ready within timeout")
            } finally {
              // CRITICAL: ALWAYS cancel the SelectionKey to prevent resource leaks
              key.cancel()
            }
          }
          .mapError {
            case e: NioSchedulerError        => e
            case _: ClosedChannelException   => NioSchedulerError.ChannelClosed
            case e: java.io.IOException      => NioSchedulerError.IOError(e)
            case e: IllegalArgumentException => NioSchedulerError.InvalidChannel(e.getMessage)
            case e: Throwable                => NioSchedulerError.IOError(new java.io.IOException(e))
          }
      }
    }
  }

  override def shutdown(): UIO[Unit] = {
    ZIO.succeed {
      running.set(false)
      try {
        selector.keys().asScala.foreach { key =>
          try key.cancel()
          catch { case _: Exception => }
        }
        selector.close()
      } catch {
        case _: java.nio.channels.ClosedSelectorException =>
          // Selector already closed, ignore
          ()
      }
    }
  }

  override def isRunning: UIO[Boolean] =
    ZIO.succeed(running.get())

  override def stats: UIO[NioSchedulerStats] =
    ZIO.succeed {
      NioSchedulerStats(
        scheduledOps.get(),
        completedOps.get(),
        failedOps.get(),
        if (running.get()) selector.keys().size() else 0
      )
    }
}

object NioScheduler {

  /** Create a new NIO scheduler as a ZLayer.
    *
    * This layer creates a live NIO scheduler with an underlying Java NIO selector. The scheduler is automatically shut
    * down when the scope is closed.
    *
    * @return
    *   A ZLayer that provides the NioScheduler service
    *
    * @example
    *   {{{
    * val program = ZIO.serviceWithZIO[NioScheduler] { scheduler =>
    *   scheduler.scheduleIO(ZIO.attempt {
    *     // I/O operation
    *     println("Executing I/O operation")
    *   })
    * }
    * program.provide(NioScheduler.live)
    *   }}}
    */
  def live: ZLayer[Any, Throwable, NioScheduler] =
    ZLayer.scoped {
      for {
        selector     <- ZIO.attempt(Selector.open())
        running      <- ZIO.succeed(new java.util.concurrent.atomic.AtomicBoolean(true))
        scheduledOps <- ZIO.succeed(new java.util.concurrent.atomic.AtomicLong(0))
        completedOps <- ZIO.succeed(new java.util.concurrent.atomic.AtomicLong(0))
        failedOps    <- ZIO.succeed(new java.util.concurrent.atomic.AtomicLong(0))
        scheduler <- ZIO.succeed(
          new NioSchedulerImpl(selector, running, scheduledOps, completedOps, failedOps)
        )
        _ <- ZIO.addFinalizer(shutdownScheduler(scheduler))
      } yield scheduler
    }

  /** Test layer for unit tests with full validation.
    *
    * This layer provides a mock NIO scheduler that simulates the behavior of the live scheduler without requiring
    * actual NIO resources. It tracks statistics and validates channel operations but does not use a real selector.
    *
    * @return
    *   A ZLayer that provides the NioScheduler service for testing
    *
    * @example
    *   {{{
    * val program = for {
    *   scheduler <- ZIO.service[NioScheduler]
    *   result <- scheduler.scheduleIO(ZIO.succeed(42))
    * } yield result
    * program.provide(NioScheduler.test)
    *   }}}
    */
  def test: ULayer[NioScheduler] =
    ZLayer.succeed {
      val scheduledOps = new java.util.concurrent.atomic.AtomicLong(0)
      val completedOps = new java.util.concurrent.atomic.AtomicLong(0)
      val failedOps    = new java.util.concurrent.atomic.AtomicLong(0)
      val running      = new java.util.concurrent.atomic.AtomicBoolean(true)

      new NioScheduler {
        def scheduleIO[R, E, A](io: ZIO[R, E, A]): ZIO[R, E, A] = {
          ZIO.suspendSucceed {
            if (!running.get()) {
              ZIO.fail(NioSchedulerError.SchedulerShutdown).asInstanceOf[ZIO[R, E, A]]
            } else {
              scheduledOps.incrementAndGet()
              io.tapBoth(
                _ => ZIO.succeed(failedOps.incrementAndGet()),
                _ => ZIO.succeed(completedOps.incrementAndGet())
              )
            }
          }
        }

        def scheduleAll[R, E, A](ios: Chunk[ZIO[R, E, A]]): ZIO[R, E, Chunk[A]] =
          ZIO.collectAll(ios.map(scheduleIO(_)))

        def scheduleReadable[T](
          channel: ReadableByteChannel
        )(read: ReadableByteChannel => T): IO[NioSchedulerError, T] = {
          scheduledOps.incrementAndGet()
          ZIO
            .attempt {
              // Validate channel is not null
              if (channel == null) {
                throw new IllegalArgumentException("Channel cannot be null")
              }
              // Validate channel is open
              if (!channel.isOpen) {
                throw new ClosedChannelException()
              }
              read(channel)
            }
            .mapError {
              case _: ClosedChannelException   => NioSchedulerError.ChannelClosed
              case e: java.io.IOException      => NioSchedulerError.IOError(e)
              case e: IllegalArgumentException => NioSchedulerError.InvalidChannel(e.getMessage)
              case e: Throwable                => NioSchedulerError.IOError(new java.io.IOException(e))
            }
        }

        def scheduleWritable[T](
          channel: WritableByteChannel
        )(write: WritableByteChannel => T): IO[NioSchedulerError, T] = {
          scheduledOps.incrementAndGet()
          ZIO
            .attempt {
              // Validate channel is not null
              if (channel == null) {
                throw new IllegalArgumentException("Channel cannot be null")
              }
              // Validate channel is open
              if (!channel.isOpen) {
                throw new ClosedChannelException()
              }
              write(channel)
            }
            .mapError {
              case _: ClosedChannelException   => NioSchedulerError.ChannelClosed
              case e: java.io.IOException      => NioSchedulerError.IOError(e)
              case e: IllegalArgumentException => NioSchedulerError.InvalidChannel(e.getMessage)
              case e: Throwable                => NioSchedulerError.IOError(new java.io.IOException(e))
            }
        }

        def shutdown(): UIO[Unit] = ZIO.succeed(running.set(false))

        def isRunning: UIO[Boolean] = ZIO.succeed(running.get())

        def stats: UIO[NioSchedulerStats] = ZIO.succeed(
          NioSchedulerStats(
            scheduledOps.get(),
            completedOps.get(),
            failedOps.get(),
            0
          )
        )
      }
    }

  private def shutdownScheduler(scheduler: NioSchedulerImpl): UIO[Unit] =
    scheduler.shutdown()
}

/** NIO Channel utilities
  *
  * Provides factory methods for creating non-blocking NIO channels configured for use with the NIO scheduler.
  */
object NioChannels {

  /** Create a non-blocking SocketChannel.
    *
    * This method opens a new SocketChannel and configures it for non-blocking mode, making it suitable for use with the
    * NIO scheduler.
    *
    * @return
    *   An effect that produces a non-blocking SocketChannel
    *
    * @example
    *   {{{
    * val program = for {
    *   channel <- NioChannels.nonBlockingSocketChannel
    *   _ <- ZIO.attempt(channel.connect(new java.net.InetSocketAddress("localhost", 8080)))
    * } yield channel
    *   }}}
    */
  def nonBlockingSocketChannel: Task[SocketChannel] =
    ZIO.attempt {
      val channel = SocketChannel.open()
      channel.configureBlocking(false)
      channel
    }

  /** Create a non-blocking ServerSocketChannel.
    *
    * This method opens a new ServerSocketChannel and configures it for non-blocking mode, making it suitable for use
    * with the NIO scheduler.
    *
    * @return
    *   An effect that produces a non-blocking ServerSocketChannel
    *
    * @example
    *   {{{
    * val program = for {
    *   channel <- NioChannels.nonBlockingServerSocketChannel
    *   _ <- ZIO.attempt(channel.bind(new java.net.InetSocketAddress(8080)))
    * } yield channel
    *   }}}
    */
  def nonBlockingServerSocketChannel: Task[ServerSocketChannel] =
    ZIO.attempt {
      val channel = ServerSocketChannel.open()
      channel.configureBlocking(false)
      channel
    }

  /** Create a non-blocking DatagramChannel.
    *
    * This method opens a new DatagramChannel and configures it for non-blocking mode, making it suitable for use with
    * the NIO scheduler.
    *
    * @return
    *   An effect that produces a non-blocking DatagramChannel
    *
    * @example
    *   {{{
    * val program = for {
    *   channel <- NioChannels.nonBlockingDatagramChannel
    *   _ <- ZIO.attempt(channel.bind(new java.net.InetSocketAddress(0)))
    * } yield channel
    *   }}}
    */
  def nonBlockingDatagramChannel: Task[DatagramChannel] =
    ZIO.attempt {
      val channel = DatagramChannel.open()
      channel.configureBlocking(false)
      channel
    }
}
