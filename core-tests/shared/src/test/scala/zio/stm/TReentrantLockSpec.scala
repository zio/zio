package zio.stm

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

object TReentrantLockSpec extends ZIOBaseSpec {
  def pollSchedule[E, A]: Schedule[Any, Option[Exit[E, A]], Option[Exit[E, A]]] =
    (Schedule.recurs(100) *>
      Schedule.identity[Option[Exit[E, A]]]).whileOutput(_.isEmpty)

  override def spec = suite("StmReentrantLock")(
    test("1 read lock") {
      for {
        lock  <- TReentrantLock.make.commit
        count <- ZIO.scoped(lock.readLock.flatMap(count => ZIO.succeed(count)))
      } yield assert(count)(equalTo(1))
    },
    test("2 read locks from same fiber") {
      for {
        lock  <- TReentrantLock.make.commit
        count <- ZIO.scoped(lock.readLock.flatMap(_ => ZIO.scoped(lock.readLock.flatMap(count => ZIO.succeed(count)))))
      } yield assert(count)(equalTo(2))
    },
    test("2 read locks from different fibers") {
      for {
        lock    <- TReentrantLock.make.commit
        rlatch  <- Promise.make[Nothing, Unit]
        mlatch  <- Promise.make[Nothing, Unit]
        wlatch  <- Promise.make[Nothing, Unit]
        _       <- ZIO.scoped(lock.readLock.flatMap(count => mlatch.succeed(()) *> rlatch.await as count)).fork
        _       <- mlatch.await
        reader2 <- ZIO.scoped(lock.readLock.flatMap(count => wlatch.succeed(()) as count)).fork
        _       <- wlatch.await
        count   <- reader2.join
      } yield assert(count)(equalTo(1))
    } @@ timeout(10.seconds),
    test("1 write lock then 1 read lock, different fibers") {
      for {
        lock   <- TReentrantLock.make.commit
        rlatch <- Promise.make[Nothing, Unit]
        wlatch <- Promise.make[Nothing, Unit]
        mlatch <- Promise.make[Nothing, Unit]
        _      <- ZIO.scoped(lock.writeLock.flatMap(count => rlatch.succeed(()) *> wlatch.await as count)).fork
        _      <- rlatch.await
        reader <- (mlatch.succeed(()) *> ZIO.scoped(lock.readLock)).fork
        _      <- mlatch.await
        locks  <- (lock.readLocks zipWith lock.writeLocks)(_ + _).commit
        option <- reader.poll.repeat(pollSchedule)
        _      <- wlatch.succeed(())
        rcount <- reader.join
      } yield assert(locks)(equalTo(1)) &&
        assert(option)(isNone) &&
        assert(1)(equalTo(rcount))
    } @@ timeout(10.seconds) @@ flaky,
    test("1 write lock then 1 write lock, different fibers") {
      for {
        lock   <- TReentrantLock.make.commit
        rlatch <- Promise.make[Nothing, Unit]
        wlatch <- Promise.make[Nothing, Unit]
        mlatch <- Promise.make[Nothing, Unit]
        _      <- ZIO.scoped(lock.writeLock.flatMap(count => rlatch.succeed(()) *> wlatch.await as count)).fork
        _      <- rlatch.await
        reader <- (mlatch.succeed(()) *> ZIO.scoped(lock.writeLock)).fork
        _      <- mlatch.await
        locks  <- (lock.readLocks zipWith lock.writeLocks)(_ + _).commit
        option <- reader.poll.repeat(pollSchedule)
        _      <- wlatch.succeed(())
        rcount <- reader.join
      } yield assert(locks)(equalTo(1)) &&
        assert(option)(isNone) &&
        assert(1)(equalTo(rcount))
    } @@ timeout(10.seconds) @@ flaky,
    test("write lock followed by read lock from same fiber") {
      for {
        lock <- TReentrantLock.make.commit
        ref  <- Ref.make(0)
        rcount <- ZIO.scoped(
                    lock.writeLock
                      .flatMap(_ =>
                        ZIO.scoped(lock.readLock.flatMap(count => lock.writeLocks.commit.flatMap(ref.set(_)) as count))
                      )
                  )
        wcount <- ref.get
      } yield assert(rcount)(equalTo(1)) && assert(wcount)(equalTo(1))
    },
    test("upgrade read lock to write lock from same fiber") {
      for {
        lock <- TReentrantLock.make.commit
        ref  <- Ref.make(0)
        rcount <- ZIO.scoped(
                    lock.readLock
                      .flatMap(_ =>
                        ZIO.scoped(lock.writeLock.flatMap(count => lock.writeLocks.commit.flatMap(ref.set(_)) as count))
                      )
                  )
        wcount <- ref.get
      } yield assert(rcount)(equalTo(1)) && assert(wcount)(equalTo(1))
    },
    test("read to writer upgrade with other readers") {
      for {
        lock   <- TReentrantLock.make.commit
        rlatch <- Promise.make[Nothing, Unit]
        mlatch <- Promise.make[Nothing, Unit]
        wlatch <- Promise.make[Nothing, Unit]
        _      <- ZIO.scoped(lock.readLock.flatMap(count => mlatch.succeed(()) *> rlatch.await as count)).fork
        _      <- mlatch.await
        writer <- ZIO
                    .scoped(
                      lock.readLock.flatMap(_ =>
                        wlatch.succeed(()) *> ZIO.scoped(lock.writeLock.flatMap(count => ZIO.succeed(count)))
                      )
                    )
                    .fork
        _      <- wlatch.await
        option <- writer.poll.repeat(pollSchedule)
        _      <- rlatch.succeed(())
        count  <- writer.join
      } yield assert(option)(isNone) && assert(count)(equalTo(1))
    } @@ timeout(10.seconds) @@ flaky
  ) @@ TestAspect.exceptNative
}
