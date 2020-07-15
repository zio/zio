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

package zio.stm

import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect.{ flaky, timeout }
import zio.test._
import zio.{ Exit, Promise, Ref, Schedule, ZIO }

object TReentrantLockSpec extends DefaultRunnableSpec {
  def pollSchedule[E, A] =
    (Schedule.recurs(100) *>
      Schedule.identity[Option[Exit[E, A]]]).whileOutput(_.isEmpty)

  override def spec = suite("StmReentrantLock")(
    testM("1 read lock") {
      for {
        lock  <- TReentrantLock.make.commit
        count <- lock.readLock.use(count => ZIO.succeed(count))
      } yield assert(count)(equalTo(1))
    },
    testM("2 read locks from same fiber") {
      for {
        lock  <- TReentrantLock.make.commit
        count <- lock.readLock.use(_ => lock.readLock.use(count => ZIO.succeed(count)))
      } yield assert(count)(equalTo(2))
    },
    testM("2 read locks from different fibers") {
      for {
        lock    <- TReentrantLock.make.commit
        rlatch  <- Promise.make[Nothing, Unit]
        mlatch  <- Promise.make[Nothing, Unit]
        wlatch  <- Promise.make[Nothing, Unit]
        _       <- lock.readLock.use(count => mlatch.succeed(()) *> rlatch.await as count).fork
        _       <- mlatch.await
        reader2 <- lock.readLock.use(count => wlatch.succeed(()) as count).fork
        _       <- wlatch.await
        count   <- reader2.join
      } yield assert(count)(equalTo(1))
    } @@ timeout(10.seconds),
    testM("1 write lock then 1 read lock, different fibers") {
      for {
        lock   <- TReentrantLock.make.commit
        rlatch <- Promise.make[Nothing, Unit]
        wlatch <- Promise.make[Nothing, Unit]
        mlatch <- Promise.make[Nothing, Unit]
        _      <- lock.writeLock.use(count => rlatch.succeed(()) *> wlatch.await as count).fork
        _      <- rlatch.await
        reader <- (mlatch.succeed(()) *> lock.readLock.use(ZIO.succeedNow(_))).fork
        _      <- mlatch.await
        locks  <- (lock.readLocks zipWith lock.writeLocks)(_ + _).commit
        option <- reader.poll.repeat(pollSchedule)
        _      <- wlatch.succeed(())
        rcount <- reader.join
      } yield assert(locks)(equalTo(1)) &&
        assert(option)(isNone) &&
        assert(1)(equalTo(rcount))
    } @@ timeout(10.seconds) @@ flaky,
    testM("1 write lock then 1 write lock, different fibers") {
      for {
        lock   <- TReentrantLock.make.commit
        rlatch <- Promise.make[Nothing, Unit]
        wlatch <- Promise.make[Nothing, Unit]
        mlatch <- Promise.make[Nothing, Unit]
        _      <- lock.writeLock.use(count => rlatch.succeed(()) *> wlatch.await as count).fork
        _      <- rlatch.await
        reader <- (mlatch.succeed(()) *> lock.writeLock.use(ZIO.succeed(_))).fork
        _      <- mlatch.await
        locks  <- (lock.readLocks zipWith lock.writeLocks)(_ + _).commit
        option <- reader.poll.repeat(pollSchedule)
        _      <- wlatch.succeed(())
        rcount <- reader.join
      } yield assert(locks)(equalTo(1)) &&
        assert(option)(isNone) &&
        assert(1)(equalTo(rcount))
    } @@ timeout(10.seconds) @@ flaky,
    testM("write lock followed by read lock from same fiber") {
      for {
        lock <- TReentrantLock.make.commit
        ref  <- Ref.make(0)
        rcount <- lock.writeLock
                   .use(_ => lock.readLock.use(count => lock.writeLocks.commit.flatMap(ref.set(_)) as count))
        wcount <- ref.get
      } yield assert(rcount)(equalTo(1)) && assert(wcount)(equalTo(1))
    },
    testM("upgrade read lock to write lock from same fiber") {
      for {
        lock <- TReentrantLock.make.commit
        ref  <- Ref.make(0)
        rcount <- lock.readLock
                   .use(_ => lock.writeLock.use(count => lock.writeLocks.commit.flatMap(ref.set(_)) as count))
        wcount <- ref.get
      } yield assert(rcount)(equalTo(1)) && assert(wcount)(equalTo(1))
    },
    testM("read to writer upgrade with other readers") {
      for {
        lock   <- TReentrantLock.make.commit
        rlatch <- Promise.make[Nothing, Unit]
        mlatch <- Promise.make[Nothing, Unit]
        wlatch <- Promise.make[Nothing, Unit]
        _      <- lock.readLock.use(count => mlatch.succeed(()) *> rlatch.await as count).fork
        _      <- mlatch.await
        writer <- lock.readLock.use(_ => wlatch.succeed(()) *> lock.writeLock.use(count => ZIO.succeed(count))).fork
        _      <- wlatch.await
        option <- writer.poll.repeat(pollSchedule)
        _      <- rlatch.succeed(())
        count  <- writer.join
      } yield assert(option)(isNone) && assert(count)(equalTo(1))
    } @@ timeout(10.seconds) @@ flaky
  )
}
