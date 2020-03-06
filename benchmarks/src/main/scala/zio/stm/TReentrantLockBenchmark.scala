package zio.stm

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.StampedLock

import scala.util.Random

import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._
import zio.{ Managed, _ }
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 5)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 5)
@Fork(1)
@Threads(1)
class TReentrantLockBenchmark {

  @Param(Array("50", "100", "500"))
  var numReaders: Int = _

  @Param(Array("1", "10", "25", "50", "100"))
  var numWriters: Int = _

  @Param(Array("100"))
  var ops: Int = _

  @Param(Array("1000"))
  var dataSize: Int               = _
  private var data: Map[Int, Int] = _

  private var zioLock: UIO[TReentrantLock] = _

  private var javaLock: UIO[JavaStampedLock] = _

  val rnd = new Random(0)

  @Setup(Level.Trial)
  def setup(): Unit = {
    data = (0 to dataSize).toList.zipWithIndex.toMap
    zioLock = TReentrantLock.make.commit
    javaLock = UIO(new JavaStampedLock(new StampedLock()))
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit =
    data.foreach {
      case (k, v) => assert(k == v)
    }

  @Benchmark
  def reentrantLock(): Int = {

    val io = for {
      lock    <- zioLock
      reader  = lock.readLock.use(_ => readData)
      writer  = lock.writeLock.use(_ => writeData)
      readers <- ZIO.forkAll(List.fill(numReaders)(repeat(ops)(reader)))
      writers <- ZIO.forkAll(List.fill(numWriters)(repeat(ops)(writer)))
      _       <- readers.join
      _       <- writers.join
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def stampedLock(): Int = {

    val io = for {
      lock    <- javaLock
      reader  = lock.readLock.use(_ => readData)
      writer  = lock.writeLock.use(_ => writeData)
      readers <- ZIO.forkAll(List.fill(numReaders)(repeat(ops)(reader)))
      writers <- ZIO.forkAll(List.fill(numWriters)(repeat(ops)(writer)))
      _       <- readers.join
      _       <- writers.join
    } yield 0

    unsafeRun(io)
  }

  def readData: UIO[Int] = ZIO.succeed(data.getOrElse(rnd.nextInt(dataSize), 0))

  def writeData: UIO[Map[Int, Int]] = UIO {
    lazy val nrnd = rnd.nextInt(dataSize)
    data.updated(nrnd, nrnd)
  }

  class JavaStampedLock(jLock: StampedLock) {

    def readLock: ZManaged[Any, Nothing, Long] = {
      val unlock = stamp => jLock.unlockRead(stamp)
      Managed.makeEffect(jLock.readLock())(unlock).orDie
    }

    def writeLock: ZManaged[Any, Nothing, Long] = {
      val unlock = stamp => jLock.unlockWrite(stamp)
      Managed.makeEffect(jLock.writeLock())(unlock).orDie
    }
  }
}
