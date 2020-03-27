package zio.stm

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.{ ReentrantLock, StampedLock }

import org.openjdk.jmh.annotations.{ Benchmark, Group, GroupThreads, _ }
import org.openjdk.jmh.infra.Blackhole

import zio.IOBenchmarks._
import zio._

@State(Scope.Group)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(2)
class TReentrantLockBenchmark {

  val stampedLock = new StampedLock()

  val reentrantLock = new ReentrantLock()

  val zioLock: ZIO[Any, Nothing, TReentrantLock] = TReentrantLock.make.commit

  @Benchmark
  @Group("ZioLockBasic")
  @GroupThreads(1)
  def zioLockReadGroup(): Unit = zioLockRead()

  @Benchmark
  @Group("ZioLockBasic")
  @GroupThreads(1)
  def zioLockWriteGroup(): Unit = zioLockWrite()

  @Benchmark
  @Group("ZioLockLowContention")
  @GroupThreads(20)
  def zioLockReadGroup1(): Unit = zioLockRead()

  @Benchmark
  @Group("ZioLockLowContention")
  @GroupThreads(5)
  def zioLockWriteGroup1(): Unit = zioLockWrite()

  @Benchmark
  @Group("ZioLockMediumContention")
  @GroupThreads(20)
  def zioLockReadGroup2(): Unit = zioLockRead()

  @Benchmark
  @Group("ZioLockMediumContention")
  @GroupThreads(10)
  def zioLockWriteGroup2(): Unit = zioLockWrite()

  @Benchmark
  @Group("ZioLockHighContention")
  @GroupThreads(20)
  def zioLockReadGroup3(): Unit = zioLockRead()

  @Benchmark
  @Group("ZioLockHighContention")
  @GroupThreads(20)
  def zioLockWriteGroup3(): Unit = zioLockWrite()

  @Benchmark
  @Group("StampedLockBasic")
  @GroupThreads(1)
  def javaLockReadGroup(): Unit = stampedLockRead()

  @Benchmark
  @Group("StampedLockBasic")
  @GroupThreads(1)
  def javaLockWriteGroup(): Unit = stampedLockWrite()

  @Benchmark
  @Group("StampedLockLowContention")
  @GroupThreads(20)
  def javaLockReadGroup1(): Unit = stampedLockRead()

  @Benchmark
  @Group("StampedLockLowContention")
  @GroupThreads(5)
  def javaLockWriteGroup1(): Unit = stampedLockWrite()

  @Benchmark
  @Group("StampedLockMediumContention")
  @GroupThreads(20)
  def javaLockReadGroup2(): Unit = stampedLockRead()

  @Benchmark
  @Group("StampedLockMediumContention")
  @GroupThreads(10)
  def javaLockWriteGroup2(): Unit = stampedLockWrite()

  @Benchmark
  @Group("StampedLockHighContention")
  @GroupThreads(20)
  def javaLockReadGroup3(): Unit = stampedLockRead()

  @Benchmark
  @Group("StampedLockHighContention")
  @GroupThreads(20)
  def javaLockWriteGroup3(): Unit = stampedLockWrite()

  @Benchmark
  @Group("ReentrantLockBasic")
  @GroupThreads(1)
  def reentrantLockReadGroup(): Unit = reentrantLockRead()

  @Benchmark
  @Group("ReentrantLockBasic")
  @GroupThreads(1)
  def reentrantLockWriteGroup(): Unit = reentrantLockWrite()

  @Benchmark
  @Group("ReentrantLockLowContention")
  @GroupThreads(20)
  def reentrantLockReadGroup1(): Unit = reentrantLockRead()

  @Benchmark
  @Group("ReentrantLockLowContention")
  @GroupThreads(5)
  def reentrantLockWriteGroup1(): Unit = reentrantLockWrite()

  @Benchmark
  @Group("ReentrantLockMediumContention")
  @GroupThreads(20)
  def reentrantLockReadGroup2(): Unit = reentrantLockRead()

  @Benchmark
  @Group("ReentrantLockMediumContention")
  @GroupThreads(10)
  def reentrantLockWriteGroup2(): Unit = reentrantLockWrite()

  @Benchmark
  @Group("ReentrantLockHighContention")
  @GroupThreads(20)
  def reentrantLockReadGroup3(): Unit = reentrantLockRead()

  @Benchmark
  @Group("ReentrantLockHighContention")
  @GroupThreads(20)
  def reentrantLockWriteGroup3(): Unit = reentrantLockWrite()

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def zioLockRead(): Unit = {
    val io = for {
      lock <- zioLock
      _    <- lock.readLock.use(_ => doWorkM())
    } yield ()

    unsafeRun(io)
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def zioLockWrite(): Unit = {
    val io = for {
      lock <- zioLock
      _    <- lock.writeLock.use(_ => doWorkM())
    } yield ()

    unsafeRun(io)
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def stampedLockRead(): Unit = {
    val stamp = stampedLock.tryOptimisticRead

    if (stampedLock.validate(stamp)) {
      doWork()
    } else {
      val stamp = stampedLock.readLock()
      doWork()
      stampedLock.unlockRead(stamp)
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def reentrantLockWrite(): Unit = {
    reentrantLock.lock()
    doWork()
    reentrantLock.unlock()
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def reentrantLockRead(): Unit = {
    reentrantLock.lock()
    doWork()
    reentrantLock.unlock()
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def stampedLockWrite(): Unit = {
    val stamp = stampedLock.writeLock()
    doWork()
    stampedLock.unlockWrite(stamp)
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doWorkM(): UIO[Unit] = ZIO.effectTotal(doWork())

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doWork(): Unit = Blackhole.consumeCPU(100L)

}
