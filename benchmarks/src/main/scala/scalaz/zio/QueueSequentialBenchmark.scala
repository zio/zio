// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import cats.effect.ContextShift
import cats.effect.{ IO => CIO }
import cats.implicits._
import org.openjdk.jmh.annotations._
import scalaz.zio.IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(3)
/**
 * This benchmark sequentially offers a number of items to the queue, then takes them out of the queue.
 */
class QueueSequentialBenchmark {

  val totalSize = 1000

  implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

  var zioQ: Queue[Int]                     = _
  var fs2Q: fs2.concurrent.Queue[CIO, Int] = _

  @Setup(Level.Trial)
  def createQueues(): Unit = {
    zioQ = unsafeRun(Queue.bounded[Int](totalSize))
    fs2Q = fs2.concurrent.Queue.bounded[CIO, Int](totalSize).unsafeRunSync()
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
}
