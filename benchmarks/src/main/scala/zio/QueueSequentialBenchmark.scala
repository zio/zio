package zio

import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext

import cats.effect.{ ContextShift, IO => CIO }
import cats.implicits._
import monix.eval.{ Task => MTask }
import monix.execution.BufferCapacity.Bounded
import monix.execution.ChannelType.SPSC
import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._
import zio.stm._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(3)
/**
 * This benchmark sequentially offers a number of items to the queue, then takes them out of the queue.
 */
class QueueSequentialBenchmark {

  val totalSize = 1000

  implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

  var zioQ: Queue[Int]                                 = _
  var fs2Q: fs2.concurrent.Queue[CIO, Int]             = _
  var zioTQ: TQueue[Int]                               = _
  var monixQ: monix.catnap.ConcurrentQueue[MTask, Int] = _

  @Setup(Level.Trial)
  def createQueues(): Unit = {
    zioQ = unsafeRun(Queue.bounded[Int](totalSize))
    fs2Q = fs2.concurrent.Queue.bounded[CIO, Int](totalSize).unsafeRunSync()
    zioTQ = unsafeRun(TQueue.bounded(totalSize).commit)
    monixQ = monix.catnap.ConcurrentQueue.withConfig[MTask, Int](Bounded(totalSize), SPSC).runSyncUnsafe()
  }

  @Benchmark
  def zioQueue(): Int = {

    def repeat(task: UIO[Unit], max: Int): UIO[Unit] =
      if (max < 1) IO.unit
      else task.flatMap(_ => repeat(task, max - 1))

    val io = for {
      _ <- repeat(zioQ.offer(0).map(_ => ()), totalSize)
      _ <- repeat(zioQ.take.map(_ => ()), totalSize)
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def zioTQueue(): Int = {

    def repeat(task: UIO[Unit], max: Int): UIO[Unit] =
      if (max < 1) IO.unit
      else task.flatMap(_ => repeat(task, max - 1))

    val io = for {
      _ <- repeat(zioTQ.offer(0).unit.commit, totalSize)
      _ <- repeat(zioTQ.take.unit.commit, totalSize)
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def fs2Queue(): Int = {

    def repeat(task: CIO[Unit], max: Int): CIO[Unit] =
      if (max < 1) CIO.unit
      else task >> repeat(task, max - 1)

    val io = for {
      _ <- repeat(fs2Q.enqueue1(0), totalSize)
      _ <- repeat(fs2Q.dequeue1.map(_ => ()), totalSize)
    } yield 0

    io.unsafeRunSync()
  }

  @Benchmark
  def monixQueue(): Int = {
    import IOBenchmarks.monixScheduler

    def repeat(task: MTask[Unit], max: Int): MTask[Unit] =
      if (max < 1) MTask.unit
      else task >> repeat(task, max - 1)

    val io = for {
      _ <- repeat(monixQ.offer(0), totalSize)
      _ <- repeat(monixQ.poll.map(_ => ()), totalSize)
    } yield 0

    io.runSyncUnsafe()
  }
}
