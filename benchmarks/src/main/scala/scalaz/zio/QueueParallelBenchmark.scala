// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import cats.effect.{ ContextShift, IO => CIO }
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
 * This benchmark offers and takes a number of items in parallel, without back pressure.
 */
class QueueParallelBenchmark {

  val totalSize   = 1000
  val parallelism = 5

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

    def repeat(task: IO[Nothing, Unit], max: Int): IO[Nothing, Unit] =
      if (max < 1) IO.unit
      else task.flatMap(_ => repeat(task, max - 1))

    val io = for {
      offers <- IO.forkAll(List.fill(parallelism)(repeat(zioQ.offer(0).map(_ => ()), totalSize / parallelism))).fork
      takes  <- IO.forkAll(List.fill(parallelism)(repeat(zioQ.take.map(_ => ()), totalSize / parallelism))).fork
      _      <- offers.join
      _      <- takes.join
    } yield 0

    unsafeRun(io)
  }

  @Benchmark
  def fs2Queue(): Int = {

    def repeat(task: CIO[Unit], max: Int): CIO[Unit] =
      if (max < 1) CIO.unit
      else task >> repeat(task, max - 1)

    val io = for {
      offers <- List.fill(parallelism)(repeat(fs2Q.enqueue1(0), totalSize / parallelism)).sequence.start
      takes  <- List.fill(parallelism)(repeat(fs2Q.dequeue1.map(_ => ()), totalSize / parallelism)).sequence.start
      _      <- offers.join
      _      <- takes.join
    } yield 0

    io.unsafeRunSync()
  }
}
