// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import cats.effect.ContextShift
import org.openjdk.jmh.annotations._
import scalaz.zio.IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
/**
 * This benchmark sequentially offers a number of items to the queue, then takes them out of the queue.
 */
class QueueSequentialBenchmark {

  val totalSize = 10000

  @Benchmark
  def zioQueue(): Int = {

    def repeat(task: IO[Nothing, Unit], max: Int): IO[Nothing, Unit] =
      if (max < 1) IO.unit
      else task.flatMap(_ => repeat(task, max - 1))

    val io = for {
      queue <- Queue.bounded[Int](totalSize)
      _     <- repeat(queue.offer(0).map(_ => ()), totalSize)
      _     <- repeat(queue.take.map(_ => ()), totalSize)
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def fs2Queue(): Int = {
    import cats.effect.{ IO => CIO }
    import cats.implicits._

    implicit val contextShift: ContextShift[CIO] = CIO.contextShift(ExecutionContext.global)

    def repeat(task: CIO[Unit], max: Int): CIO[Unit] =
      if (max < 1) CIO.unit
      else task >> repeat(task, max - 1)

    val io = for {
      queue <- fs2.concurrent.Queue.bounded[CIO, Int](totalSize)
      _     <- repeat(queue.enqueue1(0), totalSize)
      _     <- repeat(queue.dequeue1.map(_ => ()), totalSize)
    } yield 0

    io.unsafeRunSync()
  }
}
