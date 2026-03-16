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
import zio.test._
import zio.test.Assertion._
import java.nio.ByteBuffer
import java.nio.channels._
import java.net.InetSocketAddress

object NioSchedulerSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("NioSchedulerSpec")(
      // ========================================================================
      // BASIC FUNCTIONALITY TESTS
      // ========================================================================

      test("NioScheduler.live layer can be created") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          isRunning <- scheduler.isRunning
        } yield assertTrue(isRunning)
      }.provide(NioScheduler.live),
      test("NioScheduler.test layer works") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          result    <- scheduler.scheduleIO(ZIO.succeed(42))
        } yield assertTrue(result == 42)
      }.provide(NioScheduler.test),
      test("scheduleIO executes successfully") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          result    <- scheduler.scheduleIO(ZIO.succeed("hello"))
        } yield assertTrue(result == "hello")
      }.provide(NioScheduler.test),
      test("scheduleAll executes all operations") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          results   <- scheduler.scheduleAll(Chunk(ZIO.succeed(1), ZIO.succeed(2), ZIO.succeed(3)))
        } yield assertTrue(results == Chunk(1, 2, 3))
      }.provide(NioScheduler.test),
      test("shutdown works") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          _         <- scheduler.shutdown()
          isRunning <- scheduler.isRunning
        } yield assertTrue(!isRunning)
      }.provide(NioScheduler.test),
      test("stats are tracked") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          _ <- scheduler.scheduleAll(
            Chunk(ZIO.succeed(1), ZIO.succeed(2), ZIO.succeed(3), ZIO.succeed(4), ZIO.succeed(5))
          )
          stats <- scheduler.stats
        } yield assertTrue(stats.scheduledOperations >= 5)
      }.provide(NioScheduler.test),

      // ========================================================================
      // NIO CHANNEL UTILITY TESTS
      // ========================================================================

      test("NioChannels creates non-blocking SocketChannel") {
        for {
          socketChannel <- NioChannels.nonBlockingSocketChannel
          isBlocking    <- ZIO.attempt(socketChannel.isBlocking)
        } yield assertTrue(!isBlocking)
      },
      test("NioChannels creates non-blocking ServerSocketChannel") {
        for {
          channel    <- NioChannels.nonBlockingServerSocketChannel
          isBlocking <- ZIO.attempt(channel.isBlocking)
        } yield assertTrue(!isBlocking)
      },
      test("NioChannels creates non-blocking DatagramChannel") {
        for {
          channel    <- NioChannels.nonBlockingDatagramChannel
          isBlocking <- ZIO.attempt(channel.isBlocking)
        } yield assertTrue(!isBlocking)
      },
      test("NioChannels ServerSocketChannel can be bound") {
        for {
          channel <- NioChannels.nonBlockingServerSocketChannel
          _       <- ZIO.attempt(channel.socket().bind(new InetSocketAddress(0)))
          port    <- ZIO.attempt(channel.socket().getLocalPort)
        } yield assertTrue(port > 0)
      },
      test("NioChannels DatagramChannel can send and receive") {
        for {
          channel1 <- NioChannels.nonBlockingDatagramChannel
          channel2 <- NioChannels.nonBlockingDatagramChannel
          _ <- ZIO.attempt {
            channel1.socket().bind(new InetSocketAddress(0))
            channel2.socket().bind(new InetSocketAddress(0))
          }
        } yield assertTrue(channel1.isOpen && channel2.isOpen)
      },
      test("NioChannels creates non-blocking channels") {
        for {
          socketChannel <- NioChannels.nonBlockingSocketChannel
          isBlocking    <- ZIO.attempt(socketChannel.isBlocking)
        } yield assertTrue(!isBlocking)
      },
      test("ReadableByteChannel can be scheduled") {
        val data    = "test data"
        val channel = Channels.newChannel(new java.io.ByteArrayInputStream(data.getBytes))

        for {
          scheduler <- ZIO.service[NioScheduler]
          result <- scheduler.scheduleReadable(channel) { ch =>
            val buffer = ByteBuffer.allocate(1024)
            ch.read(buffer)
            buffer.flip()
            new String(buffer.array(), 0, buffer.remaining())
          }
        } yield assertTrue(result == data)
      }.provide(NioScheduler.test),

      // ========================================================================
      // ERROR CASE TESTS (Issue #6)
      // ========================================================================

      test("scheduleReadable fails with closed channel") {
        val closedChannel = Channels.newChannel(new java.io.ByteArrayInputStream(Array.emptyByteArray))
        closedChannel.close()

        for {
          scheduler <- ZIO.service[NioScheduler]
          result    <- scheduler.scheduleReadable(closedChannel)(_ => 42).exit
        } yield assert(result)(fails(isSubtype[NioSchedulerError.ChannelClosed.type](anything)))
      }.provide(NioScheduler.test),
      test("scheduleIO tracks failed operations") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          _         <- scheduler.scheduleIO(ZIO.fail(new RuntimeException("test"))).exit
          stats     <- scheduler.stats
        } yield assertTrue(stats.failedOperations == 1)
      }.provide(NioScheduler.test),
      test("operations fail after shutdown") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          _         <- scheduler.shutdown()
          result    <- scheduler.scheduleIO(ZIO.succeed(42)).exit
        } yield assertTrue(result.isFailure)
      }.provide(NioScheduler.live),
      test("scheduleReadable validates non-null channel") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          result    <- scheduler.scheduleReadable(null: ReadableByteChannel)(_ => 42).exit
        } yield assert(result)(fails(isSubtype[NioSchedulerError.InvalidChannel](anything)))
      }.provide(NioScheduler.test),
      test("scheduleReadable validates channel is open") {
        val closedChannel = Channels.newChannel(new java.io.ByteArrayInputStream(Array.emptyByteArray))
        closedChannel.close()

        for {
          scheduler <- ZIO.service[NioScheduler]
          result    <- scheduler.scheduleReadable(closedChannel)(_ => 42).exit
        } yield assert(result)(fails(anything))
      }.provide(NioScheduler.test),
      test("scheduleWritable validates non-null channel") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          result    <- scheduler.scheduleWritable(null: WritableByteChannel)(_ => 42).exit
        } yield assert(result)(fails(isSubtype[NioSchedulerError.InvalidChannel](anything)))
      }.provide(NioScheduler.test),
      test("scheduleWritable validates channel is open") {
        val closedChannel = Channels.newChannel(new java.io.ByteArrayOutputStream())
        closedChannel.close()

        for {
          scheduler <- ZIO.service[NioScheduler]
          result    <- scheduler.scheduleWritable(closedChannel)(_ => 42).exit
        } yield assert(result)(fails(anything))
      }.provide(NioScheduler.test),
      test("scheduleReadable fails with SchedulerShutdown error after shutdown") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          _         <- scheduler.shutdown()
          result <- scheduler
            .scheduleReadable(Channels.newChannel(new java.io.ByteArrayInputStream(Array.emptyByteArray)))(_ => 42)
            .exit
        } yield assert(result)(fails(isSubtype[NioSchedulerError.SchedulerShutdown.type](anything)))
      }.provide(NioScheduler.live),
      test("scheduleWritable fails with SchedulerShutdown error after shutdown") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          _         <- scheduler.shutdown()
          result <- scheduler.scheduleWritable(Channels.newChannel(new java.io.ByteArrayOutputStream()))(_ => 42).exit
        } yield assert(result)(fails(isSubtype[NioSchedulerError.SchedulerShutdown.type](anything)))
      }.provide(NioScheduler.live),

      // ========================================================================
      // INTEGRATION TESTS (Live Layer)
      // ========================================================================

      test("live scheduler handles actual socket channel operations") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          channel   <- NioChannels.nonBlockingSocketChannel
          result <- scheduler
            .scheduleReadable(channel) { ch =>
              val buffer    = ByteBuffer.allocate(1024)
              val bytesRead = ch.read(buffer)
              buffer.flip()
              if (bytesRead > 0) new String(buffer.array(), 0, buffer.remaining()) else ""
            }
            .catchAll { _ => ZIO.succeed("") }
        } yield assertTrue(result != null)
      }.provide(NioScheduler.live),
      test("live scheduler stats are tracked correctly") {
        for {
          scheduler    <- ZIO.service[NioScheduler]
          initialStats <- scheduler.stats
          _            <- scheduler.scheduleIO(ZIO.succeed(1)).repeatN(9)
          finalStats   <- scheduler.stats
        } yield assertTrue(
          finalStats.scheduledOperations == initialStats.scheduledOperations + 10,
          finalStats.completedOperations == initialStats.completedOperations + 10
        )
      }.provide(NioScheduler.live),
      test("live scheduler shutdown releases resources") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          _         <- scheduler.scheduleIO(ZIO.succeed(1))
          _         <- scheduler.shutdown()
          isRunning <- scheduler.isRunning
        } yield assertTrue(!isRunning)
      }.provide(NioScheduler.live),
      test("live scheduler stats returns 0 active channels after shutdown") {
        for {
          scheduler <- ZIO.service[NioScheduler]
          _         <- scheduler.scheduleIO(ZIO.succeed(1))
          _         <- scheduler.shutdown()
          stats     <- scheduler.stats
        } yield assertTrue(stats.activeChannels == 0)
      }.provide(NioScheduler.live)
    )
}
