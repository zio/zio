package zio.stm

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.StampedLock

import org.openjdk.jmh.annotations.{ Benchmark, Group, GroupThreads, _ }
import org.openjdk.jmh.infra.Blackhole
import zio.IOBenchmarks._
import zio._

@State(Scope.Group)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(2)
class TReentrantLockBenchmark {

  val javaLock = new StampedLock()

  val zioLock: UIO[TReentrantLock] = TReentrantLock.make.commit

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
  @Group("JavaLockBasic")
  @GroupThreads(1)
  def javaLockReadGroup(): Unit = javaLockRead()

  @Benchmark
  @Group("JavaLockBasic")
  @GroupThreads(1)
  def javaLockWriteGroup(): Unit = javaLockWrite()

  @Benchmark
  @Group("JavaLockLowContention")
  @GroupThreads(20)
  def javaLockReadGroup1(): Unit = javaLockRead()

  @Benchmark
  @Group("JavaLockLowContention")
  @GroupThreads(5)
  def javaLockWriteGroup1(): Unit = javaLockWrite()

  @Benchmark
  @Group("JavaLockMediumContention")
  @GroupThreads(20)
  def javaLockReadGroup2(): Unit = javaLockRead()

  @Benchmark
  @Group("JavaLockMediumContention")
  @GroupThreads(10)
  def javaLockWriteGroup2(): Unit = javaLockWrite()

  @Benchmark
  @Group("JavaLockHighContention")
  @GroupThreads(20)
  def javaLockReadGroup3(): Unit = javaLockRead()

  @Benchmark
  @Group("JavaLockHighContention")
  @GroupThreads(20)
  def javaLockWriteGroup3(): Unit = javaLockWrite()

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
  def javaLockRead(): Unit = {
    val stamp = javaLock.tryOptimisticRead

    if (javaLock.validate(stamp)) {
      doWork()
    } else {
      val stamp = javaLock.readLock()
      doWork()
      javaLock.unlockRead(stamp)
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def javaLockWrite(): Unit = {
    val stamp = javaLock.writeLock()
    doWork()
    javaLock.unlockWrite(stamp)
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doWorkM(): UIO[Unit] = ZIO.effectTotal(doWork())

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doWork(): Unit = Blackhole.consumeCPU(100L)

}
