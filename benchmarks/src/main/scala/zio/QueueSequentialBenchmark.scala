package zio

import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._
import zio.stm._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(3)
/**
 * This benchmark sequentially offers a number of items to the queue, then takes
 * them out of the queue.
 */
class QueueSequentialBenchmark {

  val totalSize = 1000

  var zioQ: Queue[Int]                      = _
  var fs2Q: cats.effect.std.Queue[CIO, Int] = _
  var zioTQ: TQueue[Int]                    = _
  // var monixQ: monix.catnap.ConcurrentQueue[MTask, Int] = _

  @Setup(Level.Trial)
  def createQueues(): Unit = Unsafe.unsafeCompat { implicit u =>
    zioQ = unsafeRun(Queue.bounded[Int](totalSize))
    fs2Q = cats.effect.std.Queue.bounded[CIO, Int](totalSize).unsafeRunSync()
    zioTQ = unsafeRun(TQueue.bounded(totalSize).commit)
  // monixQ = monix.catnap.ConcurrentQueue.withConfig[MTask, Int](Bounded(totalSize), SPSC).runSyncUnsafe()
  }

  @Benchmark
  def zioQueue(): Int = {

    def repeat(task: UIO[Unit], max: Int): UIO[Unit] =
      if (max < 1) ZIO.unit
      else task.flatMap(_ => repeat(task, max - 1))

    val io = for {
      _ <- repeat(zioQ.offer(0).unit, totalSize)
      _ <- repeat(zioQ.take.unit, totalSize)
    } yield 0

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(io)
    }
  }

  @Benchmark
  def zioTQueue(): Int = {

    def repeat(task: UIO[Unit], max: Int): UIO[Unit] =
      if (max < 1) ZIO.unit
      else task.flatMap(_ => repeat(task, max - 1))

    val io = for {
      _ <- repeat(zioTQ.offer(0).unit.commit, totalSize)
      _ <- repeat(zioTQ.take.unit.commit, totalSize)
    } yield 0

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(io)
    }
  }

  @Benchmark
  def fs2Queue(): Int = {

    def repeat(task: CIO[Unit], max: Int): CIO[Unit] =
      if (max < 1) CIO.unit
      else task >> repeat(task, max - 1)

    val io = for {
      _ <- repeat(fs2Q.offer(0), totalSize)
      _ <- repeat(fs2Q.take.void, totalSize)
    } yield 0

    io.unsafeRunSync()
  }

  // @Benchmark
  // def monixQueue(): Int = {
  //   import BenchmarkUtil.monixScheduler

  //   def repeat(task: MTask[Unit], max: Int): MTask[Unit] =
  //     if (max < 1) MTask.unit
  //     else task >> repeat(task, max - 1)

  //   val io = for {
  //     _ <- repeat(monixQ.offer(0), totalSize)
  //     _ <- repeat(monixQ.poll.map(_ => ()), totalSize)
  //   } yield 0

  //   io.runSyncUnsafe()
  // }
}
