package zio.stm

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.{ReentrantLock, StampedLock}

import org.openjdk.jmh.annotations.{Benchmark, Group, GroupThreads, _}
import zio.IOBenchmarks._
import zio.{UIO, ZIO, clock}
import zio.duration._

@State(Scope.Group)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 1, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 1, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(2)
class TReentrantLockBenchmark {

  val stampedLock = new StampedLock()

  val reentrantLock = new ReentrantLock()

  var zioLock: TReentrantLock = unsafeRun(TReentrantLock.make.commit)

/*
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
*/
  @Benchmark
  @Group("ZioLockHighContention")
  @GroupThreads(20)
  def zioLockReadGroup3(): Unit = zioLockRead()

  @Benchmark
  @Group("ZioLockHighContention")
  @GroupThreads(20)
  def zioLockWriteGroup3(): Unit = zioLockWrite()
/*
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
*/
  @Benchmark
  @Group("ReentrantLockHighContention")
  @GroupThreads(20)
  def reentrantLockReadGroup3(): Unit = reentrantLockRead()

  @Benchmark
  @Group("ReentrantLockHighContention")
  @GroupThreads(20)
  def reentrantLockWriteGroup3(): Unit = reentrantLockWrite()


  // used to amortize the relative cost of unsafeRun
  // compared to benchmarked operations
  private val calls = 100
  private val readSleep = 1L
  private val writeSleep = 1L

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def repeat(n: Int)(effect: UIO[Any]): UIO[Any] =
    if (n <= 0) effect else effect.flatMap(_ => effect)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def zioLockRead(): Unit = {
    unsafeRun(repeat(calls)((zioLock.acquireRead.commit *> doReadM() *> zioLock.releaseRead.commit)))
    ()
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def zioLockWrite(): Unit = {
    unsafeRun(repeat(calls)((zioLock.acquireWrite.commit *> doWriteM() *> zioLock.releaseWrite.commit)))
    ()
  }

  /*@CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def stampedLockRead(): Unit = {
    val stamp = stampedLock.tryOptimisticRead

    if (stampedLock.validate(stamp)) {
    } else {
      val stamp = stampedLock.readLock()
      stampedLock.unlockRead(stamp)
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def stampedLockWrite(): Unit = {
    val stamp = stampedLock.writeLock()
    stampedLock.unlockWrite(stamp)
  }*/

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def reentrantLockWrite(): Unit =
    for (_ <- 1 to calls) {
      reentrantLock.lock()
      doWrite()
      reentrantLock.unlock()
    }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def reentrantLockRead(): Unit =
    for (_ <- 1 to calls) {
      reentrantLock.lock()
      doRead()
      reentrantLock.unlock()
    }
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doReadM(): UIO[Unit] = UIO(clock.sleep(readSleep.millis)) *> ZIO.yieldNow

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doWriteM(): UIO[Unit] = UIO(clock.sleep(writeSleep.millis)) *> ZIO.yieldNow

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doRead(): Unit = Thread.sleep(readSleep)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doWrite(): Unit = Thread.sleep(writeSleep)

}
